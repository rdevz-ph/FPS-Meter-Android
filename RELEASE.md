# FPS Meter Android v1.5

### What's New in v1.5
- **Updated Shizuku Documentation**: Clarified Shizuku integration in the app settings to highlight privileged real game FPS monitoring alongside permission management.
- **Official F-Droid Packaging**: Added Fastlane metadata structure and streamlined Gradle build configuration for official F-Droid repository inclusion ([Inclusion MR !47608](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/47608)).

For the complete release history, see [CHANGELOG.md](https://github.com/rdevz-ph/FPS-Meter-Android/blob/main/CHANGELOG.md).

### Features
- Choice between Choreographer and SurfaceFlinger FPS measurement providers
- Automatic Graphics API detection (Vulkan and OpenGL ES)
- Samsung-style pill-shaped overlay with cyan labels and white values
- Real-time FPS, frame time (MS), and battery temperature (TEMP) monitoring
- Dynamic FPS color coding (green, yellow, orange, red) based on performance thresholds
- Compact horizontal layout with pipe separators
- Six preset positions (top/bottom left/center/right) with manual drag support
- Light and dark mode themes
- Adjustable text size and overlay opacity
- Optional Shizuku integration for one-tap permission grant and SurfaceFlinger game FPS
- Quick Settings status bar tile and Floating Assistive Bubble
- Automated game detection via Accessibility Service

### Requirements
- Android 8.0+ (API 26)
- Overlay Permission (SYSTEM_ALERT_WINDOW)
- Shizuku (optional, required for SurfaceFlinger real game FPS mode)

### Installation & Setup Guides
Download the APK from the assets below and enable the "Display over other apps" permission when prompted.

If you encounter Play Protect installation blocks or Android 13+ restricted settings warnings when enabling features like Accessibility Service or Shizuku, follow the [Troubleshooting and Setup Guide](./tutorials/README.md).