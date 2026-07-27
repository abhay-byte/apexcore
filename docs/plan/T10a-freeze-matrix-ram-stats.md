# T10a — Freeze matrix + RAM Free stats validation

| Field | Value |
|-------|-------|
| **ID** | T10a (part of T10) |
| **Parent** | [T10 ship readiness](T10-ship-readiness.md) |
| **Type** | feature / verify |
| **Priority** | high |
| **Difficulty** | hard |
| **Branch** | `T10a-freeze-matrix-ram-stats` (suggested) |
| **Status** | plan draft — awaiting approval |
| **Depends on** | none (first slice) |
| **Unblocks** | T10b, T10c |

## Todo source (proposed slice)

```yaml
- id: T10a
  title: Freeze matrix (Standard/Shizuku/Root) + RAM Free stats validation
  type: feature
  priority: high
  difficulty: hard
  status: pending
  why: |
    Home BOOST via freezeAll is neutered on Standard (Fallback skips ForceStop).
    Shizuku/Root paths exist but need adb proof. RAM Free freed numbers must match
    /proc/meminfo truth before ship.
  really_needed: yes
  impact: FallbackFreezeBackend, FreezeFramework metrics, MemStats, RamFillerManager/UI, adb scripts
  followups: T10b (overlay + pin), T10c (regression + Play compliance)
  plan: docs/plan/T10a-freeze-matrix-ram-stats.md
```

---

## Goal

1. **Freeze backends work and stay decoupled** — Standard / Shizuku / Root each produce real, logged behavior.
2. **Prove via adb** on device (not unit tests alone).
3. **RAM Free + Home BOOST stats honest** — only measured MemAvailable (and swap) deltas; never invent freed MB.

---

## Research (this slice)

### Architecture (keep)

```
FreezeBackend
  ├─ ShizukuFreezeBackend   "Shizuku"      → am force-stop via Shizuku
  ├─ RootFreezeBackend      "Root"         → su -c am force-stop
  ├─ AccessibilityFreezeBackend            → STUB (T10a: do not rely on)
  └─ FallbackFreezeBackend  "cached only"  → CRITICAL: ForceStop currently SKIPPED

FreezeBackendResolver.detect() → preferred if ready, else first ready
FreezeFramework.freezeAll(filter) → ForceStop for each target
```

**Decoupling is good.** Honesty is not.

### CRITICAL-1 — Standard / cached-only neutered

`FallbackFreezeBackend`: `ForceStop` → always `SKIPPED_FALLBACK`.  
`freezeAll` only emits `ForceStop` → **Standard mode kills 0 apps**.

Home BOOST uses `FreezeFramework.freezeAll`, **not** `BoostManager.kick` (which still has `killBackgroundProcesses`).

### MAJOR — Stats paths

| Path | Source | Risk |
|------|--------|------|
| Home gauges | `getSystemMemStats` = MemTotal−MemAvailable | OK baseline |
| Freeze freed UI | `max(ΔMemAvailable, Δtarget RSS via ps)` | Can over-report |
| RAM Free freed | afterAvail − beforeAvail from same MemStats | OK if same source |

### Code map

| File | Role |
|------|------|
| `freeze/FallbackFreezeBackend.kt` | Fix ForceStop |
| `freeze/ShizukuFreezeBackend.kt` | Verify batch force-stop |
| `freeze/RootFreezeBackend.kt` | Verify su batch |
| `freeze/FreezeBackendResolver.kt` | Preferred Standard/Shizuku/Root |
| `freeze/FreezeFramework.kt` | freezeAll metrics honesty |
| `MainActivity.kt` | `getSystemMemStats`, BOOST, backend dropdown |
| `BoostManager.kt` | Legacy kill path — unify or leave dead |
| `ram/RamFillerManager.kt` | before/after MemStats |
| `ram/RamFreeScreen.kt` | Result labels |
| **NEW** `scripts/adb-freeze-matrix.sh` | Device proof |
| **OPTIONAL** `MemInfo.kt` | Extract shared stats reader |

---

## Scope

### In

- Fix Fallback ForceStop → real Standard path (Decision A default)
- Honest limited-mode UI when backend is `cached only`
- Freeze freed UI = **MemAvailable Δ primary**; RSS log-only
- RAM Free: same MemStats before/after; floor 0; already-at-cap honesty
- adb matrix: Standard / Shizuku / Root
- Logging: backend, targets, killed/failed/skipped, before/after MemAvailable

### Out (other parts)

- Overlay BOOST button → **T10b**
- Whitelist / pin apps → **T10b**
- Accessibility product path / privacy rewrite → **T10c** (note: do not claim a11y works in this slice)
- FreezeReceiver export / Play declarations → **T10c**
- Full feature regression suite → **T10c**

---

## Approach

### 1. Fallback ForceStop (Decision A — locked default)

```kotlin
// FallbackFreezeBackend.execute
when (op) {
    is FreezeOperation.ForceStop -> {
        am.killBackgroundProcesses(op.pkg)
        Result.Success // best-effort cached reclaim
    }
    else -> Result.Failure("unsupported-on-fallback")
}
```

UI when `backendName == "cached only"`: short note  
**"Limited mode — install Shizuku for deep freeze"**.

Alternatives (only if user overrides):

| B | Block BOOST until Shizuku/Root ready |
| C | Standard calls `BoostManager.kick` instead of freezeAll |

### 2. Freeze metrics honesty

In `FreezeFramework.freezeAll`:

- Log RSS delta if useful
- **UI `freedKb` = MemAvailable Δ only** (coerceAtLeast 0)
- If killed==0 and freed==0 → caller shows "Already optimized"

### 3. Shared MemStats (optional hygiene)

Move `getSystemMemStats` / `MemStats` from `MainActivity.kt` → `MemInfo.kt` if cheap. Same formulas:

- `ramUsed = MemTotal − MemAvailable`
- `swapUsed = SwapTotal − SwapFree`
- Fallback to `ActivityManager.MemoryInfo` if `/proc/meminfo` unreadable

### 4. RAM Free validation

1. `RamFillerManager.run`: before/after = `getSystemMemStats` only  
2. `freedKb = max(0, afterAvail − beforeAvail)`  
3. Result card: "MemAvailable Δ +N MB" (or existing copy if already accurate)  
4. No synthetic positive freed on timeout/cancel without measured progress  

### 5. Backend dropdown (no behavior change except honesty)

| Pref | Resolver |
|------|----------|
| `standard` | preferredName = null → auto-detect |
| `shizuku` | preferred "Shizuku" if ready |
| `root` | preferred "Root" if ready |

---

## adb freeze matrix (merge gate)

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# --- Standard ---
# Disable Shizuku grant / pick Standard in UI → BOOST
adb logcat -c
# trigger BOOST in UI
adb logcat -d -s ApexCore.Freeze:* | tail -80
# Expect: freezeAll via cached only; killed >= 0 honest; limited-mode note in UI

# --- Shizuku ---
# Grant Shizuku to ApexCore; dropdown Shizuku → BOOST
# Expect: freezeAll via Shizuku; killed > 0 when user apps running
# Prove: start a user app, BOOST, pidof empty (if not system-protected)

# --- Root ---
# su available; dropdown Root → BOOST
# Expect: freezeAll via Root

# --- RAM Free accuracy ---
adb shell 'grep -E "MemAvailable|MemTotal|SwapFree|SwapTotal" /proc/meminfo'
# Run RAM Free in UI; note result
adb shell 'grep -E "MemAvailable|SwapFree" /proc/meminfo'
# UI Δ within ~±5% of adb MemAvailable Δ (noise OK)
```

Optional: commit `scripts/adb-freeze-matrix.sh` with steps + log greps.

### Pass criteria

| Mode | Expect |
|------|--------|
| Standard | backend `cached only`; killBackground best-effort **or** explicit limited message; **no fake multi-GB** |
| Shizuku | `freezeAll via Shizuku`; force-stop targets; killed>0 when targets exist |
| Root | `freezeAll via Root`; same |
| Dropdown switch | re-detect after invalidate; no crash |
| RAM Free | UI Δ matches `/proc/meminfo` within noise |
| Home BOOST freed | MemAvailable Δ only; 0 → "Already optimized" |

---

## Files to change

| Action | File |
|--------|------|
| **MODIFY** | `freeze/FallbackFreezeBackend.kt` |
| **MODIFY** | `freeze/FreezeFramework.kt` |
| **MODIFY** | `MainActivity.kt` (limited-mode copy; optional MemStats extract) |
| **MODIFY** | `ram/RamFillerManager.kt` / `RamFreeScreen.kt` if labels/deltas wrong |
| **OPTIONAL** | `BoostManager.kt` — delete dead path or wire as shared kill helper |
| **OPTIONAL NEW** | `MemInfo.kt` |
| **NEW** | `scripts/adb-freeze-matrix.sh` |
| **MODIFY** | unit tests if Fallback behavior assertions exist |

---

## Edge cases

| Case | Handling |
|------|----------|
| Shizuku installed, not granted | Ready=false; auto Standard |
| Root preferred, no su | Fall auto-detect |
| All kills skip / 0 free | "Already optimized" |
| MemAvailable rises after freeze (noise) | floor 0 |
| Concurrent BOOST + RAM Free | existing single-flight / cancel |
| Accessibility `isReady` true somehow | Still stub — do not claim success in T10a |

---

## Test plan

### Automated

- `./gradlew :app:testDebugUnitTest`
- Resolver tests still pass
- Add/adjust Fallback ForceStop test if present

### Manual / adb (required)

- Full matrix table above on physical device
- Screenshot or logcat paste as evidence in PR

---

## Decisions (this slice)

| # | Decision | Lock |
|---|----------|------|
| 1 | Standard fallback | **A**: killBackgroundProcesses on ForceStop + limited-mode copy |
| 2 | Freed metric (UI) | MemAvailable Δ primary; RSS log-only |
| 3 | Accessibility | No product work in T10a; do not depend on it for pass criteria |
| 4 | T9 fill re-arch | Out of T10a unless adb proves fill still broken |

---

## Out of scope

- Overlay CTA, whitelist, receiver export, privacy rewrite, Play Console checklist, full regression (→ T10b/T10c)
- Boot freeze, tags, `pm disable`, fake GB claims

---

## Implementation order

1. Fallback ForceStop fix + unit check  
2. FreezeFramework UI freed = MemAvailable Δ  
3. Limited-mode Home copy  
4. RAM Free before/after audit + label polish  
5. Optional MemInfo extract  
6. adb matrix script + device run  

---

## Iteration 1 exit

- Standard path not a no-op (or honestly blocked)  
- Shizuku + Root proven once via adb logcat  
- RAM Free Δ validated against `/proc/meminfo`  
- No inflated freed numbers on zero-kill runs  

---

## Open questions

1. Prefer Decision **A** (killBackground) vs **B** (block BOOST without elevation)? Plan default **A**.  
2. Keep `BoostManager` or delete after Fallback covers Standard?  
3. Fold any open **T9 iter-4** fill bugs into T10a if device still fails fill?

---

## References

- Parent: `docs/plan/T10-ship-readiness.md`
- Next: `docs/plan/T10b-overlay-pin-apps.md`
- `docs/freeze-architecture.md`, `docs/freeze-api.md`, `docs/plan/T9-ram-filler.md`
)
