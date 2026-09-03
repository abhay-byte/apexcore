# ApexCore v1.7 Release Notes

**Build:** 8  
**Target SDK:** 37 (Android 17)  
**Min SDK:** 24  
**Date:** September 2026  

---

## Highlights

### 1. Target SDK 37 (Android 17) Compliance
ApexCore now targets Android 17 (API 37), meeting the latest Google Play quality and platform requirements. Build toolchain upgraded to Android Gradle Plugin 9.3.2, Gradle 9.5.0, and AGP built-in Kotlin compiler.

### 2. 16 KB Memory Page-Size Alignment
- Full support for 16 KB page-size devices.
- Bundled native libraries verified and aligned to 16 KB boundaries (`PAGE_ALIGNMENT_16K`).
- Zipalign verified with 16 KB alignment requirements (`zipalign -c -P 16 -v 4`).

### 3. Production R8 Optimization & DEX Shrinking
- Full release R8 code shrinking, obfuscation, and resource optimization enabled via `optimization { enable = true }`.
- Release APK size reduced from 9.96 MB down to 3.27 MB (**-65.5%**).
- MultiDex eliminated: single classes.dex shrunk from 19.86 MB down to 2.56 MB (**-87.1%**).
- Shizuku UserService (`@Keep`), AIDL IPC interfaces, and reflection paths safely protected.

### 4. UI & Settings Refresh
- Settings Toolbox now explicitly reports both Version and Build number (`VERSION 1.7 · BUILD 8`).
- Strict lint checks passed cleanly across all production source sets.
