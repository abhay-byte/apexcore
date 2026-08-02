# T10b — Overlay BOOST + pin apps (freeze whitelist)

| Field | Value |
|-------|-------|
| **ID** | T10b (part of T10) |
| **Parent** | [T10 ship readiness](T10-ship-readiness.md) |
| **Type** | feature |
| **Priority** | high |
| **Difficulty** | medium |
| **Branch** | `T10b-overlay-pin-apps` |
| **Status** | **iter-2 review APPROVE — code complete; manual matrix pending for full exit** |
| **Depends on** | **T10a** (Decision E: Shizuku/Root only; honest freezeAll) — merged |
| **Unblocks** | T10c regression (overlay + pin cases) |
| **Last review** | 2026-08-01 iter-2 (review-fix re-review) |

## Todo source (proposed slice)

```yaml
- id: T10b
  title: Overlay BOOST button + freeze whitelist (pin apps)
  type: feature
  priority: high
  difficulty: medium
  status: in_progress
  why: |
    Overlay action is toast-only fake unfreeze. Freeze-all has no user pin list —
    kills chat/music/nav. Need real in-game BOOST and safe freeze filter.
  really_needed: yes
  impact: GameOverlayService, FreezeFilter, WhitelistStore, pin UI, GameLauncher filter
  followups: T10c (regression + Play compliance)
  plan: docs/plan/T10b-overlay-pin-apps.md
```

---

## Goal

1. **Overlay** — final basic **BOOST** button that runs real freeze (not toast simulation).  
2. **Pin apps** — user whitelist; pinned packages **never** frozen by Home BOOST, game launch freeze, overlay BOOST, or RAM Free pre-freeze.  
3. **Honest elevation** — overlay BOOST must not claim “Already optimized” when freeze is **blocked** (no Shizuku/Root). Align with T10a Decision E.

---

## Research (this slice)

### CRITICAL-2 — Overlay button fake

`GameOverlayService` previously:

```text
Toast "SYSTEM DEFROSTED — ACCELERATION RESET"
// Simulate unfreezing action
```

T5 review listed BOOST button; UI was snowflake Unfreeze with **no** `FreezeFramework` call.

### CRITICAL-4 — No whitelist

`FreezeFilter.default` only skipped:

- self package  
- pure system  
- already `FLAG_STOPPED`

No user pins. Elevated from problem-statement OOS by product decision.

### Code map (as implemented)

| File | Role | Status |
|------|------|--------|
| `freeze/WhitelistStore.kt` | SharedPrefs pin set | **NEW done** |
| `games/WhitelistPickerDialog.kt` | Pin UI (search + toggle) | **NEW done** |
| `freeze/FreezeFilter.kt` | pin check in `default` | **done** |
| `games/GameOverlayService.kt` | BOOST CTA + real freezeAll + blocked toast | **done** |
| `games/GamesScreen.kt` | Header pin entry (Lock icon) | **done** |
| `games/GameLauncher.kt` | default + not gamePkg (inherits pin) | **no code change needed** |
| `MainActivity` / `RamFillerManager` / `FreezeReceiver` | freezeAll default filter | **inherits pin** |
| `FreezeFilterTest` + `WhitelistStoreTest` | unit tests | **done, green** |

### Call sites that honor pin (via `FreezeFilter.default`)

| Entry | Filter | Pin honored? |
|-------|--------|--------------|
| Home BOOST | `FreezeFilter.default` (implicit) | **yes** |
| Game launch | default + not gamePkg | **yes** |
| Overlay BOOST | default + not gamePkg | **yes** |
| RAM Free pre-freeze | default | **yes** |
| FREEZE_ALL receiver | default | **yes** |

---

## Scope

### In

- Overlay CTA label **BOOST** + lightning icon  
- Real `freezeAll` from overlay service scope  
- `WhitelistStore` + filter integration  
- Minimal pin UI (list + toggle) on Games tab  
- All freeze entry points honor pin via `FreezeFilter.default`  
- Honest toast when freeze backend is `blocked` (Decision E)

### Out

- Freeze backend fixes / adb matrix → **T10a**  
- RAM Free fill algorithm → T9 residual  
- Play compliance, privacy, receiver export → **T10c**  
- Tags, boot freeze, per-game overlay settings, FPS target, governor  
- Full Accessibility automation  
- Suggested default pins (WhatsApp etc.) — keep empty unless product flips later  

---

## Approach (locked)

### 1. WhitelistStore

```kotlin
// freeze/WhitelistStore.kt
object WhitelistStore {
    private const val PREFS = "apexcore_whitelist"
    private const val KEY = "pinned_packages"

    fun isPinned(context: Context, pkg: String): Boolean
    fun setPinned(context: Context, pkg: String, pinned: Boolean)
    fun allPinned(context: Context): Set<String>
}
```

Persistence: `SharedPreferences` string set. Default pins: **empty**.  
Defensive: return a **copy** from `allPinned` (`?.toSet()`), never the live prefs set.

### 2. FreezeFilter

```kotlin
fun default(context: Context, pkg: ApplicationInfo): Boolean {
    if (pkg.packageName == context.packageName) return false
    if (WhitelistStore.isPinned(context, pkg.packageName)) return false
    // stopped / pure system …
    return true
}
```

Game launch / overlay:

```kotlin
info.packageName != gamePkg && FreezeFilter.default(context, info)
```

### 3. Pin UI (minimal)

- Dialog: installable user apps (`GameManager.listInstallableApps`), search, per-row pin toggle (instant save)  
- Show pin count in subtitle  
- Entry: **Games tab header** next to “+” (Lock icon; optional “PIN” label polish)  
- V1: pin only. No tags. No inverse list.

### 4. Overlay BOOST

1. CTA label **BOOST** + lightning (not fake Unfreeze snowflake).  
2. Single-flight via `isBoosting`; disable CTA while running.  
3. `FreezeFramework.init` in service `onCreate`.  
4. Real freeze:

```kotlin
val result = FreezeFramework.freezeAll(applicationContext) { info ->
    info.packageName != gamePkg && FreezeFilter.default(applicationContext, info)
}
```

5. Toast (Decision E honest):

| Condition | Toast |
|-----------|--------|
| `result.backend == "blocked"` | `"Setup required — Shizuku or Root"` (or equivalent short copy) |
| `killed == 0 && freedKb == 0` (elevated, nothing to kill) | `"Already optimized"` |
| else | `"BOOST · ${killed} apps · +${freedKb/1024} MB"` |

6. Keep FPS / RAM sparkline / CPU HUD; do not break FGS / exit watcher.

### 5. Overlay lifecycle + elevation

- Keep FGS notification + `specialUse`.  
- **No Standard freeze** (T10a Decision E). Without Shizuku/Root, `freezeAll` returns `backend=blocked` — must not crash and must not lie via “Already optimized”.  
- Optional UX: pre-check `FreezeFramework.isReady()` before calling freezeAll (same message).  

---

## Files

| Action | File |
|--------|------|
| **NEW** | `freeze/WhitelistStore.kt` |
| **NEW** | `games/WhitelistPickerDialog.kt` |
| **NEW** | `test/.../WhitelistStoreTest.kt` |
| **MODIFY** | `freeze/FreezeFilter.kt` |
| **MODIFY** | `games/GameOverlayService.kt` |
| **MODIFY** | `games/GamesScreen.kt` |
| **MODIFY** | `test/.../FreezeFilterTest.kt` |

---

## Edge cases

| Case | Handling |
|------|----------|
| Pin ApexCore | Already excluded by self; pin noop |
| Pin the active game | Excluded twice — fine |
| Overlay BOOST while boosting | Single-flight `isBoosting` |
| Empty pin list | Same freeze set + real BOOST |
| User pins everything | empty targets → “Already optimized” (only if elevated) |
| No elevation | toast setup required — **not** “Already optimized” |
| Uninstall pinned app | Leave stale pkg (YAGNI prune) |

---

## Test plan

### Automated (done iter-1)

- `FreezeFilter` rejects pinned package; includes unpinned  
- `WhitelistStore` empty default / add / remove / idempotent pin  
- Compile + freeze unit tests green

### Manual (pending)

1. Pin a test user app; Home BOOST; `pidof` still alive.  
2. Unpin; BOOST with Shizuku or Root; process force-stopped.  
3. Launch game; expand overlay; BOOST; logcat `ApexCore.Freeze`; toast shows real numbers.  
4. Overlay BOOST does **not** freeze the game package.  
5. RAM Free pre-freeze skips pinned apps.  
6. **No elevation:** overlay BOOST → setup toast (not “Already optimized”); no crash.  
7. Drag overlay / collapse during BOOST — no crash.

---

## Decisions (this slice)

| # | Decision | Lock |
|---|----------|------|
| 1 | Overlay CTA | **BOOST** → real `freezeAll` |
| 2 | Whitelist | pin = never freeze |
| 3 | Default pins | **Empty** |
| 4 | Pin UI | Games tab header — locked |
| 5 | Tags / inverse freeze list | Out of T10b |
| 6 | No elevation on overlay | Honest toast / no fake success (**Decision E**) |

---

## Out of scope

- Backend matrix / MemAvailable validation → T10a  
- Privacy policy, Play declarations, receiver export → T10c  
- Boot schedule, tags, `pm disable`  
- Per-game overlay settings  

---

## Implementation order

1. ~~`WhitelistStore` + `FreezeFilter` + unit tests~~  
2. ~~Wire pin via default filter (all call sites inherit)~~  
3. ~~Minimal pin UI + Games header entry~~  
4. ~~Overlay BOOST real path + single-flight~~  
5. ~~Review fix: blocked-backend toast honesty + `allPinned` copy + PIN label~~  
6. Manual matrix (pin + overlay + no-elevation) — **pending device**

---

## Iteration 1 — shipped

| Piece | Location | Notes |
|-------|----------|-------|
| WhitelistStore | `freeze/WhitelistStore.kt` | SharedPrefs; empty default |
| Pin in filter | `FreezeFilter.default` | early `isPinned` → false |
| Pin UI | `WhitelistPickerDialog.kt` | search, toggle, pin count |
| Games entry | `GamesScreen` header Lock | opens picker |
| Overlay BOOST | `GameOverlayService` | freezeAll + not game + filter; lightning; `isBoosting` |
| Init freeze | overlay `onCreate` | `FreezeFramework.init` |
| Tests | `FreezeFilterTest`, `WhitelistStoreTest` | BUILD SUCCESSFUL |

### Review (iteration 1) — **CHANGES_REQUESTED**

| Sev | Issue | Evidence |
|-----|--------|----------|
| **MAJOR** | Overlay toast lies when freeze is blocked | `killed==0 && freedKb==0` → `"Already optimized"`. Decision E `freezeAll` returns `backend="blocked"` with zeros → same message as a successful no-op. Home gates with `isReady()` + setup dialog; overlay must be honest. |
| **MINOR** | `allPinned` returns prefs live set | `getStringSet` without copy; callers that mutate could corrupt prefs. `setPinned` already copies; harden `allPinned` with `?.toSet()`. |
| **NIT** | Pin entry is Lock icon only | Plan said “PIN button”; discoverability. Optional: small “PIN” label or pin icon. Non-blocking. |
| **PASS** | Real overlay freezeAll (no toast-only fake) | `FreezeFramework.freezeAll` + filter |
| **PASS** | Pin honored on all freezeAll default sites | Home / game / overlay / RAM / receiver |
| **PASS** | Single-flight BOOST | `isBoosting` + clickable disabled |
| **PASS** | Unit tests | freeze package tests green; compile OK |
| **PASS** | Pin UI placement | Games header as locked |

### Verdict

Core T10b feature is in place. **Do not call exit until:** (1) blocked-backend toast fixed, (2) manual matrix run (at least pin survival + overlay BOOST with elevation).

---

## Residual work (iter-1.1) — **done in iter-2**

| # | Item | Status |
|---|------|--------|
| 1 | MAJOR blocked toast honesty | **done** — `"BOOST needs Shizuku or Root — open setup"` |
| 2 | MINOR `allPinned` defensive copy | **done** — `?.toSet()` |
| 3 | NIT PIN label on Games header | **done** — Lock + “PIN” |
| 4 | Manual matrix | **pending** (device) |

---

## Iteration 2 — review-fix re-review — **APPROVE**

| Sev | Issue | Disposition |
|-----|--------|-------------|
| **MAJOR** (iter-1) | blocked → “Already optimized” | **Fixed** — `when { backend == "blocked" → …; zero → Already optimized; else BOOST line }` |
| **MINOR** (iter-1) | live prefs set from `allPinned` | **Fixed** — `getStringSet(...)?.toSet() ?: emptySet()` |
| **NIT** (iter-1) | Lock without PIN | **Fixed** — Column Lock + “PIN” label |
| **PASS** | Real overlay freezeAll | unchanged, still correct |
| **PASS** | Pin via `FreezeFilter.default` all sites | unchanged |
| **PASS** | Single-flight + init | unchanged |
| **PASS** | Unit tests / compile | BUILD SUCCESSFUL freeze package |

### Residual (non-blocking for code approve)

- **Manual matrix** still open (pin survival, overlay BOOST numbers, no-elevation toast, no crash on drag). Needed for full **iteration exit**, not for code merge readiness if you accept device QA after PR.

### Verdict

**Approve** code for commit/PR. No further worker code pass required for T10b unless manual matrix finds a bug.

---

## Iteration exit

- [x] Overlay BOOST triggers freezeAll (code)  
- [ ] Pinned app survives freeze — **manual**  
- [x] No toast-only fake unfreeze  
- [x] No “Already optimized” when `backend=blocked`  
- [x] Unit tests green  
- [ ] Device manual matrix complete  

---

## Open questions

1. ~~**Pin UI placement?**~~ → **Locked: Games tab header.**  
2. ~~**Default pins?**~~ → **Empty** (locked for V1).  
3. ~~**Batch multi-select?**~~ → V1 per-row toggle only.  

---

## References

- Parent: `docs/plan/T10-ship-readiness.md`  
- Prev: `docs/plan/T10a-freeze-matrix-ram-stats.md` (Decision E)  
- Next: `docs/plan/T10c-regression-play-compliance.md`  
- T5 review: `docs/review/review-T5-game-overlay.md`  
- T8 picker patterns: `docs/plan/T8-manual-game-addition.md`  
