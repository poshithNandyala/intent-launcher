package com.poshith.intent.ui

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.poshith.intent.data.AppEntry
import com.poshith.intent.data.Apps

/**
 * Choose apps from a searchable list. Ids are app keys (an activity in a profile) or, with
 * byPackage, package names (rules apply per package).
 */
class AppPicker(
    private val a: BaseActivity,
    private val title: String,
    private val subtitle: String,
    initial: List<String>,
    private val byPackage: Boolean,
    private val max: Int = 0,
    private val suggested: Set<String> = emptySet(),
    private val reasonDisabled: (AppEntry) -> String? = { null },
    private val doneLabel: String = "Done",
    private val showDone: Boolean = true,
    private val onChange: ((List<String>) -> Unit)? = null,
    private val onDone: (List<String>) -> Unit,
) {
    val selected = ArrayList<String>(initial.distinct())
    private val list = a.vbox()
    private val counter = a.text("", 14f, P.text2)
    private var query = ""
    private val appsListener: () -> Unit = { render() }

    private fun id(e: AppEntry) = if (byPackage) e.pkg else e.key

    fun build(): View {
        val root = a.vbox()
        root.addView(a.text(title, 28f, P.text, Fonts.medium(a)).apply { setPadding(a.dp(24), 0, a.dp(24), 0) }, lp())
        if (subtitle.isNotBlank()) root.addView(a.paragraph(subtitle), lp())
        val search = a.field("Search apps", caps = false)
        search.onText { query = it; render() }
        root.addView(FrameLayout(a).apply { setPadding(a.dp(20), a.dp(10), a.dp(20), a.dp(6)); addView(search) }, lp())
        root.addView(a.scroller(list), lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val bar = a.hbox().apply { setPadding(a.dp(24), a.dp(10), a.dp(20), a.dp(4)) }
        bar.addView(counter, lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (showDone) bar.addView(a.button(doneLabel, Kind.PRIMARY) { onDone(selected.toList()) }, lp(ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(bar, lp())
        root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { Apps.onChange(appsListener) }
            override fun onViewDetachedFromWindow(v: View) { Apps.removeListener(appsListener) }
        })
        render()
        return root
    }

    private fun entries(): List<AppEntry> {
        val all = Apps.list
        return if (byPackage) all.distinctBy { it.pkg } else all
    }

    fun render() {
        list.removeAllViews()
        updateCounter()
        val all = entries()
        if (all.isEmpty()) {
            list.addView(a.paragraph(if (Apps.loaded) "No apps found." else "Loading your apps…", 15f, P.text3))
            return
        }
        val q = query.trim()
        val shown = if (q.isEmpty()) all else all.filter { Apps.score(it.label, q) >= 0 || Apps.score(it.sysLabel, q) >= 0 }
        if (q.isEmpty() && suggested.isNotEmpty()) {
            val sug = shown.filter { id(it) in suggested }
            if (sug.isNotEmpty()) {
                list.addView(a.sectionTitle("Suggested"))
                sug.forEach { list.addView(row(it), lp()) }
                list.addView(a.sectionTitle("All apps"))
            }
            shown.filter { id(it) !in suggested }.forEach { list.addView(row(it), lp()) }
        } else shown.forEach { list.addView(row(it), lp()) }
        if (shown.isEmpty()) list.addView(a.paragraph("No app matches “$q”.", 15f, P.text3))
    }

    private fun updateCounter() {
        counter.text = when {
            max > 0 -> "${selected.size} of $max" + if (selected.size >= max) " · that's the most" else ""
            else -> "${selected.size} selected"
        }
    }

    private fun row(e: AppEntry): View {
        val myId = id(e)
        val reason = reasonDisabled(e)
        val row = a.hbox().apply {
            minimumHeight = a.dp(56)
            setPadding(a.dp(24), a.dp(8), a.dp(24), a.dp(8))
            background = ripple(null)
            isClickable = reason == null
        }
        val mark = a.text("", 18f, P.accent, Fonts.medium(a)).apply { minWidth = a.dp(30) }
        val col = a.vbox()
        col.addView(a.text(e.label + if (e.isWork) "  ·  work" else "", 17f, if (reason == null) P.text else P.text3, lines = 1))
        if (reason != null) col.addView(a.text(reason, 13f, P.text3))
        row.addView(mark)
        row.addView(col, lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        fun paint() {
            val on = myId in selected
            mark.text = if (reason != null) "–" else if (on) "●" else "○"
            mark.setTextColor(if (on) P.accent else P.text3)
            row.contentDescription = e.label + if (on) ", selected" else ""
        }
        paint()
        if (reason == null) row.setOnClickListener {
            it.tick()
            if (myId in selected) selected.remove(myId)
            else {
                if (max > 0 && selected.size >= max) {
                    com.poshith.intent.guard.Launch.toast(a, "Up to $max. Fewer is calmer.")
                    return@setOnClickListener
                }
                selected += myId
            }
            paint(); updateCounter()
            onChange?.invoke(selected.toList())
        }
        return row
    }
}

/** Presents any full-screen view as a layer (back closes it). */
class Overlay(private val a: BaseActivity, private val content: View) {
    private val root = FrameLayout(a)
    private var open = false
    private val closer: () -> Unit = { close() }
    private val bars = BaseActivity.BarLayer(P.dark, P.dark)
    private val barL: (Bars) -> Unit = { b -> root.setPadding(b.left, b.top + a.dp(20), b.right, b.bottomOrIme + a.dp(12)) }
    var onClose: (() -> Unit)? = null

    init {
        root.setBackgroundColor(P.bg)
        root.isClickable = true
        root.addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    fun show(): Overlay {
        if (open) return this
        open = true
        a.host.addView(root, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        a.pushBack(closer)
        a.onBars(barL)
        a.pushBarLayer(bars)
        if (a.animationsOn()) { root.alpha = 0f; root.animate().alpha(1f).setDuration(160).start() }
        return this
    }

    fun close() {
        if (!open) return
        open = false
        a.dropBack(closer)
        a.removeBars(barL)
        a.dropBarLayer(bars)
        hideKeyboard(a, root)
        (root.parent as? ViewGroup)?.removeView(root)
        onClose?.invoke()
    }
}

fun gravityText(t: TextView, g: Int) { t.gravity = g or Gravity.CENTER_VERTICAL }
fun LinearLayout.addAll(vararg v: View) { v.forEach { addView(it) } }
