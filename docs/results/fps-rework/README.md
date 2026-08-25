# FPS Rework — ApexCore

| | |
|---|---|
| **Date** | 2026-08-25 |
| **Branch** | `main` (fps-rework) |
| **Commit** | `HEAD` (see git log) |
| **Scope** | `app/src/main/kotlin/com/ivarna/apexcore/fps/` + overlay + launcher + tests |

## Summary

Production-grade FPS measurement rework that fixes the SurfaceFlinger timeline math, separates instantaneous FPS from long-window percentiles, eliminates redundant `dumpsys` spam, tracks the launched game package (not transient focus), handles privilege changes fail-closed, and exposes full provenance/diagnostics.

## What Changed

### 1. `SurfaceFlingerFpsDataSource.kt`
- **Presentation-timeline model:** uses `actualPresentTime` (middle column, `fields[1]`) as the presentation clock. Old code used `column3 - column1` per row (`frameReady - desiredPresent`) and averaged that — a queue-latency, not presentation cadence.
- **Robust sentinel filtering:** zero, `Long.MAX_VALUE`, negative, `MIN_VALUE`, `>9e15` (far beyond boottime), plus per-row `desired==0` / `MAX` checks. One malformed row no longer poisons the sample.
- **Interval derivation:** `intervalsNs = actualPresentTimes.zipWithNext { prev, next -> next-prev }.filter{>0}` then `fps = 1e9 / avg(recentIntervalsNs)` (equivalent to `1e9*count/(last-first)` for stable timelines).
- **Recent window = 32** (≈0.5 s @60 Hz, ≈0.25 s @120 Hz) for responsiveness; recent window is not dominated by the 128-frame ring.
- **Jank:** missed presentation periods `periods = ((deltaNs + refresh/2)/refresh).coerceAtLeast(1)`, `missed = periods-1`, summed. Tiny jitter does not count.
- **Refresh sanity:** 1 ms … 100 ms, clamped `1..240` with `*1.05` grace, snaps to refresh when within 10 % and low variance.
- **Cache:** `CachedSurface(pkg, layer, elapsedMs)` with 30 s TTL, plus 5 s `--list` cache. `findSurfaceForPackage()` reuses while working; invalidates on `--latency` failure, consecutive empty (`≥3`) or consecutive failure (`≥2`), package change, or explicit `clearCache()`. Uses `elapsedRealtime()` monotonic.
- **Monotonic:** `measuredAtElapsedMs = SystemClock.elapsedRealtime()`, `diagnostics` includes `presents/intervals/recent avgMs fpsRaw refresh missed tier`, `sourceDetail="SF:<layer>"`.

### 2. `FpsSnapshot.kt`
Extended with provenance without churn:
```kotlin
measuredAtElapsedMs: Long  // elapsedRealtime for isFresh/age
sourceDetail: String?       // SF:layer / DMA:cmdbatch_inflight / GFX:framestats
sampleAgeMs: Long           // elapsedNow - measuredAtElapsedMs
```
`isFresh()` now prefers elapsed, falls back to wall-clock. `ZERO` updated.

### 3. `DmaFenceFpsDataSource.kt`
- Stale uses `elapsedRealtime()` (`STALE_MS=6000`), returns `sampleAgeMs`.
- `sourceDetail` mapping (`DMA:cmdbatch_inflight`, `DMA:syncpoint`, …).
- `clearCache()` already existed, now also clears `lastReceivedAtElapsedMs`.

### 4. `GfxinfoFpsDataSource.kt`
- Added `clearCache()` and `sourceDetail` (`GFX:framestats`/`histogram`/`gpuHistogram`), `measuredAtElapsedMs`.

### 5. `FpsRepository.kt` — conservative routing + monotonic + separation
- **Game routing:** `GAME: SurfaceFlinger only`. Never DMA for games (even Adreno) and never `gfxinfo` for games without hardware proof. Previous GPU-aware DMA for Adreno games removed to honor *Keep Game Routing Conservative*.
- **UI routing:** `DMA → SF → GFX` with idle-undercount heuristic (`<5 FPS` prefers GFX if higher).
- **Current FPS ownership:** `resolveDisplayFps()` trusts source `currentFps` (cap only), does **not** recompute from `frametimeHistogram`/`FrametimeBuffer`. Buffer is for `p1/p01` only.
- **Smoothing:** median-of-3 (kill single-sample spike without EMA lag).
- **Last-good:** monotonic `lastGoodAtElapsedMs`, `isTierAllowed()` fail-closed, age computed via `elapsedRealtime()`, held copy marked `isStale + sampleAgeMs`, drops on tier not allowed or `>4000 ms`.
- **Target package:** `setTargetPackage()` now clears held state, foreground caches, SF/Gfx caches when package changes; also clears on `null` (service teardown). Prevents holding old game's 60 FPS after switching games.
- **Privilege:** `onPrivilegeModeChanged()` clears last-good, buffers, `recentDisplayFps`, DMA/SF/Gfx caches, `cachedGpuVendor`, sets `lastDiagnostics`.
- **Diagnostics:** `target pkg isGame vendor method tier src layer raw beforeSmooth afterSmooth intervals age srcDiag buf STALE`.

### 6. `ForegroundAppResolver.kt`
- Preferred-package fast path still avoids `dumpsys window` while overlay active.
- All TTLs now monotonic (`elapsedRealtime()`): foreground 1200 ms, pid 2000 ms, refresh 5000 ms, game-like verdict 30 s.
- `setTargetPackage()` / `clearTargetPackage()` invalidate pid + foreground caches.

### 7. `GameOverlayService.kt`
- **Target wired:** `onStartCommand` sets `repository.setTargetPackage(gamePkg)` before first sample; on different pkg restarts telemetry; `shutdown()` clears target.
- **Scopes:** reuses `telemetryScope`; `onDefrost` now `telemetryScope.launch` (no ad-hoc scope per click).
- **Cadence split:** `FPS_MEASUREMENT_PERIOD_MS=850` (within 750-1000) for FPS probe, `HUD_REDRAW_PERIOD_MS=350` (within 250-500) for `RailView` push. Both use elapsed compensation:
  ```kotlin
  val loopStart = elapsedRealtime()
  ...
  delay((TARGET - (elapsedRealtime()-loopStart)).coerceAtLeast(MIN_DELAY_MS))
  ```
  Prevents drift `500 + executionTime`. No expensive FPS probe just because UI wants smoother redraw.
- **No double clamp:** overlay no longer `rawFps.coerceAtMost(refreshHz)`; trusts repository's already-capped `currentFps`. Shows `0 → "--"` when `method==NONE`.
- **Diagnostics:** logs `target method tier surface fps age diag`.

### 8. `FpsStack.kt`
- `onModeChanged` now `repo.onPrivilegeModeChanged()` (which already clears DMA/SF/Gfx); extra `dma.clearCache()` redundant retained for safety. Stops daemon on `SHIZUKU|STANDARD`.

### 9. `RailView.kt`
- Renders `"--"` instead of `"0"` when `fps<=0`, matching `-- FPS` unavailable semantics.

### 10. `GameLauncher.kt`
- Already wired `setTargetPackage(gamePkg)` immediately after `startActivity` (prior commit, retained).

## Why Old SF Math Was Wrong

Old: `frameDuration = column3 - column1` per row (`frameReady - desiredPresent`), then `FPS = 1000 / avg(frameDuration)`.
That measures how long a single buffer sat in the queue, not how often the display actually presents. A game could have 16 ms queue latency every frame yet present at 30 Hz if it misses vsync — old math would still report 60. SurfaceFlinger latency rows are a timeline; true FPS is the cadence of `actualPresentTime` across rows:
```
fps = 1e9 / avg(nextActual - prevActual)
```
or `1e9 * count / (last - first)` for a stable interval.

## New FPS Formula

```kotlin
val intervalsNs = actualPresentTimes.zipWithNext { a,b -> b-a }.filter { it in 1_000_000..500_000_000 }
val recent = intervalsNs.takeLast(32)
val avgNs = recent.average() // ns
val fps = 1_000_000_000.0 / avgNs
// jank
val periods = ((deltaNs + refreshPeriodNs/2)/refreshPeriodNs).coerceAtLeast(1)
val missed = (periods-1).coerceAtLeast(0)
```
`refreshPeriodNs` from line 0, validated `1_000_000..100_000_000`. Jank is missed vsync periods, not jitter.

## Fallback Order

```
GAME (pkg isGameLike || targetPackage!=null):
  SurfaceFlinger (present deltas) → unavailable (--)

NON-GAME / UI:
  DMA (broadcast, no dumpsys, 6 s stale) → SF → GFX (framestats/histogram)
  DMA <5 FPS idle-undercount may prefer higher GFX
```
`gfxinfo` never for games/packages containing `SurfaceView|NativeActivity|Vulkan|GLSurfaceView|Unity|Unreal|Cocos` or known benchmark prefixes.

## Sampling Cadence

- **Measurement:** 850 ms ± execution, monotonic, `MIN_DELAY 80 ms`.
- **HUD redraw:** 350 ms ± execution, reuses `latestFpsSnapshot` (no extra dumpsys).
- Typical minute: `~70` SF `--latency` calls (was `~120` at 500 ms) plus `--list` only on cache miss (was every sample). `--list` cache 5 s, surface cache 30 s with failure-driven invalidation.

## Surface Cache Behaviour

```
initial:  SF --list → identify target render surface (prefer SurfaceView/NativeActivity/Vulkan/BLAST/GLSurfaceView/#)
then:     SF --latency <cached> repeatedly
re-list only on: target change | latency failure (≥2) | empty usable timestamps (≥3) | layer disappeared | TTL 30 s | privilege change
```

## Privilege Invalidation (fail-closed)

`FpsRepository.onPrivilegeModeChanged()` (also `clearCacheForTest()`) clears:
```
lastGoodSnapshot, lastGoodAtElapsedMs, lastSourceKey, lastBatchFingerprint,
recentDisplayFps, frametimeBuffers, DMA cache, SF cache (surface+list), GFX cache, cachedGpuVendor
```
`DmaFenceFpsDataSource.clearCache()` and `SurfaceFlingerFpsDataSource.clearCache()` / `GfxinfoFpsDataSource.clearCache()` are idempotent. Held root sample is not shown after switching to Shizuku/Standard (`isTierAllowed` check + age).

## Tests Run

```
./gradlew :app:testDebugUnitTest --tests "com.ivarna.apexcore.fps.*"
```
- `SurfaceFlingerFpsDataSourceTest` (19): 30/60/90/120/144 synthetic, 60↔30 transitions, duplicate/zero/decreasing/sentinel/only-refresh/one-usable/mixed-valid, Android-15 layer parsing, recent-window responsiveness, jank missed periods, extreme refresh, timeline formula equivalence — **all pass**.
- `FpsRepositoryTest` (7): game not gfx, UI fallback, source FPS not overwritten, median suppress spike, median persistent change, target change clears, privilege change clears, source change resets — **all pass**.
- `DmaFenceFpsDataSourceTest` (3): stale rejected, clearCache, fresh age/sourceDetail — **all pass**.
- `ForegroundAppResolverTest` (4): preferred wins, target change resolves new pid, clear returns to system, game-like cache — **all pass**.
- Full suite: 147 tests, 1 pre-existing failure `TuneBackendDropRestoresTest.testBackendDropRestoresSession` (fails on HEAD as well, unrelated to FPS; see known limitations). Build `assembleDebug` succeeds.

## Hardware Validation

*No physical device available in CI for this patch.*

```
Algorithm/unit-test implementation complete.
Physical FPS accuracy validation still pending.
```

Recommended matrix when hardware is available:

| Device | Android | GPU | Refresh | Game A (30 cap) | Game B (60 cap) | Bench variable |
|---|---|---|---|---|---|---|
| e.g. Snapdragon 8g3, Android 15 | 15 | Adreno 750 | 60/90/120/144 | raw/display vs in-game | raw/display vs benchmark counter | vsync |

Record: expected/reference, Apex raw, Apex display, method, tier, layer, refresh, cadence. Test stable 30/60, transition, loading screen, shade, overlay interaction, game restart/switch, privilege switch. Capture `dumpsys SurfaceFlinger --latency` raw for offline replay vs parser.

## Before / After

| Metric | Before (HEAD) | After |
|---|---|---|
| SF FPS math | `C-A` avg per row | `actualPresent` consecutive deltas |
| SF column | `frameComplete` (C) | `actualPresent` (B, chromium/collector) |
| Jank | `ceil((C-A)/period)` delta between rows? | `(delta+period/2)/period -1` per interval |
| Invalid handling | 0 / `<=frameStart` / `>2000 ms` only | + `MAX`, negative, `>9e15`, desired 0, vsync MAX |
| Refresh check | `<=0` only | `1 ms..100 ms` + ceiling |
| Cache | `--list` every sample | `--list` 5 s, surface 30 s, failure-driven |
| Calls/min (stable) | `--list` ~120, `--latency` ~120 | `--list` ~0-1, `--latency` ~70 |
| Thread | was handler+runBlocking on main (historical) → IO scope 700 ms fixed | IO measurement 850 + UI 350, both compensated, IO background / Main for push |
| Double clamp | repo + overlay both `coerceAtMost(refresh)` | repo only (overlay trusts) |
| Privilege | last-good held across mode switch | cleared, tier-checked, monotonic |
| Provenance | none / partial | `method tier sourceDetail surface age intervals raw/smoothed` |
| Unavailable | `0` or `NONE` but overlay showed `0` | `NONE` with `--` rendering, held stale marked |
| Game DMA | skipped (good) vs Adreno hybrid (speculative) | kept conservative: SF only |
| Tests | ~0 for SF | 33 deterministic fps tests |

## Known Limitations / Follow-ups

- **Hardware proof pending** — Adreno `cmdbatch_inflight` hybrid remains disabled for games until side-by-side vs in-game counter validates <2 % error on real devices. Re-enable behind `GpuVendor==ADRENO` guard only with evidence.
- **Pre-existing test failure:** `TuneBackendDropRestoresTest.testBackendDropRestoresSession` fails on `main` independently of FPS changes; not caused by this patch.
- **Refresh fallback:** when `foreground.refreshRateHz` unknown, uses `60 Hz` → clamp may be wrong on 90/120 devices for a few seconds until `WindowManager.display.refreshRate` or `dumpsys display` supplies real rate. Mitigated by `coerceIn(1..240)` and `*1.05` grace.
- **DMA vsync inflation on Mali:** preserved — DMA never for games on non-Adreno. If Mali ever proves accurate, gate similarly.
- **Last-good hold 4 s:** kept but monotonic; consider 2.5 s if user testing shows stale hold too long.
- **Gfxinfo throttling 2 s + 3 empty cap:** retained; no change.
- **No Choreographer path** in this rework; SF remains primary for games.

## Files Changed

```
app/src/main/kotlin/com/ivarna/apexcore/fps/FpsRepository.kt
app/src/main/kotlin/com/ivarna/apexcore/fps/FpsStack.kt
app/src/main/kotlin/com/ivarna/apexcore/fps/model/FpsSnapshot.kt
app/src/main/kotlin/com/ivarna/apexcore/fps/source/DmaFenceFpsDataSource.kt
app/src/main/kotlin/com/ivarna/apexcore/fps/source/GfxinfoFpsDataSource.kt
app/src/main/kotlin/com/ivarna/apexcore/fps/source/SurfaceFlingerFpsDataSource.kt
app/src/main/kotlin/com/ivarna/apexcore/fps/util/ForegroundAppResolver.kt
app/src/main/kotlin/com/ivarna/apexcore/games/GameOverlayService.kt
app/src/main/kotlin/com/ivarna/apexcore/games/RailView.kt
app/src/test/java/com/ivarna/apexcore/fps/source/SurfaceFlingerFpsDataSourceTest.kt  (new)
app/src/test/java/com/ivarna/apexcore/fps/FpsRepositoryTest.kt                          (new)
app/src/test/java/com/ivarna/apexcore/fps/source/DmaFenceFpsDataSourceTest.kt          (new)
app/src/test/java/com/ivarna/apexcore/fps/util/ForegroundAppResolverTest.kt            (new)
docs/results/fps-rework/README.md                                                        (new)
```

## How to Re-run Validation

```bash
./gradlew :app:testDebugUnitTest --tests "com.ivarna.apexcore.fps.*"
./gradlew :app:assembleDebug
adb shell dumpsys SurfaceFlinger --list | grep <pkg>
adb shell dumpsys SurfaceFlinger --latency <layer> > /tmp/sf.txt
# replay sf.txt through SurfaceFlingerFpsDataSource.parseLatency() in a unit test
```

## References

- `factualstats` `core/data/.../FpsRepository.kt`, `SurfaceFlingerFpsDataSource.kt`, `DmaFenceFpsDataSource.kt`, `OverlayService.kt`, `docs/fps-measurement.md`, `docs/T1-snapdragon-in-game-fps-handoff.md`
- Chromium `surface_stats_collector.py` — middle column is presentation timestamp, `pending_fence_timestamp = (1<<63)-1`
- `platform/frameworks/native/.../FrameTracker.cpp` — `desired, actualPresent, frameReady` + fence signal times
