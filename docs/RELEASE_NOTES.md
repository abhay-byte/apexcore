# ApexCore Release Notes

---

## Version 1.2 (Build 3) — Real Game Optimisation Engine

**Release Date:** August 18, 2026  
**Target SDK:** 36 (Android 16 Ready)  
**Min SDK:** 24 (Android 7.0+)

### Highlights

Version 1.2 replaces legacy placeholder switches with **T12 Real Game Optimisation**: a capability-gated, session-scoped, reversible kernel & Android system tuning architecture.

---

### Key Features & Improvements

#### 1. 36 Capability-Gated Tuning Options Across 10 Categories
ApexCore now supports a comprehensive clean-room catalog of 36 kernel sysfs and system setting options:
* **GPU (4 options):** GPU Minimum Frequency Floor, GPU Governor Performance, Adreno Bus Scaling Boost, Adreno Force Idle Off.
* **CPU (4 options):** CPU Floor Frequency Boost, CPU Governor Ramp-Up, CPU Energy Model Favor Performance, CPU Performance Sched Core.
* **Input (2 options):** Touch Polling Rate Boost, Touchscreen Sensitivity / Game Mode.
* **Thermal (2 options):** Thermal Game Profile, Thermal Sustained Performance Mode *(Never disables hardware thermal throttling)*.
* **Memory (4 options):** Compaction Proactive Aggressive, Swappiness Tuning, Dirty Background Ratio Reduction, Drop Caches Page Cache Reclaim.
* **I/O (2 options):** Block I/O Read-Ahead Scaling, UFS High-Performance Mode.
* **Display (4 options):** Force Peak Refresh Rate, Lock Screen Resolution / Window Scale, Disable Hardware Overlays (HW Comps), HDR Brightness Peak Boost.
* **Focus & DND (4 options):** Gaming Do Not Disturb, Heads-up Notification Suppression, Window Animation Scale Boost, Keep Screen Awake / Inactivity Timeout.
* **Charging & Battery (3 options):** Gaming Bypass Charging, Battery Saver Lockout, Fast Charge Current Cap.
* **Network & Connectivity (7 options):** Wi-Fi Low Latency Game Mode, Wi-Fi Power Save Disable, Wi-Fi Packet Pacing Boost, Wi-Fi Band Steering / 5 GHz Prefer, Cellular NR SA Prefer, Bluetooth Low Latency Audio (aptX Adaptive / LHDC), TCP Congestion Control (BBR/Cubic).

#### 2. Capability-First Hardware Probing
* **Safe On-Device Probing:** Probes kernel sysfs nodes on startup with a strict 3.5s total budget and 120ms per-node timeout.
* **Write Verification:** Sysfs nodes are only marked available if real test-writes and reads succeed; read-only or unsupported nodes are marked unavailable.
* **Honest Disabled States:** The UI clearly explains why an option is unavailable on the current kernel (e.g., `"Requires root on this kernel"`, `"Not supported by hardware/kernel"`).

#### 3. Session-Scoped & 100% Reversible Tuning
* **Snapshot & Rollback:** Before any sysfs node is modified, ApexCore captures the original value and saves it to a persistent snapshot tagged with `boot_id`.
* **Clean Session Teardown:** All kernel and system settings automatically revert to their pre-game state the moment the game session exits.
* **Reboot/Crash Recovery:** If the device reboots or the app is killed mid-game, orphaned snapshots are safely rolled back on the next launch or boot.

#### 4. Dual-Watchdog Reversion Architecture
* **Primary (Overlay HUD):** Tracks active game window focus and initiates instant teardown when the user exits the game.
* **Fallback (UsageStats Watchdog):** If Draw Over Other Apps permission is not granted, a background watchdog polls top-activity every 5s with a fail-safe restore policy.

#### 5. User Interface & Experience
* **New Game Optimisation Screen:** Full categorized settings UI with live search, per-option explanations, and capability status chips.
* **Live Kernel Readiness Badge:** Home screen displays exact kernel availability (e.g., `"14 available on this kernel"` or `"None on this kernel"`).
* **Thermal Protection Disclosure:** Clear, permanent disclosure emphasizing that thermal protections remain active at all times.

#### 6. Platform & Security Fixes
* **Android 14–16 Shizuku Compatibility:** Added `<queries>` declarations for `moe.shizuku.privileged.api` and cleaned up provider permissions, ensuring instant binder handshakes.
* **Google Play Policy Compliance:** Resolved deceptive behavior concerns by replacing placeholder toggles with genuine, capability-verified logic.

---

## Version 1.1 (Build 2) — Organic Zen Redesign

- Redesigned visual aesthetics with frosted glass blur (Haze), leaf tanks, and organic accents.
- Floating draggable performance HUD overlay for live FPS, RAM, and CPU telemetry.
- RAM Free memory reclaim feature with 90% allocation cap.
- App pinning to protect crucial background apps from freeze purges.

---

## Version 1.0 (Build 1) — Initial Architecture

- Core deep freeze framework using Shizuku (wireless debugging) and direct Root access.
- Local game library launcher.
- On-device local privacy policy (no analytics, no tracking, no internet telemetry).
