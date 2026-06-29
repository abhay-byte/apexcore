# ApexCore — UI/UX Page Map

> Companion to `docs/design.md`. Defines every screen, its route, its role, and its relationship to the others. Built around the **Summit** design language and the current state of the codebase (T1 ✅, T2 ✅, T3 next).

---

## 1. Scope

**In scope now (post-T3):**
- Home (Boost) tab — exists, gets reused
- Storage tab — T3 introduces this
- Storage → App Detail — drill-down from Storage
- Settings — minimum viable, makes the app feel finished

**Explicitly out of scope (reserved for future versions):**
- Onboarding / first-run
- Login / account
- Per-game profiles
- Network analyzer, battery analyzer, temperature monitor
- Push notifications beyond Android system

**Why this shape:** T3 forces a tab container. Once tabs exist, every existing and future feature lives behind one. This doc defines the *minimum* tab set that feels complete without overbuilding.

---

## 2. Navigation Map

```
                          ┌────────────────────┐
                          │   Splash (transient) │
                          └─────────┬──────────┘
                                    │ auto-route
                                    ▼
                          ┌────────────────────┐
                          │   Main (TabHost)    │◄──── persistent bottom bar
                          └──┬─────┬─────┬─────┘
              ┌───────────────┤     │     ├───────────────┐
              ▼               ▼     │     ▼               ▼
       ┌──────────┐   ┌──────────┐  │  ┌──────────┐  ┌──────────┐
       │  Home    │   │ Storage  │  │  │(reserved)│  │ Settings │
       │  (Boost) │   │   Tab    │  │  │  future  │  │          │
       └────┬─────┘   └────┬─────┘  │  └──────────┘  └──────────┘
            │              │       │
            │              ▼       │
            │       ┌──────────┐  │
            │       │  App     │  │  (push: full-screen, hides tab bar)
            │       │  Detail  │  │
            │       └──────────┘  │
            │                     │
            ▼                     │
       (Result panel — inline on  │
        Home, no navigation)      │
                                  │
                  ◄───────────────┘
                    (back returns to last tab)
```

### Navigation rules
1. **Tab bar is persistent** on Home, Storage, and Settings. It is hidden on App Detail (drill-down takes the full screen).
2. **Splash** is not a tab and not back-stack-able. It is a launch transition only.
3. **Boost** is an *action*, not a route. Its result appears inline on Home.
4. **Future tab slot** (3rd tab) is reserved but hidden in v0.2. This keeps the bar at 3 tabs which is the Android Material sweet spot, and reserves the 4th for Settings which behaves differently (no scrollable content).
5. **Hardware back** on App Detail returns to Storage. Hardware back on any tab does nothing (root of the back stack).

---

## 3. Page Inventory

| # | Page | Route | Tab? | Status | Purpose |
|---|------|-------|------|--------|---------|
| 1 | Splash | `splash` | no | scaffold needed | Branded entry transition |
| 2 | Home (Boost) | `home` | yes | ✅ exists (T2) | Single-tap memory reclaim |
| 3 | Storage | `storage` | yes | 🔜 T3 | Storage usage + app list |
| 4 | App Detail | `storage/app/{packageName}` | no (drill) | 🔜 T3 follow-up | Per-app size + clear cache |
| 5 | Settings | `settings` | yes | future (v0.3) | App info, theme, about |

---

## 4. Shared Chrome

These elements persist across multiple pages and must be designed once.

### Top Bar
- **Height:** 30 dp content + 72 dp vertical padding (132 dp total)
- **Left:** Apex mark (30 dp circle) + `APEX` wordmark
- **Right:** Contextual action OR version chip `v0.1.0`
- **Background:** Transparent over `--obsidian`
- **Used on:** Home, Storage, Settings, App Detail
- **Behavior:** Sticky on scroll on Storage (since the list is long)

### Tab Bar
- **Position:** Bottom, 96 dp tall
- **Background:** `--obsidian-2` with 1 dp top border in `--obsidian-3`
- **Tabs (max 4):** icon glyph + 10 sp mono label
- **Active state:** Cyan icon + cyan label + 3 dp top accent bar
- **Inactive state:** `--ink-50` icon + `--ink-50` label
- **Iconography:** Vector glyphs, 24 dp, 1.5 dp stroke
- **Labels:** `BOOST` / `STORAGE` / `[future]` / `SETTINGS`

### Loading States
- Cyan sweep ring (reused from Home's Boost Ring) at 96 dp, centered
- `● Loading…` mono label below in `--ink-50`
- No spinners. No progress bars. The ring is the universal loading primitive.

### Empty States
- Centered mono text in `--ink-50`, 12 sp
- 48 dp top padding
- Format: `● No apps found` / `● Storage healthy` / `● Nothing to clear`

---

## 5. Page Specs

### 5.1 Splash

**Purpose:** Brand impression during cold-start. ≤ 600 ms.

**Layout (centered, full screen):**
```
                ┌──────────┐
                │   ●      │   ← Apex mark, 60 dp, pulse-once
                │  APEX    │   ← Wordmark, 18 sp, white, 0.3 letter-spacing
                └──────────┘
```

- Background: `--obsidian`
- Mark: 60 dp circle, `--summit-cyan`, single 800 ms pulse (scale 1.0 → 1.15 → 1.0)
- Wordmark fades in at 200 ms, opacity 0 → 1 over 400 ms
- Auto-routes to Home after 600 ms total
- No buttons. No skip. No animation cancel.

**No back button.** System back during splash exits the app.

---

### 5.2 Home (Boost) — Tab 1

**Purpose:** The hero. One tap to reclaim memory. Nothing else on this screen.

**Route:** `home`

**Layout (top to bottom, on `--obsidian`):**

```
┌─────────────────────────────────────┐
│ ●  APEX                  v0.1.0    │ ← top bar
│                                     │
│             (192 dp)                │
│                                     │
│              Game                   │ ← 56 sp Inter Bold, white
│           Performance               │ ← 56 sp Inter Bold, cyan
│                                     │
│       One tap to reclaim memory     │ ← 13 sp, --ink-70
│             & focus CPU             │
│                                     │
│           ● Ready to boost          │ ← 12 sp mono
│                                     │
│            (168 dp)                 │
│                                     │
│            ╭─────────╮              │
│           │  BOOST   │              │ ← 660 dp circle, cyan gradient
│            ╰─────────╯              │
│         (with sweep + glow rings)   │
│                                     │
│           (144 dp, when result)     │
│                                     │
│      ┌─ BOOST COMPLETE ─┐           │
│      │       312        │           │ ← 64 sp cyan
│      │    MB reclaimed   │           │
│      │  ─────────────    │           │
│      │  PROCESSES  MEM  LOAD │       │
│      │   14       1.8→2.4  0.8→0.4   │
│      └───────────────────┘           │
└─────────────────────────────────────┘
       [BOOST] [STORAGE] [] [GEAR]    ← tab bar
```

**Components:**
- Top bar (shared)
- Title block (static)
- Subtitle (static)
- Status line (state-driven: `Ready` / `Optimizing…` / `Available: X MB` / `Nothing to clean`)
- Button container with BoostRing + GlowRing + Button (existing T2 code)
- Result panel (state-driven, hidden in IDLE)

**State machine:** `IDLE → BOOSTING → RESULT → IDLE` (existing, do not modify)
- Tapping in RESULT state resets to IDLE
- Tapping during BOOSTING is a no-op

**Interactions:**
- Tap BOOST button → start boost
- No other interactive elements on this screen
- Tab bar switch → fade out content, fade in new tab content (200 ms)

**Errors:**
- If `BoostManager.kick()` throws → catch, show `● Boost failed` in red, button returns to IDLE after 2 s
- Network/storage permission missing → handled silently (no permissions required in v0.2)

---

### 5.3 Storage — Tab 2

**Purpose:** Surface what's eating space. Let the user drill into an app to act on it.

**Route:** `storage`

**Layout (top to bottom, scrollable):**

```
┌─────────────────────────────────────┐
│ ●  APEX                  v0.1.0    │
│                                     │
│  STORAGE                            │ ← 28 sp Inter Bold, white
│  ● 42.1 GB used · 18.3 GB free     │ ← 13 sp mono
│                                     │
│      ╭──────────────╮               │
│     │     70%       │               │ ← ring chart, 240 dp
│     │   OF 60.4GB   │               │ ← 11 sp mono
│      ╰──────────────╯               │
│                                     │
│  ─── BREAKDOWN ───                  │ ← mono 9 sp, --ink-50
│                                     │
│  Games           18.2 GB    ▮▮▮▮▮▮▮│ ← 14 sp label, 13 sp value
│  Apps             9.4 GB    ▮▮▮▮   │   bar: --obsidian-3 track
│  Cache            8.1 GB    ▮▮▮▮   │         --summit-cyan fill
│  System           5.7 GB    ▮▮▮     │
│  Other            0.7 GB    ▮       │
│                                     │
│  ─── TOP OFFENDERS ───              │
│                                     │
│  ┌─────────────────────────────┐    │
│  │ ●  Genshin Impact  12.4 GB  │    │ ← app row, 96 dp tall
│  │    com.miHoYo            ›  │    │   package mono 10 sp
│  └─────────────────────────────┘    │
│  ┌─────────────────────────────┐    │
│  │ ●  PUBG Mobile      6.1 GB  │    │
│  │    com.tencent.ig     ›      │    │
│  └─────────────────────────────┘    │
│  ┌─────────────────────────────┐    │
│  │ ●  WhatsApp         4.2 GB  │    │
│  │    com.whatsapp      ›      │    │
│  └─────────────────────────────┘    │
│  ...                                │
│  (scroll for more)                  │
└─────────────────────────────────────┘
       [BOOST] [STORAGE] [] [GEAR]
```

**Components:**

1. **Header section (sticky)**
   - Title `STORAGE` (28 sp Inter Bold)
   - Status line: `● {used} used · {free} free` (13 sp mono)
   - Total capacity below the ring: `OF {total}` mono 11 sp

2. **Donut ring (storage overview)**
   - 240 dp diameter, stroke 16 dp
   - Two colors: `--summit-cyan` (used), `--obsidian-3` (free)
   - Center text: percentage in 40 sp Inter Bold white, `OF {total}` below in mono
   - Sweep animation on first render: 0° → used-angle over 800 ms ease-in-out

3. **Breakdown section**
   - Label `─── BREAKDOWN ───` mono 9 sp, `--ink-50`
   - 5 rows, 48 dp tall each
   - Each row: category name (14 sp Inter) + size value (13 sp Inter Bold right-aligned) + horizontal bar
   - Bar: full width minus 240 dp, 6 dp tall, `--obsidian-3` track, `--summit-cyan` fill
   - Tapping a category filters the Top Offenders list to that category (visual: section label changes to `─── {CATEGORY} APPS ───`)

4. **Top offenders list**
   - Label `─── TOP OFFENDERS ───` mono 9 sp
   - Vertical list, sorted by size desc
   - Each row is a card:
     - Background: `--obsidian-2`, 12 dp corner radius, 1 dp border `--obsidian-3`
     - Height: 96 dp
     - Left: small app icon (40 dp, vector or scaled PNG), 24 dp left padding
     - Center: app name (14 sp Inter Bold) on top, package name (10 sp mono `--ink-70`) below
     - Right: size (13 sp Inter Bold) + chevron `›` (16 sp `--ink-50`)
     - Press: scale 0.98, border becomes `--summit-cyan` at 30%
   - Tap → navigate to App Detail

**State machine:**
- `LOADING` → `READY` → (filter applied) `READY_FILTERED`
- `ERROR` only if StorageManager throws on first read; shows `● Storage unavailable` and a `RETRY` mono button

**Pull-to-refresh:**
- Re-runs StorageManager.scan()
- 200 ms minimum visible indicator
- Does not animate the donut from scratch (just updates values)

---

### 5.4 App Detail — Drill-down

**Purpose:** Per-app storage breakdown + safe actions.

**Route:** `storage/app/{packageName}` (deep-linkable for future "share this app's storage" use)

**Layout (top to bottom, no scroll, no tab bar):**

```
┌─────────────────────────────────────┐
│ ‹  APEX                            │ ← back chevron + wordmark
│                                     │
│  ┌────┐                             │
│  │ ▣  │  Genshin Impact             │ ← icon 56 dp + name 22 sp
│  │    │  com.miHoYo                 │ ← package 11 sp mono
│  └────┘                             │
│                                     │
│      ╭──────────────╮               │
│     │   12.4 GB     │               │ ← 36 sp Inter Bold cyan
│     │  TOTAL SIZE   │               │ ← 10 sp mono
│      ╰──────────────╯               │
│                                     │
│  ─── COMPOSITION ───                │
│                                     │
│  App                2.1 GB   ▮▮     │
│  Data               3.8 GB   ▮▮▮▮   │
│  Cache              6.5 GB   ▮▮▮▮▮▮▮│
│                                     │
│  ─── ACTIONS ───                    │
│                                     │
│  ┌─────────────────────────────┐    │
│  │  CLEAR CACHE          6.5GB │    │ ← primary action
│  └─────────────────────────────┘    │
│                                     │
│  ┌─────────────────────────────┐    │
│  │  CLEAR DATA          9.9GB  │    │ ← secondary, destructive
│  └─────────────────────────────┘    │
│                                     │
│  Last used: 2 hours ago             │ ← 11 sp mono, --ink-50
└─────────────────────────────────────┘
```

**Components:**

1. **Top bar override**
   - Replaces the shared top bar
   - Left: back chevron `‹` (28 sp, white) + `APEX` wordmark
   - No version chip

2. **App identity**
   - Icon: 56 dp, vector preferred, fallback rounded-square placeholder in `--obsidian-3` with first letter
   - Name: 22 sp Inter Bold
   - Package: 11 sp mono `--ink-70`

3. **Total size block**
   - Centered number, 36 sp Inter Bold, `--summit-cyan`
   - `TOTAL SIZE` mono 10 sp below, `--ink-50`
   - Number animates count-up on entry (reuses Home's count pattern)

4. **Composition breakdown**
   - Same row pattern as Storage tab breakdown
   - 3 rows: App, Data, Cache
   - Sum of bars equals total

5. **Action buttons**
   - Each is a full-width row, 84 dp tall
   - Background: `--obsidian-2`, 16 dp corner radius, 1 dp border
   - Label: 14 sp Inter Bold
   - Right side: size that will be freed (13 sp Inter Bold cyan)
   - **CLEAR CACHE** — primary, always enabled, default border `--obsidian-3`, on tap border becomes `--summit-cyan`
   - **CLEAR DATA** — secondary, enabled only if app is debuggable OR user is owner; otherwise disabled (40% alpha, `--ink-50` text)
   - Tap fires confirmation sheet (see below)

6. **Metadata footer**
   - `Last used: {relative time}` mono 11 sp `--ink-50`

**Confirmation sheet (modal, bottom):**
- Slides up 240 dp from bottom
- Background: `--obsidian-2`, 24 dp top corner radius, 1 dp top border `--obsidian-3`
- Title: `{ACTION NAME}` mono 11 sp `--ink-50`
- Body: `This will free {size} from {app name}. The app may lose its logged-in session.` (15 sp Inter, white)
- Two buttons stacked:
  - `CANCEL` — ghost button, full width, 56 dp tall
  - `CONFIRM` — filled cyan button, full width, 56 dp tall
- No tap-outside-to-dismiss on destructive actions (CLEAR DATA)

**State machine:**
- `LOADING` (initial fetch of app stats) → `READY`
- `CONFIRMING_CLEAR_CACHE` | `CONFIRMING_CLEAR_DATA` → `CLEARING` → `READY` (re-fetched)
- `CLEAR_FAILED` → `READY` with toast `● Could not clear {app}`

**Hardware back** → returns to Storage tab, preserves scroll position.

---

### 5.5 Settings — Tab 4

**Purpose:** App metadata, about, legal. Not a power-user surface.

**Route:** `settings`

**Layout:**

```
┌─────────────────────────────────────┐
│ ●  APEX                  v0.1.0    │
│                                     │
│  SETTINGS                           │ ← 28 sp Inter Bold
│                                     │
│  ─── APP ───                        │
│                                     │
│  Version                  v0.1.0    │ ← row, 72 dp
│  Build                 d30a1726     │
│  Target SDK                   37    │
│                                     │
│  ─── THEME ───                      │
│                                     │
│  Appearance                  Dark › │ ← single option for now
│  Accent                    Cyan  ›  │
│                                     │
│  ─── DATA ───                       │
│                                     │
│  Storage used by ApexCore    1.2 MB │
│  Scan all apps on launch       [●]  │ ← toggle, default off
│                                     │
│  ─── ABOUT ───                      │
│                                     │
│  Open source licenses            ›  │
│  Source on GitHub               ›  │ ← opens external
│  Privacy policy                 ›  │
│                                     │
│                                     │
│           (centered)                │
│      ●  APEX  v0.1.0                │ ← footer logo + version
│   One tap to reclaim memory         │
└─────────────────────────────────────┘
       [BOOST] [STORAGE] [] [GEAR]
```

**Components:**

1. **Section pattern**
   - Label `─── {NAME} ───` mono 9 sp `--ink-50`
   - 24 dp top padding before label, 12 dp bottom padding after

2. **Row pattern**
   - 72 dp tall, full width, no card background (flat list on obsidian)
   - Left: label (14 sp Inter, white)
   - Right: value (13 sp mono `--ink-70`) OR chevron `›` (16 sp `--ink-50`) OR toggle
   - Bottom border: 1 dp `--obsidian-3`
   - Press: full-row flash `--summit-cyan` at 6% alpha

3. **Toggle**
   - 44×24 dp pill, `--obsidian-3` track, 20 dp circular thumb
   - ON: track `--summit-cyan`, thumb white, 4 dp right offset
   - OFF: track `--obsidian-3`, thumb `--ink-70`, 4 dp left offset
   - 150 ms ease-in-out slide

4. **Footer**
   - Centered, 96 dp top padding
   - Apex mark (16 dp) + `APEX v0.1.0` (mono 11 sp)
   - Tagline below in 12 sp Inter, `--ink-50`

**No destructive actions on this screen.** Even "clear app data" is intentionally absent — Settings is read-only metadata + preferences.

---

## 6. Component Library (cross-page)

These are the reusable building blocks. Each must be built once and consumed by multiple pages.

| Component | Used On | Source |
|---|---|---|
| `TopBar` | Home, Storage, Settings, App Detail | shared |
| `TabBar` | Home, Storage, Settings | shared |
| `BoostRing` | Home, Loading states | T2 (exists) |
| `GlowRing` | Home | T2 (exists) |
| `BoostButton` | Home | T2 (exists) |
| `StorageRing` | Storage | T3 new |
| `BreakdownRow` | Storage, App Detail | T3 new |
| `AppRow` | Storage | T3 new |
| `ActionButton` | App Detail | T3 new |
| `ConfirmSheet` | App Detail | T3 new |
| `Toggle` | Settings | future |
| `ChevronRow` | Settings | future |

---

## 7. Information Architecture Summary

```
ApexCore
├── Splash (transient, no state)
└── Main
    ├── Home (Boost)
    │   └── inline: Result Panel
    ├── Storage
    │   ├── Breakdown filter (no route, in-place filter)
    │   └── App Detail (drill)
    │       └── Confirm Sheet (modal)
    └── Settings
        └── external links (browser intents)
```

**Total persistent screens:** 4 (Home, Storage, App Detail, Settings)
**Total modals:** 1 (Confirm Sheet)
**Total transient screens:** 1 (Splash)

This is the minimum that makes the T3 expansion feel coherent. Adding a 3rd tab in the future (e.g. CPU, Network) is now a 1-line config change in the TabBar.

---

## 8. Open Questions for T3 Implementation

1. **Storage permissions** — `MANAGE_EXTERNAL_STORAGE` or scoped storage? Affects whether we can list all apps' data dirs.
2. **App icons** — system PackageManager gives us a Drawable; do we cache them or fetch live each render?
3. **"Clear cache" availability** — every app exposes `CacheDir`; clearing it works on all user apps but the path differs by API level. Need a `StorageManager.clearAppCache(packageName)` wrapper.
4. **Tab bar visibility on App Detail** — confirm full-screen drill-down (currently planned) vs. persistent tab bar with pushed fragment.
5. **Deep links** — `apexcore://storage/app/{pkg}` for future share intents; defer to post-T3.
