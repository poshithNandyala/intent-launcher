package com.poshith.intent.core

import java.util.Calendar

/*
 * The rules engine. Pure Kotlin (no Android) so it can be unit tested on any computer: ./build.sh test
 *
 * Every way of opening an app goes through Policy.decide(): taps on the home screen, search results,
 * and (when Focus Guard is on) apps opened from notifications or the recents screen.
 */

/** How Intent treats an app. Rules are per package, so they apply in every profile. */
enum class Mode {
    /** Opens instantly, always, even in Focus and Wind-down (calls, maps, payments, authenticator...). */
    ESSENTIAL,
    /** Opens instantly. Paused only during a Focus session (or Wind-down if you choose "strict"). */
    NORMAL,
    /** "Mindful": a short pause screen first, then a session length you choose. */
    MINDFUL,
    /** "Check-in times": opens only in your daily check-in windows (email, Slack, news...). */
    BATCHED,
    /** Never opens until you unblock it, or type a deliberate phrase for a 5-minute pass. */
    BLOCKED;

    /** Modes are declared from loosest to strictest. */
    fun isLooserThan(other: Mode): Boolean = ordinal < other.ordinal

    companion object {
        fun parse(s: String?): Mode = values().firstOrNull { it.name == s } ?: NORMAL
    }
}

data class Rule(
    val mode: Mode = Mode.NORMAL,
    val pauseSec: Int = DEFAULT_PAUSE_SEC,
    val sessionMin: Int = DEFAULT_SESSION_MIN,
    /** 0 = no daily limit. */
    val dailyOpenLimit: Int = 0,
    /** The pause grows a little with every open after the second one today. */
    val escalate: Boolean = true,
) {
    companion object {
        const val DEFAULT_PAUSE_SEC = 6
        const val DEFAULT_SESSION_MIN = 5
        val PAUSE_CHOICES = intArrayOf(3, 6, 10, 15)
        val SESSION_CHOICES = intArrayOf(2, 5, 10, 15, 30)
        val LIMIT_CHOICES = intArrayOf(0, 3, 5, 10, 15, 25)
    }
}

enum class BlockReason { FOCUS, BREAK, BASIC, MORNING, BATCH, WIND_DOWN, BLOCKED_RULE }

sealed class Decision {
    object Open : Decision()

    data class Pause(
        val pauseMs: Long,
        /** Sessions already started today (not counting this one). */
        val opensToday: Int,
        val limitReached: Boolean,
        val sessionMin: Int,
    ) : Decision()

    data class Blocked(val reason: BlockReason, /** 0 = until you change the rule. */ val untilWallMs: Long) : Decision()
}

/** A daily window, in minutes after midnight. May wrap past midnight (22:30 to 06:30). */
data class Window(val startMin: Int, val endMin: Int) {
    fun contains(minuteOfDay: Int): Boolean = when {
        startMin == endMin -> false
        startMin < endMin -> minuteOfDay in startMin until endMin
        else -> minuteOfDay >= startMin || minuteOfDay < endMin
    }

    /** Next wall-clock time this window starts (later today, or tomorrow). */
    fun nextStartAfter(nowWallMs: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = nowWallMs
        c.set(Calendar.HOUR_OF_DAY, startMin / 60)
        c.set(Calendar.MINUTE, startMin % 60)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        if (c.timeInMillis <= nowWallMs) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    /** Wall-clock time at which the window that contains [nowWallMs] ends. */
    fun endsAfter(nowWallMs: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = nowWallMs
        val minute = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        c.set(Calendar.HOUR_OF_DAY, endMin / 60)
        c.set(Calendar.MINUTE, endMin % 60)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        if (minute >= endMin) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }
}

/** Everything the policy needs to know about this moment. */
data class Situation(
    val nowWallMs: Long,
    val minuteOfDay: Int,
    /** 0 = no focus session running. */
    val focusUntilWallMs: Long = 0,
    /** A focus session that is a screen-free break (same rules, different words). */
    val focusIsBreak: Boolean = false,
    /** "Basic phone": essentials only, until you turn it off. */
    val focusIsBasic: Boolean = false,
    /** Morning quiet: mindful apps rest for the first minutes after your first unlock of the day. 0 = none. */
    val morningUntilWallMs: Long = 0,
    /** null = Wind-down is off. */
    val windDown: Window? = null,
    /** Daily check-in windows for BATCHED apps. */
    val checkIns: List<Window> = emptyList(),
    /** Strict Wind-down also pauses NORMAL apps, not just mindful ones. */
    val windDownStrict: Boolean = false,
    val opensToday: Int = 0,
    val hasActiveSession: Boolean = false,
    /** Phone, settings, keyboards, the permission screens... never paused, whatever the rule says. */
    val protectedApp: Boolean = false,
    /** A session for this app ended a few minutes ago: the next pause is a little longer. */
    val recentlyEnded: Boolean = false,
)

object Policy {
    const val ESCALATE_STEP_MS = 2_000L
    const val MIN_PAUSE_MS = 2_000L
    const val MAX_PAUSE_MS = 20_000L
    const val COOLDOWN_BONUS_MS = 5_000L

    fun decide(rule: Rule, s: Situation): Decision {
        if (s.protectedApp) return Decision.Open
        // A real break is stricter than focus: only calling (and what you chose to keep) opens, so
        // even essentials wait. The caller decides what counts as "protected" during a break.
        if (s.focusUntilWallMs > s.nowWallMs && s.focusIsBreak && !s.focusIsBasic) {
            return Decision.Blocked(BlockReason.BREAK, s.focusUntilWallMs)
        }
        if (rule.mode == Mode.ESSENTIAL) return Decision.Open
        // Focus and basic phone mean essentials only, even if a session was running before.
        if (s.focusUntilWallMs > s.nowWallMs) {
            val why = when { s.focusIsBasic -> BlockReason.BASIC; s.focusIsBreak -> BlockReason.BREAK; else -> BlockReason.FOCUS }
            return Decision.Blocked(why, s.focusUntilWallMs)
        }
        // A session you chose (or a one-time unlock) keeps working across quick app switches.
        // Tightening an app's rule ends its session (Store.setRule), so a new block applies at once.
        if (s.hasActiveSession) return Decision.Open
        if (rule.mode == Mode.BLOCKED) return Decision.Blocked(BlockReason.BLOCKED_RULE, 0)
        if (rule.mode == Mode.MINDFUL && s.morningUntilWallMs > s.nowWallMs) {
            return Decision.Blocked(BlockReason.MORNING, s.morningUntilWallMs)
        }
        if (rule.mode == Mode.BATCHED) {
            if (s.checkIns.any { it.contains(s.minuteOfDay) }) return Decision.Open
            val next = s.checkIns.minOfOrNull { it.nextStartAfter(s.nowWallMs) } ?: 0L
            return Decision.Blocked(BlockReason.BATCH, next)
        }
        val wd = s.windDown
        if (wd != null && wd.contains(s.minuteOfDay) && (rule.mode == Mode.MINDFUL || s.windDownStrict)) {
            return Decision.Blocked(BlockReason.WIND_DOWN, wd.endsAfter(s.nowWallMs))
        }
        if (rule.mode == Mode.MINDFUL) {
            val limitReached = rule.dailyOpenLimit > 0 && s.opensToday >= rule.dailyOpenLimit
            return Decision.Pause(pauseMs(rule, s), s.opensToday, limitReached, rule.sessionMin)
        }
        return Decision.Open
    }

    /** Base pause, +2 s for every session after the second today, +5 s right after a session ended. Max 20 s. */
    fun pauseMs(rule: Rule, s: Situation): Long {
        var ms = rule.pauseSec * 1000L
        if (rule.escalate && s.opensToday >= 2) ms += ESCALATE_STEP_MS * (s.opensToday - 1)
        if (s.recentlyEnded) ms += COOLDOWN_BONUS_MS
        return ms.coerceIn(MIN_PAUSE_MS, MAX_PAUSE_MS)
    }
}
