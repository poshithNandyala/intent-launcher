package com.poshith.intent

import android.app.Activity
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

/**
 * If Intent ever crashes, it doesn't just vanish: a plain screen (in its own process, using none of
 * Intent's own code) shows what went wrong, lets you copy it, restart, or switch back to another
 * home app. A home screen that crashes must never leave you stuck.
 */
object Crash {
    const val EXTRA = "report"
    private const val FILE = "last_crash.txt"

    fun isCrashProcess(app: Application): Boolean = processName(app).endsWith(":crash")

    private fun processName(app: Application): String = try {
        if (Build.VERSION.SDK_INT >= 28) Application.getProcessName()
        else File("/proc/self/cmdline").readText().trim { it <= ' ' }
    } catch (_: Throwable) { app.packageName }

    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val text = report(app, thread, error)
                try { File(app.filesDir, FILE).writeText(text) } catch (_: Throwable) {}
                val prefs = app.getSharedPreferences("crash", Context.MODE_PRIVATE)
                val now = System.currentTimeMillis()
                val last = prefs.getLong("at", 0)
                prefs.edit().putLong("at", now).commit()
                // Two crashes within a few seconds: let Android handle it instead of looping.
                if (now - last > 4_000) {
                    app.startActivity(Intent(app, CrashActivity::class.java)
                        .putExtra(EXTRA, text)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                    Process.killProcess(Process.myPid())
                    exitProcess(10)
                }
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(ctx: Context): String? = try {
        File(ctx.filesDir, FILE).takeIf { it.exists() }?.readText()
    } catch (_: Throwable) { null }

    private fun report(app: Application, thread: Thread, error: Throwable): String {
        val version = try { app.packageManager.getPackageInfo(app.packageName, 0).versionName } catch (_: Throwable) { "?" }
        val sw = StringWriter()
        error.printStackTrace(PrintWriter(sw))
        return ("Intent $version · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · " +
            "${Build.MANUFACTURER} ${Build.MODEL} · thread ${thread.name}\n\n$sw").take(40_000)
    }
}

/** The crash screen. Deliberately uses only the plain Android framework. */
class CrashActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val report = intent.getStringExtra(Crash.EXTRA) ?: Crash.read(this) ?: "No details were saved."
        val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val bg = if (dark) 0xFF0C0C0D.toInt() else 0xFFF6F3ED.toInt()
        val fg = if (dark) 0xFFEDEAE4.toInt() else 0xFF1B1A18.toInt()
        val fg2 = if (dark) 0xFF9C9891.toInt() else 0xFF66635D.toInt()
        val accent = if (dark) 0xFFA9C5A0.toInt() else 0xFF3F6B46.toInt()
        val onAccent = if (dark) 0xFF0F1A10.toInt() else 0xFFFFFFFF.toInt()
        val density = resources.displayMetrics.density
        fun px(v: Int) = (v * density + 0.5f).toInt()
        window.decorView.setBackgroundColor(bg)

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
        col.addView(TextView(this).apply {
            text = "Intent hit a problem"
            textSize = 26f
            setTextColor(fg)
        })
        col.addView(TextView(this).apply {
            text = "Sorry. Intent closed instead of opening properly. Nothing on your phone was changed.\n\n" +
                "Copy the details below and send them to the developer so it can be fixed. If Intent is your home app " +
                "and keeps closing, switch back to your previous home app until it's fixed."
            textSize = 15f
            setTextColor(fg2)
            setLineSpacing(0f, 1.2f)
            setPadding(0, px(10), 0, px(14))
        })
        val details = TextView(this).apply {
            text = report
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextColor(fg2)
            setTextIsSelectable(true)
            setPadding(px(14), px(12), px(14), px(12))
            background = GradientDrawable().apply { setColor(if (dark) 0xFF161618.toInt() else 0xFFFFFFFF.toInt()); cornerRadius = px(14).toFloat() }
        }
        col.addView(ScrollView(this).apply { addView(details) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        fun button(label: String, primary: Boolean, onClick: () -> Unit) = TextView(this).apply {
            text = label
            textSize = 16f
            gravity = Gravity.CENTER
            minHeight = px(52)
            setTextColor(if (primary) onAccent else fg)
            background = GradientDrawable().apply {
                cornerRadius = px(26).toFloat()
                if (primary) setColor(accent) else { setColor(0); setStroke(px(1), fg2) }
            }
            setOnClickListener { onClick() }
        }
        fun add(v: TextView) = col.addView(v, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = px(10) })
        add(button("Copy details", true) {
            try {
                getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Intent crash report", report))
                if (Build.VERSION.SDK_INT < 33) Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
            } catch (_: Throwable) {}
        })
        add(button("Restart Intent", false) {
            try { startActivity(Intent(this, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) } catch (_: Throwable) {}
            finish()
        })
        add(button("Switch home app", false) {
            val ok = try { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)); true } catch (_: Throwable) { false }
            if (!ok) try { startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (_: Throwable) {}
        })

        @Suppress("DEPRECATION")
        col.setOnApplyWindowInsetsListener { v, ins ->
            v.setPadding(px(24) + ins.systemWindowInsetLeft, px(28) + ins.systemWindowInsetTop,
                px(24) + ins.systemWindowInsetRight, px(20) + ins.systemWindowInsetBottom)
            ins
        }
        col.setPadding(px(24), px(56), px(24), px(28))
        setContentView(col)
    }
}
