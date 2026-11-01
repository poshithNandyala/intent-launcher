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
 * Real break, notifications (optional, needs "Notification access").
 *
 * What it does:  during a real break, every notification that isn't a call is set aside (snoozed),
 *                so nothing shows: not in the shade, not on the lock screen, not as a sound. Calls, missed
 *                calls and alarms stay. Snoozed notifications come back on their own a few minutes after
 *                the break ends; nothing is deleted.
 * What it can't: reply to, open or send anything, or go online (Intent has no internet permission).
 *                Outside a break it does nothing at all.
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

        /** A break just started: clear what's already showing. */
        fun breakStarted() { instance?.let { s -> try { s.activeNotifications?.forEach { s.consider(it) } } catch (_: Exception) {} } }
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
        if (!Store.breakActive()) return
        if (keep(sbn)) return
        val left = Store.focusUntil - System.currentTimeMillis()
        if (left <= 0) return
        try { snoozeNotification(sbn.key, left.coerceAtMost(CHUNK_MS)) } catch (_: Exception) {
            // Some notifications can't be snoozed: clear them if they can be cleared.
            try { if (sbn.isClearable) cancelNotification(sbn.key) } catch (_: Exception) {}
        }
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
