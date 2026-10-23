package com.mingzhu.appC

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.animation.ValueAnimator
import com.mingzhu.core.Diary
import com.mingzhu.core.DayEntry
import com.mingzhu.core.Typography2
import com.mingzhu.core.Wrap
import java.time.LocalDate

/**
 * 版本 C「时间轴」：从今天往上倒序排列，每天一个节点，
 * 竖向滚动翻看历史，点节点展开当天的书与句。
 */
class TimelineView(context: Context) : View(context) {

    private val bg = Color.parseColor("#12151A")
    private val card = Color.parseColor("#1B2027")
    private val cream = Color.parseColor("#EDE6D6")
    private val soft = Color.parseColor("#7C8492")
    private val gold = Color.parseColor("#D8A657")
    private val rail = Color.parseColor("#2A313B")

    private val pBody = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typography2.body }
    private val pMeta = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typography2.meta }
    private val pLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private val today: LocalDate = LocalDate.now()
    private val entries: List<DayEntry> = Diary.recent(today, 60)
    private var expanded = 0
    private var scroll = 0f
    private var maxScroll = 0f
    private var downY = 0f
    private var lastY = 0f
    private var dragging = false
    private var animator: ValueAnimator? = null

    private val rowH: Float get() = width * 0.30f

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawColor(bg)

        // 顶部标题
        pBody.color = cream
        pBody.textSize = w * 0.072f
        canvas.drawText("时间轴", w * 0.09f, h * 0.085f, pBody)
        pMeta.textSize = w * 0.027f
        pMeta.color = soft
        pMeta.letterSpacing = 0.14f
        canvas.drawText("TIMELINE · 每日一书", w * 0.09f, h * 0.115f, pMeta)
        pMeta.letterSpacing = 0f

        val railX = w * 0.085f
        val top = h * 0.155f
        val right = w * 0.94f

        var y = top - scroll
        entries.forEachIndexed { i, e ->
            val hh = if (i == expanded) rowH * 1.55f else rowH
            if (y + hh > top - rowH && y < h) {
                drawRow(canvas, e, i, w, y, hh, railX, right)
            }
            y += hh
        }
        maxScroll = (y + scroll - (h - h * 0.03f)).coerceAtLeast(0f)

        // 竖轨
        pLine.color = rail
        pLine.strokeWidth = 2f
        canvas.drawLine(railX, top, railX, h, pLine)
    }

    private fun drawRow(c: Canvas, e: DayEntry, i: Int, w: Float, y: Float, hh: Float, railX: Float, right: Float) {
        val isOpen = i == expanded
        val cx = w * 0.175f
        val cy = y + w * 0.085f

        // 节点
        if (isOpen) {
            c.drawCircle(railX, cy, w * 0.020f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = gold })
        } else {
            c.drawCircle(railX, cy, w * 0.011f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = rail })
            pLine.color = if (e.isToday) gold else soft
            pLine.strokeWidth = 1.6f
            c.drawCircle(railX, cy, w * 0.018f, pLine)
        }

        // 日期
        pMeta.textSize = w * 0.028f
        pMeta.color = if (e.isToday) gold else soft
        pMeta.letterSpacing = 0.10f
        val label = if (e.isToday) "今天 · ${"%02d".format(e.date.monthValue)}.${"%02d".format(e.date.dayOfMonth)}"
                    else "${"%02d".format(e.date.monthValue)}.${"%02d".format(e.date.dayOfMonth)}"
        c.drawText(label, cx, y + w * 0.038f, pMeta)
        pMeta.letterSpacing = 0f

        // 书名
        pBody.color = cream
        pBody.textSize = w * 0.055f
        c.drawText(e.book.title, cx, y + w * 0.090f, pBody)

        pMeta.textSize = w * 0.026f
        pMeta.color = soft
        c.drawText("${e.book.author} · ${e.book.nation}", cx, y + w * 0.125f, pMeta)

        // 摘要行
        if (!isOpen) {
            pBody.textSize = w * 0.034f
            pBody.color = Color.parseColor("#A9B0BC")
            val one = Wrap.trimEnd(e.quote)
            val text = if (one.length > 26) one.take(26) + "…" else one
            c.drawText(text, cx, y + w * 0.180f, pBody)
        } else {
            pBody.textSize = w * 0.050f
            pBody.color = cream
            val lines = Wrap.split(Wrap.trimEnd(e.quote), pBody, right - cx)
            var ly = y + w * 0.195f
            lines.forEach { l ->
                c.drawText(l, cx, ly, pBody)
                ly += pBody.textSize * 1.60f
            }
            pMeta.textSize = w * 0.025f
            pMeta.color = gold
            c.drawText("第 ${e.quoteIndex + 1} / ${e.book.count} 句", cx, ly + w * 0.010f, pMeta)
        }

        // 分隔线
        pLine.color = Color.parseColor("#242A33")
        pLine.strokeWidth = 1f
        c.drawLine(cx, y + hh - w * 0.02f, right, y + hh - w * 0.02f, pLine)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downY = e.y
                lastY = e.y
                dragging = false
                animator?.cancel()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = lastY - e.y
                if (kotlin.math.abs(e.y - downY) > height * 0.012f) dragging = true
                scroll = (scroll + dy).coerceIn(0f, maxScroll)
                lastY = e.y
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!dragging) {
                    // 点哪一行就展开哪一行
                    val top = height * 0.155f
                    var y = top - scroll
                    entries.forEachIndexed { i, _ ->
                        val hh = if (i == expanded) rowH * 1.55f else rowH
                        if (e.y >= y && e.y < y + hh) {
                            expanded = i
                            invalidate()
                        }
                        y += hh
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
