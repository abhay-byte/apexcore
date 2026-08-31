# ApexCore v1.4 Release Notes

## Version Details
* **Version Name:** 1.4
* **Version Code:** 5
* **Target SDK:** 36 (Android 16 Ready)
* **Minimum SDK:** 24 (Android 7.0+)
* **Release Date:** August 26, 2026
* **AAB:** `app/build/outputs/bundle/release/app-release.aab` (signed with `keys/apexcore-release.jks`)
* **APK:** `app/build/outputs/apk/debug/app-debug.apk` / `app-release.apk`

---

## Executive Summary

ApexCore 1.4 is a **FPS accuracy and performance** update: correct SurfaceFlinger presentation cadence, Standard-mode Choreographer fallback verified on 90Hz (realme X2 Pro) and 120Hz (OPD2403EEA), lower sampling overhead, and fail-closed privilege handling — with no fabricated FPS.

---

## What's New in v1.4

### 1. Correct SurfaceFlinger Presentation Cadence
* `SurfaceFlingerFpsDataSource` now uses the **middle column `actualPresentTime`** (`desired == fields[0]`, `actual == fields[1]`, `ready == fields[2]`) per Chromium `surface_stats_collector.py` / factualstats, not `column_3 - column_1`.
* Filters sentinel `0`, `Long.MAX_VALUE`, negative, non-monotonic and `>9e15` timestamps; derives intervals `actualPresentTimes.zipWithNext { next - prev }` filtered `1M..500M ns`; recent window `32` → `fps = 1e9 / avgRecentInterval` clamped `1..240` with snap-to-refresh only when `refresh >=90Hz` within 10%.
* Jank via `periods = ((deltaNs + refreshPeriodNs/2)/refreshPeriodNs).coerceAtLeast(1)`, `missedPeriods = (periods-1).coerceAtLeast(0)`.

### 2. Standard-Mode Choreographer Fallback (90Hz Verified)
* New `ChoreographerFpsDataSource` (priority 4) — `Choreographer.FrameCallback` with `frameTimesNs` filtered `1M..500M`, recent 32 avg, `sourceDetail="CHR:vsync|refresh"`, `STALE_MS=6000`.
* `FpsRepository` routing: `GAME: SF only` (never `gfxinfo`/DMA — Vulkan/SurfaceView fake), `UI: DMA_FENCE → SF → GFXINFO → CHOREOGRAPHER`. Standard-mode `dumpsys SurfaceFlinger --list` permission failure now correctly falls back to `CHR` showing `89.6 FPS` on realme X2 Pro (90Hz) and `120 FPS` on OPD2403EEA.
* Removed 60Hz hard ceiling — `resolveDisplayFps()` now trusts source `currentFps.coerceIn(1f,240f)`; refresh used only for jank, not cap.

### 3. Lower Overhead & Monotonic Timing
* `CachedSurface(packageName, layerName, resolvedAtMs)` with `surfaceCacheTtlMs=30_000`, `listCacheTtlMs=5000`, `findCandidateLayers()` ordered preferred then `#` suffix iterating until `parseLatency` succeeds; `maxEmptyBeforeInvalidate=3` / `maxFailureBeforeInvalidate=3`.
* Separate cadence: `GameOverlayService` `FPS_MEASUREMENT_PERIOD_MS=850` vs `HUD_REDRAW_PERIOD_MS=350` with `elapsedRealtime()` compensation `delay((TARGET - elapsed).coerceAtLeast(80))`; `latestFpsSnapshot` shared, both jobs cancelled on `shutdown()`.
* All staleness/TTLs use `SystemClock.elapsedRealtime()` (`ForegroundAppResolver`, `DmaFence`, `Gfxinfo`, `FpsRepository.lastGoodAtElapsedMs`).

### 4. Privilege-Aware Fail-Closed
* `FpsRepository.setTargetPackage(packageName)` before first sample + clear on teardown; `onPrivilegeModeChanged()` clears `lastGoodSnapshot/lastGoodAtMs/lastSourceKey/lastBatchFingerprint/recentDisplayFps` and `DMA/SF/GFX/choreographer` caches + `cachedPid/foreground`.
* `FpsStack.syncPreferredBackend` stops daemon on `SHIZUKU/STANDARD`; `PrivilegeModeStore` listener wired.

### 5. Provenance & Smoothing
* `FpsSnapshot` extended `measuredAtElapsedMs`, `sourceDetail`, `sampleAgeMs`; `isFresh()` prefers `elapsedRealtime`; diagnostics log `target/pkg/isGame/vendor/method/tier/src/layer/raw/beforeSmooth/afterSmooth/intervals/age`.
* Current FPS (`snapshot.currentFps`) separate from `fpsHistory` percentiles; `frametimeBuffers` for `DmaFence` only; median-of-3 smoothing applied to display FPS only, not history.

### 6. Version Bump
* `app/build.gradle.kts` `versionCode 4→5`, `versionName 1.3→1.4`; `MainScreen.kt` fallback updated; Toolbox displays 1.4.

---

## Verification

* `./gradlew :app:testDebugUnitTest --tests "com.ivarna.apexcore.fps.*"` — PASS (SurfaceFlingerFpsDataSourceTest 19, FpsRepositoryTest 7, DmaFenceFpsDataSourceTest 3, ForegroundAppResolverTest 4)
* `./gradlew :app:assembleDebug` / `bundleRelease` — BUILD SUCCESSFUL
* Device: `RMX1931 realme X2 Pro (SM8150 Adreno 640, Android 16, 90Hz)` — `logcat GameOverlayService` `method=CHOREOGRAPHER tier=STANDARD surface=CHR:vsync fps=89.6 age=1ms` steady; `OPD2403EEA pineaple Adreno (Android 16, 120Hz)` — `fps=120.0` vs prior `63.0` clamp bug; `overlay Window 31x446 → 204x446` on tap verified.
* No fabricated FPS: `--` when `fps<=0`, stale >6s rejected, negative/sentinel intervals ignored.

---

## Upgrade

No data migration needed. Orphan snapshots still honored via `boot_id` tagged recovery. Users on Standard mode will now see accurate 60/90/120Hz via Choreographer where `SurfaceFlinger --list` is permission-gated.
