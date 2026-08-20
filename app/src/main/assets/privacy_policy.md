# Privacy Policy

**Last Updated: August 20, 2026**

**Developer:** ApexCore (private repository). Contact via in-app support or the store listing.

ApexCore ("we", "our", or "us") explains how the **ApexCore** Android application handles information. This policy is meant to match the Play Store listing developer name and the in-app Privacy Policy link.

## 1. No Data Collection

ApexCore is designed to run **completely locally** on your device.

* **No Telemetry or Analytics**: We do not track how you use the app.
* **No Network Transmissions**: The application does not collect, store, or transmit personal data, device identifiers, or installed-app lists to external servers. The app does not request the `INTERNET` permission for product features.
* **No Third-Party SDKs**: We do not integrate advertising, tracking, or analytics libraries (such as Google Analytics or Firebase) that harvest your info.
* **No Accounts**: ApexCore does not create user accounts or sign-in.

## 2. Local Storage Only

App preferences are stored only on your device (Android SharedPreferences / local app storage), for example:

* Preferred freeze backend (Shizuku / Root)
* Pinned apps list
* Theme and display preferences
* One-time setup dialog flags

**Retention:** Local preferences remain until you clear them or uninstall the app. There is **no cloud copy** and no server-side retention.

**How to delete local data:**

1. Android **Settings → Apps → ApexCore → Storage → Clear data** (or Clear storage), or  
2. **Uninstall** ApexCore.

Because we do not run accounts or remote profiles, there is no separate web account-deletion URL.

## 3. Permissions Utilized & Purpose

ApexCore requests system permissions only for on-device features:

### A. System Alert Window (Draw Over Other Apps)

* **Purpose**: Display the real-time gameplay performance HUD overlay (FPS, memory, CPU) on top of games.
* **Security**: The overlay runs locally and shows transient system stats; it does not record your screen or log touch inputs.

### B. Foreground Service (Performance HUD)

* **Purpose**: Runs the draggable performance HUD as a foreground service (type `specialUse`) so live game stats can remain visible while you play.
* **Security**: User-triggered; stoppable at any time; runs only while the HUD is active.

### C. Superuser (Root Access - Optional)

* **Purpose**: Run shell commands (such as `am force-stop`) locally to freeze background apps when you choose Root.
* **Security**: Commands run on-device via `su` and are limited to process management you initiate.

### D. Shizuku API (Optional - Recommended)

* **Purpose**: Access local Android framework APIs to freeze background processes without full root credentials.
* **Security**: Communication is local binder traffic with the Shizuku manager you install and authorize separately.

### E. Package Visibility (Installed Apps List)

* **Purpose**: List installed apps for the games library picker and to choose freeze targets.
* **Security**: Package lists are used only on-device and never leave the device.

### F. Kill Background Processes

* **Purpose**: Best-effort cache clearing on devices without elevation; not used to claim a working deep-freeze without Shizuku or Root.

## 4. No Accessibility Service

ApexCore does **not** declare, request, or use an Accessibility service in this release. Accessibility-based freeze from earlier development is **not** included — not implemented as a product path, not advertised, and cannot be enabled.

## 5. Security Practices

* Freeze and memory tools run only after **you** start them (no silent background freeze of other apps).
* No sale of personal data (we do not collect personal data for sale or sharing).
* Source is private; no public build artifacts are published.

## 6. Verification

Builds are reproducible from private source; no public repository is advertised.

## 7. Changes to This Policy

We may update this Privacy Policy from time to time. Changes are posted in this document and shipped with the app.

## 8. Contact

Questions about this policy or the app:

* In-app support / Settings
* App store listing contact