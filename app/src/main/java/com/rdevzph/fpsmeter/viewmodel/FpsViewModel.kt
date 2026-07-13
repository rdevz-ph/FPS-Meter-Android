package com.rdevzph.fpsmeter.viewmodel

import android.app.AppOpsManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.view.Gravity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rdevzph.fpsmeter.overlay.FpsOverlayService
import com.rdevzph.fpsmeter.shizuku.ShizukuHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OverlaySettings(
    val color: Int = AUTO_COLOR,
    val textSizeSp: Float = 14f,
    val alpha: Float = 0.9f,
    val posX: Int = 0,
    val posY: Int = 100,
    val showMs: Boolean = false,
    val showTemp: Boolean = false,
    val gravity: Int = Gravity.TOP or Gravity.START
) {
    companion object {
        const val AUTO_COLOR = 0 // Sentinel value for automatic coloring
    }
}

class FpsViewModel(
    private val packageManager: PackageManager
) : ViewModel() {

    private val shizukuHelper = ShizukuHelper()

    val shizukuAvailable: StateFlow<Boolean> = shizukuHelper.shizukuAvailable
    val shizukuPermissionGranted: StateFlow<Boolean> = shizukuHelper.shizukuPermissionGranted

    private val _overlayPermissionGranted = MutableStateFlow(false)
    val overlayPermissionGranted: StateFlow<Boolean> = _overlayPermissionGranted.asStateFlow()

    private val _isOverlayRunning = MutableStateFlow(FpsOverlayService.isRunning)
    val isOverlayRunning: StateFlow<Boolean> = _isOverlayRunning.asStateFlow()

    private val _settings = MutableStateFlow(OverlaySettings())
    val settings: StateFlow<OverlaySettings> = _settings.asStateFlow()

    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    init {
        shizukuHelper.updateAvailability(packageManager)
    }

    fun refreshStatus() {
        _isOverlayRunning.value = FpsOverlayService.isRunning
        shizukuHelper.updateAvailability(packageManager)
    }

    fun checkOverlayPermission(context: Context): Boolean {
        val granted = Settings.canDrawOverlays(context)
        _overlayPermissionGranted.value = granted
        return granted
    }

    /**
     * Grant SYSTEM_ALERT_WINDOW via Shizuku (appops set <pkg> SYSTEM_ALERT_WINDOW allow)
     */
    fun grantOverlayViaShizuku(context: Context) {
        if (!shizukuHelper.shizukuAvailable.value || !shizukuHelper.shizukuPermissionGranted.value) {
            _statusMessage.value = "Shizuku not ready"
            return
        }
        viewModelScope.launch {
            try {
                val pkg = context.packageName
                val process = rikka.shizuku.Shizuku.newProcess(
                    arrayOf("appops", "set", pkg, "SYSTEM_ALERT_WINDOW", "allow"),
                    null, null
                )
                val exit = process.waitFor()
                if (exit == 0) {
                    _overlayPermissionGranted.value = Settings.canDrawOverlays(context)
                    _statusMessage.value = if (_overlayPermissionGranted.value)
                        "Overlay permission granted!" else "Granted via shell, recheck failed"
                } else {
                    _statusMessage.value = "Shell command failed (exit $exit)"
                }
            } catch (e: Exception) {
                _statusMessage.value = "Shizuku error: ${e.message}"
            }
        }
    }

    fun requestShizukuPermission() = shizukuHelper.requestPermission()
    fun checkShizukuStatus() = shizukuHelper.updateAvailability(packageManager)

    fun updateSettings(new: OverlaySettings) {
        _settings.value = new
    }

    fun setOverlayRunning(running: Boolean) {
        _isOverlayRunning.value = running
    }

    fun clearStatus() { _statusMessage.value = "" }

    override fun onCleared() {
        super.onCleared()
        shizukuHelper.onDestroy()
    }

    class Factory(private val pm: PackageManager) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            FpsViewModel(pm) as T
    }
}
