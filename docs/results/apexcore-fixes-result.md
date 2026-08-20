# Result

## Task
- Task ID: `apexcore-fixes`
- User Requirements:
  1. Remove "Free RAM" / "RAM Free" button from Home screen.
  2. Remove "Privacy Policy" button from SetupDialog.
  3. Fix UI issue in GamesScreen where resource demand badge text touches container boundary.
  4. Remove 'test' and 'dummy' text from HUD Overlay screen; fix HUD overlay running state persistence across app close/relaunch.
  5. Create original fully rendered in-app Privacy Policy page (offline-first, no external browser dependency, no INTERNET permission added).

## Final Status
DONE

---

## Workflow Summary

| Stage | Pass | Iteration | Status | Output / Findings |
|---|:---:|:---:|:---:|---|
| **PLANNING** | 1 | 1 | REVISE | 4 MAJOR, 3 MINOR findings flagged in plan review. |
| **PLANNING** | 2 | 2 | APPROVE | All 7 findings resolved. Deterministic line-level targets, offline-first policy page specified. |
| **IMPLEMENTATION** | 1 | 1 | APPROVE | Code implemented cleanly across 7 modified and 2 new files. |
| **IMPL_REVIEW** | 1 | 1 | APPROVE | Code verified against acceptance criteria. 0 Critical, 0 Major, 1 Minor (untracked files staged for git). |
| **MANUAL_TEST** | 1 | 1 | REVISE | Device testing uncovered Haze blur modifier crash when entering PrivacyPolicyScreen. |
| **MANUAL_TEST** | 2 | 2 | PASS | Fixed Haze top bar crash. All 6 test scenarios passed on physical Android device (`2a580689`). |
| **FINALIZATION** | 1 | 1 | DONE | Final result documented, daemon stopped, changes committed and pushed. |

---

## Implementation

1. **Home Screen RAM Free Removal**:
   - Removed `HomeAnimatedEntryRow` for "RAM Free" and trailing `Spacer` from `HomeScreen.kt`.
   - Removed `onRamFreeClick` callback parameter from `HomeScreen.kt` and invocation from `MainScreen.kt`.
   - Maintained correct layout spacing for remaining items ("Game optimisation", "Pin Apps", "Thermal Telemetry").

2. **Setup Dialog Privacy Policy Removal**:
   - Deleted the `PRIVACY POLICY` button pill and trailing `Spacer` from `SetupDialog.kt`.
   - Preserved `openPrivacyPolicy()` with `@Deprecated` annotation for fallback.
   - Retained Settings screen entry point for in-app Privacy Policy navigation.

3. **Demand Badge Padding Fix**:
   - Increased padding of resource demand badges (HIGH, MEDIUM, LOW) on game cards in `GamesScreen.kt` from `horizontal = 8.dp, vertical = 3.dp` to `horizontal = 10.dp, vertical = 5.dp`.
   - Ensured clean visual separation between badge text and container outline.

4. **HUD Overlay String Cleanup & State Persistence**:
   - Updated `OverlayScreen.kt` title from `TEST HUD OVERLAY` to `HUD OVERLAY` and description text from `Launch a dummy monitor...` to `Launch a preview overlay to check placement, transparency, and drag gestures.`
   - Implemented dual-source persistence in `GameOverlayService.kt` and `OverlayScreen.kt`:
     - Service persists running flag (`PREF_OVERLAY_RUNNING`) and package name (`PREF_OVERLAY_PKG`) to `SharedPreferences` upon start.
     - All exit paths (`onDestroy`, `shutdown()`, companion `stop()`) clear preferences.
     - `OverlayScreen` polls active state and reconciles in-memory status, preferences, overlay permissions, and service status, clearing drift on external kills.

5. **In-App Fully Rendered Privacy Policy Page**:
   - Added canonical offline asset `app/src/main/assets/privacy_policy.md` (92 lines, Sections 1-8).
   - Created `PrivacyPolicyScreen.kt` featuring a native Compose `LazyColumn` markdown parser supporting headers, subheadings, bullet items, bold formatting, and spacing without external dependencies or `INTERNET` permission.
   - Integrated full-screen navigation branch and back handler into `MainScreen.kt` with top/bottom system chrome auto-hiding.
   - Connected "Privacy Policy" card in `SettingsScreen.kt` to trigger in-app navigation.

---

## Files Changed & Tests

### Modified Files
- `app/src/main/kotlin/com/ivarna/apexcore/SetupDialog.kt`: Removed Privacy Policy pill button; marked fallback function deprecated.
- `app/src/main/kotlin/com/ivarna/apexcore/games/GameOverlayService.kt`: Added preference-based persistence and clean teardown methods.
- `app/src/main/kotlin/com/ivarna/apexcore/games/GamesScreen.kt`: Updated demand chip padding to `10.dp, 5.dp`.
- `app/src/main/kotlin/com/ivarna/apexcore/ui/home/HomeScreen.kt`: Removed RAM Free row and parameter.
- `app/src/main/kotlin/com/ivarna/apexcore/ui/overlay/OverlayScreen.kt`: Updated header/desc strings; added state polling and reconciliation.
- `app/src/main/kotlin/com/ivarna/apexcore/ui/settings/SettingsScreen.kt`: Added `onPrivacyClick` callback wiring.
- `app/src/main/kotlin/com/ivarna/apexcore/ui/shell/MainScreen.kt`: Added `showPrivacyPolicy` state, screen routing, and chrome visibility handling.

### New Files
- `app/src/main/assets/privacy_policy.md`: Offline markdown source for Privacy Policy.
- `app/src/main/kotlin/com/ivarna/apexcore/ui/legal/PrivacyPolicyScreen.kt`: In-app Compose screen rendering the privacy policy.

### Tests
- Unit / Build Verification: Gradle compilation and unit test suite passed (`./gradlew testDebugUnitTest`).
- Manual Runtime Tests: 6 test scenarios executed on physical device (`2a580689`).

---

## Runtime / Manual Verification

- **Device**: Physical Android Device (Serial: `2a580689`), Android OS (SDK 36 runtime compatibility).
- **Execution Summary**:
  - Home screen: Confirmed absence of "RAM Free" button across full scroll.
  - Setup Dialog: Confirmed "PRIVACY POLICY" pill button is removed.
  - Games Screen: Verified `RESOURCE DEMAND` badge has 10/5 dp padding without text touching borders.
  - HUD Overlay: Verified header reads "HUD OVERLAY" without "test" or "dummy" strings.
  - Overlay State Persistence: Verified overlay continues running after app backgrounding/closing; reopening app shows STOP button active; tapping STOP stops service cleanly.
  - Privacy Policy Screen: Verified tapping Privacy Policy in Settings opens in-app scrollable view (Sections 1-8), works offline without crash, and back button returns to Settings.

---

## Review Findings

- **Plan Review (Pass 1 & 2)**: Addressed target specificity, off-by-one spacer hunks, dual-source state derivation, and offline asset strategy.
- **Implementation Review (Pass 1)**: Approved all changes with 0 Critical, 0 Major, 1 Minor (staging untracked files).
- **Manual Test Review (Pass 1 & 2)**: Fixed Haze crash in PrivacyPolicyScreen; all 6 test scenarios re-verified with PASS.

---

## Evidence

- `docs/testers/apexcore-fixes/01_home_top.png`
- `docs/testers/apexcore-fixes/02_home_scrolled.png`
- `docs/testers/apexcore-fixes/03_setup_dialog.png`
- `docs/testers/apexcore-fixes/04_games_screen_badge.png`
- `docs/testers/apexcore-fixes/05_overlay_initial.png`
- `docs/testers/apexcore-fixes/06_overlay_running.png`
- `docs/testers/apexcore-fixes/07_overlay_after_reopen.png`
- `docs/testers/apexcore-fixes/08_overlay_running_after_home.png`
- `docs/testers/apexcore-fixes/09_settings_privacy_button.png`
- `docs/testers/apexcore-fixes/10_privacy_policy_page_top.png`
- `docs/testers/apexcore-fixes/11_privacy_policy_page_middle.png`
- `docs/testers/apexcore-fixes/12_privacy_policy_page_bottom.png`
- `docs/testers/apexcore-fixes/13_back_to_settings.png`
- `docs/testers/apexcore-fixes/apexcore-fixes-test-report.md`
- `docs/reviews/apexcore-fixes-impl-review.md`
- `docs/plans/apexcore-fixes-plan.md`

---

## Remaining Limitations

- Privacy Policy is bundled offline in `app/src/main/assets/privacy_policy.md`. Changes to canonical `docs/privacy-policy.md` require asset synchronization.

---

## Final Acceptance Criteria

| # | Acceptance Criterion | Status | Evidence |
|---|---|:---:|---|
| 1 | Free RAM button removed from Home screen | VERIFIED | `01_home_top.png`, `02_home_scrolled.png` |
| 2 | Privacy Policy button removed from Setup dialog | VERIFIED | `03_setup_dialog.png` |
| 3 | Resource demand tag text padding fixed on Games screen | VERIFIED | `04_games_screen_badge.png` |
| 4a | "Test" and "dummy" labels removed from HUD overlay UI | VERIFIED | `05_overlay_initial.png` |
| 4b | HUD overlay running state persists across app close / relaunch | VERIFIED | `06_overlay_running.png`, `08_overlay_running_after_home.png` |
| 5 | Original in-app rendered Privacy Policy page fully functional | VERIFIED | `10_privacy_policy_page_top.png` - `13_back_to_settings.png` |

---

## Final Verdict & Verification Statement

**VERDICT**: VERIFIED / PASS

All requested features, fixes, and behavioral verifications have been implemented, reviewed, and confirmed on a physical Android device. Gradle daemons stopped, codebase staged, committed, and pushed.
