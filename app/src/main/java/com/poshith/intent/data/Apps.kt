package com.poshith.intent.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/** One launchable app (an activity in a profile). */
class AppEntry(
    val pkg: String,
    val cls: String,
    val user: UserHandle,
    val serial: Long,
    val sysLabel: String,
    val isWork: Boolean,
    val category: Int,
) {
    val key: String get() = "$pkg/$cls/$serial"
    val component: ComponentName get() = ComponentName(pkg, cls)
    val label: String get() = Store.customLabel(key) ?: sysLabel
}

/** The list of launchable apps, kept fresh by LauncherApps callbacks. Loaded off the main thread. */
object Apps {
    @Volatile var list: List<AppEntry> = emptyList(); private set
    @Volatile var loaded = false; private set
    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var registered = false

    fun onChange(l: () -> Unit) { listeners += l }
    fun removeListener(l: () -> Unit) { listeners -= l }

    fun refresh(ctx: Context) {
        val app = ctx.applicationContext
        register(app)
        io.execute {
            val fresh = try { load(app) } catch (_: Exception) { list }
            list = fresh
            loaded = true
            Store.cacheNames(fresh.associate { it.key to it.sysLabel })
            main.post { listeners.forEach { it() } }
        }
    }

    private fun register(ctx: Context) {
        if (registered) return
        registered = true
        val la = ctx.getSystemService(LauncherApps::class.java) ?: return
        la.registerCallback(object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String, user: UserHandle) = refresh(ctx)
            override fun onPackageAdded(packageName: String, user: UserHandle) = refresh(ctx)
            override fun onPackageChanged(packageName: String, user: UserHandle) = refresh(ctx)
            override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = refresh(ctx)
            override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = refresh(ctx)
        }, main)
    }

    private fun load(ctx: Context): List<AppEntry> {
        val la = ctx.getSystemService(LauncherApps::class.java) ?: return emptyList()
        val um = ctx.getSystemService(UserManager::class.java)
        val me = Process.myUserHandle()
        val out = ArrayList<AppEntry>()
        val profiles = try { la.profiles } catch (_: Exception) { listOf(me) }
        for (user in profiles) {
            // Paused work profiles are skipped. Hidden profiles (Private Space) are never listed to us.
            if (user != me && (um?.isQuietModeEnabled(user) == true)) continue
            val serial = um?.getSerialNumberForUser(user) ?: 0L
            val infos: List<LauncherActivityInfo> = try { la.getActivityList(null, user) } catch (_: Exception) { continue }
            for (info in infos) {
                val pkg = info.componentName.packageName
                if (pkg == ctx.packageName) continue
                val label = info.label?.toString()?.trim().orEmpty().ifEmpty { pkg }
                out += AppEntry(pkg, info.componentName.className, user, serial, label, user != me,
                    info.applicationInfo?.category ?: -1)
            }
        }
        out.sortWith(compareBy<AppEntry> { it.sysLabel.lowercase(Locale.getDefault()) }.thenBy { it.isWork })
        return out
    }

    fun byKey(key: String): AppEntry? = list.firstOrNull { it.key == key }

    /** Prefers the personal profile. */
    fun byPkg(pkg: String): AppEntry? = list.firstOrNull { it.pkg == pkg && !it.isWork } ?: list.firstOrNull { it.pkg == pkg }

    fun isInstalled(pkg: String) = list.any { it.pkg == pkg }

    /** Display name for a package even before the list has loaded. */
    fun labelFor(ctx: Context, pkg: String): String {
        byPkg(pkg)?.let { return it.label }
        return try {
            val pm = ctx.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (_: Exception) { pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() } }
    }

    // ------------------------------------------------------------------ search
    fun normalize(s: String): String = Normalizer.normalize(s.lowercase(Locale.getDefault()), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")

    /** Lower is better; -1 = no match. Prefix > word start > initials > anywhere. */
    fun score(label: String, query: String): Int {
        val l = normalize(label); val q = normalize(query).trim()
        if (q.isEmpty()) return -1
        if (l.startsWith(q)) return 0
        val words = l.split(' ', '-', '_', '.', '&').filter { it.isNotEmpty() }
        if (words.any { it.startsWith(q) }) return 1
        val initials = words.joinToString("") { it.take(1) }
        if (q.length >= 2 && initials.startsWith(q)) return 2
        val compact = l.replace(" ", "")
        if (compact.contains(q.replace(" ", ""))) return 3
        return -1
    }

    fun search(query: String, hidden: Set<String>, limit: Int = 8): List<AppEntry> {
        if (query.isBlank()) return emptyList()
        return list.asSequence()
            .filter { it.key !in hidden }
            .mapNotNull { a ->
                val s1 = score(a.label, query)
                val s2 = if (a.label != a.sysLabel) score(a.sysLabel, query) else -1
                val s = listOf(s1, s2).filter { it >= 0 }.minOrNull() ?: return@mapNotNull null
                s to a
            }
            .sortedWith(compareBy<Pair<Int, AppEntry>> { it.first }.thenBy { it.second.label.length }.thenBy { it.second.label })
            .take(limit)
            .map { it.second }
            .toList()
    }
}
