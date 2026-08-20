# T12 addendum — Game Optimisation option catalog (rev 4)

| Field | Value |
|-------|-------|
| **Parent** | [`T12-real-game-optimisation.md`](T12-real-game-optimisation.md) |
| **Date** | 2026-08-18 |
| **Status** | Normative catalog (rev 4 IDs). Shipped in `TuneId.kt`. Display/Focus Settings apply except DND is still stubbed — see T12 rev 5 remaining work and [`T12-real-game-optimisation-results.md`](T12-real-game-optimisation-results.md). |
| **Target** | Android 16 / SDK 36 |

T12 rev 3 shipped **4 user-facing effects** (and ~32 hidden sysfs aliases behind them). This addendum is the requested expansion: **as many honest, capability-gated options and categories as the researched managers actually expose**, without becoming a fake kernel manager.

Lifecycle, privilege, snapshot, overlay-less watchdog, no dummy-true migration, no Main-thread `su`, and Play honesty from T12 **still apply**. This file only changes *what the user can turn on* and *how Home presents it*.

---

## Counts

| Layer | Rev 3 | Rev 4 |
|-------|------:|------:|
| User-visible categories | 1 card (“GAME OPTIMISATION”) | **10** |
| User-visible options | **4** | **36** |
| Control kinds | switch only | switch + slider + enum |
| Catalog alias paths (approx.) | ~32 | ~70 (still probe-gated) |

**36 options / 10 categories** is the implementable inventory. A device will typically light **0–12** of them. Unavailable rows stay visible inside their category with an honest subtitle. Empty categories (zero write-verified nodes **and** no Settings API) are **hidden**, not shown as a dead header.

---

## Presentation (Home must not grow 36 switches)

| Surface | What it shows |
|---------|----------------|
| Home | One entry row: **Game optimisation** / “N available on this kernel” → opens the tune page. Dummy four-switch card is deleted (T12 PR 4). |
| Tune page | Full-screen, same pattern as RAM Free (bottom nav hidden). Category sections. Only elevated backend. |
| Footer (every category) | “Applies when you launch a game from ApexCore. Restored when the session ends. Does not disable thermal protections.” |

Control kinds:

| Kind | When |
|------|------|
| `SWITCH` | Boolean node or alias group |
| `SLIDER` | Numeric range the kernel actually exposes (Adreno 0–3, `input_boost_ms`, swappiness, uclamp, stune, read-ahead) |
| `ENUM` | Governor / I/O scheduler / TCP / sconfig — values from `availablePath` only |

`setIntent` becomes `setIntent(id, TuneValue)`. Switch OFF / slider at the kernel’s snapshotted default / enum “Default” all mean **restore that option**.

---

## Categories and options

Apply order (session budget **1500 ms**, or **2500 ms** when more than 4 intents are on — same as T12 KD-17):  
**GPU → CPU → Input → Thermal → Focus → Display → Charge → Memory → I/O → Network**.

### 1. GPU — 8 options

| # | TuneId | Title | Kind | Apply (first write-verified alias) | Source |
|---|--------|-------|------|-------------------------------------|--------|
| 1 | `GPU_FLOOR` | GPU frequency floor | SWITCH | One `gpu_min` node to ~60th percentile of available | T12 rev 3 |
| 2 | `GPU_HOLD` | GPU keep-awake | SWITCH | kgsl `force_clk/bus/rail_on=1` + `idle_timer=10000` only. **Not** `gx_game_mode` (that is `GPU_GED_GAME`) | Xtra monster / lockGPU |
| 3 | `GPU_ADRENO` | Adreno boost level | SLIDER 0–3 | `/sys/class/kgsl/kgsl-3d0/devfreq/adrenoboost` | RV `ADRENO_BOOST`; SP `Adrenoboost` |
| 4 | `GPU_GOVERNOR` | GPU governor | ENUM | `performance` only if listed **and** current is `powersave`; else leave | RV `GOV_GPU` |
| 5 | `GPU_PWRLEVEL` | GPU power floor | SLIDER | `min_pwrlevel` `max(0, current-2)` — extra, **not** a floor enabler | RV `MIN_PWRLEVEL` |
| 6 | `GPU_GED_GAME` | MediaTek game mode | SWITCH | `/sys/module/ged/parameters/gx_game_mode=1` (Root, 0440) | community GED |
| 7 | `GPU_SAMSUNG_MIN` | Samsung GPU min clock | SWITCH | `/sys/kernel/gpu/gpu_min_clock` 60th pct if present | community, probe-required |
| 8 | `GPU_SIMPLE` | Simple GPU ramp | SWITCH | `/sys/module/simple_gpu_algorithm/parameters/simple_gpu_activate=1` | SP `SimpleGPU` |

**Still never write:** `throttling`, `gx_force_cpu_boost`, `boost_gpu_enable`, `gpu_dvfs_enable`, `/dev/mali0`, `/proc/ged`, `debug.hwui.renderer`, `debug.egl.*`.

### 2. CPU and scheduling — 8 options

| # | TuneId | Title | Kind | Apply | Source |
|---|--------|-------|------|-------|--------|
| 9 | `CPU_FLOOR` | CPU frequency floor (all clusters) | SWITCH | Up to 4 `policyN/scaling_min_freq` medians | RV / XKM |
| 10 | `CPU_FLOOR_LITTLE` | Little-cluster floor | SWITCH | Lowest-`cpuinfo_max_freq` policy only | XKM cluster heuristic |
| 11 | `CPU_FLOOR_BIG` | Big-cluster floor | SWITCH | Mid cluster if ≥2 policies | same |
| 12 | `CPU_FLOOR_PRIME` | Prime-cluster floor | SWITCH | Highest-`cpuinfo_max_freq` policy if ≥3 | same |
| 13 | `CPU_GOVERNOR` | Leave powersave | SWITCH | If governor ∈ {powersave, conservative} → `schedutil` if listed | T12 rev 3 |
| 14 | `CPU_UCLAMP` | Top-app uclamp | SLIDER | `/dev/cpuctl/top-app/cpu.uclamp.min` — 0–100 or 0–1024 (see T12 KD-18) | cgroup |
| 15 | `CPU_STUNE` | Top-app schedtune | SLIDER 0–20 | `/dev/stune/top-app/schedtune.boost` write `10` default | SP `StuneBoost` |
| 16 | `CPU_STUNE_IDLE` | Prefer idle for top-app | SWITCH | `schedtune.prefer_idle=1` | SP |

If `CPU_FLOOR` is on, little/big/prime are implied and their rows stay off (disabled subtitle “Covered by CPU frequency floor”). User may use the split rows **instead of** the all-cluster switch, not both.

**Still never write:** `/proc/sys/kernel/sched_util_clamp_min` or `_max`. No `performance` governor as a default option (Xtra Monster). Optional later: `CPU_GOVERNOR_PERF` is **rejected** for v1 (cooks the phone; same as KD-6).

`CPU_BORE` (`/proc/sys/kernel/sched_bore`) is **rejected** for v1 — global scheduler personality, not a game-session floor.

### 3. Touch and input — 7 options

| # | TuneId | Title | Kind | Apply | Source |
|---|--------|-------|------|-------|--------|
| 17 | `INPUT_BOOST_EN` | Input boost | SWITCH | First of SP `cpu_boost` / `cpuboost_enable` / `input_boost_enabled` | SP `CPUBoost` |
| 18 | `INPUT_BOOST_MS` | Input boost duration | SLIDER 40–128 | `input_boost_ms` or RV `cpu_boost/input_boost_ms`; `max(current, 64)` cap 128 | SP / RV |
| 19 | `TOUCHBOOST` | Touch boost | SWITCH | `/sys/module/msm_performance/parameters/touchboost` | SP |
| 20 | `CPUFREQ_BOOST` | CPU frequency boost | SWITCH | `/sys/devices/system/cpu/cpufreq/boost=1` | Linux cpufreq |
| 21 | `DEVFREQ_BOOST` | Devfreq input duration | SLIDER | `/sys/module/devfreq_boost/parameters/input_boost_duration` | SP `DevfreqBoost` |
| 22 | `SCHED_BOOST_INPUT` | Sched boost on input | SWITCH | RV `cpu_boost/sched_boost_on_input` | RV |
| 23 | `SULTAN_INPUT` | Sultan input boost | SWITCH | `/sys/kernel/cpu_input_boost/enabled` or module param | SP `CPUInputBoost` |

These **replace** the old `KERNEL` mega-bundle’s input half. Thermal is its own category.

### 4. Thermal — 1 option

| # | TuneId | Title | Kind | Apply | Source |
|---|--------|-------|------|-------|--------|
| 24 | `THERMAL_SCONFIG` | Gaming thermal profile | ENUM | Write `13` (gaming); fallback `10` if verify fails; restore snapshot | RV `BatteryScreen` `"13"` |

Known ids (RV): `0` default, `8` dialer, `10` benchmark, `11` browser, `12` camera, **`13` gaming**, `14` streaming. UI enum shows only ids the write-verify accepts; default selection is `13`.

**Never write:** `msm_thermal/.../enabled`, kgsl `throttling`, XKM Extreme `2`.

### 5. Memory — 3 options

| # | TuneId | Title | Kind | Apply | Source |
|---|--------|-------|------|-------|--------|
| 25 | `VM_SWAPPINESS` | Lower swappiness | SLIDER 1–100 | `/proc/sys/vm/swappiness` → `30` if current > 30 | RV `SWAPPINESS`; XKM `TuningConfig` |
| 26 | `VM_VFS_CACHE` | Keep file cache | SLIDER | `/proc/sys/vm/vfs_cache_pressure` → `50` if current > 50 | community / Kernel Adiutor family |
| 27 | `VM_DIRTY_RATIO` | Dirty ratio | SLIDER | `/proc/sys/vm/dirty_ratio` — only if current > 20, write `20` | RV `DIRTY_RATIO` |

**Never:** `drop_caches`, ZRAM resize, LMK adj, `am kill-all` (Xtra `clearRAM`). ApexCore already has freeze + RAM Free.

### 6. Storage I/O — 2 options

| # | TuneId | Title | Kind | Apply | Source |
|---|--------|-------|------|-------|--------|
| 28 | `IO_SCHEDULER` | I/O scheduler | ENUM | Internal disk `queue/scheduler` → `mq-deadline` or `none` if listed; else leave | SP `IO`; XKM prefs |
| 29 | `IO_READAHEAD` | Read-ahead | SLIDER 128–2048 | `queue/read_ahead_kb` → `max(current, 512)` cap 2048 | SP `IO.READ_AHEAD` |

Discover the boot/data block via `/sys/block/sd*/queue` / `mmcblk0` / `dm-0` that is writable. One device only. Restore scheduler token including brackets (`[mq-deadline]` → write `mq-deadline`).

### 7. Display — 2 options

| # | TuneId | Title | Kind | Apply | Source |
|---|--------|-------|------|-------|--------|
| 30 | `DISPLAY_PEAK` | Peak refresh rate | ENUM | `settings put system/global/secure peak_refresh_rate` + `min_refresh_rate` to the **already-advertised** peak (read current `peak_refresh_rate` first). Restore both. | Xtra `AppProfileService` refresh block |
| 31 | `DISPLAY_MIUI` | MIUI refresh mode | ENUM | `settings put system refresh_rate_mode` / `miui_refresh_rate` only if those keys already exist | Xtra same |

No invented Hz. If peak cannot be read, option is unavailable. Android 16: do **not** lock orientation.

### 8. Focus — 3 options (Settings APIs, not sysfs)

| # | TuneId | Title | Kind | Apply | Source |
|---|--------|-------|------|-------|--------|
| 32 | `FOCUS_DND` | Do not disturb | SWITCH | `NotificationManager.INTERRUPTION_FILTER_PRIORITY` if policy access granted; else unavailable with “Needs Do Not Disturb access” | Xtra `GameControlUseCase.enableDND` |
| 33 | `FOCUS_HEADSUP` | Hide heads-up | SWITCH | `settings put global heads_up_notifications_enabled 0` | Xtra `hideNotifications` |
| 34 | `FOCUS_IMMERSIVE` | Immersive bars | SWITCH | `settings put global policy_control immersive.full=*` | Xtra `setImmersiveMode` |

`FOCUS_DND` is the only option that may prompt a system settings screen. Restore on session end. Not a kernel write; Shizuku/Root still required to match the section gate, except DND which can work Standard **if** policy access is granted — **still hide the whole tune page unless elevated** (T12 KD-7). DND row inside the page may show “Needs DND access” when elevated but permission missing.

### 9. Charging — 1 option

| # | TuneId | Title | Kind | Apply | Source |
|---|--------|-------|------|-------|--------|
| 35 | `CHARGE_BYPASS` | Bypass charging in-game | SWITCH | First writable of `/sys/class/qcom-battery/bypass_charging_enable`, `/sys/class/power_supply/battery/input_suspend` | Xtra `FunctionalRomUseCase.BYPASS_CHARGING`; RV battery |

Session-scoped. Restore is mandatory — leaving `input_suspend=1` after the game is a battery-support bug. Probe write-verify required. If restore fails, retry on next `recoverSession` (same as dirty sysfs).

**Not in v1:** force fast charge, charge-current tables (not a game-session feature).

### 10. Network — 1 option

| # | TuneId | Title | Kind | Apply | Source |
|---|--------|-------|------|-------|--------|
| 36 | `NET_TCP` | TCP congestion | ENUM | `/proc/sys/net/ipv4/tcp_congestion_control` → `bbr` or `westwood` if listed; else unavailable | RV `TCP_CONGESTION_ALGORITHM` |

Low gaming value; include because the user asked for breadth and the node is real. Honest subtitle: “Change TCP congestion during the session.”

---

## Rejected (still not options)

| Idea | Why |
|------|-----|
| OpenGL / HWUI / ANGLE / `debug.egl` | No portable game-GL hook on Android 16 (T12 KD-4) |
| Mali IOCTL / `/proc/ged` | A16 filtering + Project Zero |
| `sched_util_clamp_min=64` | Throttle (T12 KD-18) |
| `performance` governor / min=max | Xtra Monster; cooks device |
| kgsl `throttling=0` / `msm_thermal` disable | Thermal disable |
| GED `gx_force_cpu_boost` / `boost_gpu_enable` | Thermal-adjacent force-max |
| ZRAM resize / drop_caches / `am kill-all` | Already have freeze + RAM Free; drop_caches is harmful |
| Undervolt / voltage tables / wakelock blocker | Kernel-manager, not game booster |
| Spectrum / Magisk / Xposed / Accessibility game monitor | Out of scope + Play |
| CPU core offline | Can wedge the device |
| Sound / LED / KLapse | Not game performance |
| Per-game profile editor | T12 non-goal v1 |
| Apply-on-boot | T12 KD-2 |

---

## Mapping from the old 4 dummies

| Old dummy / rev 3 TuneId | Rev 4 |
|--------------------------|-------|
| `GPU_FLOOR` | same id, category GPU |
| `CPU_SCHED` | split → `CPU_FLOOR` + `CPU_UCLAMP` + `CPU_STUNE` (all default **off**) |
| `GPU_HOLD` | same id, category GPU |
| `KERNEL` | split → `THERMAL_SCONFIG` + input category (all default **off**) |
| `dummy_opt_*` | still deleted, never migrated |

**IDs that must not appear** (an earlier results draft listed these; they are rejected, not shipped): `MEM_DROP_CACHES`, `THERMAL_CORE_CONTROL`, `THERMAL_VDD_RESTRICTION`, `NET_WIFI_PM`, `CPU_INTERACTIVE_HISPEED`. GPU keep-awake is `GPU_HOLD`, not `GPU_KEEPAWAKE`.

---

## Prefs

File remains `"apexcore"`.

| Key | Type |
|-----|------|
| `tune_on_<TuneId>` | Boolean (switch / “use this option”) |
| `tune_val_<TuneId>` | String (slider int or enum token) |
| `tune_migrated_v1` | Boolean — dummy-key **deletion** only |
| `tune_applied`, `tune_boot_count`, `tune_boot_id`, `tune_snapshot_json`, `tune_owner`, `tune_session_pkg` | unchanged |

Default every `tune_on_*` = **false**.

---

## Probe / apply budget (replaces T12 KD-8 numbers)

- Phase 1: **one candidate per TuneId that is not already covered** (skip little/big/prime if `CPU_FLOOR` not being probed as separate — still probe the split ids so they can light independently). Max **36** phase-1 candidates.
- Wall **3500 ms**. Per-node **120 ms** `destroy()`. Batches of **6**.
- Phase 2 fill only if wall remains.
- Cache TTL 60 s + backend fingerprint.
- `refreshCapabilities()` still returns immediately.

Session apply: **2500 ms** hard budget when more than 4 intents are on; skip remainder; toast only if 0 succeeded.

---

## Types (delta on T12 API)

```kotlin
enum class TuneCategory {
    GPU, CPU, INPUT, THERMAL, MEMORY, IO, DISPLAY, FOCUS, CHARGE, NETWORK
}

enum class TuneControlKind { SWITCH, SLIDER, ENUM }

enum class TuneId {
    GPU_FLOOR, GPU_HOLD, GPU_ADRENO, GPU_GOVERNOR, GPU_PWRLEVEL,
    GPU_GED_GAME, GPU_SAMSUNG_MIN, GPU_SIMPLE,
    CPU_FLOOR, CPU_FLOOR_LITTLE, CPU_FLOOR_BIG, CPU_FLOOR_PRIME,
    CPU_GOVERNOR, CPU_UCLAMP, CPU_STUNE, CPU_STUNE_IDLE,
    INPUT_BOOST_EN, INPUT_BOOST_MS, TOUCHBOOST, CPUFREQ_BOOST,
    DEVFREQ_BOOST, SCHED_BOOST_INPUT, SULTAN_INPUT,
    THERMAL_SCONFIG,
    VM_SWAPPINESS, VM_VFS_CACHE, VM_DIRTY_RATIO,
    IO_SCHEDULER, IO_READAHEAD,
    DISPLAY_PEAK, DISPLAY_MIUI,
    FOCUS_DND, FOCUS_HEADSUP, FOCUS_IMMERSIVE,
    CHARGE_BYPASS,
    NET_TCP
}

data class TuneValue(val on: Boolean, val raw: String? = null)

data class TuneSpec(
    val id: TuneId,
    val category: TuneCategory,
    val title: String,
    val kind: TuneControlKind,
    val slider: IntRange? = null
)
```

`TuneCatalog.specs: List<TuneSpec>` is the UI source of truth. Home count = specs with `capability.available`.

---

## Files (added on T12 file plan)

```
tune/TuneCategory.kt
tune/TuneSpecs.kt          // titles, kinds, slider ranges, apply order
ui/tune/TuneScreen.kt      // categorized page
ui/tune/TuneCategorySection.kt
ui/tune/TuneOptionRow.kt   // switch / slider / enum
```

Home: replace dummy card with `HomeAnimatedEntryRow` “Game optimisation”.

---

## Tests (added)

| Test | Assert |
|------|--------|
| `TuneSpecsCount` | exactly 36 ids, 10 categories, unique paths per groupId |
| `TuneCpuFloorMutex` | `CPU_FLOOR` on ⇒ split cluster intents ignored |
| `TuneRejectedPathsAbsent` | catalog excludes thermal disable, mali0, ged ioctl, uclamp sysctl |
| `TuneChargeBypassRestores` | apply then restore returns `input_suspend` |
| `TuneFocusDndNoSysfs` | DND does not call `writePath` |
| `TuneEmptyCategoryHidden` | 0 available in NETWORK ⇒ section omitted |

---

## PR delta

T12 PRs 1–3 stay valid (shell, applier, session). Changes:

- **PR 1** — catalog is the 36-id `TuneSpecs` + alias paths, not 4 bundles. Probe phase-1 walks every `TuneId`.
- **PR 2** — `TuneValue` + `tune_on_*` / `tune_val_*`.
- **PR 4** — **TuneScreen** (not four Home switches) + Home entry row + dummy-key deletion.

Still no dummy-true migration. Still no apply-on-boot.
