# ApexCore v1.3 Release Notes

## Version Details
* **Version Name:** 1.3
* **Version Code:** 4
* **Target SDK:** 36 (Android 16 Ready)
* **Minimum SDK:** 24 (Android 7.0+)
* **Release Date:** August 24, 2026
* **AAB:** `app/build/outputs/bundle/release/app-release.aab` (8.0 MB, signed with `keys/apexcore-release.jks`)

---

## Executive Summary

ApexCore 1.3 is a **performance and polish** update: 60fps Game Optimisation scroll, instant Pin navigation, Vellum theme correctness, and accurately rendered store icons.

---

## What's New in v1.3

### 1. TuningRoom 60fps Scroll
* Sorted categories hoisted with `remember`, `LazyColumn` now uses keyed `items` with `contentType` and `withShadow=false` plates to avoid shadow `RenderNode` allocation per frame.
* Fixed stutter source: `pull` was a `mutableFloatStateOf` causing recomposition on every scroll delta – replaced with plain `var` inside `NestedScrollConnection`.

### 2. Home → Games → Pin Navigation
* Pin sheet launch deferred 260ms after `GearTabTransition` to avoid triple composition (tab + sheet + PM scan).
* `GameManager.listInstallableApps` cached 4s, `AppIconCache` async loads `getApplicationIcon` off main thread, `LaunchMatrix` blur disabled and pager keys added.

### 3. Vellum Theme Correctness
* `EngravedPlate` adaptive to `ironSkin()`; `TuningRoom` categories use `PaperPlate` on Vellum so titles (`Ink900` on `Bone100`) are readable vs previous dark-on-dark.
* `BridgePlate` / `GearSelector` use white (`#FFFFFF`) on Vellum with dark text/glyphs; status/nav bars `isAppearanceLightStatusBars=true` for dark icons on white.

### 4. Accurate Icon PNGs
* `docs/storelisting/icon.png` (512), `icon_no_bg.png` (512 RGBA), `icon_1024.png`, `icon_256/192/144/96/72/48.png`, `app_icon_512/1024.png` regenerated from `ic_launcher_foreground.xml` + `ic_launcher_background.xml` via `rsvg-convert` with 25% zoom centered then +5.4 viewport shift right/top (≈10% plate width).

### 5. Version Bump
* `app/build.gradle.kts` `versionCode 3→4`, `versionName 1.2→1.3`; `MainScreen.kt` fallback updated; `Toolbox` now shows VERSION 1.3.

---

## Verification

* `./gradlew assembleDebug` — BUILD SUCCESSFUL
* `./gradlew testDebugUnitTest` — BUILD SUCCESSFUL
* `./gradlew bundleRelease` — BUILD SUCCESSFUL (8.0 MB AAB, `signReleaseBundle`)
* Manual: Home→Pin Apps→Games no stutter, TuningRoom scroll 60fps, Vellum titles readable, header/footer white, icons 512 verified `245,240,228` background.

---

## Upgrade

No data migration needed. Orphan snapshots still honored via `boot_id` tagged recovery.
