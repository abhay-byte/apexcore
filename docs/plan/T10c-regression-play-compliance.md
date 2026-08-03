# T10c — Full feature regression + Play compliance

| Field | Value |
|-------|-------|
| **ID** | T10c (part of T10) |
| **Parent** | [T10 ship readiness](T10-ship-readiness.md) |
| **Type** | verify + compliance |
| **Priority** | high |
| **Difficulty** | medium |
| **Branch** | `T10c-regression-play-compliance` |
| **Status** | **R1 CLOSED 2026-08-03 (iter-2)** — repo public; public privacy URL serves honest Aug 3 policy (PR #6 merged; curl-verified). Remaining = device matrix rows 1–12 (optional) + owner sign-off |
| **Depends on** | **T10a** + **T10b** (features stable before ship audit) |
| **Unblocks** | Release / Play submit (`release` skill) |

## Todo source (proposed slice)

```yaml
- id: T10c
  title: Full feature regression + Google Play policy compliance
  type: feature
  priority: high
  difficulty: medium
  status: rework
  why: |
    Features mostly done; need first full end-to-end regression and Play policy pass
    (permissions, FGS, claims honesty, privacy, exported components) before ship.
  really_needed: yes
  impact: Manifest, privacy policy, FreezeReceiver, compliance doc, listing claims, a11y honesty
  followups: Play Console upload / release skill
  plan: docs/plan/T10c-regression-play-compliance.md
```

---

## Goal

1. **First full feature regression** across Home / Games / Overlay / RAM Free / backends / pin.  
2. **Play compliance** against policy guide + local checklist — ship-honest, no deceptive claims.  
3. Produce **evidence doc** for release: `docs/review/compliance-T10.md`.

---

## Review (2026-08-03) — worker iteration 1

Reviewer re-ran build/tests, grepped product claims, opened the **public** privacy URL linked from Setup.

### Verdict: **REWORK** (not Approve)

Code/manifest/listing/repo docs are largely right. **One ship-blocking honesty hole remains:** the in-app privacy link points at a hosted file that still claims Accessibility Force Stop automation. Until that is fixed (or the in-app URL points at the honest policy), T10c cannot claim compliance PASS.

### What landed (PASS)

| Item | Evidence |
|------|----------|
| `FreezeReceiver` `exported="false"` | `AndroidManifest.xml` receiver |
| `FORCE_STOP_PACKAGES` removed | Manifest diff; unused by code |
| Accessibility not product path | `isReady()` hard `false`; not in `FreezeBackendResolver` candidates; no a11y service in manifest |
| Setup UI honesty | Accessibility card removed; Root full-width; "PRIVACY POLICY" chip |
| Repo privacy rewrite | `docs/privacy-policy.md` 2026-08-03: no a11y claim; FGS / QAP / kill-bg sections; §3 explicit no a11y |
| Listing / changelog | `full_description.txt` + `changelogs/1.txt` drop accessibility fallback |
| Problem statement OOS | A11y + external FREEZE broadcast listed as OOS |
| Freeze API banner | `docs/freeze-api.md` T10c status note |
| Compliance artifact | `docs/review/compliance-T10.md` — verdict **PASS_WITH_RISKS** (needs update for URL content) |
| Regression artifact | `docs/review/regression-T10.md` — rows 13–14 ☑; 1–12 ☐ device |
| Build + unit tests | Re-verified: `assembleDebug` + `assembleRelease` SUCCESS; **30** unit tests, **0** failures |

### Findings

| Severity | ID | Finding | Action |
|----------|-----|---------|--------|
| **MAJOR** | R1 | **Public privacy URL is live but still OLD.** In-app `PRIVACY_POLICY_URL` → `https://github.com/abhay-byte/abhay-byte/blob/main/assets/apexcore-privacy-policy.md`. Fetched 2026-08-03: **Last Updated July 6, 2026** still has **"### B. Accessibility Service (Optional Fallback)"** claiming Force Stop automation in Settings. Repo `docs/privacy-policy.md` is fixed; hosted asset is not. Play reviewers open the **linked** policy — Deceptive Behavior / Behavior Transparency risk. | **CLOSED (iter-2 2026-08-03):** apexcore repo made public; in-app URL retargeted to `https://github.com/abhay-byte/apexcore/blob/main/docs/privacy-policy.md`; honest policy merged to main (PR #6); curl-verified no a11y claims. |
| **MAJOR** | R6 | **In-app privacy link not discoverable.** Worker only put "PRIVACY POLICY" inside `SetupDialog`, which auto-shows once (`setup_shown_v1`) when backend is null, or when user opens SETUP. Owner could not find it in normal Home use. Play User Data expects an easily reachable in-app link, not a one-shot modal. | **Fixed (review iter):** underline "PRIVACY POLICY" at bottom of Home (below Access Diagnostics); keep chip in SetupDialog; `openPrivacyPolicy()` public. Re-verify on device. |
| **MINOR** | R2 | `docs/freeze-architecture.md` body still documents a11y product path, `exported="true"`, `FORCE_STOP_PACKAGES`, Standard fallback. Only a top T10c banner was added. Internal, not store-facing, but confuses ship truth. | Optional same-slice: patch exported + a11y sections, or mark body as historical-only more strongly. |
| **MINOR** | R3 | Device regression rows **1–12** still open (no device session this slice). Rows 13–14 done. | Owner / device pass; fill `docs/review/regression-T10.md`. |
| **NIT** | R4 | `BoostManager` dead code still present; `KILL_BACKGROUND_PROCESSES` kept (also used by non-product `FallbackFreezeBackend`). Privacy §F documents it honestly. | Leave or delete dead `BoostManager` later; not blocking. |
| **NIT** | R5 | Compliance doc said privacy URL "not yet confirmed live" — it **is** live; problem is **content stale**, not reachability. | Fix when rewriting compliance after R1. |

### Not findings (confirmed good)

- No INTERNET permission; freeze only on user gesture (BOOST / launch / overlay).
- FGS `specialUse` + property subtype string present and accurate.
- Resolver still Shizuku/Root only (Decision E).
- Store listing screenshots under `docs/storelisting/` have no a11y copy.
- Demo videos for FGS/QAP already under `docs/storelisting/` (`apexcore_fgs_demo.mp4`, etc.) — available for console later.

---

## Residual work (before Approve / exit)

1. ~~**[MAJOR R1]**~~ **CLOSED 2026-08-03** — repo public; URL retargeted; honest policy on main (PR #6); curl-proof in compliance doc.  
2. **[MAJOR R6]** Always-reachable Home privacy link — **code fixed in review**; owner re-check on device (scroll Home past diagnostics).  
3. **[MINOR R3]** Run device matrix rows 1–12; tick `docs/review/regression-T10.md` (optional — owner may defer).  
4. ~~Update `docs/review/compliance-T10.md`: R1 closed, re-fetch evidence~~ — **done iter-2**; owner sign-off row remains.  
5. Optional R2 freeze-architecture body cleanup.  
6. Owner accepts open Play risks (cleaner category, QAP, privacy host).

---

## Research (this slice)

### CRITICAL-3 — Accessibility stub vs privacy

- Code: `AccessibilityFreezeBackend` returns `SKIPPED_A11Y` ("not implemented in T4")  
- `docs/privacy-policy.md` claims a11y automates Force Stop in Settings  
- Play **Deceptive Behavior / Behavior Transparency** risk  

**Default lock:** remove a11y from ready product path for v1.0 **or** never `isReady=true`; rewrite privacy; no a11y permission until real.

**Status 2026-08-03:** Code + repo privacy + listing ✅. **Public hosted policy ❌ (R1)** — same CRITICAL residual until public URL matches.

### MAJOR-6 — Exported FreezeReceiver

```xml
<receiver android:name=".freeze.FreezeReceiver" android:exported="true">
```

Any app can broadcast freeze-all. Fix: **`exported="false"`** for ship (or signature protect).

**Status:** ✅ done (`exported="false"`).

### MAJOR-7 — Permissions / declarations

| Item | Risk / action | Status |
|------|----------------|--------|
| `QUERY_ALL_PACKAGES` | Play declaration + core-use justification (games list + freeze targets) | Code OK; console text drafted in compliance B4 |
| `SYSTEM_ALERT_WINDOW` | In-app + listing disclosure | Code OK; console still |
| FGS `specialUse` overlay | Console declaration; match property subtype string | Manifest OK |
| `FORCE_STOP_PACKAGES` | Privileged no-op for Play installs — remove or document | ✅ removed |
| Privacy policy | Need **public HTTPS URL** + in-app link | In-app link ✅; **public content ❌ R1** |
| Data safety form | Local-only: no collection (accurate) | Draft OK; console |
| targetSdk 36 + AAB | Already target 36; ship AAB not raw APK | APK builds OK; AAB = release skill |

### Compliance hotspots (cleaner/optimizer)

| Policy | Exposure | Action | Status |
|--------|----------|--------|--------|
| Device & Network Abuse | force-stop / kill others | User-initiated only; no background auto-freeze | ✅ code |
| MUwS | cleaner category | Honest claims; no fake infection | ✅ code/listing |
| Deceptive Behavior | free numbers, fake features, a11y | T10a/b + this slice | ⚠️ public privacy R1 |
| Restricted permissions | QAP, SAO, FGS | Declarations + disclosure | Code OK; console open |
| Spam / min functionality | must work without Shizuku | RAM Free + HUD without elevation (Decision E) | ✅ code; device recheck |
| Privacy / Data safety | local-only | Public policy + form | ⚠️ public policy R1 |

### Code / docs map

| File | Role | Status |
|------|------|--------|
| `AndroidManifest.xml` | receiver export, permission cleanup | ✅ |
| `freeze/AccessibilityFreezeBackend.kt` + resolver | ship path honesty | ✅ |
| `freeze/FreezeReceiver.kt` | export policy | unchanged body; manifest fixed |
| `SetupDialog.kt` | a11y card out; privacy chip | ✅ |
| `docs/privacy-policy.md` | match code | ✅ repo |
| Hosted privacy asset (abhay-byte assets) | public URL | ❌ R1 |
| `docs/problem-statement.md` | OOS cleanup | ✅ |
| `docs/Google_Play_Store_Policy_Compliance_Checklist.md` | source checklist | reference |
| **NEW** `docs/review/compliance-T10.md` | PASS/FAIL evidence | ✅ drafted (update after R1) |
| **NEW** `docs/review/regression-T10.md` | device matrix | partial (13–14 only) |
| Store assets | `docs/storelisting/` — claim audit | listing copy ✅ |

Policy guide (external):  
https://raw.githubusercontent.com/abhay-byte/abhay-kb/refs/heads/main/Google_Play_Store_Policy_Compliance_Guide.md

---

## Scope

### In

- Full regression matrix (table below) on device  
- Privacy policy rewrite for a11y / permissions truth  
- FreezeReceiver `exported=false`  
- Accessibility product decision implementation (default: not ship-ready)  
- Manifest permission hygiene (`FORCE_STOP_PACKAGES` decision)  
- In-app privacy link if missing  
- Listing/copy audit vs real capability  
- `docs/review/compliance-T10.md`  
- **Public privacy URL content must match repo** (R1 residual)

### Out

- Implementing new freeze backends / overlay BOOST / pin UI (done in T10a/b)  
- Play Console actual upload (release skill after PASS)  
- Boot freeze, tags, accounts, Families program  
- Implementing full a11y automation (unless product reopens)  

---

## Workstream A — Full feature regression

Run after T10a + T10b merged (or on combined branch).  
Evidence file: `docs/review/regression-T10.md`.

| # | Feature | Check | Pass |
|---|---------|-------|------|
| 1 | Home BOOST Standard | Limited mode honest; killBackground or clear copy | ☐ device |
| 2 | Home BOOST Shizuku | Real force-stop; logcat backend=Shizuku | ☐ device |
| 3 | Home BOOST Root | Real force-stop if device allows | ☐ device |
| 4 | Backend dropdown | Ready flags; switch re-detects | ☐ device |
| 5 | Games add/remove | T8 picker still works | ☐ device |
| 6 | Game launch | Freeze then start + overlay starts | ☐ device |
| 7 | Overlay BOOST | Real freeze; toast real numbers | ☐ device |
| 8 | RAM Free | Δ matches adb MemAvailable ~noise | ☐ device |
| 9 | Pin apps | Pinned survives all freeze entry points | ☐ device |
| 10 | RAM Free cancel / pause | Safe release; no crash | ☐ device |
| 11 | First-run no Shizuku | Setup dialog honest (no a11y card; privacy chip) | ☐ device |
| 12 | Rotation Home / Games / RAM Free | No crash; state OK | ☐ device |
| 13 | Build | `assembleDebug` + `assembleRelease` | ☑ 2026-08-03 |
| 14 | Unit tests | `./gradlew :app:testDebugUnitTest` (30 / 0 fail) | ☑ 2026-08-03 |

**Regression evidence:** logcat snippets + short notes in PR or `docs/review/regression-T10.md`.

---

## Workstream B — Play compliance pass

### B1. Code / manifest

- [x] `FreezeReceiver` `android:exported="false"`  
- [x] Accessibility: not ready / not in auto chain / repo privacy fixed  
- [x] Remove or justify `FORCE_STOP_PACKAGES`  
- [x] FGS overlay property subtype still present and accurate  
- [x] No background freeze without user gesture  
- [x] Queries / `QUERY_ALL_PACKAGES` only as needed  

### B2. Privacy & data

- [x] **Public HTTPS privacy URL serves honest text** ← **R1 CLOSED 2026-08-03**: apexcore repo public; `PRIVACY_POLICY_URL` → `https://github.com/abhay-byte/apexcore/blob/main/docs/privacy-policy.md`; PR #6 merged to main; curl-verified (no a11y Force Stop, §3 No Accessibility, Last Updated Aug 3)  
- [x] In-app link — Setup chip (worker) + **Home footer always-on (R6 review fix)**  
- [x] Repo policy text matches permissions actually used  
- [x] No "a11y Force Stop automation" in repo / listing / UI  
- [x] Data safety draft: no collection, no sharing, no encryption-in-transit of user data (N/A local)  

### B3. Claims & listing

- [x] No guaranteed multi-GB free claims  
- [x] Freeze gated without Shizuku/Root (Decision E; setup honest)  
- [x] Screenshots not showing fake free numbers (storelisting assets)  
- [x] Feature graphic / title within Play metadata rules  
- [x] Overlay described as performance HUD + user-triggered boost  
- [x] Listing + changelog: accessibility fallback removed  

### B4. Console prep (document only; no upload required in T10c)

- [x] QUERY_ALL_PACKAGES declaration text prepared (in compliance-T10 §B4)  
- [x] SYSTEM_ALERT_WINDOW disclosure prepared (console checklist)  
- [x] FGS specialUse justification = manifest property string (documented)  
- [ ] targetSdk 36 + AAB build command documented (`bundleRelease` still release skill)  
- [x] Content rating / target audience notes (not for kids) in compliance  

### B5. Compliance artifact

- [x] Created `docs/review/compliance-T10.md` (worker: PASS_WITH_RISKS)  
- [ ] Update after R1 fix + owner sign-off  

---

## Approach — Accessibility (Decision A default)

| Option | Action | Status |
|--------|--------|--------|
| **A (Recommended)** | Remove from ready candidates or hard `isReady=false`; rewrite privacy; no a11y service in manifest if unused | **Chosen** — code + repo privacy done; public URL lagging |
| B | Implement minimal clear-recents (out of T10c unless reopened) | out |
| C | Keep stub; never ready; never claim in UI/privacy | subset of A |

Default **A** or **C** — both ship-safe if privacy fixed. Prefer **A** clarity. **Ship gate includes public policy host.**

---

## Files to change

| Action | File | Status |
|--------|------|--------|
| **MODIFY** | `AndroidManifest.xml` | ✅ |
| **MODIFY** | `freeze/FreezeReceiver.kt` (if needed) | n/a (manifest only) |
| **MODIFY** | `freeze/AccessibilityFreezeBackend.kt` / `FreezeBackendResolver.kt` | ✅ |
| **MODIFY** | `SetupDialog.kt` (+ privacy link) | ✅ |
| **MODIFY** | `docs/privacy-policy.md` | ✅ |
| **MODIFY** | hosted `apexcore-privacy-policy.md` (abhay-byte assets) **or** URL retarget | ❌ R1 |
| **MODIFY** | `docs/problem-statement.md` | ✅ |
| **MODIFY** | fastlane listing + changelog | ✅ |
| **NEW** | `docs/review/compliance-T10.md` | ✅ draft |
| **NEW** | `docs/review/regression-T10.md` | ✅ partial |
| **OPTIONAL** | `docs/freeze-architecture.md` body | banner only (R2) |

---

## Edge cases / ship risks

| Risk | Mitigation | Status |
|------|------------|--------|
| Reviewer has no Shizuku | Non-freeze utility (RAM Free, HUD, games lib); honest setup | code ✅; device ☐ |
| QUERY_ALL_PACKAGES rejection | Declaration + video under `docs/storelisting/` | console |
| specialUse FGS rejection | Property string + Console justification aligned | code ✅ |
| Residual deceptive copy | Listing + in-app + **public privacy** audit | **R1 open** |
| Exported receiver abuse | exported=false | ✅ |

---

## Test plan

1. Complete regression table (all ☐ → ☑) on physical device.  
2. `assembleRelease` succeeds. ✅  
3. Privacy wording review vs code grep (`Accessibility`, `Force Stop`, network). ✅ repo; ❌ public URL  
4. Manifest review: exported components, permissions. ✅  
5. Compliance doc verdict **PASS** or **PASS_WITH_RISKS** with owner sign-off. ⏳ after R1  

---

## Decisions (this slice)

| # | Decision | Lock | Status |
|---|----------|------|--------|
| 1 | FreezeReceiver | `exported=false` for ship | ✅ |
| 2 | Accessibility | Not ship-ready; privacy honest (**incl. public URL**) | code ✅; public ❌ |
| 3 | FORCE_STOP_PACKAGES | Remove if unused by non-system path | ✅ removed |
| 4 | Compliance evidence | Required `docs/review/compliance-T10.md` | ✅ draft |
| 5 | Play upload | **Out** of T10c — release skill after PASS | — |

---

## Out of scope

- New features (pin/overlay/backends) — T10a/b  
- Actual Play Console submit  
- Boot freeze, Families, ads, IAP  

---

## Implementation order

1. ~~Manifest + receiver export fix~~  
2. ~~Accessibility + privacy honesty (repo + UI + listing)~~  
3. ~~Permission hygiene~~  
4. ~~In-app privacy link~~  
5. **Sync public privacy host (R1)** ← next  
6. Full regression matrix on device (rows 1–12)  
7. ~~Listing/copy claim audit~~  
8. Update compliance + regression docs after R1 / device  
9. Owner sign-off → hand off to release  

---

## Iteration exit

- [x] **R1 closed:** public privacy URL content matches ship honesty (no a11y Force Stop claim) — curl-verified 2026-08-03
- [ ] Regression matrix critical rows pass (device 1–12 optional; build/tests already ☑)
- [ ] `compliance-T10.md` verdict not FAIL; owner signed — verdict now PASS_WITH_RISKS (console + device rows only)
- [x] Repo privacy matches code
- [x] No exported freeze broadcast hole
- Ready for `release` skill / Play Console **only after** above

---

## Open questions

1. **Public privacy URL host?** **RESOLVED (iter-2):** `https://github.com/abhay-byte/apexcore/blob/main/docs/privacy-policy.md` (repo public). GitHub Pages/custom domain optional stability upgrade before Console submission.
2. **Accept residual Play risks?** e.g. cleaner category scrutiny with owner note.
3. **Include demo video** for FGS / QAP declarations — assets already in `docs/storelisting/`; attach at release/console time.

---

## References

- Parent: `docs/plan/T10-ship-readiness.md`  
- Prev: `docs/plan/T10a-freeze-matrix-ram-stats.md`, `docs/plan/T10b-overlay-pin-apps.md`  
- Evidence: `docs/review/compliance-T10.md`, `docs/review/regression-T10.md`  
- Full review: `docs/review/review-T10c.md`  
- `docs/privacy-policy.md`  
- `docs/Google_Play_Store_Policy_Compliance_Checklist.md`  
- Play guide (abhay-kb)  
- Store assets: `docs/storelisting/`  

---

## Worker prompt (iter-2 rework) — copy below

```
You are the worker agent on ApexCore branch `T10c-regression-play-compliance`.

Read first (do not skip):
- docs/plan/T10c-regression-play-compliance.md  (status REWORK; residual R1 required)
- docs/review/review-T10c.md                   (full review verdict)
- docs/review/compliance-T10.md
- docs/review/regression-T10.md
- docs/privacy-policy.md                       (source of truth for honest policy text)

## Goal
Close **MAJOR R1** so the privacy policy users/Play open is ship-honest. Then update compliance evidence. Do **not** implement new features. Do **not** Play Console upload. Do **not** commit unless the owner asks.

## Context (already done — do not redo)
- FreezeReceiver exported=false; FORCE_STOP_PACKAGES removed
- Accessibility isReady=false, not in resolver, no a11y service, Setup a11y card gone
- Repo privacy rewritten; listing/changelog de-claim a11y
- Home always-on "PRIVACY POLICY" footer (R6) + Setup chip — keep both
- assembleDebug/Release + 30 unit tests already green on this branch
- R6 code is already in MainActivity; only device-confirm, do not remove

## Required work

### 1. MAJOR R1 — public privacy content (blocking)

Problem: In-app `PRIVACY_POLICY_URL` in SetupDialog.kt points to:
  https://github.com/abhay-byte/abhay-byte/blob/main/assets/apexcore-privacy-policy.md
Live content is still July 6 2026 and claims Accessibility Force Stop automation.
Repo `docs/privacy-policy.md` (Aug 3 2026) is correct.

Do ONE of these (prefer A if you have write access to abhay-byte/abhay-byte):

**A (preferred):** Update the hosted file
  `assets/apexcore-privacy-policy.md` in repo `abhay-byte/abhay-byte`
  so it matches apexcore `docs/privacy-policy.md` content (same honesty:
  no Accessibility Force Stop claim; include No Accessibility Service section;
  FGS / package visibility / kill-bg as in repo). Push so raw/blob serves new text.

**B (if no access to that repo):** Retarget `PRIVACY_POLICY_URL` in
  `app/src/main/kotlin/com/ivarna/apexcore/SetupDialog.kt` to a public HTTPS
  URL that already serves the honest policy. Prefer:
  - GitHub Pages or raw.githubusercontent.com for a repo you can update, OR
  - raw link to apexcore privacy if published publicly with correct content.
  Do not use a URL that 404s or still shows a11y Force Stop.

After A or B:
- `curl -sL <final-url-or-raw>` and confirm:
  - NO "Accessibility Service (Optional Fallback)"
  - NO "automate clicking the Force Stop button"
  - HAS "No Accessibility" / "does not declare" a11y (or equivalent honest text)
  - Last Updated is current / post-T10c
- Paste re-fetch evidence into docs/review/compliance-T10.md (B2 + section 4.1).

### 2. Update compliance + plan status

- docs/review/compliance-T10.md:
  - Mark R1 closed with fetch date + URL used
  - Section 4.1 → PASS (or PASS_WITH_RISKS if host is still blob-fragile)
  - Summary: PASS_WITH_RISKS only for console + optional device rows — not for public a11y lie
  - Keep open risks: QAP, cleaner category, device matrix 1–12 if not run
- docs/plan/T10c-regression-play-compliance.md:
  - Status: residual R1 closed; remaining = device matrix optional + owner sign-off
  - Check B2 public URL item [x]
- docs/review/review-T10c.md: short iter-2 note that R1 closed (or still open if blocked)

### 3. Optional (same PR if quick)

- R2: Fix freeze-architecture.md body lines that still say exported=true /
  FORCE_STOP_PACKAGES / a11y as product path (align with T10c banner + ship reality).
  Do not rewrite the whole history doc.

### 4. Explicitly OUT of scope

- Device matrix rows 1–12 (owner may run; if no device, leave ☐ and note)
- Play Console upload / AAB signing / release skill
- Implementing Accessibility freeze
- New product features (pin/overlay/backends)
- Committing .kotlin/errors/*.log
- git commit / PR unless owner requests

### 5. Verify

- ./gradlew :app:assembleDebug :app:testDebugUnitTest  (must stay green)
- Grep product claims: no a11y Force Stop in app strings, fastlane, docs/privacy-policy.md
- Confirm PRIVACY_POLICY_URL constant matches the honest host
- Confirm MainActivity still has Home "PRIVACY POLICY" footer calling openPrivacyPolicy

### 6. Stop condition

Done when:
1. Public privacy URL content is honest (curl proof in compliance doc)
2. Compliance 4.1 no longer FAIL for content
3. Build/tests green
4. Plan status updated

Then stop and report: what you chose (A vs B), final URL, curl proof snippet, files changed.
If you cannot update any public host, STOP and report blocked on R1 with what the owner must do (push asset / provide Pages URL) — do not fake PASS.
```
