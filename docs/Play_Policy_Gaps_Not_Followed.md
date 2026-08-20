# Google Play Policy — Gaps ApexCore Does **Not** Follow

| Field | Value |
|-------|-------|
| **Date** | 2026-08-03 |
| **App** | ApexCore (`com.ivarna.apexcore`) |
| **Policy source** | [abhay-kb Google Play Store Policy Compliance Guide](https://github.com/abhay-byte/abhay-kb/blob/main/Google_Play_Store_Policy_Compliance_Guide.md) (compiled July 25, 2026) |
| **Local mirror** | `docs/Google_Play_Store_Policy_Compliance_Checklist.md` |
| **Audit method** | Manifest, `app/` code, `docs/privacy-policy.md`, fastlane listing, store assets, prior `docs/review/compliance-T10.md` |
| **Verdict** | **NOT READY FOR PLAY SUBMISSION** — code honesty + console work remaining |

This file lists **only** checklist items the project does **not** fully satisfy.  
Items that already pass (no restricted content, no ads/billing, targetSdk 36, 64-bit libs, no INTERNET, no a11y service, AAB build present, etc.) are omitted here; see `docs/review/compliance-T10.md` for prior passes.

---

## Executive summary

| Priority | Count | Theme |
|----------|------:|-------|
| **P0 — Ship-blocking (code)** | 1 | Dummy Home card gone, but Tune Display/Focus Settings apply is still stubbed (no-op ON). See T12 rev 5. |
| **P1 — Listing honesty** | 2 | Stale store screenshots; thin listing copy |
| **P1 — Privacy policy quality** | 3 | Developer identity, contact, retention language |
| **P2 — Play Console (not done)** | 10+ | Data safety, IARC, QAP, FGS, Financial Features, etc. |
| **P2 — Account / process** | 3 | Closed testing, identity verify, Android Developer Verification |
| **Residual risk (accepted)** | 2 | Cleaner force-stop scrutiny; QAP review |

---

## P0 — Ship-blocking code gaps

### 1. §5.1 Deceptive Behavior — Misleading claims (dummy Game Optimisation)

**Status:** OPEN (T12 landed incomplete — do not close until PR 6 + PR 5)

- Home `dummy_opt_*` four-switch card is **deleted** (not migrated). Catalog IDs match [`docs/plans/T12-tune-options.md`](plans/T12-tune-options.md).
- **Still deceptive:** `FOCUS_HEADSUP`, `FOCUS_IMMERSIVE`, `DISPLAY_PEAK`, and `DISPLAY_MIUI` light as available and return apply success without writing Settings (`TuneApplier` stubs). Same P0 class as the old dummies.
- Capability probe is not auto-started from Home, so the row can show “None on this kernel” after a cold start even when nodes exist.
- Fastlane `full_description.txt` L9 still says “tune game options”. Overlay-less restore is unproven.
- Close this item only after T12 **PR 6** (real Settings apply, mutex/restore, auto-probe) and **PR 5** (listing rewrite + draw-over-denied restore proof). Spec: [`docs/plans/T12-real-game-optimisation.md`](plans/T12-real-game-optimisation.md) rev 5; status: [`docs/plans/T12-real-game-optimisation-results.md`](plans/T12-real-game-optimisation-results.md).

---

## P1 — Store listing honesty (§7.1 Metadata)

### 2. Screenshots do not match current UI

**Guide rule:** Listing metadata/screenshots must accurately represent the app (no misleading assets).

**Evidence**

- `docs/storelisting/README.md` states phone screenshots still show the **pre–Zen Organic (cryo/tech)** UI and must be recaptured before ship.
- Fastlane assets: `fastlane/metadata/android/en-US/images/phoneScreenshots/` (same generation).
- Current app UI is Zen Organic (see device captures under `docs/review/zen-screenshots/` and root `home.png` / `home_scrolled.png`).

**Required fix**

- Replace all Play phone screenshots (and feature graphic if cryo-branded) with current Zen UI captures.
- Promote `docs/brand/featureGraphic_zen.png` / logo assets if they are the intended brand.

**Status:** ❌ Does not follow (listing assets stale)

### 3. Full description incomplete / under-describes product

**Guide rule:** Metadata should not mislead; descriptions should match real functionality.

**Evidence**

`fastlane/metadata/android/en-US/full_description.txt` (~454 chars) only mentions:

- Freeze via Shizuku/Root  
- Performance HUD  

It omits real features: RAM Free, Games library, Pin Apps, and (currently) does not disclose elevation requirements for deep freeze as clearly as the in-app setup banner does. Thin listings are not always rejected, but combined with stale screenshots this weakens honesty.

**Required fix**

- Expand full description to match real features and elevation requirements.
- Do **not** describe Game Optimisation until it is real (see P0).

**Status:** ⚠️ Partial — incomplete vs product surface

---

## P1 — Privacy Policy quality (§4.1)

Public URL works (HTTP 200):  
`https://github.com/abhay-byte/apexcore/blob/main/docs/privacy-policy.md`  
In-app links exist (Settings → Privacy Policy; Setup dialog chip).

Still **not fully meeting** the guide’s privacy checklist:

### 4. Developer / entity name must match store listing

**Guide:** Privacy policy must name the developer/entity that matches the store listing.

**Gap:** Policy speaks as “ApexCore” only. Package namespace is `com.ivarna.apexcore`; developer account name on Play is not stated in the policy. Legal entity / publisher name is missing.

**Status:** ❌ Incomplete

### 5. Contact method too weak

**Guide:** Disclose a contact method.

**Gap:** Policy says only “reach out via our GitHub repository” — no email, no specific issues URL, no maintainer identity. Prefer a stable email and/or full issues URL.

**Status:** ❌ Incomplete

### 6. Retention / deletion practices not spelled out

**Guide:** Disclose retention/deletion policy (and security practices at a usable level).

**Gap:** Local prefs are mentioned, but there is no explicit retention, how to clear data (uninstall / clear app storage), or “no server-side deletion needed because no accounts / no cloud data” statement.

**Status:** ⚠️ Partial

### Note — Home privacy footer regression (informational)

Earlier T10c R6 put “PRIVACY POLICY” on Home. Current `HomeScreen` no longer includes it (Zen redesign). Settings still has a Legal → Privacy Policy entry, which can satisfy “linked/shown in-app,” but Home is no longer the always-visible footer. **Not scored as FAIL** if Settings remains one tap from bottom nav; optional improvement: restore Home footer.

---

## P2 — Play Console requirements not completed (§1, §4.2, §4.6, §7.2, §11, §13)

These are **not implemented in Console** (code may be ready). Until done, pre-submission checkmarks are unchecked.

| # | Guide item | Status | Action |
|---|------------|--------|--------|
| 7 | **Data safety form** completed and accurate (§4.2 / §1) | ❌ Not done | File: no collection, no sharing, no encryption-in-transit needed (no network); align with privacy URL |
| 8 | **Privacy policy URL** entered in Play Console (§4.1 / §1) | ❌ Not done | Paste public HTTPS URL above |
| 9 | **IARC content rating** questionnaire (§7.2 / §1) | ❌ Not done | Complete truthfully (utility / no UGC / no gambling) |
| 10 | **Target audience** declaration (§1 / §9) | ❌ Not done | Declare **not for children** (18+ or 13+ as appropriate); do **not** opt into Families |
| 11 | **Financial Features declaration** (§13 — mandatory even if none) | ❌ Not done | Declare no financial features; incomplete form blocks updates |
| 12 | **`QUERY_ALL_PACKAGES` declaration** (§4.6) | ❌ Not done | Draft: *“ApexCore lists installed apps solely to build the games library and to identify background apps the user chooses to freeze. No package data leaves the device.”* Demo: `docs/storelisting/` |
| 13 | **`SYSTEM_ALERT_WINDOW` disclosure** (§4.6) | ❌ Not done | Disclose HUD overlay purpose in Console policy forms |
| 14 | **FGS `specialUse` justification** (§4.7 FGS) | ❌ Not done | Use exact manifest property string: *“Draggable performance HUD to monitor game stats (FPS, RAM, CPU) and trigger memory boost during gameplay.”* |
| 15 | **Play App Signing** + upload **AAB** (§1 / §11.4) | ⚠️ AAB built locally; Console not confirmed | `app/build/outputs/bundle/release/app-release.aab` exists; enroll Play App Signing and upload AAB (not raw APK as primary) |
| 16 | **Closed testing** 12 testers × 14 days (§1 / §11.3) | ❌ Unknown / likely not done | Required if **personal** account created after 2023-11-13; org accounts exempt |
| 17 | **Developer identity verification** (§1 / §11.1) | ❌ Unknown | Personal gov ID or org D-U-N-S as required |
| 18 | **Android Developer Verification** (§1 / §11.2 / §13) | ❌ Not done | Register identity + package + signing key; enforcement from **Sept 2026** in select regions |
| 19 | **Account deletion** web + in-app (§1 / §4.5) | ✅ N/A if no accounts | Confirm Data safety / App content: no account creation → no deletion URL required |

---

## Residual policy risks (not hard checklist fails, but review exposure)

### R-A. Device & Network Abuse / MUwS — force-stop of other apps

**Guide:** Apps must not interfere with other apps’ normal operation; MUwS forbids harming usability/trust.

**Product reality:** Core feature uses Shizuku/Root `am force-stop` (and `KILL_BACKGROUND_PROCESSES` best-effort) on **user gesture** only. Receiver is `exported="false"`. No background auto-freeze. Honest “elevation required” copy when blocked.

**Risk:** Cleaner/optimizer category is heavily scrutinized. Mitigation is honesty + user initiation + no fake infection UI — **not a guarantee of approval**.

**Status:** Mitigated in code; residual Play review risk

### R-B. `QUERY_ALL_PACKAGES` approval risk

Even with justification, Play may reject QAP if reviewers believe scoped `<queries>` would suffice. Keep demo video and core-use narrative ready.

---

## What already follows (do not rework for these)

For orientation only — **not** gaps:

| Area | Result |
|------|--------|
| §3 Restricted content | N/A — no CSAM, sexual, hate, gambling, health, crypto, AI gen, UGC |
| §4.4 Selling data | No collection / no sale |
| §4.5 Account deletion | No accounts |
| §4.7 External code / self-update / hostile download | No INTERNET; no dynamic dex; no APK install |
| §4.8 Fake infection / Play Protect disable | Not present |
| §5.2 Impersonation | No false brand affiliation found |
| §6 Ads / payments / subscriptions | No ads or IAP SDKs |
| §8 Min functionality (core) | Real utility: freeze (elevated), RAM Free, HUD, games library — **except** dummy toggles harm honesty |
| §9 Families | Not targeting children (declare in Console) |
| §11.4 targetSdk | **36** (meets Aug 31, 2026 floor) |
| §11.4 64-bit | Native `.so` includes `arm64-v8a` |
| §11.4 Icon / feature graphic sizes | Icon 512×512 PNG ≤1MB; feature graphic 1024×500 |
| Accessibility freeze | Hard-disabled stub; not in product path; privacy §3 honest |
| Privacy URL public + in-app | Settings + Setup; public GitHub blob 200 |

---

## Recommended close-out order

1. **Remove or honestly label** dummy Game Optimisation toggles (P0).  
2. **Refresh** Play screenshots + feature graphic to Zen UI (P1).  
3. **Harden** `docs/privacy-policy.md` (developer name, contact email/URL, retention/clear-data) and keep public URL in sync (P1).  
4. **Expand** full description without claiming unfinished features (P1).  
5. Complete **Play Console** forms (data safety, IARC, audience, QAP, SAO, FGS, financial features, privacy URL) (P2).  
6. Run **closed test** if personal account; enroll **Play App Signing**; upload **AAB**.  
7. Register **Android Developer Verification** before Sept 2026 regional enforcement.  
8. Re-run this gap list; only ship when P0–P1 closed and P2 Console rows are checked.

---

## Traceability

| Evidence path | Used for |
|---------------|----------|
| `app/src/main/AndroidManifest.xml` | Permissions, FGS specialUse, exported components |
| `app/src/main/kotlin/.../ui/home/HomeScreen.kt` | Dummy optimisation toggles |
| `app/src/main/kotlin/.../SetupDialog.kt` | `PRIVACY_POLICY_URL` |
| `docs/privacy-policy.md` | Privacy content gaps |
| `fastlane/metadata/android/en-US/*` | Title, short/full description, screenshots |
| `docs/storelisting/README.md` | Explicit stale-screenshot warning |
| `app/build.gradle.kts` | targetSdk 36 |
| `app/build/outputs/bundle/release/app-release.aab` | AAB present locally |
| `docs/review/compliance-T10.md` | Prior PASS_WITH_RISKS (console + residual) |

---

*Internal compliance gap list for ApexCore. Not legal advice. Re-check live [Policy Center](https://support.google.com/googleplay/android-developer/topic/9858052) before submission.*
