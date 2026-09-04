# Changelog

All notable changes to the FPS Meter project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [v1.5] - 2026-09-02

### Added
- **Fastlane Metadata Structure**: Added structured fastlane metadata and full historical changelogs (1 to 6) in `fastlane/metadata/android/en-US/` for F-Droid repository indexing.

### Changed
- **Updated Shizuku Documentation**: Clarified Shizuku integration in app settings to highlight privileged real game FPS monitoring via SurfaceFlinger alongside permission management.
- **Gradle Build Compliance**: Removed the `foojay-resolver` plugin from `settings.gradle.kts` to comply with F-Droid offline build and reproducibility standards.

---

## [v1.4] - 2026-09-02

### Added
- **SurfaceFlinger Game FPS Provider**: Added privileged frame monitoring using Shizuku to inspect compositor layers and sample real frame presentation buffers directly from active game `SurfaceView` buffer queues.
- **FPS Provider Setting**: Added the ability to choose how FPS is measured:
  - **Choreographer (Default)**: Measures display vsync timing with minimal overhead for standard UI monitoring.
  - **SurfaceFlinger**: Measures true game rendering frame rates using Shizuku privileged shell access.
- **Automatic Graphics API Detection**: Dynamically inspects the foreground game rendering pipeline via system GPU telemetry and dumpsys metrics to detect whether the game runs on Vulkan or OpenGL ES.
- **Graphics API Overlay Badge**: Optional on-screen `[VK]` or `[GL]` badge displayed on the overlay pill next to the FPS value when SurfaceFlinger mode is active.
- **Self-Healing Layer Recovery**: Automated active layer invalidation and presentation timestamp resynchronization to prevent 0 FPS stalls during game switches and splash screen transitions.
- **Graceful Fallback Mechanism**: Automatically falls back to Choreographer measurement if Shizuku is unavailable, permissions are revoked, or the target game is minimized.
- **Dynamic Shizuku Requirement Handling**: SurfaceFlinger option dynamically enables when Shizuku is installed and running with permissions granted; otherwise, displays a clear requirement indicator.
- **Troubleshooting Documentation**: Added a dedicated setup guide for Play Protect installation prompts and Android 13+ restricted settings.

---

## [v1.3] - 2026-08-23

### Added
- **2-Color Precision Tachometer Icon**: Minimalist, high-contrast 2-color app icon in Obsidian Slate (`#0F141C`) and Electric Mint (`#00E676`) with mathematical centering across all launcher densities.
- **Quick Settings Panel Tile (`TileService`)**: Status bar tile allowing users to toggle the FPS overlay on or off instantly from inside any game or app without switching tasks.
- **Floating Assistive Quick-Toggle Bubble**: Draggable on-screen floating bubble that stays above apps for 1-tap show/hide of the FPS counter with smooth edge-snapping.
- **Auto On/Off per App (`AccessibilityService`)**: Automatically activates the overlay when designated target games or apps launch and stops it when closed.
- **Target Game and App Selector**: Searchable in-app dialog with filter chips (All, User Apps, Selected) to configure auto-start targets.
- **Immediate `onResume` Accessibility Detection**: Instantly detects accessibility permission grant upon returning from Android Settings without requiring an app restart.
- **Enhanced Notification Controls**: Added interactive **Hide / Show** and **Stop** action buttons directly inside the persistent notification shade.
- **Full Package Visibility Support**: Added `QUERY_ALL_PACKAGES` permission to discover and list all user-installed games in the app selector.

### Changed
- Refined dynamic color coding thresholds to include green, yellow, orange, and red states.

---

## [v1.2] - 2026-07-15

### Added
- **Persistent Storage via SharedPreferences**: Saves and restores overlay settings (text color, size, opacity, metric visibility choices, preset positioning, and manual drag coordinates) across app and service restarts.
- **Reset Settings Option**: Added a Reset Settings button under overlay controls accompanied by a confirmation dialog to restore defaults.
- **Interactive Updates Card Footer**: Added footer in the main settings screen displaying the installed application version and update status.

### Changed
- Refined visual alignments and centered layouts across settings cards.

---

## [v1.1] - 2026-07-13

### Added
- **Automatic Overlay Permission Detection**: Automatically detects when the `SYSTEM_ALERT_WINDOW` permission has been granted upon returning to the app without requiring an app restart.

### Changed
- Cleaned codebase and removed unused extractor files.
- Automated CI release workflow with custom tag and release note support.

---

## [v1.0] - 2026-04-18

### Added
- **Initial Release**: Lightweight, high-performance real-time FPS monitoring overlay for Android inspired by Samsung Perf Z.
- **Choreographer FPS Engine**: Display vsync frame timing measurement with low CPU and battery overhead.
- **Real-Time Telemetry**: Live FPS counter, frame time in milliseconds (MS), and battery temperature in Celsius (TEMP).
- **Samsung Perf Z Style Overlay**: Compact horizontal pill-shaped layout with cyan labels, white values, and pipe separators.
- **Dynamic Performance Color Coding**: Color-coded FPS display based on performance thresholds (green, yellow, red).
- **Custom Positioning**: Six preset screen positions (top/bottom left/center/right) with freehand drag-and-drop repositioning.
- **Customizable Appearance**: Light and dark mode themes, adjustable overlay scale/text size, and background opacity controls.
- **Optional Shizuku Integration**: One-tap automated permission grant for overlay display without manual adb commands.

---

[v1.5]: https://github.com/rdevz-ph/FPS-Meter-Android/releases/tag/v1.5
[v1.4]: https://github.com/rdevz-ph/FPS-Meter-Android/releases/tag/v1.4
[v1.3]: https://github.com/rdevz-ph/FPS-Meter-Android/releases/tag/v1.3
[v1.2]: https://github.com/rdevz-ph/FPS-Meter-Android/releases/tag/v1.2
[v1.1]: https://github.com/rdevz-ph/FPS-Meter-Android/releases/tag/v1.1
[v1.0]: https://github.com/rdevz-ph/FPS-Meter-Android/releases/tag/v1.0
