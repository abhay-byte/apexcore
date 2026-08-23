# Plan Review: IRONWORK Fix Pass — ITER 3

| | |
|---|---|
| **Task ID** | ironwork-fix-pass |
| **Document Under Review** | `docs/plans/ironwork-fix-pass-plan.md` (AC-PLAN-IRONWORK-FIX · PASS 1 · ITER 3) |
| **Parent Plan** | `docs/plans/ironwork-redesign-plan.md` |
| **Spec Reference** | AC-DS-004 REV D `docs/design/new_design.md` §10 + Packs I-III (`new_implementation_part_{1,2,3}.md`) — Skin.kt, Toolbox stations |
| **Prior Review** | `docs/reviews/ironwork-fix-pass-plan-review.md` (ITER 1 — REVISE, 5 majors) |
| **This Review** | **ITER 3 — FIX PASS PLAN REVIEW** (supersedes ITER 1 review) |
| **Verdict** | **APPROVE** — worker can start; no rediscovery needed |

## Executive Summary

ITER 3 pins D9 (two finishes, not three) and finishes §2.0 Vellum wiring. All 5 ITER 1 majors closed with concrete ownership, branch detail, and migration location. D9 correctly rejects third `IronSkin` — SYSTEM is resolver `ThemeMode.resolve(systemDark)` → `IronFinish`. Vellum split (chassis always metal, content chrome = skin, data readouts = ink plates) matches spec §10 + pack II `Skin.kt` + pack III Vellum rule. Grain blend `MULTIPLY`/`SCREEN` and `IronSkin.phosphor()` additions are minimal and spec-required, not overbuild. Plan not invent third look. Ready for worker.

## Iteration Closure Check (ITER 1 → ITER 3)

| ITER 1 Major | ITER 2/3 Pin | Closed? | Evidence |
|---|---|---|---|
| 1. Theme ownership contradictory (Crossfade vs provider vs animateColor) | D1: `IronTheme` only provider of `LocalIronFinish`; `IronShell` drops `Crossfade`+provider, `animateColorAsState(canvas, tween 220)` | **Yes** | Plan §2.5 (merged §2.1) + §7 items 4/5/20; repo `IronTheme.kt:67-69` vs `IronShell.kt:150-152` double-provider verified; fix direction correct |
| 2. Tab efficiency — three alternatives, no pick | D2: keep `GearTabTransition`/`AnimatedContent` (unmount = cheap), pass `active:Boolean` into `TheBench`/`OpticsBench`, `rememberSaveable` for pager/ticker/segment | **Yes** | Plan §1.5 + §2.5; no HorizontalPager keep-alive this pass |
| 3. PAPER INSERTS default not decided | D3: fresh `!prefs.contains(KEY)` → false (metal Graphite), preserve stored bool, migration in `ThemePreferences.getLightTankBg` | **Yes** | Plan §2.3 + §6; repo `ThemePreferences.getLightTankBg` default `true` confirmed |
| 4. Riso counting mechanism wrong | D4: `IronScreen` resets per screen via `remember { mutableIntStateOf(0) }` + `CompositionLocalProvider(LocalRisoCount)`, `RisoText` `DisposableEffect(Unit)` once per instance, Bridge `RisoText` → `EngravedText` | **Yes** | Plan §4.1; repo `Primitives.kt:63` `SideEffect` + `IronTheme.kt:26-30` `remember(name){count=0}` both wrong, plan replaces correctly |
| 5. Reduced-motion shutter/ignition branches underspecified | D6/D7/D8: shutter 200ms dim scrim `onSeam`, ignition 200ms fade skip sweep `delay(200)`, needle `tween(150, LinearEasing)` | **Yes** | Plan §1.6 table + §1.5/§1.8; repo `ShutterOverlay.kt:32-35`, `Ignition.kt:40-41`, `InstrumentDial.kt:88,94,97` `IronMotion.needle()` unconditional verified |

D9 + §2.0 added in ITER 3: **verified correct** — see §6 below.

## Criteria Evaluation

1. **Architecture & behavioral ownership — PASS**
   - `IronTheme` single owner `LocalIronFinish`/`LocalPaperSurfaces`/`LocalReducedMotion`/`LocalRisoCount`; `IronShell` only animates canvas color 220ms, no `Crossfade`, no re-provide. Tree stays mounted, tab state survives. `LocalCeremonyGate` hoisted to `IronShell` (FeedbackExtras.kt), TheBench/ShutterOverlay use `gate.run`. Correct per spec §4.1 one ceremony/screen.
   - Tab strategy pinned minimal: `AnimatedContent` unmount + `active` gate. Not composing 4 tabs, not pager.
   - Finish count 2 not 3: D9 correctly forbids third `IronSkin`; SYSTEM has no palette. Grain/phosphor/ink-plate split ownership correct (see §6).

2. **Data/control flow & API contracts — PASS**
   - Single `ui.iron.ThemeMode` SYSTEM/VELLUM/GRAPHITE; storage `system`/`vellum`/`graphite`; `fromStorage` migrates `light→VELLUM`, `dark→GRAPHITE` plus new strings. Ordinal 0/1/2 stable with old LIGHT/DARK, so `rememberSaveable` ordinal survives. `MainActivity`/`MainScreen`/`Toolbox` all use iron type, bridging deleted.
   - `paperInserts` default via `contains`, migration in same file. `LocalPaperSurfaces = finish==VELLUM || paperInserts`. Vellum always paper regardless toggle — correct.
   - `mechanical_motion` `full`/`reduced`/`auto`(default) → `reducedMotionOverride` false/true/null; resolve `systemReduced || override==true` (scale 0 always wins). Toolbox shows FULL/REDUCED only, writes `full`/`reduced`.

3. **Lifecycle & threading/concurrency — PASS**
   - `OpticsBench` 500ms loop `LaunchedEffect(active)` cancel on inactive/dispose. `FieldManual` probe `LaunchedEffect(page){ if(page!=4) return; while(true){onProbe();delay(1200)} }`. One-shot `MainActivity.probeBackends()` kept.
   - Replay overlay `if(replayOverlay!=null) skip tab content` stops dial drift + BenchViewModel ticks under booklet. `InstrumentDial` `active` added, both drift/hunt gated. Correct.

4. **Persistence/storage & error handling — PASS**
   - Pref keys inventoried, backward compat read, no extra migrate fn. Existing stored `light_tank_bg=true` preserved. Fresh false.

5. **Compatibility & testing & edge cases & scope — PASS**
   - `LaunchMatrix.kt` blur `Build.VERSION.SDK_INT>=31 && !reduced && abs(offset)>0.5f` else no blur. Correct (<31 no RenderEffect).
   - Grain blend: `Modifier.ironGrain` not `@Composable` cannot call `ironSkin()` — plan adds `paper:Boolean` param or composable wrapper reading `ironSkin().isPaper` and delegating to `drawWithCache` with `BlendMode.Multiply/Screen`. Correct fix. Keep-list 7 vs drop-list explicit accurate (shell root, Ignition, Manual, Ledger, DebugBench, BenchSheet scrim, Stamp 12%).

## Findings

### [MINOR] IronShell `finish` param — pin drop, not hint
Location: `IronShell.kt:137-152` + plan §2.5.3
Problem: Plan says drop `finish:IronFinish` param **or** keep as paint hint — preferred drop. Leaves minor option. Worker could keep param and still re-provide.
Evidence: Current `IronShell(finish:IronFinish)` used in `Crossfade(finish)`. Desired: `Box(background(animateColorAsState(ironSkin().canvas)))` reading local.
Impact: Low — ambiguous wording could keep stale prop drilling.
Required planner change: None blocking. Worker action: delete `finish` param from `IronShell` and `MainScreen` call site; rely on `ironSkin().canvas`. If kept for preview, mark deprecated and never provide LocalIronFinish.

### [MINOR] Grain blend wrapper signature — clarify overload
Location: `IronTheme.kt:92-98` `Modifier.ironGrain` + plan §2.0
Problem: Plan proposes `paper:Boolean = false` param **or** `@Composable fun Modifier.ironGrain()` wrapper. Wrapper is `@Composable` extension on `Modifier` — unusual but valid; must be called inside `@Composable` call sites (all are). Ensure one helper, not two competing.
Evidence: `ironGrain` is `Modifier.drawWithCache` helper, not composable.
Impact: Low. Worker may create two helpers.
Required planner change: None blocking. Implement one: `@Composable fun Modifier.ironGrain(alpha:Float=0.04f):Modifier { val isPaper=ironSkin().isPaper; return ironGrainInternal(alpha,isPaper) }` + private non-composable with `paper:Boolean`. Keep call sites `.ironGrain(0.04f)`.

### [MINOR] Phosphor on chassis LEDs — nuance
Location: `Primitives.kt:45,53` `LedDot` + plan §2.2
Problem: Plan maps all `Phosphor400→skin.phosphor()` including `LedDot`. Bridge/Gear stay Anvil both finishes (pack III). Chassis LEDs on Anvil should stay `Phosphor400` even in Vellum for contrast; plan says "Fine on Bridge too" — acceptable but slightly over-applies Vellum phosphor to metal chassis.
Evidence: Spec §10 accents `phosphor.600` on paper; chassis is dark metal even in Vellum.
Impact: Negligible visual; phosphor 600 on dark still readable. No block.
Required planner change: Keep as plan but if review flags, gate `LedDot` phosphor by `isPaper && !isChassis` — out of scope for this pass.

### [MINOR] Pixel-match acceptance — scope to content
Location: Plan §8 Theme `SYSTEM+dark == GRAPHITE` pixel-match
Problem: Status/nav bar tint + `isSystemInDarkTheme()` source differ by OS; full screenshot including bars may mismatch. Test must compare content canvas/plates, not bars.
Evidence: `IronTheme` SideEffect sets `isAppearanceLightStatusBars = finish==VELLUM`.
Impact: Flaky golden if bars included.
Required planner change: Keep check but scope: compare `IronShell` content background + plate colors; bars verified via `WindowInsetsController` assertion, not pixels.

### [MINOR] `systemReduced` not reactive to animator scale change
Location: `IronTheme.kt:48-54`
Problem: `remember { Settings.Global.getFloat(...ANIMATOR_DURATION_SCALE)==0f }` captures once. Changing animator scale in dev options until recompose won't flip. `remember(key= animatorScaleFlow)` not needed this pass but note.
Evidence: Plan keeps `systemReduced || override==true`.
Impact: Low — requires activity restart to pick up scale change, matches current behavior.
Required planner change: None this pass. Future: read via `snapshotFlow` or re-read on resume.

### [SUGGESTION] Keep `material3.Text` — correct YAGNI
Location: Plan §6/§10
Problem: None. Plan correctly leaves `material3.Text`/`HorizontalDivider` — no mass replace. Approve.

## Spec Conflict Rulings

- **Field Manual stays paper — APPROVE.** Spec §7.2 explicit: manual is `bone.50` paper booklet with stitched binding. Plan §3 keeps paper, fixes only stripe/probe/grain/replay cost. Correct. Themed manual would be spec change.
- **PAPER INSERTS "off in Vellum" rejected as third look — APPROVE.** Spec §10 line "switches paper surfaces on in Graphite / off in Vellum" would produce cream canvas + metal plates (third look). D9 forbids; plan §2.3 keeps `LocalPaperSurfaces = VELLUM || paperInserts` so Vellum always paper. Toggle only adds paper plates onto Graphite. Correct Vellum = 2nd finish, not 3rd theme variant.
- **Vellum phosphor `600` (#3E9B2E) on paper, grain `Multiply`/`Screen` — APPROVE** as spec-required. Helper `IronSkin.phosphor()` + blend wrapper minimal, not overbuild.

## YAGNI / Overbuild Check — PASS

- Not adding third `IronSkin`/SYSTEM palette — correct deletion.
- Not adding HorizontalPager keep-alive, Roborazzi, baseline profile, APK audit, HUD redesign, i18n, replacing Material Text — all correctly out of scope.
- Phosphor helper (1 fun), grain blend (1 branch), ink-plate split (reuse `EngravedPlate` vs `IronSurface`/`PaperPlate`) are smallest spec-correct deltas. No new deps, no factory/interface inflation. Approve.

## Completeness vs User Asks

| User ask ("3 themes" observation) | Plan coverage | Verdict |
|---|---|---|
| GRAPHITE done | Canvas + tokens done — plan keeps | ✅ |
| VELLUM partial (canvas flips, content still `Iron.Bone100` cream-on-cream) | §2.2 full content chrome `skin.text`/`IronSurface` + data readouts stay ink plates + accents `phosphor()` | ✅ Complete |
| SYSTEM-dark == GRAPHITE identical — correct, not missing theme | D9 explicitly pins identical pixels; fix is resolve, not art | ✅ Correct |
| SYSTEM-light == broken Vellum until Vellum content fixed | §2.2+§2.5 fix makes SYSTEM-light == VELLUM | ✅ |
| "3rd one same as first" — do not invent third look | D9 forbids; plan has guardrail + risk | ✅ |
| Grain blend MULTIPLY/SCREEN missing | §2.0 wrapper | ✅ |
| Phosphor 600 missing | `IronSkin.phosphor()` | ✅ |
| Onboarding on-theme only | §3 keeps paper, fixes stripe/probe | ✅ |

## Acceptance Criteria — Testable? YES
- `grep rememberInfiniteTransition` → 0; Home idle 0 timed recompositions
- Keep-list 7 grain sites only
- `SYSTEM+dark screenshot` vs `GRAPHITE` — content plates match (scope bars separately)
- `SYSTEM+light` vs `VELLUM` — match after Vellum content patch
- Vellum readable (ink on bone), data readouts dark + bone mono, Bridge/Gear stay anvil, dial `dialPalette()` stays, 220ms canvas `animateColorAsState` no `Crossfade` remount
- Reduced: shutter 200ms dim, ignition 200ms fade route 200ms not 550ms, needle linear 150ms, no shavings/slam/roll/marquee/wobble
- `CeremonyGate` shared, replay no Home drift, probe page 4 only, Zen grep 0

## Order of Work — APPROVE
1. ChamferButton busy-only → 2. Grain de-stack → 3. Lifecycle gating (`active`, probe, replay skip, CeremonyGate) **before** 4. Theme unify (remount removal) → 5. Toolbox motion + reduced branches → 6. Onboarding leftovers → 7. Riso + ticker → 8. Delete Zen/GoogleFonts. Correct: gating before remount removal prevents hiding leaks.

## What Must Change Before Worker Starts
Nothing blocking. Minors above are worker notes, not plan rewrites. If planner wants zero ambiguity, tighten the two "or" wordings (finish param, grain overload) in next edit, but not required for APPROVE.

---
## Structured Summary

```
PLAN_REVIEW: APPROVE
PASS: 1
ITERATION: 3
CRITICAL: 0
MAJOR: 0
MINOR: 4
SUGGESTIONS: 1
NEXT_AGENT: Worker
NEXT_ACTION: Implement docs/plans/ironwork-fix-pass-plan.md ITER 3 as written; respect D1-D9 pins, two finishes only, keep Field Manual paper, no third IronSkin, follow file-by-file change list §7 and order §9
```
