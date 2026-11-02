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
import com.poshith.intent.core.Feed
import com.poshith.intent.core.Quotes
import com.poshith.intent.core.Sitting
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
import com.poshith.intent.ui.fillCost
import com.poshith.intent.ui.identityLine
import com.poshith.intent.ui.nextThing
import com.poshith.intent.ui.lp
import com.poshith.intent.ui.ripple
import com.poshith.intent.ui.scroller
import com.poshith.intent.ui.space
import com.poshith.intent.ui.text
import com.poshith.intent.ui.tick
import com.poshith.intent.ui.vbox
import com.poshith.intent.ui.vflex

/**
 * The pause after YouTube Shorts or Instagram Reels, shown by the Shorts & Reels guard when you start
 * swiping (and again after the number you chose). The app has already been closed behind it, so
 * nothing plays on. Today's count, one blunt line, a big way out, and a small way back in.
 *
 * In Strict mode there's no "more": at the first swipe of a sitting it says what's left today, and
 * past today's number it's a stop. Going back to Shorts or Reels then locks them (30 minutes, then 2
 * hours, then the day), only them: the rest of YouTube and Instagram keeps working. It says so up front.
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
        if (strict && left >= 0) { buildHeadsUp(); return }
        if (strict) { buildStop(); return }
        host.removeAllViews()
        h.removeCallbacksAndMessages(null)
        val allow = Store.reelsAllow
        val s = ReelsGuardService.sitting(feed)
        val today = Stats.shortVideosToday(feed)

        val col = vbox(Gravity.CENTER_HORIZONTAL)
        host.addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        onBars { b -> col.setPadding(dp(28) + b.left, b.top + dp(32), dp(28) + b.right, b.bottom + dp(20)) }

        col.addView(vflex())
        col.addView(text(feed.many.uppercase(), 14f, P.text3, Fonts.medium(this)).apply { letterSpacing = 0.08f; gravity = Gravity.CENTER })
        val (head, why) = when {
            ask == Sitting.Ask.MORE -> "That's ${s.swipes} in a row" to "Each one felt like nothing. Together they're an evening."
            allow == 0 -> "${feed.many} are off" to "You chose this while calm. The one you opened was fine; the swipe is where the hour goes."
            else -> "Was that the one you came for?" to "The next swipe is where the hour goes."
        }
        col.addView(text(head, 30f, P.text, Fonts.medium(this)).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(10), 0, 0); breakStrategy = android.text.Layout.BREAK_STRATEGY_BALANCED
        }, lp())
        col.addView(text(why, 16f, P.text2).apply {
            gravity = Gravity.CENTER; setLineSpacing(0f, 1.25f); setPadding(0, dp(12), 0, 0)
            breakStrategy = android.text.Layout.BREAK_STRATEGY_BALANCED
        }, lp())
        // Today's number, plainly. It turns warm once it's a lot.
        val noun = if (today == 1) feed.one else feed.many
        col.addView(text("$today $noun today", 15f, if (today >= 50) P.warn else P.text2, Fonts.medium(this)).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(18), 0, 0)
        }, lp())

        // A blunt line here, not a gentle quote: this is the moment a soft one gets skimmed.
        col.addView(text(Quotes.bluntFor(System.currentTimeMillis()), 19f, P.text, Fonts.medium(this)).apply {
            gravity = Gravity.CENTER; setLineSpacing(0f, 1.25f); setPadding(0, dp(30), 0, 0)
            breakStrategy = android.text.Layout.BREAK_STRATEGY_BALANCED
        }, lp())
        nextThing()?.let { col.addView(text(it, 15f, P.text2).apply { gravity = Gravity.CENTER; setPadding(0, dp(14), 0, 0) }, lp()) }
        col.addView(vflex())

        col.addView(button("Close ${feed.app}", Kind.PRIMARY) { closeApp() }, lp())
        col.addView(space(6))
        col.addView(button("Keep ${feed.app}, without ${feed.many}", Kind.PLAIN) { leaveFeed() }, lp())
        if (allow > 0) col.addView(more(allow, s.waitSec()), lp().apply { topMargin = dp(2) })

        if (animationsOn()) { col.alpha = 0f; col.animate().alpha(1f).setDuration(220).start() }
    }

    /** Strict mode: a stop, not a question. What happened, what the next one costs, where to go now. */
    private fun buildStop() {
        host.removeAllViews()
        h.removeCallbacksAndMessages(null)
        val now = System.currentTimeMillis()
        val locked = until > now
        val at = if (locked) android.text.format.DateFormat.getTimeFormat(this).format(java.util.Date(until)) else ""
        val today = Stats.shortVideosToday()

        val col = vbox(Gravity.CENTER_HORIZONTAL)
        val sc = scroller(col)
        host.addView(sc, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        onBars { b -> col.setPadding(dp(28) + b.left, b.top + dp(32), dp(28) + b.right, b.bottom + dp(20)) }

        col.addView(vflex())
        col.addView(text("STRICT MODE · ${feed.many.uppercase()}", 13.5f, P.text3, Fonts.medium(this)).apply { letterSpacing = 0.08f; gravity = Gravity.CENTER })
        val head = when {
            locked -> "${feed.many} are locked until $at"
            Store.reelsAllowToday() == 0 -> "${feed.many} are off"
            else -> "That's all your ${feed.many} for today"
        }
        col.addView(text(head, 30f, P.text, Fonts.medium(this)).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(10), 0, 0); breakStrategy = android.text.Layout.BREAK_STRATEGY_BALANCED
        }, lp())
        val why = when {
            locked && stopN == 0 -> "Only ${feed.many} are locked: the rest of ${feed.app} works. They open again at $at."
            locked -> "You went back after being stopped. Only ${feed.many} are locked: the rest of ${feed.app} works. " +
                com.poshith.intent.core.Strict.nextLockWords(stopN)
            else -> "You chose this while calm. ${com.poshith.intent.core.Strict.nextLockWords(stopN)}"
        }
        col.addView(text(why, 16f, P.text2).apply {
            gravity = Gravity.CENTER; setLineSpacing(0f, 1.25f); setPadding(0, dp(12), 0, 0)
            breakStrategy = android.text.Layout.BREAK_STRATEGY_BALANCED
        }, lp())
        col.addView(text(Quotes.bluntFor(now), 19f, P.text, Fonts.medium(this)).apply {
            gravity = Gravity.CENTER; setLineSpacing(0f, 1.25f); setPadding(0, dp(26), 0, 0)
            breakStrategy = android.text.Layout.BREAK_STRATEGY_BALANCED
        }, lp())
        val noun = if (today == 1) feed.one else feed.many
        col.addView(text("$today $noun today", 15f, if (today >= 50) P.warn else P.text2, Fonts.medium(this)).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(18), 0, 0)
        }, lp())
        val cost = text("", 14f, P.text3).apply { gravity = Gravity.CENTER; setLineSpacing(0f, 1.2f); setPadding(0, dp(6), 0, 0) }
        col.addView(cost, lp())
        fillCost(cost)
        nextThing()?.let { col.addView(text(it, 16f, P.text).apply { gravity = Gravity.CENTER; setPadding(0, dp(20), 0, 0) }, lp()) }
        identityLine()?.let { col.addView(text(it, 14.5f, P.text3).apply { gravity = Gravity.CENTER; setPadding(0, dp(6), 0, 0) }, lp()) }
        col.addView(vflex())

        col.addView(button("Close ${feed.app}", Kind.PRIMARY) { closeApp() }, lp().apply { topMargin = dp(20) })
        // The rest of the app is fine: back into it, out of the feed.
        col.addView(space(6))
        col.addView(button("Keep ${feed.app}, without ${feed.many}", Kind.PLAIN) { leaveFeed() }, lp())
        emergencyLink("It ends the lock and gives you ${com.poshith.intent.core.Strict.EMERGENCY_MORE} more ${feed.many} today.") {
            Store.lockUntil = 0
            Store.addReelsBonus(com.poshith.intent.core.Strict.EMERGENCY_MORE)
            ReelsGuardService.keepGoing(feed)
            leaving = true
            Closer.reopen(this, pkg)
            finish()
        }?.let { col.addView(it, lp().apply { topMargin = dp(4) }) }

        if (animationsOn()) { col.alpha = 0f; col.animate().alpha(1f).setDuration(220).start() }
    }

    /**
     * Strict mode, the first swipe of a sitting: what's left of today's number and what happens after.
     * Not an extension (nothing is added): you chose the number while calm.
     */
    private fun buildHeadsUp() {
        host.removeAllViews()
        h.removeCallbacksAndMessages(null)
        val now = System.currentTimeMillis()
        val col = vbox(Gravity.CENTER_HORIZONTAL)
        val sc = scroller(col)
        host.addView(sc, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        onBars { b -> col.setPadding(dp(28) + b.left, b.top + dp(32), dp(28) + b.right, b.bottom + dp(20)) }

        col.addView(vflex())
        col.addView(text("STRICT MODE · ${feed.many.uppercase()}", 13.5f, P.text3, Fonts.medium(this)).apply { letterSpacing = 0.08f; gravity = Gravity.CENTER })
        val noun = if (left == 1) feed.one else feed.many
        val head = if (left == 0) "That was your last ${feed.one} today" else "$left $noun left today"
        col.addView(text(head, 30f, P.text, Fonts.medium(this)).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(10), 0, 0); breakStrategy = android.text.Layout.BREAK_STRATEGY_BALANCED
        }, lp())
        col.addView(text((if (left == 0) "The next swipe closes ${feed.many}. " else "When they're gone, ${feed.many} close. ") +
            "Go back after that and they lock for 30 minutes.", 16f, P.text2).apply {
            gravity = Gravity.CENTER; setLineSpacing(0f, 1.25f); setPadding(0, dp(12), 0, 0)
            breakStrategy = android.text.Layout.BREAK_STRATEGY_BALANCED
        }, lp())
        col.addView(text(Quotes.bluntFor(now), 19f, P.text, Fonts.medium(this)).apply {
            gravity = Gravity.CENTER; setLineSpacing(0f, 1.25f); setPadding(0, dp(26), 0, 0)
            breakStrategy = android.text.Layout.BREAK_STRATEGY_BALANCED
        }, lp())
        nextThing()?.let { col.addView(text(it, 16f, P.text).apply { gravity = Gravity.CENTER; setPadding(0, dp(20), 0, 0) }, lp()) }
        identityLine()?.let { col.addView(text(it, 14.5f, P.text3).apply { gravity = Gravity.CENTER; setPadding(0, dp(6), 0, 0) }, lp()) }
        col.addView(vflex())

        col.addView(button("Close ${feed.app}", Kind.PRIMARY) { closeApp() }, lp().apply { topMargin = dp(20) })
        col.addView(space(6))
        col.addView(button("Keep ${feed.app}, without ${feed.many}", Kind.PLAIN) { leaveFeed() }, lp())
        col.addView(text(if (left == 0) "Watch this last one" else "Watch ($left left)", 13.5f, P.text3).apply {
            gravity = Gravity.CENTER; minHeight = dp(44); setPadding(dp(16), 0, dp(16), 0)
            background = ripple(null, dp(20).toFloat())
            setOnClickListener { it.tick(); keepGoing() }
        }, lp().apply { topMargin = dp(2) })
        if (animationsOn()) { col.alpha = 0f; col.animate().alpha(1f).setDuration(220).start() }
    }

    /** The small way on. Free the first time; after that it waits a little longer each time. */
    private fun more(n: Int, waitSec: Int): View {
        val t = text("", 13.5f, P.text3).apply {
            gravity = Gravity.CENTER; minHeight = dp(40); setPadding(dp(16), 0, dp(16), 0)
            background = ripple(null, dp(20).toFloat())
        }
        val label = "Watch $n more"
        var left = waitSec
        val tickRun = object : Runnable {
            override fun run() {
                if (left <= 0) {
                    t.text = label
                    t.isEnabled = true
                    t.setOnClickListener { it.tick(); keepGoing() }
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
