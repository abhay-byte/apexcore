# T10b — Overlay BOOST + pin apps (freeze whitelist)

| Field | Value |
|-------|-------|
| **ID** | T10b (part of T10) |
| **Parent** | [T10 ship readiness](T10-ship-readiness.md) |
| **Type** | feature |
| **Priority** | high |
| **Difficulty** | medium |
| **Branch** | `T10b-overlay-pin-apps` (suggested) |
| **Status** | plan draft — awaiting approval |
| **Depends on** | **T10a** (honest freeze path + stats) — preferred; can parallel only if freezeAll already usable |
| **Unblocks** | T10c regression (overlay + pin cases) |

## Todo source (proposed slice)

```yaml
- id: T10b
  title: Overlay BOOST button + freeze whitelist (pin apps)
  type: feature
  priority: high
  difficulty: medium
  status: pending
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

---

## Research (this slice)

### CRITICAL-2 — Overlay button fake

`GameOverlayService` `onUnfreezeClick`:

```text
Toast "SYSTEM DEFROSTED — ACCELERATION RESET"
// Simulate unfreezing action
```

T5 review listed BOOST button; UI is snowflake Unfreeze with **no** `FreezeFramework` call.

### CRITICAL-4 — No whitelist

`FreezeFilter.default` only skips:

- self package  
- pure system  
- already `FLAG_STOPPED`

No user pins. Elevated from problem-statement OOS by product decision.

### Code map

| File | Role |
|------|------|
| `games/GameOverlayService.kt` | BOOST CTA + freeze call |
| `games/GameLauncher.kt` | freeze filter exclude game (+ pin) |
| `freeze/FreezeFilter.kt` | pin check |
| `freeze/FreezeFramework.kt` | freezeAll (consume filter only) |
| `ram/RamFillerManager.kt` | pre-freeze uses freezeAll — inherits filter |
| `MainActivity.kt` | Home BOOST inherits filter |
| `games/GameManager.kt` / picker | reuse enumeration patterns for pin UI |
| **NEW** `freeze/WhitelistStore.kt` | SharedPrefs pin set |
| **NEW** pin UI dialog/screen | TBD placement |

### Call sites that must honor pin

| Entry | Filter today | After T10b |
|-------|--------------|------------|
| Home BOOST | `FreezeFilter.default` | default + not pinned |
| Game launch | default + not gamePkg | + not pinned |
| Overlay BOOST | none (fake) | default + not gamePkg + not pinned |
| RAM Free pre-freeze | default | default + not pinned |
| FREEZE_ALL receiver | default | default + not pinned |

---

## Scope

### In

- Overlay CTA label **BOOST**  
- Real `freezeAll` from overlay service scope  
- `WhitelistStore` + filter integration  
- Minimal pin UI (list + toggle)  
- Game launch + all freeze entry points use updated filter  

### Out

- Freeze backend fixes / adb matrix → **T10a**  
- RAM Free fill algorithm → **T10a** / T9 residual  
- Play compliance, privacy, receiver export → **T10c**  
- Tags, boot freeze, per-game overlay settings, FPS target, governor  
- Full Accessibility automation  

---

## Approach

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

Persistence: `SharedPreferences` string set. Default pins: **empty** (safer; open question if pre-pin messengers).

### 2. FreezeFilter

```kotlin
fun default(context: Context, pkg: ApplicationInfo): Boolean {
    // existing self / pure system / stopped checks
    if (WhitelistStore.isPinned(context, pkg.packageName)) return false
    return true
}
```

Game launch / overlay add:

```kotlin
appInfo.packageName != gamePkg && FreezeFilter.default(context, appInfo)
```

### 3. Pin UI (minimal)

Reuse T8 patterns (`getAllInstalledApps` / picker chrome):

- Full-screen or dialog: installed user apps, search, pin toggle  
- Show pin count  
- Entry points (pick one in Decisions): Setup / Home gear / Games settings  

V1: pin only. No tags. No "freeze these only" inverse list.

### 4. Overlay BOOST

1. Rename bottom CTA from Unfreeze snowflake → **BOOST** (icon optional lightning / power).  
2. On click (single-flight):

```kotlin
scope.launch(Dispatchers.IO) {
    val result = FreezeFramework.freezeAll(appContext) { info ->
        info.packageName != gamePkg
            && FreezeFilter.default(appContext, info)
    }
    withContext(Main) {
        Toast.makeText(
            this@GameOverlayService,
            if (result.killed == 0 && result.freedKb == 0)
                "Already optimized"
            else
                "BOOST · ${result.killed} apps · +${result.freedKb / 1024} MB",
            Toast.LENGTH_SHORT
        ).show()
    }
}
```

3. Disable CTA while running.  
4. Keep FPS / RAM sparkline / CPU HUD.  
5. No second Activity; work in service coroutine scope (`SupervisorJob`).  
6. If freeze init missing: `FreezeFramework.init(applicationContext)` in service `onCreate`.

### 5. Do not break overlay lifecycle

- Keep FGS notification + `specialUse`  
- BOOST must not crash when overlay permission only (no Shizuku) — relies on T10a Standard path  
- Exit watcher / game package on top logic unchanged  

---

## Files to change

| Action | File |
|--------|------|
| **NEW** | `freeze/WhitelistStore.kt` |
| **NEW** | pin UI (`WhitelistPickerDialog.kt` or similar) |
| **MODIFY** | `freeze/FreezeFilter.kt` |
| **MODIFY** | `games/GameOverlayService.kt` |
| **MODIFY** | `games/GameLauncher.kt` (if filter not only via default) |
| **MODIFY** | entry point nav (MainActivity / Setup / Games) for pin UI |
| **MODIFY** | `FreezeFilterTest.kt` + new whitelist tests |

---

## Edge cases

| Case | Handling |
|------|----------|
| Pin ApexCore | Already excluded by self; pin noop |
| Pin the active game | Excluded twice — fine |
| Overlay BOOST while Home BOOST | Ignore second or share single-flight flag |
| Empty pin list | Same as today + real BOOST |
| User pins everything | freeze targets empty → "Already optimized" |
| Uninstall pinned app | Prune on load optional (YAGNI: leave stale pkg, filter no-ops) |

---

## Test plan

### Automated

- `FreezeFilter` rejects pinned package  
- `WhitelistStore` add/remove/persist (Robolectric or simple prefs mock if available)

### Manual

1. Pin a test user app; Home BOOST; `pidof` still alive.  
2. Unpin; BOOST; process stopped (Shizuku/Root) or cached-killed (Standard).  
3. Launch game; expand overlay; BOOST; logcat `ApexCore.Freeze`; toast shows real numbers.  
4. Overlay BOOST does not freeze the game package.  
5. RAM Free pre-freeze skips pinned apps.  
6. Rotation / drag overlay during BOOST — no crash.

---

## Decisions (this slice)

| # | Decision | Lock |
|---|----------|------|
| 1 | Overlay CTA | **BOOST** → real `freezeAll` |
| 2 | Whitelist | Elevate; pin = never freeze |
| 3 | Default pins | **Empty** unless user overrides |
| 4 | Pin UI | Minimal picker; placement TBD (open Q) |
| 5 | Tags / inverse freeze list | Out of T10b |

---

## Out of scope

- Backend matrix / MemAvailable validation → T10a  
- Privacy policy, Play declarations, receiver export → T10c  
- Boot schedule, tags, `pm disable`  
- Per-game overlay settings  

---

## Implementation order

1. `WhitelistStore` + `FreezeFilter` + unit tests  
2. Wire all freeze call sites (default filter is enough if always used)  
3. Minimal pin UI + nav entry  
4. Overlay BOOST real path + toast  
5. Manual matrix (pin + overlay)  

---

## Iteration exit

- Overlay BOOST triggers freezeAll with log evidence  
- Pinned app survives freeze from Home + game launch + overlay  
- No toast-only fake unfreeze remains  

---

## Open questions

1. **Pin UI placement?** Setup dialog / Home gear / Games tab / dedicated settings row.  
2. **Default pins?** Empty (plan) vs suggest WhatsApp/Telegram/etc.  
3. **Pin UI multi-select batch** vs per-row toggle only?  

---

## References

- Parent: `docs/plan/T10-ship-readiness.md`  
- Prev: `docs/plan/T10a-freeze-matrix-ram-stats.md`  
- Next: `docs/plan/T10c-regression-play-compliance.md`  
- T5 review: `docs/review/review-T5-game-overlay.md`  
- T8 picker patterns: `docs/plan/T8-manual-game-addition.md`  
)
