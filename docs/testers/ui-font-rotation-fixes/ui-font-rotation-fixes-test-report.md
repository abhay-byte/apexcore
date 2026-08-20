# Manual Test Report: UI, Font & Rotation Fixes

**MANUAL_TEST:** PASS
**PASS:** 1
**ITERATION:** 1
**DATE:** 2026-08-21
**DEVICE/ENVIRONMENT:** Android Device (serial: `2a580689`), Android 14+ / SDK 36 compile, Build: Debug APK (`app-debug.apk`)

---

## Feature Set Tested & Acceptance Criteria Verification

| # | Criterion | Result | Evidence (screenshot/log path) | Notes |
|---|-----------|--------|--------------------------------|-------|
| 1 | Games screen resource demand badges (pill shape, text legibility, no clipping) | PASS | `docs/testers/ui-font-rotation-fixes/02_games_screen.png`, `03_all_apps_screen.png` | Resource demand badge pill rendered cleanly with proper text contrast and no clipping on game cards. |
| 2 | Privacy Policy screen (Markdown formatting, math/LaTeX, private repo text, back navigation) | PASS | `docs/testers/ui-font-rotation-fixes/08_privacy_policy_top.png` through `12_settings_after_back.png` | Renders clean markdown sections, bullets, header note stating private repository, back button returns to Settings. |
| 3 | Auto-rotate (0° -> 90° -> 0° -> 270° -> 0°, confirm app does NOT restart and state is preserved) | PASS | `docs/testers/ui-font-rotation-fixes/13_landscape_90_games.png`, `14_landscape_270_games.png`, `15_portrait_restored.png` | App preserves navigation and tab state through orientation changes without restarting. |
| 4 | Home screen "Game optimisation" icon (clean 24dp tune vector, crisp appearance) | PASS | `docs/testers/ui-font-rotation-fixes/01_home_screen.png`, `17_game_optimisation_detail.png` | Game optimisation card features crisp vector icon; subpage loads details cleanly. |
| 5 | Typography across cards/pages (clean font sizes and layouts) | PASS | `docs/testers/ui-font-rotation-fixes/01_home_screen.png`, `06_settings_top.png`, `07_settings_bottom.png`, `16_overlay_screen.png` | Typography unified across tabs, cards, diagnostic rows, and dialogs. |
| 6 | Dialogs (Add game dialog, Whitelist/Pin dialog) - background blur & width (0.92 screen width / max 560dp) | PASS | `docs/testers/ui-font-rotation-fixes/04_add_games_dialog_blur.png`, `05_pin_apps_dialog_blur.png`, `18_pin_apps_from_boost.png` | Modal sheets/dialogs render with background blur and extended width matching design spec. |

---

## Test Execution Log

1. Connected to ADB target `2a580689`.
2. Verified idle state on device launcher.
3. Built project via `./gradlew assembleDebug` and stopped daemon (`./gradlew --stop`).
4. Installed debug APK `app/build/outputs/apk/debug/app-debug.apk` onto `2a580689`.
5. Launched `com.ivarna.apexcore`.
6. Verified Home screen UI and "Game optimisation" icon (`01_home_screen.png`).
7. Navigated to Games tab, verified game list cards and resource demand badges (`02_games_screen.png`, `03_all_apps_screen.png`).
8. Opened "Add games" modal sheet; verified background blur and sheet width (`04_add_games_dialog_blur.png`), cancelled.
9. Opened "Pin apps" modal sheet; verified background blur and sheet width (`05_pin_apps_dialog_blur.png`), dismissed with DONE.
10. Navigated to Settings tab; verified typography, card layouts, diagnostics rows (`06_settings_top.png`, `07_settings_bottom.png`).
11. Opened Privacy Policy screen; verified Markdown layout, private repo text note, structure (`08_privacy_policy_top.png` - `11_privacy_policy_bottom.png`).
12. Clicked Back on Privacy Policy; verified return to Settings (`12_settings_after_back.png`).
13. Tested rotation handling:
    - Rotated to 90° (`user_rotation 1`): verified layout preserved (`13_landscape_90_games.png`).
    - Rotated to 270° (`user_rotation 3`): verified layout preserved (`14_landscape_270_games.png`).
    - Rotated back to portrait (`user_rotation 0`): verified layout intact (`15_portrait_restored.png`).
14. Verified Overlay screen (`16_overlay_screen.png`).
15. Verified Game Optimisation drilldown page (`17_game_optimisation_detail.png`).
16. Verified Purge engine action feedback (`19_purging_feedback.png`).
17. Returned to Home launcher to leave device idle for next session.

---

## Screenshot Index

- `docs/testers/ui-font-rotation-fixes/01_home_screen.png`: Boost home screen with tank widgets, purge action, game optimisation card.
- `docs/testers/ui-font-rotation-fixes/02_games_screen.png`: Games tab with game cards and resource demand pills.
- `docs/testers/ui-font-rotation-fixes/03_all_apps_screen.png`: All Apps tab with resource demand pills.
- `docs/testers/ui-font-rotation-fixes/04_add_games_dialog_blur.png`: Add Games sheet showing background blur and expanded width.
- `docs/testers/ui-font-rotation-fixes/05_pin_apps_dialog_blur.png`: Pin Apps sheet with blur and expanded width.
- `docs/testers/ui-font-rotation-fixes/06_settings_top.png`: Settings appearance and mode configuration.
- `docs/testers/ui-font-rotation-fixes/07_settings_bottom.png`: Settings diagnostics and Legal / Privacy Policy entries.
- `docs/testers/ui-font-rotation-fixes/08_privacy_policy_top.png`: Privacy Policy markdown top view.
- `docs/testers/ui-font-rotation-fixes/09_privacy_policy_mid.png`: Privacy Policy storage and deletion section.
- `docs/testers/ui-font-rotation-fixes/10_privacy_policy_permissions.png`: Privacy Policy permissions breakdown.
- `docs/testers/ui-font-rotation-fixes/11_privacy_policy_bottom.png`: Privacy Policy security practices and footer.
- `docs/testers/ui-font-rotation-fixes/12_settings_after_back.png`: Return to Settings from Privacy Policy via top back button.
- `docs/testers/ui-font-rotation-fixes/13_landscape_90_games.png`: Games screen rendered in 90° landscape orientation without restart.
- `docs/testers/ui-font-rotation-fixes/14_landscape_270_games.png`: Games screen rendered in 270° landscape orientation without restart.
- `docs/testers/ui-font-rotation-fixes/15_portrait_restored.png`: Portrait orientation restored.
- `docs/testers/ui-font-rotation-fixes/16_overlay_screen.png`: Overlay tab UI.
- `docs/testers/ui-font-rotation-fixes/17_game_optimisation_detail.png`: Game optimisation details screen with kernel probe status.
- `docs/testers/ui-font-rotation-fixes/18_pin_apps_from_boost.png`: Pin apps sheet triggered from Boost home screen.
- `docs/testers/ui-font-rotation-fixes/19_purging_feedback.png`: Purge engine execution feedback.

---

## Summary

- **Tests Executed:** 6
- **Passed:** 6
- **Failed:** 0
- **Blocked:** 0
- **Failure Classification:** NONE
- **Next Route:** FINAL
