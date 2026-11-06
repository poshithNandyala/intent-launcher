package com.poshith.intent.ui

import android.view.Gravity
import android.view.View
import com.poshith.intent.data.Store

/*
 * The small daily plan, all optional and all on the phone.
 *
 * Morning: what matters today, the first small step and when, and (if you like) what's likely to get in
 * the way, with an if-then plan for it. That's WOOP (Oettingen): a wish paired with its obstacle and a
 * plan helps more than the wish alone, and an if-then plan helps on its own too (Gollwitzer & Sheeran).
 * Picturing the steps, not the finished result, is what helped students study (Taylor & Pham).
 *
 * Evening, if you turned it on: did it happen, what helped, what pulled you away, and tomorrow's first
 * step. A slip gets a kind word and a next step, never a scold (Breines & Chen: self-compassion led to
 * more effort to improve). No streaks, no scores.
 */

/** "What matters today?": the morning plan. [onDone] runs after Save (or "Not today"). */
fun BaseActivity.planSheet(onDone: () -> Unit) {
    val today = Store.todayIntention() != null
    val s = Sheet(this, "What matters today?", "One thing that would make today a good day. Then the first small step, and when.")
    val thing = field("e.g. Finish the report draft", if (today) Store.oneThing else Store.staleIntention() ?: "")
    s.add(thing)
    s.add(text("First small step, and when", 15f, P.text2, Fonts.medium(this)).apply { setPadding(0, dp(10), 0, 0) })
    val lastNight = if (today) null else Store.stepFromLastNight()
    val step = field("e.g. Open the draft at 9:00", if (today) Store.firstStep else lastNight ?: "")
    s.add(step)
    if (lastNight != null) s.add(text("From last night's look-back.", 13.5f, P.text2))
    s.add(text("Picture yourself doing that step: where you'll be, what you'll open first.", 14f, P.text2).apply {
        setLineSpacing(0f, 1.2f); setPadding(0, dp(4), 0, 0)
    })

    // Optional: the obstacle and the plan for it, folded away until you want it.
    val more = vbox().apply { visibility = if (today && Store.obstacle.isNotBlank()) View.VISIBLE else View.GONE }
    val obstacle = field("e.g. I'll want to check my phone", if (today) Store.obstacle else "")
    val ifThen = field("e.g. …I'll put it in the other room", if (today) Store.ifThen else "")
    more.addView(text("What's likely to get in the way?", 15f, P.text2, Fonts.medium(this)).apply { setPadding(0, dp(6), 0, dp(6)) }, lp())
    more.addView(obstacle, lp())
    more.addView(text("If that happens, I'll…", 15f, P.text2, Fonts.medium(this)).apply { setPadding(0, dp(12), 0, dp(6)) }, lp())
    more.addView(ifThen, lp())
    val open = link("Add what might get in the way") {}
    open.setOnClickListener { it.tick(); more.visibility = View.VISIBLE; open.visibility = View.GONE; obstacle.requestFocus() }
    if (more.visibility == View.VISIBLE) open.visibility = View.GONE
    s.add(open.apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL; setPadding(0, 0, dp(16), 0) })
    s.add(more)

    s.add(button("Save", Kind.PRIMARY) {
        val t = thing.text.toString().trim()
        if (t.isEmpty()) { thing.requestFocus(); return@button }
        hideKeyboard(this, thing)
        Store.setIntention(t, step.text.toString(), obstacle.text.toString(), ifThen.text.toString())
        s.dismiss(); onDone()
    })
    s.add(button(if (today) "Close" else "Not today", Kind.PLAIN) {
        hideKeyboard(this, thing)
        if (!today) Store.planSkippedDay = Store.planDay()
        s.dismiss(); onDone()
    })
    s.show()
    if (!today) showKeyboard(thing)
}

/** Yesterday's one thing, still on the home screen: keep it for today, change it, or clear it. */
fun BaseActivity.rolloverSheet(onDone: () -> Unit) {
    val old = Store.staleIntention() ?: return planSheet(onDone)
    val s = Sheet(this, "Yesterday's one thing", "“$old”")
    s.item("Keep it for today") { Store.keepIntentionForToday(); onDone() }
    s.item("Change it") { planSheet(onDone) }
    s.item("Clear it") { Store.clearIntention(); onDone() }
    s.show()
}

/** Today's one thing, tapped: change it, look back on the day, or clear it. */
fun BaseActivity.intentionSheet(onDone: () -> Unit) {
    val thing = Store.todayIntention() ?: return planSheet(onDone)
    val s = Sheet(this, "Today", "“$thing”")
    Store.todayStep()?.let { s.add(text("First step: $it", 15.5f, P.text2).apply { setLineSpacing(0f, 1.2f) }) }
    if (Store.ifThen.isNotBlank()) s.add(text("If ${Store.obstacle.ifBlank { "it gets hard" }}: ${Store.ifThen}", 15.5f, P.text2).apply { setLineSpacing(0f, 1.2f) })
    s.item("Change it") { planSheet(onDone) }
    s.item("How did today go?", "A short look-back, kept on this phone") { reflectSheet(onDone) }
    s.item("Clear it") { Store.clearIntention(); onDone() }
    s.show()
}

/**
 * The evening look-back: did it happen, what helped, what pulled you away, tomorrow's first step.
 * Everything optional; saved only on this phone (export or delete it in Settings › Your plan).
 */
fun BaseActivity.reflectSheet(onDone: () -> Unit) {
    val thing = Store.todayIntention() ?: ""
    val s = Sheet(this, "How did today go?", if (thing.isNotBlank()) "“$thing”" else null)
    var outcome = ""
    val kind = text("", 15f, P.text2).apply { setLineSpacing(0f, 1.25f); visibility = View.GONE }
    val flow = Flow(this)
    val chips = ArrayList<Chip>()
    for ((key, label) in listOf("yes" to "It happened", "partly" to "Partly", "no" to "Not today")) {
        val c = Chip(this, label)
        c.setOnClickListener {
            it.tick(); outcome = key; chips.forEach { x -> x.on = x === c }
            kind.visibility = if (key == "no") View.VISIBLE else View.GONE
        }
        chips += c; flow.addView(c)
    }
    if (thing.isNotBlank()) s.add(flow)
    kind.text = "Slips are part of changing a habit. What will you do next time the urge shows up?"
    s.add(kind)
    fun ask(q: String, hint: String): android.widget.EditText {
        s.add(text(q, 15f, P.text2, Fonts.medium(this)).apply { setPadding(0, dp(8), 0, 0) })
        return field(hint).also { s.add(it) }
    }
    val helped = ask("What helped?", "e.g. Phone in the other room")
    val pulled = ask("What pulled you away?", "e.g. Reels after lunch")
    val next = ask("Tomorrow's first step", "e.g. Write the intro at 9:00")
    s.add(button("Save", Kind.PRIMARY) {
        hideKeyboard(this, next)
        val h = helped.text.toString().trim(); val p = pulled.text.toString().trim(); val n = next.text.toString().trim()
        if (outcome.isNotEmpty() || h.isNotEmpty() || p.isNotEmpty() || n.isNotEmpty())
            Store.addReflection(Store.Reflection(Store.planDay(), thing, outcome, h, p, n))
        s.dismiss(); onDone()
    })
    s.add(button("Skip", Kind.PLAIN) { hideKeyboard(this, next); s.dismiss(); onDone() })
    s.show()
}

/** The look-backs as plain text, for export (share) or reading. */
fun reflectionsText(): String = buildString {
    append("Intent: my look-backs\n\n")
    for (r in Store.reflections.asReversed()) {
        append(r.day)
        if (r.thing.isNotBlank()) append(" · ").append(r.thing)
        when (r.outcome) { "yes" -> append(" · it happened"); "partly" -> append(" · partly"); "no" -> append(" · not today") }
        append('\n')
        if (r.helped.isNotBlank()) append("Helped: ").append(r.helped).append('\n')
        if (r.pulled.isNotBlank()) append("Pulled away: ").append(r.pulled).append('\n')
        if (r.next.isNotBlank()) append("Next: ").append(r.next).append('\n')
        append('\n')
    }
}

/** True when the home screen should ask "What matters today?". */
fun planDue(): Boolean = Store.planPrompt && Store.todayIntention() == null && Store.staleIntention() == null &&
    Store.planSkippedDay != Store.planDay()

/** True when the home screen should ask "How did today go?" (evening look-back on, late in your day). */
fun lookBackDue(): Boolean = Store.eveningOn && Store.todayIntention() != null && !Store.reflectedToday() &&
    Store.minutesIntoDay() >= 13 * 60
