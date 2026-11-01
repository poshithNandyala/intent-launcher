package com.poshith.intent.core

/**
 * Lines worth stopping for: on the home screen (a new one every 30 seconds, or one a day), on the pause,
 * during a real break and on the Shorts pause. Mostly from books that have lasted: Seneca, Marcus Aurelius
 * and Epictetus, Thoreau and Emerson, the Gita and Kabir, and a few modern ones on doing the work. Every
 * attribution checked against its source: the internet is full of famous names on words they never said,
 * and those are left out. Old translations are quoted as they were printed. Lines without a name are Intent's own.
 */
data class Quote(val text: String, val by: String = "")

object Quotes {
    val all: List<Quote> = listOf(
        // ---- time and attention
        Quote("How we spend our days is, of course, how we spend our lives.", "Annie Dillard"),
        Quote("It is not that we have a short time to live, but that we waste a lot of it.", "Seneca"),
        Quote("While we are postponing, life speeds by.", "Seneca"),
        Quote("As if you could kill time without injuring eternity.", "Henry David Thoreau"),
        Quote("Our life is frittered away by detail. Simplify, simplify.", "Henry David Thoreau"),
        Quote("The cost of a thing is the amount of what I will call life which is required to be exchanged for it.", "Henry David Thoreau"),
        Quote("Lost time is never found again.", "Benjamin Franklin"),
        Quote("A wealth of information creates a poverty of attention.", "Herbert Simon"),
        Quote("My experience is what I agree to attend to.", "William James"),
        Quote("All the unhappiness of men arises from one single fact, that they cannot stay quietly in their own chamber.", "Blaise Pascal"),
        Quote("Clarity about what matters provides clarity about what does not.", "Cal Newport"),
        Quote("Nothing, Lucilius, is ours, except time.", "Seneca, Letters"),
        Quote("Hold every hour in your grasp.", "Seneca, Letters"),
        Quote("Begin at once to live, and count each separate day as a separate life.", "Seneca, Letters"),
        Quote("Dost thou love life? Then do not squander time, for that's the stuff life is made of.", "Benjamin Franklin"),
        Quote("You may delay, but time will not.", "Benjamin Franklin"),
        Quote("Confine thyself to the present.", "Marcus Aurelius, Meditations"),
        Quote("The years teach much which the days never know.", "Ralph Waldo Emerson"),
        Quote("The art of being wise is the art of knowing what to overlook.", "William James"),
        Quote("It is not enough to be industrious; so are the ants. What are you industrious about?", "Henry David Thoreau"),
        Quote("The average human lifespan is absurdly, terrifyingly, insultingly short.", "Oliver Burkeman, Four Thousand Weeks"),
        Quote("If you don't prioritize your life, someone else will.", "Greg McKeown, Essentialism"),
        Quote("The main thing is to keep the main thing the main thing.", "Stephen R. Covey"),

        // ---- the mind
        Quote("We suffer more often in imagination than in reality.", "Seneca, Letters"),
        Quote("Men are disturbed not by the things which happen, but by the opinions about the things.", "Epictetus"),
        Quote("Such as are thy habitual thoughts, such also will be the character of thy mind.", "Marcus Aurelius, Meditations"),
        Quote("Look within. Within is the fountain of good.", "Marcus Aurelius, Meditations"),
        Quote("Very little indeed is necessary for living a happy life.", "Marcus Aurelius, Meditations"),
        Quote("The unexamined life is not worth living.", "Socrates, in Plato's Apology"),
        Quote("It is impossible for a man to learn what he thinks he already knows.", "Epictetus"),
        Quote("Things do not change; we change.", "Henry David Thoreau, Walden"),
        Quote("He who has a why to live can bear almost any how.", "Friedrich Nietzsche"),
        Quote("The chains of habit are generally too small to be felt till they are too strong to be broken.", "Samuel Johnson"),
        Quote("Associate with those who will make a better man of you.", "Seneca, Letters"),
        Quote("If it is not right, do not do it: if it is not true, do not say it.", "Marcus Aurelius, Meditations"),

        // ---- doing the work
        Quote("Volume negates luck.", "Alex Hormozi"),
        Quote("The work works on you more than you work on it.", "Alex Hormozi"),
        Quote("Outwork your self-doubt.", "Alex Hormozi"),
        Quote("Discipline equals freedom.", "Jocko Willink"),
        Quote("Discipline is the bridge between goals and accomplishment.", "Jim Rohn"),
        Quote("Inspiration is for amateurs. The rest of us just show up and get to work.", "Chuck Close"),
        Quote("Every action you take is a vote for the type of person you wish to become.", "James Clear"),
        Quote("You do not rise to the level of your goals. You fall to the level of your systems.", "James Clear"),
        Quote("We are what we repeatedly do. Excellence, then, is not an act, but a habit.", "Will Durant"),
        Quote("Play long-term games with long-term people.", "Naval Ravikant"),
        Quote("No longer talk at all about the kind of man that a good man ought to be, but be such.", "Marcus Aurelius"),
        Quote("First say to yourself what you would be; and then do what you have to do.", "Epictetus"),
        Quote("No great thing is created suddenly.", "Epictetus"),
        Quote("What stands in the way becomes the way.", "Marcus Aurelius, Meditations"),
        Quote("He who has begun has half done. Dare to be wise; begin!", "Horace"),
        Quote("Dripping water hollows out stone.", "Ovid"),
        Quote("The journey of a thousand miles begins with one step.", "Lao Tzu, Tao Te Ching"),
        Quote("The strongest of all warriors are these two: Time and Patience.", "Leo Tolstoy, War and Peace"),
        Quote("When a man does not know what harbour he is making for, no wind is the right wind.", "Seneca, Letters"),
        Quote("You have a right to your actions, but never to their fruits.", "Bhagavad Gita 2.47"),
        Quote("Arise, awake, and stop not till the goal is reached.", "Swami Vivekananda"),
        Quote("What you would do tomorrow, do today; what you would do today, do now.", "Kabir"),
        Quote("Well done is better than well said.", "Benjamin Franklin"),
        Quote("Finish each day and be done with it.", "Ralph Waldo Emerson"),
        Quote("It's not the writing part that's hard. What's hard is sitting down to write.", "Steven Pressfield, The War of Art"),
        Quote("Almost all good writing begins with terrible first efforts.", "Anne Lamott, Bird by Bird"),

        // ---- Intent's own, written for the moment you reach for the phone
        Quote("The feed is endless. Your evening isn't."),
        Quote("Nothing in that app needs you right now. Something in your life does."),
        Quote("You won't remember a single video from today. You'll remember what you made."),
        Quote("Hundreds of people designed that app to keep you. You're one person. Decide first."),
        Quote("Five minutes, a hundred times a day, is more than eight hours."),
        Quote("Your attention is the most valuable thing you own. Spend it on purpose."),
        Quote("The people who win aren't more motivated. They're less distracted."),
        Quote("Boredom is the doorway to your best ideas. Don't scroll past it."),
        Quote("Do the hard thing first. The phone will still be here."),
        Quote("One focused hour beats a distracted day."),
        Quote("The scroll gives you a feeling. The work gives you a life."),
        Quote("You don't need more time. You need fewer leaks."),
        Quote("Most of what you check could have waited. Most of what you postpone couldn't."),
        Quote("Small wins, every day, add up to a life people call lucky."),
        Quote("Your future self is living with every choice you make in the next ten minutes."),
        Quote("Make something today that didn't exist yesterday."),
        Quote("Comfort now is paid for later. Effort now pays you back."),
        Quote("If you can't sit with your thoughts, they'll follow you into every app."),
        Quote("Reply later. Live now."),
        Quote("Nobody on their last day wished they had scrolled more."),
        Quote("The best version of you is on the other side of this pause."),
        Quote("Every time you put it down, it gets a little easier."),
        Quote("Look up. This minute won't come again."),
        Quote("Read one page. Write one line. Call one person. Then decide."),
        Quote("Be hard to reach and easy to find doing great work."),
        Quote("Your goals don't care how you feel. Start anyway."),
        Quote("Every notification is someone else's plan for your time."),
        Quote("The urge peaks and passes. Let it pass without you."),
        Quote("You can't scroll your way to a life you're proud of."),
        Quote("Be bored for a minute. It's where your own thoughts live."),
        Quote("The phone can wait. The people in front of you can't."),
        Quote("Close the app. Open the day."),
        Quote("One page read beats a hundred posts skimmed."),
        Quote("Put it down for ten minutes. Notice what you notice."),
    )

    /** Steps through the list that visit every quote before any comes back, whatever its length. */
    private fun coprime(from: Int): Int {
        fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
        var k = from
        while (gcd(k, all.size) != 1) k++
        return k
    }
    private val dayStep = coprime(7)
    private val momentStep = coprime(11)

    /** The same quote all day, a different one tomorrow. */
    fun forDay(day: Int): Quote = all[Math.floorMod(day * dayStep + 3, all.size)]

    /** A quote for a moment (a break, a session ending): changes every few minutes. */
    fun forMoment(wallMs: Long, everyMs: Long = 10 * 60_000L): Quote =
        all[Math.floorMod(Math.floorMod(wallMs / everyMs, all.size.toLong()).toInt() * momentStep + 5, all.size)]

    fun next(current: Quote): Quote = all[(all.indexOf(current) + 1).mod(all.size)]
}
