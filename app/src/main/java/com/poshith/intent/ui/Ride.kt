package com.poshith.intent.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.poshith.intent.core.Pull
import com.poshith.intent.core.Pulls
import com.poshith.intent.core.UrgeRide
import com.poshith.intent.data.Noticed

/*
 * Ride the urge: RAIN, in about a minute and a half.
 *
 * Recognize, Allow, Investigate, Nurture (Michele McDonald, Tara Brach; a version of it is used in
 * Judson Brewer's programs for cravings). It doesn't try to make the urge go away: smokers who learned
 * to "surf" urges smoked less even though the urges stayed as strong (Bowen & Marlatt, 2009), and
 * mindfulness training worked by loosening the link between craving and acting on it (Elwafi et al.,
 * 2013). So the rating before and after is there to notice, not to pass: "still 4" is a fine result.
 *
 * Each step moves on by itself after a few seconds (a thin line shows how long), or with Next. With a
 * screen reader on, nothing moves on by itself.
 */

enum class RideEnd { LET_PASS, OPEN, DONE, CLOSED }

private class RainStep(val letter: String, val name: String, val words: String, val ms: Long)

private val RAIN = listOf(
    RainStep("R", "Recognize", "Notice the pull. Name it, silently: “wanting”.", 15_000L),
    RainStep("A", "Allow", "Let it be here. You don't have to fight it, and you don't have to follow it.", 15_000L),
    RainStep("I", "Investigate", "Where do you feel it? Chest, throat, hands, belly. Tight, buzzy, restless? Watch it for a few breaths. Is it changing?", 25_000L),
    RainStep("N", "Nurture", "Breathe out slowly. A hand on your chest, if you like. Wanting something isn't a failure. Say something kind to yourself.", 20_000L),
)

private fun Context.screenReaderOn(): Boolean =
    (getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager)?.isTouchExplorationEnabled == true

/**
 * The guided ride. From the pause ([fromPause]) it ends with "Let it pass" or "Open anyway"; from
 * Practice or Settings, with "Done". [onEnd] says how it ended (CLOSED: swiped or backed away).
 */
fun BaseActivity.rideSheet(pull: Pull?, fromPause: Boolean, onEnd: (RideEnd) -> Unit = {}) {
    val s = Sheet(this, "Ride the urge", null)
    val body = vbox()
    s.add(body)
    val h = Handler(Looper.getMainLooper())
    val auto = !screenReaderOn()
    val named = pull?.takeIf { it.isPull }
    var before = 0
    var after = -1 // -1 until the ride is over
    var ended = false

    fun log(outcome: Int) = Noticed.noteRide(UrgeRide(System.currentTimeMillis(), named, before, after.coerceAtLeast(0), outcome))

    fun end(e: RideEnd) {
        if (ended) return
        ended = true
        h.removeCallbacksAndMessages(null)
        log(when (e) { RideEnd.LET_PASS -> UrgeRide.LET_PASS; RideEnd.OPEN -> UrgeRide.OPENED; else -> UrgeRide.PRACTICE })
        s.dismiss()
        onEnd(e)
    }
    s.onDismiss = {
        h.removeCallbacksAndMessages(null)
        if (!ended) {
            ended = true
            // Closed at the end, the ride still happened; closed half-way, it doesn't count.
            if (after >= 0) log(UrgeRide.PRACTICE)
            onEnd(RideEnd.CLOSED)
        }
    }

    fun screen(build: LinearLayout.() -> Unit) {
        if (isFinishing || isDestroyed) { h.removeCallbacksAndMessages(null); return }
        body.removeAllViews()
        body.build()
        if (animationsOn()) { body.alpha = 0f; body.animate().alpha(1f).setDuration(260).start() }
    }

    fun result(a: Int) {
        after = a
        screen {
            addView(text(Pulls.rideWords(before, a), 19f, P.text).apply {
                setLineSpacing(0f, 1.3f); accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            }, lp())
            if (fromPause) {
                addView(button("Let it pass", Kind.PRIMARY) { end(RideEnd.LET_PASS) }, lp().apply { topMargin = dp(20) })
                addView(button("Open anyway", Kind.PLAIN) { end(RideEnd.OPEN) }, lp().apply { topMargin = dp(6) })
            } else {
                addView(text("Every wave you ride without acting on it is one more time the pull didn't decide.", 15f, P.text2).apply {
                    setLineSpacing(0f, 1.25f); setPadding(0, dp(10), 0, 0)
                }, lp())
                addView(button("Done", Kind.QUIET) { end(RideEnd.DONE) }, lp().apply { topMargin = dp(18) })
            }
        }
    }

    fun askAfter() = screen {
        addView(text("How strong is it now?", 19f, P.text, Fonts.medium(this@rideSheet)), lp())
        addView(strengthScale { v -> h.postDelayed({ result(v) }, 250) }, lp().apply { topMargin = dp(16) })
    }

    fun step(i: Int) {
        if (i >= RAIN.size) { if (before > 0) askAfter() else result(0); return }
        val st = RAIN[i]
        val next = Runnable { step(i + 1) }
        screen {
            addView(text("${st.letter} · ${st.name.uppercase()}", 12.5f, P.accent, Fonts.medium(this@rideSheet)).apply { letterSpacing = 0.1f }, lp())
            addView(text(st.words, 20f, P.text).apply {
                setLineSpacing(0f, 1.3f); setPadding(0, dp(10), 0, 0)
                accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            }, lp())
            if (i == 0 && named != null) addView(text("You said: ${named.label.lowercase()}.", 15f, P.text2).apply { setPadding(0, dp(8), 0, 0) }, lp())
            if (auto) {
                val line = Countdown(this@rideSheet, P.accent, st.ms)
                addView(FrameLayout(this@rideSheet).apply {
                    addView(line, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)))
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)).apply { topMargin = dp(22) })
                line.post { line.start() }
            }
            addView(text("${i + 1} of ${RAIN.size}", 13f, P.text2).apply { setPadding(0, dp(10), 0, 0) }, lp())
            addView(button(if (i == RAIN.size - 1) "Done" else "Next", Kind.QUIET) { h.removeCallbacks(next); step(i + 1) },
                lp().apply { topMargin = dp(12) })
        }
        if (auto && !isFinishing) h.postDelayed(next, st.ms)
    }

    screen {
        addView(text("How strong is the pull right now?", 19f, P.text, Fonts.medium(this@rideSheet)), lp())
        addView(text("About a minute and a half. You don't need to make it go away; just watch it.", 15f, P.text2).apply {
            setLineSpacing(0f, 1.25f); setPadding(0, dp(6), 0, 0)
        }, lp())
        addView(strengthScale { v -> before = v; h.postDelayed({ step(0) }, 250) }, lp().apply { topMargin = dp(16) })
        addView(link("Start without rating") { step(0) }.apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL; setPadding(0, 0, dp(16), 0)
        }, lp(ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) })
    }
    s.show()
}

/** How strong, 1 to 5: one tap (a second tap does nothing). */
fun Context.strengthScale(onPick: (Int) -> Unit): View {
    val col = vbox()
    val row = hbox()
    val chips = ArrayList<Chip>()
    for (i in 1..5) {
        val c = Chip(this, "$i")
        c.contentDescription = "$i, ${Pulls.SCALE[i - 1].lowercase()}"
        c.setOnClickListener { v ->
            if (chips.any { it.on }) return@setOnClickListener
            v.tick()
            c.on = true
            onPick(i)
        }
        chips += c
        row.addView(c, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { if (i > 1) leftMargin = dp(8) })
    }
    col.addView(row, lp())
    val ends = hbox()
    ends.addView(text(Pulls.SCALE.first(), 13f, P.text2), lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    ends.addView(text(Pulls.SCALE.last(), 13f, P.text2).apply { gravity = Gravity.END }, lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    col.addView(ends, lp().apply { topMargin = dp(6) })
    return col
}

/**
 * "When I'm bored, I'll…": a plan for one pull, from a few ideas or in your own words. A plan for your
 * own reason tends to work better than a general one (Adriaanse, de Ridder & de Wit, 2009).
 */
fun BaseActivity.pullPlanSheet(p: Pull, onDone: () -> Unit = {}) {
    val cur = Noticed.plan(p)
    val s = Sheet(this, "${p.whenWords}, I'll…", "A plan for your own reason tends to work better than a general one.")
    val flow = Flow(this)
    for (idea in p.ideas) {
        val c = Chip(this, idea)
        c.on = cur == idea
        c.setOnClickListener { it.tick(); Noticed.setPlan(p, idea); s.dismiss(); onDone() }
        flow.addView(c)
    }
    s.add(flow)
    val own = field("Or in your own words", if (cur.isNotBlank() && cur !in p.ideas) cur else "", caps = false)
    s.add(own)
    s.add(button("Save", Kind.PRIMARY) {
        val t = own.text.toString().trim()
        if (t.isEmpty()) { own.requestFocus(); return@button }
        hideKeyboard(this, own)
        Noticed.setPlan(p, t)
        s.dismiss(); onDone()
    })
    if (cur.isNotBlank()) s.add(button("Remove this plan", Kind.PLAIN) { Noticed.setPlan(p, ""); s.dismiss(); onDone() })
    s.show()
}
