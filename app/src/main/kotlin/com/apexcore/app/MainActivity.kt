package com.apexcore.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlin.math.min

class MainActivity : ComponentActivity() {

    private enum class Tab { BOOST, STORAGE }

    private var currentTab = Tab.BOOST
    private lateinit var content: FrameLayout
    private lateinit var boostTabBtn: LinearLayout
    private lateinit var storageTabBtn: LinearLayout
    private var boostScreen: View? = null
    private var storageScreen: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        setContentView(buildRoot())
        applySystemBarInsets()
        switchTab(Tab.BOOST, animate = false)
    }

    private fun applySystemBarInsets() {
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(findViewById<View>(android.R.id.content)) { _, insets ->
            val bars = insets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.systemBars()
            )
            tabBarTop = bars.top
            tabBarBottom = bars.bottom
            tabBar.setPadding(36, 36, 36, 36 + bars.bottom)
            contentTopBasePadding = bars.top
            content.setPadding(0, bars.top, 0, 0)
            insets
        }
    }

    private var tabBarTop: Int = 0
    private var tabBarBottom: Int = 0
    private var contentTopBasePadding: Int = 0
    private lateinit var tabBar: LinearLayout

    private fun dpx(v: Float): Int {
        val fm = resources.displayMetrics
        return (TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, fm) + 0.5f).toInt()
    }

    private fun buildRoot(): View {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#070A12"))
        }

        content = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(content)

        val tabBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(36, 36, 36, 36)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.parseColor("#0F1623"))
            }
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM
            }
        }
        this.tabBar = tabBar

        boostTabBtn = makeTab("BOOST", "⚡")
        storageTabBtn = makeTab("STORAGE", "▤")
        boostTabBtn.setOnClickListener { switchTab(Tab.BOOST) }
        storageTabBtn.setOnClickListener { switchTab(Tab.STORAGE) }

        tabBar.addView(boostTabBtn)
        tabBar.addView(storageTabBtn)
        root.addView(tabBar)

        return root
    }

    private fun makeTab(label: String, icon: String): LinearLayout {
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(48, 36, 48, 36)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 60.toFloat()
                setColor(Color.parseColor("#0A0E18"))
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = 18
            }
            isClickable = true
            isFocusable = true
        }
        val iconView = TextView(this).apply {
            text = icon
            textSize = 14f
            setTextColor(Color.parseColor("#9CA3AF"))
        }
        val labelView = TextView(this).apply {
            text = label
            setTextColor(Color.parseColor("#9CA3AF"))
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.2f
            setPadding(24, 0, 0, 0)
        }
        wrap.addView(iconView)
        wrap.addView(labelView)
        wrap.tag = Pair(iconView, labelView)
        return wrap
    }

    private fun styleTab(tab: LinearLayout, active: Boolean) {
        val (icon, label) = tab.tag as Pair<TextView, TextView>
        val color = if (active) Color.parseColor("#070A12") else Color.parseColor("#9CA3AF")
        icon.setTextColor(color)
        label.setTextColor(color)
        tab.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 60.toFloat()
            if (active) {
                colors = intArrayOf(
                    Color.parseColor("#00E5FF"),
                    Color.parseColor("#0EA5E9")
                )
                orientation = GradientDrawable.Orientation.LEFT_RIGHT
            } else {
                setColor(Color.parseColor("#0A0E18"))
            }
        }
    }

    private fun switchTab(tab: Tab, animate: Boolean = true) {
        currentTab = tab
        styleTab(boostTabBtn, tab == Tab.BOOST)
        styleTab(storageTabBtn, tab == Tab.STORAGE)
        val v = when (tab) {
            Tab.BOOST -> {
                if (boostScreen == null) boostScreen = BoostScreen(this)
                boostScreen
            }
            Tab.STORAGE -> {
                if (storageScreen == null) storageScreen = StorageScreen(this)
                storageScreen
            }
        }
        content.removeAllViews()
        content.addView(v)
        if (animate) {
            v!!.alpha = 0f
            v!!.translationY = 40f
            v!!.animate().alpha(1f).translationY(0f).setDuration(280)
                .setInterpolator(AccelerateDecelerateInterpolator()).start()
        }
    }
}

class BoostScreen(context: Context) : FrameLayout(context) {

    private enum class State { IDLE, BOOSTING, RESULT }

    private var state = State.IDLE
    private val boostButton: TextView
    private val boostRing: BoostRingView
    private val glowRing: GlowRingView = GlowRingView(context)
    private val resultPanel: LinearLayout
    private val freedBig: TextView
    private val freedSub: TextView
    private val statProcesses: LinearLayout
    private val statMemory: LinearLayout
    private val statLoad: LinearLayout
    private val subtitle: TextView
    private val status: TextView
    private val topBar: LinearLayout
    private var lastResult: BoostResult? = null
    private var ringAnim: ValueAnimator? = null
    private var pulseAnim: ValueAnimator? = null
    private var countAnim: ValueAnimator? = null

    init {
        setBackgroundColor(Color.parseColor("#070A12"))

        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        topBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(72, 0, 72, 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val logoDot = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#00E5FF"))
            }
            layoutParams = LinearLayout.LayoutParams(30, 30)
        }
        val logoText = TextView(context).apply {
            text = "APEX"
            setTextColor(Color.parseColor("#FFFFFF"))
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.3f
            setPadding(24, 0, 0, 0)
        }
        val spacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
        }
        val versionChip = TextView(context).apply {
            text = "v0.1.0"
            setTextColor(Color.parseColor("#6B7280"))
            textSize = 11f
            typeface = Typeface.MONOSPACE
        }
        topBar.addView(logoDot)
        topBar.addView(logoText)
        topBar.addView(spacer)
        topBar.addView(versionChip)

        val title = TextView(context).apply {
            text = "Game"
            setTextColor(Color.parseColor("#FFFFFF"))
            textSize = 48f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            letterSpacing = -0.02f
            setPadding(0, 144, 0, 0)
        }
        val titleAccent = TextView(context).apply {
            text = "Performance"
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 48f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            letterSpacing = -0.02f
        }
        subtitle = TextView(context).apply {
            text = "One tap to reclaim memory & focus CPU"
            setTextColor(Color.parseColor("#6B7280"))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, 36, 0, 0)
        }
        status = TextView(context).apply {
            text = "Ready to boost"
            setTextColor(Color.parseColor("#10B981"))
            textSize = 12f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 0)
        }

        val buttonContainer = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(780, 780).apply {
                topMargin = 120
            }
        }
        boostRing = BoostRingView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        boostButton = TextView(context).apply {
            text = "BOOST"
            setTextColor(Color.parseColor("#070A12"))
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            maxLines = 1
            letterSpacing = 0.15f
            isClickable = true
            isFocusable = true
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                colors = intArrayOf(Color.parseColor("#00E5FF"), Color.parseColor("#0EA5E9"))
                orientation = GradientDrawable.Orientation.TL_BR
            }
            setPadding(60, 60, 60, 60)
            val btnSize = 600
            layoutParams = LayoutParams(btnSize, btnSize).apply {
                gravity = Gravity.CENTER
            }
            setOnClickListener { onBoostTapped() }
        }
        buttonContainer.addView(glowRing)
        buttonContainer.addView(boostRing)
        buttonContainer.addView(boostButton)

        resultPanel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            visibility = View.GONE
            setPadding(60, 60, 60, 60)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 60.toFloat()
                setColor(Color.parseColor("#0F1623"))
                setStroke(3, Color.parseColor("#1F2937"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 72
            }
        }
        val resultHeader = TextView(context).apply {
            text = "BOOST COMPLETE"
            setTextColor(Color.parseColor("#6B7280"))
            textSize = 10f
            typeface = Typeface.MONOSPACE
            letterSpacing = 0.2f
            gravity = Gravity.CENTER
        }
        freedBig = TextView(context).apply {
            textSize = 56f
            setTextColor(Color.parseColor("#00E5FF"))
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            letterSpacing = -0.04f
            setPadding(0, 12, 0, 0)
        }
        freedSub = TextView(context).apply {
            textSize = 12f
            setTextColor(Color.parseColor("#9CA3AF"))
            gravity = Gravity.CENTER
            setPadding(0, 6, 0, 36)
        }
        val divider = View(context).apply {
            setBackgroundColor(Color.parseColor("#1F2937"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 3)
        }
        val statsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 36, 0, 0)
        }
        statProcesses = makeStat("PROCESSES", "0")
        statMemory = makeStat("MEM FREE", "—")
        statLoad = makeStat("LOAD AVG", "—")
        statsRow.addView(statProcesses)
        statsRow.addView(makeDivider())
        statsRow.addView(statMemory)
        statsRow.addView(makeDivider())
        statsRow.addView(statLoad)
        resultPanel.addView(resultHeader)
        resultPanel.addView(freedBig)
        resultPanel.addView(freedSub)
        resultPanel.addView(divider)
        resultPanel.addView(statsRow)

        column.addView(topBar)
        column.addView(title)
        column.addView(titleAccent)
        column.addView(subtitle)
        column.addView(status)
        column.addView(buttonContainer)
        column.addView(resultPanel)
        addView(column)

        renderState()
    }

    private fun dpx(v: Float): Int {
        val fm = resources.displayMetrics
        return (TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, fm) + 0.5f).toInt()
    }

    private fun makeDivider(): View {
        return View(context).apply {
            setBackgroundColor(Color.parseColor("#1F2937"))
            layoutParams = LinearLayout.LayoutParams(3, 84)
        }
    }

    private fun makeStat(label: String, value: String): LinearLayout {
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24, 0, 24, 0)
        }
        val v = TextView(context).apply {
            text = value
            textSize = 13f
            setTextColor(Color.parseColor("#FFFFFF"))
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            maxLines = 1
        }
        val l = TextView(context).apply {
            text = label
            textSize = 9f
            setTextColor(Color.parseColor("#6B7280"))
            typeface = Typeface.MONOSPACE
            letterSpacing = 0.15f
            gravity = Gravity.CENTER
            setPadding(0, 9, 0, 0)
        }
        col.addView(v)
        col.addView(l)
        col.tag = Pair(v, l)
        return col
    }

    private fun onBoostTapped() {
        if (state == State.BOOSTING) return
        if (state == State.RESULT) {
            state = State.IDLE
            renderState()
            return
        }
        state = State.BOOSTING
        renderState()
        val act = (context as? ComponentActivity) ?: return
        act.lifecycleScope.launch {
            val result = BoostManager.kick(act)
            lastResult = result
            state = State.RESULT
            renderState()
        }
    }

    private fun renderState() {
        when (state) {
            State.IDLE -> {
                boostButton.visibility = View.VISIBLE
                boostButton.text = "BOOST"
                boostButton.alpha = 1f
                boostButton.scaleX = 1f
                boostButton.scaleY = 1f
                boostButton.isClickable = true
                boostRing.visibility = View.GONE
                resultPanel.visibility = View.GONE
                status.text = "● Ready to boost"
                status.setTextColor(Color.parseColor("#10B981"))
                ringAnim?.cancel()
                pulseAnim?.cancel()
                pulseAnim = ValueAnimator.ofFloat(1f, 1.04f, 1f).apply {
                    duration = 2000
                    repeatCount = ValueAnimator.INFINITE
                    interpolator = AccelerateDecelerateInterpolator()
                    addUpdateListener {
                        val s = it.animatedValue as Float
                        boostButton.scaleX = s
                        boostButton.scaleY = s
                    }
                    start()
                }
                glowRing.setIntensity(0.4f)
            }
            State.BOOSTING -> {
                boostButton.alpha = 0.6f
                boostButton.isClickable = false
                boostRing.visibility = View.VISIBLE
                resultPanel.visibility = View.GONE
                status.text = "● Optimizing…"
                status.setTextColor(Color.parseColor("#00E5FF"))
                pulseAnim?.cancel()
                boostButton.scaleX = 0.95f
                boostButton.scaleY = 0.95f
                ringAnim?.cancel()
                ringAnim = ValueAnimator.ofFloat(0f, 360f).apply {
                    duration = 1200
                    repeatCount = ValueAnimator.INFINITE
                    interpolator = android.view.animation.LinearInterpolator()
                    addUpdateListener { boostRing.progress = it.animatedValue as Float }
                    start()
                }
                glowRing.setIntensity(1.0f)
            }
            State.RESULT -> {
                val r = lastResult
                boostButton.visibility = View.VISIBLE
                boostButton.text = "AGAIN"
                boostButton.alpha = 1f
                boostButton.scaleX = 1f
                boostButton.scaleY = 1f
                boostButton.isClickable = true
                boostRing.visibility = View.GONE
                resultPanel.visibility = View.VISIBLE
                ringAnim?.cancel()
                pulseAnim?.cancel()
                if (r == null || r.killedApps == 0) {
                    freedBig.text = "0"
                    freedBig.setTextColor(Color.parseColor("#6B7280"))
                    freedSub.text = "Already optimized"
                    status.text = "● Nothing to clean"
                    status.setTextColor(Color.parseColor("#10B981"))
                } else {
                    freedBig.setTextColor(Color.parseColor("#00E5FF"))
                    freedSub.text = "MB reclaimed"
                    status.text = "● Available: ${r.afterAvailMb} MB"
                    status.setTextColor(Color.parseColor("#00E5FF"))
                    animateCount(freedBig, 0L, r.freedMb)
                }
                updateStatValue(statProcesses, "${r?.killedApps ?: 0}")
                updateStatValue(
                    statMemory,
                    "${r?.beforeAvailMb ?: 0}→${r?.afterAvailMb ?: 0}"
                )
                updateStatValue(
                    statLoad,
                    "${"%.2f".format(r?.beforeLoadAvg ?: 0f)}→${"%.2f".format(r?.afterLoadAvg ?: 0f)}"
                )
                resultPanel.alpha = 0f
                resultPanel.translationY = 40f
                resultPanel.animate().alpha(1f).translationY(0f).setDuration(400)
                    .setInterpolator(AccelerateDecelerateInterpolator()).start()
                glowRing.setIntensity(0.3f)
            }
        }
    }

    private fun animateCount(view: TextView, from: Long, to: Long) {
        countAnim?.cancel()
        countAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                val f = it.animatedValue as Float
                val v = (from + (to - from) * f).toLong()
                view.text = "$v"
            }
            start()
        }
    }

    private fun updateStatValue(col: LinearLayout, value: String) {
        val (v, _) = col.tag as Pair<TextView, TextView>
        v.text = value
    }
}

class StorageScreen(context: Context) : FrameLayout(context) {

    private val scrollContainer: LinearLayout
    private val statusText: TextView
    private val usageBar: UsageBarView
    private val usageLabel: TextView
    private val appListContainer: LinearLayout
    private val refreshIndicator: TextView
    private var report: StorageReport? = null
    private val handler = Handler(Looper.getMainLooper())

    init {
        setBackgroundColor(Color.parseColor("#070A12"))
        scrollContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 144, 60, 360)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        val topBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12, 0, 12, 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val logoDot = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#00E5FF"))
            }
            layoutParams = LinearLayout.LayoutParams(30, 30)
        }
        val title = TextView(context).apply {
            text = "STORAGE"
            setTextColor(Color.parseColor("#FFFFFF"))
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.3f
            setPadding(24, 0, 0, 0)
        }
        val spacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
        }
        refreshIndicator = TextView(context).apply {
            text = "Tap to scan"
            setTextColor(Color.parseColor("#6B7280"))
            textSize = 11f
            typeface = Typeface.MONOSPACE
            isClickable = true
            setOnClickListener { refresh() }
            setPadding(36, 24, 36, 24)
        }
        topBar.addView(logoDot)
        topBar.addView(title)
        topBar.addView(spacer)
        topBar.addView(refreshIndicator)
        scrollContainer.addView(topBar)

        val heroTitle = TextView(context).apply {
            text = "Device"
            setTextColor(Color.parseColor("#FFFFFF"))
            textSize = 40f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = -0.02f
            setPadding(0, 96, 0, 0)
        }
        val heroAccent = TextView(context).apply {
            text = "Storage"
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 40f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = -0.02f
        }
        scrollContainer.addView(heroTitle)
        scrollContainer.addView(heroAccent)

        statusText = TextView(context).apply {
            text = "Tap refresh to scan your device"
            setTextColor(Color.parseColor("#6B7280"))
            textSize = 13f
            setPadding(0, 24, 0, 0)
        }
        scrollContainer.addView(statusText)

        usageBar = UsageBarView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                42
            ).apply {
                topMargin = 60
            }
        }
        scrollContainer.addView(usageBar)

        usageLabel = TextView(context).apply {
            text = "— of — used"
            setTextColor(Color.parseColor("#9CA3AF"))
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setPadding(0, 24, 0, 0)
        }
        scrollContainer.addView(usageLabel)

        val topAppsHeader = TextView(context).apply {
            text = "TOP APPS BY SIZE"
            setTextColor(Color.parseColor("#6B7280"))
            textSize = 10f
            typeface = Typeface.MONOSPACE
            letterSpacing = 0.2f
            setPadding(0, 72, 0, 24)
        }
        scrollContainer.addView(topAppsHeader)

        appListContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        scrollContainer.addView(appListContainer)

        val scroll = android.widget.ScrollView(context).apply {
            setVerticalScrollBarEnabled(false)
            isFillViewport = true
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        scroll.addView(scrollContainer)
        addView(scroll)

        renderEmpty()
    }

    private fun dpx(v: Float): Int {
        val fm = resources.displayMetrics
        return (TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, fm) + 0.5f).toInt()
    }

    fun refresh() {
        refreshIndicator.text = "Scanning…"
        val act = (context as? ComponentActivity) ?: return
        act.lifecycleScope.launch {
            val r = StorageManager.scan(act)
            report = r
            renderReport(r)
        }
    }

    private fun renderEmpty() {
        usageBar.setProgress(0f)
        usageLabel.text = "Tap refresh to scan"
        appListContainer.removeAllViews()
    }

    private fun renderReport(r: StorageReport) {
        refreshIndicator.text = "Refresh"
        val totalGb = r.totalBytes / 1024.0 / 1024.0 / 1024.0
        val usedGb = r.usedBytes / 1024.0 / 1024.0 / 1024.0
        val freeGb = r.freeBytes / 1024.0 / 1024.0 / 1024.0
        statusText.text = if (r.accurate)
            "${"%.1f".format(usedGb)} GB used of ${"%.1f".format(totalGb)} GB"
        else
            "${"%.1f".format(usedGb)} GB used · Grant access for app sizes"
        usageBar.setProgress(r.usedPercent / 100f)
        usageLabel.text = "${r.usedPercent}% · ${"%.1f".format(freeGb)} GB free"

        if (!r.accurate) {
            renderPermissionCard()
            return
        }

        appListContainer.removeAllViews()
        for ((i, app) in r.apps.withIndex()) {
            if (i >= 30) break
            appListContainer.addView(makeAppRow(i + 1, app))
        }
    }

    private fun renderPermissionCard() {
        appListContainer.removeAllViews()
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 42.toFloat()
                setColor(Color.parseColor("#0F1623"))
                setStroke(3, Color.parseColor("#00E5FF"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 18
            }
        }
        val title = TextView(context).apply {
            text = "Permission needed"
            setTextColor(Color.parseColor("#FFFFFF"))
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val desc = TextView(context).apply {
            text = "Grant Usage Access to see real per-app size (APK + data + cache)."
            setTextColor(Color.parseColor("#9CA3AF"))
            textSize = 12f
            setPadding(0, 12, 0, 0)
        }
        val grantBtn = TextView(context).apply {
            text = "GRANT ACCESS"
            setTextColor(Color.parseColor("#070A12"))
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.2f
            gravity = Gravity.CENTER
            setPadding(36, 24, 36, 24)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 24f
                colors = intArrayOf(Color.parseColor("#00E5FF"), Color.parseColor("#0EA5E9"))
                orientation = GradientDrawable.Orientation.LEFT_RIGHT
            }
            isClickable = true
            setOnClickListener {
                val intent = android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
                intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 24
            }
        }
        card.addView(title)
        card.addView(desc)
        card.addView(grantBtn)
        appListContainer.addView(card)
    }

    private fun makeAppRow(rank: Int, app: AppStorage): View {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(42, 36, 42, 36)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 42.toFloat()
                setColor(Color.parseColor("#0F1623"))
                setStroke(3, Color.parseColor("#1F2937"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 18
            }
        }
        val rankView = TextView(context).apply {
            text = rank.toString()
            setTextColor(Color.parseColor("#6B7280"))
            textSize = 12f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(72, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val labelCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 0, 24, 0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val label = TextView(context).apply {
            text = app.label
            setTextColor(Color.parseColor("#FFFFFF"))
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            maxLines = 1
        }
        val detail = TextView(context).apply {
            val parts = mutableListOf<String>()
            if (app.isSystem) parts.add("system")
            parts.add(app.packageName)
            if (app.dataBytes > 0) parts.add("data ${formatSize(app.dataBytes)}")
            if (app.cacheBytes > 0) parts.add("cache ${formatSize(app.cacheBytes)}")
            text = parts.joinToString(" · ")
            setTextColor(Color.parseColor("#6B7280"))
            textSize = 10f
            typeface = Typeface.MONOSPACE
            maxLines = 1
        }
        labelCol.addView(label)
        labelCol.addView(detail)
        val sizeView = TextView(context).apply {
            text = formatSize(app.totalBytes)
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 13f
            typeface = Typeface.MONOSPACE
        }
        card.addView(rankView)
        card.addView(labelCol)
        card.addView(sizeView)
        return card
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1024L * 1024L * 1024L -> "%.1f GB".format(bytes / 1024.0 / 1024.0 / 1024.0)
            bytes >= 1024L * 1024L -> "%.0f MB".format(bytes / 1024.0 / 1024.0)
            bytes >= 1024L -> "%.0f KB".format(bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}

class BoostRingView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#00E5FF")
        alpha = 240
    }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        color = Color.parseColor("#1F2937")
    }
    private val rect = RectF()
    var progress: Float = 0f
        set(value) { field = value; invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return
        val pad = paint.strokeWidth + 6f
        val cx = width / 2f
        val cy = height / 2f
        val r = (min(width, height) / 2f) - pad
        rect.set(cx - r, cy - r, cx + r, cy + r)
        canvas.drawArc(rect, -90f, 360f, false, trackPaint)
        canvas.drawArc(rect, -90f, progress, false, paint)
    }
}

class GlowRingView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.parseColor("#00E5FF")
        alpha = 60
    }
    private var intensity: Float = 0.4f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 3000
        repeatCount = ValueAnimator.INFINITE
        interpolator = AccelerateDecelerateInterpolator()
        addUpdateListener { invalidate() }
    }
    init {
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
    }
    fun setIntensity(v: Float) { intensity = v; invalidate() }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); animator.start() }
    override fun onDetachedFromWindow() { animator.cancel(); super.onDetachedFromWindow() }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return
        val cx = width / 2f
        val cy = height / 2f
        val maxR = min(width, height) * 0.5f
        val t = (System.currentTimeMillis() % 3000L) / 3000f
        for (i in 0..2) {
            val r = maxR * (0.5f + i * 0.25f) +
                kotlin.math.sin((t + i) * Math.PI * 2).toFloat() * 6f
            paint.alpha = (intensity * 60f * (1f - i * 0.3f)).toInt().coerceIn(0, 255)
            canvas.drawCircle(cx, cy, r, paint)
        }
    }
}

class UsageBarView(context: Context) : View(context) {
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#1F2937")
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val shaderColors = intArrayOf(
        Color.parseColor("#00E5FF"),
        Color.parseColor("#0EA5E9")
    )
    private val rect = RectF()
    private var progress: Float = 0f

    fun setProgress(p: Float) {
        progress = p.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return
        val r = height / 2f
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rect, r, r, trackPaint)
        val w = width * progress
        if (w > 0) {
            val fillRect = RectF(0f, 0f, w, height.toFloat())
            fillPaint.shader = LinearGradientH(0f, 0f, w, 0f, shaderColors)
            canvas.drawRoundRect(fillRect, r, r, fillPaint)
        }
    }

    private fun LinearGradientH(x0: Float, y0: Float, x1: Float, y1: Float, colors: IntArray) =
        android.graphics.LinearGradient(x0, y0, x1, y1, colors, null, android.graphics.Shader.TileMode.CLAMP)
}
