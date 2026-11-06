package com.poshith.intent.guard

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.poshith.intent.HomeActivity

/**
 * Closing an app the way a person would want it closed, not just hidden.
 *
 * Pressing Home on a playing video makes many apps (YouTube included) shrink it into a little
 * picture-in-picture window that keeps playing. So Intent brings its home screen forward *without*
 * telling the app the user is leaving (FLAG_ACTIVITY_NO_USER_ACTION), which is what triggers that
 * window; then, if it may see media players (Notification access), it pauses that app's player. On
 * Android 13 and older it also ends the app's background process; newer Android only lets an app
 * end its own.
 */
object Closer {
    private val main = Handler(Looper.getMainLooper())

    /** Flags for any Intent screen that appears over another app, so that app doesn't go picture-in-picture. */
    const val OVER_APP = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION

    /** The home screen in front, quietly: the app behind it stops, nothing pops out. Intent's home
     *  when it's the launcher (or none is chosen yet), otherwise the launcher you use. */
    fun toHome(ctx: Context) {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val launcher = try {
            ctx.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        } catch (_: Exception) { null }
        // "android" is the "open with" chooser: no launcher picked yet.
        val i = if (launcher == null || launcher == ctx.packageName || launcher == "android")
            Intent(ctx, HomeActivity::class.java).setAction(Intent.ACTION_MAIN)
        else home.setPackage(launcher)
        try {
            ctx.startActivity(i.addFlags(OVER_APP))
        } catch (_: Exception) {
            Launch.goHome(ctx)
        }
    }

    /** Closes [pkg]: home in front, sound stopped, and (where Android allows) the app's process ended. */
    fun close(ctx: Context, pkg: String) {
        toHome(ctx)
        silence(ctx, pkg)
        if (Build.VERSION.SDK_INT <= 33) {
            val app = ctx.applicationContext
            main.postDelayed({
                try { app.getSystemService(ActivityManager::class.java)?.killBackgroundProcesses(pkg) } catch (_: Exception) {}
            }, 1_500)
        }
    }

    /** What [silence] could do: pause [pkg]'s player, find nothing of [pkg]'s playing, or not see players at all. */
    enum class Silenced { PAUSED, NOTHING_PLAYING, NO_ACCESS }

    /**
     * Stops what [pkg] is playing (a video in a little picture-in-picture window, say). Best effort, and
     * only that app: with Notification access Intent can see the phone's media players and pauses exactly
     * [pkg]'s, so your music, a podcast or directions elsewhere keep playing. Without it Intent can't tell
     * which app is playing, so it leaves sound alone rather than stop something unrelated. (Closing
     * without "user leaving" already keeps most apps from popping out into a little window.)
     */
    fun silence(ctx: Context, pkg: String?): Silenced {
        if (pkg == null) return Silenced.NOTHING_PLAYING
        val r = pausePlayer(ctx, pkg)
        android.util.Log.d("IntentCloser", "silence $pkg: $r")
        return r
    }

    /** Pauses [pkg]'s own media players. */
    private fun pausePlayer(ctx: Context, pkg: String): Silenced {
        if (!BreakQuietService.hasAccess(ctx)) return Silenced.NO_ACCESS
        return try {
            val msm = ctx.getSystemService(MediaSessionManager::class.java) ?: return Silenced.NO_ACCESS
            var paused = false
            for (c in msm.getActiveSessions(ComponentName(ctx, BreakQuietService::class.java))) {
                if (c.packageName != pkg) continue
                when (c.playbackState?.state) {
                    PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING, PlaybackState.STATE_FAST_FORWARDING,
                    PlaybackState.STATE_REWINDING -> { c.transportControls.pause(); paused = true }
                }
            }
            if (paused) Silenced.PAUSED else Silenced.NOTHING_PLAYING
        } catch (_: Exception) { Silenced.NO_ACCESS }
    }

    /** Back into [pkg] where it was left (its task comes to the front, it isn't restarted). */
    fun reopen(ctx: Context, pkg: String): Boolean {
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        return try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true } catch (_: Exception) { false }
    }
}
