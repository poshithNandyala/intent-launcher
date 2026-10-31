package com.poshith.intent

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
    }

    private val unlock = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            Store.lastUnlockElapsed = SystemClock.elapsedRealtime()
            Store.noteUnlock()
            unlockListeners.forEach { it() }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Reminder.createChannel(this)
        // A protected system broadcast: no export flag needed.
        registerReceiver(unlock, IntentFilter(Intent.ACTION_USER_PRESENT))
        Apps.refresh(this)
    }
}
