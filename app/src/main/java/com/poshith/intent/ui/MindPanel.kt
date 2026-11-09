package com.poshith.intent.ui

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.poshith.intent.core.Evidence
import com.poshith.intent.core.Lesson
import com.poshith.intent.core.Lessons
import com.poshith.intent.core.Mode
import com.poshith.intent.core.Pulls
import com.poshith.intent.core.Topic
import com.poshith.intent.data.Apps
import com.poshith.intent.data.Noticed
import com.poshith.intent.data.Stats
import com.poshith.intent.data.Store
import com.poshith.intent.data.Usage
import java.util.Calendar

/**
 * Practice (swipe right on the home screen): a one-minute reset, riding the urge out, today's idea, your
 * week, what pulls you, and a short library of lessons from research, each with its source and an honest
 * evidence label. A finite page: it ends, on purpose. No progress bar, nothing to keep up with.
 */
class MindPanel(private val a: BaseActivity) {
    private val root = FrameLayout(a)
    private val col = a.vbox()
    private val sc: ScrollView = a.scroller(col)
    var isOpen = false
        private set
    private val closer: () -> Unit = { close() }
    private val bars = BaseActivity.BarLayer(P.dark, P.dark)
    private val barL: (Bars) -> Unit = { b -> sc.setPadding(0, b.top, 0, b.bottom); col.setPadding(b.left, a.dp(28), b.right, a.dp(28)) }

    init {
        root.setBackgroundColor(P.bg)
        root.isClickable = true
        root.addView(sc, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    fun open() {
        if (isOpen) return
        isOpen = true
        build()
        root.animate().cancel()
        (root.parent as? ViewGroup)?.removeView(root)
        a.host.addView(root, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        a.pushBack(closer)
        a.onBars(barL)
        a.pushBarLayer(bars)
        sc.scrollTo(0, 0)
        if (a.animationsOn()) {
            root.translationX = -a.resources.displayMetrics.widthPixels * 0.35f
            root.alpha = 0f
            root.animate().translationX(0f).alpha(1f).setDuration(240).setInterpolator(DecelerateInterpolator(2f)).start()
        }
    }

    fun close() {
        if (!isOpen) return
        isOpen = false
        a.dropBack(closer)
        a.removeBars(barL)
        a.dropBarLayer(bars)
        if (a.animationsOn()) {
            root.animate().translationX(-a.resources.displayMetrics.widthPixels * 0.35f).alpha(0f).setDuration(180)
                .withEndAction { (root.parent as? ViewGroup)?.removeView(root) }.start()
        } else (root.parent as? ViewGroup)?.removeView(root)
    }

    private fun build() {
        col.removeAllViews()
        col.addView(a.text("Practice", 40f, P.text, Fonts.light(a)).apply { setPadding(a.dp(24), 0, a.dp(24), 0) })
        col.addView(a.paragraph("A minute off the screen, and one idea from the research. Then put the phone down."))

        // A one-minute reset, your way (breathing is only one of the options).
        val reset = a.vbox().apply {
            setPadding(a.dp(20), a.dp(18), a.dp(20), a.dp(18))
            isClickable = true
            setOnClickListener { it.tick(); a.resetSheet() }
        }
        reset.addView(a.text("A one-minute reset", 18f, P.text, Fonts.medium(a)))
        reset.addView(a.text("Breathe, look far away, feel your feet, or put the phone down. Intent keeps the time.", 15f, P.text2).apply {
            setLineSpacing(0f, 1.2f); setPadding(0, a.dp(6), 0, 0)
        })
        col.addView(card(reset, tappable = true), lp().apply { leftMargin = a.dp(16); rightMargin = a.dp(16); topMargin = a.dp(14) })

        // For a strong pull: RAIN, guided.
        val ride = a.vbox().apply {
            setPadding(a.dp(20), a.dp(18), a.dp(20), a.dp(18))
            isClickable = true
            setOnClickListener { it.tick(); a.rideSheet(null, fromPause = false) { rebuild() } }
        }
        ride.addView(a.text("Ride the urge", 18f, P.text, Fonts.medium(a)))
        ride.addView(a.text("For a strong pull: notice it, let it be, feel where it sits, be kind. About a minute and a half.", 15f, P.text2).apply {
            setLineSpacing(0f, 1.2f); setPadding(0, a.dp(6), 0, 0)
        })
        col.addView(card(ride, tappable = true), lp().apply { leftMargin = a.dp(16); rightMargin = a.dp(16); topMargin = a.dp(10) })

        // Today's idea, as a small puzzle.
        val today = Lessons.forDay(System.currentTimeMillis() / 86_400_000L)
        col.addView(a.sectionTitle("Today's idea"))
        col.addView(card(lessonCard(today)), lp().apply { leftMargin = a.dp(16); rightMargin = a.dp(16) })

        // This week, from Intent's own counters (and Android's usage data if you allowed it).
        col.addView(a.sectionTitle("Your week"))
        val week = a.vbox().apply { setPadding(a.dp(20), a.dp(16), a.dp(20), a.dp(16)) }
        val days = Stats.lastDays(7)
        val notNow = days.sumOf { it.notNow }
        val sessions = days.sumOf { it.sessions }
        val planned = days.sumOf { it.plannedMin }
        val checking = days.sumOf { it.justChecking }
        val videos = days.sumOf { it.shorts + it.reels }
        week.addView(a.text(if (notNow == 0) "No “Not now” yet this week." else "You chose “Not now” $notNow time${if (notNow == 1) "" else "s"} this week.", 17f, P.text, Fonts.medium(a)))
        week.addView(line("$sessions session${if (sessions == 1) "" else "s"} · $planned min planned" + if (checking > 0) " · noticed “just checking” $checking×" else ""))
        if (videos > 0) week.addView(line("About $videos Shorts and Reels this week (estimated from scrolling)"))
        val usageLine = line("")
        usageLine.visibility = View.GONE
        week.addView(usageLine)
        week.addView(line("New habits often take about two months to feel automatic, and a missed day doesn't undo them.", P.text2))
        col.addView(card(week), lp().apply { leftMargin = a.dp(16); rightMargin = a.dp(16) })
        Bg.run {
            val c = Calendar.getInstance()
            c.add(Calendar.DAY_OF_YEAR, -6); c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0)
            if (!Usage.hasAccess(a)) return@run
            val u = Usage.compute(a, c.timeInMillis, System.currentTimeMillis()) ?: return@run
            val mindful = Store.packagesWithMode(Mode.MINDFUL)
            val perDay = u.msFor(mindful) / 7
            val text = (if (u.unlocksKnown) "About ${u.unlocks / 7} unlocks a day" else "") +
                (if (mindful.isNotEmpty()) (if (u.unlocksKnown) " · " else "") + "${Usage.formatMinutes(perDay)} a day in the apps you want to use less" else "")
            if (text.isNotBlank()) a.runOnUiThread { if (!a.isDestroyed) { usageLine.text = text; usageLine.visibility = View.VISIBLE } }
        }

        // What pulls you: your own answers from the pause, as patterns. Only on this phone.
        col.addView(a.sectionTitle("What pulls you"))
        col.addView(card(pullsCard()), lp().apply { leftMargin = a.dp(16); rightMargin = a.dp(16) })

        // The library: tap any idea to guess and read it. No progress bar: it's there when you want it.
        for (t in Topic.values()) {
            col.addView(a.sectionTitle(t.title))
            col.addView(a.paragraph(t.blurb, 14.5f, P.text2))
            for (l in Lessons.byTopic(t)) col.addView(lessonRow(l), lp())
        }

        col.addView(a.space(24))
        col.addView(a.text("That's everything.\nNow go do your one thing.", 20f, P.text2, Fonts.light(a)).apply {
            gravity = Gravity.CENTER; setLineSpacing(0f, 1.3f); setPadding(a.dp(24), a.dp(24), a.dp(24), a.dp(8))
        }, lp())
        col.addView(a.text("Each idea has its source and how strong the evidence is. These are ideas, not treatment: if your mood is low for weeks, or you can't cut down despite trying, a doctor or therapist can help.", 13.5f, P.text2).apply {
            gravity = Gravity.CENTER; setLineSpacing(0f, 1.25f); setPadding(a.dp(24), a.dp(4), a.dp(24), 0)
        }, lp())
    }

    private fun line(s: String, color: Int = P.text2): TextView = a.text(s, 15f, color).apply {
        setLineSpacing(0f, 1.2f); setPadding(0, a.dp(8), 0, 0)
    }

    /** Rebuilds the page where you were (after a ride, or a plan). */
    private fun rebuild() {
        if (!isOpen) return
        val y = sc.scrollY
        build()
        sc.post { sc.scrollTo(0, y) }
    }

    /**
     * Your pulls over the last 30 days: the most common ones, when they come, how often you let them pass,
     * how riding them out went, and how sessions felt afterwards. Then a plan for your top pull, because
     * a plan for your own reason works better than a general one (Adriaanse et al., 2009).
     */
    private fun pullsCard(): View {
        val v = a.vbox().apply { setPadding(a.dp(20), a.dp(16), a.dp(20), a.dp(16)) }
        val now = System.currentTimeMillis()
        val since = now - com.poshith.intent.core.Pulls.WINDOW_MS
        val notes = Noticed.pulls()
        val top = Pulls.top(notes, since)
        val headline = Pulls.headline(top)
        if (headline == null) {
            v.addView(a.text("Nothing named yet", 17f, P.text, Fonts.medium(a)))
            v.addView(line(if (Store.askPull) "When the pause asks what's pulling you, your answers stay on this phone and show up here as patterns."
                else "Turn on “Ask what's pulling me” in Settings › Mindfulness, and your answers show up here as patterns."))
        } else {
            v.addView(a.text(headline, 17f, P.text, Fonts.medium(a)))
            val most = top.first().n
            for (c in top.take(4)) v.addView(bar(c.pull.label, c.n, most), lp())
            Pulls.peakHour(notes, since) { t -> Calendar.getInstance().apply { timeInMillis = t }.get(Calendar.HOUR_OF_DAY) }?.let { h ->
                v.addView(line("They come most between ${hourLabel(h)} and ${hourLabel((h + 2) % 24)}."))
            }
            val passed = Pulls.letPass(notes, since)
            if (passed.total >= 3) v.addView(line("You let it pass ${passed.passed} of ${passed.total} times."))
        }
        Pulls.rideLine(Pulls.rides(Noticed.rides(), since))?.let { v.addView(line(it)) }
        val afters = Noticed.afters()
        val felt = Pulls.felt(afters, since)
        if (felt.total > 0) v.addView(line("After a session: better ${felt.better} · about the same ${felt.same} · worse ${felt.worse}"))
        Pulls.worstApp(afters, since)?.let { w -> v.addView(line("Worse most often after ${Apps.labelFor(a, w.pkg)} (${w.worse} of ${w.total}).")) }

        // A plan for the pull that comes most.
        top.firstOrNull()?.pull?.let { p ->
            val plan = Noticed.plan(p)
            if (plan.isBlank()) v.addView(a.button("Make a plan for ${p.youWords}", Kind.QUIET) { a.pullPlanSheet(p) { rebuild() } },
                lp().apply { topMargin = a.dp(14) })
            else {
                v.addView(line("Your plan: " + Pulls.planLine(p, plan), P.accent))
                v.addView(a.link("Change it") { a.pullPlanSheet(p) { rebuild() } }.apply {
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL; setPadding(0, 0, a.dp(16), 0)
                }, lp(ViewGroup.LayoutParams.WRAP_CONTENT))
            }
        }
        if (Pulls.lowOften(notes, now) { Store.dayKey(it) }) v.addView(line("If you've felt low on most days for two weeks or more, a doctor or therapist can help. You don't have to sort it out alone.", P.text))
        v.addView(a.text("The last 30 days, kept only on this phone.", 13f, P.text2).apply { setPadding(0, a.dp(12), 0, 0) })
        return v
    }

    /** One pull, with a thin bar for how often (relative to the most common one). */
    private fun bar(label: String, n: Int, most: Int): View {
        val row = a.hbox().apply {
            setPadding(0, a.dp(10), 0, 0)
            contentDescription = "$label, $n ${if (n == 1) "time" else "times"}"
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            isFocusable = true
        }
        row.addView(a.text(label, 15f, P.text).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO },
            lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.46f))
        val track = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; weightSum = most.toFloat() }
        track.addView(View(a).apply { background = rounded(P.accent, a.dp(3).toFloat()) },
            LinearLayout.LayoutParams(0, a.dp(6), n.toFloat()))
        row.addView(track, lp(0, a.dp(6), 0.42f).apply { gravity = Gravity.CENTER_VERTICAL })
        row.addView(a.text("$n", 14f, P.text2).apply { gravity = Gravity.END; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO },
            lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.12f))
        return row
    }

    private fun hourLabel(h: Int): String {
        val c = Calendar.getInstance(); c.set(Calendar.HOUR_OF_DAY, h); c.set(Calendar.MINUTE, 0)
        return android.text.format.DateFormat.getTimeFormat(a).format(c.time)
    }

    private fun card(content: View, tappable: Boolean = false): View = FrameLayout(a).apply {
        val r = a.dp(20).toFloat()
        background = if (tappable) ripple(rounded(P.surface, r), r) else rounded(P.surface, r)
        if (tappable) { content.background = ripple(null, r) }
        addView(content)
    }

    private fun evidenceColor(e: Evidence) = when (e) {
        Evidence.STRONG, Evidence.GOOD -> P.accent
        Evidence.EARLY, Evidence.STORY, Evidence.MIXED -> P.text2
    }

    /**
     * One idea: the guess first (a quick question, two or three answers), then the lesson itself.
     * Once you've guessed, the answer and the lesson stay open the next time you come back.
     */
    private fun lessonCard(l: Lesson): View {
        val v = a.vbox().apply { setPadding(a.dp(20), a.dp(18), a.dp(20), a.dp(18)) }
        val g = com.poshith.intent.core.Guesses.forLesson(l.id)
        val reveal = a.vbox()
        fun fillReveal(chosen: Int?) {
            reveal.removeAllViews()
            if (g != null && chosen != null) {
                val right = chosen == g.answer
                reveal.addView(a.text(if (right) "Right." else "Not quite: ${g.options[g.answer].replaceFirstChar { it.lowercase() }}.",
                    15.5f, if (right) P.accent else P.text2, Fonts.medium(a)).apply { setPadding(0, a.dp(12), 0, a.dp(2)) })
            }
            reveal.addView(a.text(l.title, 19f, P.text, Fonts.medium(a)).apply { setLineSpacing(0f, 1.15f); setPadding(0, a.dp(10), 0, 0) })
            reveal.addView(a.text(l.body, 16f, P.text).apply { setLineSpacing(0f, 1.32f); setPadding(0, a.dp(10), 0, 0) })
            reveal.addView(a.text("TRY THIS", 11.5f, P.text2, Fonts.medium(a)).apply { letterSpacing = 0.1f; setPadding(0, a.dp(16), 0, 0) })
            reveal.addView(a.text(l.tryThis, 16f, P.accent).apply { setLineSpacing(0f, 1.25f); setPadding(0, a.dp(4), 0, 0) })
            val trying = Chip(a, "I'll try it").apply {
                on = l.id in Store.lessonsTrying
                text = if (on) "Trying it" else "I'll try it"
                setOnClickListener {
                    it.tick()
                    val now = l.id !in Store.lessonsTrying
                    Store.lessonsTrying = if (now) Store.lessonsTrying + l.id else Store.lessonsTrying - l.id
                    on = now; text = if (now) "Trying it" else "I'll try it"
                }
            }
            reveal.addView(trying, lp(ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = a.dp(12) })
            reveal.addView(a.text("${l.evidence.label} · ${l.source}", 13f, evidenceColor(l.evidence)).apply {
                setLineSpacing(0f, 1.2f); setPadding(0, a.dp(14), 0, 0)
            })
        }

        if (g == null) {
            fillReveal(null)
            v.addView(reveal, lp())
            Store.lessonsRead = Store.lessonsRead + l.id
            return v
        }

        v.addView(a.text(g.question, 18f, P.text, Fonts.medium(a)).apply { setLineSpacing(0f, 1.2f) })
        val flow = Flow(a).apply { setPadding(0, a.dp(12), 0, 0) }
        val chips = g.options.mapIndexed { i, label -> Chip(a, label).also { flow.addView(it) } to i }
        v.addView(flow, lp())
        v.addView(reveal, lp())

        fun settle(chosen: Int?, animate: Boolean) {
            for ((c, i) in chips) {
                c.isClickable = false
                c.on = i == g.answer
                c.alpha = if (i == g.answer || i == chosen) 1f else 0.6f
            }
            fillReveal(chosen)
            if (animate && a.animationsOn()) {
                reveal.alpha = 0f; reveal.translationY = a.dp(10).toFloat()
                reveal.animate().alpha(1f).translationY(0f).setDuration(380).setInterpolator(DecelerateInterpolator(2f)).start()
            }
        }

        if (l.id in Store.lessonsRead) settle(null, animate = false)
        else for ((c, i) in chips) c.setOnClickListener {
            it.tick()
            Store.lessonsRead = Store.lessonsRead + l.id
            settle(i, animate = true)
        }
        return v
    }

    /** A lesson on its own, as a card that slides up from the library. */
    private fun openLesson(l: Lesson) {
        val s = Sheet(a, null)
        s.content.addView(lessonCard(l), lp())
        s.onDismiss = { if (isOpen) { val y = sc.scrollY; build(); sc.post { sc.scrollTo(0, y) } } }
        s.show()
    }

    private fun lessonRow(l: Lesson): View {
        val read = l.id in Store.lessonsRead
        val head = a.hbox().apply {
            minimumHeight = a.dp(56)
            setPadding(a.dp(24), a.dp(12), a.dp(24), a.dp(12))
            background = ripple(null)
            isClickable = true
            setOnClickListener { it.tick(); openLesson(l) }
        }
        head.addView(a.text(l.title, 16.5f, if (read) P.text2 else P.text).apply { setLineSpacing(0f, 1.15f) }, lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(a.text(if (read) "Read" else l.evidence.label.substringBefore(' '), 12.5f,
            if (read) P.text2 else evidenceColor(l.evidence), Fonts.medium(a)).apply { setPadding(a.dp(12), 0, 0, 0) })
        return head
    }
}
