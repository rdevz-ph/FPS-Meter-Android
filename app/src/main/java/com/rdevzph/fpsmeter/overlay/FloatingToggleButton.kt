package com.rdevzph.fpsmeter.overlay

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.min

/**
 * Assistive floating draggable quick-toggle button that floats above all apps.
 * Tapping it opens an assistive quick menu to start/stop FPS recording for the active game
 * and toggle HUD overlay visibility without having to open the main app.
 */
class FloatingToggleButton(
    private val context: Context,
    private val onToggleOverlay: () -> Unit,
    private val onStartRecording: () -> Boolean,
    private val onStopRecording: () -> Unit,
    private val isRecordingActive: () -> Boolean,
    private val isOverlayVisible: () -> Boolean,
    private val getActiveGameName: () -> String,
    private val isGameRecordingConfigured: () -> Boolean
) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val displayMetrics = context.resources.displayMetrics
    private var isAttached = false

    private val buttonSize = (46 * displayMetrics.density).toInt()
    private val wmLayoutParams = WindowManager.LayoutParams(
        buttonSize,
        buttonSize,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = 16
        y = (displayMetrics.heightPixels * 0.4f).toInt()
    }

    private val bubbleView = BubbleView(context)

    // Menu overlay state
    private var menuView: View? = null
    private var isMenuAttached = false
    private var menuGameTitle: TextView? = null
    private var menuStatusBadge: TextView? = null
    private var menuRecordBtn: TextView? = null
    private var menuOverlayBtn: TextView? = null
    private var lastRecordedActive: Boolean? = null
    private var lastOverlayActive: Boolean? = null

    fun show() {
        if (!isAttached) {
            try {
                windowManager.addView(bubbleView, wmLayoutParams)
                isAttached = true
            } catch (e: Exception) {
                // Ignore if already added or missing permission
            }
        }
    }

    fun hide() {
        hideMenu()
        if (isAttached) {
            try {
                windowManager.removeView(bubbleView)
                isAttached = false
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    fun updateState() {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            performUpdateState()
        } else {
            bubbleView.post { performUpdateState() }
        }
    }

    private fun performUpdateState() {
        val currentRec = isRecordingActive()
        val currentOverlay = isOverlayVisible()
        if (currentRec != lastRecordedActive || currentOverlay != lastOverlayActive) {
            lastRecordedActive = currentRec
            lastOverlayActive = currentOverlay
            bubbleView.postInvalidate()
        }
        if (isMenuAttached) {
            refreshMenuContent()
        }
    }

    fun toggleMenu() {
        if (isMenuAttached) {
            hideMenu()
        } else {
            showMenu()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun showMenu() {
        if (isMenuAttached) {
            refreshMenuContent()
            return
        }

        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val menuWidth = (240 * displayMetrics.density).toInt()

        val isBubbleOnLeft = (wmLayoutParams.x + buttonSize / 2) < (screenWidth / 2)
        val menuX = if (isBubbleOnLeft) {
            (wmLayoutParams.x + buttonSize + (8 * displayMetrics.density).toInt())
                .coerceAtMost(screenWidth - menuWidth - 8)
        } else {
            (wmLayoutParams.x - menuWidth - (8 * displayMetrics.density).toInt())
                .coerceAtLeast(8)
        }

        val menuY = (wmLayoutParams.y - (20 * displayMetrics.density).toInt())
            .coerceIn((40 * displayMetrics.density).toInt(), screenHeight - (280 * displayMetrics.density).toInt())

        val menuLayoutParams = WindowManager.LayoutParams(
            menuWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = menuX
            y = menuY
        }

        val view = buildMenuView()

        try {
            windowManager.addView(view, menuLayoutParams)
            menuView = view
            isMenuAttached = true
        } catch (e: Exception) {
            // WindowManager permission or invalid token
        }
    }

    fun hideMenu() {
        if (isMenuAttached && menuView != null) {
            try {
                windowManager.removeView(menuView)
            } catch (e: Exception) {
                // Ignore
            }
            menuView = null
            menuGameTitle = null
            menuStatusBadge = null
            menuRecordBtn = null
            menuOverlayBtn = null
            isMenuAttached = false
        }
    }

    private fun refreshMenuContent() {
        if (!isMenuAttached || menuView == null) return
        val recording = isRecordingActive()
        val overlayActive = isOverlayVisible()
        val gameName = getActiveGameName()
        val isConfigured = isGameRecordingConfigured()
        val dp = displayMetrics.density

        menuGameTitle?.text = gameName

        menuStatusBadge?.apply {
            if (recording) {
                text = "REC"
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#DC2626"))
                    cornerRadius = 8 * dp
                }
            } else if (isConfigured) {
                text = "READY"
                setTextColor(Color.parseColor("#4ADE80"))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#143220"))
                    cornerRadius = 8 * dp
                }
            } else {
                text = "REC OFF"
                setTextColor(Color.parseColor("#94A3B8"))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#1E293B"))
                    cornerRadius = 8 * dp
                }
            }
        }

        menuRecordBtn?.apply {
            if (recording) {
                text = "Stop Recording"
                setTextColor(Color.parseColor("#FCA5A5"))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#3B151E"))
                    cornerRadius = 10 * dp
                    setStroke((1 * dp).toInt(), Color.parseColor("#EF4444"))
                }
            } else if (isConfigured) {
                text = "Start Recording"
                setTextColor(Color.parseColor("#F87171"))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#1E293B"))
                    cornerRadius = 10 * dp
                    setStroke((1 * dp).toInt(), Color.parseColor("#DC2626"))
                }
            } else {
                text = "Recording Not Enabled"
                setTextColor(Color.parseColor("#64748B"))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#151A24"))
                    cornerRadius = 10 * dp
                    setStroke((1 * dp).toInt(), Color.parseColor("#253042"))
                }
            }
        }

        menuOverlayBtn?.apply {
            if (overlayActive) {
                text = "Hide Overlay"
                setTextColor(Color.parseColor("#94A3B8"))
            } else {
                text = "Show Overlay"
                setTextColor(Color.parseColor("#38BDF8"))
            }
        }

        menuRecordBtn?.invalidate()
        menuStatusBadge?.invalidate()
        menuGameTitle?.invalidate()
        menuView?.invalidate()
    }

    private fun buildMenuView(): View {
        val dp = displayMetrics.density
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (12 * dp).toInt()
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#121722"))
                cornerRadius = 16 * dp
                setStroke((1.5f * dp).toInt(), Color.parseColor("#2A3649"))
            }
            elevation = 12 * dp
        }

        val recording = isRecordingActive()
        val overlayActive = isOverlayVisible()
        val gameName = getActiveGameName()
        val isConfigured = isGameRecordingConfigured()

        // 1. Header Row (Game icon + Name + Recording badge)
        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val gameTitle = TextView(context).apply {
            text = gameName
            textSize = 13f
            setTextColor(Color.parseColor("#F1F5F9"))
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        menuGameTitle = gameTitle
        headerRow.addView(gameTitle)

        val statusBadge = TextView(context).apply {
            val hPad = (6 * dp).toInt()
            val vPad = (2 * dp).toInt()
            setPadding(hPad, vPad, hPad, vPad)
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            if (recording) {
                text = "REC"
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#DC2626"))
                    cornerRadius = 8 * dp
                }
            } else if (isConfigured) {
                text = "READY"
                setTextColor(Color.parseColor("#4ADE80"))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#143220"))
                    cornerRadius = 8 * dp
                }
            } else {
                text = "REC OFF"
                setTextColor(Color.parseColor("#94A3B8"))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#1E293B"))
                    cornerRadius = 8 * dp
                }
            }
        }
        menuStatusBadge = statusBadge
        headerRow.addView(statusBadge)
        root.addView(headerRow)

        // Divider
        val divider = View(context).apply {
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (1 * dp).toInt())
            lp.topMargin = (10 * dp).toInt()
            lp.bottomMargin = (10 * dp).toInt()
            layoutParams = lp
            setBackgroundColor(Color.parseColor("#1E293B"))
        }
        root.addView(divider)

        // 2. Start / Stop Recording Button
        val recordBtn = TextView(context).apply {
            val padH = (12 * dp).toInt()
            val padV = (10 * dp).toInt()
            setPadding(padH, padV, padH, padV)
            textSize = 12f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD

            if (recording) {
                text = "Stop Recording"
                setTextColor(Color.parseColor("#FCA5A5"))
                val normalBg = GradientDrawable().apply {
                    setColor(Color.parseColor("#3B151E"))
                    cornerRadius = 10 * dp
                    setStroke((1 * dp).toInt(), Color.parseColor("#EF4444"))
                }
                background = normalBg
            } else if (isConfigured) {
                text = "Start Recording"
                setTextColor(Color.parseColor("#F87171"))
                val normalBg = GradientDrawable().apply {
                    setColor(Color.parseColor("#1E293B"))
                    cornerRadius = 10 * dp
                    setStroke((1 * dp).toInt(), Color.parseColor("#DC2626"))
                }
                background = normalBg
            } else {
                text = "Recording Not Enabled"
                setTextColor(Color.parseColor("#64748B"))
                val normalBg = GradientDrawable().apply {
                    setColor(Color.parseColor("#151A24"))
                    cornerRadius = 10 * dp
                    setStroke((1 * dp).toInt(), Color.parseColor("#253042"))
                }
                background = normalBg
            }

            setOnClickListener {
                if (isRecordingActive()) {
                    onStopRecording()
                    hideMenu()
                } else {
                    if (!isGameRecordingConfigured()) {
                        android.widget.Toast.makeText(
                            context,
                            "Enable recording for this game in Games list first",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        val started = onStartRecording()
                        if (started) {
                            hideMenu()
                        }
                    }
                }
                updateState()
            }
        }
        menuRecordBtn = recordBtn
        root.addView(recordBtn)

        // 3. Toggle Overlay Button
        val overlayBtn = TextView(context).apply {
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = (8 * dp).toInt()
            layoutParams = lp

            val padH = (12 * dp).toInt()
            val padV = (9 * dp).toInt()
            setPadding(padH, padV, padH, padV)
            textSize = 12f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD

            if (overlayActive) {
                text = "Hide Overlay"
                setTextColor(Color.parseColor("#94A3B8"))
            } else {
                text = "Show Overlay"
                setTextColor(Color.parseColor("#38BDF8"))
            }

            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = 10 * dp
                setStroke((1 * dp).toInt(), Color.parseColor("#334155"))
            }

            setOnClickListener {
                onToggleOverlay()
                updateState()
            }
        }
        menuOverlayBtn = overlayBtn
        root.addView(overlayBtn)

        // 4. Close Row
        val closeBtn = TextView(context).apply {
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = (6 * dp).toInt()
            layoutParams = lp

            setPadding(0, (6 * dp).toInt(), 0, (2 * dp).toInt())
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#64748B"))
            text = "Dismiss"
            setOnClickListener {
                hideMenu()
            }
        }
        root.addView(closeBtn)

        return root
    }

    @SuppressLint("ViewConstructor")
    private inner class BubbleView(ctx: Context) : View(ctx) {
        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F141C")
            style = Paint.Style.FILL
            alpha = 235
        }

        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00E676")
            style = Paint.Style.STROKE
            strokeWidth = 3f * displayMetrics.density
        }

        private val recordingBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF1744")
            style = Paint.Style.STROKE
            strokeWidth = 3.5f * displayMetrics.density
        }

        private val inactiveBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#557788")
            style = Paint.Style.STROKE
            strokeWidth = 2f * displayMetrics.density
        }

        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00E676")
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            textSize = 12f * displayMetrics.scaledDensity
            textAlign = Paint.Align.CENTER
        }

        private val recordingTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF1744")
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            textSize = 11.5f * displayMetrics.scaledDensity
            textAlign = Paint.Align.CENTER
        }

        private val inactiveTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#8899A6")
            typeface = Typeface.create("sans-serif-bold", Typeface.BOLD)
            textSize = 11f * displayMetrics.scaledDensity
            textAlign = Paint.Align.CENTER
        }

        private var initX = 0
        private var initY = 0
        private var touchX = 0f
        private var touchY = 0f
        private var isDragging = false
        private var lastTapTime = 0L
        private val DOUBLE_TAP_TIMEOUT = 320L

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            val cx = w / 2f
            val cy = h / 2f
            val radius = min(cx, cy) - (2.5f * displayMetrics.density)

            // Background
            canvas.drawCircle(cx, cy, radius, bgPaint)

            val recording = isRecordingActive()
            val overlayActive = isOverlayVisible()

            if (recording) {
                // Glowing RED border for recording mode
                canvas.drawCircle(cx, cy, radius, recordingBorderPaint)
                val textY = cy - ((recordingTextPaint.descent() + recordingTextPaint.ascent()) / 2f)
                canvas.drawText("REC", cx, textY, recordingTextPaint)
            } else if (overlayActive) {
                // GREEN border for active overlay
                canvas.drawCircle(cx, cy, radius, borderPaint)
                val textY = cy - ((textPaint.descent() + textPaint.ascent()) / 2f)
                canvas.drawText("FPS", cx, textY, textPaint)
            } else {
                // GREY border for hidden overlay
                canvas.drawCircle(cx, cy, radius, inactiveBorderPaint)
                val textY = cy - ((inactiveTextPaint.descent() + inactiveTextPaint.ascent()) / 2f)
                canvas.drawText("OFF", cx, textY, inactiveTextPaint)
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initX = wmLayoutParams.x
                    initY = wmLayoutParams.y
                    touchX = event.rawX
                    touchY = event.rawY
                    isDragging = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (abs(dx) > 10 || abs(dy) > 10) {
                        if (!isDragging) {
                            hideMenu()
                        }
                        isDragging = true
                    }
                    if (isDragging) {
                        wmLayoutParams.x = initX + dx
                        wmLayoutParams.y = initY + dy
                        try {
                            windowManager.updateViewLayout(bubbleView, wmLayoutParams)
                        } catch (e: Exception) {
                            // View might not be attached
                        }
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        val now = System.currentTimeMillis()
                        if (now - lastTapTime < DOUBLE_TAP_TIMEOUT) {
                            // Double tap directly toggles overlay HUD
                            lastTapTime = 0L
                            hideMenu()
                            onToggleOverlay()
                            updateState()
                        } else {
                            // Single tap opens/closes the assistive menu
                            lastTapTime = now
                            toggleMenu()
                        }
                    } else {
                        snapToNearestEdge()
                    }
                    return true
                }
            }
            return false
        }

        private fun snapToNearestEdge() {
            val screenWidth = displayMetrics.widthPixels
            val targetX = if ((wmLayoutParams.x + buttonSize / 2) < (screenWidth / 2)) {
                16
            } else {
                screenWidth - buttonSize - 16
            }

            val startX = wmLayoutParams.x
            val animator = ValueAnimator.ofInt(startX, targetX).apply {
                duration = 200
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    wmLayoutParams.x = it.animatedValue as Int
                    if (isAttached) {
                        try {
                            windowManager.updateViewLayout(bubbleView, wmLayoutParams)
                        } catch (e: Exception) {
                            // View might have been detached
                        }
                    }
                }
            }
            animator.start()
        }
    }
}
