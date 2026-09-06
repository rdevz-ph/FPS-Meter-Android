package com.rdevzph.fpsmeter.overlay

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
import androidx.core.app.NotificationCompat
import android.content.pm.PackageManager
import com.rdevzph.fpsmeter.MainActivity
import com.rdevzph.fpsmeter.R
import com.rdevzph.fpsmeter.model.FpsProvider
import com.rdevzph.fpsmeter.model.GraphicsApi
import com.rdevzph.fpsmeter.viewmodel.OverlaySettings
import rikka.shizuku.Shizuku
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

        var isRunning = false
        var isAutoStarted = false
    }

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: TextView
    private lateinit var layoutParams: WindowManager.LayoutParams
    private var floatingToggleButton: FloatingToggleButton? = null

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
    private var overlayAlpha = 0.9f
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

    private var batteryTemp: Float = 0f
    private var socTemp: Float = 0f
    private var cpuTemp: Float = 0f
    private var gpuTemp: Float = 0f
    private var socThermalMonitor: SocThermalMonitor? = null

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                val temp = it.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
                batteryTemp = temp / 10f
            }
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
            }

            choreographer.postFrameCallback(this)
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        // Load initial settings from SharedPreferences
        val saved = OverlaySettings.load(this)
        textColor = saved.color
        textSizeSp = saved.textSizeSp
        overlayAlpha = saved.alpha
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

        updateSocThermalMonitoring()

        FpsTileService.updateTile(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
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

    private fun syncFloatingToggleButton() {
        if (floatingToggleEnabled) {
            if (floatingToggleButton == null) {
                floatingToggleButton = FloatingToggleButton(this) {
                    toggleOverlayVisibility()
                }
            }
            floatingToggleButton?.setOverlayActive(isOverlayVisible)
            floatingToggleButton?.show()
        } else {
            floatingToggleButton?.hide()
            floatingToggleButton = null
        }
    }

    private fun toggleOverlayVisibility() {
        isOverlayVisible = !isOverlayVisible
        if (::overlayView.isInitialized) {
            overlayView.visibility = if (isOverlayVisible) View.VISIBLE else View.GONE
        }
        floatingToggleButton?.setOverlayActive(isOverlayVisible)

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
    }

    private fun updateOverlayAppearance() {
        if (::overlayView.isInitialized) {
            overlayView.post {
                overlayView.textSize = textSizeSp
                overlayView.alpha = overlayAlpha
                
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

        // Rounded HUD background
        val shape = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 32f // Smooth rounded corners for single or multi-line HUD
            setColor(Color.parseColor("#CC111111")) // Dark semi-transparent
        }

        overlayView = TextView(this).apply {
            background = shape
            textSize = textSizeSp
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            alpha = overlayAlpha
            gravity = Gravity.CENTER
            setLineSpacing(6f, 1f)
            setPadding(28, 12, 28, 12)
            setShadowLayer(2f, 0f, 0f, Color.BLACK)
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

        val hasApi = fpsProvider == FpsProvider.SURFACE_FLINGER && showGraphicsApi && detectedGraphicsApi != GraphicsApi.UNKNOWN
        val hasMs = showMs && fps > 0
        val hasCpu = showCpuTemp
        val hasGpu = showGpuTemp
        val hasSoc = showSocTemp
        val hasBatt = showTemp

        val extraOverlayCount = (if (hasApi) 1 else 0) +
                (if (hasMs) 1 else 0) +
                (if (hasCpu) 1 else 0) +
                (if (hasGpu) 1 else 0) +
                (if (hasSoc) 1 else 0) +
                (if (hasBatt) 1 else 0)

        val hasThermals = hasCpu || hasGpu || hasSoc || hasBatt
        // Only wrap to next line if more than 3 extra overlays are enabled (> 3); stay horizontal for 1-3 overlays
        val useNextLine = extraOverlayCount > 3 && hasThermals

        val ssb = SpannableStringBuilder()

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
            fun appendThermal(tag: String, value: String) {
                if (!isFirstThermalOnLine) {
                    ssb.append("  |  ", Color.GRAY)
                }
                ssb.append("$tag ", labelColor, StyleSpan(Typeface.BOLD))
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
            if (hasBatt) {
                appendThermal("BATT", "${batteryTemp}°C")
            }
        }

        val shape = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = if (useNextLine) 32f else 1000f
            setColor(Color.parseColor("#CC111111"))
        }

        overlayView.post {
            overlayView.background = shape
            if (useNextLine) {
                overlayView.setPadding(28, 12, 28, 12)
            } else {
                overlayView.setPadding(24, 8, 24, 8)
            }
            overlayView.text = ssb
        }
    }

    private fun SpannableStringBuilder.append(text: String, color: Int, style: Any? = null): SpannableStringBuilder {
        val start = length
        append(text)
        setSpan(ForegroundColorSpan(color), start, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
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
        }
    }

    private fun stopChoreographerMeasuring() {
        if (isChoreographerMeasuring) {
            choreographer.removeFrameCallback(frameCallback)
            isChoreographerMeasuring = false
        }
    }

    private fun startSurfaceFlingerMeasuring() {
        if (surfaceFlingerFpsMonitor == null) {
            surfaceFlingerFpsMonitor = SurfaceFlingerFpsMonitor(
                onFpsUpdate = { fps, _, api, _ ->
                    currentFps = fps
                    detectedGraphicsApi = api
                    if (isOverlayVisible) {
                        updateOverlayText()
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

    private fun buildNotification(): Notification {
        val tapIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stopIntent = PendingIntent.getService(this, 1, Intent(this, FpsOverlayService::class.java).apply { action = ACTION_STOP }, PendingIntent.FLAG_IMMUTABLE)
        val toggleIntent = PendingIntent.getService(this, 2, Intent(this, FpsOverlayService::class.java).apply { action = ACTION_TOGGLE_VISIBILITY }, PendingIntent.FLAG_IMMUTABLE)

        val toggleLabel = if (isOverlayVisible) "Hide" else "Show"
        val statusText = if (isOverlayVisible) "Overlay visible & measuring" else "Overlay hidden (minimized)"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FPS Meter")
            .setContentText(statusText)
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setContentIntent(tapIntent)
            .addAction(0, toggleLabel, toggleIntent)
            .addAction(0, "Stop", stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
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
        isRunning = false
        isAutoStarted = false
        stopChoreographerMeasuring()
        stopSurfaceFlingerMeasuring()
        socThermalMonitor?.stop()
        socThermalMonitor = null
        unregisterReceiver(batteryReceiver)
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
