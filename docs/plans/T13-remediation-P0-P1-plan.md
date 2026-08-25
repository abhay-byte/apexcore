# Plan: T13-remediation-P0-P1 — Fix T13 UI integration, verified Settings, thermal guard live-start, preset truth and scheduler verification

## Task Summary
Commit `6992a60` delivered the T13 backend core (dynamic CPU/GPU discovery, transactional max locks, `TuneBackendIdentity`, `VerificationMode`, UserService) but left product integration incomplete. Review at 06:35 UTC re-classified T13 as `REQUEST CHANGES` with 6× P0 (UI enum missing, governor wiring broken, Game Mode unreachable, Settings unverified, thermal guard not started on live max-lock, preset `applied` falsified) and 7× P1 (preset not exposed, no first-use ack, I/O scheduler verifier wrong, snapshot verification mode not persisted, capability refresh race, read-only-only hardware proof, Shizuku stdout/stderr merged). This plan fixes those gaps with smallest correct changes, preserving the verified backend work, to satisfy `docs/plans/T13-game-optimisation-v2-real-backends-max-performance.md` §§4/8/9/11/12/16 acceptance criteria.

## Research Sources
- <source: file `app/src/main/kotlin/com/ivarna/apexcore/ui/iron/tune/TuningRoom.kt:27-35,240-263` | `app/src/main/kotlin/com/ivarna/apexcore/ui/shell/MainScreen.kt:222-251` | `app/src/main/kotlin/com/ivarna/apexcore/tune/TuneManager.kt:42-49,101-142,166-242,235-237` | `app/src/main/kotlin/com/ivarna/apexcore/tune/TunePresetManager.kt:19-61,48-58` | `app/src/main/kotlin/com/ivarna/apexcore/tune/TuneApplier.kt:251-312,511-649` | `app/src/main/kotlin/com/ivarna/apexcore/tune/TuneProbe.kt:61-78,140-148,344-393` | `app/src/main/kotlin/com/ivarna/apexcore/tune/TuneCatalog.kt:545-577` | `app/src/main/kotlin/com/ivarna/apexcore/tune/TuneModels.kt:6-10,115-139` | `app/src/main/kotlin/com/ivarna/apexcore/tune/TuneSnapshotStore.kt:28-63,132-174` | `app/src/main/kotlin/com/ivarna/apexcore/tune/TuneThermalGuard.kt:27-50` | `app/src/main/kotlin/com/ivarna/apexcore/tune/cpu/CpuFrequencyLockController.kt:22-42` | `app/src/main/kotlin/com/ivarna/apexcore/tune/gpu/GpuFrequencyLockController.kt:17-34`>
- <source: file `docs/plans/T13-game-optimisation-v2-real-backends-max-performance.md:29-39,65-78,182-189,252-290,582-590,864-910,1246-1267`>
- <source: URL https://developer.android.com/reference/android/provider/Settings.Global | Android Settings.Global `getString/putString` readback pattern; triad System/Global/Secure fallback>
- <source: URL https://developer.android.com/reference/android/os/PowerManager#getCurrentThermalStatus() | addThermalStatusListener / THERMAL_STATUS_SEVERE>
- <source: URL https://github.com/RikkaApps/Shizuku-API | Shizuku UserService vs `newProcess`, `getUid()` 0 vs 2000>
- <source: URL https://www.kernel.org/doc/html/latest/admin-guide/pm/cpufreq.html | policy `scaling_min/max_freq` ordering>
- <source: URL https://developer.android.com/games/optimize/adpf/gamemode/gamemode-api | `cmd game list-modes` / `mode performance` per-package>

## Current Architecture
- **Build:** `app/build.gradle.kts:12,30,59` adds `applicationIdOverride`, `aidl`, `testInstrumentationRunner`; `app/src/main/aidl/.../IPrivilegedExecutor.aidl:6` exists with single `Bundle` `KEY_OUTPUT`.
- **Privilege:** `fps/privilege/ShellGateway.kt:15-22,120-136,154-195` already uses `verified` + `VerificationMode` typed match (`EXACT_INT`, `GOVERNOR_TOKEN`, `IO_SCHEDULER_ACTIVE_TOKEN`, `BOOLEAN_NORMALIZED`); `TuneBackendResolver` distinguishes `SU_ROOT/SHIZUKU_ROOT/SHIZUKU_SHELL/STANDARD`.
- **Discovery:** `tune/cpu/CpuPolicyDiscovery.kt` enumerates `/sys/devices/system/cpu/cpufreq/policy*`; `tune/gpu/GpuDevfreqDiscovery.kt` filters non-GPU devfreq; `CpuPolicyDiscovery.governorIntersection()` used in `TuneProbe.kt:369-392`.
- **Controllers:** `CpuFrequencyLockController.kt:22-42` returns `policies.size*2`, `CpuGovernorController.kt:16-44` transactional per-policy, `GpuFrequencyLockController.kt:17-34` returns 2, `GpuGovernorController.kt:16-39` single path, `GameModeController.kt` per-package `query/applyPerformance/restore`.
- **Probe/Applier:** `TuneProbe.kt:140-148` GAME_MODE global false; `TuneProbe.kt:344-357` scheduler snapshot uses `Regex("""\[(.*?)\]""")` but catalog `TuneCatalog.kt:545-577` declares `valueKind=ENUM` ⇒ default `GOVERNOR_TOKEN` not `IO_SCHEDULER_ACTIVE_TOKEN`. `TuneApplier.kt:251-312` `applySingleNode` snapshots `tokenToSnapshot` but via `recordOriginal(path, value)` (no `recordVerified`), restores fallback `VerificationModeFor`.
- **Manager/Session:** `TuneManager.kt:42-49` `TuneThermalGuard` callback releases max locks; `TuneManager.kt:166-242` `applyForSession` starts guard only when `CPU_LOCK_MAX`/`GPU_LOCK_MAX` intent on at launch (`TuneManager.kt:235-237`); `TuneManager.kt:101-142` `setIntent` live-applies without starting guard. `TunePresetManager.kt:19-61` composes 5 components, uses integer `report.applied.coerceAtMost(requested)` + index-based `mapIndexed` verified marking → falsified report (review bug).
- **UI:** `TuningRoom.kt:27-35,222-263` `TuneOptionUi(checked,onToggle)` + `TuneRow:240-263` only `MachinedToggle:261`; `MainScreen.kt:222-251` `refreshTune()` does `refreshCapabilities()` (async `TuneProbe.kt:61-73`) then immediate `capabilities.value` read (race) and `onToggle = { TuneValue(checked) }` with no `raw`. No preset button, no first-use ack. `TuneSpecs.kt:40-46,114-120,250-256` defines `ENUM` for CPU_GOVERNOR/GPU_GOVERNOR/THERMAL_SCONFIG/IO_SCHEDULER/NET_TCP and `SLIDER` for 8 options (GPU_ADRENO 0..3, CPU_UCLAMP, CPU_STUNE, INPUT_BOOST_MS, DEVFREQ_BOOST, VM_* , IO_READAHEAD) — all currently rendered as toggles.
- **Settings:** `TuneApplier.kt:511-648` `applyFocusHeadsUp/Immersive/DisplayPeak/Miui` use `shell.execute(... settings put ...)` + `res.isSuccess` without readback verify.
- **Snapshot:** `TuneSnapshotStore.kt:28-34` legacy `recordOriginal(path,value)` (no verificationMode/owner) hardcodes `EXACT_STRING`; `TuneSnapshotStore.kt:37-55` owner overload also hardcodes `EXACT_STRING`; `TuneSnapshotStore.kt:58-63` `recordVerified` exists but `applySingleNode:286` never calls it.
- **Thermal:** `TuneThermalGuard.kt:27-50` `start()` idempotent (`pollJob != null` return), `severe` volatile, severe callback releases locks.
- **Tests:** `T13DynamicBackendTest.kt`, `RealmeX2ProReleaseGateTest.kt` (read-only, 3/3 pass) — no privileged mutation matrix.

## Affected Components & Dependencies
- UI: `TuningRoom.kt:27-263`, `MainScreen.kt:222-574`, `Iron Controls.kt/Plates.kt` for new rows
- Tune domain: `TuneManager.kt:42-417`, `TuneApplier.kt:251-683`, `TuneProbe.kt:61-496`, `TuneCatalog.kt:545-577`, `TuneModels.kt:6-185` (`TuneControlKind`, `TuneValueKind`, `VerificationMode`, `TuneApplyReport`), `TuneSpecs.kt`, `TuneSnapshotStore.kt:28-244`, `TunePrefs.kt` (new ack), `TuneThermalGuard.kt:27-74`, `TunePresetManager.kt:1-61`, `GameModeController.kt`, `TuneShell.kt:1-52`, `ShellGateway.kt:120-215`, `cpu/*`, `gpu/*`
- Privilege: `IPrivilegedExecutor.aidl:6`, `ShizukuUserService.kt:21-76`, `ShizukuExecutorClient.kt:38-46`, `ShellGateway.kt:201-207`, `ShellResult` (`fps/util/ShellExecutor.kt`)
- Tests: `app/src/test/**`, `app/src/androidTest/**`
- Docs: `docs/results/game-tune-v2/*`, `docs/plans/*`
- Dependencies: `androidx.compose` (`ExposedDropdownMenu`/`FilterChip` or `Iron` `ChamferButton`), `PowerManager` thermal, `Settings.Global/System/Secure`, Shizuku `UserService`, Kotlin coroutines `StateFlow/Mutex`

## Implementation Steps (ordered, smallest correct change)

### Step 1 — Fix `TuneOptionUi` to support ENUM + SLIDER and preserve toggle (P0 #1, #2) — `TuningRoom.kt:27-35,240-263` + `TuneSpecs.kt:40-256`
- Extend `TuneOptionUi` with backward-compatible defaults:
  ```kotlin
  data class TuneOptionUi(
    val key: String, val title:String, val description:String,
    val available:Boolean, val reason:String?,
    val kind: TuneControlKind = TuneControlKind.SWITCH, // default preserves MainScreen.kt:234 6-arg construction
    val checked:Boolean, val onToggle:(Boolean)->Unit,
    val enumOptions:List<String> = emptyList(),
    val selectedEnum:String? = null,
    val onEnumSelect:(String)->Unit = {},
    val sliderRange:IntRange? = null,
    val sliderValue:Int? = null,
    val onSliderChange:(Int)->Unit = {}
  )
  ```
- Correct slider statement from v1 plan: there is **no** existing slider in `TuningRoom`; add `SliderRow` branch. For ENUM, row must keep toggle + selector:
  ```kotlin
  @Composable private fun TuneRow(opt: TuneOptionUi) {
    when(opt.kind) {
      ENUM -> Row { Column(weight=1f) { Text(title); Text(reason?:description) }
        // selector disabled when !checked, enabled only when checked && available
        EnumSelector(opt.selectedEnum, opt.enumOptions, enabled=opt.checked && opt.available, onSelect=opt.onEnumSelect)
        MachinedToggle(opt.checked, opt.onToggle, enabled=opt.available)
      }
      SLIDER -> Row { /* label + Slider(value, range, enabled=checked) + value text */ MachinedToggle(...) }
      else -> Row { /* existing SWITCH */ MachinedToggle(...) }
    }
  }
  ```
- ENUM disable path: `onToggle(false)` must set `TuneValue(false, raw=selectedEnum)` retaining last `raw` so re-enable restores it; `onEnumSelect` sets `TuneValue(true, token)`.
- Empty governor intersection: if `enumOptions.isEmpty()` show row disabled with reason "No common governor" (see Risks).

### Step 2 — Wire governors and Game Mode correctly in `MainScreen` (P0 #2, #3) — `MainScreen.kt:222-251` + `TuningRoom.kt:42-50` + `GameModeController.kt:18-69`
- Change `refreshTune()` to accept `selectedGamePkg: String?` param. Add state in `MainScreen`:
  ```kotlin
  var selectedTuneGamePkg by remember { mutableStateOf<String?>(null) }
  LaunchedEffect(gamesList) { if(selectedTuneGamePkg==null) selectedTuneGamePkg = gamesList.firstOrNull()?.pkg }
  ```
  Pass `selectedGamePkg = selectedTuneGamePkg` into `TuningRoom` (add `selectedGamePkg` + `onGamePkgSelect` params to `TuningRoom.kt:42-50` — renders a small game picker dropdown above categories when `gamesList` non-empty).
- For each spec in `refreshTune()`:
  - `CPU_GOVERNOR` (`TuneSpecs.kt:114-120`): `cap = caps[CPU_GOVERNOR]`; `enumOptions = cap?.availableOptions ?: emptyList()` from `TuneProbe.kt:369-392`; `selected = tuneManager.intent(spec.id).raw ?: enumOptions.firstOrNull { it=="performance" } ?: enumOptions.firstOrNull()`; `kind = ENUM`; `onEnumSelect = { token -> tuneManager.setIntent(spec.id, TuneValue(true, token)); refreshTune(selectedTuneGamePkg) }`; `onToggle = { checked -> val raw = if(checked) selected else tuneManager.intent(spec.id).raw; tuneManager.setIntent(spec.id, TuneValue(checked, raw)); refreshTune(...) }`
  - `GPU_GOVERNOR` same via `TuneProbe.kt:417-434`.
  - `GAME_MODE_PERFORMANCE` (`TuneSpecs.kt:283-290`): **do NOT** use `caps[GAME_MODE]` (always `available=false` per `TuneProbe.kt:140-148`). Instead:
    ```kotlin
    val pkg = selectedTuneGamePkg ?: prefs.getSessionPkg().takeIf { it.isNotBlank() }
    val gmCap = pkg?.let { tuneManager.gameModeCapability(it) } // TuneManager.kt:89-92 delegates to GameModeController.kt:18-38
    val isAvail = gmCap?.supportsPerformance == true
    val reason = if(!isAvail) when {
      pkg.isNullOrBlank() -> "Select a game"
      gmCap==null -> "Game Mode unavailable"
      else -> gmCap.reason?.name ?: "Performance not exposed by this game"
    } else null
    ```
    `checked = tuneManager.intent(spec.id).on`; `available = isAvail`; `kind = SWITCH` (toggle) — selector not needed. Remove `firstOrNull` arbitrary fallback; single source is `selectedTuneGamePkg`.

### Step 3 — Fix Settings mutations to require readback verification (P0 #4) — `TuneApplier.kt:511-649` + `TuneSnapshotStore.kt:28-63`
- For each `applyFocusHeadsUp` (`TuneApplier.kt:511-519`), `applyFocusImmersive` (`532-539`), `applyDisplayPeak` (`557-578`), `applyDisplayMiui` (`600-622`):
  1. Read original via **triad** `Settings.System` → `Settings.Global` → `Settings.Secure` (try each) **and** fallback `shell.execute("settings get system|global <key>")` if `ContentResolver` returns null; store via `snapshotStore.recordOriginal("settings://<key>", original)` (already).
  2. `val res = shell.execute("settings put ...", tier)`; disregard `isSuccess` alone.
  3. Read back via same triad + fallback shell `settings get`; verify exact (`a==e` for `SETTINGS_VALUE`, numeric `EXACT_INT` for peak/min). For `heads_up`: `cur==0`; for `policy_control`: `cur=="immersive.full=*"`; for peak: both `peak_refresh_rate` and `min_refresh_rate` equal requested; for miui: each present key equals requested.
  4. On `verify==false`: `snapshotStore.removeOriginal("settings://<key>")` (and paired key) and `return false` **without** `recordVerified`.
  5. On `verify==true`: `snapshotStore.recordVerified("settings://<key>", requested, VerificationMode.SETTINGS_VALUE)` (and second key for peak/miui) and `return true`.
- Mirror `restoreFocusHeadsUp` (`521-530`), `restoreFocusImmersive` (`542-554`), `restoreDisplayPeak` (`581-598`), `restoreDisplayMiui` (`625-648`) to verify restore readback before `removeOriginal`; if restore verify false, keep snapshot and return `false` so `TuneManager.restoreSessionLocked` retains `tune_applied=true`.
- `ShellGateway.kt:184` already handles `SETTINGS_VALUE` as `a==e`; no change needed there.

### Step 4 — Thermal guard live-start gated on verified count (P0 #5 safety) — `TuneManager.kt:101-142,235-237,369-371` + `TuneThermalGuard.kt:27-50`
- In `setIntent` live path (`_sessionActive.value` branch `TuneManager.kt:112-140`):
  ```kotlin
  if (value.on && id.isMaxLock()) {
    if (thermalGuard.severe) { Log.w(TAG, "Ignoring $id while thermal guard is severe"); return true }
    // after mutex.withLock:
    val count = applier.applyBundle(id, intent, tier) // TuneApplier.kt:33-48 returns verified write count
    if (count > 0) thermalGuard.start() // TuneThermalGuard.kt:27 idempotent
    else Log.w(TAG, "Live max-lock $id verify failed, guard not started")
  } else if (!value.on && id.isMaxLock()) {
    val restored = applier.restoreBundle(id, tier) // TuneApplier.kt:93-145
    // stop only when no max-lock owners remain verified
    val anyMaxOn = prefs.getIntent(TuneId.CPU_LOCK_MAX).on || prefs.getIntent(TuneId.GPU_LOCK_MAX).on
    val anyMaxOwners = snapshotStore.owners(CpuPolicyDiscovery paths) + snapshotStore.owners(GpuDevfreq paths) // via TuneSnapshotStore.kt:102 owners(path)
    if (!anyMaxOn && anyMaxOwners.isEmpty()) thermalGuard.stop()
    // alternative: if restored>0 && !anyMaxOn => stop
  }
  ```
- Do **not** start guard before `applyBundle`; do **not** stop guard if `restoreBundle` verify failed (snapshot retained). `thermalGuard.start()` remains no-op if already started.

### Step 5 — Fix preset reporting to per-component verified truth (P0 #6) — `TunePresetManager.kt:1-61` + `TuneManager.kt:166-268` + `TuneModels.kt:154-160`
- Add to `TuneModels.kt:154-160`:
  ```kotlin
  data class TuneApplyReport(
    val applied:Int, val failed:Int, val skipped:Int, val sessionActive:Boolean,
    val details: Map<String,String> = emptyMap(),
    val components: Map<TuneId,Boolean> = emptyMap() // NEW: per-TuneId verified truth
  )
  ```
  Keep existing `applied` for compat but derive as `components.count { it.value }` when `components` non-empty.
- Change `TuneManager.kt:166-242` `applyForSession(gamePkg, filterIds: Set<TuneId>? = null)` to accept optional filter; when non-null loop only `spec.id in filterIds`. Build `mutableMapOf<TuneId,Boolean>` `compVerified`; for each id set `compVerified[id] = (count>0)` or `result.verified` for Game Mode. Return `TuneApplyReport(..., components = compVerified)`. Existing callers without filter get full map (compat).
- `TunePresetManager.kt:18-61` `applyMaximumPerformance(gamePackage)` now:
  ```kotlin
  val ids = listOf(GAME_MODE_PERFORMANCE, CPU_GOVERNOR, CPU_LOCK_MAX, GPU_GOVERNOR, GPU_LOCK_MAX) // TunePresetManager.kt:21-27
  // setIntent for supported only (existing)
  val report = manager.applyForSession(gamePackage, filterIds = ids.toSet()) // uses new per-component report
  val requested = components.count { it.supported } // TunePresetManager.kt:49
  val applied = report.components.filterKeys { it in ids }.count { it.value }
  return TunePresetReport(
    name="Maximum Performance", applied=applied, requested=requested, partial=applied < requested,
    components = components.map { c -> c.copy(verified = report.components[c.id]==true, reason = if(report.components[c.id]==true) "verified" else c.reason) }
  )
  ```
  Extend `TunePresetComponent` to `data class TunePresetComponent(val id:TuneId, val requested:Boolean, val supported:Boolean, val verified:Boolean, val reason:String)` (or keep `reason` as verified marker). **Do not** call controllers directly — must go via `TuneManager.applyForSession` to preserve mutex/thermal/snapshot lifecycle.

### Step 6 — Expose Maximum Performance as user-visible action (P1 #7) — `TuningRoom.kt:42-50,152-208` + `MainScreen.kt:566-574`
- Add to `TuningRoom.kt:42-50` params:
  ```kotlin
  onMaximumPerformance: ((String)->Unit)? = null,
  presetReport: TunePresetReport? = null,
  selectedGamePkg: String? = null,
  onGamePkgSelect: (String)->Unit = {}
  ```
- In `MainScreen.kt:566-574` add above categories an `EngravedPlate` with `ChamferButton("MAXIMUM PERFORMANCE")` calling `scope.launch { val pkg = selectedTuneGamePkg ?: return@launch; val report = presetManager.applyMaximumPerformance(pkg); refreshTune(pkg); toast.show("${report.applied}/${report.requested} verified") }` plus per-component chips (`report.components` verified/reason). Button enabled only when `selectedTuneGamePkg` non-null and at least one preset id supported.

### Step 7 — First-use high-power acknowledgement (P1 #8) — `TunePrefs.kt` + `MainScreen.kt:242-244`
- Add `TunePrefs.kt` keys: `const val KEY_MAX_LOCK_ACKED = "tune_ack_max_locks"`; `fun isMaxLockAcked():Boolean = prefs.getBoolean(KEY_MAX_LOCK_ACKED,false)`; `fun setMaxLockAcked(v:Boolean)=prefs.edit().putBoolean(KEY_MAX_LOCK_ACKED,v).apply()`.
- In `MainScreen.kt:242-244` `onToggle` for `CPU_LOCK_MAX`/`GPU_LOCK_MAX` and preset button, intercept:
  ```kotlin
  if (checked && (spec.id==CPU_LOCK_MAX || spec.id==GPU_LOCK_MAX) && !tunePrefs.isMaxLockAcked()) {
    showAckDialog = true; return@TuneOptionUi
  }
  ```
  Dialog text per plan §4.3: "Maximum clocks can increase heat and battery drain. ApexCore keeps Android/kernel thermal protection enabled and will release max locks if the device reaches severe thermal stress." Confirm persists `setMaxLockAcked(true)` then proceeds; cancel keeps toggle off.

### Step 8 — I/O scheduler verification wiring (P1 #9) — `TuneCatalog.kt:545-577` + `TuneModels.kt:132-134`
- In `TuneCatalog.kt:545-552` set each `IO_SCHEDULER` `TuneNode:545-577` explicitly:
  ```kotlin
  TuneNode(path="/sys/block/sda/queue/scheduler", id=IO_SCHEDULER, ..., verificationMode=VerificationMode.IO_SCHEDULER_ACTIVE_TOKEN, groupId="io_scheduler")
  ```
  (repeat for sdb/mmcblk0/dm-0). Override default which for `ENUM` is `GOVERNOR_TOKEN` (`TuneModels.kt:132-134`).

### Step 9 — Persist verification mode on successful generic mutations (P1 #10) — `TuneApplier.kt:251-312` + `TuneSnapshotStore.kt:28-63`
- Add overload in `TuneSnapshotStore.kt:28-63`:
  ```kotlin
  @Synchronized fun recordOriginal(path:String, originalValue:String, verificationMode:VerificationMode) {
    if(path !in entries) { entries[path]=TuneSnapshotEntry(path, originalValue, verificationMode=verificationMode); save... }
  }
  // keep owner overload but add verificationMode param with default EXACT_STRING for compat
  @Synchronized fun recordOriginal(path:String, originalValue:String, owner:TuneId, transactionId:String, backend:TuneBackendIdentity, verificationMode:VerificationMode = VerificationMode.EXACT_STRING)
  ```
- Change `TuneApplier.kt:286` from `snapshotStore.recordOriginal(node.path, tokenToSnapshot)` to `snapshotStore.recordOriginal(node.path, tokenToSnapshot, node.verificationMode)` (or owner overload with `verificationMode`).
- After `writeRes.verified` true in `TuneApplier.kt:297-310` add `snapshotStore.recordVerified(node.path, targetValue, node.verificationMode)`; on rollback path already removes.
- `TuneSnapshotStore.kt:132-174` load already parses `verificationMode` name; existing JSON without it keeps `EXACT_STRING` until next successful write repairs it.

### Step 10 — Fix capability refresh race (P1 #11) — `TuneProbe.kt:61-78` + `TuneManager.kt:84-86` + `MainScreen.kt:222-251`
- Add `TuneManager.kt:84-86`:
  ```kotlin
  suspend fun refreshCapabilitiesSync(): Map<TuneId,TuneCapability> {
    // respect cache/fingerprint or bypass with documented reason; join existing probeJob
    probe.probeSync() // TuneProbe.kt:75-78 already handles Dispatchers.IO
    return capabilities.value
  }
  // alternatively expose probe.probeSync with TTL bypass flag
  ```
  In `TuneProbe.kt:61-78` make `probeSync` optionally respect TTL: if `now - lastProbeTime < CACHE_TTL_MS && backendFingerprint==lastFingerprint` return cached `capabilities.value` unless caller forces refresh; for `refreshCapabilitiesSync` bypass TTL (fresh probe needed on user pull) but still join `probeJob?.join()` before `probeInternal`.
- Make `MainScreen.kt:222-251` `refreshTune(selectedGamePkg)` a `suspend` or keep `scope.launch` but:
  ```kotlin
  fun refreshTune(pkg:String?) { scope.launch {
    isTuneProbing = true
    try {
      val caps = tuneManager.refreshCapabilitiesSync() // await completion
      // build grouped from caps (not stale capabilities.value before probe)
      tuneCategories = grouped
    } finally { isTuneProbing = false }
  }}
  ```
  Cancel previous `probeJob` handling already in `TuneProbe.kt:69` `probeJob?.cancel()`.

### Step 11 — Harden Shizuku executor stdout/stderr split (P2) — `IPrivilegedExecutor.aidl:6` + `ShizukuUserService.kt:21-76` + `ShizukuExecutorClient.kt:38-46` + `fps/util/ShellExecutor.kt` + `ShellGateway.kt:201-207`
- Change `IPrivilegedExecutor.aidl:6` Bundle keys to `stdout`, `stderr`, `exitCode` plus legacy `output` fallback (`output = stdout + if(stderr.isNotEmpty()) "\n[stderr] $stderr" else ""`).
- `ShizukuUserService.kt:21-23` change `redirectErrorStream(true)` to `false` and capture `process.inputStream` and `process.errorStream` separately.
- `ShizukuExecutorClient.kt:38-46` parse `stdout = bundle.getString("stdout")`, `stderr = bundle.getString("stderr")`, `exitCode`.
- `ShellResult` add `val stderr:String? = null`; `ShellGateway.executeViaShizuku:201-207` constructs `ShellResult(output=stdout, stderr=stderr, exitCode)`; `ShellGateway.parseWriteOutput:154-172` still parses `RC=`/`READBACK=` from `stdout` only but logs `stderr` on `!verified`.

### Step 12 — Testing & docs
- Add unit tests: `SettingsVerificationFailsWithoutReadbackTest`, `ThermalGuardStartsOnLiveMaxLockTest`, `PresetPerComponentReportingTest` (assert falsified-index bug fixed: with 3 CPU policies + only CPU_LOCK supported, `report.components[GAME_MODE].verified==false`), `IoSchedulerVerificationModeTest` (`TuneCatalog.nodesByTuneId[IO_SCHEDULER].first().verificationMode == IO_SCHEDULER_ACTIVE_TOKEN`), `EnumUiWiringTest`, `RefreshCapabilitiesSyncTest`.
- Keep `RealmeX2ProReleaseGateTest` read-only; add comment that privileged writes remain unverified on that stock device, per `docs/results/game-tune-v2/README.md:7-8`.

## File-Level Change Map
| File | Line(s) | Change | Rationale |
|------|---------|--------|-----------|
| `app/src/main/kotlin/com/ivarna/apexcore/ui/iron/tune/TuningRoom.kt` | 27-35,42-50,152-263 | Extend `TuneOptionUi` with `kind` default `SWITCH`, `enumOptions/selectedEnum/onEnumSelect`, `sliderRange/sliderValue/onSliderChange`; branch `TuneRow` to `ENUM` toggle+selector + `SLIDER` row + preset button plate; add `selectedGamePkg/onGamePkgSelect` picker | P0 #1 toggle+selector, P1 #7 preset, P1 #8 ack hook, fix missing SLIDER |
| `app/src/main/kotlin/com/ivarna/apexcore/ui/shell/MainScreen.kt` | 222-251,234-244,566-574 | Replace `refreshCapabilities` fire-and-forget with `await refreshCapabilitiesSync`; wire `CPU_GOVERNOR`/`GPU_GOVERNOR` `TuneValue(true,token)`; wire `GAME_MODE_PERFORMANCE` via `gameModeCapability(selectedTuneGamePkg)` not global cap; add `selectedTuneGamePkg` state + preset button + ack dialog | P0 #2/#3, P1 #11/#7/#8 |
| `app/src/main/kotlin/com/ivarna/apexcore/tune/TuneApplier.kt` | 251-312 (applySingleNode), 511-649 (Settings) | `applySingleNode:286` pass `node.verificationMode` to `recordOriginal` + `recordVerified` on success; Settings `apply*/restore*` require `Settings.*` triad + `shell settings get` readback and `removeOriginal` on verify false / `recordVerified(SETTINGS_VALUE)` on true | P0 #4, P1 #10 |
| `app/src/main/kotlin/com/ivarna/apexcore/tune/TuneManager.kt` | 84-86,101-142,166-242 | Add `refreshCapabilitiesSync()` suspend; `setIntent` live max-lock: verify `applyBundle count>0` before `thermalGuard.start()`, verified `restoreBundle` before `stop` when no max owners remain | P0 #5, P1 #11 |
| `app/src/main/kotlin/com/ivarna/apexcore/tune/TuneProbe.kt` | 61-78 | Make `probeSync` respect/join `probeJob` and optionally bypass TTL; ensure `refreshCapabilitiesSync` awaits completion without probe storm | P1 #11 |
| `app/src/main/kotlin/com/ivarna/apexcore/tune/TunePresetManager.kt` | 1-61 | Add `verified:Boolean` to `TunePresetComponent`; change report to per-TuneId `components:Map<TuneId,Boolean>` derived from `TuneApplyReport.components`; remove `coerceAtMost`/`mapIndexed` falsification | P0 #6 |
| `app/src/main/kotlin/com/ivarna/apexcore/tune/TuneModels.kt` | 6-10,132-134,154-160 | Add `components:Map<TuneId,Boolean>` to `TuneApplyReport`; ensure `VerificationMode` enum unchanged; `TuneControlKind` no change but `TuneOptionUi.kind` default `SWITCH` | P0 #6 preset truth, P1 #9/#10 |
| `app/src/main/kotlin/com/ivarna/apexcore/tune/TuneCatalog.kt` | 545-577 | Set `IO_SCHEDULER` nodes `verificationMode=IO_SCHEDULER_ACTIVE_TOKEN` (override ENUM default `GOVERNOR_TOKEN`) | P1 #9 |
| `app/src/main/kotlin/com/ivarna/apexcore/tune/TuneSnapshotStore.kt` | 28-63,132-174 | Add `recordOriginal(path,value,verificationMode)` overload; extend owner overload with `verificationMode` param default `EXACT_STRING`; fix first-write to store correct mode | P1 #10 |
| `app/src/main/kotlin/com/ivarna/apexcore/tune/TunePrefs.kt` | (new keys) | Add `KEY_MAX_LOCK_ACKED`, `isMaxLockAcked()/setMaxLockAcked()` | P1 #8 disclosure |
| `app/src/main/aidl/.../IPrivilegedExecutor.aidl` | 6 | Add Bundle keys `stdout`/`stderr`/`exitCode` + legacy `output` fallback | P2 separate streams |
| `app/src/main/kotlin/com/ivarna/apexcore/fps/privilege/ShizukuUserService.kt` | 21-23,73-76 | `redirectErrorStream(false)` + capture stderr separately | P2 |
| `app/src/main/kotlin/com/ivarna/apexcore/fps/privilege/ShizukuExecutorClient.kt` | 38-46 | Parse `stdout`/`stderr`/`exitCode` from Bundle | P2 |
| `app/src/main/kotlin/com/ivarna/apexcore/fps/util/ShellExecutor.kt` (+ `ShellGateway.kt:201-207`) | — | Add `stderr` field to `ShellResult`; `executeViaShizuku` preserves stderr, `parseWriteOutput` logs it | P2 |
| `app/src/test/**`, `app/src/androidTest/**` | — | New tests: `SettingsVerification*`, `ThermalGuardLive*`, `PresetPerComponent*`, `IoSchedulerMode*`, `EnumWiring*`, `RefreshSync*` | Verification |
| `docs/results/game-tune-v2/README.md` | 7-8 | Clarify preset gated until P1 fix, note read-only scope retained | Docs truth |

## Testing Strategy
- **Unit:** `TuneManagerThermalGuardLiveTest` — enable `CPU_LOCK_MAX` via `setIntent` while `sessionActive=true` → assert `thermalGuard.start()` called only when `count>0`, severe releases locks. `SettingsVerificationTest` — mock `shell.execute` returns `isSuccess=true` but `Settings.get*` unchanged → `applyFocusHeadsUp` returns `false` and `snapshotStore.containsPath` false. `PresetPerComponentTest` — stub CPU lock returns 6 writes, GPU lock unsupported, Game Mode unsupported → report `applied=1, components[CPU_LOCK_MAX].verified=true, components[GAME_MODE_PERFORMANCE].verified=false` (proves index bug fixed). `IoSchedulerModeTest` — `TuneCatalog.nodesByTuneId[IO_SCHEDULER].first().verificationMode == IO_SCHEDULER_ACTIVE_TOKEN` and `applySingleNode` stores same mode. `EnumUiWiringTest` — `MainScreen.refreshTune` with `selectedGamePkg` produces `TuneOptionUi.kind==ENUM` and `selectedEnum` from `availableOptions`.
- **Integration:** `probeSync` vs `refreshCapabilities` race — `MainScreen` refresh after probe shows `isProbing=false` + caps populated (no stale `Checking…`). Enum selector end-to-end: select `performance` → `TuneValue(raw="performance")` persisted → `CpuGovernorController.apply` receives `performance`.
- **Manual/device:** On realme X2 Pro stock: confirm Game Mode per-package enable/disable when app exposes it and shows "Select a game" when none selected; on rooted Adreno device verify CPU `scaling_governor`/`min/max_freq` transactional lock + thermal severe path (simulate via `PowerManager` mock or hot run) restores.

## Acceptance Criteria (objective, verifiable)
- [ ] `TuningRoom.kt:240-263` renders `CPU_GOVERNOR`/`GPU_GOVERNOR` as ENUM row with toggle + selector populated from `cap.availableOptions` (`TuneProbe.kt:369-392,417-434`); selecting `performance` persists `TuneValue(true,"performance")`; disabling via toggle persists `TuneValue(false,<lastRaw>)`; `SLIDER` specs render `SliderRow` not toggle.
- [ ] `GAME_MODE_PERFORMANCE` enabled state derived from `tuneManager.gameModeCapability(selectedGamePkg)?.supportsPerformance` (`TuneManager.kt:89-92`), not global `cap.available` (`TuneProbe.kt:140-148`); empty `selectedGamePkg` shows `available=false` reason "Select a game".
- [ ] `TuneApplier.kt:511-649` Settings applies return `true` only when `Settings.*` triad + fallback `shell settings get` readback equals requested value; on mismatch snapshot `removeOriginal` and return false; on success `recordVerified(...,SETTINGS_VALUE)`.
- [ ] `TuneManager.kt:101-142` `setIntent(CPU_LOCK_MAX, on=true)` while session active starts `TuneThermalGuard.kt:27` only after `count>0` verified; severe status releases both max locks; `restoreBundle` verified `count>0` before `stop` when no max owners remain.
- [ ] `TunePresetManager.kt:1-61` reports `applied == report.components.filterKeys{id in presetIds}.count{it.value}` and `components[i].verified` corresponds to `ids[i]` via `TuneModels.kt:154-160` map, not clamped integer `coerceAtMost`; falsified 3-policy ×2 writes =6 no longer marks Game Mode verified.
- [ ] `TuningRoom.kt:152-208` exposes Maximum Performance button calling `presetManager.applyMaximumPerformance(selectedGamePkg)`; first enable shows `TunePrefs.kt` ack dialog with plan §4.3 text and persists `tune_ack_max_locks`.
- [ ] `TuneCatalog.kt:545-577` `IO_SCHEDULER` nodes have `verificationMode=IO_SCHEDULER_ACTIVE_TOKEN`; `TuneApplier.kt:286` stores that mode on first write and `recordVerified` persists it; restore `TuneApplier.kt:132,178` reads correct mode.
- [ ] `MainScreen.kt:222-251` `refreshTune()` awaits `tuneManager.refreshCapabilitiesSync()` (`TuneProbe.kt:61-78`) before reading `capabilities.value`; no stale `Checking…` flash; rapid double PROBE does not storm (joins `probeJob`).
- [ ] `./gradlew :app:testDebugUnitTest` passes; `IPrivilegedExecutor.aidl:6` Bundle contains `stdout`/`stderr`/`exitCode`; `ShizukuUserService.kt:21-23` not merged; `docs/results/game-tune-v2/README.md:7-8` states privileged writes unverified on stock X2 Pro.

## Risks & Mitigations (NEW_RISKS)
- **Compose dropdown complexity:** Reuse existing `Iron` `ChamferButton` + `FilterChip`/`ExposedDropdownMenu` pattern; keep selector as chip row to avoid design-system churn; ENUM disabled when no common governor.
- **Empty governor intersection:** `TuneProbe.kt:374` intersection may be empty → show disabled `EnumSelector` with 0 options and reason "No common governor"; `onEnumSelect` no-op.
- **Game Mode with no game selected:** Mitigate by `selectedTuneGamePkg` null check → `available=false` reason "Select a game" (suggestion coverage).
- **Settings readback on OEMs with Secure/System split:** Read triad `System`→`Global`→`Secure` + fallback `shell settings get system/global` with 120 ms timeout; do not assume single namespace.
- **Thermal listener unavailable pre-Q:** `TuneThermalGuard.kt:27-50` already polls 5s + uses `ThermalMonitor`; keep polling, no invented temperature threshold; `start()` idempotent.
- **Shizuku stdout/stderr split AIDL compat:** Keep legacy `output` key as fallback concatenation; new callers read `stdout`/`stderr` separately; no breaking change to freeze backend.
- **Existing snapshots with wrong mode:** `TuneSnapshotStore.kt:132-174` migration reads stored `verificationMode`; missing key keeps `EXACT_STRING` until next successful `recordVerified` repairs it.

## Handoff to Plan Reviewer
Validate: ENUM row keeps toggle + selector with `kind` default `SWITCH` and `SLIDER` branch (not claiming existing slider); `selectedGamePkg` is single authoritative Game Mode source (no `firstOrNull` fallback); Settings verification uses triad + fallback and snapshot rollback/`recordVerified`; `recordOriginal` overload persists `verificationMode`; `refreshCapabilitiesSync` handles TTL/join and `isTuneProbing` correctly; thermal guard live-start gated on verified `count>0` and stop gated on no max owners; preset per-component truth via `TuneApplyReport.components` map (not direct controller calls) fixes falsified `applied`; I/O scheduler mode is `IO_SCHEDULER_ACTIVE_TOKEN`; `IPrivilegedExecutor` keys `stdout/stderr/exitCode` + `ShellResult.stderr`; scope remains smallest correct change under `docs/plans/T13-game-optimisation-v2-real-backends-max-performance.md`.

