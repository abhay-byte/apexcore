---
- id: T9
  title: RAM Filler ("RAM Free") system to force-free memory
  type: feature
  priority: high
  difficulty: hard
  status: done
  why: Filling the phone's RAM to 100% forces Android's LMK (Low Memory Killer) to reclaim all cached background processes before the filler process terminates itself.
  really_needed: yes
  impact: New RamFillerManager, memory allocation service/loop, UI controls
  followups: null
  images: null
  github_ref: null
  plan: docs/plan/T9-ram-filler.md
---
