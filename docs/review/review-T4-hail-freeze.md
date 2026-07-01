# Review: T4 — Hail-style Freeze Framework

**Date:** 2026-07-01
**Branch:** `release/1.0.x-T4-hail-freeze-architecture`
**Verdict:** CHANGES_REQUESTED

---

## Reference: Hail (`aistra0528/Hail` v1.10.0)

Cloned + analyzed at `https://github.com/aistra0528/Hail` for architecture alignment.

---

## Plan Adherence

- [x] Define `FreezeOperation` sealed class (ForceStop only in T4)
- [x] Define `FreezeBackend` sealed interface
- [x] Implement four backends: Shizuku, Root, Accessibility, Fallback
- [x] `FreezeBackendResolver.detect()` probes in priority order, caches
- [x] `FreezeFramework.freezeAll()` iterates apps, runs ForceStop, collects counts
- [x] `FreezeReceiver` handles `FREEZE_ALL` intent via `goAsync()` + coroutine
- [x] MainActivity: BOOST → freezeAll(); status footer shows backend + MODE column
- [⚠️] **Manifest permissions**: plan+arch doc specify `QUERY_ALL_PACKAGES`, `FORCE_STOP_PACKAGES` — neither declared
- [⚠️] **Shizuku dependency**: plan says "add dep" — used reflection instead (works at runtime, but deviation)
- [⚠️] **a11y service XML/declaration**: plan lists `apexcore_a11y_service.xml` — not created (stub for T4, acceptable)
- [❌] **Unit tests**: plan specifies tests for resolver ordering + filter — none exist

---

## Build & Tests

| Check | Result |
|-------|--------|
| Build (`assembleDebug`) | ✅ PASS |
| Tests | ❌ Missing — no test directory exists |

---

## Issues

### BLOCK

None.

### CHANGES_REQUESTED

1. **AndroidManifest.xml — Missing `QUERY_ALL_PACKAGES` + `FORCE_STOP_PACKAGES` + `ShizukuProvider`**
   - On Android 11+, `PackageManager.getInstalledApplications()` returns a filtered list without `QUERY_ALL_PACKAGES`. The `freezeAll()` loop may silently miss apps.
   - `FORCE_STOP_PACKAGES` (with `tools:ignore`) is declared in Hail's manifest — allows Shizuku/Root path to work with `am force-stop`.
   - Hail also declares `ShizukuProvider` for proper Shizuku integration.
   - **Fix:** Add `<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" tools:ignore="QueryAllPackagesPermission" />`, `<uses-permission android:name="android.permission.FORCE_STOP_PACKAGES" tools:ignore="ProtectedPermissions" />`, and `<provider android:name="rikka.shizuku.ShizukuProvider" ...>`.

2. **No unit tests (plan commitment)**
   - Plan explicitly requires "Unit: FreezeBackendResolver ordering" and "Unit: defaultFilter logic".
   - **Fix:** Add test file at `app/src/test/java/com/apexcore/app/freeze/` with JUnit tests.

3. **`FreezeFramework.freezeAll()` — Fragile string-match on error reason**
   - `res.reason.contains("not implemented", ignoreCase = true)` classifies failures as `skipped`.
   - A wording change in `AccessibilityFreezeBackend` silently breaks counting.
   - **Fix:** Add `val isSkipped: Boolean` property to `FreezeOperation.Result` that formally identifies the skip case.

4. **`MainActivity.onCreate()` — Race condition in initial status render**
   - `onCreate()` launches `lifecycleScope.launch { ... detect() ... }` then immediately calls `renderState()`.
   - IDLE state reads `activeBackend.value?.name` which is `null` until the coroutine completes, flashing `"● Ready to boost · …"`.
   - **Fix:** Show a loading placeholder or defer `renderState()` until after detection.

### MINOR

5. **`Thread.sleep(800)` in IO dispatcher — Use `delay()` instead**
   - `withContext(Dispatchers.IO)` block calls `Thread.sleep(800)`. Should use `delay(800)` since we're in a suspend context.

6. **`FreezeBackendResolver.backendByName()` — Dead code**
   - Defined but never called anywhere. Remove or document for T5.

7. **`FreezeFramework.isReady()` — Always returns `true`**
   - Fallback always resolves with `name = "cached only"` which is non-empty. The function can never return `false`.

### NIT

8. **Magic timeout values** — 3000ms, 5000ms, 800ms inline. Extract to named constants.

---

## Hail Architecture Alignment

Hail's approach differs in several ways worth adopting:

| Aspect | ApexCore (current) | Hail (reference) | Recommendation |
|--------|-------------------|-------------------|----------------|
| Shell command format | `am force-stop <pkg>` | `am force-stop --user current <pkg>` | Add `--user current` |
| Root execution | `su -c am <op> <pkg>` | `exec("su")` + pipe command to stdin | Stdin piping avoids shell escaping |
| Shizuku execution | `Shizuku.newProcess("sh", "-c", cmd)` | `IShizukuService.newProcess(["su"],... )` + pipe stdin | Align to Hail pattern |
| Shizuku dependency | Reflection (no compile dep) | `rikka.shizuku:api` compile dep | Plan says add dep; reflection is fine but add `shizuku.provider` to manifest |
| ShizukuProvider | Not declared | Declared in manifest with `INTERACT_ACROSS_USERS_FULL` | Required for Shizuku binder |
| Multi-backend policy | Shizuku > Root > A11y > Fallback | Shizuku + Root + Dhizuku + DevicePolicy + Island + Fallback | T4 scope is fine |
| Hidden API bypass | None | Uses `HiddenApiBypass` library | Not needed for T4 (only shell commands) |
| Permissions | `KILL_BACKGROUND_PROCESSES` only | `QUERY_ALL_PACKAGES`, `FORCE_STOP_PACKAGES`, `CHANGE_COMPONENT_ENABLED_STATE`, etc. | Add at minimum the first two |

---

## Fix Checklist

- [ ] **1.** AndroidManifest.xml — add permissions + ShizukuProvider
- [ ] **2.** Unit tests — resolver ordering + filter
- [ ] **3.** FreezeOperation.Result — add `isSkipped` formal property
- [ ] **4.** MainActivity — fix status render race condition
- [ ] **5.** `Thread.sleep` → `delay(800)` in FreezeFramework
- [ ] **6.** Remove or annotate dead `backendByName()`
- [ ] **7.** Fix `isReady()` semantics or remove
- [ ] **8.** Extract magic timeout constants
- [ ] **9.** Add `--user current` to shell commands in backends
- [ ] **10.** Align Shizuku execution to Hail stdin-pipe pattern
- [ ] **11.** Add ShizukuProvider to manifest
- [ ] **12.** Final build verification
