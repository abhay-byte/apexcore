# ApexCore — UI/UX Structure

> Only pages and contents. No theme, color, or size.

## App Entry Flow

```
Splash -> Onboarding (first launch) -> Main (shell with tabs)
                     -> Main (returning user, onboarding completed)
```

- **Splash** checks onboarding completion then routes.
- **Onboarding** completes flag then enters Main.
- **Main** hosts bottom-nav tabs + full-screen overlays + dialogs.

## Shell

- **Top bar** (`ZenTopBar`): app logo + title + backend chip/dropdown.
  - Backend chip shows current preference or detected backend (`SHIZUKU` / `ROOT` / `SETUP`).
  - Dropdown lists Shizuku and Root with readiness state (`Ready` / `Not available` / `Checking…`). Selecting a ready backend switches preference; selecting unavailable opens setup.
- **Bottom nav** (`ZenBottomNav`): 4 icon-only tabs — Home (Boost), Games, Overlay, Settings. Active tab indicated.
- **Navigation**: horizontal slide + fade between tabs. Full-screen screens (`Ram Free`, `Game Optimisation`, `Privacy Policy`) replace tab content and hide shell chrome until back.

---

## Pages

### 1. Splash (`ui/splash/SplashScreen.kt`)

- Route: `AppStage.SPLASH` in `MainActivity`.
- Purpose: brand intro + routing decision.
- Contains:
  - App logo
  - App name `ApexCore`
  - Tagline `ZEN PERFORMANCE ENGINE`
  - Auto-delay then calls `onSplashFinished(showOnboarding: Boolean)` based on `OnboardingPreferences.isOnboardingCompleted`.

### 2. Onboarding (`ui/onboarding/OnboardingScreen.kt`)

- Route: `AppStage.ONBOARDING` (fresh install) or replay from `Settings > Show Onboarding`.
- Pager: 5 pages, swipeable. Top: back/close + page indicator pills + Skip. Bottom: single CTA.
- Contains:
  - **Page 0 — Welcome**: logo, title, tagline, description paragraph.
  - **Page 1 — Purge Engine**: kicker `01 · PURGE ENGINE`, title `Focus Resources for Gaming`, description about deep-freeze + RAM, artwork.
  - **Page 2 — Performance HUD**: kicker `02 · PERFORMANCE HUD`, title `Live On-Screen Telemetry`, description about FPS/RAM/CPU overlay, artwork.
  - **Page 3 — Memory Toolkit**: kicker `03 · MEMORY TOOLKIT`, title `App Pins & Safe Reclaim`, description about pinning + reclaim cap, artwork.
  - **Page 4 — System Access (Elevation)**: kicker `04 · SYSTEM ACCESS`, title `Elevate Your Control`, subtitle about backend selection, two option cards:
    - `Shizuku Service` card: subtitle/status from `shizukuReady` (connected / wireless debugging / checking), CTA `USE SHIZUKU` / `CONFIGURE SHIZUKU` / `CHECKING…`, badge `READY`/`RECOMMENDED`.
    - `Root Access` card: subtitle from `rootReady` (su granted / direct su / checking), CTA `USE ROOT` / `GRANT ROOT` / `CHECKING…`, badge `READY`.
    - Footer note: changeable later in Settings.
  - Bottom CTA: `Get Started` (page 0) → `Continue` (pages 1-3) → `Enter ApexCore` (page 4, finishes onboarding).
  - Actions: select backend writes `preferred_backend` pref, syncs `FreezeFramework` + `FpsStack`, triggers re-detect; configure opens Shizuku app / Play Store; grant root probes `su`.

### 3. Home — Boost (`ui/home/HomeScreen.kt`) — Tab `HOME`

- Access: default tab in `MainScreen`.
- Purpose: memory overview + purge trigger + entry points to tuning/pins.
- Contains:
  - **MemoryLeafPair**: RAM used/total and Swap used/total readouts + purge animation hooks (`isPurgeAnimActive`, `actualFreedMb`, `freedRamText`).
  - **Status line**: `StatusPebble` + uppercase status text:
    - `Ready to purge bloat` (elevated, idle)
    - `Connect Shizuku or Root for deep freeze` (not elevated, idle)
    - `PURGING BACKGROUND PROCESSES…` (boosting)
    - `Freed N background apps` / `Already optimized` / `System fully optimized` / `Freeze blocked — connect Shizuku or Root` (result, from `lastResult`)
  - **Elevation banner** (`ShizukuConnectBanner`): shown when no elevated backend and not `Detecting…`. Title `ELEVATION REQUIRED`, subtitle `Connect Shizuku or Root for deep freeze`, description about freeze gated, CTA `CONNECT SHIZUKU / ROOT`.
  - **Action card** (AnimatedContent):
    - Idle/Boosting: `PebbleButton` (BOOST) with boosting state.
    - Result: `UnifiedResultCard` — header icon + `PURGE COMPLETE` or `FREEZE BLOCKED`, subtitle, `PURGE AGAIN` pill, divider, 4 stat items: `FREED SIZE` (total + RAM/Swap breakdown), `PURGED APPS`, `DURATION`, `SKIPPED` (with failed count). Tap resets to idle.
  - **Entry rows**:
    - `Game optimisation` row: subtitle shows probing / `N available on this kernel` / `None on this kernel`, navigates to Tune. Only shown when elevated.
    - `Pin Apps` row: subtitle `Protect apps from being frozen`, opens pin picker dialog.
  - **DeviceThermalCard**: battery + CPU thermal telemetry (live).

### 4. Games — Launch Matrix (`games/GamesScreen.kt`) — Tab `GAMES`

- Access: tab `GAMES`.
- Purpose: select and launch target app with pre-launch freeze.
- Contains:
  - **Search field**: `Search packages…` placeholder, filters by name/pkg.
  - **Actions row**: Add button (when library non-empty) + Pin/whitelist button.
  - **Segment toggle**: `GAMES` (custom list from `GameManager.load()`) vs `ALL APPS` (from `listInstallableApps`, loaded async with loading indicator).
  - **Carousel**: `HorizontalPager` showing one centered card + adjacent previews, haptic tick on page change, swipe along arc.
    - Active card shows: app icon, name, `RESOURCE DEMAND` meter (Low/Medium/High), `ALLOCATE & LAUNCH` button.
    - Tap/launch: topographic sweep animation, launches via `GameLauncher`, freezes before foreground via `FreezeFramework`.
  - **Empty state**: `NO ITEMS FOUND`, `ADD GAMES` / `SCAN FOR GAMES` CTA.
  - **Dialogs**: `AddGamePickerDialog` (pick from installed apps to add), `WhitelistPickerDialog` (pin apps), launching overlay with `launchingPkg` state.

### 5. Overlay — HUD Config (`ui/overlay/OverlayScreen.kt`) — Tab `OVERLAY`

- Access: tab `OVERLAY`.
- Purpose: configure and preview floating performance HUD.
- Contains:
  - **Permission card** (`GlassCard`):
    - Header: `StatusPebble` + `PERMISSION GRANTED` / `ACTION REQUIRED`
    - Description: granted vs needs `Draw Over Apps` permission
    - CTA `GRANT PERMISSION` (opens `ACTION_MANAGE_OVERLAY_PERMISSION`) when not granted
  - **HUD Overlay card** (`GlassCard`):
    - Description: `Launch a preview overlay to check placement, transparency, and drag gestures.`
    - Actions: `START` / `STOP` toggling preview via `GameOverlayService`
  - **Service sync**: polls `Settings.canDrawOverlays` + `GameOverlayService.isRunning` + fallback `isOverlayServiceRunningFallback`, clears stale prefs on external kill.

### 6. Settings (`ui/settings/SettingsScreen.kt`) — Tab `SETTINGS`

- Access: tab `SETTINGS`.
- Purpose: appearance, access/diagnostics, legal, about, tour replay.
- Contains:
  - Header: `Settings` + `Appearance, access, and about`
  - **APPEARANCE** section:
    - `Theme` card: label + description `Choose light, dark, or match your system` + `ThemeSegmentedControl` (System/Light/Dark)
    - `Light tank glass` card: label + description + switch
  - **ACCESS** section:
    - `ActiveModeCard`: shows `RUNNING MODE`, current backend name, preferred backend, `FPS` privilege mode (`PrivilegeMode`), GPU vendor.
    - `SystemDiagnosticsCard` / `DiagnosticRow`: diagnostics rows
    - `ACCESS DIAGNOSTICS`: live readiness rows for Shizuku/Root
    - `GlobalBackendDropdown` also exposed via top bar chip (same data)
  - **LEGAL** section: `Privacy Policy` row + `How Apex Core handles your data` → opens full-screen Privacy Policy.
  - **ABOUT** section: `Apex Core` + `Version vX.Y` (from PackageManager), app tour + info.
  - **APP TOUR** row: `Show Onboarding` + `Replay feature tour & access configuration` → opens `OnboardingScreen(isReplay=true)`.

### 7. Game Optimisation — Tune (`ui/tune/TuneScreen.kt`) — full-screen overlay

- Access: `Home > Game optimisation` row (elevated only).
- Purpose: kernel/session parameters applied on game launch, restored on exit.
- Contains:
  - Top bar: back arrow, title `Game optimisation`, refresh/re-probe button (spinner when `isProbing`).
  - Header: `Real Kernel & Session Tuning` + description `Capability-gated parameters safely applied during game sessions and restored on exit.` + `SESSION ACTIVE` pill when `sessionActive`.
  - **Category list** (`LazyColumn`): `TuneCategory` sections sorted by available count descending. Each section (`TuneCategorySection`) lists `TuneOptionRow` entries bound to `TuneSpecs.byCategory`, `capabilities`, `intents`. Toggle intent via `TuneManager.setIntent`.
  - Footer: disclosure `Applies when you launch a game from ApexCore. Restored when the session ends. Does not disable thermal protections.`

### 8. Ram Free (`ram/RamFreeScreen.kt`) — full-screen overlay

- Access: via Home/Boost flow (Ram reclaim action).
- Purpose: force system reclaim with safe cap, without killing pinned/system apps.
- Contains:
  - Top bar: back arrow, title `Ram Free`, mode badge when non-standard.
  - **RamPressureGauge**: visual gauge for RAM and Swap pressure fractions, percent label.
  - **Readout rows/chips** (`ReadoutRow`, `ReadoutChip`): current `getSystemMemStats` values (used/total), refreshes every second.
  - **Progress card** (`ProgressGlassCard`, `RamFillProgressSection`): shows `RamFillProgress` states — `PreFreeze`, `Filling`, `Holding`, `Releasing`, `Done` — with progress and result.
  - **Mode selector**: dropdown of `RamFillMode` entries (e.g. `STANDARD`) with readiness per mode, `preFreeze` toggle.
  - **Action button** (`RamFillActionButton`): start/cancel/hold/release depending on state, via `RamFillerManager` / `RamFillerService`.
  - Behaviors: keep screen on while running, cancel on pause/back/dispose.

### 9. Privacy Policy (`ui/legal/PrivacyPolicyScreen.kt`) — full-screen overlay

- Access: `Settings > Privacy Policy`.
- Purpose: offline privacy policy viewer (no network, no WebView).
- Contains:
  - `LazyColumn` rendering `privacy_policy.md` from assets via `PrivacyMarkdown.render`.
  - Blocks: headings (`#`/`##`/`###`), paragraphs with bullets, code blocks, inline formatting (`**bold**`, `*italic*`, ``code``, `[label](url)` links via `ClickableText`), tables as mono rows, LaTeX `$…$` / `$$…$$` as styled pills.
  - Top bar: back arrow.
  - Fallback text if asset fails to load.

### 10. Dialogs

- **SetupDialog** (`SetupDialog.kt`): `ZenDialog` with dimiss-on-outside. Title `SYSTEM ACCESS CONFIG`, description `Deep freeze (BOOST) requires Shizuku or Root access.` Two `OptionCard`s:
  - Shizuku: `Shizuku Service` + status subtitle + CTA `USE SHIZUKU`/`CONFIGURE SHIZUKU`/`CHECKING…` + badge `READY`/`RECOMMENDED`.
  - Root: `Root access` + status subtitle + CTA `USE ROOT`/`GRANT ROOT`/`CHECKING…`.
  - Probes both backends every 1200ms while open. Selecting ready backend writes pref, syncs `FreezeFramework` + `FpsStack`, re-detects, dismisses on success. Shown on first launch if no elevated backend and not previously shown (`setup_shown_v1`), also via `Home` banner and `Settings` + top-bar dropdown fallback.

- **WhitelistPickerDialog** (`games/WhitelistPickerDialog.kt`): Pin apps to exclude from freeze. Searchable list of installed apps, toggle pin per app via `GameManager`, persisted in whitelist store.

- **AddGamePickerDialog** (`games/AddGamePickerDialog.kt`): Add games to custom library. Searchable list of installable apps (`listInstallableApps`), multi-select add to `GameManager`.

- **Onboarding replay** (inside `MainScreen`): `OnboardingScreen(isReplay=true)` as overlay with close button instead of skip/back.

### 11. Floating HUD (outside MainScreen composition)

- **GameOverlayService** (`games/GameOverlayService.kt`): system overlay window (`TYPE_APPLICATION_OVERLAY`) rendered over foreground game. Draggable, shows live `FpsRepository` / `ThermalMonitor` data: FPS, RAM pressure, CPU. Triggered from `GamesScreen` launch flow and `OverlayScreen` preview (`START`/`STOP`). Uses `FpsStack` + `ForegroundAppResolver` + `PrivilegeModeStore`. Not a nav page — runs as service.

## File Map

```
app/src/main/kotlin/com/ivarna/apexcore/
  MainActivity.kt              # AppStage: SPLASH -> ONBOARDING -> MAIN
  SetupDialog.kt               # System access dialog
  ui/splash/SplashScreen.kt
  ui/onboarding/OnboardingScreen.kt
  ui/shell/MainScreen.kt       # Tab host + full-screen routing
  ui/shell/AppNav.kt           # Tab {HOME,GAMES,OVERLAY,SETTINGS}, State {IDLE,BOOSTING,RESULT}
  ui/shell/ZenTopBar.kt
  ui/shell/ZenBottomNav.kt
  ui/home/HomeScreen.kt        # Boost tab + MemoryLeafPair + result card + thermal card
  ui/home/DeviceThermalCard.kt
  games/GamesScreen.kt         # Launch matrix carousel
  games/GameManager.kt / GameLauncher.kt / GameOverlayService.kt
  ui/overlay/OverlayScreen.kt
  ui/settings/SettingsScreen.kt
  ui/tune/TuneScreen.kt        # Full-screen from Home
  ram/RamFreeScreen.kt         # Full-screen
  ui/legal/PrivacyPolicyScreen.kt # Full-screen
```

## What Is Not In This Doc

Theme, color, size, typography, spacing, motion specs — excluded per requirement.
