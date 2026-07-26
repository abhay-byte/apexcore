# T8 — Redo manual game addition with complete app list selection

| Field | Value |
|-------|-------|
| **ID** | T8 |
| **Type** | feature |
| **Priority** | medium |
| **Difficulty** | medium |
| **Branch** | `T8-manual-game-addition` |
| **Status** | plan only (not implemented) |

## Todo source

```yaml
- id: T8
  title: Redo manual game addition with complete app list selection
  type: feature
  priority: medium
  difficulty: medium
  why: Current manual game addition is too basic; needs a picker that shows all installed apps.
  really_needed: yes
  impact: GameLauncher, Game selection UI/dialog, PackageManager querying
```

---

## Goal

Replace package-name typing with a full installed-app picker so the user can browse, search, and multi-select apps into the GAMES library (and remove them). Keep auto-detect scan as a secondary path. Leave launch/freeze/overlay paths unchanged.

---

## Research summary

### Current state (broken / incomplete)

| Piece | Status |
|-------|--------|
| `GameManager.add` / `remove` / `detect` / `acceptDetected` | Exists, works (SharedPrefs JSON) |
| Old `GameListDialog` (T4, commit `f8c28d0`) | **Deleted in T6** — was package-name `EditText` + ADD + SCAN |
| `GamesScreen` GAMES tab | Shows `gameManager.load()` carousel only |
| `GamesScreen` ALL APPS tab | Launches any app; **does not add to library** |
| Add / remove UI | **None** — `gameManager.add` is never called from Compose UI |
| Empty GAMES list | "NO ITEMS FOUND" only — no CTA |

### What was "too basic" (old `GameListDialog`)

- User typed package name by hand (`com.example.game`)
- No icon list, no search, no multi-select
- Easy to mistype; bad for non-dev users
- Scan only picked `CATEGORY_GAME` / `isGame` meta (misses many sideloaded / mis-categorized titles)

### What already exists to reuse

| Asset | Location / notes |
|-------|------------------|
| `getAllInstalledApps()` | `GamesScreen.kt` (~line 589) — filters system apps, sorts by label |
| `QUERY_ALL_PACKAGES` + `<queries>` MAIN | `AndroidManifest.xml` |
| `AppIcon`, theme tokens | `SurfaceCard`, `AccentPrimary`, `JetBrainsMono`, etc. |
| Compose dialog chrome | `SetupDialog.kt` — full-screen dim + card + scroll |
| Persistence API | `GameManager` — keep as-is for add/remove/load/save |
| Auto-detect | `GameManager.detect()` / `acceptDetected()` — CATEGORY_GAME + meta |

### Gap vs `docs/design.md` Page 2

Design covers GAMES / ALL APPS launch carousel and ALLOCATE & LAUNCH. **It does not define library management.** T8 fills how games enter the GAMES list.

### Relevant code map

| File | Role |
|------|------|
| `app/.../games/GamesScreen.kt` | Launch Matrix UI; has `getAllInstalledApps`, no add/remove |
| `app/.../games/GameManager.kt` | SharedPrefs library; add/remove/detect unused by UI |
| `app/.../games/GameInfo.kt` | `pkg`, `name`, `isAutoDetected` |
| `app/.../games/GameLauncher.kt` | Freeze + launch — **do not change** |
| `app/.../SetupDialog.kt` | Dialog pattern to mirror |
| History: `GameListDialog.kt` @ `f8c28d0` | Old Views-based UX (package EditText + scan) |

---

## Files to change

| Action | File |
|--------|------|
| **NEW** | `app/src/main/kotlin/com/ivarna/apexcore/games/AddGamePickerDialog.kt` |
| **MODIFY** | `app/src/main/kotlin/com/ivarna/apexcore/games/GamesScreen.kt` |
| **MODIFY** | `app/src/main/kotlin/com/ivarna/apexcore/games/GameManager.kt` |
| **OPTIONAL** | Unit test for `GameManager` add/dedupe if cheap; no new deps |

---

## Approach

### 1. App enumeration (single source of truth)

Move logic into `GameManager.listInstallableApps(context): List<GameInfo>`:

1. `getInstalledApplications` (API 33+ `ApplicationInfoFlags`, else deprecated path — same as `detect()`)
2. Exclude self package
3. Exclude pure system (`FLAG_SYSTEM` and not `FLAG_UPDATED_SYSTEM_APP`) — same as current `getAllInstalledApps`
4. **Only launchable:** keep if `pm.getLaunchIntentForPackage(pkg) != null` (drops services/providers users cannot open)
5. Sort by label; map → `GameInfo(pkg, label, isAutoDetected = false)`

`getAllInstalledApps` in `GamesScreen` becomes a thin wrapper or is deleted in favor of the manager method.

### 2. `AddGamePickerDialog` (Compose)

Mirror `SetupDialog` chrome:

- Full-screen dim (`SurfaceGlass`), card (`SurfaceCard` + `BorderGlass`)
- Header: `ADD TO LIBRARY` + short subtitle
- Search field (same style as GamesScreen search)
- `LazyColumn` rows: icon + name + package + checkbox
- Filter: search on name/pkg; **hide** apps already in library
- Footer: `CANCEL` \| `ADD N` (disabled if none selected)
- On confirm: `selected.forEach { gameManager.add(it.pkg, it.name, false) }`; callback `onAdded()` to refresh parent
- Load list on IO (`Dispatchers.IO`); spinner while loading
- **Multi-select** (checkbox) — decided

### 3. Wire into `GamesScreen`

- State: `showAddPicker`; refresh `customGames = gameManager.load()` after add/remove
- **Empty GAMES tab:** CTA `ADD GAMES` + secondary `SCAN FOR GAMES`
- **Non-empty:** small `+` control near search/toggle opens picker
- **Long-press GAMES card** → `gameManager.remove` + toast (no trash icon)
- **Long-press ALL APPS card** → `gameManager.add` + toast "Added to library"; if already in library → toast "Already in library"
- **Scan:** `detect` → `acceptDetected` auto-merge all; toast `Added N` (or "No new games found") — no preview step
- Picker = primary bulk path; ALL APPS long-press = quick single-add

### 4. Do not change

- `GameLauncher` freeze + launch path
- Carousel / ALLOCATE & LAUNCH behavior
- Overlay, freeze backends, permissions (already have `QUERY_ALL_PACKAGES`)

---

## Edge cases

| Case | Behavior |
|------|----------|
| Empty install list | Show "No launchable apps" |
| App already in library | Hidden from picker; long-press ALL APPS no-ops with toast; `add()` still dedupes |
| Package uninstalled after add | Keep in prefs; launch fails with existing toast; optional prune later |
| No launch intent | Excluded from picker |
| Huge app lists (200+) | `LazyColumn` + search; load once, cache in dialog state |
| Rotation | Dialog state via `remember`; list reload OK |
| System webview / keyboard | Filtered by launch-intent + system flags |
| Concurrent add | Single-thread prefs write; fine |

---

## Test plan

1. **Empty library** → ADD GAMES → multi-select 2 apps → library has 2; carousel works
2. **Search** → filter by name/package; select 1 → adds correctly
3. **Dedupe** → re-open picker; already-added not selectable / not double-added
4. **Long-press remove (GAMES)** → gone from library; reappears in picker
5. **Long-press add (ALL APPS)** → added to library; toast; second long-press no-ops / "Already in library"
6. **Scan** → CATEGORY_GAME apps appear; no dups
7. **ALLOCATE & LAUNCH** still freezes + launches after add
8. **ALL APPS** short-tap launch still works without adding
9. Device: build + install; logcat no crash on picker open

---

## Decisions (locked)

| # | Decision |
|---|----------|
| 1 | Multi-select in picker (checkbox + ADD N) |
| 2 | Long-press GAMES card → remove (no trash icon) |
| 3 | Long-press ALL APPS card → add to library |
| 4 | Already-in-library apps: **hide** from picker |
| 5 | SCAN: auto-accept all detected (no preview) |

---

## Out of scope (YAGNI)

- Per-game settings, cloud sync, icons in SharedPrefs
- Play Store policy rewrite for `QUERY_ALL_PACKAGES` (already declared)
- Redesign of carousel itself
- Prune uninstalled packages on load (follow-up)

---

## Implementation order (when approved)

1. `GameManager.listInstallableApps` (+ optional `addAll`)
2. `AddGamePickerDialog` composable
3. Wire empty CTA / `+` / long-press remove (GAMES) / long-press add (ALL APPS) / scan in `GamesScreen`
4. Manual test on device
5. PR targeting version branch with ID tag `[T8]`

---

## References

- Todo: `docs/todo/in-progress.md` (T8)
- Design: `docs/design.md` — Page 2 Launch Matrix
- Old UX: `GameListDialog.kt` @ commit `f8c28d0`
- Dialog pattern: `SetupDialog.kt`
