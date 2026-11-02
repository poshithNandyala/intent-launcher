package com.poshith.intent.data

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import com.poshith.intent.core.Mode
import com.poshith.intent.core.Rule
import com.poshith.intent.core.SessionBook
import com.poshith.intent.core.Window
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale

/**
 * Everything Intent remembers, in one private SharedPreferences file on the phone.
 * No account, no server, no analytics: nothing here ever leaves the device.
 */
object Store {
    private lateinit var p: SharedPreferences
    private val listeners = ArrayList<SharedPreferences.OnSharedPreferenceChangeListener>()

    fun init(ctx: Context) {
        if (::p.isInitialized) return
        p = ctx.applicationContext.getSharedPreferences("intent", Context.MODE_PRIVATE)
        if (installDay.isEmpty()) installDay = dayKey()
    }

    fun listen(l: SharedPreferences.OnSharedPreferenceChangeListener) { listeners += l; p.registerOnSharedPreferenceChangeListener(l) }
    fun unlisten(l: SharedPreferences.OnSharedPreferenceChangeListener) { listeners -= l; p.unregisterOnSharedPreferenceChangeListener(l) }

    private fun str(k: String, d: String = "") = p.getString(k, d) ?: d
    private fun put(k: String, v: String) = p.edit().putString(k, v).apply()
    private fun bool(k: String, d: Boolean) = p.getBoolean(k, d)
    private fun putB(k: String, v: Boolean) = p.edit().putBoolean(k, v).apply()
    private fun int(k: String, d: Int) = p.getInt(k, d)
    private fun putI(k: String, v: Int) = p.edit().putInt(k, v).apply()
    private fun long(k: String, d: Long) = p.getLong(k, d)
    private fun putL(k: String, v: Long) = p.edit().putLong(k, v).apply()

    // ------------------------------------------------------------------ setup and appearance
    var onboarded: Boolean get() = bool("onboarded", false); set(v) = putB("onboarded", v)
    /** The setup page you're on, so setup picks up there if Android restarts it (choosing a home app can). */
    var setupStep: Int get() = int("setup_step", 0); set(v) = putI("setup_step", v)

    // ------------------------------------------------------------------ strict mode
    /** Strict mode (see core/Strict): no extensions in the moment, looser only from tomorrow, locks. */
    var strictOn: Boolean get() = bool("strict_on", false); set(v) = putB("strict_on", v)
    /** Asked to turn Strict mode off: it holds until this day ("" = not asked). */
    var strictOffDay: String get() = str("strict_off_day"); set(v) = put("strict_off_day", v)
    /** The emergency code's hash (the code itself is shown once and never kept). */
    var strictCodeHash: String get() = str("strict_code"); set(v) = put("strict_code", v)

    fun strictActive(): Boolean {
        if (!strictOn) return false
        if (com.poshith.intent.core.Strict.due(dayKey(), strictOffDay)) { strictOn = false; strictOffDay = ""; return false }
        return true
    }

    /** Strict mode stays on until midnight, then turns off. */
    fun strictOffTomorrow() { strictOffDay = dayKey(System.currentTimeMillis() + 86_400_000L) }

    /** Shorts and Reels locked (you went back after being stopped) until then. Only the feeds: the
     *  rest of YouTube and Instagram keeps working. */
    var lockUntil: Long get() = long("lock_until", 0L); set(v) = putL("lock_until", v)
    fun locked(now: Long = System.currentTimeMillis()): Boolean = strictActive() && lockUntil > now

    /** Strict mode: today's Shorts and Reels, with what the emergency code added today. */
    fun reelsAllowToday(now: Long = System.currentTimeMillis()): Int =
        reelsAllow + (if (str("reels_bonus_day") == dayKey(now)) int("reels_bonus", 0) else 0)
    fun addReelsBonus(n: Int, now: Long = System.currentTimeMillis()) {
        val cur = if (str("reels_bonus_day") == dayKey(now)) int("reels_bonus", 0) else 0
        p.edit().putString("reels_bonus_day", dayKey(now)).putInt("reels_bonus", cur + n).apply()
    }

    /** Strict stops today (Shorts or Reels past today's number): each one costs more. */
    fun stopsToday(now: Long = System.currentTimeMillis()): Int = if (str("stops_day") == dayKey(now)) int("stops", 0) else 0
    fun noteStop(now: Long = System.currentTimeMillis()): Int {
        val n = stopsToday(now) + 1
        p.edit().putString("stops_day", dayKey(now)).putInt("stops", n).apply()
        return n
    }

    /** The end of today (local midnight), for "locked for the rest of the day". */
    fun endOfDay(now: Long = System.currentTimeMillis()): Long {
        val c = Calendar.getInstance(); c.timeInMillis = now
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    /** A looser value chosen in Strict mode: it applies from tomorrow. */
    fun setTomorrow(key: String, v: Int) {
        p.edit().putInt(key + "_next", v).putString(key + "_next_day", dayKey(System.currentTimeMillis() + 86_400_000L)).apply()
    }
    /** The value waiting for tomorrow, or null. */
    fun tomorrowValue(key: String): Int? = if (str(key + "_next_day").isNotEmpty()) int(key + "_next", 0) else null
    fun clearTomorrow(key: String) { p.edit().remove(key + "_next").remove(key + "_next_day").apply() }
    private fun applyTomorrow(key: String) {
        val day = str(key + "_next_day")
        if (!com.poshith.intent.core.Strict.due(dayKey(), day)) return
        p.edit().putInt(key, int(key + "_next", 0)).remove(key + "_next").remove(key + "_next_day").apply()
    }

    /** "I'm someone who…": your own words, shown when Intent stops you. */
    var identity: String get() = str("identity"); set(v) = put("identity", v.trim().take(80))
    /** "Remind me next week" on the home screen's "turn on your protection" line. */
    var protectNudgeAfter: Long get() = long("protect_nudge_after", 0L); set(v) = putL("protect_nudge_after", v)
    var installDay: String get() = str("install_day"); set(v) = put("install_day", v)
    /** "system", "dark" or "light". */
    var theme: String get() = str("theme", "system"); set(v) = put("theme", v)
    /** "inter" (bundled) or "system". */
    var font: String get() = str("font", "inter"); set(v) = put("font", v)
    /** "start", "center" or "end". */
    var align: String get() = str("align", "start"); set(v) = put("align", v)
    var textScale: Float get() = p.getFloat("text_scale", 1f); set(v) = p.edit().putFloat("text_scale", v).apply()
    var showDate: Boolean get() = bool("show_date", true); set(v) = putB("show_date", v)
    var showStats: Boolean get() = bool("show_stats", true); set(v) = putB("show_stats", v)
    var showOneThing: Boolean get() = bool("show_one_thing", true); set(v) = putB("show_one_thing", v)
    var checkIn: Boolean get() = bool("check_in", true); set(v) = putB("check_in", v)
    /** One quote a day on the home screen. */
    var showQuote: Boolean get() = bool("show_quote", true); set(v) = putB("show_quote", v)
    /** The home quote stays all day instead of changing every 30 seconds. */
    var quoteDaily: Boolean get() = bool("quote_daily", false); set(v) = putB("quote_daily", v)
    /** Shorts and Reels: step in when you start swiping (needs the "Intent · Shorts & Reels" service). */
    var reelsOn: Boolean get() = bool("reels_on", true); set(v) = putB("reels_on", v)
    /** After "watch more", how many swipes before Intent asks again. 0: the swipe always stops you.
     *  In Strict mode: Shorts and Reels a day, together, with no "more". A looser number set in
     *  Strict mode waits for tomorrow (reels_allow_next). */
    var reelsAllow: Int
        get() { applyTomorrow("reels_allow"); return int("reels_allow", 5) }
        set(v) = putI("reels_allow", v)
    /** If the unlock question gets no answer, lock the phone again (needs Focus Guard). */
    var lockIfNoAnswer: Boolean get() = bool("lock_no_answer", true); set(v) = putB("lock_no_answer", v)
    var autoOpenSingle: Boolean get() = bool("auto_open_single", false); set(v) = putB("auto_open_single", v)
    var doubleTapLock: Boolean get() = bool("double_tap_lock", true); set(v) = putB("double_tap_lock", v)
    var swipeLeftKey: String get() = str("swipe_left"); set(v) = put("swipe_left", v)
    var swipeRightMind: Boolean get() = bool("swipe_right_mind", true); set(v) = putB("swipe_right_mind", v)
    /** The home screen sits on your own wallpaper (default), or on a plain background. */
    var wallpaper: Boolean get() = bool("wallpaper", true); set(v) = putB("wallpaper", v)
    /** Darken the wallpaper behind the words, in percent (0, 35 or 60). */
    var wallpaperDim: Int get() = int("wallpaper_dim", 0); set(v) = putI("wallpaper_dim", v)
    /** Show the status bar on the home screen. */
    var statusBar: Boolean get() = bool("status_bar", true); set(v) = putB("status_bar", v)

    // ------------------------------------------------------------------ home apps
    const val MAX_HOME = 7
    const val SUGGESTED_HOME = 5

    var homeKeys: List<String>
        get() = jsonList(str("home"))
        set(v) = put("home", JSONArray(v.distinct().take(MAX_HOME)).toString())

    private var labelsRaw: String? = null
    private var labelsObj = JSONObject()
    private fun labels(): JSONObject {
        val raw = str("labels")
        if (raw != labelsRaw) { labelsRaw = raw; labelsObj = jsonObj(raw) }
        return labelsObj
    }

    /** Your own names for apps ("Pay" instead of "PhonePe"), keyed by app key. */
    fun customLabel(key: String): String? = labels().optString(key, "").ifBlank { null }
    fun setCustomLabel(key: String, label: String?) {
        val o = jsonObj(str("labels"))
        if (label.isNullOrBlank()) o.remove(key) else o.put(key, label.trim().take(40))
        put("labels", o.toString())
    }

    /** Last known system label per key, so the home screen draws instantly before the app list loads. */
    fun cachedName(key: String): String? = jsonObj(str("names")).optString(key, "").ifBlank { null }
    fun cacheNames(map: Map<String, String>) {
        val o = jsonObj(str("names"))
        for ((k, v) in map) o.put(k, v)
        put("names", o.toString())
    }

    var hidden: Set<String>
        get() = jsonList(str("hidden")).toSet()
        set(v) = put("hidden", JSONArray(v.toList()).toString())

    // ------------------------------------------------------------------ rules
    private var rulesRaw: String? = null
    private var rulesObj = JSONObject()
    private fun rules(): JSONObject {
        val raw = str("rules")
        if (raw != rulesRaw) { rulesRaw = raw; rulesObj = jsonObj(raw) }
        return rulesObj
    }

    fun rule(pkg: String): Rule {
        val o = rules().optJSONObject(pkg) ?: return Rule(Mode.NORMAL, defaultPause, defaultSession)
        return Rule(
            mode = Mode.parse(o.optString("m")),
            pauseSec = o.optInt("p", defaultPause),
            sessionMin = o.optInt("s", defaultSession),
            dailyOpenLimit = o.optInt("l", 0),
            escalate = o.optBoolean("e", true),
        )
    }

    fun setRule(pkg: String, r: Rule) {
        // A new mode applies now: a session (or one-time unlock) from the old one ends. Stricter, so the
        // new block holds at once; looser (Essential, say), so no old timer closes the app later.
        if (rule(pkg).mode != r.mode) sessions { it.close(pkg) }
        val all = jsonObj(str("rules"))
        if (r.mode == Mode.NORMAL && r.dailyOpenLimit == 0) all.remove(pkg)
        else all.put(pkg, JSONObject().put("m", r.mode.name).put("p", r.pauseSec).put("s", r.sessionMin)
            .put("l", r.dailyOpenLimit).put("e", r.escalate))
        put("rules", all.toString())
    }

    /** Changes an app's mode. An app that becomes mindful gets your current mindful settings, all of them
     *  (pause, usual session, daily sessions, the growing pause), so nothing loosens by being added later. */
    fun setMode(pkg: String, m: Mode) {
        val cur = rule(pkg)
        setRule(pkg, if (m == Mode.MINDFUL && cur.mode != Mode.MINDFUL) mindfulDefaults(cur) else cur.copy(mode = m))
    }

    fun mindfulDefaults(r: Rule = Rule()): Rule = r.copy(mode = Mode.MINDFUL, pauseSec = defaultPause, sessionMin = defaultSession,
        dailyOpenLimit = defaultLimit, escalate = defaultEscalate)

    fun packagesWithMode(m: Mode): Set<String> {
        val all = rules()
        val out = LinkedHashSet<String>()
        for (k in all.keys()) if (Mode.parse(all.optJSONObject(k)?.optString("m")) == m) out += k
        return out
    }

    var defaultPause: Int get() = int("default_pause", Rule.DEFAULT_PAUSE_SEC); set(v) = putI("default_pause", v)
    var defaultSession: Int get() = int("default_session", Rule.DEFAULT_SESSION_MIN); set(v) = putI("default_session", v)
    /** One daily budget of minutes for all mindful apps together (0 = none). Every session takes its minutes from it. */
    var budgetMin: Int
        get() { applyTomorrow("budget_min"); return int("budget_min", 0) }
        set(v) = putI("budget_min", v)
    /** A short rest for mindful apps after several sessions close together. */
    var restOn: Boolean get() = bool("rest_on", true); set(v) = putB("rest_on", v)
    var restUntil: Long get() = long("rest_until", 0L); set(v) = putL("rest_until", v)
    /** When the last few mindful sessions started (wall clock), for the rest. */
    val sessionStarts: List<Long>
        get() = try { JSONArray(str("session_starts", "[]")).let { a -> List(a.length()) { a.getLong(it) } } } catch (_: Exception) { emptyList() }
    fun clearSessionStarts() = put("session_starts", "[]")
    fun noteSessionStart(now: Long) {
        val keep = (sessionStarts + now).filter { now - it < com.poshith.intent.core.Limits.REST_WINDOW_MS }
        put("session_starts", JSONArray(keep).toString())
    }
    /** Daily sessions per mindful app (0 = no limit). Before this was saved on its own, it lived only on
     *  the mindful apps themselves: read it from them until it's set. */
    var defaultLimit: Int
        get() = if (p.contains("default_limit")) int("default_limit", 0)
            else packagesWithMode(Mode.MINDFUL).firstOrNull()?.let { rule(it).dailyOpenLimit } ?: 0
        set(v) = putI("default_limit", v)
    var defaultEscalate: Boolean
        get() = if (p.contains("default_escalate")) bool("default_escalate", true)
            else packagesWithMode(Mode.MINDFUL).firstOrNull()?.let { rule(it).escalate } ?: true
        set(v) = putB("default_escalate", v)
    /** Seconds you wait before a change that makes an app easier to open takes effect. 0 = off. */
    var coolOffSec: Int get() = int("cool_off", 20); set(v) = putI("cool_off", v)

    // ------------------------------------------------------------------ intentions
    var oneThing: String get() = str("one_thing"); set(v) = put("one_thing", v.trim().take(80))
    var oneThingDay: String get() = str("one_thing_day"); set(v) = put("one_thing_day", v)
    /** Your if-then plan: "When I feel the pull, I'll ..." */
    var urgePlan: String get() = str("urge_plan"); set(v) = put("urge_plan", v.trim().take(80))

    // ------------------------------------------------------------------ focus and wind-down
    var focusUntil: Long get() = long("focus_until", 0); set(v) = putL("focus_until", v)
    var focusStarted: Long get() = long("focus_started", 0); set(v) = putL("focus_started", v)
    var focusLength: Int get() = int("focus_len", 50); set(v) = putI("focus_len", v)
    var windDownOn: Boolean get() = bool("wd_on", false); set(v) = putB("wd_on", v)
    var windDownStart: Int get() = int("wd_start", 22 * 60 + 30); set(v) = putI("wd_start", v)
    var windDownEnd: Int get() = int("wd_end", 6 * 60 + 30); set(v) = putI("wd_end", v)
    var windDownStrict: Boolean get() = bool("wd_strict", false); set(v) = putB("wd_strict", v)
    val windDown: Window? get() = if (windDownOn) Window(windDownStart, windDownEnd) else null

    /** "focus", "break" (a screen-free break) or "basic" (basic phone: until you turn it off). */
    var focusKind: String get() = str("focus_kind", "focus"); set(v) = put("focus_kind", v)
    val focusIsBreak: Boolean get() = focusKind == "break"
    val basicPhone: Boolean get() = focusKind == "basic" && focusActive()
    /** Far in the future: "until I turn it off". */
    const val UNTIL_OFF = 4_000_000_000_000L

    fun focusActive(now: Long = System.currentTimeMillis()) = focusUntil > now
    fun breakActive(now: Long = System.currentTimeMillis()) = focusKind == "break" && focusActive(now)

    /** Keep WhatsApp during a real break (its calls ring either way, as calls). */
    var breakKeepWhatsapp: Boolean get() = bool("break_whatsapp", false); set(v) = putB("break_whatsapp", v)
    /** The break length you chose last time, in minutes. */
    var breakLength: Int get() = int("break_len", 30); set(v) = putI("break_len", v)

    // ------------------------------------------------------------------ Do Not Disturb during a real break
    /** True while Intent has Do Not Disturb on for a break, so it knows to switch it back off. */
    var dndOurs: Boolean get() = bool("dnd_ours", false); set(v) = putB("dnd_ours", v)
    /** What Do Not Disturb looked like before the break: filter, then policy fields. -1 = not saved. */
    var dndBefore: List<Int>
        get() = jsonList(str("dnd_before")).mapNotNull { it.toIntOrNull() }
        set(v) = put("dnd_before", JSONArray(v.map { it.toString() }).toString())

    // ------------------------------------------------------------------ morning quiet (Yousef's 30/15 rule, Durov's quiet mornings)
    /** Minutes mindful apps rest after your first unlock of the morning. 0 = off. */
    var morningQuietMin: Int get() = int("morning_min", 15); set(v) = putI("morning_min", v)
    var morningDay: String get() = str("morning_day"); set(v) = put("morning_day", v)
    var morningUntil: Long get() = long("morning_until", 0); set(v) = putL("morning_until", v)

    /** Call on every unlock. The first unlock between 4:00 and 12:00 starts the morning quiet. */
    fun noteUnlock(now: Long = System.currentTimeMillis()) {
        val today = dayKey(now)
        if (morningDay == today) return
        val minute = minuteOfDay(now)
        if (minute < 4 * 60) return            // still night: the morning hasn't started
        morningDay = today
        if (minute < 12 * 60 && morningQuietMin > 0) morningUntil = now + morningQuietMin * 60_000L
    }

    // ------------------------------------------------------------------ check-in times (batched apps)
    /** Start times (minutes after midnight) of the daily check-in windows. Default 12:30 and 18:30. */
    var checkInStarts: List<Int>
        get() = jsonList(str("checkin_starts", "[\"750\",\"1110\"]")).mapNotNull { it.toIntOrNull() }.sorted()
        set(v) = put("checkin_starts", JSONArray(v.distinct().sorted().map { it.toString() }).toString())
    var checkInLength: Int get() = int("checkin_len", 20); set(v) = putI("checkin_len", v)
    val checkIns: List<Window> get() = checkInStarts.map { Window(it, (it + checkInLength) % (24 * 60)) }

    /** Ask "How are you feeling?" on the pause screen. */
    var askFeeling: Boolean get() = bool("ask_feeling", false); set(v) = putB("ask_feeling", v)

    // ------------------------------------------------------------------ sessions (see core/Sessions.kt)
    private val book = SessionBook()
    private var bookLoaded = false

    /** All session access goes through here, on any thread. */
    fun <T> sessions(block: (SessionBook) -> T): T = synchronized(book) {
        if (!bookLoaded) {
            bookLoaded = true
            // elapsedRealtime restarts at boot: sessions from before a reboot are dropped.
            val bootNow = System.currentTimeMillis() - SystemClock.elapsedRealtime()
            if (Math.abs(bootNow - long("sessions_boot", 0)) < 120_000) book.load(str("sessions"))
        }
        val r = block(book)
        val s = book.serialize()
        if (s != lastSavedSessions) {
            lastSavedSessions = s
            p.edit().putString("sessions", s)
                .putLong("sessions_boot", System.currentTimeMillis() - SystemClock.elapsedRealtime()).apply()
        }
        r
    }
    private var lastSavedSessions: String? = null

    /** When a session for this package last ended (wall clock), for the short cooldown. */
    fun markEnded(pkg: String) {
        val o = jsonObj(str("ended")); o.put(pkg, System.currentTimeMillis()); put("ended", o.toString())
    }
    fun endedRecently(pkg: String, withinMs: Long = 10 * 60_000L): Boolean {
        val t = jsonObj(str("ended")).optLong(pkg, 0)
        return t > 0 && System.currentTimeMillis() - t < withinMs
    }

    // ------------------------------------------------------------------ unlocks and the check-in
    @Volatile var lastUnlockElapsed: Long = 0
    /** A short message the home screen shows once ("Not now. That's the 3rd time today."). */
    var flash: String get() = str("flash"); set(v) { put("flash", v); putL("flash_at", System.currentTimeMillis()) }
    val flashAt: Long get() = long("flash_at", 0)

    // ------------------------------------------------------------------ the Mind library
    /** Lessons you've opened (guessed, then read), and the "try this" ideas you said you'd try. */
    var lessonsRead: Set<String>
        get() = jsonList(str("lessons_read")).toSet()
        set(v) = put("lessons_read", JSONArray(v.toList()).toString())
    var lessonsTrying: Set<String>
        get() = jsonList(str("lessons_trying")).toSet()
        set(v) = put("lessons_trying", JSONArray(v.toList()).toString())

    // ------------------------------------------------------------------ notification cleanup checklist
    var notifReviewed: Set<String>
        get() = jsonList(str("notif_done")).toSet()
        set(v) = put("notif_done", JSONArray(v.toList()).toString())

    // ------------------------------------------------------------------ helpers
    fun dayKey(ms: Long = System.currentTimeMillis()): String {
        val c = Calendar.getInstance(); c.timeInMillis = ms
        return String.format(Locale.US, "%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    fun minuteOfDay(ms: Long = System.currentTimeMillis()): Int {
        val c = Calendar.getInstance(); c.timeInMillis = ms
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    }

    /** Days since Intent was installed (day 1 = the install day). */
    fun dayNumber(): Int {
        return try {
            val parts = installDay.split('-').map { it.toInt() }
            val c = Calendar.getInstance(); c.set(parts[0], parts[1] - 1, parts[2], 0, 0, 0)
            val now = Calendar.getInstance()
            ((now.timeInMillis - c.timeInMillis) / 86_400_000L).toInt() + 1
        } catch (_: Exception) { 1 }
    }

    /** "Erase everything": back to a fresh install. Rules, counters, sessions and names are all gone. */
    fun eraseAll() {
        synchronized(book) {
            book.clear()
            bookLoaded = true
            lastSavedSessions = null
            p.edit().clear().commit()
            installDay = dayKey()
        }
    }

    internal fun raw(): SharedPreferences = p

    private fun jsonList(s: String): List<String> = try {
        val a = JSONArray(s.ifBlank { "[]" }); List(a.length()) { a.optString(it) }.filter { it.isNotBlank() }
    } catch (_: Exception) { emptyList() }

    private fun jsonObj(s: String): JSONObject = try { JSONObject(s.ifBlank { "{}" }) } catch (_: Exception) { JSONObject() }
}
