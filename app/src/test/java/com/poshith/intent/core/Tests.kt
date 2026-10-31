package com.poshith.intent.core

import java.util.Calendar

/*
 * Plain-Kotlin tests for the rules engine (no JUnit needed): ./build.sh test
 * Each check prints a line; any failure exits with status 1.
 */

private var failures = 0
private var passes = 0

private fun check(name: String, ok: Boolean) {
    if (ok) passes++ else { failures++; println("FAIL  $name") }
}

private fun sit(
    opens: Int = 0, session: Boolean = false, protected: Boolean = false, focusUntil: Long = 0,
    wd: Window? = null, strict: Boolean = false, minute: Int = 12 * 60, recent: Boolean = false,
    isBreak: Boolean = false, morningUntil: Long = 0, checkIns: List<Window> = emptyList(), basic: Boolean = false,
) = Situation(
    checkIns = checkIns, focusIsBasic = basic,
    nowWallMs = 1_000_000L, minuteOfDay = minute, focusUntilWallMs = focusUntil, focusIsBreak = isBreak,
    morningUntilWallMs = morningUntil, windDown = wd,
    windDownStrict = strict, opensToday = opens, hasActiveSession = session, protectedApp = protected,
    recentlyEnded = recent,
)

fun main() {
    val mindful = Rule(Mode.MINDFUL, pauseSec = 6, sessionMin = 5)

    // --- basic modes
    check("normal opens", Policy.decide(Rule(Mode.NORMAL), sit()) == Decision.Open)
    check("essential opens", Policy.decide(Rule(Mode.ESSENTIAL), sit()) == Decision.Open)
    check("mindful pauses", Policy.decide(mindful, sit()) is Decision.Pause)
    check("blocked blocks", (Policy.decide(Rule(Mode.BLOCKED), sit()) as? Decision.Blocked)?.reason == BlockReason.BLOCKED_RULE)

    // --- protected apps and sessions always win
    check("protected beats blocked", Policy.decide(Rule(Mode.BLOCKED), sit(protected = true)) == Decision.Open)
    check("protected beats focus", Policy.decide(Rule(Mode.NORMAL), sit(protected = true, focusUntil = 2_000_000L)) == Decision.Open)
    check("active session opens mindful", Policy.decide(mindful, sit(session = true)) == Decision.Open)
    check("active session opens during focus", Policy.decide(mindful, sit(session = true, focusUntil = 2_000_000L)) == Decision.Open)

    // --- focus
    val f = Policy.decide(Rule(Mode.NORMAL), sit(focusUntil = 2_000_000L))
    check("focus blocks normal", f is Decision.Blocked && f.reason == BlockReason.FOCUS && f.untilWallMs == 2_000_000L)
    check("focus keeps essentials", Policy.decide(Rule(Mode.ESSENTIAL), sit(focusUntil = 2_000_000L)) == Decision.Open)
    check("expired focus ignored", Policy.decide(Rule(Mode.NORMAL), sit(focusUntil = 999_999L)) == Decision.Open)

    // --- breaks and morning quiet
    check("break blocks like focus", (Policy.decide(Rule(Mode.NORMAL), sit(focusUntil = 2_000_000L, isBreak = true)) as? Decision.Blocked)?.reason == BlockReason.BREAK)
    check("basic phone blocks normal", (Policy.decide(Rule(Mode.NORMAL), sit(focusUntil = Long.MAX_VALUE / 2, basic = true)) as? Decision.Blocked)?.reason == BlockReason.BASIC)
    check("basic phone keeps essentials", Policy.decide(Rule(Mode.ESSENTIAL), sit(focusUntil = Long.MAX_VALUE / 2, basic = true)) == Decision.Open)
    check("basic phone keeps protected", Policy.decide(Rule(Mode.BLOCKED), sit(focusUntil = Long.MAX_VALUE / 2, basic = true, protected = true)) == Decision.Open)
    check("break keeps essentials", Policy.decide(Rule(Mode.ESSENTIAL), sit(focusUntil = 2_000_000L, isBreak = true)) == Decision.Open)
    val m = Policy.decide(mindful, sit(morningUntil = 1_500_000L))
    check("morning quiet pauses mindful", m is Decision.Blocked && m.reason == BlockReason.MORNING && m.untilWallMs == 1_500_000L)
    check("morning quiet spares normal", Policy.decide(Rule(Mode.NORMAL), sit(morningUntil = 1_500_000L)) == Decision.Open)
    check("morning quiet over", Policy.decide(mindful, sit(morningUntil = 900_000L)) is Decision.Pause)
    check("session beats morning", Policy.decide(mindful, sit(morningUntil = 1_500_000L, session = true)) == Decision.Open)

    // --- check-in times (batched apps)
    val noonWindows = listOf(Window(12 * 60 + 30, 12 * 60 + 50), Window(18 * 60 + 30, 18 * 60 + 50))
    check("batched opens in window", Policy.decide(Rule(Mode.BATCHED), sit(minute = 12 * 60 + 40, checkIns = noonWindows)) == Decision.Open)
    val bt = Policy.decide(Rule(Mode.BATCHED), sit(minute = 15 * 60, checkIns = noonWindows))
    check("batched blocked outside", bt is Decision.Blocked && bt.reason == BlockReason.BATCH)
    check("batched with no windows blocked", Policy.decide(Rule(Mode.BATCHED), sit(checkIns = emptyList())) is Decision.Blocked)
    check("focus beats check-in window", (Policy.decide(Rule(Mode.BATCHED), sit(minute = 12 * 60 + 40, checkIns = noonWindows, focusUntil = 2_000_000L)) as? Decision.Blocked)?.reason == BlockReason.FOCUS)
    val cc = Calendar.getInstance(); cc.set(2026, Calendar.OCTOBER, 31, 15, 0, 0); cc.set(Calendar.MILLISECOND, 0)
    val ns = Calendar.getInstance(); ns.timeInMillis = Window(18 * 60 + 30, 18 * 60 + 50).nextStartAfter(cc.timeInMillis)
    check("next check-in later today", ns.get(Calendar.DAY_OF_MONTH) == 31 && ns.get(Calendar.HOUR_OF_DAY) == 18 && ns.get(Calendar.MINUTE) == 30)
    ns.timeInMillis = Window(12 * 60 + 30, 12 * 60 + 50).nextStartAfter(cc.timeInMillis)
    check("next check-in tomorrow", ns.get(Calendar.DAY_OF_MONTH) == 1 && ns.get(Calendar.HOUR_OF_DAY) == 12)

    // --- wind-down window (22:30 - 06:30, wraps midnight)
    val wd = Window(22 * 60 + 30, 6 * 60 + 30)
    check("window contains 23:00", wd.contains(23 * 60))
    check("window contains 01:00", wd.contains(60))
    check("window excludes 06:30", !wd.contains(6 * 60 + 30))
    check("window excludes 12:00", !wd.contains(12 * 60))
    check("empty window", !Window(600, 600).contains(600))
    check("day window", Window(9 * 60, 17 * 60).contains(10 * 60) && !Window(9 * 60, 17 * 60).contains(18 * 60))
    check("wind-down blocks mindful", Policy.decide(mindful, sit(wd = wd, minute = 23 * 60)) is Decision.Blocked)
    check("wind-down spares normal", Policy.decide(Rule(Mode.NORMAL), sit(wd = wd, minute = 23 * 60)) == Decision.Open)
    check("strict wind-down blocks normal", Policy.decide(Rule(Mode.NORMAL), sit(wd = wd, minute = 23 * 60, strict = true)) is Decision.Blocked)
    check("wind-down outside window", Policy.decide(mindful, sit(wd = wd, minute = 12 * 60)) is Decision.Pause)

    val c = Calendar.getInstance()
    c.set(2026, Calendar.OCTOBER, 31, 23, 15, 0); c.set(Calendar.MILLISECOND, 0)
    val end = Calendar.getInstance(); end.timeInMillis = wd.endsAfter(c.timeInMillis)
    check("ends next morning", end.get(Calendar.DAY_OF_MONTH) == 1 && end.get(Calendar.HOUR_OF_DAY) == 6 && end.get(Calendar.MINUTE) == 30)
    c.set(2026, Calendar.NOVEMBER, 1, 2, 0, 0)
    end.timeInMillis = wd.endsAfter(c.timeInMillis)
    check("ends same morning", end.get(Calendar.DAY_OF_MONTH) == 1 && end.get(Calendar.HOUR_OF_DAY) == 6)

    // --- pause escalation and limits
    fun pause(opens: Int, recent: Boolean = false) = (Policy.decide(mindful, sit(opens = opens, recent = recent)) as Decision.Pause).pauseMs
    check("first pause = base", pause(0) == 6_000L && pause(1) == 6_000L)
    check("third open +2s", pause(2) == 8_000L)
    check("fifth open +6s", pause(4) == 12_000L)
    check("pause capped at 20s", pause(30) == Policy.MAX_PAUSE_MS)
    check("cooldown adds 5s", pause(0, recent = true) == 11_000L)
    check("no escalation when off", (Policy.decide(mindful.copy(escalate = false), sit(opens = 9)) as Decision.Pause).pauseMs == 6_000L)
    check("limit not reached", !(Policy.decide(mindful.copy(dailyOpenLimit = 5), sit(opens = 4)) as Decision.Pause).limitReached)
    check("limit reached", (Policy.decide(mindful.copy(dailyOpenLimit = 5), sit(opens = 5)) as Decision.Pause).limitReached)
    check("zero limit = none", !(Policy.decide(mindful.copy(dailyOpenLimit = 0), sit(opens = 500)) as Decision.Pause).limitReached)

    // --- sessions
    val b = SessionBook()
    b.admit("insta", 5, "A message", nowElapsed = 0, nowWall = 0)
    check("admitted is active", b.isActive("insta", 0))
    b.start("insta", 1_000)
    check("running counts", b.remainingMs("insta", 61_000) == 4 * 60_000L)
    b.stop("insta", 61_000) // 1 minute used
    check("paused doesn't count", b.remainingMs("insta", 3 * 60_000) == 4 * 60_000L)
    check("short gap keeps session", b.isActive("insta", 61_000 + 9 * 60_000))
    check("long gap ends session", !b.isActive("insta", 61_000 + 11 * 60_000))
    b.start("insta", 100_000)
    check("used up -> inactive", !b.isActive("insta", 100_000 + 4 * 60_000))
    check("extend once", b.extend("insta", SessionBook.EXTENSION_MS, 100_000 + 4 * 60_000))
    check("extension gives 2 min", b.remainingMs("insta", 100_000 + 4 * 60_000) == 2 * 60_000L)
    check("extend only once", !b.extend("insta", SessionBook.EXTENSION_MS, 100_000 + 5 * 60_000))
    check("keeps intention", b["insta"]?.intention == "A message")

    // --- serialization round trip
    b.admit("yt", 10, "Look | something\nup", nowElapsed = 5, nowWall = 7)
    val copy = SessionBook()
    copy.load(b.serialize())
    check("round trip keeps sessions", copy.remainingMs("yt", 5) == 10 * 60_000L && copy["insta"]?.extended == true)
    check("round trip cleans intention", copy["yt"]?.intention == "Look   something up")
    copy.load("garbage|x|y\n||||||||")
    check("bad input ignored", copy.activePackages(0).isEmpty())
    b.prune(100_000 + 60 * 60_000)
    check("prune removes old", b.activePackages(100_000 + 60 * 60_000).isEmpty())

    // --- lessons are complete
    check("lessons have sources", Lessons.all.all { it.source.isNotBlank() && it.body.length > 60 && it.tryThis.isNotBlank() })
    check("lesson ids unique", Lessons.all.map { it.id }.toSet().size == Lessons.all.size)
    check("every topic has lessons", Topic.values().all { Lessons.byTopic(it).size >= 4 })
    check("lesson of the day stable", Lessons.forDay(20000).id == Lessons.forDay(20000).id && Lessons.forDay(-5) != null)

    println("$passes passed, $failures failed")
    if (failures > 0) kotlin.system.exitProcess(1)
}
