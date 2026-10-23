package com.lemon.mingzhu

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

/**
 * 底部三格操作条：上一篇 / 下一篇 / 分享海报，外加长按复制。
 * 三个版本共用，颜色由各版本传入，避免每套皮肤各写一遍。
 */
class ActionBar(
    context: Context,
    private val onPrev: () -> Unit,
    private val onNext: () -> Unit,
    private val onShare: () -> Unit,
) : View(context) {

    var label: String = "分享海报"

    var ink: Int = Color.BLACK
    var accent: Int = Color.RED
    var fill: Int = Color.TRANSPARENT

    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = UIFont.body }
    private val pm = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = UIFont.meta }
    private val pressed = RectF()
    private var downX = 0f
    private var downY = 0f

    private fun zones(): Triple<RectF, RectF, RectF> {
        val pad = width * 0.055f
        val gap = width * 0.025f
        val h = height * 0.62f
        val top = (height - h) / 2f
        val arrowW = width * 0.20f
        val shareW = width - pad * 2 - arrowW * 2 - gap * 2
        val r1 = RectF(pad, top, pad + arrowW, top + h)
        val r2 = RectF(r1.right + gap, top, r1.right + gap + arrowW, top + h)
        val r3 = RectF(r2.right + gap, top, r2.right + gap + shareW, top + h)
        return Triple(r1, r2, r3)
    }

    override fun onDraw(canvas: Canvas) {
        val (prev, next, share) = zones()
        val radius = share.height() / 2f

        pm.textSize = width * 0.030f
        pm.color = ink
        pm.alpha = 90
        pm.letterSpacing = 0.04f
        pm.textAlign = Paint.Align.CENTER
        canvas.drawText("←", prev.centerX(), prev.centerY() + width * 0.011f, pm)
        canvas.drawText("→", next.centerX(), next.centerY() + width * 0.011f, pm)
        pm.textAlign = Paint.Align.LEFT
        pm.alpha = 255
        pm.letterSpacing = 0f

        p.style = Paint.Style.FILL
        p.color = accent
        canvas.drawRoundRect(share, radius, radius, p)

        p.color = fill
        p.textSize = width * 0.040f
        p.textAlign = Paint.Align.CENTER
        canvas.drawText(label, share.centerX(), share.centerY() + width * 0.014f, p)
        p.textAlign = Paint.Align.LEFT
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val (prev, next, share) = zones()
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x; downY = e.y
                pressed.set(if (prev.contains(e.x, e.y)) prev else if (next.contains(e.x, e.y)) next else if (share.contains(e.x, e.y)) share else RectF())
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (pressed.contains(e.x, e.y) && pressed.width() > 0) {
                    when {
                        pressed == prev -> onPrev()
                        pressed == next -> onNext()
                        else -> onShare()
                    }
                }
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
