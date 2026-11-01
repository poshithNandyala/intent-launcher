package com.poshith.intent.core

/**
 * Two limits across all mindful apps together, so switching from one feed to another doesn't start
 * over: one daily budget of minutes, and a short rest after several sessions close together.
 */
object Limits {
    /** This many sessions within [REST_WINDOW_MS]... */
    const val REST_AFTER_SESSIONS = 3
    const val REST_WINDOW_MS = 10 * 60_000L
    /** ...and the next opening starts a rest this long. */
    const val REST_MS = 5 * 60_000L

    val BUDGET_CHOICES = intArrayOf(0, 30, 45, 60, 90, 120)

    /**
     * When the rest ends if one is on, or starts now: 0 if mindful apps can open. [sessionStarts] are
     * wall-clock times; only sessions after the last rest count, so one rest never chains into another.
     */
    fun restUntil(sessionStarts: List<Long>, now: Long, lastRestUntil: Long): Long {
        if (lastRestUntil > now) return lastRestUntil
        val recent = sessionStarts.count { it >= lastRestUntil && now - it in 0 until REST_WINDOW_MS }
        return if (recent >= REST_AFTER_SESSIONS) now + REST_MS else 0L
    }

    /** Minutes left of today's budget, or null when there's no budget. Every session takes the minutes chosen for it. */
    fun budgetLeft(budgetMin: Int, plannedMin: Int): Int? =
        if (budgetMin <= 0) null else (budgetMin - plannedMin).coerceAtLeast(0)

    /** The session lengths to offer: the usual ones that fit in what's left, or just what's left. */
    fun sessionChoices(choices: List<Int>, left: Int?): List<Int> = when {
        left == null -> choices
        left <= 0 -> emptyList()
        else -> choices.filter { it <= left }.ifEmpty { listOf(left) }
    }
}
