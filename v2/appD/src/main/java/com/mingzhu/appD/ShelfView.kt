package com.mingzhu.appD

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.mingzhu.core.Diary
import com.mingzhu.core.Library
import com.mingzhu.core.Typography2
import java.time.LocalDate

/**
 * 版本 D「书架」：每本书占一格书脊，颜色由书名散列决定；
 * 点书脊进入这本书的 20 句，今天读到的那一句会被标出来。
 */
class ShelfView(context: Context) : View(context) {

    private val wood = Color.parseColor("#F0E9DC")
    private val spine = Color.parseColor("#2F3A33")
    private val ink = Color.parseColor("#22211E")
    private val soft = Color.parseColor("#8A8271")
    private val gold = Color.parseColor("#B08A3E")
    private val today = LocalDate.now()
    private val todayBook = Diary.bookOn(today)

    private val pBody = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typography2.body }
    private val pMeta = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typography2.meta }
    private val pLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private val cells = ArrayList<Triple<RectF, Library.BookList, Int>>()
    var openBook: Int = -1
        private set

    private val palette = intArrayOf(
        Color.parseColor("#2F3A33"), Color.parseColor("#6B4034"), Color.parseColor("#3C4A5E"),
        Color.parseColor("#5A4356"), Color.parseColor("#4E5132"), Color.parseColor("#37525A"),
        Color.parseColor("#6E5732"), Color.parseColor("#4A3B2E"), Color.parseColor("#2E4A45"),
        Color.parseColor("#53404A"), Color.parseColor("#3A4030"), Color.parseColor("#45505F"),
    )

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawColor(wood)

        pBody.color = ink
        pBody.textSize = w * 0.075f
        canvas.drawText("书架", w * 0.08f, h * 0.085f, pBody)

        pMeta.textSize = w * 0.027f
        pMeta.color = soft
        pMeta.letterSpacing = 0.10f
        canvas.drawText("${Library.books.size} 本 · 每本 20 句", w * 0.08f, h * 0.115f, pMeta)
        pMeta.textAlign = Paint.Align.RIGHT
        pMeta.color = gold
        canvas.drawText("今天：${todayBook.title}", w * 0.92f, h * 0.115f, pMeta)
        pMeta.textAlign = Paint.Align.LEFT
        pMeta.letterSpacing = 0f

        if (openBook >= 0) {
            drawDetail(canvas, w, h)
            return
        }

        // 书脊网格：3 列
        val pad = w * 0.08f
        val top = h * 0.155f
        val cols = 3
        val gap = w * 0.022f
        val cw = (w - pad * 2 - gap * (cols - 1)) / cols
        val ch = h * 0.16f
        cells.clear()

        Library.books.forEachIndexed { i, b ->
            val r = i / cols
            val c = i % cols
            val left = pad + (cw + gap) * c
            val topY = top + (ch + gap) * r
            val rect = RectF(left, topY, left + cw, topY + ch)
            cells.add(Triple(rect, Library.BookList(b), i))

            val col = palette[i % palette.size]
            canvas.drawRoundRect(rect, w * 0.014f, w * 0.014f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col })

            // 书脊顶部的金色细线（今天的书）
            if (b.id == todayBook.id) {
                pLine.color = gold
                pLine.strokeWidth = w * 0.006f
                canvas.drawLine(left + cw * 0.12f, topY + w * 0.022f,
                    left + cw * 0.88f, topY + w * 0.022f, pLine)
            }

            // 竖排书名
            pBody.color = Color.parseColor("#F2EDE2")
            pBody.textSize = w * 0.040f
            val title = b.title
            val maxChars = minOf(title.length, 4)
            var ty = topY + ch * 0.30f
            for (k in 0 until maxChars) {
                canvas.drawText(title[k].toString(), left + cw * 0.32f, ty, pBody)
                ty += pBody.textSize * 1.15f
            }

            pMeta.color = Color.parseColor("#C9C0AC")
            pMeta.textSize = w * 0.021f
            canvas.drawText("${b.count} 句", left + cw * 0.12f, topY + ch * 0.88f, pMeta)
        }

        pMeta.color = soft
        pMeta.textSize = w * 0.026f
        canvas.drawText("点书脊翻开 · 金线标记今天的书", pad, h - h * 0.035f, pMeta)
    }

    private fun drawDetail(c: Canvas, w: Float, h: Float) {
        val b = Library.books[openBook]
        val pad = w * 0.08f
        val right = w * 0.92f
        val top = h * 0.155f

        pBody.color = ink
        pBody.textSize = w * 0.085f
        c.drawText(b.title, pad, top + w * 0.06f, pBody)

        pMeta.textSize = w * 0.028f
        pMeta.color = soft
        pMeta.letterSpacing = 0.05f
        c.drawText("${b.author} · ${b.nation} · ${b.year}", pad, top + w * 0.10f, pMeta)
        pMeta.letterSpacing = 0f

        pLine.color = Color.parseColor("#D3C9B4")
        pLine.strokeWidth = 1.2f
        c.drawLine(pad, top + w * 0.12f, right, top + w * 0.12f, pLine)

        // 20 句逐条
        var y = top + w * 0.175f
        val idxToday = run {
            val d = Diary.dayIndex(today)
            ((d % b.count) + b.count) % b.count
        }
        val isToday = b.id == todayBook.id
        b.lines.forEachIndexed { i, line ->
            if (y > h - h * 0.09f) return@forEachIndexed
            val hot = isToday && i == idxToday
            pMeta.textSize = w * 0.024f
            pMeta.color = if (hot) gold else soft
            c.drawText("%02d".format(i + 1), pad, y, pMeta)

            pBody.textSize = w * 0.036f
            pBody.color = if (hot) ink else Color.parseColor("#5C574C")
            val one = line.removeSuffix("。")
            val text = if (one.length > 22) one.take(22) + "…" else one
            c.drawText(text, pad + w * 0.075f, y, pBody)
            if (hot) {
                c.drawCircle(right - w * 0.01f, y - w * 0.012f, w * 0.008f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = gold })
            }
            y += w * 0.062f
        }

        pMeta.color = soft
        pMeta.textSize = w * 0.026f
        c.drawText("点空白处返回书架", pad, h - h * 0.035f, pMeta)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_UP) {
            if (openBook >= 0) {
                openBook = -1
            } else {
                cells.firstOrNull { it.first.contains(e.x, e.y) }?.let { openBook = it.third }
            }
            invalidate()
            performClick()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
