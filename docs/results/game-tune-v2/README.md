# Game tune v2 verification

This directory records the hardware evidence for T13. The requested device scope is one physical realme X2 Pro only; no other connected device is part of this result.

The release gate is deliberately read-only on stock/non-root hardware. It verifies the device identity, discovers CPUFreq policies dynamically, checks that every discovered min/max pair is ordered, and observes Android thermal status without changing thermal policy.

The full OEM matrix in the T13 plan is not claimed here because it requires additional hardware and the test scope explicitly excludes those devices. Root-only writes, Shizuku UserService writes, GPU devfreq writes, and Android Game Mode writes remain capability-gated and are only reported as verified when their readback succeeds.

Run the device gate with the realme serial selected explicitly:

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest \
  -PapplicationIdOverride=com.ivarna.apexcore.t13device
adb -s 2a580689 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s 2a580689 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s 2a580689 shell am instrument -w -e class \
  com.ivarna.apexcore.tune.RealmeX2ProReleaseGateTest \
  com.ivarna.apexcore.t13device.test/androidx.test.runner.AndroidJUnitRunner
```

Recorded run: 3 tests passed on 2026-08-25. The `applicationIdOverride` is used only to avoid replacing an existing differently signed install during device verification.
