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
    can be invoked from a button or `am start -a com.ivarna.apexcore.action.FREEZE_ALL`.
    No UI rework — same BOOST button now triggers freeze-eligible path when privilege
    is granted.
  followups: T5 (per-app freeze list), T6 (whitelist/tags)
  images: null
  github_ref: null
  plan: null
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
  plan: null
---
