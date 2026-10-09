# Contributing to FPS Meter

Thank you for your interest in contributing to FPS Meter. Contributions from the community help make this project better for everyone.

You can contribute in several ways:
- Reporting bugs and issues
- Suggesting new features or improvements
- Translating the app into new languages
- Improving documentation
- Submitting code changes and bug fixes

---

## Reporting Bugs

Before submitting a bug report:
1. Check the [existing issues](https://github.com/rdevz-ph/FPS-Meter-Android/issues) to see if the problem was already reported.
2. Verify you are using the latest release of FPS Meter.

### Recommended Step: Device Diagnostics (Version 2.7+)

To help investigate the issue, please include your device diagnostics:
1. Open the app and grant it Shizuku permission.
2. Go to **Settings > Device Diagnostics**.
3. Tap **Report on GitHub** or **Copy All**, then paste the results into your GitHub issue.

> [!NOTE]
> FPS Meter is designed to work without root access. There are no plans to add root support, so you will still need Shizuku to use the app's features that require it, even if your device is rooted with Magisk or KernelSU.

When creating a new bug report, please include:
- A clear and descriptive title.
- Steps to reproduce the problem.
- Expected behavior versus what actually happened.
- Device information: device model, Android version, and ROM if applicable.
- Device diagnostics report from Settings > Device Diagnostics.
- Selected FPS provider (Choreographer or SurfaceFlinger).
- Logcat output or crash report text if available (FPS Meter includes a built-in crash reporter with a Copy Report button).

Thank you for taking the time to report issues and help improve FPS Meter!

---

## Suggesting Enhancements

If you have an idea for a new feature or improvement:
1. Open an issue on GitHub with the title describing your proposal.
2. Explain the use case: why would this feature be useful for users?
3. Provide details on how the feature should look or work.

---

## Contributing Translations

Translating FPS Meter into different languages is one of the easiest and most valuable ways to help the project. Non-technical users can also contribute translations easily.

We have a dedicated guide and template for translators:
- See the [Translation Guide and Template](TRANSLATION_TEMPLATE.md) for step-by-step instructions.
- If you prefer submitting your translation through an issue without opening a pull request, you can use our [Translation Issue Template](.github/ISSUE_TEMPLATE/translation.md).

Quick summary of translating:
1. The English source strings are located at `app/src/main/res/values/strings.xml`.
2. Translations belong in `app/src/main/res/values-<language_code>/strings.xml` (for example, `values-es` for Spanish or `values-ja` for Japanese).
3. Keep all string names and format variables (`%s`, `%d`) unchanged.
4. Keep technical and brand names (`FPS Meter`, `Shizuku`, `SurfaceFlinger`, `Vulkan`, `OpenGL ES`) in their original form.

---

## Development Setup

To build and work on the project locally:

### Prerequisites

- Android Studio (recent version) or Android SDK command line tools
- Java Development Kit (JDK 17 or JDK 21)
- Android SDK Platform 36 and Build Tools

### Building the Project

1. Clone the repository:
   ```bash
   git clone https://github.com/rdevz-ph/FPS-Meter-Android.git
   cd FPS-Meter-Android
   ```
2. Build the debug APK:
   ```bash
   ./gradlew assembleDebug
   ```
3. Run Kotlin compilation check:
   ```bash
   ./gradlew compileDebugKotlin
   ```
4. Run tests:
   ```bash
   ./gradlew test
   ```

---

## Code Guidelines

When contributing code:
- **Small and focused changes:** Prefer the smallest change that solves the issue. Avoid large, unrelated refactoring.
- **Follow existing patterns:** Follow the project's coding conventions, file organization, and architectural patterns.
- **UI Design:** Prefer clean, simple, and modern Material 3 styling. Avoid flashy effects, excessive gradients, or low-contrast color combinations.
- **Icons:** Use proper vector icons from the project's icon dependencies. Never use emojis as UI icons.
- **Preserve existing behavior:** Make sure existing settings, overlay toggles, and measurement providers continue working properly.

---

## Pull Request Process

1. Fork the repository and create a new branch from `main`:
   ```bash
   git checkout -b feature/your-feature-name
   ```
2. Make your changes and test them thoroughly on an emulator or physical device.
3. Review your changes before committing:
   ```bash
   git diff
   ```
4. Commit your changes with a clear and direct message explaining what was changed.
5. Push to your fork and open a Pull Request against the `main` branch.
6. In your pull request description, explain:
   - What problem your change solves.
   - What was changed and why.
   - How you tested your change.
   - Links to any related issues.
