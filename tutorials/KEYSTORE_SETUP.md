# Release Keystore & GitHub Actions Signing Setup Guide

This guide covers how to generate a release signing keystore, convert it to Base64, and configure GitHub Secrets for automated release builds.

---

## 1. Generate the Release Keystore

Run the `keytool` command in your terminal. This generates a 2048-bit RSA key pair with a 10,000-day validity (~27 years).

### Using PowerShell (Windows)
```powershell
& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v -keystore "fpsmeter-release.jks" -alias "fpsmeter" -keyalg RSA -keysize 2048 -validity 10000
```

### Using Git Bash
```bash
"/c/Program Files/Android/Android Studio/jbr/bin/keytool.exe" -genkeypair -v -keystore "fpsmeter-release.jks" -alias "fpsmeter" -keyalg RSA -keysize 2048 -validity 10000
```

### Prompts Walkthrough:
1. **Enter keystore password:** Type a strong password (save it in a password manager).
2. **Re-enter new password:** Retype your password.
3. **What is your first and last name?** Your name or developer handle (e.g. `Romel Brosas`).
4. **Organizational Unit:** `Development` (or press Enter).
5. **Organization:** `rdevz-ph` (or press Enter).
6. **City or Locality:** `Manila` (or press Enter).
7. **State or Province:** Your province / region (or press Enter).
8. **Country Code:** `PH` (two-letter code).
9. **Is CN=... correct?** Type `yes` and press Enter.

> [!CAUTION]
> Back up your `fpsmeter-release.jks` file securely (e.g., in a password manager, encrypted drive, or cloud storage). If you lose this file or password, you will not be able to update existing app installations on devices.

---

## 2. Convert Keystore to Base64

To pass the binary `.jks` file to GitHub Actions securely, convert it into a Base64 string and copy it to your clipboard.

### Using PowerShell
```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("fpsmeter-release.jks")) | Set-Clipboard
```

### Using Git Bash
```bash
base64 -w 0 fpsmeter-release.jks | clip
```

The Base64 string is now in your clipboard ready to paste into GitHub.

---

## 3. Configure GitHub Repository Secrets

1. Go to your repository on GitHub: `https://github.com/rdevz-ph/FPS-Meter-Android`
2. Navigate to: **Settings** &rarr; **Secrets and variables** &rarr; **Actions**.
3. Click the green **New repository secret** button for each of the following:

| Secret Name | Value | Description |
| :--- | :--- | :--- |
| `KEYSTORE_BASE64` | *(Paste clipboard content)* | Base64-encoded string of `fpsmeter-release.jks`. |
| `KEYSTORE_PASSWORD` | *(Your keystore password)* | Password created in Step 1. |
| `KEY_ALIAS` | `fpsmeter` | Key alias used in Step 1. |
| `KEY_PASSWORD` | *(Your keystore password)* | Same as `KEYSTORE_PASSWORD` (PKCS12 standard). |

---

## 4. How the Workflows Use These Secrets

* **Debug Workflow (`.github/workflows/android.yml`):**
  - Triggers on every push to `main`.
  - Builds and uploads `FPS-Meter-${TAG}-debug.apk`.
  - Uses `gh release upload --clobber` to update the release without deleting existing release assets.

* **Release Workflow (`.github/workflows/release.yml`):**
  - Triggers on version tags (`v*`) or manual run via **Actions** &rarr; **Release Build** &rarr; **Run workflow**.
  - Decodes `KEYSTORE_BASE64`, passes credentials to Gradle via environment variables.
  - Produces `FPS-Meter-${TAG}-release.apk`.
  - Attaches the release APK directly to the existing GitHub release without deleting existing debug builds or release notes.
