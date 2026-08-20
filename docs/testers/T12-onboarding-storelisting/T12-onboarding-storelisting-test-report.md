# Manual Test Report: T12 Real Game Optimisation, Onboarding Refinement & Store Listing Redesign

| Field | Value |
|---|---|
| **MANUAL_TEST** | **PASS** |
| **Pass** | 1 |
| **Iteration** | 1 |
| **Date** | 2026-08-20 |
| **Device / Environment** | realme X2 Pro (RMX1931), Android 14 / API 34, ApexCore Debug Build |

---

## 1. Feature Set Tested

| # | Criterion | Result | Evidence | Notes |
|---|---|---|---|---|
| 1 | Onboarding Page 4 (ElevationSetupPage) Top Image Removed | **PASS** | `docs/testers/T12-onboarding-storelisting/t12-ob-page4-01.png` | Shield graphic removed. "04 · SYSTEM ACCESS", Title, Description, and both Shizuku & Root option cards fit cleanly above the navigation bar without vertical scrolling. |
| 2 | Home Screen Game Optimisation Row Gating | **PASS** | `docs/testers/T12-onboarding-storelisting/t12-home-noelev-01.png`, `t12-home-elevated-01.png` | Hidden when non-elevated. Appears with accurate "2 available on this kernel" counter once elevated backend (Shizuku) is active. |
| 3 | TuneScreen Navigation & Category Rendering | **PASS** | `docs/testers/T12-onboarding-storelisting/t12-tunescreen-01.png`, `t12-tunescreen-expanded-01.png` | Successfully navigates from Home. Displays 10 categories sorted by capability (Focus 2/3 supported on top). Expand/collapse works smoothly with honest capability subtitles and interactive controls. |
| 4 | Store Listing Redesign & Asset Generation | **PASS** | `docs/storelisting/1_hero.png`–`8_cta.png`, `docs/storelisting/feature_graphic.png`, `fastlane/metadata/android/en-US/images/phoneScreenshots/` | All 8 promotional frames generated in 1080×1920 with Zen Organic Dark Deluxe system, modern flagship bezel, dual-stage drop shadows, Plus Jakarta Sans typography, and synced to fastlane metadata. |
| 5 | Stability & Zero Crashes | **PASS** | Logcat & UI Inspection | No ANRs, crashes, or rendering glitches observed during manual execution. |

---

## 2. Test Execution Log

1. **Build & Install:** Built debug APK with `./gradlew assembleDebug` and stopped daemon with `./gradlew --stop`. Installed cleanly to connected realme X2 Pro via ADB.
2. **Onboarding Verification:** Cleared package data and launched onboarding. Navigated to Page 4 (`ElevationSetupPage`). Captured `t12-ob-page4-01.png`. Verified top artwork box is removed and Shizuku/Root cards are immediately visible.
3. **Home Gate Verification:** Confirmed Home screen hides "Game optimisation" row when un-elevated (`t12-home-noelev-01.png`).
4. **Shizuku Elevation & Probe:** Started Shizuku server via adb, granted permission to ApexCore. Home screen updated to elevated state and displayed "Game optimisation - 2 available on this kernel" (`t12-home-elevated-01.png`).
5. **TuneScreen Verification:** Tapped "Game optimisation". Navigated to `TuneScreen` (`t12-tunescreen-01.png`). Expanded "FOCUS" category (`t12-tunescreen-expanded-01.png`). Verified 2 supported toggles ("Hide heads-up", "Immersive bars") and 1 unsupported toggle with honest subtitle ("Not supported (Needs Do Not Disturb access)").
6. **Store Listing Assets:** Validated generated assets in `docs/storelisting/` and `fastlane/metadata/android/en-US/images/phoneScreenshots/`.

---

## 3. Screenshot Index

- `docs/testers/T12-onboarding-storelisting/t12-ob-page4-01.png`: Onboarding Page 4 without top image.
- `docs/testers/T12-onboarding-storelisting/t12-home-noelev-01.png`: Home screen before elevation (Game optimisation row hidden).
- `docs/testers/T12-onboarding-storelisting/t12-home-elevated-01.png`: Home screen with active Shizuku backend ("2 available on this kernel").
- `docs/testers/T12-onboarding-storelisting/t12-tunescreen-01.png`: TuneScreen collapsed category list.
- `docs/testers/T12-onboarding-storelisting/t12-tunescreen-expanded-01.png`: TuneScreen with FOCUS category expanded showing capability-verified toggles.

---

## 4. Summary

- **Tests Executed:** 5
- **Passed:** 5
- **Failed:** 0
- **Blocked:** 0
- **Final Verdict:** **PASS**
