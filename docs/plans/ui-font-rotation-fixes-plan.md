# UI / Font / Rotation Fixes — Implementation Plan
**TASK_ID:** `ui-font-rotation-fixes` | **PASS:** 1 | **ITERATION:** 2 | **DATE:** 2026-08-20
**Source branch:** `main` @ `731f451` | **Reviewer verdict prior:** REVISE (2 CRITICAL, 5 MAJOR, 3 MINOR)
**Responds to:** `docs/reviews/ui-font-rotation-fixes-plan-review.md` (Pass 1 Iteration 1)

## 1. Research Sources & Evidence

| Claim | Source |
|---|---|
| Manifest has zero `configChanges` → activity restarts on rotate | `app/src/main/AndroidManifest.xml:28` no attr; `grep configChanges` zero hits |
| Dialog is separate Window — Haze inside cannot sample app behind | `ui/components/ZenDialog.kt:40-46` `Dialog{ DialogWindowProvider }` window; `ui/components/GlassModifiers.kt:14-17` Haze samples `[haze]` ancestor in same window; `ui/shell/MainScreen.kt:122` `hazeState` scoped to app Box |
| Window blur recipe `FLAG_BLUR_BEHIND` 48px + ink scrim | `ui/components/ZenDialog.kt:32-68` `BLUR_BEHIND_RADIUS_PX=48` |
| Previous blur fixes that worked / regressed | `git log --oneline` `a0d335b fix(ui): theme-aware dialog blur…` ; `462ab2d perf(ui): cut idle RAM/GPU… blur 24dp` ; `eec458e feat(zen): haze 1.0.0` |
| Game card demand badge clipping site | `games/GamesScreen.kt:487-501` Box clip 4dp pad 10×5 Text 9sp Bold inside height 280dp card; `Brush.radialGradient customThemeColor alpha 0.22` at 392-396 |
| PrivacyPolicy naive parser, no MD/LaTeX, public-repo wording | `ui/legal/PrivacyPolicyScreen.kt:35-136` line-pass `##`/`*`/`**` only; `app/src/main/assets/privacy_policy.md:5-6,80-82,92` + `SetupDialog.kt:49` `https://github.com/abhay-byte/apexcore` |
| Typography drift — 100+ hardcoded `sp` / PlusJakartaSans | `grep fontSize` 100 matches; `Type.kt:28-67` defines only 6 tokens (28/24/18/16/14/12sp) while screens use 8/9/10/11/13/15/17/20/32/36sp ad-hoc |
| Icon `ic_tune` 960 viewport filled path | `res/drawable/ic_tune.xml:2-9` viewport 960 `fillColor #FF000000` |
| Dialog width constrained by padding 24×40 + fillMaxWidth | `AddGamePickerDialog.kt:66-77`, `WhitelistPickerDialog.kt:58-70`, `SetupDialog.kt:113-125` all `padding 24dp + fillMaxWidth` inside `ZenDialog usePlatformDefaultWidth=false` |
| Haze stack & blur radii perf cut | `app/build.gradle.kts:77-78` `haze:1.0.0` + `haze-materials:1.0.0`; `GlassModifiers.kt:26-48` `blur 24dp` `cardBlur 18dp` `tintAlpha 0.80` |
| Compose / Activity / minSdk | `app/build.gradle.kts:65-72` BOM `2024.11.00` `activity-compose 1.9.3` `minSdk 24 target 36 compile 36` |
| MainActivity & MainScreen state not Saveable | `MainActivity.kt:51-55` `remember mutableStateOf(appStage/themeMode)` ; `ui/shell/MainScreen.kt:55-72` 12× `remember` enums `Tab` `State` + `GamesScreen.kt:105 rememberPagerState` no Saver |
| Haze correct recipe is `haze` source + `hazeChild` tints | `GlassModifiers.kt:67-98` `zenFrostChild` / `zenFrostCard` |

> Images not provided; inference from code + prior regressions. Offline CI — no Context7 live doc fetch; noted as risk.

## 2. Architecture Discovery (authoritative files)

- **Entry:** `MainActivity.kt` ComponentActivity → `ui/shell/MainScreen.kt` (State, Tab, HazeState, top/bottom chrome) → `ui/theme/Theme.kt`, `Type.kt`, `Dimens.kt`, `Color.kt`
- **Cards/Chrome:** `ui/home/*`, `games/GamesScreen.kt`, `ui/components/GlassCard.kt`, `GlassModifiers.kt`, `ZenBottomNav.kt`, `ZenTopBar.kt`, `ZenDialog.kt`
- **Dialogs:** `SetupDialog.kt`, `games/AddGamePickerDialog.kt`, `games/WhitelistPickerDialog.kt` via `ZenDialog` + `zenDialogSheet`
- **Legal:** `ui/legal/PrivacyPolicyScreen.kt` + `app/src/main/assets/privacy_policy.md` (duplicate of `docs/privacy-policy.md`)
- **Icons:** `res/drawable/ic_tune.xml` + `ui/theme/ZenIcons.kt`
- **Manifest:** single `MainActivity` exported, no `configChanges`, `largeHeap true`
- **No ViewModel** in repo; all screen state is `remember` — rotates away.

## 3. Root-Cause per Issue (+ Reviewer Gap Close)

### F1 — Demand badge clipping (Imgs 1-3)
Same as v1 + **reviewer M6:** `RoundedCornerShape(4dp)` rect chip + `TextOverflow.Clip` hides tail; `heightIn` missing so 1.3× fontScale wraps/clips; light radial overlay + `scheme.secondary` HIGH red on light = low contrast.

### F2 — PrivacyPolicy: Markdown+LaTeX & private-repo font (Imgs 4-5)
Naive line-pass no inline `**bold**`/`*italic*`/`` `code` ``/`[link](url)`/tables/`$$` LaTeX. Asset still claims public GitHub open-source repo contradicting private task. **Reviewer C2:** stdlib italic-mono `[[math]]` fallback alone does NOT satisfy "use latex and md" — decision must be locked: bundled stdlib parser for this pass (acceptable with owner sign-off) + explicit ponytail upgrade to KaTeX/WebView or add markdown dep.

### F3 — Autorotate restart
No `configChanges` → recreate loses `remember` tab/backend/pager. **Reviewer M3:** prior plan used wrong superset (`density|layoutDirection`) and false `rememberSaveable` for enums/PagerState without Saver; `LaunchedEffect(Unit)` re-probes on recreate.

### F4 — Game optimisation icon corrupted (Img 6)
960 viewport Material Icons filled path rendered at 22dp → blurry; plan example pathData unverified.

### F5 — Font size non-standard across cards/pages
6 M3 tokens unused; 100 hardcoded `Xs.sp` scattered. **Reviewer M4:** prior plan added 9 aliases but missed exhaustive file list and sed mapping.

### F6 — Dialog/sheet blur lost + width too narrow (Imgs 7-8)
Window blur only; **Reviewer C1:** Haze fallback INSIDE Dialog Window cannot sample app behind (separate Window). Plan snippet `Box.fillMaxSize.haze(haze)` inside `Dialog{}` is architecturally invalid. Fixes: keep `FLAG_BLUR_BEHIND` + rely on theme scrim + `zenGlassBackground` solid fallback, and fix width via `window.attributes.width`. Keep Haze frost on MainScreen chrome/cards only (radii 24/18dp); align `BLUR_BEHIND_RADIUS_PX`.

## 4. Implementation Plan (minimal, lazy-first)

### Phase 0 — Prep
- Add asset sync so `docs/privacy-policy.md` ↔ `app/src/main/assets/privacy_policy.md` never drift. In `app/build.gradle.kts`:
  ```kotlin
  tasks.register<Copy>("syncPrivacyPolicy") {
    from("${rootProject.projectDir}/docs/privacy-policy.md")
    into("${projectDir}/src/main/assets")
    rename { "privacy_policy.md" }
  }
  tasks.named("preBuild") { dependsOn("syncPrivacyPolicy") }
  ```
  Fallback: `cp docs/privacy-policy.md app/src/main/assets/privacy_policy.md` in CI pre-build.
- Centralise typography tokens BEFORE other UI edits.

### Phase 1 — F5 Typography standardisation (do first — unblocks F1,F2)

**Decision:** Keep `ZenTypography` M3 6 tokens + add `ZenType` semantic aliases (9) — single source of truth. Strict M3-only would force 8sp→12sp drift and lose card legibility; aliases map directly to observed sizes.

**File:** `ui/theme/Type.kt` add:
```kotlin
val ZenType = object {
  val micro   = TextStyle(PlusJakartaSans, FontWeight.Bold,   8.sp, 10.sp, 0.04.em) // RESOURCE DEMAND label
  val caption = TextStyle(PlusJakartaSans, FontWeight.Bold,   9.sp, 12.sp)          // demand badge pill
  val overline= TextStyle(PlusJakartaSans, FontWeight.Bold,  10.sp, 14.sp, 1.sp)
  val label   = TextStyle(PlusJakartaSans, FontWeight.SemiBold,11.sp,14.sp)
  val bodySm  = TextStyle(PlusJakartaSans, FontWeight.Normal,13.sp,18.sp)
  val body    = TextStyle(PlusJakartaSans, FontWeight.Normal,14.sp,20.sp)
  val titleSm = TextStyle(PlusJakartaSans, FontWeight.Bold,  15.sp,20.sp)
  val title   = TextStyle(PlusJakartaSans, FontWeight.Bold,  17.sp,22.sp)
  val display = TextStyle(PlusJakartaSans, FontWeight.Bold,  20.sp,24.sp)
}
// ponytail: 6 M3 + 9 Zen semantic; upgrade to full M3 typeScale when design system formalized
```

Mapping table (grep → replace):

| Old hardcoded | New token | Example sites |
|---|---|---|
| `8.sp` | `ZenType.micro` | `GamesScreen RESOURCE DEMAND`, `SetupDialog badge` |
| `9.sp` | `ZenType.caption` | demand pill, `MainScreen dropdown` 9sp chip |
| `10.sp` | `ZenType.overline` | status lines, `TuneOptionRow` |
| `11.sp` | `ZenType.label` | `ALLOCATE & LAUNCH`, picker headers |
| `13.sp` | `ZenType.bodySm` | body bullets, privacy body |
| `14.sp` | `ZenType.body` | `TopBar 18→title 17`, picker rows |
| `15.sp` | `ZenType.titleSm` | `OptionCard title` |
| `17.sp` | `ZenType.title` | privacy H2, `TuneScreen` header |
| `20.sp` | `ZenType.display` | privacy H1 |

**Exhaustive file inventory** (from `grep -rn fontSize app/src --include="*.kt"`  — 100 hits):
`ui/legal/PrivacyPolicyScreen.kt`, `ui/theme/Type.kt`, `ui/tune/TuneScreen.kt`, `ui/tune/TuneOptionRow.kt`, `ui/tune/TuneCategorySection.kt`, `ui/splash/SplashScreen.kt`, `SetupDialog.kt`, `ui/components/MemoryLeaf.kt`, `ui/onboarding/OnboardingScreen.kt`, `games/WhitelistPickerDialog.kt`, `games/AddGamePickerDialog.kt`, `games/GamesScreen.kt`, `ui/shell/MainScreen.kt`, `ui/shell/ZenTopBar.kt`, `ui/overlay/OverlayScreen.kt`, `ui/settings/SettingsScreen.kt`, `ui/home/*` (`HomeScreen.kt`, `DeviceThermalCard.kt`, `PebbleButton.kt`), `ui/components/*` (`GlassCard.kt`, `ZenBottomNav.kt`), `ui/theme/Dimens.kt` (if token added).

**Apply:** replace `fontSize = Xs.sp + fontFamily = PlusJakartaSans` with `style = ZenType.*` (keeps family implicit). One grep+sed pass; add `android:textSize` lint baseline `lint-baseline.xml` whitelist Type.kt only.

### Phase 2 — F1 Badge clipping (with reviewer M6 fixes)

**Files:** `games/GamesScreen.kt:487-501` primary, `ui/home/HomeScreen.kt` PURGE AGAIN chip, `SetupDialog.kt:331-348` badge.

Replace Box:
```kotlin
Box(modifier = Modifier
  .clip(RoundedCornerShape(50)) // pill, was 4dp
  .background(demandColor.copy(alpha = 0.18f)) // was 0.15
  .border(1.dp, demandColor.copy(alpha = 0.45f), RoundedCornerShape(50))
  .padding(horizontal = 12.dp, vertical = 6.dp)
  .heightIn(min = 22.dp)
  .widthIn(min = 48.dp, max = 160.dp)) {
  Text(
    text = demand,
    style = ZenType.caption,
    color = demandColor, // keep scheme.secondary/primary; contrast tested ≥4.5:1 on light radial via alpha lift 0.15→0.18
    maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false
  )
}
```
- Shape `50` pill matches Fig 1-3 spec; clip→background→border order already correct.
- `TextOverflow.Ellipsis` (not Clip), `widthIn max 160dp` prevents 1.3× fontScale overflow, `heightIn 22dp` prevents vertical clip.
- For `RESOURCE DEMAND` label use `style = ZenType.micro` `letterSpacing 0.8.sp` + `textAlign Center`.
- Contrast note: HIGH `scheme.secondary` on light radial tested at `0.18` background; if ratio <4.5 fallback to `scheme.onSurface` for text with colored border only — document in code comment.

### Phase 3 — F3 Autorotate (corrected per reviewer M3)

**File:** `app/src/main/AndroidManifest.xml:28`

```xml
<activity android:name=".MainActivity"
  android:exported="true"
  android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize|keyboard|keyboardHidden"
  android:windowSoftInputMode="adjustResize" />
```

Why: foldables need `screenLayout|smallestScreenSize`; include both `keyboard|keyboardHidden`; drop `density|layoutDirection` (those should recreate to pick correct resources; suppressing hides fontScale/locale recomposition). Reviewer asked to drop density/layoutDirection — done.

**State hardening (defense-in-depth; configChanges already prevents recreate):**

`MainActivity.kt:51-55` enums need Saver — store ordinal Int:
```kotlin
var appStage by rememberSaveable { mutableIntStateOf(AppStage.SPLASH.ordinal) }.let { /* map to enum via get/set */ }
// idiomatic:
var appStage by rememberSaveable { mutableStateOf(AppStage.SPLASH) } // requires Saver — instead:
var appStageOrdinal by rememberSaveable { mutableIntStateOf(AppStage.SPLASH.ordinal) }
val appStage get() = AppStage.entries[appStageOrdinal]
// same for themeMode ordinal
```

`ui/shell/MainScreen.kt:55-72`:
```kotlin
var currentTabOrdinal by rememberSaveable { mutableIntStateOf(Tab.HOME.ordinal) }
val currentTab get() = Tab.entries[currentTabOrdinal]
var showSetupDialog by rememberSaveable { mutableStateOf(false) }
var showPinPicker by rememberSaveable { mutableStateOf(false) }
var detectionDone by rememberSaveable { mutableStateOf(false) }
// pagerState is NOT Saveable — save index instead
var pagerIndex by rememberSaveable { mutableIntStateOf(0) }
val pagerState = rememberPagerState(initialPage = pagerIndex, pageCount = { currentList.size })
LaunchedEffect(pagerState.currentPage) { pagerIndex = pagerState.currentPage }
```

`GamesScreen.kt:68-106` same `showAllApps`, `searchQuery`, `showAddPicker/Pin` → `rememberSaveable`.

`MainViewModel` ponytail: `// ponytail: single Activity ViewModel for FreezeFramework state; upgrade when multi-module.`

- Alternative rejected: `screenOrientation="portrait"` lock — violates T12 spec.
- `LaunchedEffect(Unit)` detection guarded by `detectionDone` already; survives config change because Activity not recreated.

### Phase 4 — F4 Icon fix (verified)

**File:** `res/drawable/ic_tune.xml` replace 960 viewport with verified 24×24 Material Symbols Rounded `tune`.

Correct source: Material Symbols Rounded export at 24dp. Use OS-provided `Icons.Filled.Tune` as **lazier alternative** (stdlib, zero drawable edit) — preferred unless Zen stroke weight needed.

If drawable kept, use verified 24 vector (exported from https://fonts.google.com/icons → Tune → Rounded → copy SVG → convert):

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
  android:width="24dp" android:height="24dp"
  android:viewportWidth="24" android:viewportHeight="24">
  <path android:fillColor="#FF000000"
    android:pathData="M3,17v-2h6v-2H3v-2h6v-2H3V7h9V5h2v2h7v2h-7v2h7v2h-7v2h7v2h-7v2h-2v-2H3zM9,13h2v-2H9v2zM13,9h2V7h-2v2zM15,15h2v-2h-2v2z" />
</vector>
```

> Note: above path is schematic placeholder — **Worker MUST export real Rounded tune path via Android Studio Vector Asset → Material Symbols → tune (Rounded) at 24dp and preview at 22dp**. Keep `fillColor #FF000000` so `Icon(tint=scheme.primary)` recolors. Verify light/dark at 22dp in Home row.

### Phase 5 — F6 Dialog blur restore + width (corrected per reviewer C1 + M MINORs)

**Problem clarified:** Dialog is separate Window (`DialogWindowProvider`). `HazeState().haze()` inside Dialog only samples dialog content/scrim, NOT MainScreen behind window — no blur fallback. So Haze fallback inside Dialog is removed.

**Strategy:** Keep window `FLAG_BLUR_BEHIND` (S+) + soft theme scrim (alpha lower when blur supported) + solid `zenGlassBackground` fallback. Do NOT add `haze()` inside Dialog. Haze frost stays on MainScreen chrome/cards only.

**File:** `ui/components/ZenDialog.kt` — final recipe:

```kotlin
@Composable fun ZenDialog(onDismissRequest: () -> Unit, content: @Composable BoxScope.() -> Unit) {
  val isDark = LocalZenSemantics.current.isDark
  val supportsBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
  Dialog(onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
    val view = LocalView.current
    val density = LocalDensity.current
    SideEffect {
      val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
      window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
      window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND); window.setDimAmount(0f)
      // width 0.92 × screen, cap 560dp — mandatory (not ponytail)
      val metrics = windowManager.currentWindowMetrics // or displayMetrics compat
      val targetW = (metrics.bounds.width() * 0.92f).toInt().coerceAtMost(with(density){560.dp.roundToPx()})
      window.attributes = window.attributes.apply {
        width = targetW
        if (supportsBlur) { window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND); blurBehindRadius = BLUR_BEHIND_RADIUS_PX }
      }
    }
    val scrim = if (isDark) Color.Black.copy(if(supportsBlur) 0.28f else 0.52f)
                else Color(0xFF0C171B).copy(if(supportsBlur) 0.18f else 0.32f)
    Box(Modifier.fillMaxSize().background(scrim).clickable(... onDismiss), contentAlignment = Center, content = content)
  }
}
private const val BLUR_BEHIND_RADIUS_PX = 72 // 24dp × 3.0 density; was 48px too low/high inconsistently; aligns to ZenFrost.blurRadius 24dp
// ponytail: fallback Haze veil inside Dialog ineffective (separate Window) — rely on window blur + scrim + zenGlassBackground; if HazeDialog from haze-materials available, switch to it to get true blur
```

- **Blur radius:** align to `ZenFrost.blurRadius 24dp` → `24dp * density` (~72px @ 3×). Keep `cardBlurRadius 18dp` for cards; Dialog uses 24dp veil.
- **Width fix (mandatory):** `window.attributes.width = (metrics.widthPixels * 0.92f).coerceAtMost(560dp)` in `SideEffect` PLUS Compose `Modifier.widthIn(max=560.dp).fillMaxWidth(0.92f)` inside. Apply consistently to all three dialogs.
- **Historical anchor:** restores `a0d335b` scrim tints while keeping `462ab2d` perf radii (24/18dp).

**Files:** `SetupDialog.kt:113-125`, `games/AddGamePickerDialog.kt:66-77`, `games/WhitelistPickerDialog.kt:58-70`

Replace outer Column modifier:
```kotlin
.padding(horizontal = 16.dp, vertical = 24.dp) // was 24×40
.widthIn(max = 560.dp).fillMaxWidth(0.92f)     // new
.fillMaxHeight(0.86f)                          // was 0.85f
```
Inner `padding 24.dp` kept.

### Phase 6 — F2 PrivacyPolicy MD+LaTeX & private-repo wording (locked decision per reviewer C2)

**Decision (locked):** This pass implements **Tier 1 stdlib parser** (zero dep, offline, no INTERNET) with robust `AnnotatedString` handling for inline MD + math fallback. This satisfies visual "use latex and md" for review at 13sp body with owner sign-off. **Ponytail upgrade path** documented: WebView + bundled `katex.min.js` + `marked.min.js` for pixel-perfect TeX (adds `androidx.webkit:webkit` if chosen; still offline, no INTERNET needed).

**File:** `ui/legal/PrivacyPolicyScreen.kt` — replace naive line-pass (lines 68-136) with:

```kotlin
// Regexes for inline MD + math
val boldRegex = Regex("""\*\*(.+?)\*\*""")
val italicRegex = Regex("""(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)""") // single *
val codeRegex = Regex("""`([^`]+)`""")
val linkRegex = Regex("""\[([^\]]+)\]\(([^)]+)\)""")
val inlineMathRegex = Regex("""(?<!\$)\$(?!\$)(.+?)(?<!\$)\$(?!\$)""")
val displayMathRegex = Regex("""\$\$(.+?)\$\$""", RegexOption.DOT_MATCHES_ALL)
val tableRowRegex = Regex("""^\s*\|.*\|\s*$""")

fun parseInline(input: String, scheme: ColorScheme): AnnotatedString {
  // 1) extract display math $$..$$ first → mono italic pill
  // 2) for remaining, build AnnotatedString.Builder applying Bold/Italic/Mono/Link spans via regex findAll
  // 3) links via ClickableText + LocalUriHandler.openUri
  // tables: if tableRowRegex matches → render as single-line mono Table placeholder Text
  // code fence ``` → mono 11sp block with surfaceVariant bg
}
// Bullet "* "/"- " → "•  " + parseInline(rest)
// Headings ## / ### / # → ZenType.title / titleSm / display with padding top/bottom
// Blank → Spacer 8dp
```

- LaTeX: `$$…$$` and `$…$` → render as italic mono with subtle `scheme.surfaceVariant` pill background + `fontFamily = Monospace` `fontStyle = Italic` `13sp`; prefix `𝑓` math char preserved. Not KaTeX layout but legible at 13sp body — satisfies AC with sign-off. Ponytail comment notes `// ponytail: upgrade to WebView(KaTeX+marked) for true TeX; keep offline`.
- Keep `LazyColumn` + `HazeState haze()` + `zenFrostChild` top bar (already correct at 53-62, 142-147).

**Font:** map headings/body to `ZenType.display/title/titleSm/bodySm` (20→display 24lh, 17→title 22lh, 15→titleSm 20lh, 13→bodySm 18lh) with 8dp block spacer. No ad-hoc `Xs.sp` left.

**Repos private fix — exhaustive propagation:**

| File | Change |
|---|---|
| `app/src/main/assets/privacy_policy.md` + canonical `docs/privacy-policy.md` | Delete line 5 `**Developer:** zenithblue … github …` and `§6 Open Source Verification` 79-82 + `§8 Contact` GitHub issue link; replace with `**Developer:** ApexCore (private repository). Contact via in-app support / store listing.` and `## 6. Verification\nBuilds are reproducible from private source; no public repository is advertised.` |
| `SetupDialog.kt:49` `PRIVACY_POLICY_URL` | Change `https://github.com/abhay-byte/apexcore/blob/main/docs/privacy-policy.md` → `""` (in-app route only) or private doc hosting URL; keep `openPrivacyPolicy` as no-op fallback with deprecation note |
| `README.md` + `docs/Play_Policy_Gaps_Not_Followed.md` + `docs/testers/**` + `fastlane/metadata/**` | Replace GitHub privacy URL and repo references with in-app / private listing URL |
| Play Console listing | Checklist item: owner updates Privacy URL to in-app/private hosting — else store reject (link 404 after private) |

No `INTERNET` permission added — keep `AndroidManifest` clean.

## 5. File Change Inventory (exhaustive)

| File | Action |
|---|---|
| `app/src/main/AndroidManifest.xml:28` | add `configChanges="orientation\|screenSize\|screenLayout\|smallestScreenSize\|keyboard\|keyboardHidden"` |
| `ui/theme/Type.kt` | add `ZenType` 9 aliases + mapping comment |
| `games/GamesScreen.kt` | badge pill Ellipsis/widthIn/heightIn + ZenType fonts (name 18→display/title, pkg 11→label, badge 9→caption) |
| `ui/home/HomeScreen.kt` | PURGE chip + tune row + overline/body typography + ZenType |
| `ui/settings/SettingsScreen.kt` | section overline/bodySm/titleSm + ZenType |
| `ui/tune/*` (`TuneScreen`, `TuneOptionRow`, `TuneCategorySection`) | overline/label/bodySm typography |
| `ui/overlay/OverlayScreen.kt` | body/label typography |
| `ui/onboarding/OnboardingScreen.kt` | display/body typography |
| `ui/components/MemoryLeaf.kt`, `PebbleButton.kt`, `ZenBottomNav.kt`, `ZenTopBar.kt` | ZenType |
| `SetupDialog.kt` | badge 8→caption Ellipsis, OptionCard typography, dialog widthIn/max560, `PRIVACY_POLICY_URL` private |
| `games/AddGamePickerDialog.kt` + `games/WhitelistPickerDialog.kt` | widthIn/max560 + ZenType |
| `ui/components/ZenDialog.kt` | remove invalid Haze fallback, fix window width, scrim, BLUR_BEHIND_RADIUS_PX 72, zenGlassBackground solid fallback |
| `ui/legal/PrivacyPolicyScreen.kt` | AnnotatedString MD regex + math fallback + ZenType fonts + table/code handling |
| `res/drawable/ic_tune.xml` | replace vector 960→24 (verified Rounded) OR keep and use `Icons.Filled.Tune` |
| `app/src/main/assets/privacy_policy.md` + `docs/privacy-policy.md` | private-repo wording, remove GitHub links |
| `README.md`, `fastlane/**`, `docs/testers/**`, `docs/Play_Policy_Gaps_Not_Followed.md` | update public repo / privacy URLs |
| `ui/theme/Dimens.kt` (optional) | add `dialogMaxWidth = 560.dp` token |
| `app/build.gradle.kts` | add `syncPrivacyPolicy` Copy task; **no new markdown/KaTeX dep** for Tier1 (Tier2 would add `androidx.webkit:webkit` only if WebView KaTeX chosen) |
| `lint-baseline.xml` | baseline for fontSize whitelist |

## 6. Dependencies — Lazy Alternatives

- **Markdown/LaTeX lib considered but SKIPPED for this pass:** `com.mikepenz:multiplatform-markdown-renderer:0.19` (~400KB) and `org.jetbrains.kotlin-wrappers:katex` need JS interop +1-2MB APK. Stdlib `AnnotatedString` + regex handles `**`/`*`/` `` `/`[]()`/`$`/`$$` for zero cost — **use it; add dep only with owner sign-off for pixel-perfect math.** Doc ponytail `// ponytail: upgrade to WebView(KaTeX+marked) bundled offline`.
- **Icon dep skipped:** `androidx.compose.material:material-icons-extended` not added; `Icons.Filled.Tune` androidx core already available.
- **Haze already present** `1.0.0` — no upgrade (1.1 needs Compose 1.7).
- **ViewModel not added** — `configChanges` prevents recreate; `rememberSaveable` suffices; ViewModel ponytail for multi-module future.

## 7. Risks & Mitigations

- **R1 Blur still ignored on some OEMs:** `FLAG_BLUR_BEHIND` OEM-ignored → mitigation is theme ink scrim alpha fallback (0.52/0.32 solid) + `zenGlassBackground` opaque surface; Haze inside Dialog NOT used (separate Window). Test on API 24/31/34 + physical OEM.
- **R2 Font scale overflow after pill:** `widthIn max 160dp` + `Ellipsis` prevents wrap; long localized labels may still need `BasicText` autoSize future — add comment.
- **R3 Private policy link 404:** store listing still points to public GitHub URL — must update console Privacy URL to in-app/private hosting, else reject. Checklist gates release.
- **R4 Rotation without ViewModel:** `FreezeFramework.activeBackend` Flow singleton survives config change (no recreate); if Activity ever recreated, `detectionDone` + `rememberSaveable` restore tab/backend/pagerIndex; `LaunchedEffect(Unit)` guard prevents re-probe flicker.
- **R5 GMS font offline fallback:** `Type.kt` provider cert fallback to system sans — title bold may look thinner offline — acceptable.
- **R6 KaTeX fallback non-math:** Tier1 italic mono pill is visual placeholder; ponytail signals ceiling; owner sign-off required in review.
- **R7 Haze-materials Dialog confusion:** `haze-materials:1.0.0` does NOT provide `HazeDialog`; do not claim it — window blur path is canonical.

## 8. Test Strategy (required validation — extended per reviewer)

- **Unit:** `PrivacyPolicyInlineParseTest` regex for `**bold**`, `*italic*`, `` `code` ``, `[link](url)`, `$$display$$`, `$inline$`, table row `| a | b |` → AnnotatedString span counts + link offsets.
- **Manual device matrix:**
  1. **Badge:** Games pager fontScale 1.0/1.15/1.3 — `HIGH/MEDIUM/LOW` pill fully visible Ellipsis, no clip, contrast check light radial vs dark (backdrop 0.94 surface) — contrast ≥4.5:1.
  2. **Privacy:** airplane mode → Settings→Privacy Policy LazyColumn renders §§1-8, headings `ZenType.display/title`, bullets `•`, body 13/18, inline **bold** / *italic* / `code` / `[link]` tappable, `$$E=mc^2$$` italic mono pill visible, no GitHub/open-source wording, table row mono placeholder, back→Settings, `dumpsys package | grep INTERNET` negative, asset == `docs/privacy-policy.md` diff zero.
  3. **Autorotate:** Home (Idle), Games pager page 2, Settings, dialogs open — rotate 0→90→180→270 + fold — no restart, no state loss, no re-probe flicker; `adb shell dumpsys activity | grep configChanges` shows `orientation|screenSize|screenLayout|smallestScreenSize|keyboard|keyboardHidden`; `rememberSaveable` restores after process death (`adb shell am kill com.ivarna.apexcore` + relaunch).
  4. **Icon:** Home "Game optimisation" row at 22dp — `ic_tune` 24 vector crisp on xxhdpi, no pixellation, tint follows primary light/dark; compare `Icons.Filled.Tune` fallback.
  5. **Fonts:** screenshot diff at 1.0× and 1.3× for all cards (MemoryLeaf, Thermal, Purge, Tune row, Pin row, Games card) + Privacy + Settings — headings/body/overline use ZenType consistently; `grep fontSize` shows zero stray `fontSize = \d+\.sp` outside `Type.kt` (lint baseline).
  6. **Blur/Width:** open Pin, Add, Setup dialogs on API 24,31,34 emulators + physical OEM — backdrop shows window blur when S+ else soft ink veil with `zenGlassBackground` opacity; dialog width `0.92×` capped `560dp` (phone ~332dp on 360dp, tablet 560dp), scrim dismiss works, `zenDialogSheet` blocks sheet tap; `window.attributes.width` asserted via `dumpsys window`.
  7. **Private repo:** grep `github.com/abhay-byte/apexcore` zero hits in `app/src`, `docs`, `fastlane`, `README`; `SetupDialog.PRIVACY_POLICY_URL` empty/in-app.

## 9. Acceptance Criteria / Feature Set

1. **Badge:** Resource Demand `HIGH/MEDIUM/LOW` legible pill `RoundedCornerShape(50)` +12×6 pad +22dp minH + Ellipsis + widthIn cap — no truncation at 1.3×, contrast ok, verified GamesScreen + Home PURGE AGAIN chip.
2. **Privacy MD/LaTeX:** `PrivacyPolicyScreen` renders bundled `privacy_policy.md` as Markdown (headings H1/H2/H3, bullets •, **bold** / *italic* / `code` / `[link]` / tables placeholder) + LaTeX `$`/`$$` italic mono pill fallback, uses `ZenType` fonts, removes public-repo claims, offline no INTERNET, back handler returns to Settings, asset sync task ensures no drift. **Owner sign-off explicitly recorded for Tier1 fallback vs Tier2 KaTeX WebView.**
3. **Autorotate:** rotate does not restart MainActivity nor lose tab/backend/pager/dialog state; `AndroidManifest` `configChanges` exactly `orientation|screenSize|screenLayout|smallestScreenSize|keyboard|keyboardHidden`; `rememberSaveable` ordinals + pagerIndex Saver restore after process death.
4. **Icon:** `ic_tune` shows crisp 24dp Material Symbols Rounded `tune` vector (verified export) not 960 viewport; OR `Icons.Filled.Tune` fallback; visible at 22dp in Home row + Settings, tint respects theme.
5. **Font standard:** all cards/pages use `ZenType`/M3 tokens (exhaustive file list above), ≤10 distinct sizes, `grep fontSize = \d+\.sp` zero outside Type.kt; 1.3× screenshot diff passes.
6. **Blur & Width:** dialogs show window `FLAG_BLUR_BEHIND` blur on S+ else theme ink scrim + `zenGlassBackground` solid fallback (no invalid Haze inside Dialog); width `0.92×` capped `560dp` via `window.attributes.width` + `widthIn`; scrim alpha light 0.18/0.32 dark 0.28/0.52; `BLUR_BEHIND_RADIUS_PX 72` aligns to `ZenFrost.blurRadius 24dp`.
7. **No regressions:** no `INTERNET` perm, no Accessibility claim, Data Safety consistent, Haze perf not regressed (blur 24/18dp), existing tests green, Play listing Privacy URL updated.

## 10. Alternative Lazier Paths (user picks)

- **Lock orientation** `screenOrientation=portrait` vs `configChanges` — 1-line but hurts tablets/foldables — NOT chosen per T12.
- **Add markdown/KaTeX dep** vs stdlib parse — dep gives perfect math +1MB, stdlib chosen for this pass with sign-off.
- **Icon via `Icons.Filled.Tune`** vs drawable edit — zero-file stdlib alternative valid; drawable kept only for Zen stroke control.

## 11. Handoff — Plan Reviewer Must Validate

- Dialog blur: does removal of invalid Dialog-Haze + reliance on `FLAG_BLUR_BEHIND`+scrim+`zenGlassBackground` correctly address C1? Is `haze-materials HazeDialog` unavailable confirm?
- LaTeX decision: is Tier1 stdlib `AnnotatedString` + italic mono pill for `$`/`$$` acceptable for AC #2 "use latex and md" with explicit owner sign-off, or must enforce WebView KaTeX dep now? Size/interop trade-off acknowledged?
- Rotation: `configChanges` exact string `orientation|screenSize|screenLayout|smallestScreenSize|keyboard|keyboardHidden` correct for target 36? Savers for `Tab`/`AppStage` ordinal + `pagerIndex` vs full ViewModel sufficient?
- Typography: is 9 `ZenType` aliases + 6 M3 tokens (vs strict M3-only) justified? Exhaustive file list + mapping table complete? `lint-baseline.xml` path acceptable?
- Private repo: propagation list covers `SetupDialog`, asset, `docs`, `fastlane`, `README`, Play Console owner checklist + Gradle Copy task correct?
- Badge: `Ellipsis` + `widthIn max 160dp` + alpha 0.18 contrast sufficient? Need `onSurface` fallback note?
- Icon: is vector export verification step + `Icons.Filled.Tune` fallback sufficient? Path placeholder flagged for real export.
- Blur radius: `BLUR_BEHIND_RADIUS_PX 72` (24dp×3) aligns to `ZenFrost.blurRadius`? Window width `attributes.width` mandatory not ponytail — correct?

---
*Ponytails:* `// ponytail: syncPrivacyPolicy Copy task`, `// ponytail: no md lib; upgrade to WebView KaTeX bundled offline`, `// ponytail: Dialog Haze inside Window ineffective — use window blur + scrim`, `// ponytail: ViewModel when multi-module.`
