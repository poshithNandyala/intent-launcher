package com.poshith.intent.guard

import android.content.Context
import android.media.AudioManager

/**
 * Is a call ringing or going on right now (a phone call, or a WhatsApp/Meet-style call)? Read from the
 * audio mode, which needs no permission. While it is, Intent stays out of the way: no check-in, no
 * auto-lock, no pause screens, nothing over the call.
 */
object Calls {
    fun active(ctx: Context): Boolean = try {
        when (ctx.getSystemService(AudioManager::class.java)?.mode) {
            AudioManager.MODE_RINGTONE, AudioManager.MODE_IN_CALL, AudioManager.MODE_IN_COMMUNICATION -> true
            else -> false
        }
    } catch (_: Exception) { false }
}
