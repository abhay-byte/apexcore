# T10c — Full feature regression + Play compliance

| Field | Value |
|-------|-------|
| **ID** | T10c (part of T10) |
| **Parent** | [T10 ship readiness](T10-ship-readiness.md) |
| **Type** | verify + compliance |
| **Priority** | high |
| **Difficulty** | medium |
| **Branch** | `T10c-regression-play-compliance` (suggested) |
| **Status** | plan draft — awaiting approval |
| **Depends on** | **T10a** + **T10b** (features stable before ship audit) |
| **Unblocks** | Release / Play submit (`release` skill) |

## Todo source (proposed slice)

```yaml
- id: T10c
  title: Full feature regression + Google Play policy compliance
  type: feature
  priority: high
  difficulty: medium
  status: pending
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

## Research (this slice)

### CRITICAL-3 — Accessibility stub vs privacy

- Code: `AccessibilityFreezeBackend` returns `SKIPPED_A11Y` ("not implemented in T4")  
- `docs/privacy-policy.md` claims a11y automates Force Stop in Settings  
- Play **Deceptive Behavior / Behavior Transparency** risk  

**Default lock:** remove a11y from ready product path for v1.0 **or** never `isReady=true`; rewrite privacy; no a11y permission until real.

### MAJOR-6 — Exported FreezeReceiver

```xml
<receiver android:name=".freeze.FreezeReceiver" android:exported="true">
```

Any app can broadcast freeze-all. Fix: **`exported="false"`** for ship (or signature protect).

### MAJOR-7 — Permissions / declarations

| Item | Risk / action |
|------|----------------|
| `QUERY_ALL_PACKAGES` | Play declaration + core-use justification (games list + freeze targets) |
| `SYSTEM_ALERT_WINDOW` | In-app + listing disclosure |
| FGS `specialUse` overlay | Console declaration; match property subtype string |
| `FORCE_STOP_PACKAGES` | Privileged no-op for Play installs — remove or document |
| Privacy policy | Need **public HTTPS URL** + in-app link |
| Data safety form | Local-only: no collection (accurate) |
| targetSdk 36 + AAB | Already target 36; ship AAB not raw APK |

### Compliance hotspots (cleaner/optimizer)

| Policy | Exposure | Action |
|--------|----------|--------|
| Device & Network Abuse | force-stop / kill others | User-initiated only; no background auto-freeze |
| MUwS | cleaner category | Honest claims; no fake infection |
| Deceptive Behavior | free numbers, fake features, a11y | T10a/b fixes + this slice privacy/listing |
| Restricted permissions | QAP, SAO, FGS | Declarations + disclosure |
| Spam / min functionality | must work without Shizuku | T10a Standard path |
| Privacy / Data safety | local-only | Public policy + form |

### Code / docs map

| File | Role |
|------|------|
| `AndroidManifest.xml` | receiver export, permission cleanup |
| `freeze/AccessibilityFreezeBackend.kt` + resolver | ship path honesty |
| `freeze/FreezeReceiver.kt` | export policy |
| `docs/privacy-policy.md` | match code |
| `docs/problem-statement.md` | OOS update if whitelist shipped in T10b |
| `docs/Google_Play_Store_Policy_Compliance_Checklist.md` | source checklist |
| **NEW** `docs/review/compliance-T10.md` | PASS/FAIL evidence |
| Store assets | `docs/storelisting/` — claim audit |

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

### Out

- Implementing new freeze backends / overlay BOOST / pin UI (done in T10a/b)  
- Play Console actual upload (release skill after PASS)  
- Boot freeze, tags, accounts, Families program  
- Implementing full a11y automation (unless product reopens)  

---

## Workstream A — Full feature regression

Run after T10a + T10b merged (or on combined branch).

| # | Feature | Check | Pass |
|---|---------|-------|------|
| 1 | Home BOOST Standard | Limited mode honest; killBackground or clear copy | ☐ |
| 2 | Home BOOST Shizuku | Real force-stop; logcat backend=Shizuku | ☐ |
| 3 | Home BOOST Root | Real force-stop if device allows | ☐ |
| 4 | Backend dropdown | Ready flags; switch re-detects | ☐ |
| 5 | Games add/remove | T8 picker still works | ☐ |
| 6 | Game launch | Freeze then start + overlay starts | ☐ |
| 7 | Overlay BOOST | Real freeze; toast real numbers | ☐ |
| 8 | RAM Free | Δ matches adb MemAvailable ~noise | ☐ |
| 9 | Pin apps | Pinned survives all freeze entry points | ☐ |
| 10 | RAM Free cancel / pause | Safe release; no crash | ☐ |
| 11 | First-run no Shizuku | Setup dialog not lying about a11y | ☐ |
| 12 | Rotation Home / Games / RAM Free | No crash; state OK | ☐ |
| 13 | Build | `assembleDebug` + `assembleRelease` | ☐ |
| 14 | Unit tests | `./gradlew :app:testDebugUnitTest` | ☐ |

**Regression evidence:** logcat snippets + short notes in PR or `docs/review/regression-T10.md`.

---

## Workstream B — Play compliance pass

### B1. Code / manifest

- [ ] `FreezeReceiver` `android:exported="false"`  
- [ ] Accessibility: not ready / not in auto chain / privacy fixed  
- [ ] Remove or justify `FORCE_STOP_PACKAGES`  
- [ ] FGS overlay property subtype still present and accurate  
- [ ] No background freeze without user gesture  
- [ ] Queries / `QUERY_ALL_PACKAGES` only as needed  

### B2. Privacy & data

- [ ] Public HTTPS privacy URL (host TBD)  
- [ ] In-app link (Setup / About)  
- [ ] Policy text matches permissions actually used  
- [ ] No "a11y Force Stop automation" unless implemented  
- [ ] Data safety draft: no collection, no sharing, no encryption-in-transit of user data (N/A local)  

### B3. Claims & listing

- [ ] No guaranteed multi-GB free claims  
- [ ] Standard mode described as limited without Shizuku/Root  
- [ ] Screenshots not showing fake free numbers  
- [ ] Feature graphic / title within Play metadata rules  
- [ ] Overlay described as performance HUD + user-triggered boost  

### B4. Console prep (document only; no upload required in T10c)

- [ ] QUERY_ALL_PACKAGES declaration text prepared  
- [ ] SYSTEM_ALERT_WINDOW disclosure prepared  
- [ ] FGS specialUse justification = manifest property string  
- [ ] targetSdk 36 + AAB build command documented  
- [ ] Content rating / target audience notes (not for kids)  

### B5. Compliance artifact

Create `docs/review/compliance-T10.md`:

```markdown
# Compliance T10 — ApexCore
Date / build / commit
## Summary verdict: PASS | PASS_WITH_RISKS | FAIL
## Section results (from Play guide 0–1, 4, 5, 7, 8, 10)
| Section | Result | Evidence | Notes |
## Open risks accepted by owner
## Console-only remaining items
```

---

## Approach — Accessibility (Decision A default)

| Option | Action |
|--------|--------|
| **A (Recommended)** | Remove from ready candidates or hard `isReady=false`; rewrite privacy; no a11y service in manifest if unused |
| B | Implement minimal clear-recents (out of T10c unless reopened) |
| C | Keep stub; never ready; never claim in UI/privacy |

Default **A** or **C** — both ship-safe if privacy fixed. Prefer **A** clarity.

---

## Files to change

| Action | File |
|--------|------|
| **MODIFY** | `AndroidManifest.xml` |
| **MODIFY** | `freeze/FreezeReceiver.kt` (if needed) |
| **MODIFY** | `freeze/AccessibilityFreezeBackend.kt` / `FreezeBackendResolver.kt` |
| **MODIFY** | `docs/privacy-policy.md` |
| **MODIFY** | `docs/problem-statement.md` (OOS cleanup if needed) |
| **NEW** | `docs/review/compliance-T10.md` |
| **OPTIONAL NEW** | `docs/review/regression-T10.md` |
| **OPTIONAL** | in-app About / privacy link in Setup or MainActivity |

---

## Edge cases / ship risks

| Risk | Mitigation |
|------|------------|
| Reviewer has no Shizuku | Standard path must show real limited utility (T10a) |
| QUERY_ALL_PACKAGES rejection | Declaration + video/notes: game library + freeze targets |
| specialUse FGS rejection | Property string + Console justification aligned |
| Residual deceptive copy | Listing + in-app strings audit in B3 |
| Exported receiver abuse | exported=false |

---

## Test plan

1. Complete regression table (all ☐ → ☑) on physical device.  
2. `assembleRelease` succeeds.  
3. Privacy wording review vs code grep (`Accessibility`, `Force Stop`, network).  
4. Manifest review: exported components, permissions.  
5. Compliance doc verdict **PASS** or **PASS_WITH_RISKS** with owner sign-off.  

---

## Decisions (this slice)

| # | Decision | Lock |
|---|----------|------|
| 1 | FreezeReceiver | `exported=false` for ship |
| 2 | Accessibility | Not ship-ready; privacy honest |
| 3 | FORCE_STOP_PACKAGES | Remove if unused by non-system path |
| 4 | Compliance evidence | Required `docs/review/compliance-T10.md` |
| 5 | Play upload | **Out** of T10c — release skill after PASS |

---

## Out of scope

- New features (pin/overlay/backends) — T10a/b  
- Actual Play Console submit  
- Boot freeze, Families, ads, IAP  

---

## Implementation order

1. Manifest + receiver export fix  
2. Accessibility + privacy honesty  
3. Permission hygiene  
4. In-app privacy link  
5. Full regression matrix on device  
6. Listing/copy claim audit  
7. Write compliance + regression docs  
8. Owner sign-off → hand off to release  

---

## Iteration exit

- Regression matrix complete (all critical rows pass)  
- `compliance-T10.md` verdict not FAIL  
- Privacy matches code  
- No exported freeze broadcast hole  
- Ready for `release` skill / Play Console  

---

## Open questions

1. **Public privacy URL host?** GitHub Pages / custom domain / raw GitHub (prefer Pages/custom — raw can be fragile).  
2. **Accept residual Play risks?** e.g. cleaner category scrutiny with owner note.  
3. **Include demo video** for FGS / QAP declarations in this slice or release?  

---

## References

- Parent: `docs/plan/T10-ship-readiness.md`  
- Prev: `docs/plan/T10a-freeze-matrix-ram-stats.md`, `docs/plan/T10b-overlay-pin-apps.md`  
- `docs/privacy-policy.md`  
- `docs/Google_Play_Store_Policy_Compliance_Checklist.md`  
- Play guide (abhay-kb)  
- Store assets: `docs/storelisting/`  
)
