---
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
  plan: null
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
- id: T8
  title: Redo manual game addition with complete app list selection
  type: feature
  priority: medium
  difficulty: medium
  why: Current manual game addition is too basic; needs a picker that shows all installed apps.
  really_needed: yes
  impact: GameLauncher, Game selection UI/dialog, PackageManager querying
  followups: null
  images: null
  github_ref: null
  plan: null
- id: T9
  title: RAM Filler ("RAM Free") system to force-free memory
  type: feature
  priority: high
  difficulty: hard
  why: Filling the phone's RAM to 100% forces Android's LMK (Low Memory Killer) to reclaim all cached background processes before the filler process terminates itself.
  really_needed: yes
  impact: New RamFillerManager, memory allocation service/loop, UI controls
  followups: null
  images: null
  github_ref: null
  plan: null
---
