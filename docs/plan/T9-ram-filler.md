# T9 — RAM Filler ("RAM Free") system to force-free memory

| Field | Value |
|-------|-------|
| **ID** | T9 |
| **Type** | feature |
| **Priority** | high |
| **Difficulty** | hard |
| **Branch** | `T9-ram-filler` |
| **Status** | iteration 4 code shipped; pending device proof / review |

## Todo source

```yaml
- id: T9
  title: RAM Filler ("RAM Free") system to force-free memory
  type: feature
  priority: high
  difficulty: hard
  why: Filling the phone's RAM to 100% forces Android's LMK (Low Memory Killer) to reclaim all cached background processes before the filler process terminates itself.
  really_needed: yes
  impact: New RamFillerManager, memory allocation service/loop, UI controls
  followups: null
  images: null
  github_ref: null
  plan: docs/plan/T9-ram-filler.md
```

---

## Goal

Ship **RAM Free**: fill system RAM (and reclaimable swap pressure path) up to a **90% safe ceiling**, force LMK reclaim of **cached/background user apps only**, release buffers, report honest freed MB.

- Product name: **RAM Free**
- Subtitle / status copy: **Force system reclaim** (honest; no fake GB)
- Default path: **Standard** (no root, no Shizuku)
- Optional: Shizuku / Root extras + **BOOST/freeze pre-step** for more reclaim
- Hard rules: **never kill UI process**, **never kill system processes**

---

## Decisions (LOCKED)

| # | Decision | Lock |
|---|----------|------|
| 1 | Navigation | Separate full page. **Bottom nav hidden.** Top bar only: back + title `RAM FREE`. |
| 2 | Kill policy | **Never** kill ApexCore UI. **Never** kill system / privileged packages. LMK victims = cached/background only (OS decides). Freeze pre-step uses existing freeze filter (user apps only). |
| 3 | Fill target | Pressure toward full RAM (and swap pressure as measurable); **hard stop at 90% of total RAM used** (safe limit). Same spirit for swap: do not push past **90% swap used** if swap total known. |
| 4 | Backend UX | Per-page dropdown on RAM Free (detect each option). **Global backend dropdown on Home** (shared privilege pick; detect each). Default selection: **Standard**. |
| 5 | Pre-step | **Yes** — optional/default: run BOOST/freeze first (`FreezeFramework.freezeAll`), then fill. More free RAM. |
| 6 | Naming | **RAM Free** primary. Supporting: "Force system reclaim". Result: `+N MB` real delta only. |
| 7 | Hold after fill | **~400 ms** hold at peak. Hard cap **15 s wall-clock for entire `run()`** (pre-freeze + fill + hold + release), not fill-loop only. |
| 8 | Process / FGS | **No foreground service.** Work runs **in-app** while `RamFreeScreen` is resumed. Keep process importance high via visible Activity only. On `onPause` / back during run → **cancel + release** immediately. No `:ramfiller` process unless later proven needed (YAGNI V1). **⚠️ iter-2 review: heap-only in-UI process cannot hit 90% system RAM — see root cause. Decision 8 may need revise in iter 3.** |
| 9 | Concurrent run | **Single-flight:** ignore / no-op second `run()` while active; CTA disabled while running. |
| 10 | Alloc buffers | Prefer **heap `ByteArray` chunks**; avoid long-lived direct `ByteBuffer` unless release proven. Always drop refs in `finally`. **⚠️ iter-2 review: heap-only is why fill fails — revise to native/direct pressure path.** |
| 11 | Cap overshoot | Last chunk sized to **remaining headroom** under 90% (not fixed 8–16 MB past stop). |
| 12 | Preferred backend cold start | Load `preferred_backend` prefs → `FreezeFramework.setPreferredBackend` **before** first `detect()` in `MainScreen`. |
| 13 | RAM Free mode meaning | Mode dropdown selects fill **backend extras** when ready; if extras not implemented, mode must not claim different fill behavior (log + Standard alloc only is OK if UI says so). |

---

## Research summary

### Why fill works (theory)

Android LMK / `lmkd` reclaims when free + reclaimable memory drop. Rapid anonymous alloc raises pressure; OS prefers killing **cached** apps over a **visible foreground** Activity. Filler then **releases** → `MemAvailable` rises. Report delta from `/proc/meminfo` (same family as `getSystemMemStats`).

Orthogonal to freeze; T9 **uses freeze as pre-step**, then pressure fill.

### Why current impl does NOT fill (iter-2 field failure)

User report: **RAM does not fill; nothing like plan happens.** Code review root causes:

#### CRITICAL-1 — Java heap ceiling ≪ system RAM (primary)

`RamFillerManager` only does `ByteArray(chunkBytes)` held in a list.

| Reality | Implication |
|---------|-------------|
| App heap max typically **192–512 MB** (`dalvik.vm.heapsize`; no `android:largeHeap` in manifest) | Process OOMs / soft-fails long before multi-GB system RAM hits 90% |
| System used% driven by **all processes** + kernel + ION/GPU | One app adding ~100–300 MB moves used% a few points on 6–12 GB phones |
| LMK needs **system-wide** free+reclaimable collapse | Heap-only never creates that pressure on free/mid-load devices |
| Field symptom | CTA runs, phases flash, peakAllocated small or OOM quick, gauge barely moves, `+0 MB` / "No reclaim" |

**Plan D10 preferred heap ByteArray** is correct for *safety/release*, wrong as *only* pressure source for *system* 90% target.

#### CRITICAL-2 — Swap gate aborts fill early (very common)

```kotlin
// RamFillerManager fill loop
if (stats.swapTotalKb > 0 && swapPercent >= TARGET_SWAP) break
val maxSafe = minOf(CHUNK_MAX_BYTES, ramHeadroomBytes, swapHeadroomBytes)
if (maxSafe < CHUNK_MIN_BYTES) break  // needs ≥8 MB under BOTH caps
```

Android **zRAM** is often large and already near full (compressed). If swap used ≥ ~90% **or** swap headroom < 8 MB:

- Fill loop exits **before meaningful alloc**
- RAM may still be at 40–70% with GBs free
- UI still goes HOLD → RELEASE → DONE with near-zero work

Plan treated swap stop as co-equal safety. On real devices it becomes a **silent no-op**.

#### MAJOR-3 — Min chunk 8 MB vs last-headroom rule

Plan D11: last chunk = remaining headroom (even 1–7 MB).
Code: refuse any alloc if `maxSafe < 8 MB`. Leaves headroom on table; with swap gate, often **zero chunks**.

#### MAJOR-4 — No runtime diagnostics

No log of `Runtime.maxMemory()`, heap free, chunk count, stop reason enum, or post-fill peakAllocated vs system delta. Hard to debug on device without logcat skill.

#### Secondary (not primary failure)

- Pre-freeze can burn wall-clock of 15 s budget (fill starved if freeze slow)
- `delay(16)` per chunk fine
- Page-touch every 4 KB is good (forces commit)
- `cancel()` via Job OK; `finally { releaseAll() }` OK
- Mode Shizuku/Root = readiness only (policy B) — OK if UI honest

### Correct pressure model (for iter 3)

To move **system** MemAvailable toward pressure:

1. Allocate **anonymous committed RSS** that is **not** limited to Java heap max (or raise process budget massively).
2. Prefer order:
   - **A.** `ByteBuffer.allocateDirect` / largeHeap + accept heap limit still ~512 MB–1 GB — *still often insufficient alone*
   - **B.** Native `mmap(MAP_ANONYMOUS|MAP_PRIVATE)` via tiny JNI / `Os.mmap` — outside Java heap; process RSS can grow until LMK or RLIMIT
   - **C.** **Separate `:ramfiller` process** (revisit YAGNI): UI stays alive; filler process absorbs OOM/LMK; classic cleaner design; matches "never kill UI"
3. Measure stop against **MemAvailable / system used%**, not heap usage.
4. **Swap stop:** change to soft/secondary — do **not** let swap alone abort while RAM used% still ≪ 90%.

**Recommend iter 3 default:** RAM-only hard stop at 90%; swap as readout only. Native/direct alloc or `:ramfiller` process required for real pressure. Decision 8 (no separate process) **conflicts with "never kill UI" + multi-GB fill** — pick one:

| Option | UI safety | Can hit 90% system | Complexity |
|--------|-----------|--------------------|------------|
| Heap-only in UI (current) | High | **No** | Low |
| Native mmap in UI | Medium (UI process OOM risk) | Partial–yes | Med |
| `:ramfiller` process + bind/AIDL | High | Yes | Higher |
| Pre-freeze only (no fill) | High | N/A — different product | Low |

Product goal needs real fill → **recommend unlock `:ramfiller` process** OR native mmap with careful cancel/release.

If process stays YAGNI: **honest product pivot** — claim "app memory pressure assist" not "fill system to 90%"; rely on pre-purge for reclaim. Do **not** keep 90% system gauge as success metric.

### Current state in ApexCore (post iter 2)

| Piece | Status |
|-------|--------|
| `getSystemMemStats` / `/proc/meminfo` | Exists — reused (`ramUsed = MemTotal - MemAvailable`) |
| `SimpleMemoryDisplay` + purge anim | Home BOOST |
| `FreezeFramework` + filter | Pre-step wired |
| Home global backend dropdown | Shipped + cold-start prefs |
| RAM fill | **Shipped but ineffective** (heap + swap gate) |
| RAM Free page | UI shipped; phases run; pressure fake/weak |

### Privilege model

```
Dropdown options (detect readiness each row):
  Standard  — always ready  [DEFAULT]
  Shizuku   — ready if binder + permission
  Root      — ready if su works

Home global dropdown  → preferred freeze/boost backend
RAM Free dropdown     → fill mode (Standard always works; extras not implemented — UI says so)

Never disable FREE RAM waiting for privilege.
Never auto-kill system packages.
```

### Kill / safety policy (non-negotiable)

| Allowed | Forbidden |
|---------|-----------|
| OS LMK reclaim of cached apps | Force-kill ApexCore UI / own process (unless deliberate separate filler process dies) |
| Freeze pre-step via existing filter (user apps) | Kill system / `FLAG_SYSTEM` pure system pkgs |
| Release own alloc buffers | `drop_caches` without root confirm (Root-only, best-effort, optional) |
| Cancel on leave page | Background silent fill without UI |

### 90% safe limits (intended)

```
ramStop  = ramTotalKb * 0.90
swapStop = swapTotalKb * 0.90   // REVISE: do not sole-abort on swap for V1

While filling:
  if ramUsedKb >= ramStop → stop fill (HOLD)
  // iter3: swap alone must NOT stop while ramUsedKb << ramStop
  if OOM on next chunk → stop fill (HOLD)
  if elapsed > 15s → stop fill (HOLD)
  if cancelled → RELEASING immediately
  if process RSS / native budget exhausted → HOLD (log reason)
```

### What already exists to reuse

| Asset | Notes |
|-------|--------|
| `getSystemMemStats` | `MainActivity.kt` |
| `SimpleMemoryDisplay` | `ui/components/Sonogram.kt` |
| Theme tokens | `AccentPrimary`, `AccentWarning`, `SurfaceCard`, mono fonts |
| `FreezeFramework.freezeAll` | Pre-step |
| `FreezeFilter` | Ensures freeze path skips system / self |
| `SetupDialog` | Deep link from dropdown if Shizuku not ready |
| Nav patterns | `GamesScreen`, `AnimatedContent` |

### Relevant code map

| File | Role |
|------|------|
| `MainActivity.kt` | Home, tabs, mem stats, **global dropdown**, navigate RAM Free |
| `freeze/*` | Pre-step + readiness detect + preferred backend |
| `ui/components/Sonogram.kt` | Viz primitives / tokens |
| `ram/RamFillerManager.kt` | **Broken core fill** |
| `ram/RamFreeScreen.kt` | UI (mostly OK) |
| `ram/RamFillMode.kt` / `RamFillResult.kt` | Types |

---

## Files to change

| Action | File |
|--------|------|
| **NEW** | `app/src/main/kotlin/com/ivarna/apexcore/ram/RamFillerManager.kt` |
| **NEW** | `app/src/main/kotlin/com/ivarna/apexcore/ram/RamFillMode.kt` |
| **NEW** | `app/src/main/kotlin/com/ivarna/apexcore/ram/RamFillResult.kt` + progress types |
| **NEW** | `app/src/main/kotlin/com/ivarna/apexcore/ram/RamFreeScreen.kt` |
| **MODIFY** | `MainActivity.kt` — Home entry, global backend dropdown, show `RamFreeScreen` |
| **MODIFY** | `freeze/FreezeBackendResolver.kt` — user-selected backend preference |
| **ITER 3** | `RamFillerManager` pressure path (native/direct and/or process); swap gate; headroom; diagnostics |
| **ITER 3 maybe** | `AndroidManifest.xml` — `largeHeap` and/or `:ramfiller` process |
| **NO (still)** | FGS for fill unless process route needs it |

---

## Approach

### 1. `RamFillerManager` (core) — intended

```
suspend fun run(...): RamFillResult
fun cancel()
```

**Phases:** PRE_FREEZE → snapshot before → FILLING → HOLDING ~400ms → RELEASING → sleep ~400ms → DONE with honest freedKb.

### 2. Survive without FGS

- Fill only while `RamFreeScreen` RESUMED; pause → cancel; keep screen on while filling; release in `finally`.

### 3. `RamFreeScreen` UI

- Full screen; bottom nav hidden; gauge; RAM/swap/MemAvailable; mode dropdown; pre-purge; FREE RAM / CANCEL; result card; footer.

### 4. Home entry + global dropdown

- **RAM FREE** under BOOST; global backend dropdown + prefs + cold-start apply.

### 5. Do not change

- Freeze filter safety; Games/overlay; fake marketing numbers.

---

## Edge cases

| Case | Behavior |
|------|----------|
| Hit 90% RAM | Stop fill → hold → release |
| Hit 90% swap only | **iter3:** do not sole-stop if RAM still low |
| OOM mid-chunk | Catch → hold → release → measure |
| User backs out / backgrounded | Cancel + full release |
| Pre-freeze kills 0 | Still run fill |
| Honest zero free | "No reclaim detected" |
| Heap/process budget hit first | Stop, log `stopReason=BUDGET`, still measure |

---

## Test plan

### Test channels (locked)

| Path | How to test | What proves |
|------|-------------|-------------|
| **Standard** | **In-app only** — open RAM Free, FREE RAM, watch gauge / MemAvailable / result | Fill loop, cancel, pause, pre-purge, honest delta on device UI |
| **Shizuku** | **ADB + Shizuku ready** — grant/bind via adb, exercise freeze pre-step + any fill extras through shell/logcat | Backend detect Ready; pre-purge uses Shizuku; no system kills; fill still runs |
| **Root** | **ADB + `su` ready** — root shell checks + in-app mode Root when su works | Backend detect Ready; pre-purge/root extras best-effort; never system pkg kills; fill still runs |

Rules:

- Standard **must not** require adb for acceptance (user path).
- Shizuku/Root **logic** (detect, preferred backend, freeze pre-step, future extras) **must** be proven with **adb** (logcat + shell), not UI click theater alone.
- Privilege paths never block FREE RAM when falling back to Standard alloc.

### Standard — in-app (manual)

1. Cold start app → Home → **RAM FREE** (bottom nav hidden; back restores nav).
2. Mode **Standard**, pre-purge **OFF** → FREE RAM → phases run; MemAvailable drops during FILLING (hundreds of MB+ after iter 3 pressure fix) or honest BUDGET stop; UI stays alive.
3. Pre-purge **ON** → freeze then fill; more or equal free vs fill-only when background apps exist.
4. Cancel mid-fill → buffers gone; mem rebounds; no crash.
5. Home / recents mid-fill → cancel path; no silent continue.
6. Already-high RAM device → stop at ~90% or BUDGET; no hang.
7. Busy zRAM → fill still allocates (swap must not no-op).
8. Result card: `+N MB` or "No reclaim detected" only when true.
9. Repeat FREE RAM 5× → no progressive leak / crash.

### Shizuku — adb (+ app for CTA)

Prereq: Shizuku installed; wireless/adb pairing as device requires.

```bash
# binder / permission smoke (adjust for device Shizuku version)
adb shell dumpsys package moe.shizuku.privileged.api | head
# start app, open RAM Free, select Shizuku when Ready
adb logcat -c
adb logcat -s ApexCore.RamFiller:I ApexCore.Freeze:I | tee /tmp/t9-shizuku.log
# In app: mode Shizuku, pre-purge ON, FREE RAM
# After run:
grep -E "Pre-freeze|Resolved|Shizuku|run\(\)|Stop:|After fill" /tmp/t9-shizuku.log
```

Assert:

1. Mode row shows **Ready** when binder+permission OK; **Not available** otherwise (not selectable as active without setup path).
2. Pre-purge logs Shizuku (or preferred) backend; **no** system/privileged package in kill list.
3. Fill path still executes after pre-freeze (not stuck on privilege).
4. If Shizuku dies mid-run → no crash; fallback or honest fail; buffers released.
5. Home global dropdown: pick Shizuku when ready → cold restart → `setPreferredBackend` + detect prefers Shizuku (log).

### Root — adb (+ app for CTA)

Prereq: device `su` works for shell uid used by app backends.

```bash
adb shell su -c id
adb logcat -c
adb logcat -s ApexCore.RamFiller:I ApexCore.Freeze:I | tee /tmp/t9-root.log
# In app: mode Root when Ready, pre-purge ON, FREE RAM
grep -E "Pre-freeze|Resolved|Root|run\(\)|Stop:|After fill|drop_caches" /tmp/t9-root.log
```

Assert:

1. Mode **Root** Ready only when `su` works; else Not available.
2. Pre-purge/root path never force-kills system packages (log assert).
3. Optional Root extras (`drop_caches` if added) best-effort only; failure non-fatal.
4. Fill still runs; cancel/release still clean.
5. Home global dropdown Root preference survives cold start when su ready.

### Cross-backend adb checks (all modes)

```bash
# mem pressure sample during a run (second shell)
adb shell "grep -E 'MemTotal|MemAvailable|SwapTotal|SwapFree' /proc/meminfo"
# repeat every ~1s while FILLING; expect MemAvailable drop for real pressure

# stop reason / budget (after iter 3)
adb logcat -d -s ApexCore.RamFiller:I | grep -E "stopReason|peakAllocated|headroom|OOM|timeout"
```

| Check | Standard (in-app) | Shizuku (adb) | Root (adb) |
|-------|-------------------|---------------|------------|
| FREE RAM completes | yes | yes | yes |
| Pre-purge backend correct | N/A / fallback | Shizuku logs | Root logs |
| No system kills | freeze filter | freeze filter + log | freeze filter + log |
| MemAvailable moves (iter 3) | visual + optional adb | adb meminfo | adb meminfo |
| Cancel / pause release | in-app | in-app + log | in-app + log |
| Preferred backend cold start | dropdown Standard | adb log after reboot app | adb log after reboot app |

### Regression (any mode)

1. Bottom nav hidden on RAM Free; restored on back.
2. Concurrent BOOST on Home blocked or cancelled first.
3. Mode honesty: until fill extras exist, Shizuku/Root must not claim different *fill* behavior (pre-purge backend may differ).

---


## Out of scope (YAGNI) — revised

- Scheduled / boot cleaners
- Accessibility-based fill
- 4th bottom-nav tab
- Killing system processes
- Guaranteed multi-GB marketing claims
- FGS (unless process route needs it)

**Unlocked for iter 3 evaluation:** `:ramfiller` secondary process — required candidate if UI-process native still cannot pressure without killing UI.

---

## Implementation order

1. ~~Types + Manager + Screen + nav + global dropdown~~ iter 1
2. ~~Single-flight, cold-start, headroom, 15s, ByteArray release, MemAvailable, navBars, mode B~~ iter 2
3. **iter 3 — fix real pressure** (below)
4. Device matrix + logcat proof

---

## Iteration 1

### Shipped

| Piece | Location |
|-------|----------|
| Types + Manager + Screen | `ram/*` |
| Nav + Home entry + global dropdown | `MainActivity.kt` |
| Compile | pass |

### Review (iteration 1) — **CHANGES_REQUESTED**

| Sev | Issue |
|-----|--------|
| MAJOR | Concurrent double-tap `run()` race |
| MAJOR | Pref backend not applied on cold start |
| MAJOR | Last chunk can overshoot 90% |
| MAJOR | Direct buffer release weak / leak risk |
| MAJOR | 15s cap fill-only, not full `run()` |
| MAJOR | Mode extras not wired |
| MINOR | No MemAvailable; missing navBars; CTA guard; no unit tests |

---

## Iteration 2

### Shipped (checklist)

| # | Item | Status |
|---|------|--------|
| 1 | Single-flight `run()` + CTA while running | **DONE** |
| 2 | Cold-start preferred backend before `detect()` | **DONE** |
| 3 | 90% last-chunk headroom sizing | **PARTIAL** — refuses if headroom < 8 MB; swap min breaks RAM fill |
| 4 | 15s wall-clock from `run()` start | **DONE** |
| 5 | Heap `ByteArray` + `chunks.clear()` in `finally` | **DONE** (and why pressure fails) |
| 6 | Mode policy B (honest Standard fill) | **DONE** |
| 7 | MemAvailable mono readout | **DONE** |
| 8 | `navigationBarsPadding` | **DONE** |
| 9 | Device smoke for real fill | **FAIL** — user: RAM not filling |

### Review (iteration 2) — **CHANGES_REQUESTED**

| Sev | Issue | Evidence |
|-----|--------|----------|
| **BLOCKER** | **System RAM never meaningfully fills** | Heap-only `ByteArray`; no `largeHeap`; no native/direct/process. Free multi-GB device: used% barely moves; LMK never fires. |
| **BLOCKER** | **Swap 90% / swap headroom aborts fill** | `min(ramHeadroom, swapHeadroom)` + early break; busy zRAM → zero chunks. |
| **MAJOR** | Last chunk min 8 MB vs remaining headroom | Stops instead of allocating 1–7 MB remainder. |
| **MAJOR** | Success metric wrong | Gauge/stop use system used% but alloc cannot drive it. |
| **MAJOR** | No `stopReason` / heap budget logs | Cannot distinguish timeout vs swap-stop vs OOM vs already-at-cap. |
| **MINOR** | 15s includes pre-freeze | Slow freeze starves fill (log if so). |
| **MINOR** | Mode still shows Shizuku/Root selectable | OK with honesty line; extras no-ops. |
| **PASS** | Nav hide bottom nav, back, pre-purge, cold-start prefs | Meets chrome. |
| **PASS** | Cancel on pause / back; single-flight | Wired. |

### Verdict

UI shell + safety checklist mostly met. **Core product promise (pressure fill → LMK reclaim) not effective.** Do not ship. Iteration 3 must fix pressure path + stop conditions, then re-test on device.

---

## Iteration 3

### Shipped (code present)

| Piece | Location | Notes |
|-------|----------|-------|
| `:ramfiller` process service | `ram/RamFillerService.kt` + Manifest `android:process=":ramfiller"` | Binder/Messenger IPC |
| Manager bind + pre-freeze budget | `ram/RamFillerManager.kt` | freeze soft 8s; bindAndWait |
| Direct buffer alloc | `ByteBuffer.allocateDirect` + page touch 4KB | Not heap ByteArray |
| `largeHeap=true` | `AndroidManifest.xml` application | Applies app-wide |
| `StopReason` + UI BUDGET copy | `RamFillResult.kt` / `RamFreeScreen.kt` | |
| Swap sole-stop removed | Service loop | RAM-only hard stop |

### Review (iteration 3) — **CHANGES_REQUESTED** (3rd review)

Field: **RAM still does not fill / nothing useful happens.**

| Sev | Issue | Evidence |
|-----|--------|----------|
| **BLOCKER** | **Cancel path never reaches fill loop** | `RamFillerService` handles `MSG_START`/`MSG_CANCEL` on **same** `HandlerThread`. `handleStart()` is a long **blocking** loop (`Thread.sleep`, alloc). `MSG_CANCEL` **queued behind** start → `cancelled` flag never set during fill. Cancel only works via `runJob.cancel()` → unbind → `onDestroy`. |
| **BLOCKER** | **Manager timeout returns null → reports 0 alloc CANCEL** | `bindAndWait`: on timeout/`withTimeout`/`onServiceDisconnected` completes `null`. Manager then sets `peakAllocatedMb=0`, `stopReason=CANCEL` even if service allocated GBs. Freeze can leave **remainingBudget ≈ 7s**; service internal timeout 15s — UI gives up early and **unbinds**, killing pressure. |
| **BLOCKER** | **First chunk 128 MB direct — too large / kill-prone** | `CHUNK_MAX = 128 * 1024 * 1024`. One `allocateDirect(128MB)` + full page-touch often **OOM** or **LMK-kills `:ramfiller`** before progress; kill → `onServiceDisconnected` → null → zero result. No **halve-and-retry**. |
| **MAJOR** | **No progressive chunk fallback** | OOM on chunk → immediate stop. Should retry 64→32→16→8→4 MB. |
| **MAJOR** | **Direct buffer free is GC-dependent** | `chunks.clear()` only; native memory may lag. Prefer `sun.misc.Unsafe`/cleaner/`Os.munmap` if switching to mmap. |
| **MAJOR** | **Already-high system used% → instant RAM_CAP 0 chunks** | `ramUsed = MemTotal - MemAvailable`. Android often **already ≥85–95%** by this metric (cache). Loop hits `ramPercent >= 0.90` **before any alloc**. UI flashes DONE / No reclaim — looks broken. Need either: pressure until **MemAvailable floor** (e.g. allocate while avail > max(10% total, 200MB)), or show "already at cap" honestly with pre-purge-only. |
| **MAJOR** | **IPC progress may never paint** | If service dies / OOM on chunk 1 / immediate RAM_CAP, user never sees FILLING phase with rising allocatedMb. |
| **MINOR** | Cancel from `ON_PAUSE` still aggressive | Correct per plan; ensure bind/start does not trigger pause. |
| **PASS** | Manifest `:ramfiller` + `largeHeap` | Present |
| **PASS** | Swap not sole-stop | Service uses RAM only for stop |
| **PASS** | Page touch every 4KB | Present |
| **PASS** | stopReason enum + BUDGET UI | Present |

### Root cause summary (why still no fill)

```
User taps FREE RAM
  → Manager pre-freeze (up to 8s)
  → bind :ramfiller + MSG_START
  → Service either:
      (A) ramPercent already ≥90% → 0 chunks, RAM_CAP  (common on Android)
      (B) allocateDirect(128MB) OOM / process LMK → disconnect → UI CANCEL 0
      (C) alloc starts but Manager timeout (short remainingBudget) → null → unbind kill
      (D) rare: bind fails → null
  → User sees phases brief or "Cancelled" / "No reclaim" / tiny peak
  → System gauge barely moves
```

Architecture direction (process + direct) is right. **Execution still fails** on timeout/IPC result handling, chunk strategy, and stop metric edge cases.

---

## Iteration 4

### Shipped (code present)

| Piece | Location | Notes |
|-------|----------|-------|
| `:ramfiller` concurrency fix | `ram/RamFillerService.kt` | Fill loop on separate `Thread`; `AtomicBoolean cancelled`; `MSG_CANCEL` interrupts immediately |
| Chunk shrink-retry | `ram/RamFillerService.kt` | `CHUNK_START=16MB`, `CHUNK_MAX=64MB`, halve on OOM to `PAGE_SIZE` floor; grow on success |
| Manager bind contract | `ram/RamFillerManager.kt` | `remainingBudget >= 5s`; passes `KEY_TIMEOUT_MS`; waits for `MSG_DONE`; synthesizes from last progress on timeout/disconnect |
| Stop condition honesty | `ram/RamFillerService.kt` | `availFloorKb = max(total*0.10, 256MB)`; hard 95% cap; `RAM_CAP` at 0 chunks |
| UI already-at-cap copy | `ram/RamFreeScreen.kt` | Dedicated result card: "Already at 90% safe cap" when `RAM_CAP && chunkCount==0` |
| Log proof | `ram/RamFillerService.kt` | Per-chunk log: size, cumulative, avail, ramPercent; final log: stopReason, peak, chunks |

### Review (iteration 4) — **PENDING**

Device proof required before sign-off:
- Standard in-app: `peakAllocatedMb >= 64` on free mid-range device OR clear Already-at-cap copy if system already >= 90%.
- `adb logcat -s ApexCore.RamFiller:I` shows multiple chunk lines and rising allocatedKb.
- Busy zRAM: still allocates (no swap gate).

### Research locks

1. **Never complete UI with null/0 when service may have worked** — always wait for MSG_DONE or explicit cancel with last known peak.
2. **Cancel must be concurrent** — dedicated cancel flag set from binder thread OR separate cancel Handler, not blocked behind `handleStart`.
3. **Chunk strategy: start small (8–16 MB), grow, on OOM shrink and retry** — never open with 128 MB only.
4. **Stop metric: if already ≥90% used, still run pre-purge; for fill either no-op with clear UI "Already at safe cap" OR switch target to "drive MemAvailable down by N MB / to floor" so free devices with high cache still pressure anonymous RSS.
5. **Manager wall budget must cover full fill** — pass remaining ms into service; do not unbind before MSG_DONE unless cancel; service timeout = remaining budget.

### Checklist

1. **Service concurrency**
   - `AtomicBoolean cancelled` set immediately in `onBind` path / `Messenger` callback for MSG_CANCEL **without** waiting for fill loop to finish current message.
   - Run fill loop on **separate** executor/thread from the Messenger Handler so MSG_CANCEL is always handled.
   - On cancel: set flag, releaseAll, sendDone(CANCEL, lastPeak…).

2. **Chunk alloc robustness**
   - `preferredChunk` start **16 MB** (or 8 MB low-RAM); max 64 MB optional growth.
   - On `OutOfMemoryError` / failed alloc: `preferredChunk /= 2`; if `>= 4KB` retry; else stop OOM.
   - Log each successful chunk size + running total.

3. **Manager bindAndWait contract**
   - On timeout: send MSG_CANCEL, **wait briefly for MSG_DONE**, use that result (not null zeros).
   - On disconnect mid-run: stopReason=BUDGET/OOM style, not silent CANCEL 0 if progress was seen.
   - `remainingBudget = max(TIMEOUT - elapsed, 5000)` minimum fill window after freeze; pass budget to service as `KEY_TIMEOUT_MS`.
   - Do not `stopService`/unbind until DONE processed or cancel ack (except process death).

4. **Stop condition honesty**
   - If `ramPercent >= 0.90` **before first chunk**: stopReason=`RAM_CAP`, chunkCount=0, UI: **"Already at 90% safe cap"** (not empty failure).
   - Optional improvement (recommended): fill while `MemAvailableKb > availFloor` where `availFloor = max(ramTotal*0.10, 256MB)` — equivalent pressure goal, works when "used%" already high due to cache accounting.
   - Keep hard safety: do not intentionally exceed ~90% used / do not kill UI process.

5. **Prove pressure in logs**
   - Every chunk: `allocMb cumulative, MemAvailableKb, ramPercent, chunkBytes`.
   - Final: `stopReason, peakAllocatedMb, chunkCount, peakRamPercent`.
   - UI always shows peakAllocatedMb + stopReason on Done (already partial).

6. **Regression**
   - Single-flight, cancel on back/pause, pre-freeze 8s soft, no system kills, Standard in-app test, Shizuku/Root adb log tags.

7. **Device proof (blocking)**
   - Standard in-app: during FILLING, **peakAllocatedMb ≥ 64** on free mid-range device OR clear Already-at-cap copy if system already ≥90%.
   - `adb logcat -s ApexCore.RamFiller:I` shows multiple chunk lines and rising allocatedKb.
   - Busy zRAM: still allocates (no swap gate).

### Out of iteration 4

- Fake GB claims
- FGS unless process still LMK'd while bound from resumed Activity (then revisit FGS type carefully)
- Full unit tests

---

## Open questions (iter 4)

**None blocking** if implementer follows checklist defaults:
- Concurrent cancel handler + small-chunk retry + manager waits for DONE + honest already-at-cap / MemAvailable floor.

Residual: if bound `:ramfiller` still LMK'd under load, add lightweight notification FGS **only in filler process** for duration of fill (revisit).

---

## References

- Todo: `docs/todo/todo.md` T9
- Design: `docs/design.md`
- Freeze: `docs/freeze-architecture.md`, `FreezeFramework`, `FreezeFilter`
- Plan format: `docs/plan/T8-manual-game-addition.md`
- Mem: `getSystemMemStats` in `MainActivity.kt`
- Manager: `ram/RamFillerManager.kt` (iter 2)
- Screen: `ram/RamFreeScreen.kt`
