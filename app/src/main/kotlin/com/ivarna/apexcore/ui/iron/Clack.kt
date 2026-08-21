package com.ivarna.apexcore.ui.iron

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.delay

class HapticGate(private val clock: () -> Long = { SystemClock.uptimeMillis() }) {
    private var last = -80L
    fun allow(): Boolean {
        val n = clock()
        if (n - last < 80) return false
        last = n
        return true
    }
}

class Clack(private val view: View) {
    private val gate = HapticGate()

    private val vibrator: Vibrator? by lazy {
        val ctx = view.context
        if (Build.VERSION.SDK_INT >= 31) {
            val vm = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun tick() {
        if (gate.allow()) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun keyTap() {
        if (gate.allow()) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    fun row() {
        if (gate.allow()) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    fun longPress() {
        if (gate.allow()) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    fun confirm() {
        if (Build.VERSION.SDK_INT >= 30) {
            if (gate.allow()) view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            row()
        }
    }

    fun off() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (gate.allow()) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        } else {
            row()
        }
    }

    fun thud() {
        if (gate.allow()) {
            if (Build.VERSION.SDK_INT >= 29) {
                try {
                    vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
                } catch (_: Throwable) {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                }
            } else {
                longPress()
            }
        }
    }

    fun no() {
        if (Build.VERSION.SDK_INT >= 30) {
            if (gate.allow()) view.performHapticFeedback(HapticFeedbackConstants.REJECT)
        } else {
            longPress()
        }
    }

    suspend fun purgeDone() {
        thud()
        delay(90)
        row()
    }
}

@Composable
fun rememberClack(): Clack {
    val view = LocalView.current
    return remember(view) { Clack(view) }
}
