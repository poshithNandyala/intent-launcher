package com.poshith.intent

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.poshith.intent.core.BlockReason
import com.poshith.intent.core.Decision
import com.poshith.intent.core.Feeling
import com.poshith.intent.core.Purpose
import com.poshith.intent.core.Rule
import com.poshith.intent.data.Apps
import com.poshith.intent.data.Stats
import com.poshith.intent.data.Store
import com.poshith.intent.data.Usage
import com.poshith.intent.guard.FocusGuardService
import com.poshith.intent.guard.Launch
import com.poshith.intent.guard.Reminder
import com.poshith.intent.ui.BaseActivity
import com.poshith.intent.ui.animationsOn
import com.poshith.intent.ui.BreathView
import com.poshith.intent.ui.Chip
import com.poshith.intent.ui.Flow
import com.poshith.intent.ui.Fonts
import com.poshith.intent.ui.HoldButton
import com.poshith.intent.ui.Kind
import com.poshith.intent.ui.P
import com.poshith.intent.ui.button
import com.poshith.intent.ui.confirmPhrase
import com.poshith.intent.ui.dp
import com.poshith.intent.ui.lp
import com.poshith.intent.ui.scroller
import com.poshith.intent.ui.space
import com.poshith.intent.ui.styleButton
import com.poshith.intent.ui.text
import com.poshith.intent.ui.vbox
import java.util.Date

/**
 * The pause. Shown before a mindful app opens: one slow breath, "How are you feeling?",
 * "What's it for?", how many minutes, and an exit that is always the biggest button.
 * The design follows the one sec study (Grüning et al., PNAS 2023): a short delay plus an easy way
 * out is what cut openings, more than any message.
 */
class GateActivity : BaseActivity() {

    companion object {
        private const val EXTRA_PKG = "pkg"
        private const val EXTRA_KEY = "key"
        private const val EXTRA_LAUNCHER = "from_launcher"

        fun intent(ctx: Context, pkg: String, key: String?, fromLauncher: Boolean): Intent =
            Intent(ctx, GateActivity::class.java)
                .putExtra(EXTRA_PKG, pkg).putExtra(EXTRA_KEY, key).putExtra(EXTRA_LAUNCHER, fromLauncher)

        fun show(a: Activity, pkg: String, key: String?, fromLauncher: Boolean) {
            a.startActivity(intent(a, pkg, key, fromLauncher))
            a.overridePendingTransition(android.R.anim.fade_in, 0)
        }
    }

    private var pkg = ""
    private var key: String? = null
    private var fromLauncher = true
    private var label = ""
    private var leaving = false

    private var minutes = Rule.DEFAULT_SESSION_MIN
    private var purpose: Purpose? = null
    private var feeling: Feeling? = null
    private var pauseDone = false
    private var openBtn: TextView? = null
    private lateinit var bar: LinearLayout
    private var barsListener: (com.poshith.intent.ui.Bars) -> Unit = { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        read(intent)
        build()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        read(intent)
        build()
    }

    private fun read(i: Intent) {
        pkg = i.getStringExtra(EXTRA_PKG) ?: ""
        key = i.getStringExtra(EXTRA_KEY)
        fromLauncher = i.getBooleanExtra(EXTRA_LAUNCHER, true)
        label = Apps.labelFor(this, pkg)
        purpose = null; feeling = null; pauseDone = false; leaving = false
        minutes = Store.rule(pkg).sessionMin
    }

    override fun onStop() {
        super.onStop()
        // Never leave a stale pause screen behind: if you went elsewhere, it's gone.
        if (!leaving && !isFinishing && !isChangingConfigurations) finish()
    }

    override fun onBackRoot() = notNow()

    // ---------------------------------------------------------------------------------- building

    private fun build() {
        host.removeAllViews()
        closeAllLayers()
        if (pkg.isEmpty()) { finish(); return }
        val d = Launch.decide(this, pkg)
        if (d is Decision.Open) { openApp(); return }
        Stats.pauseShown(pkg)

        // Scrolling content on top, the exit pinned at the bottom: "Not now" is always visible.
        val root = vbox()
        val col = vbox(Gravity.CENTER_HORIZONTAL)
        root.addView(scroller(col), lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        bar = vbox()
        root.addView(bar, lp())
        host.addView(root, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        removeBars(barsListener)
        barsListener = { b ->
            col.setPadding(dp(28), b.top + dp(28), dp(28), dp(12))
            bar.setPadding(dp(24) + b.left, dp(8), dp(24) + b.right, b.bottomOrIme + dp(14))
        }
        onBars(barsListener)

        when (d) {
            is Decision.Pause -> if (d.limitReached) buildLimit(col, d) else buildPause(col, d)
            is Decision.Blocked -> buildBlocked(col, d)
            else -> {}
        }
        if (animationsOn()) { root.alpha = 0f; root.animate().alpha(1f).setDuration(220).start() }
    }

    private fun title(col: LinearLayout, small: String, big: String) {
        col.addView(text(small, 14f, P.text3, Fonts.medium(this)).apply { letterSpacing = 0.06f; gravity = Gravity.CENTER })
        col.addView(text(big, 34f, P.text, Fonts.medium(this)).apply { gravity = Gravity.CENTER; setPadding(0, dp(4), 0, 0) })
    }

    private fun line(col: LinearLayout, s: String, color: Int = P.text2, size: Float = 15f, top: Int = 8): TextView {
        val t = text(s, size, color).apply { gravity = Gravity.CENTER; setLineSpacing(0f, 1.2f); setPadding(0, dp(top), 0, 0) }
        col.addView(t, lp())
        return t
    }

    private fun question(col: LinearLayout, q: String) {
        col.addView(text(q, 17f, P.text, Fonts.medium(this)).apply { gravity = Gravity.CENTER; setPadding(0, dp(26), 0, dp(10)) }, lp())
    }

    private fun buildPause(col: LinearLayout, d: Decision.Pause) {
        title(col, "BEFORE YOU OPEN", label)
        val countLine = line(col, sessionsLine(d.opensToday))
        // Today's minutes in this app, if usage access is on (computed off the main thread).
        com.poshith.intent.ui.Bg.run {
            val ms = Usage.today(this)?.foregroundMs?.get(pkg) ?: return@run
            if (ms >= 60_000) runOnUiThread { if (!isDestroyed) countLine.text = sessionsLine(d.opensToday) + " · " + Usage.formatMinutes(ms) + " so far" }
        }

        // One slow breath.
        val breath = BreathView(this)
        col.addView(breath, LinearLayout.LayoutParams(dp(196), dp(196)).apply { topMargin = dp(22) })
        val phase = text("Breathe in", 16f, P.text2).apply { gravity = Gravity.CENTER; setPadding(0, dp(10), 0, 0) }
        col.addView(phase, lp())
        breath.onPhase = { inhale -> phase.text = if (inhale) "Breathe in" else "Breathe out" }
        breath.onDone = { pauseDone = true; phase.text = "Ready when you are"; refreshOpen() }
        breath.start(d.pauseMs)

        // How are you feeling? (affect labeling + Dr. Yousef's "cognitive pause")
        val nudge = text("", 15f, P.text2).apply { gravity = Gravity.CENTER; setLineSpacing(0f, 1.2f); visibility = View.GONE }
        val nudgeAction = vbox(Gravity.CENTER_HORIZONTAL)
        if (Store.askFeeling) {
            question(col, "How are you feeling?")
            val flow = Flow(this, centered = true)
            val chips = ArrayList<Chip>()
            for (f in Feeling.values()) {
                val c = Chip(this, f.label)
                c.setOnClickListener {
                    feeling = f; chips.forEach { x -> x.on = x === c }
                    showNudge(f, nudge, nudgeAction)
                }
                chips += c; flow.addView(c)
            }
            col.addView(flow, lp())
            col.addView(nudge, lp().apply { topMargin = dp(12) })
            col.addView(nudgeAction, lp())
        }

        // What's it for?
        question(col, "What's it for?")
        val pFlow = Flow(this, centered = true)
        val pChips = ArrayList<Chip>()
        for (p in Purpose.values()) {
            val c = Chip(this, p.label)
            c.setOnClickListener { purpose = p; pChips.forEach { x -> x.on = x === c }; refreshOpen() }
            pChips += c; pFlow.addView(c)
        }
        col.addView(pFlow, lp())

        // For how long?
        question(col, "For how long?")
        val mFlow = Flow(this, centered = true)
        val mChips = ArrayList<Chip>()
        val choices = Rule.SESSION_CHOICES.filter { it <= 15 }.toMutableList()
        if (minutes !in choices) choices += minutes
        for (m in choices.sorted()) {
            val c = Chip(this, "$m min")
            c.on = m == minutes
            c.setOnClickListener { minutes = m; mChips.forEach { x -> x.on = x === c }; refreshOpen() }
            mChips += c; mFlow.addView(c)
        }
        col.addView(mFlow, lp())

        val plan = Store.urgePlan
        if (plan.isNotBlank()) line(col, "Your plan: when I feel the pull, I'll $plan.", P.text3, 14f, 24)
        else if (Store.oneThing.isNotBlank()) line(col, "Your one thing today: ${Store.oneThing}", P.text3, 14f, 24)

        bar.addView(button("Not now", Kind.PRIMARY) { notNow() }, lp())
        val open = button("", Kind.OUTLINE) { proceed(minutes, purpose?.label ?: "") }
        openBtn = open
        bar.addView(open, lp().apply { topMargin = dp(8) })
        refreshOpen()
    }

    private fun showNudge(f: Feeling, nudge: TextView, actions: LinearLayout) {
        actions.removeAllViews()
        if (f.nudge.isEmpty()) { nudge.visibility = View.GONE; return }
        nudge.text = f.nudge
        nudge.visibility = View.VISIBLE
        when (f.action) {
            Feeling.Action.CALL -> actions.addView(button("Call someone instead", Kind.QUIET) {
                Stats.notNow(pkg); leaving = true; Launch.dialer(this); finish()
            }, lp().apply { topMargin = dp(10) })
            Feeling.Action.ONE_THING -> if (Store.oneThing.isNotBlank()) {
                actions.addView(text("Your one thing: ${Store.oneThing}", 15f, P.accent, Fonts.medium(this)).apply {
                    gravity = Gravity.CENTER; setPadding(0, dp(8), 0, 0)
                }, lp())
            }
            Feeling.Action.NONE -> {}
        }
    }

    private fun refreshOpen() {
        val b = openBtn ?: return
        val ready = pauseDone && purpose != null
        b.text = when {
            !pauseDone -> "Take a breath first"
            purpose == null -> "Choose what it's for"
            else -> "Open for $minutes min"
        }
        styleButton(b, Kind.OUTLINE, enabled = ready)
    }

    private fun buildLimit(col: LinearLayout, d: Decision.Pause) {
        val r = Store.rule(pkg)
        title(col, "DAILY LIMIT", label)
        line(col, "That's ${r.dailyOpenLimit} sessions today: the limit you set while you were calm.", P.text2, 16f, 14)
        line(col, "Tomorrow it starts fresh.", P.text3, 15f, 6)
        bar.addView(button("Not now", Kind.PRIMARY) { notNow() }, lp())
        bar.addView(button("Unlock 5 minutes", Kind.PLAIN) {
            confirmPhrase("Unlock $label?", "You set a limit for a reason. If you really need it, type the phrase.",
                "open ${label.lowercase()}", "Open for 5 min") { proceed(5, "Past my limit") }
        }, lp().apply { topMargin = dp(6) })
    }

    private fun buildBlocked(col: LinearLayout, d: Decision.Blocked) {
        val until = if (d.untilWallMs > 0) DateFormat.getTimeFormat(this).format(Date(d.untilWallMs)) else ""
        when (d.reason) {
            BlockReason.FOCUS -> {
                title(col, "FOCUS", "$label can wait")
                line(col, "You're in a focus session until $until. Calls and your essentials still work.", P.text2, 16f, 14)
                actions(col, "Back to focus", HoldButton(this, "Hold to end focus", 3000) { Store.focusUntil = 0; build() })
            }
            BlockReason.BREAK -> {
                title(col, "BREAK", "You're on a break")
                line(col, "Until $until. Step away from the screen: a real break is one without a feed.", P.text2, 16f, 14)
                actions(col, "Back to my break", HoldButton(this, "Hold to end the break", 3000) { Store.focusUntil = 0; build() })
            }
            BlockReason.BASIC -> {
                title(col, "BASIC PHONE", "$label is off")
                line(col, "Basic phone is on: only your essentials open, like a phone from before feeds. Calls and messages work as always.", P.text2, 16f, 14)
                actions(col, "Back", HoldButton(this, "Hold to turn basic phone off", 3000) { Store.focusUntil = 0; build() })
            }
            BlockReason.MORNING -> {
                title(col, "MORNING QUIET", "Not yet")
                line(col, "Mindful apps rest until $until. The first minutes of your day are yours, before anyone tells you what to think about.", P.text2, 16f, 14)
                actions(col, "OK, later", HoldButton(this, "Hold to skip this morning", 3000) { Store.morningUntil = 0; build() })
            }
            BlockReason.BATCH -> {
                title(col, "CHECK-IN TIMES", "$label can wait")
                line(col, "It opens at your check-in times. Next one: $until. Batching messages like this lowers stress (Kushlev & Dunn, 2015).", P.text2, 16f, 14)
                actions(col, "OK, later", phraseUnlock())
            }
            BlockReason.WIND_DOWN -> {
                title(col, "WIND-DOWN", "Time to rest")
                line(col, "Mindful apps are paused until $until. Screens before bed push sleep later.", P.text2, 16f, 14)
                actions(col, "Back", phraseUnlock())
            }
            BlockReason.BLOCKED_RULE -> {
                title(col, "BLOCKED", label)
                line(col, "You blocked this app. You can change that in Settings (it takes a short wait), or unlock it once.", P.text2, 16f, 14)
                actions(col, "Back", phraseUnlock())
            }
        }
    }

    private fun phraseUnlock(): View = button("Unlock 5 minutes", Kind.PLAIN) {
        confirmPhrase("Unlock $label?", "This opens it for 5 minutes, then the rule applies again.",
            "open ${label.lowercase()}", "Open for 5 min") { proceed(5, "One-time unlock") }
    }

    private fun actions(@Suppress("UNUSED_PARAMETER") col: LinearLayout, back: String, secondary: View) {
        bar.addView(button(back, Kind.PRIMARY) { notNow() }, lp())
        bar.addView(secondary, lp().apply { topMargin = dp(8) })
    }

    private fun sessionsLine(n: Int): String = when (n) {
        0 -> "First session today"
        else -> "${ordinal(n + 1)} session today"
    }

    private fun ordinal(n: Int): String {
        val suffix = if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
        return "$n$suffix"
    }

    // ---------------------------------------------------------------------------------- actions

    private fun notNow() {
        if (leaving) return
        leaving = true
        Stats.notNow(pkg)
        val n = Stats.notNowToday()
        Store.flash = if (n == 1) "Not now. Nice." else "Not now. That's $n times today."
        if (!fromLauncher) Launch.goHome(this)
        finish()
        overridePendingTransition(0, android.R.anim.fade_out)
    }

    private fun proceed(min: Int, why: String) {
        if (leaving) return
        leaving = true
        val now = SystemClock.elapsedRealtime()
        Store.sessions { it.admit(pkg, min, why, now, System.currentTimeMillis()) }
        Stats.sessionStarted(pkg, min)
        if (!Launch.isGuardOn(this)) Reminder.schedule(this, pkg, min)
        openApp()
    }

    private fun openApp() {
        leaving = true
        if (fromLauncher) {
            val app = key?.let { Apps.byKey(it) } ?: Apps.byPkg(pkg)
            if (app != null) Launch.start(this, app) else Launch.startPackage(this, pkg)
        } else {
            FocusGuardService.sessionStarted(pkg)
        }
        finish()
        overridePendingTransition(0, android.R.anim.fade_out)
    }

}
