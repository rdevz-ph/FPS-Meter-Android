package com.rdevzph.fpsmeter.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.rdevzph.fpsmeter.overlay.FpsOverlayService
import com.rdevzph.fpsmeter.viewmodel.OverlaySettings

/**
 * Handles device boot broadcast to automatically launch the FPS overlay
 * if enabled in settings and overlay permission is granted.
 */
class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            Log.d(TAG, "Boot event received: $action")
            val settings = OverlaySettings.load(context)
            if (!settings.startOnBoot) {
                Log.d(TAG, "Start on boot is disabled in settings")
                return
            }

            if (!Settings.canDrawOverlays(context)) {
                Log.w(TAG, "Cannot start overlay on boot: overlay permission not granted")
                return
            }

            try {
                val serviceIntent = Intent(context, FpsOverlayService::class.java).apply {
                    putExtra(FpsOverlayService.EXTRA_COLOR, settings.color)
                    putExtra(FpsOverlayService.EXTRA_SIZE, settings.textSizeSp)
                    putExtra(FpsOverlayService.EXTRA_ALPHA, settings.alpha)
                    putExtra(FpsOverlayService.EXTRA_BACKGROUND_ALPHA, settings.backgroundAlpha)
                    putExtra(FpsOverlayService.EXTRA_POSITION_X, settings.posX)
                    putExtra(FpsOverlayService.EXTRA_POSITION_Y, settings.posY)
                    putExtra(FpsOverlayService.EXTRA_SHOW_MS, settings.showMs)
                    putExtra(FpsOverlayService.EXTRA_SHOW_TEMP, settings.showTemp)
                    putExtra(FpsOverlayService.EXTRA_SHOW_SOC_TEMP, settings.showSocTemp)
                    putExtra(FpsOverlayService.EXTRA_SHOW_CPU_TEMP, settings.showCpuTemp)
                    putExtra(FpsOverlayService.EXTRA_SHOW_GPU_TEMP, settings.showGpuTemp)
                    putExtra(FpsOverlayService.EXTRA_GRAVITY, settings.gravity)
                    putExtra(FpsOverlayService.EXTRA_FLOATING_TOGGLE, settings.floatingToggleEnabled)
                    putExtra(FpsOverlayService.EXTRA_FPS_PROVIDER, settings.fpsProvider.name)
                    putExtra(FpsOverlayService.EXTRA_SHOW_API, settings.showGraphicsApi)
                    putExtra(FpsOverlayService.EXTRA_SHOW_BATTERY_LEVEL, settings.showBatteryLevel)
                    putExtra(FpsOverlayService.EXTRA_SHOW_BATTERY_POWER, settings.showBatteryPower)
                    putExtra(FpsOverlayService.EXTRA_SHOW_RAM, settings.showRam)
                    putExtra(FpsOverlayService.EXTRA_SINGLE_LINE_OVERLAY, settings.singleLineOverlay)
                    putExtra(FpsOverlayService.EXTRA_OVERLAY_STYLE, settings.overlayStyle.name)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                Log.d(TAG, "FpsOverlayService started on boot successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start FpsOverlayService on boot", e)
            }
        }
    }
}

