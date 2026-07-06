# Privacy Policy

**Last Updated: July 6, 2026**

ApexCore ("we", "our", or "us") is dedicated to protecting your privacy. This Privacy Policy explains our practices regarding data collection and usage in the **ApexCore** Android application.

## 1. No Data Collection

ApexCore is designed to run **completely locally** on your device.
* **No Telemetry or Analytics**: We do not track how you use the app.
* **No Network Transmissions**: The application does not collect, store, or transmit any personal data, device information, or library details to external servers.
* **No Third-Party SDKs**: We do not integrate any advertising, tracking, or analytics libraries (such as Google Analytics or Firebase) that could harvest your info.

## 2. Permissions Utilized & Purpose

To freeze background processes and display active performance stats, ApexCore requests several system permissions. Here is exactly what they are used for:

### A. System Alert Window (Draw Over Other Apps)
* **Purpose**: Used solely to display the real-time gameplay performance HUD overlay (FPS & memory usage telemetry) on top of games.
* **Security**: This overlay runs entirely locally and displays transient system stats; it does not record your screen or log touch inputs.

### B. Accessibility Service (Optional Fallback)
* **Purpose**: Used as a secure fallback backend to automate clicking the "Force Stop" button in Android's system Settings app. This allows freezing background tasks if Shizuku or Root accesses are not available.
* **Security**: We do not collect, monitor, or store any screen text, keystrokes, or user behavior. The service executes only when you explicitly tap the "Optimize" button.

### C. Superuser (Root Access - Optional)
* **Purpose**: Used to run shell commands (like `am force-stop` or process suspend operations) directly to freeze background apps.
* **Security**: Root commands are run locally via the system `su` binary and are strictly limited to process management.

### D. Shizuku API (Optional - Recommended)
* **Purpose**: Accesses local Android framework APIs to securely manage process states and execute deep freezing without requiring root credentials.
* **Security**: Communication occurs locally via binder transactions with the Shizuku system manager.

## 3. Open Source Verification

As an open-source project, our code is fully auditable. You can review our implementation, check our permission handlers, and build the app from source to verify that no tracking or telemetry code exists.

## 4. Changes to This Policy

We may update this Privacy Policy from time to time. Any changes will be posted within this document and committed directly to the project repository.

## 5. Contact Information

If you have any questions or concerns about this policy or the application, please reach out via our GitHub repository.
