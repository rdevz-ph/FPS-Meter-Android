# FPS Meter Android v1.3

### What's New in v1.3
- **Brand New 2-Color App Icon**: Minimalist, high-contrast 2-color precision tachometer icon in Obsidian Slate (`#0F141C`) and Electric Mint (`#00E676`) with balanced mathematical centering across all launcher densities.
- **Quick Settings Panel Tile (`TileService`)**: Pull down the Android status bar / notification shade from inside **any game or app** and tap the **FPS Meter** tile to toggle the overlay on or off instantly without switching apps.
- **Floating Assistive Quick-Toggle Bubble**: A draggable on-screen floating bubble that stays above apps and allows 1-tap show/hide of the FPS counter with smooth edge-snapping.
- **Auto On/Off per App (`AccessibilityService`)**: Automatically turns on the FPS overlay when target games and apps launch, and stops it automatically when closed.
- **Target App & Game Selector**: Searchable in-app dialog with filter chips (All, User Apps, Selected) to easily pick which games trigger auto-start.
- **Immediate `onResume` Accessibility Detection**: The app instantly detects when the Accessibility permission is granted upon returning from Android Settings without requiring an app restart.
- **Enhanced Notification Shade Controls**: Added **Hide / Show** and **Stop** action buttons directly inside the active notification.
- **Full Package Visibility Support**: Added `QUERY_ALL_PACKAGES` permission so user-installed games and apps are prioritized and listed.

### Features
- Samsung-style pill-shaped overlay with cyan labels and white values
- Real-time FPS, frame time (MS), and battery temperature (TEMP) monitoring
- Dynamic FPS color coding (green, yellow, orange, red) based on performance thresholds
- Compact horizontal layout with pipe separators
- Six preset positions (top/bottom left/center/right) with manual drag support
- Light and dark mode themes
- Adjustable text size and overlay opacity
- Optional Shizuku integration for one-tap permission grant
- Quick Settings tile & Floating Assistive Bubble
- Automated game detection via Accessibility Service

### Requirements
- Android 8.0+ (API 26)

### Installation & Setup Guides
Download the APK from the assets below and enable "Display over other apps" permission when prompted.

If you encounter Play Protect installation blocks or Android 13+ restricted settings warnings when enabling features like Accessibility Service, follow the [Troubleshooting and Setup Guide](./tutorials/README.md).