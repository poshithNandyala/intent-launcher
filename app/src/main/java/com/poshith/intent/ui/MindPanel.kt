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
    private val barL: (Bars) -> Unit = { b -> col.setPadding(b.left, b.top + a.dp(28), b.right, b.bottom + a.dp(28)) }

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
        if (a.animationsOn()) {
            root.animate().translationX(-a.resources.displayMetrics.widthPixels * 0.35f).alpha(0f).setDuration(180)
                .withEndAction { (root.parent as? ViewGroup)?.removeView(root) }.start()
        } else (root.parent as? ViewGroup)?.removeView(root)
    }

    private fun build() {
        col.removeAllViews()
        col.addView(a.text("Mind", 40f, P.text, Fonts.light(a)).apply { setPadding(a.dp(24), 0, a.dp(24), 0) })
        col.addView(a.paragraph("Short lessons from research, each with its source and an honest label for how strong the evidence is. Read one, then put the phone down."))

        // Today's lesson, open.
        val today = Lessons.forDay(System.currentTimeMillis() / 86_400_000L)
        col.addView(a.sectionTitle("Today"))
        col.addView(card(lessonFull(today)), lp().apply { leftMargin = a.dp(16); rightMargin = a.dp(16) })

        // This week, from Intent's own counters (and Android's usage data if you allowed it).
        col.addView(a.sectionTitle("Your week"))
        val week = a.vbox().apply { setPadding(a.dp(20), a.dp(16), a.dp(20), a.dp(16)) }
        val days = Stats.lastDays(7)
        val notNow = days.sumOf { it.notNow }
        val sessions = days.sumOf { it.sessions }
        val planned = days.sumOf { it.plannedMin }
        val checking = days.sumOf { it.justChecking }
        week.addView(a.text(if (notNow == 0) "No “Not now” yet this week. The first one is the hardest." else "You chose “Not now” $notNow time${if (notNow == 1) "" else "s"} this week.", 17f, P.text, Fonts.medium(a)))
        week.addView(line("$sessions mindful session${if (sessions == 1) "" else "s"} · $planned min planned" + if (checking > 0) " · noticed “just checking” $checking×" else ""))
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

        // The library.
        for (t in Topic.values()) {
            col.addView(a.sectionTitle(t.title))
            col.addView(a.paragraph(t.blurb, 14.5f, P.text3))
            for (l in Lessons.byTopic(t)) col.addView(lessonRow(l), lp())
        }

        col.addView(a.space(24))
        col.addView(a.text("That's everything.\nNow go do your one thing.", 20f, P.text2, Fonts.light(a)).apply {
            gravity = Gravity.CENTER; setLineSpacing(0f, 1.3f); setPadding(a.dp(24), a.dp(24), a.dp(24), a.dp(8))
        }, lp())
        col.addView(a.text("${Lessons.all.size} lessons · sources in each card", 13f, P.text3).apply { gravity = Gravity.CENTER }, lp())
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

    private fun lessonFull(l: Lesson): View {
        val v = a.vbox().apply { setPadding(a.dp(20), a.dp(18), a.dp(20), a.dp(18)) }
        v.addView(a.text(l.title, 19f, P.text, Fonts.medium(a)).apply { setLineSpacing(0f, 1.15f) })
        v.addView(a.text(l.body, 16f, P.text).apply { setLineSpacing(0f, 1.32f); setPadding(0, a.dp(10), 0, 0) })
        v.addView(a.text("Try this: ${l.tryThis}", 15.5f, P.accent).apply { setLineSpacing(0f, 1.25f); setPadding(0, a.dp(12), 0, 0) })
        v.addView(a.text("${l.evidence.label} · ${l.source}", 13f, evidenceColor(l.evidence)).apply {
            alpha = 0.9f; setLineSpacing(0f, 1.2f); setPadding(0, a.dp(12), 0, 0)
        })
        return v
    }

    private fun lessonRow(l: Lesson): View {
        val wrap = a.vbox()
        val head = a.hbox().apply {
            minimumHeight = a.dp(56)
            setPadding(a.dp(24), a.dp(12), a.dp(24), a.dp(12))
            background = ripple(null)
            isClickable = true
        }
        head.addView(a.text(l.title, 16.5f, P.text).apply { setLineSpacing(0f, 1.15f) }, lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(a.text(l.evidence.label.substringBefore(' '), 12.5f, evidenceColor(l.evidence), Fonts.medium(a)).apply {
            setPadding(a.dp(12), 0, 0, 0)
        })
        wrap.addView(head, lp())
        var body: View? = null
        head.setOnClickListener {
            it.tick()
            val b = body
            if (b == null) {
                val full = lessonFull(l)
                val c = card(full)
                wrap.addView(c, lp().apply { leftMargin = a.dp(16); rightMargin = a.dp(16); bottomMargin = a.dp(8) })
                body = c
            } else {
                wrap.removeView(b); body = null
            }
        }
        return wrap
    }
}
