# Review: T5 — Game Overlay Performance HUD

**Date:** 2026-07-02
**Branch:** `release/1.0.x`
**Verdict:** APPROVE

---

## Plan Adherence

- [x] Implement `GameOverlayService` (foreground service + window overlay)
- [x] Update `GameLauncher` to auto-start the overlay service
- [x] Declare `SYSTEM_ALERT_WINDOW` and foreground service permissions in manifest
- [x] Implement draggable pill interface for HUD
- [x] Implement expanded panel showing FPS, RAM usage, CPU load, and a BOOST button

---

## Build & Tests

| Check | Result |
|-------|--------|
| Build (`assembleRelease`) | ✅ PASS |
| Tests | ✅ PASS |

---

## Issues

### BLOCK

None.

### CHANGES_REQUESTED

1. **AndroidManifest.xml — Missing FGS property subtype for Android 14+**
   - Declaring `specialUse` foreground service requires a property specifying the subtype on Android 14+. Without it, the app will fail Play Store checks or runtime checks.
   - **Fix:** Add `android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE` property under the service tag. (Fixed)

2. **GameOverlayService.kt — Limited permission check in start()**
   - The check `Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU` causes the overlay permission check to be skipped on APIs 23–32, leading to runtime crashes.
   - **Fix:** Remove the SDK SDK_INT check and query `canDrawOverlays` unconditionally. (Fixed)

3. **GameOverlayService.kt — Crash on pre-Oreo devices (API 24/25)**
   - Unconditional use of `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY` causes immediate crash on Android 7.0/7.1.
   - **Fix:** Add API fallback to `TYPE_PHONE` for API < 26. (Fixed)

4. **GameOverlayService.kt — Premature service exit on Android 10+**
   - `isPackageOnTop` queries `am.appTasks` which only returns ApexCore tasks, always returning `false` for the game.
   - **Fix:** Query `UsageStatsManager` for the foreground package and fallback to `true` (staying alive) if not authorized/empty. (Fixed)

5. **GameOverlayService.kt — Potential crashes on WindowManager.removeView()**
   - Removing the view in `onDestroy()` and `shutdown()` without safety checks can throw `IllegalArgumentException` if already detached.
   - **Fix:** Wrap `wm.removeView` calls in a `try/catch` block. (Fixed)

---

## Fix Checklist

- [x] **1.** AndroidManifest.xml — Add `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` property to service
- [x] **2.** GameOverlayService.kt — Verify overlay permission unconditionally in `start()`
- [x] **3.** GameOverlayService.kt — Fallback to `TYPE_PHONE` on API < 26 in `createLayoutParams()`
- [x] **4.** GameOverlayService.kt — Query `UsageStatsManager` with `true` fallback in `isPackageOnTop()`
- [x] **5.** GameOverlayService.kt — Wrap `wm.removeView()` in `try/catch` in `onDestroy()` and `shutdown()`
- [x] **6.** Verification — Confirm tests pass and release APK builds successfully
