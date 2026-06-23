package com.apexcore.app

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0F1419"))
            gravity = Gravity.CENTER
        }
        val title = TextView(this).apply {
            text = "ApexCore"
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 36f
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = "Game performance companion"
            setTextColor(Color.parseColor("#9CA3AF"))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 0)
        }
        val status = TextView(this).apply {
            text = "v0.1.0 · dummy build OK"
            setTextColor(Color.parseColor("#10B981"))
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, 48, 0, 0)
        }
        layout.addView(title)
        layout.addView(subtitle)
        layout.addView(status)
        setContentView(layout)
    }
}
