# ApexCore v1.6 Release Notes

## Version Details
* **Version Name:** 1.6
* **Version Code:** 7
* **Target SDK:** 36 (Android 16 Ready)
* **Minimum SDK:** 24 (Android 7.0+)
* **Release Date:** September 1, 2026
* **AAB:** `app/build/outputs/bundle/release/app-release.aab` (signed with `keys/apexcore-release.jks`)

---

## Executive Summary

ApexCore 1.6 is a **Field Manual and launch-press polish** release: cleaner onboarding page turns, a corrected FIG.01 dial, and a theme-aware ALLOCATE & LAUNCH shutter that uses paper plates on Vellum instead of always rendering Graphite black.

---

## What's New in v1.6

### 1. Field Manual Transition Cleanup
* Per-page clipping and page-width parallax so adjacent spreads no longer bleed during pager turns.
* Previous-page elements no longer linger for ~100ms after a swipe.

### 2. FIG.01 Dial Needle
* Exploded dial needle draws from the hub (with counterweight tail and brass center), not as a stub outside the ring.
* Dashed alignment trail continues past the tip as the callout.

### 3. Theme-Aware Launch Shutter
* `ShutterOverlay` reads `ironSkin()` and applies a `ShutterPalette`: Bone/ink plates on Vellum, Anvil plates on Graphite.
* ALLOCATE & LAUNCH hydraulic press, status text, icon well, and seam ticks follow the active theme.

### 4. Version Bump
* `app/build.gradle.kts` `versionCode 6→7`, `versionName 1.5→1.6`; `MainScreen.kt` About fallback updated; Toolbox About shows **1.6**.

---

## Verification

* `./gradlew :app:bundleRelease` — release-signed AAB.
* About / Toolbox VERSION **1.6**.

---

## Upgrade

Install over previous builds (same applicationId / signing key). No migration required.
