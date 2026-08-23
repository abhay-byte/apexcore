# IRONWORK Fix Pass — Implementation Plan

| | |
|---|---|
| **Doc No.** | AC-PLAN-IRONWORK-FIX · PASS 1 · ITER 3 |
| **Spec** | AC-DS-004 REV D (`docs/design/new_design.md`) §10 + Packs I–III (`Skin.kt`, pack III Vellum rule) |
| **Parent plan** | `docs/plans/ironwork-redesign-plan.md` |
| **Prior review** | `docs/reviews/ironwork-fix-pass-plan-review.md` (ITER 3 — **APPROVE**) |
| **Status** | APPROVED — worker can start |

Surfaces exist under `ui/iron/`. This pass is **not a redesign**. It is a correctness + efficiency pass against the spec. Scope: animation leaks, grain, theming (incl. onboarding), reduced motion, page/tab cost, leftover Zen.

ITER 2 pins architecture. ITER 3 pins the finish model: spec is **two finishes, three Toolbox stations**. SYSTEM is not a third look.

---

## 0. Verdict of current build

IRONWORK files landed. 12 surfaces exist. IronTheme wraps the tree. Zen screens gone. Haze gone.

**Not done.** Animation not optimised. **Vellum (2nd finish) is a canvas swap only** — content still Graphite tokens, so Vellum is cream-on-cream / looks broken. **SYSTEM is not a 3rd finish**; on a dark phone it resolves to Graphite and **must** look identical to picking GRAPHITE. Onboarding locked to paper. BOOST button burns frames idle.

Do not add a third paint job. Do not add features. Fix the list below.

---

## 0.1 Pinned decisions (ITER 2 — do not re-litigate)

| # | Topic | Decision |
|---|---|---|
| D1 | Theme owner | `IronTheme` is the **only** provider of `LocalIronFinish`. `IronShell` **must not** wrap in `Crossfade(finish)` or re-provide the local. Shell animates canvas color only (`animateColorAsState`, 220ms). |
| D2 | Tab compose | Keep `GearTabTransition` / `AnimatedContent` (unmount = cheap memory). Do **not** compose all four tabs. Do **not** add HorizontalPager. Pass `active: Boolean` into `TheBench` and `OpticsBench`. `rememberSaveable` for pager index / tickerWide / segment. HorizontalPager keep-alive is out of scope unless profiler shows 240ms crossfade jank >16ms. |
| D3 | PAPER INSERTS | Fresh install default **false** (metal Graphite). Existing stored bool preserved via `prefs.contains(KEY)`. Migration lives in `ThemePreferences`. |
| D4 | Riso count | `IronScreen` resets count. `RisoText` increments **once per instance** via `DisposableEffect(Unit)`. Bridge wordmark → `EngravedText`. |
| D5 | Field Manual | Stays paper (spec §7.2). Do not theme-swap booklet. |
| D6 | Reduced shutter | Skip plates. 200ms dim scrim then `onSeam`. |
| D7 | Reduced ignition | 200ms fade only, skip sweep, `delay(200)` then route. |
| D8 | Reduced needle | `tween(150, LinearEasing)` for value + freed tracking. |
| D9 | Finish count | Spec §10: **one engine, two finishes** (GRAPHITE / VELLUM). Toolbox has three stations (SYSTEM / VELLUM / GRAPHITE). SYSTEM is a **resolver**, not a third `IronSkin`. Do **not** invent a third look. SYSTEM + dark OS = Graphite (same pixels as GRAPHITE). SYSTEM + light OS = Vellum (same pixels as VELLUM). If those pairs differ, the bug is in resolve/ownership, not missing art. |

---

## 1. Animation inefficiency (strict)

### 1.1 CRITICAL — ChamferButton barber-pole always running

**File:** `ui/iron/Buttons.kt`

**Bug:** `rememberInfiniteTransition` + `infiniteRepeatable(tween(1200))` is created **unconditionally**. Every `ChamferButton` on every screen invalidates every frame, idle or not.

BOOST · DEEP FREEZE on Home is the worst: 56dp primary CTA, always composed, always looping. Same leak on onboarding CONTINUE, Games ADD/PIN/LAUNCH, Toolbox CHECK, Pressure START, Field Manual CTAs.

**Spec:** pack I §5 — stripe `LaunchedEffect(busy) { if (!busy) return; while (true) withFrameNanos { … } }`. Idle = zero frames.

**Fix:**
- Delete `rememberInfiniteTransition`.
- Drive stripe with `Animatable` + `LaunchedEffect(busy)` only.
- When `busy` goes false: `snapTo(0f)` and exit. No infinite transition object while idle.
- `drawWithCache` already skips stripe draw when `!busy` — keep that. The leak is the **animation clock**, not the draw.

**Check:** `grep rememberInfiniteTransition` → 0 hits. Layout Inspector: Home idle, ChamferButton must not recompose on a timer.

### 1.2 Grain stacked N times per screen

**Spec §2.5 / §11.1:** grain tiled **once per screen** at 4%. Stamps extra at 12%. Never per-composable.

**Current (17 call sites, helper excluded):**

| Location | Grain | Keep? |
|---|---|---|
| `IronShell` root (`IronShell.kt:152`) | 4% | **KEEP** — Graphite/Vellum canvas |
| `FullScreenSlot` (`IronShell.kt:202`) | 4% | **DROP** — slot sits on shell canvas; reuse root grain |
| `EngravedPlate` (`Plates.kt:53`) | 4% | **DROP** |
| `PaperPlate` (`Plates.kt:143`) | 5% | **DROP** |
| `MachinedSegment` (`Controls.kt:98`) | 3% | **DROP** |
| `StampLabel` (`StampLabel.kt:73`) | 12% | **KEEP** (spec) |
| `BenchSheet` scrim (`BenchSheet.kt:55`) | 4% | **KEEP** — overlay above shell |
| `BenchSheet` body (`BenchSheet.kt:71`) | 4% | **DROP** |
| `FieldManual` root (`FieldManual.kt:63`) | 5% | **KEEP** — paper booklet, own surface |
| `KeyCard` (`FieldManual.kt:264`) | 6% | **DROP** |
| `TheLedger` root (`LedgerScreen.kt:50`) | 5% | **KEEP** — paper ledger, own surface |
| `TheLedger` code block (`LedgerScreen.kt:126`) | 5% | **DROP** |
| `Ignition` (`Ignition.kt:45`) | 4% | **KEEP** — own surface before shell |
| `DebugBench` (`DebugBench.kt:36`) | 4% | **KEEP** — own surface |
| `ShutterOverlay` plates (`ShutterOverlay.kt:46,55`) | 5% each | **DROP** |

`FullScreenSlot` grain only stacks when a slot is open (not always). Still drop it: slot content already draws on `ironSkin().canvas`; shell grain shows through.

**Keep list (exactly these):**
- `IronShell` root 4%
- `Ignition` root 4%
- `FieldManual` root 5%
- `TheLedger` root 5%
- `DebugBench` root 4%
- `BenchSheet` scrim 4% (not body)
- `StampLabel` 12%

**Drop list (exactly these):**
- `EngravedPlate`, `PaperPlate`, `MachinedSegment`, `KeyCard`, both shutter plates, `BenchSheet` body, `FullScreenSlot`, Ledger code-block box.

`ironGrain` helper stays. Just stop stacking. Blend mode (`Multiply` paper / `Screen` dark) is **not** done — wire in §2.0. De-stack first; blend is the Vellum grain finish, not a 3rd layer.

### 1.3 Dial idle / hunt loops (keep, tighten)

**File:** `InstrumentDial.kt`

Idle drift `delay(100)` (~10fps) when energized && !boosting && !reduced — **OK**, spec ±0.3° / 4s.

Hunt `delay(33)` (~30fps) only while boosting — **OK**.

**Fix:** none unless profiler shows jank. Do **not** remove drift. Drift already gated on `!reduced`. Confirm SWAP MiniDial `ignition = false` so it does not double-sweep.

**ITER 2 add:** gate both loops on `active` (see §1.5). Signature change:

```kotlin
fun InstrumentDial(..., active: Boolean = true)
```

`LaunchedEffect(energized, boosting, active)`: if `!active` do not start drift/hunt. Home passes `active`. MiniDial default `true` is fine (only composed on Home).

Theme-switch needle re-sweep: `LaunchedEffect(LocalIronFinish.current)` on Home's main dial only — reset `swept = false` then re-run ignition. Do **not** remount the tree.

### 1.4 Carousel adjacent blur — unbounded + no API guard

**File:** `ui/iron/games/LaunchMatrix.kt` `Rack`

```kotlin
.blur(if (abs(offset) > 0.5f) 4.dp else 0.dp)
```

- `.blur()` is RenderEffect. Spec: API 31+ only. Crash / no-op risk on 24–30.
- Applied **during swipe** on every adjacent page. Expensive.

**Fix:**
```kotlin
val reduced = LocalReducedMotion.current
val blurAdj = Build.VERSION.SDK_INT >= 31 && abs(offset) > 0.5f && !reduced
Modifier.then(if (blurAdj) Modifier.blur(4.dp) else Modifier)
```
Scale 0.8 + alpha 0.4 already do the depth. Blur is garnish.

### 1.5 Tab efficiency — PICKED: keep AnimatedContent + `active` gate

**File:** `IronShell.kt` `GearTabTransition`

`AnimatedContent` unmounts previous tab. That is the intended memory trade. Keep it.

**Out of this pass:** HorizontalPager, Box-of-four-tabs with alpha, keep-alive `wasRecentlySelected`. Revisit only if profiler shows 240ms crossfade jank >16ms.

**State that must survive unmount** — add `rememberSaveable` at the call site that owns it:

| State | Owner | Action |
|---|---|---|
| Dial `swept` | `InstrumentDial.kt:78` | already `rememberSaveable` — keep |
| Games pager page | `LaunchMatrix.kt` (pager) | `rememberSaveable` current page / restore into `PagerState` |
| `tickerWide` | `TheBench` (new, §4.2) | `rememberSaveable { true }` |
| Toolbox / Games `segment` | existing `remember` | upgrade to `rememberSaveable` if not already |

**`active` plumbing (assigned):**

1. `IronShell` does **not** grow an `active` param. Tabs unmount; the composed tab is the active one **except** when `replayOverlay != null`.
2. `IronShell` when `replayOverlay != null`: **do not invoke** `home()`/`games()`/`optics()`/`toolbox()` (skip tab content). Overlay is full-screen paper; leaving Home composed keeps dial drift + BenchViewModel ticks running under the booklet.
3. `MainScreen.kt` still passes `active` for the Optics/Home loops that can outlive composition via ViewModel collection:
   - `TheBench(..., active: Boolean)` — `active = gearTab == GearTab.HOME && !showReplayManual`.
   - `OpticsBench(..., active: Boolean)` — `active = gearTab == GearTab.HUD && !showReplayManual`.
4. Even with unmount, pass `active` anyway: if a future change keeps composition, loops stay gated. Cheap.

**TheBench `active` gates:**
- Dial: pass `active` into `InstrumentDial`.
- Do not start purge ceremony / shavings if `!active`.
- `NestedScrollConnection` pull-to-purge: no-op if `!active`.

**OpticsBench `active` gates:**
- Replace `LaunchedEffect(Unit) { while(true) delay(500) }` with `LaunchedEffect(active) { if (!active) return@LaunchedEffect; while (true) { … delay(500) } }`. Cancel on `active=false` or dispose.

Tab transition 240ms `ease.wind` is spec. Keep duration. Keep fade+slide unless 60fps fails.

### 1.6 Reduced-motion branches (pinned per file)

Read `LocalReducedMotion.current` in each file. Reduced path must not start `withInfiniteAnimationFrameMillis`, `ShavingsState.burst`, odometer `DigitRoll`, shutter plate anim.

| Effect | File | Full | Reduced — exact branch |
|---|---|---|---|
| Ignition | `Ignition.kt` `LaunchedEffect(Unit)` | sweep 300 + needle + appear 360 + `delay(550)` | **if reduced:** skip `sweep` entirely. `appear.animateTo(1f, tween(200))`. `delay(200)`. Then route. Do not wait 550ms. |
| Stamp slam | `StampLabel.kt` | spring.stamp | already fade in at −3°, no slam — **keep** |
| Dial ignition | `InstrumentDial.kt:81-89` | 0→100→value | already skips sweep when reduced — **keep**. Landing `animateTo(value, …)` uses reduced needle (below). |
| Dial needle (value + freed) | `InstrumentDial.kt:88, 94, 97` | `IronMotion.needle()` | `val spec = if (reduced) tween(150, LinearEasing) else IronMotion.needle()` — use `spec` for all three `animateTo` after ignition. |
| Shavings | `Effects.kt` / `TheBench` | physics burst | `ShavingsLayer`: if reduced, draw nothing / skip `burst`. `TheBench` purge: do not call `burst` when reduced. |
| Odometer | `Effects.kt` `OdometerCounter` | `DigitRoll` | if reduced: single `Text(text)` + `LaunchedEffect(text) { onSettled() }`. No `DigitRoll`. |
| Shutter | `ShutterOverlay.kt` | plates 160 close + 80 hold + 280 open | **if reduced:** do not compose the two plate Boxes. `Box(Modifier.fillMaxSize().background(Iron.Anvil900.copy(alpha = 0.45f)))` with `animateFloatAsState(1f→0f, tween(200))`. `LaunchedEffect(trigger) { delay(200); onSeam() }`. No `clack.thud`. |
| Marquee | `TickerLine.kt` | `basicMarquee` | if reduced: `overflow = TextOverflow.Ellipsis`, no marquee. |
| Toggle wobble | `Controls.kt` | ±3° 90ms | if reduced: skip wobble `Animatable`. |
| Purge ceremony | `TheBench.kt` | 1400ms full | if reduced: skip shavings, skip stamp slam, skip flight overlay; snap to Work Order. Still go through `ceremonyGate.run`. |
| Carousel blur | `LaunchMatrix.kt` | 4.dp | already gated `!reduced` in §1.4. |

`LocalReducedMotion` is already provided by `IronTheme`. After §2.4, Toolbox REDUCED feeds `reducedMotionOverride = true`. Animator scale 0 still auto-reduces even if Toolbox says FULL (`IronTheme.kt:49-54` already ORs — keep: `reducedMotionOverride ?: systemReduced` becomes `systemReduced \|\| (override == true)` wait:

**Pinned reduced-motion resolve in `IronTheme`:**

```kotlin
val reduced = systemReduced || (reducedMotionOverride == true)
LocalReducedMotion provides reduced
```

Animator scale 0 **always** wins (spec §4.4 auto-REDUCED). Toolbox FULL cannot override a 0 scale. Toolbox REDUCED forces reduced even if scale is 1. Toolbox AUTO / unset = `reducedMotionOverride = null` → system only. See §2.4.

### 1.7 CeremonyGate hoist

**Current:** `rememberCeremonyGate()` inside `TheBench` only. `ShutterOverlay` ungated. User can tab to Games mid-purge and fire shutter — two >400ms ceremonies. Spec §4.1 one ceremony per screen.

**Fix:**
- Add `val LocalCeremonyGate = staticCompositionLocalOf { CeremonyGate() }` in `FeedbackExtras.kt`.
- `IronShell` calls `val gate = rememberCeremonyGate()` once and `CompositionLocalProvider(LocalCeremonyGate provides gate)`.
- `TheBench` **deletes** `rememberCeremonyGate()`. Uses `LocalCeremonyGate.current`.
- `ShutterOverlay(trigger, onSeam)`: wrap launch path in `gate.run { … }`. If gate busy, no-op (do not start plates / dim).
- `LaunchMatrix` shutter trigger still increments; overlay no-ops when gate busy.

Do not put the gate on `MainScreen`. Shell owns the visible surface.

### 1.8 Other idle loops

| Loop | Where | Problem | Fix |
|---|---|---|---|
| `FieldManual` `while(true) { onProbe(); delay(1200) }` | always | probe even on pages 0–3 | `LaunchedEffect(page) { if (page != 4) return@LaunchedEffect; while (true) { onProbe(); delay(1200) } }` — cancel on leave page 4 / dispose |
| `MainActivity` `LaunchedEffect(Unit) { probeBackends() }` | boot | one-shot OK | **keep** one-shot. Do **not** add a second 1200ms loop at activity level. FieldManual's `onProbe` is the repeating one; gating page 4 is enough. When `appStage != ONBOARDING`, FieldManual is not composed → loop gone. |
| `OpticsBench` fake FPS/RAM/CPU 500ms | always once composed | runs after leaving HUD if kept / restarts on re-enter | `LaunchedEffect(active)` — §1.5 |
| Replay overlay vs Home | `IronShell` still composes tabs under booklet | dial drift + mem tick | skip tab content when `replayOverlay != null` — §1.5 |
| `LedDot` CHECKING/BLOCKED/LIVE | pulse loops | OK — only those states | keep |
| `StampLabel` pulse | SESSION ACTIVE | OK if `pulse=false` default | keep |
| `ThermometerStrip` pulse 50ms | only `cpuC > 45` | OK | keep |
| `LoadingNeedle` 900ms spin | while shown | OK | keep |
| `ChamferButton` press haptic | OK | keep |

### 1.9 DigitRoll / FlipCard / flight

- Odometer rolls from 0 every composition (pack: intentional). Reduced: skip (§1.6).
- FlipCard guarded (`showBack != flipped`). OK.
- Flight overlay uses `graphicsLayer` on Text — OK. Reduced: skip flight, snap to Work Order.

---

## 2. Theme — two finishes, three stations (not three looks)

Spec §10 (`new_design.md:1063-1077`) + pack II `Skin.kt` + pack III Vellum rule:

> One theme engine, two finishes.

| Toolbox station | What it is | Resolves to |
|---|---|---|
| **SYSTEM** | Follow `isSystemInDarkTheme()` | GRAPHITE if OS dark, VELLUM if OS light |
| **VELLUM** | Force paper finish | `IronFinish.VELLUM` always |
| **GRAPHITE** | Force metal finish | `IronFinish.GRAPHITE` always |

There is **no third `IronSkin`**. `IronFinish` has two values. `ThemeMode` has three because SYSTEM is a preference, not a paint.

**What the user is seeing today (why it looks like "1 done, 2 partial, 3 = 1"):**

| Station | Canvas | Content tokens | Actual look |
|---|---|---|---|
| GRAPHITE | `anvil.900` via `ironSkin()` | hardcoded `Iron.Bone*` / `Anvil*` | **done** — this is the only complete finish |
| VELLUM | `bone.50` via `ironSkin()` | **still** `Iron.Bone100` on cream | **partial** — chassis canvas flips, copy/plates/accents do not. Cream-on-cream. Unreadable. Looks "broken light mode", not a 2nd finish. |
| SYSTEM (dark OS) | same Graphite canvas | same Graphite tokens | **identical to GRAPHITE** — **correct**. Not a missing 3rd theme. |
| SYSTEM (light OS) | Vellum canvas | Graphite tokens | **identical to broken Vellum** — resolve works; Vellum content is the bug. |

Do **not** invent a SYSTEM-specific palette. Do **not** restyle Graphite to "look different from SYSTEM". The work is: finish Vellum so SYSTEM-light and VELLUM are a real 2nd finish.

**Vellum completeness (spec §10 table) — all of this is this pass:**

| Layer | GRAPHITE (done) | VELLUM (must become) | Code today |
|---|---|---|---|
| Canvas | `anvil.900` + grain SCREEN-tint | `bone.50` + grain **MULTIPLY** | canvas color OK via `ironSkin()`. Grain is always `drawRect(brush, alpha)` — **no blend mode**. Spec §2.5: `MULTIPLY` on paper, `SCREEN` on dark. |
| Content plates | `anvil.700` engraved | `bone.100` paper w/ print shadow | screens hardcode `EngravedPlate`. `IronSurface` unused. |
| Data readouts | bone on anvil | **ink plates** — `#201C16` + bone mono | ToolRow/ticker/scales still Anvil800+Bone100. On Vellum these must stay **metal** (`EngravedPlate` / `Iron.Ink900` fill + `Bone100` mono) — pack II: "Data readouts STAY metal in both (in Vellum that's the ink-plate inversion)". |
| Accents | `signal.500` / `phosphor.400` | `signal.500` / **`phosphor.600`** | `Phosphor400` hardcoded in Optics FPS, Pressure dots, Tune values, `LedDot`, `StampInk.Phosphor`, toggle checked fill. Dial `freed` already `Phosphor600` on Vellum. |
| Hardware | brass on dark | brass + ink outlines | MachinedSegment/Toggle stay metal (chassis-adjacent). OK. |
| Gauges | engraved dark | ink-on-paper blueprint | `dialPalette()` **done**. |
| Chassis | Bridge + Gear anvil | **same anvil** both finishes | `BridgePlate`/`GearSelector` hardcoded Anvil900. **Keep.** Pack III rule. |
| Status bars | light icons | dark ink icons | `IronTheme` SideEffect. **done.** |

**Skin helper additions this pass** (do not grow a third skin):

```kotlin
fun IronSkin.phosphor(): Color = if (isPaper) Iron.Phosphor600 else Iron.Phosphor400
```

Grain:

```kotlin
fun Modifier.ironGrain(alpha: Float = 0.04f): Modifier = this.drawWithCache {
    val paper = /* read finish via param or composition — pass isPaper into helper */
    val brush = ShaderBrush(ImageShader(Grain.image, TileMode.Repeated, TileMode.Repeated))
    onDrawWithContent {
        drawContent()
        drawRect(brush = brush, alpha = alpha, blendMode = if (paper) BlendMode.Multiply else BlendMode.Screen)
    }
}
```

`ironGrain` is a `Modifier` extension, not `@Composable`. It cannot call `ironSkin()`. **Pin — one helper, not two:**

```kotlin
internal fun Modifier.ironGrainInternal(alpha: Float, paper: Boolean): Modifier =
    this.drawWithCache { /* existing ImageShader; blend Multiply if paper else Screen */ }

@Composable
fun Modifier.ironGrain(alpha: Float = 0.04f): Modifier {
    val paper = ironSkin().isPaper
    return ironGrainInternal(alpha, paper)
}
```

Call sites stay `.ironGrain(0.04f)`. Do **not** add a public `paper: Boolean` overload.

**SYSTEM vs GRAPHITE pixel-identity check:** after Vellum content is wired, screenshot SYSTEM+dark next to GRAPHITE — they must match. Screenshot SYSTEM+light next to VELLUM — they must match. If they don't, `ThemeMode.resolve` or the Shell provider fight is still live (§2.5).

### 2.1 Dual ThemeMode enums — one enum, one owner

| Enum | Values | Storage |
|---|---|---|
| `ui.theme.ThemeMode` | SYSTEM / **LIGHT** / **DARK** | `"system"` / `"light"` / `"dark"` |
| `ui.iron.ThemeMode` | SYSTEM / **VELLUM** / **GRAPHITE** | none — mapped by **ordinal** |

`MainActivity.kt:65-71` ordinal-bridges. `MainScreen.kt:102-106` maps LIGHT→VELLUM. `Toolbox` writes VELLUM→LIGHT.

**Fix (single model):**
- **Keep** `ui.iron.ThemeMode` { SYSTEM, VELLUM, GRAPHITE }. This is the only ThemeMode in the Compose tree.
- **Delete** `ui.theme.ThemeMode` enum. `ThemePreferences` moves to persist iron values.
- `ThemePreferences` file stays at `ui/theme/ThemePreferences.kt` (used by `MainActivity`). Change storage strings to `system` / `vellum` / `graphite`.
- Read path one-time migrate in `fromStorage`:
  - `"light"` → `VELLUM`
  - `"dark"` → `GRAPHITE`
  - `"vellum"` → `VELLUM`
  - `"graphite"` → `GRAPHITE`
  - else → `SYSTEM`
- Write path always writes `vellum`/`graphite`/`system`. Never write `light`/`dark` again.
- `MainActivity` / `MainScreen` / `Toolbox` all take `com.ivarna.apexcore.ui.iron.ThemeMode`. Delete ordinal bridging (`themeModeOrdinal` can stay as `rememberSaveable` of the iron enum ordinal — same 0/1/2 — or persist the enum via `toStorage()` on change only; either is fine as long as there is one type).
- `MainScreen` signature: `themeMode: com.ivarna.apexcore.ui.iron.ThemeMode`, drop the LIGHT/DARK remap block at lines 102–106 and 528–532.

### 2.2 Content colors hardcoded Graphite (Vellum still partial)

Almost every screen uses `Iron.Bone100` / `Iron.Bone500` / `Iron.Anvil*` for text, rows, headers.

On **Vellum** (`LocalIronFinish = VELLUM`):
- Canvas becomes `bone.50` (cream) via `ironSkin().canvas`. **This is the only Vellum paint that landed.**
- Text stays `Bone100` (cream on cream). **Unreadable.** That is why Vellum looks "not implemented" and why SYSTEM-light looks like a broken clone of Graphite.

Spec §10 + pack III Vellum rule — three layers, do not mix:

| Layer | Vellum | Tokens |
|---|---|---|
| **Chassis** | always metal | Bridge, Gear, SearchSlot, MachinedSegment/Toggle. Keep Anvil/Bone. **No `ironSkin()`.** |
| **Content chrome** | paper | titles, captions, Toolbox/Games/Optics/Tune/Pressure plates, ToolRow, SerialFooter, Chamfer outline. `skin.text` / `skin.textDim` / `skin.hairline` / `skin.plate` via `IronSurface`. |
| **Data readouts** | **ink plates** (dark `#201C16` + bone mono) | PressureScale, ThermometerStrip, Optics FPS/sparkline, odometer, ticker **values**. Stay `EngravedPlate` / Anvil fill + `Bone100` mono even on Vellum. Do **not** run `skin.text` (Ink900) on these — ink-on-anvil vanishes. |

Dials already have `dialPalette()` — **done**. Do not restyle chassis to "prove" SYSTEM ≠ GRAPHITE.

**Content chrome to patch** (follow `ironSkin()` / `IronSurface`):
- `TheBench.kt` — section titles, SerialFooter. Work Order stays `PaperPlate` (always bone) — OK.
- `LaunchMatrix.kt` — titles, empty states, card chrome (not the metal slot search).
- `OpticsBench.kt` — OPTICS title, section plates. FPS number + sparklines = data readout (stay phosphor/bone on anvil).
- `Toolbox.kt` — TOOLBOX title, APPEARANCE/ACCESS/ABOUT plates via `IronSurface`.
- `TuningRoom.kt` / `PressureRoom.kt` — titles + plates via `IronSurface`. Reclaimed-GB / scale = data readout (stay metal).
- `ToolRow.kt` — content nav → `IronSurface` + `skin.text`.
- `TickerLine.kt` — banner chrome follows skin; LED + value stay phosphor/bone (readout).
- `SerialFooter.kt` — `skin.textDim`.
- `Chrome.kt` dropdown labels currently Bone100/Bone300 — if dropdown sits on content, skin; if on chassis, keep.
- `sheets/Sheets.kt` — picker rows sit on BenchSheet (metal body). Keep Anvil/Bone (sheet is hardware overlay).
- `ChamferButton` Outline: Vellum paper → outline `Ink600`. Primary fill stays `signal.500` both finishes.
- `DebugBench.kt` — keep Graphite tokens (debug surface, always dark).

**Data readouts — keep metal / switch phosphor only:**
- `Scales.kt` PressureScale + ThermometerStrip: keep Anvil + Bone. Tick colors stay. No skin.text.
- `OpticsBench.kt` FPS `Phosphor400` → `skin.phosphor()` (`Phosphor600` on Vellum).
- `PressureRoom.kt` / `TuningRoom.kt` phosphor values → `skin.phosphor()`.
- `LedDot` (`Primitives.kt`): `Phosphor400` → `skin.phosphor()` (accent table). Fine on Bridge too.
- `Controls.kt` toggle checked fill: `Phosphor400` → `skin.phosphor()`.
- `StampInk.Phosphor` stays `Phosphor400` (rubber-stamp ink, not finish accent).

**Do not** theme Bridge/Gear/dials-as-hardware.

After patch:
- `grep 'Iron.Bone\|Iron.Anvil' ui/iron/**/*.kt` — remaining hits must be chassis, data readouts, Ignition, DebugBench, Ledger/Manual paper (Ink/Bone50), or StampInk.
- Screenshot four: GRAPHITE, VELLUM, SYSTEM+dark, SYSTEM+light. Pairwise identity: SYSTEM+dark == GRAPHITE, SYSTEM+light == VELLUM.

### 2.3 PAPER INSERTS — default + migration pinned

Pref key `light_tank_bg` reused. Current default **true**. Almost no screen uses `IronSurface` / `LocalPaperSurfaces`. Plates are `EngravedPlate` hardcoded. Toggle does nothing.

**Pinned default:**
- Fresh install (key absent): **false** — metal Graphite.
- Existing install (key present): keep stored bool.

**Migration location:** `ThemePreferences.getLightTankBg`:

```kotlin
fun getLightTankBg(context: Context): Boolean {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    if (!prefs.contains(KEY_LIGHT_TANK_BG)) return false
    return prefs.getBoolean(KEY_LIGHT_TANK_BG, false)
}
```

No extra `migrate()` function. `light→vellum` string migration lives in `ThemeMode.fromStorage` in the same file (§2.1).

**Wire the toggle:**
- Content cards that should flip: route through `IronSurface` **or** `if (LocalPaperSurfaces.current) PaperPlate else EngravedPlate`.
- Targets: Toolbox APPEARANCE/ACCESS/ABOUT plates, ToolRow, LaunchMatrix info plates, Optics section plates. Not Bridge, not Gear, not dials, not SearchSlot, not MachinedSegment.
- `IronTheme` keeps `LocalPaperSurfaces provides (finish == VELLUM || paperInserts)`.
- Vellum always paper regardless of toggle (OR still holds). Spec §10 says the toggle also "switches paper … off in Vellum". **Do not implement metal-on-vellum-canvas.** That would be a third look (cream canvas + Graphite plates). D9 forbids it. Toggle only adds paper plates **onto Graphite**. Vellum cannot be turned into Graphite-content-on-cream.

### 2.4 MECHANICAL MOTION setting

Spec §4.4: Toolbox setting **MECHANICAL MOTION: FULL / REDUCED**. Auto-REDUCED if animator scale = 0.

`IronTheme` has `reducedMotionOverride` and reads `ANIMATOR_DURATION_SCALE`. **Toolbox has no control.** Override never passed.

**Fix:**
- Pref key `mechanical_motion` in `ThemePreferences`: values `full` | `reduced` | `auto`. Default `auto`.
- Toolbox APPEARANCE: MachinedSegment `FULL / REDUCED` (two options, spec). Map: FULL → `"full"`, REDUCED → `"reduced"`. Do **not** expose AUTO in the UI. `auto` is only the stored default for fresh installs (system scale decides).
- When user picks FULL or REDUCED, write that string. Reading: `full` → override false, `reduced` → override true, `auto`/absent → override null.
- `MainActivity` reads pref, passes `reducedMotionOverride` into `IronTheme`.
- Resolve in IronTheme: `systemReduced || (reducedMotionOverride == true)` — scale 0 always wins (§1.6).

### 2.5 IronTheme vs IronShell — one owner (merged with §2.1)

**Current fight:** `IronTheme.kt:67-69` provides `LocalIronFinish` from `themeMode.resolve()`. `IronShell.kt:150-152` `Crossfade(finish) { f -> CompositionLocalProvider(LocalIronFinish provides f) { Box(background(ironSkin().canvas)) } }` remounts the entire shell, re-provides the local, re-sweeps dials.

**Pinned model:**

1. `IronTheme` owns `LocalIronFinish`. Nobody else provides it.
2. `IronShell` **deletes** the `finish: IronFinish` parameter. No paint hint. No deprecated leftover. Shell reads `ironSkin().canvas` from the local IronTheme already set. `MainScreen` stops passing `finish`.
3. `IronShell` body:

```kotlin
val canvas by animateColorAsState(ironSkin().canvas, tween(220), label = "finishCanvas")
Box(Modifier.fillMaxSize().background(canvas).ironGrain(0.04f)) { … }
```

No `Crossfade`. No inner `CompositionLocalProvider(LocalIronFinish)`. Tree stays mounted. Tab state survives theme switch.

4. `MainScreen` stops passing `finish = finish` once the param is gone.
5. Needle re-sweep on theme change: `InstrumentDial` on Home only, `LaunchedEffect(LocalIronFinish.current)` resets `swept` and re-runs ignition. Not a remount.
6. Status bar tint: `IronTheme` SideEffect already keys on `finish`. Stays. Verify it still runs (it will — provider is unchanged).

### 2.6 Status bar tint

`IronTheme` SideEffect sets light bars on Vellum. Good. No Shell override after §2.5. No extra work.

---

## 3. Onboarding / Field Manual — theme not applied

**User callout:** onboarding page is on-theme only. Fix that too.

**Plan default (APPROVED by review):** keep paper manual (spec §7.2). Do not theme-swap the booklet. Finish local still wraps FieldManual (Ignition + Manual inside `IronTheme`) but Manual ignores it by design.

### 3.1 Hardcoded paper forever — keep

`FieldManual.kt` `Box(…background(Iron.Bone50).ironGrain(0.05f))`. Copy `Ink900` / `Ink600`. KeyCards `onPaper = true`. Correct for a printed booklet.

**Concrete onboarding bugs still in scope:**

1. Every `ChamferButton` idle stripe — covered by §1.1.
2. `KeyCard` extra 6% grain — drop, §1.2.
3. Probe loop: `LaunchedEffect(page) { if (page != 4) return; while (true) { onProbe(); delay(1200) } }`. Cancel on dispose / leaving page 4.
4. `RisoText("APEXCORE")` on cover — OK (one hero). Wrap FieldManual in `IronScreen("MANUAL")`.
5. Replay overlay: `IronShell` skips tab content when `replayOverlay != null` (§1.5). Dial drift does not run under the booklet.

`MainActivity.probeBackends()` one-shot at boot stays. Repeating probe is FieldManual-only, page 4 only.

If product later wants themed manual (Graphite = dark plates + bone type; Vellum = current paper), that is a **spec change**, not this pass.

---

## 4. Riso / hallmarks / a11y leftovers

### 4.1 Riso audit — once per instance

**Wrong (current):** `RisoText` `SideEffect { count++ }` every recomposition. `IronScreen` never called. Count lives on `IronTheme`'s `mutableIntStateOf(0)` — not per screen.

**Wrong (ITER 1 plan):** `remember(text) { count++ }` counts distinct strings, re-counts on text change.

**Pinned:**

`IronScreen` (`IronTheme.kt:26-30`):

```kotlin
@Composable
fun IronScreen(name: String, content: @Composable () -> Unit) {
    val count = remember { mutableIntStateOf(0) }
    CompositionLocalProvider(LocalRisoCount provides count) {
        content()
    }
}
```

Reset is implicit: new `remember` per `IronScreen` composition. Do **not** mutate a parent counter to 0 via `remember(name)`.

`RisoText` (`Primitives.kt:60-73`):

```kotlin
DisposableEffect(Unit) {
    count.intValue++
    if (count.intValue > 1) Log.w("IRONWORK", "riso count=${count.intValue} (max 1)")
    onDispose { count.intValue-- }
}
```

Once per instance. Recomposition does not increment. Leaving the composition decrements.

**Wrap each surface:**

| Surface | Wrapper | Call site |
|---|---|---|
| Home | `IronScreen("HOME")` | `IronShell` around `home()` **or** `TheBench` root — pick `TheBench` root so replay skip still works |
| Games | `IronScreen("GAMES")` | `LaunchMatrix` root |
| HUD | `IronScreen("HUD")` | `OpticsBench` root |
| Tools | `IronScreen("TOOLS")` | `Toolbox` root |
| Ignition | `IronScreen("IGNITION")` | `Ignition` root |
| Manual | `IronScreen("MANUAL")` | `FieldManual` root |
| Ledger | `IronScreen("LEDGER")` | `TheLedger` root |
| Tune / Pressure slots | `IronScreen("TUNE")` / `IronScreen("PRESSURE")` | slot content roots |

**Bridge wordmark:** `IronShell.kt:95` `RisoText("APEXCORE")` → `EngravedText("APEXCORE", …)`. Chassis, not a hallmarks hero.

**Home hero:** none until Work Order `RisoText("PURGE COMPLETE")` **or** ElevationSlip. If ElevationSlip shows, it uses `EngravedText`, not riso — only Work Order is the Home riso. Never both.

`IronTheme` may keep `LocalRisoCount provides mutableIntStateOf(0)` as a fallback for anything not wrapped; wrapped screens override via `IronScreen`.

### 4.2 Ticker double-tap not wired

`TickerLine` has `collapsed` + `onDoubleTap`. `TheBench` never passes them. Spec §6.2 / §7.4.

**Fix:** `var tickerWide by rememberSaveable { mutableStateOf(true) }` in TheBench. Pass `collapsed = !tickerWide`, `onDoubleTap = { tickerWide = !tickerWide }`.

### 4.3 Ticker marquee vs reduced

`basicMarquee(iterations = Int.MAX_VALUE)` always. Reduced: `overflow = Ellipsis`, no marquee. See §1.6.

### 4.4 StampToast clear bug

`rememberStampToast`: after 1400ms calls `s.show("")`. Host checks `!msg.isNullOrEmpty()` so empty string hides — OK-ish. Set `message = null`. Minor.

---

## 5. Page / navigation efficiency

### 5.1 MainScreen god-composable

`MainScreen.kt` ~687 lines: probes, games, overlay prefs, tune, ram, markdown parse, all sheets. **All state lives even on Home tab.**

- `ledgerBlocks` parsed at first composition (OK, `remember`). **Change:** parse only when `ironSlot == LEDGER` (lazy). Don't parse privacy markdown until Ledger opens.
- Tune `refreshTune` only when slot TUNE (OK).
- Games list loaded at start (OK).
- `allApps` load — check it is lazy on ALL APPS segment, not at boot. Keep lazy.

Don't start Optics fake telemetry until HUD tab (`active` flag, §1.5). Don't probe backends every 1200ms from FieldManual when Main is showing (FieldManual unmounted, §1.8).

### 5.2 Tab content cost

See §1.5. Decision is unmount + saveable + `active`. No further options.

### 5.3 Pull-to-purge NestedScroll

`TheBench` `NestedScrollConnection` with `var pull by mutableFloatStateOf` **inside** `remember { object { } }`. Unusual but works. Confirm it doesn't allocate each scroll. No change unless bug. Gate on `active` (§1.5).

### 5.4 Shutter grain + full-screen overlay

Shutter two plates each `ironGrain(0.05f)` — drop (§1.2). Reduced: 200ms dim (§1.6). Gated by shell CeremonyGate (§1.7).

---

## 6. Dead Zen / leftover deps

Grep-verified: `ZenColors` / `ZenTypography` / `ApexCoreTheme` / `ZenIcons` / `ZenDimens` are **only referenced inside `ui/theme/`**. `MainActivity` wraps `IronTheme`, not `ApexCoreTheme`. Iron color tokens live in `ui/iron/IronTokens.kt` — **do not delete that**.

| Item | Action |
|---|---|
| `ui/theme/Theme.kt` (`ApexCoreTheme`) | **DELETE** |
| `ui/theme/ZenIcons.kt` | **DELETE** |
| `ui/theme/Color.kt` (`ZenColors` only — not Iron tokens) | **DELETE** |
| `ui/theme/Type.kt` (`PlusJakartaSans` + `ZenTypography` + GoogleFont.Provider) | **DELETE** |
| `ui/theme/Dimens.kt` (`ZenDimens` only, zero external refs) | **DELETE** |
| `app/build.gradle.kts` `implementation("androidx.compose.ui:ui-text-google-fonts")` | **REMOVE** |
| `ui/theme/ThemePreferences.kt` | **KEEP** — migrate keys (§2.1, §2.3, §2.4) |
| `haze` | already gone |
| `material3.Text` / `HorizontalDivider` | every iron file. Pack said no Material. Harmless. **Don't** mass-replace this pass. |
| Roborazzi / baseline profile | **out of this pass** |
| `compose-bom 2024.11.00` vs pack 2024.10.00 | keep 11 |

After delete: `grep ZenColors\|ZenTypography\|ApexCoreTheme\|ZenIcons\|ZenDimens\|PlusJakartaSans\|googlefonts` → 0 hits.

---

## 7. File-by-file change list

### Must change

1. **`Buttons.kt`** — busy-only stripe Animatable. Highest priority. Delete `rememberInfiniteTransition`.
2. **`Plates.kt`** — drop `ironGrain` on EngravedPlate / PaperPlate.
3. **`Controls.kt`** — drop segment grain; skip wobble if reduced.
4. **`IronShell.kt`** — drop `Crossfade` + `LocalIronFinish` re-provide; `animateColorAsState` canvas 220ms; drop `finish` param; drop FullScreenSlot grain; Bridge wordmark → `EngravedText`; hoist `CeremonyGate` via `LocalCeremonyGate`; skip tab content when `replayOverlay != null`.
5. **`Skin.kt`** — add `IronSkin.phosphor(): Color` (`Phosphor600` if `isPaper` else `Phosphor400`). ThemeMode enum stays here. No third finish.
6. **`ThemePreferences.kt`** — delete `ui.theme.ThemeMode`; persist `system`/`vellum`/`graphite`; migrate `light`/`dark`; `getLightTankBg` contains-check default false; add `mechanical_motion` pref.
7. **`MainActivity.kt`** — one iron `ThemeMode`; pass `reducedMotionOverride`; keep one-shot `probeBackends`.
8. **`MainScreen.kt`** — iron `ThemeMode` only; drop LIGHT/DARK remap; pass `active` into TheBench / OpticsBench; lazy ledger parse; stop passing `finish` into IronShell.
9. **`Toolbox.kt`** — MECHANICAL MOTION FULL/REDUCED segment; skin colors; paper inserts flip plates via `IronSurface` / `LocalPaperSurfaces`.
10. **`TheBench.kt`** — `active` param; drop local CeremonyGate (use Local); reduced purge; `tickerWide` saveable; skin; `IronScreen("HOME")`; pass `active` into dial.
11. **`TickerLine.kt`** — skin colors; marquee gated; wire collapsed/onDoubleTap from TheBench.
12. **`ToolRow.kt`** — skin plate/text; honor `LocalPaperSurfaces`.
13. **`LaunchMatrix.kt`** — API 31 blur; reduced shutter trigger still fires overlay (overlay itself reduced); skin; `rememberSaveable` pager page; `IronScreen("GAMES")`.
14. **`ShutterOverlay.kt`** — reduced 200ms dim, no plates, no grain; `LocalCeremonyGate.run`.
15. **`Effects.kt`** — reduced odometer = Text; ShavingsLayer no-op if reduced.
16. **`InstrumentDial.kt`** — `active` param; reduced value tracking `tween(150, LinearEasing)`; `LaunchedEffect(finish)` re-sweep on Home only.
17. **`FieldManual.kt`** — probe only page 4; drop KeyCard grain; keep paper; `IronScreen("MANUAL")`.
18. **`OpticsBench.kt`** — `active` param; telemetry loop `LaunchedEffect(active)`; skin; `IronScreen("HUD")`.
19. **`Primitives.kt`** — RisoText `DisposableEffect(Unit)` once-per-instance; `LedDot` READY/LIVE uses `skin.phosphor()`.
20. **`IronTheme.kt`** — `IronScreen` provides per-screen count; reduced resolve `systemReduced || override==true`; grain helper blend `Multiply` on paper / `Screen` on dark (composable wrapper reads `ironSkin().isPaper`); keep `LocalIronFinish` provider (the only one).
21. **`FeedbackExtras.kt`** — `LocalCeremonyGate`; toast `message = null`.
22. **`SerialFooter.kt`** — `skin.textDim`.
23. **`LedgerScreen.kt`** — stays paper. Drop code-block grain. Root grain keep. `IronScreen("LEDGER")`.
24. **`BenchSheet.kt`** — grain scrim only, not body.
25. **`Ignition.kt`** — reduced = 200ms fade then route. `IronScreen("IGNITION")`.
26. **`Scales.kt`** — keep Anvil/Bone (data readout / ink plate). No `skin.text`.
27. **`PressureRoom.kt` / `TuningRoom.kt` / `Controls.kt` toggle** — phosphor via `skin.phosphor()`.
28. **Delete** `ui/theme/{Theme,ZenIcons,Color,Type,Dimens}.kt` + gradle `ui-text-google-fonts`.

### Do not change this pass

- Freeze / Fps / Ram / Tune / Game backend logic.
- HUD `GameOverlayService` / `RailView` (already rewired).
- Font files (`res/font/*`, `IronTokens.kt` families).
- Icon / splash.
- Adding Roborazzi.
- Replacing material3.Text.
- HorizontalPager tab keep-alive.
- Theming Field Manual off paper.
- A third `IronSkin` / SYSTEM-specific palette. SYSTEM is a resolver (D9).
- PAPER INSERTS turning Vellum into metal-on-cream.

---

## 8. Acceptance

### Animation
- [ ] `grep rememberInfiniteTransition` → 0 hits.
- [ ] Home idle: BOOST button 0 fps cost until `busy=true`. Layout Inspector: ChamferButton recomposition count idle = 0 timed.
- [ ] `.ironGrain` only at keep-list roots + stamps (grep the 7 keep sites; drop-list gone).
- [ ] Games adjacent blur skipped on API < 31; skipped when reduced.
- [ ] Optics 500ms loop stops when leaving HUD tab (`active=false` cancels).
- [ ] FieldManual probe only on page 4.
- [ ] Purge and Shutter cannot overlap (shared `LocalCeremonyGate`).
- [ ] Replay booklet: Home dial not composed (no idle drift under overlay).

### Reduced motion
- [ ] Toolbox MECHANICAL MOTION FULL/REDUCED persists (`mechanical_motion` pref).
- [ ] Animator scale 0 → reduced even if Toolbox FULL.
- [ ] Reduced shutter: no plates, 200ms dim, then launch.
- [ ] Reduced ignition: 200ms fade, no sweep, route at 200ms not 550ms.
- [ ] Reduced needle: linear 150ms (value + freed).
- [ ] Reduced: no shavings, no stamp slam, no odometer roll, marquee off, toggle no wobble.

### Theme
- [ ] One ThemeMode enum (`ui.iron`). Pref `vellum`/`graphite`/`system`. Old `light`/`dark` still load.
- [ ] **Two finishes only.** SYSTEM is a resolver. No third `IronSkin`.
- [ ] SYSTEM + dark OS vs GRAPHITE: **content** canvas + plates match (not status/nav bars — those follow `WindowInsetsController`, assert separately). SYSTEM + light OS vs VELLUM: same, content only.
- [ ] Vellum content chrome: ink on bone, contrast readable (not cream on cream). Titles/ToolRow/Toolbox use `ironSkin()`.
- [ ] Vellum data readouts: ink plates (dark + bone mono). Scales/FPS/odometer not paper-ified.
- [ ] Vellum accents: `phosphor.600` on content LEDs/FPS/toggle. Dial `freed` already.
- [ ] Grain: `BlendMode.Multiply` on Vellum canvas, `BlendMode.Screen` on Graphite.
- [ ] Graphite: bone on anvil, as now. Do not restyle Graphite to differ from SYSTEM-dark.
- [ ] Bridge + Gear stay anvil both finishes.
- [ ] Dials use dialPalette (already). No Graphite leak on Vellum dials.
- [ ] Theme switch 220ms color crossfade, **no** `Crossfade` of tree, **no** lost tab state.
- [ ] PAPER INSERTS: fresh install Graphite = metal; toggle ON = paper content plates; Vellum always paper; existing stored true preserved.

### Onboarding
- [ ] Field Manual stays paper (spec). Idle CONTINUE does not animate stripes.
- [ ] Replay does not leave Home dial hunting under the booklet.
- [ ] Page 4 only probes backends.

### Riso / a11y
- [ ] `IronScreen` wraps each surface. Debug log silent at 1 riso. `DisposableEffect` not `SideEffect`.
- [ ] Bridge is `EngravedText`. Home riso is Work Order only (ElevationSlip engraved).
- [ ] Ticker double-tap collapses to LED.

### Dead code
- [ ] `grep ZenColors|ZenTypography|ApexCoreTheme|ZenIcons|ZenDimens|PlusJakartaSans|googlefonts` → 0.
- [ ] `ui-text-google-fonts` gone from gradle.

---

## 9. Order of work

1. **ChamferButton busy-only** — unblocks every screen including onboarding.
2. **Grain de-stack** — plates, segment, keycard, shutter, sheet body, FullScreenSlot, ledger code box.
3. **Lifecycle gating** — `active` on TheBench/OpticsBench; FieldManual probe page 4; IronShell skip tabs under replay; CeremonyGate hoist. Do this **before** theme remount removal so loops are proven stopped without a remount reset hiding them.
4. **ThemeMode unify + IronShell stop remounting + skin colors on content.**
5. **Toolbox MECHANICAL MOTION + reduced paths** (shutter dim, ignition 200ms, needle 150ms, shavings, odometer, marquee, wobble, purge).
6. **Onboarding leftover** — KeyCard grain already in 2; confirm paper stays; `IronScreen("MANUAL")`.
7. **Riso + ticker double-tap.**
8. **Delete unused Zen theme files + google-fonts.**

Do not start 4 before 1–3. Theme remount currently hides animation bugs by resetting them.

---

## 10. Out of scope

- Roborazzi goldens, baseline profile, APK 1.5 MB audit (parent plan §13).
- HUD visual redesign.
- i18n / RTL beyond existing tab slide.
- Replacing Material3 Text.
- Changing Field Manual from paper to themed metal (spec forbids). Review approved keep-paper.
- HorizontalPager / compose-all-tabs.
- A third finish. Spec §10 is two finishes. SYSTEM-dark must equal GRAPHITE.

---

## 11. Risks

| Risk | Mitigation |
|---|---|
| Skin-color sweep misses a Bone100 | grep `Iron.Bone` / `Iron.Anvil` after patch; leftover = chassis/readouts/debug/paper only |
| Worker invents a 3rd SYSTEM skin | D9. SYSTEM has no `IronSkin`. Resolve only. |
| Worker paper-ifies data readouts | §2.2 table. Scales/FPS stay ink plates. |
| PAPER INSERTS default flip surprises existing users | `prefs.contains` preserves stored true; only absent key → false |
| Not remounting tabs keeps stale Optics loop | explicit `active` flag, don't rely on dispose |
| Busy-only stripe regression (BOOST looks dead while purging) | TheBench already passes `busy = phase == BOOSTING` |
| Skipping tab content under replay loses saveable state | `rememberSaveable` is in TheBench/LaunchMatrix, not in a parent that dies; overlay skip is `if (replayOverlay==null) GearTabTransition` — saveable restored on close |
| CeremonyGate no-op feels like launch failed | LaunchMatrix still starts the game via `onSeam` only inside `gate.run`; if busy, user retries after purge. Do not launch behind the gate. |

---

## 12. Definition of done

- BOOST idle: no looping animation. `rememberInfiniteTransition` gone.
- Vellum readable on all four tabs + overlays. SYSTEM-dark == GRAPHITE. SYSTEM-light == VELLUM. No third look.
- Onboarding paper, no idle stripe, probe on page 4 only.
- Grain once per screen (keep-list only).
- MECHANICAL MOTION works. Scale 0 always reduces.
- One ThemeMode. IronTheme owns `LocalIronFinish`. Shell animates color only.
- CeremonyGate shared on shell.
- Replay does not tick Home dial.
- Unused Zen/GoogleFonts removed.

That's the pass.
