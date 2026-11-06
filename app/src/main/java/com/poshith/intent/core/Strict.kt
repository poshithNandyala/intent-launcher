package com.poshith.intent.core

import java.security.MessageDigest
import java.util.Random
import kotlin.math.roundToInt

/**
 * Strict mode: limits decided while calm, held while craving.
 *
 * Why: a pause with a "few more" button teaches a new loop (pause, wait, more). A choice made while
 * craving almost always goes to the craving (the hot-cold empathy gap), so in Strict mode the choice
 * is made earlier, when calm, and the moment of craving has no button to press:
 *  - No extensions in the moment. When today's Shorts and Reels (or minutes) are used, they're used.
 *  - Looser only from tomorrow. Stricter applies now; looser waits for the next day.
 *  - Going back to Shorts or Reels after being stopped locks them (only them: the rest of YouTube and
 *    Instagram keeps working), longer each time: 30 min, 2 h, the day.
 *  - The one way past it today is an emergency code, shown once, kept somewhere out of reach.
 */
object Strict {
    const val LOCK_SECOND_MS = 30 * 60_000L
    const val LOCK_THIRD_MS = 2 * 60 * 60_000L

    /** Shorts and Reels a day to choose from (both together). */
    val SHORTS_CHOICES = intArrayOf(0, 3, 5, 10, 20)

    /**
     * After the [nth] stop today (1 = the first), the pull apps are locked until this time, or 0 for no
     * lock. The first stop only closes the app; coming back gets 30 minutes, then 2 hours, then the
     * rest of the day ([endOfDay]).
     */
    fun lockUntil(nth: Int, now: Long, endOfDay: Long): Long = when {
        nth <= 1 -> 0L
        nth == 2 -> now + LOCK_SECOND_MS
        nth == 3 -> now + LOCK_THIRD_MS
        else -> maxOf(endOfDay, now + LOCK_THIRD_MS)
    }

    /** What the next stop will cost, said up front ("Go back and they lock for 30 minutes."). */
    fun nextLockWords(nthSoFar: Int): String = when {
        nthSoFar <= 1 -> "Go back to them and they lock for 30 minutes."
        nthSoFar == 2 -> "Go back again and they lock for 2 hours."
        else -> "Go back again and they're locked for the rest of the day."
    }

    /**
     * When Shorts and Reels open again (0 = they're open). Once today's number is [usedUp] that's
     * tomorrow, whatever a lock says; a lock that runs past midnight keeps them closed until it ends.
     */
    fun opensAt(usedUp: Boolean, lockUntil: Long, now: Long, endOfDay: Long): Long {
        val lock = if (lockUntil > now) lockUntil else 0L
        return if (usedUp) maxOf(endOfDay, lock) else lock
    }

    /** "tomorrow", "at 14:30" or "tomorrow at 01:30", for [at] from [opensAt]. */
    fun whenWords(at: Long, endOfDay: Long, time: (Long) -> String): String = when {
        at == endOfDay -> "tomorrow"
        at > endOfDay -> "tomorrow at ${time(at)}"
        else -> "at ${time(at)}"
    }

    /** What's left of today's number after [count] ([allow] a day): never below zero. */
    fun left(allow: Int, count: Int): Int = (allow - count).coerceAtLeast(0)

    /** The emergency code's gift on a Shorts or Reels stop: this many more today, and the lock ends. */
    const val EMERGENCY_MORE = 5

    /** A looser value chosen on one day takes over from [pendingDay] on ("yyyy-MM-dd" sorts by date). */
    fun due(today: String, pendingDay: String): Boolean = pendingDay.isNotEmpty() && today >= pendingDay

    // ---------------------------------------------------------------------------------- the cost

    /** Waking days a year at [minutesPerDay] (16 waking hours a day). */
    fun wakingDaysPerYear(minutesPerDay: Int): Int = (minutesPerDay.coerceAtLeast(0) * 365.0 / 960.0).roundToInt()

    /** "1 in every N waking hours" for [minutesPerDay]; 0 when it's too little to say. */
    fun oneInWakingHours(minutesPerDay: Int): Int = if (minutesPerDay < 10) 0 else (960.0 / minutesPerDay).roundToInt().coerceAtLeast(1)

    // ---------------------------------------------------------------------------------- the code

    /** Letters and digits that can't be misread (no 0/O, 1/I/L). */
    private const val CODE_CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

    /** A new emergency code, like "K7P2-9QXM". */
    fun newCode(random: Random = Random()): String {
        val c = (1..8).map { CODE_CHARS[random.nextInt(CODE_CHARS.length)] }.joinToString("")
        return c.substring(0, 4) + "-" + c.substring(4)
    }

    /** Typed loosely is fine: case, spaces and dashes don't matter. */
    fun normalize(input: String): String = input.uppercase().filter { it.isLetterOrDigit() }

    /** Only this is kept on the phone, never the code itself. */
    fun hash(code: String): String =
        MessageDigest.getInstance("SHA-256").digest(("intent-strict:" + normalize(code)).toByteArray())
            .joinToString("") { "%02x".format(it) }

    fun matches(input: String, storedHash: String): Boolean =
        storedHash.isNotEmpty() && normalize(input).length == 8 && hash(input) == storedHash
}
