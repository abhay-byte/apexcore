# Implementation Review — apexcore-fixes — PASS 1 ITER 1

IMPLEMENTATION_REVIEW: APPROVE
PASS: 1
ITERATION: 1
CRITICAL: 0
MAJOR: 0
MINOR: 1
FINDINGS:
- [MINOR-1] Untracked new files not in git index — FIX_ROUTE: WORKER
  - Detail: `app/src/main/kotlin/com/ivarna/apexcore/ui/legal/PrivacyPolicyScreen.kt` and `app/src/main/assets/privacy_policy.md` exist on filesystem (verified via glob/read, 176 lines and 92 lines) but `git diff --stat HEAD` and `git status` show only 7 modified files, no added files. Implementation present but not staged/committed. Risk: CI/build could miss privacy page/asset if PR merged without `git add`.
  - Evidence: `git diff --stat HEAD` → 7 files changed, 66+/40-; `glob **/PrivacyPolicyScreen.kt` found; `glob **/privacy_policy.md` found at `app/src/main/assets/privacy_policy.md`; `read` shows asset identical to `docs/privacy-policy.md`.
  - Action: `git add app/src/main/kotlin/com/ivarna/apexcore/ui/legal/PrivacyPolicyScreen.kt app/src/main/assets/privacy_policy.md` before merge.

Scenario checks (vs acceptance criteria):
- Scenario 1 — Remove Free RAM from Home: PASS. Deleted RAM Free row (283-289) + trailing Spacer 291 only, kept Spacer 280 inside `if(isElevatedBackend)`, removed `onRamFreeClick` param at 91, removed passthrough at MainScreen:199. `grep -rn "RAM Free" app/src/main/kotlin/ui/home` → 0 in HomeScreen (confirmed via read). Gap tune→Pin 12.dp preserved.
- Scenario 2 — Remove Privacy Policy from SetupDialog pill (224-241): PASS. Pill Box clipped 50 + Spacer 243 deleted, `PRIVACY_POLICY_URL` const and `openPrivacyPolicy()` kept with `@Deprecated` at SetupDialog.kt:415. Settings LEGAL card guarded: still present, rerouted to `onPrivacyClick` (SettingsScreen.kt:228). `grep "PRIVACY POLICY"` → 0 hits.
- Scenario 3 — Tag touching boundary (GamesScreen demand badge): PASS. `GamesScreen.kt:492` padding 8/3 → 10/5 dp (matches plan ModeChip). Shape/border 1.dp kept.
- Scenario 4a — Remove 'test' text: PASS. `OverlayScreen.kt:166` "TEST HUD OVERLAY" → "HUD OVERLAY"; `:177` "dummy monitor to test..." → "preview overlay to check...". No remaining test/dummy in HUD strings.
- Scenario 4b — Overlay persistence across app close/relaunch: PASS (code-level). `GameOverlayService` writes `overlay_running`/`overlay_pkg` via `apply()` in `onStartCommand` (126-130), clears via `clearOverlayPrefs()` in `onDestroy` (190), `shutdown` (423), `companion stop` (634-638) — all 3 exit paths. `OverlayScreen.kt:32-39` fallback `getRunningServices` (deprecated own-pkg valid) + poll 1s reconciles `isRunning` vs prefs vs `canDrawOverlays`, clears drift when `!running && prefRunning`. Keys namespaced `overlay_running`/`overlay_pkg` in file "apexcore". Device-timed behavior (≤1s relaunch, ≤2s external kill) requires manual validation.
- Scenario 5 — Fully rendered Privacy Policy page: PASS. `PrivacyPolicyScreen.kt` LazyColumn native parser (## 17sp/20lh, ### 15sp, * / - → •, # 20sp title, ** stripped, blank→8.dp spacer, body 13sp/18sp onSurfaceVariant), Haze top bar with BackHandler, `LazyColumn` padding `ZenDimens.containerPadding`, bottomNavClearance. Asset `app/src/main/assets/privacy_policy.md` offline copy 92 lines identical to canonical `docs/privacy-policy.md`. `MainScreen.kt` adds `showPrivacyPolicy` (64), branch priority RamFree > Tune > PrivacyPolicy (144-150), dual `AnimatedVisibility` gated `!showRamFree && !showTuneScreen && !showPrivacyPolicy` (229,269), Settings wired `onPrivacyClick={showPrivacyPolicy=true}` (220). `AndroidManifest.xml` no INTERNET added.

Code quality notes:
- `SettingsScreen.kt:49` removed `import openPrivacyPolicy` (now unused) — cleaner than plan's keep-deprecated; not a defect (SetupDialog still hosts fallback).
- `OverlayScreen.kt` derived state uses `isRunning || (prefRunning && hasPermission && fallback)` — equivalent to plan intent; fallback throttled risk documented, primary is volatile + prefs+permission.
- `ponytail` comment present in PrivacyPolicyScreen.kt: offline duplicated from docs.

FIX_ROUTE: WORKER
EVIDENCE:
- `git diff --stat HEAD` → 7 modified files, 66 insertions 40 deletions
- `git diff HEAD -- SetupDialog.kt` → pill + spacer deleted, @Deprecated kept
- `git diff HEAD -- ui/home/HomeScreen.kt` → RAM Free row + spacer deleted, param removed
- `git diff HEAD -- games/GamesScreen.kt` → padding 10/5
- `git diff HEAD -- ui/overlay/OverlayScreen.kt` → fallback fn + poll + header/desc fix
- `git diff HEAD -- games/GameOverlayService.kt` → prefs put/clear + constants
- `git diff HEAD -- ui/settings/SettingsScreen.kt` → onPrivacyClick reroute
- `git diff HEAD -- ui/shell/MainScreen.kt` → showPrivacyPolicy branch + dual visibility
- `read app/src/main/kotlin/com/ivarna/apexcore/ui/legal/PrivacyPolicyScreen.kt` (176 lines), `read app/src/main/assets/privacy_policy.md` (92 lines), `read app/src/main/AndroidManifest.xml` (no INTERNET), `read games/GamesScreen.kt:487-501` (10/5)
- `grep "PRIVACY POLICY" app/src/main/kotlin` → 0; `grep "RAM Free"` → 0 in HomeScreen.kt
NEXT_ACTION: Worker `git add` 2 new files + commit, then manual/runtime validation on device (API 34): Home no RAM Free gap, SetupDialog no pill / Settings still shows Privacy Policy in-app, Games badge padding at 360dp 1.0x/1.3x, Overlay START→swipe→relaunch STOP enabled ≤1s + external kill flips ≤2s + prefs cleared, Privacy offline airplane mode renders §§1-8 scrollable back→Settings.
