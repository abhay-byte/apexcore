# Regression T10 — ApexCore full feature matrix

| Field | Value |
|-------|-------|
| **Date** | 2026-08-03 |
| **Branch** | `T10c-regression-play-compliance` (base `b17c3f5` = T10b) |
| **Device** | TBD — rows 1–12 require physical device |
| **CI-verifiable rows** | 13–14 ✅ (see below) |

Run on the combined T10a+b+c build. Update ☐ → ☑ with logcat snippet or
short note. Rows 13–14 already executed for this slice.

| # | Feature | Check | Pass |
|---|---------|-------|------|
| 1 | Home BOOST Standard | Limited mode honest; killBackground or clear copy | ☐ device |
| 2 | Home BOOST Shizuku | Real force-stop; logcat backend=Shizuku | ☐ device |
| 3 | Home BOOST Root | Real force-stop if device allows | ☐ device |
| 4 | Backend dropdown | Ready flags; switch re-detects | ☐ device |
| 5 | Games add/remove | T8 picker still works | ☐ device |
| 6 | Game launch | Freeze then start + overlay starts | ☐ device |
| 7 | Overlay BOOST | Real freeze; toast real numbers | ☐ device |
| 8 | RAM Free | Δ matches adb MemAvailable ~noise | ☐ device |
| 9 | Pin apps | Pinned survives all freeze entry points | ☐ device |
| 10 | RAM Free cancel / pause | Safe release; no crash | ☐ device |
| 11 | First-run no Shizuku | Setup dialog honest (no a11y card — T10c removed it) | ☐ device |
| 12 | Rotation Home / Games / RAM Free | No crash; state OK | ☐ device |
| 13 | Build | `assembleDebug` + `assembleRelease` | ☑ 2026-08-03 — BUILD SUCCESSFUL; apks under `app/build/outputs/apk/{debug,release}/` |
| 14 | Unit tests | `./gradlew :app:testDebugUnitTest` | ☑ 2026-08-03 — 30 tests, 0 failures |

## This-slice change surface (what rows 1–12 must re-check)

- `FreezeReceiver` now `exported="false"` — no external FREEZE_ALL broadcasts (row 1–3 unaffected: BOOST is in-app).
- SetupDialog: Accessibility card removed; Root card full-width; new PRIVACY POLICY chip (row 11).
- `AccessibilityFreezeBackend.isReady()` hard `false` — cannot affect product path (resolver excludes it).
- `FORCE_STOP_PACKAGES` permission removed — no code used it.
- Privacy policy + listing copy de-claimed a11y.

## Suggested adb evidence format

```
adb logcat -s ApexCore.Freeze | grep freezeAll
# row 2: "freezeAll via Shizuku -> N apps" + "killed=N failed=0"
# row 3: "freezeAll via Root -> N apps"
```
