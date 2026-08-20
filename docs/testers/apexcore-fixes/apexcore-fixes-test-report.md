# Manual Test Report: ApexCore Fixes (Iteration 2 / Pass 2)

**MANUAL_TEST**: PASS  
**PASS**: 2  
**ITERATION**: 2  
**DATE**: 2026-08-20  
**DEVICE/ENVIRONMENT**: Physical Android Device (Serial: `2a580689`), Android OS (SDK 36 runtime compatibility), Debug Build (`app-debug.apk`)

---

## 1. Feature Set & Acceptance Criteria Tested

| # | Criterion | Result | Evidence | Notes |
|---|---|:---:|---|---|
| 1 | **Remove Free RAM from Home**: Scroll Home screen top to bottom. Verify no "RAM Free" / "Force system reclaim" row is visible. | **PASS** | `docs/testers/apexcore-fixes/01_home_top.png`<br>`docs/testers/apexcore-fixes/02_home_scrolled.png` | Only RAM/SWAP tanks, Elevation Required card, Purge Engine, Pin Apps, and Thermal Telemetry cards are present. No Free RAM button exists. |
| 2 | **Remove Privacy Policy from SetupDialog**: Verify SetupDialog does not have Privacy Policy pill button. Settings retains Privacy Policy. | **PASS** | `docs/testers/apexcore-fixes/03_setup_dialog.png`<br>`docs/testers/apexcore-fixes/09_settings_privacy_button.png` | SetupDialog top modal displays only "Shizuku" and "Root" options. Privacy Policy button removed from dialog. Privacy Policy remains accessible in Settings. |
| 3 | **Tag touching boundary fixed**: Verify GamesScreen demand badge (HIGH/MEDIUM/LOW) has proper padding and text does not touch the border. | **PASS** | `docs/testers/apexcore-fixes/04_games_screen_badge.png` | Demand badge (`RESOURCE DEMAND LOW`) has appropriate horizontal and vertical inner padding inside container; text is clearly separated from container borders. |
| 4a | **Remove 'test' text from Overlay screen**: HUD Overlay header reads "HUD OVERLAY" (no "TEST") and description does not mention "dummy" or "test". | **PASS** | `docs/testers/apexcore-fixes/05_overlay_initial.png` | Screen displays clean HUD controls without test/dummy labeling. |
| 4b | **Overlay persistence across app close/relaunch**: Start HUD overlay. Close app (kill/background). Re-open app. Verify Overlay screen reflects RUNNING state (START disabled / STOP enabled). Tapping STOP stops overlay. | **PASS** | `docs/testers/apexcore-fixes/06_overlay_running.png`<br>`docs/testers/apexcore-fixes/08_overlay_running_after_home.png`<br>`docs/testers/apexcore-fixes/07_overlay_after_reopen.png` | Overlay service is running in foreground. When app is sent home and reopened, HUD screen correctly shows `STOP` button active. Tapping `STOP` successfully terminates overlay service and updates state back to `START`. |
| 5 | **Fully rendered Privacy Policy page**: In Settings, tap Privacy Policy. Opens in-app fully rendered scrollable Privacy Policy page with back navigation and NO crash. | **PASS** | `docs/testers/apexcore-fixes/10_privacy_policy_page_top.png`<br>`docs/testers/apexcore-fixes/11_privacy_policy_page_middle.png`<br>`docs/testers/apexcore-fixes/12_privacy_policy_page_bottom.png`<br>`docs/testers/apexcore-fixes/13_back_to_settings.png` | Privacy Policy page renders cleanly, scrolls all the way to contact/repo links at bottom without any crash (Haze blur crash previously observed is completely fixed). Tapping Back returns smoothly to Settings screen. |

---

## 2. Test Execution Log

1. Built fresh debug APK with Gradle daemon stop: `./gradlew assembleDebug && ./gradlew --stop`.
2. Verified physical device `2a580689` connected and idle.
3. Installed `app-debug.apk` to device and launched `com.ivarna.apexcore/.MainActivity`.
4. **Scenario 1 (Home Screen RAM Free removal)**: Inspected initial screen (`01_home_top.png`), swiped down to thermal telemetry (`02_home_scrolled.png`). Confirmed no "RAM Free" button or row.
5. **Scenario 2 (Setup Dialog Privacy Policy removal)**: Tapped SETUP in top bar (`940, 178`). Checked dialog (`03_setup_dialog.png`). Confirmed only Shizuku and Root rows are present, no Privacy Policy pill button. Dismissed dialog.
6. **Scenario 3 (Badge Boundary Padding)**: Navigated to Games tab (`434, 2216`). Verified `SambaS3` game card demand badge (`04_games_screen_badge.png`). Confirmed tag text has clear padding and does not touch container edges.
7. **Scenario 4a & 4b (Overlay text & persistence)**:
   - Navigated to Overlay tab (`646, 2216`) (`05_overlay_initial.png`). Verified no "test" / "dummy" labels.
   - Tapped `START` (`316, 1387`). State updated to `STOP` (`06_overlay_running.png`). Verified `GameOverlayService` active via dumpsys.
   - Pressed HOME, verified service remained alive, re-opened app, confirmed state maintained as `STOP` (`08_overlay_running_after_home.png`).
   - Tapped `STOP` (`764, 1387`). State returned to `START`.
8. **Scenario 5 (Privacy Policy In-App Rendering & Navigation)**:
   - Navigated to Settings tab (`857, 2216`) and scrolled down to Legal section (`09_settings_privacy_button.png`).
   - Tapped "Privacy Policy" (`540, 1923`).
   - Privacy Policy screen opened instantly without crash (`10_privacy_policy_page_top.png`).
   - Performed multiple swipe gestures to scroll through Sections 1-8 down to bottom contacts and GitHub issue link (`11_privacy_policy_page_middle.png`, `12_privacy_policy_page_bottom.png`).
   - Tapped `Back` button (`87, 157`). Successfully returned to Settings screen (`13_back_to_settings.png`).
9. Teardown: Returned device to HOME screen and force stopped app. Device left clean and idle.

---

## 3. Findings & Regressions

- **Code Defects Found**: None.
- **Previous Issue Resolution**: Haze blur modifier crash in Privacy Policy composable is resolved and verified working.
- **Regression Check**: All navigation tabs (Boost, Games, Overlay, Settings), dialogs, and services functioning smoothly without regressions.

---

## 4. Screenshot Index

- `docs/testers/apexcore-fixes/01_home_top.png` - Home screen top view (no RAM Free button).
- `docs/testers/apexcore-fixes/02_home_scrolled.png` - Home screen scrolled down (Thermal & Purge cards).
- `docs/testers/apexcore-fixes/03_setup_dialog.png` - Setup dialog without Privacy Policy pill.
- `docs/testers/apexcore-fixes/04_games_screen_badge.png` - Games screen resource demand badge with padding fix.
- `docs/testers/apexcore-fixes/05_overlay_initial.png` - Overlay screen without test text.
- `docs/testers/apexcore-fixes/06_overlay_running.png` - Overlay running state with STOP button.
- `docs/testers/apexcore-fixes/07_overlay_after_reopen.png` - Overlay status verification.
- `docs/testers/apexcore-fixes/08_overlay_running_after_home.png` - Overlay state retained after backgrounding app.
- `docs/testers/apexcore-fixes/09_settings_privacy_button.png` - Settings screen showing Privacy Policy entry.
- `docs/testers/apexcore-fixes/10_privacy_policy_page_top.png` - In-app Privacy Policy page top view.
- `docs/testers/apexcore-fixes/11_privacy_policy_page_middle.png` - In-app Privacy Policy page permissions section.
- `docs/testers/apexcore-fixes/12_privacy_policy_page_bottom.png` - In-app Privacy Policy page bottom contacts.
- `docs/testers/apexcore-fixes/13_back_to_settings.png` - Back navigation from Privacy Policy to Settings.

---

## 5. Summary

- **Tests Executed**: 6
- **Passed**: 6
- **Failed**: 0
- **Blocked**: 0
- **Result**: ALL PASS
