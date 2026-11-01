package com.poshith.intent.core

/** The endless short-video feeds Intent can step into, when you turn that on. */
enum class Feed(val packages: Set<String>, val app: String, val one: String, val many: String) {
    SHORTS(setOf("com.google.android.youtube", "app.revanced.android.youtube"), "YouTube", "Short", "Shorts"),
    REELS(setOf("com.instagram.android"), "Instagram", "Reel", "Reels");

    companion object {
        fun of(pkg: String?): Feed? = values().firstOrNull { pkg != null && pkg in it.packages }
    }
}

/**
 * How a swipe through Shorts or Reels is told apart from everything else: by the names (resource
 * IDs) of the view that scrolled and the views around it, never by anything you see or type.
 * A swipe counts when that chain includes the feed's full-screen pager and isn't inside comments or
 * another sheet. The names are the ones current open-source blockers rely on (2026). Several are
 * kept for each app, because the apps rename things now and then.
 */
object FeedIds {
    private val PAGER = mapOf(
        Feed.SHORTS to listOf("reel_recycler", "reel_watch", "reel_player", "shorts_player", "shorts_video"),
        Feed.REELS to listOf("clips_viewer_view_pager", "clips_view_pager"),
    )
    private val SHEETS = mapOf(
        Feed.SHORTS to listOf("comment", "reply", "bottom_sheet", "results"),
        Feed.REELS to listOf("comment", "bottom_sheet", "recycler_view_container_id"),
    )

    /**
     * Views that exist only while the full-screen feed is on screen (never in a shelf of thumbnails on
     * the home feed). Current YouTube scrolls Shorts in a view with no telling name, so these are how
     * Intent knows you're in Shorts at all. Per-page ones come first: each Short has its own, so a new
     * one appearing in front is a swipe.
     */
    val PLAYER_PAGES = mapOf(
        Feed.SHORTS to listOf("reel_player_page_container", "reel_player_underlay"),
        Feed.REELS to emptyList(), // Instagram's per-reel views also appear in the home feed; its swipes are caught by the pager.
    )
    val PLAYER_SCREENS = mapOf(
        Feed.SHORTS to listOf("reel_watch_fragment_root", "reel_watch_player", "shorts_video_pager", "reel_recycler"),
        Feed.REELS to listOf("clips_viewer_view_pager", "clips_view_pager"),
    )

    /** A scroll inside a comments or other sheet, never a swipe to the next video. */
    fun inSheet(feed: Feed, path: List<String>): Boolean {
        val sheets = SHEETS.getValue(feed)
        return path.any { id -> sheets.any { id.contains(it) } }
    }

    /** [path]: the scrolled view's ID first, then its parents' (short names, without "package:id/"). */
    fun isFeedSwipe(feed: Feed, path: List<String>): Boolean {
        val pager = PAGER.getValue(feed)
        val sheets = SHEETS.getValue(feed)
        return path.any { id -> pager.any { id.contains(it) } } && path.none { id -> sheets.any { id.contains(it) } }
    }

    /** "com.google.android.youtube:id/reel_recycler" → "reel_recycler". */
    fun short(id: String?): String = id?.substringAfter(":id/").orEmpty()
}

/**
 * One sitting in a feed: the swipes since you came in, and when Intent steps in.
 *
 * The one you opened (from a friend, a link, search) is always fine: it's the chain that gets a
 * pause. The first swipe to another one asks, every time, until you choose to keep watching; then
 * [keepGoing] lets a set number through before it asks again, with a little longer to wait for the
 * "more" button each time. A sitting ends after a few quiet minutes, so leaving and coming straight
 * back doesn't start over.
 */
class Sitting {
    enum class Ask { NONE, START, MORE }

    /** New swipes this sitting. */
    var swipes = 0
        private set
    /** How many times you chose to keep watching this sitting. */
    var continues = 0
        private set
    private var allowedUntil = 0
    private var started = false
    /** The last scroll event, and when the swipe it belongs to started. */
    private var lastEventAt = 0L
    private var burstStart = 0L
    /** The last swipe counted from scrolling, until the new video it brought in has been seen. */
    private var unmatchedAt = -1L

    /**
     * A scroll of the feed's pager at [now] (ms). True if it starts a new swipe, not more of the last one.
     * One swipe is a burst of scroll events close together: a pause starts the next swipe, and so does a
     * burst that's gone on longer than one swipe can (flicking fast runs swipes together).
     */
    fun swipe(now: Long): Boolean {
        if (started && now - lastEventAt > GAP_MS && now - lastCountAt > GAP_MS) reset()
        val fresh = !started || now - lastEventAt > QUIET_MS || now - burstStart > BURST_MS
        started = true
        lastEventAt = now
        if (fresh) { burstStart = now; count(now); unmatchedAt = now }
        return fresh
    }

    /**
     * A different video came to the front at [now] (seen on screen, a moment after the scroll). True if it's
     * a swipe the scrolling didn't already count; each counted scroll is matched to one new video.
     */
    fun newVideo(now: Long): Boolean {
        if (started && now - lastEventAt > GAP_MS && now - lastCountAt > GAP_MS) reset()
        started = true
        if (unmatchedAt >= 0 && now - unmatchedAt < MATCH_MS) { unmatchedAt = -1; return false }
        unmatchedAt = -1
        count(now)
        return true
    }

    private var lastCountAt = 0L
    private fun count(now: Long) { swipes++; lastCountAt = now }

    /** After a new swipe: should Intent step in? */
    fun ask(): Ask = when {
        continues == 0 -> Ask.START
        swipes > allowedUntil -> Ask.MORE
        else -> Ask.NONE
    }

    /** "Watch [n] more": the next [n] swipes go through. */
    fun keepGoing(n: Int) {
        continues++
        allowedUntil = swipes + n
    }

    /** Seconds before "watch more" can be tapped: none the first time, then 5 s more each time (max 20). */
    fun waitSec(): Int = (continues * 5).coerceAtMost(20)

    fun reset() {
        swipes = 0
        continues = 0
        allowedUntil = 0
        started = false
        unmatchedAt = -1
    }

    companion object {
        /** Scroll events closer together than this belong to one swipe (the drag, then the page settling,
         *  which on a slow phone can come half a second later). */
        const val QUIET_MS = 700L
        /** One swipe lasts at most this long; a longer run of scrolling is several swipes. */
        const val BURST_MS = 2_000L
        /** A new video seen this soon after a counted scroll is the one that scroll brought in. Fast
         *  flicking runs scrolls together, and then it's the new videos that count the rest. */
        const val MATCH_MS = 2_500L
        /** This long without a swipe, and the next one starts a new sitting. */
        const val GAP_MS = 3 * 60_000L
    }
}
