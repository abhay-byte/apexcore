# ApexCore — Hail Freeze Architecture

> Reference: `aistra0528/Hail` (6.1k★), `FreezeYou/FreezeYou`, `SuperFreezZ/SuperFreezZ`.
>
> Goal: deep-freeze background apps on a single button tap. Three privilege
> backends, picked at runtime, no UI rework.

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
| **Accessibility** | User grants a11y | None | Slow (UI automation) | Medium |

ApexCore supports all three. The framework picks the best available at
runtime and degrades gracefully when none are granted — in that case the
button still does T2's cached-process kill (better than nothing).

---

## 2. Module layout

```
app/src/main/kotlin/com/apexcore/app/freeze/
├── FreezeFramework.kt        # public façade, picks backend
├── FreezeBackend.kt          # sealed interface
├── ShizukuFreezeBackend.kt   # Shizuku API
├── RootFreezeBackend.kt      # Runtime.exec("su -c …")
├── AccessibilityFreezeBackend.kt  # simulated Force Stop
├── FreezeBackendResolver.kt  # detects which backends are usable
├── FreezeOperation.kt        # sealed class: ForceStop | Disable | Hide | Suspend
├── FreezeResult.kt           # data class
├── FreezeReceiver.kt         # BroadcastReceiver for am start -a FREEZE_ALL
└── FreezeAccessibilityService.kt
```

The existing `BoostManager` is kept as the fallback path (`FallbackFreezeBackend`).

---

## 3. Backend selection algorithm

`FreezeBackendResolver.detect()` runs in order, returns the first usable
backend, caches the result for the process lifetime:

```
1. ShizukuFreezeBackend.isReady()
   └─ Shizuku.pingBinder() + Shizuku.checkSelfPermission()
2. RootFreezeBackend.isReady()
   └─ Runtime.exec("su -c id").waitFor() == 0
3. AccessibilityFreezeBackend.isReady()
   └─ Settings.Secure.getString(ENABLED_ACCESSIBILITY_SERVICES)
       .contains(packageName/serviceClass)
4. FallbackFreezeBackend.isReady()  // always true
```

The active backend is exposed on `FreezeFramework.activeBackend` and
shown in the status footer so the user knows which mode they're in:

- `● Freeze: Shizuku`
- `● Freeze: Root`
- `● Freeze: Accessibility`
- `● Freeze: cached only`   ← fallback

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
adb shell am start -a com.apexcore.app.action.FREEZE_ALL
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

Last-resort. Opens `Settings > Accessibility`, asks the user to enable
ApexCore's service, then simulates:

1. Open Recents (KEYCODE_APP_SWITCH)
2. Tap "Clear all" (3-dot menu → Clear all)
3. For each app in the configured list: Settings → Apps → [App] → Force Stop → OK

This is slow (~2-5s per app) and fragile across OEM skins. It's the path
that "just works" on any device with no setup beyond one a11y toggle.

**Note:** The T4 button uses the simpler "Clear all recents" action via
Accessibility. Per-app Force Stop is a T5 concern.

---

## 9. Fallback path (T2 reuse)

If no backend is granted, the framework delegates to `BoostManager.kick()`
which still does `killBackgroundProcesses`. The user sees the same UI
panel, just with the "● Freeze: cached only" footer and a smaller
"Freed MB" number (typically <100 MB, often 0).

This is **not a regression** — it's identical to T2 behavior. The
architecture's value is unlocked the moment a user grants Shizuku.

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
<uses-permission android:name="android.permission.FORCE_STOP_PACKAGES"
                 tools:ignore="ProtectedPermissions" />  <!-- Shizuku only -->
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```

`FORCE_STOP_PACKAGES` is signature|privileged — only Shizuku/Root can use
it, normal apps don't need it. We declare it under `tools:ignore` so the
manifest builds but the actual grant comes from the backend.

`QUERY_ALL_PACKAGES` is required on Android 11+ to enumerate other apps'
package names. Without it, `pm list packages -3` from shell still works
(Shizuku isn't restricted by this perm), but the app-side filter does.

---

## 12. Testing matrix

| Device | Backend | Expected |
|---|---|---|
| Pixel 7 (no root, no shizuku) | Fallback | T2 behavior unchanged, footer says "cached only" |
| Pixel 7 + Shizuku (adb) | Shizuku | 15-30 apps force-stopped, ~400-800 MB freed |
| OnePlus 9 + Magisk | Root | same as Shizuku |
| Pixel 4a + a11y grant | Accessibility | "Clear all" recents, 0-3 apps force-stopped, slow |
| Pixel 7 + Shizuku dead | Shizuku → resolver fails | footer flips to "cached only" within 1s |

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
