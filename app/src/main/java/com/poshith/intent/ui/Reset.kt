package com.poshith.intent.ui

import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout

/*
 * A one-minute reset, your way. Breathing is only one of the options: some people find watching the
 * breath makes them more anxious, so there are plain ones too (look far away, feel your feet, put the
 * phone down). Intent keeps the time with a thin line, so there's nothing to watch.
 */

private class ResetWay(val title: String, val how: String, val note: String = "")

private val WAYS = listOf(
    ResetWay("Breathe naturally", "Notice your breath without changing it. In, and out. When your mind wanders, notice where it went and come back."),
    ResetWay("Long sighs", "Two short breaths in through the nose, then one long, slow breath out through the mouth. Repeat, gently. Stop if you feel dizzy.",
        "Five minutes a day of this lifted mood in a month-long study (Balban et al., 2023)."),
    ResetWay("Look far away", "Find the farthest thing you can see, out of a window if you can, and let your eyes rest there."),
    ResetWay("Feel your feet", "Notice the floor under your feet. Then the weight of your body where you sit or stand. Then the air on your hands."),
    ResetWay("Put the phone down", "Face down, out of reach. Come back to it when you like."),
)

/** Pick a way to spend one minute off the screen. */
fun BaseActivity.resetSheet() {
    val s = Sheet(this, "A one-minute reset", "Pick one. Intent keeps the time; you don't need to watch it.")
    for (w in WAYS) s.item(w.title) { runReset(w) }
    s.show()
}

private fun BaseActivity.runReset(w: ResetWay) {
    if (w.title == "Put the phone down" && com.poshith.intent.guard.FocusGuardService.lockScreen()) return
    val s = Sheet(this, w.title, null)
    val col = vbox()
    col.addView(text(w.how, 18f, P.text).apply { setLineSpacing(0f, 1.3f) }, lp())
    val line = Countdown(this, P.accent, 60_000L)
    col.addView(FrameLayout(this).apply { addView(line, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(3))) },
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(3)).apply { topMargin = dp(22) })
    val after = text("That's a minute.", 16f, P.text2, Fonts.medium(this)).apply { visibility = View.INVISIBLE; setPadding(0, dp(14), 0, 0) }
    col.addView(after, lp())
    if (w.note.isNotBlank()) col.addView(text(w.note, 13.5f, P.text2).apply { setLineSpacing(0f, 1.2f); setPadding(0, dp(8), 0, 0) }, lp())
    s.add(col)
    s.add(button("Done", Kind.QUIET) { s.dismiss() })
    val h = Handler(Looper.getMainLooper())
    val end = Runnable { after.visibility = View.VISIBLE; after.gravity = Gravity.START; col.thud() }
    s.onDismiss = { h.removeCallbacks(end) }
    s.show()
    line.post { line.start() }
    h.postDelayed(end, 60_000L)
}
