# Plan Review: IRONWORK Redesign

| | |
|---|---|
| **Task ID** | ironwork-redesign |
| **Document Under Review** | `docs/plans/ironwork-redesign-plan.md` |
| **Spec Reference** | `docs/design/new_design.md` (AC-DS-004 REV D) + Implementation Packs I–III (`docs/design/new_implementation_part_{1,2,3}.md`) + IA (`docs/ui-ux.md`) |
| **Review Stage** | PLAN_REVIEW (Pass 1, Iteration 1) |
| **Verdict** | **APPROVE** |

---

## Executive Summary

The implementation plan for the **IRONWORK** redesign (`docs/plans/ironwork-redesign-plan.md`) is comprehensive, technically sound, consistent with the repository architecture, and faithfully incorporates all design specifications from AC-DS-004 and implementation packs I, II, and III.

The plan covers all 12 redesigned surfaces, foundational tokens (colors, typography, shapes, grain, serial numbers), haptic grammar (`Clack` / `HapticGate`), motion and ceremony gating (`CeremonyGate`), bottom sheets (`BenchSheet`), floating HUD (`Phantom Rail` / `GameOverlayService`), asset pipelines, Roborazzi screenshot tests, and the debug QA harness (`DebugBench`).

All 11 acceptance criteria sections (§13.1–§13.11) are clearly defined, testable, and aligned with the design specification.

---

## Criteria Evaluation

### 1. Architecture & Behavioral Ownership
- **Pass**: The plan accurately maps current repository architecture (`MainActivity`, `FreezeFramework`, `FpsStack`, `GameManager`, `TuneManager`, `RamFillerManager`, `ThermalMonitor`) to the new Compose-based IRONWORK UI in `ui/iron/`.
- No architectural regressions: functional backends remain intact; changes are strictly in presentation, interaction, haptics, and telemetry formatting.

### 2. Data & Control Flow / API Contracts
- **Pass**: Data flow is explicitly designed with `BenchViewModel` and state hoisting in `IronShell`.
- ADAPT points for `FreezeFramework`, `ThermalMonitor`, `FpsStack`, `MemInfo` (`getSystemMemStats`), and `GameOverlayService` are fully documented and match existing contracts.

### 3. Lifecycle & Threading
- **Pass**:
  - Pressure Room correctly enforces keep-screen-on and lifecycle pause/dispose cancellation.
  - HUD overlay handles external service lifecycle and settings polling cleanly.
  - Ceremony loops (particles, odometer, shutter) are bounded and self-terminating.

### 4. Persistence & Storage
- **Pass**:
  - Prefs keys (`preferred_backend`, `setup_shown_v1`, `hud_size`, `hud_opacity`, `hud_edge`, theme settings) preserve backward compatibility with existing keys.
  - Re-use of the light tank glass storage key for Paper Inserts ensures smooth migration.

### 5. Compatibility & Scope
- **Pass**:
  - `minSdk 24` fallbacks for haptics, blur (`RenderEffect` on API 31+), adaptive icons, and predictive back (`activity-compose:1.10.0` / API 33+) are rigorously specified.
  - Explicit out-of-scope section (§16) protects project boundaries.

---

## Findings

**CRITICAL: 0**
**MAJOR: 0**
**MINOR: 0**
**SUGGESTIONS: 2**

### Suggestion 1: ThemeMode Enum Reconciliation
- **Location**: `ui/iron/Skin.kt` vs `ui/theme/ThemePreferences.kt`
- **Observation**: Existing `ThemePreferences` uses `ThemeMode.SYSTEM / LIGHT / DARK`. `Skin.kt` defines `ThemeMode.SYSTEM / VELLUM / GRAPHITE`.
- **Recommendation**: Worker should align `ThemePreferences` serialization (or provide an adapter mapping `LIGHT` ↔ `VELLUM`, `DARK` ↔ `GRAPHITE`) so existing saved user preferences do not throw `IllegalArgumentException` on enum lookup.

### Suggestion 2: Dependency Cleanup post-Zen
- **Location**: `app/build.gradle.kts`
- **Observation**: `dev.chrisbanes.haze:haze` was used for the Zen Organic glassmorphic style.
- **Recommendation**: Once the IRONWORK redesign replaces all Zen screens and components, remove `haze` dependencies in Phase F to keep the APK footprint minimal.

---

## Structured Summary

```
PLAN_REVIEW: APPROVE
PASS: 1
ITERATION: 1
CRITICAL: 0
MAJOR: 0
MINOR: 0
SUGGESTIONS: 2
NEXT_AGENT: Worker
NEXT_ACTION: Implement Phase A (Foundations: fonts, tokens, theme, models, gradle/manifest updates) per docs/plans/ironwork-redesign-plan.md
```
