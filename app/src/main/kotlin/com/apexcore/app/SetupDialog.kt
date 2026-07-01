package com.apexcore.app

import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.apexcore.app.freeze.FreezeBackendResolver
import com.apexcore.app.freeze.FreezeFramework
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SetupDialog(
    private val activity: MainActivity,
    private val resolver: FreezeBackendResolver
) {

    private val dpf: (Float) -> Float = { v ->
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, activity.resources.displayMetrics)
    }

    fun show() {
        val d = Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        d.window?.setBackgroundDrawable(GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.parseColor("#CC070A12"))
        })
        d.setContentView(build(d))
        d.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )
        d.setCancelable(true)
        d.setOnDismissListener { prefs().edit().putBoolean(KEY_SHOWN, true).apply() }
        d.show()
    }

    private fun prefs() = activity.getSharedPreferences("apexcore", Context.MODE_PRIVATE)

    private fun build(d: Dialog): View {
        val root = FrameLayout(activity).apply {
            setBackgroundColor(Color.parseColor("#CC070A12"))
        }

        val dim = View(activity).apply {
            setBackgroundColor(Color.parseColor("#CC070A12"))
            isClickable = true
            setOnClickListener { d.dismiss() }
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(72, 72, 72, 72)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpf(20f)
                setColor(Color.parseColor("#0F1623"))
                setStroke(3, Color.parseColor("#1F2937"))
            }
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = dpf(48f).toInt()
                rightMargin = dpf(48f).toInt()
                gravity = Gravity.TOP
                topMargin = dpf(96f).toInt()
                bottomMargin = dpf(96f).toInt()
            }
        }

        card.addView(headerText("SETUP REQUIRED"))
        card.addView(spacer(24))
        card.addView(bodyText("Real deep-freeze needs elevated access. Pick a mode:"))
        card.addView(spacer(36))

        card.addView(optionRow("Shizuku (recommended)",
            "Works without root via wireless ADB. 1-time grant.",
            "OPEN SHIZUKU",
            onClick = {
                openShizuku()
                d.dismiss()
            }
        ))
        card.addView(spacer(24))

        card.addView(optionRow("Root",
            "Uses su shell. Tap to re-verify root is granted.",
            "VERIFY ROOT",
            onClick = {
                card.post {
                    CoroutineScope(Dispatchers.Main).launch {
                        resolver.invalidate()
                        val backend = FreezeFramework.detect()
                        if (backend.priority < 99) {
                            d.dismiss()
                        } else {
                            showRetryToast(backend.name)
                        }
                    }
                }
            }
        ))
        card.addView(spacer(24))

        card.addView(optionRow("Accessibility",
            "Slowest. No setup if device is already rooted.",
            "OPEN SETTINGS",
            onClick = {
                openAccessibilitySettings()
                d.dismiss()
            }
        ))
        card.addView(spacer(36))

        card.addView(footerButton("USE CACHED-ONLY MODE", onClick = { d.dismiss() }))

        val scroll = ScrollView(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            isFillViewport = true
        }
        scroll.addView(card)
        root.addView(dim, 0)
        root.addView(scroll, 1)
        return root
    }

    private fun headerText(s: String): TextView = TextView(activity).apply {
        text = s
        setTextColor(Color.parseColor("#6B7280"))
        textSize = 10f
        typeface = Typeface.MONOSPACE
        letterSpacing = 0.2f
        gravity = Gravity.START
    }

    private fun bodyText(s: String): TextView = TextView(activity).apply {
        text = s
        setTextColor(Color.parseColor("#FFFFFF"))
        textSize = 13f
        gravity = Gravity.START
    }

    private fun spacer(dp: Int): View = View(activity).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpf(dp.toFloat()).toInt())
    }

    private fun optionRow(title: String, sub: String, cta: String, onClick: () -> Unit): View {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpf(36f).toInt(), dpf(36f).toInt(), dpf(36f).toInt(), dpf(36f).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpf(14f)
                setColor(Color.parseColor("#070A12"))
                setStroke(2, Color.parseColor("#1F2937"))
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
        row.addView(TextView(activity).apply {
            text = title
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        })
        row.addView(spacer(12))
        row.addView(TextView(activity).apply {
            text = sub
            setTextColor(Color.parseColor("#9CA3AF"))
            textSize = 12f
        })
        row.addView(spacer(18))
        row.addView(footerButton(cta, onClick = onClick))
        return row
    }

    private fun footerButton(label: String, onClick: () -> Unit): TextView = TextView(activity).apply {
        text = label
        setTextColor(Color.parseColor("#070A12"))
        textSize = 13f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        letterSpacing = 0.15f
        gravity = Gravity.CENTER
        setPadding(0, dpf(28f).toInt(), 0, dpf(28f).toInt())
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpf(12f)
            colors = intArrayOf(
                Color.parseColor("#00E5FF"),
                Color.parseColor("#0EA5E9")
            )
            orientation = GradientDrawable.Orientation.TL_BR
        }
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    private fun openShizuku() {
        val candidates = listOf(
            "moe.shizuku.manager",
            "moe.shizuku.api"
        )
        for (pkg in candidates) {
            val intent = activity.packageManager.getLeanbackLaunchIntentForPackage(pkg)
                ?: activity.packageManager.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                try {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    activity.startActivity(intent)
                    return
                } catch (_: ActivityNotFoundException) { /* try next */ }
            }
        }
        val play = Intent(Intent.ACTION_VIEW).apply {
            data = android.net.Uri.parse("https://play.google.com/store/apps/details?id=moe.shizuku.manager")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try { activity.startActivity(play) } catch (_: Throwable) {}
    }

    private fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
        } catch (_: Throwable) {}
    }

    private fun showRetryToast(backendName: String) {
        android.widget.Toast.makeText(
            activity,
            "Still on $backendName — grant access then tap again",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    companion object {
        const val KEY_SHOWN = "setup_shown_v1"
        const val PREFS = "apexcore"
    }
}
