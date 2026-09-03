# Baseline — T14 Play Quality SDK 37, 16 KB Page Size, R8 Optimization

**Date:** 2026-09-03  
**Base commit:** `c6fbee8` (v1.6 on `main`)

## Environment Baseline
- **AGP:** 8.8.2
- **Gradle:** 8.14
- **JVM:** OpenJDK 17.0.20.1
- **compileSdk:** 36
- **targetSdk:** 36
- **minSdk:** 24
- **Kotlin:** 2.0.21

## Artifact Baseline
- **Release APK size:** 9,961,857 bytes (9.50 MB)
- **Release AAB size:** 9,638,516 bytes (9.19 MB)
- **classes.dex:** 13,579,868 bytes
- **classes2.dex:** 6,284,012 bytes
- **Total uncompressed DEX:** 19,863,880 bytes (~19.86 MB)
- **Native .so libraries present:**
  - `lib/arm64-v8a/libandroidx.graphics.path.so` (10,096 bytes)
  - `lib/armeabi-v7a/libandroidx.graphics.path.so` (7,252 bytes)
  - `lib/x86/libandroidx.graphics.path.so` (9,284 bytes)
  - `lib/x86_64/libandroidx.graphics.path.so` (10,760 bytes)
- **R8 / ProGuard:** Disabled (`isMinifyEnabled = false`, `isShrinkResources = false`)
- **Unit test status:** 166 passing (TuneMidSessionPerBundleOffTest timing-sensitive in batch run, passes isolated)
- **Lint status:** Warning on AGP 8.8.2 vs compileSdk 36
