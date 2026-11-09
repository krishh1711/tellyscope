package app.tellyscope.helper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

class CursorView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        path.reset()
        val w = width.toFloat()
        val h = height.toFloat()
        path.moveTo(0f, 0f)
        path.lineTo(0f, h * 0.85f)
        path.lineTo(w * 0.35f, h * 0.65f)
        path.lineTo(w * 0.65f, h * 0.95f)
        path.lineTo(w * 0.85f, h * 0.8f)
        path.lineTo(w * 0.55f, h * 0.5f)
        path.lineTo(w * 0.85f, h * 0.5f)
        path.close()

        canvas.drawPath(path, paint)
        canvas.drawPath(path, strokePaint)
    }
}

class MouseOverlayService : AccessibilityService() {

    private var windowManager: WindowManager? = null
    private var cursorView: CursorView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var isVisible = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        initOverlay()
    }

    private fun initOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        cursorView = CursorView(this)

        val density = resources.displayMetrics.density
        val sizePx = (32 * density).toInt()

        layoutParams = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (resources.displayMetrics.widthPixels * 0.5f).toInt()
            y = (resources.displayMetrics.heightPixels * 0.5f).toInt()
        }

        try {
            windowManager?.addView(cursorView, layoutParams)
            isVisible = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setCursorPosition(xPercent: Float, yPercent: Float) {
        val wm = windowManager ?: return
        val view = cursorView ?: return
        val params = layoutParams ?: return

        val displayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels

        params.x = (xPercent * width).toInt().coerceIn(0, width)
        params.y = (yPercent * height).toInt().coerceIn(0, height)

        if (!isVisible) {
            try {
                wm.addView(view, params)
                isVisible = true
            } catch (_: Exception) {}
        } else {
            try {
                wm.updateViewLayout(view, params)
            } catch (_: Exception) {}
        }
    }

    fun performClickAt(xPercent: Float, yPercent: Float) {
        val displayMetrics = resources.displayMetrics
        val x = xPercent * displayMetrics.widthPixels
        val y = yPercent * displayMetrics.heightPixels

        val path = Path().apply {
            moveTo(x, y)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()

        dispatchGesture(gesture, null, null)
    }

    fun toggleVisibility() {
        val wm = windowManager ?: return
        val view = cursorView ?: return

        if (isVisible) {
            try {
                wm.removeView(view)
                isVisible = false
            } catch (_: Exception) {}
        } else {
            try {
                wm.addView(view, layoutParams)
                isVisible = true
            } catch (_: Exception) {}
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        if (isVisible && cursorView != null) {
            try {
                windowManager?.removeView(cursorView)
            } catch (_: Exception) {}
        }
    }

    companion object {
        var instance: MouseOverlayService? = null
    }
}
