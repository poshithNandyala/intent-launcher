package com.poshith.intent.guard

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.telecom.TelecomManager
import com.poshith.intent.data.Store

/**
 * Notification access (optional): real breaks, and check-in apps' notifications if you ask for it.
 *
 * What it does:  during a real break, every notification that isn't a call is set aside (snoozed),
 *                so nothing shows: not in the shade, not on the lock screen, not as a sound. Calls, missed
 *                calls and alarms stay. Snoozed notifications come back on their own a few minutes after
 *                the break ends; nothing is deleted. If you turn it on (Settings › Check-in times), a
 *                check-in app's notifications are set aside the same way until your next check-in time:
 *                a few batches a day instead of a stream (Fitz et al., 2019).
 *                It also lets Intent pause exactly the app it closes (see Closer.silence).
 * What it can't: reply to, open or send anything, or go online (Intent has no internet permission).
 *                It doesn't read or keep what notifications say.
 */
class BreakQuietService : NotificationListenerService() {

    companion object {
        @Volatile private var instance: BreakQuietService? = null
        /** Set aside in chunks, so notifications come back soon after a break that ends early. */
        private const val CHUNK_MS = 5 * 60_000L
        private const val MISSED_CALL = "missed_call" // Notification.CATEGORY_MISSED_CALL (Android 11)

        fun hasAccess(ctx: Context): Boolean = try {
            val enabled = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: ""
            val me = ComponentName(ctx, BreakQuietService::class.java)
            enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        } catch (_: Exception) { false }

        fun requestAccess(ctx: Context) {
            val i = if (Build.VERSION.SDK_INT >= 30)
                android.content.Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, ComponentName(ctx, BreakQuietService::class.java).flattenToString())
            else android.content.Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
            if (!Launch.tryStart(ctx, i, null)) Launch.tryStart(ctx, android.content.Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"), "Open Settings > Notifications > Notification access")
        }

        /** A break just started (or batching was turned on): set aside what's already showing. */
        fun breakStarted() { instance?.let { s -> try { s.activeNotifications?.forEach { s.consider(it) } } catch (_: Exception) {} } }
        fun sweep() = breakStarted()

        /** Allowed in Android's settings and actually connected right now. */
        val connected: Boolean get() = instance != null

        /** Allowed, but Android dropped the connection (it can, after an update or a crash): ask for it back. */
        fun reconnect(ctx: Context) {
            if (connected || !hasAccess(ctx)) return
            try { NotificationListenerService.requestRebind(ComponentName(ctx, BreakQuietService::class.java)) } catch (_: Exception) {}
        }
    }

    private var dialers: Set<String> = emptySet()

    override fun onListenerConnected() {
        super.onListenerConnected()
        Store.init(this)
        instance = this
        dialers = callApps()
        breakStarted()
    }

    override fun onListenerDisconnected() {
        instance = null
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn != null) consider(sbn)
    }

    private fun consider(sbn: StatusBarNotification) {
        if (keep(sbn)) return
        val now = System.currentTimeMillis()
        if (Store.breakActive(now)) {
            val left = Store.focusUntil - now
            if (left <= 0) return
            // Set aside, never deleted: a notification Android won't snooze stays where it is (Do Not Disturb
            // still keeps it silent and out of sight, and the shade stays closed).
            try { snoozeNotification(sbn.key, left.coerceAtMost(CHUNK_MS)) } catch (_: Exception) {}
            return
        }
        // Check-in apps, if you chose to batch them: their notifications wait for your next check-in time.
        if (!Store.batchNotifications || sbn.isOngoing) return
        if (Store.rule(sbn.packageName).mode != com.poshith.intent.core.Mode.BATCHED) return
        val wins = Store.checkIns
        if (wins.any { it.contains(Store.minuteOfDay(now)) }) return
        val next = wins.minOfOrNull { it.nextStartAfter(now) } ?: return
        try { snoozeNotification(sbn.key, (next - now).coerceAtLeast(60_000L)) } catch (_: Exception) {}
    }

    /** Calls ring and show, alarms can be stopped; Intent's own break notice stays. Nothing else. */
    private fun keep(sbn: StatusBarNotification): Boolean {
        val n = sbn.notification ?: return true
        val pkg = sbn.packageName ?: return true
        if (pkg == packageName || pkg == "android") return true
        val cat = n.category
        if (cat == Notification.CATEGORY_CALL || cat == MISSED_CALL || cat == Notification.CATEGORY_ALARM) return true
        if (pkg in dialers) return true
        return false
    }

    private fun callApps(): Set<String> {
        val out = HashSet<String>(listOf("com.android.server.telecom", "com.android.phone", "com.android.incallui",
            "com.samsung.android.incallui", "com.google.android.dialer", "com.android.dialer", "com.samsung.android.dialer"))
        try { getSystemService(TelecomManager::class.java)?.defaultDialerPackage?.let { out += it } } catch (_: Exception) {}
        return out
    }
}
