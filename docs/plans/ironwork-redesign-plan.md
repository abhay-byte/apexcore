# IRONWORK Redesign — Implementation Plan

| | |
|---|---|
| **Doc No.** | AC-PLAN-IRONWORK · PASS 1 · ITER 1 |
| **Spec** | AC-DS-004 REV D (`docs/design/new_design.md`) + Packs I-III (`new_implementation_part_1/2/3.md`) |
| **UI/UX Structure** | `docs/ui-ux.md` |
| **Status** | READY FOR REVIEW |

---

## 0. Research Sources

- Local spec: `docs/design/new_design.md` (full IRONWORK AC-DS-004, 14 sections, 12 page redesigns)
- Local packs: `docs/design/new_implementation_part_1.md` (IronTokens, Clack, Plates, Buttons, StampLabel, InstrumentDial, Scales, Ticker, ToolRow, Controls), `new_implementation_part_2.md` (Skin, Chrome, Ink, PressureRoom, FieldManual, Toolbox), `new_implementation_part_3.md` (errata 15 fixes, IronTheme, IronModels, FeedbackExtras, Purge flight, BenchViewModel, GameOverlayService, icon/splash, Gradle, tests, DebugBench)
- Local IA: `docs/ui-ux.md` (10 pages + dialogs + HUD, file map)
- Repo archaeology:
  - `app/src/main/kotlin/com/ivarna/apexcore/MainActivity.kt` — AppStage SPLASH→ONBOARDING→MAIN, FreezeFramework + Shizuku binder listener, `WindowCompat.setDecorFitsSystemWindows(window,false)`
  - `app/src/main/kotlin/com/ivarna/apexcore/ui/shell/MainScreen.kt` — tab host (HOME/GAMES/OVERLAY/SETTINGS), full-screen overlays (RamFree/Tune/Privacy), haze chrome, GlobalBackendDropdown, setup/pin/onboarding sheets
  - `app/src/main/kotlin/com/ivarna/apexcore/ui/theme/Color.kt` — Zen Organic tokens (sage/teal, light+Dark), to be superseded
  - `app/build.gradle.kts` — compose-bom 2024.11.00, activity-compose 1.9.3, minSdk 24 target 36, haze 1.0.0, no splashscreen/profileinstaller yet
  - `app/src/main/AndroidManifest.xml` — no `enableOnBackInvokedCallback`, theme `Theme.App`, permissions include SYSTEM_ALERT_WINDOW/KILL_BACKGROUND_PROCESSES, services: GameOverlayService, RamFillerService(:ramfiller)
  - `app/src/main/kotlin/com/ivarna/apexcore/` — 95 files: fps/, freeze/ (12), games/ (8), ram/ (5), thermal/, tune/ (12), ui/components (8), ui/home (5), ui/onboarding, ui/overlay, ui/settings, ui/shell, ui/splash, ui/theme
  - `docs/plans/` existing plans (apexcore-fixes, T12-*, font-rotation)

---

## 1. Architecture Discovery

### 1.1 Current architecture (authoritative files)

- **Entry:** `MainActivity.kt` owns `AppStage` (ordinal-saved), `ThemeMode` + `lightTankBg`, `ApexCoreTheme(darkTheme)`, `AnimatedContent` stage transition 350ms fade. `onResume` re-detects FreezeFramework.
- **Shell:** `ui/shell/MainScreen.kt` is tab host + overlay router. State: `State(IDLE/BOOSTING/RESULT)`, `Tab(4)`, `backendName`, `lastResult: FreezeResult?`, `showRamFree/Tune/Privacy`, `globalBackendPref` (prefs key `preferred_backend`), `hazeState`. Content uses `Modifier.haze()`, chrome uses `hazeChild()`. Top/Bottom chrome are `ZenTopBar` / `ZenBottomNav` with `AnimatedVisibility` slide+fade.
- **Home:** `ui/home/HomeScreen.kt` — MemoryLeafPair, StatusPebble line, elevation banner, PebbleButton vs UnifiedResultCard, ToolRows, DeviceThermalCard.
- **Games:** `games/GamesScreen.kt` — Search field, Segment GAMES/ALL APPS, HorizontalPager carousel (center + adjacent previews), resource demand meter, ALLOCATE & LAUNCH + shutter animation, dialogs: AddGamePicker/WhitelistPicker.
- **Overlay:** `ui/overlay/OverlayScreen.kt` — GlassCard permission + HUD preview START/STOP via `GameOverlayService`.
- **Settings:** `ui/settings/SettingsScreen.kt` — ThemeSegmentedControl, Light tank glass switch, ActiveModeCard, diagnostics rows, legal/about/tour.
- **Tune:** `ui/tune/TuneScreen.kt` — full-screen, LazyColumn categories, MachinedToggle per TuneSpec, session stamp.
- **Ram:** `ram/RamFreeScreen.kt` + `RamFillerManager/Service` — gauge, progress phases (PreFreeze/Filling/Holding/Releasing/Done), mode selector.
- **Privacy:** `ui/legal/PrivacyPolicyScreen.kt` — LazyColumn markdown render from `assets/privacy_policy.md`.
- **Dialogs:** `SetupDialog.kt` (probes every 1200ms), `WhitelistPickerDialog`, `AddGamePickerDialog`.
- **HUD Service:** `games/GameOverlayService.kt` — `TYPE_APPLICATION_OVERLAY`, draggable rail, shows FpsRepository/ThermalMonitor data, not compose.

### 1.2 Design→Code mapping (12 surfaces)

| # | Surface | Spec § | Current file | New IRONWORK file(s) |
|---|---|---|---|---|
| 1 | Splash — Ignition | §7.1 | `ui/splash/SplashScreen.kt` | `ui/iron/splash/Ignition.kt` + `ui/iron/IronTokens.kt` + core-splashscreen theme |
| 2 | Onboarding — Field Manual | §7.2 | `ui/onboarding/OnboardingScreen.kt` | `ui/iron/manual/FieldManual.kt` + `ui/iron/Ink.kt` |
| 3 | Shell — BridgePlate + GearSelector | §7.3, §3.11-12 | `ui/shell/ZenTopBar/BottomNav` + `MainScreen.kt` | `ui/iron/shell/IronShell.kt` (BridgePlate, GearSelector, IronShell wrapper) |
| 4 | Home — The Bench | §7.4 | `ui/home/HomeScreen.kt` | `ui/iron/home/TheBench.kt` + `ui/iron/InstrumentDial.kt` + `Scales.kt` + `Effects.kt` + `TickerLine.kt` |
| 5 | Games — The Rack | §7.5 | `games/GamesScreen.kt` | `ui/iron/games/LaunchMatrix.kt` + `ShutterOverlay.kt` |
| 6 | Overlay — Optics Bench | §7.6 | `ui/overlay/OverlayScreen.kt` | `ui/iron/overlay/OpticsBench.kt` |
| 7 | Settings — Toolbox | §7.7 | `ui/settings/SettingsScreen.kt` | `ui/iron/settings/Toolbox.kt` |
| 8 | Tune — Tuning Room | §7.8 | `ui/tune/TuneScreen.kt` | `ui/iron/tune/TuningRoom.kt` |
| 9 | Ram Free — Pressure Room | §7.9 | `ram/RamFreeScreen.kt` | `ui/iron/ram/PressureRoom.kt` |
| 10 | Privacy — The Ledger | §7.10 | `ui/legal/PrivacyPolicyScreen.kt` | `ui/iron/legal/LedgerScreen.kt` |
| 11 | Dialogs → BenchSheets | §7.11 | `SetupDialog`, `WhitelistPickerDialog`, `AddGamePickerDialog` | `ui/iron/sheets/BenchSheet.kt` + `Sheets.kt` (KeySelector, PinSheet, AddSheet) |
| 12 | Floating HUD — Phantom Rail | §7.12 | `games/GameOverlayService.kt` + `games/GameOverlayService.RailView` | `games/GameOverlayService.kt` (rewired) + `games/RailView.kt` (compose-less View, callbacks, window owns sizing) |

Plus cross-cutting: App Icon (§7.13), Error/Toast/Context (§8, §37), DebugBench (§14, §45).

---

## 2. Component Library (to build in `ui/iron/`)

All components use IRONWORK tokens (§2). Build order respects dependencies.

| Component | Spec | File | Notes |
|---|---|---|---|
| **IronTokens** | §2.1-2.5, §4 | `ui/iron/IronTokens.kt` | Colors (Anvil/Bone/Ink/Signal/Phosphor/Ember/Brass/Scrim), FontFamily (Archivo/PlexMono/Caveat), IronType scale 8 tokens, IronShape (Plate/Slot), ChamferShape (GenericShape 4dp+10dp cut), IronMotion 5 springs + 2 easings. Grain fixed via §35 (ImageBitmap+asImageBitmap, ShaderBrush, drawWithCache, 4-5% alpha). SerialNumber (ANDROID_ID hash → XX-NNNN, S/N per install). LocalReducedMotion. |
| **Clack + HapticGate** | §5 | `ui/iron/Clack.kt` + `FeedbackExtras.kt:37.1` | `Clack(view)` with gate ≥80ms (extract `HapticGate` unit-testable). Verbs: tick(CLOCK_TICK), keyTap, row(VIRTUAL_KEY), longPress, confirm(CONFIRM/30→VIRTUAL_KEY), off(CONTEXT_CLICK/23), thud(EFFECT_HEAVY_CLICK/30→LONG_PRESS), no(REJECT/30→LONG_PRESS), purgeDone(thud→90ms→row). API guards exact (§5.2). |
| **Primitives** | §2.1,2.5,2.6 | `ui/iron/Primitives.kt` | LedDot (5 states, pulse loops: CHECKING 1200ms, BLOCKED double-blink 1000ms, LIVE 2000ms breathe), RisoText (§2.1 ghost+ink, audit via LocalRisoCount), EngravedText, Screw (4dp brass + cross), Glyphs (2dp stroke, tick terminals): Gauge/Cartridge/Rail/Caliper/Chevron/Loupe + Doodles (Ink.kt). |
| **Plates** | §3.1,3.2,3.13 | `ui/iron/Plates.kt` | EngravedPlate (anvil.700, hairline inset via drawWithCache, structural screws max 2/screen, caption mono-sm), PaperPlate (bone.100, deckle option, 1dp hard shadow + 8dp shadow, grain 5%), DeckleShape, StatRow, SerialFooter (PLATE nn · SCREEN · S/N · REV, centered, debug 5-tap hook for DebugBench). |
| **ChamferButton** | §3.3 | `ui/iron/Buttons.kt` | Height 56/44, fill signal.500→signal.700 pressed, chamfer silhouette, 2dp outline variant, 1dp catch-light, press scale 0.97 spring.machined 120ms, busy barber-pole 45° stripes 30dp/s, haptics confirm/row. |
| **StampLabel** | §3.4 | `ui/iron/StampLabel.kt` | 2dp border + 1dp inset, Archivo 900 0.1em, grain 12%, rot -3° (−8→−3 slam 260ms spring.stamp 700/0.68), vocabulary colors (phosphor/brass/ember/signal), pulse 1↔0.6 1200ms, TalkBack `contentDescription "$text, status"`. |
| **InstrumentDial** | §3.5 | `ui/iron/InstrumentDial.kt` | Canvas drawWithCache, diam 240/96 MiniDial, 64 ticks (major every 8th), numerals outside, bone blade + brass pivot 8dp+ink center, rest stop −6°, value motion spring.needle 320/0.62 overshoot, idle ±0.3° 4s drift, boosting hunt, ignition sweep 0→100→value 700ms +3 CLOCK_TICKs, Vellum palette via dialPalette() (§43.2). A11y `RAM pressure 62 percent` on ≥5% change. Long-press copy + COPIED toast. |
| **PressureScale** | §3.6 | `ui/iron/Scales.kt` | Ruler replacing progress bars, 8dp signal fill following brass flag, minor 5% / major 25%, labels optional, marker spring.drawer, haptic off() on major crossing. |
| **ThermometerStrip** | §3.7 | `ui/iron/Scales.kt` | Shared 30-60°C scale, two flags (BATT/CPU), >45°C ember hairlines + pulse + ticker THROTTLING. |
| **TickerLine** | §3.8 | `ui/iron/TickerLine.kt` | LED + mono text, overflow marquee 24dp/s 2s dwell, crossfade 160ms, liveRegion polite (§43.1), double-tap collapse to LED-only. |
| **ToolRow** | §3.9 | `ui/iron/ToolRow.kt` | 64dp, 40dp engraved icon slot, press scale 0.985 + slot inset anvil.950, long-press lift 1.02+shadow 400ms sheet, haptics VIRTUAL_KEY/LONG_PRESS. |
| **MachinedToggle/Segment** | §3.10 | `ui/iron/Controls.kt` | Toggle 52×28 track anvil.600→phosphor@30% + brass knob wobble ±3° 60ms, haptics confirm/off, locked bone.500+ember LED when unavailable. Segment groove anvil.950 + brass block spring.block + CLOCK_TICK, labels engraved active ink-filled. |
| **GearSelector** | §3.11 | `ui/iron/shell/IronShell.kt` | 64dp+nav inset, groove hairlines, brass block 44×4 sliding spring.block, active icon lift 1dp+label fade 120ms, tabs slide+fade 240 ease.wind RTL-aware. |
| **BridgePlate** | §3.12 | `ui/iron/shell/IronShell.kt` | Screws, wordmark+MK·II, backend chip LED+name+caret → dropdown sheet per-backend LEDs (Shizuku/Root readiness), switch → needle re-sweep + confirm. Edge-to-edge padding via statusBars inset. |
| **BenchSheet** | §3.14, §6.1 | `ui/iron/sheets/BenchSheet.kt` | Handle 32×4 brass, drag-dismiss velocity fling, predictive back scrub scale 1.0→0.92 + scrim fade, scrim 64%+grain. |
| **OdometerCounter** | §3.15 | `ui/iron/Effects.kt` | mono-lg digit columns 0-9 rolling spring.drawer stagger 30ms right→left, settle CONTEXT_CLICK. |
| **ShavingsParticles** | §3.16 | `ui/iron/Effects.kt` | 4-6dp parallelograms, pos/vel/angularVel, gravity 2400dp/s², bounce 0.15 one floor bounce, fade after bounce, cap 220, spawned from dial arc segments, same Canvas as dial, fixed FloatArray pool + withFrameNanos self-terminating. FlipCard (rotateX 90→0 320ms guarded). |
| **SearchSlot + IndexRail** | §3.17 | `ui/iron/Fields.kt` | SearchSlot anvil.950 radius.slot 2dp inner top shadow loupe icon mono placeholder SEARCH PACKAGES…, focus brass hairline+LED. IndexRail 20dp right-edge alphabet scrub 16dp hit, KEYBOARD_TAP per letter. Single gesture handler (§37.6). |
| **Skin/IronSurface** | §10 | `ui/iron/Skin.kt` + `IronTheme.kt` | IronFinish GRAPHITE/VELLUM, ThemeMode SYSTEM/VELLUM/GRAPHITE resolve(systemDark), IronSkin data, ironSkin(), LocalIronFinish/LocalPaperSurfaces, IronSurface (paper vs metal), finish-aware palettes. IronTheme wraps tree, caps fontScale 1.3×, detects systemReduced (ANIMATOR_DURATION_SCALE==0), sets status/nav icon tint via WindowCompat. LocalRisoCount audit (log if >1/scr). IronContentFrame 480dp centered (tablet 1.15× gauges). |
| **Chrome helpers** | §6, §8 | `ui/iron/Chrome.kt` | IronDropdown (Popup TopEnd), DropdownLedRow, BackArrow, LoadingNeedle (spinning needle, never skeleton). |
| **Feedback extras** | §5.1, §4.1, §8, §6.2, §9 | `ui/iron/FeedbackExtras.kt` | HapticGate, CeremonyGate (One ceremony at a time, suspend run), StampToast (StampToastState+Host 1400ms), ErrorSlip (paper, auto-dismiss 6s, RETRY/DISMISS), ContextSheet (4 actions), ironFocus (brass ring 2dp@4dp), clickableNoIndication. |
| **Effects choreography** | §7.4, §7.5 | `ui/iron/Effects.kt` + `ShutterOverlay.kt` | ShutterLaunch 520ms (plates close 0-160 stagger 40ms, hold 60ms heavy click, freeze broadcast 160-240 brass ticks, plates part 240-520), Purge ceremony 1400ms (button 0-120, wind-up +12% 0-180, shavings 180-500 heavy click 180, stamp 250-500, needle 400-1000, odometer 600-1400 + flight 320 into card). |

---

## 3. Motion System (§4)

- **Springs:** machined 1500/0.9, drawer 380/0.85, needle 320/0.62, stamp 700/0.68, block 480/0.8
- **Easings:** ease.wind (0.2,0.7,0.3,1.0) entrances, ease.slam (0.7,0,0.84,0) falling shavings, FastOutSlowIn standard
- **Durations:** 80 tick, 140 micro, 220 standard, 320 scene, 520 shutter, 1400 purge
- **One ceremony rule:** CeremonyGate enforces ≤1 >400ms animation/screen (Purge vs Shutter). Gate wraps LaunchedEffect ceremonies; second trigger no-ops while busy.
- **Reduced motion:** §4.4 FULL/REDUCED via LocalReducedMotion. REDUCED replaces: ignition sweep→fade, shavings+slam→fade −3° no particles, odometer roll→crossfade, shutter→200ms dim, marquee→truncate …, needle spring→linear 150ms. Auto-REDUCED when system animator scale 0. Setting `MECHANICAL MOTION` in Toolbox.

---

## 4. Haptics System (§5)

- Single source `Clack` + `HapticGate` (80ms floor). Battery rule enforced centrally.
- **Map (§5.2):** CLOCK_TICK (carousel/Gear/segment/scale-cross/hud-snap/ignition ×3/pull-threshold/carriage), KEYBOARD_TAP (IndexRail), CONFIRM/VIRTUAL_KEY (toggle ON fallback), CONTEXT_CLICK/VIRTUAL_KEY (toggle OFF), VIRTUAL_KEY (row), LONG_PRESS (row long-press), EFFECT_HEAVY_CLICK(30→LONG_PRESS) stamp landing, composition HEAVY→90ms→CLICK purgeDone, REJECT/LONG_PRESS freeze blocked, CONTEXT_CLICK sheet fling/drag, CONTEXT_CLICK odometer settle. Verify each via DebugBench.

---

## 5. Gesture System (§6)

- **System:** enableOnBackInvokedCallback true, edge-to-edge `enableEdgeToEdge()`, stretch overscroll kept, HUD rail gesture-exclusion rect minimal while dragging, keyboard/DPAD brass focus ring 2dp@4dp on all rows/toggles/dials.
- **In-app:** pull-to-purge (Home 120dp resisted, winds needle, threshold CLOCK_TICK, release CONFIRM or REJECT+bounce if not elevated, shows ElevationSlip), two-finger horiz swipe (Games GAMES↔ALL APPS), drag cartridge down ≥80dp tilt 8° → eject sheet (REMOVE/KEEP), long-press dial → copy stats+COPIED stamp, double-tap ticker → collapse, long-press card/row → context sheet, drag-to-dismiss+fling sheets, alphabet scrub, HUD rail drag magnetic snap 4 zones (25/50/75%+20%) + CLOCK_TICK, double-tap HUD expand/collapse, pull-to-reprobe Tune 96dp.

---

## 6. Theming, Typography, Color

### 6.1 Color (§2.1)
- **Graphite (default dark) tokens:** anvil 950 #0B0C0D, 900 #101113, 800 #17191C, 700 #1F2226, 600 #2B2F34, 500 #3A3F45; bone 50 #F5F0E4, 100 #EAE3D2, 300 #CFC6AE, 500 #A29880; ink 900 #201C16, 600 #4A4436; signal 500 #FF5A1F, 300 #FF8A50, 700 #B23A0F; phosphor 400 #7FE060 (+600 #3E9B2E for Vellum), ember 500 #F5402C, brass 400 #D9A75A, scrim 64%+grain. Contrast audit enforced: bone100/anvil900 13.2:1, ink900/bone100 13.8:1, signal/anvil 5.1:1 (large/bold only), phosphor/anvil 9.9:1.
- **Vellum mapping (§10):** canvas bone.50, plates bone.100, data readouts = ink plates (dark #201C16 with bone mono), accents phosphor600, gauges ink-on-paper, hardware brass+ink outlines. Bridge/Gear stay metal both finishes (instrument chassis).

### 6.2 Typography (§2.2)
- Archivo variable 500/700/900 (titles/labels/body/buttons), IBM Plex Mono 400/500/600 (numerics/serials/kickers), Caveat 700 (onboarding margin notes only). **No Inter.** Scale 8 tokens (display 34/38 +0.01 UPPER, title 22/26, label 13/16 +0.08 UPPER, body 15/22, caption 12/16 +0.02, mono-lg 40/40 +0.02 UPPER, mono 15/18 +0.04 UPPER, mono-sm 11/14 +0.06 UPPER, hand 18/20). Numerals always PlexMono tabular. Kickers mono-sm middle-dot `01 · PURGE ENGINE`.

### 6.3 Layout/shape/texture (§2.3-2.5)
- Base 4dp scale 4/8/12/16/20/24/32/48, margins 20, plate pad 16, section 32, between sections 48, max width 480 centered (tablet gauges 1.15×), edge-to-edge with inset padding.
- Radius: plate 4dp, chamfer 10dp@45° top-right (primary CTAs only, signature silhouette), slot 2dp, stamp 2dp, full for LEDs/pivots.
- Grain 128×128 PNG monochrome ~2KB @4% multiply/screen, stamps 12%. Stitch 4dp/3gap 1.5dp ink600, screws 4dp brass max 2/screen (Bridge+structural), hairline 1dp with gaps at labels. Riso recipe once/screen (signal ghost +1.5/+1dp + grain on ghost). Engraving recipe on dark (anvil950 shadow @40% +1dp + bone300 cut).

---

## 7. Font & Asset Configuration

- **Fonts:** `res/font/` add 6 subset TTFs — archivo_medium/bold/black, plexmono_regular/semibold, caveat_bold. Subset via `pyftsubset --unicodes="U+0020-007E,U+00B0,U+00B7,U+2014" --no-hinting` per font (see §42), ~215KB total. Update `IronTokens.kt` FontFamily to reference `R.font.*`. Remove GoogleFonts runtime loading (or keep fallback).
- **Grain:** §35 ImageBitmap (128px ARGB_8888 gray noise seeded 42), `ShaderBrush(BitmapShader(... REPEAT))`, `Modifier.ironGrain(alpha)` via drawWithCache. Delete old ALPHA_8/GLES branching.
- **Icon:** `drawable/ic_launcher_foreground.xml` (vector per §41: chamfered plate #17191C, engraved hairline #2B2F34, 16 ticks #CFC6AE, signal needle #FF5A1F, brass pivot #D9A75A + ink center #201C16), `ic_launcher_monochrome.xml` (all #FFFFFF), `mipmap-anydpi-v26/ic_launcher.xml` adaptive with background `@color/ic_launcher_bg` #101113 + monochrome, `colors.xml` adds ic_launcher_bg. Export PNG fallbacks for API 24-25 mipmap-*.
- **Splash:** `core-splashscreen` theme `Theme.ApexCore.Splash` (background ic_launcher_bg, animatedIcon foreground, postSplashScreenTheme Theme.ApexCore) + transparent status/nav, `installSplashScreen()` + `enableEdgeToEdge()` in MainActivity before super. Handoff into Ignition needle sweep continues motion.
- **Wordmark/tagline:** APEXCORE Archivo 900 riso, MK·II mono, FIELD-GRADE PERFORMANCE INSTRUMENTS mono-sm.

---

## 8. Build & Manifest Wiring

- **Gradle (`app/build.gradle.kts`):** bump `activity-compose` 1.9.3 → 1.10.0 (PredictiveBackHandler ≥1.8, plan pins 1.10.0), add `core-splashscreen:1.0.1`, `profileinstaller:1.4.1`, ensure `ui-text` present, `compose-bom 2024.10.00` (or keep 2024.11.00 if newer, but plan pins 2024.10.00 — verify no API delta), enable `testOptions.unitTests.isIncludeAndroidResources=true`, add test deps `roborazzi:1.26.0` + `robolectric:4.13`. Kotlin compose compiler `2.4.0` if not already implied by `org.jetbrains.kotlin.plugin.compose`.
- **Manifest:** `application android:enableOnBackInvokedCallback="true"`, activity theme → `Theme.ApexCore.Splash`, keep SYSTEM_ALERT_WINDOW + KILL_BACKGROUND_PROCESSES + FOREGROUND_SERVICE, add `<property specialUse>` already present. Ensure `android:windowSplashScreenAnimatedIcon` wired.
- **Base theme:** `IronTheme` wraps entire tree in MainActivity `setContent { IronTheme(themeMode, paperInserts) { … } }`. Remove `ApexCoreTheme` wrapper (or make it delegate to IronTheme). `Light tank glass` pref key reused for `paperInserts` semantics (same storage key, new label PAPER INSERTS).

---

## 9. Full-Screen & Navigation Behavior

- **Tab transitions:** slide+fade 240ms ease.wind, RTL-aware (sign flip on slideInHorizontally).
- **Full-screen overlays (Tune, Pressure Room, Ledger):** slide up 320ms, Bridge/Gear fade out, predictive back scrubs slide-down proportionally. Keep-screen-on + cancel on pause/dispose for Pressure Room; run TuneManager recovery on entry.
- **Onboarding replay:** overlay with close instead of skip, same FieldManual.
- **Setup trigger:** `setup_shown_v1` once, plus banner/dropdown fallbacks; probe every 1200ms while open.
- **Predictive back per-surface:** BenchSheet (scale 1→0.92+scrim), Tune/Pressure slips (END SESSION?), RamFree already has bench sheet behavior.

---

## 10. Data Wiring (ADAPT points — reuse existing singletons)

- **Freeze:** `FreezeFramework` — `detect()`, `isReady()/isElevated()`, `freezeAll(context)` / `freezeBackgroundApps(exclude)`, `activeBackend` flow, `setPreferredBackend(name)`, `resolver()` for probes. Backend readiness via `ShizukuFreezeBackend/RootFreezeBackend.isReady()`.
- **Fps/Privilege:** `FpsStack.get(context).syncPreferredBackend(pref)` follows top-bar selection; `FpsDaemonManager`, `PrivilegeModeStore` for Toolbox RUNNING MODE row.
- **Mem/Thermal:** `getSystemMemStats()` (ramUsedMb/total, swapUsed/Total), `ThermalMonitor.flow` (batteryC/cpuC) 1Hz/500ms, `RamFillerManager/Service` phases → PressureUiState mapping.
- **Tune:** `TuneManager`, `TuneSpecs.byCategory`, `capabilities/intents`, `setIntent`, `TuneSessionWatchdog`.
- **Games:** `GameManager.load()/scan`, `GameLauncher`, `listInstallableApps`, whitelist store.
- **Prefs:** `ThemePreferences` (ThemeMode), `OnboardingPreferences.isOnboardingCompleted`, `apexcore` prefs `preferred_backend`, `setup_shown_v1`, `hud_size/opacity/edge`, `GameOverlayService.isRunning` + fallback poll + canDrawOverlays.
- **New ViewModel:** `ui/iron/home/BenchViewModel.kt` (AndroidViewModel) owns `Mem`, `Ui(phase,elevated,backendName,backendLed,mem,freedFraction,lastOrder,batteryC/cpuC)`, 1s mem tick + thermal collect + redetect(), `boost()` → WorkOrderData mapping from FreezeResult, `ticker()` single-source strings. TheBench consumes `vm.ui.collectAsState()` + callbacks.

---

## 11. Implementation Phases (ordered, dependency-respecting)

### Phase A — Foundations (no UI yet, compiles)
1. Add fonts to `res/font/` + subset. Add `IronTokens.kt` (§1) + `IronTheme.kt` (§35) + `IronModels.kt` (§36). Fix grain shader (errata #1/2). Wire `IronTheme` around app tree in `MainActivity` (wrap existing content, keep current screens visible).
2. Update `build.gradle.kts` + `AndroidManifest.xml` (predictive back, splashscreen deps, test flags). Add icon drawables (`ic_launcher_foreground/monochrome`, `ic_launcher.xml`, `colors.xml`, `themes.xml` splash). Verify `installSplashScreen()` + `enableEdgeToEdge()` + status bar tint flips with finish.
3. Implement `Clack.kt` + `FeedbackExtras: HapticGate/CeremonyGate/ironFocus/clickableNoIndication` (§37.1/2/7/8). Verify 80ms gate unit test.

### Phase B — Primitives & Plates
4. `Primitives.kt` (LedDot/Riso/Engraved/Screw/Glyphs), `Plates.kt` (EngravedPaper/SerialFooter/StatRow), `Chrome.kt` (IronDropdown/BackArrow/LoadingNeedle), `Skin.kt` (IronSkin/IronSurface/IronContentFrame).
5. `Buttons.kt` (ChamferButton), `StampLabel.kt`, `Scales.kt` (PressureScale/ThermometerStrip), `TickerLine.kt` (with a11y liveRegion + collapse).

### Phase C — Hero Components
6. `InstrumentDial.kt` (§7) — with dialPalette Vellum patch (§43.2), idle drift/hunt loops conditional, drawWithCache precompute. `Effects.kt` (Odometer/Shavings/FlipCard) + `Fields.kt` (SearchSlot/IndexRail single handler) + `Ink.kt` (Doodles/FigArtwork/FigFrame/Keys).

### Phase D — Shell
7. `ui/iron/shell/IronShell.kt` — BridgePlate + GearSelector + IronShell container. Port `MainScreen.kt` routing into IronShell (preserve HazeState removal: grain replaces haze; or keep haze only for BenchSheet scrim if spec allows. Default: drop haze per spec §1.6 glassmorphism ban except HUD floating). Keep all state hoisted (currentTab, backendName, lastResult, dialogs) so screens stay dumb. Add `IronScreen` wrapper per screen for riso audit.

### Phase E — Screens (in dependency order)
8. **Ignition (Splash):** `ui/iron/splash/Ignition.kt` — plate scale 0.96→1 spring.machined, needle mini-sweep, wordmark stagger + riso ghost settle, reduced-motion single fade. Route after 550ms.
9. **Field Manual (Onboarding):** `ui/iron/manual/FieldManual.kt` — bone paper + stitched binding + ruler pager + 5 pages + parallax (figure 0.4, doodles 0.7), KeyCards (paper variant) + READY slam + probe 1200ms loop + SKIP/ENTER.
10. **The Bench (Home):** `ui/iron/home/TheBench.kt` — Bridge + ticker + InstrumentDial (240) + MiniDial SWAP + ChamferButton + ToolRows + ThermometerStrip + WorkOrder card (deckle, riso header, stats, PURGE AGAIN, tap flip) + ElevationSlip banner + BenchViewModel + purge ceremony 1400ms + odometer flight (§38) + pull-to-purge (120dp) + ticker double-tap + dial long-press copy + ticker change shake/haptics. Gate whole ceremony with CeremonyGate.
11. **Launch Matrix (Games):** `ui/iron/games/LaunchMatrix.kt` — SearchSlot, add/pin row, MachinedSegment GAMES/ALL APPS, HorizontalPager with parabolic arc + scale 0.8 α0.4 + RenderEffect blur adjacents API31+, lens brass ring + wash tint, demand meter, caret drag eject ≥80dp tilt, long-press context sheet, shutter overlay 520ms, empty states doodles. Two-finger swipe toggle.
12. **Optics Bench (Overlay):** `ui/iron/overlay/OpticsBench.kt` — permission stamp card + live preview rail (real HUD instance constrained to dashed window, draggable snap, double-tap expand), machined toggle ON/OFF, size/opacity/edge segments+slider.
13. **Toolbox (Settings):** `ui/iron/settings/Toolbox.kt` — header, APPEARANCE (MachinedSegment SYSTEM/VELLUM/GRAPHITE + PAPER INSERTS toggle), ACCESS (RUNNING MODE + diagnostics LedRows with CHECK→needle sweep), LEGAL (ToolRow Ledger), ABOUT (engraved plate NO ADS stamps).
14. **Tuning Room:** `ui/iron/tune/TuningRoom.kt` — top bar ↻ probe sweep, description, SESSION ACTIVE pulse stamp + elapsed, drawer-pull headers sorted by availableCount, MachinedToggle rows (unavailable de-energized + ember LED), paper slip footer, STATE live, pull-to-reprobe 96dp.
15. **Pressure Room:** `ui/iron/ram/PressureRoom.kt` — TubeManometer (RAM/SWAP animated levels), StateRailway (5 stations + brass carriage + CLOCK_TICK), mode chip dropdown, pre-freeze toggle, action state machine START/HOLD/RELEASE/CANCEL, window keep-screen-on + lifecycle cancel + predictive back END SESSION? slip, result odometer.
16. **Ledger (Privacy):** `ui/iron/legal/LedgerScreen.kt` — bone.50 paper, header THE LEDGER + PRINTED OFFLINE stamp, markdown pipeline same but restyled (Archivo ink, hairlines, brass bullets, code chips, dark plates for code blocks, mono tables, signal squiggle links, LaTeX chips).
17. **BenchSheets:** `ui/iron/sheets/Sheets.kt` — Setup sheet (key cards metal variant), Pin sheet (SearchSlot+IndexRail+brass pin toggles+DONE count), Add sheet (multi-select + ADDED 3 stamp). All as BenchSheet bottom sheets with handle+drag+predictive scrub.
18. **Phantom Rail (HUD):** rewire `GameOverlayService.kt` (§40) — window owns sizing (12dp collapsed, panelWidthDp S/M/L), callbacks onExpand/onDrag/onDragEnd, magnetic snap 4 zones + CLOCK_TICK, applyFit(prefs) size/opacity, collapsed 2dp brass filament 8% glow + pulse loops, expanded smoked-glass column + FPS/sparkline/CPU bars stagger, DEFROST snow doodle → DEFROSTED flash, auto-min 5s, gesture exclusion while dragging, ignore TalkBack, shared prefs hud_*.

### Phase F — Hardening & Polish
19. Apply 15 errata (§34) cross-check, Vellum dial palette patch, TickerLine a11y, IndexRail single handler, Chamfer border2, Effects flip guard, etc. Verify one riso/screen audit silent.
20. `DebugBench.kt` (§45) debug-only, 5-tap SerialFooter hook, haptic grammar rows + ceremony playground + sheet demo.
21. Baseline profile (profileinstaller + macrobenchmark: cold start → Home → purge → Games swipe → settings scroll) for 900ms cold start / 60fps ceremony guard.

---

## 12. FEATURE_SET (complete — every surface, component, system)

> Implementation MUST match `docs/design/new_design.md` and code packs `new_implementation_part_{1,2,3}.md` verbatim where code is provided (“use the implementation code as it is”). Deviations require explicit errata note.

**Foundations:**
- [ ] Iron color tokens (15 colors + scrim + phosphor600), grain tile 128px, ironGrain Modifier via ImageBitmap+ShaderBrush+drawWithCache
- [ ] Type scale 9 tokens (Display/Title/Label/Body/Caption/Mono-lg/Mono/Mono-sm/Hand) with Archivo/PlexMono/Caveat families, numerals always mono tabular
- [ ] Shape tokens (Plate 4dp, Chamfer 4+10 cut, Slot 2dp, Stamp 2dp, full) + ChamferShape GenericShape exact path
- [ ] Motion springs 5 + easings 2 + duration scale 6 + one-ceremony gate
- [ ] Haptics grammar 14 rows via Clack/HapticGate (80ms floor), API guards exact
- [ ] Layout grid (4dp scale, margins 20, plate pad 16, rhythm 32/48, max 480 centered, 1.15× tablet gauges)
- [ ] Texture rules (grain 4-5%, stitch, screws ≤2/screen, hairlines with gaps, misregistration once/screen, rot spice −3°, serial S/N per install, zero gradients, one riso/screen)

**Component Library (18 components):**
- [ ] EngravedPlate / PaperPlate(+deckle) / IronSurface
- [ ] ChamferButton (Primary/Outline, busy barber-pole, catch-light)
- [ ] StampLabel (+pulse, slam spring.stamp, grain 12%)
- [ ] InstrumentDial (+MiniDial 96dp, rest stop, energized/de-energized, freed arc, boosting hunt, ignition sweep, idle drift, Vellum palette)
- [ ] PressureScale + ThermometerStrip
- [ ] TickerLine (LED+marquee+liveRegion+collapse)
- [ ] ToolRow (64dp, icon slot, press/long-press)
- [ ] MachinedToggle + MachinedSegment (wobble, spring.block)
- [ ] GearSelector (bottom nav groove+brass block)
- [ ] BridgePlate (screws+chip dropdown)
- [ ] SerialFooter (PLATE nn · SCREEN · S/N · REV)
- [ ] BenchSheet (handle+drag+predictive scrub)
- [ ] OdometerCounter + ShavingsParticles (+FlipCard)
- [ ] SearchSlot + IndexRail + LoadingNeedle
- [ ] Feedback extras: StampToast, ErrorSlip, ContextSheet, ironFocus, clickableNoIndication

**Motion & Gestures:**
- [ ] All §4 spring/easing/duration tokens used; one ceremony enforced (Purge 1400 OR Shutter 520, never overlap)
- [ ] Reduced motion FULL/REDUCED + auto-reduce on animator scale 0; all §4.4 substitutions
- [ ] System gestures (predictive back scrub 1→0.92+scrim, edge-to-edge behind bars, gesture exclusion HUD, keyboard/DPAD focus ring)
- [ ] In-app gestures (pull-to-purge 120dp, two-finger swipe, drag cartridge 80dp/8°, long-press dial copy, double-tap ticker, long-press rows, drag-dismiss, alphabet scrub, HUD drag snap 4 zones + double-tap, pull-to-reprobe 96dp)

**Theming:**
- [ ] IronFinish GRAPHITE/VELLUM + ThemeMode SYSTEM/VELLUM/GRAPHITE resolve, IronSkin Graphite/Vellum, LocalIronFinish/PaperSurfaces/LocalReducedMotion/LocalRisoCount
- [ ] Bridge/Gear stay metal both finishes; content surfaces flip; data readouts stay metal (ink plates in Vellum)
- [ ] IronTheme wraps tree, caps fontScale 1.3×, flips status/nav icon tint, provides density audit

**12 Surfaces:** (each: layout per §7, states per §8, a11y per §9, serial footer, haptics, gestures)
- [ ] 01 Splash Ignition — 550ms sequence (grain 0-120, needle 80-480, wordmark stagger 120-360, tagline 360-550), reduced 200ms fade
- [ ] 02 Field Manual Onboarding — 5 pages cover+4 FIGs, bone paper + binding + ruler pager + parallax 0.6/0.3, margin notes Caveat + doodles, Key Selector with READY slam + pref write + framework sync, skip/replay
- [ ] 03 Shell (Bridge+Gear) — screws, dropdown readiness LEDs, segment sliding, tab slide+fade 240 ease.wind RTL-aware, full-screen overlay slide behavior
- [ ] 04 The Bench Home — dial 240+mini, ticker states (ready/blocked/purging/result), banner ElevationSlip, Work Order deckle, Purge ceremony 1400 (wind-up/shavings/stamp/needle/odometer flight shared-element) + two-stage haptic, pull-to-purge, flip reset
- [ ] 05 The Rack Games — SearchSlot, segment, pager arc+blur adjacents+RenderEffect, lens brass, demand meter, drag eject, context sheet, Shutter 520 (plates close/hold/freeze ticks/part), empty states
- [ ] 06 Optics Bench Overlay — permission READY/ACTION REQUIRED stamp, preview rail live+drag snap, machined toggle, fit segments (S/M/L, opacity slider, LEFT/RIGHT), telemetry 500ms
- [ ] 07 Toolbox Settings — APPEARANCE (segment+sizes crossfade 220), PAPER INSERTS, ACCESS (RUNNING MODE+diagnostics LED rows+CHECK needle), LEGAL/ABOUT stamps, tour replay
- [ ] 08 Tuning Room — categories sorted by availableCount, SESSION ACTIVE pulse + timer, toggles with wobble+haptics, unavailable de-energized, paper slip, pull-to-reprobe
- [ ] 09 Pressure Room — TubeManometer, StateRailway 5 stations+b carriage+tick+crossfade, mode dropdown with LEDs, pre-freeze toggle, action state machine 4 labels, keep-screen-on+lifecycle cancel+back slip, result GB
- [ ] 10 Ledger Privacy — bone paper full-screen, markdown restyled (hairlines/brass bullets/dark code plates/mono tables/signal squiggle links), offline printed stamp
- [ ] 11 BenchSheets — Setup/Pin/Add as BenchSheet (handle/grain scrim/drag/predictive), Pin with IndexRail, Add with stamp before dismiss
- [ ] 12 Phantom Rail HUD — collapsed 12dp strip / 2dp filament+8% glow+brass/ember pulse, expand 350 smoked-glass+stagger, FPS/ram sparkline/cpu bars, DEFROST snow+flash, drag snap 4 zones, auto-min 5s, minimal exclusion, window-owned sizing

**Other:**
- [ ] App Icon + monochrome + Store 512 + splash theme handoff (§7.13, §41)
- [ ] Font subset + asset pipeline (§42)
- [ ] DebugBench QA harness (5-tap, haptic rows, ceremonies, sheet)

---

## 13. ACCEPTANCE_CRITERIA (objective, testable — maps to §14 QA + spec)

### 13.1 Build & Bundle
- [ ] `./gradlew assembleDebug` clean with pinned BOM (`compose-bom:2024.10.00`, `activity-compose:1.10.0`, `core-splashscreen:1.0.1`, `profileinstaller:1.4.1`), compileSdk 36 minSdk 24, no lint errors in `ui/iron/*`
- [ ] APK ≤ 1.5 MB; fonts subset total ≤ 230KB; grain bitmap single instance; baseline profile generated
- [ ] `AndroidManifest` has `enableOnBackInvokedCallback=true`, splash theme on activity, `SYSTEM_ALERT_WINDOW` retained, no exported receiver/service regression

### 13.2 Theme & Typography
- [ ] `IronTheme` wraps whole tree; toggling SYSTEM/VELLUM/GRAPHITE crossfades 220ms + needle re-sweep; `isSystemInDarkTheme()` respected for SYSTEM
- [ ] Status/nav icon tint flips with finish (dark ink icons on Vellum, light on Graphite) via WindowCompat
- [ ] FontScale capped at 1.3× (verify by setting device font scale 1.5× → dials/text do not overflow, dials shrink gracefully)
- [ ] Archivo/PlexMono/Caveat loaded from `res/font/`; no Inter; numerals always Plex Mono tabular; kickers `NN · WORD` format; all `sp` tokens scale with density cap

### 13.3 Colors & Contrast
- [ ] Sampled screens meet contrast audit: bone100/anvil900 ≥ 12:1, ink900/bone100 ≥ 12:1, phosphor/anvil ≥ 8:1 (screenshot + color picker)
- [ ] Vellum dials render ink-on-paper (minor ink600@35%, major ink900, numeral ink600, needle ink900) — no Graphite colors leak in Vellum

### 13.4 Motion
- [ ] CeremonyGate: triggering Purge blocks concurrent Shutter and vice-versa; second trigger no-ops while busy; `busy` flag visible in DebugBench
- [ ] Purge ceremony hits 60fps (GPU profiling < 1% jank, ≤ 16ms frame), needle overshoot visible, shavings cap 220 particles, odometer flight lands in card slot without stutter
- [ ] Reduced motion: setting MECHANICAL MOTION=REDUCED replaces all 6 ceremony types per §4.4 table (manual QA checklist); auto-REDUCED when animator duration scale 0 (unit test via Settings.Global read)

### 13.5 Haptics
- [ ] `HapticGateTest` passes: allow() false at 50ms, true at 90ms (seeded clock)
- [ ] Every §5.2 row fires correct constant on DebugBench (13 verbs), verified on device API 30+ and fallback path on API <30 (CLOCK_TICK→KEYBOARD_TAP, CONFIRM→VIRTUAL_KEY, etc.)
- [ ] Pull-to-purge threshold fires exactly once per pull; drag gestures tick on threshold crossing, not continuously

### 13.6 Components & States
- [ ] One riso per screen audit: `LocalRisoCount` log stays at 1 after full walkthrough (Splash→Manual→Home→Games→Overlay→Toolbox→Tune→Pressure→Ledger), second riso logs warning in debug
- [ ] StampLabel semantics: TalkBack reads `"<text>, status"`; ticker liveRegion polite announces on change; doodles/grain invisibleToUser
- [ ] InstrumentDial a11y: `contentDescription "RAM pressure 62 percent"`; announces only on ≥5% change; long-press copies stats + shows COPIED stamp 1400ms
- [ ] Ticker collapsed state persists double-tap toggle; marquee vs truncate per motion mode
- [ ] ErrorSlip auto-dismiss 6s; ContextSheet has LAUNCH/PIN/REMOVE(danger)/COPY PACKAGE; focused elements show 2dp brass ring @4dp offset

### 13.7 Screens Functional
- [ ] Splash routes correctly: first launch → Field Manual, returning → Main; no flash of old Zen screen
- [ ] FieldManual: pager 5 pages swipeable + parallax, ruler pager ticks, page 4 key selection writes `preferred_backend` pref + syncs FreezeFramework/FpsStack + shows READY slam; skip path completes onboarding with null backend; replay shows close not skip
- [ ] Bench idle/elevated states: elevated=live dial+phosphor arc+READY ticker; not-elevated=de-energized needle at −6°, bone ticks, amber pulse `◌ CONNECT…`, ElevationSlip banner with key buttons → Setup sheet; boosting signal shimmer+barber-pole; result WorkOrder deckle with riso header+E3% stamp
- [ ] Bench purge end-to-end: freezeAll mocked in instrumented test produces WorkOrder (+1.4GB example, apps/duration/skipped/failed), ticker transitions `FROZEN 12 APPS · FREED 1.4 GB` + `ALREADY OPTIMIZED` / `FREEZE BLOCKED` with ember shake 2×8dp+REJECT
- [ ] Games: empty `NO ITEMS FOUND` + doodle+SCAN; populated shows segment GAMES/ALL APPS with two-finger swipe; carousel adjacent blur API31+ else no crash; search filters; drag down eject sheet REMOVE unregisters; launch triggers Shutter 520 + HUD attach
- [ ] Overlay: permission granted shows READY+phosphor LED else ACTION REQUIRED+ember+GRANT CTA opens overlay settings; START/STOP toggles service preview + machined toggle haptics; live preview drag snap + double-tap; service sync polls canDrawOverlays+isRunning and clears stale pref
- [ ] Toolbox: diagnostics CHECK runs 700ms needle sweep; theme segment crossfade; PAPER INSERTS toggle same pref key; about shows `v1.4 · S/N … · MACHINED IN 1.2 MB · NO ADS · NO TRACKING`
- [ ] TuningRoom: categories sorted availableCount desc; available rows toggle intent writes + haptics; unavailable locked+ember LED+mono reason; pull-to-reprobe sweeps needle ×3; slip footer visible
- [ ] PressureRoom: tube mercury follows 1s mem refresh; StateRailway carriage slides + CLOCK_TICK per station + label crossfade; mode dropdown shows all RamFillModes with LED readiness; pre-freeze toggle; action labels START→HOLD→RELEASE→CANCEL cycle per phase; keep-screen-on while running; cancel on pause/back with END SESSION? slip
- [ ] Ledger: renders `privacy_policy.md` with new styles (dark code plates, brass bullets, signal squiggle links, mono tables, LaTeX chips); no glass; stretch overscroll only
- [ ] BenchSheets: all 3 open as bottom sheets with handle+grain scrim, drag-dismiss+fling, predictive back scrub 1→0.92; Pin sheet IndexRail scrub fires KEYBOARD_TAP per letter; Add sheet `ADDED 3` stamp 240ms before dismiss

### 13.8 HUD Service
- [ ] Collapsed width 12dp hit area (visual 2dp filament) lets game touches pass beside it; expanded width S/M/L (50/58/66dp) updates window layout synchronously via `resizeWindow()`
- [ ] Drag snappable 4 zones (20/40/60/80% height) with CLOCK_TICK on settle; registers gesture-exclusion rect only while dragging
- [ ] Telemetry loop 500ms pushes FPS/RAM/CPU; thermal >45 ember pulse; sparkline/equalizer animate (bars spring animated)
- [ ] Auto-minimize reverse expand after 5s idle (fade 100ms + shrink 150ms); DEFROST unfreezes + phosphor flash 600ms + CONFIRM
- [ ] Service `isRunning` static + fallback check in Overlay/Toolbox stay consistent on external kill

### 13.9 Predictive Back & Edge-to-Edge
- [ ] System back scrubs current transition proportional to finger (sheets scale+scrim, full-screen slide-down, RamFree END SESSION? slip); commit=dismiss, cancel=springs back — tested with `PredictiveBackHandler` on API 33+ and graceful on 24-32
- [ ] Content draws behind bars on all screens; Bridge pads statusBars inset, Gear pads navigationBars inset; hairline sits under status bar
- [ ] Stretch overscroll preserved everywhere, no glow fallback

### 13.10 Testing (§44 + QA harness)
- [ ] Unit: `HapticGateTest` (80ms floor), `SerialNumberTest` (same id deterministic, different ids differ, format `XX-NNNN` uppercase A-Z without I/O)
- [ ] Screenshot (Roborazzi+Robolectric Pixel7 SDK34): `dial_graphite_62`, `dial vellum de-energized`, `StampLabel status`, plus goldens for ChamferButton idle/pressed/busy, BenchSheet, cartridge — `./gradlew testRoborazzi` green, recorded goldens in repo
- [ ] Accessibility: scanner audit (contrast, target size ≥48dp, brass ring, content descriptions) + TalkBack walkthrough checklist
- [ ] Perf: cold start ≤ 900ms with baseline profile, purge/shutter maintain 60fps (macrobenchmark trace), grain/shavings have zero allocation at idle (verify with allocation tracker)
- [ ] DebugBench reachable only in debug via 5 taps on any SerialFooter within 3s; all haptic verbs fire from it; ceremony buttons replay sweep/stamp/burst/sheet

### 13.11 Migration & Regression
- [ ] Old Zen components (`ZenTopBar/BottomNav`, `GlassCard`, `Haze`) fully removed or gated — no leftover glassmorphism on primary surfaces (blur only in HUD expanded glass, per spec allowed)
- [ ] No regression: Freeze/Fps/Ram/Tune/Game/Thermal pipelines unchanged behind UI; existing instrumented tests still pass
- [ ] `preferred_backend` pref migration `standard→null` retained; onboarding flag and setup_shown_v1 logic unchanged

---

## 14. Risks & Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Canvas heavy dial re-caching on every frame | Jank during ceremonies, >1% jank budget | `drawWithCache` precomputes ticks/numerals/rings; needle only mutates via deferred `Animatable` reads — verify with profiler; cap grain draw to root once |
| Grain shader tiling cost on large screens | Fill-rate jank | Use compose `BitmapShader+ShaderBrush` with `TileMode.Repeated`, `drawWithCache` single brush per screen, 4% alpha; never per-composable |
| Haptic constants unavailable on minSdk 24 | Crash/No-op | Gate with `Build.VERSION.SDK_INT >= 30/23/21` + fallback table (§5.2 last column) |
| RenderEffect blur on API <31 Adjacents | Crash | Guard with `if (Build.VERSION.SDK_INT >= 31)` else α/scale only |
| PredictiveBackHandler requires activity-compose≥1.8 | Compile fail | Pin to 1.10.0; guard with try/catch GestureCancellationException; test on API 24 emulator |
| Icon anydpi-v26 not applied on API 24-25 | Wrong icon | Export PNG fallbacks to mipmap-* |
| Font subset clipping (U+00B0/00B7/2014) | Missing °·— in readouts | Verify subset includes those; add U+25A0 blocks if degree missing |
| One riso/screen rule easy to violate | Visual noise | `LocalRisoCount` audit logs in debug; CI fails if log contains IRONWORK warning |
| HUD window ownership (width before view attached) | Layout flicker | `resizeWindow()` only after `wm.addView`; panelWidthDp read from prefs via applyFit |
| Shavings 220 particles × withFrameNanos leak | Idle loop never stops | Self-terminating loop: stops when all particles dead or out of bounds; cap + early exit |

---

## 15. Rollout Order (for PR slicing)

1. **PR A — Foundations:** fonts+grain+tokens+theme+manifest+icon+splash — ship as non-breaking (IronTheme wraps old UI).
2. **PR B — Primitives+Plates+Buttons+Stamps** — visual-only, storybook screenshots.
3. **PR C — Shell + Ignition + Manual** — first user-visible IRONWORK (splash+onboarding+shell, home still old but framed).
4. **PR D — Bench + Games + Overlay** — core daily surfaces; enables purge/shutter ceremonies.
5. **PR E — Toolbox + Tuning + Pressure + Ledger** — full-screen surfaces.
6. **PR F — Sheets + HUD + DebugBench + tests** — dialogs, floating rail, harness, goldens.

Each PR gated by its acceptance slice above; keep old Zen files until final PR then delete.

---

## 16. Out of Scope (deliberate, REV E)

Automation broadcasts (non-exported), boot scheduling, network/battery graphs beyond ThermometerStrip, accessibility-service freezing, Wear/widget, sound design (haptics is the audio channel), i18n non-Latin uppercase bypass, RTL manual testing beyond tab slide sign.

---

## 17. File Map (new files to create)

```
ui/iron/
  IronTokens.kt
  IronTheme.kt
  IronModels.kt
  Skin.kt
  Clack.kt
  FeedbackExtras.kt
  Primitives.kt
  Chrome.kt
  Ink.kt
  Plates.kt
  Buttons.kt
  StampLabel.kt
  InstrumentDial.kt
  Scales.kt
  TickerLine.kt
  ToolRow.kt
  Controls.kt
  Effects.kt
  Fields.kt
  ShutterOverlay.kt
  splash/Ignition.kt
  shell/IronShell.kt
  manual/FieldManual.kt
  home/TheBench.kt (+ BenchViewModel.kt)
  games/LaunchMatrix.kt
  overlay/OpticsBench.kt
  settings/Toolbox.kt
  tune/TuningRoom.kt
  ram/PressureRoom.kt
  legal/LedgerScreen.kt
  sheets/BenchSheet.kt
  sheets/Sheets.kt
  debug/DebugBench.kt
games/
  RailView.kt   (patched)
  GameOverlayService.kt (rewired)
res/
  font/archivo_*.ttf (6)
  drawable/ic_launcher_foreground.xml (+ monochrome)
  mipmap-anydpi-v26/ic_launcher.xml
  values/colors.xml (ic_launcher_bg)
  values/themes.xml (splash)
```

---

## 18. Definition of Done (matches §46 final checklist)

- [ ] 15 errata applied; compiles clean pinned BOM
- [ ] IronTheme wraps tree; bar icons flip; fontScale capped 1.3×
- [ ] Exactly one riso/screen — debug log silent full walkthrough
- [ ] Purge flight shavings→stamp→odometer→slot (CeremonyGate, 60fps, two-stage haptic)
- [ ] Every haptic verb fires from DebugBench matching §5.2
- [ ] HUD resizes on expand, snaps 4 zones, auto-min 5s, DEFROST, predictive back scrubs sheets/Tune/Pressure
- [ ] Roborazzi goldens green; HapticGate/SerialNumber tests green; Vellum dial contrast OK; APK ≤1.5MB; cold start ≤900ms with baseline profile

