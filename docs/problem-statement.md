# Problem Statement — ApexCore

## The Problem

Mobile gaming on Android suffers from background process bloat. After launching a game, the user's device is still running messengers, social apps, system services, and cached processes — consuming RAM, CPU, and I/O that should go to the game. This causes:

- **Stutter and frame drops** — background apps compete for CPU cycles
- **Longer load times** — kernel memory pressure triggers swap and reclaim
- **Thermal throttling** — sustained CPU load from background processes heats the SoC
- **Inconsistent performance** — results vary wildly depending on what's running

Existing solutions are either manual (user must force-stop apps one by one), require root (automation scripts), or are bundled into "game booster" bloatware that does not actually freeze third-party processes.

## The Solution

ApexCore is a one-tap Android app that freezes background user apps — not system processes — before launching a game. It works across four backends (Shizuku, Root, Accessibility, fallback) to cover every device without requiring root. The core loop:

1. User taps BOOST (or launches a game from the game list)
2. ApexCore enumerates running user apps via `ActivityManager`
3. Apps that match the freeze filter (not system, not whitelisted, not the target game) are frozen via `cmd activity kill` or equivalent
4. Freed memory is reported — RAM and swap reclaimed

## Key Differentiators

- **No root required** — Shizuku and Accessibility backends cover non-rooted devices
- **Game-aware freeze** — the game being launched is excluded from the freeze
- **Composable overlay HUD** — real-time FPS, RAM, CPU stats while gaming
- **ADB/Tasker integration** — `FREEZE_ALL` broadcast for automation workflows
- ~1.2 MB APK, no ads, no tracking, no permissions beyond what's needed

## Target Users

- Mobile gamers who want consistent frame rates
- Power users who freeze background apps before gaming
- Automation users who trigger freezes via Tasker/ADB

## Out of Scope (v0.x)

- Per-app freeze whitelist UI
- Boot-time auto-freeze scheduling
- Network / battery / temperature monitoring
- Multi-user or work profile support
