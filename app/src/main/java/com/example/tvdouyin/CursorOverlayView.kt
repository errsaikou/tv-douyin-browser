package com.example.tvdouyin

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.webkit.WebView

/**
 * Virtual mouse cursor overlay.
 *
 * Draws a mouse pointer icon on top of the WebView and supports:
 * - Smooth cursor movement controlled by phone touchpad
 * - Click simulation via MotionEvent injection into the WebView
 * - Auto-hide after 5 seconds of inactivity
 * - Shadow effect for cursor visibility on any background
 */
class CursorOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** The WebView to dispatch simulated click events to */
    var targetWebView: WebView? = null

    // Cursor position (screen coordinates)
    private var cursorX: Float = 0f
    private var cursorY: Float = 0f

    // Visibility state
    private var isCursorVisible: Boolean = false
    private var lastMoveTime: Long = 0
    private val autoHideDelay = 5000L // ms

    // Cursor size (dp-independent)
    private val cursorScale: Float = context.resources.displayMetrics.density * 10f

    // Sensitivity multiplier for phone touchpad movement
    var sensitivity: Float = 2.0f

    // Paint objects
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(80, 0, 0, 0)
        style = Paint.Style.FILL
    }

    // Reusable path for cursor arrow shape
    private val cursorPath = Path()

    init {
        // This overlay should not intercept any touch/focus events
        isClickable = false
        isFocusable = false
        isFocusableInTouchMode = false
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Initialize cursor at screen center
        cursorX = w / 2f
        cursorY = h / 2f
    }

    /**
     * Move cursor by relative displacement (called from phone touchpad).
     * @param dx horizontal displacement in pixels from phone
     * @param dy vertical displacement in pixels from phone
     */
    fun moveCursor(dx: Float, dy: Float) {
        isCursorVisible = true
        lastMoveTime = System.currentTimeMillis()

        cursorX = (cursorX + dx * sensitivity).coerceIn(0f, width.toFloat())
        cursorY = (cursorY + dy * sensitivity).coerceIn(0f, height.toFloat())

        invalidate()
    }

    /**
     * Simulate a mouse click at the current cursor position.
     * Dispatches ACTION_DOWN + ACTION_UP MotionEvents to the target WebView.
     */
    fun performClickAtCursor() {
        val webView = targetWebView ?: return

        isCursorVisible = true
        lastMoveTime = System.currentTimeMillis()

        val downTime = SystemClock.uptimeMillis()

        // ACTION_DOWN
        val downEvent = MotionEvent.obtain(
            downTime, downTime,
            MotionEvent.ACTION_DOWN,
            cursorX, cursorY, 0
        )
        webView.dispatchTouchEvent(downEvent)
        downEvent.recycle()

        // ACTION_UP (50ms after DOWN for a realistic click)
        val upEvent = MotionEvent.obtain(
            downTime, downTime + 50,
            MotionEvent.ACTION_UP,
            cursorX, cursorY, 0
        )
        webView.dispatchTouchEvent(upEvent)
        upEvent.recycle()

        invalidate()
    }

    /**
     * Manually show/hide cursor
     */
    fun setCursorVisible(visible: Boolean) {
        isCursorVisible = visible
        if (visible) lastMoveTime = System.currentTimeMillis()
        invalidate()
    }

    // ===================================================================
    // Drawing
    // ===================================================================

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!isCursorVisible) return

        // Auto-hide after inactivity
        if (System.currentTimeMillis() - lastMoveTime > autoHideDelay) {
            isCursorVisible = false
            return
        }

        // Draw shadow (offset by 2px)
        canvas.save()
        canvas.translate(cursorX + 2f, cursorY + 2f)
        buildCursorPath(cursorPath, cursorScale)
        canvas.drawPath(cursorPath, shadowPaint)
        canvas.restore()

        // Draw filled cursor
        canvas.save()
        canvas.translate(cursorX, cursorY)
        buildCursorPath(cursorPath, cursorScale)
        canvas.drawPath(cursorPath, fillPaint)
        canvas.drawPath(cursorPath, outlinePaint)
        canvas.restore()

        // Schedule periodic redraw for auto-hide countdown
        postInvalidateDelayed(1000)
    }

    /**
     * Build a classic arrow cursor path.
     * The arrow points to (0,0) at the top-left corner.
     */
    private fun buildCursorPath(path: Path, scale: Float) {
        path.reset()
        val s = scale / 10f // normalize

        path.moveTo(0f, 0f)                    // Tip (hot spot)
        path.lineTo(0f, s * 16f)               // Down left edge
        path.lineTo(s * 4.5f, s * 12.5f)       // Notch inward
        path.lineTo(s * 7.5f, s * 18f)         // Handle bottom-right
        path.lineTo(s * 10f, s * 16.5f)        // Handle top-right
        path.lineTo(s * 6.5f, s * 11f)         // Handle top-left
        path.lineTo(s * 11.5f, s * 10.5f)      // Right wing
        path.close()
    }
}
