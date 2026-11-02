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
 *                (and after the number you chose), closes the app and shows Intent's pause screen.
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

        /** Back into the app, out of its feed: the app comes back where it was, then Android's Back. */
        fun leaveFeed(ctx: Context, pkg: String): Boolean {
            if (!Closer.reopen(ctx, pkg)) return false
            val s = instance ?: return true
            s.h.postDelayed({ s.performGlobalAction(GLOBAL_ACTION_BACK) }, 900)
            return true
        }

        /** Switched on in Android's accessibility settings (it may be between restarts right now). */
        fun isOn(ctx: Context): Boolean = running || Launch.switchedOn(ctx, ReelsGuardService::class.java)
    }

    private val h = Handler(Looper.getMainLooper())
    private var askedAt = 0L
    private val lastIndex = HashMap<Feed, Int>()
    /** The Short in front right now (its page view), so a different one in front means you swiped. */
    private var lastPage: AccessibilityNodeInfo? = null
    /** The one before it, and when the front one last changed: going straight back isn't a new video. */
    private var twoBack: AccessibilityNodeInfo? = null
    private var changedAt = 0L
    private var inFeed: Feed? = null
    private var lookPending = false
    private var lookPkg = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        Store.init(this)
        running = true
        instance = this
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e == null || !Store.reelsOn) return
        val pkg = e.packageName?.toString() ?: return
        val feed = Feed.of(pkg) ?: return
        // Strict mode lock: YouTube and Instagram stay closed. (Focus Guard does this for every pull app
        // when it's on; this covers them when it isn't.)
        if (e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && !FocusGuardService.running && Store.locked()) {
            stop(feed, pkg, counts = false); return
        }
        when (e.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> onScroll(e, feed, pkg)
            // The screen changed: look (at most every 300 ms) for the full-screen player, and whether
            // the Short in front is a new one. Current YouTube needs this: its Shorts pager has no
            // telling name, so the scroll alone doesn't say it was Shorts.
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED, AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                lookPkg = pkg
                if (!lookPending) { lookPending = true; h.postDelayed({ lookPending = false; look(lookPkg) }, 300) }
            }
        }
    }

    private fun onScroll(e: AccessibilityEvent, feed: Feed, pkg: String) {
        // Opening a Short or closing a sheet sends scroll events that don't move: not a swipe.
        if (Build.VERSION.SDK_INT >= 28 && e.scrollDeltaY == 0) return
        val src = e.source ?: run { log("scroll without a source"); return }
        // The full-screen player, not a shelf of thumbnails or a row in a list.
        val r = Rect()
        src.getBoundsInScreen(r)
        if (r.height() < resources.displayMetrics.heightPixels / 2) return
        val path = idPath(src)
        val named = FeedIds.isFeedSwipe(feed, path)
        // Not a pager Intent knows by name: still a swipe if it's a full-screen scroll while the
        // feed's player is in front, and not inside comments.
        if (!named && (FeedIds.inSheet(feed, path) || playerScreen(feed, pkg) == null)) {
            log("scroll not counted: ${path.filter { it.isNotEmpty() }.take(4)}")
            return
        }
        // Android 8 doesn't say how far a view moved; there, a swipe is the pager's first visible item changing.
        if (Build.VERSION.SDK_INT < 28 && e.fromIndex >= 0) {
            val last = lastIndex[feed]
            lastIndex[feed] = e.fromIndex
            if (last == null || last == e.fromIndex) return
        }
        swiped(feed, pkg, newVideo = false)
    }

    /** What's in front of [pkg] now: in the feed or not, and has the Short changed since last time? */
    private fun look(pkg: String) {
        val feed = Feed.of(pkg) ?: return
        val screen = playerScreen(feed, pkg)
        if (screen == null) {
            // Left the feed (or the app): the next time you come in starts clean.
            inFeed = null; lastPage = null; twoBack = null
            return
        }
        val entering = inFeed != feed
        inFeed = feed
        // Strict mode: stepping into the feed with today's number used is a stop.
        if (entering && Store.strictActive() && Stats.shortVideosToday() >= Store.reelsAllow) {
            lastPage = screen.page
            stop(feed, pkg, counts = true)
            return
        }
        // "Off" means off: stepping into the feed at all gets the pause, not just the swipe.
        if (entering && Store.reelsAllow == 0) {
            lastPage = screen.page
            ask(feed, pkg, Sitting.Ask.START, count = false)
            return
        }
        val page = screen.page ?: return
        val before = lastPage
        lastPage = page
        if (before != null && page != before) {
            // Straight back to the one from a moment ago: a swipe you didn't finish (or a step back), not a new video.
            val now = SystemClock.elapsedRealtime()
            val back = page == twoBack && now - changedAt < 1_500
            twoBack = before; changedAt = now
            if (back) { log("back to the same ${feed.one}"); return }
            log("new ${feed.one} in front"); swiped(feed, pkg, newVideo = true)
        }
    }

    /** Debug builds and the emulator test read these; view names and counts only, never what's on screen. */
    private fun log(msg: String) { android.util.Log.d("IntentReels", msg) }

    private class PlayerScreen(val page: AccessibilityNodeInfo?)

    /** The feed's full-screen player, if it's in front: the Short's own page view when there is one. */
    private fun playerScreen(feed: Feed, pkg: String): PlayerScreen? {
        val root = rootFor(pkg) ?: return null
        val tall = resources.displayMetrics.heightPixels * 3 / 5
        val r = Rect()
        fun biggest(ids: List<String>, minHeight: Int = tall): AccessibilityNodeInfo? {
            var best: AccessibilityNodeInfo? = null
            var bestArea = 0
            for (id in ids) {
                val found = try { root.findAccessibilityNodeInfosByViewId("$pkg:id/$id") } catch (_: Exception) { null } ?: continue
                for (n in found) {
                    if (!n.isVisibleToUser) continue
                    n.getBoundsInScreen(r)
                    if (r.height() < minHeight) continue
                    val area = r.width() * r.height()
                    if (area > bestArea) { best = n; bestArea = area }
                }
            }
            return best
        }
        val page = biggest(FeedIds.PLAYER_PAGES.getValue(feed), tall)
            ?: biggest(FeedIds.PLAYER_PAGES.getValue(feed), resources.displayMetrics.heightPixels / 3)?.let { return PlayerScreen(null) } // mid-swipe
        if (page != null) return PlayerScreen(page)
        return if (biggest(FeedIds.PLAYER_SCREENS.getValue(feed)) != null) PlayerScreen(null) else null
    }

    private fun rootFor(pkg: String): AccessibilityNodeInfo? {
        try { rootInActiveWindow?.let { if (it.packageName?.toString() == pkg) return it } } catch (_: Exception) {}
        return try { windows.firstNotNullOfOrNull { w -> w.root?.takeIf { it.packageName?.toString() == pkg } } } catch (_: Exception) { null }
    }

    /** A swipe seen from scrolling, or a [newVideo] seen on screen (the two are matched, not double-counted). */
    private fun swiped(feed: Feed, pkg: String, newVideo: Boolean) {
        val now = SystemClock.elapsedRealtime()
        val ask = synchronized(sittings) {
            val s = sitting(feed)
            if (!(if (newVideo) s.newVideo(now) else s.swipe(now))) return
            s.ask()
        }
        Stats.shortVideo(feed)
        // Strict mode: no questions, just today's number (Shorts and Reels together). Past it, a stop.
        if (Store.strictActive()) {
            val today = Stats.shortVideosToday()
            log("swipe counted (strict), $today of ${Store.reelsAllow} today")
            if (today > Store.reelsAllow) stop(feed, pkg, counts = true)
            return
        }
        log("swipe counted, ask=$ask")
        if (ask != Sitting.Ask.NONE) ask(feed, pkg, ask, count = true)
    }

    /**
     * Strict mode: close the app and say so. A stop that [counts] goes on today's tally, and from the
     * second one the pull apps lock (30 minutes, then 2 hours, then the day).
     */
    private fun stop(feed: Feed, pkg: String, counts: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (now - askedAt <= 1_500 || com.poshith.intent.guard.Calls.active(this)) return
        askedAt = now
        val wall = System.currentTimeMillis()
        var n = 0
        if (counts) {
            n = Store.noteStop(wall)
            val until = com.poshith.intent.core.Strict.lockUntil(n, wall, Store.endOfDay(wall))
            if (until > Store.lockUntil) Store.lockUntil = until
        }
        log("strict stop $n, locked until ${Store.lockUntil}")
        lastPage = null; twoBack = null; inFeed = null
        Closer.toHome(this)
        Closer.silence(this, pkg)
        val until = if (Store.lockUntil > wall) Store.lockUntil else 0L
        try {
            startActivity(ReelsActivity.stopIntent(this, feed, pkg, n, until).addFlags(Closer.OVER_APP or Intent.FLAG_ACTIVITY_NO_ANIMATION))
        } catch (_: Exception) {}
    }

    private fun ask(feed: Feed, pkg: String, ask: Sitting.Ask, @Suppress("UNUSED_PARAMETER") count: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (now - askedAt <= 1_500 || com.poshith.intent.guard.Calls.active(this)) return
        askedAt = now
        // Whatever is in front when you come back is the new starting point, not a swipe.
        lastPage = null; twoBack = null
        // The app goes first (home in front without "user leaving", so no picture-in-picture, and its
        // sound stops), then the pause appears over the home screen.
        Closer.toHome(this)
        Closer.silence(this, pkg)
        try {
            startActivity(ReelsActivity.intent(this, feed, pkg, ask).addFlags(Closer.OVER_APP or Intent.FLAG_ACTIVITY_NO_ANIMATION))
        } catch (_: Exception) {}
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
        lastPage = null; twoBack = null; inFeed = null; lookPending = false
        h.removeCallbacksAndMessages(null)
    }
}
