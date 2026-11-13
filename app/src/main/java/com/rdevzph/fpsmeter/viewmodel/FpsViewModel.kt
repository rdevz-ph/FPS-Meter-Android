package com.rdevzph.fpsmeter.viewmodel

import com.rdevzph.fpsmeter.R

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.view.Gravity
import android.view.accessibility.AccessibilityManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rdevzph.fpsmeter.accessibility.FpsAccessibilityService
import com.rdevzph.fpsmeter.overlay.FpsOverlayService
import com.rdevzph.fpsmeter.model.FpsProvider
import com.rdevzph.fpsmeter.model.FpsSessionRecord
import com.rdevzph.fpsmeter.model.OverlayStyle
import com.rdevzph.fpsmeter.model.SurfaceFlingerMethod
import com.rdevzph.fpsmeter.recording.FpsRecordingManager
import com.rdevzph.fpsmeter.shizuku.ShizukuHelper
import com.rdevzph.fpsmeter.ui.theme.AppThemeSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OverlaySettings(
    val color: Int = AUTO_COLOR,
    val textSizeSp: Float = 14f,
    val alpha: Float = 1.0f,
    val backgroundAlpha: Float = 0.8f,
    val posX: Int = 0,
    val posY: Int = 100,
    val showFps: Boolean = true,
    val showMs: Boolean = false,
    val showTemp: Boolean = false,
    val showSocTemp: Boolean = false,
    val showCpuTemp: Boolean = false,
    val showGpuTemp: Boolean = false,
    val gravity: Int = Gravity.TOP or Gravity.START,
    val floatingToggleEnabled: Boolean = false,
    val autoHideFloatingBubble: Boolean = false,
    val autoStartEnabled: Boolean = false,
    val autoStartPackages: Set<String> = emptySet(),
    val recordingPackages: Set<String> = emptySet(),
    val autoRecordAll: Boolean = false,
    val fpsProvider: FpsProvider = FpsProvider.CHOREOGRAPHER,
    val surfaceFlingerMethod: SurfaceFlingerMethod = SurfaceFlingerMethod.LATENCY,
    val showGraphicsApi: Boolean = true,
    val showBatteryLevel: Boolean = false,
    val showBatteryPower: Boolean = false,
    val showBatteryCurrent: Boolean = true,
    val showRam: Boolean = false,
    val singleLineOverlay: Boolean = false,
    val overlayStyle: OverlayStyle = OverlayStyle.DEFAULT,
    val showSocName: Boolean = true,
    val startOnBoot: Boolean = false
) {
    companion object {
        const val AUTO_COLOR = 0 // Sentinel value for automatic coloring

        private const val PREFS_NAME = "overlay_settings_prefs"
        private const val KEY_COLOR = "color"
        private const val KEY_TEXT_SIZE = "text_size"
        private const val KEY_ALPHA = "alpha"
        private const val KEY_BACKGROUND_ALPHA = "background_alpha"
        private const val KEY_POS_X = "pos_x"
        private const val KEY_POS_Y = "pos_y"
        private const val KEY_SHOW_FPS = "show_fps"
        private const val KEY_SHOW_MS = "show_ms"
        private const val KEY_SHOW_TEMP = "show_temp"
        private const val KEY_SHOW_SOC_TEMP = "show_soc_temp"
        private const val KEY_SHOW_CPU_TEMP = "show_cpu_temp"
        private const val KEY_SHOW_GPU_TEMP = "show_gpu_temp"
        private const val KEY_GRAVITY = "gravity"
        private const val KEY_FLOATING_TOGGLE = "floating_toggle"
        private const val KEY_AUTO_HIDE_FLOATING_BUBBLE = "auto_hide_floating_bubble"
        private const val KEY_AUTO_START = "auto_start"
        private const val KEY_AUTO_PACKAGES = "auto_packages"
        private const val KEY_RECORDING_PACKAGES = "recording_packages"
        private const val KEY_AUTO_RECORD_ALL = "auto_record_all"
        private const val KEY_FPS_PROVIDER = "fps_provider"
        private const val KEY_SURFACE_FLINGER_METHOD = "surface_flinger_method"
        private const val KEY_SHOW_GRAPHICS_API = "show_graphics_api"
        private const val KEY_SHOW_BATTERY_LEVEL = "show_battery_level"
        private const val KEY_SHOW_BATTERY_POWER = "show_battery_power"
        private const val KEY_SHOW_BATTERY_CURRENT = "show_battery_current"
        private const val KEY_SHOW_RAM = "show_ram"
        private const val KEY_SINGLE_LINE_OVERLAY = "single_line_overlay"
        private const val KEY_OVERLAY_STYLE = "overlay_style"
        private const val KEY_SHOW_SOC_NAME = "show_soc_name"
        private const val KEY_START_ON_BOOT = "start_on_boot"

        fun load(context: Context): OverlaySettings {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val defaultSettings = OverlaySettings()
            return OverlaySettings(
                color = prefs.getInt(KEY_COLOR, defaultSettings.color),
                textSizeSp = prefs.getFloat(KEY_TEXT_SIZE, defaultSettings.textSizeSp),
                alpha = prefs.getFloat(KEY_ALPHA, defaultSettings.alpha),
                backgroundAlpha = prefs.getFloat(KEY_BACKGROUND_ALPHA, defaultSettings.backgroundAlpha),
                posX = prefs.getInt(KEY_POS_X, defaultSettings.posX),
                posY = prefs.getInt(KEY_POS_Y, defaultSettings.posY),
                showFps = prefs.getBoolean(KEY_SHOW_FPS, defaultSettings.showFps),
                showMs = prefs.getBoolean(KEY_SHOW_MS, defaultSettings.showMs),
                showTemp = prefs.getBoolean(KEY_SHOW_TEMP, defaultSettings.showTemp),
                showSocTemp = prefs.getBoolean(KEY_SHOW_SOC_TEMP, defaultSettings.showSocTemp),
                showCpuTemp = prefs.getBoolean(KEY_SHOW_CPU_TEMP, defaultSettings.showCpuTemp),
                showGpuTemp = prefs.getBoolean(KEY_SHOW_GPU_TEMP, defaultSettings.showGpuTemp),
                gravity = prefs.getInt(KEY_GRAVITY, defaultSettings.gravity),
                floatingToggleEnabled = prefs.getBoolean(KEY_FLOATING_TOGGLE, defaultSettings.floatingToggleEnabled),
                autoHideFloatingBubble = prefs.getBoolean(KEY_AUTO_HIDE_FLOATING_BUBBLE, defaultSettings.autoHideFloatingBubble),
                autoStartEnabled = prefs.getBoolean(KEY_AUTO_START, defaultSettings.autoStartEnabled),
                autoStartPackages = prefs.getStringSet(KEY_AUTO_PACKAGES, defaultSettings.autoStartPackages) ?: emptySet(),
                recordingPackages = prefs.getStringSet(KEY_RECORDING_PACKAGES, defaultSettings.recordingPackages) ?: emptySet(),
                autoRecordAll = prefs.getBoolean(KEY_AUTO_RECORD_ALL, defaultSettings.autoRecordAll),
                fpsProvider = FpsProvider.fromString(prefs.getString(KEY_FPS_PROVIDER, defaultSettings.fpsProvider.name)),
                surfaceFlingerMethod = SurfaceFlingerMethod.fromString(prefs.getString(KEY_SURFACE_FLINGER_METHOD, defaultSettings.surfaceFlingerMethod.name)),
                showGraphicsApi = prefs.getBoolean(KEY_SHOW_GRAPHICS_API, defaultSettings.showGraphicsApi),
                showBatteryLevel = prefs.getBoolean(KEY_SHOW_BATTERY_LEVEL, defaultSettings.showBatteryLevel),
                showBatteryPower = prefs.getBoolean(KEY_SHOW_BATTERY_POWER, defaultSettings.showBatteryPower),
                showBatteryCurrent = prefs.getBoolean(KEY_SHOW_BATTERY_CURRENT, defaultSettings.showBatteryCurrent),
                showRam = prefs.getBoolean(KEY_SHOW_RAM, defaultSettings.showRam),
                singleLineOverlay = prefs.getBoolean(KEY_SINGLE_LINE_OVERLAY, defaultSettings.singleLineOverlay),
                overlayStyle = OverlayStyle.fromString(prefs.getString(KEY_OVERLAY_STYLE, defaultSettings.overlayStyle.name)),
                showSocName = prefs.getBoolean(KEY_SHOW_SOC_NAME, defaultSettings.showSocName),
                startOnBoot = prefs.getBoolean(KEY_START_ON_BOOT, defaultSettings.startOnBoot)
            )
        }

        fun save(context: Context, settings: OverlaySettings) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
                putInt(KEY_COLOR, settings.color)
                putFloat(KEY_TEXT_SIZE, settings.textSizeSp)
                putFloat(KEY_ALPHA, settings.alpha)
                putFloat(KEY_BACKGROUND_ALPHA, settings.backgroundAlpha)
                putInt(KEY_POS_X, settings.posX)
                putInt(KEY_POS_Y, settings.posY)
                putBoolean(KEY_SHOW_FPS, settings.showFps)
                putBoolean(KEY_SHOW_MS, settings.showMs)
                putBoolean(KEY_SHOW_TEMP, settings.showTemp)
                putBoolean(KEY_SHOW_SOC_TEMP, settings.showSocTemp)
                putBoolean(KEY_SHOW_CPU_TEMP, settings.showCpuTemp)
                putBoolean(KEY_SHOW_GPU_TEMP, settings.showGpuTemp)
                putInt(KEY_GRAVITY, settings.gravity)
                putBoolean(KEY_FLOATING_TOGGLE, settings.floatingToggleEnabled)
                putBoolean(KEY_AUTO_HIDE_FLOATING_BUBBLE, settings.autoHideFloatingBubble)
                putBoolean(KEY_AUTO_START, settings.autoStartEnabled)
                putStringSet(KEY_AUTO_PACKAGES, settings.autoStartPackages)
                putStringSet(KEY_RECORDING_PACKAGES, settings.recordingPackages)
                putBoolean(KEY_AUTO_RECORD_ALL, settings.autoRecordAll)
                putString(KEY_FPS_PROVIDER, settings.fpsProvider.name)
                putString(KEY_SURFACE_FLINGER_METHOD, settings.surfaceFlingerMethod.name)
                putBoolean(KEY_SHOW_GRAPHICS_API, settings.showGraphicsApi)
                putBoolean(KEY_SHOW_BATTERY_LEVEL, settings.showBatteryLevel)
                putBoolean(KEY_SHOW_BATTERY_POWER, settings.showBatteryPower)
                putBoolean(KEY_SHOW_BATTERY_CURRENT, settings.showBatteryCurrent)
                putBoolean(KEY_SHOW_RAM, settings.showRam)
                putBoolean(KEY_SINGLE_LINE_OVERLAY, settings.singleLineOverlay)
                putString(KEY_OVERLAY_STYLE, settings.overlayStyle.name)
                putBoolean(KEY_SHOW_SOC_NAME, settings.showSocName)
                putBoolean(KEY_START_ON_BOOT, settings.startOnBoot)
                apply()
            }
        }
    }
}

class FpsViewModel(
    private val packageManager: PackageManager,
    private val context: Context
) : ViewModel() {

    private val shizukuHelper = ShizukuHelper()

    val shizukuAvailable: StateFlow<Boolean> = shizukuHelper.shizukuAvailable
    val shizukuPermissionGranted: StateFlow<Boolean> = shizukuHelper.shizukuPermissionGranted

    private val _overlayPermissionGranted = MutableStateFlow(false)
    val overlayPermissionGranted: StateFlow<Boolean> = _overlayPermissionGranted.asStateFlow()

    private val _isOverlayRunning = MutableStateFlow(FpsOverlayService.isRunning)
    val isOverlayRunning: StateFlow<Boolean> = _isOverlayRunning.asStateFlow()

    private val _settings = MutableStateFlow(OverlaySettings.load(context))
    val settings: StateFlow<OverlaySettings> = _settings.asStateFlow()

    private val _themeSettings = MutableStateFlow(AppThemeSettings.load(context))
    val themeSettings: StateFlow<AppThemeSettings> = _themeSettings.asStateFlow()

    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _sessions = MutableStateFlow<List<FpsSessionRecord>>(emptyList())
    val sessions: StateFlow<List<FpsSessionRecord>> = _sessions.asStateFlow()

    init {
        FpsAccessibilityService.onServiceStateChanged = { running ->
            if (running) {
                _accessibilityEnabled.value = true
                _accessibilityMalfunctioning.value = false
            } else {
                checkAccessibilityService(context)
            }
        }
        shizukuHelper.updateAvailability(packageManager)
        refreshSessions()
    }

    fun refreshStatus() {
        _isOverlayRunning.value = FpsOverlayService.isRunning
        shizukuHelper.updateAvailability(packageManager)
        _settings.value = OverlaySettings.load(context)
        _themeSettings.value = AppThemeSettings.load(context)
        checkOverlayPermission(context)
        checkAccessibilityService(context)
        refreshSessions()
    }

    fun refreshSessions() {
        _sessions.value = FpsRecordingManager.getSessions(context)
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
            _statusMessage.value = context.getString(R.string.shizuku_not_ready)
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
                        context.getString(R.string.overlay_permission_granted) else context.getString(R.string.overlay_granted_recheck_failed)
                } else {
                    _statusMessage.value = context.getString(R.string.shell_command_failed, exit)
                }
            } catch (e: Exception) {
                _statusMessage.value = context.getString(R.string.shizuku_error, e.message)
            }
        }
    }

    data class AppInfo(
        val packageName: String,
        val appName: String,
        val isSystemApp: Boolean = false
    )

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps.asStateFlow()

    private val _isLoadingApps = MutableStateFlow(false)
    val isLoadingApps: StateFlow<Boolean> = _isLoadingApps.asStateFlow()

    private val _accessibilityEnabled = MutableStateFlow(false)
    val accessibilityEnabled: StateFlow<Boolean> = _accessibilityEnabled.asStateFlow()

    private val _accessibilityMalfunctioning = MutableStateFlow(false)
    val accessibilityMalfunctioning: StateFlow<Boolean> = _accessibilityMalfunctioning.asStateFlow()

    fun dismissAccessibilityMalfunction() {
        _accessibilityMalfunctioning.value = false
    }

    fun forceStopAndResetViaShizuku(context: Context) {
        val current = _settings.value
        updateSettings(current.copy(autoStartEnabled = false))

        if (!shizukuHelper.shizukuAvailable.value || !shizukuHelper.shizukuPermissionGranted.value) {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return
        }

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val svc = "${context.packageName}/${FpsAccessibilityService::class.java.name}"
            val script = """
                SVC="$svc"
                CUR=${'$'}(settings get secure enabled_accessibility_services)
                TMP=${'$'}(echo "${'$'}CUR" | tr ':' '\n' | grep -v "${'$'}SVC" | paste -sd:)
                settings put secure enabled_accessibility_services "${'$'}TMP"
                am force-stop ${context.packageName}
            """.trimIndent()

            try {
                rikka.shizuku.Shizuku.newProcess(
                    arrayOf("sh", "-c", script),
                    null, null
                )
            } catch (e: Exception) {
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}")
                )
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        }
    }

    fun enableAccessibilityViaShizuku(context: Context, onComplete: ((Boolean) -> Unit)? = null) =
        restartAccessibilityViaShizuku(context, isEnabling = true, onComplete = onComplete)

    fun disableAccessibilityViaShizuku(context: Context, onComplete: ((Boolean) -> Unit)? = null) {
        if (!shizukuHelper.shizukuAvailable.value || !shizukuHelper.shizukuPermissionGranted.value) {
            onComplete?.invoke(false)
            return
        }

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val svc = "${context.packageName}/${FpsAccessibilityService::class.java.name}"
            val script = """
                SVC="$svc"
                CUR=${'$'}(settings get secure enabled_accessibility_services)
                TMP=${'$'}(echo "${'$'}CUR" | tr ':' '\n' | grep -v "${'$'}SVC" | grep -v '^${'$'}' | paste -sd:)
                if [ -z "${'$'}TMP" ] || [ "${'$'}TMP" = "null" ]; then
                    settings put secure enabled_accessibility_services ""
                    settings put secure accessibility_enabled 0
                else
                    settings put secure enabled_accessibility_services "${'$'}TMP"
                fi
            """.trimIndent()

            try {
                val process = rikka.shizuku.Shizuku.newProcess(
                    arrayOf("sh", "-c", script),
                    null, null
                )
                val exitCode = process.waitFor()
                kotlinx.coroutines.delay(400)
                FpsAccessibilityService.isServiceRunning = false
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    checkAccessibilityService(context)
                    if (exitCode == 0) {
                        _statusMessage.value = context.getString(R.string.accessibility_disable_success)
                    }
                    onComplete?.invoke(exitCode == 0)
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    checkAccessibilityService(context)
                    onComplete?.invoke(false)
                }
            }
        }
    }

    fun restartAccessibilityViaShizuku(
        context: Context,
        isEnabling: Boolean = false,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        if (!shizukuHelper.shizukuAvailable.value || !shizukuHelper.shizukuPermissionGranted.value) {
            _statusMessage.value = context.getString(R.string.shizuku_not_ready)
            onComplete?.invoke(false)
            return
        }

        val isRestart = !isEnabling
        _statusMessage.value = if (isRestart) {
            context.getString(R.string.accessibility_restarting)
        } else {
            context.getString(R.string.accessibility_starting)
        }

        // 1. Immediately toggle auto-start setting off
        val current = _settings.value
        updateSettings(current.copy(autoStartEnabled = false))

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val svc = "${context.packageName}/${FpsAccessibilityService::class.java.name}"
            val script = """
                SVC="$svc"
                CUR=${'$'}(settings get secure enabled_accessibility_services)
                TMP=${'$'}(echo "${'$'}CUR" | tr ':' '\n' | grep -v "${'$'}SVC" | paste -sd:)
                settings put secure enabled_accessibility_services "${'$'}TMP"
                settings put secure accessibility_enabled 0
                sleep 1
                if [ -z "${'$'}TMP" ] || [ "${'$'}TMP" = "null" ]; then
                    settings put secure enabled_accessibility_services "${'$'}SVC"
                else
                    settings put secure enabled_accessibility_services "${'$'}TMP:${'$'}SVC"
                fi
                settings put secure accessibility_enabled 1
            """.trimIndent()

            try {
                val process = rikka.shizuku.Shizuku.newProcess(
                    arrayOf("sh", "-c", script),
                    null, null
                )
                val exitCode = process.waitFor()
                kotlinx.coroutines.delay(800)
                val alive = FpsAccessibilityService.isServiceRunning
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    val healthy = checkAccessibilityService(context)
                    // 2. Toggle auto-start setting back on
                    val latest = _settings.value
                    updateSettings(latest.copy(autoStartEnabled = true))

                    if (exitCode == 0 || healthy || alive) {
                        _statusMessage.value = if (isRestart) {
                            context.getString(R.string.accessibility_restart_success)
                        } else {
                            context.getString(R.string.accessibility_enable_success)
                        }
                        onComplete?.invoke(true)
                    } else {
                        _statusMessage.value = if (isRestart) {
                            context.getString(R.string.accessibility_restart_failed)
                        } else {
                            context.getString(R.string.accessibility_enable_failed)
                        }
                        onComplete?.invoke(false)
                    }
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    val latest = _settings.value
                    updateSettings(latest.copy(autoStartEnabled = true))
                    _statusMessage.value = context.getString(R.string.shizuku_error, e.message)
                    onComplete?.invoke(false)
                }
            }
        }
    }

    fun loadInstalledApps() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isLoadingApps.value = true
            try {
                // Ensure a smooth, noticeable skeleton shimmer transition
                kotlinx.coroutines.delay(450)
                val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.queryIntentActivities(
                        mainIntent,
                        PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
                    )
                } else {
                    packageManager.queryIntentActivities(mainIntent, PackageManager.MATCH_ALL)
                }

                val apps = resolveInfos
                    .filter { it.activityInfo.packageName != context.packageName }
                    .map {
                        val isSys = (it.activityInfo.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                        AppInfo(
                            packageName = it.activityInfo.packageName,
                            appName = it.loadLabel(packageManager).toString(),
                            isSystemApp = isSys
                        )
                    }
                    .distinctBy { it.packageName }
                    .sortedWith(compareBy({ it.isSystemApp }, { it.appName.lowercase() }))
                _installedApps.value = apps
            } catch (e: Exception) {
                // Ignore failure querying launcher activities
            } finally {
                _isLoadingApps.value = false
            }
        }
    }

    fun checkAccessibilityService(context: Context): Boolean {
        // 1. AccessibilityManager enabled services list from system server (only returns actively bound services)
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        val isBoundInSystem = am?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            ?.any { 
                it.resolveInfo?.serviceInfo?.packageName == context.packageName &&
                it.resolveInfo?.serviceInfo?.name?.contains("FpsAccessibilityService") == true
            } == true

        // 2. Direct static runtime check if service instance is running in-process
        // If the system server does not have our service bound, it cannot deliver any accessibility events.
        // Reset any lingering in-process flag to prevent false positive healthy checks when service died.
        if (!isBoundInSystem) {
            FpsAccessibilityService.isServiceRunning = false
        }

        // Healthy ONLY if the system server actively has our service bound
        if (isBoundInSystem) {
            _accessibilityEnabled.value = true
            _accessibilityMalfunctioning.value = false
            return true
        }

        // 3. Check Settings.Secure to see if user had turned it on in system settings
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        val isConfiguredInSettings = enabledServices.split(":").any {
            val comp = ComponentName.unflattenFromString(it.trim())
            comp?.packageName == context.packageName && comp.className.contains("FpsAccessibilityService")
        }

        // If it is configured in Settings.Secure, but neither AccessibilityManager nor runtime service is active,
        // it has been stopped or crashed by the system ("Not working" state).
        if (isConfiguredInSettings) {
            _accessibilityEnabled.value = false
            _accessibilityMalfunctioning.value = true
            return false
        }

        // Neither running nor configured in settings
        _accessibilityEnabled.value = false
        _accessibilityMalfunctioning.value = false
        return false
    }

    fun toggleAutoStartPackage(pkg: String) {
        val current = _settings.value
        val updatedSet = current.autoStartPackages.toMutableSet()
        if (updatedSet.contains(pkg)) {
            updatedSet.remove(pkg)
        } else {
            updatedSet.add(pkg)
        }
        updateSettings(current.copy(autoStartPackages = updatedSet))
    }

    fun setRecordingPackage(pkg: String, enable: Boolean) {
        val current = _settings.value
        val updatedSet = current.recordingPackages.toMutableSet()
        if (enable) {
            updatedSet.add(pkg)
        } else {
            updatedSet.remove(pkg)
        }
        updateSettings(current.copy(recordingPackages = updatedSet))
    }

    fun setAutoRecordAll(enabled: Boolean) {
        val current = _settings.value
        updateSettings(current.copy(autoRecordAll = enabled))
    }

    fun deleteSession(sessionId: String) {
        FpsRecordingManager.deleteSession(context, sessionId)
        refreshSessions()
    }

    fun clearSessionsForPackage(packageName: String) {
        FpsRecordingManager.clearSessionsForPackage(context, packageName)
        refreshSessions()
    }

    fun clearAllSessions() {
        FpsRecordingManager.clearAllSessions(context)
        refreshSessions()
    }

    fun requestShizukuPermission() = shizukuHelper.requestPermission()
    fun checkShizukuStatus() = shizukuHelper.updateAvailability(packageManager)

    fun updateSettings(new: OverlaySettings) {
        _settings.value = new
        OverlaySettings.save(context, new)
    }

    fun updateThemeSettings(newSettings: AppThemeSettings) {
        _themeSettings.value = newSettings
        AppThemeSettings.save(context, newSettings)
    }

    fun setOverlayRunning(running: Boolean) {
        _isOverlayRunning.value = running
    }

    fun clearStatus() { _statusMessage.value = "" }

    override fun onCleared() {
        super.onCleared()
        FpsAccessibilityService.onServiceStateChanged = null
        shizukuHelper.onDestroy()
    }

    class Factory(
        private val pm: PackageManager,
        private val context: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            FpsViewModel(pm, context.applicationContext) as T
    }
}
