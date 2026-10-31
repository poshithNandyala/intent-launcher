package com.poshith.intent.guard

import android.app.Activity
import android.app.ActivityOptions
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.os.UserHandle
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import com.poshith.intent.GateActivity
import com.poshith.intent.HomeActivity
import com.poshith.intent.core.Catalog
import com.poshith.intent.core.Decision
import com.poshith.intent.core.Mode
import com.poshith.intent.core.Policy
import com.poshith.intent.core.Situation
import com.poshith.intent.data.AppEntry
import com.poshith.intent.data.Apps
import com.poshith.intent.data.Stats
import com.poshith.intent.data.Store

/** Opening apps, always through the rules. */
object Launch {

    // ------------------------------------------------------------------ the one decision path
    fun decide(ctx: Context, pkg: String): Decision {
        val now = System.currentTimeMillis()
        val rule = Store.rule(pkg)
        val active = Store.sessions { it.isActive(pkg, SystemClock.elapsedRealtime()) }
        val s = Situation(
            nowWallMs = now,
            minuteOfDay = Store.minuteOfDay(now),
            focusUntilWallMs = Store.focusUntil,
            focusIsBreak = Store.focusIsBreak,
            focusIsBasic = Store.focusKind == "basic",
            morningUntilWallMs = Store.morningUntil,
            windDown = Store.windDown,
            checkIns = Store.checkIns,
            windDownStrict = Store.windDownStrict,
            opensToday = Stats.sessionsToday(pkg),
            hasActiveSession = active,
            protectedApp = isProtected(ctx, pkg),
            recentlyEnded = Store.endedRecently(pkg),
        )
        return Policy.decide(rule, s)
    }

    /** Tap on the home screen or a search result. */
    fun open(a: Activity, app: AppEntry, from: View?) {
        when (val d = decide(a, app.pkg)) {
            is Decision.Open -> start(a, app, from)
            else -> GateActivity.show(a, app.pkg, app.key, fromLauncher = true)
        }
    }

    fun start(ctx: Context, app: AppEntry, from: View? = null) {
        val la = ctx.getSystemService(LauncherApps::class.java)
        try {
            val opts = from?.let { ActivityOptions.makeClipRevealAnimation(it, 0, 0, it.width, it.height).toBundle() }
            la.startMainActivity(app.component, app.user, null, opts)
        } catch (e: Exception) {
            if (!startPackage(ctx, app.pkg, app.user)) toast(ctx, "Couldn't open ${app.label}")
        }
    }

    /** Opens a package's main screen (used after a pause, when we only know the package). */
    fun startPackage(ctx: Context, pkg: String, user: UserHandle? = null): Boolean {
        Apps.byPkg(pkg)?.let { app ->
            if (user == null || app.user == user) {
                return try {
                    ctx.getSystemService(LauncherApps::class.java).startMainActivity(app.component, app.user, null, null); true
                } catch (_: Exception) { false }
            }
        }
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        return try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true } catch (_: Exception) { false }
    }

    fun goHome(ctx: Context) {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { ctx.startActivity(i) } catch (_: Exception) {
            ctx.startActivity(Intent(ctx, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    // ------------------------------------------------------------------ protected apps
    @Volatile private var dynamicProtected: Set<String> = emptySet()
    @Volatile private var dynamicAt = 0L

    /** Calls, SMS, keyboards, launchers, settings: never paused. */
    fun isProtected(ctx: Context, pkg: String): Boolean {
        if (pkg == ctx.packageName || pkg in Catalog.PROTECTED) return true
        val now = SystemClock.elapsedRealtime()
        if (now - dynamicAt > 60_000 || dynamicAt == 0L) {
            dynamicProtected = computeProtected(ctx)
            dynamicAt = now
        }
        return pkg in dynamicProtected
    }

    private fun computeProtected(ctx: Context): Set<String> {
        val out = HashSet<String>()
        try { ctx.getSystemService(TelecomManager::class.java)?.defaultDialerPackage?.let { out += it } } catch (_: Exception) {}
        try { Telephony.Sms.getDefaultSmsPackage(ctx)?.let { out += it } } catch (_: Exception) {}
        try {
            (ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).enabledInputMethodList.forEach { out += it.packageName }
        } catch (_: Exception) {}
        try {
            val homes = ctx.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            homes.forEach { out += it.activityInfo.packageName }
        } catch (_: Exception) {}
        try {
            ctx.packageManager.resolveActivity(Intent(MediaStore.ACTION_IMAGE_CAPTURE), PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName?.let { if (it != "android") out += it }
        } catch (_: Exception) {}
        return out
    }

    /** Package that handles an intent by default (null if none or only the system chooser). */
    fun resolve(ctx: Context, i: Intent): String? = try {
        ctx.packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName?.takeIf { it != "android" }
    } catch (_: Exception) { null }

    // ------------------------------------------------------------------ the default tools
    fun dialer(ctx: Context) = tryStart(ctx, Intent(Intent.ACTION_DIAL), "No phone app found")
    fun camera(ctx: Context) = tryStart(ctx, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA), "No camera app found")
    fun alarms(ctx: Context) = tryStart(ctx, Intent(AlarmClock.ACTION_SHOW_ALARMS), "No clock app found")
    fun calendar(ctx: Context) {
        val uri = CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build()
        tryStart(ctx, Intent(Intent.ACTION_VIEW, uri), "No calendar app found")
    }

    fun appInfo(ctx: Context, app: AppEntry) {
        try {
            ctx.getSystemService(LauncherApps::class.java).startAppDetailsActivity(app.component, app.user, null, null)
        } catch (_: Exception) {
            tryStart(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}")), "Couldn't open app info")
        }
    }

    fun uninstall(ctx: Context, app: AppEntry) {
        @Suppress("DEPRECATION")
        val i = Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:${app.pkg}"))
            .putExtra(Intent.EXTRA_USER, app.user)
        tryStart(ctx, i, "Couldn't uninstall")
    }

    fun notificationSettings(ctx: Context, pkg: String) {
        val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
        if (!tryStart(ctx, i, null)) tryStart(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")), "Couldn't open settings")
    }

    fun accessibilitySettings(ctx: Context) = tryStart(ctx, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), "Couldn't open settings")

    /** Xiaomi, Redmi and POCO (MIUI / HyperOS) block apps from opening screens from the background
     *  unless "Display pop-up windows while running in the background" is allowed. */
    val isXiaomi: Boolean get() = listOf(Build.MANUFACTURER, Build.BRAND).any {
        it.equals("xiaomi", true) || it.equals("redmi", true) || it.equals("poco", true)
    }

    fun xiaomiPermissions(ctx: Context) {
        val miui = Intent("miui.intent.action.APP_PERM_EDITOR")
            .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
            .putExtra("extra_pkgname", ctx.packageName)
        val ok = try { if (ctx !is Activity) miui.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(miui); true } catch (_: Exception) { false }
        if (!ok) ownAppInfo(ctx)
    }
    fun usageSettings(ctx: Context) {
        val i = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        if (Build.VERSION.SDK_INT >= 29) i.data = Uri.parse("package:${ctx.packageName}")
        if (!tryStart(ctx, i, null)) tryStart(ctx, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS), "Couldn't open settings")
    }
    fun ownAppInfo(ctx: Context) = tryStart(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")), null)
    fun homeSettings(ctx: Context) {
        if (!tryStart(ctx, Intent(Settings.ACTION_HOME_SETTINGS), null))
            tryStart(ctx, Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS), "Open Settings > Apps > Default apps")
    }
    fun digitalWellbeing(ctx: Context) {
        val dw = ctx.packageManager.getLaunchIntentForPackage("com.google.android.apps.wellbeing")
        if (dw != null && tryStart(ctx, dw, null)) return
        accessibilitySettings(ctx)
    }

    /** Asks Android to make Intent the home app (role dialog on Android 10+, settings before). */
    /** Returns true if Android's "choose home app" dialog was shown (its result comes back to [a]). */
    fun requestHome(a: Activity, requestCode: Int = 41): Boolean {
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = a.getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                try { a.startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), requestCode); return true } catch (_: Exception) {}
            }
        }
        homeSettings(a)
        return false
    }

    fun isDefaultHome(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = ctx.getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) return rm.isRoleHeld(RoleManager.ROLE_HOME)
        }
        val r = ctx.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
        return r?.activityInfo?.packageName == ctx.packageName
    }

    fun isGuardOn(ctx: Context): Boolean {
        if (FocusGuardService.running) return true
        return try {
            val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
            val me = ComponentName(ctx, FocusGuardService::class.java)
            enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        } catch (_: Exception) { false }
    }

    fun tryStart(ctx: Context, i: Intent, fail: String?): Boolean = try {
        if (ctx !is Activity) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i); true
    } catch (e: ActivityNotFoundException) { fail?.let { toast(ctx, it) }; false }
    catch (e: SecurityException) { fail?.let { toast(ctx, it) }; false }

    fun toast(ctx: Context, msg: String) = Toast.makeText(ctx.applicationContext, msg, Toast.LENGTH_SHORT).show()

    fun me(): UserHandle = Process.myUserHandle()

    fun modeLabel(m: Mode) = when (m) {
        Mode.ESSENTIAL -> "Essential"
        Mode.NORMAL -> "Normal"
        Mode.MINDFUL -> "Mindful"
        Mode.BATCHED -> "Check-in times"
        Mode.BLOCKED -> "Blocked"
    }
}
