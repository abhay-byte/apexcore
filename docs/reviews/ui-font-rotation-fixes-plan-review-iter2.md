# Plan Review — ui-font-rotation-fixes — Pass 1 Iteration 2
**Task:** ui-font-rotation-fixes | **Plan:** docs/plans/ui-font-rotation-fixes-plan.md @ 731f451 | **Date:** 2026-08-20
**Reviewer:** independent plan reviewer | **Verdict:** APPROVE

## Verdict Summary
Iteration 2 resolves 2 CRITICAL + 5 MAJOR from iter1. Dialog Haze fallback removed (correct: Dialog separate Window cannot sample MainScreen). LaTeX decision locked Tier1 stdlib + ponytail + owner sign-off gate. configChanges corrected, Saver via ordinal/pagerIndex specified. Font inventory + mapping added, badge Ellipsis/widthIn, icon verification step, blur radius aligned, width mandatory via window.attributes, asset sync task added. Remaining gaps minor, non-blocking. Worker can build.

## Findings — Resolved from Iter1
- [CRITICAL] Dialog Haze inside Dialog Window — fixed: plan §5 Phase5 removes `haze()` inside Dialog, relies on `FLAG_BLUR_BEHIND`+scrim+`zenGlassBackground`, notes Haze ineffective separate Window. Verified against `ZenDialog.kt:40-46 DialogWindowProvider` + `GlassModifiers.kt:67-78 hazeChild`.
- [CRITICAL] Privacy LaTeX stdlib fallback — fixed: locked Tier1 `AnnotatedString` italic mono pill for `$`/`$$` + tables placeholder, explicit ponytail upgrade to WebView KaTeX `marked.min.js` offline, sign-off gate in AC #2 §6 and §11 handoff.
- [MAJOR] Autorotate configChanges + rememberSaveable — fixed: `orientation|screenSize|screenLayout|smallestScreenSize|keyboard|keyboardHidden` (drops `density|layoutDirection`), ordinals via `mutableIntStateOf(...ordinal)` + `pagerIndex`, `detectionDone` guard.
- [MAJOR] Typography under-scoped — fixed: adds `ZenType` 9 aliases, grep inventory, mapping table, file list, lint-baseline whitelist. Partial residual (see MINOR below).
- [MAJOR] Private-repo propagation — fixed: covers `app/src/main/assets/privacy_policy.md` + `docs/privacy-policy.md` + `SetupDialog.kt:49 PRIVACY_POLICY_URL` + `README` + `fastlane/**` + `docs/testers/**` + `docs/Play_Policy_Gaps_Not_Followed.md` + Play Console checklist + Gradle Copy task.
- [MAJOR] Badge pill edge handling — fixed: `RoundedCornerShape(50)` pill, `12×6` pad, `heightIn 22dp`, `widthIn min48 max160`, `Ellipsis` maxLines1, alpha 0.15→0.18 + contrast fallback note.
- [MAJOR] Icon vector unverified — fixed: flags placeholder `M3,17v-2h...` schematic, requires real Rounded export via Vector Asset → Material Symbols `tune` 24dp, fallback `Icons.Filled.Tune` documented.
- [MINOR] Blur radius — fixed: `BLUR_BEHIND_RADIUS_PX 72` (=24dp×3) aligns to `ZenFrost.blurRadius 24dp` (was 48).
- [MINOR] Dialog width — fixed: `window.attributes.width = (metrics.width*0.92).coerceAtMost(560dp)` SideEffect mandatory + Compose `widthIn max560 fillMaxWidth 0.92`, padding `16×24`, `fillMaxHeight 0.86`.
- [MINOR] Privacy asset sync — fixed: `tasks.register<Copy>("syncPrivacyPolicy")` + `preBuild dependsOn` + cp fallback.

## Findings — Remaining (Iter2)

### [MINOR] Typography mapping missing sizes
Location: `ui/theme/Type.kt` Phase1 mapping table + `app/build.gradle.kts` grep
Problem: table maps 8/9/10/11/13/14/15/17/20sp but grep 168 hits includes 12sp (30+ hits: Settings, MemoryLeaf, Home), 16sp, 18sp, 24sp, 28sp, 32/36sp, RamFreeScreen/GameOverlayService. Worker leaving orphan `fontSize=12.sp` outside Type.kt fails AC #5 zero stray.
Evidence: `grep fontSize` shows `RamFreeScreen.kt:35`, `GameOverlayService.kt:5`, `SettingsScreen.kt:16` with 12sp etc not in table.
Impact: incomplete standardisation, lint baseline false pass.
Required planner change: extend mapping: `12.sp→ZenType.labelSm or bodyXs`, `16.sp→ZenTypography.bodyMedium`, `18.sp→bodyLarge/headlineSmall`, `24.sp→headlineMedium`, `28/32/36→headlineLarge/display`. Or keep aliases but document passthrough.

### [MINOR] File inventory omits two modules
Location: `Phase1` + `§5 File Change Inventory`
Problem: inventory lists home/tune/overlay/onboarding etc but omits `ram/RamFreeScreen.kt` (35 hits) and `games/GameOverlayService.kt` (5 hits) which also use hardcoded sp. AC #5 expects all cards/pages.
Evidence: grep output above.
Impact: Worker skips RAM Free screen fonts.
Required planner change: add both files to inventory and mapping table, or explicitly exclude with reason (overlay HUD uses fixed 9sp for perf not user-font-scale).

### [MINOR] Blur radius hard-coded 72px not density-aware
Location: `ui/components/ZenDialog.kt` §5 `BLUR_BEHIND_RADIUS_PX=72`
Problem: 72 =24dp×3 assumes 3× density. On mdpi/hdpi/xxxhdpi blur too small/large. Perf commit 462ab2d used dp tokens. Plan aligns to ZenFrost.blurRadius but constant not computed.
Evidence: `GlassModifiers.kt:31 blurRadius 24.dp`, plan hardcodes 72.
Impact: visual inconsistency across densities, GPU cost variance.
Required planner change: compute dynamically: `val radiusPx = with(density){ ZenFrost.blurRadius.roundToPx() }` or keep 72 comment but add density conversion in SideEffect.

### [MINOR] Dialog width snippet API incorrect
Location: `ZenDialog.kt` Phase5 SideEffect
Problem: `windowManager.currentWindowMetrics` not available in Compose scope; `window.attributes.apply{ width=...; window.addFlags...; blurBehindRadius=...}` mixes window flag mutation inside attributes apply. Needscompat for API<30 (`displayMetrics.widthPixels`) and correct ordering: `window.addFlags` outside `attributes` copy.
Evidence: plan code 220-225.
Impact: Worker copy-paste fails compile or width not set on tablets API24.
Required planner change: use `val wm = context.getSystemService(WindowManager::class.java); val widthPx = if(Build.VERSION.SDK_INT>=30) wm.currentWindowMetrics.bounds.width() else @Suppress("DEPRECATION") wm.defaultDisplay.width` then `window.attributes = window.attributes.apply{ width=...; blurBehindRadius=...}; window.addFlags(...)`.

### [MINOR] Gradle sync task hook fragile on AGP 8
Location: `app/build.gradle.kts` Phase0 `tasks.named("preBuild"){dependsOn("syncPrivacyPolicy")}`
Problem: AGP 8+ `preBuild` ordering not guarantee before `mergeAssets`; asset may not sync in incremental builds. Duplicate asset comment still present in `PrivacyPolicyScreen.kt:31` ponytail referencing github raw.
Evidence: existing `PrivacyPolicyScreen.kt:31` comment still points to public github.
Impact: drift between `docs/privacy-policy.md` and asset returns.
Required planner change: hook `tasks.named("preBuild")` or `tasks.named("mergeDebugAssets")`/`mergeReleaseAssets`, or use `android.sourceSets.assets.srcDirs` copy spec; remove github raw ponytail.

### [SUGGESTION] Pager index coerce + detection guard on process death
Location: `MainScreen.kt` Phase3 `pagerState` init
Problem: `pagerIndex` restore may exceed `currentList.size` after filter/search, and `FreezeFramework.activeBackend` Flow singleton lost after process death though `detectionDone` rememberSaveable survives. Plan notes correctly but not handling coerce.
Evidence: `GamesScreen.kt:92-108` list filtered.
Impact: minor flicker / out-of-bounds initial page on restore.
Required change: `pagerState = rememberPagerState(initialPage = pagerIndex.coerceIn(0, (currentList.size-1).coerceAtLeast(0)), ...)` and note `FreezeFramework.detect()` re-run needed after kill.

### [SUGGESTION] Inline MD extraction order
Location: `PrivacyPolicyScreen.kt` Phase6 regexes
Problem: italicRegex will match `**bold**` interior if run before bold extraction. Code comment says extract displayMath first but not code→bold→italic→link order. Tables placeholder rendering truncated.
Evidence: plan snippet.
Impact: low — visual artifact only.
Required change: document order: displayMath → code fence → inline code → bold → italic → link → tables; add test case for `**bold**` not double-italic.

## Recommendation
APPROVE — no blocking gaps. Worker implement with minor fixes above. Owner sign-off required for Tier1 LaTeX fallback (§2 acceptance).

