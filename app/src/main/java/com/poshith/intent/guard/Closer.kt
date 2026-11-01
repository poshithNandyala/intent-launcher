package com.poshith.intent.guard

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
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
 * window; then it briefly takes audio focus, so anything still playing pauses. On Android 13 and
 * older it also ends the app's background process; newer Android only lets an app end its own.
 */
object Closer {
    private val main = Handler(Looper.getMainLooper())

    /** Flags for any Intent screen that appears over another app, so that app doesn't go picture-in-picture. */
    const val OVER_APP = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION

    /** Intent's home in front, quietly: the app behind it stops, nothing pops out. */
    fun toHome(ctx: Context) {
        try {
            ctx.startActivity(Intent(ctx, HomeActivity::class.java).setAction(Intent.ACTION_MAIN).addFlags(OVER_APP))
        } catch (_: Exception) {
            Launch.goHome(ctx)
        }
    }

    /** Closes [pkg]: home in front, sound stopped, and (where Android allows) the app's process ended. */
    fun close(ctx: Context, pkg: String) {
        toHome(ctx)
        silence(ctx)
        if (Build.VERSION.SDK_INT <= 33) {
            val app = ctx.applicationContext
            main.postDelayed({
                try { app.getSystemService(ActivityManager::class.java)?.killBackgroundProcesses(pkg) } catch (_: Exception) {}
            }, 1_500)
        }
    }

    /** Takes audio focus for a moment and lets it go: whatever was playing pauses, and stays paused. */
    fun silence(ctx: Context) {
        val am = ctx.applicationContext.getSystemService(AudioManager::class.java) ?: return
        try {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build())
                .setOnAudioFocusChangeListener { }
                .build()
            if (am.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                main.postDelayed({ try { am.abandonAudioFocusRequest(req) } catch (_: Exception) {} }, 800)
            }
        } catch (_: Exception) {}
    }

    /** Back into [pkg] where it was left (its task comes to the front, it isn't restarted). */
    fun reopen(ctx: Context, pkg: String): Boolean {
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        return try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true } catch (_: Exception) { false }
    }
}
