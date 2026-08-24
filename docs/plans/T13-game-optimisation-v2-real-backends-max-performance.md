# T13 — Game Optimisation V2: real backends, verified writes, performance governors and max-frequency locks

| Field | Value |
|---|---|
| **Document** | Implementation plan / code-review remediation |
| **ID** | T13 |
| **Priority** | P0 correctness + P1 feature expansion |
| **Date** | 2026-08-24 |
| **Status** | **READY FOR IMPLEMENTATION** |
| **Repository** | `abhay-byte/apexcore` |
| **Target** | Android 16 / compileSdk 36 / targetSdk 36 / minSdk 24 |
| **Supersedes** | Does **not** delete T12; this is the follow-up hardening/expansion plan |
| **Primary code** | `app/src/main/kotlin/com/ivarna/apexcore/tune/` |

---

## 1. Executive conclusion

The current T12 implementation is **real code, not dummy UI**: it has a capability probe, root/Shizuku shell gateway, sysfs catalog, snapshots, restore logic, session ownership and unit tests. However, the current repository is **not yet strong enough to claim that CPU/GPU game optimisation is proven to work correctly on real devices**.

The main reason is not that the architecture is fake. The main reason is that the implementation currently conflates **command success** with **verified kernel state**, conflates **Shizuku availability** with **root-like kernel write capability**, and has no physical-device validation suite. In particular:

1. `ShellGateway.writePath()` already returns both `ok` and `verified`, but `TuneProbe` and `TuneApplier` currently accept `ok` alone. A sysfs driver can accept, clamp, normalize or ignore a value while the shell command itself exits successfully. A row can therefore be presented as working when the requested state was not actually established.
2. Shizuku may run as **ADB shell UID 2000** or as **root UID 0**. The current tune layer identifies only `Root` versus `Shizuku` from the freeze backend name, so root-backed Shizuku is unnecessarily restricted while ADB-backed Shizuku can be described too optimistically.
3. The current Shizuku command path uses reflective `Shizuku.newProcess`, which Shizuku has deprecated in favor of `UserService`.
4. Existing 79 tests are useful unit/spec tests, but there is no `app/src/androidTest` device test suite and no checked-in hardware results proving requested CPU/GPU clocks/governors changed on real stock/rooted kernels.
5. T12 intentionally avoided `performance` governors and min=max frequency locking. T13 adds them as **explicit Advanced / high-power controls**, while retaining automatic thermal protection and exact restore.

### Required product truth

After T13, ApexCore must distinguish these statements:

- **Command executed** — shell command exited successfully.
- **Requested value verified** — readback matches the requested normalized value.
- **Actual hardware frequency observed** — optional live frequency sampling shows what the hardware is actually running at.

Only the second state may make a tune option show **Applied**. The third is diagnostic because thermal firmware/hardware may still throttle below a requested DVFS bound.

---

## 2. Review of the current implementation

### 2.1 What is already good and should be preserved

Keep these T12 design decisions:

- session-scoped tuning rather than apply-on-boot;
- snapshot-before-write and restore on session end;
- persisted boot identity for orphan recovery;
- overlay/watchdog session ownership;
- per-device capability probing instead of assuming every sysfs path exists;
- no `/dev/mali0` IOCTL usage;
- no thermal throttling disable;
- no `msm_thermal/enabled` writes;
- no `sched_util_clamp_min/max` global sysctl writes;
- no vendor/system remounts;
- game launch must still succeed when tuning fails;
- Standard mode must not claim kernel tuning;
- no INTERNET permission required for tuning.

The existing `TuneManager`, `TuneApplier`, `TuneProbe`, `TuneSnapshotStore`, `TuneSessionWatchdog`, `TuneCatalog` and UI are a usable base. T13 is a hardening/refactor, not a rewrite of the whole app.

### 2.2 P0 correctness bugs to fix before adding max-performance controls

#### P0-A — `ok` is used instead of `verified`

Current behavior:

- `ShellGateway.writePath()` returns `WriteResult(ok, verified, readback, ...)`.
- `TuneProbe.probeSingleNode()` stores a node as writable using `writeRes.ok`.
- `TuneApplier.applySingleNode()` treats a write as applied using `writeRes.ok`.

Required behavior:

- a kernel/settings mutation succeeds only when the expected value is **verified** using a type-aware comparator;
- `ok && !verified` must become `WRITE_NOT_EFFECTIVE`, not success;
- the UI must never report Applied for this state;
- apply failure after a snapshot must either rollback immediately or leave the snapshot persisted for recovery.

Also remove the current `readback.contains(expectedValue)` numeric verification rule. `expected=200` must not verify against `readback=1200`.

Introduce:

```kotlin
enum class VerificationMode {
    EXACT_STRING,
    EXACT_INT,
    GOVERNOR_TOKEN,
    IO_SCHEDULER_ACTIVE_TOKEN,
    BOOLEAN_NORMALIZED,
    SETTINGS_VALUE,
    CUSTOM
}
```

and attach a verification mode to every mutable node/action.

#### P0-B — tune privilege must not be derived from freeze backend name

Current behavior:

```text
Freeze backend Root     -> PrivilegeTier.ROOT
Freeze backend Shizuku  -> PrivilegeTier.SHIZUKU
```

This is too coarse.

Create a tune-specific runtime identity:

```kotlin
enum class TuneBackendIdentity {
    SU_ROOT,          // su command, effective uid 0
    SHIZUKU_ROOT,     // Shizuku/Sui server uid 0
    SHIZUKU_SHELL,    // Shizuku server uid 2000
    STANDARD
}
```

The resolver must use:

- `ShellGateway.canRoot()` and an actual `id -u` root probe for `SU_ROOT`;
- `Shizuku.pingBinder()` + permission + `Shizuku.getUid()` for Shizuku identity;
- `0` => `SHIZUKU_ROOT`;
- `2000` => `SHIZUKU_SHELL`;
- any unexpected UID => conservative shell-like capability probing, never assume root.

`FreezeFramework.activeBackend` may remain responsible for app freezing, but `TuneManager` must use a **TuneBackendResolver**. Freezing and kernel tuning have different privilege needs and must not share a string-name trust boundary.

#### P0-C — migrate away from reflective `Shizuku.newProcess`

Current `ShellGateway.executeViaShizuku()` reflectively calls `Shizuku.newProcess`.

Implement a Shizuku `UserService` executor and keep the legacy process path only as a short-lived compatibility fallback if the API/runtime version requires it. The preferred implementation must:

- expose `uid()` / `identity()`;
- expose `exec(argv, timeoutMs)` or a narrowly-scoped command API;
- return stdout, stderr and exit code separately;
- kill timed-out work;
- avoid shell string interpolation where structured argv is possible;
- bind once and reuse the service for a game session/probe batch;
- unbind cleanly when no longer needed.

Do not build a general interactive terminal. This is a narrow privileged executor for ApexCore-owned operations.

#### P0-D — no real-device proof exists yet

Unit tests do not prove sysfs writes work on physical Android kernels. Add a hardware validation protocol and checked-in results before changing product copy from capability language to performance claims.

Create:

- `docs/results/game-tune-v2/README.md`
- one sanitized result file per tested device/build;
- optional `tools/device-verify-tune.sh` for developer-side ADB/root verification;
- an instrumentation/debug-only diagnostic flow where feasible.

Do not collect device identifiers or upload diagnostics automatically.

---

## 3. External/platform findings that change T12

### 3.1 Shizuku is not one privilege level

Official Shizuku documentation states that the service may run with root UID 0 or ADB shell UID 2000, and recommends checking `Shizuku.getUid()`. ADB and root have materially different Linux/SELinux privileges.

Reference:
- https://github.com/RikkaApps/Shizuku-API
- https://github.com/RikkaApps/Shizuku-API/blob/master/README.md

**Design consequence:** Shizuku-shell must be treated as a strong Android shell/Binder backend, not a generic replacement for root sysfs access. Any sysfs write under shell must still pass live exact write verification.

### 3.2 Use Android Game Mode Performance where supported

Android exposes a game manager shell command:

```text
cmd game list-modes <PACKAGE_NAME>
cmd game mode performance <PACKAGE_NAME>
```

Performance mode is available only when the package/device reports it as supported. This is valuable for `SHIZUKU_SHELL` because it uses a platform service that shell is intended to access, instead of pretending shell owns kernel DVFS nodes.

References:
- https://developer.android.com/games/optimize/adpf/gamemode/gamemode-api
- AOSP `GameManagerShellCommand.java`

Add this as a first-class session action named **OEM Game Mode — Performance**. It must snapshot the previous game mode and restore it at session end.

Do **not** force `device_config game_overlay` overrides by default. Those are developer/OEM intervention mechanisms, may alter resolution/FPS/ANGLE behavior, can persist beyond a session if mishandled, and are not necessary for this feature.

### 3.3 CPUFreq supports real governor selection and min/max bounds

Linux CPUFreq exposes per-policy:

- `scaling_available_governors`
- `scaling_governor`
- `scaling_available_frequencies` when the driver provides it
- `cpuinfo_max_freq`
- `scaling_min_freq`
- `scaling_max_freq`

`scaling_min_freq` must not be greater than `scaling_max_freq`, and `scaling_max_freq` must not be lower than the minimum. Therefore a max lock must be an ordered transaction, not two unrelated writes.

Reference:
- https://www.kernel.org/doc/html/latest/admin-guide/pm/cpufreq.html

### 3.4 Generic devfreq provides the right model for GPU bounds

Linux devfreq has user min/max frequency requests and a governor model. Vendor GPU drivers expose different concrete sysfs layouts, so ApexCore must discover and verify actual GPU devfreq targets rather than writing every `/sys/class/devfreq/*` entry.

Reference:
- https://www.kernel.org/doc/html/latest/driver-api/devfreq.html

### 3.5 Do not use Android Fixed Performance Mode as a game boost

`cmd power set-fixed-performance-mode-enabled true` is a benchmark/testing feature. Android explicitly says it does not represent maximum device performance and may still overheat.

Reference:
- https://developer.android.com/games/optimize/adpf/fixed-performance-mode

**Decision:** do not expose Fixed Performance Mode in ApexCore game tuning.

### 3.6 Remove automatic `drop_caches` from game launch

The current freeze path executes:

```text
echo 3 > /proc/sys/vm/drop_caches
```

Linux explicitly warns that `drop_caches` is not a mechanism for normal cache management and can cause performance problems because discarded page cache/dentries/inodes must be recreated with I/O and CPU work.

Reference:
- https://www.kernel.org/doc/html/latest/admin-guide/sysctl/vm.html

**Decision:** remove `drop_caches` from normal pre-game freeze/BOOST. Killing selected background apps is separate. Do not evict useful filesystem cache just before the game loads assets.

### 3.7 Max lock must retain thermal protection

A requested min=max bound is not permission to disable thermal control. Android exposes thermal status/headroom APIs specifically because sustained mobile performance is thermally constrained.

References:
- https://developer.android.com/games/optimize/adpf/thermal
- https://developer.android.com/reference/android/os/PowerManager

ApexCore must automatically release high-power frequency locks at severe thermal stress rather than fighting platform protection.

---

## 4. V2 user-visible feature model

T13 keeps existing safe options, fixes misleading ones, and adds four primitive controls plus one preset.

### 4.1 New/changed options

| Option | Control | Backend expectation | Behavior |
|---|---|---|---|
| **OEM Game Mode — Performance** | switch | Shizuku shell/root or su root when command works | Set target game's supported Android game mode to `performance`; restore previous mode |
| **CPU governor** | enum | usually root; shell only if live verified | Select from intersection of `scaling_available_governors` across target policies; include `performance` only when actually listed |
| **CPU lock to max** | switch | usually root | For every discovered CPUFreq policy, transactionally set max then min to the selected max bound; exact verify; rollback whole transaction on failure |
| **GPU governor** | enum | usually root | Select only from governors reported by the discovered GPU devfreq/vendor node; `performance` shown only if actually supported |
| **GPU lock to max** | switch | usually root | Transactionally set GPU max then min to the highest verified available operating point on a real GPU frequency node |
| **Maximum Performance preset** | preset/action | capability aggregate | Enable OEM Game Mode Performance + CPU performance governor if supported + CPU max lock + GPU performance governor if supported + GPU max lock; never disables thermal protection |

### 4.2 Reuse existing TuneIds where possible

Change existing semantics instead of creating duplicate controls:

- `CPU_GOVERNOR`: change from “leave powersave” switch to a real enum selector.
- `GPU_GOVERNOR`: keep as enum but stop silently substituting a different governor when the user asks for `performance`.

Add:

```kotlin
GAME_MODE_PERFORMANCE,
CPU_LOCK_MAX,
GPU_LOCK_MAX
```

`MAX_PERFORMANCE_PRESET` should preferably be a UI/profile concept that composes primitive intents rather than another sysfs-writing TuneId. This keeps restore ownership and diagnostics attributable to the actual primitives.

### 4.3 Explicit high-power disclosure

The first time the user enables either max lock or Maximum Performance, display a concise disclosure:

> Maximum clocks can increase heat and battery drain. ApexCore keeps Android/kernel thermal protection enabled and will release max locks if the device reaches severe thermal stress.

Do not use alarmist language. Persist acknowledgement, not the actual tune state.

---

## 5. Backend capability model

### 5.1 Capability matrix

| Action | Standard | Shizuku UID 2000 | Shizuku UID 0 / Sui | `su` root |
|---|:---:|:---:|:---:|:---:|
| DND with user-granted policy access | ✅ | ✅ | ✅ | ✅ |
| Android settings shell actions | ❌/API dependent | ✅ if verified | ✅ if verified | ✅ if verified |
| `cmd game mode performance` | ❌ | ✅ if game exposes it | ✅ if game exposes it | ✅ if command works |
| CPU sysfs governor/frequency | ❌ | **probe only; normally unavailable** | ✅ if verified | ✅ if verified |
| GPU sysfs governor/frequency | ❌ | **probe only; normally unavailable** | ✅ if verified | ✅ if verified |
| vendor thermal sysfs | ❌ | normally unavailable | root-only, vendor-gated | root-only, vendor-gated |

The UI must say **Needs Root for this kernel** when Shizuku UID 2000 cannot write a kernel node. Do not say simply “Shizuku connected” and imply every tune works.

### 5.2 `TunePrivilege` replacement

Replace:

```kotlin
enum class TunePrivilege { ROOT_ONLY, SHELL_OK }
```

with a capability requirement that does not promise writability merely because a node is tagged shell-compatible:

```kotlin
enum class RequiredIdentity {
    ANY,
    SHELL_OR_ROOT,
    ROOT
}
```

Even `SHELL_OR_ROOT` is only a **permission to attempt a safe probe**. Runtime verification remains authoritative.

### 5.3 Backend fingerprint

Probe cache key must include at least:

- backend type;
- Shizuku server UID/version when applicable;
- boot ID;
- kernel release (`uname -r`);
- device build fingerprint hash or local stable fingerprint if already available without new permissions.

Never reuse a Root capability result after switching to Shizuku shell.

---

## 6. Correct CPU implementation

### 6.1 Dynamic CPU policy discovery

Do not assume `policy0`, `policy4`, `policy7` represent little/big/prime.

Enumerate directories matching:

```text
/sys/devices/system/cpu/cpufreq/policy*
```

For each policy read:

```text
related_cpus
scaling_driver
scaling_governor
scaling_available_governors
scaling_min_freq
scaling_max_freq
cpuinfo_min_freq
cpuinfo_max_freq
scaling_available_frequencies   # optional
scaling_cur_freq                # diagnostic only
```

Build a `CpuPolicyDescriptor`:

```kotlin
data class CpuPolicyDescriptor(
    val name: String,
    val relatedCpus: Set<Int>,
    val driver: String?,
    val minPath: String,
    val maxPath: String,
    val governorPath: String,
    val availableGovernors: Set<String>,
    val availableFrequenciesKhz: List<Long>,
    val cpuInfoMaxKhz: Long?,
    val currentMaxKhz: Long
)
```

Cluster labels are presentation only and derived from capacity/max-frequency ordering. The write engine targets policy directories, not guessed core numbers.

### 6.2 CPU governor selection

For “All CPU policies”, show the intersection of governors available on every writable policy.

Example:

```text
schedutil
performance
powersave
walt
```

Rules:

- never invent a governor;
- never replace `performance` with `schedutil` after the user selected performance;
- if `performance` is absent, show it disabled/unavailable rather than silently substituting;
- snapshot each policy's original governor independently;
- all-policy action is atomic from the user's perspective: if a required policy fails, rollback policies already changed.

### 6.3 CPU max-frequency lock

Target value selection:

1. Prefer the highest numeric entry from `scaling_available_frequencies` if present and consistent with `cpuinfo_max_freq`.
2. Otherwise use `cpuinfo_max_freq` if valid.
3. Otherwise use the current `scaling_max_freq` as the maximum ApexCore can safely infer.
4. Never extrapolate a frequency.

Apply each policy as a transaction:

```text
snapshot governor/min/max
write scaling_max_freq = targetMax
verify exact numeric readback
write scaling_min_freq = targetMax
verify exact numeric readback
```

If min write fails, immediately restore the policy's original min/max. Do not leave a half-applied lock.

The lock requests a fixed DVFS range; it does **not** disable thermal/firmware throttling and does not guarantee physical silicon remains at that clock every instant.

### 6.4 Restore CPU lock

Safe restore order:

```text
restore scaling_min_freq first
restore scaling_max_freq second
restore governor last if this transaction owns it
```

This avoids transient `min > max` errors when restoring a lower original maximum.

---

## 7. Correct GPU implementation

### 7.1 GPU target discovery

Create `GpuDevfreqDiscovery`. It must identify actual GPU nodes rather than treating arbitrary devfreq devices as GPUs.

Priority candidates:

#### Qualcomm Adreno / KGSL

```text
/sys/class/kgsl/kgsl-3d0/devfreq/min_freq
/sys/class/kgsl/kgsl-3d0/devfreq/max_freq
/sys/class/kgsl/kgsl-3d0/devfreq/governor
/sys/class/kgsl/kgsl-3d0/devfreq/available_governors
/sys/class/kgsl/kgsl-3d0/gpu_available_frequencies
/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq   # diagnostic if available
```

Vendor alternatives may be retained only when positively identified and verified.

#### Mali / generic devfreq

Discover candidate directories under:

```text
/sys/class/devfreq/
/sys/class/misc/mali0/device/devfreq/
```

Match only entries whose resolved path/name/uevent/device metadata identifies GPU/Mali. Do not write generic memory-bus, DDR, ISP or NPU devfreq nodes.

For a selected GPU devfreq target, require a valid min/max pair before enabling **GPU lock to max**.

#### Samsung community/vendor nodes

Support paths such as `/sys/kernel/gpu/gpu_min_clock` only when the corresponding max/available metadata for that exact kernel exists and exact write verification succeeds. Never enable max lock from a min-only node.

### 7.2 GPU governor selection

Populate the UI from the discovered device's actual available-governor list.

Rules:

- `performance` appears only if available;
- Adreno kernels that expose only `msm-adreno-tz` must not show a fake performance option;
- no fallback substitution after the user chooses a specific governor;
- snapshot and exact-verify the selected governor.

### 7.3 GPU max-frequency lock

Require:

- verified GPU identity;
- a writable min frequency path;
- a writable max frequency path;
- a trustworthy list/max source.

Target is the highest actual available OPP/frequency, never an invented number.

Transaction:

```text
snapshot gpu min/max (+ governor if part of preset)
write gpu max = target
verify
write gpu min = target
verify
```

Partial failure => immediate rollback.

### 7.4 Do not use pwrlevel-only as “frequency lock”

Keep T12's distinction: Adreno `min_pwrlevel`/`default_pwrlevel` is vendor-specific and must not satisfy the new GPU max-frequency-lock capability by itself. A frequency lock requires real frequency bounds.

---

## 8. Android/OEM Game Mode action

### 8.1 Capability probe is game-specific

When a game package is selected/launched:

```text
cmd game list-modes <pkg>
```

Parse:

- current mode;
- available modes.

Enable **OEM Game Mode — Performance** only when `performance` is listed.

Do not force a package that Android does not identify/accept as a game.

### 8.2 Apply and restore

Before apply, snapshot the exact previous mode.

Apply:

```text
cmd game mode performance <pkg>
```

Verify with `cmd game list-modes <pkg>` that current mode is now `performance`.

Restore exact prior mode (`standard`, `battery`, `custom`, etc.) when the session ends if that mode remains supported. If exact restore cannot be performed, retain a recovery record and surface the failure in diagnostics.

### 8.3 Why this matters

This becomes the primary meaningful game-performance action for non-root Shizuku users on OEMs that support it. OEM Power HAL/game interventions can make hardware-specific performance decisions more safely than a shell-UID app guessing sysfs values.

---

## 9. Thermal safety controller

Max-frequency controls must always run under an independent safety guard.

Create `TuneThermalGuard` using `PowerManager`:

- `getCurrentThermalStatus()`;
- `addThermalStatusListener()`;
- `getThermalHeadroom()` where supported and meaningful.

Policy:

- `NONE/LIGHT`: no automatic change;
- `MODERATE`: show diagnostics/indicator only;
- `SEVERE` or above: immediately **release CPU_LOCK_MAX and GPU_LOCK_MAX** back to their pre-session values;
- do not disable Android thermal daemons, vendor thermal control, KGSL throttling, or cooling devices;
- do not automatically re-lock while the status remains severe;
- re-lock after cooling only if the user intent is still on, the session is active, and a hysteresis/cooldown condition is satisfied;
- if thermal API is unsupported/unreliable, keep kernel thermal protection untouched and do not invent temperature limits.

The safety guard is mandatory for Maximum Performance; it is not a user-disablable switch.

---

## 10. Fix probe semantics

### 10.1 Classify mutation types

The current probe writes the current value back to many nodes. That is not safe for every sysfs interface because some files represent triggers/actions rather than ordinary state.

Add:

```kotlin
enum class ProbeStrategy {
    READ_METADATA_ONLY,
    WRITE_SAME_VALUE_SAFE,
    APPLY_VERIFY_ROLLBACK,
    COMMAND_QUERY
}
```

Every catalog entry/action must declare a strategy.

Examples:

- CPU governor/min/max: `WRITE_SAME_VALUE_SAFE` or `APPLY_VERIFY_ROLLBACK`;
- Game Mode: `COMMAND_QUERY` (`list-modes`) — no mutation needed;
- one-shot kernel trigger/action files: no generic write probe; capability must be established by documented semantics or real apply/rollback;
- settings: query current value and verify after real apply.

### 10.2 `verified` is the only writable signal

A sysfs capability is available only when:

```text
exists/readable
AND identity is allowed to attempt the operation
AND safe write probe succeeds
AND exact normalized readback verifies
```

`writeRes.ok` alone is insufficient.

### 10.3 Capability failure reasons

Extend `TuneCapability` with structured reasons:

```kotlin
enum class CapabilityReason {
    AVAILABLE,
    NEEDS_ROOT,
    SHIZUKU_SHELL_LIMITED,
    NODE_NOT_FOUND,
    READ_DENIED,
    WRITE_DENIED,
    WRITE_NOT_EFFECTIVE,
    OPTION_NOT_SUPPORTED,
    THERMAL_SAFETY_BLOCKED,
    PROBE_TIMEOUT,
    UNKNOWN
}
```

UI subtitles should come from these reasons, not ad-hoc strings.

---

## 11. Transactional snapshot and path ownership

The current snapshot store is keyed only by path. That preserves the first original value but does not model **who currently owns a modified path**. T12 already has duplicate/shared-path possibilities, for example two TuneIds can refer to the same Samsung GPU min path with different group IDs. Turning one option off can therefore restore a path another active option still expects.

Replace path-only mutation ownership with:

```kotlin
data class TuneSnapshotEntry(
    val path: String,
    val originalValue: String,
    val owners: Set<TuneId>,
    val lastVerifiedValue: String?,
    val verificationMode: VerificationMode,
    val transactionId: String?
)
```

Rules:

1. first writer snapshots original;
2. additional compatible owners attach without replacing original;
3. disabling one TuneId removes only its ownership;
4. restore path only when no active owner still requires a non-original state;
5. conflicting requested values must be resolved by explicit priority/profile composition before writing;
6. CPU/GPU lock min+max writes share a transaction ID and rollback together.

Persist transaction records so process death cannot strand one half of a max lock.

---

## 12. Shell write engine hardening

### 12.1 Root write

Do not blindly `chmod 644` every path first.

Preferred root flow:

1. `stat` original mode;
2. attempt direct root write;
3. only if write fails specifically because of permissions and changing mode is safe for that catalog entry, temporarily adjust mode;
4. write;
5. read back;
6. restore mode immediately;
7. verify expected value with the node's verification mode.

A failed chmod must not be mistaken for a failed kernel capability if root could have written directly.

### 12.2 Structured result

Return:

```kotlin
data class MutationResult(
    val commandOk: Boolean,
    val verified: Boolean,
    val requested: String,
    val readback: String?,
    val effectiveBackend: TuneBackendIdentity,
    val failure: MutationFailure?
)
```

Never log `Applied` from `commandOk` alone.

### 12.3 Shell escaping

Continue path/value allow-list validation. With UserService, prefer structured arguments/file I/O where possible. Do not broaden accepted characters merely to support arbitrary shell commands.

---

## 13. Maximum Performance preset composition

The preset is not “write every tweak”. It is a deterministic composition of verified, high-value controls.

Preferred order after game launch succeeds:

1. OEM Game Mode Performance, if available;
2. CPU governor = `performance`, if every required policy supports it;
3. CPU lock to max, if enabled by capability;
4. GPU governor = `performance`, only if GPU reports it;
5. GPU lock to max, if supported;
6. existing peak refresh option, only if the user separately chose it;
7. existing focus/DND options remain independent.

Do **not** automatically include:

- `drop_caches`;
- thermal disable;
- charging bypass;
- TCP congestion change;
- VM dirty/swappiness changes;
- I/O scheduler changes;
- filesystem read-ahead changes;
- ANGLE/downscale overrides;
- fixed-performance benchmark mode.

Those are not universal “more FPS” switches and can make results worse or harder to attribute.

If a primitive is unavailable, the preset applies the remaining verified primitives and reports a per-item summary such as `3/5 applied`, with specific unavailable reasons.

---

## 14. Reassess low-confidence existing T12 tweaks

T13 should not expand the catalog indiscriminately. Audit existing options for evidence and reversibility.

### 14.1 Thermal `sconfig 13`

The mapping of `sconfig` numeric IDs is vendor/ROM/kernel specific. `13` is often associated with a PUBG/game profile on some Xiaomi kernels, but it is not a cross-Android standard.

Change `THERMAL_SCONFIG` from a generic gaming option to a **vendor-qualified experimental option**:

- only show when the exact known node exists;
- expose the current and supported/known values only when device/vendor evidence is available;
- never claim `13` is universally “Gaming”;
- never use it as part of Maximum Performance;
- retain exact snapshot/restore;
- no thermal-disable fallback.

### 14.2 VM/TCP/I/O tweaks

Keep them out of the primary game-performance preset. They may remain under **Advanced / Experimental** if write-verified, but product copy must not imply guaranteed FPS improvement.

### 14.3 Input boosts

Keep only on kernels that expose the corresponding module and exact write verification passes. Avoid simultaneously stacking multiple vendor input-boost mechanisms unless the device actually exposes them and their interaction is understood.

---

## 15. Remove `drop_caches` from `FreezeFramework`

Current normal game launch invokes background force-stop and then, for Root/Shizuku, attempts `echo 3 > /proc/sys/vm/drop_caches`.

T13 must delete this automatic operation from `freezeAll()`.

Acceptance test:

```text
GameLauncher.launch()
 -> FreezeFramework.freezeAll()
 -> no command containing /proc/sys/vm/drop_caches
```

If a developer wants cache dropping for benchmarks, keep it in a developer-only test tool, not the production game booster.

Also update any RAM-freed metric explanation because page cache should no longer be deliberately evicted to inflate apparent free-memory gains.

---

## 16. UI/UX changes

### 16.1 Backend status must be explicit

Instead of only showing `Shizuku` or `Root`, Tune screen diagnostics should show:

```text
Backend: Shizuku (ADB shell UID 2000)
Kernel tuning: limited / capability-probed
OEM Game Mode: available
```

or:

```text
Backend: Shizuku (root UID 0)
Kernel tuning: root-capable / capability-probed
```

or:

```text
Backend: Root (su)
Kernel tuning: capability-probed
```

### 16.2 Advanced section

Put these under an **Advanced performance** section:

- CPU governor
- CPU lock to max
- GPU governor
- GPU lock to max
- Maximum Performance preset

Default state remains OFF.

### 16.3 Requested vs applied state

A switch cannot visually remain in a misleading “on and working” state when apply fails.

Represent separately:

- user intent: ON/OFF;
- session state: Not active / Applying / Applied / Partial / Failed / Thermal released;
- last verified readback.

For a persisted intent that cannot apply on the current backend/device, show intent retained but **Not available on this backend**.

### 16.4 Diagnostics drawer

For each option provide developer/debug details:

- backend identity + UID;
- discovered path(s);
- original value;
- requested value;
- readback;
- verification result;
- transaction/rollback status;
- current CPU/GPU clock sample where readable;
- thermal status/headroom;
- failure reason.

No raw device serial numbers.

---

## 17. Game-session lifecycle

Keep the T12 rule that a game launch is never blocked by tune failure.

Recommended order:

```text
1. determine/protect target game
2. freeze eligible background apps WITHOUT drop_caches
3. resolve game launch intent
4. start game
5. establish overlay/watchdog restore owner
6. resolve current TuneBackendIdentity
7. apply fast verified game-mode/governor/lock transaction on IO
8. start thermal guard for high-power locks
9. monitor session ownership
10. restore on game exit/backend loss/thermal severe/orphan recovery
```

Do not move risky root writes before `startActivity()` until device validation proves that doing so materially helps and cannot interfere with launch.

When backend identity changes mid-session:

- stop new writes;
- attempt restore using the strongest currently valid identity;
- if restore cannot run, retain persisted recovery snapshot;
- do not discard original values merely because Shizuku disconnected.

---

## 18. File-by-file implementation plan

### PR 1 — correctness before features

#### `fps/privilege/ShellGateway.kt`

- use `verified` as authoritative mutation success;
- replace substring verification with verification strategy;
- separate stdout/stderr when feasible;
- add effective UID query;
- prepare UserService executor abstraction;
- direct-root-write before temporary chmod.

#### `tune/TuneModels.kt`

Add:

- `TuneBackendIdentity`;
- `RequiredIdentity`;
- `VerificationMode`;
- `ProbeStrategy`;
- structured capability/failure reasons;
- structured mutation result.

#### New `tune/TuneBackendResolver.kt`

- decouple tune identity from `FreezeFramework`;
- inspect `Shizuku.getUid()`;
- probe `su` effective UID;
- expose a flow so capability cache invalidates immediately on backend change.

#### `tune/TuneProbe.kt`

- require verified writes;
- type-safe comparators;
- backend fingerprint cache;
- probe strategy classification;
- no blind writes to action nodes;
- structured failure reasons.

#### `tune/TuneApplier.kt`

- treat unverified as failure;
- rollback failed mutations;
- correct thermal fallback logging/readback;
- no `Applied` log without verification.

### PR 2 — Shizuku executor modernization

Add an AIDL-backed Shizuku UserService, e.g.:

```text
app/src/main/aidl/com/ivarna/apexcore/privilege/IPrivilegedExecutor.aidl
app/src/main/kotlin/com/ivarna/apexcore/fps/privilege/ShizukuUserService.kt
app/src/main/kotlin/com/ivarna/apexcore/fps/privilege/ShizukuExecutorClient.kt
```

Requirements:

- reusable binding;
- `uid()`;
- bounded execution;
- cancellation/timeout;
- no unrestricted user-controlled commands;
- lifecycle tests.

Remove reflection-based `newProcess` once minimum supported Shizuku API/version allows it; until then isolate it in one compatibility class and never make it the primary path.

### PR 3 — Android Game Mode Performance

Add:

```text
tune/GameModeController.kt
```

Functions:

```kotlin
query(pkg): GameModeCapability
applyPerformance(pkg): MutationResult
restore(pkg): MutationResult
```

Integrate with session snapshot and `GameLauncher` target package.

### PR 4 — dynamic CPU topology + governors

Add:

```text
tune/cpu/CpuPolicyDiscovery.kt
tune/cpu/CpuGovernorController.kt
```

Modify `TuneCatalog` so policy paths are generated from discovery instead of hard-coded `policy0/4/7` assumptions for governor controls.

Change `CPU_GOVERNOR` to enum with runtime options.

### PR 5 — transactional CPU max lock

Add:

```text
tune/cpu/CpuFrequencyLockController.kt
```

Add `CPU_LOCK_MAX`.

Use policy transactions and exact restore sequencing.

### PR 6 — GPU discovery/governor/max lock

Add:

```text
tune/gpu/GpuDevfreqDiscovery.kt
tune/gpu/GpuGovernorController.kt
tune/gpu/GpuFrequencyLockController.kt
```

Add `GPU_LOCK_MAX` and runtime governor options.

Do not use arbitrary devfreq entries.

### PR 7 — snapshot ownership + transaction recovery

Upgrade `TuneSnapshotStore` to persist:

- original values;
- owners;
- transaction IDs;
- last verified values;
- backend identity used;
- restore status.

Add migration from old path→value JSON. Old snapshots must remain restorable; do not silently discard a same-boot T12 snapshot merely because the schema upgraded.

### PR 8 — thermal guard + Maximum Performance preset

Add:

```text
tune/TuneThermalGuard.kt
tune/TunePresetManager.kt
```

Implement severe-thermal automatic release and composed Maximum Performance preset.

### PR 9 — remove cache dropping and clean weak claims

Modify:

- `freeze/FreezeFramework.kt` — remove production `drop_caches`;
- tuning descriptions/metadata — distinguish experimental options from proven controls;
- `THERMAL_SCONFIG` — vendor-gate and remove from primary preset;
- store listing/help copy — claim only what is verified.

### PR 10 — hardware verification and diagnostics

Add:

- debug diagnostics screen/details;
- device verification tooling;
- device test result templates;
- hardware matrix results;
- release gate.

---

## 19. Test plan

### 19.1 Unit tests — mandatory

Add tests for at least:

1. `ok=true, verified=false` is unavailable in probe.
2. `ok=true, verified=false` is failed apply and rollback occurs.
3. numeric expected `200` does not verify readback `1200`.
4. boolean `1`/`Y` normalization is node-specific, not global substring logic.
5. Shizuku UID 2000 resolves `SHIZUKU_SHELL`.
6. Shizuku UID 0 resolves `SHIZUKU_ROOT`.
7. Root `su` identity is verified by `id -u`.
8. Root capability cache is not reused after switching to Shizuku shell.
9. CPU policies are discovered dynamically.
10. CPU all-policy governor options are an intersection.
11. `performance` is not offered when one required CPU policy lacks it.
12. CPU max target picks a real available maximum.
13. CPU max transaction rolls back if min write fails.
14. CPU restore min-before-max avoids invalid bounds.
15. GPU discovery rejects DDR/NPU devfreq entries.
16. GPU max lock requires a min/max frequency pair.
17. GPU `performance` is not invented when absent.
18. Game Mode capability parses current + available modes.
19. Game Mode performance applies only if supported.
20. Game Mode restores previous exact mode.
21. shared-path TuneId ownership does not restore while another owner is active.
22. process-death recovery restores a partial min/max transaction.
23. Severe thermal status releases both max locks.
24. thermal release does not turn off platform thermal control.
25. `FreezeFramework.freezeAll()` never calls `drop_caches`.
26. Maximum Performance composes only verified primitives.
27. unavailable preset components report Partial, not Success.
28. game launch succeeds if every tune fails.

### 19.2 Integration/device tests

A real-device test must capture before/apply/after/restore values.

For each CPU policy:

```text
governor before -> requested -> verified -> restored
min before -> target -> verified -> restored
max before -> target -> verified -> restored
```

For GPU:

```text
discovered device identity
available frequencies/governors
min/max/governor before
requested values
verified readback
current-frequency samples while game runs
restored values
```

For Shizuku:

```text
Shizuku.getUid()
server/API version
Game Mode list-modes result
sysfs expected denial or verified success
```

### 19.3 Minimum hardware matrix before release claim

Test at least:

| Device class | Root state | Required validation |
|---|---|---|
| Qualcomm/Adreno stock ROM | non-root + Shizuku ADB | Game Mode where supported; confirm kernel rows fail closed rather than fake success |
| Qualcomm/Adreno | rooted `su` | CPU policy governor/max lock + KGSL GPU governor/max lock |
| Samsung Exynos/Mali | rooted | Mali/Samsung discovery; min/max pairing; restore |
| MediaTek/Mali | rooted | Mali devfreq discovery; do not rely on GED force-max hacks |
| Any rooted device | Shizuku/Sui UID 0 | confirm resolver gives root-capable identity and verified writes work |

Prefer more than one OEM for Shizuku UID 2000 because shell SELinux/permissions vary by Android/OEM build.

### 19.4 Performance validation

Do not validate only sysfs changes. Measure outcome:

- frame-time median/p95/p99;
- FPS stability;
- CPU/GPU current-frequency traces where readable;
- thermal state/headroom;
- battery/temperature trend for a sustained run;
- cold launch and warm launch separately;
- baseline vs OEM Game Mode vs CPU lock vs GPU lock vs Maximum Performance.

Run sustained sessions of at least 15 minutes on representative devices to expose thermal regression. A max lock that wins the first 90 seconds but causes worse sustained frame times must not be advertised as universally better.

---

## 20. Acceptance criteria

T13 is complete only when all are true:

### Backend truth

- [ ] Shizuku UID 2000 and UID 0 are distinguished with `Shizuku.getUid()`.
- [ ] Tune privilege is no longer inferred solely from freeze backend name.
- [ ] UserService is the primary Shizuku executor; `newProcess` is removed or isolated compatibility-only.
- [ ] Switching backend invalidates capability results.

### Mutation truth

- [ ] Every mutable kernel option requires `verified=true` before showing Applied.
- [ ] No substring numeric verification remains.
- [ ] Unverified writes rollback or retain a recovery snapshot.
- [ ] Shared path ownership cannot be restored by one TuneId while another still owns it.

### CPU

- [ ] CPU policies are discovered dynamically.
- [ ] CPU governor is a real enum from kernel-advertised governors.
- [ ] `performance` is selectable only if supported.
- [ ] CPU lock-to-max transaction sets verified max+min for every target policy.
- [ ] CPU lock restores exact pre-session bounds.

### GPU

- [ ] GPU target discovery cannot select unrelated devfreq devices.
- [ ] GPU governor options come from the actual GPU node.
- [ ] `performance` is selectable only if the GPU exposes it.
- [ ] GPU max lock requires verified min/max bounds and a real max source.
- [ ] GPU restore is exact and transactional.

### Shizuku non-root

- [ ] OEM Game Mode Performance is implemented and verified per game where supported.
- [ ] Non-root Shizuku does not claim root-only CPU/GPU controls.
- [ ] If an OEM unusually permits a sysfs write to shell, it is enabled only after exact live verification.

### Safety

- [ ] `drop_caches` is absent from production game launch/freeze.
- [ ] No thermal disable path is introduced.
- [ ] Severe thermal status automatically releases max-frequency locks.
- [ ] Fixed Performance Mode is not exposed as a max-performance game feature.
- [ ] Maximum Performance defaults OFF and has a high-power disclosure.

### Evidence

- [ ] Unit suite passes.
- [ ] Hardware matrix results are checked in.
- [ ] At least one Shizuku UID-2000 device demonstrates graceful fail-closed kernel tuning plus a real platform action such as supported Game Mode Performance.
- [ ] At least one rooted Adreno device verifies CPU and GPU max locks with restoration.
- [ ] At least one rooted Mali/Exynos or MediaTek device verifies vendor/generic GPU discovery and restoration.
- [ ] Sustained performance/thermal results are reviewed before stronger store-listing claims.

---

## 21. Explicit non-goals / forbidden shortcuts

Do not add these to satisfy “more optimization” requests:

- disabling thermal throttling/services;
- disabling KGSL GPU throttling;
- forcing GED thermal-adjacent max hacks;
- overclocking beyond kernel-provided OPPs;
- undervolting;
- arbitrary `/dev/mali0` IOCTLs;
- remounting `/system` or `/vendor`;
- persistent boot scripts/Magisk modules;
- `device_config game_overlay` hacks as default behavior;
- fixed-performance benchmark mode as a user game boost;
- cache dropping before game launch;
- writing arbitrary devfreq nodes;
- claiming a max-frequency request guarantees the physical clock stays at max under thermal/firmware limits.

---

## 22. Recommended implementation order for the planner/agent

Do not start with the new max-lock UI. Implement in this order:

1. fix `verified` correctness and typed comparison;
2. decouple tune backend identity and detect Shizuku UID;
3. migrate Shizuku to UserService;
4. remove production `drop_caches`;
5. implement/verify OEM Game Mode Performance for Shizuku shell;
6. add dynamic CPU policy discovery;
7. upgrade CPU governor to enum;
8. add transactional CPU max lock;
9. add strict GPU target discovery;
10. upgrade GPU governor to enum;
11. add transactional GPU max lock;
12. upgrade snapshot ownership/transactions;
13. add thermal guard;
14. add Maximum Performance preset;
15. audit/gate low-confidence T12 options;
16. add device diagnostics + hardware validation matrix;
17. update user/store copy only from validated evidence.

**Do not mark T13 complete from unit tests alone.** The release gate is exact readback plus physical-device validation.
