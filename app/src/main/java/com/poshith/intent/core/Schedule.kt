package com.poshith.intent.core

/**
 * Is a change to a daily schedule stricter or looser? Every editor (wind-down, check-in times) asks
 * here, so a looser schedule always waits like any other loosening (and Strict mode keeps it for
 * tomorrow), while a stricter one applies at once.
 *
 * Schedules are compared minute by minute over a whole day, so windows that cross midnight
 * (22:30 to 06:30) and empty ones (start == end: nothing is covered) need no special cases.
 */
object Schedule {
    const val DAY_MIN = 24 * 60

    /** The minutes of the day that any of [windows] covers. */
    fun minutes(windows: List<Window>): BooleanArray {
        val a = BooleanArray(DAY_MIN)
        for (w in windows) for (m in 0 until DAY_MIN) if (w.contains(m)) a[m] = true
        return a
    }

    /** True if every minute covered by [inner] is also covered by [outer]. */
    fun covers(outer: List<Window>, inner: List<Window>): Boolean {
        val o = minutes(outer)
        val i = minutes(inner)
        for (m in 0 until DAY_MIN) if (i[m] && !o[m]) return false
        return true
    }

    /**
     * Wind-down pauses apps inside its window: a change is looser if any minute it covered isn't covered
     * any more (a later start, an earlier end, start == end, or turning it off: [new] = null).
     */
    fun windDownLooser(old: Window?, new: Window?): Boolean = !covers(listOfNotNull(new), listOfNotNull(old))

    /**
     * Check-in apps open only inside their windows: a change is looser if they'd open at any minute
     * they couldn't before (more windows, longer ones, or moved ones).
     */
    fun checkInsLooser(old: List<Window>, new: List<Window>): Boolean = !covers(old, new)

    /** The check-in windows for these start times and length. */
    fun checkIns(starts: List<Int>, lengthMin: Int): List<Window> = starts.map { Window(it, (it + lengthMin) % DAY_MIN) }
}
