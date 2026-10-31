package com.poshith.intent.core

/**
 * "How are you feeling?" on the pause screen. Naming a feeling ("affect labeling") reduces the
 * brain's alarm response (Lieberman et al., 2007), and it's where Dr. Sahar Yousef suggests breaking
 * the habit loop: feeling -> craving -> reach. Each answer gets one small, kind suggestion.
 */
enum class Feeling(val label: String, val nudge: String, val action: Action) {
    BORED("Bored", "Boredom is where ideas start. Give it two minutes before you fill it.", Action.NONE),
    STRESSED("Stressed", "Try one long, slow breath out. A feed rarely lowers stress; a short walk often does.", Action.NONE),
    TIRED("Tired", "Your brain is asking for rest, not more input. Close your eyes for two minutes.", Action.NONE),
    LONELY("Lonely", "Scrolling isn't connecting. Call or message one real person instead.", Action.CALL),
    AVOIDING("Avoiding something", "Start the thing for just two minutes. Starting is the hard part.", Action.ONE_THING),
    CURIOUS("Curious", "Go for it, on purpose: decide what you're looking for first.", Action.NONE),
    FINE("Fine", "", Action.NONE);

    enum class Action { NONE, CALL, ONE_THING }

    /** Feelings where "Not now" is the gentle default. */
    val leanNotNow: Boolean get() = this == BORED || this == STRESSED || this == TIRED || this == LONELY || this == AVOIDING
}

/** What the session is for. Shown again when time is up. */
enum class Purpose(val label: String) {
    MESSAGE("Reply to someone"),
    POST("Post something"),
    LOOKUP("Look something up"),
    LEARN("Learn something"),
    WATCH("Watch on purpose"),
    BROWSE("Just browse"),
}
