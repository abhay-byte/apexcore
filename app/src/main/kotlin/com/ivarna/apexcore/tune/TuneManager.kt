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
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    val capabilities: StateFlow<Map<TuneId, TuneCapability>> = probe.capabilities

    private val _sessionActive = MutableStateFlow(false)
    val sessionActive: StateFlow<Boolean> = _sessionActive.asStateFlow()

    @Volatile
    private var _owner: TuneSessionOwner = TuneSessionOwner.NONE
    val owner: TuneSessionOwner get() = _owner

    private val mutex = Mutex()
    private var recoverJob: Job? = null

    init {
        _owner = prefs.getOwner()
        startRecovery()
    }

    private fun startRecovery() {
        recoverJob = scope.launch {
            recoverSession()
        }
    }

    fun refreshCapabilities() {
        probe.refreshCapabilities()
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
            if (cap == null || !cap.available) {
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
                            applier.applyBundle(id, value, tier)
                        } else {
                            applier.restoreBundle(id, tier)
                            // If all bundles are turned off, end session
                            val anyOn = TuneSpecs.all.any { spec ->
                                prefs.getIntent(spec.id).on && capabilities.value[spec.id]?.available == true
                            }
                            if (!anyOn) {
                                restoreSession()
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

    fun writeTier(): PrivilegeTier? = when (FreezeFramework.activeBackend.value?.name) {
        "Root" -> PrivilegeTier.ROOT
        "Shizuku" -> PrivilegeTier.SHIZUKU
        else -> null
    }

    suspend fun applyForSession(gamePkg: String): TuneApplyReport {
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

            snapshotStore.recordBootIdentity()

            var applied = 0
            var failed = 0
            var skipped = 0

            val cpuFloorOn = prefs.getIntent(TuneId.CPU_FLOOR).on

            for (spec in TuneSpecs.all) {
                val id = spec.id
                val intent = prefs.getIntent(id)
                val cap = capabilities.value[id]

                if (!intent.on || cap?.available != true) {
                    skipped++
                    continue
                }

                // Mutex: if CPU_FLOOR is on, ignore split cluster intents
                if (cpuFloorOn && (id == TuneId.CPU_FLOOR_LITTLE || id == TuneId.CPU_FLOOR_BIG || id == TuneId.CPU_FLOOR_PRIME)) {
                    skipped++
                    continue
                }

                val count = applier.applyBundle(id, intent, tier)
                if (count > 0) {
                    applied += count
                } else {
                    failed++
                }
            }

            if (applied > 0) {
                _sessionActive.value = true
                prefs.setApplied(true)
                prefs.setSessionPkg(gamePkg)
            }

            Log.i(TAG, "applyForSession for $gamePkg: applied=$applied failed=$failed skipped=$skipped")
            TuneApplyReport(applied, failed, skipped, _sessionActive.value)
        }
    }

    suspend fun applyBundle(id: TuneId): TuneApplyReport {
        recoverJob?.join()
        return mutex.withLock {
            val tier = writeTier() ?: return@withLock TuneApplyReport(0, 0, 0, _sessionActive.value)
            val intent = prefs.getIntent(id)
            val cap = capabilities.value[id]
            if (!intent.on || cap?.available != true) {
                return@withLock TuneApplyReport(0, 0, 1, _sessionActive.value)
            }
            val count = applier.applyBundle(id, intent, tier)
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

    suspend fun restoreSession(): TuneApplyReport {
        recoverJob?.join()
        return mutex.withLock {
            val tier = writeTier() ?: PrivilegeTier.STANDARD
            val count = applier.restoreAll(tier)
            snapshotStore.clear()
            _sessionActive.value = false
            prefs.setApplied(false)
            setOwner(TuneSessionOwner.NONE)
            Log.i(TAG, "restoreSession completed: $count paths restored")
            TuneApplyReport(count, 0, 0, false)
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
        applier.restoreAll(tier)
        snapshotStore.clear()
        _sessionActive.value = false
        prefs.setApplied(false)
        setOwner(TuneSessionOwner.NONE)
    }

    fun deleteDummyKeysIfNeeded() {
        prefs.deleteDummyKeysIfNeeded()
    }

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
            val tuneShell = ShellGatewayTuneShell(shellGateway) {
                when (FreezeFramework.activeBackend.value?.name) {
                    "Root" -> PrivilegeTier.ROOT
                    "Shizuku" -> PrivilegeTier.SHIZUKU
                    else -> PrivilegeTier.STANDARD
                }
            }
            val snapshotStore = TuneSnapshotStore(
                context = appCtx,
                prefs = SharedPrefsKeyValue(appCtx.getSharedPreferences(TunePrefs.PREFS_NAME, Context.MODE_PRIVATE)),
                shell = tuneShell
            )
            val probe = TuneProbe(appCtx, tuneShell)
            val applier = TuneApplier(appCtx, tuneShell, snapshotStore)

            return TuneManager(
                appContext = appCtx,
                shellGateway = shellGateway,
                prefs = prefs,
                snapshotStore = snapshotStore,
                probe = probe,
                applier = applier
            )
        }
    }
}
