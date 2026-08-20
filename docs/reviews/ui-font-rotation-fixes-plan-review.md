# Plan Review — ui-font-rotation-fixes — Pass 1 Iteration 1
**Task:** ui-font-rotation-fixes | **Plan:** docs/plans/ui-font-rotation-fixes-plan.md @ 731f451 | **Date:** 2026-08-20
**Reviewer:** independent plan reviewer | **Verdict:** REVISE

## Verdict Summary
Plan research strong (evidence table, git log anchors). Fixes for badge, icon, manifest direction correct. Two critical gaps block Worker: dialog Haze fallback architecturally invalid (Dialog is separate Window), and LaTeX/Markdown stdlib fallback does not satisfy "use latex and md" acceptance. Plus major gaps in rotation state hardening, typography migration scope, private-repo propagation.

## Findings

### [CRITICAL] Dialog Haze fallback inside Dialog Window cannot blur app behind
Location: `ui/components/ZenDialog.kt` Phase 5 § Haze `haze(haze)` + `zenFrostChild`
Problem: Plan adds `HazeState()` inside `Dialog { Box.fillMaxSize.haze(...) Box.zenFrostChild... }` as fallback when `FLAG_BLUR_BEHIND` ignored. Dialog uses `DialogWindowProvider` separate Window/decorView. Haze source must be ancestor in same window (see `MainScreen.kt:124-135 haze(hazeState)` vs `GlassModifiers.kt:67-78 hazeChild`). Inner Dialog HazeState only samples scrim/dialog content, not MainScreen behind window. Result: fallback veil remains solid color, no blur — same flat ink failure.
Evidence: `ZenDialog.kt:40-46 DialogProperties usePlatformDefaultWidth=false` creates new Window; `GlassModifiers.kt:14-17 Haze 1.0 blur of [haze] source then [tints]`; `MainScreen.kt:122 hazeState` scoped to app Box, not Dialog.
Impact: F6 blur still missing on OEMs that ignore FLAG_BLUR_BEHIND; tester sees no regression fix.
Required planner change: Specify correct blur strategy: keep window `FLAG_BLUR_BEHIND` for S+ **and** either (a) render dialogs as overlay inside MainScreen Box (no Dialog window) so Haze can sample, or (b) document that Haze fallback inside Dialog is ineffective and rely on scrim alpha + `zenGlassBackground` solid fallback only, or (c) use `haze-materials` `HazeDialog` if available. Remove invalid `Box.haze(haze)` snippet; replace with window-attribute width fix + scrim handling only.

### [CRITICAL] PrivacyPolicy LaTeX stdlib fallback inadequate for acceptance
Location: `ui/legal/PrivacyPolicyScreen.kt` Phase 6 Tier 1
Problem: Task AC #2: "use latex and md" + Images 4,5 show formatted doc. Plan Tier 1 renders `$...$` as italic mono `[[math]]` fallback, tables as placeholder Text, no real math layout. Explicitly notes `R6 KaTeX fallback looks non-math`. Does not satisfy "render Markdown & LaTeX / math properly". Reviewer cannot approve stdlib-only as passing.
Evidence: Plan § Phase 6: `detect $…$ → italic mono with [[math]] prefix`, `Tier 2 ponytail upgrade to WebView KaTeX` but chooses Tier 1; `app/build.gradle.kts:77-78 haze 1.0.0` no markdown/KaTeX dep.
Impact: Manual tester rejects F2; store policy formatting remains poor.
Required planner change: Lock decision: either add dependency `com.mikepenz:multiplatform-markdown-renderer` + KaTeX/WebView (with INTERNET=false keep offline) OR define acceptance relaxation explicitly ("italic math fallback acceptable for this pass") and get owner sign-off. Update File Change Inventory + Dependencies + Test Strategy accordingly; include asset size impact and offline guarantee.

### [MAJOR] Autorotate configChanges + rememberSaveable hardening incorrect/incomplete
Location: `app/src/main/AndroidManifest.xml:28`, `MainActivity.kt:51-55`, `ui/shell/MainScreen.kt:55-72`
Problem: (a) `android:configChanges` string `orientation|screenSize|screenLayout|smallestScreenSize|keyboardHidden|density|layoutDirection` includes `keyboardHidden` deprecated, missing `keyboard`, includes `density|layoutDirection` which suppress locale/fontScale recreations — hides correct recomposition. (b) `remember → rememberSaveable` for `currentTab: Tab` enum, `appStage: AppStage` enum, `pagerState: PagerState` non-trivial. `rememberSaveable` needs `Saver` or `ordinal Int` map; plan says "pagerState is already saveable" false. (c) `LaunchedEffect(Unit)` detection + `FreezeFramework.activeBackend` Flow still re-fires on config change if Activity recreated without ViewModel; plan claims guard `detectionDone` suffices but not for process death.
Evidence: `MainActivity.kt:51 remember mutableStateOf(AppStage.SPLASH)` lost on recreate; `MainScreen.kt:56 currentTab Tab.HOME enum`, `GamesScreen.kt:105 rememberPagerState`; no ViewModel in repo.
Impact: Rotate without configChanges still loses tab/backend/pager; with configChanges unsaved states appear fixed but plan's hardening gives false confidence for future removal of flag.
Required planner change: Correct `configChanges="orientation|screenSize|screenLayout|smallestScreenSize"` (drop density/layoutDirection unless explicitly needed, replace keyboardHidden with `keyboard|keyboardHidden`). Specify exact Saver: `currentTab ordinal Int + rememberSaveable`, `appStage ordinal`, or introduce single `MainViewModel: ViewModel` holding `currentTab/backendName/pagerIndex`. Document that `configChanges` prevents recreate so Compose state survives, `rememberSaveable` is defense-in-depth only.

### [MAJOR] Typography standardisation under-scoped and token design adds drift
Location: `ui/theme/Type.kt` Phase 1
Problem: Plan adds `ZenType` object 9 new aliases (micro 8sp … display 20sp) on top of `ZenTypography` 6 M3 tokens (28/24/18/16/14/12sp). Claims grep 100 hardcoded sp scattered, but File Change Inventory lists only ~8 files; misses `ui/home/*`, `ui/overlay`, `ui/onboarding`, `ZenBottomNav/TopBar`. `0.04.em` vs `1.sp` letterSpacing mix, `heightIn` not defined. Increases drift vs canonical M3 consolidation.
Evidence: `Type.kt:28-67` defines 6 tokens; grep 100 matches noted plan §2 line 14; inventory §5 omits many screens.
Impact: Worker will incomplete replace, leaves stray `fontSize = 11.sp` outside Type.kt; review AC #5 "no ad-hoc Xs.sp outside Type.kt" fails.
Required planner change: Choose strict M3-only mapping or justify ZenType aliases with spec table. List exhaustive file set via `grep -r fontSize app/src`. Provide sed/replace mapping table (e.g., 8sp→micro, 9→caption, 11→label). Add lint baseline file path.

### [MAJOR] Private-repo wording propagation incomplete
Location: `docs/privacy-policy.md:5-6,80-82,92`, `app/src/main/assets/privacy_policy.md`, `SetupDialog.kt:49 PRIVACY_POLICY_URL`, fastlane listing
Problem: Plan edits `privacy_policy.md` §6 + Developer line but ignores `SetupDialog.PRIVACY_POLICY_URL="https://github.com/abhay-byte/apexcore/..."` public link, Play Console listing Privacy URL, `docs/testers` screenshots, `README`, `docs/Play_Policy_Gaps_Not_Followed.md` references. Leaves public URL reachable after repo private → 404 / deceptive.
Evidence: `SetupDialog.kt:49` github URL; `docs/privacy-policy.md:5` zenithblue github link; asset duplicate.
Impact: F2 back handler passes but compliance T10 public privacy URL fails; store review reject.
Required planner change: Add to inventory: `SetupDialog.kt PRIVACY_POLICY_URL → "" or in-app route`, `fastlane/metadata/...` listing URLs, `README` links. Add checklist item Play Console update owner. Specify asset sync method (Gradle copy task vs manual cp).

### [MAJOR] Badge pill fix missing edge handling
Location: `games/GamesScreen.kt:487-501` Phase 2
Problem: Pill `RoundedCornerShape(50)`+`padding 12×6`+`heightIn 22dp` may still clip 1.3× fontScale; `TextOverflow.Clip` hides tail vs `Ellipsis`; light radial `customThemeColor` alpha 0.22 on `surfaceContainerLowest 0.94` still low contrast for HIGH red (secondary). Plan lifts alpha 0.15→0.18 but no contrast calculation.
Evidence: `GamesScreen.kt:392-396 Brush.radialGradient customThemeColor` light overlay; `demandColor scheme.secondary` on light.
Impact: Badge legible on dark, illegible on light radial; a11y fontScale still overflow.
Required planner change: Use `TextOverflow.Ellipsis`, `maxLines=1 softWrap=false`, `widthIn max` sibling, consider `autoSize` or `scaleFactor`. Specify contrast test (4.5:1) and fallback `demandColor` vs `scheme.onSurface`.

### [MAJOR] Icon vector pathData unverified / example invalid
Location: `res/drawable/ic_tune.xml` Phase 4
Problem: Plan example `pathData="M3,17v-2h6v-2H3v-2..."` commas + truncated, viewport 24×24 but Material Symbols Rounded tune path is different. No verification via `android/vector` preview. Keeping `fillColor #FF000000` with tint may still artifact if path is filled not stroked.
Evidence: Current `ic_tune.xml:6 viewport 960 pathData M440,840...` vs plan 24 viewport.
Impact: Worker copy-pastes broken path → invisible/corrupted icon persists (F4 not fixed).
Required planner change: Provide verified 24dp vector asset source (link to Material Symbols `tune` Rounded, file export) or use `Icons.Filled.Tune` as lazier fallback with explicit import. Add preview step.

### [MINOR] Blur radius inconsistency
Location: `ui/components/ZenDialog.kt:99 BLUR_BEHIND_RADIUS_PX=48` vs `GlassModifiers.kt:31-33 blurRadius 24dp cardBlurRadius 18dp`
Problem: Perf commit `462ab2d` cut radii to 24/18, but ZenDialog still 48px (≈ 48px ~ 16dp at 3× density). Plan says "keep 18/24dp" but snippet keeps 48.
Impact: GPU cost spike vs perf fix regression.
Required planner change: Align `BLUR_BEHIND_RADIUS_PX` to `20-24dp * density` or `ZenFrost.blurRadius` conversion.

### [MINOR] Dialog width implementation underspecified
Location: `games/AddGamePickerDialog.kt:66-77`, `SetupDialog.kt:113-125`
Problem: Plan changes outer `padding 24×40→16×24` + `widthIn max560 fillMaxWidth 0.92` inside Dialog. Without `window.attributes.width` or `DialogProperties`, width may still constrained by decor insets on tablets.
Evidence: Three dialogs inconsistent padding.
Impact: Width fix appears on phone (332dp vs 312dp) but tablet cap 560dp not enforced.
Required planner change: Make `window.attributes.width = (metrics.widthPixels*0.92).coerceAtMost(560.dp)` SideEffect mandatory, not ponytail. Apply consistently to all three dialogs.

### [MINOR] Privacy asset sync task missing
Location: Phase 0
Problem: Plan notes "Add docs/privacy-policy.md → assets sync Gradle copy task" but File Change Inventory lists no `build.gradle.kts` task.
Impact: Drift between canonical and asset returns (previous review risk).
Required planner change: Add Gradle `tasks.register<Copy>` snippet or pre-build `cp` command in `README`/CI.

### [SUGGESTION] FontScale test matrix incomplete
Location: Test Strategy §8
Problem: Manual matrix lists fontScale 1.0/1.15/1.3 for badge but not for PrivacyPolicy headings/body or Games pager name/pkg.
Evidence: AC #5 standardise across all cards/pages.
Impact: Typography regressions missed.
Required planner change: Add screenshot diff at 1.3× for Home, Settings, Games cards.

## Recommendation
**REVISE** — fix CRITICAL dialog Haze architecture + LaTeX decision before Worker start. Address MAJOR rotation saver + private-repo propagation.
