package com.poshith.intent.guard

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.poshith.intent.data.Store

/**
 * A real break: only calls get through. Intent shows a dark, quiet screen instead of the home screen
 * (just the time left, in hours and minutes), Focus Guard keeps every other app and the notification shade closed,
 * "Do Not Disturb access" keeps every sound but calls and alarms off, and "Notification access" sets
 * every notification but calls aside until the break is over. When it ends, Do Not Disturb goes back
 * exactly as it was.
 */
object Break {
    const val MAX_MIN = 8 * 60

    fun start(ctx: Context, minutes: Int) {
        val now = System.currentTimeMillis()
        val m = minutes.coerceIn(1, MAX_MIN)
        Store.focusKind = "break"
        Store.breakLength = m
        Store.focusStarted = now
        Store.focusUntil = now + m * 60_000L
        Store.sessions { it.clear() } // nothing you opened before the break carries on through it
        Dnd.begin(ctx)
        BreakQuietService.reconnect(ctx)
        BreakQuietService.breakStarted()
        scheduleEnd(ctx, Store.focusUntil)
    }

    /** Ends the break now (held "end" button), or tidies up after it ran out. */
    fun end(ctx: Context) {
        if (Store.focusKind == "break" && Store.focusActive()) Store.focusUntil = 0
        Dnd.end(ctx)
        try { ctx.getSystemService(AlarmManager::class.java).cancel(endIntent(ctx)) } catch (_: Exception) {}
    }

    /** Called whenever Intent wakes up: if a break ended while nothing was running, finish the job. */
    fun checkEnded(ctx: Context) {
        if (Store.dndOurs && !Store.breakActive()) {
            Dnd.end(ctx)
            // You're back before Android got round to the alarm: no need to tell you later that it's over.
            try { ctx.getSystemService(AlarmManager::class.java).cancel(endIntent(ctx)) } catch (_: Exception) {}
        }
    }

    private fun scheduleEnd(ctx: Context, atWall: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val pi = endIntent(ctx)
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atWall, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atWall, pi)
        } catch (_: Exception) {
            try { am.set(AlarmManager.RTC_WAKEUP, atWall, pi) } catch (_: Exception) {}
        }
    }

    private fun endIntent(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(ctx, 4201, Intent(ctx, BreakReceiver::class.java).setAction("intent.break.end"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}

class BreakReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        if (!Store.breakActive()) {
            Dnd.end(context)
            Store.flash = "Break done. Welcome back."
            // The phone says when it's over (after Do Not Disturb is off), so you can put it away and forget it.
            if (Reminder.canNotify(context)) try {
                Reminder.createChannel(context)
                val open = android.app.PendingIntent.getActivity(context, 8, Intent(context, com.poshith.intent.HomeActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), android.app.PendingIntent.FLAG_IMMUTABLE)
                val n = android.app.Notification.Builder(context, Reminder.CHANNEL)
                    .setSmallIcon(com.poshith.intent.R.drawable.ic_stat)
                    .setContentTitle("Your break is over")
                    .setContentText("Welcome back. Your notifications come back over the next few minutes.")
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .build()
                context.getSystemService(NotificationManager::class.java).notify(4202, n)
            } catch (_: Exception) {}
        }
    }
}

/** Do Not Disturb for real breaks: calls (from anyone, and repeat callers) and alarms only. */
object Dnd {
    private fun nm(ctx: Context) = ctx.getSystemService(NotificationManager::class.java)

    fun hasAccess(ctx: Context): Boolean = try { nm(ctx).isNotificationPolicyAccessGranted } catch (_: Exception) { false }

    fun requestAccess(ctx: Context) {
        Launch.tryStart(ctx, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS), "Open Settings > Notifications > Do Not Disturb access")
    }

    fun begin(ctx: Context) {
        if (!hasAccess(ctx)) return
        val nm = nm(ctx)
        try {
            if (!Store.dndOurs) {
                val p = nm.notificationPolicy
                val conv = if (Build.VERSION.SDK_INT >= 30) p.priorityConversationSenders else -1
                Store.dndBefore = listOf(nm.currentInterruptionFilter, p.priorityCategories, p.priorityCallSenders,
                    p.priorityMessageSenders, p.suppressedVisualEffects, conv)
            }
            var categories = NotificationManager.Policy.PRIORITY_CATEGORY_CALLS or NotificationManager.Policy.PRIORITY_CATEGORY_REPEAT_CALLERS
            if (Build.VERSION.SDK_INT >= 28) categories = categories or NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS
            // Silenced notifications don't show at all: not in the shade, not as a dot, not on the lock screen.
            @Suppress("DEPRECATION")
            val hidden = if (Build.VERSION.SDK_INT >= 28) {
                NotificationManager.Policy.SUPPRESSED_EFFECT_FULL_SCREEN_INTENT or NotificationManager.Policy.SUPPRESSED_EFFECT_LIGHTS or
                    NotificationManager.Policy.SUPPRESSED_EFFECT_PEEK or NotificationManager.Policy.SUPPRESSED_EFFECT_STATUS_BAR or
                    NotificationManager.Policy.SUPPRESSED_EFFECT_BADGE or NotificationManager.Policy.SUPPRESSED_EFFECT_AMBIENT or
                    NotificationManager.Policy.SUPPRESSED_EFFECT_NOTIFICATION_LIST
            } else NotificationManager.Policy.SUPPRESSED_EFFECT_SCREEN_OFF or NotificationManager.Policy.SUPPRESSED_EFFECT_SCREEN_ON
            // Calls from anyone; no messages, and (Android 11+) no "priority conversations" either.
            nm.notificationPolicy = if (Build.VERSION.SDK_INT >= 30) NotificationManager.Policy(categories,
                NotificationManager.Policy.PRIORITY_SENDERS_ANY, NotificationManager.Policy.PRIORITY_SENDERS_ANY, hidden,
                NotificationManager.Policy.CONVERSATION_SENDERS_NONE)
            else NotificationManager.Policy(categories, NotificationManager.Policy.PRIORITY_SENDERS_ANY,
                NotificationManager.Policy.PRIORITY_SENDERS_ANY, hidden)
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            Store.dndOurs = true
        } catch (_: Exception) {}
    }

    fun end(ctx: Context) {
        if (!Store.dndOurs) return
        Store.dndOurs = false
        if (!hasAccess(ctx)) return
        val nm = nm(ctx)
        try {
            // Android 15+ keeps an app's Do Not Disturb separate from yours: switching ours off is enough.
            if (Build.VERSION.SDK_INT >= 35) { nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL); return }
            val b = Store.dndBefore
            if (b.size >= 5) {
                nm.notificationPolicy = if (Build.VERSION.SDK_INT >= 30 && b.size >= 6 && b[5] >= 0)
                    NotificationManager.Policy(b[1], b[2], b[3], b[4], b[5])
                else NotificationManager.Policy(b[1], b[2], b[3], b[4])
                nm.setInterruptionFilter(if (b[0] > 0) b[0] else NotificationManager.INTERRUPTION_FILTER_ALL)
            } else nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        } catch (_: Exception) {}
    }
}
