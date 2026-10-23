package com.lemon.mingzhu

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View

/**
 * 方形印章：两行竖排的"明著摘录"，边缘做几处缺口，像真的盖上去的。
 * [style] 0 = 朱砂阳文，1 = 墨色阴文，2 = 描边空心。
 */
class SealView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var style: Int = 0
        set(v) { field = v; invalidate() }
    var text: String = "明著摘录"
        set(v) { field = v; invalidate() }
    var ink: Int = 0xFFB3352B.toInt()
        set(v) { field = v; invalidate() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }

    override fun onDraw(canvas: Canvas) {
        val s = minOf(width, height).toFloat()
        val pad = s * 0.04f
        val r = RectF(pad, pad, s - pad, s - pad)
        val radius = s * 0.06f
        paint.color = ink
        when (style) {
            0 -> { // 阳文：实心块 + 挖空字
                paint.style = Paint.Style.FILL
                canvas.drawRoundRect(r, radius, radius, paint)
                paint.color = 0x00000000
                paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
                drawGlyphs(canvas, r, false)
                paint.xfermode = null
            }
            1 -> { // 阴文：墨底白字
                paint.style = Paint.Style.FILL
                canvas.drawRoundRect(r, radius, radius, paint)
                paint.color = 0xFFFFFFFF.toInt()
                drawGlyphs(canvas, r, true)
            }
            else -> { // 空心
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = s * 0.045f
                canvas.drawRoundRect(r, radius, radius, paint)
                paint.style = Paint.Style.FILL
                drawGlyphs(canvas, r, true)
            }
        }
    }

    private fun drawGlyphs(canvas: Canvas, r: RectF, fillDirect: Boolean) {
        val cols = 2
        val perRow = (text.length + cols - 1) / cols
        val cell = minOf(r.width() / cols, r.height() / (perRow + 0.6f))
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = cell * 0.82f
        val startX = r.left + (r.width() - cell * cols) / 2f + cell / 2f
        val totalH = cell * perRow
        val startY = r.centerY() - totalH / 2f
        text.forEachIndexed { i, ch ->
            val col = i / perRow
            val row = i % perRow
            val x = startX + col * cell
            val y = startY + row * cell + cell * 0.78f
            canvas.drawText(ch.toString(), x, y, paint)
        }
        if (!fillDirect) paint.color = 0x00000000
    }
}
