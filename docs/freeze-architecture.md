# ApexCore — Hail Freeze Architecture

> Reference: `aistra0528/Hail` (6.1k★), `FreezeYou/FreezeYou`, `SuperFreezZ/SuperFreezZ`.
>
> Goal: deep-freeze background apps on a single button tap. Three privilege
> backends, picked at runtime, no UI rework.

> **T10c status (2026-08-03):** this document describes the T4-era design.
> Shipping reality: **Accessibility is a non-ready stub** (`isReady` hard `false`,
> no manifest service, not in product candidates, not claimed in UI/privacy —
> see T10c Decision 2) and there is **no Standard fallback freeze mode**
> (Decision E — freeze requires Shizuku or Root).

---

## 1. Why this exists

T2's `BoostManager.kick()` uses `ActivityManager.killBackgroundProcesses()`.
That API has been neutered since Android 8: a third-party app can no longer
kill processes of *other* apps whose `uid` differs. The reported "freed MB" is
mostly cached-only and respawns within seconds.

To get a real deep-freeze you need a shell that can run `pm disable` or
`am force-stop` on a foreign package. There are exactly three ways a
non-system app can get that power:

| Backend | Requires | Privilege | Speed | Reliability |
|---|---|---|---|---|
| **Shizuku** | `adb` (one-time) | Per-app API token | Fast (direct binder) | High |
| **Root** | Magisk/KernelSU | `su` shell | Fast | High (but rare user) |
| ~~Accessibility~~ | — | — | — | **Not ship-ready (T10c)** — stub only, excluded from resolver |

ApexCore ships **Shizuku and Root** only (Decision E + T10c). The framework
picks the best available at runtime and gates freeze when neither is
granted — no fake "standard" mode, no a11y path.

---

## 2. Module layout

```
app/src/main/kotlin/com/apexcore/app/freeze/
├── FreezeFramework.kt        # public façade, picks backend
├── FreezeBackend.kt          # sealed interface
├── ShizukuFreezeBackend.kt   # Shizuku API
├── RootFreezeBackend.kt      # Runtime.exec("su -c …")
├── AccessibilityFreezeBackend.kt  # STUB — isReady=false, not in resolver (T10c)
├── FreezeBackendResolver.kt  # detects which backends are usable
├── FreezeOperation.kt        # sealed class: ForceStop | Disable | Hide | Suspend
├── FreezeResult.kt           # data class
└── FreezeReceiver.kt         # FREEZE_ALL receiver — exported=false (T10c)
```

> Note: `FallbackFreezeBackend` exists for tests/defensive paths only — it is
> **not** a product freeze mode (Decision E). No `FreezeAccessibilityService` exists.

---

## 3. Backend selection algorithm

`FreezeBackendResolver.detect()` runs in order, returns the first usable
backend, caches the result for the process lifetime:

```
1. ShizukuFreezeBackend.isReady()
   └─ Shizuku.pingBinder() + Shizuku.checkSelfPermission()
2. RootFreezeBackend.isReady()
   └─ Runtime.exec("su -c id").waitFor() == 0
3. null — freeze gated (Setup dialog) when neither is ready
```

(Historical note: an a11y backend and a "standard" fallback existed in
T4-era design; both are removed from the product path — T10a Decision E,
T10c.)

The active backend is exposed on `FreezeFramework.activeBackend` and
shown in the status footer so the user knows which mode they're in:

- `● Freeze: Shizuku`
- `● Freeze: Root`
- `● SETUP REQUIRED` (no elevation → freeze blocked)

---

## 4. Operations

| Op | Shell | Effect |
|---|---|---|
| `ForceStop` | `am force-stop <pkg>` | Kill processes, no effect on install state. App respawns on next launch. |
| `Disable` | `pm disable <pkg>` | Removes from launcher, blocks components, killed on next boot. Persists until `pm enable`. |
| `Hide` | `pm hide <pkg>` | Like disable but invisible to launcher. |
| `Suspend` | `pm suspend <pkg>` | (Android 7+) grayed-out icon, no notifications. |

**T4 ships `ForceStop` only.** `Disable` / `Hide` / `Suspend` are exposed
as `FreezeOperation` subtypes so T5 (per-app list) can swap in without
breaking the API.

---

## 5. FREEZE_ALL action

```
adb shell am start -a com.ivarna.apexcore.action.FREEZE_ALL
hail://freeze_all                         (no — ApexCore uses action only)
```

Internal flow:

1. `FreezeReceiver.onReceive()` → `FreezeFramework.freezeAll()`
2. `freezeAll()` enumerates non-system, non-self packages from PackageManager
3. For each, `backend.execute(ForceStop(pkg))` on a coroutine dispatcher
4. Collects results, emits `FreezeResult(killed, failed, ms)` via a
   `MutableStateFlow<FreezeResult?>` that the UI subscribes to.

The action is declared in the manifest with `android:exported="true"`.

---

## 6. Shizuku integration

API: `dev.rikka.shizuku.api.ShizukuBinderWrapper` (Shizuku 13+).

```kotlin
// pseudocode
val result = ShizukuRemoteProcess("pm disable-user $pkg").exec()
```

Dependency added as `implementation("dev.rikka.shizuku:api:13.1.5")`.
The Shizuku Manager must be installed (Play Store / F-Droid). The user
starts it once and grants our `applicationId`; we receive a binder token
that's valid until reboot.

Wire to lifecycle: `Shizuku.addBinderReceivedListener` /
`addBinderDeadListener` — backend flips to "unavailable" if Shizuku dies
mid-session.

---

## 7. Root integration

```kotlin
val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", "pm disable $pkg"))
proc.waitFor(5, TimeUnit.SECONDS)
```

No additional deps. Detection: try `su -c id` once on cold start, cache
the result. The user is shown a "Root detected" footer.

---

## 8. Accessibility integration

> **Historical (T4-era). NOT shipped.** T10c Decision 2: no a11y service is
> declared in the manifest, `AccessibilityFreezeBackend.isReady()` is hard
> `false`, and the resolver excludes it. Nothing in the app, listing, or
> privacy policy claims a11y automation. If reopened later, the design was:

Last-resort. Opens `Settings > Accessibility`, asks the user to enable
ApexCore's service, then simulates:

1. Open Recents (KEYCODE_APP_SWITCH)
2. Tap "Clear all" (3-dot menu → Clear all)
3. For each app in the configured list: Settings → Apps → [App] → Force Stop → OK

This is slow (~2-5s per app) and fragile across OEM skins.

---

## 9. Fallback path (T2 reuse)

> **Historical (T4-era). NOT a product mode.** T10a Decision E removed
> "standard" freeze — `FallbackFreezeBackend` never reports Success and is
> excluded from resolver candidates; freeze is gated until Shizuku/Root.
> `BoostManager` is dead code kept only for reference.

Originally: if no backend is granted, the framework delegates to
`BoostManager.kick()` which does `killBackgroundProcesses`.

---

## 10. UI integration

The `BOOST` button is reused. On tap:

```
IDLE → BOOSTING → (FreezeFramework.freezeAll()) → RESULT
```

The status footer is replaced when a real backend is active:

- IDLE: `● Ready to boost`  (unchanged)
- BOOSTING: `● Freezing via Shizuku…`  (backend name)
- RESULT: `● Freezed 18 apps via Shizuku`  (killed count + backend)

Result panel grows a fourth stat column: `MODE` showing the backend.

The design.md layout and color tokens are not modified — this is a
backend swap, not a redesign.

---

## 11. Permissions manifest

```xml
<uses-permission android:name="android.permission.KILL_BACKGROUND_PROCESSES" />
<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
```

> Ship reality (T10c): `FORCE_STOP_PACKAGES` is **removed** — it is
> signature|privileged, a no-op for Play installs, and unused by code
> (Shizuku/Root execute `am force-stop` via their own privileges).
> No `RECEIVE_BOOT_COMPLETED` in the shipped manifest (no boot features).
> `FreezeReceiver` is `exported="false"` — no external FREEZE_ALL broadcasts.

`QUERY_ALL_PACKAGES` is required on Android 11+ to enumerate other apps'
package names for the games library and freeze targets. Without it,
`pm list packages -3` from shell still works (Shizuku isn't restricted by
this perm), but the app-side filter does.

---

## 12. Testing matrix

| Device | Backend | Expected |
|---|---|---|
| Pixel 7 (no root, no shizuku) | none | Freeze gated — Setup dialog; RAM Free / HUD still work |
| Pixel 7 + Shizuku (adb) | Shizuku | 15-30 apps force-stopped, ~400-800 MB freed |
| OnePlus 9 + Magisk | Root | same as Shizuku |
| ~~Pixel 4a + a11y grant~~ | ~~Accessibility~~ | **not a product path (T10c)** |
| Pixel 7 + Shizuku dead | Shizuku → resolver fails | chip flips to SETUP REQUIRED |

Build verification: `./gradlew :app:assembleDebug` must succeed.
Install: `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

---

## 13. What this does NOT do (T5+ scope)

- Per-app freeze list with checkboxes
- Whitelist / pinned apps (skip when freeze-all runs)
- Tags (group apps, freeze by tag)
- `pm disable` / `pm hide` (Force-stop only in T4)
- `am force-stop` of foreground game (deliberate — don't kill what user is playing)
- Boot-time freeze schedule

These are deliberately out of T4 scope and tracked as T5/T6 followups.
