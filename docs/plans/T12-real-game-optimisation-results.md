# T12 — Real Game Optimisation: Implementation & Verification Results

| Field | Value |
|-------|-------|
| **Document** | Implementation & Verification Results — Real Game Optimisation |
| **ID** | T12-RESULTS |
| **Reference Spec** | [`docs/plans/T12-real-game-optimisation.md`](T12-real-game-optimisation.md) |
| **Options Spec** | [`docs/plans/T12-tune-options.md`](T12-tune-options.md) |
| **Status** | **Completed & Hardware Verified** |
| **Target** | Android 16 (API 36) / minSdk 24 |
| **Hardware Tested** | Physical Poco X6 Pro (`Y5WWBMJVOZSK4HU8`, Android 16, Shizuku & Root privilege tiers) |
| **Test Suite** | 74 Unit Tests (100% Passing) |

---

## 1. Executive Summary

ApexCore has successfully eliminated the deceptive dummy toggles (`dummy_opt_*`) identified in Play Policy Audit §1 and replaced them with a native, capability-gated, session-scoped kernel and game-boost tuning architecture. 

The implementation covers all **36 options across 10 categories** specified in `T12-tune-options.md`. All options are strictly gated behind capability probing and write-verification. In addition, when applied during game sessions, original kernel parameters are snapshotted to disk and automatically restored upon game exit, overlay dismissal, app crash, or device reboot.

---

## 2. Completed Architecture & Modules

### 2.1 Core Subsystems

| Module | Location | Description |
|--------|----------|-------------|
| **`TuneSpecs`** | `tune/TuneSpecs.kt` | Comprehensive definitions for all 36 options, 10 categories, control kinds (`SWITCH`, `SLIDER`, `ENUM`), units, and fallback defaults. |
| **`TuneCatalog`** | `tune/TuneCatalog.kt` | Sysfs node catalog mapping every `TuneId` to real hardware paths across Adreno, Mali, MediaTek, Samsung, and Generic Linux kernel interfaces. |
| **`ShellGateway`** | `fps/privilege/ShellGateway.kt` | Hardened privilege execution layer supporting Root (`su -c`) and Shizuku (`newProcess`) with regex validation, path canonicalization, process timeouts (120ms), and write verification. |
| **`TuneProbe`** | `tune/TuneProbe.kt` | Two-phase capability probing engine with wall-clock budget (3500ms max), candidate limits (16 max), and live write-verification before marking any node available. |
| **`TuneSessionManager`**| `tune/TuneSessionManager.kt` | Session lifecycle manager that applies verified user intents upon game launch, snapshots previous values to `tune_snapshot.json`, and coordinates recovery. |
| **`TuneRestoreReceiver`**| `tune/TuneRestoreReceiver.kt` | Broadcast receiver registered for `ACTION_BOOT_COMPLETED` and `ACTION_MY_PACKAGE_REPLACED` to guarantee restoration after unexpected reboot or upgrade. |
| **`TuneWatchdog`** | `tune/TuneWatchdog.kt` | Failsafe monitor ensuring parameters never remain locked if the foreground game exits abruptly. |
| **`TuneManager`** | `tune/TuneManager.kt` | Main entry point managing capability cache, user intents, dummy key migration/cleanup, and session execution. |

### 2.2 UI & UX Experience

- **Placement on Home Screen:** The **Game optimisation** card is positioned immediately above **RAM Free**, showing the live capability state on the current kernel.
- **Collapsible Category Cards:** All 10 category cards on `TuneScreen` are collapsible and collapsed by default, ensuring smooth 60/120fps scrolling and rapid rendering.
- **Supported Options Prioritized:** Categories with supported options and individual supported tweaks within categories are automatically sorted to the top.
- **Honest Capability Disclosures:** When nodes lack kernel support or require higher elevation (e.g. Root), they are legibly marked `Not supported on this kernel` or `Not supported (Needs Root backend)` with disabled interactive toggles.
- **Frosted Glass Aesthetic:** Standardized on `zenFrostChild` and `zenGlassBackground` with full insets support (`statusBarsPadding()`).

---

## 3. The 36-Option / 10-Category Catalog Implementation

| Category | Option Count | Implemented Option IDs |
|----------|:------------:|------------------------|
| **GPU** | 8 | `GPU_FLOOR`, `GPU_KEEPAWAKE`, `GPU_ADRENO_BOOST`, `GPU_GOVERNOR`, `GPU_POWER_FLOOR`, `GPU_MEDIATEK_MODE`, `GPU_SAMSUNG_MIN`, `GPU_SIMPLE_RAMP` |
| **CPU & Scheduling** | 8 | `CPU_MIN_ALL`, `CPU_MIN_LITTLE`, `CPU_MIN_BIG`, `CPU_MIN_PRIME`, `CPU_LEAVE_POWERSAVE`, `CPU_UCLAMP_MIN`, `CPU_SCHEDTUNE_BOOST`, `CPU_INTERACTIVE_HISPEED` |
| **Touch & Input** | 3 | `TOUCH_CPU_BOOST_MS`, `TOUCH_CPU_BOOST_FREQ`, `TOUCH_DEVFREQ_BOOST_MS` |
| **Thermal** | 3 | `THERMAL_XIAOMI_SCONFIG`, `THERMAL_CORE_CONTROL`, `THERMAL_VDD_RESTRICTION` |
| **Memory** | 4 | `MEM_DROP_CACHES`, `MEM_DIRTY_RATIO`, `MEM_SWAPPINESS`, `MEM_VFS_CACHE_PRESSURE` |
| **Storage I/O** | 2 | `IO_READAHEAD_KB`, `IO_SCHEDULER` |
| **Display** | 2 | `DISPLAY_PEAK`, `DISPLAY_MIUI` |
| **Focus** | 3 | `FOCUS_DND`, `FOCUS_HEADSUP`, `FOCUS_IMMERSIVE` |
| **Charging** | 1 | `CHARGING_BYPASS` |
| **Network** | 2 | `NET_TCP_CONGESTION`, `NET_WIFI_PM` |

---

## 4. Verification & Testing

### 4.1 Unit Test Suite
The complete unit test suite was executed via `./gradlew testDebugUnitTest`:

```
74 tests completed, 0 failed
BUILD SUCCESSFUL in 12s
```

Test coverage includes:
- `TuneProbeTimeoutTest`: Verifies bounded execution within the 3500ms wall budget.
- `TuneProbeSafetyTest`: Ensures non-whitelisted paths and deceptive inputs are rejected.
- `TuneSessionManagerTest`: Verifies atomic snapshot creation, application, and complete restoration.
- `TuneSnapshotStoreTest`: Tests JSON persistence, corrupt file recovery, and schema validity.
- `TuneEmptyCategoryHiddenTest`: Validates sorting of supported categories to the top and accurate support ratio counters.
- `ShellGatewayTest`: Verifies permission boundary checks, root vs. Shizuku dispatch, and timeout recovery.

### 4.2 Hardware Verification on Physical Device
- **Device:** Poco X6 Pro (`Y5WWBMJVOZSK4HU8`)
- **OS:** Android 16
- **Backend Verified:** Shizuku & Root
- **Telemetry Card:** Live CPU thermal and battery temperature streams rendered in real-time.
- **Probing Result:** Fast probing within budget; honest fallback badges displayed for read-only vendor nodes.
- **Lifecycle Testing:** Cold start, screen rotation, background/foreground transitions, and back-navigation verified with zero crashes and smooth animations.

---

## 5. Artifacts Created & Maintained

1. **Plans (Unmodified):**
   - `docs/plans/T12-real-game-optimisation.md`
   - `docs/plans/T12-tune-options.md`
2. **Results Document:**
   - `docs/plans/T12-real-game-optimisation-results.md`
3. **Release Notes & Fastlane Metadata:**
   - `docs/release-notes-v1.2.0.md`
   - `fastlane/metadata/android/en-US/changelogs/3.txt`
   - `fastlane/metadata/android/en-US/full_description.txt`
