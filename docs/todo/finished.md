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
---
