package com.poshith.intent.core

/*
 * "What's pulling you?" Mindfulness for the moment your hand reaches for a feed.
 *
 * The pause asks what's behind the reach. Naming it comes first in everything here: putting a feeling
 * into words calms the brain's alarm response (Lieberman et al., 2007), and people who tell their bad
 * feelings apart act on them less (Kashdan, Barrett & McKnight, 2015). The reason matters as much as the
 * reach: using the phone to escape bad feelings went with more anxiety and depression, using it out of
 * boredom didn't (Panova & Lleras, 2016). So each pull gets its own answer (something that meets the
 * actual need) and its own if-then plan: plans keyed to a personal reason cut a habit where plans keyed
 * to a place and time didn't (Adriaanse, de Ridder & de Wit, 2009).
 *
 * For a strong pull there's RAIN (Recognize, Allow, Investigate, Nurture: Michele McDonald, Tara Brach),
 * how mindfulness training meets a craving: noticing it with curiosity loosened the link between craving
 * and smoking (Brewer et al., 2011; Elwafi et al., 2013). After a session, how it actually felt: paying
 * attention to what a habit really gives you is how its reward gets re-learned (Ludwig, Brown & Brewer,
 * 2020). Nothing here diagnoses anything, scores you, or keeps a streak.
 *
 * This file is the logic only, with no Android in it, so it's covered by the unit tests. The answers are
 * kept on the phone (data/Noticed) for 60 days.
 */

enum class Pull(
    val id: String,
    val label: String,
    /** The "if" of its plan: "When I'm bored". */
    val whenWords: String,
    /** The same, said to you: "when you're bored". */
    val youWords: String,
    /** What the pause says once you've named it: short, kind, and true to the research. */
    val reply: String,
    val action: Action,
    /** Ideas for its plan's "then" part. */
    val ideas: List<String>,
) {
    BORED("bored", "Bored", "When I'm bored", "when you're bored",
        "Boredom isn't an emergency, and it's often where ideas start. Give it two minutes, or open it on purpose.",
        Action.RIDE, listOf("stand up and stretch", "read one page of a book", "look out of a window for a minute",
            "do my one thing for two minutes", "tidy one small thing")),
    STRESSED("stressed", "Stressed", "When I'm stressed", "when you're stressed",
        "A feed rarely lowers stress. A long, slow breath out often does, and so does a short walk.",
        Action.SIGHS, listOf("take five long, slow breaths out", "step outside for a minute", "drink a glass of water",
            "write down what's worrying me", "walk to the end of the street")),
    LOW("low", "Low", "When I feel low", "when you feel low",
        "Go gently. Scrolling rarely lifts a low mood; a person, some daylight or one small thing you enjoy usually helps more.",
        Action.RIDE, listOf("message someone I like", "go outside for five minutes", "put on a song I love",
            "make a cup of tea", "write one kind line to myself")),
    LONELY("lonely", "Lonely", "When I'm lonely", "when you're lonely",
        "Scrolling isn't connecting. A call to one person tends to feel better than we expect, and less awkward.",
        Action.CALL, listOf("call one person", "send a voice note to a friend", "message someone I haven't talked to in a while",
            "go where people are", "plan to meet someone this week")),
    TIRED("tired", "Tired", "When I'm tired", "when you're tired",
        "Your brain is asking for rest, not more input. Close your eyes for a minute, or go to bed a little earlier.",
        Action.REST, listOf("close my eyes for two minutes", "put the phone in another room", "go to bed",
            "get some fresh air", "lie down without the phone")),
    AVOIDING("avoiding", "Putting something off", "When I'm putting something off", "when you're putting something off",
        "Putting it off is a way to feel better right now. Start it for just two minutes: starting is the hard part.",
        Action.ONE_THING, listOf("do two minutes of it", "write down the very first step", "open the file and leave it open",
            "set a ten-minute timer and start", "tell someone I'm starting")),
    HABIT("habit", "Just checking", "When I check out of habit", "when you check out of habit",
        "Habit, not need. Nothing new needs you right now: let this one pass.",
        Action.NONE, listOf("put the phone face down", "take one breath and look up", "check at a set time instead",
            "put it in my bag", "start a focus hour")),
    PURPOSE("purpose", "Something specific", "", "",
        "Good. Decide what you're looking for, and come back out once you've found it.",
        Action.NONE, emptyList());

    /** The one thing the pause offers after the reply ("Not now" is always there too). */
    enum class Action { RIDE, SIGHS, CALL, REST, ONE_THING, NONE }

    /** A pull worth a plan and a place in your patterns. "Something specific" is a reason, not a pull. */
    val isPull: Boolean get() = this != PURPOSE

    /** Feelings to escape, where riding the urge is worth offering next to the reply's own idea. */
    val feeling: Boolean get() = this == BORED || this == STRESSED || this == LOW || this == LONELY || this == AVOIDING

    companion object {
        fun byId(id: String?): Pull? = values().firstOrNull { it.id == id }
        val pulls: List<Pull> get() = values().filter { it.isPull }
    }
}

/** What the session is for. Asked only when it matters (the 4th open today, or straight back). */
enum class Purpose(val label: String) {
    MESSAGE("Reply to someone"),
    POST("Post something"),
    LOOKUP("Look something up"),
    LEARN("Learn something"),
    WATCH("Watch on purpose"),
    BROWSE("Just browse"),
}

/** One answer on the pause: what pulled, in which app, and whether you went on to open it. */
class PullNote(val at: Long, val pull: Pull, val pkg: String, val opened: Boolean)

/** One ride: the pull's strength before and after (1 to 5, 0 = not rated), and what came next. */
class UrgeRide(val at: Long, val pull: Pull?, val before: Int, val after: Int, val outcome: Int) {
    companion object {
        const val PRACTICE = 0 // from Practice or Settings, or the outcome isn't known
        const val LET_PASS = 1
        const val OPENED = 2
    }
}

/** How you felt after a session: 1 better, 0 about the same, -1 worse. */
class AfterCheck(val at: Long, val pkg: String, val feel: Int)

/** When to ask how a session felt. */
enum class AfterAsk { WAIT, ASK, DROP }

object Pulls {
    const val DAY_MS = 86_400_000L
    const val KEEP_DAYS = 60
    const val KEEP_MS = KEEP_DAYS * DAY_MS
    /** Patterns look at the last 30 days. */
    const val WINDOW_MS = 30 * DAY_MS
    /** Ask how it felt only after at least a minute in the app... */
    const val ASK_AFTER_MS = 60_000L
    /** ...within three hours of starting (later, the moment has passed)... */
    const val ASK_WITHIN_MS = 3 * 3_600_000L
    /** ...and at most three times a day. */
    const val MAX_ASKS_A_DAY = 3

    /** How strong the pull is, 1 to 5. */
    val SCALE = listOf("Barely", "A little", "Medium", "Strong", "Very strong")

    // ------------------------------------------------------------------ storage format (one line each)

    private fun lines(s: String?): List<List<String>> =
        if (s.isNullOrBlank()) emptyList() else s.split('\n').filter { it.isNotBlank() }.map { it.split('|') }

    private fun clean(pkg: String) = pkg.replace('|', ' ').replace('\n', ' ')

    fun encodeNotes(l: List<PullNote>): String = l.joinToString("\n") { "${it.at}|${it.pull.id}|${clean(it.pkg)}|${if (it.opened) 1 else 0}" }
    fun decodeNotes(s: String?): List<PullNote> = lines(s).mapNotNull { p ->
        val pull = Pull.byId(p.getOrNull(1)) ?: return@mapNotNull null
        val at = p[0].toLongOrNull() ?: return@mapNotNull null
        if (p.size < 4) null else PullNote(at, pull, p[2], p[3] == "1")
    }

    fun encodeRides(l: List<UrgeRide>): String = l.joinToString("\n") { "${it.at}|${it.pull?.id ?: "-"}|${it.before}|${it.after}|${it.outcome}" }
    fun decodeRides(s: String?): List<UrgeRide> = lines(s).mapNotNull { p ->
        if (p.size < 5) return@mapNotNull null
        val at = p[0].toLongOrNull() ?: return@mapNotNull null
        val before = p[2].toIntOrNull()?.coerceIn(0, 5) ?: return@mapNotNull null
        val after = p[3].toIntOrNull()?.coerceIn(0, 5) ?: return@mapNotNull null
        UrgeRide(at, Pull.byId(p[1]), before, after, p[4].toIntOrNull() ?: UrgeRide.PRACTICE)
    }

    fun encodeAfter(l: List<AfterCheck>): String = l.joinToString("\n") { "${it.at}|${clean(it.pkg)}|${it.feel}" }
    fun decodeAfter(s: String?): List<AfterCheck> = lines(s).mapNotNull { p ->
        if (p.size < 3) return@mapNotNull null
        val at = p[0].toLongOrNull() ?: return@mapNotNull null
        val feel = p[2].toIntOrNull()?.takeIf { it in -1..1 } ?: return@mapNotNull null
        AfterCheck(at, p[1], feel)
    }

    /** Keeps the last [max] entries from the last 60 days (entries dated in the future, after a clock change, stay). */
    fun <T> keep(l: List<T>, at: (T) -> Long, now: Long, max: Int): List<T> = l.filter { now - at(it) < KEEP_MS }.takeLast(max)

    // ------------------------------------------------------------------ patterns

    class Counted(val pull: Pull, val n: Int)

    /** Your pulls since [since], most often first (ties in the usual order). "Something specific" isn't one. */
    fun top(notes: List<PullNote>, since: Long): List<Counted> =
        Pull.pulls.map { p -> Counted(p, notes.count { it.pull == p && it.at >= since }) }
            .filter { it.n > 0 }
            .sortedWith(compareByDescending<Counted> { it.n }.thenBy { it.pull.ordinal })

    fun headline(top: List<Counted>): String? = when (top.size) {
        0 -> null
        1 -> "Most often: ${top[0].pull.label.lowercase()}"
        else -> "Most often: ${top[0].pull.label.lowercase()}, then ${top[1].pull.label.lowercase()}"
    }

    /**
     * The start hour of the two hours with the most pulls (it may wrap past midnight), or null with too
     * few to tell: fewer than 6 pulls, or no two hours holding at least a third of them.
     */
    fun peakHour(notes: List<PullNote>, since: Long, hourOf: (Long) -> Int): Int? {
        val real = notes.filter { it.at >= since && it.pull.isPull }
        if (real.size < 6) return null
        val h = IntArray(24)
        for (n in real) h[Math.floorMod(hourOf(n.at), 24)]++
        var best = 0
        var bestN = -1
        for (s in 0 until 24) {
            val c = h[s] + h[(s + 1) % 24]
            if (c > bestN) { bestN = c; best = s }
        }
        return if (bestN * 3 >= real.size) best else null
    }

    class Passed(val passed: Int, val total: Int)

    /** Of the pulls you named, how many you let pass (didn't open the app). */
    fun letPass(notes: List<PullNote>, since: Long): Passed {
        val real = notes.filter { it.at >= since && it.pull.isPull }
        return Passed(real.count { !it.opened }, real.size)
    }

    class RideSum(val rides: Int, val rated: Int, val lower: Int, val passed: Int)

    fun rides(l: List<UrgeRide>, since: Long): RideSum {
        val r = l.filter { it.at >= since }
        val rated = r.filter { it.before > 0 && it.after > 0 }
        return RideSum(r.size, rated.size, rated.count { it.after < it.before }, r.count { it.outcome == UrgeRide.LET_PASS })
    }

    fun rideLine(s: RideSum): String? {
        if (s.rides == 0) return null
        val what = if (s.rides == 1) "You rode out 1 urge" else "You rode out ${s.rides} urges"
        return when {
            s.rated == 0 -> "$what."
            s.rated == 1 -> what + (if (s.lower == 1) "; the pull was lower afterwards." else "; the pull stayed, and that's fine.")
            else -> "$what; the pull was lower afterwards ${s.lower} of ${s.rated} times."
        }
    }

    /** What a ride ends with: honest about the urge, which doesn't have to go away for you to choose. */
    fun rideWords(before: Int, after: Int): String = when {
        before <= 0 || after <= 0 -> "That's one wave ridden. You can still choose."
        after < before -> "From $before to $after. Urges rise and fall like waves; this one is passing."
        after == before -> "Still $after. That's all right: you can feel the pull and still choose."
        else -> "Up to $after. Sometimes a wave builds before it breaks. You can still choose."
    }

    class Felt(val better: Int, val same: Int, val worse: Int) {
        val total: Int get() = better + same + worse
    }

    fun felt(checks: List<AfterCheck>, since: Long, pkg: String? = null): Felt {
        val c = checks.filter { it.at >= since && (pkg == null || it.pkg == pkg) }
        return Felt(c.count { it.feel > 0 }, c.count { it.feel == 0 }, c.count { it.feel < 0 })
    }

    class Worst(val pkg: String, val worse: Int, val total: Int)

    /** The app you most often felt worse after: at least 3 answers, worse in at least half of them. */
    fun worstApp(checks: List<AfterCheck>, since: Long): Worst? =
        checks.filter { it.at >= since }.groupBy { it.pkg }
            .map { (pkg, l) -> Worst(pkg, l.count { it.feel < 0 }, l.size) }
            .filter { it.total >= 3 && it.worse * 2 >= it.total }
            .maxWithOrNull(compareBy<Worst> { it.worse * 1000 / it.total }.thenBy { it.worse })

    /**
     * For the pause: how the last few sessions in this app felt, but only when it was mostly worse (at
     * least 3 answers in the last 30 days, worse in most of the last 5). That's the moment the memory helps.
     */
    fun afterLine(checks: List<AfterCheck>, pkg: String, now: Long): String? {
        val recent = checks.filter { it.pkg == pkg && now - it.at < WINDOW_MS }.takeLast(5)
        if (recent.size < 3) return null
        val worse = recent.count { it.feel < 0 }
        if (worse * 2 <= recent.size) return null
        return if (worse == recent.size) "You felt worse after each of your last ${recent.size} sessions here."
        else "You felt worse after $worse of your last ${recent.size} sessions here."
    }

    /** Back home after a session that started at [startedAt]: ask now, wait, or let it go. */
    fun afterAsk(startedAt: Long, now: Long, asksToday: Int): AfterAsk = when {
        startedAt <= 0L -> AfterAsk.DROP
        now - startedAt > ASK_WITHIN_MS || now - startedAt < -ASK_AFTER_MS -> AfterAsk.DROP
        asksToday >= MAX_ASKS_A_DAY -> AfterAsk.DROP
        now - startedAt < ASK_AFTER_MS -> AfterAsk.WAIT
        else -> AfterAsk.ASK
    }

    /** "When I'm bored, I'll read one page." */
    fun planLine(p: Pull, plan: String): String = "${p.whenWords}, I'll ${plan.trim().trimEnd('.', ' ')}."

    /**
     * Felt low on 5 or more different days in the last two weeks: the insights add a kind word about
     * support. Not a diagnosis, and nothing is shown or sent anywhere else.
     */
    fun lowOften(notes: List<PullNote>, now: Long, dayOf: (Long) -> String): Boolean =
        notes.filter { it.pull == Pull.LOW && now - it.at in 0 until 14 * DAY_MS }.map { dayOf(it.at) }.toSet().size >= 5
}
