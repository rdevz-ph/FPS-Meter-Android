package com.rdevzph.fpsmeter.overlay

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.IBinder
import android.text.Layout
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import android.content.pm.PackageManager
import com.rdevzph.fpsmeter.MainActivity
import com.rdevzph.fpsmeter.R
import com.rdevzph.fpsmeter.accessibility.FpsAccessibilityService
import com.rdevzph.fpsmeter.model.FpsProvider
import com.rdevzph.fpsmeter.model.GraphicsApi
import com.rdevzph.fpsmeter.model.OverlayStyle
import com.rdevzph.fpsmeter.recording.FpsRecordingManager
import com.rdevzph.fpsmeter.util.DeviceHardwareHelper
import com.rdevzph.fpsmeter.util.LocaleHelper
import com.rdevzph.fpsmeter.viewmodel.OverlaySettings
import kotlinx.coroutines.*
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Lightweight foreground service that draws an FPS counter overlay mimicking Samsung Perf Z style,
 * with Quick Settings Tile integration, floating assistive button, and rich notification controls.
 */
class FpsOverlayService : Service() {

    companion object {
        const val CHANNEL_ID = "fps_overlay_channel"
        private const val NOTIF_ID = 1
        const val EXTRA_SETTINGS = "overlay_settings"

        const val ACTION_STOP = "STOP"
        const val ACTION_TOGGLE_VISIBILITY = "TOGGLE_VISIBILITY"

        // Intent extras for settings
        const val EXTRA_COLOR = "color"
        const val EXTRA_SIZE = "size"
        const val EXTRA_ALPHA = "alpha"
        const val EXTRA_BACKGROUND_ALPHA = "background_alpha"
        const val EXTRA_POSITION_X = "pos_x"
        const val EXTRA_POSITION_Y = "pos_y"
        const val EXTRA_SHOW_MS = "show_ms"
        const val EXTRA_SHOW_TEMP = "show_temp"
        const val EXTRA_SHOW_SOC_TEMP = "show_soc_temp"
        const val EXTRA_SHOW_CPU_TEMP = "show_cpu_temp"
        const val EXTRA_SHOW_GPU_TEMP = "show_gpu_temp"
        const val EXTRA_GRAVITY = "gravity"
        const val EXTRA_FLOATING_TOGGLE = "floating_toggle"
        const val EXTRA_FPS_PROVIDER = "fps_provider"
        const val EXTRA_SHOW_API = "show_api"
        const val EXTRA_SHOW_BATTERY_LEVEL = "show_battery_level"
        const val EXTRA_SHOW_BATTERY_POWER = "show_battery_power"
        const val EXTRA_SHOW_RAM = "show_ram"
        const val EXTRA_SINGLE_LINE_OVERLAY = "single_line_overlay"
        const val EXTRA_OVERLAY_STYLE = "overlay_style"

        @Volatile
        var instance: FpsOverlayService? = null
        var isRunning = false
        var isAutoStarted = false

        fun stop(context: Context) {
            isRunning = false
            isAutoStarted = false
            val currentInstance = instance
            if (currentInstance != null) {
                currentInstance.stopSelf()
            } else {
                try {
                    context.stopService(Intent(context, FpsOverlayService::class.java))
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: TextView
    private lateinit var layoutParams: WindowManager.LayoutParams
    private var floatingToggleButton: FloatingToggleButton? = null
    private var lastDetectedPackage: String? = null
    private var lastUseNextLine: Boolean? = null
    private var lastRecordingState: Boolean? = null
    private var lastForegroundPkgForAssistive: String? = null

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var choreoDetectorJob: Job? = null
    @Volatile private var choreoForegroundPkg: String? = null

    // Choreographer for frame timing
    private val choreographer = Choreographer.getInstance()
    private var frameCount = 0
    private var lastSampleTime = 0L
    private var currentFps = 0
    private var isOverlayVisible = true
    private var isChoreographerMeasuring = false

    // SurfaceFlinger privileged monitor via Shizuku
    private var surfaceFlingerFpsMonitor: SurfaceFlingerFpsMonitor? = null
    private var detectedGraphicsApi = GraphicsApi.UNKNOWN

    // Settings (with defaults)
    private var textColor = OverlaySettings.AUTO_COLOR
    private var textSizeSp = 14f
    private var overlayAlpha = 1.0f
    private var backgroundAlpha = 0.8f
    private var posX = 0
    private var posY = 100
    private var showMs = false
    private var showTemp = false
    private var showSocTemp = false
    private var showCpuTemp = false
    private var showGpuTemp = false
    private var overlayGravity = Gravity.TOP or Gravity.START
    private var floatingToggleEnabled = false
    private var fpsProvider = FpsProvider.CHOREOGRAPHER
    private var showGraphicsApi = true
    private var showBatteryLevel = false
    private var showBatteryPower = false
    private var showRam = false
    private var singleLineOverlay = false
    private var overlayStyle = OverlayStyle.DEFAULT

    private var availableRamGb: Float = 0f
    private var usedRamPercent: Int = 0
    private var ramMonitorJob: Job? = null

    private var batteryMonitor: BatteryMonitor? = null
    private var socTemp: Float = 0f
    private var cpuTemp: Float = 0f
    private var gpuTemp: Float = 0f
    private var socThermalMonitor: SocThermalMonitor? = null

    private fun getAppNameForPackage(pkg: String): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            pkg
        }
    }

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            frameCount++
            val now = System.currentTimeMillis()

            if (lastSampleTime == 0L) {
                lastSampleTime = now
            }

            val elapsed = now - lastSampleTime
            if (elapsed >= 1000L) {
                currentFps = (frameCount * 1000f / elapsed).roundToInt()
                frameCount = 0
                lastSampleTime = now
                if (isOverlayVisible) {
                    updateOverlayText()
                }

                // In-memory FPS recording integration
                val fgPkg = choreoForegroundPkg ?: FpsAccessibilityService.currentForegroundPackage
                if (fgPkg != null && fgPkg != packageName) {
                    lastDetectedPackage = fgPkg
                }
                val samplePkg = fgPkg ?: lastDetectedPackage ?: FpsRecordingManager.getActivePackage()
                val appName = samplePkg?.let { getAppNameForPackage(it) }
                FpsRecordingManager.onFpsSample(this@FpsOverlayService, samplePkg, currentFps, appName)

                val recActive = FpsRecordingManager.isRecordingActive()
                if (recActive != lastRecordingState || fgPkg != lastForegroundPkgForAssistive) {
                    lastRecordingState = recActive
                    lastForegroundPkgForAssistive = fgPkg
                    floatingToggleButton?.updateState()
                }
            }

            choreographer.postFrameCallback(this)
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        isRunning = true
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        batteryMonitor = BatteryMonitor(this).apply { start() }

        // Load initial settings from SharedPreferences
        val saved = OverlaySettings.load(this)
        textColor = saved.color
        textSizeSp = saved.textSizeSp
        overlayAlpha = saved.alpha
        backgroundAlpha = saved.backgroundAlpha
        posX = saved.posX
        posY = saved.posY
        showMs = saved.showMs
        showTemp = saved.showTemp
        showSocTemp = saved.showSocTemp
        showCpuTemp = saved.showCpuTemp
        showGpuTemp = saved.showGpuTemp
        overlayGravity = saved.gravity
        floatingToggleEnabled = saved.floatingToggleEnabled
        fpsProvider = saved.fpsProvider
        showGraphicsApi = saved.showGraphicsApi
        showBatteryLevel = saved.showBatteryLevel
        showBatteryPower = saved.showBatteryPower
        showRam = saved.showRam
        singleLineOverlay = saved.singleLineOverlay
        overlayStyle = saved.overlayStyle

        if (showRam) {
            val (ramGb, ramPercent) = sampleRamUsage()
            availableRamGb = ramGb
            usedRamPercent = ramPercent
        }
        updateRamMonitoring()
        updateSocThermalMonitoring()

        FpsTileService.updateTile(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stop(this)
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_VISIBILITY -> {
                toggleOverlayVisibility()
                return START_STICKY
            }
        }

        intent?.let { applySettings(it) }

        if (!::overlayView.isInitialized) {
            startForeground(NOTIF_ID, buildNotification())
            createOverlayView()
            startMeasuring()
        } else {
            updateOverlayAppearance()
        }

        syncFloatingToggleButton()
        FpsTileService.updateTile(this)

        return START_STICKY
    }

    private fun getActiveGamePackage(): String? {
        val recorded = FpsRecordingManager.getActivePackage()
        if (recorded != null) return recorded
        val lastRec = FpsRecordingManager.getLastRecordedPackage()
        if (lastRec != null && lastRec != packageName) return lastRec
        val sfPkg = surfaceFlingerFpsMonitor?.getCurrentForegroundPackage()
        if (sfPkg != null && sfPkg != packageName) return sfPkg
        val choreoPkg = choreoForegroundPkg
        if (choreoPkg != null && choreoPkg != packageName) return choreoPkg
        val accPkg = FpsAccessibilityService.currentForegroundPackage
        if (accPkg != null && accPkg != packageName) return accPkg
        return lastDetectedPackage?.takeIf { it != packageName }
    }

    private fun getActiveGameName(): String {
        val recordedName = FpsRecordingManager.getActiveAppName()
        if (recordedName.isNotEmpty()) return recordedName
        val lastRecName = FpsRecordingManager.getLastRecordedAppName()
        if (lastRecName.isNotEmpty()) return lastRecName
        val pkg = getActiveGamePackage() ?: return getString(R.string.no_active_game)
        return getAppNameForPackage(pkg)
    }

    private fun isGameRecordingConfigured(): Boolean {
        val settings = OverlaySettings.load(this)
        if (settings.autoRecordAll) return true
        val pkg = getActiveGamePackage() ?: return false
        return settings.recordingPackages.contains(pkg)
    }

    private fun startRecordingActiveGame(): Boolean {
        val pkg = getActiveGamePackage()
        if (pkg == null) {
            Toast.makeText(this, getString(R.string.no_active_game_to_record), Toast.LENGTH_SHORT).show()
            return false
        }
        val settings = OverlaySettings.load(this)
        val appName = getAppNameForPackage(pkg)
        if (!settings.autoRecordAll && !settings.recordingPackages.contains(pkg)) {
            Toast.makeText(this, getString(R.string.recording_not_enabled_for, appName), Toast.LENGTH_SHORT).show()
            return false
        }
        lastDetectedPackage = pkg
        val started = FpsRecordingManager.startRecording(this, pkg, appName)
        if (started) {
            Toast.makeText(this, getString(R.string.recording_fps_for, appName), Toast.LENGTH_SHORT).show()
            floatingToggleButton?.updateState()
            return true
        }
        return false
    }

    private fun stopRecordingActiveGame() {
        val record = FpsRecordingManager.stopRecording(this)
        if (record != null) {
            Toast.makeText(
                this,
                getString(R.string.saved_recording, record.appName, record.avgFps, record.maxFps),
                Toast.LENGTH_LONG
            ).show()
        } else {
            Toast.makeText(this, getString(R.string.recording_stopped), Toast.LENGTH_SHORT).show()
        }
        floatingToggleButton?.updateState()
    }

    private fun syncFloatingToggleButton() {
        if (floatingToggleEnabled) {
            if (floatingToggleButton == null) {
                floatingToggleButton = FloatingToggleButton(
                    context = this,
                    onToggleOverlay = { toggleOverlayVisibility() },
                    onStartRecording = { startRecordingActiveGame() },
                    onStopRecording = { stopRecordingActiveGame() },
                    isRecordingActive = { FpsRecordingManager.isRecordingActive() },
                    isOverlayVisible = { isOverlayVisible },
                    getActiveGameName = { getActiveGameName() },
                    isGameRecordingConfigured = { isGameRecordingConfigured() },
                    onReturnToApp = { returnToApp() }
                )
            }
            floatingToggleButton?.updateState()
            floatingToggleButton?.show()
        } else {
            floatingToggleButton?.hide()
            floatingToggleButton = null
        }
    }

    private fun returnToApp() {
        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
            startActivity(intent)
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun toggleOverlayVisibility() {
        isOverlayVisible = !isOverlayVisible
        if (::overlayView.isInitialized) {
            overlayView.visibility = if (isOverlayVisible) View.VISIBLE else View.GONE
        }
        floatingToggleButton?.updateState()

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIF_ID, buildNotification())
    }

    private fun applySettings(intent: Intent) {
        if (intent.hasExtra(EXTRA_COLOR)) {
            textColor = intent.getIntExtra(EXTRA_COLOR, textColor)
        }
        if (intent.hasExtra(EXTRA_SIZE)) {
            textSizeSp = intent.getFloatExtra(EXTRA_SIZE, textSizeSp)
        }
        if (intent.hasExtra(EXTRA_ALPHA)) {
            overlayAlpha = intent.getFloatExtra(EXTRA_ALPHA, overlayAlpha)
        }
        if (intent.hasExtra(EXTRA_BACKGROUND_ALPHA)) {
            backgroundAlpha = intent.getFloatExtra(EXTRA_BACKGROUND_ALPHA, backgroundAlpha)
        }
        if (intent.hasExtra(EXTRA_POSITION_X)) {
            posX = intent.getIntExtra(EXTRA_POSITION_X, posX)
        }
        if (intent.hasExtra(EXTRA_POSITION_Y)) {
            posY = intent.getIntExtra(EXTRA_POSITION_Y, posY)
        }
        if (intent.hasExtra(EXTRA_SHOW_MS)) {
            showMs = intent.getBooleanExtra(EXTRA_SHOW_MS, showMs)
        }
        if (intent.hasExtra(EXTRA_SHOW_TEMP)) {
            showTemp = intent.getBooleanExtra(EXTRA_SHOW_TEMP, showTemp)
        }
        if (intent.hasExtra(EXTRA_SHOW_SOC_TEMP)) {
            val newShowSoc = intent.getBooleanExtra(EXTRA_SHOW_SOC_TEMP, showSocTemp)
            if (newShowSoc != showSocTemp) {
                showSocTemp = newShowSoc
                updateSocThermalMonitoring()
            }
        }
        if (intent.hasExtra(EXTRA_SHOW_CPU_TEMP)) {
            val newShowCpu = intent.getBooleanExtra(EXTRA_SHOW_CPU_TEMP, showCpuTemp)
            if (newShowCpu != showCpuTemp) {
                showCpuTemp = newShowCpu
                updateSocThermalMonitoring()
            }
        }
        if (intent.hasExtra(EXTRA_SHOW_GPU_TEMP)) {
            val newShowGpu = intent.getBooleanExtra(EXTRA_SHOW_GPU_TEMP, showGpuTemp)
            if (newShowGpu != showGpuTemp) {
                showGpuTemp = newShowGpu
                updateSocThermalMonitoring()
            }
        }
        if (intent.hasExtra(EXTRA_GRAVITY)) {
            overlayGravity = intent.getIntExtra(EXTRA_GRAVITY, overlayGravity)
        }
        if (intent.hasExtra(EXTRA_FLOATING_TOGGLE)) {
            floatingToggleEnabled = intent.getBooleanExtra(EXTRA_FLOATING_TOGGLE, floatingToggleEnabled)
        }
        if (intent.hasExtra(EXTRA_FPS_PROVIDER)) {
            val providerName = intent.getStringExtra(EXTRA_FPS_PROVIDER)
            val newProvider = FpsProvider.fromString(providerName)
            if (newProvider != fpsProvider) {
                fpsProvider = newProvider
                if (isRunning) {
                    switchMeasuringProvider()
                }
            }
        }
        if (intent.hasExtra(EXTRA_SHOW_API)) {
            showGraphicsApi = intent.getBooleanExtra(EXTRA_SHOW_API, showGraphicsApi)
        }
        if (intent.hasExtra(EXTRA_SHOW_BATTERY_LEVEL)) {
            showBatteryLevel = intent.getBooleanExtra(EXTRA_SHOW_BATTERY_LEVEL, showBatteryLevel)
        }
        if (intent.hasExtra(EXTRA_SHOW_BATTERY_POWER)) {
            showBatteryPower = intent.getBooleanExtra(EXTRA_SHOW_BATTERY_POWER, showBatteryPower)
        }
        if (intent.hasExtra(EXTRA_SHOW_RAM)) {
            val newShowRam = intent.getBooleanExtra(EXTRA_SHOW_RAM, showRam)
            if (newShowRam != showRam) {
                showRam = newShowRam
                if (showRam && availableRamGb == 0f) {
                    val (ramGb, ramPercent) = sampleRamUsage()
                    availableRamGb = ramGb
                    usedRamPercent = ramPercent
                }
                updateRamMonitoring()
            }
        }
        if (intent.hasExtra(EXTRA_SINGLE_LINE_OVERLAY)) {
            val newSingleLine = intent.getBooleanExtra(EXTRA_SINGLE_LINE_OVERLAY, singleLineOverlay)
            if (newSingleLine != singleLineOverlay) {
                singleLineOverlay = newSingleLine
                if (::overlayView.isInitialized) {
                    updateOverlayAppearance()
                }
            }
        }
        if (intent.hasExtra(EXTRA_OVERLAY_STYLE)) {
            val styleStr = intent.getStringExtra(EXTRA_OVERLAY_STYLE)
            val newStyle = OverlayStyle.fromString(styleStr)
            if (newStyle != overlayStyle) {
                overlayStyle = newStyle
                if (::overlayView.isInitialized) {
                    updateOverlayAppearance()
                }
            }
        }
    }

    private fun createOverlayBackgroundDrawable(isMultiLine: Boolean): GradientDrawable {
        val bgAlphaInt = (backgroundAlpha.coerceIn(0f, 1f) * 255).toInt()
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = if (overlayStyle == OverlayStyle.MSI_AFTERBURNER) {
                16f
            } else if (isMultiLine) {
                32f
            } else {
                1000f
            }
            val bgColor = if (overlayStyle == OverlayStyle.MSI_AFTERBURNER) {
                Color.argb(bgAlphaInt, 20, 24, 32)
            } else {
                Color.argb(bgAlphaInt, 17, 17, 17)
            }
            setColor(bgColor)
        }
    }

    private fun updateOverlayAppearance() {
        if (::overlayView.isInitialized) {
            overlayView.post {
                overlayView.textSize = textSizeSp
                overlayView.typeface = Typeface.DEFAULT_BOLD
                overlayView.gravity = if (overlayStyle == OverlayStyle.MSI_AFTERBURNER && !singleLineOverlay) Gravity.START else Gravity.CENTER
                if (singleLineOverlay) {
                    overlayView.isSingleLine = true
                    overlayView.maxLines = 1
                } else {
                    overlayView.isSingleLine = false
                    overlayView.maxLines = Int.MAX_VALUE
                }
                val isMulti = if (overlayStyle == OverlayStyle.MSI_AFTERBURNER) !singleLineOverlay else (lastUseNextLine == true)
                overlayView.background = createOverlayBackgroundDrawable(isMulti)
                if (overlayStyle == OverlayStyle.MSI_AFTERBURNER) {
                    if (singleLineOverlay) {
                        overlayView.setPadding(24, 8, 24, 8)
                    } else {
                        overlayView.setPadding(24, 14, 24, 14)
                    }
                } else if (isMulti) {
                    overlayView.setPadding(28, 12, 28, 12)
                } else {
                    overlayView.setPadding(24, 8, 24, 8)
                }
                overlayView.alpha = 1.0f
                val shadowAlpha = (255 * overlayAlpha.coerceIn(0.1f, 1.0f)).roundToInt()
                overlayView.setShadowLayer(2f, 0f, 0f, Color.argb(shadowAlpha, 0, 0, 0))
                
                layoutParams.gravity = overlayGravity
                layoutParams.x = posX
                layoutParams.y = posY
                windowManager.updateViewLayout(overlayView, layoutParams)
                
                updateOverlayText()
            }
        }
    }

    private fun createOverlayView() {
        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = overlayGravity
            x = posX
            y = posY
        }

        val isMulti = if (overlayStyle == OverlayStyle.MSI_AFTERBURNER) !singleLineOverlay else false
        overlayView = OverlayTextView(this).apply {
            background = createOverlayBackgroundDrawable(isMulti)
            textSize = textSizeSp
            typeface = Typeface.DEFAULT_BOLD
            alpha = 1.0f
            gravity = if (overlayStyle == OverlayStyle.MSI_AFTERBURNER && !singleLineOverlay) Gravity.START else Gravity.CENTER
            setLineSpacing(4f, 1f)
            if (singleLineOverlay) {
                isSingleLine = true
                maxLines = 1
            } else {
                isSingleLine = false
                maxLines = Int.MAX_VALUE
            }
            if (overlayStyle == OverlayStyle.MSI_AFTERBURNER) {
                if (singleLineOverlay) {
                    setPadding(24, 8, 24, 8)
                } else {
                    setPadding(24, 14, 24, 14)
                }
            } else {
                setPadding(24, 8, 24, 8)
            }
            val shadowAlpha = (255 * overlayAlpha.coerceIn(0.1f, 1.0f)).roundToInt()
            setShadowLayer(2f, 0f, 0f, Color.argb(shadowAlpha, 0, 0, 0))
            setOnTouchListener(DragTouchListener())
        }

        windowManager.addView(overlayView, layoutParams)
        updateOverlayText()
    }

    private fun updateOverlayText() {
        if (!::overlayView.isInitialized) return
        val fps = currentFps
        val fpsValueColor = if (textColor == OverlaySettings.AUTO_COLOR) {
             when {
                fps >= 60 -> Color.GREEN
                fps >= 45 -> Color.YELLOW
                fps >= 30 -> Color.rgb(255, 165, 0)
                fps > 0 -> Color.RED
                else -> Color.GREEN
            }
        } else {
            textColor
        }

        val labelColor = Color.parseColor("#00E5FF") // Cyan accent
        val valueColor = Color.WHITE

        val batteryTemp = batteryMonitor?.getTemperature() ?: 0f
        val batteryLevel = batteryMonitor?.getLevel() ?: -1

        val hasApi = fpsProvider == FpsProvider.SURFACE_FLINGER && showGraphicsApi && detectedGraphicsApi != GraphicsApi.UNKNOWN
        val hasMs = showMs && fps > 0
        val hasCpu = showCpuTemp
        val hasGpu = showGpuTemp
        val hasSoc = showSocTemp
        val hasBatt = showTemp
        val hasBattLevel = showBatteryLevel
        val hasBattPower = showBatteryPower
        val hasBattBoth = hasBatt && hasBattLevel
        val hasRam = showRam

        val extraOverlayCount = (if (hasApi) 1 else 0) +
                (if (hasMs) 1 else 0) +
                (if (hasCpu) 1 else 0) +
                (if (hasGpu) 1 else 0) +
                (if (hasSoc) 1 else 0) +
                (if (hasBattBoth) 1 else ((if (hasBatt) 1 else 0) + (if (hasBattLevel) 1 else 0))) +
                (if (hasBattPower) 1 else 0)

        val hasThermals = hasCpu || hasGpu || hasSoc || hasBatt || hasBattLevel || hasBattPower
        // Wrap thermals to next line if more than 3 extra overlays are enabled (> 3) OR if RAM overlay is also enabled and thermals are present
        // If singleLineOverlay is enabled, keep all metrics on a single horizontal line.
        val useNextLine = !singleLineOverlay && ((extraOverlayCount > 3 && hasThermals) || (hasRam && hasThermals))
        val isMultiLine = !singleLineOverlay && (useNextLine || hasRam)

        val ssb = SpannableStringBuilder()

        if (overlayStyle == OverlayStyle.MSI_AFTERBURNER) {
            val socModel = DeviceHardwareHelper.getSocModel()
            val cpuFreq = DeviceHardwareHelper.getCpuFrequencyMhz()

            val colorGpu = Color.parseColor("#00E676")   // Bright Green
            val colorCpu = Color.parseColor("#00B0FF")   // Sky Blue
            val colorSocTag = Color.parseColor("#00E5FF") // Electric Cyan
            val colorBat = Color.parseColor("#FFA000")   // Amber Gold
            val colorRam = Color.parseColor("#BA68C8")   // Orchid / Violet
            val colorFps = Color.parseColor("#FF8A80")   // Salmon Coral
            val colorVal = Color.parseColor("#FF7A00")   // Bright Orange for active values
            val colorNa = Color.parseColor("#808080")    // Medium Gray for N/A
            val colorSoc = Color.parseColor("#B0BEC5")   // Soft Silver for SoC header
            val colorApi = if (detectedGraphicsApi == GraphicsApi.VULKAN) {
                Color.parseColor("#FF5722") // Distinct orange for Vulkan
            } else {
                Color.parseColor("#2196F3") // Blue for OpenGL ES
            }

            fun appendRow(tag: String, tagColor: Int, cells: List<Pair<String, Int>>) {
                ssb.append(tag, tagColor, StyleSpan(Typeface.BOLD))
                val spaceAfterTag = if (singleLineOverlay) "\u00A0" else "    "
                ssb.append(spaceAfterTag)
                val spaceBetweenCells = if (singleLineOverlay) " " else "    "
                cells.forEachIndexed { index, (value, vColor) ->
                    if (index > 0) {
                        ssb.append(spaceBetweenCells)
                    }
                    ssb.append(value, vColor, StyleSpan(Typeface.BOLD))
                }
            }

            var isFirstLine = true
            fun newLine() {
                if (!isFirstLine) {
                    if (singleLineOverlay) {
                        ssb.append("\u00A0 | ", Color.GRAY)
                    } else {
                        ssb.append("\n")
                    }
                }
                isFirstLine = false
            }

            // 1. SoC header on top
            if (socModel.isNotEmpty()) {
                newLine()
                ssb.append(socModel, colorSoc, StyleSpan(Typeface.BOLD))
            }

            // 2. GPU row (only if GPU temp enabled)
            if (hasGpu) {
                newLine()
                val gpuTempStr = if (gpuTemp > 0f) {
                    if (singleLineOverlay) String.format(Locale.US, "%.1f°C", gpuTemp) else String.format(Locale.US, "%.1f °C", gpuTemp)
                } else {
                    if (singleLineOverlay) "--°C" else "-- °C"
                }
                appendRow("GPU", colorGpu, listOf(gpuTempStr to colorVal))
            }

            // 3. CPU row (only if CPU temp enabled)
            if (hasCpu) {
                newLine()
                val cpuTempStr = if (cpuTemp > 0f) {
                    if (singleLineOverlay) String.format(Locale.US, "%.1f°C", cpuTemp) else String.format(Locale.US, "%.1f °C", cpuTemp)
                } else {
                    if (singleLineOverlay) "--°C" else "-- °C"
                }
                val cpuCells = mutableListOf(cpuTempStr to colorVal)
                if (cpuFreq > 0) {
                    val freqStr = if (singleLineOverlay) "${cpuFreq}MHz" else "$cpuFreq MHz"
                    cpuCells.add(freqStr to colorVal)
                }
                appendRow("CPU", colorCpu, cpuCells)
            }

            // 4. SoC Temp row (only if SoC temp enabled)
            if (hasSoc) {
                newLine()
                val socTempStr = if (socTemp > 0f) {
                    if (singleLineOverlay) String.format(Locale.US, "%.1f°C", socTemp) else String.format(Locale.US, "%.1f °C", socTemp)
                } else {
                    if (singleLineOverlay) "--°C" else "-- °C"
                }
                appendRow("SOC", colorSocTag, listOf(socTempStr to colorVal))
            }

            // 5. BAT row (only if battery temp, level, or power enabled)
            if (hasBatt || hasBattLevel || hasBattPower) {
                newLine()
                val batCells = mutableListOf<Pair<String, Int>>()
                if (hasBatt) {
                    val batTempStr = if (batteryTemp > 0f) {
                        if (singleLineOverlay) String.format(Locale.US, "%.1f°C", batteryTemp) else String.format(Locale.US, "%.1f °C", batteryTemp)
                    } else {
                        if (singleLineOverlay) "--°C" else "-- °C"
                    }
                    batCells.add(batTempStr to colorVal)
                }
                if (hasBattLevel && batteryLevel >= 0) {
                    val batLevelStr = if (singleLineOverlay) "$batteryLevel%" else "$batteryLevel %"
                    batCells.add(batLevelStr to colorVal)
                }
                if (hasBattPower) {
                    val powerStr = batteryMonitor?.getPowerText(singleLineOverlay)
                    if (powerStr != null) {
                        batCells.add(powerStr to colorVal)
                    }
                }
                appendRow("BAT", colorBat, batCells)
            }

            // 6. RAM row (only if RAM overlay enabled)
            if (hasRam) {
                newLine()
                val ramGbStr = if (availableRamGb > 0f) {
                    if (singleLineOverlay) String.format(Locale.US, "%.1fGB", availableRamGb) else String.format(Locale.US, "%.1f GB", availableRamGb)
                } else {
                    if (singleLineOverlay) "--GB" else "-- GB"
                }
                val ramCells = mutableListOf(ramGbStr to colorVal)
                if (usedRamPercent > 0) {
                    val ramPctStr = if (singleLineOverlay) "$usedRamPercent%" else "$usedRamPercent %"
                    ramCells.add(ramPctStr to colorVal)
                }
                appendRow("RAM", colorRam, ramCells)
            }

            // 7. FPS row (always shown)
            newLine()
            val fpsStr = "$fps FPS"
            val fpsColor = if (textColor == OverlaySettings.AUTO_COLOR) colorVal else fpsValueColor
            val fpsCells = mutableListOf(fpsStr to fpsColor)
            if (hasApi && detectedGraphicsApi != GraphicsApi.UNKNOWN) {
                fpsCells.add(detectedGraphicsApi.shortLabel to colorApi)
            }
            if (hasMs && fps > 0) {
                val msStr = if (singleLineOverlay) "${(1000f / fps).roundToInt()}ms" else "${(1000f / fps).roundToInt()} ms"
                fpsCells.add(msStr to colorVal)
            }
            appendRow("FPS", colorFps, fpsCells)

            val isMulti = !singleLineOverlay
            overlayView.post {
                if (lastUseNextLine != isMulti) {
                    lastUseNextLine = isMulti
                    overlayView.background = createOverlayBackgroundDrawable(isMulti)
                    if (isMulti) {
                        overlayView.setPadding(24, 14, 24, 14)
                    } else {
                        overlayView.setPadding(24, 8, 24, 8)
                    }
                }
                if (singleLineOverlay) {
                    if (!overlayView.isSingleLine) {
                        overlayView.isSingleLine = true
                        overlayView.maxLines = 1
                    }
                } else {
                    if (overlayView.isSingleLine) {
                        overlayView.isSingleLine = false
                        overlayView.maxLines = Int.MAX_VALUE
                    }
                }
                overlayView.text = ssb
            }
            return
        }

        // === Performance Group (FPS, Graphics API, Frame Time) ===
        ssb.append("FPS ", labelColor, StyleSpan(Typeface.BOLD))
        ssb.append("$fps", fpsValueColor, StyleSpan(Typeface.BOLD))

        // Graphics API tag (when using SurfaceFlinger provider and option enabled)
        if (hasApi) {
            val apiColor = if (detectedGraphicsApi == GraphicsApi.VULKAN) {
                Color.parseColor("#FF5722") // Distinct orange for Vulkan
            } else {
                Color.parseColor("#2196F3") // Blue for OpenGL ES
            }
            ssb.append("  |  ", Color.GRAY)
            ssb.append(detectedGraphicsApi.shortLabel, apiColor, StyleSpan(Typeface.BOLD))
        }

        // MS segment
        if (hasMs) {
            ssb.append("  |  ", Color.GRAY)
            ssb.append("MS ", labelColor, StyleSpan(Typeface.BOLD))
            ssb.append("${(1000f / fps).roundToInt()}", valueColor, StyleSpan(Typeface.BOLD))
        }

        // === Hardware / Thermals Group (CPU, GPU, SoC, Battery) ===
        if (hasThermals) {
            if (useNextLine) {
                ssb.append("\n")
            }
            var isFirstThermalOnLine = useNextLine
            val tagSpace = if (singleLineOverlay) "\u00A0" else " "
            fun appendThermal(tag: String, value: String) {
                if (!isFirstThermalOnLine) {
                    ssb.append("  |  ", Color.GRAY)
                }
                ssb.append("$tag$tagSpace", labelColor, StyleSpan(Typeface.BOLD))
                ssb.append(value, valueColor, StyleSpan(Typeface.BOLD))
                isFirstThermalOnLine = false
            }

            if (hasCpu) {
                val cpuDisplay = if (cpuTemp > 0f) "${cpuTemp}°C" else "--°C"
                appendThermal("CPU", cpuDisplay)
            }
            if (hasGpu) {
                val gpuDisplay = if (gpuTemp > 0f) "${gpuTemp}°C" else "--°C"
                appendThermal("GPU", gpuDisplay)
            }
            if (hasSoc) {
                val socDisplay = if (socTemp > 0f) "${socTemp}°C" else "--°C"
                appendThermal("SOC", socDisplay)
            }
            val powerSuffix = if (hasBattPower) {
                val p = batteryMonitor?.getPowerText(singleLineOverlay)
                if (p != null) (if (singleLineOverlay) "\u00A0$p" else " $p") else ""
            } else ""

            if (hasBattBoth) {
                val levelDisplay = if (batteryLevel >= 0) "${batteryLevel}%" else "--%"
                val tempDisplay = if (batteryTemp > 0f) "${batteryTemp}°C" else "--°C"
                val valueSep = if (singleLineOverlay) "\u00A0" else " "
                appendThermal("BAT", "$tempDisplay$valueSep($levelDisplay)$powerSuffix")
            } else if (hasBatt) {
                appendThermal("BATT", "${batteryTemp}°C$powerSuffix")
            } else if (hasBattLevel) {
                val levelDisplay = if (batteryLevel >= 0) "${batteryLevel}%" else "--%"
                appendThermal("BAT", "$levelDisplay$powerSuffix")
            } else if (hasBattPower) {
                val p = batteryMonitor?.getPowerText(singleLineOverlay) ?: "--W"
                appendThermal("BAT", p)
            }
        }

        // === Memory Group (RAM) ===
        if (hasRam) {
            if (singleLineOverlay) {
                ssb.append("  |  ", Color.GRAY)
            } else {
                ssb.append("\n")
            }
            val ramTagSpace = if (singleLineOverlay) "\u00A0" else " "
            ssb.append("RAM$ramTagSpace", labelColor, StyleSpan(Typeface.BOLD))
            val ramDisplay = if (availableRamGb > 0f) {
                if (singleLineOverlay) {
                    String.format(Locale.US, "%.1f\u00A0GB\u00A0(%d%%)", availableRamGb, usedRamPercent)
                } else {
                    String.format(Locale.US, "%.1f GB (%d%%)", availableRamGb, usedRamPercent)
                }
            } else {
                if (singleLineOverlay) "--\u00A0GB\u00A0(--%)" else "-- GB (--%)"
            }
            ssb.append(ramDisplay, valueColor, StyleSpan(Typeface.BOLD))
        }

        overlayView.post {
            if (lastUseNextLine != isMultiLine) {
                lastUseNextLine = isMultiLine
                overlayView.background = createOverlayBackgroundDrawable(isMultiLine)
                if (isMultiLine) {
                    overlayView.setPadding(28, 12, 28, 12)
                } else {
                    overlayView.setPadding(24, 8, 24, 8)
                }
            }
            if (singleLineOverlay) {
                if (!overlayView.isSingleLine) {
                    overlayView.isSingleLine = true
                    overlayView.maxLines = 1
                }
            } else {
                if (overlayView.isSingleLine) {
                    overlayView.isSingleLine = false
                    overlayView.maxLines = Int.MAX_VALUE
                }
            }
            overlayView.text = ssb
        }
    }

    private fun applyTextAlpha(color: Int): Int {
        val factor = overlayAlpha.coerceIn(0.1f, 1.0f)
        val origAlpha = Color.alpha(color)
        val newAlpha = (origAlpha * factor).roundToInt().coerceIn(0, 255)
        return Color.argb(newAlpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun SpannableStringBuilder.append(text: String, color: Int, style: Any? = null): SpannableStringBuilder {
        val start = length
        append(text)
        setSpan(ForegroundColorSpan(applyTextAlpha(color)), start, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        style?.let { setSpan(it, start, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE) }
        return this
    }

    private fun startMeasuring() {
        switchMeasuringProvider()
    }

    private fun switchMeasuringProvider() {
        if (fpsProvider == FpsProvider.SURFACE_FLINGER) {
            val isShizukuReady = try {
                Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } catch (e: Exception) {
                false
            }

            if (isShizukuReady) {
                stopChoreographerMeasuring()
                startSurfaceFlingerMeasuring()
                return
            } else {
                android.util.Log.w("FpsOverlayService", "Shizuku not ready for SurfaceFlinger, falling back to Choreographer")
            }
        }

        // Default or fallback: Choreographer
        stopSurfaceFlingerMeasuring()
        startChoreographerMeasuring()
    }

    private fun startChoreographerMeasuring() {
        if (!isChoreographerMeasuring) {
            lastSampleTime = 0L
            frameCount = 0
            isChoreographerMeasuring = true
            choreographer.postFrameCallback(frameCallback)
            startChoreoForegroundDetection()
        }
    }

    private fun stopChoreographerMeasuring() {
        if (isChoreographerMeasuring) {
            choreographer.removeFrameCallback(frameCallback)
            isChoreographerMeasuring = false
            stopChoreoForegroundDetection()
        }
    }

    private fun startChoreoForegroundDetection() {
        stopChoreoForegroundDetection()
        choreoDetectorJob = serviceScope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val pkg = getChoreoForegroundPackage()
                    choreoForegroundPkg = pkg
                } catch (e: CancellationException) {
                    break
                } catch (e: Exception) {
                    // Ignore
                }
                delay(1000L)
            }
        }
    }

    private fun stopChoreoForegroundDetection() {
        choreoDetectorJob?.cancel()
        choreoDetectorJob = null
        choreoForegroundPkg = null
    }

    private fun getChoreoForegroundPackage(): String? {
        val isShizukuReady = try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
        if (isShizukuReady) {
            val windowOutput = runShizukuShellCommand("dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'")
            for (line in windowOutput.lines()) {
                val match = Regex("(?:mCurrentFocus|mFocusedApp)=.*?([a-zA-Z0-9._]+)/[a-zA-Z0-9._]+").find(line)
                if (match != null) {
                    val pkg = match.groupValues[1]
                    if (pkg != packageName) {
                        return pkg
                    }
                }
            }

            val actOutput = runShizukuShellCommand("dumpsys activity activities | grep -E 'mResumedActivity|ResumedActivity'")
            for (line in actOutput.lines()) {
                val match = Regex("([a-zA-Z0-9._]+)/[a-zA-Z0-9._]+").find(line)
                if (match != null) {
                    val pkg = match.groupValues[1]
                    if (pkg != packageName) {
                        return pkg
                    }
                }
            }
        }

        return FpsAccessibilityService.currentForegroundPackage
    }

    @Suppress("DEPRECATION")
    private fun runShizukuShellCommand(command: String): String {
        var process: Process? = null
        return try {
            process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
            val output = StringBuilder()
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    output.append(line).append("\n")
                }
            }
            process.waitFor()
            output.toString()
        } catch (e: Exception) {
            ""
        } finally {
            try {
                process?.destroy()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun startSurfaceFlingerMeasuring() {
        if (surfaceFlingerFpsMonitor == null) {
            surfaceFlingerFpsMonitor = SurfaceFlingerFpsMonitor(
                onFpsUpdate = { fps, _, api, _, fgPkg ->
                    currentFps = fps
                    detectedGraphicsApi = api
                    if (isOverlayVisible) {
                        updateOverlayText()
                    }

                    // In-memory FPS recording integration
                    val activePkg = fgPkg ?: FpsAccessibilityService.currentForegroundPackage
                    if (activePkg != null && activePkg != packageName) {
                        lastDetectedPackage = activePkg
                    }
                    val samplePkg = activePkg ?: lastDetectedPackage ?: FpsRecordingManager.getActivePackage()
                    val appName = samplePkg?.let { getAppNameForPackage(it) }
                    FpsRecordingManager.onFpsSample(this@FpsOverlayService, samplePkg, currentFps, appName)

                    val recActive = FpsRecordingManager.isRecordingActive()
                    if (recActive != lastRecordingState || activePkg != lastForegroundPkgForAssistive) {
                        lastRecordingState = recActive
                        lastForegroundPkgForAssistive = activePkg
                        floatingToggleButton?.updateState()
                    }
                },
                onFallbackNeeded = {
                    android.util.Log.w("FpsOverlayService", "SurfaceFlinger collector failed, falling back to Choreographer")
                    stopSurfaceFlingerMeasuring()
                    startChoreographerMeasuring()
                }
            )
        }
        surfaceFlingerFpsMonitor?.start()
    }

    private fun stopSurfaceFlingerMeasuring() {
        surfaceFlingerFpsMonitor?.stop()
        surfaceFlingerFpsMonitor = null
        detectedGraphicsApi = GraphicsApi.UNKNOWN
    }

    private fun updateSocThermalMonitoring() {
        val anyShizukuTempNeeded = showSocTemp || showCpuTemp || showGpuTemp
        if (anyShizukuTempNeeded) {
            if (socThermalMonitor == null) {
                socThermalMonitor = SocThermalMonitor(
                    onSnapshotUpdate = { snapshot ->
                        socTemp = snapshot.soc ?: 0f
                        cpuTemp = snapshot.cpu ?: 0f
                        gpuTemp = snapshot.gpu ?: 0f
                        if (isOverlayVisible) {
                            updateOverlayText()
                        }
                    },
                    onUnavailable = {
                        var changed = false
                        if (socTemp != 0f) { socTemp = 0f; changed = true }
                        if (cpuTemp != 0f) { cpuTemp = 0f; changed = true }
                        if (gpuTemp != 0f) { gpuTemp = 0f; changed = true }
                        if (changed && isOverlayVisible) {
                            updateOverlayText()
                        }
                    }
                )
            }
            socThermalMonitor?.start()
        } else {
            socThermalMonitor?.stop()
            socThermalMonitor = null
            socTemp = 0f
            cpuTemp = 0f
            gpuTemp = 0f
            if (isOverlayVisible) {
                updateOverlayText()
            }
        }
    }

    private fun sampleRamUsage(): Pair<Float, Int> {
        return try {
            val actManager = getSystemService(ACTIVITY_SERVICE) as? ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            actManager?.getMemoryInfo(memInfo)
            val availGb = memInfo.availMem.toFloat() / (1024f * 1024f * 1024f)
            val usedPercent = if (memInfo.totalMem > 0L) {
                val usedBytes = (memInfo.totalMem - memInfo.availMem).coerceAtLeast(0L)
                ((usedBytes.toDouble() / memInfo.totalMem.toDouble()) * 100.0).roundToInt().coerceIn(0, 100)
            } else {
                0
            }
            Pair(availGb, usedPercent)
        } catch (e: Exception) {
            Pair(0f, 0)
        }
    }

    private fun updateRamMonitoring() {
        if (showRam) {
            if (ramMonitorJob == null || ramMonitorJob?.isActive != true) {
                ramMonitorJob = serviceScope.launch(Dispatchers.IO) {
                    while (isActive) {
                        val (ramGb, ramPercent) = sampleRamUsage()
                        withContext(Dispatchers.Main) {
                            availableRamGb = ramGb
                            usedRamPercent = ramPercent
                            if (isOverlayVisible) {
                                updateOverlayText()
                            }
                        }
                        delay(2000L)
                    }
                }
            }
        } else {
            ramMonitorJob?.cancel()
            ramMonitorJob = null
            availableRamGb = 0f
            usedRamPercent = 0
            if (isOverlayVisible) {
                updateOverlayText()
            }
        }
    }

    private fun buildNotification(): Notification {
        val tapIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stopIntent = PendingIntent.getService(this, 1, Intent(this, FpsOverlayService::class.java).apply { action = ACTION_STOP }, PendingIntent.FLAG_IMMUTABLE)
        val toggleIntent = PendingIntent.getService(this, 2, Intent(this, FpsOverlayService::class.java).apply { action = ACTION_TOGGLE_VISIBILITY }, PendingIntent.FLAG_IMMUTABLE)

        val toggleLabel = if (isOverlayVisible) getString(R.string.notif_action_hide) else getString(R.string.notif_action_show)
        val statusText = if (isOverlayVisible) getString(R.string.notif_status_visible) else getString(R.string.notif_status_hidden)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(statusText)
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setContentIntent(tapIntent)
            .addAction(0, toggleLabel, toggleIntent)
            .addAction(0, getString(R.string.notif_action_stop), stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private inner class OverlayTextView(context: Context) : TextView(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            val currentText = text
            if (!currentText.isNullOrEmpty()) {
                val desiredTextWidth = ceil(Layout.getDesiredWidth(currentText, paint)).toInt() +
                        compoundPaddingLeft + compoundPaddingRight
                if (desiredTextWidth > measuredWidth) {
                    val specSize = MeasureSpec.getSize(widthMeasureSpec)
                    val state = if (specSize in 1 until desiredTextWidth) {
                        MEASURED_STATE_TOO_SMALL
                    } else {
                        0
                    }
                    setMeasuredDimension(
                        resolveSizeAndState(desiredTextWidth, widthMeasureSpec, state),
                        measuredHeightAndState
                    )
                }
            }
        }
    }

    private inner class DragTouchListener : View.OnTouchListener {
        private var initX = 0
        private var initY = 0
        private var touchX = 0f
        private var touchY = 0f
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initX = layoutParams.x
                    initY = layoutParams.y
                    touchX = event.rawX
                    touchY = event.rawY
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    // Update internal position so manual drag is remembered
                    layoutParams.x = initX + (event.rawX - touchX).toInt()
                    layoutParams.y = initY + (event.rawY - touchY).toInt()
                    posX = layoutParams.x
                    posY = layoutParams.y
                    windowManager.updateViewLayout(overlayView, layoutParams)
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    // Save the updated position to SharedPreferences
                    val saved = OverlaySettings.load(this@FpsOverlayService)
                    val updated = saved.copy(posX = posX, posY = posY)
                    OverlaySettings.save(this@FpsOverlayService, updated)
                    return true
                }
            }
            return false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) {
            instance = null
        }
        isRunning = false
        isAutoStarted = false
        serviceScope.cancel()
        stopChoreographerMeasuring()
        stopSurfaceFlingerMeasuring()
        socThermalMonitor?.stop()
        socThermalMonitor = null
        ramMonitorJob?.cancel()
        ramMonitorJob = null
        FpsRecordingManager.endCurrentSession(this)
        batteryMonitor?.stop()
        batteryMonitor = null
        if (::overlayView.isInitialized) {
            try {
                windowManager.removeView(overlayView)
            } catch (e: Exception) {
                // Ignore
            }
        }
        floatingToggleButton?.hide()
        floatingToggleButton = null

        FpsTileService.updateTile(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
