package com.poshith.intent.data

import com.poshith.intent.core.AfterCheck
import com.poshith.intent.core.Pull
import com.poshith.intent.core.PullNote
import com.poshith.intent.core.Pulls
import com.poshith.intent.core.UrgeRide
import org.json.JSONObject

/**
 * What Intent noticed, with you: the pulls you named on the pause, the urges you rode out, how sessions
 * felt afterwards, and your plan for each pull. Kept in Intent's private storage on this phone for 60 days
 * (the plans until you change them), never sent anywhere. Delete it in Settings › Mindfulness.
 */
object Noticed {
    private const val PULLS = "pull_log"
    private const val RIDES = "urge_rides"
    private const val AFTER = "after_checks"
    private const val PLANS = "pull_plans"
    private const val PENDING = "after_pending"
    private const val ASKED_DAY = "after_asked_day"
    private const val ASKED_N = "after_asked_n"
    private const val MAX_PULLS = 500
    private const val MAX_RIDES = 200
    private const val MAX_AFTER = 300

    private fun get(k: String): String = Store.raw().getString(k, "") ?: ""
    private fun put(k: String, v: String) = Store.raw().edit().putString(k, v).apply()

    // ------------------------------------------------------------------ the pulls you named

    fun pulls(): List<PullNote> = Pulls.decodeNotes(get(PULLS))

    @Synchronized
    fun notePull(p: Pull, pkg: String, opened: Boolean, now: Long = System.currentTimeMillis()) =
        put(PULLS, Pulls.encodeNotes(Pulls.keep(pulls() + PullNote(now, p, pkg, opened), { it.at }, now, MAX_PULLS)))

    // ------------------------------------------------------------------ urges ridden out

    fun rides(): List<UrgeRide> = Pulls.decodeRides(get(RIDES))

    @Synchronized
    fun noteRide(r: UrgeRide) = put(RIDES, Pulls.encodeRides(Pulls.keep(rides() + r, { it.at }, r.at, MAX_RIDES)))

    // ------------------------------------------------------------------ how sessions felt

    fun afters(): List<AfterCheck> = Pulls.decodeAfter(get(AFTER))

    @Synchronized
    fun noteAfter(pkg: String, feel: Int, now: Long = System.currentTimeMillis()) =
        put(AFTER, Pulls.encodeAfter(Pulls.keep(afters() + AfterCheck(now, pkg, feel.coerceIn(-1, 1)), { it.at }, now, MAX_AFTER)))

    /** A session started: once you're back home (after a minute or more), ask how it felt. Only the latest counts. */
    fun expectAfter(pkg: String, now: Long = System.currentTimeMillis()) = put(PENDING, "$pkg|$now")

    /** The session waiting for "how do you feel?": the app and when it started. */
    fun pendingAfter(): Pair<String, Long>? {
        val p = get(PENDING).split('|')
        val at = p.getOrNull(1)?.toLongOrNull() ?: return null
        return if (p[0].isBlank()) null else p[0] to at
    }

    fun clearPendingAfter() = Store.raw().edit().remove(PENDING).apply()

    fun asksToday(): Int = if (get(ASKED_DAY) == Store.dayKey()) Store.raw().getInt(ASKED_N, 0) else 0
    fun noteAsked() { Store.raw().edit().putString(ASKED_DAY, Store.dayKey()).putInt(ASKED_N, asksToday() + 1).apply() }

    // ------------------------------------------------------------------ a plan for each pull

    private fun plans(): JSONObject = try { JSONObject(get(PLANS).ifBlank { "{}" }) } catch (_: Exception) { JSONObject() }

    /** Your "then" for this pull ("read one page"), or "". */
    fun plan(p: Pull): String = plans().optString(p.id, "")

    fun setPlan(p: Pull, then: String) {
        val o = plans()
        val t = then.trim().trimEnd('.').take(80)
        if (t.isBlank()) o.remove(p.id) else o.put(p.id, t)
        put(PLANS, o.toString())
    }

    // ------------------------------------------------------------------ all of it

    fun isEmpty(): Boolean = get(PULLS).isBlank() && get(RIDES).isBlank() && get(AFTER).isBlank()

    /** Deletes the pulls, rides and after-session answers. Your plans are your own words: they stay. */
    fun deleteAll() {
        Store.raw().edit().remove(PULLS).remove(RIDES).remove(AFTER).remove(PENDING).remove(ASKED_DAY).remove(ASKED_N).apply()
    }
}
