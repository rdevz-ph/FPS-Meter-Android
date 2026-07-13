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
import com.rdevzph.fpsmeter.MainActivity
import com.rdevzph.fpsmeter.R
import com.rdevzph.fpsmeter.viewmodel.OverlaySettings
import kotlin.math.roundToInt

/**
 * Lightweight foreground service that draws an FPS counter overlay mimicking Samsung Perf Z style.
 */
class FpsOverlayService : Service() {

    companion object {
        const val CHANNEL_ID = "fps_overlay_channel"
        private const val NOTIF_ID = 1
        const val EXTRA_SETTINGS = "overlay_settings"

        // Intent extras for settings
        const val EXTRA_COLOR = "color"
        const val EXTRA_SIZE = "size"
        const val EXTRA_ALPHA = "alpha"
        const val EXTRA_POSITION_X = "pos_x"
        const val EXTRA_POSITION_Y = "pos_y"
        const val EXTRA_SHOW_MS = "show_ms"
        const val EXTRA_SHOW_TEMP = "show_temp"
        const val EXTRA_GRAVITY = "gravity"

        var isRunning = false
    }

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: TextView
    private lateinit var layoutParams: WindowManager.LayoutParams

    // Choreographer for frame timing
    private val choreographer = Choreographer.getInstance()
    private var frameCount = 0
    private var lastSampleTime = 0L
    private var currentFps = 0

    // Settings (with defaults)
    private var textColor = OverlaySettings.AUTO_COLOR
    private var textSizeSp = 14f
    private var overlayAlpha = 0.9f
    private var posX = 0
    private var posY = 100
    private var showMs = false
    private var showTemp = false
    private var overlayGravity = Gravity.TOP or Gravity.START

    private var batteryTemp: Float = 0f

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
                updateOverlayText()
            }

            choreographer.postFrameCallback(this)
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopSelf()
            return START_NOT_STICKY
        }

        intent?.let { applySettings(it) }

        if (!::overlayView.isInitialized) {
            startForeground(NOTIF_ID, buildNotification())
            createOverlayView()
            startMeasuring()
        } else {
            updateOverlayAppearance()
        }

        return START_STICKY
    }

    private fun applySettings(intent: Intent) {
        textColor = intent.getIntExtra(EXTRA_COLOR, OverlaySettings.AUTO_COLOR)
        textSizeSp = intent.getFloatExtra(EXTRA_SIZE, 14f)
        overlayAlpha = intent.getFloatExtra(EXTRA_ALPHA, 0.9f)
        posX = intent.getIntExtra(EXTRA_POSITION_X, 0)
        posY = intent.getIntExtra(EXTRA_POSITION_Y, 100)
        showMs = intent.getBooleanExtra(EXTRA_SHOW_MS, false)
        showTemp = intent.getBooleanExtra(EXTRA_SHOW_TEMP, false)
        overlayGravity = intent.getIntExtra(EXTRA_GRAVITY, Gravity.TOP or Gravity.START)
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

        // Pill shape background
        val shape = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 1000f // Large corner radius for pill shape
            setColor(Color.parseColor("#CC111111")) // Dark semi-transparent
        }

        overlayView = TextView(this).apply {
            background = shape
            textSize = textSizeSp
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            alpha = overlayAlpha
            setPadding(24, 8, 24, 8)
            setShadowLayer(2f, 0f, 0f, Color.BLACK)
            setOnTouchListener(DragTouchListener())
        }

        windowManager.addView(overlayView, layoutParams)
    }

    private fun updateOverlayText() {
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

        val labelColor = Color.parseColor("#00E5FF") // Cyan label color
        val valueColor = Color.WHITE

        val ssb = SpannableStringBuilder()

        // FPS segment
        ssb.append("FPS ", labelColor, StyleSpan(Typeface.BOLD))
        ssb.append("$fps", fpsValueColor, StyleSpan(Typeface.BOLD))

        // MS segment
        if (showMs && fps > 0) {
            ssb.append("  |  ", Color.GRAY)
            ssb.append("MS ", labelColor, StyleSpan(Typeface.BOLD))
            ssb.append("${(1000f / fps).roundToInt()}", valueColor, StyleSpan(Typeface.BOLD))
        }

        // Temp segment
        if (showTemp) {
            ssb.append("  |  ", Color.GRAY)
            ssb.append("TEMP ", labelColor, StyleSpan(Typeface.BOLD))
            ssb.append("${batteryTemp}°C", valueColor, StyleSpan(Typeface.BOLD))
        }

        overlayView.post {
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
        lastSampleTime = 0L
        frameCount = 0
        choreographer.postFrameCallback(frameCallback)
    }

    private fun buildNotification(): Notification {
        val tapIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stopIntent = PendingIntent.getService(this, 0, Intent(this, FpsOverlayService::class.java).apply { action = "STOP" }, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FPS Meter Active")
            .setContentText("Overlay is running")
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setContentIntent(tapIntent)
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
            }
            return false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        choreographer.removeFrameCallback(frameCallback)
        unregisterReceiver(batteryReceiver)
        if (::overlayView.isInitialized) windowManager.removeView(overlayView)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
