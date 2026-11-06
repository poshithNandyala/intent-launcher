package com.poshith.intent

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.poshith.intent.core.Feed
import com.poshith.intent.core.Quotes
import com.poshith.intent.core.Sitting
import com.poshith.intent.core.Strict
import com.poshith.intent.data.Stats
import com.poshith.intent.data.Store
import com.poshith.intent.guard.Closer
import com.poshith.intent.guard.Launch
import com.poshith.intent.guard.ReelsGuardService
import com.poshith.intent.ui.BaseActivity
import com.poshith.intent.ui.Fonts
import com.poshith.intent.ui.Kind
import com.poshith.intent.ui.P
import com.poshith.intent.ui.animationsOn
import com.poshith.intent.ui.button
import com.poshith.intent.ui.dp
import com.poshith.intent.ui.emergencyLink
import com.poshith.intent.ui.link
import com.poshith.intent.ui.nextThing
import com.poshith.intent.ui.lp
import com.poshith.intent.ui.scroller
import com.poshith.intent.ui.space
import com.poshith.intent.ui.text
import com.poshith.intent.ui.vbox
import com.poshith.intent.ui.vflex

/**
 * The pause after YouTube Shorts or Instagram Reels, shown by the Shorts & Reels guard when you start
 * swiping (and again after the number you chose). The app has already been closed behind it, so
 * nothing plays on. One heading, one factual line, what you planned to do next, a big way out, and a
 * small way back in.
 *
 * In Strict mode there's no "more": at the first swipe of a sitting it says what's left today, and
 * past today's number it's a stop until tomorrow. Only Shorts and Reels close: the rest of YouTube and
 * Instagram keeps working. Counts come from watching scrolls, so they're estimates ("about").
 */
class ReelsActivity : BaseActivity() {

    companion object {
        private const val EXTRA_FEED = "feed"
        private const val EXTRA_PKG = "pkg"
        private const val EXTRA_ASK = "ask"
        private const val EXTRA_STOP = "stop"
        private const val EXTRA_UNTIL = "until"
        fun intent(ctx: Context, feed: Feed, pkg: String, ask: Sitting.Ask): Intent = Intent(ctx, ReelsActivity::class.java)
            .putExtra(EXTRA_FEED, feed.name).putExtra(EXTRA_PKG, pkg).putExtra(EXTRA_ASK, ask.name)

        /** Strict mode: the [nth] stop today (0 = already locked, nothing new), locked until [until] (0 = not locked). */
        fun stopIntent(ctx: Context, feed: Feed, pkg: String, nth: Int, until: Long): Intent =
            intent(ctx, feed, pkg, Sitting.Ask.MORE).putExtra(EXTRA_STOP, nth).putExtra(EXTRA_UNTIL, until).putExtra(EXTRA_STRICT, true)

        /** Strict mode, the first swipe of a sitting: [left] of today's number to go. */
        fun headsUpIntent(ctx: Context, feed: Feed, pkg: String, left: Int): Intent =
            intent(ctx, feed, pkg, Sitting.Ask.START).putExtra(EXTRA_STRICT, true).putExtra(EXTRA_LEFT, left)

        private const val EXTRA_STRICT = "strict"
        private const val EXTRA_LEFT = "left"
    }

    private var strict = false
    private var left = -1
    private var stopN = 0
    private var until = 0L

    private var feed = Feed.SHORTS
    private var pkg = ""
    private var ask = Sitting.Ask.START
    private var leaving = false
    private val h = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        read(intent)
        build()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        read(intent)
        leaving = false
        build()
    }

    private fun read(i: Intent) {
        feed = runCatching { Feed.valueOf(i.getStringExtra(EXTRA_FEED) ?: "") }.getOrDefault(Feed.SHORTS)
        pkg = i.getStringExtra(EXTRA_PKG) ?: feed.packages.first()
        ask = runCatching { Sitting.Ask.valueOf(i.getStringExtra(EXTRA_ASK) ?: "") }.getOrDefault(Sitting.Ask.START)
        strict = i.getBooleanExtra(EXTRA_STRICT, false)
        stopN = i.getIntExtra(EXTRA_STOP, 0)
        until = i.getLongExtra(EXTRA_UNTIL, 0L)
        left = i.getIntExtra(EXTRA_LEFT, -1)
    }

    // Home or another app instead of a choice: nothing is granted, the app stays closed.
    override fun onStop() {
        super.onStop()
        if (!leaving && !isFinishing && !isChangingConfigurations) finish()
    }

    override fun onBackRoot() = closeApp()

    private fun build() {
        when {
            strict && left >= 0 -> buildHeadsUp()
            strict -> buildStop()
            else -> buildGentle()
        }
    }

    /**
     * Every Shorts and Reels screen has the same calm shape: a small label, one heading, one factual
     * line, then (if you set one) what you planned to do next, and the way out as the biggest button.
     * [extra] adds anything specific in between; [actions] adds the buttons.
     */
    private fun frame(label: String, head: String, fact: String, extra: (LinearLayout) -> Unit = {}, actions: (LinearLayout) -> Unit) {
        host.removeAllViews()
        h.removeCallbacksAndMessages(null)
        val col = vbox(Gravity.CENTER_HORIZONTAL)
        val sc = scroller(col)
        host.addView(sc, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        onBars { b -> col.setPadding(dp(28) + b.left, b.top + dp(32), dp(28) + b.right, b.bottom + dp(20)) }

        col.addView(vflex())
        col.addView(text(label.uppercase(), 13.5f, P.text3, Fonts.medium(this)).apply { letterSpacing = 0.08f; gravity = Gravity.CENTER })
        col.addView(text(head, 30f, P.text, Fonts.medium(this)).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(10), 0, 0); breakStrategy = android.text.Layout.BREAK_STRATEGY_BALANCED
        }, lp())
        col.addView(text(fact, 16f, P.text2).apply {
            gravity = Gravity.CENTER; setLineSpacing(0f, 1.25f); setPadding(0, dp(12), 0, 0)
            breakStrategy = android.text.Layout.BREAK_STRATEGY_BALANCED
        }, lp())
        extra(col)
        // A blunt line only if you asked for them (Settings › Shorts and Reels).
        if (Store.bluntLines) col.addView(text(Quotes.bluntFor(System.currentTimeMillis()), 18f, P.text, Fonts.medium(this)).apply {
            gravity = Gravity.CENTER; setLineSpacing(0f, 1.25f); setPadding(0, dp(24), 0, 0)
            breakStrategy = android.text.Layout.BREAK_STRATEGY_BALANCED
        }, lp())
        nextThing()?.let { col.addView(text(it, 16f, P.text).apply { gravity = Gravity.CENTER; setLineSpacing(0f, 1.2f); setPadding(0, dp(22), 0, 0) }, lp()) }
        col.addView(vflex())
        actions(col)
        if (animationsOn()) { col.alpha = 0f; col.animate().alpha(1f).setDuration(220).start() }
    }

    /** Today's count, plainly and honestly: the guard estimates it from scrolling, so "about". */
    private fun countLine(col: LinearLayout, n: Int, both: Boolean) {
        val what = if (both) "Shorts and Reels" else if (n == 1) feed.one else feed.many
        col.addView(text("About $n $what today", 14.5f, P.text2).apply { gravity = Gravity.CENTER; setPadding(0, dp(16), 0, 0) }, lp())
    }

    /** The way out first, then back into the app without its feed. */
    private fun exits(col: LinearLayout, top: Int = 20) {
        col.addView(button("Close ${feed.app}", Kind.PRIMARY) { closeApp() }, lp().apply { topMargin = dp(top) })
        col.addView(space(6))
        col.addView(button("Keep ${feed.app}, without ${feed.many}", Kind.PLAIN) { leaveFeed() }, lp())
    }

    /** Gentle mode: a question at the first swipe, and after the number you chose. */
    private fun buildGentle() {
        val allow = Store.reelsAllow
        val s = ReelsGuardService.sitting(feed)
        val (head, fact) = when {
            ask == Sitting.Ask.MORE -> "That's about ${s.swipes} in a row" to "You chose to use this less. What did you come here for?"
            allow == 0 -> "${feed.many} are off" to "You chose this earlier. The rest of ${feed.app} still works."
            else -> "Was that the one you came for?" to "The next swipe is where the time goes."
        }
        frame(feed.many, head, fact, extra = { countLine(it, Stats.shortVideosToday(feed), both = false) }) { col ->
            exits(col)
            if (allow > 0) col.addView(more(allow, s.waitSec()), lp().apply { topMargin = dp(2) })
        }
    }

    /**
     * Strict mode: a stop, not a question. Today's number is used (or off, or locked), when they open
     * again, and where to go now. Once today's number is used they're closed until tomorrow, whatever
     * a lock says, so the screen never promises an earlier time.
     */
    private fun buildStop() {
        val now = System.currentTimeMillis()
        val eod = Store.endOfDay(now)
        val usedUp = Stats.shortVideosToday() >= Store.reelsAllowToday()
        val opens = Strict.opensAt(usedUp, until, now, eod)
        val time = { ms: Long -> android.text.format.DateFormat.getTimeFormat(this).format(java.util.Date(ms)) }
        val head = when {
            Store.reelsAllowToday() == 0 -> "${feed.many} are off today"
            usedUp -> "That's today's ${feed.many}"
            else -> "${feed.many} are closed for now"
        }
        val fact = (if (opens > 0) "Available again ${Strict.whenWords(opens, eod, time)}. " else "") +
            "You chose this limit earlier. The rest of ${feed.app} still works."
        frame("Strict mode · ${feed.many}", head, fact, extra = { countLine(it, Stats.shortVideosToday(), both = true) }) { col ->
            exits(col)
            emergencyLink("It gives you ${Strict.EMERGENCY_MORE} more Shorts and Reels today.") {
                Store.lockUntil = 0
                Store.addReelsBonus(Strict.EMERGENCY_MORE)
                ReelsGuardService.keepGoing(feed)
                leaving = true
                Closer.reopen(this, pkg)
                finish()
            }?.let { col.addView(it, lp().apply { topMargin = dp(4) }) }
        }
    }

    /**
     * Strict mode, the first swipe of a sitting: what's left of today's number, and what happens after.
     * Not an extension (nothing is added): you chose the number earlier.
     */
    private fun buildHeadsUp() {
        val noun = if (left == 1) feed.one else feed.many
        val head = if (left == 0) "That was your last ${feed.one} today" else "$left $noun left today"
        val fact = (if (left == 0) "The next swipe closes ${feed.many} until tomorrow." else "Then ${feed.many} close until tomorrow.") +
            " The rest of ${feed.app} still works."
        frame("Strict mode · ${feed.many}", head, fact) { col ->
            exits(col)
            col.addView(link(if (left == 0) "Watch this last one" else "Watch ($left left)") { keepGoing() }, lp().apply { topMargin = dp(2) })
        }
    }

    /** The small way on. Free the first time; after that it waits a little longer each time. */
    private fun more(n: Int, waitSec: Int): View {
        val label = "Watch $n more"
        val t = link(label) { keepGoing() }
        var left = waitSec
        val tickRun = object : Runnable {
            override fun run() {
                if (left <= 0) {
                    t.text = label
                    t.isEnabled = true
                } else {
                    t.text = "$label in $left"
                    t.isEnabled = false
                    left--
                    h.postDelayed(this, 1000)
                }
            }
        }
        h.post(tickRun)
        return t
    }

    /** Stay out: the app is already closed behind this screen; make sure it stays quiet. */
    private fun closeApp() {
        if (leaving) return
        leaving = true
        Store.sessions { it.close(pkg) }
        Closer.close(this, pkg)
        finish()
    }

    /** Back into the app, out of its Shorts or Reels. */
    private fun leaveFeed() {
        if (leaving) return
        leaving = true
        if (!ReelsGuardService.leaveFeed(this, pkg)) Launch.goHome(this)
        finish()
    }

    /** Back to the feed, with N more before the next pause. */
    private fun keepGoing() {
        if (leaving) return
        leaving = true
        ReelsGuardService.keepGoing(feed)
        Closer.reopen(this, pkg)
        finish()
    }

    override fun onDestroy() {
        h.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
