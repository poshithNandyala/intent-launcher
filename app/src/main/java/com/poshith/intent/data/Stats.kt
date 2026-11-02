package com.poshith.intent.data

import org.json.JSONObject

/**
 * Intent's own counters, kept for 35 days on the phone. They work without any special permission:
 * how often the pause appeared, how often you chose "Not now", how many sessions you started.
 */
object Stats {
    private const val KEY = "stats"
    private const val KEEP_DAYS = 35

    class Day(
        val day: String,
        val pauses: Int,
        val notNow: Int,
        val sessions: Int,
        val plannedMin: Int,
        val justChecking: Int,
        val perApp: Map<String, IntArray>, // pkg -> [pauses, notNow, sessions]
        val shorts: Int = 0,
        val reels: Int = 0,
    )

    @Synchronized
    private fun bump(pkg: String?, field: String, by: Int = 1, appIndex: Int = -1) {
        val p = Store.raw()
        val all = try { JSONObject(p.getString(KEY, "{}") ?: "{}") } catch (_: Exception) { JSONObject() }
        val today = Store.dayKey()
        val d = all.optJSONObject(today) ?: JSONObject()
        d.put(field, d.optInt(field, 0) + by)
        if (pkg != null && appIndex >= 0) {
            val apps = d.optJSONObject("apps") ?: JSONObject()
            val arr = apps.optJSONArray(pkg) ?: org.json.JSONArray(listOf(0, 0, 0))
            arr.put(appIndex, arr.optInt(appIndex, 0) + 1)
            apps.put(pkg, arr)
            d.put("apps", apps)
        }
        all.put(today, d)
        // keep the file small
        if (all.length() > KEEP_DAYS) {
            val keys = all.keys().asSequence().toList().sorted()
            for (k in keys.take(all.length() - KEEP_DAYS)) all.remove(k)
        }
        p.edit().putString(KEY, all.toString()).apply()
    }

    fun pauseShown(pkg: String) = bump(pkg, "pauses", appIndex = 0)
    fun notNow(pkg: String) = bump(pkg, "notNow", appIndex = 1)
    /** A session started. Its minutes count as planned mindful time (the daily budget, Your week) only if [mindful]. */
    fun sessionStarted(pkg: String, minutes: Int, mindful: Boolean = true) {
        bump(pkg, "sessions", appIndex = 2)
        if (mindful) bump(null, "planned", minutes)
    }
    fun justChecking() = bump(null, "justChecking")
    /** The Strict mode emergency code was used. */
    fun emergencyUsed() = bump(null, "emergency")
    /** One more swipe through YouTube Shorts or Instagram Reels (counted only with the Shorts & Reels guard on). */
    fun shortVideo(feed: com.poshith.intent.core.Feed) = bump(null, if (feed == com.poshith.intent.core.Feed.SHORTS) "shorts" else "reels")
    fun shortVideosToday(feed: com.poshith.intent.core.Feed): Int = day().let { if (feed == com.poshith.intent.core.Feed.SHORTS) it.shorts else it.reels }
    fun shortVideosToday(): Int = day().let { it.shorts + it.reels }

    fun day(key: String = Store.dayKey()): Day {
        val all = try { JSONObject(Store.raw().getString(KEY, "{}") ?: "{}") } catch (_: Exception) { JSONObject() }
        val d = all.optJSONObject(key) ?: JSONObject()
        val per = HashMap<String, IntArray>()
        d.optJSONObject("apps")?.let { apps ->
            for (k in apps.keys()) {
                val a = apps.optJSONArray(k) ?: continue
                per[k] = intArrayOf(a.optInt(0), a.optInt(1), a.optInt(2))
            }
        }
        return Day(key, d.optInt("pauses"), d.optInt("notNow"), d.optInt("sessions"), d.optInt("planned"),
            d.optInt("justChecking"), per, d.optInt("shorts"), d.optInt("reels"))
    }

    /** Sessions started today for this app (the pause grows with each one). */
    fun sessionsToday(pkg: String): Int = day().perApp[pkg]?.get(2) ?: 0
    fun pausesToday(pkg: String): Int = day().perApp[pkg]?.get(0) ?: 0
    fun notNowToday(): Int = day().notNow

    fun lastDays(n: Int): List<Day> {
        val out = ArrayList<Day>()
        val c = java.util.Calendar.getInstance()
        repeat(n) {
            out += day(Store.dayKey(c.timeInMillis))
            c.add(java.util.Calendar.DAY_OF_YEAR, -1)
        }
        return out
    }
}
