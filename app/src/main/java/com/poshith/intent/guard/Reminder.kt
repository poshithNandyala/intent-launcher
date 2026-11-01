package com.poshith.intent.guard

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.poshith.intent.HomeActivity
import com.poshith.intent.R
import com.poshith.intent.data.Apps
import com.poshith.intent.data.Store

/**
 * When Focus Guard is off, Intent can't end a session by itself, so it does the next best thing:
 * a quiet notification when the minutes you chose are up. (With Focus Guard on, the time's-up
 * screen appears instead and this is never used.)
 */
object Reminder {
    const val CHANNEL = "sessions"
    private const val EXTRA_PKG = "pkg"
    private const val EXTRA_MIN = "min"
    private val main = Handler(Looper.getMainLooper())
    private val pending = HashMap<String, Runnable>()

    fun createChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) != null) return
        val ch = NotificationChannel(CHANNEL, "Session reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Tells you when the minutes you chose for an app are up (only when Focus Guard is off)."
            setShowBadge(false)
        }
        nm.createNotificationChannel(ch)
    }

    fun canNotify(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun schedule(ctx: Context, pkg: String, minutes: Int) {
        val app = ctx.applicationContext
        cancel(app, pkg)
        val ms = minutes * 60_000L
        // In-process timer (on time while Intent is alive, which a home app usually is)...
        val r = Runnable { fire(app, pkg, minutes) }
        pending[pkg] = r
        main.postDelayed(r, ms)
        // ...and an inexact alarm as a backup if Android stops the process.
        try {
            val am = app.getSystemService(AlarmManager::class.java)
            am.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + ms + 15_000L, alarmIntent(app, pkg, minutes))
        } catch (_: Exception) {}
    }

    fun cancel(ctx: Context, pkg: String) {
        pending.remove(pkg)?.let { main.removeCallbacks(it) }
        try { ctx.getSystemService(AlarmManager::class.java).cancel(alarmIntent(ctx, pkg, 0)) } catch (_: Exception) {}
    }

    private fun alarmIntent(ctx: Context, pkg: String, minutes: Int): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java).setAction("intent.reminder.$pkg")
            .putExtra(EXTRA_PKG, pkg).putExtra(EXTRA_MIN, minutes)
        return PendingIntent.getBroadcast(ctx, pkg.hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    internal fun fire(ctx: Context, pkg: String, minutes: Int) {
        pending.remove(pkg)?.let { main.removeCallbacks(it) }
        // If Focus Guard came on in the meantime, it handles the ending itself.
        if (FocusGuardService.running) return
        Store.init(ctx)
        val key = "reminded_$pkg"
        val last = Store.raw().getLong(key, 0)
        if (System.currentTimeMillis() - last < 60_000) return // the alarm backup after the in-process timer
        Store.raw().edit().putLong(key, System.currentTimeMillis()).apply()
        Store.sessions { it.close(pkg) }
        Store.markEnded(pkg)
        if (!canNotify(ctx)) return
        val label = Apps.labelFor(ctx, pkg)
        val open = PendingIntent.getActivity(ctx, 7, Intent(ctx, HomeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE)
        val next = Store.oneThing.ifBlank { null }
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(if (minutes > 0) "Your $minutes minutes on $label are up" else "Time's up on $label")
            .setContentText(next?.let { "Back to: $it" } ?: "This is the moment you planned to stop.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .build()
        try { ctx.getSystemService(NotificationManager::class.java).notify(pkg.hashCode(), n) } catch (_: Exception) {}
    }

    fun readPkg(i: Intent): String? = i.getStringExtra(EXTRA_PKG)
    fun readMin(i: Intent): Int = i.getIntExtra(EXTRA_MIN, 0)
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pkg = Reminder.readPkg(intent) ?: return
        Reminder.fire(context, pkg, Reminder.readMin(intent))
    }
}
