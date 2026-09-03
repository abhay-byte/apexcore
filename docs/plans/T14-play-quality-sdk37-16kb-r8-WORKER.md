# ApexCore Worker Task — Target SDK 37, 16 KB Page Size, R8-Safe Play App Optimization

**Repository:** `abhay-byte/apexcore`  
**Target branch:** `main` — create a dedicated feature branch before implementation  
**Suggested repo destination:** `docs/plans/T14-play-quality-sdk37-16kb-r8-WORKER.md`  
**Priority:** P0 — Google Play publishing / app-quality compliance  
> **Revision:** R8 safety re-review — 2026-09-03  
> **Key rule:** A successful optimized build is **not** proof of correctness. The worker must prove that the *minified, shrunk, optimized* app still executes every critical ApexCore runtime path before this task can be closed.  
> **Repo-specific safety finding:** `ShizukuUserService` is currently annotated `@Keep`; **do not remove that annotation in this task**. It is an externally started Shizuku UserService and is deliberately protected from minification removal.

**Primary goal:** Make the next production Android App Bundle target Android 17 / API 37, remain verifiably compatible with 16 KB page-size devices, and be fully optimized with R8 so Play Console no longer reports the current low/near-empty optimization metrics.

---

# 0. Worker mission

Fix all of the following Play Console findings for ApexCore:

| Play Console finding | Current reported state | Required result |
|---|---:|---|
| Target SDK | Below requested level | `targetSdk = 37` |
| 16 KB memory page size | "Supports 16 KB", but Play warns undetected libraries/runtime assumptions may still fail | Build and test evidence proving real 16 KB compatibility |
| App optimization | `Low` | Release build fully optimized with R8 |
| Optimization percentage | `-` | R8 optimization metadata visible to Play; meet/exceed Google Play threshold |
| Obfuscation percentage | `1%` | Meaningfully increased through release minification |
| Shrinking percentage | `-` | Code + resource shrinking enabled and effective |
| R8 configuration | `-` | Valid production R8 configuration included |
| DEX size | `19.9 MB` uncompressed | Materially reduce without breaking functionality |
| AGP | Current repo uses 8.8.2 | Upgrade to API-37-compatible AGP 9.1.1+ |

Google's August 26, 2026 app-quality guidance states that published apps/games will need at least **25% coverage across optimization, shrinking, and obfuscation** for optimized DEX code, with enforcement beginning in February 2027. Treat 25% as the minimum compliance floor, not the performance target.

This is a release-engineering task, not a cosmetic Play Console task. Do not make the dashboard look green by disabling functionality, adding broad keep rules, suppressing build warnings, or shipping a different code path.

---

# 1. Repository baseline confirmed before implementation

The current `main` branch has:

## Root `build.gradle.kts`

```kotlin
plugins {
    id("com.android.application") version "8.8.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
```

## `app/build.gradle.kts`

Current important values:

```kotlin
android {
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        targetSdk = 36
    }

    buildTypes {
        release {
            // signing only
            // no isMinifyEnabled
            // no isShrinkResources
            // no proguardFiles
        }
    }
}
```

The app also applies:

```kotlin
id("org.jetbrains.kotlin.android")
id("org.jetbrains.kotlin.plugin.compose")
```

No app-owned CMake/external-native build or `System.loadLibrary()` usage was found in the inspected `main` branch. That does **not** prove the final bundle contains no native `.so` files: transitive dependencies may still package native libraries. The final APK/AAB must be inspected.

---

# 2. Required target build stack

Do **not** stop at AGP 9.0. API level 37 requires AGP 9.1.1 or newer.

For this task, prefer the following **conservative stable R8-analysis-capable baseline**:

```text
compileSdk: 37
targetSdk:  37
AGP:        9.3.2
Gradle:     9.5.0
JDK:        17
minSdk:     keep 24 unless a real dependency forces a change
```

Why AGP 9.3.2 instead of merely 9.1.1:

- AGP 9.1.1 is the minimum version that supports API 37.
- AGP 9.3.x also supports API 37.
- AGP 9.3 adds the stable `analyzeReleaseR8Config` task and R8 Configuration Analyzer, which is directly useful for this Play optimization task.
- AGP 9.3 introduces the simpler `optimization { enable = true }` DSL while retaining legacy compatibility.
- Use the latest stable patch in the selected 9.3 line. At the time this task was re-reviewed, that is `9.3.2`.
- Do **not** jump to preview/alpha tooling merely for a newer R8.
- If the worker deliberately chooses current stable AGP 9.4 instead, pair it with its required Gradle version and rerun the entire compatibility matrix. Do not mix Gradle requirements between AGP lines.

Do not perform an unrelated "update every dependency" sweep in this task.

---
# 3. Non-negotiable constraints

1. **Do not change ApexCore user-visible behavior merely to satisfy R8.**
2. Preserve all Shizuku, root, app-launch, freeze/optimization, overlay/HUD, accessibility, package-management, and settings flows.
3. Do not raise `minSdk` from 24 unless absolutely required and documented.
4. Do not add:
   ```proguard
   -dontoptimize
   -dontshrink
   -dontobfuscate
   ```
5. Do not add blanket keep rules such as:
   ```proguard
   -keep class com.ivarna.apexcore.** { *; }
   -keep class ** { *; }
   ```
   These defeat the purpose of the task.
6. Do not hide problems with blanket:
   ```proguard
   -dontwarn **
   ```
7. Do not disable AGP 9 built-in Kotlin as the final solution.
8. Do not keep `android.builtInKotlin=false` or `android.newDsl=false` as a permanent workaround.
9. Do not add 4 KB-specific native assumptions.
10. Do not claim 16 KB support based only on Play's current static result. Verify the produced bundle/APK.
11. Do not change production signing architecture except where necessary to build locally; signing is not the objective of this task.
12. Keep release mapping files available so production crashes can be retraced.

13. **Do not remove the existing `@Keep` from `ShizukuUserService`.** It is a non-manifest Shizuku UserService started in an external Shizuku process; preserving this entry point is more important than gaining a tiny extra obfuscation percentage.
14. A debug build/test result is **not** acceptable evidence that R8 is safe. At least one installed and exercised build must contain the same R8 code/resource optimization configuration as production release.
15. Every new keep rule must include:
    - the exact class/member it protects;
    - why static analysis cannot see the runtime use;
    - which runtime test proves the rule is needed;
    - confirmation that a broader package keep was not used.
16. Do not "fix" a release-only crash by adding successively broader keep rules until it disappears. Isolate the exact dynamic entry point first.
17. Do not remove a keep rule supplied by a dependency until its affected release runtime path has been tested.
18. Do not treat `mapping.txt`, `usage.txt`, or the R8 Analyzer as substitutes for runtime testing. They are static evidence only.

---

# 4. Phase A — Establish a reproducible baseline

Before changing Gradle files, build the current branch and record the baseline.

Run:

```bash
git status
./gradlew --version
./gradlew clean
./gradlew :app:assembleRelease
./gradlew :app:bundleRelease
./gradlew :app:test
./gradlew :app:lintRelease
```

If `lintRelease` or tests already fail on `main`, record the exact existing failures. Do not silently attribute pre-existing failures to this task.

Record at minimum:

- AGP version
- Gradle version
- Java/JDK version
- compileSdk
- targetSdk
- release APK file size
- release AAB file size
- total DEX size where measurable
- whether release currently contains `.so` files
- current unit/lint status

Suggested evidence file:

```text
docs/results/T14-play-quality-baseline.md
```

Do not commit generated APK/AAB artifacts unless the repository already has an explicit policy for doing so.

---

# 5. Phase B — Upgrade to Android 17 / API 37

## B1. Upgrade AGP

In root `build.gradle.kts`, move:

```kotlin
id("com.android.application") version "8.8.2" apply false
```

to the preferred stable baseline:

```kotlin
id("com.android.application") version "9.3.2" apply false
```

`9.1.1` is the minimum API-37-compatible AGP, but prefer `9.3.2` here so the worker can use the stable R8 Configuration Analyzer. Use another stable AGP only if:

- it supports API 37;
- its required Gradle version is also adopted;
- all tests/builds pass;
- the choice is documented.

## B2. Upgrade Gradle wrapper

Update:

```text
gradle/wrapper/gradle-wrapper.properties
```

to the Gradle version required by the selected AGP.

For AGP 9.3.x:

```text
Gradle 9.5.0
```

If a different AGP line is selected, use that line's documented Gradle requirement rather than copying `9.5.0` blindly.

Verify:

```bash
./gradlew --version
```

## B3. Migrate to AGP 9 built-in Kotlin

AGP 9 enables built-in Kotlin by default. The current ApexCore project explicitly applies `org.jetbrains.kotlin.android`, which must be migrated.

Remove the Android Kotlin plugin from the app module:

```kotlin
id("org.jetbrains.kotlin.android")
```

and remove its top-level declaration when no longer needed:

```kotlin
id("org.jetbrains.kotlin.android") version "2.0.21" apply false
```

Do **not** disable built-in Kotlin to avoid doing the migration.

Check the project for legacy Kotlin DSL usage:

```text
android.kotlinOptions { ... }
kotlin.sourceSets { ... }
kapt / kotlin-kapt
```

Migrate any occurrences according to the AGP built-in Kotlin migration guide.

The current inspected app does not show a `kotlinOptions` block, so this should be small, but search the entire repository before concluding that.

Example searches:

```bash
rg 'org\.jetbrains\.kotlin\.android|kotlin-android|kotlinOptions|kotlin\.sourceSets|kapt'
```

## B4. Keep Compose compiler compatibility correct

ApexCore uses Jetpack Compose and currently applies:

```kotlin
id("org.jetbrains.kotlin.plugin.compose")
```

The Compose Compiler Gradle plugin version must be compatible with the Kotlin compiler used by the chosen build stack.

Do not blindly retain version `2.0.21` if AGP 9 built-in Kotlin reports incompatibility.

Requirements:

- retain the Compose compiler plugin because the app uses Compose;
- align it with the Kotlin compiler/build configuration supported by the selected AGP;
- do not re-introduce `org.jetbrains.kotlin.android` merely to satisfy Compose;
- do not update unrelated Compose/AndroidX libraries unless required to restore compatibility.

Document the final Kotlin/Compose compiler combination in the result report.

## B5. Set Android 17 SDK levels

In `app/build.gradle.kts`:

```kotlin
android {
    compileSdk = 37

    defaultConfig {
        minSdk = 24
        targetSdk = 37
    }
}
```

Do not put `<uses-sdk>` in the manifest.

Build immediately after the SDK/AGP migration before adding R8 changes. Isolate migration failures from optimizer failures.

Gate:

```bash
./gradlew clean :app:assembleDebug :app:test :app:lintDebug
```

must pass or all new failures must be resolved/documented before continuing.

---

# 6. Phase C — Enable real R8 optimization on release builds

The current release build does not explicitly enable R8 minification or resource shrinking. Fix this **without weakening dynamic ApexCore entry points**.

## C1. Prefer the AGP 9.3+ optimization DSL

For the preferred AGP 9.3.x baseline, configure the release build approximately as:

```kotlin
android {
    buildTypes {
        release {
            optimization {
                enable = true
            }

            signingConfig = if (keystorePropertiesFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
}
```

For AGP 9.3+, `optimization { enable = true }` enables code optimization and optimized resource shrinking and includes the Android platform's default keep rules.

Do **not** combine snippets from different AGP generations without checking the selected version's DSL.

If the worker intentionally remains on AGP 9.1.x/9.2.x instead, use the legacy equivalent:

```kotlin
release {
    isMinifyEnabled = true
    isShrinkResources = true

    proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
    )
}
```

Do not use obsolete `proguard-android.txt`, `-dontoptimize`, or `android.enableR8.fullMode=false`.

## C2. Keep rules location

For AGP 9.3+, prefer:

```text
app/src/main/keepRules/apexcore.keep
```

The new keep-rules source set is easier to reason about and works with the updated optimization DSL.

If the worker intentionally uses the legacy DSL, `app/proguard-rules.pro` remains acceptable.

**Do not create both files with duplicate rules.** Pick one approach and document it.

Start with the minimum possible ApexCore-specific rules.

### Existing repo-specific keep protection

ApexCore already has:

```kotlin
@Keep
class ShizukuUserService : IPrivilegedExecutor.Stub()
```

This class is passed to Shizuku through:

```kotlin
ComponentName(appContext, ShizukuUserService::class.java)
```

**Keep the existing `@Keep`. Do not replace it with a package-wide rule.**

The annotation is intentionally conservative for this single, externally-started service. Its small optimization cost is acceptable because a missing or renamed external entry point would break privileged execution.

Do not add a second redundant rule for `ShizukuUserService` unless inspection proves that the annotation is not represented in the effective R8 configuration.

## C3. Understand what R8 already protects

Android components declared in the merged manifest are recognized as app entry points. ApexCore currently declares important components including:

```text
MainActivity
FreezeReceiver
GameOverlayService
RamFillerService
rikka.shizuku.ShizukuProvider
```

Do **not** add blanket keep rules for these simply because they are components.

Instead:

1. inspect the merged release manifest;
2. inspect effective R8 keep configuration;
3. install the optimized build;
4. invoke each component through its real runtime path.

If a manifest component is missing at runtime, investigate the merged manifest/R8 integration first before adding a manual keep.

## C4. Build the optimized release

Run:

```bash
./gradlew clean :app:assembleRelease :app:bundleRelease
```

Inspect generated R8 outputs, including as available:

```text
app/build/outputs/mapping/release/mapping.txt
app/build/outputs/mapping/release/usage.txt
app/build/outputs/mapping/release/seeds.txt
app/build/outputs/mapping/release/configuration.txt
```

`mapping.txt` must be retained through the normal production artifact workflow so release crashes can be retraced.

## C5. Add an R8 static-safety inspection

After the first optimized release build:

```bash
MAPPING=app/build/outputs/mapping/release/mapping.txt
USAGE=app/build/outputs/mapping/release/usage.txt
CONFIG=app/build/outputs/mapping/release/configuration.txt

grep -F "com.ivarna.apexcore.fps.privilege.ShizukuUserService" "$MAPPING" || true
grep -F "com.ivarna.apexcore.fps.privilege.ShizukuUserService" "$USAGE" || true
grep -F "androidx.annotation.Keep" "$CONFIG" || true
```

Interpretation:

- `ShizukuUserService` should remain present in the optimized program.
- If it appears in `usage.txt` as removed, treat that as a P0 blocker and investigate before installing.
- If the class appears mapped to itself, the existing `@Keep` is doing its intended job.
- If output format differs on the selected R8 version, inspect with the R8 Analyzer / APK Analyzer rather than assuming a missing grep match means failure.

Also inspect the final APK with APK Analyzer or `apkanalyzer dex packages` to confirm the privileged package still contains the required reachable implementation.

Static inspection is an early warning only; runtime binding remains mandatory.

---
# 7. Phase D — Prove R8 does not remove functionality

This is the most important phase of the task.

R8 can safely remove unreachable code, but ApexCore contains privileged and lifecycle-driven paths that are not adequately proven by a normal debug test run. The worker must build a **dynamic-entry-point inventory**, inspect effective rules, and test an optimized artifact.

## D1. Audit dynamic code patterns before adding any keep rules

Run repository-wide searches:

```bash
rg 'Class\.forName|java\.lang\.reflect|getDeclared(Field|Method|Constructor)|setAccessible'
rg 'ServiceLoader|DexClassLoader|PathClassLoader|InMemoryDexClassLoader'
rg 'System\.load|System\.loadLibrary|Runtime\.getRuntime\(\).*load'
rg 'getIdentifier|Resources\.getIdentifier'
rg '::class\.java\.name|\.class\.name|canonicalName|simpleName'
rg 'ObjectInputStream|Serializable|Parcelable|Parcelize'
rg 'kotlinx\.serialization|Gson|Moshi|Jackson'
rg 'assets\.open|AssetManager|openRawResource'
rg 'ComponentName\(|Intent\([^)]*class|setClassName|setComponent'
```

For every match, classify it as:

```text
directly visible to R8
manifest-driven
annotation-protected
consumer-rule-protected
reflection/dynamic name lookup
external process / Binder / AIDL
JNI/native callback
resource-by-name lookup
serialization
```

No match should automatically generate a keep rule.

## D2. Repo-specific P0 dynamic entry point — Shizuku UserService

ApexCore's `ShizukuExecutorClient` builds:

```kotlin
Shizuku.UserServiceArgs(
    ComponentName(appContext, ShizukuUserService::class.java)
)
```

and binds to it with `Shizuku.bindUserService(...)`.

`ShizukuUserService`:

- runs in Shizuku's root/ADB-shell process;
- extends generated `IPrivilegedExecutor.Stub`;
- is currently annotated `@Keep`;
- performs the privileged command and UID operations.

Required actions:

1. **Do not remove `@Keep`.**
2. Inspect the effective R8 configuration and dependency consumer rules.
3. Confirm `ShizukuUserService` survives in the release mapping/output.
4. On a device with Shizuku running, install the optimized release-equivalent build.
5. Grant Shizuku permission.
6. Bind the UserService.
7. Execute a harmless command, for example:
   ```text
   id
   ```
   or another non-mutating command already used by the app's diagnostic flow.
8. Verify:
   - bind succeeds;
   - `IPrivilegedExecutor.Stub.asInterface()` returns a working proxy;
   - `uid()` returns the expected shell/root UID;
   - command output returns through the AIDL `Bundle`;
   - unbind succeeds;
   - rebind succeeds;
   - binder/service death recovery still works.

A build that launches but cannot complete these steps has **failed R8 validation**.

## D3. AIDL/Binder safety

ApexCore includes generated AIDL for:

```text
com.ivarna.apexcore.fps.privilege.IPrivilegedExecutor
```

R8 can see direct generated references, so do not blindly keep the entire AIDL package.

Instead verify in the optimized build:

```text
client -> bind -> Stub.asInterface -> uid()
client -> bind -> Stub.asInterface -> execute()
service -> Bundle result -> client
disconnect/binding death -> invalidate -> rebind
```

If this fails:

1. inspect `mapping.txt` and `usage.txt`;
2. inspect merged consumer rules from Shizuku and AndroidX;
3. identify the exact removed/renamed runtime entry;
4. add only the smallest rule required.

Never use:

```proguard
-keep class com.ivarna.apexcore.fps.privilege.** { *; }
```

as the fix.

## D4. Manifest component runtime gate

The current manifest contains:

```text
MainActivity
FreezeReceiver
GameOverlayService
RamFillerService
ShizukuProvider
```

The optimized build must execute the real path for each applicable component:

### `MainActivity`
- launcher cold start;
- warm start;
- process-death restart.

### `FreezeReceiver`
- invoke the app's actual freeze broadcast path;
- verify `onReceive()` executes and expected downstream behavior occurs.

### `GameOverlayService`
- start the foreground overlay service;
- show HUD;
- perform a non-destructive HUD action;
- stop it;
- start it again.

### `RamFillerService`
- start through the real app flow;
- verify IPC/process behavior in `:ramfiller`;
- cancel/stop cleanly;
- verify a second start works.

### `ShizukuProvider`
- app starts with provider enabled;
- Shizuku permission/binder path initializes normally.

Do not add explicit keep rules merely because these are Android components. Manifest entry points are already part of R8's reachability model.

## D5. Root / shell execution gate

R8 must not break alternate privileged backends.

Exercise:

```text
Shizuku backend
root backend, on a suitable rooted device
legacy/fallback process backend, if still reachable by production code
```

For each backend verify:

- availability detection;
- command execution;
- stdout/stderr/exit-code handling;
- timeout handling;
- reconnect/retry if implemented.

Do not remove a "fallback" class just because it appears rarely used until its intended production reachability is understood.

## D6. Resource shrinking safety

Optimized resource shrinking can remove resources reachable only through dynamic name lookup.

Audit:

```bash
rg 'getIdentifier|Resources\.getIdentifier|assets\.open|AssetManager|openRawResource'
```

Then verify the optimized APK/AAB still includes non-resource assets that ApexCore relies on, including the currently bundled scripts/policy assets where applicable:

```bash
unzip -l app/build/outputs/apk/release/app-release.apk | \
  grep -E 'assets/(fps_daemon\.sh|fps_count\.awk|privacy_policy\.md)' || true
```

Also runtime-test:

- launcher icon;
- notification/foreground-service icon;
- fonts used by the UI;
- splash/theme resources;
- overlay/HUD resources;
- privacy-policy asset;
- shell/FPS assets if their production path is active.

If a real dynamic resource lookup is found and a resource is removed, use the narrow Android resource-shrinker keep mechanism for that exact resource. Do **not** keep all resources.

## D7. Third-party consumer-rule audit

Libraries can contribute R8 consumer rules automatically.

Inspect at minimum the effective rules originating from:

```text
Shizuku API/provider
AndroidX
Compose/runtime dependencies
profileinstaller
any dependency found by the dynamic-code audit
```

Use the generated `configuration.txt` and, with AGP 9.3+, the R8 Configuration Analyzer.

Do **not** copy random ProGuard snippets from Stack Overflow or old library READMEs.

If a dependency contributes a broad keep rule:

1. verify whether a newer stable library version narrows/fixes it;
2. understand the runtime feature protected by the rule;
3. test that feature in an optimized build;
4. only then consider filtering/replacing that consumer rule.

## D8. Keep-rule protocol

Every project keep rule must have a comment directly above it:

```proguard
# WHY: <runtime mechanism R8 cannot infer>
# PROTECTS: <exact class/member>
# VERIFIED BY: <test/manual release smoke step>
<rule>
```

Preferred order of solutions:

```text
1. Make runtime use statically visible to R8 where practical.
2. Use dependency-provided consumer rules.
3. Preserve an exact annotated/dynamic entry point.
4. Add a narrow class/member rule.
5. Only as a temporary local diagnostic, disable one R8 transformation to isolate the cause.
```

Any temporary diagnostic use of:

```proguard
-dontobfuscate
-dontoptimize
-dontshrink
```

must remain **local/uncommitted** and be removed before the final build.

## D9. Release-only failure isolation

If debug works but optimized release fails:

1. reproduce twice on the optimized build;
2. capture `logcat`;
3. retrace the stack trace with the exact `mapping.txt`;
4. inspect `usage.txt`;
5. inspect R8 Analyzer keep-rule attribution;
6. identify whether the break is:
   - code shrinking;
   - obfuscation/name dependence;
   - logical optimization;
   - resource shrinking;
   - AGP/targetSdk behavior rather than R8;
7. apply the narrow fix;
8. rerun the exact failing release path;
9. rerun the entire P0 release smoke matrix.

Do not close the task after only the formerly failing screen passes.

---
# 8. Phase E — Verify 16 KB memory page-size compatibility

Play currently says ApexCore "Supports 16 KB", but explicitly warns that undetected libraries or code assumptions may still fail. Treat this as a verification task.

## E1. Inspect the final AAB for native libraries

Build:

```bash
./gradlew :app:bundleRelease
```

Then inspect:

```bash
unzip -l app/build/outputs/bundle/release/app-release.aab | grep -E '(^|/)lib/.*\.so$' || true
```

Also inspect the release APK:

```bash
unzip -l app/build/outputs/apk/release/app-release.apk | grep -E '(^|/)lib/.*\.so$' || true
```

Record every ABI and every `.so` file.

### If there are no native `.so` files

Document that the app-owned code path is Java/Kotlin-only and the produced bundle contains no native shared libraries.

Still complete package-alignment and real-device/emulator checks below.

### If native `.so` files are present

Do not assume they are app-owned. Determine which dependency contributes each library.

Use:

```bash
./gradlew :app:dependencies --configuration releaseRuntimeClasspath
```

and dependency inspection / Gradle dependency insight as needed.

For each native library:

- identify owner dependency;
- identify its version;
- verify the version declares/supports 16 KB page-size devices;
- upgrade only the affected dependency when necessary;
- verify all shipped ABIs.

If an incompatible prebuilt native dependency cannot be updated, this task is not complete.

## E2. Verify AAB requests 16 KB ZIP alignment

Use a current `bundletool`:

```bash
bundletool dump config \
  --bundle=app/build/outputs/bundle/release/app-release.aab \
  | grep alignment
```

Expected result for native-library bundles:

```text
PAGE_ALIGNMENT_16K
```

A result of:

```text
PAGE_ALIGNMENT_4K
```

is a blocker.

## E3. Verify APK alignment

Use the Android SDK `zipalign` tool:

```bash
zipalign -c -P 16 -v 4 \
  app/build/outputs/apk/release/app-release.apk
```

This must succeed.

If the exact locally generated release APK path differs, locate it rather than skipping the check.

## E4. If ApexCore ever compiles native code in this branch

There was no app-owned CMake/JNI build in the inspected baseline. If implementation introduces or discovers native compilation:

- prefer NDK r28 or later;
- ensure ELF segments are 16 KB aligned;
- never hard-code `4096` as the runtime memory page size;
- use platform APIs such as `getpagesize()` or `sysconf(_SC_PAGESIZE)` where page size is needed;
- inspect all third-party/prebuilt `.so` files.

Do not introduce native code merely to satisfy this task.

## E5. Test on a real 16 KB runtime

Use an emulator/device configured for 16 KB pages.

Verify first:

```bash
adb shell getconf PAGE_SIZE
```

Required:

```text
16384
```

Then install and smoke-test the **optimized release** build, not only debug.

At minimum test:

- cold app launch;
- main navigation;
- Games screen;
- All Apps screen;
- app details;
- selected game/app launch;
- Shizuku permission/binding path where available;
- root path on a suitable test device if applicable;
- freeze/optimization flow;
- overlay/HUD flow;
- settings;
- process/app list refresh;
- background → foreground resume;
- process death / relaunch;
- rotation / large-screen resize if supported.

Zero linker/page-size crashes are allowed.

Capture relevant `logcat` if any native loader error occurs.

---

# 9. Phase F — Android 17 / targetSdk 37 compatibility audit

Changing `targetSdk` is not just a manifest number.

Review both:

```text
Android 17 behavior changes — all apps
Android 17 behavior changes — apps targeting API 37+
```

Focus on behavior that ApexCore actually exercises.

## Required checks

### F1. Reflection / platform internals

Android 17 target behavior includes stricter runtime behavior in some platform areas.

Search for reflection into Android framework internals:

```bash
rg 'Class\.forName|getDeclaredField|getDeclaredMethod|setAccessible|isAccessible|java\.lang\.reflect'
```

Particularly verify no ApexCore path mutates static final platform fields or reflects into `MessageQueue` internals.

### F2. Local network

Android 17 requires `ACCESS_LOCAL_NETWORK` for apps targeting API 37 when they access LAN devices directly.

Search the app before adding any permission:

```bash
rg 'Socket|Datagram|Multicast|NSD|NsdManager|WifiManager|InetAddress|127\.0\.0\.1|localhost'
```

If ApexCore does **not** use local network access, do not add the permission.

If it does, implement the correct runtime flow and test it.

### F3. Dynamic native loading

Android 17 hardens dynamic native code loading. Any files loaded with `System.load()` must satisfy the platform's read-only requirements.

The baseline search found no `System.loadLibrary()` usage, but repeat a complete search:

```bash
rg 'System\.load|System\.loadLibrary|Runtime\.getRuntime\(\).*load'
```

If found, audit and test it.

### F4. Large screens / resizability

API 37 removes older large-screen orientation/resizability opt-outs. Test ApexCore on at least one `sw >= 600dp` configuration and make sure the UI does not crash or become unusable.

### F5. App memory limits

Android 17 introduces app memory limits. This task is primarily DEX/R8/16 KB work, but perform a lightweight smoke check for:

- unbounded bitmap/icon caching;
- obvious retained large bitmaps;
- process memory growth across navigation;
- repeated app/process list refresh;
- background state retaining unnecessary UI bitmaps.

Do not turn this task into a broad architecture rewrite. File a follow-up if profiling reveals a separate memory-leak project.

---

# 10. Phase G — Reduce the 19.9 MB uncompressed DEX footprint

The Play Console baseline reports:

```text
Total uncompressed DEX size: 19.9 MB
```

The primary remediation should be R8, not arbitrary feature deletion.

## G1. Measure release DEX before/after

Capture both pre-R8 and post-R8 metrics with reproducible commands/tools.

Useful options include:

```bash
apkanalyzer files list <apk>
apkanalyzer dex packages <apk>
```

or equivalent Android Studio APK Analyzer measurements.

Record:

- classes.dex size
- classes2.dex, etc., if present
- total uncompressed DEX
- APK size
- AAB size
- number of DEX files

## G2. Inspect dependency weight

Use:

```bash
./gradlew :app:dependencies --configuration releaseRuntimeClasspath
```

Look for:

- duplicated libraries;
- obsolete compatibility libraries;
- accidentally included test/debug artifacts;
- dependencies that pull large unused transitive graphs;
- broad reflection frameworks that prevent R8 optimization.

Do not remove a dependency without proving its app functionality is unused.

## G3. Success target

Hard compliance target:

- Play reports at least the required optimization/shrinking/obfuscation coverage.

Engineering target:

- materially reduce the 19.9 MB uncompressed DEX baseline;
- aim for **>= 20% DEX reduction** if achievable without functionality regressions;
- if reduction is smaller, provide evidence explaining why and still prove R8 is fully active.

Do not trade correctness for an arbitrary size number.

---

# 11. Phase H — R8 Configuration Analyzer / keep-rule quality

With the preferred AGP 9.3.x baseline, generate the stable R8 Configuration Analyzer report:

```bash
./gradlew :app:analyzeReleaseR8Config
```

Expected report location:

```text
app/build/reports/r8/r8-config-analyzer-release.html
```

Record the report's:

```text
shrinking score
optimization score
obfuscation score
broadest keep rules
unused rules
subsumed/duplicate rules
third-party rules with high impact
```

The Analyzer is used to find over-broad rules, **not** to justify deleting safety rules without runtime validation.

In particular:

- Keep the existing single-class `@Keep` on `ShizukuUserService` unless a separately reviewed redesign makes the external entry point statically safe.
- A tiny score cost for one critical externally-instantiated class is acceptable.
- Do not trade Shizuku functionality for a cosmetic percentage increase.

If the selected AGP unexpectedly does not provide `analyzeReleaseR8Config`, manually inspect:

```text
effective R8 configuration
mapping.txt
usage.txt
seeds.txt
dependency consumer rules
```

Do not move to preview R8/AGP solely to obtain the report.

---
# 12. Phase I — Automated verification gates

Before marking the task complete, run from a clean checkout/worktree:

```bash
./gradlew --version

./gradlew clean \
  :app:test \
  :app:lintRelease \
  :app:assembleDebug \
  :app:assembleRelease \
  :app:bundleRelease \
  :app:analyzeReleaseR8Config
```

## I1. Important: debug instrumentation is not R8 proof

This may still be useful:

```bash
./gradlew :app:connectedDebugAndroidTest
```

but it **does not prove that R8 preserved functionality**, because the debug variant normally does not use production minification/resource shrinking.

Do not write "R8 verified" based on debug instrumentation.

## I2. Required optimized-artifact test

At minimum:

```bash
adb install -r <optimized-release-or-release-equivalent.apk>
```

and execute the complete P0 runtime matrix from the next section.

The installed artifact must have the same:

```text
R8 code optimization
shrinking
obfuscation
optimized resource shrinking
consumer keep rules
project keep rules
```

as the production bundle.

## I3. Optional but preferred `r8Qa` build type

If production signing or instrumentation makes direct `release` testing inconvenient, create a dedicated **test-only build type** such as `r8Qa` that:

- uses debug signing;
- copies the release R8/resource-optimization configuration;
- uses the same application code/resources/dependencies;
- is clearly not publishable;
- can run Android instrumentation tests.

Conceptually:

```kotlin
create("r8Qa") {
    initWith(getByName("release"))
    signingConfig = signingConfigs.getByName("debug")
    matchingFallbacks += listOf("release")
}
```

Then make sure the variant really remains minified/optimized after `initWith`.

List tasks first:

```bash
./gradlew :app:tasks --all | grep -i r8Qa
```

Run the generated connected instrumentation task if available.

Do not hard-code a Gradle task name into CI until `tasks --all` confirms it for the chosen AGP version.

The purpose of `r8Qa` is testing only. Production release remains the source of truth.

## I4. Release stacktrace retracing test

Trigger or use a controlled test exception in a non-production test path if one already exists, then verify that the exact release mapping can retrace it.

Do not intentionally crash normal production UX just to satisfy this task.

No new warnings/errors from AGP migration should be ignored simply because the bundle builds.

---
# 13. Release smoke-test matrix

Test the optimized release build on at least:

| Device/runtime | Purpose |
|---|---|
| API 37 / Android 17, normal page size configuration | targetSdk behavior |
| 16 KB page-size emulator/device with `PAGE_SIZE=16384` | 16 KB runtime proof |
| One lower supported Android version close to `minSdk` if practical | regression guard |
| One typical modern physical Android device if available | real-world smoke test |
| Tablet/foldable or `sw>=600dp` emulator | API 37 large-screen behavior |

For ApexCore-specific functionality, verify every flow whose classes/resources might be removed, renamed, merged, or optimized by R8.

### P0 — must pass on an actually optimized artifact

- cold launcher start
- warm launcher start
- process-death relaunch
- app discovery
- game discovery
- app/game detail screen
- launch intent for a selected app/game
- Shizuku permission flow
- Shizuku `UserService` bind
- Shizuku `uid()` AIDL call
- Shizuku harmless `execute()` AIDL call
- Shizuku unbind + rebind
- Shizuku binder/service death recovery where practical
- root shell backend on a rooted test device
- any legacy/fallback privileged executor that remains production-reachable
- `FreezeReceiver` real broadcast path
- freeze/optimization action
- process controls
- `GameOverlayService` start/show/action/stop/restart
- `RamFillerService` start/stop/restart in its secondary process
- overlay permission flow
- foreground service notification/icon
- settings persistence across restart
- privacy-policy asset opening
- FPS/shell assets if their production path is active
- background -> foreground resume

### P1 — regression coverage

- accessibility-based actions, if currently exposed
- deep links/intents, if present
- reduced-motion/accessibility settings
- rotation/resizing
- tablet/foldable layout
- low-memory/process-recreation behavior
- lower supported Android API smoke

For every P0 failure:

```text
capture logcat
retrace with exact mapping.txt
record whether debug passes
record whether unoptimized local build passes
identify removed/renamed/optimized item
add only a narrow fix
rerun all P0 items
```

R8 regressions that appear only in optimized builds are release blockers.

---

# 14. Expected files changed

Likely:

```text
build.gradle.kts
gradle/wrapper/gradle-wrapper.properties
app/build.gradle.kts
app/src/main/keepRules/apexcore.keep    # preferred with AGP 9.3+
# OR app/proguard-rules.pro                 # only if using legacy R8 DSL
```

Possibly, only when required by real compatibility findings:

```text
gradle.properties
app/src/main/AndroidManifest.xml
specific Kotlin source files affected by Android 17 behavior changes
dependency versions required for 16 KB compatibility
CI workflow files pinned to an old JDK/Gradle environment
docs/results/T14-play-quality-baseline.md
docs/results/T14-play-quality-final.md
```

Avoid unrelated source-formatting or dependency churn.

---

# 15. Required final evidence report

Create:

```text
docs/results/T14-play-quality-final.md
```

Include:

## Toolchain

```text
AGP:
Gradle:
JDK:
compileSdk:
targetSdk:
minSdk:
Compose compiler:
```

## R8

```text
optimization enabled:
optimization DSL used (AGP 9.3+ / legacy):
legacy isMinifyEnabled (if applicable):
legacy isShrinkResources (if applicable):
default optimization config:
project rules file:
mapping.txt generated:
usage.txt generated:
R8 Analyzer report:
shrinking score:
optimization score:
obfuscation score:
existing ShizukuUserService @Keep preserved:
R8 errors/warnings:
```

## Size

```text
Before uncompressed DEX:
After uncompressed DEX:
DEX reduction:
Before APK:
After APK:
Before AAB:
After AAB:
```

## 16 KB

```text
AAB native libraries:
APK native libraries:
bundletool alignment:
zipalign -P 16:
test device PAGE_SIZE:
runtime result:
```

## Tests

```text
unit:
lintRelease:
assembleDebug:
assembleRelease:
bundleRelease:
instrumentation:
API 37 smoke:
16 KB smoke:
lower-API smoke:
```

## Functional verification

Explicit PASS/FAIL for:

```text
Games
All Apps
launch
Shizuku permission
Shizuku UserService bind
Shizuku AIDL uid()
Shizuku AIDL execute()
Shizuku unbind/rebind
root
FreezeReceiver
freeze/optimization
GameOverlayService
RamFillerService
overlay/HUD
settings
process controls
resume/relaunch
large screen
```

## Remaining Play Console verification

Note that Play Console percentages are server-side results and can only be confirmed after uploading the newly generated AAB. The worker must prepare and verify the bundle locally; do not fabricate Play Console percentages.

---

# 16. Acceptance criteria

The task is complete only when all applicable items below are true.

## SDK / build

- [ ] `compileSdk = 37`
- [ ] `targetSdk = 37`
- [ ] `minSdk` remains 24 unless a documented requirement changed it
- [ ] AGP is at least 9.1.1; preferred task baseline is stable 9.3.2 for R8 Analyzer support
- [ ] Gradle is compatible with the selected AGP
- [ ] JDK requirement is documented and CI/local build agree
- [ ] AGP 9 built-in Kotlin migration is complete
- [ ] `org.jetbrains.kotlin.android` is not used as a permanent AGP 9 workaround
- [ ] Compose compiler configuration is compatible

## R8 / optimization

- [ ] Production release optimization is enabled
- [ ] Optimized resource shrinking is enabled
- [ ] Preferred AGP 9.3+ `optimization { enable = true }` DSL is used, or the chosen legacy equivalent is documented
- [ ] Keep-rules location matches the chosen AGP DSL and is not duplicated
- [ ] No global `-dontoptimize`
- [ ] No global `-dontshrink`
- [ ] No global `-dontobfuscate`
- [ ] No blanket project-wide/package-wide keep rule
- [ ] Existing `@Keep` on `ShizukuUserService` remains intact
- [ ] Release `mapping.txt` is generated
- [ ] Release `usage.txt` / equivalent shrink evidence is inspected
- [ ] R8 Configuration Analyzer report is generated on AGP 9.3+
- [ ] Broad/unused/subsumed rules are reviewed
- [ ] Every added project keep rule has WHY / PROTECTS / VERIFIED BY documentation
- [ ] No critical dynamic entry point is shown as removed
- [ ] Release DEX size is measured before and after
- [ ] An actually minified/optimized APK is installed on a device
- [ ] Debug instrumentation is **not** used as the sole R8 proof
- [ ] Shizuku UserService binds in the optimized build
- [ ] `IPrivilegedExecutor.uid()` works in the optimized build
- [ ] `IPrivilegedExecutor.execute()` works in the optimized build
- [ ] Shizuku unbind/rebind works in the optimized build
- [ ] Manifest receiver/service paths work in the optimized build
- [ ] Root/fallback privileged paths are verified where applicable
- [ ] Required resources/assets survive resource shrinking
- [ ] No release-only R8 crash remains
- [ ] Any obfuscated crash used during testing can be retraced with the exact mapping

## 16 KB

- [ ] Final AAB inspected for `.so` libraries
- [ ] Every packaged `.so` owner/version is documented
- [ ] All packaged native dependencies are 16 KB compatible
- [ ] AAB reports correct alignment
- [ ] Release APK passes `zipalign -c -P 16 -v 4`
- [ ] Runtime test device confirms `getconf PAGE_SIZE = 16384`
- [ ] Optimized release app launches and passes smoke tests on that device

## Android 17

- [ ] Android 17 all-app behavior changes reviewed
- [ ] Android 17 target-37 behavior changes reviewed
- [ ] Reflection/platform-internal usage audited
- [ ] Local network behavior audited; permission added only if actually needed
- [ ] Dynamic native loading audited
- [ ] Large-screen behavior tested
- [ ] No targetSdk-37 regression found in critical ApexCore flows

## Build/test

- [ ] `:app:test` passes
- [ ] `:app:lintRelease` passes or any pre-existing issue is clearly documented
- [ ] `:app:assembleDebug` passes
- [ ] `:app:assembleRelease` passes
- [ ] `:app:bundleRelease` passes
- [ ] Optimized release-equivalent P0 smoke matrix passes
- [ ] At least one device test exercises genuinely minified code/resources
- [ ] `connectedDebugAndroidTest` is not counted as R8 validation by itself
- [ ] Result report committed

## Play Console follow-up

After the new AAB is uploaded:

- [ ] Target SDK reports API 37
- [ ] 16 KB support remains green
- [ ] App optimization no longer reports `Low`
- [ ] Optimization percentage is populated
- [ ] Shrinking percentage is populated
- [ ] Obfuscation is materially above the prior 1%
- [ ] R8 configuration is detected
- [ ] Optimization/shrinking/obfuscation coverage meets the Google Play minimum threshold
- [ ] Uncompressed DEX is lower than the 19.9 MB baseline or the result report explains any unexpected result

---

# 17. Stop conditions / escalation

Stop and document rather than papering over the problem if:

1. a dependency ships a native library that is not 16 KB compatible and no compatible release exists;
2. R8 appears to require a blanket keep rule to keep ApexCore functional — stop and isolate the exact dynamic entry point instead;
3. AGP 9 migration exposes a plugin incompatible with built-in Kotlin;
4. targetSdk 37 requires a functional change whose UX/security implications are larger than this task;
5. optimized-build-only Shizuku/AIDL/root/FreezeReceiver/GameOverlayService/RamFillerService functionality fails and a narrow, evidence-backed R8 fix cannot be justified;
6. the 16 KB release fails on a real `PAGE_SIZE=16384` runtime;
7. API 37 introduces a behavior change affecting ApexCore's privileged/system-management architecture that needs a separate design decision.

In any stop case, produce:

```text
root cause
reproduction
affected file/dependency
attempted fixes
recommended next action
```

Do not mark the task done.

---

# 18. Implementation order — do not reorder casually

Use this sequence to keep failures attributable:

```text
1. Record baseline
2. Upgrade Gradle + AGP
3. Migrate AGP 9 built-in Kotlin
4. Set compileSdk/targetSdk 37
5. Get debug build/tests green
6. Inventory dynamic/reflective/Binder/resource entry points
7. Preserve the existing ShizukuUserService @Keep
8. Enable production R8 + optimized resource shrinking
9. Generate R8 Analyzer + mapping/usage evidence
10. Build and install an actually optimized artifact
11. Run Shizuku/AIDL/manifest-service/root P0 smoke gates
12. Fix only proven R8 compatibility issues with narrow rules
13. Rerun the full P0 optimized-build matrix after every R8 rule change
14. Inspect AAB/APK native libraries
15. Verify 16 KB alignment
16. Test optimized release on 16 KB runtime
17. Audit Android 17 behavior changes
18. Measure DEX/APK/AAB size delta
19. Run full release verification matrix
20. Write final evidence report
21. Upload AAB separately and verify Play Console metrics
```

This order matters. Do not enable AGP migration, targetSdk changes, R8, dependency upgrades, and functional rewrites in one unreviewable commit.

Prefer several focused commits, for example:

```text
build: upgrade ApexCore to AGP 9.3.2 and Gradle 9.5.0
build: migrate Android module to AGP built-in Kotlin
build: target Android 17 API 37
build: enable release R8 and optimized resource shrinking
test: add optimized-release R8 safety gates
fix: add targeted R8 compatibility rules
test: verify 16 KB page-size release compatibility
docs: record Play quality optimization evidence
```

---

# 19. Source guidance

Worker must use the current official Android documentation as the authority:

1. Google Android Developers Blog — August 26, 2026  
   `https://android-developers.googleblog.com/2026/08/app-quality-memory-optimization-secure-onboarding.html`

2. 16 KB page-size support  
   `https://developer.android.com/guide/practices/page-sizes`

3. Enable app optimization with R8  
   `https://developer.android.com/topic/performance/app-optimization/enable-app-optimization`

4. AGP 9.1.1 release notes — minimum API 37 compatibility reference  
   `https://developer.android.com/build/releases/agp-9-1-0-release-notes`

5. Android Gradle Plugin version/API compatibility  
   `https://developer.android.com/build/releases/about-agp`

5a. AGP 9.3 release notes / R8 Analyzer + optimization DSL  
   `https://developer.android.com/build/releases/agp-9-3-0-release-notes`

5b. R8 Configuration Analyzer  
   `https://developer.android.com/topic/performance/app-optimization/r8-configuration-analyzer`

5c. Optimization for library authors / consumer keep rules  
   `https://developer.android.com/topic/performance/app-optimization/library-optimization`

6. Migrate to built-in Kotlin  
   `https://developer.android.com/build/migrate-to-built-in-kotlin`

7. Android 17 SDK setup  
   `https://developer.android.com/about/versions/17/setup-sdk`

8. Android 17 behavior changes — all apps  
   `https://developer.android.com/about/versions/17/behavior-changes-all`

9. Android 17 behavior changes — target API 37+  
   `https://developer.android.com/about/versions/17/behavior-changes-17`

If guidance changes after this task was authored, use the latest stable official Android guidance and note the deviation in the result report.

---

# 19A. R8 correctness re-review checklist

Before closing the worker task, a reviewer other than the implementer should answer **YES** to every item:

```text
[ ] Did we test the optimized artifact, not only debug?
[ ] Is ShizukuUserService still @Keep?
[ ] Did Shizuku bind and execute over AIDL after R8?
[ ] Did we inspect mapping/usage/effective keep rules?
[ ] Did we run the R8 Configuration Analyzer?
[ ] Are there zero blanket ApexCore package keep rules?
[ ] Does every new keep rule have a precise runtime justification?
[ ] Did manifest services/receiver actually execute after R8?
[ ] Did root/fallback privileged execution still work where testable?
[ ] Did required assets/resources survive shrinking?
[ ] Did we rerun the complete P0 matrix after the final keep-rule change?
[ ] Can release crashes be retraced with the exact mapping?
[ ] Did DEX reduction come from safe optimization rather than removed features?
```

If any answer is NO, the R8 portion of this task is **not done**.

---

# 20. Definition of done

ApexCore is done with this task when a clean production-equivalent release build:

- targets API 37;
- builds using an API-37-supported AGP 9.x stack;
- uses AGP 9 built-in Kotlin correctly;
- has R8 code optimization, shrinking, obfuscation, and resource shrinking genuinely enabled;
- materially reduces the release DEX footprint where possible;
- contains no unexplained/broken native libraries;
- is verified for 16 KB package alignment;
- runs on an actual 16 KB page-size Android runtime;
- preserves the existing Shizuku external-service entry point and survives real Shizuku/AIDL privileged execution after R8;
- proves MainActivity, FreezeReceiver, GameOverlayService, RamFillerService, root/fallback backends, and required assets still function in an actually optimized artifact;
- has no R8 safety claim based only on debug instrumentation;
- passes API 37 compatibility testing;
- produces the mapping/evidence needed for production;
- is ready for upload so Play Console can recompute the optimization metrics.

**Do not close the task based solely on a successful `bundleRelease`. Runtime verification of the optimized release is mandatory.**
