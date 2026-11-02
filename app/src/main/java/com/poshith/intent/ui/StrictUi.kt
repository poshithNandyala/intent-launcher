package com.poshith.intent.ui

import android.view.View
import android.widget.TextView
import com.poshith.intent.core.Feed
import com.poshith.intent.core.Mode
import com.poshith.intent.core.Strict
import com.poshith.intent.data.Stats
import com.poshith.intent.data.Store
import com.poshith.intent.data.Usage

/**
 * The parts of Strict mode you see: the emergency code, the "rules only get stricter today" sheet, and
 * the lines every stop screen shares (what to do next, who you said you are, what the scrolling costs).
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
    return text("Emergency code", 13.5f, P.text3).apply {
        gravity = android.view.Gravity.CENTER
        minHeight = dp(44)
        setPadding(dp(16), 0, dp(16), 0)
        background = ripple(null, dp(20).toFloat())
        isClickable = true
        setOnClickListener { it.tick(); askEmergencyCode(what, onOk) }
    }
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

/** What to do instead, in your own words: today's one thing, or your plan for the pull. */
fun nextThing(): String? {
    val one = Store.oneThing
    if (one.isNotBlank() && Store.oneThingDay == Store.dayKey()) return "Next: $one"
    val plan = Store.urgePlan
    return if (plan.isNotBlank()) "Next: $plan." else null
}

/** Who you said you're becoming. */
fun identityLine(): String? = Store.identity.takeIf { it.isNotBlank() }?.let { "You're someone who $it." }

/**
 * What the scrolling costs, in your own numbers (needs usage access): "1 h 52 min in pull apps today.
 * A year of days like this: 43 waking days." Filled in off the main thread; stays hidden otherwise.
 */
fun BaseActivity.fillCost(into: TextView) {
    into.visibility = View.GONE
    Bg.run {
        val u = Usage.today(this) ?: return@run
        val pull = Store.packagesWithMode(Mode.MINDFUL) + Feed.values().flatMap { it.packages }
        val ms = u.msFor(pull)
        val minutes = (ms / 60_000L).toInt()
        if (minutes < 10) return@run
        val line = "${Usage.formatMinutes(ms)} in pull apps today. A year of days like this: ${Strict.wakingDaysPerYear(minutes)} waking days."
        runOnUiThread { if (!isDestroyed) { into.text = line; into.visibility = View.VISIBLE } }
    }
}
