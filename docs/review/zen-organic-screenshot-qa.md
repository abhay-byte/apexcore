# Zen Organic — Device Screenshot QA

| Field | Value |
|-------|-------|
| **Date** | 2026-08-03 |
| **Branch** | `execute-plan/3b165d73-zen-organic-full` |
| **Device** | CPH2691 (`d30a1726`), 1264×2780 |
| **APK** | release `com.ivarna.apexcore` |
| **Screenshots** | `docs/review/zen-screenshots/` |

## Scope

Full-page capture of every primary surface after Zen Organic install. Findings below drive the fix list.

## Screens captured

| # | Screen | File | Status |
|---|--------|------|--------|
| 1 | Home (Boost) | `01_home.png` | **Fail** — leaf metrics unreadable |
| 1b | Home scrolled (diagnostics + privacy) | `01b_home_scrolled.png` | Pass (privacy + diagnostics OK) |
| 2 | Games library | `02_games.png` | Pass |
| 3 | Overlay settings | `03_overlay.png` | Pass |
| 4 | RAM Free | `04_ram_free.png` | Pass |
| 4b | RAM Free (scrolled) | `04b_ram_free_scrolled.png` | Pass |
| 5 | Pin Apps dialog | `05_pin_apps.png` | Pass |
| 6 | Backend dropdown | `06_backend_dropdown.png` | Pass (plain M3 menu — minor) |
| 7 | Add to Library dialog | `07_add_games.png` | Pass |
| 8 | Games · ALL APPS | `08_games_all_apps.png` | Pass |
| 9 | Pin from Games | `09_pin_from_games.png` | Pass |
| 10 | Setup dialog | `10_setup_dialog.png` | **Skipped** — both backends Ready (dialog gated) |
| 11 | Overlay START active | `11_overlay_hud_test.png` | Pass (service running; thin left rail) |

---

## Findings

### F1 — CRITICAL: Memory leaf labels/values collide

**Where:** Home hero (`MemoryLeafPair`)

**Evidence:** `01_home.png`, `01b_home_scrolled.png`, `06_backend_dropdown.png`

**What:** RAM and SWAP leaf graphics overlap by design (168dp teardrop + 112dp diamond offset). Each `MemoryLeaf` also draws **label + MB text under the leaf** inside the same 220×220 `Box`. The two metric stacks paint on top of each other → unreadable `RAMSWAP` / mangled MB figures.

**Root cause:** `MemoryLeaf.kt` `MemoryLeafPair` nests full `MemoryLeaf` columns (shape **and** metrics) in a fixed-size overlapping `Box`. Spec allows overlapping **shapes** only; metrics must remain legible under each leaf.

**Fix:**
1. Add `showMetrics: Boolean = true` to `MemoryLeaf`.
2. In `MemoryLeafPair`, render leaf shapes only inside the art `Box` (`showMetrics = false`).
3. Place a clear **metrics row under the pair**: left column RAM (primary/teal), right column SWAP (tertiary/earth) — label bold + MB bold, no overlap.
4. Ensure pair height includes art + metrics + optional purge chip without clipping.

---

### F2 — MEDIUM: Privacy Policy below the fold on Home

**Where:** Home bottom

**Evidence:** Not visible on `01_home.png`; visible after scroll `01b_home_scrolled.png`

**What:** Compliance C1 link works and is styled correctly, but first viewport is dominated by diagnostics. Acceptable for v1 if scroll is natural; optional polish is to tighten diagnostics padding or collapse diagnostics so privacy is nearer the fold.

**Fix (v1):** Keep as-is unless product wants fold priority — **no code change required** for ship; note for store screenshot framing.

---

### F3 — LOW: Backend dropdown is stock Material3

**Where:** Top-bar SHIZUKU chip menu

**Evidence:** `06_backend_dropdown.png`

**What:** Menu works (Shizuku/Root + Ready). Visual is plain white `DropdownMenu`, not solid-glass card. Functional OK.

**Fix (optional):** Apply `surfaceContainerLowest` + outline already partially set; raise elevation/radius to match glass cards. **Defer** unless quick.

---

### F4 — INFO: Setup dialog not auto-captured

**Why:** Device has Root + Shizuku both Ready; auto-show requires `backend == null` and `setup_shown_v1 = false`. Dialog code path reviewed earlier in implement/review; restyle already landed in PR stack.

**Follow-up:** Manual open via non-elevated state if regression suspected; not blocking F1.

---

### F5 — INFO: In-game HUD is a thin left rail

**Evidence:** `dumpsys` → overlay window `(0,603)(43×384)` APPLICATION_OVERLAY; START→disabled / STOP→active on `11_overlay_hud_test.png`.

**What:** Matches KD-13 contrast-first rail (not full light card). Not a Zen regression.

---

## Pass checklist (non-leaf)

| Area | Result |
|------|--------|
| Light Zen background (mint wash) | Pass |
| Logo + “Apex Core” Light primary top bar | Pass |
| Floating glass island bottom nav (~90% width) | Pass |
| Nav labels Boost / Games / Overlay | Pass |
| Material vectors only (no ASCII → ← ●) | Pass |
| Pebble Purge Engine card | Pass |
| Zen entry rows RAM Free / Pin Apps | Pass |
| Access diagnostics ACTIVE + StatusPebble | Pass |
| Privacy Policy underline (scrolled) | Pass |
| Games search, tabs, card, ALLOCATE & LAUNCH | Pass |
| Overlay permission + START/STOP | Pass |
| RAM Free gauge, cards, Free RAM CTA, ArrowBack | Pass |
| Pin / Add dialogs glass + DONE/CANCEL | Pass |

---

## Fix plan (ordered)

1. **[Must]** F1 — restructure `MemoryLeafPair` metrics so RAM/SWAP never overlap.
2. **[Verify]** Rebuild release APK, reinstall, re-screenshot Home (+ dropdown).
3. **[Skip v1]** F2, F3 polish unless time after F1.

## Acceptance for F1

- Home screenshot shows **two distinct** metric columns: `RAM` + `NNNN MB` and `SWAP` + `NNNN MB`.
- No characters from one label drawn through the other.
- Leaf shapes still form the asymmetric overlapping pair.
- Purge animation chip (if shown) still appears under metrics without collision.

---

## Post-fix re-verify

**Build:** `./build-and-install.sh release` → Success on `d30a1726`  
**Code:** `MemoryLeaf.kt` — leaf art in overlap `Box` with `showMetrics = false`; distinct `LeafMetricLabels` row under pair.

| # | Screen | File | Result |
|---|--------|------|--------|
| 12 | Home (F1 fix) | `12_home_fixed.png` | **Pass** — `RAM 4358 MB` / `SWAP 1090 MB` side-by-side, no collision |
| 12b | Home scrolled | `12b_home_scrolled_fixed.png` | **Pass** — metrics + privacy + diagnostics |
| 13 | Games recheck | `13_games_recheck.png` | Pass |
| 14 | Overlay recheck | `14_overlay_recheck.png` | Pass |
| 15 | RAM Free recheck | `15_ramfree_recheck.png` | Pass |

### F1 acceptance

- [x] Two distinct metric columns (RAM + MB, SWAP + MB)
- [x] No characters from one label drawn through the other
- [x] Leaf shapes still form asymmetric overlapping pair
- [x] Status / Purge Engine / entries layout intact below

### Summary

| Finding | Severity | Status |
|---------|----------|--------|
| F1 MemoryLeafPair metric collision | Critical | **Fixed** |
| F2 Privacy below fold | Medium | Deferred (scroll OK) |
| F3 Plain backend dropdown | Low | Deferred |
| F4 Setup dialog not auto-captured | Info | N/A on elevated device |
| F5 HUD thin rail | Info | By design (KD-13) |

**Ship readiness (UI QA):** Home hero metrics fixed; remaining items are polish/info only.

---

## Polish pass (2026-08-03 later)

| Fix | Detail | Status |
|-----|--------|--------|
| Bottom nav labels | Icon-only; labels only as `contentDescription` | **Done** |
| Games PIN affordance | Removed cramped 7sp `PIN` under icon; icon-only like Add | **Done** |
| Home bottom spacer | 88dp → 24dp (nav is Column-layout, not overlay) | **Done** |
| Backend dropdown | Rounded 16dp solid-glass menu + outline | **Done** |
| Overlay bottom inset | Extra breathing room above nav | **Done** |

**Verify shots:** `20_home_polish.png`, `20b_home_scrolled_polish.png`, `21_games_polish.png`, `22_overlay_polish.png`, `23_dropdown_polish.png`

---

## Full UI correctness + dark theme (2026-08-03)

### Overlap
| Issue | Fix | Status |
|-------|-----|--------|
| Bottom nav mid-card clip | Nav is true floating overlay; screens use `ZenDimens.bottomNavClearance` (88dp) | **Fixed** |
| Leaf metrics collision | Metrics outside art box (prior F1) | **Fixed** |
| Cramped PIN label | Icon-only | **Fixed** |

### Contrast
| Issue | Fix | Status |
|-------|-----|--------|
| Washed secondary text (0.72 alpha) | Full `onSurfaceVariant` for body/status/labels | **Fixed** |
| Inactive nav icons weak | Full `onSurfaceVariant`; active = `primary` pill + `onPrimary` icon | **Fixed** |
| SHIZUKU chip low contrast | `primaryContainer` fill + `onPrimaryContainer` text | **Fixed** |
| Purge / Launch gradients | Solid (or near-solid) `primary` + `onPrimary` text | **Fixed** |
| Glass cards too transparent | Fill alpha 0.97 | **Fixed** |

### Dark theme
| Item | Detail | Status |
|------|--------|--------|
| `ZenDarkColorScheme` | Deep teal ink surfaces, mint primary, warm secondary | **Shipped** |
| `LocalZenSemantics` | Theme-aware leaf/status colors | **Shipped** |
| `ApexCoreTheme` | Follows `isSystemInDarkTheme()`; status/nav bar icons flip | **Shipped** |
| `values-night/themes.xml` | Dark splash / window background | **Shipped** |

**Final verify shots:** `final_light_home.png`, `final_light_home_scrolled.png`, `final_dark_home.png`, `final_dark_games.png`, `final_dark_overlay.png`

Toggle on device: system Dark mode (Settings → Display), or `adb shell cmd uimode night yes|no`.
