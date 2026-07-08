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
  plan: |
    Goal: Redesign the primary optimize action into a premium circular button with concentric glow rings and active sweep animations.
    Approach:
    1. Define circular BoostButton (180.dp diameter) with Cyan-to-Sky gradient, bold label, spring scaling press, and idle breathing animation.
    2. Define GlowRings concentric backdrop behind the button with phase-shifted breathing animations.
    3. Define SweepRing overlay active during BOOSTING rotating 0° to 360° over 1.2s.
    4. Replace MainActionCard with these elements in HomeScreen.
---
