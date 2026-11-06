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
            // One Back, not two: while it's on its way, a locked feed isn't bounced as well.
            s.leavingAt = SystemClock.elapsedRealtime()
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
        // Strict mode, stepping into Shorts or Reels: locked, so straight back out (the rest of the app
        // stays); or today's number is used, so it's a stop.
        if (Store.strictActive() && Store.locked()) {
            lastPage = screen.page
            bounce(feed, pkg)
            return
        }
        if (entering && Store.strictActive() && Stats.shortVideosToday() >= Store.reelsAllowToday()) {
            lastPage = screen.page
            stop(feed, pkg, counts = true)
            return
        }
        // "Off" means off: stepping into the feed at all gets the pause, not just the swipe. (Strict mode
        // counts against today's number instead, above.)
        if (entering && !Store.strictActive() && Store.reelsAllow == 0) {
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
        // Strict mode, locked: this one doesn't count, it's just back out of the feed.
        if (Store.strictActive() && Store.locked()) { bounce(feed, pkg); return }
        Stats.shortVideo(feed)
        // Strict mode: no "more", just today's number (Shorts and Reels together). Said at the first swipe
        // of a sitting, warned at the last one, and past it a stop.
        if (Store.strictActive()) {
            val today = Stats.shortVideosToday()
            val allow = Store.reelsAllowToday()
            log("swipe counted (strict), $today of $allow today")
            when {
                today > allow -> stop(feed, pkg, counts = true)
                ask == Sitting.Ask.START -> headsUp(feed, pkg, com.poshith.intent.core.Strict.left(allow, today))
                today == allow -> toast("Last ${feed.one.lowercase()} today. The next swipe closes ${feed.many}.")
            }
            return
        }
        log("swipe counted, ask=$ask")
        if (ask != Sitting.Ask.NONE) ask(feed, pkg, ask, count = true)
    }

    /**
     * Strict mode, the first swipe of a sitting: not a question about more, just what's left today (and
     * what happens after). The app closes behind it, like any pause; "Watch" takes you back.
     */
    private fun headsUp(feed: Feed, pkg: String, left: Int) {
        val now = SystemClock.elapsedRealtime()
        if (now - askedAt <= 1_500 || com.poshith.intent.guard.Calls.active(this)) return
        askedAt = now
        lastPage = null; twoBack = null
        Closer.toHome(this)
        Closer.silence(this, pkg)
        try {
            startActivity(ReelsActivity.headsUpIntent(this, feed, pkg, left).addFlags(Closer.OVER_APP or Intent.FLAG_ACTIVITY_NO_ANIMATION))
        } catch (_: Exception) {}
    }

    private var bouncedAt = 0L
    @Volatile private var leavingAt = 0L

    /**
     * Strict mode, Shorts or Reels locked: Android's Back takes you out of the feed and the rest of the app
     * stays (messages, search, long videos). Back goes to the window in focus, so it waits until the app
     * is (just after it opens, the home screen may still have it), and tries twice. If you're still in
     * the feed after that (an app that doesn't go back), the app closes and the lock screen says why.
     */
    private fun bounce(feed: Feed, pkg: String, attempt: Int = 1) {
        val now = SystemClock.elapsedRealtime()
        if (attempt == 1 && (now - bouncedAt < 1_200 || now - leavingAt < 2_000)) return
        if (com.poshith.intent.guard.Calls.active(this) || !Store.locked()) return
        bouncedAt = now
        val focused = try { rootInActiveWindow?.packageName?.toString() == pkg } catch (_: Exception) { false }
        if (!focused && attempt < 4) { h.postDelayed({ bounce(feed, pkg, attempt + 1) }, 300); return }
        log("locked: out of ${feed.many} (try $attempt)")
        inFeed = null
        performGlobalAction(GLOBAL_ACTION_BACK)
        if (attempt <= 4) {
            // When they really open again: once today's number is used, that's tomorrow, whatever the lock says.
            val wall = System.currentTimeMillis()
            val eod = Store.endOfDay(wall)
            val opens = com.poshith.intent.core.Strict.opensAt(Stats.shortVideosToday() >= Store.reelsAllowToday(), Store.lockUntil, wall, eod)
            val at = com.poshith.intent.core.Strict.whenWords(opens, eod) { android.text.format.DateFormat.getTimeFormat(this).format(java.util.Date(it)) }
            toast("${feed.many} are closed until ${at.removePrefix("at ")}. The rest of ${feed.app} still works.")
        }
        h.postDelayed({
            if (!Store.locked() || playerScreen(feed, pkg) == null) return@postDelayed
            if (attempt < 6) bounce(feed, pkg, 6) // one more Back
            else { askedAt = 0; stop(feed, pkg, counts = false) }
        }, 1_000)
    }

    private var toastAt = 0L
    private fun toast(msg: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - toastAt < 2_000) return
        toastAt = now
        try { android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show() } catch (_: Exception) {}
    }

    /**
     * Strict mode: close the app and say so. A stop that [counts] goes on today's tally, and from the
     * second one Shorts and Reels lock (30 minutes, then 2 hours, then the day). Only them: the rest of
     * YouTube and Instagram keeps working.
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
