# Plan Review — apexcore-fixes — PASS 1 ITER 1

PLAN_REVIEW: REVISE
PASS: 1
ITERATION: 1
CRITICAL: 0
MAJOR: 4
MINOR: 3
SUGGESTIONS: 2

## Verdict
REVISE — plan thorough, line-cited, minimal diff. Worker can execute 1/3/4a/5 offline path without rediscovery. Gaps: Image-2 target ambiguous blocks item 2, overlay poll+prefs drift, privacy markdown render underspecified, Home spacer hunk off-by-one.

## Findings

### [MAJOR] Image-2 target ambiguous — no deterministic file/line
Location: `docs/plans/apexcore-fixes-plan.md:42-55` / `SetupDialog.kt:224-241` vs `SettingsScreen.kt:227-255`
Problem: Screenshots not bundled, AC says remove Privacy Policy from Image-2 screen but keep Settings page (item 5). Plan guesses SetupDialog pill most probable, defers to device reproduction. Worker cannot implement without rediscovering target.
Evidence: Plan `Evidence gap (must resolve before code)` admits ambiguity. Candidates: SetupDialog priv pill `Box(clickable{openPrivacyPolicy}).Text("PRIVACY POLICY")`, Settings LEGAL card `GlassCard(onClick={openPrivacyPolicy})`, historical Home footer gone. No grep in repo for Image-2 mapping.
Impact: Wrong deletion breaks compliance (KDE-14) or leaves duplicate. Blocks item 2.
Required planner change: Add pre-edit step: `grep -rn "PRIVACY POLICY\|Privacy Policy\|PRIVACY_POLICY_URL"` capture screenshots of all hits (SetupDialog, Settings, Home footer history). Declare primary target = SetupDialog.kt:224-241, fallback: if Image-2 shows Settings then BLOCK item2 and clarify with reporter per plan guard. Add checklist photo before delete.

### [MAJOR] Overlay persistence poll still uses deprecated getRunningServices, prefs not cleared on external kill
Location: `GameOverlayService.kt:592-614,122-186,159-185` / `ui/overlay/OverlayScreen.kt:33-42,164-217` / plan 4b:86-94
Problem: Plan hydrates `testOverlayActive` via `LaunchedEffect` poll 1s checking `getRunningServices().any{...}` || `isRunning` || `prefs`. `getRunningServices(Int.MAX_VALUE)` deprecated API26, on Android 14+ returns only own services but may be throttled; single source unreliable. Also prefs `overlay_running=true` set on start, cleared only on `onDestroy`/`stop`, not when FGS killed externally (notification swipe, system kill) — poll will see service dead but prefs still true, UI stuck STOP enabled.
Evidence: Service `isRunning @Volatile` memory-only, `START_REDELIVER_INTENT`, no SharedPreferences now. OverlayScreen `testOverlayActive remember{mutableStateOf(false)}` ephemeral. Plan adds dual-source but describes poll as `isOverlayServiceRunning()` or `isRunning || prefs && canDrawOverlays` without clear-branch logic.
Impact: App close→relaunch shows wrong state, or external kill leaves UI stuck, contradict AC 4b (within 1-2s flip).
Required planner change: Specify exact state derivation: `val running = GameOverlayService.isRunning || (prefs.getBoolean("overlay_running",false) && canDrawOverlays && isServiceRunningFallback)` where `isServiceRunningFallback` is `try{am.getRunningServices...}catch{false}` only as last resort. Poll logic: if `!running && prefs.getBoolean(...)` → `prefs.edit().remove("overlay_running").remove("overlay_running_pkg").apply()` and `testOverlayActive=false`. On start set prefs, on stop/onDestroy/shutdown clear prefs (cover `shutdown()` at :403, `stop()` companion, `onDestroy`). Use `apply()` on Main thread. Mention QUERY_ALL_PACKAGES already granted, no extra permission. Prefer isRunning+prefs, document getRunningServices as deprecated fallback.

### [MAJOR] Privacy page render underspecified — no markdown parser, asset sync, INTERNET contradiction
Location: `ui/legal/PrivacyPolicyScreen.kt (new)` / `app/src/main/assets/privacy_policy.md (new)` / `docs/privacy-policy.md:1-92` / `AndroidManifest.xml:5-12` / plan item5:104-114
Problem: Plan says `LazyColumn with Text blocks parsed from bundled markdown (headings 17.sp bold, body 13.sp)` but no parsing rule, no handling of `##` sections 1-8, bold bullets. Also asset duplicated from `docs/privacy-policy.md` with no sync; offline vs WebView choice leaves `INTERNET` permission ambiguous. Policy doc line 14 states `does not request INTERNET` — adding it contradicts shipped policy and needs Play Data Safety re-declare.
Evidence: Current `SettingsScreen.kt:228 openPrivacyPolicy` opens browser via `PRIVACY_POLICY_URL="https://github.com/abhay-byte/apexcore/blob/main/docs/privacy-policy.md"` (blob HTML not raw). No `ui/legal` package, no WebView/markdown dep in `app/build.gradle.kts`. Manifest no INTERNET.
Impact: Worker must invent parser or add dep, risk WebView chose blob vs raw inconsistency, or adds INTERNET without docs update.
Required planner change: Lock offline-first (recommended) as default: copy `docs/privacy-policy.md` to `app/src/main/assets/privacy_policy.md` at build, load via `context.assets.open(...).readText()`, render with simple line pass: `if startsWith("## ") -> heading 17sp bold else if startsWith("* ") -> bullet 13sp else -> body 13sp lineHeight 18sp` inside `LazyColumn`. Add `ponytail: no markdown lib; upgrade to WebView+raw when live updates needed` comment. Explicitly state DO NOT add INTERNET for v1; if live fetch chosen, add `<uses-permission INTERNET>` and update `docs/privacy-policy.md:14` + Data Safety. Add sync note: `cp docs/privacy-policy.md app/src/main/assets/privacy_policy.md` pre-build or Gradle copy task.

### [MAJOR] HomeScreen delete hunk spacers off by one — layout gap risk
Location: `ui/home/HomeScreen.kt:283-292` / plan 1:30-32
Problem: Plan says delete `HomeAnimatedEntryRow("RAM Free"...) block + its preceding Spacer(12.dp) (lines 283-292)`. File shows: conditional tune block ends with `Spacer(12.dp)` inside `if`, then `HomeAnimatedEntryRow(RAM Free)`, then `Spacer(12.dp)` after, then `HomeAnimatedEntryRow(Pin Apps)`. Preceding spacer belongs to tune section, not RAM Free. Deleting it collapses tune→Pin gap when tune visible.
Evidence: `HomeScreen.kt:280 Spacer(12.dp)` inside `if(isElevatedBackend)`, `283-289 RAM Free row`, `291 Spacer(12.dp)`, `293 Pin Apps row`.
Impact: Off-by-one leaves double gap (if delete after) or missing gap (if delete before). Visual regression.
Required planner change: Correct hunk: delete `HomeAnimatedEntryRow(title="RAM Free"...)` at 283-289 + following `Spacer(12.dp)` at 291 only. Keep `onRamFreeClick` param deprecated one commit, then remove param/import `ZenIcons.WaterDrop` if unused, and `MainScreen.kt:199 onRamFreeClick={showRamFree=true}` passthrough. Note `RamFreeScreen` stays orphaned hidden; decide keep vs delete file.

### [MINOR] MainScreen chrome visibility not fully wired for new privacy route
Location: `ui/shell/MainScreen.kt:61-62,134-138,220-272` / plan 5:109
Problem: Plan adds `showPrivacyPolicy` state and says `hide top/bottom chrome when showPrivacyPolicy true (same AnimatedVisibility condition as ram/tune)` but file change matrix lists `60-70,134-216,220-272` without explicit code: `visible = !showRamFree && !showTuneScreen && !showPrivacyPolicy`. Worker may miss second `AnimatedVisibility` (bottom nav) and content branch priority.
Evidence: `MainScreen.kt:221-226 AnimatedVisibility(visible = !showRamFree && !showTuneScreen` for top bar, `261-266` same for bottom. Content branch `if(showRamFree) else if(showTuneScreen) else AnimatedContent`.
Impact: Privacy page shows with top/bottom chrome overlapping.
Required planner change: Specify: `var showPrivacyPolicy by remember{mutableStateOf(false)}` alongside others, insert branch `if(showRamFree) ... else if(showTuneScreen) ... else if(showPrivacyPolicy) PrivacyPolicyScreen(modifier=weight(1f), onBack={showPrivacyPolicy=false}) else AnimatedContent(...)`. Update both `AnimatedVisibility` visibles to `!showRamFree && !showTuneScreen && !showPrivacyPolicy`. Wire `SettingsScreen` param `onPrivacyClick:()->Unit = {}` and change LEGAL card `onClick` from `openPrivacyPolicy` to `onPrivacyClick`.

### [MINOR] SettingsScreen LEGAL card reroute leaves dead import
Location: `ui/settings/SettingsScreen.kt:49,227-255` / plan 5:108-109
Problem: After rerouting LEGAL card to `onPrivacyClick`, `import com.ivarna.apexcore.openPrivacyPolicy` becomes unused unless SetupDialog fallback kept. Plan says `Keep openPrivacyPolicy/PRIVACY_POLICY_URL as fallback (or deprecate) — do not delete until new screen ships` but doesn't say to retain import or add deprecation annotation.
Impact: Lint unused import, or accidental deletion breaks SetupDialog still using it.
Required planner change: Keep import + `openPrivacyPolicy` function for SetupDialog fallback; add `@Deprecated("Use in-app PrivacyPolicyScreen")` or comment. No deletion in same PR.

### [MINOR] Overlay prefs write threading + key collision
Location: `GameOverlayService.kt:122-125,159,403-418` / plan 4b:83-92
Problem: Plan uses `getSharedPreferences("apexcore", MODE_PRIVATE)` same file as `preferred_backend`, `setup_shown`. Keys `overlay_running` / `overlay_running_pkg` risk collision not namespaced. Also doesn't state `apply()` vs `commit()` on Service main thread.
Evidence: `MainScreen.kt:65` uses `apexcore` prefs for backend. `SetupDialogHelper.PREFS="apexcore"`.
Impact: Minor but needs hygiene.
Required planner change: Use keys `overlay_running` + `overlay_pkg` namespaced, `apply()` async, read via `remember { context.getSharedPreferences("apexcore", MODE_PRIVATE) }` in OverlayScreen poll to avoid recreation.

### [SUGGESTION] Keep permission footprint minimal — avoid WebView
Location: plan 5 alt WebView `AndroidView(WebView)` / `BackHandler`
Problem: WebView needs INTERNET + `WebViewClient` handling, increases Data Safety surface, contradicts current policy `no INTERNET`.
Suggestion: Ship offline asset v1, note upgrade path comment. No new dep, offline works airplane mode per AC.

### [SUGGESTION] Tag padding exact value — use design token
Location: `games/GamesScreen.kt:487-501` / `ui/tune/TuneCategorySection.kt:88-97`
Problem: Plan proposes `10dp/5dp` or `12dp/6dp` but not tied to `ZenDimens`.
Suggestion: Use `horizontal=10.dp vertical=5.dp` matches `ModeChip 10/4` closest; keep `RoundedCornerShape(ZenDimens.roundedSm)` and `1.dp` border. Verify at 360dp width no overflow.

## Acceptance Check
- 1 Remove Free RAM: Home row gone, build green — plan covers, fix spacer hunk.
- 2 Remove Privacy Policy stray: needs Image-2 confirmation before edit — plan blocks correctly but needs explicit photo step.
- 3 Tag padding: isolated to GamesScreen demand badge — correct, Tune count not bug.
- 4a test text: header `TEST HUD OVERLAY` + desc `dummy...test` — plan maps correctly.
- 4b persistence: needs prefs+poll with external-kill clear — plan needs tightening.
- 5 Privacy page: in-app fully rendered scrollable, back to Settings, offline — plan offline asset satisfies, WebView alt documented.

## Next Action
Planner: patch plan per MAJOR/MINOR above, re-issue rev 2, then Worker.
