package com.apexcore.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.apexcore.app.freeze.FreezeFramework
import com.apexcore.app.freeze.FreezeResult
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {

    private enum class State { IDLE, BOOSTING, RESULT }

    private var state = State.IDLE
    private lateinit var boostButton: TextView
    private lateinit var boostRing: BoostRingView
    private lateinit var glowRing: GlowRingView
    private lateinit var resultPanel: LinearLayout
    private lateinit var freedBig: TextView
    private lateinit var freedSub: TextView
    private lateinit var statProcesses: LinearLayout
    private lateinit var statMemory: LinearLayout
    private lateinit var statLoad: LinearLayout
    private lateinit var statMode: LinearLayout
    private lateinit var subtitle: TextView
    private lateinit var status: TextView
    private lateinit var topBar: LinearLayout
    private var lastResult: FreezeResult? = null
    private var ringAnim: ValueAnimator? = null
    private var pulseAnim: ValueAnimator? = null
    private var countAnim: ValueAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FreezeFramework.init(this)
        setContentView(buildLayout())
        lifecycleScope.launch {
            val backend = FreezeFramework.detect()
            status.text = "● Freeze: ${backend.name}"
        }
        renderState()
    }

    override fun onDestroy() {
        ringAnim?.cancel()
        pulseAnim?.cancel()
        countAnim?.cancel()
        super.onDestroy()
    }

    private fun dpInt(v: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()

    private fun dpf(v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    private fun buildLayout(): View {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#070A12"))
        }

        glowRing = GlowRingView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(glowRing)

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).apply {
                topMargin = 144
                bottomMargin = 144
            }
        }

        topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(72, 0, 72, 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val logoDot = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#00E5FF"))
            }
            layoutParams = LinearLayout.LayoutParams(30, 30)
        }
        val logoText = TextView(this).apply {
            text = "APEX"
            setTextColor(Color.parseColor("#FFFFFF"))
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.3f
            setPadding(24, 0, 0, 0)
        }
        val spacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
        }
        val versionChip = TextView(this).apply {
            text = "v0.1.0"
            setTextColor(Color.parseColor("#6B7280"))
            textSize = 11f
            typeface = Typeface.MONOSPACE
        }
        topBar.addView(logoDot)
        topBar.addView(logoText)
        topBar.addView(spacer)
        topBar.addView(versionChip)

        val title = TextView(this).apply {
            text = "Game"
            setTextColor(Color.parseColor("#FFFFFF"))
            textSize = 56f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            letterSpacing = -0.02f
            setPadding(0, 192, 0, 0)
        }
        val titleAccent = TextView(this).apply {
            text = "Performance"
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 56f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            letterSpacing = -0.02f
        }

        subtitle = TextView(this).apply {
            text = "One tap to reclaim memory & focus CPU"
            setTextColor(Color.parseColor("#6B7280"))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, 36, 0, 0)
        }

        status = TextView(this).apply {
            text = "Ready to boost"
            setTextColor(Color.parseColor("#10B981"))
            textSize = 12f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 0)
        }

        val buttonContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(840, 840).apply {
                topMargin = 168
            }
        }

        boostRing = BoostRingView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        boostButton = TextView(this).apply {
            text = "BOOST"
            setTextColor(Color.parseColor("#070A12"))
            textSize = 26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            maxLines = 1
            letterSpacing = 0.15f
            isClickable = true
            isFocusable = true
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                colors = intArrayOf(
                    Color.parseColor("#00E5FF"),
                    Color.parseColor("#0EA5E9")
                )
                orientation = GradientDrawable.Orientation.TL_BR
            }
            setPadding(72, 72, 72, 72)
            val btnSize = 660
            layoutParams = FrameLayout.LayoutParams(btnSize, btnSize).apply {
                gravity = Gravity.CENTER
            }
            setOnClickListener { onBoostTapped() }
        }

        buttonContainer.addView(boostRing)
        buttonContainer.addView(boostButton)

        resultPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            visibility = View.GONE
            setPadding(72, 72, 72, 72)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 20f
                setColor(Color.parseColor("#0F1623"))
                setStroke(3, Color.parseColor("#1F2937"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 144
            }
        }

        val resultHeader = TextView(this).apply {
            text = "BOOST COMPLETE"
            setTextColor(Color.parseColor("#6B7280"))
            textSize = 10f
            typeface = Typeface.MONOSPACE
            letterSpacing = 0.2f
            gravity = Gravity.CENTER
        }

        freedBig = TextView(this).apply {
            textSize = 64f
            setTextColor(Color.parseColor("#00E5FF"))
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            letterSpacing = -0.04f
            setPadding(0, 24, 0, 0)
        }

        freedSub = TextView(this).apply {
            text = "MB reclaimed"
            setTextColor(Color.parseColor("#9CA3AF"))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 60)
        }

        val divider = View(this).apply {
            setBackgroundColor(Color.parseColor("#1F2937"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 3)
        }

        val statsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 48, 0, 0)
        }

        statProcesses = makeStat("PROCESSES", "0")
        statMemory = makeStat("MEM FREE", "—")
        statLoad = makeStat("LOAD AVG", "—")
        statMode = makeStat("MODE", "—")

        statsRow.addView(statProcesses)
        statsRow.addView(makeDivider())
        statsRow.addView(statMemory)
        statsRow.addView(makeDivider())
        statsRow.addView(statLoad)
        statsRow.addView(makeDivider())
        statsRow.addView(statMode)

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

        root.addView(column)
        return root
    }

    private fun makeDivider(): View {
        return View(this).apply {
            setBackgroundColor(Color.parseColor("#1F2937"))
            layoutParams = LinearLayout.LayoutParams(3, 96)
        }
    }

    private fun makeStat(label: String, value: String): LinearLayout {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(36, 0, 36, 0)
        }
        val v = TextView(this).apply {
            text = value
            textSize = 16f
            setTextColor(Color.parseColor("#FFFFFF"))
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            maxLines = 1
        }
        val l = TextView(this).apply {
            text = label
            textSize = 9f
            setTextColor(Color.parseColor("#6B7280"))
            typeface = Typeface.MONOSPACE
            letterSpacing = 0.15f
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 0)
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

        lifecycleScope.launch {
            val result = FreezeFramework.freezeAll(this@MainActivity)
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
                val backend = FreezeFramework.activeBackend.value?.name ?: "…"
                status.text = "● Ready to boost · $backend"
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
                val backend = FreezeFramework.activeBackend.value?.name ?: "…"
                status.text = "● Freezing via $backend…"
                status.setTextColor(Color.parseColor("#00E5FF"))
                pulseAnim?.cancel()
                boostButton.scaleX = 0.95f
                boostButton.scaleY = 0.95f
                ringAnim?.cancel()
                ringAnim = ValueAnimator.ofFloat(0f, 360f).apply {
                    duration = 1200
                    repeatCount = ValueAnimator.INFINITE
                    interpolator = android.view.animation.LinearInterpolator()
                    addUpdateListener {
                        boostRing.progress = it.animatedValue as Float
                    }
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
                if (r == null || r.killed == 0) {
                    freedBig.text = "0"
                    freedBig.setTextColor(Color.parseColor("#6B7280"))
                    freedSub.text = "Already optimized"
                    status.text = "● Nothing to clean · ${r?.backend ?: ""}".trim()
                    status.setTextColor(Color.parseColor("#10B981"))
                } else {
                    freedBig.setTextColor(Color.parseColor("#00E5FF"))
                    freedSub.text = "MB reclaimed"
                    status.text = "● Freezed ${r.killed} apps via ${r.backend}"
                    status.setTextColor(Color.parseColor("#00E5FF"))
                    animateCount(freedBig, 0L, r.freedMb)
                }
                updateStat(statProcesses, "${r?.killed ?: 0}")
                updateStat(
                    statMemory,
                    "${r?.beforeAvailMb ?: 0}→${r?.afterAvailMb ?: 0}"
                )
                updateStat(statLoad, "—")
                updateStat(statMode, r?.backend ?: "—")
                resultPanel.alpha = 0f
                resultPanel.translationY = 60f
                resultPanel.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(500)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .start()
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

    private fun updateStat(col: LinearLayout, value: String) {
        val (v, _) = col.tag as Pair<TextView, TextView>
        v.text = value
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
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return
        val pad = paint.strokeWidth + 6f
        val cx = width / 2f
        val cy = height / 2f
        val r = (minOf(width, height) / 2f) - pad
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
    private val rect = RectF()
    private var intensity: Float = 0.4f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 3000
        repeatCount = ValueAnimator.INFINITE
        interpolator = AccelerateDecelerateInterpolator()
        addUpdateListener {
            invalidate()
        }
    }

    fun setIntensity(v: Float) {
        intensity = v
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return
        val cx = width / 2f
        val cy = height * 0.62f
        val maxR = minOf(width, height) * 0.45f
        val t = (System.currentTimeMillis() % 3000L) / 3000f
        for (i in 0..2) {
            val r = maxR * (0.5f + i * 0.25f) + sin((t + i) * Math.PI * 2).toFloat() * 12f
            paint.alpha = (intensity * 60f * (1f - i * 0.3f)).toInt().coerceIn(0, 255)
            canvas.drawCircle(cx, cy, r, paint)
        }
    }
}
