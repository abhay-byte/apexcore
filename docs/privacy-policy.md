# Privacy Policy

**Last Updated: August 3, 2026**

ApexCore ("we", "our", or "us") is dedicated to protecting your privacy. This Privacy Policy explains our practices regarding data collection and usage in the **ApexCore** Android application.

## 1. No Data Collection

ApexCore is designed to run **completely locally** on your device.
* **No Telemetry or Analytics**: We do not track how you use the app.
* **No Network Transmissions**: The application does not collect, store, or transmit any personal data, device information, or library details to external servers.
* **No Third-Party SDKs**: We do not integrate any advertising, tracking, or analytics libraries (such as Google Analytics or Firebase) that could harvest your info.
* **Local-only storage**: App preferences (such as your selected freeze backend and pinned apps list) are stored only on your device and are never transmitted anywhere.

## 2. Permissions Utilized & Purpose

ApexCore requests several system permissions to provide its features. Here is exactly what they are used for:

### A. System Alert Window (Draw Over Other Apps)
* **Purpose**: Used solely to display the real-time gameplay performance HUD overlay (FPS & memory usage telemetry) on top of games.
* **Security**: This overlay runs entirely locally and displays transient system stats; it does not record your screen or log touch inputs.

### B. Foreground Service (Performance HUD)
* **Purpose**: Runs the draggable performance HUD as a foreground service (type `specialUse`) so it can keep showing live game stats while you play.
* **Security**: The service runs only while the HUD is active, is user-triggered, and can be stopped by the user at any time.

### C. Superuser (Root Access - Optional)
* **Purpose**: Used to run shell commands (like `am force-stop`) directly to freeze background apps.
* **Security**: Root commands are run locally via the system `su` binary and are strictly limited to process management.

### D. Shizuku API (Optional - Recommended)
* **Purpose**: Accesses local Android framework APIs to securely manage process states and execute deep freezing without requiring root credentials.
* **Security**: Communication occurs locally via binder transactions with the Shizuku system manager.

### E. Package Visibility (Installed Apps List)
* **Purpose**: Used to list installed apps for the games library picker and to determine which background apps are safe freeze targets.
* **Security**: The list of installed packages is used locally only and never leaves your device.

### F. Kill Background Processes
* **Purpose**: Requested for best-effort cache clearing on devices without elevation; it is not used to claim a working deep-freeze on unprivileged devices.

## 3. No Accessibility Service

ApexCore does **not** declare, request, or use an Accessibility service in this release. The accessibility-based freeze path that appeared in earlier development versions is **not included** — it is not implemented, not advertised, and cannot be enabled.

## 4. Open Source Verification

As an open-source project, our code is fully auditable. You can review our implementation, check our permission handlers, and build the app from source to verify that no tracking or telemetry code exists.

## 5. Changes to This Policy

We may update this Privacy Policy from time to time. Any changes will be posted within this document and committed directly to the project repository.

## 6. Contact Information

If you have any questions or concerns about this policy or the application, please reach out via our GitHub repository.
