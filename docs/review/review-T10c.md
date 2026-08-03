# Review T10c — Full feature regression + Play compliance

| Field | Value |
|-------|-------|
| **Date** | 2026-08-03 |
| **Branch** | `T10c-regression-play-compliance` (base `b17c3f5` T10b) |
| **Plan** | `docs/plan/T10c-regression-play-compliance.md` |
| **Reviewer** | review agent (manual code + public URL + build re-verify) |
| **Worker iter** | 1 (+ review fix R6 Home privacy link) |
| **Verdict** | **REWORK** — not Approve |

> **Iter-2 note (2026-08-03):** **R1 CLOSED.** Owner directed: apexcore repo made public; `PRIVACY_POLICY_URL` retargeted to `https://github.com/abhay-byte/apexcore/blob/main/docs/privacy-policy.md`; honest Aug 3 policy merged to main (PR #6). Re-fetch 2026-08-03: HTTP 200, no a11y Force Stop claims, §3 "No Accessibility Service", Last Updated Aug 3. Compliance doc updated (4.1 → PASS; verdict PASS_WITH_RISKS for console + optional device rows only). Remaining: owner sign-off; optional R2 body cleanup; device matrix 1–12.

---

## Executive summary

T10c worker closed most **code/manifest/listing/repo-doc** compliance items correctly: exported receiver, no `FORCE_STOP_PACKAGES`, Accessibility non-product path, honest Setup (no a11y card), rewritten repo privacy, listing de-claims, compliance + regression artifacts, debug/release + unit tests green.

Two **ship-blocking honesty / discoverability** issues remain for Play:

1. **R1 (MAJOR):** Public privacy URL still serves **July 6** policy with **Accessibility Force Stop automation** claims. Repo policy is honest; **linked** policy is not.  
2. **R6 (MAJOR → fixed in review):** Privacy link was **Setup-only** (one-shot / SETUP path). Owner could not find it on Home. Review added always-on Home footer link; still needs device confirm.

Device regression matrix rows **1–12** not run this slice (rows 13–14 PASS). Owner sign-off pending. **Do not** hand off to `release` skill until R1 closed and compliance re-judged.

---

## Scope checked

| Stream | Checked? |
|--------|----------|
| B1 Code / manifest | ✅ |
| B2 Privacy (repo + public URL + in-app) | ✅ |
| B3 Listing / claims | ✅ |
| B4 Console prep docs | ✅ draft only |
| B5 Artifacts | ✅ present |
| Workstream A device matrix | ⚠️ CI rows only |
| Build + unit tests | ✅ re-run PASS |

**Not checked:** physical device BOOST / overlay / pin / RAM Free paths on this branch.

---

## What landed (PASS)

| Decision / item | Status | Evidence |
|-----------------|--------|----------|
| FreezeReceiver `exported=false` | ✅ | `AndroidManifest.xml` |
| Remove `FORCE_STOP_PACKAGES` | ✅ | Manifest; no code callers |
| Accessibility not ship-ready | ✅ | `isReady() = false`; not in resolver candidates; no a11y service in manifest; Setup card removed |
| Decision E freeze gate | ✅ (prior T10a) | Shizuku/Root only; Setup honest |
| Repo `docs/privacy-policy.md` | ✅ | 2026-08-03; no a11y; FGS/QAP/kill-bg; §3 No Accessibility |
| Listing + changelog | ✅ | a11y fallback removed |
| problem-statement OOS | ✅ | a11y + non-exported broadcast |
| freeze-api / freeze-architecture banners | ✅ partial | Top banners only (body R2) |
| In-app privacy link wiring | ⚠️→✅ R6 | Setup chip + **Home footer** (review) |
| `docs/review/compliance-T10.md` | ✅ draft | Verdict blocked on R1 |
| `docs/review/regression-T10.md` | ✅ partial | 13–14 ☑; 1–12 ☐ |
| assembleDebug / assembleRelease | ✅ | SUCCESS |
| Unit tests | ✅ | 30 tests, 0 failures |

---

## Findings

| Sev | ID | Finding | Status |
|-----|-----|---------|--------|
| **MAJOR** | **R1** | **Public privacy content stale.** `PRIVACY_POLICY_URL` = `https://github.com/abhay-byte/abhay-byte/blob/main/assets/apexcore-privacy-policy.md`. Live fetch still **Last Updated: July 6, 2026** with **§B Accessibility Service (Optional Fallback)** claiming Settings Force Stop automation. Repo file is correct. Play reviewers follow the **app/console URL**, not the repo path. Deceptive Behavior / Behavior Transparency risk. | **CLOSED iter-2 2026-08-03** — repo public; URL → apexcore `docs/privacy-policy.md` on main; curl-verified honest |
| **MAJOR** | **R6** | Privacy only in `SetupDialog` (first-run flag + SETUP). Owner: “I don’t see privacy policy link in app.” Play expects easy access. | **FIXED in review** (Home footer under Access Diagnostics). Device re-check. |
| **MINOR** | **R2** | `docs/freeze-architecture.md` body still T4-era (`exported=true`, FORCE_STOP, a11y product, Standard fallback). Banner only. Internal. | Optional |
| **MINOR** | **R3** | Device regression 1–12 not executed. | Owner / device session |
| **NIT** | **R4** | Dead `BoostManager`; `KILL_BACKGROUND` still declared (Fallback + privacy §F). | Non-blocking |
| **NIT** | **R5** | Worker compliance understated R1 as “URL not confirmed live”; URL was live, **content wrong**. | Fixed in review notes |

### R1 public excerpt (fetched 2026-08-03 — before fix)

```
### B. Accessibility Service (Optional Fallback)
* Purpose: Used as a secure fallback backend to automate clicking the
  "Force Stop" button in Android's system Settings app...
```

Must not ship while any in-app or Console privacy URL serves this. **Fixed
iter-2 2026-08-03:** see iter-2 note above; re-fetch proof in `compliance-T10.md` B2.

---

## Workstream checklist (reviewer view)

### B1 Code / manifest

- [x] FreezeReceiver exported=false  
- [x] Accessibility not ready / not auto chain  
- [x] FORCE_STOP_PACKAGES removed  
- [x] FGS specialUse property accurate  
- [x] No background freeze without gesture  
- [x] QAP only as needed  

### B2 Privacy

- [x] Public URL honest content ← **R1 closed iter-2 2026-08-03** (repo public, PR #6, curl-verified)
- [x] In-app link always reachable ← **R6 review**  
- [x] Repo policy matches permissions  
- [x] No a11y claim in repo/UI/listing  
- [x] Data safety draft local-only  

### B3 Listing

- [x] No multi-GB guarantees  
- [x] No Standard freeze theater  
- [x] Screenshots honest  
- [x] Title/metadata OK  
- [x] Overlay = HUD + user boost  
- [x] a11y removed from store copy  

### A Regression

- [x] 13 Build  
- [x] 14 Unit tests  
- [ ] 1–12 device  

---

## Verdict matrix

| Gate | Result |
|------|--------|
| Code compliance (manifest, a11y product path, export) | **PASS** |
| Repo docs honesty | **PASS** |
| Listing honesty | **PASS** |
| Public privacy honesty | **FAIL (R1)** → **PASS (closed iter-2 2026-08-03)** |
| In-app privacy discoverability | **PASS after R6** (device confirm) |
| Full device regression | **OPEN** |
| Owner sign-off | **OPEN** |
| **Slice exit / release handoff** | **BLOCKED** |

**Overall: REWORK**

---

## Residual order (worker / owner)

1. ~~**R1 required**~~ — **CLOSED iter-2 2026-08-03** (repo public; URL → apexcore `docs/privacy-policy.md` main; curl-proof in compliance B2).  
2. ~~Update `docs/review/compliance-T10.md` B2 / 4.1 / verdict after re-fetch~~ — **done iter-2**.  
3. Confirm R6 on device: Home scroll → PRIVACY POLICY opens browser.  
4. Device matrix 1–12 → `docs/review/regression-T10.md` (owner OK to defer if accepting risk; not a code FAIL).  
5. Optional R2 freeze-architecture body.  
6. Owner sign-off → release skill (upload out of T10c).

---

## Worker scorecard (iter 1)

| Dimension | /10 | Notes |
|-----------|-----|--------|
| Plan fidelity | 8 | Hit B1–B5 artifacts; skipped public host sync |
| Scope discipline | 9 | No feature creep |
| Play honesty | 5 | Repo/UI good; **public URL left deceptive** |
| Discoverability | 4 | Privacy Setup-only until owner/review catch |
| Evidence quality | 7 | Good compliance draft; understated R1 |
| Build/test hygiene | 9 | Green |
| **Overall** | **~72/100** | Strong code slice; weak “end-to-end honesty” (public surface) |

---

## References

- Plan: `docs/plan/T10c-regression-play-compliance.md`  
- Compliance: `docs/review/compliance-T10.md`  
- Regression: `docs/review/regression-T10.md`  
- Repo privacy: `docs/privacy-policy.md`  
- Public (stale): `https://github.com/abhay-byte/abhay-byte/blob/main/assets/apexcore-privacy-policy.md`  
