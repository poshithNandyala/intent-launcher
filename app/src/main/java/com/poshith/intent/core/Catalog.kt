package com.poshith.intent.core

/** Known apps, used only for suggestions during setup. You always make the final choice. */
object Catalog {

    /**
     * Never paused or blocked, whatever rule is set: calls, emergency, settings, permission and install
     * screens, keyboards (added at runtime), the clock. Removing a guard must always stay possible.
     */
    val PROTECTED = setOf(
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.android.settings.intelligence",
        "com.samsung.android.settings",
        "com.miui.securitycenter",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.android.phone",
        "com.android.server.telecom",
        "com.android.incallui",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.samsung.android.dialer",
        "com.samsung.android.incallui",
        "com.android.contacts",
        "com.google.android.contacts",
        "com.samsung.android.app.contacts",
        "com.android.emergency",
        "com.google.android.apps.safetyhub",
        "com.android.stk",
        "com.android.deskclock",
        "com.google.android.deskclock",
        "com.sec.android.app.clockpackage",
        "com.android.mms",
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        "com.google.android.gms",
        "com.google.android.apps.authenticator2",
        "com.azure.authenticator",
    )

    /** Often pulled into without deciding to: feeds, short video, endless browsing. Pre-selected as "mindful". */
    val PULL = linkedSetOf(
        "com.instagram.android",            // Instagram
        "com.google.android.youtube",       // YouTube
        "com.facebook.katana",              // Facebook
        "com.facebook.lite",
        "com.snapchat.android",
        "com.twitter.android",              // X
        "com.instagram.barcelona",          // Threads
        "com.reddit.frontpage",
        "com.zhiliaoapp.musically",         // TikTok
        "com.ss.android.ugc.trill",
        "in.mohalla.sharechat",             // ShareChat
        "in.mohalla.video",                 // Moj
        "com.eterno.shortvideos",           // Josh
        "com.pinterest",
        "com.tumblr",
        "com.ninegag.android.app",
        "com.quora.android",
        "tv.twitch.android.app",
        "com.discord",
        "com.netflix.mediaclient",
        "in.startv.hotstar",                // JioHotstar
        "com.jio.media.ondemand",           // JioCinema
        "com.amazon.avod.thirdpartyclient", // Prime Video
        "com.mxtech.videoplayer.ad",
        "com.eterno",                       // Dailyhunt
        "com.nis.app",                      // Inshorts
        "com.flipkart.android",
        "in.amazon.mShop.android.shopping",
        "com.myntra.android",
        "com.meesho.supply",
    )

    /** Tools people need fast. Suggested for the home screen. */
    val TOOLS = linkedSetOf(
        "com.whatsapp",
        "com.whatsapp.w4b",
        "com.google.android.apps.maps",
        "com.google.android.apps.nbu.paisa.user", // Google Pay
        "com.phonepe.app",
        "net.one97.paytm",
        "in.org.npci.upiapp",                     // BHIM
        "com.fampay.in",                          // FamApp (FamPay)
        "com.google.android.gm",                  // Gmail
        "com.google.android.calendar",
        "com.google.android.keep",
        "com.ubercab",
        "com.olacabs.customer",
        "com.rapido.passenger",
        "com.google.android.apps.authenticator2",
        "com.azure.authenticator",
        "org.telegram.messenger",
        "com.microsoft.teams",
        "com.Slack",
    )

    /** Payment, navigation, authentication: suggested as ESSENTIAL so they're never in the way. */
    val ESSENTIAL_HINT = setOf(
        "com.google.android.apps.maps",
        "com.google.android.apps.nbu.paisa.user",
        "com.phonepe.app",
        "net.one97.paytm",
        "in.org.npci.upiapp",
        "com.fampay.in",
        "com.google.android.apps.authenticator2",
        "com.azure.authenticator",
        "com.ubercab",
        "com.olacabs.customer",
        "com.rapido.passenger",
        "com.whatsapp",
        "com.whatsapp.w4b",
    )

    /** ApplicationInfo.category values worth suggesting as mindful: game(0), video(2), social(4), news(5). */
    val PULL_CATEGORIES = setOf(0, 2, 4, 5)
}
