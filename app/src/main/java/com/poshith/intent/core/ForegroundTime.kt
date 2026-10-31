package com.poshith.intent.core

/**
 * Minutes each app was in front, rebuilt from Android's usage events (pure Kotlin, unit tested).
 *
 * An app is in front while at least one of its activities is resumed. Moving between two screens of
 * the same app produces "A paused, B resumed, A stopped", so the time is tracked per activity:
 * A's later "stopped" must not end the time B is on screen.
 */
class ForegroundTime(private val windowStart: Long) {
    private val resumed = HashMap<String, MutableSet<String>>()
    private val since = HashMap<String, Long>()
    private val totals = HashMap<String, Long>()
    private val seen = HashSet<String>()

    fun resumed(pkg: String, cls: String, t: Long) {
        seen += pkg
        val set = resumed.getOrPut(pkg) { HashSet() }
        if (set.isEmpty()) since[pkg] = t
        set += cls
    }

    fun paused(pkg: String, cls: String, t: Long) {
        if (pkg !in seen) {
            // Its first event is a pause: it was opened before the window began (left open past
            // midnight), so it counts from the start of the window.
            seen += pkg
            add(pkg, t - windowStart)
            return
        }
        val set = resumed[pkg] ?: return
        if (cls !in set) return
        set -= cls
        if (set.isEmpty()) close(pkg, t)
    }

    /** The screen went off: nothing is in front any more. */
    fun allPaused(t: Long) {
        for ((pkg, set) in resumed) if (set.isNotEmpty()) { set.clear(); close(pkg, t) }
    }

    fun finish(t: Long): Map<String, Long> {
        allPaused(t)
        return totals
    }

    private fun close(pkg: String, t: Long) {
        val s = since.remove(pkg) ?: return
        add(pkg, t - s)
    }

    private fun add(pkg: String, ms: Long) {
        if (ms > 0) totals[pkg] = (totals[pkg] ?: 0L) + ms
    }
}
