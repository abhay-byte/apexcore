# ApexCore — UI/UX Page Map

> Companion to `docs/design.md`. Defines every screen, its state machine, its components, and their relationships. Built around the **Summit** design language and the current T5 codebase (Compose + 3-tab layout + overlay HUD).

---

## 1. Scope

**In scope (T5 current):**
- Home (Boost) — primary tab, freeze action + result
- Games tab — game library, scan, add, launch
- Overlay tab — HUD configuration
- Setup dialog — backend privilege onboarding (Compose dialog)
- Game overlay service — draggable HUD while gaming
- Broadcast receiver — FREEZE_ALL (ADB/Tasker)

**Explicitly deferred:**
- Per-app freeze whitelist
- Boot-time freeze schedule
- Onboarding / first-run tutorial
- Multiple users / work profiles

---

## 2. Design Constraints

See `docs/design.md` §13 for full don'ts. Key rules:
- **No raster icons** in the body (except launcher icon). Every glyph is a vector or mono character.
- **No green-blue gradients.** Cyan stops at `#00838F`.
- **No drop shadows on text.** The dark canvas is the shadow.
- **No icons inside the BOOST button** — the word is the icon.
- **No white text smaller than 11 sp.**
- **No animations longer than 1s on user-triggered actions.**
- **Never apologize** — "Already optimized" not "Sorry, nothing found."

---

## 3. Color Theme

Token definitions in `app/…/ui/theme/Color.kt` via `ApexCoreTheme` (Material3 `darkColorScheme`):

| Token | Hex | Usage |
|-------|-----|-------|
| `BgDark` | `#09090B` | App background |
| `SurfaceGlass` | `#26FFFFFF` | Glass overlays (15% white) |
| `SurfaceCard` | `#18181B` | Card / panel surface |
| `BorderGlass` | `#1AFFFFFF` | Subtle borders (10% white) |
| `AccentPrimary` | `#00E5FF` | Actions, primary accent, BOOST |
| `AccentSecondary` | `#00838F` | Gradient stop |
| `AccentSuccess` | `#10B981` | Idle / success states |
| `AccentWarning` | `#F59E0B` | Setup hint, warnings |
| `TextTitle` | `#FAFAFA` | Headlines, big numbers |
| `TextBody` | `#A1A1AA` | Body labels |
| `TextMuted` | `#71717A` | Hints, mono metadata |

**Rule:** Cyan is *currency*. Spend it only on (a) the BOOST button, (b) result numbers, (c) accent highlights. Everything else is grayscale on obsidian.

---

## 4. Navigation Map

```
┌──────────────────────────────────────┐
│              MainActivity             │ ← App Bar: icon + "APEX CORE" + backend chip
│                                       │
│  ┌─ HOME ──┐  ┌─ GAMES ──┐  ┌─ OVRL ─┐ ← bottom nav bar (3 tabs)
│  │ Boost   │  │ Library  │  │ HUD    │
│  │ Result  │  │ Scan/Add │  │ Config │
│  │         │  │ Launch   │  │        │
│  └─────────┘  └──────────┘  └────────┘
│                                       │
│  ┌─ SetupDialog ────┐  (modal)       │
│  │  Shizuku / Root  │                │
│  │  Accessibility   │  shown once     │
│  └──────────────────┘  on cold start  │
│                                       │
│  ┌─ OverlayService ──────┐ (HUD)     │
│  │  Draggable pill/hud   │ while game │
│  │  FPS, RAM, CPU, BOOST │ is running │
│  └───────────────────────┘            │
└──────────────────────────────────────┘
```

### Navigation rules
1. **3 tabs** — HOME (boost), GAMES (library), OVERLAY (HUD config). Bottom nav bar.
2. **Animated transitions** — slides left/right with fade between tabs.
3. **SetupDialog** appears once on cold start when backend is "cached only". Compose `Dialog` API.
4. **OverlayService** — draggable pill/mini-HUD shown when a game is launched. Not a navigation target; configured via OVERLAY tab.
5. **Back** dismisses SetupDialog. On main screen, system back exits the app.

---

## 5. Page Inventory

| # | Page / Component | Route | Trigger | Status | Purpose |
|---|-----------------|-------|---------|--------|---------|
| 1 | Home (Boost) | `Tab.HOME` | bottom nav | ✅ Compose | Single-tap freeze + memory reclaim |
| 2 | Games | `Tab.GAMES` | bottom nav | ✅ Compose | Game library, scan, add, launch |
| 3 | Overlay Config | `Tab.OVERLAY` | bottom nav | ✅ Compose | HUD settings (start/stop overlay) |
| 4 | Setup Dialog | dialog | auto / tap chip | ✅ Compose | Backend privilege onboarding |
| 5 | Overlay HUD | system overlay | game launch | ✅ Compose | Draggable FPS/RAM/CPU/BOOST |
| 6 | FREEZE_ALL Broadcast | external | `am broadcast` | ✅ exists | ADB / Tasker trigger |

---

## 6. Page Specs

### 6.1 Home (Boost) — `Tab.HOME`

**Layout (top to bottom, scrollable):**

```
┌─────────────────────────────────────┐
│  Game                                │ ← 44 sp Inter Bold, white
│  Optimization                        │ ← 44 sp Inter Bold, cyan
│                                      │
│  One tap to reclaim memory & focus   │ ← 13 sp, TextBody
│               CPU                    │
│                                      │
│  ● READY TO OPTIMIZE                 │ ← 10 sp mono uppercase, TextMuted / AccentWarning
│                                      │
│  > CONFIGURE ELEVATED ACCESS         │ ← amber, only when "cached only"
│                                      │
│       ┌─────────────────────────┐    │
│       │    MainActionCard       │    │ ← 200 dp, RoundedCornerShape(32)
│       │                         │    │   IDLE:   "OPTIMIZE SYSTEM"
│       │   OPTIMIZE SYSTEM       │    │           "Tap to reclaim memory"
│       │   Tap to reclaim memory │    │   BOOSTING: sweep arc, dimmed
│       │                         │    │   RESULT:  → UnifiedResultCard
│       └─────────────────────────┘    │
│                                      │
│       ┌─ UnifiedResultCard ────┐    │ ← shown in RESULT state
│       │  ✓ OPTIMIZATION COMPLETE│    │    + right: "RUN AGAIN" pill
│       │  ─────────────────────  │    │
│       │  FREED MB  │  KILLED   │    │    2×2 grid, staggered entry
│       │    312     │   14      │    │    indicator bar per stat
│       │  Reclaimed │  Apps     │    │
│       │  ──────────┼────────── │    │
│       │  DURATION  │  FAILED   │    │
│       │   4.2s     │   0       │    │
│       │  Exec time │  Errors   │    │
│       └─────────────────────────┘    │
└─────────────────────────────────────┘
```

**State machine:** `IDLE → BOOSTING → RESULT → IDLE`

| State | MainActionCard | Result Panel | Status Color |
|-------|---------------|-------------|-------------|
| IDLE | "OPTIMIZE SYSTEM" | hidden | TextMuted |
| BOOSTING | "FREEZING APPS", sweep arc | hidden | AccentWarning |
| RESULT | (transitions to UnifiedResultCard) | visible, stagger-fade | AccentSuccess |

### 6.2 Games Tab — `Tab.GAMES`

Full-page composable, not a dialog. Replaces the old `GameListDialog`.

**Layout:**
- Header: `GAMES` (24 sp bold) + `Tap to optimize and launch` (12 sp muted)
- Scan button (top right): `SCAN` pill → `SCANNING…` while detecting
- Game list: `LazyColumn` with `GameCard` items
  - Game name (15 sp bold white)
  - Tag chip: `AUTO` or `MANUAL` (7 sp mono, BorderGlass bg)
  - Package name (10 sp mono muted)
  - Arrow `>` (cyan, 32 dp circle with 15% alpha bg)
- Empty state: centered `EMPTY` + helper text
- Bottom: `BasicTextField` for manual package add + `ADD` button (cyan gradient)

**Interactions:**
- Tap card → `GameLauncher.launch()` with toast "Optimizing & launching {name}…"
- Long-press card → remove game with toast
- Tap ADD → validate package name, add to `GameManager`, refresh list
- Tap SCAN → `gameManager.detect()`, add found games

### 6.3 Overlay Tab — `Tab.OVERLAY`

Configuration screen for the in-game HUD overlay service.

**Layout:**
- Header: `HUD OVERLAY` (24 sp bold) + "Configure floating gameplay monitor" (12 sp muted)
- **Permission card**: Shows `PERMISSION GRANTED` (green) or `ACTION REQUIRED` (amber). If denied, a `GRANT PERMISSION` button opens `ACTION_MANAGE_OVERLAY_PERMISSION` intent. Permission state polls every 1s.
- **Test HUD controls**: `START TEST HUD` / `STOP TEST HUD` buttons. Requires permission granted. Starts/ stops a dummy overlay via `GameOverlayService.start()` for testing placement and drag gestures.

### 6.4 Setup Dialog

Compose `Dialog` API (not full-screen translucent). Triggered once on cold start when backend is "cached only".

**Layout:**
- Header: `SYSTEM ACCESS CONFIG` (10 sp mono, TextMuted)
- Body: "Select a mode to enable deep process freezing."
- **Shizuku Service** (full width, recommended): green `RECOMMENDED` badge, "CONFIGURE SHIZUKU →" CTA. Opens Shizuku Manager or Play Store.
- **Root access** + **Accessibility** (side-by-side cards): "GRANT ROOT →" and "OPEN SETTINGS →"
- Footer: `USE CACHED-ONLY MODE` (cyan, dismisses dialog)

### 6.5 Overlay HUD — `GameOverlayService`

Draggable system overlay shown while a game is running. Built with Compose via `ComposeView` and `ApexCoreTheme`.

**Two states:**
- **Collapsed** (56×56 dp pill): cyan border, FPS counter. Pulsing green dot indicator. Drag to move.
- **Expanded** (185 dp wide card): auto-collapses after 15s.
  - `MONITOR` header + close ✕ button
  - FPS value (32 sp, amber)
  - RAM usage bar (visual progress bar)
  - CPU load bar (visual progress bar)
  - `BOOST SYSTEM` button (cyan gradient)

**FPS tracking:** via `Choreographer.FrameCallback`.

---

## 7. Game Launch Flow

```
User taps game in Games tab
  │
  ├─ GameLauncher.launch(context, pkg)
  │    ├─ FreezeFramework.freezeAll() with custom filter
  │    │    └─ excludes the target game
  │    ├─ context.packageManager.getLaunchIntentForPackage(pkg)
  │    └─ context.startActivity(intent) with FLAG_ACTIVITY_NEW_TASK
  │
  └─ Result: toast on failure, game opens on success
```

The freeze runs silently — no result panel is shown. User sees toast "Optimizing & launching {game}…" then game opens.

---

## 8. Component Library

| Component | Location | Tech |
|-----------|----------|------|
| `MainScreen` | `MainActivity.kt:69` | Compose |
| `HomeScreen` | `MainActivity.kt` | Compose |
| `GamesScreen` | `games/GamesScreen.kt` | Compose |
| `OverlayScreen` | `MainActivity.kt:665` | Compose |
| `SetupDialog` | `SetupDialog.kt:42` | Compose `Dialog` |
| `GameOverlayService` | `games/GameOverlayService.kt` | Compose via `ComposeView` |
| `ApexCoreTheme` | `ui/theme/Theme.kt` | Material3 `darkColorScheme` |
| `Color` tokens | `ui/theme/Color.kt` | Compose `Color` constants |
| `GameManager` | `games/GameManager.kt` | Data layer |
| `GameLauncher` | `games/GameLauncher.kt` | Freeze + launch |

---

## 9. Broadcast Receiver: FREEZE_ALL

```bash
adb shell am broadcast -a com.ivarna.apexcore.action.FREEZE_ALL
```

- No UI shown — fire-and-forget via `goAsync()` + coroutine
- Result logged to Logcat
- Uses whatever backend is currently active

---

## 10. Open Questions

1. **Per-app freeze whitelist** — deferred. Need UI for excluding apps.
2. **Game categories** — currently uses `CATEGORY_GAME` only.
3. **Overlay HUD customisation** — which stats to show, colors, opacity.
4. **Multiple users / work profiles** — freeze targets current user only.
