---
- id: T4
  title: Hail architecture — Shizuku/Root/Accessibility freeze framework
  type: feature
  priority: high
  difficulty: hard
  why: T2's killBackgroundProcesses is restricted on Android 12+ and only frees cached
    processes. Real deep-freeze needs pm disable / am force-stop which require elevated
    privileges. Hail, FreezeYou, SuperFreezZ are proven open-source references.
  really_needed: yes
  impact: New FreezeFramework module with three backends (Shizuku, Root, Accessibility).
    Replaces BoostManager's free-only path with freeze path. Adds FREEZE_ALL action that
    can be invoked from a button or `am start -a com.apexcore.app.action.FREEZE_ALL`.
    No UI rework — same BOOST button now triggers freeze-eligible path when privilege
    is granted.
  followups: T5 (per-app freeze list), T6 (whitelist/tags)
  images: null
  github_ref: null
  plan: |
    Goal: Implement Hail's "freeze" pattern in ApexCore. Replace T2's
    killBackgroundProcesses-only path with a backend-pluggable freeze
    framework. Same BOOST button, real deep-freeze on tap when a backend
    is granted.

    Files:
      NEW  app/src/main/kotlin/com/apexcore/app/freeze/FreezeFramework.kt
      NEW  app/src/main/kotlin/com/apexcore/app/freeze/FreezeBackend.kt
      NEW  app/src/main/kotlin/com/apexcore/app/freeze/ShizukuFreezeBackend.kt
      NEW  app/src/main/kotlin/com/apexcore/app/freeze/RootFreezeBackend.kt
      NEW  app/src/main/kotlin/com/apexcore/app/freeze/AccessibilityFreezeBackend.kt
      NEW  app/src/main/kotlin/com/apexcore/app/freeze/FallbackFreezeBackend.kt
      NEW  app/src/main/kotlin/com/apexcore/app/freeze/FreezeBackendResolver.kt
      NEW  app/src/main/kotlin/com/apexcore/app/freeze/FreezeOperation.kt
      NEW  app/src/main/kotlin/com/apexcore/app/freeze/FreezeResult.kt
      NEW  app/src/main/kotlin/com/apexcore/app/freeze/FreezeReceiver.kt
      MOD  app/src/main/kotlin/com/apexcore/app/MainActivity.kt
      MOD  app/src/main/AndroidManifest.xml
      MOD  app/build.gradle.kts
      NEW  app/src/main/res/xml/apexcore_a11y_service.xml

    Approach:
      1. Define FreezeOperation sealed class (ForceStop only in T4) + Result.
      2. Define FreezeBackend sealed interface (name, priority, isReady, execute).
      3. Implement four backends:
         - ShizukuFreezeBackend: ShizukuRemoteProcess wrapping "sh -c 'am force-stop $pkg'"
         - RootFreezeBackend:   Runtime.exec(arrayOf("su","-c","am force-stop $pkg"))
         - AccessibilityFreezeBackend: GlobalAction "RECENTS" + "Clear all" (no per-app)
         - FallbackFreezeBackend:      wraps BoostManager.kick() (T2 path, T4 default)
      4. FreezeBackendResolver.detect() probes in priority order, caches result.
      5. FreezeFramework.freezeAll() iterates non-system packages, runs ForceStop
         on each, collects counts + duration. Active backend as StateFlow.
      6. FreezeReceiver handles "com.apexcore.app.action.FREEZE_ALL" intent via
         goAsync() + coroutine, returns immediately.
      7. MainActivity: on BOOST tap, call FreezeFramework.freezeAll(); status
         footer shows backend name + killed count. Result panel adds MODE column.
      8. Manifest: register FreezeReceiver, add QUERY_ALL_PACKAGES, add a11y
         service config xml (optional in T4, defined for T5).
      9. Build with `./gradlew :app:assembleDebug`, install, manual test:
         - Pixel without root: should still work via fallback (T2 behavior)
         - Status footer shows "cached only"
         - With Shizuku (manual setup): footer shows "Shizuku", real apps die.

    Edge cases:
      - Shizuku binder dead mid-session -> resolver falls back, status flips
      - su present but denied -> RootFreezeBackend.isReady()=false, next backend tried
      - PackageManager crash -> isReady()=false, button shows "Unavailable"
      - 0 apps killed -> "Already optimized" copy unchanged
      - 100+ apps -> 5s timeout per backend.execute(), continue on timeout
      - Rotation during freeze -> lastResult is in FreezeFramework StateFlow,
        survives activity recreation

    Test plan:
      - Unit: FreezeBackendResolver ordering (Shizuku > Root > Accessibility > Fallback)
      - Unit: defaultFilter excludes system apps, self, already-stopped
      - Manual: Pixel no-root, click BOOST, confirm footer says "cached only",
        confirm BoostResult shape unchanged
      - Manual: Pixel + Shizuku (manual grant), click BOOST, confirm killed
        count > 0, freed MB > 100
      - Manual: `adb shell am start -a com.apexcore.app.action.FREEZE_ALL`
        from terminal triggers freeze
      - Build: `./gradlew :app:assembleDebug` passes
      - Install: `adb install -r app-debug.apk` succeeds
      - Logcat: no exceptions, no Shizuku NPEs

    Open questions:
      - Should AccessibilityFreezeBackend be wired in T4 or deferred to T5?
        T4 spec: stub backend with isReady()=false for now (no service file).
      - Should we add Shizuku dependency now (buildable without Shizuku app)
        or wait for T5 when Shizuku path actually fires? T4: add dep, ship
        ready code, run with fallback until user installs Shizuku.
  status: in-progress
  started_at: 2026-06-30
---
