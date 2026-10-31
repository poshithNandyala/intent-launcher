package com.poshith.intent.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import java.util.Calendar

/**
 * Optional, with "Usage access" granted in Android settings: honest numbers for today, computed on the
 * phone from Android's own usage events. Nothing is stored or sent anywhere.
 */
object Usage {
    // Event types (numbers so they also work on older Android versions).
    private const val RESUMED = 1          // ACTIVITY_RESUMED / MOVE_TO_FOREGROUND
    private const val PAUSED = 2           // ACTIVITY_PAUSED / MOVE_TO_BACKGROUND
    private const val SCREEN_OFF = 16      // SCREEN_NON_INTERACTIVE (API 28)
    private const val KEYGUARD_HIDDEN = 18 // unlocked (API 28)
    private const val STOPPED = 23         // ACTIVITY_STOPPED (API 29)

    class Today(val unlocks: Int, val unlocksKnown: Boolean, val foregroundMs: Map<String, Long>) {
        fun msFor(pkgs: Collection<String>): Long = pkgs.sumOf { foregroundMs[it] ?: 0L }
    }

    fun hasAccess(ctx: Context): Boolean = try {
        val ops = ctx.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= 29)
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        else ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        mode == AppOpsManager.MODE_ALLOWED
    } catch (_: Exception) { false }

    @Volatile private var cache: Today? = null
    @Volatile private var cacheAt = 0L

    /** Call off the main thread. Cached for 30 seconds. */
    fun today(ctx: Context): Today? {
        if (!hasAccess(ctx)) return null
        val now = System.currentTimeMillis()
        cache?.let { if (now - cacheAt < 30_000) return it }
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        val r = compute(ctx, c.timeInMillis, now) ?: return null
        cache = r; cacheAt = now
        return r
    }

    fun compute(ctx: Context, from: Long, to: Long): Today? = try {
        val usm = ctx.getSystemService(UsageStatsManager::class.java)
        val events = usm.queryEvents(from, to)
        val e = UsageEvents.Event()
        val started = HashMap<String, Long>()
        val total = HashMap<String, Long>()
        var unlocks = 0
        fun close(pkg: String, t: Long) {
            val s = started.remove(pkg) ?: return
            total[pkg] = (total[pkg] ?: 0L) + (t - s).coerceAtLeast(0)
        }
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val pkg = e.packageName ?: continue
            when (e.eventType) {
                RESUMED -> if (pkg !in started) started[pkg] = e.timeStamp
                PAUSED, STOPPED -> close(pkg, e.timeStamp)
                SCREEN_OFF -> started.keys.toList().forEach { close(it, e.timeStamp) }
                KEYGUARD_HIDDEN -> unlocks++
            }
        }
        started.keys.toList().forEach { close(it, to) }
        Today(unlocks, Build.VERSION.SDK_INT >= 28, total)
    } catch (_: Exception) { null }

    /** The app most recently brought to the front in the last few minutes, if any. */
    fun lastForeground(ctx: Context, withinMs: Long = 15 * 60_000L): String? = try {
        if (!hasAccess(ctx)) null else {
            val usm = ctx.getSystemService(UsageStatsManager::class.java)
            val now = System.currentTimeMillis()
            val events = usm.queryEvents(now - withinMs, now)
            val e = UsageEvents.Event()
            var last: String? = null
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                if (e.eventType == RESUMED) last = e.packageName
            }
            last
        }
    } catch (_: Exception) { null }

    fun formatMinutes(ms: Long): String {
        val m = (ms / 60_000L).toInt()
        return if (m < 60) "$m min" else "${m / 60} h ${m % 60} min"
    }
}
