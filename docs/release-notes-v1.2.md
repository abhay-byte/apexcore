# ApexCore v1.2 Release Notes

## Version Details
* **Version Name:** 1.2
* **Version Code:** 3
* **Target SDK:** 36 (Android 16 Ready)
* **Minimum SDK:** 24 (Android 7.0+)
* **Release Date:** August 18, 2026

---

## Executive Summary

ApexCore 1.2 introduces **T12 Real Game Optimisation**, overhauling the application from simple process management to a hardware-safe, capability-verified, session-scoped game performance tuning platform.

---

## What's New in v1.2

### 1. 36-Option Kernel & System Tuning Catalog
A curated set of 36 options spanning 10 functional categories:
* **GPU:** Frequency floors, performance governors, Adreno bus scaling, and idle control.
* **CPU:** Floor boost, governor ramp-up tuning, energy model performance bias, and sched core allocation.
* **Input:** Touch polling rate boost and touchscreen sensitivity/game mode.
* **Thermal:** Game thermal profiles and sustained performance mode (hardware throttling always preserved).
* **Memory:** Proactive memory compaction, swappiness adjustment, and dirty ratio tuning.
* **I/O:** Block I/O read-ahead scaling and UFS performance mode.
* **Display:** Peak refresh rate enforcement, window scaling, and hardware overlay control.
* **Focus:** Game Do Not Disturb, heads-up notification blocking, animation scaling, and stay-awake locks.
* **Charging:** Game bypass charging, battery saver lockout, and fast-charge current caps.
* **Network:** Wi-Fi low-latency mode, packet pacing, 5GHz preference, and TCP congestion control (BBR).

### 2. Capability-First Hardware Probing
* Real-time probing at startup with a 3.5-second total budget and 120ms per-node timeout.
* Active write-verification ensures options are only enabled if the running kernel actually accepts writes.
* Clear UI status indicators explaining device capability limits without deceptive claims.

### 3. Reversible Sessions & Crash Recovery
* Automatic snapshots of all original values prior to game launch.
* Automatic reversion of all parameters upon game exit.
* Persistent `boot_id`-tagged recovery mechanism to restore settings if the app or device terminates unexpectedly.

### 4. Dual Watchdog Lifecycle
* Primary monitoring via floating overlay HUD.
* Standalone `UsageStatsManager` polling watchdog fallback when Draw Over Other Apps is not granted.

### 5. System Integration & Stability
* Android 16 (Target SDK 36) support with updated Shizuku IPC binder resolution.
* Fully offline, zero-telemetry architecture compliant with Google Play Store policies.
