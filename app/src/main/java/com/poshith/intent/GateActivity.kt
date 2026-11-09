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
import com.poshith.intent.core.Limits
import com.poshith.intent.core.Mode
import com.poshith.intent.core.Pull
import com.poshith.intent.core.Pulls
import com.poshith.intent.core.Purpose
import com.poshith.intent.core.Rule
import com.poshith.intent.core.SessionBook
import com.poshith.intent.data.Apps
import com.poshith.intent.data.Noticed
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
import com.poshith.intent.ui.nextThing
import com.poshith.intent.ui.emergencyLink
import com.poshith.intent.ui.Kind
import com.poshith.intent.ui.RideEnd
import com.poshith.intent.ui.link
import com.poshith.intent.ui.longSighs
import com.poshith.intent.ui.rideSheet
import com.poshith.intent.ui.P
import com.poshith.intent.ui.button
import com.poshith.intent.ui.confirmPhrase
import com.poshith.intent.ui.dp
import com.poshith.intent.ui.lp
import com.poshith.intent.ui.scroller
import com.poshith.intent.ui.space
import com.poshith.intent.ui.styleButton
import com.poshith.intent.ui.text
import com.poshith.intent.ui.tick
import com.poshith.intent.ui.vbox
import java.util.Date

/**
 * The pause. Shown before a mindful app opens: one slow breath, then how many minutes, and an exit
 * that is always the biggest button. "What's it for?" comes only when it matters (the 4th open today,
 * or straight back after a session).
 * The design follows the one sec study (Grüning et al., PNAS 2023): a short delay plus an easy way
 * out is what cut openings, more than any message.
 *
 * While you breathe: "What's pulling you?" (core/Pulls). Naming it is optional and never needed to
 * open; the answer gets a kind line, your own plan for that pull, and one idea that meets the actual
 * need (a call when lonely, long sighs when stressed, riding the urge out when bored). If an app has
 * mostly left you feeling worse lately, the pause says so, in your own answers.
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
    private var askWhy = false
    private var scroll: android.widget.ScrollView? = null
    /** What's pulling you, if you named it; written down once, when you leave the pause. */
    private var pull: Pull? = null
    private var pullLogged = false
    private var planLine: TextView? = null
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
        logPull(opened = false)
        setIntent(intent)
        read(intent)
        build()
    }

    private fun read(i: Intent) {
        pkg = i.getStringExtra(EXTRA_PKG) ?: ""
        key = i.getStringExtra(EXTRA_KEY)
        fromLauncher = i.getBooleanExtra(EXTRA_LAUNCHER, true)
        label = Apps.labelFor(this, pkg)
        purpose = null; pull = null; pullLogged = false; planLine = null; pauseDone = false; leaving = false; askWhy = false
        minutes = Store.rule(pkg).sessionMin
    }

    override fun onStop() {
        super.onStop()
        // Never leave a stale pause screen behind: if you went elsewhere, it's gone (and nothing opened).
        if (!leaving && !isFinishing && !isChangingConfigurations) { logPull(opened = false); finish() }
    }

    /** Writes down what pulled you, once, with whether you went on to open the app. */
    private fun logPull(opened: Boolean) {
        val p = pull ?: return
        if (pullLogged || pkg.isEmpty()) return
        pullLogged = true
        Noticed.notePull(p, pkg, opened)
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
        val sc = scroller(col)
        scroll = sc
        root.addView(sc, lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        bar = vbox()
        root.addView(bar, lp())
        host.addView(root, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        removeBars(barsListener)
        barsListener = { b ->
            sc.setPadding(0, b.top, 0, 0)
            col.setPadding(dp(28), dp(28), dp(28), dp(12))
            bar.setPadding(dp(24) + b.left, dp(8), dp(24) + b.right, b.bottomOrIme + dp(14))
        }
        onBars(barsListener)

        when (d) {
            is Decision.Pause -> {
                // Limits across all mindful apps come first: switching feeds doesn't start over.
                val now = System.currentTimeMillis()
                val rest = if (Store.restOn) Limits.restUntil(Store.sessionStarts, now, Store.restUntil) else 0L
                if (rest > Store.restUntil) {
                    Store.restUntil = rest
                    // A rest is for all of them: sessions still running in other mindful apps end too,
                    // or going back to one of those would skip the rest.
                    for (p in Store.packagesWithMode(Mode.MINDFUL)) Store.sessions { it.close(p) }
                }
                when {
                    budgetLeft() == 0 -> buildBudget(col)
                    d.limitReached -> buildLimit(col, d)
                    rest > now -> buildRest(col, rest)
                    else -> buildPause(col, d)
                }
            }
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
        // How it felt afterwards, in your own answers, when it was mostly worse: the real reward, remembered
        // at the moment the app promises another one.
        if (Store.askAfter) Pulls.afterLine(Noticed.afters(), pkg, System.currentTimeMillis())?.let { line(col, it, P.text2, 14.5f, 4) }

        // One slow breath.
        val breath = BreathView(this)
        col.addView(breath, LinearLayout.LayoutParams(dp(196), dp(196)).apply { topMargin = dp(22) })
        val phase = text("", 16f, P.text2).apply { gravity = Gravity.CENTER; setPadding(0, dp(10), 0, 0) }
        col.addView(phase, lp())
        breath.onPhase = { p -> phase.text = p.words }
        breath.start(d.pauseMs)

        // What's pulling you? Naming it is where the habit loop can break (affect labeling); optional, and
        // never needed to open.
        val reply = text("", 15.5f, P.text).apply {
            gravity = Gravity.CENTER; setLineSpacing(0f, 1.25f); visibility = View.GONE
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        val replyActions = vbox(Gravity.CENTER_HORIZONTAL)
        if (Store.askPull) {
            question(col, "What's pulling you?")
            val flow = Flow(this, centered = true)
            val chips = ArrayList<Chip>()
            for (p in Pull.values()) {
                val c = Chip(this, p.label)
                c.setOnClickListener {
                    it.tick()
                    pull = p; chips.forEach { x -> x.on = x === c }
                    showReply(p, reply, replyActions)
                }
                chips += c; flow.addView(c)
            }
            col.addView(flow, lp())
            col.addView(reply, lp().apply { topMargin = dp(16) })
            col.addView(replyActions, lp())
        }

        // After the breath: just how long. "What's it for?" only when it matters: the 4th time today
        // or more, or straight back after a session ended. Every other time it's one tap to open.
        askWhy = d.opensToday >= 3 || Store.endedRecently(pkg)
        val after = vbox(Gravity.CENTER_HORIZONTAL).apply { visibility = View.GONE }
        col.addView(after, lp())
        if (askWhy) {
            question(after, if (d.opensToday >= 3) "${ordinal(d.opensToday + 1)} time today. What's it for?" else "You just closed it. What's it for?")
            val pFlow = Flow(this, centered = true)
            val pChips = ArrayList<Chip>()
            for (p in Purpose.values()) {
                val c = Chip(this, p.label)
                c.setOnClickListener { purpose = p; pChips.forEach { x -> x.on = x === c }; refreshOpen() }
                pChips += c; pFlow.addView(c)
            }
            after.addView(pFlow, lp())
        }

        // For how long? Chosen right on the chips, no extra question.
        val mFlow = Flow(this, centered = true)
        val mChips = ArrayList<Chip>()
        val usual = Rule.SESSION_CHOICES.filter { it <= 15 }.toMutableList()
        if (minutes !in usual) usual += minutes
        // With a daily budget, only the lengths that still fit (and it says how much is left).
        val left = budgetLeft()
        val choices = Limits.sessionChoices(usual.sorted(), left)
        if (minutes !in choices) minutes = choices.lastOrNull { it <= minutes } ?: choices.first()
        for (m in choices) {
            val c = Chip(this, "$m min")
            c.on = m == minutes
            c.setOnClickListener { minutes = m; mChips.forEach { x -> x.on = x === c }; refreshOpen() }
            mChips += c; mFlow.addView(c)
        }
        after.addView(mFlow, lp().apply { topMargin = dp(if (askWhy) 18 else 26) })
        if (left != null) line(after, "$left of ${Store.budgetMin} min left today, for all the apps you want to use less", P.text2, 14f, 12)

        planLine = line(after, "", P.text2, 14f, 22)
        updatePlanLine()

        bar.addView(button("Not now", Kind.PRIMARY) { notNow() }, lp())
        val open = button("", Kind.OUTLINE) { proceed(minutes, purpose?.label ?: "") }.apply { visibility = View.GONE }
        openBtn = open
        bar.addView(open, lp().apply { topMargin = dp(8) })
        refreshOpen()

        breath.onDone = {
            pauseDone = true
            breath.onPhase = null
            phase.text = "Ready when you are"
            reveal(after)
            reveal(open)
            refreshOpen()
            // Show what comes next. If you're reading the answer to what's pulling you, it stays where it is,
            // unless opening needs "What's it for?": then just enough of that comes into view at the bottom.
            after.post {
                val sc = scroll ?: return@post
                if (pull == null) sc.smoothScrollTo(0, after.top - dp(24))
                else if (askWhy) (after.top + dp(140) - sc.height).let { want -> if (want > sc.scrollY) sc.smoothScrollTo(0, want) }
            }
        }
    }

    /** Fades a part of the pause screen in, rising a little: calm, never a jump. */
    private fun reveal(v: View) {
        v.visibility = View.VISIBLE
        if (!animationsOn()) return
        v.alpha = 0f
        v.translationY = dp(14).toFloat()
        v.animate().alpha(1f).translationY(0f).setDuration(420).setInterpolator(android.view.animation.DecelerateInterpolator(2f)).start()
    }

    /**
     * The answer to what's pulling you: one kind line, your own plan for that pull if you made one, and
     * one idea that meets the actual need. For a feeling, riding the urge out is always offered too.
     */
    private fun showReply(p: Pull, reply: TextView, actions: LinearLayout) {
        reply.text = p.reply
        reply.visibility = View.VISIBLE
        actions.removeAllViews()
        fun gap() = lp().apply { topMargin = dp(12) }
        val plan = Noticed.plan(p).takeIf { p.isPull && it.isNotBlank() }
        if (plan != null) actions.addView(text("Your plan: " + Pulls.planLine(p, plan), 15f, P.accent, Fonts.medium(this)).apply {
            gravity = Gravity.CENTER; setLineSpacing(0f, 1.2f); setPadding(0, dp(10), 0, 0)
        }, lp())
        var offersRide = false
        when (p.action) {
            Pull.Action.RIDE -> { actions.addView(button("Ride it out · 90 s", Kind.QUIET) { ride() }, gap()); offersRide = true }
            Pull.Action.SIGHS -> actions.addView(button("A minute of long sighs", Kind.QUIET) { longSighs() }, gap())
            Pull.Action.CALL -> actions.addView(button("Call someone instead", Kind.QUIET) {
                if (leaving) return@button
                leaving = true
                logPull(opened = false)
                Stats.notNow(pkg)
                Launch.dialer(this); finish()
            }, gap())
            Pull.Action.REST -> actions.addView(button("Put the phone down", Kind.QUIET) {
                notNow("Not now. Rest your eyes.")
                // Locks the screen when Focus Guard can; otherwise it's simply back home.
                FocusGuardService.lockScreen()
            }, gap())
            Pull.Action.ONE_THING -> {
                val one = Store.todayIntention()
                if (one != null) {
                    actions.addView(text("Your one thing: $one", 15f, P.accent, Fonts.medium(this)).apply {
                        gravity = Gravity.CENTER; setLineSpacing(0f, 1.2f); setPadding(0, dp(10), 0, 0)
                    }, lp())
                    val start = Store.todayStep() ?: one
                    actions.addView(button("Do two minutes of it", Kind.QUIET) { notNow("Two minutes of: $start. Go.") }, gap())
                } else { actions.addView(button("Ride it out · 90 s", Kind.QUIET) { ride() }, gap()); offersRide = true }
            }
            Pull.Action.NONE -> {}
        }
        if (p.feeling && !offersRide) actions.addView(link("Or ride the urge out, 90 s") { ride() }, lp(ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(4)
        })
        updatePlanLine()
        // Keep the answer in view: scroll just enough to show it, and nothing jumps away from it later.
        actions.post {
            val sc = scroll ?: return@post
            val bottom = actions.bottom + dp(12)
            if (bottom > sc.scrollY + sc.height) sc.smoothScrollTo(0, bottom - sc.height)
        }
    }

    /** RAIN, from the pause: "Let it pass" leaves; "Open anyway" (or closing it) comes back here. */
    private fun ride() = rideSheet(pull, fromPause = true) { end ->
        if (end == RideEnd.LET_PASS) notNow("Not now. You rode it out.")
    }

    /** Your general plan for the pull, after the breath, unless the pull you named has its own (shown above). */
    private fun updatePlanLine() {
        val t = planLine ?: return
        val own = pull?.let { p -> p.isPull && Noticed.plan(p).isNotBlank() } == true
        val plan = Store.urgePlan
        t.text = "Your plan: when I feel the pull, I'll $plan."
        t.visibility = if (plan.isBlank() || own) View.GONE else View.VISIBLE
    }

    private fun refreshOpen() {
        val b = openBtn ?: return
        val ready = pauseDone && (!askWhy || purpose != null)
        b.text = when {
            !pauseDone -> "Take a breath first"
            askWhy && purpose == null -> "Choose what it's for"
            else -> "Open for $minutes min"
        }
        styleButton(b, Kind.OUTLINE, enabled = ready)
    }

    /** The end of every stop screen: what you planned to do next (today's one thing, or your plan). */
    private fun stopLines(col: LinearLayout) {
        nextThing()?.let { line(col, it, P.text, 16f, 22) }
    }

    /** The way past a stop: in Strict mode only the emergency code; otherwise the given [gentle] one. */
    private fun escape(gentle: View?, what: String = "It opens $label for 10 minutes.", onCode: () -> Unit = { proceed(10, SessionBook.WHY_CODE) }): View? =
        if (Store.strictActive()) emergencyLink(what, onCode) else gentle

    /** Minutes left of today's budget for all mindful apps, or null when there's none. */
    private fun budgetLeft(): Int? = Limits.budgetLeft(Store.budgetMin, Stats.day().plannedMin)

    private fun buildBudget(col: LinearLayout) {
        title(col, label.uppercase(), "Today's allowance is finished")
        line(col, "Available tomorrow. Calls and your essential apps still work.", P.text2, 16f, 14)
        stopLines(col)
        bar.addView(button("Not now", Kind.PRIMARY) { notNow() }, lp())
        escape(button("Unlock 5 minutes", Kind.PLAIN) {
            confirmPhrase("Unlock $label?", "You set this budget earlier, on purpose. If you really need it, type the phrase.",
                "open ${label.lowercase()}", "Open for 5 min") { proceed(5, SessionBook.WHY_BUDGET) }
        })?.let { bar.addView(it, lp().apply { topMargin = dp(6) }) }
    }

    /** Several sessions close together: these apps rest for a few minutes. */
    private fun buildRest(col: LinearLayout, until: Long) {
        val at = DateFormat.getTimeFormat(this).format(Date(until))
        title(col, "A SHORT REST", "Back at $at")
        line(col, "${Limits.REST_AFTER_SESSIONS} sessions in ${Limits.REST_WINDOW_MS / 60_000} minutes, so the apps you want to use less rest for a few minutes.", P.text2, 16f, 14)
        stopLines(col)
        bar.addView(button("Put the phone down", Kind.PRIMARY) {
            notNow()
            // Locks the screen when Focus Guard can; otherwise it's simply back home.
            FocusGuardService.lockScreen()
        }, lp())
        escape(HoldButton(this, "Hold to skip the rest", 5000) { Store.restUntil = 0; Store.clearSessionStarts(); build() },
            "It ends the rest now.") { Store.restUntil = 0; Store.clearSessionStarts(); build() }
            ?.let { bar.addView(it, lp().apply { topMargin = dp(8) }) }
    }

    private fun buildLimit(col: LinearLayout, @Suppress("UNUSED_PARAMETER") d: Decision.Pause) {
        val r = Store.rule(pkg)
        title(col, label.uppercase(), "That's today's ${r.dailyOpenLimit} sessions")
        line(col, "You chose this limit earlier. Available tomorrow.", P.text2, 16f, 14)
        stopLines(col)
        bar.addView(button("Not now", Kind.PRIMARY) { notNow() }, lp())
        escape(button("Unlock 5 minutes", Kind.PLAIN) {
            confirmPhrase("Unlock $label?", "You set a limit for a reason. If you really need it, type the phrase.",
                "open ${label.lowercase()}", "Open for 5 min") { proceed(5, SessionBook.WHY_LIMIT) }
        })?.let { bar.addView(it, lp().apply { topMargin = dp(6) }) }
    }

    private fun buildBlocked(col: LinearLayout, d: Decision.Blocked) {
        val until = if (d.untilWallMs > 0) DateFormat.getTimeFormat(this).format(Date(d.untilWallMs)) else ""
        when (d.reason) {
            BlockReason.FOCUS -> {
                title(col, "FOCUS", "$label can wait")
                line(col, "Focus until $until. Calls and your essentials still work.", P.text2, 16f, 14)
                actions(col, "Back to focus", HoldButton(this, "Hold to end focus", 3000) { Store.focusUntil = 0; build() })
            }
            BlockReason.BREAK -> {
                title(col, "REAL BREAK", "Not now")
                line(col, (if (until.isNotEmpty()) "You're on a break until $until. " else "You're on a break. ") + "Only calls get through; everything else waits.", P.text2, 16f, 14)
                actions(col, "Back to my break", HoldButton(this, "Hold to end the break", 6000) { com.poshith.intent.guard.Break.end(this); build() })
            }
            BlockReason.BASIC -> {
                title(col, "BASIC PHONE", "$label is off")
                line(col, "Basic phone is on: only your essentials open. Calls and messages work as always.", P.text2, 16f, 14)
                actions(col, "Back", HoldButton(this, "Hold to turn basic phone off", 3000) { Store.focusUntil = 0; build() })
            }
            BlockReason.MORNING -> {
                title(col, "MORNING QUIET", "Not yet")
                line(col, "$label opens at $until. The first minutes of your day are yours.", P.text2, 16f, 14)
                stopLines(col)
                actions(col, "OK, later", escape(HoldButton(this, "Hold to skip this morning", 3000) { Store.morningUntil = 0; build() },
                    "It ends morning quiet for today.") { Store.morningUntil = 0; build() })
            }
            BlockReason.BATCH -> {
                title(col, "CHECK-IN TIMES", "$label can wait")
                line(col, "It opens at your next check-in, $until.", P.text2, 16f, 14)
                stopLines(col)
                actions(col, "OK, later", escape(phraseUnlock()))
            }
            BlockReason.WIND_DOWN -> {
                title(col, "WIND-DOWN", "Time to rest")
                line(col, "$label opens at $until. Calls, alarms and your essentials still work.", P.text2, 16f, 14)
                stopLines(col)
                actions(col, "Back", escape(phraseUnlock()))
            }
            BlockReason.BLOCKED_RULE -> {
                title(col, "BLOCKED", label)
                line(col, "You blocked this app. You can change that in Settings, or unlock it once.", P.text2, 16f, 14)
                actions(col, "Back", escape(phraseUnlock()))
            }
        }
    }

    private fun phraseUnlock(): View = button("Unlock 5 minutes", Kind.PLAIN) {
        confirmPhrase("Unlock $label?", "This opens it for 5 minutes, then the rule applies again.",
            "open ${label.lowercase()}", "Open for 5 min") { proceed(5, SessionBook.WHY_UNLOCK) }
    }

    private fun actions(@Suppress("UNUSED_PARAMETER") col: LinearLayout, back: String, secondary: View?) {
        bar.addView(button(back, Kind.PRIMARY) { notNow() }, lp())
        if (secondary != null) bar.addView(secondary, lp().apply { topMargin = dp(8) })
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

    /** Leave without opening. [said]: the line the home screen shows (otherwise your plan, or a count). */
    private fun notNow(said: String? = null) {
        if (leaving) return
        leaving = true
        logPull(opened = false)
        Stats.notNow(pkg)
        FocusGuardService.saidNotNow(pkg)
        Store.sessions { it.close(pkg) }
        val n = Stats.notNowToday()
        // Leaving is the easy choice, and the next thing to do is the one you picked yourself: your plan
        // for the pull you named, or your plan for any pull.
        val plan = pull?.let { p -> Noticed.plan(p).takeIf { p.isPull && it.isNotBlank() } } ?: Store.urgePlan
        Store.flash = when {
            said != null -> said
            plan.isNotBlank() -> "Not now. Your plan: $plan."
            n == 1 -> "Not now. Nice."
            else -> "Not now. That's $n times today."
        }
        if (!fromLauncher) { com.poshith.intent.guard.Closer.toHome(this); com.poshith.intent.guard.Closer.silence(this, pkg) }
        finish()
        overridePendingTransition(0, android.R.anim.fade_out)
    }

    private fun proceed(min: Int, why: String) {
        if (leaving) return
        leaving = true
        val now = SystemClock.elapsedRealtime()
        // With Focus Guard running, minutes count only while the app is in front. Without it nothing can
        // tell, so the minutes run on the clock from now and a reminder says when they're up. "Running",
        // not "switched on": some phones stop the service and leave its switch on, and then only the
        // reminder is left to end the session. (If Focus Guard comes back, it takes over and cancels it.)
        val guard = FocusGuardService.running
        // Minutes start now. Focus Guard pauses them while you're away from the app and resumes them
        // when you come back; if it ever misses that, the clock still runs, so a session always ends.
        Store.sessions { it.admit(pkg, min, why, now, System.currentTimeMillis(), runningNow = true) }
        // Only mindful sessions count toward the mindful budget and the short rest; a one-time unlock
        // of a blocked or check-in app is its own thing.
        val mindful = Store.rule(pkg).mode == Mode.MINDFUL
        Stats.sessionStarted(pkg, min, mindful)
        if (mindful) Store.noteSessionStart(System.currentTimeMillis())
        logPull(opened = true)
        // Back home after it: "How do you feel?" (once, and only if you spent a minute or more in it).
        if (mindful && Store.askAfter) Noticed.expectAfter(pkg)
        if (!guard) Reminder.schedule(this, pkg, min)
        openApp()
    }

    private fun openApp() {
        leaving = true
        if (fromLauncher) {
            val app = key?.let { Apps.byKey(it) } ?: Apps.byPkg(pkg)
            if (app != null) Launch.start(this, app) else Launch.startPackage(this, pkg)
        }
        // Either way, Focus Guard starts timing it (even if the app's own window event goes missing).
        FocusGuardService.sessionStarted(pkg)
        finish()
        overridePendingTransition(0, android.R.anim.fade_out)
    }

}
