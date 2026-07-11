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
    Goal: Redesign the primary optimize action into a premium 3D rectangular cyber button with tactile physical press feel, metallic base lip, and custom vector lightning badge.
    Approach:
    1. Define wide horizontal 3D card (`148.dp` height, `RoundedCornerShape(32.dp)` matching UnifiedResultCard and SystemDiagnosticsCard).
    2. Implement physical 3D base lip (`offset(y = 8.dp)`) with dark cyber metallic gradient (`Color(0xFF0F172A)` to `Color(0xFF020617)`).
    3. Implement tactile front face that depresses `6.dp` downward when pressed (`buttonOffsetY`), compressing into the base lip.
    4. Add custom vector 3D lightning bolt (`Path`) inside right-hand badge container (`RoundedCornerShape(22.dp)`).
    5. Add active cyber glow sweep along perimeter during `BOOSTING` state.
---
