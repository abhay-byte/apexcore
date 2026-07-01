# ApexCore — UI/UX Page Map

> Companion to `docs/design.md`. Defines every screen, its state machine, its components, and their relationships. Built around the **Summit** design language and the current T4 codebase (freeze framework + game launcher).

---

## 1. Scope

**In scope (T4 current):**
- Home (Boost) — the primary screen
- Freeze result panel — inline stats
- Setup dialog — backend privilege onboarding
- Game list dialog — auto-detect + manual add + launch

**Explicitly deferred (T5+):**
- Tab navigation (Storage, Settings)
- Per-app freeze list with checkboxes
- Whitelist / pinned apps
- Boot-time freeze schedule
- Onboarding / first-run tutorial

---

## 2. Navigation Map

```
┌─────────────────────────────────────┐
│           MainActivity               │ ← single screen, no tabs
│         (ScrollView)                 │
├─────────────────────────────────────┤
│  Top Bar                             │
│  Hero Title                          │
│  Subtitle                            │
│  Status Line                         │
│  BOOST Button (+ rings)              │
│  Result Panel (state-driven)         │
│  GAMES Button                        │
├─────────────────────────────────────┤
│  ┌─ SetupDialog ─┐   (modal)        │
│  │  - Shizuku    │                   │
│  │  - Root       │   shown once      │
│  │  - A11y       │   on cold start   │
│  └───────────────┘   when fallback   │
├─────────────────────────────────────┤
│  ┌─ GameListDialog ────┐  (modal)   │
│  │  - Auto-detected    │             │
│  │  - Manually added   │             │
│  │  - Tap to freezeluanch│           │
│  └──────────────────────┘            │
└─────────────────────────────────────┘
```

### Navigation rules
1. **Single screen** — no tab bar, no fragments. Everything is a dialog or inline panel.
2. **Boost** is an action, not a route. Its result appears inline below the button.
3. **SetupDialog** appears once on cold start when no elevated backend is detected. Can be re-opened via the amber `▷ TAP FOR SETUP` hint.
4. **GameListDialog** is opened by tapping the GAMES button. Full-screen modal.
5. **Back** dismisses any open dialog. Back on the main screen does nothing (root of stack).

---

## 3. Page Inventory

| # | Page / Component | Route | Trigger | Status | Purpose |
|---|-----------------|-------|---------|--------|---------|
| 1 | Home (Boost) | `main` | launch | ✅ exists | Single-tap freeze + memory reclaim |
| 2 | Freeze Result Panel | inline | after boost | ✅ exists | Killed/failed/skipped + mem stats |
| 3 | Setup Dialog | dialog | auto / tap hint | ✅ exists | Backend privilege onboarding |
| 4 | Game List Dialog | dialog | tap GAMES button | ✅ exists | Auto-detect + manual add + launch |
| 5 | FREEZE_ALL Broadcast | external | `am broadcast` | ✅ exists | ADB / Tasker trigger |

---

## 4. Shared Chrome

### Top Bar
- **Height:** 30 dp content + vertical padding (stretched by layout)
- **Left:** Apex mark (30 dp cyan circle) + `APEX` wordmark (14 sp Inter Bold)
- **Right:** Version chip `v0.1.0` (11 sp JetBrains Mono, `--ink-50`)
- **Background:** Transparent over `--obsidian`

### Status Line
- Mono 12 sp, always starts with `●`
- States:
  - `● Detecting…` — on cold start while backend resolves
  - `● Freeze: {backend}` — normal idle (e.g. `● Freeze: Root`)
  - `● Ready to boost · {backend}` — idle with backend (in IDLE state)
  - `● Freezing via {backend}…` — during boost operation
  - `● Freezed {N} apps via {backend}` — success result
  - `● Nothing to clean · {backend}` — zero apps killed

### Loading States
- Cyan sweep ring (BoostRing) at ring container size, centered on button
- Status line shows `● Freezing via {backend}…`
- No spinners. The sweep ring is the only loading primitive.

### Empty States (Result Panel)
- When 0 apps killed: `freedBig` shows `0` in `--ink-50`, `freedSub` shows `Already optimized`
- When >0 apps killed: `freedBig` shows freed MB in cyan, `freedSub` shows `MB reclaimed`

### Error States
- Boost failures caught per-app — failures counted in FAILED stat, not displayed as errors
- Backend detection errors log to Logcat silently, next backend in priority tried
- Game launch failures show toast `Failed: {reason}`

---

## 5. Page Specs

### 5.1 Home (Boost)

**Layout (top to bottom, scrollable):**

```
┌─────────────────────────────────────┐
│ ●  APEX                  v0.1.0    │ ← top bar
│                                     │
│              Game                   │ ← 56 sp Inter Bold, white
│           Performance               │ ← 56 sp Inter Bold, cyan
│                                     │
│   One tap to reclaim memory & focus │ ← 13 sp, --ink-70
│                CPU                  │
│                                     │
│   ● Freeze: Root / ● Detecting…    │ ← 12 sp mono status
│                                     │
│   ▷ TAP FOR SETUP                  │ ← amber, only when "cached only"
│                                     │
│         ╭─────────────────╮         │
│        │      BOOST       │         │ ← 660 dp circle, cyan gradient
│         ╰─────────────────╯         │
│        (BoostRing sweep arc)        │
│     (GlowRing ambient behind)       │
│                                     │
│   ┌──── BOOST COMPLETE ──────┐      │
│   │         312              │      │ ← 64 sp cyan freed MB
│   │      MB reclaimed        │      │
│   │  ──────────────────────  │      │
│   │  KILLED  FAILED  SKIPPED │      │
│   │    14      0        2    │      │
│   │  RAM            SWAP     │      │
│   │  8192M/1450M  4096M/2.9G│      │ ← total / available
│   │  MODE          DURATION  │      │
│   │  Shizuku        4.2s    │      │
│   └──────────────────────────┘      │
│                                     │
│   ┌────────────────────────────┐    │
│   │       🎮  GAMES            │    │ ← opens GameListDialog
│   └────────────────────────────┘    │
└─────────────────────────────────────┘
```

**State machine:** `IDLE → BOOSTING → RESULT → IDLE`

| State | Button | Ring | Result Panel | Status Color |
|-------|--------|------|-------------|-------------|
| IDLE | BOOST, breathing pulse | hidden | hidden | `--ready` (green) |
| BOOSTING | dimmed, not clickable | sweep arc visible | hidden | `--summit-cyan` |
| RESULT | AGAIN, normal | hidden | visible, slide-up | `--summit-cyan` |

### 5.2 Freeze Result Panel

Appears below the button after a successful boost. Card in `--obsidian-2` with 20 dp radius.

**Stats grid (3 rows):**

| Row | Col 1 | Col 2 | Col 3 |
|-----|-------|-------|-------|
| 1 | KILLED | FAILED | SKIPPED |
| 2 | RAM (total/available) | SWAP (total/free) | MODE (backend name) |
| 3 | DURATION | — | — |

**Data sources:**
- KILLED / FAILED / SKIPPED — from `FreezeResult` counters
- RAM — `MemTotal:` / `MemAvailable:` from `/proc/meminfo`
- SWAP — `SwapTotal:` / `SwapFree:` from `/proc/meminfo`
- MODE — backend name (Shizuku / Root / Accessibility / cached only)
- DURATION — elapsed time of `freezeAll()` in seconds

**Entry animation:** alpha 0→1, translationY 60→0 over 500 ms, ease-in-out.

### 5.3 Setup Dialog

Full-screen translucent dialog, triggered automatically once on first cold start when backend is `cached only`.

**Card content:**
- Header: `SETUP REQUIRED` (mono 10 sp)
- Body text explaining the need for elevated access
- 3 option rows:
  1. **Shizuku (recommended)** — opens Shizuku Manager or Play Store
  2. **Root** — verifies su access via re-detection
  3. **Accessibility** — opens Accessibility settings
- Footer: `USE CACHED-ONLY MODE` — dismisses dialog

### 5.4 Game List Dialog

Full-screen translucent dialog, opened by tapping the GAMES button.

**Card content:**
- Header: `GAME LAUNCHER` (mono 10 sp)
- Body: `Auto-detect or manually add games. Tap to freeze + launch.`
- **Game list** — scrollable list of added games, each showing:
  - Game name (14 sp Inter Bold, white)
  - Tag: `auto` or `manual` (mono 9 sp in `--obsidian-3` chip)
  - Play arrow `▶` (cyan)
- Tap game → freeze background apps → launch game intent
- Long-press game → remove from list (with toast confirmation)
- **Manual add row** — EditText for package name + ADD button
- **SCAN FOR GAMES button** — scans PackageManager for `CATEGORY_GAME` apps, adds them
- Footer: `CLOSE` — dismisses dialog

**State machine:** `IDLE → SCANNING → (results added | no results found)` via SCAN button

---

## 6. Game Launch Flow

```
User taps game in list
  │
  ├─ GameLauncher.launch(context, pkg)
  │    ├─ FreezeFramework.freezeAll() with custom filter
  │    │    └─ excludes the target game (game survives)
  │    ├─ context.packageManager.getLaunchIntentForPackage(pkg)
  │    └─ context.startActivity(intent) with FLAG_ACTIVITY_NEW_TASK
  │
  └─ Result: toast on failure, game opens on success
```

The freeze runs silently — no result panel is shown for the pre-launch freeze. The user sees a toast `"Freezing + launching {game}…"` and then the game opens.

---

## 7. Component Library (cross-page)

| Component | Used On | Source |
|---|---|---|
| `TopBar` | Home | `MainActivity.kt` |
| `GlowRing` | Home (behind button) | `MainActivity.kt` (inner class) |
| `BoostRing` | Home (sweep arc) | `MainActivity.kt` (inner class) |
| `BoostButton` | Home | `MainActivity.kt` |
| `ResultPanel` | Home (inline) | `MainActivity.kt` |
| `SetupDialog` | Home (modal) | `SetupDialog.kt` |
| `GameListDialog` | Home (modal) | `games/GameListDialog.kt` |
| `GameManager` | Game list storage | `games/GameManager.kt` |
| `GameLauncher` | Freeze + launch | `games/GameLauncher.kt` |

---

## 8. Broadcast Receiver: FREEZE_ALL

External trigger via ADB or automation apps:

```bash
adb shell am broadcast -a com.apexcore.app.action.FREEZE_ALL
```

- No UI shown — fire-and-forget via `goAsync()` + coroutine
- Result logged to Logcat: `I/ApexCore.Freeze: freezeAll done: FreezeResult(...)`
- Uses whatever backend is currently active (Shizuku / Root / Fallback)

---

## 9. Open Questions

1. **Per-app freeze whitelist** — T5 feature. Need UI for selecting apps to exclude from freeze-all.
2. **Game categories** — currently uses `CATEGORY_GAME` only. Should we also detect game-related meta-data or known game store categories?
3. **Game launch animation** — currently no transition between freeze and launch. Could add a "Preparing game…" interstitial.
4. **Multiple users / work profiles** — freeze and launch target the current user only (`--user current`). No multi-user support yet.
