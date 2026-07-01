package com.apexcore.app.games

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.apexcore.app.freeze.FreezeFramework
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GameListDialog(
    private val activity: android.app.Activity,
    private val gameManager: GameManager
) {
    private val pm: PackageManager = activity.packageManager
    private var games: MutableList<GameInfo> = gameManager.load().toMutableList()
    private var detected: List<GameInfo> = emptyList()
    private var gameListContainer: LinearLayout? = null
    private var detectRow: LinearLayout? = null

    private val dpf: (Float) -> Float = { v ->
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, activity.resources.displayMetrics)
    }

    fun show() {
        val d = Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        d.window?.setBackgroundDrawable(GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE; setColor(Color.parseColor("#CC070A12"))
        })
        d.setContentView(build(d))
        d.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        d.setCancelable(true)
        d.show()
        refreshList()
    }

    private fun build(d: Dialog): View {
        val root = FrameLayout(activity).apply { setBackgroundColor(Color.parseColor("#CC070A12")) }
        root.addView(View(activity).apply {
            setBackgroundColor(Color.parseColor("#CC070A12")); isClickable = true
            setOnClickListener { d.dismiss() }
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }, 0)

        val c = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL; setPadding(72, 72, 72, 72)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dpf(20f)
                setColor(Color.parseColor("#0F1623")); setStroke(3, Color.parseColor("#1F2937"))
            }
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = dpf(24f).toInt(); rightMargin = dpf(24f).toInt()
                gravity = Gravity.TOP; topMargin = dpf(48f).toInt(); bottomMargin = dpf(48f).toInt()
            }
        }

        c.apply {
            addView(header("GAME LAUNCHER"))
            addView(spacer(12))
            addView(body("Auto-detect or manually add games. Tap to freeze + launch."))
            addView(spacer(24))

            val listContainer = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
            }
            gameListContainer = listContainer
            addView(listContainer)

            addView(spacer(24))
            val addRow = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            }
            val input = EditText(activity).apply {
                hint = "Package name (e.g. com.example)"
                setTextColor(Color.parseColor("#FFFFFF"))
                setHintTextColor(Color.parseColor("#6B7280"))
                textSize = 12f; typeface = Typeface.MONOSPACE
                setPadding(dpf(24f).toInt(), dpf(16f).toInt(), dpf(24f).toInt(), dpf(16f).toInt())
                setBackgroundResource(android.R.color.transparent)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    rightMargin = dpf(12f).toInt()
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE; cornerRadius = dpf(10f)
                    setColor(Color.parseColor("#070A12")); setStroke(2, Color.parseColor("#1F2937"))
                }
            }
            val addBtn = smallBtn("ADD") {
                val pkg = input.text.toString().trim()
                if (pkg.isNotEmpty()) {
                    val name = resolveLabel(pkg)
                    if (name != null) {
                        gameManager.add(pkg, name, autoDetected = false)
                        input.text.clear()
                        refreshList()
                    } else {
                        Toast.makeText(activity, "Package $pkg not found", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            addRow.addView(input)
            addRow.addView(addBtn)
            addView(addRow)

            addView(spacer(12))
            val dr = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
                setPadding(dpf(24f).toInt(), dpf(18f).toInt(), dpf(24f).toInt(), dpf(18f).toInt())
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE; cornerRadius = dpf(12f)
                    setColor(Color.parseColor("#070A12")); setStroke(2, Color.parseColor("#1F2937"))
                }
                isClickable = true; isFocusable = true
                setOnClickListener { runDetect() }
                addView(TextView(activity).apply {
                    text = "🔍  SCAN FOR GAMES"; setTextColor(Color.parseColor("#00E5FF")); textSize = 13f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); gravity = Gravity.CENTER
                })
            }
            detectRow = dr
            addView(dr)

            addView(spacer(36))
            addView(footerBtn("CLOSE") { d.dismiss() })
        }

        val scroll = ScrollView(activity).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        scroll.addView(c)
        root.addView(scroll)
        return root
    }

    private fun refreshList() {
        games = gameManager.load().toMutableList()
        val container = gameListContainer ?: return
        container.removeAllViews()

        if (games.isEmpty()) {
            container.addView(TextView(activity).apply {
                text = "No games yet. Scan or add manually."; setTextColor(Color.parseColor("#6B7280"))
                textSize = 12f; gravity = Gravity.CENTER; setPadding(0, dpf(24f).toInt(), 0, dpf(24f).toInt())
            })
        } else {
            for (game in games) {
                val tag = if (game.isAutoDetected) "auto" else "manual"
                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    setPadding(dpf(20f).toInt(), dpf(16f).toInt(), dpf(20f).toInt(), dpf(16f).toInt())
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE; cornerRadius = dpf(10f)
                        setColor(Color.parseColor("#070A12")); setStroke(1, Color.parseColor("#1F2937"))
                    }
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        bottomMargin = dpf(8f).toInt()
                    }
                    isClickable = true; isFocusable = true
                    setOnClickListener { launchGame(game) }
                    setOnLongClickListener {
                        val prevCount = gameManager.load().size
                        gameManager.remove(game.pkg)
                        refreshList()
                        Toast.makeText(activity, "Removed ${game.name}", Toast.LENGTH_SHORT).show()
                        true
                    }
                }
                row.addView(TextView(activity).apply {
                    text = game.name; setTextColor(Color.parseColor("#FFFFFF")); textSize = 14f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    maxLines = 1
                })
                row.addView(TextView(activity).apply {
                    text = tag; setTextColor(Color.parseColor("#6B7280")); textSize = 9f
                    typeface = Typeface.MONOSPACE; setPadding(dpf(8f).toInt(), dpf(4f).toInt(), dpf(8f).toInt(), dpf(4f).toInt())
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE; cornerRadius = dpf(6f)
                        setColor(Color.parseColor("#1F2937"))
                    }
                })
                row.addView(TextView(activity).apply {
                    text = "▶"; setTextColor(Color.parseColor("#00E5FF")); textSize = 16f
                    setPadding(dpf(16f).toInt(), 0, 0, 0)
                })
                container.addView(row)
            }
        }
    }

    private fun launchGame(game: GameInfo) {
        Toast.makeText(activity, "Freezing + launching ${game.name}…", Toast.LENGTH_SHORT).show()
        CoroutineScope(Dispatchers.Main).launch {
            val result = GameLauncher.launch(activity, game.pkg)
            if (!result.success) {
                Toast.makeText(activity, "Failed: ${result.error}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun runDetect() {
        val dr = detectRow ?: return
        dr.getChildAt(0)?.let { (it as? TextView)?.text = "⏳  SCANNING…" }
        dr.isClickable = false
        CoroutineScope(Dispatchers.Main).launch {
            detected = gameManager.detect(activity)
            if (detected.isEmpty()) {
                Toast.makeText(activity, "No new games found", Toast.LENGTH_SHORT).show()
            } else {
                gameManager.acceptDetected(activity)
                Toast.makeText(activity, "Added ${detected.size} game(s)", Toast.LENGTH_SHORT).show()
            }
            refreshList()
            dr.getChildAt(0)?.let { (it as? TextView)?.text = "🔍  SCAN FOR GAMES" }
            dr.isClickable = true
        }
    }

    private fun resolveLabel(pkg: String): String? = try {
        val ai = pm.getApplicationInfo(pkg, 0)
        pm.getApplicationLabel(ai)?.toString() ?: pkg
    } catch (_: PackageManager.NameNotFoundException) { null }

    // ── UI helpers ──

    private fun header(s: String) = TextView(activity).apply {
        text = s; setTextColor(Color.parseColor("#6B7280")); textSize = 10f
        typeface = Typeface.MONOSPACE; letterSpacing = 0.2f
    }

    private fun body(s: String) = TextView(activity).apply {
        text = s; setTextColor(Color.parseColor("#FFFFFF")); textSize = 13f
    }

    private fun spacer(dp: Int) = View(activity).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpf(dp.toFloat()).toInt())
    }

    private fun smallBtn(label: String, onClick: () -> Unit) = TextView(activity).apply {
        text = label; setTextColor(Color.parseColor("#070A12")); textSize = 12f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setPadding(dpf(20f).toInt(), dpf(14f).toInt(), dpf(20f).toInt(), dpf(14f).toInt())
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE; cornerRadius = dpf(10f)
            colors = intArrayOf(Color.parseColor("#00E5FF"), Color.parseColor("#0EA5E9"))
            orientation = GradientDrawable.Orientation.TL_BR
        }
        isClickable = true; isFocusable = true
        setOnClickListener { onClick() }
    }

    private fun footerBtn(label: String, onClick: () -> Unit) = TextView(activity).apply {
        text = label; setTextColor(Color.parseColor("#070A12")); textSize = 13f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); letterSpacing = 0.15f; gravity = Gravity.CENTER
        setPadding(0, dpf(28f).toInt(), 0, dpf(28f).toInt())
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE; cornerRadius = dpf(12f)
            colors = intArrayOf(Color.parseColor("#00E5FF"), Color.parseColor("#0EA5E9"))
            orientation = GradientDrawable.Orientation.TL_BR
        }
        isClickable = true; isFocusable = true
        setOnClickListener { onClick() }
    }
}
