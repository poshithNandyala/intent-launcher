package com.poshith.intent.core

/*
 * Sessions: "I'm opening Instagram for 5 minutes."
 *
 * Time is counted only while the app is actually in front (Focus Guard tells us), using the
 * monotonic clock (elapsedRealtime), so changing the phone's clock can't cheat it and a phone call
 * in the middle doesn't eat your minutes. Leave for less than GAP_MS and come back: the same
 * session continues. Stay away longer: it's over, and the next open starts with a pause again.
 */
class SessionBook {

    class Session(
        val pkg: String,
        var allowanceMs: Long,
        var usedMs: Long,
        /** elapsedRealtime when it last came to the front, or -1 while it's in the background. */
        var runningSince: Long,
        /** elapsedRealtime of the last start or stop. */
        var lastActive: Long,
        var extended: Boolean,
        val intention: String,
        val startedWallMs: Long,
    )

    private val map = LinkedHashMap<String, Session>()

    fun admit(pkg: String, minutes: Int, intention: String, nowElapsed: Long, nowWall: Long) {
        map[pkg] = Session(pkg, minutes * 60_000L, 0, -1, nowElapsed, false, clean(intention), nowWall)
    }

    operator fun get(pkg: String): Session? = map[pkg]

    fun usedMs(pkg: String, now: Long): Long {
        val s = map[pkg] ?: return 0
        return s.usedMs + if (s.runningSince >= 0) (now - s.runningSince).coerceAtLeast(0) else 0
    }

    fun remainingMs(pkg: String, now: Long): Long {
        val s = map[pkg] ?: return 0
        return (s.allowanceMs - usedMs(pkg, now)).coerceAtLeast(0)
    }

    /** True while the session still has time and you haven't been away for longer than GAP_MS. */
    fun isActive(pkg: String, now: Long): Boolean {
        val s = map[pkg] ?: return false
        if (remainingMs(pkg, now) <= 0) return false
        if (s.runningSince < 0 && now - s.lastActive > GAP_MS) return false
        return true
    }

    fun isRunning(pkg: String): Boolean = (map[pkg]?.runningSince ?: -1) >= 0

    fun start(pkg: String, now: Long) {
        val s = map[pkg] ?: return
        if (s.runningSince < 0) s.runningSince = now
        s.lastActive = now
    }

    fun stop(pkg: String, now: Long) {
        val s = map[pkg] ?: return
        if (s.runningSince >= 0) {
            s.usedMs += (now - s.runningSince).coerceAtLeast(0)
            s.runningSince = -1
        }
        s.lastActive = now
    }

    /** One extension per session ("2 more minutes"), so "one more" can't become an hour. */
    fun extend(pkg: String, ms: Long, now: Long): Boolean {
        val s = map[pkg] ?: return false
        if (s.extended) return false
        s.allowanceMs = maxOf(s.allowanceMs, usedMs(pkg, now)) + ms
        s.extended = true
        s.lastActive = now
        return true
    }

    fun close(pkg: String) {
        map.remove(pkg)
    }

    /** Drops sessions that are over. */
    fun prune(now: Long) {
        val it = map.entries.iterator()
        while (it.hasNext()) if (!isActive(it.next().key, now)) it.remove()
    }

    fun activePackages(now: Long): List<String> = map.keys.filter { isActive(it, now) }

    fun clear() = map.clear()

    /** One line per session; no JSON library needed so this also runs in plain-JVM tests. */
    fun serialize(): String = map.values.joinToString("\n") { s ->
        listOf(s.pkg, s.allowanceMs, s.usedMs, s.runningSince, s.lastActive, if (s.extended) 1 else 0,
            s.startedWallMs, s.intention).joinToString("|")
    }

    fun load(text: String?) {
        map.clear()
        if (text.isNullOrBlank()) return
        for (line in text.split('\n')) {
            val p = line.split('|')
            if (p.size < 8) continue
            try {
                map[p[0]] = Session(p[0], p[1].toLong(), p[2].toLong(), p[3].toLong(), p[4].toLong(),
                    p[5] == "1", p.subList(7, p.size).joinToString("|"), p[6].toLong())
            } catch (_: NumberFormatException) {
            }
        }
    }

    companion object {
        /** Away for longer than this and the session ends. */
        const val GAP_MS = 10 * 60_000L
        const val EXTENSION_MS = 2 * 60_000L
        private fun clean(s: String) = s.replace('|', ' ').replace('\n', ' ').take(60)
    }
}
