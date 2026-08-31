package com.ivarna.apexcore.ui.iron.tune

import com.ivarna.apexcore.tune.GameModeCapability
import com.ivarna.apexcore.tune.TuneCapability
import com.ivarna.apexcore.tune.TuneCategory
import com.ivarna.apexcore.tune.TuneControlKind
import com.ivarna.apexcore.tune.TuneId
import com.ivarna.apexcore.tune.TuneSpecs
import com.ivarna.apexcore.tune.TuneValue

/**
 * Builds Tuning Room category rows from capability probe results.
 * Always returns a full option list from [TuneSpecs.all] so probe failure
 * never produces an unexplained blank screen.
 */
fun buildTuneCategories(
    caps: Map<TuneId, TuneCapability>,
    selectedPkg: String?,
    sessionPkg: String?,
    probeFailure: String? = null,
    gameModeCapability: ((String) -> GameModeCapability?)? = null,
    intentOf: (TuneId) -> TuneValue,
    onToggle: (TuneId, Boolean, String?) -> Unit,
    onEnumSelect: (TuneId, String) -> Unit,
    onSliderChange: (TuneId, Int) -> Unit,
): List<TuneCategoryUi> {
    val probeReason = probeFailure?.takeIf { it.isNotBlank() }
        ?: if (probeFailure != null) "Capability probe failed" else null

    return TuneCategory.entries.map { cat ->
        val catSpecs = TuneSpecs.all.filter { it.category == cat }
        val options = catSpecs.map { spec ->
            val id = spec.id
            val (isAvail, reason) = if (id == TuneId.GAME_MODE_PERFORMANCE) {
                val pkg = selectedPkg?.takeIf { it.isNotBlank() }
                    ?: sessionPkg?.takeIf { it.isNotBlank() }
                val gmCap = pkg?.let { gameModeCapability?.invoke(it) }
                val avail = gmCap?.supportsPerformance == true
                val r = when {
                    probeReason != null && !avail -> probeReason
                    pkg.isNullOrBlank() -> "Select a game"
                    gmCap == null -> "Game Mode unavailable"
                    !avail -> gmCap.reason.name.ifBlank { "Performance not exposed by this game" }
                    else -> null
                }
                avail to r
            } else {
                val cap = caps[id]
                val avail = cap?.available == true
                val r = when {
                    avail -> null
                    probeReason != null -> probeReason
                    else -> cap?.subtitle ?: "Not available on kernel"
                }
                avail to r
            }
            val intent = intentOf(id)
            val isChecked = intent.on
            val cap = caps[id]
            val enumOpts = when (id) {
                TuneId.CPU_GOVERNOR, TuneId.GPU_GOVERNOR,
                TuneId.IO_SCHEDULER, TuneId.NET_TCP -> cap?.availableOptions ?: emptyList()
                else -> emptyList()
            }
            val selectedEnum = intent.raw ?: when (id) {
                TuneId.CPU_GOVERNOR, TuneId.GPU_GOVERNOR ->
                    enumOpts.firstOrNull { it == "performance" } ?: enumOpts.firstOrNull()
                else -> enumOpts.firstOrNull()
            }
            TuneOptionUi(
                key = id.name,
                title = spec.title,
                description = spec.description,
                available = isAvail,
                reason = if (!isAvail) reason else null,
                kind = spec.kind,
                checked = isChecked,
                onToggle = { checked ->
                    val raw = if (spec.kind == TuneControlKind.ENUM) {
                        if (checked) selectedEnum else intent.raw
                    } else intent.raw
                    onToggle(id, checked, raw)
                },
                enumOptions = enumOpts,
                selectedEnum = selectedEnum,
                onEnumSelect = { token -> onEnumSelect(id, token) },
                sliderRange = spec.slider,
                sliderValue = intent.raw?.toIntOrNull()
                    ?: spec.defaultVal?.toIntOrNull()
                    ?: spec.slider?.first,
                onSliderChange = { v -> onSliderChange(id, v) },
            )
        }
        TuneCategoryUi(name = cat.name, options = options)
    }
}
