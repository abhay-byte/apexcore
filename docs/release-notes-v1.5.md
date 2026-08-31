# ApexCore v1.5 Release Notes

## Version Details
* **Version Name:** 1.5
* **Version Code:** 6
* **Target SDK:** 36 (Android 16 Ready)
* **Minimum SDK:** 24 (Android 7.0+)
* **Release Date:** August 31, 2026
* **AAB:** `app/build/outputs/bundle/release/app-release.aab` (signed with `keys/apexcore-release.jks`)

---

## Executive Summary

ApexCore 1.5 is an **Iron UI, brand, and theme reliability** release: T13 Tuning Room custom controls, Graphite/Vellum official marks, crash-safe theme switching, and SYSTEM finish that correctly follows device dark mode.

---

## What's New in v1.5

### 1. T13 Iron Tuning Room Controls
* Replaced Material3 sliders with Canvas `IronSlider` ruler controls per `docs/design/new_design.md`.
* Themed select menus and dialogs; WCAG-minded contrast on Graphite and Vellum.
* Max Perf section scrolls with Game Mode (no longer pinned over the list).
* APPLY shows a loading needle while max-perf applies; disabled APPLY is muted when no game is selected.

### 2. Official Graphite / Vellum Brand
* Theme-aware launcher aliases (`LauncherGraphite` / `LauncherVellum`), splash, and in-app chrome via `ThemeBrand` / `ApexBrandIcon`.
* Graphite (dark) icon refreshed from the new plate art; storelisting, fastlane, mipmaps, and drawables updated.
* MK·II removed from the top bar wordmark.

### 3. Theme Reliability
* Changing theme no longer tears down the activity: launcher-alias swaps are deferred to `onStop` with enable-first / delayed-disable.
* `ThemeMode.SYSTEM` resolves night mode from `applicationContext` so a prior Vellum configuration wrap cannot keep SYSTEM on light when the device is dark.

### 4. Version Bump
* `app/build.gradle.kts` `versionCode 5→6`, `versionName 1.4→1.5`; `MainScreen.kt` About fallback updated; Toolbox About shows **1.5**.

---

## Verification

* `./gradlew :app:assembleDebug` / `:app:bundleRelease` — BUILD SUCCESSFUL (release-signed AAB).
* Device: realme X2 Pro (`2a580689`) — theme SYSTEM after Vellum cold-start → Graphite on night mode; rapid SYSTEM/VELLUM/GRAPHITE switches keep process alive; About VERSION **1.5**.

---

## Upgrade

Install over previous builds (same applicationId / signing key). No migration required.
