package com.poshith.intent.guard

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.poshith.intent.ReelsActivity
import com.poshith.intent.core.Feed
import com.poshith.intent.core.FeedIds
import com.poshith.intent.core.Sitting
import com.poshith.intent.data.Stats
import com.poshith.intent.data.Store

/**
 * The Shorts & Reels guard (optional, separate from Focus Guard, off until you turn it on).
 *
 * What it does:  Android tells it only when something scrolls inside YouTube or Instagram (packageNames
 *                in res/xml/reels_guard.xml). For each scroll it looks at the names of the view that
 *                scrolled and the views around it, to tell a swipe through Shorts or Reels from
 *                scrolling comments or the home feed. It counts those swipes and, when you start one
 *                (and after the number you chose), shows Intent's pause screen.
 * What it can't: read text, see any other app, or go online (Intent has no internet permission).
 */
class ReelsGuardService : AccessibilityService() {

    companion object {
        @Volatile var running = false
            private set
        @Volatile private var instance: ReelsGuardService? = null

        private val sittings = HashMap<Feed, Sitting>()
        fun sitting(f: Feed): Sitting = synchronized(sittings) { sittings.getOrPut(f) { Sitting() } }

        /** "Watch N more" on the pause screen. */
        fun keepGoing(f: Feed) = synchronized(sittings) { sitting(f).keepGoing(Store.reelsAllow) }

        /** Out of the feed but still in the app: Android's Back, once the pause screen has gone. */
        fun leaveFeed(): Boolean {
            val s = instance ?: return false
            s.h.postDelayed({ s.performGlobalAction(GLOBAL_ACTION_BACK) }, 350)
            return true
        }

        fun goHome(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_HOME) ?: false

        /** Switched on in Android's accessibility settings (it may be between restarts right now). */
        fun isOn(ctx: Context): Boolean {
            if (running) return true
            return try {
                val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
                val me = ComponentName(ctx, ReelsGuardService::class.java)
                enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
            } catch (_: Exception) { false }
        }
    }

    private val h = Handler(Looper.getMainLooper())
    private var askedAt = 0L
    private val lastIndex = HashMap<Feed, Int>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        Store.init(this)
        running = true
        instance = this
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e == null || e.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED || !Store.reelsOn) return
        val pkg = e.packageName?.toString() ?: return
        val feed = Feed.of(pkg) ?: return
        // Opening a Short or closing a sheet sends scroll events that don't move: not a swipe.
        if (Build.VERSION.SDK_INT >= 28 && e.scrollDeltaY == 0) return
        val src = e.source ?: return
        // The full-screen player, not a shelf of thumbnails or a row in a list.
        val r = Rect()
        src.getBoundsInScreen(r)
        if (r.height() < resources.displayMetrics.heightPixels / 2) return
        if (!FeedIds.isFeedSwipe(feed, idPath(src))) return
        // Android 8 doesn't say how far a view moved; there, a swipe is the pager's first visible item changing.
        if (Build.VERSION.SDK_INT < 28 && e.fromIndex >= 0) {
            val last = lastIndex[feed]
            lastIndex[feed] = e.fromIndex
            if (last == null || last == e.fromIndex) return
        }

        val now = SystemClock.elapsedRealtime()
        val ask = synchronized(sittings) {
            val s = sitting(feed)
            if (!s.swipe(now)) return
            s.ask()
        }
        Stats.shortVideo(feed)
        if (ask != Sitting.Ask.NONE && now - askedAt > 1_500) {
            askedAt = now
            try {
                startActivity(ReelsActivity.intent(this, feed, pkg, ask)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION))
            } catch (_: Exception) {}
        }
    }

    /** The view's resource name, then its parents', up to a few levels (names only, never text). */
    private fun idPath(n: AccessibilityNodeInfo): List<String> {
        val out = ArrayList<String>(8)
        var cur: AccessibilityNodeInfo? = n
        var depth = 0
        while (cur != null && depth < 8) {
            out += FeedIds.short(cur.viewIdResourceName)
            cur = cur.parent
            depth++
        }
        return out
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        shutdown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    private fun shutdown() {
        running = false
        instance = null
        h.removeCallbacksAndMessages(null)
    }
}
