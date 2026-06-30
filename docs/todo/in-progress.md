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
  plan: null
  status: in-progress
  started_at: 2026-06-30
---
