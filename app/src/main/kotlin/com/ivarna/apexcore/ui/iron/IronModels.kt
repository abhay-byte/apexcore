package com.ivarna.apexcore.ui.iron

import androidx.compose.runtime.Composable

enum class BackendChoice { SHIZUKU, ROOT }

data class KeyStatus(
    val ready: Boolean = false,
    val checking: Boolean = true,
    val statusLine: String = "CHECKING…",
)

data class WorkOrderData(
    val freedGb: Float,
    val freedRamGb: Float,
    val freedSwapGb: Float,
    val apps: Int,
    val durationS: Float,
    val skipped: Int,
    val failed: Int,
)

enum class BenchPhase { IDLE, BOOSTING, RESULT }

data class PickerApp(
    val name: String,
    val pkg: String,
    val icon: @Composable () -> Unit,
)
