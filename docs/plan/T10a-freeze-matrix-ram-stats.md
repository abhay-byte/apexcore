# T10a — Freeze matrix + RAM Free stats validation

| Field | Value |
|-------|-------|
| **ID** | T10a (part of T10) |
| **Parent** | [T10 ship readiness](T10-ship-readiness.md) |
| **Type** | feature / verify |
| **Priority** | high |
| **Difficulty** | hard |
| **Branch** | `T10a-freeze-matrix-ram-stats` |
| **Status** | **iter-1 reviewed — Standard cannot deep-freeze; Shizuku/Root required** |
| **Depends on** | none (first slice) |
| **Unblocks** | T10b, T10c |

## Todo source (proposed slice)

```yaml
- id: T10a
  title: Freeze matrix (Standard/Shizuku/Root) + RAM Free stats validation
  type: feature
  priority: high
  difficulty: hard
  status: in_progress
  why: |
    Home BOOST via freezeAll is neutered on Standard. Real force-stop needs
    Shizuku or Root (am force-stop). killBackgroundProcesses is not enough
    on modern Android. RAM Free freed numbers must match /proc/meminfo.
  really_needed: yes
  impact: FallbackFreezeBackend, FreezeFramework metrics, MemStats, Shizuku banner UI, adb scripts
  followups: T10b (overlay + pin), T10c (regression + Play compliance)
  plan: docs/plan/T10a-freeze-matrix-ram-stats.md
```

---

## Goal

1. **Freeze backends stay decoupled** — Standard / Shizuku / Root honest about capability.
2. **Deep freeze only via elevation** — Shizuku `am force-stop` or Root `su -c am force-stop`.
3. **Standard never fakes success** — no inflated killed counts; banner pushes Shizuku.
4. **RAM Free + Home BOOST stats honest** — MemAvailable (and swap) deltas only.

---

## Iteration 1 review (2026-07-27)

### Commits on branch

| SHA | Change |
|-----|--------|
| `ff8d1c0` | Fallback ForceStop → `killBackgroundProcesses` + Success |
| `78c165e` | `freedKb` = MemAvailable Δ only; RSS log-only |
| `9203efc` | Limited-mode text + "Already optimized" zero-kill |
| `4f9712e` | `MemInfo.kt` extract |
| `93d5aa1` | `scripts/adb-freeze-matrix.sh` |

### What works

| Area | Verdict |
|------|---------|
| Metrics honesty (`freedKb` MemAvailable only) | **Good** |
| MemInfo extract | **Good** |
| Zero-kill / zero-freed "Already optimized" | **Good** |
| Unit test for Fallback ForceStop call | **Present** (must re-lock on SKIPPED) |
| Shizuku / Root backends (`am force-stop` batch) | **Unchanged — correct design** |
| SetupDialog open Shizuku / Play store | **Exists** |

### What fails product intent

| Issue | Detail |
|-------|--------|
| **Standard does not freeze** | `ActivityManager.killBackgroundProcesses` only pokes cached/empty processes of other apps. On Android 12+ (and most OEMs) it is effectively a **no-op for third-party force-stop**. Real stop needs privileged `FORCE_STOP_PACKAGES` / shell `am force-stop` via **Shizuku or Root**. |
| **Fake Success** | Fallback returns `Result.Success` for every ForceStop → `killed` = target count even when nothing died → UI "Freed N background apps" lies. |
| **Weak limited-mode UX** | One-line warning text; easy to miss. Not a clear **Connect Shizuku** banner. |
| **adb matrix** | Script present; device proof for Shizuku/Root still required for merge. |

### Feasibility conclusion

| Mode | Real force-stop possible? | Notes |
|------|---------------------------|--------|
| **Standard (no elevation)** | **No** (not for product "deep freeze") | `killBackgroundProcesses` best-effort only; may reclaim tiny cached RAM; cannot match Shizuku. |
| **Shizuku** | **Yes** | `ShizukuFreezeBackend` → `am force-stop --user current` |
| **Root** | **Yes** | `RootFreezeBackend` → `su -c am force-stop` |
| **Accessibility** | **No (stub)** | Out of T10a |

**Decision flip:** Decision **A** (treat killBackground as working Standard path) is **wrong for ship**. Lock **Decision D** below.

---

## Research (this slice)

### Architecture (keep)

```
FreezeBackend
  ├─ ShizukuFreezeBackend   "Shizuku"      → am force-stop via Shizuku  ✅ deep freeze
  ├─ RootFreezeBackend      "Root"         → su -c am force-stop         ✅ deep freeze
  ├─ AccessibilityFreezeBackend            → STUB (T10a: do not rely on)
  └─ FallbackFreezeBackend  "standard"  → best-effort killBackground + SKIPPED (honest)

FreezeBackendResolver.detect() → preferred if ready, else first ready
FreezeFramework.freezeAll(filter) → ForceStop for each target
```

### CRITICAL-1 — Standard cannot deep-freeze (platform limit)

- `freezeAll` only emits `ForceStop`.
- Without Shizuku/Root, only `killBackgroundProcesses` is available → **not** force-stop.
- Home BOOST uses `FreezeFramework.freezeAll`, not `BoostManager.kick` (same weak API).

### MAJOR — Stats paths

| Path | Source | Risk |
|------|--------|------|
| Home gauges | `getSystemMemStats` = MemTotal−MemAvailable | OK |
| Freeze freed UI | MemAvailable Δ only (iter-1) | OK if honest killed/skipped |
| RAM Free freed | same MemStats before/after | OK |

### Code map

| File | Role |
|------|------|
| `freeze/FallbackFreezeBackend.kt` | Best-effort kill + **SKIPPED_FALLBACK** (no fake Success) |
| `freeze/ShizukuFreezeBackend.kt` | Real batch force-stop |
| `freeze/RootFreezeBackend.kt` | Real su batch |
| `freeze/FreezeFramework.kt` | MemAvailable freed; counts Success vs skip |
| `MainActivity.kt` | **Shizuku connect banner** when `standard` |
| `SetupDialog.kt` | Configure Shizuku / Root |
| `MemInfo.kt` | Shared stats |
| `scripts/adb-freeze-matrix.sh` | Device proof |

---

## Scope

### In

- Acknowledge Standard **cannot** deep-freeze without elevation
- Fallback: optional `killBackgroundProcesses` but result = **SKIPPED_FALLBACK** (honest metrics)
- **Prominent Connect Shizuku banner** when backend is `standard` (opens SetupDialog)
- Freeze freed UI = MemAvailable Δ; RSS log-only
- RAM Free: same honesty (already largely done)
- adb matrix: Standard (expect skip / banner) / Shizuku / Root
- Logging: backend, targets, killed/failed/skipped, MemAvailable before/after

### Out (other parts)

- Overlay BOOST button → **T10b**
- Whitelist / pin apps → **T10b**
- Accessibility product path / privacy rewrite → **T10c**
- FreezeReceiver export / Play declarations → **T10c**
- Full feature regression suite → **T10c**

---

## Approach

### 1. Standard path — Decision D (locked after iter-1 review)

```kotlin
// FallbackFreezeBackend.execute — ForceStop
try {
    am.killBackgroundProcesses(op.pkg) // best-effort only; may no-op on modern Android
} catch (_: Throwable) { /* ignore */ }
return FreezeOperation.Result.SKIPPED_FALLBACK // never Success — no fake killed count
```

**UI when `backendName == "standard"`:**

- **Banner card** (not one-line text):
  - Title: **Connect Shizuku for deep freeze**
  - Body: Standard mode cannot force-stop apps on modern Android.
  - CTA: **CONNECT SHIZUKU** → `onSetupClick()` → existing `SetupDialog` / open Shizuku app / Play.

BOOST remains tappable (RAM Free still useful; freeze path logs skips). Do **not** claim "Freed N apps" from Standard Success.

| Rejected | Why |
|----------|-----|
| **A** killBackground + Success | Inflates killed; user sees "works" when it doesn't |
| **B** hard-block BOOST | Over-blocks; RAM Free / gauges still useful |
| **C** wire BoostManager.kick | Same weak API; dual code paths |

### 2. Freeze metrics honesty

- UI `freedKb` = MemAvailable Δ only (`coerceAtLeast(0)`)
- RSS delta log-only
- killed==0 && freed==0 → "Already optimized"
- Standard: expect high `skipped`, killed=0 unless another backend used

### 3. Shared MemStats

Done in iter-1 (`MemInfo.kt`). Keep.

### 4. RAM Free validation

Unchanged: before/after MemAvailable only; floor 0.

### 5. Backend dropdown

| Pref | Resolver |
|------|----------|
| `standard` | preferredName = null → auto-detect (Fallback if no elevation) |
| `shizuku` | preferred "Shizuku" if ready |
| `root` | preferred "Root" if ready |

---

## adb freeze matrix (merge gate)

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# --- Standard ---
# No Shizuku grant / pick Standard → BOOST
adb logcat -c
# trigger BOOST
adb logcat -d -s ApexCore.Freeze:* | tail -80
# Expect: freezeAll via standard; killed=0; skipped>0; banner visible; no fake multi-GB

# --- Shizuku ---
# Grant Shizuku; dropdown Shizuku → BOOST
# Expect: freezeAll via Shizuku; killed>0 when targets exist
# Prove: start user app, BOOST, pidof empty (if not system-protected)

# --- Root ---
# su available; dropdown Root → BOOST
# Expect: freezeAll via Root

# --- RAM Free accuracy ---
adb shell 'grep -E "MemAvailable|MemTotal|SwapFree|SwapTotal" /proc/meminfo'
# UI Δ within ~±5% of adb MemAvailable Δ
```

### Pass criteria

| Mode | Expect |
|------|--------|
| Standard | backend `standard`; **killed=0**; skipped honest; **Connect Shizuku banner**; no fake multi-GB |
| Shizuku | `freezeAll via Shizuku`; real force-stop; killed>0 when targets exist |
| Root | `freezeAll via Root`; same |
| Dropdown | re-detect; no crash |
| RAM Free | UI Δ matches `/proc/meminfo` within noise |
| Home BOOST freed | MemAvailable Δ only |

---

## Files to change

| Action | File | Notes |
|--------|------|-------|
| **MODIFY** | `freeze/FallbackFreezeBackend.kt` | killBackground + **SKIPPED_FALLBACK** |
| **MODIFY** | `FallbackFreezeBackendTest.kt` | assert SKIPPED + verify kill call |
| **MODIFY** | `MainActivity.kt` | **ShizukuConnectBanner** replaces weak limited text |
| DONE | `FreezeFramework.kt` / `MemInfo.kt` / adb script | keep unless bugs |

---

## Edge cases

| Case | Handling |
|------|----------|
| Shizuku installed, not granted | Ready=false; Fallback + banner |
| Root preferred, no su | Fall auto-detect → banner if standard |
| User dismisses SetupDialog | Banner stays until elevated backend ready |
| killBackground throws | Catch; still SKIPPED |
| Accessibility ready | Still stub — no product claim |

---

## Test plan

### Automated

- `./gradlew :app:testDebugUnitTest`
- Fallback: ForceStop → `SKIPPED_FALLBACK` + `killBackgroundProcesses` verified

### Manual / adb (required)

- Standard: banner + killed=0
- Shizuku: real kill proof once
- RAM Free Δ vs `/proc/meminfo`

---

## Decisions (this slice)

| # | Decision | Lock |
|---|----------|------|
| 1 | Standard deep freeze | **Impossible without Shizuku/Root** (platform) |
| 2 | Standard ForceStop result | **D**: best-effort killBackground + **SKIPPED_FALLBACK** (no Success) |
| 3 | Standard UX | **Connect Shizuku banner** (SetupDialog CTA); BOOST not hard-blocked |
| 4 | Freed metric (UI) | MemAvailable Δ primary; RSS log-only |
| 5 | Accessibility | No product work in T10a |
| 6 | T9 fill re-arch | Out of T10a unless adb proves fill still broken |

---

## Out of scope

- Overlay CTA, whitelist, receiver export, privacy rewrite, Play Console checklist, full regression (→ T10b/T10c)
- Boot freeze, tags, `pm disable`, fake GB claims
- Hard-blocking BOOST without elevation (rejected Decision B)

---

## Implementation order

1. ~~Metrics MemAvailable + MemInfo extract~~ (iter-1)
2. **Iter-1.1:** Fallback honest SKIPPED + unit test fix  
3. **Iter-1.1:** Shizuku connect banner  
4. adb matrix device proof (Shizuku/Root)  
5. PR  

---

## Iteration 1 exit (updated)

- [x] Metrics honesty (MemAvailable freed)  
- [x] Limited-mode text (insufficient alone)  
- [ ] **Standard honest (SKIPPED, no fake killed)** ← iter-1.1  
- [ ] **Connect Shizuku banner** ← iter-1.1  
- [ ] Shizuku + Root proven once via adb logcat  
- [ ] RAM Free Δ validated on device  

---

## Open questions (resolved / remaining)

1. ~~A vs B?~~ → **D** (honest skip + banner; no hard block).  
2. Keep `BoostManager`? Leave dead for now; same API as Fallback.  
3. T9 fill bugs into T10a? Only if device still fails fill.

---

## References

- Parent: `docs/plan/T10-ship-readiness.md`
- Next: `docs/plan/T10b-overlay-pin-apps.md`
- `docs/freeze-architecture.md`, `docs/freeze-api.md`, `docs/plan/T9-ram-filler.md`
- Platform: `ActivityManager.killBackgroundProcesses` ≠ force-stop; needs shell/root UID for `am force-stop`
