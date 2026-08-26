# FPS Meter Android

![API](https://img.shields.io/badge/API-26%2B-10b981)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?logo=kotlin)
![License](https://img.shields.io/badge/license-MIT-4f46e5)
![Version](https://img.shields.io/badge/version-1.3-f59e0b)

A high-performance, lightweight FPS monitoring tool for Android. This application provides a real-time frame rate overlay inspired by the Samsung Perf Z aesthetic, offering a professional monitoring experience for mobile gaming and performance testing.

## How It Works

> [!NOTE]
> Here is a high-level overview of how the application operates:
> - **FPS Calculation**: Uses Android's `Choreographer` API to receive frame callbacks, measuring elapsed time to calculate real-time frames per second (FPS). Frame time (MS) is derived directly from this rate.
> - **Thermal Monitoring**: Registers a dynamic `BroadcastReceiver` for `Intent.ACTION_BATTERY_CHANGED` to read and display the current battery/device temperature in real time.
> - **Overlay View**: Starts a foreground service (`FpsOverlayService`) that utilizes the Android WindowManager to draw a floating pill-shaped overlay using the `SYSTEM_ALERT_WINDOW` permission.
> - **Quick Settings Panel Tile**: Exposes an Android `TileService` (`FpsTileService`) that allows 1-tap toggling of the FPS overlay directly from the notification pull-down shade without opening the main app.
> - **Floating Assistive Bubble**: Provides an optional draggable floating on-screen bubble (`FloatingToggleButton`) that enables 1-tap show/hide of the overlay counter from inside any active game.
> - **Auto On/Off Game Detection**: An optional `AccessibilityService` (`FpsAccessibilityService`) monitors foreground window state changes to automatically activate the overlay when designated target games/apps launch and stop when exited.
> - **Interaction & Dragging**: Tracks touch gestures using an `OnTouchListener` to support real-time dragging. The updated layout coordinates are saved in `SharedPreferences` on gesture completion to persist the custom location.
> - **Shizuku Integration**: Runs a shell command (`appops set <package> SYSTEM_ALERT_WINDOW allow`) via a Shizuku process to auto-grant the overlay permission, removing the need for manual settings navigation.

## Download latest version

Navigate to the [Releases](https://github.com/rdevz-ph/FPS-Meter-Android/releases) page to download the latest APK. For troubleshooting installation issues (Google Play Protect) or Android 13+ permission restrictions, check out the [Troubleshooting and Setup Guide](./tutorials/README.md).

## Screenshots

| Light Mode | Dark Mode |
|:-----------:|:-----------:|
| ![Light Mode](./screenshots/Screenshot_1.jpg) | ![Dark Mode](./screenshots/Screenshot_2.jpg) |

## Features

### Quick Access & Status Bar Tile
- **Quick Settings Tile**: Pull down the Android status bar / Quick Settings panel in any game and tap **FPS Meter** to start or stop the overlay instantly.
- **Floating Assistive Bubble**: A minimal draggable bubble on screen for instant 1-tap show/hide of the FPS counter without opening the app.
- **Notification Controls**: Dedicated **Hide / Show** and **Stop** action buttons directly in the persistent notification.

### Auto On/Off for Games & Apps
- **Automated Detection**: Automatically starts the FPS counter when selected games or benchmark apps launch, and automatically stops it when closed.
- **In-App App Picker**: Easily search and checkmark target apps with filter chips (**All**, **User Apps**, and **Selected**).

### Samsung-Style Aesthetic
The overlay utilizes a pill-shaped background with high-contrast, color-coded metrics for optimal visibility.
- **Labels (FPS, MS, TEMP)**: Displayed in Cyan (#00E5FF).
- **Values**: Rendered in White for clarity.
- **Dynamic FPS Color**: The FPS value automatically changes color (Green, Yellow, Orange, Red) based on real-time performance thresholds.

### Improved Horizontal Layout
The metrics are presented in a compact horizontal display using pipe separators (|). This design minimizes screen obstruction while providing essential performance data at a glance.

### Position Presets
Quick-snap presets allow for instant positioning across six key screen locations:
- Top Left, Top Center, Top Right
- Bottom Left, Bottom Center, Bottom Right
- Manual dragging is fully supported for custom placement.

### Real-time Updates
Configuration changes apply immediately to the active overlay. Adjustments to text size, opacity, color mode, and visibility do not require a service restart.

### Shizuku Integration (Optional)
The setup process includes an optional Shizuku panel to auto-grant the "Display over other apps" permission, bypassing the need for manual navigation through system settings.

## Requirements
- Android 8.0+ (API 26)
- Overlay Permission (SYSTEM_ALERT_WINDOW)

## Developer
Built by **rdevz-ph**
[GitHub Profile](https://github.com/rdevz-ph)

## License

This project is licensed under the [MIT License](LICENSE).