package com.rdevzph.fpsmeter.overlay

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs
import kotlin.math.min

/**
 * Assistive floating draggable quick-toggle button that floats above all apps.
 * Tapping it instantly toggles the FPS overlay on/off without opening the app.
 */
class FloatingToggleButton(
    private val context: Context,
    private val onToggleClick: () -> Unit
) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val displayMetrics = context.resources.displayMetrics
    private var isAttached = false
    private var isOverlayActive = true

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
        if (isAttached) {
            try {
                windowManager.removeView(bubbleView)
                isAttached = false
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    fun setOverlayActive(active: Boolean) {
        isOverlayActive = active
        bubbleView.postInvalidate()
    }

    @SuppressLint("ViewConstructor")
    private inner class BubbleView(ctx: Context) : View(ctx) {
        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F141C")
            style = Paint.Style.FILL
            alpha = 230
        }

        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00E676")
            style = Paint.Style.STROKE
            strokeWidth = 3f * displayMetrics.density
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

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            val cx = w / 2f
            val cy = h / 2f
            val radius = min(cx, cy) - (2f * displayMetrics.density)

            // Background
            canvas.drawCircle(cx, cy, radius, bgPaint)

            // Border
            if (isOverlayActive) {
                canvas.drawCircle(cx, cy, radius, borderPaint)
                val textY = cy - ((textPaint.descent() + textPaint.ascent()) / 2f)
                canvas.drawText("FPS", cx, textY, textPaint)
            } else {
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
                        isDragging = true
                    }
                    if (isDragging) {
                        wmLayoutParams.x = initX + dx
                        wmLayoutParams.y = initY + dy
                        windowManager.updateViewLayout(bubbleView, wmLayoutParams)
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        onToggleClick()
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
