package com.ivarna.apexcore.tune

import android.content.Context
import android.util.Log
import com.ivarna.apexcore.fps.FpsStack
import com.ivarna.apexcore.fps.privilege.PrivilegeTier
import com.ivarna.apexcore.fps.privilege.ShellGateway
import com.ivarna.apexcore.freeze.FreezeFramework
import com.ivarna.apexcore.games.GameOverlayService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Central facade for Game Optimisation kernel and session tuning.
 * Manages intents, capability gating, session lifecycle, recovery, and mutex-protected writes.
 */
class TuneManager internal constructor(
    private val appContext: Context,
    val shellGateway: ShellGateway,
    val prefs: TunePrefs,
    val snapshotStore: TuneSnapshotStore,
    val probe: TuneProbe,
    val applier: TuneApplier,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    private val backendResolver: TuneBackendResolver? = null
) {
    val capabilities: StateFlow<Map<TuneId, TuneCapability>> = probe.capabilities

    private val _sessionActive = MutableStateFlow(false)
    val sessionActive: StateFlow<Boolean> = _sessionActive.asStateFlow()

    @Volatile
    private var _owner: TuneSessionOwner = TuneSessionOwner.NONE
    val owner: TuneSessionOwner get() = _owner

    private val mutex = Mutex()
    private var recoverJob: Job? = null
    private val thermalGuard = TuneThermalGuard(appContext, scope) {
        scope.launch {
            mutex.withLock {
                val tier = writeTier() ?: PrivilegeTier.STANDARD
                val released = applier.releaseMaxLocks(tier)
                Log.w(TAG, "Severe thermal status: released $released CPU/GPU max-lock values")
            }
        }
    }

    init {
        _owner = prefs.getOwner()
        startRecovery()
        observeBackendChanges()
    }

    private fun startRecovery() {
        recoverJob = scope.launch {
            recoverSession()
        }
    }

    private fun observeBackendChanges() {
        scope.launch {
            // React only to an actual drop from a previously elevated backend.
            // The initial emission (null on cold start) must not trigger a spurious
            // restore — recovery owns the cold-start path (boot-match + orphan check).
            var previousName: String? = null
            FreezeFramework.activeBackend.collect { backend ->
                val name = backend?.name
                val droppedFromElevated = previousName == "Root" || previousName == "Shizuku"
                previousName = name
                if (droppedFromElevated && name != "Root" && name != "Shizuku") {
                    if (_sessionActive.value || prefs.isApplied()) {
                        Log.w(TAG, "Backend dropped to non-elevated ($name) while session active; restoring session")
                        restoreSession()
                    }
                }
            }
        }
    }

    fun refreshCapabilities() {
        probe.refreshCapabilities()
    }

    suspend fun refreshCapabilitiesSync(): Map<TuneId, TuneCapability> {
        return probe.probeSync()
    }

    /** Per-game capability because Android Game Mode is package-specific. */
    fun gameModeCapability(gamePackage: String): GameModeCapability? {
        val tier = writeTier() ?: return null
        return applier.gameModeCapability(gamePackage, tier)
    }

    fun intent(id: TuneId): TuneValue = prefs.getIntent(id)

    /**
     * Non-suspend callback to set intent.
     * Validates capability when turning ON.
     * Persists immediately and returns; if session is active, launches async IO apply/restore.
     */
    fun setIntent(id: TuneId, value: TuneValue): Boolean {
        if (value.on) {
            val cap = capabilities.value[id]
            if (id != TuneId.GAME_MODE_PERFORMANCE && (cap == null || !cap.available)) {
                Log.w(TAG, "Cannot turn on $id: capability not available")
                return false
            }
        }

        prefs.setIntent(id, value)

        if (_sessionActive.value) {
            scope.launch {
                recoverJob?.join()
                mutex.withLock {
                    val tier = writeTier()
                    if (tier != null) {
                        if (value.on) {
                            if (id.isMaxLock() && thermalGuard.severe) {
                                Log.w(TAG, "Ignoring $id while thermal guard is severe")
                            } else if (id == TuneId.GAME_MODE_PERFORMANCE && prefs.getSessionPkg().isNotBlank()) {
                                applier.applyGameModeForSession(prefs.getSessionPkg(), tier, backendFor(tier))
                            } else {
                                val count = applier.applyBundle(id, value, tier)
                                if (id.isMaxLock() && count > 0) {
                                    thermalGuard.start()
                                }
                            }
                        } else {
                            val restored = applier.restoreBundle(id, tier)
                            if (id.isMaxLock()) {
                                val anyMaxOn = prefs.getIntent(TuneId.CPU_LOCK_MAX).on || prefs.getIntent(TuneId.GPU_LOCK_MAX).on
                                val hasOwner = snapshotStore.getAllEntries().values.any {
                                    TuneId.CPU_LOCK_MAX in it.owners || TuneId.GPU_LOCK_MAX in it.owners
                                }
                                if (!anyMaxOn && !hasOwner) {
                                    thermalGuard.stop()
                                }
                            }
                            // If all bundles are turned off, end session
                            val anyOn = TuneSpecs.all.any { spec ->
                                prefs.getIntent(spec.id).on &&
                                    (spec.id == TuneId.GAME_MODE_PERFORMANCE || capabilities.value[spec.id]?.available == true)
                            }
                            if (!anyOn) {
                                restoreSessionLocked(tier)
                            }
                        }
                    }
                }
            }
        }
        return true
    }

    fun setOwner(newOwner: TuneSessionOwner) {
        _owner = newOwner
        prefs.setOwner(newOwner)
    }

    fun isRealGamePkg(pkg: String?): Boolean {
        val p = pkg?.takeIf { it.isNotBlank() } ?: return false
        return p != appContext.packageName
    }

    fun writeTier(): PrivilegeTier? {
        backendResolver?.let {
            val identity = it.refresh()
            return identity.asPrivilegeTier().takeIf { tier -> tier != PrivilegeTier.STANDARD }
        }
        return when (FreezeFramework.activeBackend.value?.name) {
            "Root" -> PrivilegeTier.SU_ROOT
            "Shizuku" -> PrivilegeTier.SHIZUKU_SHELL
            else -> null
        }
    }

    suspend fun applyForSession(gamePkg: String, filterIds: Set<TuneId>? = null): TuneApplyReport {
        if (!isRealGamePkg(gamePkg)) {
            Log.i(TAG, "applyForSession skipped: self or blank pkg ($gamePkg)")
            return TuneApplyReport(0, 0, 0, _sessionActive.value)
        }

        recoverJob?.join()
        return mutex.withLock {
            val tier = writeTier() ?: run {
                Log.w(TAG, "applyForSession: no elevated tier available")
                return@withLock TuneApplyReport(0, 0, 0, _sessionActive.value)
            }

            // If capabilities are not probed yet, probe sync before applying
            if (capabilities.value.values.none { it.available }) {
                try {
                    probe.probeSync()
                } catch (t: Throwable) {
                    Log.w(TAG, "Probe during applyForSession failed: ${t.message}")
                }
            }

            snapshotStore.recordBootIdentity()

            var applied = 0
            var failed = 0
            var skipped = 0
            val components = mutableMapOf<TuneId, Boolean>()

            val onIntentsCount = TuneSpecs.all.count { spec ->
                (filterIds == null || spec.id in filterIds) &&
                    prefs.getIntent(spec.id).on && (spec.id == TuneId.GAME_MODE_PERFORMANCE || capabilities.value[spec.id]?.available == true)
            }
            val budgetMs = if (onIntentsCount > 4) 2500L else 1500L
            val cpuFloorOn = prefs.getIntent(TuneId.CPU_FLOOR).on

            withTimeoutOrNull(budgetMs) {
                for (spec in TuneSpecs.all) {
                    val id = spec.id
                    if (filterIds != null && id !in filterIds) continue
                    val intent = prefs.getIntent(id)
                    val cap = capabilities.value[id]

                    if (!intent.on || (id != TuneId.GAME_MODE_PERFORMANCE && cap?.available != true)) {
                        skipped++
                        components[id] = false
                        continue
                    }

                    // Mutex: if CPU_FLOOR is on, ignore split cluster intents
                    if (cpuFloorOn && (id == TuneId.CPU_FLOOR_LITTLE || id == TuneId.CPU_FLOOR_BIG || id == TuneId.CPU_FLOOR_PRIME)) {
                        skipped++
                        components[id] = false
                        continue
                    }

                    if (id == TuneId.GAME_MODE_PERFORMANCE) {
                        val result = applier.applyGameModeForSession(gamePkg, tier, backendResolver?.current() ?: backendFor(tier))
                        if (result.verified) {
                            applied++
                            components[id] = true
                        } else {
                            failed++
                            components[id] = false
                        }
                    } else if (id.isMaxLock() && thermalGuard.severe) {
                        skipped++
                        components[id] = false
                    } else {
                        val count = applier.applyBundle(id, intent, tier)
                        if (count > 0) {
                            applied += count
                            components[id] = true
                        } else {
                            failed++
                            components[id] = false
                        }
                    }
                }
            } ?: run {
                Log.w(TAG, "applyForSession hit timeout budget (${budgetMs}ms); skipped remaining options")
            }

            if (applied > 0) {
                _sessionActive.value = true
                prefs.setApplied(true)
                prefs.setSessionPkg(gamePkg)
                val maxLockVerified = components[com.ivarna.apexcore.tune.TuneId.CPU_LOCK_MAX] == true ||
                    components[com.ivarna.apexcore.tune.TuneId.GPU_LOCK_MAX] == true
                if (maxLockVerified) {
                    thermalGuard.start()
                }
            }

            Log.i(TAG, "applyForSession for $gamePkg: applied=$applied failed=$failed skipped=$skipped (budget=${budgetMs}ms) components=$components")
            TuneApplyReport(applied, failed, skipped, _sessionActive.value, components = components)
        }
    }

    suspend fun applyBundle(id: TuneId): TuneApplyReport {
        recoverJob?.join()
        return mutex.withLock {
            val tier = writeTier() ?: return@withLock TuneApplyReport(0, 0, 0, _sessionActive.value)
            val intent = prefs.getIntent(id)
            val cap = capabilities.value[id]
            if (!intent.on || (id != TuneId.GAME_MODE_PERFORMANCE && cap?.available != true)) {
                return@withLock TuneApplyReport(0, 0, 1, _sessionActive.value)
            }
            if (id.isMaxLock() && thermalGuard.severe) {
                return@withLock TuneApplyReport(0, 0, 1, _sessionActive.value,
                    details = mapOf("reason" to CapabilityReason.THERMAL_SAFETY_BLOCKED.name))
            }
            val count = if (id == TuneId.GAME_MODE_PERFORMANCE) {
                if (prefs.getSessionPkg().isBlank()) 0 else
                    if (applier.applyGameModeForSession(prefs.getSessionPkg(), tier, backendResolver?.current() ?: backendFor(tier)).verified) 1 else 0
            } else applier.applyBundle(id, intent, tier)
            if (count > 0) {
                _sessionActive.value = true
                prefs.setApplied(true)
            }
            TuneApplyReport(count, if (count == 0) 1 else 0, 0, _sessionActive.value)
        }
    }

    suspend fun restoreBundle(id: TuneId): TuneApplyReport {
        recoverJob?.join()
        return mutex.withLock {
            val tier = writeTier() ?: PrivilegeTier.STANDARD
            val count = applier.restoreBundle(id, tier)
            TuneApplyReport(count, 0, 0, _sessionActive.value)
        }
    }

    private fun restoreSessionLocked(tier: PrivilegeTier): TuneApplyReport {
        val count = applier.restoreAll(tier)
        val remaining = snapshotStore.getAllOriginals()
        return if (remaining.isEmpty()) {
            snapshotStore.clear()
            _sessionActive.value = false
            prefs.setApplied(false)
            setOwner(TuneSessionOwner.NONE)
            thermalGuard.stop()
            Log.i(TAG, "restoreSession completed: $count paths restored, all clean")
            TuneApplyReport(count, 0, 0, false)
        } else {
            Log.w(TAG, "restoreSession: ${remaining.size} paths failed to restore; keeping snapshot and tune_applied=true")
            _sessionActive.value = false
            prefs.setApplied(true)
            TuneApplyReport(count, remaining.size, 0, false)
        }
    }

    suspend fun restoreSession(): TuneApplyReport {
        recoverJob?.join()
        return mutex.withLock {
            val tier = writeTier() ?: PrivilegeTier.STANDARD
            restoreSessionLocked(tier)
        }
    }

    suspend fun recoverSession() = withContext(Dispatchers.IO) {
        FreezeFramework.init(appContext)
        if (FreezeFramework.activeBackend.value == null) {
            try {
                FreezeFramework.detect()
            } catch (t: Throwable) {
                Log.w(TAG, "FreezeFramework.detect failed during recovery: ${t.message}")
            }
        }

        if (!prefs.isApplied()) {
            return@withContext
        }

        if (!snapshotStore.isBootMatching()) {
            Log.w(TAG, "Boot mismatch detected on recovery: discarding stale snapshot")
            snapshotStore.clear()
            prefs.setApplied(false)
            setOwner(TuneSessionOwner.NONE)
            thermalGuard.stop()
            return@withContext
        }

        if (_sessionActive.value) {
            return@withContext
        }

        val overlayRunning = GameOverlayService.isRunning
        if (overlayRunning) {
            Log.i(TAG, "Overlay is running on recovery: rehydrating sessionActive=true, owner=OVERLAY")
            _sessionActive.value = true
            setOwner(TuneSessionOwner.OVERLAY)
            return@withContext
        }

        // Orphan predicate: applied && bootMatch && !inMemory && !isRunning
        Log.w(TAG, "Orphan session detected: restoring sysfs snapshot")
        val tier = writeTier() ?: PrivilegeTier.STANDARD
        val count = applier.restoreAll(tier)
        val remaining = snapshotStore.getAllOriginals()
        if (remaining.isEmpty()) {
            snapshotStore.clear()
            _sessionActive.value = false
            prefs.setApplied(false)
            setOwner(TuneSessionOwner.NONE)
            thermalGuard.stop()
            Log.i(TAG, "Orphan restore completed: $count paths restored, all clean")
        } else {
            Log.w(TAG, "Orphan restore: ${remaining.size} paths failed to restore, keeping snapshot and tune_applied=true")
            _sessionActive.value = false
            prefs.setApplied(true)
        }
    }

    fun deleteDummyKeysIfNeeded() {
        prefs.deleteDummyKeysIfNeeded()
    }

    private fun backendFor(tier: PrivilegeTier): TuneBackendIdentity = when (tier) {
        PrivilegeTier.SU_ROOT -> TuneBackendIdentity.SU_ROOT
        PrivilegeTier.SHIZUKU_ROOT -> TuneBackendIdentity.SHIZUKU_ROOT
        PrivilegeTier.SHIZUKU_SHELL -> TuneBackendIdentity.SHIZUKU_SHELL
        PrivilegeTier.STANDARD -> TuneBackendIdentity.STANDARD
    }

    private fun TuneId.isMaxLock(): Boolean = this == TuneId.CPU_LOCK_MAX || this == TuneId.GPU_LOCK_MAX

    companion object {
        private const val TAG = "ApexCore.Tune"

        @Volatile
        private var instance: TuneManager? = null

        fun get(context: Context): TuneManager {
            instance?.let { return it }
            return synchronized(this) {
                instance ?: create(context.applicationContext).also { instance = it }
            }
        }

        fun setInstanceForTest(inst: TuneManager?) {
            instance = inst
        }

        private fun create(appCtx: Context): TuneManager {
            val fpsStack = FpsStack.get(appCtx)
            val shellGateway = fpsStack.shellGateway
            val prefs = TunePrefs(appCtx)
            val backendResolver = TuneBackendResolver(shellGateway, fpsStack.privilegeModeStore)
            val tuneShell = ShellGatewayTuneShell(shellGateway) {
                backendResolver.refresh().asPrivilegeTier()
            }
            val snapshotStore = TuneSnapshotStore(
                context = appCtx,
                prefs = SharedPrefsKeyValue(appCtx.getSharedPreferences(TunePrefs.PREFS_NAME, Context.MODE_PRIVATE)),
                shell = tuneShell
            )
            val probe = TuneProbe(appCtx, tuneShell, backendResolver = backendResolver)
            val applier = TuneApplier(appCtx, tuneShell, snapshotStore)

            return TuneManager(
                appContext = appCtx,
                shellGateway = shellGateway,
                prefs = prefs,
                snapshotStore = snapshotStore,
                probe = probe,
                applier = applier,
                backendResolver = backendResolver
            )
        }
    }
}
