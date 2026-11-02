package com.mingzhu.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.mingzhu.core.Typography2

/**
 * 底部五格切换条：日历 / 书页 / 时间轴 / 书架 / 归档。
 * 每格一个图标 + 标签，选中项用强调色。
 */
class TabBar(
    context: Context,
    private val onPick: (Int) -> Unit,
) : View(context) {

    var index: Int = 0
        set(v) { field = v; invalidate() }

    private val labels = arrayOf("日历", "书页", "时间轴", "书架", "归档")
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typography2.body }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private var bounced = -1

    /** 由外部同步当前界面的主题色。 */
    var ink: Int = Color.DKGRAY
    var accent: Int = Color.RED
    var bg: Int = Color.WHITE
    var dark: Boolean = false

    private fun zones(): List<RectF> {
        val w = width.toFloat()
        val h = height.toFloat()
        val each = w / labels.size
        return labels.indices.map { i -> RectF(each * i, 0f, each * (i + 1), h) }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(bg)

        // 顶部细线
        line.color = ink
        line.alpha = 30
        line.strokeWidth = 1f
        canvas.drawLine(0f, 0.5f, width.toFloat(), 0.5f, line)

        val z = zones()
        val cx = width.toFloat() / labels.size
        z.forEachIndexed { i, r ->
            val on = i == index
            val c = if (on) accent else ink
            drawIcon(canvas, i, r.centerX(), r.top + height * 0.30f, cx * 0.20f, c, on)

            p.color = c
            p.alpha = if (on) 255 else 150
            p.textSize = cx * 0.20f
            p.textAlign = Paint.Align.CENTER
            canvas.drawText(labels[i], r.centerX(), r.top + height * 0.80f, p)
            p.textAlign = Paint.Align.LEFT
            p.alpha = 255
        }
    }

    /** 极简线条图标，避免引入资源文件。 */
    private fun drawIcon(c: Canvas, which: Int, x: Float, y: Float, s: Float, color: Int, filled: Boolean) {
        p.style = if (filled) Paint.Style.FILL else Paint.Style.STROKE
        p.color = color
        p.strokeWidth = s * 0.22f
        when (which) {
            0 -> { // 日历：方框 + 横线
                c.drawRect(x - s, y - s * 0.85f, x + s, y + s * 0.9f, p)
                c.drawLine(x - s, y - s * 0.2f, x + s, y - s * 0.2f, p)
                c.drawLine(x - s * 0.5f, y - s * 1.3f, x - s * 0.5f, y - s * 0.7f, p)
                c.drawLine(x + s * 0.5f, y - s * 1.3f, x + s * 0.5f, y - s * 0.7f, p)
            }
            1 -> { // 书页：两页
                val path = android.graphics.Path()
                path.moveTo(x, y - s * 0.9f)
                path.lineTo(x - s, y - s * 0.5f)
                path.lineTo(x - s, y + s * 0.9f)
                path.lineTo(x, y + s * 0.5f)
                path.close()
                c.drawPath(path, p)
                val path2 = android.graphics.Path()
                path2.moveTo(x, y - s * 0.9f)
                path2.lineTo(x + s, y - s * 0.5f)
                path2.lineTo(x + s, y + s * 0.9f)
                path2.lineTo(x, y + s * 0.5f)
                path2.close()
                c.drawPath(path2, p)
            }
            2 -> { // 时间轴：竖线 + 三个节点
                c.drawLine(x - s * 0.7f, y - s, x - s * 0.7f, y + s, p)
                listOf(-s * 0.6f, 0f, s * 0.6f).forEach { dy ->
                    c.drawCircle(x - s * 0.7f, y + dy, s * 0.26f, p)
                }
                c.drawLine(x - s * 0.2f, y - s * 0.6f, x + s, y - s * 0.6f, p)
                c.drawLine(x - s * 0.2f, y, x + s * 0.6f, y, p)
            }
            3 -> { // 书架：三根书脊
                c.drawRect(x - s, y - s, x - s * 0.35f, y + s, p)
                c.drawRect(x - s * 0.15f, y - s * 1.15f, x + s * 0.35f, y + s, p)
                c.drawRect(x + s * 0.55f, y - s * 0.8f, x + s, y + s, p)
            }
            else -> { // 归档：横条列表
                listOf(-s * 0.75f, 0f, s * 0.75f).forEach { dy ->
                    c.drawLine(x - s, y + dy, x + s, y + dy, p)
                }
            }
        }
        p.style = Paint.Style.FILL
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
                zones().forEachIndexed { i, r ->
                    if (r.contains(e.x, e.y)) {
                        if (i != index) {
                            index = i
                            onPick(i)
                        }
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
