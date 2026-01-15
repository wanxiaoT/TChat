package com.tchat.wanxiaot.junglehelper

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class OcrSelectionOverlayView(
    context: Context,
    private val onSelected: (Rect) -> Unit,
    private val onCancel: () -> Unit
) : FrameLayout(context) {

    private val selectionView = SelectionView(context, onSelected)

    init {
        addView(
            selectionView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )

        addView(
            TextView(context).apply {
                text = "取消"
                setTextColor(Color.WHITE)
                textSize = 14f
                setPadding(24, 16, 24, 16)
                setBackgroundColor(Color.parseColor("#99000000"))
                setOnClickListener { onCancel() }
            },
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = 24
                marginEnd = 24
            }
        )
    }

    private class SelectionView(
        context: Context,
        private val onSelected: (Rect) -> Unit
    ) : View(context) {

        private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#88000000")
            style = Paint.Style.FILL
        }

        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }

        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 42f
        }

        private var startX = 0f
        private var startY = 0f
        private var endX = 0f
        private var endY = 0f
        private var isSelecting = false

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)

            val hint = "拖动框选 OCR 区域"
            canvas.drawText(hint, 32f, 80f, textPaint)

            if (isSelecting) {
                val left = min(startX, endX)
                val top = min(startY, endY)
                val right = max(startX, endX)
                val bottom = max(startY, endY)
                canvas.drawRect(left, top, right, bottom, borderPaint)
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    startY = event.y
                    endX = startX
                    endY = startY
                    isSelecting = true
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!isSelecting) return true
                    endX = event.x
                    endY = event.y
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!isSelecting) return true
                    endX = event.x
                    endY = event.y
                    isSelecting = false
                    invalidate()

                    val left = min(startX, endX).toInt()
                    val top = min(startY, endY).toInt()
                    val right = max(startX, endX).toInt()
                    val bottom = max(startY, endY).toInt()

                    val minSizePx = 24
                    if (abs(right - left) >= minSizePx && abs(bottom - top) >= minSizePx) {
                        onSelected(Rect(left, top, right, bottom))
                    }
                    return true
                }
            }
            return super.onTouchEvent(event)
        }
    }
}

