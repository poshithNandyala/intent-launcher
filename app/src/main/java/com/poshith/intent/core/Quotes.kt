package com.poshith.intent.core

/**
 * A few lines worth stopping for: on the home screen (one a day), during a real break, and when a
 * session's time is up. Quality over quantity, and every attribution checked: the internet is full
 * of famous names on words they never said, so those are left out. Lines without a name are Intent's own.
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
    )

    /** The same quote all day, a different one tomorrow. */
    fun forDay(day: Int): Quote = all[Math.floorMod(day * 7 + 3, all.size)]

    /** A quote for a moment (a break, a session ending): changes every few minutes. */
    fun forMoment(wallMs: Long, everyMs: Long = 10 * 60_000L): Quote = all[Math.floorMod((wallMs / everyMs).toInt() * 11 + 5, all.size)]

    fun next(current: Quote): Quote = all[(all.indexOf(current) + 1).mod(all.size)]
}
