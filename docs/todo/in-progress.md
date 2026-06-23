---
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
    5. No new permissions — killBackgroundProcesses only needs
       android.permission.KILL_BACKGROUND_PROCESSES (normal, granted at install)
    Edge cases:
    - Boot just completed -> low numbers, OK
    - No apps to kill -> "Already optimized"
    - system_server binding fails -> catch, show error
    - Rotation during BOOSTING -> save state
    Test plan: manual tap on d30a1726, verify "Freed X MB" displays,
    APK builds + installs + launches, no crashes in logcat.
---
