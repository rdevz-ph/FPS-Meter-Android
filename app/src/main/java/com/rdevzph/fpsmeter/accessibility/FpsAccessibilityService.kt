package com.rdevzph.fpsmeter.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.rdevzph.fpsmeter.overlay.FpsOverlayService
import com.rdevzph.fpsmeter.viewmodel.OverlaySettings

/**
 * Accessibility Service that monitors foreground app/window transitions
 * to automatically launch or stop the FPS overlay for designated games and apps.
 */
class FpsAccessibilityService : AccessibilityService() {

    companion object {
        var isServiceRunning = false
        var currentForegroundPackage: String? = null
    }

    private val ignoredPackages = setOf(
        "com.android.systemui",
        "android",
        "com.google.android.inputmethod.latin",
        "com.touchtype.swiftkey",
        "com.android.settings"
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceRunning = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val eventType = event?.eventType ?: return
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return

        val rawPkg = event.packageName?.toString()
            ?: try { rootInActiveWindow?.packageName?.toString() } catch (e: Exception) { null }
            ?: return
        if (rawPkg in ignoredPackages || rawPkg == packageName) return
        currentForegroundPackage = rawPkg

        val settings = OverlaySettings.load(this)
        val shouldAutoStart = settings.autoStartEnabled && (settings.autoStartPackages.contains(rawPkg) || settings.recordingPackages.contains(rawPkg))
        val isTargetApp = settings.autoStartPackages.contains(rawPkg) || settings.recordingPackages.contains(rawPkg)

        if (isTargetApp) {
            // Target or recorded game opened: start FPS overlay if auto-start is enabled and not already running
            if (shouldAutoStart && !FpsOverlayService.isRunning && Settings.canDrawOverlays(this)) {
                FpsOverlayService.isAutoStarted = true
                val startIntent = Intent(this, FpsOverlayService::class.java)
                ContextCompat.startForegroundService(this, startIntent)
            }
        } else {
            // Non-target app in foreground: if overlay was auto-started, stop it
            if (FpsOverlayService.isRunning && FpsOverlayService.isAutoStarted) {
                FpsOverlayService.isAutoStarted = false
                val stopIntent = Intent(this, FpsOverlayService::class.java).apply {
                    action = "STOP"
                }
                startService(stopIntent)
            }
        }
    }

    override fun onInterrupt() {
        // Required by AccessibilityService
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        currentForegroundPackage = null
    }
}
