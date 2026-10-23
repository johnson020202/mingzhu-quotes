package com.mingzhu.core

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

/**
 * 底部三格操作条：换字体 / 分享海报 / 上一页、下一页由各版本自行决定。
 * 版本之间配色不同，所以颜色由外部传入。
 */
class ActionRow(
    context: Context,
    private val onFont: () -> Unit,
    private val onShare: () -> Unit,
    private val onExtra: (() -> Unit)? = null,
    private val extraLabel: String = "今天",
) : View(context) {

    var ink: Int = Color.BLACK
    var accent: Int = Color.RED
    var fill: Int = Color.TRANSPARENT
    var fontLabel: String = "字体"

    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typography2.body }
    private val m = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typography2.meta }
    private var idx = -1

    private fun zones(): List<RectF> {
        val pad = width * 0.05f
        val gap = width * 0.02f
        val n = if (onExtra != null) 3 else 2
        val total = width - pad * 2 - gap * (n - 1)
        val each = total / n
        val h = height * 0.58f
        val top = (height - h) / 2f
        return (0 until n).map { i ->
            val l = pad + (each + gap) * i
            RectF(l, top, l + each, top + h)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val z = zones()
        val labels = buildList {
            add(fontLabel)
            if (onExtra != null) add(extraLabel)
            add("分享海报")
        }
        z.forEachIndexed { i, r ->
            val primary = i == z.size - 1
            if (primary) {
                p.color = accent
                p.style = Paint.Style.FILL
                canvas.drawRoundRect(r, r.height() / 2f, r.height() / 2f, p)
                p.color = fill
            } else {
                p.color = ink
                p.alpha = 60
                p.style = Paint.Style.STROKE
                p.strokeWidth = 1.6f
                canvas.drawRoundRect(r, r.height() / 2f, r.height() / 2f, p)
                p.style = Paint.Style.FILL
                p.alpha = 220
                p.color = ink
            }
            p.textSize = width * 0.036f
            p.textAlign = Paint.Align.CENTER
            val label = labels.getOrElse(i) { "" }
            canvas.drawText(label, r.centerX(), r.centerY() + width * 0.013f, p)
            p.textAlign = Paint.Align.LEFT
            p.alpha = 255
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val z = zones()
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                idx = z.indexOfFirst { it.contains(e.x, e.y) }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (idx >= 0 && idx < z.size && z[idx].contains(e.x, e.y)) {
                    when {
                        idx == z.size - 1 -> onShare()
                        onExtra != null && idx == 1 -> onExtra()
                        else -> onFont()
                    }
                }
                idx = -1
                performClick()
                return true
            }
        }
        return super.onTouchEvent(e)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
