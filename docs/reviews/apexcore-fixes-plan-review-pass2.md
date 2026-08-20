# Plan Review — apexcore-fixes — PASS 2 ITER 2

PLAN_REVIEW: APPROVE
PASS: 2
ITERATION: 2
CRITICAL: 0
MAJOR: 0
MINOR: 2
SUGGESTIONS: 1

## Verdict
APPROVE — Pass 2 plan addresses all 4 MAJOR + 3 MINOR from Pass 1 with deterministic lines, correct hunk boundaries, offline-first privacy, and dual-source overlay persistence. Worker can implement without rediscovering architecture. Remaining items are non-blocking polish.

## Previous Findings — Closure Check

### [MAJOR-1] Image-2 ambiguous — FIXED
Plan adds mandatory `grep -rn "PRIVACY POLICY|PRIVACY_POLICY_URL|openPrivacyPolicy"` + photo gate, locks primary target `SetupDialog.kt:224-241` pill + `Spacer` at 243, guards `SettingsScreen.kt:227-255` LEGAL card with BLOCK+clarify default-keep per Scenario 5. Keeps `PRIVACY_POLICY_URL` const and `openPrivacyPolicy()` deprecated. Deterministic.

### [MAJOR-4] Home spacer off-by-one — FIXED
Plan deletes `HomeAnimatedEntryRow RAM Free 283-289` + following `Spacer 291` only, keeps `Spacer 280` inside `if(isElevatedBackend)`. Second commit deprecates `onRamFreeClick` param + `ZenIcons.WaterDrop` import and `MainScreen:199` passthrough. `RamFreeScreen.kt` kept orphaned ponytail. Layout gap correct both tune visible/hidden.

### [MAJOR-2] Overlay persistence drift + getRunningServices — FIXED
Plan adds `PREF_OVERLAY_RUNNING=overlay_running`, `PREF_OVERLAY_PKG=overlay_pkg` in single file `apexcore`, `isOverlayServiceRunningFallback()` try/catch, `deriveRunning()` + 1s `LaunchedEffect` poll checking `isRunning || (prefsFlag && canDrawOverlays && fallback)`, clears drift `remove(...).apply()` + `testOverlayActive=false` when service dead but prefs true, writes `apply()` async on all 3 exit paths `onStartCommand:122-126`, `onDestroy:159-185`, `shutdown:403-418`, `companion stop:612-614`. No new permission.

### [MAJOR-3] Privacy render underspecified — FIXED
Locked offline-first: `cp docs/privacy-policy.md` (92 lines, §§1-8) to `app/src/main/assets/privacy_policy.md`, native `LazyColumn` line-pass `## `→17sp bold heading, `### `→subheading, `* `/`- `→bullet 13sp/18sp, `# `→20sp title, blank→Spacer, else body, `**` stripped. Explicitly DO NOT add `INTERNET` (keeps `docs/privacy-policy.md:14` truthful), ponytail upgrade path to WebView+raw noted, airplane-mode acceptance.

### [MINOR-5] MainScreen chrome — FIXED
Adds `showPrivacyPolicy` alongside `showRamFree/showTuneScreen`, branch priority `RamFree > Tune > PrivacyPolicy > AnimatedContent`, updates both `AnimatedVisibility` at 221-226 and 261-266 to `!showRamFree && !showTuneScreen && !showPrivacyPolicy`, passes `PrivacyPolicyScreen(Modifier.weight(1f), onBack={showPrivacyPolicy=false})`.

### [MINOR-6] Settings import hygiene — FIXED
Keeps `SettingsScreen:49 import openPrivacyPolicy` and `SetupDialog:438 openPrivacyPolicy + PRIVACY_POLICY_URL` with `@Deprecated("Use in-app PrivacyPolicyScreen; kept for SetupDialog fallback")`, reroutes `GlassCard onClick` at 227-228 to `onPrivacyClick` param default `{}`, wires `MainScreen:205-214 onPrivacyClick={showPrivacyPolicy=true}`.

### [MINOR-7] Prefs namespacing/threading — FIXED
Keys `overlay_running`/`overlay_pkg` in `"apexcore"` (shared with `preferred_backend`, `setup_shown_v1`), all writes `apply()` async, read via `remember { getSharedPreferences("apexcore", MODE_PRIVATE) }`.

### Suggestions — Adopted
Offline minimal (no WebView/markdown dep), badge `8/3 → 10/5` with `RoundedCornerShape(ZenDimens.roundedSm)` +1dp border verified at 360dp/1.0x/1.3x.

## New Findings (Pass 2 regression scan)

### [MINOR] Privacy parser snippet missing LocalContext + LazyColumn items pseudocode
Location: `docs/plans/apexcore-fixes-plan.md:149-176` / `ui/legal/PrivacyPolicyScreen.kt (new)`
Problem: Snippet uses `context.assets.open(...)` and `remember { context... }` without `val context = LocalContext.current`; `LazyColumn { items(lines.size) { idx -> when { trimmed.startsWith("## ") -> item Heading } } }` mixes `items`/`item` pseudocode — Worker must translate to `itemsIndexed(lines) { _, raw -> ... }` with `Text`/`Spacer` per line, not nested `item` inside `items`.
Evidence: Plan line 153 `val text = remember { context.assets.open(...) }` no LocalContext. Implementation order correct.
Impact: Compile error if copied verbatim; trivial fix.
Required planner change: None blocking — Worker adds `val context = LocalContext.current` before `remember`, uses `LazyColumn { itemsIndexed(lines) { ... } }` or `items(lines.size)` with `when` emitting `Text` directly. Planner may patch snippet in next rev but not required for APPROVE.

### [MINOR] Overlay fallback as hard gate on relaunch may clear prefs if getRunningServices throttled
Location: `docs/plans/apexcore-fixes-plan.md:108-122` / `GameOverlayService.kt:592-614`
Problem: Poll `running = isRunning || (flag && hasPermission && fallback)` requires `fallback==true` to keep `running==true` after process death. If `getRunningServices` throttled on API 34 returns empty, `running` false → drift-clear branch `!running && flag` clears `overlay_running` even though service may still be running. Own-package fallback is documented as still returned on 34, so low risk, but logic couples truth to deprecated API.
Evidence: Mitigation note says primary is `isRunning+prefs+canDrawOverlays`, fallback only — but code gates on fallback. Service already holds `QUERY_ALL_PACKAGES`.
Impact: Rare false-negative relaunch (START shown while overlay running). Recovers on next `isRunning` poll if service pings.
Required planner change: Optional tightening — keep `flag && hasPermission` as truth without fallback, use fallback only for dead-confirmation: `if (!isRunning && flag && hasPermission && !fallback) clear` vs `if (!isRunning && flag && hasPermission && fallback==false)` — not blocking for APPROVE; document own-pkg reliability.

### [SUGGESTION] Privacy asset error path not specified
Location: `ui/legal/PrivacyPolicyScreen.kt` asset load
Suggestion: Wrap `context.assets.open("privacy_policy.md")` in `try { } catch { Text("Privacy policy unavailable") }` or fallback to `docs/privacy-policy.md` raw string, so missing asset (clean build) not crash.

## Acceptance Check
- Scenario 1 RAM Free: delete 283-289+291 only, keep 280, gap correct — PASS
- Scenario 2 Privacy pill: SetupDialog 224-241+243 gone, Settings LEGAL stays via in-app route, grep 0 outside Settings — PASS
- Scenario 3 Badge: 10/5 padding — PASS
- Scenario 4a test text: `TEST HUD OVERLAY`→`HUD OVERLAY`, dummy→preview — PASS
- Scenario 4b persistence: start→close→relaunch RUNNING ≤1s, external kill flips + prefs cleared ≤2s, STOP kills FGS via 3 clear paths — PASS
- Scenario 5 Privacy page: LazyColumn asset §§1-8 13sp/18sp body 17sp headings bullets scrollable back→Settings offline no INTERNET — PASS

## Next Action
Worker: execute in order §Implementation Order 1→6, photo-gate Item 2, device-test overlay drift + airplane-mode privacy.

NEXT_AGENT: Worker
NEXT_ACTION: Implement per Pass 2 plan file matrix (11 hunks), `./gradlew :app:assembleDebug :app:testDebugUnitTest`, device verify Home/Games/Overlay/Privacy acceptance above.
