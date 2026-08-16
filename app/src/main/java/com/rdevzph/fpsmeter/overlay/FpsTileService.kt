package com.rdevzph.fpsmeter.overlay

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.rdevzph.fpsmeter.MainActivity
import com.rdevzph.fpsmeter.R

/**
 * Quick Settings Panel Tile service that allows users to toggle the FPS overlay
 * directly from the Android status bar / Quick Settings notification shade.
 */
class FpsTileService : TileService() {

    companion object {
        fun updateTile(context: Context) {
            try {
                requestListeningState(context, ComponentName(context, FpsTileService::class.java))
            } catch (e: Exception) {
                // Ignore if tile not placed
            }
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Overlay permission required. Please open FPS Meter.", Toast.LENGTH_LONG).show()
            val appIntent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivityAndCollapse(appIntent)
            return
        }

        if (FpsOverlayService.isRunning) {
            // Stop the overlay service
            val stopIntent = Intent(this, FpsOverlayService::class.java).apply {
                action = "STOP"
            }
            startService(stopIntent)
        } else {
            // Start the overlay service
            val startIntent = Intent(this, FpsOverlayService::class.java)
            ContextCompat.startForegroundService(this, startIntent)
        }

        // Post update with slight delay to ensure service state settles
        qsTile?.let { tile ->
            tile.state = if (FpsOverlayService.isRunning) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
            tile.updateTile()
        }
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val isRunning = FpsOverlayService.isRunning

        tile.state = if (isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.quick_settings_tile_label)
        tile.icon = Icon.createWithResource(this, R.mipmap.ic_launcher_foreground)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (isRunning) "Active" else "Tap to start"
        }

        tile.updateTile()
    }
}
