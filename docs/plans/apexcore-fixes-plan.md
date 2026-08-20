# ApexCore Fixes Plan — PASS 2 (Iteration 2)

> Addresses all 4 MAJOR + 3 MINOR findings from `docs/reviews/apexcore-fixes-plan-review.md` (PASS 1 ITER 1). Deterministic targets, no invented deps, read-only pass.

## Research Sources

- Repo scan (verified PASS 2):
  - `app/src/main/kotlin/com/ivarna/apexcore/ui/home/HomeScreen.kt:264-293` (RAM Free row + spacers, `onRamFreeClick` param)
  - `app/src/main/kotlin/com/ivarna/apexcore/SetupDialog.kt:49,224-241,438-446` (PRIVACY POLICY pill + `openPrivacyPolicy`)
  - `app/src/main/kotlin/com/ivarna/apexcore/ui/settings/SettingsScreen.kt:49,227-255` (LEGAL card, `openPrivacyPolicy` import — KEEP)
  - `app/src/main/kotlin/com/ivarna/apexcore/ui/shell/MainScreen.kt:61-62,134-145,199,220-272` (showRamFree/showTuneScreen state, dual `AnimatedVisibility`)
  - `app/src/main/kotlin/com/ivarna/apexcore/games/GameOverlayService.kt:122-186,403-418,585-615` (onStartCommand, onDestroy, shutdown(), `isRunning @Volatile`, START_REDELIVER_INTENT)
  - `app/src/main/kotlin/com/ivarna/apexcore/ui/overlay/OverlayScreen.kt:33-42,142-219` (testOverlayActive ephemeral, header strings)
  - `app/src/main/kotlin/com/ivarna/apexcore/games/GamesScreen.kt:487-501` (demand badge padding 8/3)
  - `app/src/main/AndroidManifest.xml:5-50` (no INTERNET, FGS specialUse TYPE_APPLICATION_OVERLAY)
  - `docs/privacy-policy.md:1-92` (canonical §§1-8, line 14 `does not request INTERNET`)
  - `app/build.gradle.kts` (no WebView/markdown dep)
- Review evidence: `docs/reviews/apexcore-fixes-plan-review.md` (MAJOR×4, MINOR×3, SUGGESTION×2)
- Compliance: `docs/plans/T11-zen-organic-ui-redesign.md:97` (KDE-14 privacy discoverability), `docs/Play_Policy_Gaps_Not_Followed.md:117`

## Current Architecture (unchanged, re-verified)

- `MainScreen` hosts `showRamFree`/`showTuneScreen` full-screen branches + `AnimatedContent` tabs; chrome (`ZenTopBar`, `ZenBottomNav`) gated by `AnimatedVisibility(visible = !showRamFree && !showTuneScreen)` at lines 221 and 261.
- `HomeScreen(81-93)` param `onRamFreeClick` wired at `MainScreen:199`; row at `283-289` + trailing spacer `291`.
- Privacy: `SetupDialog` pill external browser, `Settings` LEGAL card external browser — Settings must survive as item 5 entry point.
- Overlay: `isRunning` volatile in-memory only; `testOverlayActive` ephemeral; no prefs hydration.

---

## Finding-by-Finding Patch (PASS 2)

### [MAJOR-1] Image-2 target — lock to SetupDialog.kt:224-241, guard Settings

**Pre-edit deterministic step (mandatory, before any delete):**

```bash
grep -rn "PRIVACY POLICY\|Privacy Policy\|PRIVACY_POLICY_URL\|openPrivacyPolicy" app/src/main/kotlin --include="*.kt"
# Expected hits:
# SetupDialog.kt:49  const val PRIVACY_POLICY_URL
# SetupDialog.kt:230 .clickable { openPrivacyPolicy(context) }  + Text("PRIVACY POLICY")
# SetupDialog.kt:438 fun openPrivacyPolicy
# ui/settings/SettingsScreen.kt:49  import com.ivarna.apexcore.openPrivacyPolicy
# ui/settings/SettingsScreen.kt:228 onClick = { openPrivacyPolicy(context) }
```

- Capture screenshot/photo of every hit screen before edit (camera, not code).
- **Primary target = `SetupDialog.kt:224-241`** — the `Box(clip(RoundedCornerShape(50)).background(...).border(...).clickable{openPrivacyPolicy}.padding(16.dp,8.dp)) { Text("PRIVACY POLICY") }` pill plus its **following** `Spacer(12.dp)` at line 243.
- **Guard: DO NOT touch `SettingsScreen.kt:227-255` LEGAL `GlassCard(onClick={openPrivacyPolicy})`** — it is rerouted in item 5 to in-app page, not deleted. If Image 2 photo matches Settings LEGAL card (contradiction with AC 5), STOP item 2, flag BLOCK, clarify with reporter — default is keep Settings per FEATURE_SET Scenario 5.

**Change (1 hunk, 1 file):**
- `SetupDialog.kt`: delete lines 224-241 (Box pill) + line 243 `Spacer(12.dp)` only. Keep `PRIVACY_POLICY_URL` const (line 49) and `openPrivacyPolicy()` function (438-446) — still used as fallback/deprecated until new screen ships. No import change needed here.

**Acceptance:** `grep -rn "PRIVACY POLICY" app/src/main/kotlin` finds 0 hits outside `SettingsScreen` LEGAL card (which after item 5 no longer shows that literal if rerouted, but before item 5 it shows header "Privacy Policy" — case-sensitive check). Device photo: SetupDialog no longer shows pill; Settings still shows Privacy Policy.

### [MAJOR-4] HomeScreen spacer hunk off-by-one — exact lines

**Evidence:** `HomeScreen.kt:264-293` structure:
```
264 if (isElevatedBackend) {
265   HomeAnimatedEntryRow("Game optimisation"...)
280   Spacer(12.dp)   // ← belongs to tune block (INSIDE if)
281 }
283 HomeAnimatedEntryRow("RAM Free"...)   // ← delete
...
291 Spacer(12.dp)                          // ← delete (trailing, belongs to RAM Free)
293 HomeAnimatedEntryRow("Pin Apps"...)
301 Spacer(ZenDimens.elementGap)
```

**Change (1 file, 1 hunk):**
- Delete `HomeAnimatedEntryRow(title="RAM Free", subtitle="Force system reclaim", icon=ZenIcons.WaterDrop, enabled=..., onClick=onRamFreeClick)` at **283-289** + **following `Spacer(12.dp)` at 291 only**.
- **Keep** `Spacer(12.dp)` at line 280 inside `if(isElevatedBackend)` — it spaces Game optimisation → Pin Apps when tune visible.
- Follow-up (same PR, second commit): deprecate `onRamFreeClick` param at `HomeScreen.kt:91` for one commit, then remove it + `import ZenIcons.WaterDrop` if unused elsewhere + `MainScreen.kt:199 onRamFreeClick={showRamFree=true}` passthrough. Keep `RamFreeScreen.kt` file orphaned hidden (`ponytail: hidden route, delete file only if product confirms feature fully removed`).

**Acceptance:** Home shows Game optimisation → Pin Apps → DeviceThermalCard with correct 12.dp gap whether tune visible or not; `grep -n "RAM Free" app/src/main/kotlin/ui/home/HomeScreen.kt` → 0.

### [MAJOR-2] Overlay persistence — dual-source + clear on external kill

**Exact state derivation (put in `OverlayScreen.kt`):**

```kotlin
// Single prefs file — same as app: "apexcore" MODE_PRIVATE
private const val PREF_OVERLAY_RUNNING = "overlay_running"
private const val PREF_OVERLAY_PKG = "overlay_pkg"   // was overlay_running_pkg — now short, namespaced

fun isOverlayServiceRunningFallback(context: Context): Boolean = try {
    @Suppress("DEPRECATION")
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
    am.getRunningServices(Int.MAX_VALUE).any { it.service.className == GameOverlayService::class.java.name }
} catch (_: Throwable) { false }

fun deriveRunning(context: Context, prefs: android.content.SharedPreferences): Boolean {
    if (GameOverlayService.isRunning) return true
    val flag = prefs.getBoolean(PREF_OVERLAY_RUNNING, false)
    if (!flag) return false
    // flag true but need truth check: canDrawOverlays + fallback
    if (!android.provider.Settings.canDrawOverlays(context)) return false
    // Prefer isRunning; fallback poll may be throttled on API 34+ but valid for own pkg
    return if (flag) isOverlayServiceRunningFallback(context) || GameOverlayService.isRunning else false
    // Simpler: return flag && (GameOverlayService.isRunning || isOverlayServiceRunningFallback(context))
}
```

**Poll & reconciliation (extend existing `LaunchedEffect` at `OverlayScreen.kt:38-42`):**

```kotlin
val prefs = remember { context.getSharedPreferences("apexcore", Context.MODE_PRIVATE) }
LaunchedEffect(Unit) {
    while (true) {
        hasPermission = Settings.canDrawOverlays(context)
        val running = GameOverlayService.isRunning ||
            (prefs.getBoolean(PREF_OVERLAY_RUNNING,false) && hasPermission && isOverlayServiceRunningFallback(context))
        // External kill / system stop: service dead but prefs still true → clear drift
        if (!running && prefs.getBoolean(PREF_OVERLAY_RUNNING,false)) {
            prefs.edit().remove(PREF_OVERLAY_RUNNING).remove(PREF_OVERLAY_PKG).apply() // apply() async on main thread
            testOverlayActive = false
        } else {
            testOverlayActive = running
        }
        delay(1000)
    }
}
```

**Service prefs writes — all `apply()` async (never `commit()` on main):**
- `GameOverlayService.onStartCommand(122-126)`: after `startForeground`, `getSharedPreferences("apexcore",MODE_PRIVATE).edit().putBoolean(PREF_OVERLAY_RUNNING,true).putString(PREF_OVERLAY_PKG, gamePkg).apply()`
- Clear on **all exit paths** (cover 3 sites):
  - `onDestroy(159-185)` before `isRunning=false`: `prefs.edit().remove(PREF_OVERLAY_RUNNING).remove(PREF_OVERLAY_PKG).apply()`
  - `shutdown(403-418)` before `stopSelf()`: same clear
  - `companion stop(context)` at 612-614: also clear (defensive; service may already be dead, prefs still true if caller hits STOP button) — `context.getSharedPreferences("apexcore",MODE_PRIVATE).edit().remove(...).apply()`
- No new permission; `QUERY_ALL_PACKAGES` already granted, `getRunningServices` is deprecated fallback only (own service still returned on API 34 per docs).

**Acceptance:** START → swipe app away → relaunch → STOP enabled/START disabled within 1s; notification swipe/system kill → UI flips to START within 1-2s, prefs cleared; STOP clears FGS + prefs.

**4a — remove 'test' literals (same files):**
- `OverlayScreen.kt:143` `Text("TEST HUD OVERLAY")` → `Text("HUD OVERLAY")`
- `OverlayScreen.kt:154` `"Launch a dummy monitor to test placement..."` → `"Launch a preview overlay to check placement, transparency, and drag gestures."`
- Sweep `grep -i "\"test\"|\bdummy\b"` limited to `OverlayScreen.kt`/`GameOverlayService.kt` HUD strings only; keep `GameManager`/`FpsStack` non-UI logs.

### [MAJOR-3] Privacy page — offline-first, no INTERNET, exact parser

**Decision (locked): OFFLINE-FIRST. DO NOT add `<uses-permission android:name="android.permission.INTERNET"/>`. Keep `docs/privacy-policy.md:14` truthful. If live fetch ever needed, add perm + update policy + Play Data Safety — not in v1.**

- Asset: copy canonical `docs/privacy-policy.md` (92 lines) to `app/src/main/assets/privacy_policy.md` (or `res/raw` if preferred). Sync: manual `cp docs/privacy-policy.md app/src/main/assets/privacy_policy.md` pre-build; add Gradle `copy` task or `ponytail: asset duplicated from docs; upstream is docs/privacy-policy.md` comment.
- No new dependency (no markdown lib, no WebView). Render natively.

**New file `app/src/main/kotlin/com/ivarna/apexcore/ui/legal/PrivacyPolicyScreen.kt`:**

```kotlin
@Composable
fun PrivacyPolicyScreen(onBack: ()->Unit, modifier: Modifier = Modifier) {
 // Load once
 val text = remember { context.assets.open("privacy_policy.md").bufferedReader().readText() }
 val lines = remember(text) { text.lines() } // stdlib
 // HazeState + zenFrostChild top bar like TuneScreen.kt:145-198 (statusBarsPadding + ArrowBack)
 // Body: LazyColumn
 LazyColumn(modifier.fillMaxSize().padding(horizontal=ZenDimens.containerPadding)) {
   items(lines.size) { idx ->
     val raw = lines[idx]
     val trimmed = raw.trimEnd()
     when {
       trimmed.startsWith("## ") -> item Heading (trimmed.removePrefix("## ").trim(), 17.sp bold, 20.sp lineHeight, 14.dp top padding, 6.dp bottom)
       trimmed.startsWith("### ") -> Heading2 (15.sp bold)
       trimmed.startsWith("* ") || trimmed.startsWith("- ") -> BulletRow (trimmed.removePrefix("* ").removePrefix("- ").trim(), 13.sp, 18.sp lineHeight, bullet = "•  ")
       trimmed.startsWith("# ") -> Title (trimmed.removePrefix("# ").trim(), 20.sp bold) // first line
       trimmed.startsWith("**") -> BodyBold (strip **)
       trimmed.isBlank() -> Spacer(8.dp)
       else -> Body (trimmed, 13.sp, 18.sp lineHeight, color=onSurfaceVariant)
     }
   }
   item { Spacer(ZenDimens.bottomNavClearance) }
 }
 BackHandler(onBack = onBack)
}
// ponytail: offline asset; upgrade to WebView + https://raw.githubusercontent.com/abhay-byte/apexcore/main/docs/privacy-policy.md when live updates needed (add INTERNET + WebView)
```

- Parsing rule locked: `## ` → section heading, `### ` → subheading, `* ` / `- ` → bullet, `# ` → title, blank → spacer, else body. Bold `**x**` stripped to plain (no span needed). No HTML, no WebView, no INTERNET.

**Wiring (also fixes MINOR-4):**

- `MainScreen.kt`: add `var showPrivacyPolicy by remember { mutableStateOf(false) }` alongside `showRamFree`/`showTuneScreen` (62-63).
  - Content branch priority (deterministic): `if (showRamFree) RamFreeScreen(...) else if (showTuneScreen) TuneScreen(...) else if (showPrivacyPolicy) PrivacyPolicyScreen(onBack={showPrivacyPolicy=false}, modifier=Modifier.weight(1f)) else AnimatedContent(...)`
  - Update **both** `AnimatedVisibility` at 221-226 (top bar) and 261-266 (bottom nav): `visible = !showRamFree && !showTuneScreen && !showPrivacyPolicy`
- `SettingsScreen.kt`: add param `onPrivacyClick: ()->Unit = {}` (keep default for previews), change `GlassCard(onClick={openPrivacyPolicy(context)})` at 227-228 to `GlassCard(onClick=onPrivacyClick)`. In `MainScreen:205-214` pass `onPrivacyClick={showPrivacyPolicy=true}`.
- Asset counts as part of item 5; verify airplane mode renders §§1-8 scrollably.

### [MINOR-5] MainScreen chrome visibility — specified above

- Already covered: two `AnimatedVisibility` visibles + branch priority `RamFree > Tune > PrivacyPolicy > AnimatedContent`. Ensures no top/bottom chrome overlap on privacy page.

### [MINOR-6] SettingsScreen import hygiene

- **Keep** `import com.ivarna.apexcore.openPrivacyPolicy` at `SettingsScreen.kt:49` and `SetupDialog.kt:438 openPrivacyPolicy` + `PRIVACY_POLICY_URL` — `SetupDialog` still uses it if pill kept as deprecated fallback. Add in `SetupDialog.kt` above `fun openPrivacyPolicy`: `@Deprecated("Use in-app PrivacyPolicyScreen; kept for SetupDialog fallback")`. No deletion in same PR. Lint clean: import stays used via `SetupDialog`, or suppress if Settings no longer references it.

### [MINOR-7] Overlay prefs namespacing & threading — specified above

- Keys: `overlay_running` (Boolean) + `overlay_pkg` (String) in file `"apexcore"` (shared with `preferred_backend`, `setup_shown_v1`). Not `apexcore_overlay` new file — intentional single file to avoid migration.
- All writes use `apply()` async (main thread safe). Read via `remember { context.getSharedPreferences("apexcore", MODE_PRIVATE) }` in `OverlayScreen` poll to avoid recreation.

### Item 3 — Tag touching boundary (unchanged, re-affirmed)

- `GamesScreen.kt:487-501` badge `Box(.clip(RoundedCornerShape(ZenDimens.roundedSm)).background(...).border(1.dp,...).padding(horizontal=8.dp,vertical=3.dp))` → change to `horizontal=10.dp, vertical=5.dp` (matches `ModeChip 10/4` closest, suggestion locked). Keep shape and 1.dp border. Verify 360dp width, fontScale 1.0/1.3 glyph↔stroke ≥2.dp.

---

## File Change Matrix (authoritative, post-fix)

| Item | File | Lines | Action |
|------|------|-------|--------|
| 1 | `ui/home/HomeScreen.kt` | 283-289, 291 | delete RAM Free row + **following** Spacer only; keep 280 |
| 1 opt | `ui/shell/MainScreen.kt` | 199, 91 | remove passthrough after param deprecated |
| 2 | `SetupDialog.kt` | 224-241 + 243 | delete PRIVACY POLICY pill + following Spacer; keep URL+function deprecated |
| 3 | `games/GamesScreen.kt` | 487-501 | padding `8/3 → 10/5` |
| 4a | `ui/overlay/OverlayScreen.kt` | 142-154 | rename header/desc, remove test/dummy |
| 4b | `games/GameOverlayService.kt` | 122-126, 159-185, 403-418, 612-614 | prefs put/clear `overlay_running`/`overlay_pkg` via `apply()` |
| 4b | `ui/overlay/OverlayScreen.kt` | 33-42 | derive running, poll 1s, clear prefs on external kill |
| 5 | `ui/legal/PrivacyPolicyScreen.kt` | new | Compose LazyColumn native parser + `assets/privacy_policy.md` |
| 5 | `ui/settings/SettingsScreen.kt` | 49, 61-71, 227-228 | add `onPrivacyClick`, reroute LEGAL card, keep import deprecated |
| 5 | `ui/shell/MainScreen.kt` | 61-63, 134-216, 221-226, 261-266 | add `showPrivacyPolicy`, branch, dual chrome visibility |
| 5 asset | `app/src/main/assets/privacy_policy.md` | new | `cp docs/privacy-policy.md` |
| — | `AndroidManifest.xml` | — | **NO** INTERNET added |

`ponytail: RamFreeScreen kept hidden; delete file if product wants full removal. Privacy offline-duplicated; upgrade to live WebView+raw when needed.`

## Implementation Order

1. Item 3 chip padding (1 hunk).
2. Item 1 Home RAM Free (1 hunk, off-by-one fixed).
3. Item 4a test string (string-only).
4. Item 4b overlay prefs + poll (device test).
5. Item 2 SetupDialog pill (photo-gated).
6. Item 5 Privacy page + asset + shell wiring (largest).

## Testing Strategy

- Static: `grep -rn "RAM Free" app/src/main/kotlin/ui/home` → 0; `grep -rn "PRIVACY POLICY" app/src/main/kotlin` → 0 outside Settings header; `grep -n "overlay_running\|overlay_pkg\|isRunning\|testOverlayActive" app/src/main/kotlin`
- Build: `./gradlew :app:assembleDebug :app:testDebugUnitTest` (tune 43 tests green).
- Device (API 34):
  - Home: no RAM Free; tune on/off gaps correct.
  - Games: demand badge 1.0x/1.3x gap visible, 360dp no overflow.
  - Overlay: START → home swipe → relaunch → STOP active ≤1s; notification dismiss → flips ≤2s + prefs cleared; STOP removes FGS.
  - Privacy: Settings → Privacy Policy → in-app LazyColumn renders §§1-8 (§ titles bold 17sp, bullets, body 13sp/18sp), scrollable, back→Settings, airplane mode works, no browser.

## Risks & Mitigations

- Image-2 mis-target → photo gate + grep; BLOCK if Settings match.
- `getRunningServices` throttled on 14+ → primary is `isRunning`+`canDrawOverlays`+prefs, fallback only.
- Prefs drift after external kill → poll clears drift.
- Asset dup desync → Gradle copy or manual cp pre-build.

## Acceptance Criteria

```gherkin
Scenario 1: Home no RAM Free, gap correct, build green
Scenario 2: SetupDialog pill gone (224-241+243), Settings LEGAL stays via in-app route
Scenario 3: Badge 10/5 padding, ≥2dp glyph-border at 1.0x/1.3x
Scenario 4a: HUD header "HUD OVERLAY", no "test"/"dummy" in HUD strings
Scenario 4b: START→close→reopen shows RUNNING, external kill flips + prefs cleared ≤2s, STOP kills FGS
Scenario 5: Settings→Privacy opens LazyColumn page (asset, §§1-8, 13sp/18sp body, 17sp headings, bullets), back→Settings, offline, no INTERNET perm
```

## Handoff — Reviewer Validates

- Image-2 primary = SetupDialog 224-241+243, Settings 227-255 guarded not deleted — agrees with grep/photo gate?
- Home hunk = 283-289 + 291 only, 280 kept — off-by-one fixed?
- Overlay dual-source + `apply()` + clear on all 3 exit paths + external-kill drift clear within poll?
- Privacy offline-first, no INTERNET, line-pass parser (`##`/`###`/`* `//body/blank), asset sync noted?
- MainScreen branch priority + dual `AnimatedVisibility` = `!showRamFree && !showTuneScreen && !showPrivacyPolicy`?
- Settings import kept deprecated, prefs keys `overlay_running`/`overlay_pkg` namespaced `apply()`?
