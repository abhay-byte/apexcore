# ApexCore Worker Plan — Fix `ALLOCATE & LAUNCH` Animation and Missing App Logo

**Repository:** `abhay-byte/apexcore`  
**Target branch:** `main` (create a feature branch before implementation)  
**Suggested repo destination:** `docs/plans/launch-animation-missing-logo-WORKER.md`  
**Priority:** P0 visual regression / launch UX  
**Scope:** Both launch entry points that use the shared launch ceremony:
- **Games → select game/app → `ALLOCATE & LAUNCH`**
- **All Apps → select app → `ALLOCATE & LAUNCH`**

## 0. Worker mission

Fix the shared ApexCore launch ceremony so that:

1. The selected app/game **logo is clearly visible during the launch ceremony**, including the `FREEZE` / `OPTIMIZED` state.
2. The hydraulic press animation visibly transitions into the launched app/game instead of the target activity appearing before Compose can render the opening (`PART`) animation.
3. The same behavior is reliable from both **Games** and **All Apps**.
4. Real freeze progress remains honest; do not invent progress.
5. The fix preserves predictive back, reduced-motion behavior, launch failure recovery, the one-launch-at-a-time guard, and overlay/HUD handoff.
6. The implementation remains smooth on mid-range Android devices and does not add main-thread package/icon loading.

This plan is based on a repository inspection of the current `main` branch and the supplied screenshot showing the closed press with `OPTIMIZED` but no selected app icon.

---

# 1. What is wrong now

## 1.1 User-visible regression

In the supplied screenshot, the hydraulic press has reached the center seam and displays:

`OPTIMIZED`

but the app/game logo is missing. The screen therefore looks like two blank plates with a status label instead of a launch ceremony tied to the item the user selected.

This is not merely an intermittent drawable-loading issue. The current overlay deliberately fades the icon away when the coordinator enters `FREEZE`.

## 1.2 Current shared architecture

The launch flow is already centralized, which is good:

- `ui/iron/games/GameLaunch.kt`
  - owns the launch state machine and timing
- `ui/iron/games/ShutterOverlay.kt`
  - owns plate/icon/seam animation and haptics
- `ui/iron/games/LaunchMatrix.kt`
  - contains cards/details and the `ALLOCATE & LAUNCH` buttons
- `ui/shell/MainScreen.kt`
  - creates `AppCardData`, wires `AsyncAppIcon`, owns `GamesViewModel`, and places `ShutterOverlay` in `IronShell`
- `ui/iron/AppIconCache.kt`
  - asynchronously loads application icons through `PackageManager`
- `games/GameLauncher.kt`
  - resolves and starts the target activity
- `ui/iron/shell/IronShell.kt`
  - provides the root overlay slot

Because Games and All Apps feed the same coordinator/overlay, **do not build two separate fixes**. Fix the shared ceremony once.

---

# 2. Root-cause analysis

## P0 — Root cause A: `FREEZE` explicitly fades the logo to zero

In the current `ShutterOverlay.kt`, the icon has a dedicated `iconAlpha` animation. When the phase changes to `FREEZE`, the code animates:

```kotlin
iconAlpha.animateTo(0f, tween(100))
```

The rendered alpha is effectively:

```kotlin
alpha = iconAppear.value * iconAlpha.value
```

Therefore, approximately 100 ms after entering `FREEZE`, the selected app icon is intentionally invisible.

The coordinator also holds the `FREEZE` phase for at least `MIN_FREEZE_MS = 320L`. As a result, the empty-looking press state is deterministic and lasts long enough to be obvious.

### Required fix

**Do not fade the selected logo out in `FREEZE`.**

Instead:

- keep the icon at full/near-full alpha during `FREEZE`;
- release the press squash so the logo becomes readable again;
- fade/scale the icon out only when the plates begin to part.

---

## P0 — Root cause B: launch intent fires before the overlay reaches `PART`

Current coordinator order in `GameLaunch.kt` is functionally:

```kotlin
val launched = launchIntent(app.pkg)
if (!launched) fail(...)
state = state.copy(phase = LaunchPhase.PART)
```

`launchIntent()` ultimately calls `startActivity()`.

Android can foreground the launched activity immediately. Once that happens, ApexCore may stop producing visible Compose frames before `state = PART` is rendered. The result is that the intended opening-press transition is invisible or nearly invisible.

This is especially important because `GameLauncher.fireIntent()` suppresses the normal activity transition with `applyPendingTransition(0, 0)`. The product is relying on its own ceremony to provide the visual handoff.

### Required fix

The overlay must enter the opening phase **before** `startActivity()` is called.

Preferred minimal approach:

```kotlin
state = state.copy(phase = LaunchPhase.PART)
delay(LaunchTiming.PART_LEAD_MS)  // e.g. 64–80 ms
val launched = launchIntent(app.pkg)
```

This gives Compose at least a few frames to visibly begin opening the press before Android covers ApexCore with the target activity.

### Recommended timing

Add:

```kotlin
const val PART_LEAD_MS = 72L
```

Use a range of roughly **64–80 ms** after device testing. Start with 72 ms.

Do **not** wait the full 280 ms before starting the activity. That would make the launch feel slow and would eliminate the desired “game/app surfaces through the opening press” effect.

---

## P1 — Root cause C: asynchronous icon load has a very short visibility window

`MainScreen.kt` supplies icons using `AsyncAppIcon(packageName)`.

`AsyncAppIcon` checks `AppIconCache`, then loads the icon asynchronously with `PackageManager` on `Dispatchers.IO`. That is the correct thread choice, but the current ceremony gives a cold icon only about:

- 160 ms WIND
- +100 ms PRESS
- then immediately starts fading it during FREEZE

So a cold/un-cached icon can show the fallback/placeholder during the only period in which it is allowed to be visible.

### Required fix

Two complementary changes:

1. **Keep the icon visible through FREEZE**, which greatly widens the time in which a cold load can complete.
2. **Warm the icon cache before the user presses launch** when an app/game detail becomes selected.

Do not perform synchronous `PackageManager.getApplicationIcon()` on the UI thread.

---

## P1 — Root cause D: seam rendering will visually slice through the restored icon

At present, the icon is composed before the seam. If the icon remains visible during `FREEZE`, the seam can be drawn over it.

### Required fix

Make the hierarchy explicit:

- plates: base
- seam/progress: above plates
- selected app logo: above seam
- status/failure text: topmost where appropriate

Use either:
- composition order; or
- `Modifier.zIndex(...)`

Recommended:
- seam `zIndex(1f)`
- icon `zIndex(2f)`
- failure/status overlay `zIndex(3f)`

Also move the icon slightly above the exact seam so the logo and readout do not fight for the same pixels.

Suggested visual offset:

```kotlin
.offset(y = (-28).dp)
```

Tune between `-20.dp` and `-32.dp` on phone layouts.

---

## P1 — Root cause E: full squash makes the logo effectively unreadable

The current PRESS behavior drives a strong vertical squash. That is good as a quick mechanical stamp, but if that squash value remains applied after PRESS, keeping alpha at 1.0 alone is insufficient.

### Required fix

At `FREEZE`, rebound the icon from the stamped/squashed state into a readable “locked in” state.

Example:

```kotlin
LaunchPhase.FREEZE -> {
    launch { squash.animateTo(0f, tween(110, easing = FastOutSlowInEasing)) }
    launch { iconAlpha.animateTo(1f, tween(80)) }
}
```

The press can stamp the icon for ~60–80 ms, then let it rebound while the plates remain closed.

The animation should read as:

**selected → stamped → verified/optimized → released into target app**

rather than:

**selected → stamped → disappears → blank waiting screen**

---

# 3. Target launch choreography

The original hydraulic-press concept should remain, but the logo lifetime changes.

| Relative time | Phase | Target visual | Logic |
|---:|---|---|---|
| 0 ms | `WIND` | Plates start closing. Selected logo is already available or begins appearing immediately. | none |
| 0–160 ms | `WIND` | Logo fades/scales from ~0.82 to 1.0 while plates close. | none |
| ~160 ms | `PRESS` | Plates meet. Short heavy-click. Logo compresses vertically for the stamp. | none |
| ~240–260 ms | `FREEZE` | Logo rebounds to readable shape and **remains visible**. Brass seam/ticks/status show real freeze result. | freeze completes / progress updates |
| empty target list | `FREEZE` | Logo remains visible; status says `OPTIMIZED`. No fake ticks. | no fabricated work |
| freeze done | `PART` | Plates immediately begin moving apart. Logo fades/scales out during first ~100–140 ms. | phase changes first |
| +72 ms from PART | `PART` | Opening motion is already visible. | `startActivity()` |
| +520 ms from PART | handoff | Target is foreground. | attach HUD rail when allowed |
| failure | `FAILED` | Plates return/stay closed; icon remains recognizable; failure stamp appears. | no launch/rail |

## Important product decision

The older concept said the icon should be gone by FREEZE. That is exactly what creates the current user complaint. For this fix, **the selected app/game identity must remain visible until PART begins**.

---

# 4. File-by-file implementation plan

## 4.1 `app/src/main/kotlin/com/ivarna/apexcore/ui/iron/games/ShutterOverlay.kt`

### Task A — stop fading logo during FREEZE

Find the `LaunchedEffect(state.phase)` phase switch.

Current problematic behavior conceptually:

```kotlin
LaunchPhase.FREEZE -> {
    launch { iconAlpha.animateTo(0f, tween(100)) }
    ...
}
```

Replace with behavior that restores and holds the logo:

```kotlin
LaunchPhase.FREEZE -> {
    launch {
        iconAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(80)
        )
    }
    launch {
        squash.animateTo(
            targetValue = 0f,
            animationSpec = tween(110, easing = FastOutSlowInEasing)
        )
    }

    // Keep any intended slight plate pressure-release effect,
    // but do not let it expose background gaps or jitter the seam.
}
```

### Task B — fade logo only during PART

Inside `LaunchPhase.PART`:

```kotlin
LaunchPhase.PART -> {
    if (reduced) {
        // existing reduced-motion treatment
    } else {
        launch {
            iconAlpha.animateTo(0f, tween(120))
        }
        launch {
            iconAppear.animateTo(0f, tween(140))
        }

        launch {
            plateTop.animateTo(0f, tween(280, easing = IronMotion.EaseWind))
        }
        delay(40)
        plateBottom.animateTo(0f, tween(240, easing = IronMotion.EaseWind))
    }
}
```

Do not wait for the icon fade before moving the plates. These animations must overlap.

### Task C — make the icon readable after the stamp

Avoid tying readable scale strictly to `state.phase == PRESS` if a phase switch can occur before the squash animation has reset.

Recommended render logic:

```kotlin
.graphicsLayer {
    val base = 0.82f + 0.18f * iconAppear.value
    val pressedY = 1f - 0.45f * squash.value

    scaleX = base
    scaleY = base * pressedY
    alpha = iconAppear.value * iconAlpha.value
}
```

The previous ~82% squash can visually reduce the icon almost to a line. Use a less destructive squash, roughly **40–55% compression**, and keep it short.

Target:
- PRESS minimum `scaleY` around 0.45–0.60
- FREEZE back to ~1.0
- PART fade+scale out

### Task D — fix z-order

Ensure logo draws above the seam:

```kotlin
Modifier
    .zIndex(2f)
    .offset(y = (-28).dp)
```

Give seam content a lower explicit z-index if needed.

Do not hide the progress/status behind the icon. Keep the text below the seam with enough spacing.

### Task E — add stable test tags / semantics

Add:

```kotlin
Modifier.testTag("launch_app_icon")
```

for the launch icon container.

Add a tag to the readout:

```kotlin
Modifier.testTag("launch_readout")
```

Optionally:
- `launch_press_top`
- `launch_press_bottom`
- `launch_seam`

This makes Compose UI tests deterministic.

### Task F — handle `IDLE` reset correctly

When returning to `IDLE`, reset:

- `iconAlpha` → 1f for next launch
- `iconAppear` → 0f
- `squash` → 0f
- plate values → open
- back scrub → 0f
- tick fill as appropriate

Be careful that a previous PART fade does not leave `iconAlpha = 0f` for the next ceremony.

Expected reset:

```kotlin
LaunchPhase.IDLE -> {
    ...
    iconAlpha.snapTo(1f)
    squash.snapTo(0f)
    backScrub = 0f
}
```

### Task G — failure treatment

If launch fails after PART has begun:
- transition to `FAILED`;
- immediately bring plates back to closed;
- restore `iconAlpha` to 1f;
- restore readable squash to 0f;
- show failure stamp.

This prevents a failure state containing an invisible logo.

---

## 4.2 `app/src/main/kotlin/com/ivarna/apexcore/ui/iron/games/GameLaunch.kt`

### Task A — add visible PART lead time

Add:

```kotlin
const val PART_LEAD_MS = 72L
```

### Task B — transition to PART before `startActivity()`

Change the ordering from:

```kotlin
launchIntent(...)
state = PART
```

to:

```kotlin
state = state.copy(phase = LaunchPhase.PART)

delay(LaunchTiming.PART_LEAD_MS)

val launched = try {
    launchIntent(app.pkg)
} catch (_: Exception) {
    false
}

if (!launched) {
    return fail("LAUNCH FAILED", "no launchable activity")
}
```

### Why this matters

A Compose phase mutation only helps visually if ApexCore stays foreground long enough to draw at least a few frames. A 72 ms lead is roughly 4 frames at 60 Hz and ~6 frames at 90 Hz.

That is enough to make the opening press unmistakably start without adding a perceptibly large delay.

### Task C — keep rail timing relative to PART, not intent fire

The HUD rail should still attach around 520 ms after PART begins.

If `PART_LEAD_MS = 72`:

```kotlin
delay(LaunchTiming.RAIL_ATTACH_MS - LaunchTiming.PART_LEAD_MS)
attachRail(app.pkg)
```

Do not accidentally make rail attach 592 ms after PART.

### Task D — ensure state cleanup cannot race a new launch

Keep the existing one-job/busy protection.

Audit final delays so `state = LaunchState()` happens only after:
- target intent has been fired;
- handoff timing has elapsed;
- failure state is not active.

### Task E — cancellation boundary

Predictive back should remain cancellable only in:
- `WIND`
- `PRESS`

Once `FREEZE` begins, the launch is committed.

Do not expand cancellation into `PART` unless the product intentionally supports aborting a partially-started target activity.

---

# 5. Make icon availability reliable

## 5.1 `app/src/main/kotlin/com/ivarna/apexcore/ui/iron/AppIconCache.kt`

Keep all actual icon loading off the main thread.

Add/confirm a small prefetch API such as:

```kotlin
suspend fun prefetch(
    pm: PackageManager,
    packageName: String
) {
    if (getIfCached(packageName) != null) return
    load(pm, packageName)
}
```

If the existing `load()` already updates the cache and is safe for concurrent calls, this can simply wrap it.

### Optional improvement: de-duplicate in-flight loads

If repeated visible rows can request the same package icon concurrently, maintain a package-keyed in-flight map so only one PackageManager load happens at a time.

This is useful but not required for the P0 fix.

---

## 5.2 `LaunchMatrix.kt` / selection owner

When the selected detail app/game changes, warm its icon.

Example pattern:

```kotlin
LaunchedEffect(selectedApp?.pkg) {
    val pkg = selectedApp?.pkg ?: return@LaunchedEffect
    withContext(Dispatchers.IO) {
        AppIconCache.prefetch(context.packageManager, pkg)
    }
}
```

Use the correct current selection state for both:
- Games detail
- All Apps detail

If both views share the same detail component, place the prefetch at that common level.

### Do not

- block `onClick` waiting for icon load;
- synchronously query `PackageManager` in a composable;
- convert the pure `GameLaunchCoordinator` into an Android drawable owner.

The coordinator should continue to carry logical app identity, not Android graphics objects.

---

# 6. `AppCardData` considerations

Current `AppCardData` carries:

```kotlin
val icon: @Composable () -> Unit
```

This is workable for the immediate fix.

Do not perform a broad data-model rewrite unless needed.

However, ensure every `AppCardData` used by Games and All Apps has:
- stable `pkg`
- stable `name`
- the same package-backed icon lambda used by the normal card/detail UI

The launch overlay must never receive a stripped copy of `AppCardData` whose `icon` lambda is `{}`.

Add a debug assertion or test fixture if this is easy to enforce.

---

# 7. `GameLauncher.kt` review

`GameLauncher.fireIntent(packageName)` currently resolves the target launch intent, calls `startActivity()`, and suppresses the system transition.

For this task, keep the zero system-transition behavior unless device testing shows a black/white flash.

The primary fix is the coordinator's PART-before-intent sequencing.

## Optional robust follow-up

If launch failures need cleaner animation semantics, split “can this package launch?” from “actually start it”:

```kotlin
fun canLaunch(packageName: String): Boolean
fun fireIntent(packageName: String): Boolean
```

Then the coordinator can:

1. validate target;
2. enter PART;
3. wait 72 ms;
4. call `fireIntent()`.

That avoids briefly opening the press and then snapping closed for a package with no launchable activity.

This is a follow-up improvement, not required to fix the screenshot regression.

---

# 8. Zero-freeze-target (`OPTIMIZED`) behavior

The screenshot is specifically the empty-target path.

Current user-visible problem:
- `FREEZE`
- no targets
- `OPTIMIZED`
- logo faded away
- mandatory dwell makes the blank state conspicuous

Required target:
- no fake tick row
- `OPTIMIZED` remains truthful
- selected logo remains visible
- short mechanical settle
- then PART starts

## Timing recommendation

Keep a minimum ceremony beat, but consider reducing the zero-target dwell separately.

For example:

```kotlin
const val MIN_FREEZE_MS = 320L
const val MIN_EMPTY_FREEZE_MS = 180L
```

Then:

```kotlin
val minHold = if (outcome.total == 0) {
    LaunchTiming.MIN_EMPTY_FREEZE_MS
} else {
    LaunchTiming.MIN_FREEZE_MS
}
```

This is optional for the first patch.

The P0 requirement is only that the logo no longer vanishes. After that is verified, tune the `OPTIMIZED` hold based on real-device feel.

---

# 9. Reduced motion

Reduced motion must still preserve app identity.

Target reduced-motion flow:

1. 200 ms dim
2. selected logo + `PREPARING` / real freeze status
3. freeze
4. `OPTIMIZED` or progress result
5. short ~80 ms visual handoff
6. launch

Do not use:
- plate travel
- squash
- scan sweep

But **do show the app/game logo**. Reduced motion is not “remove identity.”

---

# 10. Test plan

## 10.1 Unit tests — `GameLaunchCoordinatorTest`

Create:

`app/src/test/kotlin/com/ivarna/apexcore/ui/iron/games/GameLaunchCoordinatorTest.kt`

Use a test dispatcher/test scope; do not depend on wall-clock time.

### Test 1 — PART is entered before launch intent fires

Capture the coordinator state from inside the fake launch adapter:

```kotlin
@Test
fun `part phase is visible before launch intent fires`() = runTest {
    lateinit var coordinator: GameLaunchCoordinator
    var phaseAtIntent: LaunchPhase? = null

    coordinator = makeCoordinator(
        freeze = { _, _ -> FreezeOutcome.Ok(0, 0) },
        launchIntent = {
            phaseAtIntent = coordinator.state.phase
            true
        }
    )

    coordinator.launch(app)
    advanceUntilIdle()

    assertEquals(LaunchPhase.PART, phaseAtIntent)
}
```

This is the regression test for the invisible PART bug.

### Test 2 — launch lead is not skipped

Advance to just before `PART_LEAD_MS` and assert the fake intent has not fired.

Then advance past the lead and assert it fires once.

### Test 3 — blocked freeze never launches

Existing expected behavior:
- no target start
- failed state occurs
- resets after failure hold

### Test 4 — cancel during WIND prevents freeze/launch

Ensure both freeze and launch adapters remain untouched.

### Test 5 — zero targets is valid

`FreezeOutcome.Ok(0, 0)` must:
- not fail;
- reach PART;
- launch once.

### Test 6 — rail timing remains relative to PART

Record:
- PART transition time
- intent time
- rail call time

Assert rail occurs around `RAIL_ATTACH_MS` from PART start, not from the delayed intent.

### Test 7 — double launch ignored

Call `launch()` twice while the first sequence is active.

Assert only first package reaches adapters.

---

## 10.2 Compose/UI tests

Add a testable wrapper around `ShutterOverlay` if necessary.

### Test 8 — icon visible in FREEZE

Render:

```kotlin
LaunchState(
    phase = LaunchPhase.FREEZE,
    app = fakeApp,
    frozenCount = 0,
    totalTargets = 0
)
```

Assert:
- node `launch_app_icon` exists and is displayed;
- readout says `OPTIMIZED`.

This directly guards the screenshot regression.

### Test 9 — icon remains visible with progress

State:

```kotlin
frozenCount = 4
totalTargets = 10
```

Assert:
- icon shown;
- readout `FREEZING · 4 / 10`.

### Test 10 — PART eventually removes/fades icon

If alpha cannot be asserted reliably through semantics, use a screenshot/golden test or test the phase animation via a controlled clock.

### Test 11 — FAILED restores icon

Render a failed state after simulating PART and ensure the app identity is not permanently at alpha zero.

---

# 11. Manual QA matrix

Test on at least one 60 Hz and, if available, one 90/120 Hz Android device.

## Games path

- [ ] Open Games.
- [ ] Select a game.
- [ ] Confirm its icon is visible in the detail UI.
- [ ] Tap `ALLOCATE & LAUNCH`.
- [ ] Icon appears as plates close.
- [ ] Icon stamps/squashes briefly.
- [ ] Icon rebounds and remains clearly visible during FREEZE.
- [ ] With no freeze targets, `OPTIMIZED` appears while icon is still visible.
- [ ] Plates begin opening before target game covers ApexCore.
- [ ] No blank closed-press interval.
- [ ] No white/black activity-transition flash introduced.
- [ ] HUD rail attaches at expected time when enabled/permitted.

## All Apps path

Repeat all items above from All Apps → select app → `ALLOCATE & LAUNCH`.

## Slow icon load

- [ ] Clear/restart ApexCore so target icon is not warm in memory.
- [ ] Open target detail.
- [ ] Wait less than a second, launch.
- [ ] Logo is available by ceremony or becomes visible during FREEZE without a sudden late flash.
- [ ] No main-thread jank.

## Empty freeze target list

- [ ] `OPTIMIZED` shown.
- [ ] No fabricated tick progress.
- [ ] Logo remains visible.
- [ ] Transition proceeds without unnecessary long blank dwell.

## Real freeze targets

- [ ] Counts correspond to actual callbacks/results.
- [ ] Tick fill remains real.
- [ ] Logo remains visible while counts update.
- [ ] No frame loop outside FREEZE.

## Failure

- [ ] Try non-launchable/bad package test fixture.
- [ ] Failure stamp appears.
- [ ] Plates recover correctly.
- [ ] Icon is visible/restored.
- [ ] No HUD rail attaches.

## Predictive back

- [ ] Back during WIND scrubs/reverses.
- [ ] Back during PRESS cancels.
- [ ] Back after FREEZE does not cancel committed work.
- [ ] Next launch starts with a fully reset icon/plate state.

## Rotation / lifecycle

- [ ] Rotate during ceremony if activity configuration allows it.
- [ ] No permanently invisible icon after recreation.
- [ ] No second launch intent.
- [ ] No duplicate rail attach.

## Reduced motion

- [ ] No press/squash/scan.
- [ ] App/game logo still visible.
- [ ] Freeze status remains truthful.
- [ ] Launch still has a short visual handoff.

---

# 12. Performance requirements

Keep the existing design rule: per-frame animation reads should stay in draw/graphics phases rather than triggering unnecessary recomposition.

## Required

- `Animatable` values used for translation/scale/alpha should be read inside `graphicsLayer` where practical.
- Scan frame loop exists only while FREEZE and stops immediately afterward.
- No new bitmap conversion on every frame.
- No `PackageManager` icon fetch on main.
- No continuously running coroutine when `IDLE`.
- No per-frame object allocation added to seam/plate drawing.
- Do not duplicate the whole overlay for Apps vs Games.

## Target

- no visible hitch when plate starts;
- no stutter at PRESS/FREEZE transition;
- no blank frame before `startActivity()`;
- 60 fps on a representative mid-range device where the rest of the screen already meets budget.

---

# 13. Exact acceptance criteria

The patch is complete only when all of the following are true:

1. **The selected app/game logo is visible during `FREEZE`, including the exact `OPTIMIZED` state shown in the supplied screenshot.**
2. The logo does not get sliced unreadably by the seam.
3. The logo stamps briefly during PRESS, then returns to a readable shape.
4. The logo fades out during PART, not during FREEZE.
5. The coordinator enters `PART` before `startActivity()`.
6. There is a deliberate 64–80 ms PART lead (initial target 72 ms) so at least several opening frames can be seen.
7. Games and All Apps use the same corrected behavior.
8. A cold icon load is warmed on selection and never fetched synchronously on main.
9. `OPTIMIZED` remains truthful when there are zero freezable targets.
10. Freeze ticks/counts remain tied to real data.
11. Failure and predictive-back flows still work.
12. Reduced motion still shows app identity.
13. Unit regression test proves `phaseAtIntent == PART`.
14. Compose/UI regression test proves the launch icon is displayed in `FREEZE`.
15. Debug build and unit tests pass.

---

# 14. Implementation order for the worker

## Phase 1 — reproduce and lock the bug

1. Build current `main`.
2. Launch an item where freeze target count is zero.
3. Confirm screenshot behavior: closed press + `OPTIMIZED` + missing logo.
4. Add the coordinator ordering regression test.
5. Add the `FREEZE` icon-visible UI test or at minimum a test tag needed for it.

Do not alter timing before the baseline test fails for the expected reason.

## Phase 2 — P0 visual fix

1. Remove FREEZE fade-to-zero.
2. Rebound squash in FREEZE.
3. Move logo above seam.
4. Reset icon animation state safely in IDLE.
5. Fade icon in PART.

Run tests and manually check `OPTIMIZED`.

## Phase 3 — visible handoff fix

1. Add `PART_LEAD_MS = 72L`.
2. Set state to PART first.
3. Delay by PART lead.
4. Fire intent.
5. Correct rail delay arithmetic.
6. Test launch failure.

## Phase 4 — icon reliability

1. Add cache prefetch API if not already suitable.
2. Prefetch selected package when Games/All Apps detail selection changes.
3. Verify cold-start/cold-cache launch.

## Phase 5 — tune and verify

1. Test 60 Hz device.
2. Tune PART lead only if needed; stay inside 64–80 ms unless evidence justifies otherwise.
3. Consider reducing empty-target `OPTIMIZED` dwell after the functional bug is solved.
4. Capture before/after screen recording.
5. Run full debug/unit verification.

---

# 15. Suggested worker commits

Keep changes reviewable.

### Commit 1
`test(launch): cover PART ordering and FREEZE logo regression`

### Commit 2
`fix(launch): keep selected logo visible through freeze`

Contains:
- FREEZE alpha/squash correction
- z-order
- PART fade
- reset/failure restoration

### Commit 3
`fix(launch): begin press opening before target activity`

Contains:
- `PART_LEAD_MS`
- state ordering
- rail timing correction
- coordinator tests

### Commit 4
`perf(icons): prefetch selected app icon before launch`

Contains:
- cache prefetch
- selection warm-up
- cold-cache QA

Do not combine unrelated UI cleanup into these commits.

---

# 16. Verification commands

Use the repository's Gradle wrapper.

At minimum:

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

If the project has existing lint failures unrelated to this work, report them separately rather than hiding them.

If instrumentation infrastructure is available:

```bash
./gradlew :app:connectedDebugAndroidTest
```

Also install and verify on a physical device because the critical issue is animation/lifecycle timing around `startActivity()`.

---

# 17. Worker guardrails

## Do

- keep one shared launch coordinator/overlay;
- preserve real progress;
- preserve async/background icon loading;
- use the existing Iron design language;
- make the selected app identity persistent through FREEZE;
- give Compose visible PART frames before activity launch;
- add regression tests for both root causes.

## Do not

- fake freeze counts to make ticks move;
- keep the icon hidden because an older animation spec said it should disappear;
- start the target activity before entering PART;
- add an arbitrary 300–500 ms launch delay just to make the animation visible;
- synchronously load icons on main;
- duplicate launch logic for Games and All Apps;
- remove reduced-motion support;
- hide failures or silently launch when freeze is blocked;
- alter unrelated navigation, freezer logic, or HUD behavior.

---

# 18. Expected final visual read

The finished ceremony should feel like this:

**Tap `ALLOCATE & LAUNCH` → selected logo enters the press → press stamps it → logo rebounds and remains identifiable while ApexCore verifies/freezes the environment → `OPTIMIZED` or real progress appears → the press visibly begins opening → the selected app/game takes over through that opening.**

The crucial change is that the user never spends the center of the ceremony staring at a blank press without knowing what is being launched.

---

# 19. Final worker completion report format

When implementation is done, report:

```text
Launch animation fix — completion report

Root causes fixed:
- [x] Logo was faded to zero during FREEZE
- [x] Intent fired before PART could render
- [x] Cold icon path warmed before launch
- [x] Logo/seam z-order corrected

Files changed:
- ...
- ...

Tests added:
- ...
- ...

Verification:
- compileDebugKotlin:
- testDebugUnitTest:
- assembleDebug:
- physical-device QA:

Timing used:
- WIND:
- PRESS:
- PART_LEAD:
- PART:
- empty FREEZE minimum:

Known remaining issues:
- none / list

Before/after evidence:
- screen recording or screenshots
```

Do not call the work complete without testing the exact **`OPTIMIZED` + visible logo** state and the **visible PART-before-target-activity** transition.
