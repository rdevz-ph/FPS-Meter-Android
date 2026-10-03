# Translation Guide and Template

Thank you for your interest in translating FPS Meter to your language. This guide explains how to translate the app, what rules to follow, and how to submit your translation.

## Overview

FPS Meter uses standard Android string resources. All text visible to users is defined in an XML file:

- Source strings (English): `app/src/main/res/values/strings.xml`
- Existing translations (Simplified Chinese): `app/src/main/res/values-zh/strings.xml`

Translations are organized in folders named `values-<language_code>`, where `<language_code>` is the standard ISO 639-1 two-letter code (for example, `values-es` for Spanish, `values-ja` for Japanese, `values-de` for German, `values-fr` for French).

---

## Step by Step Translation Guide

### Step 1: Create the Translation Folder and File

1. Navigate to `app/src/main/res/`.
2. Create a new folder named `values-<language_code>`.
   - Examples:
     - Spanish: `app/src/main/res/values-es/`
     - Japanese: `app/src/main/res/values-ja/`
     - Russian: `app/src/main/res/values-ru/`
     - German: `app/src/main/res/values-de/`
3. Copy `app/src/main/res/values/strings.xml` into your new folder.

### Step 2: Translate the Text

Open the newly created `strings.xml` and translate the text inside each `<string>` element.

Example:

```xml
<!-- Original (English) in values/strings.xml -->
<string name="tab_meter">Meter</string>
<string name="start_overlay">START OVERLAY</string>

<!-- Translated (Spanish) in values-es/strings.xml -->
<string name="tab_meter">Medidor</string>
<string name="start_overlay">INICIAR SUPERPOSICION</string>
```

#### Important Rules for Translating

1. **Do not change string names:** Keep the `name="..."` attribute exactly as it is in the English file.
2. **Preserve placeholders and variables:**
   - Placeholders such as `%s`, `%d`, `%1$s`, `%2$d` are replaced by numbers, names, or values at runtime.
   - Do not remove or translate them.
   - Example:
     ```xml
     <!-- English -->
     <string name="bench_device_prefix">Device: %s</string>
     <!-- Spanish -->
     <string name="bench_device_prefix">Dispositivo: %s</string>
     ```
3. **Escape special characters:**
   - Apostrophes: Use `\'` instead of `'` (for example: `don\'t`).
   - Quotes: Use `\"` instead of `"`.
   - Ampersands: Use `&amp;` instead of `&`.
   - Newlines: Keep `\n` where line breaks are needed.
4. **Do not translate brand and technical terms:**
   - Keep the following terms in their original form:
     - App name: `FPS Meter`
     - Providers: `SurfaceFlinger`, `Choreographer`
     - Permissions and APIs: `Shizuku`, `SYSTEM_ALERT_WINDOW`, `Vulkan`, `OpenGL ES`
     - Metric labels: `FPS`, `BAT`, `CPU`, `GPU`, `SOC`

### Step 3: Add the Language to the In-App Selector (Optional)

If you are comfortable editing Kotlin code, you can also add your language to the in-app language switcher. If not, open an issue or pull request with your `strings.xml` and the maintainers will connect it for you.

1. **Add language name to `app/src/main/res/values/strings.xml`:**
   ```xml
   <string name="language_spanish">Español</string>
   ```
2. **Add language constant to `app/src/main/java/com/rdevzph/fpsmeter/util/LocaleHelper.kt`:**
   ```kotlin
   const val LANG_SPANISH = "es"
   ```
   And add it to `wrap()`:
   ```kotlin
   val locale = when (lang) {
       LANG_CHINESE -> Locale.SIMPLIFIED_CHINESE
       LANG_ENGLISH -> Locale.ENGLISH
       LANG_SPANISH -> Locale("es")
       else -> return base
   }
   ```
3. **Add option to `app/src/main/java/com/rdevzph/fpsmeter/ui/screen/SettingsScreen.kt`:**
   ```kotlin
   val languageOptions = listOf(
       LocaleHelper.LANG_SYSTEM to R.string.language_system,
       LocaleHelper.LANG_ENGLISH to R.string.language_english,
       LocaleHelper.LANG_CHINESE to R.string.language_chinese,
       LocaleHelper.LANG_SPANISH to R.string.language_spanish
   )
   ```

### Step 4: Test Your Translation

Build the app to verify there are no XML syntax or escaping errors:

```bash
./gradlew compileDebugKotlin
```

If you have a test device connected, you can install and test:

```bash
./gradlew installDebug
```

---

## Pull Request Submission Template

When opening a Pull Request for a new translation or translation update, you can use the template below for your PR description:

```markdown
### Translation Contribution

- **Language:** (for example: Spanish / Español)
- **Language Code:** (for example: es)
- **New or Update:** (New translation / Update to existing)

### Changes Included
- [ ] Created or updated `app/src/main/res/values-<lang>/strings.xml`
- [ ] Preserved all `%s`, `%d`, and positional placeholders
- [ ] Escaped special characters (\', \", &amp;)
- [ ] Verified that XML builds without errors via `./gradlew compileDebugKotlin`
- [ ] (Optional) Registered language in `LocaleHelper.kt` and `SettingsScreen.kt`

### Notes or Questions for Maintainer
(Add any questions or context about specific term translations here)
```
