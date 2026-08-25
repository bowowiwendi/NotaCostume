package com.notacostume.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class ScannerOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7C3AED")
        strokeWidth = 6f
        style = Paint.Style.STROKE
    }

    private val scanLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7C3AED")
        strokeWidth = 3f
    }

    private var scanLineY = 0f
    private var scanDirection = 1f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val cornerLen = 40f

        // Top-left
        canvas.drawLine(0f, 0f, cornerLen, 0f, cornerPaint)
        canvas.drawLine(0f, 0f, 0f, cornerLen, cornerPaint)
        // Top-right
        canvas.drawLine(w, 0f, w - cornerLen, 0f, cornerPaint)
        canvas.drawLine(w, 0f, w, cornerLen, cornerPaint)
        // Bottom-left
        canvas.drawLine(0f, h, cornerLen, h, cornerPaint)
        canvas.drawLine(0f, h, 0f, h - cornerLen, cornerPaint)
        // Bottom-right
        canvas.drawLine(w, h, w - cornerLen, h, cornerPaint)
        canvas.drawLine(w, h, w, h - cornerLen, cornerPaint)

        // Scan line animation
        canvas.drawLine(20f, scanLineY, w - 20f, scanLineY, scanLinePaint)

        scanLineY += scanDirection * 4f
        if (scanLineY > h - 20f) scanDirection = -1f
        if (scanLineY < 20f) scanDirection = 1f

        invalidate()
    }
}
