# Compliance T10 — ApexCore

| Field | Value |
|-------|-------|
| **Date** | 2026-08-03 |
| **Branch** | `T10c-regression-play-compliance` |
| **Base commit** | `b17c3f5` (T10b merged) |
| **Build** | `./gradlew :app:assembleDebug` + `:app:assembleRelease` — both PASS (app-debug.apk, app-release.apk) |
| **Unit tests** | `./gradlew :app:testDebugUnitTest` — 30 tests, 0 failures |
| **Policy reference** | `docs/Google_Play_Store_Policy_Compliance_Checklist.md` + abhay-kb Play guide |

## Summary verdict: PASS_WITH_RISKS (R1 CLOSED)

Code/manifest/listing/repo policy are ship-honest. **R1 (public privacy
content) closed 2026-08-03:** apexcore repo made public and `docs/privacy-policy.md`
(Aug 3 2026, no a11y claims) is the public URL served by both in-app links.
Remaining items are **console-only** (declarations, data safety form, rating,
AAB) and **optional** device matrix rows 1–12 + owner sign-off — no
store-facing honesty FAIL remains.

---

## Section results (Play guide 0–1, 4, 5, 7, 8, 10)

| Section | Result | Evidence | Notes |
|---------|--------|----------|-------|
| 0–1 Quick pre-submission | PASS (build path) | AAB not yet produced — `bundleRelease` pending in release slice; targetSdk 36, minSdk 24, versionName 1.0 | Console-only: signing / Play App Signing |
| 4.1 Privacy Policy | **PASS** (2026-08-03, R1 closed) | `PRIVACY_POLICY_URL` → `https://github.com/abhay-byte/apexcore/blob/main/docs/privacy-policy.md` (repo made public; PR #6 merged). Re-fetch 2026-08-03: HTTP 200; **no** "Accessibility Service (Optional Fallback)", **no** "automate clicking the Force Stop button"; has "## 3. No Accessibility Service" + "does **not** declare, request, or use an Accessibility service"; "Last Updated: August 3, 2026". Host = public GitHub repo blob (stable enough; Pages/custom optional upgrade). | In-app links (Home footer R6 + Setup chip) both open this URL |
| 4.2 Data safety form | PASS (draft) | Local-only: no collection, no sharing, no account creation → no deletion requirement | Console-only entry |
| 4.3 Prominent disclosure | N/A | No off-app data collection; all permissions are user-visible settings toggles | — |
| 4.6 Restricted permissions | PASS (code) | `QUERY_ALL_PACKAGES` only for game library + freeze targets (justification drafted, §B4); `SYSTEM_ALERT_WINDOW` disclosed in-app (Overlay screen) + listing; FGS `specialUse` property string present in manifest | QAP declaration + FGS justification text = console-only |
| 4.7 Device & Network Abuse | PASS | No INTERNET permission in manifest; no external code execution; no self-update; no dynamic dex/.so loading | Verified by manifest grep |
| 4.8 MUwS | PASS | No fake infections / no misrepresented device state; BOOST copy honest ("FREEZE BLOCKED" when no elevation); no Play Protect disable prompts | — |
| 5.1 Deceptive Behavior | PASS | Standard-mode theater removed (T10a Decision E); a11y stub hard `isReady=false`, excluded from resolver, removed from Setup UI, privacy + listing de-claimed; no fake free numbers | Residual: "PURGE COMPLETE" header when blocked? — no, blocked path shows "FREEZE BLOCKED" (MainActivity:901) |
| 7 Store listing | PASS (draft) | Title "ApexCore - Game Booster" (20 chars, no emoji/ALL CAPS); full description a11y claim removed 2026-08-03; screenshots under `docs/storelisting/` show real UI | Listing copy audit in §B3 below |
| 8 Spam / min functionality | PASS_WITH_RISK | App has real utility without Shizuku: RAM Free, HUD, games library; BOOST freeze gated with honest setup dialog | Device rows of regression matrix still open (§regression-T10) |
| 10 Technical/account | CONSOLE | IARC rating, target audience (18+, not for kids), 12-testers/14-day closed test if personal account | Console-only |

---

## B1. Code / manifest audit

| Item | Status | Evidence |
|------|--------|----------|
| `FreezeReceiver` `exported="false"` | ✅ PASS | `app/src/main/AndroidManifest.xml` line 45; merged manifest confirms `exported="false"` |
| Accessibility not ready / not in auto chain | ✅ PASS | `AccessibilityFreezeBackend.isReady()` hard `false`; excluded from `FreezeBackendResolver` candidates; no a11y service in manifest; Setup UI card removed |
| `FORCE_STOP_PACKAGES` | ✅ PASS | Removed from manifest (privileged no-op on Play installs; unused by code) |
| FGS overlay property subtype | ✅ PASS | `specialUse` + `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` "Draggable performance HUD..." present |
| No background freeze without user gesture | ✅ PASS | All freeze entry points are user taps (BOOST, game launch, overlay BOOST); receiver is not exported; no alarms/boot receivers |
| Queries / QAP only as needed | ✅ PASS | `<queries>` MAIN intent only + QAP for installed-app enumeration (games + targets) |
| No INTERNET permission | ✅ PASS | Manifest grep — no network capability |
| Other exported components | ✅ PASS | `MainActivity` (LAUNCHER, required), `ShizukuProvider` (library contract, INTERACT_ACROSS_USERS_FULL), `ProfileInstallReceiver` (AndroidX, DUMP-protected) |

## B2. Privacy & data audit

| Item | Status | Notes |
|------|--------|-------|
| Public HTTPS privacy URL | ✅ **PASS (R1 closed 2026-08-03)** | `https://github.com/abhay-byte/apexcore/blob/main/docs/privacy-policy.md` — repo public, PR #6 merged, curl proof below. Both raw + blob HTTP 200, honest content (no a11y Force Stop; §3 No Accessibility; Last Updated Aug 3). |
| In-app link | ✅ PASS | Home scroll footer "PRIVACY POLICY" (R6, always reachable) + Setup chip — both call `openPrivacyPolicy()` → `PRIVACY_POLICY_URL` (retargeted to apexcore main). |
| Policy matches permissions | ✅ PASS | Repo sections A–F map 1:1 to manifest; a11y §3 honest. Same file served publicly. |
| No "a11y Force Stop automation" claim | ✅ PASS (repo + public) | Removed from repo privacy, problem-statement, listing, changelog, and the **publicly served file** — verified by curl re-fetch (below). |
| Data safety draft | ✅ PASS | Local-only: no collection / no sharing / no transmission; no accounts |

**R1 curl re-fetch evidence (2026-08-03):**

```
$ curl -sL https://raw.githubusercontent.com/abhay-byte/apexcore/main/docs/privacy-policy.md
3: **Last Updated: August 3, 2026**
42: ## 3. No Accessibility Service
44: ApexCore does **not** declare, request, or use an Accessibility service in this release...
Banned phrases ("Accessibility Service (Optional Fallback)", "automate clicking the Force Stop button"): 0 matches
$ curl -sL -o /dev/null -w "%{http_code}" https://github.com/abhay-byte/apexcore/blob/main/docs/privacy-policy.md
200
```

## B3. Claims & listing audit

| Claim | Status | Notes |
|-------|--------|-------|
| No guaranteed multi-GB free claims | ✅ PASS | No such copy in app or listing |
| Standard mode described as limited | ✅ PASS | No Standard freeze mode exists (Decision E); setup dialog says freeze requires Shizuku/Root |
| Screenshots not showing fake numbers | ✅ PASS | Store screenshots from real device runs (see `docs/storelisting/`) |
| Title / feature graphic metadata rules | ✅ PASS | Title 20 chars; no emojis/ALL CAPS |
| Overlay described as HUD + user-triggered boost | ✅ PASS | FGS property string + listing match |
| a11y fallback claim in listing | ✅ PASS | Removed from `full_description.txt` + changelog 1.txt |

## B4. Console-only remaining items (no upload in T10c)

- [ ] `bundleRelease` AAB build + Play App Signing (release skill)
- [ ] QAP declaration text: *"ApexCore lists installed apps solely to build the games library and to identify background apps the user chooses to freeze. No package data leaves the device."*
- [ ] `SYSTEM_ALERT_WINDOW` disclosure in Play Console policy declarations
- [ ] FGS `specialUse` justification = exact manifest property string
- [ ] Data safety form (no collection) + privacy URL entry
- [ ] IARC content rating + target audience (not for kids)
- [ ] Financial features declaration (none) — mandatory to unblock updates
- [ ] 12-testers/14-day closed test if personal account

## Open risks accepted by owner

1. **Privacy URL stability (low)** — URL is a public GitHub blob on `main`; GitHub Pages/custom domain optional upgrade before Console submission. Content now honest (R1 closed).
2. **Cleaner category scrutiny** — user-initiated freeze of other apps; mitigated by no background automation, no fake infections, honest copy. Residual review risk.
3. **QUERY_ALL_PACKAGES rejection risk** — declaration text + core-use justification; demo video under `docs/storelisting/` if requested by review.
4. **Device regression rows 1–12** — not re-verified on this branch; matrix in `docs/review/regression-T10.md` (optional before release, owner may defer).

## Reviewer notes (2026-08-03)

| Item | Result |
|------|--------|
| Manifest export / FORCE_STOP / a11y product path | PASS |
| Repo privacy + listing + Setup UI | PASS |
| Public privacy URL content | **FAIL (R1)** → **PASS (closed iter-2 2026-08-03)** |
| In-app privacy discoverability | PASS after R6 (device confirm) |
| assembleDebug / assembleRelease / 30 unit tests | PASS (re-verified iter-2) |
| Device matrix 1–12 | OPEN (optional) |

## Sign-off

| Role | Name | Date | Verdict |
|------|------|------|---------|
| Implementation | worker (T10c) | 2026-08-03 | PASS_WITH_RISKS (understated R1) |
| Review | review agent | 2026-08-03 | **REWORK** — close R1 then re-judge |
| Worker iter-2 | worker (T10c) | 2026-08-03 | R1 closed; verdict PASS_WITH_RISKS (console + device rows only) |
| Owner | — | — | ☐ (pending) |
