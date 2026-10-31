package com.poshith.intent

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.SystemClock
import com.poshith.intent.data.Apps
import com.poshith.intent.data.Store
import com.poshith.intent.guard.Reminder
import java.util.concurrent.CopyOnWriteArrayList

class IntentApp : Application() {

    companion object {
        private val unlockListeners = CopyOnWriteArrayList<() -> Unit>()
        fun onUnlock(l: () -> Unit) { unlockListeners += l }
        fun removeUnlock(l: () -> Unit) { unlockListeners -= l }

        /** System broadcasts (screen off, unlock). Explicitly exported on Android 13+ so no phone maker's rules can reject it. */
        fun listen(ctx: Context, r: BroadcastReceiver, f: IntentFilter) {
            try {
                if (Build.VERSION.SDK_INT >= 33) ctx.registerReceiver(r, f, Context.RECEIVER_EXPORTED)
                else ctx.registerReceiver(r, f)
            } catch (_: Exception) {}
        }
    }

    private val unlock = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            Store.lastUnlockElapsed = SystemClock.elapsedRealtime()
            Store.noteUnlock()
            unlockListeners.forEach { it() }
        }
    }

    private val profiles = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = Apps.refresh(c)
    }

    override fun onCreate() {
        super.onCreate()
        // The crash screen runs in its own small process and needs none of this.
        if (Crash.isCrashProcess(this)) return
        Crash.install(this)
        Store.init(this)
        try { Reminder.createChannel(this) } catch (_: Exception) {}
        listen(this, unlock, IntentFilter(Intent.ACTION_USER_PRESENT))
        // A work profile paused, resumed, added or removed: reload the app list.
        listen(this, profiles, IntentFilter().apply {
            addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE); addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
            addAction(Intent.ACTION_MANAGED_PROFILE_ADDED); addAction(Intent.ACTION_MANAGED_PROFILE_REMOVED)
            addAction(Intent.ACTION_MANAGED_PROFILE_UNLOCKED)
        })
        Apps.refresh(this)
        com.poshith.intent.guard.Break.checkEnded(this)
    }
}
