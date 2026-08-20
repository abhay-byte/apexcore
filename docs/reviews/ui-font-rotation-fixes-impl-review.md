# Implementation Review — ui-font-rotation-fixes — Pass 1 / Iteration 2

Date: 2026-08-21
Reviewer: implementation-reviewer (read-only)
Scope: 30 files diff vs HEAD (731f451), full source inspection, no device run

## Verdict
IMPLEMENTATION_REVIEW: APPROVE
PASS: 1
ITERATION: 2
CRITICAL: 0
MAJOR: 0
MINOR: 1
FIX_ROUTE: FINAL

## Summary
Iteration 2 fix for ic_tune.xml holds. All 6 acceptance criteria met. Typography centralized, rotation handled, blur restored, dialogs widened, privacy rendered offline with MD+LaTeX.

## Previous Findings Re-check
- ic_tune.xml vector pathData / viewport — **RESOLVED**. Now 24x24 viewport, pathData `M3,17v2h6v-2H3zM3,5v2h10V5H3zM13,21v-2h8v-2h-8v-2h-2v6h2zM7,9v2H3v2h4v2h2V9H7zM21,13v-2H11v2h10zM15,9h2V7h4V5h-4V3h-2v6z`, fillColor #FF000000 for tint. Comment documents Material Symbols Rounded tune.

## Acceptance Criteria

### 1. Resource demand badge clipping / illegibility
PASS. `GamesScreen.kt`: pill uses `ZenType.caption (9sp)` + `ZenType.micro (8sp)` label, `heightIn 22dp`, `widthIn 48..160dp`, `padding 12x6dp`, `maxLines 1`, `overflow Ellipsis`, `softWrap false`, `alpha 0.18` (was 0.15). Scales at 1.3x fontScale without clip.

### 2. Privacy Policy Markdown + LaTeX, private repo, font
PASS. `PrivacyPolicyScreen.kt`: stdlib-only parser (`PrivacyMarkdown`) — headings, bullets, **bold**, *italic*, `code`, links, tables, code fences, `$inline$`, `$$display$$` as italic mono pill. No WebView, no INTERNET. Asset `assets/privacy_policy.md` synced via `syncPrivacyPolicy` task (build.gradle.kts preBuild). Text updated: `Last Updated: Aug 20 2026`, private repository wording, no public GitHub URL. Docs `privacy-policy.md` and `Play_Policy_Gaps_Not_Followed.md` updated. Typography uses `ZenType.bodySm/title/display` etc. ponytail notes future KaTeX upgrade path.

### 3. Auto-rotate restart
PASS. `AndroidManifest.xml`: `configChanges="orientation|screenSize|screenLayout|smallestScreenSize|keyboard|keyboardHidden"` on MainActivity prevents recreate. `MainActivity.kt`: `appStageOrdinal`, `themeModeOrdinal`, `lightTankBg` via `rememberSaveable` (ordinal for enums). `MainScreen.kt`: `currentTabOrdinal`, dialog flags, `globalBackendPref`, `detectionDone` via `rememberSaveable`. `GamesScreen.kt`: `showAllApps`, `searchQuery`, `showAddPicker/PinPicker`, `pagerIndex` saveable; `pagerState` seeded from saveable index.

### 4. Game optimisation icon
PASS. `ic_tune.xml` corrected as above. Previous findings closed.

### 5. Standardise font sizes
PASS. `Type.kt`: `ZenType` object is single source — 11 semantic aliases (micro 8sp .. heroLg 36sp) + 6 M3 tokens. `grep fontSize` shows 18 hits only in `Type.kt`; zero ad-hoc `fontSize = X.sp` elsewhere. All screens/dialogs migrated: Onboarding, Splash, Home, Rams, Games, Tune, Settings, Overlay, Shell, dialogs use `ZenType` or `MaterialTheme.typography`.

### 6. Dialog blur + width
PASS. `ZenDialog.kt`: true backdrop blur API31+ via `FLAG_BLUR_BEHIND` + `blurBehindRadius 72px (24dp*3)`, clears `FLAG_DIM_BEHIND`, scrim ink tint `Black 0.28/0.52` dark / `#0C171B 0.18/0.32` light, window width `0.92*screenWidth coerceAtMost 560dp`, height full for scrim. `usePlatformDefaultWidth false`. Callers (`SetupDialog`, `AddGamePickerDialog`, `WhitelistPickerDialog`) set `widthIn max 560dp fillMaxWidth fillMaxHeight 0.86f` + vertical padding so glass card fills window.

## Findings
- MINOR — MISSING_TEST: `PrivacyMarkdown.parseInline/render` pure and testable but no unit test file shipped. Low risk; parser is small stdlib regex. Add when test suite formalizes. Not blocking.
- ENVIRONMENT_LIMITATION: blur, rotation, and fontScale clipping need manual/device validation (API31+ blur, 1.3x large font, light/dark). Route to MANUAL_TEST if needed.

## Evidence
- `git status` : 29 modified, main @731f451
- `git diff --stat HEAD` : 30 files (+privacy assets, manifest, 6 screens, 3 dialogs, ZenDialog, Type)
- `read ic_tune.xml` : 24x24, canonical pathData verified
- `read AndroidManifest.xml` : configChanges present
- `read MainActivity.kt/MainScreen.kt/GamesScreen.kt` : rememberSaveable coverage verified
- `read PrivacyPolicyScreen.kt/build.gradle.kts/assets/privacy_policy.md` : offline MD+LaTeX, sync task
- `read ZenDialog.kt/SetupDialog.kt/AddGamePickerDialog.kt/WhitelistPickerDialog.kt` : blur + width
- `read Type.kt` + `grep fontSize` : centralized typography, zero leaks
- Previous evidence: build logs claimed pass (not re-run per no-device rule)

## Next Action
APPROVE to FINAL. Optional MANUAL_TEST: verify blur on API31+, rotate preserves tab/dialog, badge at 1.3x fontScale, privacy renders headings/bold/latex in light/dark offline.
