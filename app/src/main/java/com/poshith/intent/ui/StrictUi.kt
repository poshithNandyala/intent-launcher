package com.poshith.intent.ui

import android.view.View
import com.poshith.intent.core.Strict
import com.poshith.intent.data.Stats
import com.poshith.intent.data.Store

/**
 * The parts of Strict mode you see: the emergency code and the "rules only get stricter today" sheet;
 * and the line every stop screen ends with: what you planned to do next.
 */

/** The one way past Strict mode today: the code you wrote down when you turned it on. */
fun BaseActivity.askEmergencyCode(what: String, onOk: () -> Unit) {
    val s = Sheet(this, "Emergency code", "The code you wrote down when you turned on Strict mode. $what")
    val f = field("XXXX-XXXX", "", caps = true)
    val err = text("", 14f, P.warn).apply { visibility = View.GONE }
    s.add(f)
    s.add(err)
    s.add(button("Unlock", Kind.PRIMARY) {
        if (Strict.matches(f.text.toString(), Store.strictCodeHash)) {
            hideKeyboard(this, f)
            Stats.emergencyUsed()
            s.dismiss()
            onOk()
        } else {
            err.text = "That's not the code."
            err.visibility = View.VISIBLE
        }
    })
    s.add(button("Keep it closed", Kind.PLAIN) { hideKeyboard(this, f); s.dismiss() })
    s.show()
    showKeyboard(f)
}

/** A small "Emergency code" link for a stop screen (only when there's a code to type). */
fun BaseActivity.emergencyLink(what: String, onOk: () -> Unit): View? {
    if (Store.strictCodeHash.isEmpty()) return null
    return link("Emergency code") { askEmergencyCode(what, onOk) }
}

/** Strict mode: something tried to make a rule looser. Today it can't; tomorrow it can. */
fun BaseActivity.strictBlocksLoosening(onCancel: (() -> Unit)?) {
    val off = Store.strictOffDay.isNotEmpty()
    val s = Sheet(this, "Strict mode is on",
        if (off) "Rules only get stricter today. Strict mode switches off at midnight; after that you can loosen them."
        else "Rules only get stricter today. To loosen anything, turn Strict mode off: it switches off at midnight, so tomorrow's you decides.")
    if (!off) s.add(button("Turn off Strict mode at midnight", Kind.QUIET) { Store.strictOffTomorrow(); s.dismiss() })
    if (Store.strictCodeHash.isNotEmpty()) s.add(button("I have my emergency code", Kind.PLAIN) {
        s.dismiss()
        askEmergencyCode("It turns Strict mode off now.") { Store.strictOn = false; Store.strictOffDay = "" }
    })
    s.add(button("Keep it", Kind.PLAIN) { s.dismiss() })
    s.onDismiss = { onCancel?.invoke() }
    s.show()
}

/**
 * Strict mode, after a stop: when Shorts and Reels open again ("tomorrow", "14:30", "tomorrow at 01:30"),
 * or null while they're open. Once today's number is used that's tomorrow, whatever the lock says.
 */
fun android.content.Context.feedsClosedUntil(now: Long = System.currentTimeMillis()): String? {
    if (!Store.locked(now)) return null
    val eod = Store.endOfDay(now)
    val at = Strict.opensAt(Stats.shortVideosToday() >= Store.reelsAllowToday(now), Store.lockUntil, now, eod)
    if (at <= now) return null
    return Strict.whenWords(at, eod) { android.text.format.DateFormat.getTimeFormat(this).format(java.util.Date(it)) }.removePrefix("at ")
}

/**
 * What to do instead, in your own words: today's first step (or today's one thing), else your plan for
 * the pull. Only today's: yesterday's plan isn't passed off as today's.
 */
fun nextThing(): String? {
    Store.todayStep()?.let { return "Next: $it" }
    Store.todayIntention()?.let { return "Next: $it" }
    val plan = Store.urgePlan
    return if (plan.isNotBlank()) "Next: $plan." else null
}
