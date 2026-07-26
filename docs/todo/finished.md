---
- id: T1
  title: Scaffold ApexCore Android skeleton + dummy build
  type: feature
  priority: high
  difficulty: easy
  why: Establish repo + working build pipeline before adding game-boost features
  really_needed: Yes — foundation for all future work
  impact: project structure, gradle, CI foundation
  followups: T2
  images: null
  github_ref: null
  plan: null
  status: done
  completed_at: 2026-06-23
- id: T2
  title: One-tap Boost mode
  type: feature
  priority: high
  difficulty: easy
  why: User demand — expected marquee feature of any game-boost app
  really_needed: unknown
  impact: Main activity UI button + boost animation
  followups: null
  images: null
  github_ref: null
  plan: |
    Goal: Single-tap "BOOST" button on main screen. 1.5s animated
    spinner, kills cached background processes via ActivityManager,
    displays freed memory count, returns to idle.
    Files: NEW BoostManager.kt; MODIFY MainActivity.kt
    Approach:
    1. BoostManager.kick(ctx) reads /proc/meminfo before/after, calls
       ActivityManager.killBackgroundProcesses() on user apps, returns
       BoostResult(freedMb, beforeMb, afterMb)
    2. MainActivity adds big circular "BOOST" button below subtitle
    3. On tap: button -> spinner overlay (1.5s via coroutine delay),
       then result text "Freed 312 MB" + Done button
    4. State machine: IDLE -> BOOSTING -> RESULT -> IDLE
    5. No new permissions - killBackgroundProcesses only needs
       android.permission.KILL_BACKGROUND_PROCESSES (normal, granted at install)
    Edge cases:
    - Boot just completed -> low numbers, OK
    - No apps to kill -> "Already optimized"
    - system_server binding fails -> catch, show error
    - Rotation during BOOSTING -> save state
    Test plan: manual tap on d30a1726, verify "Freed X MB" displays,
    APK builds + installs + launches, no crashes in logcat.
  status: done
  completed_at: 2026-06-23
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
    can be invoked from a button or `am start -a com.ivarna.apexcore.action.FREEZE_ALL`.
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
  status: done
  completed_at: 2026-07-02
- id: T5
  title: Game overlay — draggable performance HUD during gameplay
  type: feature
  priority: medium
  difficulty: hard
  why: Users want real-time performance data while gaming — FPS, memory pressure,
    CPU load — and a quick way to freeze background apps without leaving the game.
  really_needed: yes
  impact: New GameOverlayService (foreground service + WindowManager overlay).
    Adds SYSTEM_ALERT_WINDOW permission. Updates GameLauncher to auto-start overlay.
    Draggable pill with expanded panel showing FPS, RAM, CPU, BOOST button.
  followups: Per-game overlay settings, custom FPS target, CPU governor switching
  images: null
  github_ref: null
  plan: |
    Goal: Implement GameOverlayService (foreground service + WindowManager overlay)
    and integrate with GameLauncher.
    Approach:
    1. Implement GameOverlayService with SYSTEM_ALERT_WINDOW permission.
    2. Add draggable pill that expands into a performance HUD displaying FPS, RAM, CPU, and a BOOST button.
    3. Update GameLauncher to auto-start the overlay service.
  status: done
  completed_at: 2026-07-02
- id: T6
  title: Optimize system button UI redesign
  type: feature
  priority: medium
  difficulty: easy
  why: Current optimize system button design does not look premium / matches standard styles.
  really_needed: yes
  impact: MainActivity UI layout and theme styling
  followups: null
  images: null
  github_ref: null
  plan: |
    Goal: Redesign the primary optimize action into a premium 3D rectangular cyber button with tactile physical press feel, metallic base lip, and custom vector lightning badge.
    Approach:
    1. Define wide horizontal 3D card (`148.dp` height, `RoundedCornerShape(32.dp)` matching UnifiedResultCard and SystemDiagnosticsCard).
    2. Implement physical 3D base lip (`offset(y = 8.dp)`) with dark cyber metallic gradient (`Color(0xFF0F172A)` to `Color(0xFF020617)`).
    3. Implement tactile front face that depresses `6.dp` downward when pressed (`buttonOffsetY`), compressing into the base lip.
    4. Add custom vector 3D lightning bolt (`Path`) inside right-hand badge container (`RoundedCornerShape(22.dp)`).
    5. Add active cyber glow sweep along perimeter during `BOOSTING` state.
  status: done
  completed_at: 2026-07-12
- id: T7
  title: RAM & Swap real-time display and post-optimization results
  type: feature
  priority: high
  difficulty: medium
  why: Users need to see real-time RAM/Swap usage in MB and the exact RAM+Swap freed post-optimization.
  really_needed: yes
  impact: MainActivity Composables, memory metrics provider
  followups: null
  images: null
  github_ref: null
  plan: null
  status: done
  completed_at: 2026-07-26
---

