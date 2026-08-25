package com.ivarna.apexcore.tune

data class TunePresetComponent(
    val id: TuneId,
    val requested: Boolean,
    val supported: Boolean,
    val reason: String
)

data class TunePresetReport(
    val name: String,
    val applied: Int,
    val requested: Int,
    val partial: Boolean,
    val components: List<TunePresetComponent>
)

/** Composes only verified high-value primitives; it never adds unsafe VM/I/O tweaks. */
class TunePresetManager(private val manager: TuneManager) {
    suspend fun applyMaximumPerformance(gamePackage: String): TunePresetReport {
        val ids = listOf(
            TuneId.GAME_MODE_PERFORMANCE,
            TuneId.CPU_GOVERNOR,
            TuneId.CPU_LOCK_MAX,
            TuneId.GPU_GOVERNOR,
            TuneId.GPU_LOCK_MAX
        )
        val components = ids.map { id ->
            val capability = manager.capabilities.value[id]
            val supported = when (id) {
                TuneId.GAME_MODE_PERFORMANCE -> manager.gameModeCapability(gamePackage)?.supportsPerformance == true
                TuneId.CPU_GOVERNOR, TuneId.GPU_GOVERNOR ->
                    capability?.available == true && "performance" in capability.availableOptions
                else -> capability?.available == true
            }
            val raw = if (id == TuneId.CPU_GOVERNOR || id == TuneId.GPU_GOVERNOR) "performance" else null
            if (supported) manager.setIntent(id, TuneValue(true, raw))
            TunePresetComponent(
                id = id,
                requested = true,
                supported = supported,
                reason = if (supported) "requested" else when (id) {
                    TuneId.GAME_MODE_PERFORMANCE -> manager.gameModeCapability(gamePackage)?.reason?.name ?: "not probed"
                    else -> capability?.subtitle ?: "not probed"
                }
            )
        }
        val report = manager.applyForSession(gamePackage)
        val requested = components.count { it.supported }
        val applied = report.applied.coerceAtMost(requested)
        return TunePresetReport(
            name = "Maximum Performance",
            applied = applied,
            requested = requested,
            partial = applied < ids.size,
            components = components.mapIndexed { index, component ->
                if (index < applied) component.copy(reason = "verified") else component
            }
        )
    }
}
