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

    // --- protected apps always win; a session wins over everything except focus, breaks and basic phone
    check("protected beats blocked", Policy.decide(Rule(Mode.BLOCKED), sit(protected = true)) == Decision.Open)
    check("protected beats focus", Policy.decide(Rule(Mode.NORMAL), sit(protected = true, focusUntil = 2_000_000L)) == Decision.Open)
    check("active session opens mindful", Policy.decide(mindful, sit(session = true)) == Decision.Open)
    check("focus beats an active session", (Policy.decide(mindful, sit(session = true, focusUntil = 2_000_000L)) as? Decision.Blocked)?.reason == BlockReason.FOCUS)
    check("basic phone beats an active session", (Policy.decide(mindful, sit(session = true, focusUntil = Long.MAX_VALUE / 2, basic = true)) as? Decision.Blocked)?.reason == BlockReason.BASIC)
    check("break beats an active session", (Policy.decide(Rule(Mode.NORMAL), sit(session = true, focusUntil = 2_000_000L, isBreak = true)) as? Decision.Blocked)?.reason == BlockReason.BREAK)
    check("one-time unlock opens blocked", Policy.decide(Rule(Mode.BLOCKED), sit(session = true)) == Decision.Open)
    check("session beats wind-down", Policy.decide(mindful, sit(wd = Window(22 * 60, 6 * 60), minute = 23 * 60, session = true)) == Decision.Open)

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
    check("next wraps around", Quotes.next(Quotes.all.last()) == Quotes.all.first())
    check("a quote every 30 s cycles through all", (0 until Quotes.all.size).map { Quotes.forMoment(it * 30_000L, 30_000L) }.toSet().size == Quotes.all.size)

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
    val flick = Sitting()
    var counted = 0
    for (i in 0 until 20) if (flick.swipe(100_000L + i * 400L)) counted++
    check("20 flicks 400 ms apart are 20 swipes", counted == 20 && flick.swipes == 20)
    val one = Sitting()
    for (t in 0L..500L step 100L) one.swipe(200_000L + t)
    check("one swipe's burst of events is one swipe", one.swipes == 1)
    val run = Sitting()
    for (t in 0L until 3_000L step 100L) run.swipe(300_000L + t)
    check("3 s of non-stop scrolling is several swipes", run.swipes == 3)
    val seen = Sitting()
    seen.swipe(400_000); seen.swipe(400_100)
    check("the new video a scroll brought in isn't counted twice", !seen.newVideo(400_400) && seen.swipes == 1)
    check("a new video no scroll counted is counted", seen.newVideo(402_000) && seen.swipes == 2)
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
