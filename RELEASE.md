# FPS Meter Android v1.4

### What's New in v1.4
- **FPS Provider Setting**: Added the ability to choose how FPS is measured:
  - **Choreographer (Default)**: Measures display vsync timing with low overhead, ideal for standard UI monitoring.
  - **SurfaceFlinger**: Measures true game rendering frame rates using Shizuku privileged shell access to inspect active compositor layers.
- **SurfaceFlinger Game FPS Measurement**: Samples real frame presentation buffers directly from active game `SurfaceView` buffer queues, delivering accurate in-game FPS counters for demanding titles.
- **Automatic Graphics API Detection**: Dynamically inspects the foreground game's rendering pipeline (via system GPU telemetry and dumpsys metrics) to detect whether the game is running on **Vulkan** or **OpenGL ES** (such as *Genshin Impact* and *Wuthering Waves*), applying the appropriate frame measurement strategy for each API.
- **Graphics API Overlay Badge**: Optional toggle to display an on-screen [VK] or [GL] badge directly on the overlay pill next to the FPS value when SurfaceFlinger mode is active.
- **Graceful Fallback Mechanism**: If Shizuku is unavailable, permissions are revoked, or the game is minimized, the measurement engine automatically falls back to Choreographer to ensure uninterrupted monitoring.
- **Dynamic Shizuku Requirement Handling**: The SurfaceFlinger option is automatically enabled when Shizuku is installed and running with permissions granted; otherwise, it is disabled with a clear requirement indicator.
- **In-Game Testing & Verification**: Added and documented in-game verification showcasing real-time game performance tracking with the overlay.

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