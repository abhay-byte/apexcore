# HUD Overlay FPS / Permission / Crash Fix — Single-File Plan

**ID:** HUD-FIX-2026-08-24
**Type:** bugfix (3 issues + 1 crash)
**Status:** Implemented & verified on device `2a580689` (realme X2 Pro, Android 16)
**Commit:** `bc2d6ee` (pushed to `main`)
**Files:** 6 changed, `183 + / 35 -` — `FpsRepository.kt`, `FpsSnapshot.kt`, `GameOverlayService.kt`, `RailView.kt`, `OpticsBench.kt`, `MainScreen.kt`

---

## 1. Issues (from screenshot + logcat)

1. **Permission guard missing** — `OpticsBench` showed `START OVERLAY` enabled even when `!canDrawOverlays`. Logs: `GameOverlayService.start()` returns `false` when `!canDrawOverlays`, but UI left `previewRunning=true` and toggle stayed on.
2. **FPS > refresh** — preview + real overlay showed `134 FPS` on 60/90 Hz panel. `FpsRepository.resolveDisplayFps()` capped non-DMA only; `DMA_FENCE` returned `coerceIn(1,240)` ignoring `refreshCeiling`. Preview used `118+(0..26)` unconditionally. `RailView.push()`/`GameOverlayService` had no second-level cap.
3. **No method label** — overlay showed `FPS` only; user asked for compact abbreviation of method/fallback (`SF`/`DMA`/`GFX`/`CHR`).
4. **Crash on size change + restart** — `IllegalArgumentException: View=…RailView… not attached to window manager` at `GameOverlayService.resizeWindow():151` → `RailView.setExpanded():109` → `autoMinimize` Runnable. `RailView` posts `autoMinimize` 5 s after expand; `shutdown()` called `wm.removeView(rail)` but never cleared `Handler` callback. Pending runnable then calls `wm.updateViewLayout(detachedView)` → FATAL. Also `telemetryScope.cancel()` made scope unusable for reused service instance; `moveWindow`/`snapWindow` lacked guards.

Evidence: `logcat --pid=26061/26596` both FATAL at `GameOverlayService.kt:151` / `RailView.kt:109-100`.

---

## 2. Fix (smallest correct change)

### Permission guard
- `OpticsBench.kt`: `MachinedToggle(enabled = permissionGranted)`, `START enabled = permissionGranted && !previewRunning`, helper text when `!granted`; `PhantomRailPreview` now takes `fpsMethod`.
- `MainScreen.kt`: initial `overlayPreview = isRunning && granted`; continuous poll `while(true){ granted=canDrawOverlays; running=isRunning; desired=running&&granted; }` 1 s; `onTogglePreview(true)` guards with `if(!canDrawOverlays){toast; return}` and respects `started` return value.

### FPS cap (never > display Hz)
- `FpsSnapshot.kt`: `fun FpsMethod.abbrev()` → `DMA`/`SF`/`GFX`/`CHR`/`--`.
- `FpsRepository.kt:186`: compute `refreshCeiling` first, then cap **all** including `DMA_FENCE` → `coerceIn(1,refreshCeiling)`.
- `RailView.kt`: new `fpsMethod`, `push(..., method)`, `displayRefreshHz()` via `display?.refreshRate` / `WindowManager.defaultDisplay`; setter + `push` `coerceAtMost(refresh)`; `onDraw` adds 6.5 sp abbrev when `curW>40`, `sy = fpsY+28`.
- `GameOverlayService.kt`: `displayRefreshHz()` helper, `startTelemetry` caps `rawFps.coerceAtMost(refreshHz)` and forwards `method` to `rail.push`.
- `OpticsBench.kt`: `previewRefreshHz()` + `simulated = (ceiling - 0..18).coerceIn(1,ceiling)` so preview never exceeds Hz; cycles demo `SF/DMA/GFX`.

### Method label
- `RailView.kt:184` / `OpticsBench.kt:309` draw abbrev under `FPS` (Iron.Bone500 7 sp) when `!=--`.

### Crash guard
- `RailView.kt:102`: `setExpanded` only `performHaptic`/`onExpand` if `isAttachedToWindow` with `try/catch`; `notifyInteraction` only posts if attached; `onDetachedFromWindow()` clears `autoMinimize` + `expandAnim.cancel()`; `cancelPendingAnimations()` exposed.
- `GameOverlayService.kt:149`: `resizeWindow`/`moveWindow`/`snapWindow` early-return if `!::rail.isInitialized` or `parent==null` or `!isAttachedToWindow`, `try/catch` `updateViewLayout`; `snapWindow` listener guarded; `shutdown()` calls `rail.cancelPendingAnimations()`, no longer `telemetryScope.cancel()` (only `telemetryJob.cancel()`), wraps `stopForeground`/`stopSelf`.

---

## 3. Verification

- Build: `./gradlew :app:assembleRelease` → BUILD SUCCESSFUL (2/49, 1m46s), `app-release.apk` 8.4 M.
- Device `2a580689`: `appops set SYSTEM_ALERT_WINDOW allow` → `OPTICS` flips `GRANTED` within 1 s poll; toggle disabled when `!granted` (issue 1 fixed).
- Overlay start → `b0bdc86` window `frame=[Rect(0,876-32)]` collapsed, tap → `151` (S) expanded; FPS capped to `60` on this panel (never 134) for both real overlay and preview; abbrev visible.
- Stress: `S→M→L` size switches + `STOP`/`START` rapid + 6 s auto-minimize collapse → `pid 6897` survived, `logcat --pid=6897` zero `FATAL`; previous pids `26061/26596` no longer reproduce.
- Unit: `./gradlew :app:testDebugUnitTest` → BUILD SUCCESSFUL.
- Git: `bc2d6ee` → `origin/main` after `pull --rebase`.

---

## 4. Limitations / Follow-ups

- `ChamferButton` disabled still looks orange (clickable disabled but bg unchanged) — visual polish optional.
- Preview `fpsMethod` demo cycles randomly; real fallback (`HOLD`) not shown — could add `H` suffix when `lastGoodSnapshot` used.
- `MachinedToggle` green/grey background vs circle position may desync if `enabled` flips mid-animation — mitigated by `isAttachedToWindow` guard.

---

## 5. Pull

```bash
git pull origin main   # single file: docs/plans/hud-overlay-fps-permission-crash-fix-plan.md
git log --oneline -1   # bc2d6ee
```
