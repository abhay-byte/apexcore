# T10a — Freeze matrix + RAM Free stats validation

| Field | Value |
|-------|-------|
| **ID** | T10a (part of T10) |
| **Parent** | [T10 ship readiness](T10-ship-readiness.md) |
| **Type** | feature / verify |
| **Priority** | high |
| **Difficulty** | hard |
| **Branch** | `T10a-freeze-matrix-ram-stats` |
| **Status** | **iter-1.2 code done + review fixes + Shizuku/Root device PASS — no-elevation proof pending** |
| **Depends on** | none (first slice) |
| **Unblocks** | T10b, T10c |
| **Last review** | 2026-08-01 Decision D code OK; product flip → **Decision E** (no Standard mode) |

## Todo source (proposed slice)

```yaml
- id: T10a
  title: Freeze matrix (Shizuku/Root only) + RAM Free stats validation
  type: feature
  priority: high
  difficulty: hard
  status: in_progress
  why: |
    Standard mode cannot force-stop apps on modern Android (killBackground is
    useless for deep freeze). Product freeze/BOOST only works with Shizuku or
    Root (am force-stop). Do not ship a fake/useless Standard freeze path.
    RAM Free freed numbers must match /proc/meminfo (works without elevation).
  really_needed: yes
  impact: FreezeBackendResolver, MainActivity BOOST gate, SetupDialog, dropdown, FallbackFreezeBackend de-productized, adb scripts
  followups: T10b (overlay + pin), T10c (regression + Play compliance)
  plan: docs/plan/T10a-freeze-matrix-ram-stats.md
```

---

## Goal

1. **Freeze works only with elevation** — Shizuku or Root (`am force-stop`). No product “Standard freeze” mode.
2. **Without Shizuku/Root: no freezeAll** — do not run a useless skip-all path; gate BOOST freeze and push setup.
3. **Honest metrics** — `freedKb` = MemAvailable Δ only; no inflated killed counts.
4. **RAM Free stays useful without elevation** — fill/free path is independent of freeze backends.
5. **Device proof** — adb matrix for **Shizuku + Root** (not Standard success path).

---

## Product decision flip — Decision E (locked)

| Decision | Status |
|----------|--------|
| **A** killBackground + Success as Standard | **Rejected** (fake killed) |
| **D** killBackground + SKIPPED + banner; BOOST still freezes | **Superseded** — Standard still not useful; skip-all UX is noise |
| **E** **No Standard freeze mode** — freeze/BOOST only Shizuku or Root | **Locked (now)** |

### What Decision E means

| Surface | Behavior |
|---------|----------|
| Backend dropdown | **Shizuku** and **Root** only. Remove “Standard (Auto)” as a freeze choice. |
| Default pref | Prefer `shizuku` if ready, else `root` if ready; if neither ready → **no freeze backend active** (not “standard”). |
| Home BOOST (freeze) | **Blocked / gated** until Shizuku or Root is ready. CTA opens SetupDialog. |
| No elevation | Full-screen or prominent **Connect Shizuku / Root** setup — not a limited freeze result card full of SKIPPED. |
| `FallbackFreezeBackend` | **Not a product mode.** Keep as internal safety (or drop from resolver) so auto-detect never presents “standard” freeze as working. Never return Success for ForceStop. |
| SetupDialog | Remove / de-emphasize “USE STANDARD MODE” as a valid end state for freeze. Force elevated path for freeze features. |
| RAM Free | **Still works** without Shizuku/Root (unchanged honesty). |
| Home gauges / MemAvailable | Unchanged. |

### Why remove Standard (not just “honest skip”)

- Platform: `killBackgroundProcesses` ≠ force-stop → **does not deep-freeze**.
- Product: SKIPPED-all BOOST is **not useful** — confuses users and wastes a tap.
- Honesty: better to **refuse freeze** and require Shizuku/Root than ship a mode that never freezes.

---

## History (short)

### Iter-1 / 1.1 (done, partially superseded)

| SHA / state | Change | Keep? |
|-------------|--------|-------|
| `78c165e` / `4f9712e` | MemAvailable `freedKb`; `MemInfo.kt` | **Keep** |
| `5381fd4` | SKIPPED_FALLBACK + banner + name `"standard"` | **Supersede product path** (keep honest non-Success if backend retained) |
| WT | Result SKIPPED card; Root `no-output-line` Failure | Root fix **keep**; Standard limited-result UX **replace with gate** |
| Review 2026-08-01 | Decision D code correct | Valid for honesty; **product no longer wants Standard mode** |
| Review 2026-08-01 (worker) | Decision E code complete; 1 bug + 5 suggestions + 3 nits | Fixes applied: SKIPPED subtitle copy, BOOST gate-before-anim, onResume re-detect, test gaps (a11y→null, preferred Root, freezeAll blocked), Detecting residual, Accessibility relabel, GameLauncher blocked log |
| Device 2026-08-01 (Shizuku) | 12 force-stops, freed 1622 MB vs gauge Δ 1615 MB (~0.4%) | **PASS** — real Shizuku deep freeze, honest MemAvailable, SKIPPED 0/“None skipped”. Polish applied: gauge refresh on result, badge “+1622 MB RAM (+1 MB Swap)”, FREED SIZE “incl. cache reclaim” |
| Device 2026-08-01 (Root) | 10 force-stops, freed 1176 MB vs gauge Δ 1166 MB (~0.85%) | **PASS** — real Root freeze; swap flat; no pending→Success. Post-review fixes: chip flips to SETUP on mid-session elevation drop, Root readiness cache cleared on resolver invalidate, Accessibility dropped from product candidates, FreezeFrameworkTest restores singleton |
| No-elevation | **Not yet evidenced** | Device proof pending — merge gate |

### Feasibility (unchanged platform facts)

| Mode | Real force-stop? | Product freeze? |
|------|------------------|-----------------|
| **Standard / no elevation** | **No** | **No — removed** |
| **Shizuku** | **Yes** | **Yes** |
| **Root** | **Yes** | **Yes** |
| **Accessibility** | Stub | Out of T10a |

---

## Architecture (target)

```
FreezeBackend (product)
  ├─ ShizukuFreezeBackend   "Shizuku"  → am force-stop via Shizuku  ✅
  ├─ RootFreezeBackend      "Root"     → su -c am force-stop         ✅
  ├─ AccessibilityFreezeBackend        → STUB (do not rely on)
  └─ FallbackFreezeBackend  (internal only — NOT selectable, NOT advertised)
       → if ever hit: never Success ForceStop; preferably not used by UI

Resolver (product):
  detect() → preferred elevated if ready, else other elevated if ready
  if none ready → null / not-ready (UI setup gate), NOT "standard"

FreezeFramework.freezeAll → only called when elevated backend ready
```

### Stats paths (keep)

| Path | Source | Risk |
|------|--------|------|
| Home gauges | MemTotal − MemAvailable | OK |
| Freeze freed UI | MemAvailable Δ only | OK with real Shizuku/Root kills |
| RAM Free freed | MemStats before/after | OK without elevation |

### Code map (iter-1.2 targets)

| File | Role | Action |
|------|------|--------|
| `MainActivity.kt` | BOOST gate; remove Standard pref; setup when not elevated | **MODIFY** |
| `SetupDialog.kt` | No “USE STANDARD MODE” as freeze solution | **MODIFY** |
| `FreezeBackendResolver.kt` | Prefer elevated only; no product fallback to standard | **MODIFY** |
| `FallbackFreezeBackend.kt` | De-productize (keep internal / optional) | **MODIFY** |
| `RootFreezeBackend.kt` | pending → Failure (WT) | **KEEP / commit** |
| `FreezeFramework.kt` / `MemInfo.kt` | Honest MemAvailable | **KEEP** |
| `scripts/adb-freeze-matrix.sh` | Shizuku + Root only; no-elevation = blocked UI | **MODIFY** |
| Tests | Resolver + BOOST gate; drop Standard-as-mode claims | **MODIFY** |

---

## Scope

### In (iter-1.2)

- **Remove Standard as freeze product mode** (dropdown, default, copy)
- **Gate freezeAll / Home BOOST freeze** on Shizuku or Root ready
- Setup / banner: connect elevation (Shizuku primary, Root secondary)
- Keep metrics honesty (MemAvailable); Root no-output Failure
- adb matrix: **no elevation (blocked)** / **Shizuku** / **Root** / RAM Free
- Logging: backend name, killed/failed/skipped only on real elevated runs

### Out

- Overlay BOOST → **T10b**
- Whitelist / pin → **T10b**
- Accessibility product path, Play, full regression → **T10c**
- Reviving killBackground as a marketed freeze path

---

## Approach

### 1. Elevation-only freeze (Decision E)

```text
if (!isElevatedBackendReady()) {
  // do NOT call freezeAll
  show Connect Shizuku / Root UI
  return
}
framework.freezeAll(...)
```

**Elevated** = active backend is Shizuku or Root and `isReady() == true`.

### 2. Dropdown / prefs

| Pref value | Meaning |
|------------|---------|
| `shizuku` | Prefer Shizuku if ready |
| `root` | Prefer Root if ready |
| ~~`standard`~~ | **Remove** from UI; migrate stored `"standard"` → treat as “needs setup” (e.g. default prefer shizuku, blocked until ready) |

### 3. FallbackFreezeBackend

- Prefer: **exclude from product resolver list** so UI never shows backend `standard`.
- If kept for tests/safety: ForceStop must **never** Success (SKIPPED or unused).
- Do not document or surface as “Standard mode works”.

### 4. Metrics (unchanged)

- UI `freedKb` = MemAvailable Δ only
- RSS log-only
- killed==0 && freed==0 on elevated run → “Already optimized” OK

### 5. RAM Free

- Unchanged; works without elevation
- adb: true before/after `/proc/meminfo` (fix prior R1)

---

## adb freeze matrix (merge gate)

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# --- No elevation ---
# Revoke Shizuku / no root → open app
# Expect: NO freezeAll via standard; BOOST freeze gated; setup CTA visible
# Expect: logcat has no successful freezeAll kill inflation

# --- Shizuku ---
# Grant Shizuku; select Shizuku → BOOST
# Expect: freezeAll via Shizuku; killed>0 when targets exist
# Prove: start user app, BOOST, pidof empty (if not system-protected)

# --- Root ---
# su available; select Root → BOOST
# Expect: freezeAll via Root; no pending→Success inflation

# --- RAM Free (no elevation OK) ---
adb shell 'grep MemAvailable /proc/meminfo'   # BEFORE
# tap FREE RAM
adb shell 'grep MemAvailable /proc/meminfo'   # AFTER
# UI Δ within ~±5%
```

### Pass criteria

| Mode | Expect |
|------|--------|
| No elevation | **Freeze blocked**; setup CTA; **no** Standard freeze result with fake/skipped theater |
| Shizuku | `freezeAll via Shizuku`; real force-stop; killed>0 when targets exist |
| Root | `freezeAll via Root`; same; no pending→Success |
| Dropdown | Shizuku / Root only; re-detect; no crash |
| RAM Free | UI Δ matches true before/after MemAvailable |
| Home BOOST freed (elevated) | MemAvailable Δ only |

---

## Files to change (iter-1.2)

| Action | File | Notes |
|--------|------|-------|
| **MODIFY** | `MainActivity.kt` | Gate BOOST freeze; remove Standard menu item; setup when not elevated; commit prior WT polish where still valid |
| **MODIFY** | `SetupDialog.kt` | Remove “USE STANDARD MODE” as freeze OK path; Shizuku/Root only |
| **MODIFY** | `FreezeBackendResolver.kt` | Elevated-only product detect; no fallback to standard for UI |
| **MODIFY** | `FallbackFreezeBackend.kt` | De-productize / exclude from product list |
| **MODIFY** | Tests (resolver, fallback, UI if any) | Reflect Decision E |
| **MODIFY** | `scripts/adb-freeze-matrix.sh` | No-elevation blocked; Shizuku; Root; RAM Free before/after |
| COMMIT | `RootFreezeBackend.kt` | pending → `Failure("no-output-line")` |
| KEEP | `FreezeFramework.kt`, `MemInfo.kt` | Metrics honesty |

---

## Edge cases

| Case | Handling |
|------|----------|
| Shizuku installed, not granted | Not ready → gate BOOST; setup CTA |
| Root preferred, no su | Fall to Shizuku if ready; else gate |
| User dismisses SetupDialog | Gate remains until elevated ready |
| Pref was `"standard"` from old builds | Migrate: clear / treat as needs setup |
| freezeAll called without elevation | Should not happen; if safety path hits Fallback, never Success |
| Accessibility ready | Still stub — no product claim |
| RAM Free without elevation | Allowed |

---

## Test plan

### Automated

- `./gradlew :app:testDebugUnitTest`
- Resolver: no product “standard” when elevated missing
- Fallback (if retained): ForceStop never Success
- Root incomplete batch → Failure (prior R5)
- Optional: BOOST gate unit/UI test if practical

### Manual / adb (required)

- No elevation: freeze blocked + setup
- Shizuku: real kill once
- Root: freezeAll via Root
- RAM Free Δ true before/after

---

## Decisions (this slice)

| # | Decision | Lock |
|---|----------|------|
| 1 | Standard deep freeze | **Impossible** (platform) |
| 2 | Standard as product freeze mode | **Removed (Decision E)** |
| 3 | Home BOOST freeze without elevation | **Gated** — setup CTA, no freezeAll |
| 4 | Product freeze backends | **Shizuku + Root only** |
| 5 | Freed metric (UI) | MemAvailable Δ; RSS log-only |
| 6 | RAM Free | Works without elevation |
| 7 | Accessibility | No product work in T10a |
| 8 | Decision D honest-skip path | **Superseded by E** (keep non-Success if Fallback kept internal) |

---

## Out of scope

- Overlay CTA, whitelist, receiver export, privacy rewrite, Play checklist, full regression (→ T10b/T10c)
- Boot freeze, tags, `pm disable`, fake GB claims
- Marketing killBackground as freeze

---

## Implementation order

1. ~~Metrics MemAvailable + MemInfo~~ (iter-1)
2. ~~Honest SKIPPED / Root no-output / banner (iter-1.1)~~ — honesty kept; Standard product path **removed in 1.2**
3. **Iter-1.2:** Remove Standard from dropdown + prefs migration
4. **Iter-1.2:** Gate BOOST freeze on elevated backend ready
5. **Iter-1.2:** SetupDialog — no Standard as freeze OK
6. **Iter-1.2:** Resolver elevated-only for product UI
7. Update adb matrix script + device proof (Shizuku/Root + blocked no-elevation)
8. Unit tests green; commit WT; PR

---

## Iteration exit checklist

### Code

- [x] Metrics honesty (MemAvailable freed)
- [x] Root batch missing OK/FAIL → Failure
- [x] ForceStop never fake Success on Fallback (if backend retained)
- [x] **Standard removed from freeze product UI** (dropdown / copy / SetupDialog)
- [x] **BOOST freeze gated** without Shizuku/Root
- [x] Resolver / state never presents “standard” as working freeze mode
- [x] Pref migration away from `"standard"`
- [x] adb script updated for Decision E
- [x] Unit tests updated + green

### Device proof (merge gate)

- [ ] No elevation: freeze blocked; setup CTA; no freezeAll standard theater
- [x] Shizuku: real force-stop; killed>0 when targets exist (2026-08-01: 12 apps, ~0.4% Δ error)
- [x] Root: freezeAll via Root; no pending→Success (2026-08-01: 10 apps, ~0.85% Δ error)
- [x] Dropdown: Shizuku/Root only; re-detect OK (code; Shizuku/Root runs)
- [ ] RAM Free UI Δ vs true before/after MemAvailable
- [x] `./gradlew :app:testDebugUnitTest` green

---

## Recommended next agent tasks

1. ~~Implement **Decision E** in `MainActivity` + `SetupDialog` + resolver (gate BOOST; drop Standard menu).~~ **done**
2. ~~De-productize `FallbackFreezeBackend` (exclude from product detect / never Success).~~ **done**
3. ~~Migrate pref `"standard"` → needs setup.~~ **done**
4. ~~Fix adb script (no-elevation blocked; Shizuku; Root; RAM Free before/after).~~ **done**
5. ~~Update tests; run unit tests.~~ **done** — 23 tests green
6. ~~Review fixes (worker 2026-08-01): SKIPPED subtitle on elevated success; gate BOOST before anim; onResume re-detect; test gaps (a11y→null, preferred Root, freezeAll blocked); Detecting residual; Accessibility relabel; GameLauncher blocked log.~~ **done**
7. ~~Device: Shizuku real force-stop + MemAvailable cross-check.~~ **PASS** (2026-08-01, ~0.4% error)
8. ~~Device: Root freezeAll + MemAvailable cross-check.~~ **PASS** (2026-08-01, ~0.85% error)
9. **Remaining:**
   - Device: **no elevation** gate (BOOST → setup CTA, no `freezeAll` in logcat)
   - Optional device: RAM Free screen before/after (separate from BOOST)
   - Known (T10b/c): Games pre-launch freeze / FreezeReceiver / RAM-fill pre-freeze still call `freezeAll` — framework returns `blocked` (honest, no fake kills); gate or CTA there in T10b
   - Doc sync `docs/freeze-architecture.md` / `freeze-api.md` (Standard-mode language) after device proof
   - Check off exit list; commit WT (exclude `.kotlin/errors/*.log`); PR

---

## Open questions

1. ~~A vs D vs E?~~ → **E** (no Standard freeze mode; Shizuku/Root only).
2. Keep `FallbackFreezeBackend` class for safety/tests? **Yes optional** — must not be a user-facing mode.
3. Keep `BoostManager`? Leave dead; same weak API.
4. T9 fill bugs into T10a? Only if device still fails fill.

---

## References

- Parent: `docs/plan/T10-ship-readiness.md`
- Next: `docs/plan/T10b-overlay-pin-apps.md`
- `docs/freeze-architecture.md`, `docs/freeze-api.md`, `docs/plan/T9-ram-filler.md`
- Platform: `ActivityManager.killBackgroundProcesses` ≠ force-stop; needs shell/root UID for `am force-stop`
