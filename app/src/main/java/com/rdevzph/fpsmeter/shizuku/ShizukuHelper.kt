package com.rdevzph.fpsmeter.shizuku

import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import rikka.shizuku.Shizuku

/**
 * Helper class to manage Shizuku service binding and permission requests.
 */
class ShizukuHelper {
    companion object {
        private const val TAG = "ShizukuHelper"
        private const val REQUEST_CODE = 1001
        private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    }

    private val _shizukuAvailable = MutableStateFlow(false)
    val shizukuAvailable: StateFlow<Boolean> = _shizukuAvailable

    private val _shizukuPermissionGranted = MutableStateFlow(false)
    val shizukuPermissionGranted: StateFlow<Boolean> = _shizukuPermissionGranted

    private val onBinderReceivedListener = Shizuku.OnBinderReceivedListener {
        Log.d(TAG, "Shizuku binder received")
        checkStatus()
    }

    private val onBinderDeadListener = Shizuku.OnBinderDeadListener {
        Log.d(TAG, "Shizuku binder dead")
        _shizukuAvailable.value = false
        _shizukuPermissionGranted.value = false
    }

    private val onPermissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == REQUEST_CODE) {
            val granted = grantResult == PackageManager.PERMISSION_GRANTED
            Log.d(TAG, "Shizuku permission result: $granted")
            _shizukuPermissionGranted.value = granted
        }
    }

    init {
        try {
            Shizuku.addBinderReceivedListener(onBinderReceivedListener)
            Shizuku.addBinderDeadListener(onBinderDeadListener)
            Shizuku.addRequestPermissionResultListener(onPermissionResultListener)
            
            // Initial check
            checkStatus()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Shizuku listeners", e)
        }
    }

    /**
     * Updates the availability and permission state.
     */
    fun updateAvailability(pm: PackageManager) {
        checkStatus()
        
        // Also check if the package is installed as a fallback
        if (!_shizukuAvailable.value) {
            val isInstalled = isShizukuInstalled(pm)
            Log.d(TAG, "Shizuku package installed: $isInstalled (but binder not running)")
        }
    }

    private fun checkStatus() {
        val isRunning = Shizuku.pingBinder()
        _shizukuAvailable.value = isRunning
        
        if (isRunning) {
            val granted = try {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } catch (e: Exception) {
                false
            }
            _shizukuPermissionGranted.value = granted
            Log.d(TAG, "Shizuku running, permission: $granted")
        } else {
            _shizukuPermissionGranted.value = false
            Log.d(TAG, "Shizuku not running")
        }
    }

    private fun isShizukuInstalled(pm: PackageManager): Boolean {
        return try {
            pm.getPackageInfo(SHIZUKU_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Requests Shizuku permission.
     */
    fun requestPermission() {
        try {
            if (Shizuku.pingBinder()) {
                Log.d(TAG, "Requesting Shizuku permission...")
                Shizuku.requestPermission(REQUEST_CODE)
            } else {
                Log.e(TAG, "Binder not available to request permission")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting Shizuku permission", e)
        }
    }

    fun onDestroy() {
        Shizuku.removeBinderReceivedListener(onBinderReceivedListener)
        Shizuku.removeBinderDeadListener(onBinderDeadListener)
        Shizuku.removeRequestPermissionResultListener(onPermissionResultListener)
    }
}
