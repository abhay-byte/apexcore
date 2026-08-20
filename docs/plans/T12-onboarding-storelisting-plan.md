# T12 Implementation Verification, Onboarding Refinement & Store Listing Redesign Plan

| Field | Value |
|---|---|
| **Document** | Implementation Plan — T12 Verification, Onboarding Header Image Removal & Store Listing Modernization |
| **Plan ID** | `T12-onboarding-storelisting-plan` |
| **Date** | 2026-08-20 |
| **Target** | Android 16 (API 36) / minSdk 24 |
| **Status** | READY FOR REVIEW |
| **Author** | Senior Android Systems & UI/UX Engineer |

---

## 1. Executive Summary & Goals

This plan addresses three interrelated deliverables:
1. **T12 Real Game Optimisation Verification:** Audit and verify the complete `com.ivarna.apexcore.tune` architecture against `docs/plans/T12-real-game-optimisation.md` and `docs/plans/T12-real-game-optimisation-results.md` on device and code levels.
2. **Onboarding Screen Refinement:** Remove the top artwork/image from the final onboarding page (`ElevationSetupPage` in `OnboardingScreen.kt`) so that the elevation backend options (Shizuku & Root) are immediately visible without unnecessary vertical scrolling.
3. **Store Listing Visual & Asset Redesign:** Overhaul `generate_playstore_images.py` and produce high-conversion, modern, aesthetic Zen Organic Play Store promotional assets (1080×1920 frames `1_hero` through `8_cta` and 1024×500 `feature_graphic.png`), syncing them to Fastlane and documentation directories.

---

## 2. Research & Codebase Investigation Findings

### 2.1 T12 Real Game Optimisation State
- **Architecture:** Complete clean-room implementation under `app/src/main/kotlin/com/ivarna/apexcore/tune/`.
- **Key Files & Roles:**
  - `TuneId.kt`: 36 normative IDs across 10 categories (GPU, CPU, Touch/Input, Thermal, Memory, Storage I/O, Display, Focus, Charging, Network).
  - `TuneSpecs.kt`: Specifications and metadata for each tune option.
  - `TuneCatalog.kt`: Sysfs path bindings, group IDs, and dynamic CPU/Mali cluster discovery.
  - `TuneProbe.kt`: Asynchronous 2-phase probe with a 3500ms wall deadline and 120ms timeout per node.
  - `TuneApplier.kt`: Concrete applier with insert-if-absent snapshotting, frequency percentile selection, scale normalization (uclamp 0–100 / 0–1024), and real Settings execution for Focus/Display (`FOCUS_HEADSUP`, `FOCUS_IMMERSIVE`, `DISPLAY_PEAK`, `DISPLAY_MIUI`, `FOCUS_DND`).
  - `TuneSnapshotStore.kt` & `TunePrefs.kt`: Persistent state keyed by boot count (`Settings.Global.BOOT_COUNT`) and `/proc/sys/kernel/random/boot_id`.
  - `TuneManager.kt`: Facade managing session lifecycle, mutex protection, and orphan recovery.
  - `TuneSessionWatchdog.kt`: Primary session restore owner for overlay-less launches with UsageStats polling and streak recovery.
  - `HomeScreen.kt`: Displays "Game optimisation" entry row above "RAM Free" only when elevated (`Shizuku` or `Root`), showing active available count.
  - `TuneScreen.kt`: Full-screen categorized UI, sorting categories with supported options to the top, collapsing categories by default.
  - `GameLauncher.kt`: Freezes background apps, starts game activity, determines overlay vs watchdog ownership, and applies session tuning on `Dispatchers.IO`.
  - `GameOverlayService.kt`: Companion `@Volatile isRunning` flag, real game session validation (`EXTRA_PKG`), and exit watcher restoration.
- **Unit Tests:** 79 unit tests across `app/src/test/java/com/ivarna/apexcore/tune/` passing, verifying timeout bounds, non-blocking UI, watchdog fallback, and deadlock-free mutexes.

### 2.2 Onboarding Screen Top Image Issue
- **Location:** `app/src/main/kotlin/com/ivarna/apexcore/ui/onboarding/OnboardingScreen.kt` L507–657 (`ElevationSetupPage`).
- **Problem:** `ElevationSetupPage` contains a 230.dp Box displaying `R.drawable.ic_onboard_access` at the top:
  ```kotlin
  // Large Access Shield Art
  Box(
      modifier = Modifier
          .size(230.dp)
          .clip(RoundedCornerShape(32.dp)),
      contentAlignment = Alignment.Center
  ) {
      Image(
          painter = painterResource(id = page.imageRes),
          contentDescription = page.title,
          modifier = Modifier.fillMaxSize()
      )
  }
  ```
  This pushes the Shizuku and Root `OptionCard` components below the fold, forcing the user to scroll vertically to configure access.
- **Solution:** Remove this `Box` and `Image` entirely from `ElevationSetupPage`. Polish the top spacing (`Spacer(modifier = Modifier.height(28.dp))`), center the kicker badge ("04 · SYSTEM ACCESS"), title ("Elevate Your Control"), and description, allowing the Shizuku and Root cards to sit comfortably in the primary viewport without scrolling.

### 2.3 Store Listing Asset Generation Analysis
- **Current Script:** `docs/storelisting/generate_playstore_images.py`.
- **Existing Shortcomings:**
  - Phone framing is simplistic: basic single outline without modern bevel highlights, screen reflections, realistic dynamic island / camera pill, or diffuse drop shadows.
  - Text layout is plain and lacks modern visual hierarchy (flat kicker, standard wrapped title).
  - Background atmosphere uses basic linear depth and monochromatic noise without multi-color radial depth layers or glassmorphic accents.
  - Screenshots referenced in fallback map point to legacy or non-optimal captures rather than the crisp Zen Organic captures available in `docs/storelisting/organic_capture/`.
  - Feature Graphic (1024×500) lacks high-impact 3D floating phone perspective and modern branding glow.

---

## 3. Detailed Implementation Plan

### Workstream A: T12 Real Game Optimisation Verification & Confirmation
1. **Device Verification Checklist:**
   - Verify `HomeScreen` displays "Game optimisation" row only when backend is `Shizuku` or `Root`.
   - Verify clicking "Game optimisation" navigates to `TuneScreen`.
   - Verify `TuneScreen` displays categories sorted by available options, with capability badges (`ROOT`, `SHELL`, `AVAILABLE`, `UNSUPPORTED`).
   - Verify launching a game via `GameLauncher` triggers `applyForSession` and restores on exit.
2. **Codebase Quality & Honesty Audit:**
   - Confirm dummy preferences are deleted via `tuneManager.deleteDummyKeysIfNeeded()`.
   - Confirm thermal safeguards remain intact (no thermal disablement, sconfig fallback 13 → 10).
   - Ensure all 79 unit tests continue to pass cleanly.

---

### Workstream B: Onboarding Screen Layout Update
1. **Target File:** `app/src/main/kotlin/com/ivarna/apexcore/ui/onboarding/OnboardingScreen.kt`
2. **Modifications in `ElevationSetupPage`:**
   - Remove the `Box` containing `Image(painter = painterResource(id = page.imageRes))` (L529–L540).
   - Remove the extra `Spacer(modifier = Modifier.height(16.dp))` below the deleted image.
   - Set top padding of `Column` to `Spacer(modifier = Modifier.height(20.dp))`.
   - Keep the Kicker Badge (`04 · SYSTEM ACCESS`), Title (`Elevate Your Control`), Subtitle (`Select an elevated backend for deep freeze optimization.`), and `OptionCard` items for Shizuku and Root.
   - Verify layout looks balanced and eliminates awkward vertical scrolling on standard device aspect ratios (19.5:9, 20:9, 16:9).

---

### Workstream C: Store Listing & Asset Generation Redesign
1. **Target Script:** `docs/storelisting/generate_playstore_images.py`
2. **Modern Visual System Specification:**
   - **Canvas Dimensions:** 1080×1920 (Play Store Portrait Screenshots), 1024×500 (Feature Graphic).
   - **Color Palette (Zen Organic Dark Deluxe):**
     - Background: Deep Obsidian Slate `#070E12` / `#0A1317`
     - Ambient Accent Glows: Vibrant Cyan/Teal `#4EE4D0` (alpha 60–90), Radiant Amber/Gold `#F4C96B` (alpha 40–60), Soft Lavender accent `#7B61FF` (alpha 25)
     - Text Primary: Pure Ice `#F2FAFC` (High contrast, crisp rendering)
     - Text Subtitle / Dim: Soft Cyan Slate `#9AB3BE`
     - Highlight Badges: Neon Mint `#4EF5C6` with dark text `#070E12`
   - **Background Shader & Textures:**
     - Multi-layer radial chromatic gradients (primary teal spotlight + warm secondary corner accent).
     - Ultra-fine dot matrix / subtle grid overlay (opacity 6–10%) for a refined engineering aesthetic.
     - Soft film grain noise layer (opacity 8%) to eliminate banding.
     - Smooth vignette framing around edges.
   - **Device Frame Aesthetics (Flagship Phone Mockup):**
     - Ultra-thin titanium/dark matte bezel with 2.5D rounded corners (`radius = 34px`).
     - Subtle inner metallic stroke (`#2A424C` with top-left specular highlight `#6FD8C8` at 30% alpha).
     - Sleek top pill speaker / camera cutout.
     - Realistic multi-stage drop shadow:
       1. Contact shadow (dark, tight blur, 8px offset).
       2. Ambient diffuse shadow (wide spread, 40px blur, 20% opacity).
   - **Typography & Headline System:**
     - Kicker Badge: Pill container with gradient border and micro-dot icon, e.g. `◆ 01 · PURGE ENGINE`, `◆ 02 · REAL KERNEL TUNE`, `◆ 03 · PERFORMANCE HUD`.
     - Headline: 2-line punchy bold typography using Plus Jakarta Sans Bold (size 58–64pt).
     - Subtitle: Clear, benefit-focused description (size 24–26pt, line height 34pt).
3. **8-Frame Storyboard & Screen Mapping:**
   | Frame | File | Kicker Badge | Headline | Subtitle | Screenshot Source (`organic_capture/`) | Layout Style |
   |---|---|---|---|---|---|---|
   | 1 | `1_hero.png` | `◆ ZEN PERFORMANCE` | **ApexCore**<br>**Pure Game Focus** | Deep freeze bloat & unlock peak hardware performance | `01_home_store.png` + Center Glow Logo | Hero Phone + Ambient Aura |
   | 2 | `2_purge.png` | `◆ 01 · PURGE ENGINE` | **Deep Freeze Bloat**<br>**Zero Background Lag** | Reclaim CPU cycles and memory before launching games | `01_home.png` | Large Flagship Phone |
   | 3 | `3_ram_free.png` | `◆ 02 · MEMORY TOOLKIT` | **Force RAM Reclaim**<br>**Instant System Clean** | Safely clear cached memory with hardware-safe 90% cap | `04_ram_free.png` | Large Flagship Phone |
   | 4 | `4_overlay.png` | `◆ 03 · PERFORMANCE HUD` | **Live On-Screen HUD**<br>**FPS · RAM · CPU** | Real-time telemetry overlay rendered while you play | `03b_overlay_hud_active.png` | Large Flagship Phone |
   | 5 | `5_pin_apps.png` | `◆ 04 · APP WHITELIST` | **Pin Essential Apps**<br>**Stay Always Awake** | Protect messaging, audio, and tools from being frozen | `05_pin_apps.png` | Large Flagship Phone |
   | 6 | `6_library.png` | `◆ 05 · GAME LIBRARY` | **Your Game Hub**<br>**Allocate & Launch** | Automatic game detection with one-tap boost launch | `02_games.png` + `07_add_games.png` | Staggered Duo Phones |
   | 7 | `7_settings.png` | `◆ 06 · REAL KERNEL TUNE` | **Real Kernel Tuning**<br>**36 Advanced Knobs** | Frequency floors, GPU keep-awake & input boost | `01c_home_game_opt.png` | Large Flagship Phone |
   | 8 | `8_cta.png` | `◆ 07 · PRIVATE & LOCAL` | **100% On-Device**<br>**No Ads · No Tracking** | Shizuku & Root privileged speed without cloud dependencies | `06c_settings_privacy.png` + `10_backend_dropdown.png` | Staggered Duo Phones |

4. **Feature Graphic (1024×500) Overhaul:**
   - Left side: Glowing ApexCore brand icon + bold title "ApexCore" + subtitle "Zen Performance Engine" + 3 glowing feature chips ("NO ADS · NO ACCOUNTS", "REAL KERNEL TUNE", "100% ON-DEVICE").
   - Right side: Angled/floating phone mockup showcasing `03b_overlay_hud_active.png` with vibrant cyan backlight glow.
5. **Asset Output & Sync:**
   - Execute `generate_playstore_images.py` to write:
     - `docs/storelisting/1_hero.png` through `8_cta.png`
     - `docs/storelisting/feature_graphic.png` and `docs/storelisting/featureGraphic.png`
     - `docs/storelisting/phoneScreenshots/` (1 through 8)
     - `fastlane/metadata/android/en-US/images/phoneScreenshots/` (1 through 8)
     - `fastlane/metadata/android/en-US/images/featureGraphic.png`

---

## 4. Test & Validation Strategy

1. **Onboarding UI Validation:**
   - Verify `OnboardingScreen` Page 4 (`ElevationSetupPage`) renders without the top image.
   - Verify Shizuku and Root selection cards are fully visible above the bottom navigation bar on standard screens.
   - Verify selecting Shizuku or Root updates `preferred_backend` and detects status seamlessly.
2. **Tune / T12 Validation:**
   - Run complete unit test suite: `app/src/test/java/com/ivarna/apexcore/tune/` (ensure 79/79 passing).
   - Validate that `HomeScreen` game-optimisation entry row shows accurate capability counts on elevated devices.
3. **Store Listing Visual Quality Validation:**
   - Inspect generated 1080×1920 PNGs and 1024×500 feature graphic.
   - Check text contrast, sharpness of Plus Jakarta Sans typography, shadow realism, and screenshot fidelity.
   - Ensure all generated image files are valid PNG format with proper dimensions and optimized file sizes.

---

## 5. Acceptance Criteria & Feature Set

```
ACCEPTANCE_CRITERIA:
- [ ] T12 Real Game Optimisation verified on device and in test suite (79 unit tests passing, zero regressions).
- [ ] Onboarding Page 4 (ElevationSetupPage) top artwork image removed; layout polished and backend options immediately accessible without scrolling.
- [ ] generate_playstore_images.py updated with modern Zen Organic dark deluxe visual system (refined lighting, ambient glows, flagship phone bezel with drop shadows, crisp Plus Jakarta Sans typography, and clear benefit headlines).
- [ ] 8 store listing promotional frames (1080x1920) generated with correct Zen Organic screenshots showcasing Purge, RAM Free, HUD, Pin Apps, Games Library, Real Kernel Tune, and Privacy.
- [ ] Feature graphic (1024x500) generated with high-impact branding and floating device mockup.
- [ ] Fastlane metadata assets synchronized under fastlane/metadata/android/en-US/images/.
- [ ] Zero build or test failures across the ApexCore project.
```
