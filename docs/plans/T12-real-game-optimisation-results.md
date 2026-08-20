# T12 — Real Game Optimisation: Implementation vs spec

| Field | Value |
|-------|-------|
| **Document** | Implementation status — Real Game Optimisation |
| **ID** | T12-RESULTS |
| **Reference Spec** | [`T12-real-game-optimisation.md`](T12-real-game-optimisation.md) (rev 5) |
| **Options Spec** | [`T12-tune-options.md`](T12-tune-options.md) |
| **Status** | **PR 6 Complete.** All 5 bugs (B1–B5) and 13 suggestions (S1–S13) implemented and unit-tested. |
| **Target** | Android 16 (API 36) / minSdk 24 |
| **Tree** | `main` with PR 6 changes |
| **Review** | 2026-08-18 plan-compliance review resolved (B1–B5 fixed, S1–S13 landed) |

---

## 1. Executive summary

ApexCore deleted the legacy `dummy_opt_*` toggles and implemented a session-scoped `com.ivarna.apexcore.tune` stack. The **36 spec TuneIds** are in `TuneId` / `TuneSpecs`. Rejected sysfs paths are not catalogued.

With PR 6 landed:
- **[B1 Fixed]** Real `settings put` / restore for `FOCUS_HEADSUP`, `FOCUS_IMMERSIVE`, `DISPLAY_PEAK`, and `DISPLAY_MIUI` executed via `TuneShell.execute`. Real read verification on probe.
- **[B2 Fixed]** `restoreSessionLocked()` extracted; setting the last intent to OFF during an active session restores without deadlocking `TuneManager.mutex`.
- **[B3 Fixed]** `restoreSession()` and `recoverSession()` only clear snapshots and mark `tune_applied=false` if all paths restored successfully; failed paths remain recorded for retry.
- **[B4 Fixed]** Capabilities are auto-probed on `HomeScreen` (when elevated) and on first composition in `TuneScreen`. `applyForSession` auto-probes if cache is empty.
- **[B5 / Scorecard Validated]** `ShellGateway.mutex` acquired across all shell operations (S1). Apply wall budget (1500ms / 2500ms) enforced (S2). Home game-optimisation row hidden unless elevated (S3). Empty categories filtered out on `TuneScreen` (S4). Split CPU cluster rows disabled and labeled `"Covered by CPU frequency floor"` when `CPU_FLOOR` is enabled (S5). Dynamic cpufreq policy and Mali node discovery added (S6). Bare token parsing for `IO_SCHEDULER` implemented (S7). Unit test suite updated with real assertions across 79 tests (S8). Fastlane metadata L9 updated (S9). Root cgroup uclamp removed (S10). `FOCUS_DND` gated on notification policy access (S11). Watchdog fallback re-armed if overlay dies before `onCreate` (S12). Session restored immediately on mid-session elevation drop (S13).

---

## 2. Modules and Architecture

| Module | Location | Role |
|--------|----------|------|
| `TuneId` / `TuneSpecs` / `TuneCategory` | `tune/` | 36 IDs, 10 categories, control kinds |
| `TuneCatalog` | `tune/TuneCatalog.kt` | Clean sysfs nodes + `discoverPolicies` helper |
| `TuneProbe` | `tune/TuneProbe.kt` | Two-phase write-verifying probe (3500ms wall budget, 120ms node timeout) |
| `TuneApplier` / `TuneSnapshotStore` / `TunePrefs` | `tune/` | Apply, insert-if-absent snapshot, restore, intent persistence |
| `TuneManager` | `tune/TuneManager.kt` | Facade, session recovery, deadlock-free locks, backend observer |
| `TuneSessionWatchdog` | `tune/TuneSessionWatchdog.kt` | Overlay-less restore owner, top-app polling with unknown streak recovery |
| `TuneShell` + `ShellGateway` / `ShellExecutor` | `tune/` + `fps/` | Direct and shell execution, mutex protection, timeout destroy |
| `TuneScreen` / `TuneCategorySection` / `TuneOptionRow` | `ui/tune/` | Capability-gated UI, empty category filtering, CPU mutex overrides |

---

## 3. Catalog (36 Normative IDs)

| Category | Count | TuneIds |
|----------|:-----:|---------|
| GPU | 8 | `GPU_FLOOR`, `GPU_HOLD`, `GPU_ADRENO`, `GPU_GOVERNOR`, `GPU_PWRLEVEL`, `GPU_GED_GAME`, `GPU_SAMSUNG_MIN`, `GPU_SIMPLE` |
| CPU and scheduling | 8 | `CPU_FLOOR`, `CPU_FLOOR_LITTLE`, `CPU_FLOOR_BIG`, `CPU_FLOOR_PRIME`, `CPU_GOVERNOR`, `CPU_UCLAMP`, `CPU_STUNE`, `CPU_STUNE_IDLE` |
| Touch and input | 7 | `INPUT_BOOST_EN`, `INPUT_BOOST_MS`, `TOUCHBOOST`, `CPUFREQ_BOOST`, `DEVFREQ_BOOST`, `SCHED_BOOST_INPUT`, `SULTAN_INPUT` |
| Thermal | 1 | `THERMAL_SCONFIG` |
| Memory | 3 | `VM_SWAPPINESS`, `VM_VFS_CACHE`, `VM_DIRTY_RATIO` |
| Storage I/O | 2 | `IO_SCHEDULER`, `IO_READAHEAD` |
| Display | 2 | `DISPLAY_PEAK`, `DISPLAY_MIUI` |
| Focus | 3 | `FOCUS_DND`, `FOCUS_HEADSUP`, `FOCUS_IMMERSIVE` |
| Charging | 1 | `CHARGE_BYPASS` |
| Network | 1 | `NET_TCP` |

---

## 4. Key Decision Scorecard

| Item | Status | Notes |
|------|:------:|-------|
| KD-1 Tune package + `TuneManager` | **PASS** | Clean singleton facade in `com.ivarna.apexcore.tune` |
| KD-2 No apply-on-boot | **PASS** | Recovery restores orphaned snapshots; never applies at boot |
| KD-3 Live `setIntent` apply/restore | **PASS** | `restoreSessionLocked` prevents mutex reentrancy deadlock |
| KD-4 GPU keep-awake, not OpenGL | **PASS** | `GPU_HOLD` targets sysfs keepawake nodes |
| KD-5 sconfig 13/10; no thermal disable | **PASS** | Thermal throttling preserved; writes 13 with 10 fallback |
| KD-6 Floors, not min=max; pwrlevel ≠ GPU_FLOOR | **PASS** | Minimum frequencies raised; `min_pwrlevel` alone does not light `GPU_FLOOR` |
| KD-7 Hide unless elevated; write-verify per row | **PASS** | Home row hidden unless elevated; sysfs probed via write-verification |
| KD-8 Auto probe 3500 ms | **PASS** | Auto-probes on Home & TuneScreen; respects 3500ms budget |
| KD-9 Boot id + recover on IO | **PASS** | Failed restores retain snapshot & keep `tune_applied=true` |
| KD-10 `writePath` + destroy + mutex | **PASS** | `ShellGateway.mutex` acquired across all operations |
| KD-11 Watchdog primary when `start()==false` | **PASS** | Watchdog armed when overlay cannot start or fails before onCreate |
| KD-12 Overlay BOOST / Home Purge freeze-only | **PASS** | Freeze only; tuning is session-scoped to game launch |
| KD-13 Clean-room catalog | **PASS** | No rejected paths (`drop_caches`, `msm_thermal`, `sched_util_clamp_min`) |
| KD-14 No dummy-true migration | **PASS** | Legacy dummy keys deleted on launch |
| KD-15 0/N overlay toast | **PASS** | Toast shown when overlay is up and 0/N options applied |
| KD-16 `writeTier` from `activeBackend.name` | **PASS** | Root / Shizuku mapped cleanly |
| KD-17 startActivity first; 1500/2500 budget | **PASS** | Launched first, then applied under wall timeout budget |
| KD-18 No `sched_util_clamp_min`; top-app uclamp only | **PASS** | Root cgroup node removed from catalog |
| KD-19 Self-pkg apply no-op | **PASS** | Blocked for ApexCore self package |
| KD-20 36 IDs; `CPU_FLOOR` mutex | **PASS** | Split CPU rows disabled and overridden in UI and applier when `CPU_FLOOR` is on |

---

## 5. Test Suite Verification

- Total unit tests executed: **79** (all passing).
- Key test coverage:
  - `TuneEmptyCategoryHiddenTest`: asserts categories with 0 available options are hidden.
  - `WatchdogUnknownFailsTowardRestoreTest`: drives real `TuneSessionWatchdog` polling loop to session restoration.
  - `WatchdogWhenStartFalseTest`: exercises `GameOverlayService.start()` failure and verifies `owner=WATCHDOG`.
  - `TuneSetIntentDoesNotBlockTest`: verifies non-blocking `setIntent` on active session with slow shell writes.
  - `TuneProbeTimeoutTest`: verifies 3500ms wall budget compliance.
  - `LaunchSucceedsIfApplyThrowsTest`: asserts `GameLauncher.launch` succeeds even when apply throws runtime exceptions.
  - `SettingsApplyRestoreTest`: tests `FOCUS_HEADSUP`, `FOCUS_IMMERSIVE`, `DISPLAY_PEAK`, `DISPLAY_MIUI` settings execution and restoration.
  - `TuneLastOffDeadlockTest`: verifies turning off last active option does not deadlock.
  - `TuneFailedRestoreSnapshotRetainedTest`: verifies snapshot retention and retry readiness on failed restore.
  - `TuneBackendDropRestoresTest`: verifies automatic session restoration when backend drops to non-elevated.
