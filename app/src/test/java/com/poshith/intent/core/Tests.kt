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
    strictMode: Boolean = false, override: Boolean = false,
) = Situation(
    checkIns = checkIns, focusIsBasic = basic, strictMode = strictMode, sessionOverride = override,
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

    // --- protected apps always win; a session wins over everything except focus, breaks and basic phone
    check("protected beats blocked", Policy.decide(Rule(Mode.BLOCKED), sit(protected = true)) == Decision.Open)
    check("protected beats focus", Policy.decide(Rule(Mode.NORMAL), sit(protected = true, focusUntil = 2_000_000L)) == Decision.Open)
    check("active session opens mindful", Policy.decide(mindful, sit(session = true)) == Decision.Open)
    check("focus beats an active session", (Policy.decide(mindful, sit(session = true, focusUntil = 2_000_000L)) as? Decision.Blocked)?.reason == BlockReason.FOCUS)
    check("basic phone beats an active session", (Policy.decide(mindful, sit(session = true, focusUntil = Long.MAX_VALUE / 2, basic = true)) as? Decision.Blocked)?.reason == BlockReason.BASIC)
    check("break beats an active session", (Policy.decide(Rule(Mode.NORMAL), sit(session = true, focusUntil = 2_000_000L, isBreak = true)) as? Decision.Blocked)?.reason == BlockReason.BREAK)
    check("one-time unlock opens blocked", Policy.decide(Rule(Mode.BLOCKED), sit(session = true)) == Decision.Open)
    check("gentle wind-down lets a running session finish", Policy.decide(mindful, sit(wd = Window(22 * 60, 6 * 60), minute = 23 * 60, session = true)) == Decision.Open)

    // --- firm boundaries: in Strict mode (or strict wind-down) quiet hours end an ordinary session;
    //     a session unlocked past a stop on purpose keeps its minutes
    val night = Window(22 * 60, 6 * 60)
    check("strict mode: wind-down ends a running session", (Policy.decide(mindful, sit(wd = night, minute = 23 * 60, session = true, strictMode = true)) as? Decision.Blocked)?.reason == BlockReason.WIND_DOWN)
    check("strict wind-down ends a running session", (Policy.decide(mindful, sit(wd = night, minute = 23 * 60, session = true, strict = true)) as? Decision.Blocked)?.reason == BlockReason.WIND_DOWN)
    check("an unlock past a stop survives firm wind-down", Policy.decide(mindful, sit(wd = night, minute = 23 * 60, session = true, strictMode = true, override = true)) == Decision.Open)
    check("a one-time unlock of a blocked app survives firm wind-down", Policy.decide(Rule(Mode.BLOCKED), sit(wd = night, minute = 23 * 60, session = true, strict = true, override = true)) == Decision.Open)
    check("strict mode: morning quiet ends a running session", (Policy.decide(mindful, sit(morningUntil = 1_500_000L, session = true, strictMode = true)) as? Decision.Blocked)?.reason == BlockReason.MORNING)
    check("strict mode: a running session outside quiet hours goes on", Policy.decide(mindful, sit(wd = night, minute = 12 * 60, session = true, strictMode = true)) == Decision.Open)
    check("strict mode still pauses at the start of wind-down", (Policy.decide(mindful, sit(wd = night, minute = 23 * 60, strictMode = true)) as? Decision.Blocked)?.reason == BlockReason.WIND_DOWN)
    check("check-in apps rest in wind-down too", (Policy.decide(Rule(Mode.BATCHED), sit(wd = night, minute = 23 * 60, checkIns = listOf(Window(22 * 60 + 30, 23 * 60 + 30)))) as? Decision.Blocked)?.reason == BlockReason.WIND_DOWN)
    check("check-in apps open in their window outside wind-down", Policy.decide(Rule(Mode.BATCHED), sit(wd = night, minute = 18 * 60 + 40, checkIns = listOf(Window(18 * 60 + 30, 18 * 60 + 50)))) == Decision.Open)
    check("gentle wind-down still spares normal apps", Policy.decide(Rule(Mode.NORMAL), sit(wd = night, minute = 23 * 60, strictMode = true)) == Decision.Open)

    // --- which changes make an app easier to open (those wait)
    check("blocked to essential is looser", Mode.ESSENTIAL.isLooserThan(Mode.BLOCKED))
    check("blocked to mindful is looser", Mode.MINDFUL.isLooserThan(Mode.BLOCKED))
    check("mindful to blocked is stricter", !Mode.BLOCKED.isLooserThan(Mode.MINDFUL))
    check("same mode isn't looser", !Mode.MINDFUL.isLooserThan(Mode.MINDFUL))

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
    check("a real break pauses even essentials", (Policy.decide(Rule(Mode.ESSENTIAL), sit(focusUntil = 2_000_000L, isBreak = true)) as? Decision.Blocked)?.reason == BlockReason.BREAK)
    check("a real break keeps calls (protected)", Policy.decide(Rule(Mode.ESSENTIAL), sit(focusUntil = 2_000_000L, isBreak = true, protected = true)) == Decision.Open)
    check("a one-time unlock doesn't beat a break", (Policy.decide(Rule(Mode.BLOCKED), sit(focusUntil = 2_000_000L, isBreak = true, session = true)) as? Decision.Blocked)?.reason == BlockReason.BREAK)
    check("focus still keeps essentials", Policy.decide(Rule(Mode.ESSENTIAL), sit(focusUntil = 2_000_000L)) == Decision.Open)
    val m = Policy.decide(mindful, sit(morningUntil = 1_500_000L))
    check("morning quiet pauses mindful", m is Decision.Blocked && m.reason == BlockReason.MORNING && m.untilWallMs == 1_500_000L)
    check("morning quiet spares normal", Policy.decide(Rule(Mode.NORMAL), sit(morningUntil = 1_500_000L)) == Decision.Open)
    check("morning quiet over", Policy.decide(mindful, sit(morningUntil = 900_000L)) is Decision.Pause)
    check("gentle morning quiet lets a running session finish", Policy.decide(mindful, sit(morningUntil = 1_500_000L, session = true)) == Decision.Open)

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

    // --- schedule changes: which ones loosen (those wait, and Strict mode keeps them for tomorrow)
    val wdOld = Window(22 * 60 + 30, 6 * 60 + 30)
    check("an earlier, longer wind-down is stricter", !Schedule.windDownLooser(wdOld, Window(22 * 60, 7 * 60)))
    check("a later wind-down start is looser", Schedule.windDownLooser(wdOld, Window(23 * 60, 6 * 60 + 30)))
    check("an earlier wind-down end is looser", Schedule.windDownLooser(wdOld, Window(22 * 60 + 30, 6 * 60)))
    check("start == end covers nothing: looser", Schedule.windDownLooser(wdOld, Window(22 * 60 + 30, 22 * 60 + 30)))
    check("no longer crossing midnight is looser", Schedule.windDownLooser(wdOld, Window(21 * 60, 23 * 60 + 59)))
    check("turning wind-down off is looser", Schedule.windDownLooser(wdOld, null))
    check("turning it on is stricter", !Schedule.windDownLooser(null, wdOld))
    check("from an empty window anything is stricter", !Schedule.windDownLooser(Window(600, 600), Window(60, 120)))
    check("the same window isn't looser", !Schedule.windDownLooser(wdOld, Window(22 * 60 + 30, 6 * 60 + 30)))
    val twoTimes = Schedule.checkIns(listOf(12 * 60 + 30, 18 * 60 + 30), 20)
    check("three check-ins instead of two is looser", Schedule.checkInsLooser(twoTimes, Schedule.checkIns(listOf(9 * 60, 13 * 60, 18 * 60), 20)))
    check("one check-in of the two is stricter", !Schedule.checkInsLooser(twoTimes, Schedule.checkIns(listOf(18 * 60 + 30), 20)))
    check("a shorter check-in is stricter", !Schedule.checkInsLooser(twoTimes, Schedule.checkIns(listOf(12 * 60 + 30, 18 * 60 + 30), 15)))
    check("a longer check-in is looser", Schedule.checkInsLooser(twoTimes, Schedule.checkIns(listOf(12 * 60 + 30, 18 * 60 + 30), 30)))
    check("a check-in moved is looser", Schedule.checkInsLooser(twoTimes, Schedule.checkIns(listOf(12 * 60 + 30, 19 * 60), 20)))
    val lateCheck = Schedule.checkIns(listOf(23 * 60 + 50), 20)
    check("check-ins across midnight: longer is looser", Schedule.checkInsLooser(lateCheck, Schedule.checkIns(listOf(23 * 60 + 50), 30)))
    check("check-ins across midnight: inside is stricter", !Schedule.checkInsLooser(lateCheck, listOf(Window(23 * 60 + 55, 5))))
    check("check-ins from none is looser", Schedule.checkInsLooser(emptyList(), twoTimes))
    check("budget: more minutes or none is looser", Limits.budgetLooser(60, 90) && Limits.budgetLooser(60, 0))
    check("budget: fewer minutes, or one from none, is stricter", !Limits.budgetLooser(60, 45) && !Limits.budgetLooser(0, 45) && !Limits.budgetLooser(60, 60))
    check("override reasons are known", SessionBook.isOverride(SessionBook.WHY_CODE) && SessionBook.isOverride("One-time unlock") &&
        !SessionBook.isOverride("Reply to someone") && !SessionBook.isOverride("") && !SessionBook.isOverride(null))

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

    // --- an ended session stays ended (stop() on a stopped session must not restart its gap)
    val e = SessionBook()
    e.admit("x", 5, "", nowElapsed = 0, nowWall = 0)
    e.start("x", 0); e.stop("x", 60_000)
    check("ended by the gap", !e.isActive("x", 60_000 + 11 * 60_000))
    e.stop("x", 60_000 + 11 * 60_000) // Focus Guard leaving the app as the pause appears
    check("stop doesn't revive an ended session", !e.isActive("x", 60_000 + 12 * 60_000))

    // --- without Focus Guard, minutes run on the clock from the moment you chose them
    val w = SessionBook()
    w.admit("two", 2, "", nowElapsed = 0, nowWall = 0, runningNow = true)
    w.admit("thirty", 30, "", nowElapsed = 0, nowWall = 0, runningNow = true)
    check("2-min session active at 1 min", w.isActive("two", 60_000))
    check("2-min session over at 3 min", !w.isActive("two", 3 * 60_000))
    check("30-min session still active at 11 min", w.isActive("thirty", 11 * 60_000))
    check("30-min session over at 31 min", !w.isActive("thirty", 31 * 60_000))

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

    // --- foreground time from usage events
    val t = ForegroundTime(windowStart = 0)
    t.resumed("app", "A", 0)
    t.paused("app", "A", 60_000)      // moving to another screen of the same app:
    t.resumed("app", "B", 60_000)     //   A paused, B resumed, (A stopped is ignored)
    t.paused("app", "B", 120_000)
    t.resumed("other", "X", 130_000)
    t.allPaused(190_000)              // screen off
    t.paused("late", "Y", 500_000)    // first event is a pause: open since the window began
    val totals = t.finish(600_000)
    check("screens of one app add up", totals["app"] == 120_000L)
    check("screen off ends time", totals["other"] == 60_000L)
    check("open since the start counts", totals["late"] == 500_000L)
    val two = ForegroundTime(0)
    two.resumed("a", "1", 0); two.resumed("a", "2", 10_000); two.paused("a", "1", 20_000); two.paused("a", "2", 50_000)
    check("overlapping screens count once", two.finish(60_000)["a"] == 50_000L)

    // --- quotes: short, distinct, every one reachable
    check("quotes are short", Quotes.all.all { it.text.length in 12..130 })
    check("quotes are distinct", Quotes.all.map { it.text }.toSet().size == Quotes.all.size)
    check("a quote a day cycles through all", (0 until Quotes.all.size).map { Quotes.forDay(it) }.toSet().size == Quotes.all.size)
    check("quotes keep their notes short", Quotes.all.all { it.note.length <= 200 })
    val dayMs = 86_400_000L
    check("the quote's day turns over at local midnight", Quotes.localDay(dayMs - 1, 0) == 0 && Quotes.localDay(dayMs, 0) == 1 &&
        Quotes.localDay(dayMs - 3_600_000L, 3_600_000) == 1)
    check("a pinned quote stays for its week", Quotes.today(5, 2, 10_000L, 9_000L) == Quotes.all[2])
    check("after its week it's one a day again", Quotes.today(5, 2, 10_000L, 10_000L) == Quotes.forDay(5) && Quotes.today(5, -1, 0L, 0L) == Quotes.forDay(5))

    // --- Shorts and Reels: which apps, which scrolls count
    check("youtube is shorts", Feed.of("com.google.android.youtube") == Feed.SHORTS)
    check("instagram is reels", Feed.of("com.instagram.android") == Feed.REELS)
    check("other apps aren't feeds", Feed.of("com.whatsapp") == null && Feed.of(null) == null)
    check("short id", FeedIds.short("com.google.android.youtube:id/reel_recycler") == "reel_recycler" && FeedIds.short(null) == "")
    check("shorts pager swipe", FeedIds.isFeedSwipe(Feed.SHORTS, listOf("reel_recycler", "reel_watch_fragment_root")))
    check("shorts player parent counts", FeedIds.isFeedSwipe(Feed.SHORTS, listOf("", "reel_player_page_container")))
    check("shorts comments don't count", !FeedIds.isFeedSwipe(Feed.SHORTS, listOf("comments_list", "reel_watch_fragment_root")))
    check("shorts sheet doesn't count", !FeedIds.isFeedSwipe(Feed.SHORTS, listOf("list", "bottom_sheet_container", "reel_watch_fragment_root")))
    check("youtube home feed doesn't count", !FeedIds.isFeedSwipe(Feed.SHORTS, listOf("results", "section_list")))
    check("reels pager swipe", FeedIds.isFeedSwipe(Feed.REELS, listOf("clips_viewer_view_pager")))
    check("reels comments don't count", !FeedIds.isFeedSwipe(Feed.REELS, listOf("recycler_view_container_id", "clips_viewer_view_pager")))
    check("instagram feed doesn't count", !FeedIds.isFeedSwipe(Feed.REELS, listOf("list", "refreshable_container")))
    check("ids are per app", !FeedIds.isFeedSwipe(Feed.REELS, listOf("reel_recycler")))

    // --- a sitting: the first swipe asks, "N more" lets N through, then it asks again
    val st = Sitting()
    check("first swipe is new", st.swipe(10_000))
    check("first swipe asks", st.ask() == Sitting.Ask.START)
    check("the same swipe settling isn't new", !st.swipe(10_300) && st.swipes == 1)
    check("still asks until you choose", st.swipe(12_000) && st.ask() == Sitting.Ask.START)
    check("no wait the first time", st.waitSec() == 0)
    st.keepGoing(3)
    check("watching more: quiet", st.swipe(20_000) && st.ask() == Sitting.Ask.NONE)
    st.swipe(30_000); st.swipe(40_000)
    check("allowance used: still quiet", st.ask() == Sitting.Ask.NONE && st.swipes == 5)
    check("one past it asks again", st.swipe(50_000) && st.ask() == Sitting.Ask.MORE)
    check("the wait grows", st.waitSec() == 5)
    st.keepGoing(3); st.keepGoing(3); st.keepGoing(3); st.keepGoing(3)
    check("the wait stops at 20 s", st.waitSec() == 20)
    check("a few quiet minutes start over", st.swipe(50_000 + Sitting.GAP_MS + 1) && st.swipes == 1 && st.ask() == Sitting.Ask.START)
    val quick = Sitting()
    quick.swipe(0); quick.keepGoing(10); quick.swipe(60_000)
    check("leaving for a minute doesn't start over", quick.swipes == 2 && quick.ask() == Sitting.Ask.NONE)

    // --- counting swipes: fast flicking counts every swipe, one swipe's events count once
    // 20 flicks 400 ms apart, each bringing in a new video a moment later (what the guard sees)
    val flick = Sitting()
    for (i in 0 until 20) {
        val t = 100_000L + i * 400L
        flick.swipe(t); flick.swipe(t + 100); flick.swipe(t + 200)
        flick.newVideo(t + 300)
    }
    check("20 fast flicks are 20 swipes", flick.swipes == 20)
    val blind = Sitting()
    for (i in 0 until 20) blind.swipe(150_000L + i * 400L)
    check("fast flicks with no video in sight still count, not just the first", blind.swipes >= 4)
    val one = Sitting()
    for (t in 0L..500L step 100L) one.swipe(200_000L + t)
    check("one swipe's burst of events is one swipe", one.swipes == 1)
    val slow = Sitting()
    slow.swipe(250_000); slow.swipe(250_450); slow.swipe(250_900); slow.newVideo(251_300)
    check("a slow phone's swipe, settling half a second later, is one swipe", slow.swipes == 1)
    val run = Sitting()
    for (t in 0L until 3_000L step 100L) run.swipe(300_000L + t)
    check("3 s of non-stop scrolling is more than one swipe", run.swipes == 2)
    val seen = Sitting()
    seen.swipe(400_000); seen.swipe(400_100)
    check("the new video a scroll brought in isn't counted twice", !seen.newVideo(400_400) && seen.swipes == 1)
    check("a new video no scroll counted is counted", seen.newVideo(403_000) && seen.swipes == 2)
    seen.swipe(410_000)
    seen.newVideo(410_300)
    check("each scroll matches one new video only", seen.newVideo(410_900) && seen.swipes == 4)
    val pages = Sitting()
    check("seen on screen only: the first new video asks", pages.newVideo(500_000) && pages.ask() == Sitting.Ask.START)
    pages.keepGoing(2)
    pages.newVideo(505_000); pages.newVideo(510_000)
    check("seen on screen only: asks again after the allowance", pages.newVideo(515_000) && pages.ask() == Sitting.Ask.MORE)

    // --- one budget and a short rest, across all mindful apps
    val min = 60_000L
    check("no budget: no limit", Limits.budgetLeft(0, 500) == null)
    check("budget counts every session's minutes", Limits.budgetLeft(60, 45) == 15 && Limits.budgetLeft(60, 75) == 0)
    check("only lengths that fit are offered", Limits.sessionChoices(listOf(2, 5, 10, 15), 12) == listOf(2, 5, 10))
    check("less than the shortest: what's left", Limits.sessionChoices(listOf(2, 5, 10), 1) == listOf(1))
    check("nothing left: nothing offered", Limits.sessionChoices(listOf(2, 5), 0).isEmpty())
    check("two sessions in 10 min: no rest", Limits.restUntil(listOf(0L, 3 * min), 6 * min, 0L) == 0L)
    check("three sessions in 10 min: a 5 min rest", Limits.restUntil(listOf(0L, 3 * min, 5 * min), 7 * min, 0L) == 12 * min)
    check("a rest that's on stays until it ends", Limits.restUntil(listOf(0L, 1 * min, 2 * min), 9 * min, 12 * min) == 12 * min)
    check("sessions spread out: no rest", Limits.restUntil(listOf(0L, 6 * min, 12 * min), 13 * min, 0L) == 0L)
    check("one rest doesn't chain into the next", Limits.restUntil(listOf(1 * min, 2 * min, 4 * min), 10 * min, 9 * min) == 0L)

    // --- Strict mode: no extensions, looser from tomorrow, locks that grow, an emergency code
    val h = 60 * min
    check("first stop only closes", Strict.lockUntil(1, 0L, 10 * h) == 0L)
    check("second stop locks 30 min", Strict.lockUntil(2, 0L, 10 * h) == 30 * min)
    check("third stop locks 2 h", Strict.lockUntil(3, 0L, 10 * h) == 2 * h)
    check("fourth stop: the rest of the day", Strict.lockUntil(4, 0L, 10 * h) == 10 * h)
    check("late at night the day lock still lasts 2 h", Strict.lockUntil(4, 0L, 5 * min) == 2 * h)
    check("looser applies from the chosen day", Strict.due("2026-11-04", "2026-11-04") && Strict.due("2026-11-05", "2026-11-04"))
    check("looser doesn't apply before it", !Strict.due("2026-11-03", "2026-11-04") && !Strict.due("2026-11-03", ""))
    check("2 h a day is 1 in 8 waking hours", Strict.oneInWakingHours(120) == 8)
    check("2 h a day is 46 waking days a year", Strict.wakingDaysPerYear(120) == 46)
    val code = Strict.newCode(java.util.Random(7))
    check("code looks like XXXX-XXXX", Regex("[A-Z2-9]{4}-[A-Z2-9]{4}").matches(code))
    check("code has no look-alike characters", code.none { it in "01OIL" })
    val stored = Strict.hash(code)
    check("the code matches, typed loosely", Strict.matches(code.lowercase().replace("-", " "), stored))
    check("a wrong code doesn't", !Strict.matches("AAAA-AAAA", stored) && !Strict.matches("", stored) && !Strict.matches(code, ""))
    check("only a hash is kept", stored.length == 64 && !stored.contains(Strict.normalize(code)))
    val eod = 24 * h
    check("used up: open tomorrow, not when a short lock ends", Strict.opensAt(true, 10 * h + 30 * min, 10 * h, eod) == eod)
    check("used up, a lock past midnight: open when it ends", Strict.opensAt(true, eod + h, 23 * h, eod) == eod + h)
    check("used up, no lock: tomorrow", Strict.opensAt(true, 0L, 10 * h, eod) == eod)
    check("not used up, locked: when the lock ends", Strict.opensAt(false, 11 * h, 10 * h, eod) == 11 * h)
    check("not used up, an old lock: open now", Strict.opensAt(false, 9 * h, 10 * h, eod) == 0L)
    check("opens again: words", Strict.whenWords(eod, eod) { "T" } == "tomorrow" && Strict.whenWords(eod + h, eod) { "01:00" } == "tomorrow at 01:00" &&
        Strict.whenWords(11 * h, eod) { "11:00" } == "at 11:00")
    check("what's left never goes below zero", Strict.left(5, 3) == 2 && Strict.left(5, 9) == 0 && Strict.left(0, 0) == 0)
    check("the lock words say what's next", Strict.nextLockWords(1).contains("30 minutes") && Strict.nextLockWords(2).contains("2 hours") &&
        Strict.nextLockWords(3).contains("rest of the day"))
    check("blunt lines are short and distinct", Quotes.blunt.all { it.length in 8..90 } && Quotes.blunt.toSet().size == Quotes.blunt.size)

    // --- what's pulling you: the pull check, urge rides, how sessions felt
    check("pull ids are unique and stable", Pull.values().map { it.id }.toSet().size == Pull.values().size &&
        Pull.byId("bored") == Pull.BORED && Pull.byId("nope") == null && Pull.byId(null) == null)
    check("every pull has a short reply, plan words and ideas", Pull.pulls.all {
        it.reply.length in 30..140 && it.ideas.size >= 3 && it.whenWords.startsWith("When ") && it.youWords.startsWith("when you")
    })
    check("'something specific' is a reason, not a pull", !Pull.PURPOSE.isPull && Pull.PURPOSE !in Pull.pulls && Pull.PURPOSE.reply.isNotBlank())
    check("chip labels are short", Pull.values().all { it.label.length <= 22 })
    val pday = Pulls.DAY_MS
    val now0 = 100 * pday
    val notes = listOf(
        PullNote(now0 - 1_000, Pull.BORED, "a", false),
        PullNote(now0 - 2_000, Pull.BORED, "a", true),
        PullNote(now0 - 3_000, Pull.STRESSED, "b", false),
        PullNote(now0 - 4_000, Pull.PURPOSE, "a", true),
        PullNote(now0 - 40 * pday, Pull.LONELY, "a", false),
    )
    check("named pulls survive storage", Pulls.decodeNotes(Pulls.encodeNotes(notes)).let { d ->
        d.size == 5 && d[0].pull == Pull.BORED && d[0].pkg == "a" && !d[0].opened && d[1].opened && d[3].pull == Pull.PURPOSE
    })
    check("a broken line is skipped, not fatal", Pulls.decodeNotes("x|bored|a|1\n5|nope|a|1\n7|bored|a\n9|habit|b|0").let { it.size == 1 && it[0].pull == Pull.HABIT } &&
        Pulls.decodeNotes(null).isEmpty() && Pulls.decodeNotes("").isEmpty())
    val since30 = now0 - Pulls.WINDOW_MS
    val topPulls = Pulls.top(notes, since30)
    check("most common pull first; 'specific' and old ones left out", topPulls.size == 2 && topPulls[0].pull == Pull.BORED && topPulls[0].n == 2 && topPulls[1].pull == Pull.STRESSED)
    check("ties keep the usual order", Pulls.top(listOf(PullNote(1, Pull.TIRED, "a", false), PullNote(2, Pull.BORED, "a", false)), 0).map { it.pull } == listOf(Pull.BORED, Pull.TIRED))
    check("headline names the top two", Pulls.headline(topPulls) == "Most often: bored, then stressed" && Pulls.headline(emptyList()) == null &&
        Pulls.headline(topPulls.take(1)) == "Most often: bored")
    check("let it pass counts real pulls only", Pulls.letPass(notes, since30).let { it.passed == 2 && it.total == 3 })
    val hourOf = { t: Long -> ((t / 3_600_000L) % 24).toInt() }
    fun atHour(hr: Int) = 10 * pday + hr * 3_600_000L
    val evening = List(5) { PullNote(atHour(21) + it, Pull.BORED, "a", false) } + listOf(PullNote(atHour(22), Pull.TIRED, "a", false), PullNote(atHour(9), Pull.HABIT, "a", false))
    check("the busiest two hours", Pulls.peakHour(evening, 0, hourOf) == 21)
    check("they can wrap past midnight", Pulls.peakHour(List(4) { PullNote(atHour(23), Pull.BORED, "a", false) } + List(3) { PullNote(atHour(0), Pull.BORED, "a", false) }, 0, hourOf) == 23)
    check("too few pulls: no peak", Pulls.peakHour(evening.take(5), 0, hourOf) == null)
    check("spread out evenly: no peak", Pulls.peakHour(List(12) { PullNote(atHour(it * 2), Pull.BORED, "a", false) }, 0, hourOf) == null)

    val rides = listOf(UrgeRide(now0 - 10, Pull.BORED, 4, 2, UrgeRide.LET_PASS), UrgeRide(now0 - 20, null, 3, 3, UrgeRide.OPENED),
        UrgeRide(now0 - 30, Pull.LOW, 0, 0, UrgeRide.PRACTICE), UrgeRide(now0 - 40 * pday, Pull.LOW, 5, 1, UrgeRide.LET_PASS))
    check("rides survive storage", Pulls.decodeRides(Pulls.encodeRides(rides)).let { d ->
        d.size == 4 && d[1].pull == null && d[0].before == 4 && d[0].after == 2 && d[0].outcome == UrgeRide.LET_PASS
    })
    check("ride ratings stay within 0 to 5", Pulls.decodeRides("1|bored|9|-3|1").let { it.size == 1 && it[0].before == 5 && it[0].after == 0 })
    val rideSum = Pulls.rides(rides, since30)
    check("ride summary counts rated rides only for 'lower'", rideSum.rides == 3 && rideSum.rated == 2 && rideSum.lower == 1 && rideSum.passed == 1)
    check("ride line", Pulls.rideLine(rideSum) == "You rode out 3 urges; the pull was lower afterwards 1 of 2 times." &&
        Pulls.rideLine(Pulls.rides(emptyList(), 0)) == null && Pulls.rideLine(Pulls.rides(rides.take(1), 0)) == "You rode out 1 urge; the pull was lower afterwards.")
    check("ride words are honest about the urge", Pulls.rideWords(4, 2).startsWith("From 4 to 2") && Pulls.rideWords(3, 3).startsWith("Still 3") &&
        Pulls.rideWords(2, 4).startsWith("Up to 4") && Pulls.rideWords(0, 0).contains("still choose"))
    check("the scale has five steps", Pulls.SCALE.size == 5)

    val checks = listOf(AfterCheck(now0 - 5 * pday, "ig", -1), AfterCheck(now0 - 4 * pday, "ig", -1), AfterCheck(now0 - 3 * pday, "ig", 0),
        AfterCheck(now0 - 2 * pday, "ig", -1), AfterCheck(now0 - pday, "yt", 1))
    check("after-session answers survive storage", Pulls.decodeAfter(Pulls.encodeAfter(checks)).let { d -> d.size == 5 && d[0].feel == -1 && d[4].pkg == "yt" } &&
        Pulls.decodeAfter("1|ig|7\n2|ig|1").size == 1)
    check("how sessions felt, added up", Pulls.felt(checks, since30).let { it.better == 1 && it.same == 1 && it.worse == 3 && it.total == 5 } &&
        Pulls.felt(checks, since30, "yt").total == 1)
    check("the app you felt worse after: 3 answers or more, mostly worse", Pulls.worstApp(checks, since30)?.let { it.pkg == "ig" && it.worse == 3 && it.total == 4 } == true &&
        Pulls.worstApp(checks.takeLast(2), since30) == null)
    check("the pause remembers sessions that felt worse", Pulls.afterLine(checks, "ig", now0) == "You felt worse after 3 of your last 4 sessions here.")
    check("and stays quiet otherwise", Pulls.afterLine(checks, "yt", now0) == null && Pulls.afterLine(checks.take(2), "ig", now0) == null &&
        Pulls.afterLine(listOf(AfterCheck(1, "x", -1), AfterCheck(2, "x", 1), AfterCheck(3, "x", 0), AfterCheck(4, "x", -1)), "x", 10) == null)
    check("worse each time", Pulls.afterLine(List(3) { AfterCheck(now0 - it, "x", -1) }, "x", now0) == "You felt worse after each of your last 3 sessions here.")
    check("answers older than 30 days don't count", Pulls.afterLine(List(4) { AfterCheck(now0 - 31 * pday - it, "x", -1) }, "x", now0) == null)
    val s0 = 1_000_000_000L
    check("after a session: wait out the first minute", Pulls.afterAsk(s0, s0 + 30_000, 0) == AfterAsk.WAIT)
    check("then ask", Pulls.afterAsk(s0, s0 + 5 * 60_000, 0) == AfterAsk.ASK)
    check("but not hours later", Pulls.afterAsk(s0, s0 + 4 * 3_600_000L, 0) == AfterAsk.DROP)
    check("at most three times a day", Pulls.afterAsk(s0, s0 + 5 * 60_000, 3) == AfterAsk.DROP && Pulls.afterAsk(s0, s0 + 5 * 60_000, 2) == AfterAsk.ASK)
    check("no session, or the clock went back: let it go", Pulls.afterAsk(0, s0, 0) == AfterAsk.DROP && Pulls.afterAsk(s0, s0 - 10 * 60_000, 0) == AfterAsk.DROP)
    check("keeps 60 days, and the newest", Pulls.keep(listOf(1L, 2L, 3L, 4L), { it }, Pulls.KEEP_MS + 2, 10) == listOf(3L, 4L) &&
        Pulls.keep((1L..10L).toList(), { it }, 20, 3) == listOf(8L, 9L, 10L))
    check("a plan reads as one sentence", Pulls.planLine(Pull.BORED, " read one page. ") == "When I'm bored, I'll read one page.")
    val dayOf = { t: Long -> (t / pday).toString() }
    check("low on 5 different days lately", Pulls.lowOften(List(5) { PullNote(now0 - it * pday - 1, Pull.LOW, "a", false) }, now0, dayOf))
    check("not when it's the same day, or long ago", !Pulls.lowOften(List(9) { PullNote(now0 - it, Pull.LOW, "a", false) }, now0, dayOf) &&
        !Pulls.lowOften(List(5) { PullNote(now0 - (20 + it) * pday, Pull.LOW, "a", false) }, now0, dayOf))

    // --- lessons are complete
    check("lessons have sources", Lessons.all.all { it.source.isNotBlank() && it.body.length > 60 && it.tryThis.isNotBlank() })
    check("lesson ids unique", Lessons.all.map { it.id }.toSet().size == Lessons.all.size)
    check("every topic has lessons", Topic.values().all { Lessons.byTopic(it).size >= 4 })
    check("every lesson starts with a guess", Lessons.all.all { Guesses.forLesson(it.id) != null })
    check("guesses have 2 or 3 distinct options and a valid answer", Lessons.all.mapNotNull { Guesses.forLesson(it.id) }.all { g ->
        g.options.size in 2..3 && g.options.toSet().size == g.options.size && g.answer in g.options.indices && g.question.length in 10..140
    })
    check("lesson of the day stable", Lessons.forDay(20000).id == Lessons.forDay(20000).id && Lessons.forDay(-5) != null)

    println("$passes passed, $failures failed")
    if (failures > 0) kotlin.system.exitProcess(1)
}
