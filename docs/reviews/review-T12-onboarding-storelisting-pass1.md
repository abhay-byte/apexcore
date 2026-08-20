# Plan Review — T12-onboarding-storelisting-plan.md

| Field | Value |
|---|---|
| **Pass** | 1 |
| **Iteration** | 1 |
| **Verdict** | APPROVE |
| **Date** | 2026-08-20 |

---

## Findings Summary

CRITICAL: 0  
MAJOR: 1  
MINOR: 2  
SUGGESTIONS: 1

---

## Findings

### [MAJOR] `phoneScreenshots/` sync missing from current `generate_playstore_images.py`

**Location:** `docs/storelisting/generate_playstore_images.py` `main()` / plan §3 Workstream C step 5

**Problem:** Plan acceptance criterion states Worker must sync renamed frames to both `docs/storelisting/phoneScreenshots/` (1–8) and `fastlane/metadata/android/en-US/images/phoneScreenshots/` (1–8). The current script does **not** write to either `phoneScreenshots/` dir — it only copies `featureGraphic.png` to fastlane. The existing `phoneScreenshots/` files use the old naming scheme (`1_home.png`, `2_games.png`, …) not the new `1_hero.png`…`8_cta.png` scheme. Worker must add explicit copy logic for both dirs after the main frame loop; the plan says this but gives no concrete code or pseudocode for the Worker to follow. Without it the acceptance criterion silently fails — Worker could skip it.

**Evidence:**
- Script `main()` L472–501: only `featureGraphic.png` is fastlane-synced; zero `phoneScreenshots/` writes.
- Existing files in `docs/storelisting/phoneScreenshots/`: `1_home.png … 8_settings_privacy.png` (old names).
- Plan §3.C.5 lists the copy target but gives no implementation pointer.

**Impact:** Fastlane metadata screenshots stay stale with old naming; CI/CD upload would push wrong images.

**Required planner change:** Add explicit step: "After frame loop, copy `OUT/1_hero.png…8_cta.png` → `OUT/phoneScreenshots/1.png…8.png` AND `fastlane/…/phoneScreenshots/1.png…8.png`; clear old files first." Or specify the exact naming convention (numbered `1.png`–`8.png` is Play Store canonical).

---

### [MINOR] Plan line-number references for image removal are off by one

**Location:** Plan §Workstream B step 2 ("L529–L540")

**Problem:** Plan says remove the `Box`+`Image` at "L529–L540". Actual code: `Box` starts at L529, `Image` closes at L539, post-box `Spacer` is L542. Plan also says "Remove the extra `Spacer(modifier = Modifier.height(16.dp))` below the deleted image" — that `Spacer` is at **L542**, not L540. The first `Spacer(Modifier.height(6.dp))` at L526 is the pre-image spacer. Plan says to replace top padding with `Spacer(Modifier.height(20.dp))` but doesn't clarify it means the L526 one. Worker needs exact line context or risks deleting the wrong spacer and breaking layout.

**Evidence:** `OnboardingScreen.kt` L526–L543 read directly.

**Impact:** Low risk of build break but high risk of layout regression if wrong spacer removed or wrong replacement value applied.

**Required planner change:** Clarify: remove L529–L540 (`Box`+`Image`), remove L542 (`Spacer` 16dp), change L526 `Spacer` from 6dp → 20dp (or whatever target).

---

### [MINOR] Frame 8 references `10_backend_dropdown.png` which does not exist in `organic_capture/`

**Location:** Plan §3.C.3 Frame 8 storyboard / `docs/storelisting/organic_capture/`

**Problem:** Plan storyboard for frame 8 (`8_cta.png`) lists screenshot source as `06c_settings_privacy.png` + `10_backend_dropdown.png`. `10_backend_dropdown.png` **exists** in `organic_capture/` — confirmed by glob. However the existing `SHOT_MAP` in `generate_playstore_images.py` has no entry for `backend_dropdown`. Worker must add this key or frame 8 duo-phones layout will `FileNotFoundError`.

**Evidence:**
- `organic_capture/10_backend_dropdown.png` — file exists.
- `SHOT_MAP` in script: no `backend_dropdown` key present.
- Plan §3.C.3 frame 8 lists it as a screenshot source for duo staggered layout.

**Impact:** Script crashes at frame 8 unless `SHOT_MAP` is extended.

**Required planner change:** Add note: "Add `'backend_dropdown': ['10_backend_dropdown.png']` to `SHOT_MAP` and map frame 8 `SHOTS` to `[('privacy', ''), ('backend_dropdown', '')]`."

---

### [SUGGESTION] T12 device verification is planner-scoped but plan marks it as Worker action

**Location:** Plan §Workstream A / Acceptance Criteria item 1

**Problem:** "T12 Real Game Optimisation verified on device" is in acceptance criteria but the task constraints say this reviewer does not touch devices. The implementation (all 12 tune files exist, 79 test files exist, `T12-real-game-optimisation-results.md` declares PR 6 complete with all KDs PASS) strongly suggests T12 is done in code. Plan treats device verification as a Worker code-audit step, not a manual tester step. Worker cannot verify on device.

**Impact:** Worker will correctly confirm code-level completeness but cannot satisfy the device-test AC line. Should be re-routed to Manual Tester agent or scoped to "run unit tests locally."

**Required planner change:** Split AC item 1: "(a) Unit tests 79/79 passing — Worker verifies via `./gradlew test`; (b) On-device smoke — Manual Tester."

---

## Evidence Summary

| Claim | Verified |
|---|---|
| `ElevationSetupPage` Box+Image exists at L529–L539 | ✅ confirmed |
| `generate_playstore_images.py` does not write to `phoneScreenshots/` | ✅ confirmed |
| `10_backend_dropdown.png` exists in `organic_capture/` | ✅ confirmed |
| `SHOT_MAP` lacks `backend_dropdown` key | ✅ confirmed |
| All 12 tune `.kt` files exist | ✅ confirmed |
| 47 tune test files exist | ✅ confirmed |
| `T12-real-game-optimisation-results.md` status: PR 6 complete, all KDs PASS | ✅ confirmed |
| `HomeScreen.kt` has `Game optimisation` entry row behind `isElevatedBackend` gate | ✅ confirmed |
| `deleteDummyKeysIfNeeded()` called from `HomeScreen` | ✅ confirmed |
