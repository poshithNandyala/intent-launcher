package com.poshith.intent.core

/**
 * Lines worth stopping for: one a day on the home screen (or one you pinned for a week), and during a
 * real break. Mostly from books that have lasted: Seneca, Marcus Aurelius and Epictetus, Thoreau and
 * Emerson, the Gita and Kabir, and a few modern ones on doing the work. Every attribution checked
 * against its source: the internet is full of famous names on words they never said, and those are
 * left out. Old translations are quoted as they were printed.
 *
 * Kept calm on purpose: no fear about how short life is, no hustle, no shame. Lines without a name are
 * Intent's own; [Quote.note] says what research a line leans on, shown when you tap it.
 */
data class Quote(val text: String, val by: String = "", val note: String = "")

object Quotes {
    val all: List<Quote> = listOf(
        // ---- time and attention
        Quote("How we spend our days is, of course, how we spend our lives.", "Annie Dillard, The Writing Life"),
        Quote("Life is long, if you know how to use it.", "Seneca, On the Shortness of Life"),
        Quote("To be everywhere is to be nowhere.", "Seneca, Letters"),
        Quote("Our life is frittered away by detail. Simplify, simplify.", "Henry David Thoreau, Walden"),
        Quote("The cost of a thing is the amount of what I will call life which is required to be exchanged for it.", "Henry David Thoreau, Walden"),
        Quote("A wealth of information creates a poverty of attention.", "Herbert Simon"),
        Quote("My experience is what I agree to attend to.", "William James, The Principles of Psychology"),
        Quote("Attention is the rarest and purest form of generosity.", "Simone Weil, in a letter to Joë Bousquet"),
        Quote("All the unhappiness of men arises from one single fact, that they cannot stay quietly in their own chamber.", "Blaise Pascal, Pensées"),
        Quote("Clarity about what matters provides clarity about what does not.", "Cal Newport"),
        Quote("Nothing, Lucilius, is ours, except time.", "Seneca, Letters"),
        Quote("Begin at once to live, and count each separate day as a separate life.", "Seneca, Letters"),
        Quote("Confine thyself to the present.", "Marcus Aurelius, Meditations"),
        Quote("The years teach much which the days never know.", "Ralph Waldo Emerson"),
        Quote("The art of being wise is the art of knowing what to overlook.", "William James, The Principles of Psychology"),
        Quote("It is not enough to be industrious; so are the ants. What are you industrious about?", "Henry David Thoreau, in a letter"),
        Quote("If you don't prioritize your life, someone else will.", "Greg McKeown, Essentialism"),
        Quote("Almost everything will work again if you unplug it for a few minutes, including you.", "Anne Lamott"),
        Quote("Rest is not idleness.", "John Lubbock, The Use of Life"),

        // ---- the mind
        Quote("We suffer more often in imagination than in reality.", "Seneca, Letters"),
        Quote("Men are disturbed not by the things which happen, but by the opinions about the things.", "Epictetus, Enchiridion"),
        Quote("Such as are thy habitual thoughts, such also will be the character of thy mind.", "Marcus Aurelius, Meditations"),
        Quote("Look within. Within is the fountain of good.", "Marcus Aurelius, Meditations"),
        Quote("Very little indeed is necessary for living a happy life.", "Marcus Aurelius, Meditations"),
        Quote("Do not spoil what you have by desiring what you have not.", "Epicurus"),
        Quote("The unexamined life is not worth living.", "Socrates, in Plato's Apology"),
        Quote("It is impossible for a man to learn what he thinks he already knows.", "Epictetus, Discourses"),
        Quote("Things do not change; we change.", "Henry David Thoreau, Walden"),
        Quote("He who has a why to live can bear almost any how.", "Friedrich Nietzsche, Twilight of the Idols"),
        Quote("You can't stop the waves, but you can learn to surf.", "Jon Kabat-Zinn, Wherever You Go, There You Are"),
        Quote("Associate with those who will make a better man of you.", "Seneca, Letters"),
        Quote("If it is not right, do not do it: if it is not true, do not say it.", "Marcus Aurelius, Meditations"),

        // ---- habits
        Quote("The chains of habit are generally too small to be felt till they are too strong to be broken.", "Samuel Johnson"),
        Quote("Habit is habit, and not to be flung out of the window by any man, but coaxed downstairs a step at a time.", "Mark Twain, Pudd'nhead Wilson"),
        Quote("Whatever you would make habitual, practise it.", "Epictetus, Discourses"),
        Quote("Every action you take is a vote for the type of person you wish to become.", "James Clear, Atomic Habits"),
        Quote("You do not rise to the level of your goals. You fall to the level of your systems.", "James Clear, Atomic Habits"),
        Quote("We are what we repeatedly do. Excellence, then, is not an act, but a habit.", "Will Durant, The Story of Philosophy"),

        // ---- doing the work
        Quote("Discipline equals freedom.", "Jocko Willink"),
        Quote("Inspiration is for amateurs. The rest of us just show up and get to work.", "Chuck Close"),
        Quote("Play long-term games with long-term people.", "Naval Ravikant"),
        Quote("No longer talk at all about the kind of man that a good man ought to be, but be such.", "Marcus Aurelius, Meditations"),
        Quote("First say to yourself what you would be; and then do what you have to do.", "Epictetus, Discourses"),
        Quote("No great thing is created suddenly.", "Epictetus, Discourses"),
        Quote("What stands in the way becomes the way.", "Marcus Aurelius, Meditations"),
        Quote("If you want to improve, be content to be thought foolish and stupid.", "Epictetus, Enchiridion"),
        Quote("He who has begun has half done. Dare to be wise; begin!", "Horace, Epistles"),
        Quote("Dripping water hollows out stone.", "Ovid"),
        Quote("The journey of a thousand miles begins with one step.", "Lao Tzu, Tao Te Ching"),
        Quote("Do the difficult things while they are easy and do the great things while they are small.", "Lao Tzu, Tao Te Ching"),
        Quote("The strongest of all warriors are these two: Time and Patience.", "Leo Tolstoy, War and Peace"),
        Quote("Wisely, and slow. They stumble that run fast.", "William Shakespeare, Romeo and Juliet"),
        Quote("The best way out is always through.", "Robert Frost, A Servant to Servants"),
        Quote("When a man does not know what harbour he is making for, no wind is the right wind.", "Seneca, Letters"),
        Quote("You have a right to your actions, but never to their fruits.", "Bhagavad Gita 2.47"),
        Quote("Arise, awake, and stop not till the goal is reached.", "Swami Vivekananda"),
        Quote("What you would do tomorrow, do today; what you would do today, do now.", "Kabir"),
        Quote("Well done is better than well said.", "Benjamin Franklin, Poor Richard's Almanack"),
        Quote("Finish each day and be done with it.", "Ralph Waldo Emerson, in a letter to his daughter"),
        Quote("It's not the writing part that's hard. What's hard is sitting down to write.", "Steven Pressfield, The War of Art"),
        Quote("Almost all good writing begins with terrible first efforts.", "Anne Lamott, Bird by Bird"),

        // ---- Intent's own: for the moment you reach for the phone
        Quote("The feed is endless. Your evening isn't."),
        Quote("Nothing in that app needs you right now. Something in your life might."),
        Quote("Hundreds of people designed that app to keep you. You're one person. Decide first."),
        Quote("Five minutes, a hundred times a day, is more than eight hours."),
        Quote("Your attention is yours. Spend it on purpose."),
        Quote("Do the hard thing first. The phone will still be here."),
        Quote("You don't need more time. You need fewer leaks."),
        Quote("Most of what you check could have waited. Most of what you postpone couldn't."),
        Quote("Make something today that didn't exist yesterday."),
        Quote("Reply later. Live now."),
        Quote("Look up. This minute won't come again."),
        Quote("Read one page. Write one line. Call one person. Then decide."),
        Quote("Be hard to reach and easy to find doing good work."),
        Quote("Every notification is someone else's plan for your time."),
        Quote("Be bored for a minute. It's where your own thoughts live."),
        Quote("The phone can wait. The people in front of you can't."),
        Quote("Close the app. Open the day."),
        Quote("One page read beats a hundred posts skimmed."),
        Quote("Put it down for ten minutes. Notice what you notice."),

        // ---- Intent's own, from the research (tap a line on the home screen to see what it leans on)
        Quote("An urge can be present without opening the app.", note = "Urge surfing: in a trial, smokers who practised it smoked fewer cigarettes, though their urges stayed as strong (Bowen & Marlatt, 2009)."),
        Quote("The urge rises, peaks and passes. Let it pass without you.", note = "Cravings come in waves that fade on their own if you don't act on them (urge surfing, Marlatt)."),
        Quote("Missed a day? Habits survive that.", note = "Missing one day didn't derail forming a habit (Lally et al., 2010). Habits took 18 to 254 days, median 66: not 21."),
        Quote("Name the next small step, and when.", note = "If-then plans (when X, I'll do Y) had a medium-to-large effect on reaching goals across 94 studies (Gollwitzer & Sheeran, 2006)."),
        Quote("Picture the steps, not the trophy.", note = "Students who pictured how they'd study studied more and did better than those who pictured the grade (Taylor & Pham)."),
        Quote("Wishing isn't planning. Name what's in the way, then plan around it.", note = "Dreaming only of the outcome can sap the energy to act (Oettingen). Pairing the wish with its obstacle and a plan helps (WOOP)."),
        Quote("Be kind about the slip. Be specific about the next step.", note = "Self-compassion after a setback led to more effort to improve than self-criticism (Breines & Chen, 2012)."),
        Quote("A wandering mind isn't broken. It's planning, remembering, imagining.", note = "The brain's default mode network does real work: memory, planning, thinking about others."),
        Quote("Every time you put it down on purpose, it gets a little easier.", note = "Habits form by repetition in the same situation (Lally et al., 2010; Wood & Neal)."),
        Quote("Boredom can be where your own ideas start.", note = "After a boring task, people came up with more ideas in a creativity test (Mann & Cadman, 2014)."),
    )

    /** Steps through the list that visit every quote before any comes back, whatever its length. */
    private fun coprime(from: Int): Int {
        fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
        var k = from
        while (gcd(k, all.size) != 1) k++
        return k
    }
    private val dayStep = coprime(7)

    /** The same quote all day, a different one tomorrow: [day] counts local days (see [localDay]). */
    fun forDay(day: Int): Quote = all[Math.floorMod(day * dayStep + 3, all.size)]

    /** Today's number for [forDay]: days since 1970 where you are, so it turns over at your midnight. */
    fun localDay(wallMs: Long, offsetMs: Int): Int = Math.floorDiv(wallMs + offsetMs, 86_400_000L).toInt()

    /** A pinned quote stays until [pinnedUntil]; otherwise it's [forDay]. [pinned] is its index in [all]. */
    fun today(day: Int, pinned: Int, pinnedUntil: Long, now: Long): Quote =
        if (pinned in all.indices && pinnedUntil > now) all[pinned] else forDay(day)

    /** Pinned for a week, from now. */
    const val PIN_MS = 7 * 86_400_000L

    /**
     * Blunt lines for the Shorts and Reels pause: off unless you turn them on (Settings › Shorts and
     * Reels), because shame tends to backfire. Hard on the habit, never on the person.
     */
    val blunt: List<String> = listOf(
        "You're not relaxing. You're numbing.",
        "Another one won't fix whatever you're avoiding.",
        "Be honest: you stopped enjoying this a while ago.",
        "You won't remember a single one of these tomorrow.",
        "Somebody is building their dream right now. You're watching it.",
        "This is exactly how a whole evening disappears.",
        "The algorithm is winning. Your goals are losing.",
        "Ten more won't feel any better than the last ten.",
        "You said you'd change. This isn't it.",
        "You're paying for this with the only life you get.",
        "Close it. You already know.",
        "Strangers' highlights. Your hours.",
        "This app is working perfectly. On you.",
        "Every swipe is a vote. Look who's winning.",
        "Your competition isn't scrolling right now.",
        "Put. It. Down.",
        "You've seen this before. It didn't change your life then either.",
        "The feed doesn't care about you. Your goals do.",
        "Nobody gets fit, rich or wise watching other people do it.",
        "Stop giving your best hours to an algorithm.",
        "Keep doing this and nothing changes. That's the deal.",
        "Bored? Good. Do something with it.",
        "Wake up. This is your life, not a feed.",
        "The urge is lying to you. One more never means one.",
        "You don't need another video. You need to start.",
    )

    /** A blunt line for a moment: changes every half minute, so it doesn't turn into wallpaper. */
    fun bluntFor(wallMs: Long): String = blunt[Math.floorMod(Math.floorMod(wallMs / 30_000L, blunt.size.toLong()).toInt() * 7 + 2, blunt.size)]
}
