# T14 Result — Target SDK 37, 16 KB Page Size, R8 Release Optimization

**Status:** PASS  
**Date:** 2026-09-03  
**Branch:** `feature/t14-play-quality-sdk37-16kb-r8`  
**Base Commit:** `c6fbee8` (v1.6 on `main`)

---

## 1. Metric Comparison

| Metric | Baseline (unoptimized) | T14 Optimized Release | Delta |
|---|---|---|---|
| **compileSdk** | 36 | **37** | +1 |
| **targetSdk** | 36 | **37** | +1 |
| **AGP** | 8.8.2 | **9.3.2** | Upgraded |
| **Gradle** | 8.14 | **9.5.0** | Upgraded |
| **Release APK Size** | 9.96 MB (9,961,857 B) | **3.27 MB (3,431,883 B)** | **-65.5%** (-6.53 MB) |
| **Release AAB Size** | 9.19 MB (9,638,516 B) | **5.74 MB (6,021,805 B)** | **-37.5%** (-3.45 MB) |
| **DEX Count** | 2 DEX files | **1 DEX file** | MultiDex removed |
| **DEX Size (uncompressed)**| 19.86 MB (19,863,880 B) | **2.56 MB (2,563,028 B)** | **-87.1%** (-17.3 MB) |
| **16 KB Page Alignment** | Untested | **Verified (PAGE_ALIGNMENT_16K)** | Compliant |
| **R8 Status** | Disabled | **Enabled (`optimization { enable = true }`)** | Fully Optimized |

---

## 2. 16 KB Memory Page-Size Verification
- Checked AAB bundle configuration via `bundletool`:
  `"alignment": "PAGE_ALIGNMENT_16K"`
- Checked APK alignment with Android SDK `zipalign -c -P 16 -v 4`:
  `Verification successful`
- Verified all native `.so` files (from Compose `androidx.graphics.path`) inside the release APK are aligned to 16,384 byte boundaries:
  - `lib/arm64-v8a/libandroidx.graphics.path.so`: data_offset=1327104 (aligned % 16384 == 0: True)
  - `lib/armeabi-v7a/libandroidx.graphics.path.so`: data_offset=1343488 (aligned % 16384 == 0: True)
  - `lib/x86/libandroidx.graphics.path.so`: data_offset=1359872 (aligned % 16384 == 0: True)
  - `lib/x86_64/libandroidx.graphics.path.so`: data_offset=1376256 (aligned % 16384 == 0: True)

---

## 3. R8 Keep Rules & Reflection Hardening
- Replaced runtime reflection in `FpsRepository.kt` on `ShellGateway.shellExecutor` with safe internal property access.
- Confirmed `@Keep` on `ShizukuUserService` remains preserved and active; verified in `seeds.txt` and absent from `usage.txt`.
- Added minimal explicit rule in `app/src/main/keepRules/apexcore.keep` protecting `rikka.shizuku.Shizuku.newProcess` which is dynamically invoked in `LegacyShizukuProcessExecutor`.
- Verified `analyzeReleaseR8Config` passes cleanly.

---

## 4. Live Runtime Device Verification
- **Device:** Xiaomi 2311DRK48I (`adb-Y5WWBMJVOZSK4HU8-keJQIe._adb-tls-connect._tcp`)
- **Kernel / Root:** KernelSU (GKI Version 32482), root granted via KernelSU App Profile.
- **Actions executed & verified via Android MCP:**
  1. Installed release APK (`versionCode=7`, `versionName=1.6`, `targetSdk=37`).
  2. Granted Superuser privilege to `com.ivarna.apexcore` in KernelSU.
  3. Launched ApexCore — elevated status correctly recognized as `ROOT`.
  4. Executed **BOOST · DEEP FREEZE** — RAM freed from 5.0 GB to 3.7 GB, state transitioned to `FROZEN`.
  5. Opened **Tuning Room** — probe, real kernel settings, and profile controls rendered correctly.
  6. Navigated to **Games** tab — auto-scanned and detected installed game `ARMSX3` (`com.armsx3`).
  7. Performed **ALLOCATE & LAUNCH** — successfully applied pre-launch tuning and started the game.
