# ApexCore Release Notes

---

## Version 1.6 (Build 7) — Field Manual & Launch Polish

**Release Date:** September 1, 2026  
**Target SDK:** 36 (Android 16 Ready)  
**Min SDK:** 24 (Android 7.0+)

### Highlights

Version 1.6 cleans Field Manual page-turn bleed, fixes the FIG.01 dial needle so it sits on the hub, and themes the ALLOCATE & LAUNCH hydraulic press for Vellum (paper) as well as Graphite (anvil).

### Key Improvements

#### 1. Field Manual Transitions
* Per-page clip and page-width parallax; no lingering previous-spread elements during pager turns.

#### 2. FIG.01 Dial
* Hub-centered needle with counterweight and dashed exploded callout trail.

#### 3. Theme-Aware Launch Shutter
* `ShutterOverlay` `ShutterPalette` from `ironSkin()` — Bone/ink on Vellum, Anvil on Graphite.

#### 4. Version Bump
* `versionCode 6→7`, `versionName 1.5→1.6` in `app/build.gradle.kts` and About fallback; toolbox displays 1.6.

See `docs/release-notes-v1.6.md` and `fastlane/metadata/android/en-US/changelogs/7.txt`.

---

## Version 1.5 (Build 6) — Iron UI, Brand & Theme Reliability

**Release Date:** August 31, 2026  
**Target SDK:** 36 (Android 16 Ready)  
**Min SDK:** 24 (Android 7.0+)

### Highlights

Version 1.5 ships T13 Iron Tuning Room controls (custom ruler slider, themed select/dialogs), official Graphite/Vellum brand marks, a refreshed dark Graphite icon, crash-safe theme switching, and SYSTEM finish that follows real device dark mode.

### Key Improvements

#### 1. T13 Iron Tuning Room
* Canvas `IronSlider` (no Material3 sliders), themed select/dialogs, contrast fixes.
* Max Perf scrolls with the list; APPLY loading + muted disabled state when no game is selected.

#### 2. Brand & Icons
* Theme-aware launcher/splash/chrome Graphite↔Vellum marks; refreshed Graphite plate art across mipmaps, storelisting, and fastlane.

#### 3. Theme Reliability
* Deferred launcher-alias sync avoids activity teardown on theme change.
* SYSTEM reads night mode from `applicationContext` (not a Vellum-wrapped activity config).

#### 4. Version Bump
* `versionCode 5→6`, `versionName 1.4→1.5` in `app/build.gradle.kts` and About fallback; toolbox displays 1.5.

See `docs/release-notes-v1.5.md` and `fastlane/metadata/android/en-US/changelogs/6.txt`.

---

## Version 1.4 (Build 5) — FPS Accuracy & Performance

**Release Date:** August 26, 2026  
**Target SDK:** 36 (Android 16 Ready)  
**Min SDK:** 24 (Android 7.0+)

### Highlights

Version 1.4 is an FPS accuracy and performance release: correct SurfaceFlinger presentation cadence, Standard-mode Choreographer fallback verified on 90Hz (realme X2 Pro) and 120Hz (OPD2403EEA), lower sampling overhead, and fail-closed privilege handling with no fabricated FPS.

---

### Key Improvements

#### 1. Correct SurfaceFlinger Cadence
* Uses middle column `actualPresentTime` (`desired/actual/ready`) per Chromium `surface_stats_collector.py`; filters `0`/`MAX`/negative/non-monotonic/`>9e15`, intervals `1M..500M ns`, recent `32` → `fps=1e9/avg` clamped `1..240`; jank via `periods=((delta+period/2)/period)`.

#### 2. Standard-Mode Choreographer Fallback
* New `ChoreographerFpsDataSource` (priority 4, `STALE_MS=6000`) with `frameTimesNs` 32-avg; routing `GAME: SF only`, `UI: DMA→SF→GFX→CHR`; Standard `dumpsys --list` permission failure now shows `CHR:vsync 89.6 FPS` on 90Hz and `120 FPS` on 120Hz (removed 60Hz ceiling, `resolveDisplayFps` trusts source).

#### 3. Lower Overhead & Monotonic Timing
* `CachedSurface` `30s` + `listCache 5s`, `findCandidateLayers()` iterating `#` suffix until `parseLatency` succeeds; `850ms` sampling vs `350ms` HUD with `elapsedRealtime()` compensation; all TTLs monotonic.

#### 4. Fail-Closed Privilege
* `setTargetPackage()` before first sample, `onPrivilegeModeChanged()` clears `lastGood/lastSource/recentDisplayFps`/DMA+SF+GFX+CHR caches; `syncPreferredBackend` stops daemon on `SHIZUKU/STANDARD`.

#### 5. Version Bump
* `versionCode 4→5`, `versionName 1.3→1.4` in `app/build.gradle.kts` and fallback in `MainScreen.kt`; toolbox displays 1.4.

---

## Version 1.3 (Build 4) — Performance & Polish

**Release Date:** August 24, 2026  
**Target SDK:** 36 (Android 16 Ready)  
**Min SDK:** 24 (Android 7.0+)

### Highlights

Version 1.3 is a performance and polish release focused on 60fps UI, theme correctness, and accurate store assets.

---

### Key Improvements

#### 1. TuningRoom 60fps Scroll
* Hoisted category sorting out of `LazyColumn`, switched to keyed `items` with `contentType`, disabled shadow `RenderNode` for list plates, and removed `mutableState` write on every scroll delta in `NestedScrollConnection` – eliminates jank when scrolling Game Optimisation.

#### 2. Home → Games → Pin Navigation
* Deferred Pin sheet 260ms after `GearTabTransition`, cached `PackageManager` scan 4s in `GameManager`, async `AppIconCache` for `getApplicationIcon`, disabled `RenderEffect` blur in Games carousel, optimized `LazyColumn` keys in sheets.

#### 3. Vellum Theme Correctness
* `EngravedPlate` now adaptive (`skin.plate`/`hairline`), `TuningRoom` uses `PaperPlate` on Vellum for readable dark-on-light titles.
* `BridgePlate` and `GearSelector` use white (`#FFFFFF`) on Vellum with dark glyphs/text for contrast; `Paper` status/nav bars white with dark icons (`isAppearanceLightStatusBars=true`).

#### 4. Accurate Icon PNGs
* Regenerated `docs/storelisting/icon*.png` and `app_icon_*.png` (512/1024/256/192/144/96/72/48) accurately from `ic_launcher_foreground.xml` + `ic_launcher_background.xml` via `rsvg-convert`, 25% zoom centered then +5.4 viewport shift right/top.

#### 5. Version Bump
* `versionCode 3→4`, `versionName 1.2→1.3` in `app/build.gradle.kts` and fallback in `MainScreen.kt`; toolbox displays 1.3.

---

## Version 1.2 (Build 3) — Real Game Optimisation Engine

**Release Date:** August 18, 2026  
**Target SDK:** 36 (Android 16 Ready)  
**Min SDK:** 24 (Android 7.0+)

### Highlights

Version 1.2 replaces legacy placeholder switches with **T12 Real Game Optimisation**: a capability-gated, session-scoped, reversible kernel & Android system tuning architecture.

---

### Key Features & Improvements

#### 1. 36 Capability-Gated Tuning Options Across 10 Categories
ApexCore now supports a comprehensive clean-room catalog of 36 kernel sysfs and system setting options:
* **GPU (4 options):** GPU Minimum Frequency Floor, GPU Governor Performance, Adreno Bus Scaling Boost, Adreno Force Idle Off.
* **CPU (4 options):** CPU Floor Frequency Boost, CPU Governor Ramp-Up, CPU Energy Model Favor Performance, CPU Performance Sched Core.
* **Input (2 options):** Touch Polling Rate Boost, Touchscreen Sensitivity / Game Mode.
* **Thermal (2 options):** Thermal Game Profile, Thermal Sustained Performance Mode *(Never disables hardware thermal throttling)*.
* **Memory (4 options):** Compaction Proactive Aggressive, Swappiness Tuning, Dirty Background Ratio Reduction, Drop Caches Page Cache Reclaim.
* **I/O (2 options):** Block I/O Read-Ahead Scaling, UFS High-Performance Mode.
* **Display (4 options):** Force Peak Refresh Rate, Lock Screen Resolution / Window Scale, Disable Hardware Overlays (HW Comps), HDR Brightness Peak Boost.
* **Focus & DND (4 options):** Gaming Do Not Disturb, Heads-up Notification Suppression, Window Animation Scale Boost, Keep Screen Awake / Inactivity Timeout.
* **Charging & Battery (3 options):** Gaming Bypass Charging, Battery Saver Lockout, Fast Charge Current Cap.
* **Network & Connectivity (7 options):** Wi-Fi Low Latency Game Mode, Wi-Fi Power Save Disable, Wi-Fi Packet Pacing Boost, Wi-Fi Band Steering / 5 GHz Prefer, Cellular NR SA Prefer, Bluetooth Low Latency Audio (aptX Adaptive / LHDC), TCP Congestion Control (BBR/Cubic).

#### 2. Capability-First Hardware Probing
* **Safe On-Device Probing:** Probes kernel sysfs nodes on startup with a strict 3.5s total budget and 120ms per-node timeout.
* **Write Verification:** Sysfs nodes are only marked available if real test-writes and reads succeed; read-only or unsupported nodes are marked unavailable.
* **Honest Disabled States:** The UI clearly explains why an option is unavailable on the current kernel (e.g., `"Requires root on this kernel"`, `"Not supported by hardware/kernel"`).

#### 3. Session-Scoped & 100% Reversible Tuning
* **Snapshot & Rollback:** Before any sysfs node is modified, ApexCore captures the original value and saves it to a persistent snapshot tagged with `boot_id`.
* **Clean Session Teardown:** All kernel and system settings automatically revert to their pre-game state the moment the game session exits.
* **Reboot/Crash Recovery:** If the device reboots or the app is killed mid-game, orphaned snapshots are safely rolled back on the next launch or boot.

#### 4. Dual-Watchdog Reversion Architecture
* **Primary (Overlay HUD):** Tracks active game window focus and initiates instant teardown when the user exits the game.
* **Fallback (UsageStats Watchdog):** If Draw Over Other Apps permission is not granted, a background watchdog polls top-activity every 5s with a fail-safe restore policy.

#### 5. User Interface & Experience
* **New Game Optimisation Screen:** Full categorized settings UI with live search, per-option explanations, and capability status chips.
* **Live Kernel Readiness Badge:** Home screen displays exact kernel availability (e.g., `"14 available on this kernel"` or `"None on this kernel"`).
* **Thermal Protection Disclosure:** Clear, permanent disclosure emphasizing that thermal protections remain active at all times.

#### 6. Platform & Security Fixes
* **Android 14–16 Shizuku Compatibility:** Added `<queries>` declarations for `moe.shizuku.privileged.api` and cleaned up provider permissions, ensuring instant binder handshakes.
* **Google Play Policy Compliance:** Resolved deceptive behavior concerns by replacing placeholder toggles with genuine, capability-verified logic.

---

## Version 1.1 (Build 2) — Organic Zen Redesign

- Redesigned visual aesthetics with frosted glass blur (Haze), leaf tanks, and organic accents.
- Floating draggable performance HUD overlay for live FPS, RAM, and CPU telemetry.
- RAM Free memory reclaim feature with 90% allocation cap.
- App pinning to protect crucial background apps from freeze purges.

---

## Version 1.0 (Build 1) — Initial Architecture

- Core deep freeze framework using Shizuku (wireless debugging) and direct Root access.
- Local game library launcher.
- On-device local privacy policy (no analytics, no tracking, no internet telemetry).
