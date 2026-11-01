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
import com.poshith.intent.core.Topic
import com.poshith.intent.data.Stats
import com.poshith.intent.data.Store
import com.poshith.intent.data.Usage
import java.util.Calendar

/**
 * The Mind library (swipe right on the home screen). Short lessons from research, each with its
 * source and an honest evidence label. A finite page you can finish: it ends, on purpose.
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
        col.addView(a.text("Mind", 40f, P.text, Fonts.light(a)).apply { setPadding(a.dp(24), 0, a.dp(24), 0) })
        col.addView(a.paragraph("One idea a day from the research. Guess first, then see what they found. A minute, then put the phone down."))

        // How far along you are, and a way to pick at random.
        val read = Store.lessonsRead.count { id -> Lessons.all.any { it.id == id } }
        val total = Lessons.all.size
        val prog = a.hbox(Gravity.CENTER_VERTICAL).apply { setPadding(a.dp(24), a.dp(6), a.dp(24), a.dp(4)) }
        val bar = a.hbox().apply { background = rounded(P.surface2, a.dp(3).toFloat()); clipToOutline = true }
        if (read > 0) bar.addView(View(a).apply { background = rounded(P.accent, a.dp(3).toFloat()) }, LinearLayout.LayoutParams(0, a.dp(5), read.toFloat()))
        if (read < total) bar.addView(View(a), LinearLayout.LayoutParams(0, a.dp(5), (total - read).toFloat()))
        prog.addView(bar, LinearLayout.LayoutParams(0, a.dp(5), 1f))
        prog.addView(a.text("$read of $total", 13.5f, P.text2, Fonts.medium(a)).apply { setPadding(a.dp(14), 0, 0, 0) })
        col.addView(prog, lp())
        col.addView(Chip(a, "Surprise me").apply {
            setOnClickListener {
                it.tick()
                val unread = Lessons.all.filter { l -> l.id !in Store.lessonsRead }
                openLesson((unread.ifEmpty { Lessons.all }).random())
            }
        }, lp(ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = a.dp(24); topMargin = a.dp(10) })

        // Today's idea, as a small puzzle.
        val today = Lessons.forDay(System.currentTimeMillis() / 86_400_000L)
        col.addView(a.sectionTitle("Today · one minute"))
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
        week.addView(a.text(if (notNow == 0) "No “Not now” yet this week. The first one is the hardest." else "You chose “Not now” $notNow time${if (notNow == 1) "" else "s"} this week.", 17f, P.text, Fonts.medium(a)))
        week.addView(line("$sessions mindful session${if (sessions == 1) "" else "s"} · $planned min planned" + if (checking > 0) " · noticed “just checking” $checking×" else ""))
        if (videos > 0) week.addView(line("$videos short video${if (videos == 1) "" else "s"} swiped to this week"))
        val usageLine = line("")
        usageLine.visibility = View.GONE
        week.addView(usageLine)
        val n = Store.dayNumber()
        week.addView(line("Day $n with Intent. New habits take about two months to feel automatic, so be patient with yourself.", P.text3))
        col.addView(card(week), lp().apply { leftMargin = a.dp(16); rightMargin = a.dp(16) })
        Bg.run {
            val c = Calendar.getInstance()
            c.add(Calendar.DAY_OF_YEAR, -6); c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0)
            if (!Usage.hasAccess(a)) return@run
            val u = Usage.compute(a, c.timeInMillis, System.currentTimeMillis()) ?: return@run
            val mindful = Store.packagesWithMode(Mode.MINDFUL)
            val perDay = u.msFor(mindful) / 7
            val text = (if (u.unlocksKnown) "About ${u.unlocks / 7} unlocks a day" else "") +
                (if (mindful.isNotEmpty()) (if (u.unlocksKnown) " · " else "") + "${Usage.formatMinutes(perDay)} a day in mindful apps" else "")
            if (text.isNotBlank()) a.runOnUiThread { if (!a.isDestroyed) { usageLine.text = text; usageLine.visibility = View.VISIBLE } }
        }

        // The library: tap any idea to guess and read it.
        for (t in Topic.values()) {
            col.addView(a.sectionTitle(t.title))
            col.addView(a.paragraph(t.blurb, 14.5f, P.text3))
            for (l in Lessons.byTopic(t)) col.addView(lessonRow(l), lp())
        }

        col.addView(a.space(24))
        col.addView(a.text("That's everything.\nNow go do your one thing.", 20f, P.text2, Fonts.light(a)).apply {
            gravity = Gravity.CENTER; setLineSpacing(0f, 1.3f); setPadding(a.dp(24), a.dp(24), a.dp(24), a.dp(8))
        }, lp())
        col.addView(a.text("$total ideas · each with its source and how strong the evidence is", 13f, P.text3).apply {
            gravity = Gravity.CENTER; setPadding(a.dp(24), 0, a.dp(24), 0)
        }, lp())
    }

    private fun line(s: String, color: Int = P.text2): TextView = a.text(s, 15f, color).apply {
        setLineSpacing(0f, 1.2f); setPadding(0, a.dp(8), 0, 0)
    }

    private fun card(content: View): View = FrameLayout(a).apply {
        background = rounded(P.surface, a.dp(20).toFloat())
        addView(content)
    }

    private fun evidenceColor(e: Evidence) = when (e) {
        Evidence.STRONG, Evidence.GOOD -> P.accent
        Evidence.EARLY, Evidence.STORY -> P.text2
        Evidence.MIXED -> P.warn
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
            reveal.addView(a.text("TRY THIS", 11.5f, P.text3, Fonts.medium(a)).apply { letterSpacing = 0.1f; setPadding(0, a.dp(16), 0, 0) })
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
                alpha = 0.9f; setLineSpacing(0f, 1.2f); setPadding(0, a.dp(14), 0, 0)
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
                c.alpha = if (i == g.answer || i == chosen) 1f else 0.45f
                if (chosen != null && i == chosen && i != g.answer) c.setTextColor(P.warn)
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

    /** A lesson on its own, as a card that slides up: from the library or "Surprise me". */
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
            if (read) P.text3 else evidenceColor(l.evidence), Fonts.medium(a)).apply { setPadding(a.dp(12), 0, 0, 0) })
        return head
    }
}
