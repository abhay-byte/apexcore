---
- id: T3
  title: Storage analyzer with tab navigation
  type: feature
  priority: high
  difficulty: medium
  why: Real perf killer — full storage degrades app launch + game load
  really_needed: unknown
  impact: New tab navigation, new StorageManager, app list + category breakdown
  followups: null
  images: null
  github_ref: null
  plan: |
    Goal: Add STORAGE tab alongside BOOST tab. Bottom pill tab bar.
    Storage tab shows: total/used/free with bar, category breakdown,
    top 10 apps by size. Pull-to-refresh re-scans.
    Files: NEW StorageManager.kt; MODIFY MainActivity.kt
    Approach:
    1. StorageManager.scan(ctx) returns StorageReport via StatFs + per-app size
    2. MainActivity: bottom tab bar with 2 pills, ViewFlipper for content
    3. Custom donut View for usage
    4. No new permissions; sourceDir size fallback for <API 26
    Edge cases: <API 26 fallback, no permission, no ext storage, 0 apps
    Test: switch tabs, sort apps desc, verify usage bar %
    Open: tab style bottom vs top (bottom); MediaStore (defer);
          delete per row (defer).
---
