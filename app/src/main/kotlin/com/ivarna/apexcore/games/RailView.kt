package com.ivarna.apexcore.games

import android.content.Context
import android.content.SharedPreferences
import android.graphics.*
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.res.ResourcesCompat
import com.ivarna.apexcore.R
import kotlin.math.abs

/**
 * §7.12 Phantom Rail — collapsed 2dp brass filament; expands to smoked-glass
 * telemetry column. Pure Canvas View: FPS, RAM sparkline, CPU bars, DEFROST.
 * Window moves + snap zones are handled by GameOverlayService.
 */
class RailView(context: Context) : View(context) {

    var fps = 0
        set(v) {
            field = v
            if (expanded) invalidate()
        }
    var thermal = false
        set(v) {
            field = v
            invalidate()
        }
    var onDefrost: (() -> Unit)? = null
    var onExpand: ((Boolean) -> Unit)? = null
    var onDrag: ((Float) -> Unit)? = null
    var onDragEnd: (() -> Unit)? = null

    private var sizePref = "M"
    private var opacityPref = 0.94f

    val panelWidthDp: Int
        get() = when (sizePref) {
            "S" -> 56
            "L" -> 100
            else -> 78
        }

    private val ram = FloatArray(60) { 0.5f }
    private var ramIdx = 0
    private val cpu = FloatArray(8)
    private var isDragging = false

    fun applyFit(prefs: SharedPreferences) {
        sizePref = prefs.getString("hud_size", "M") ?: "M"
        opacityPref = prefs.getFloat("hud_opacity", 0.94f)
        invalidate()
    }

    fun push(fps: Int, ramFraction: Float, cpuFractions: FloatArray) {
        this.fps = fps
        ram[ramIdx] = ramFraction
        ramIdx = (ramIdx + 1) % ram.size
        System.arraycopy(cpuFractions, 0, cpu, 0, minOf(cpuFractions.size, cpu.size))
        if (expanded) invalidate()
    }

    private var expanded = false
    private var expandT = 0f
    private val expandAnim = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 350
        interpolator = DecelerateInterpolator()
        addUpdateListener {
            expandT = it.animatedValue as Float
            invalidate()
        }
    }
    private val handler = Handler(Looper.getMainLooper())
    private val autoMinimize = Runnable { setExpanded(false) }

    fun setExpanded(on: Boolean) {
        if (expanded == on) return
        expanded = on
        expandAnim.cancel()
        expandAnim.setFloatValues(if (on) 0f else 1f, if (on) 1f else 0f)
        expandAnim.start()
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        onExpand?.invoke(on)
        if (on) handler.postDelayed(autoMinimize, 5000) else handler.removeCallbacks(autoMinimize)
    }

    fun notifyInteraction() {
        if (expanded) {
            handler.removeCallbacks(autoMinimize)
            handler.postDelayed(autoMinimize, 5000)
        }
    }

    private val d = resources.displayMetrics.density
    private fun dp(v: Float) = v * d
    private val filamentP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFD9A75A.toInt(); strokeCap = Paint.Cap.ROUND }
    private val emberP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF5402C.toInt() }
    private val panelP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (Build.VERSION.SDK_INT >= 31) 0xD0101113.toInt() else 0xCC101113.toInt()
    }
    private val strokeP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2B2F34.toInt(); style = Paint.Style.STROKE; strokeWidth = dp(1f) }
    private val customTypeface by lazy {
        try {
            ResourcesCompat.getFont(context, R.font.plexmono_semibold) ?: Typeface.MONOSPACE
        } catch (_: Throwable) {
            Typeface.MONOSPACE
        }
    }
    private val fpsP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF7FE060.toInt()
        typeface = customTypeface
        isFakeBoldText = true
    }
    private val labelP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFA29880.toInt(); typeface = customTypeface }
    private val sparkP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFCFC6AE.toInt(); style = Paint.Style.STROKE; strokeWidth = dp(1.2f) }
    private val barP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFD9A75A.toInt() }
    private val path = Path()

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        if (Build.VERSION.SDK_INT >= 29 && isDragging) {
            systemGestureExclusionRects = listOf(Rect(0, 0, width, height))
        }
    }

    override fun onDraw(c: Canvas) {
        val h = measuredHeight.toFloat()
        val panelW = dp(panelWidthDp.toFloat())
        val curW = dp(2f) + (panelW - dp(2f)) * expandT
        val pulse = if (thermal) 0.35f + 0.65f * ((System.nanoTime() / 4e8f) % 1f) else 1f

        if (expandT < 0.99f) {
            filamentP.alpha = (255 * (1f - expandT * 0.7f) * pulse).toInt().coerceIn(0, 255)
            filamentP.strokeWidth = dp(2f)
            c.drawLine(dp(7f), 0f, dp(7f), h, filamentP)
        }
        if (expandT > 0.01f) {
            val alpha = (255 * expandT * opacityPref).toInt().coerceIn(0, 255)
            panelP.alpha = alpha
            strokeP.alpha = (255 * expandT).toInt().coerceIn(0, 255)
            fpsP.alpha = (255 * expandT).toInt().coerceIn(0, 255)
            labelP.alpha = (255 * expandT).toInt().coerceIn(0, 255)
            sparkP.alpha = (255 * expandT).toInt().coerceIn(0, 255)
            barP.alpha = (255 * expandT).toInt().coerceIn(0, 255)
            val left = dp(3f)
            val top = 0f
            val right = left + curW
            val bottom = h
            c.drawRoundRect(left, top, right, bottom, dp(3f), dp(3f), panelP)
            c.drawRoundRect(left, top, right, bottom, dp(3f), dp(3f), strokeP)

            val cx = (left + right) / 2f
            fpsP.textSize = dp(when (sizePref) { "S" -> 18f; "L" -> 34f; else -> 26f })
            val fpsY = top + dp(30f)
            c.drawText("$fps", cx - fpsP.measureText("$fps") / 2f, fpsY, fpsP)
            labelP.textSize = dp(8f)
            c.drawText("FPS", cx - labelP.measureText("FPS") / 2f, fpsY + dp(11f), labelP)

            path.reset()
            val sy = fpsY + dp(22f)
            val sh = dp(20f)
            val sw = curW - dp(16f)
            for (i in 0 until ram.size) {
                val v = ram[(ramIdx + i) % ram.size]
                val x = left + dp(8f) + sw * i / (ram.size - 1f)
                val yy = sy + sh - v * sh
                if (i == 0) path.moveTo(x, yy) else path.lineTo(x, yy)
            }
            c.drawPath(path, sparkP)

            val by = sy + sh + dp(8f)
            val bh = dp(24f)
            val bw = (curW - dp(16f)) / 8f
            for (i in 0 until 8) {
                val bh2 = cpu[i] * bh
                c.drawRect(left + dp(8f) + i * bw + dp(0.5f), by + bh - bh2,
                    left + dp(8f) + (i + 1) * bw - dp(0.5f), by + bh, barP)
            }

            drawSnow(c, cx, by + bh + dp(24f), dp(9f), labelP)
        }
    }

    private fun drawSnow(c: Canvas, cx: Float, cy: Float, r: Float, p: Paint) {
        p.strokeWidth = dp(1.2f)
        repeat(6) { i ->
            val a = i / 6f * 2f * Math.PI.toFloat()
            c.drawLine(
                cx - kotlin.math.cos(a) * r, cy - kotlin.math.sin(a) * r,
                cx + kotlin.math.cos(a) * r, cy + kotlin.math.sin(a) * r, p
            )
        }
    }

    private var downY = 0f
    private var downT = 0L
    private var lastTouchY = 0f

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downY = e.rawY
                lastTouchY = e.rawY
                downT = System.currentTimeMillis()
                isDragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dyTotal = abs(e.rawY - downY)
                if (!isDragging && dyTotal > dp(8f)) {
                    isDragging = true
                }
                if (isDragging) {
                    val dy = e.rawY - lastTouchY
                    lastTouchY = e.rawY
                    onDrag?.invoke(dy)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    onDragEnd?.invoke()
                } else {
                    notifyInteraction()
                    val tap = abs(e.rawY - downY) < dp(8f) && System.currentTimeMillis() - downT < 300
                    if (tap) {
                        if (!expanded) {
                            setExpanded(true)
                        } else {
                            val defrostY = measuredHeight - dp(40f)
                            if (e.y > defrostY) {
                                performHapticFeedback(
                                    if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM
                                    else HapticFeedbackConstants.VIRTUAL_KEY
                                )
                                onDefrost?.invoke()
                            } else {
                                setExpanded(false)
                            }
                        }
                    }
                }
                return true
            }
        }
        return super.onTouchEvent(e)
    }
}
