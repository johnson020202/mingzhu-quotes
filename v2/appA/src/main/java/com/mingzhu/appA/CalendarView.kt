package com.mingzhu.appA

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
import java.time.YearMonth

/**
 * 版本 A「日历」：上方是当月月历，点任意一天就翻到那天的书目与主推句；
 * 下方显示选中当天的书、句和出处。历史日期不用联网即可回看。
 */
class CalendarView(context: Context) : View(context) {

    private val paper = Color.parseColor("#FBF8F2")
    private val ink = Color.parseColor("#20201D")
    private val soft = Color.parseColor("#8A867C")
    private val accent = Color.parseColor("#3E6B57")
    private val dot = Color.parseColor("#B8AE98")
    private val today = Color.parseColor("#3E6B57")

    private val pBody = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typography2.body }
    private val pMeta = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typography2.meta }
    private val pLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.4f
    }

    var selected: LocalDate = LocalDate.now()
    var onSelect: ((LocalDate) -> Unit)? = null

    private var month: YearMonth = YearMonth.now()
    private val cells = ArrayList<RectF>()

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawColor(paper)

        val pad = w * 0.075f
        val top = h * 0.055f

        // 月份标题
        pBody.color = ink
        pBody.textSize = w * 0.072f
        canvas.drawText("${month.year}", pad, top + w * 0.072f, pBody)
        pBody.textSize = w * 0.042f
        pBody.color = soft
        canvas.drawText("年 ${month.monthValue} 月", pad + w * 0.20f, top + w * 0.072f, pBody)

        // 左右切换月份
        pMeta.textSize = w * 0.040f
        pMeta.color = soft
        pMeta.textAlign = Paint.Align.RIGHT
        canvas.drawText("‹", w - pad - w * 0.08f, top + w * 0.072f, pMeta)
        canvas.drawText("›", w - pad, top + w * 0.072f, pMeta)
        pMeta.textAlign = Paint.Align.LEFT

        // 星期表头
        val weekTop = top + w * 0.115f
        val weekNames = arrayOf("一", "二", "三", "四", "五", "六", "日")
        val colW = (w - pad * 2) / 7f
        pMeta.textSize = w * 0.028f
        pMeta.color = subtle()
        pMeta.textAlign = Paint.Align.CENTER
        weekNames.forEachIndexed { i, s ->
            canvas.drawText(s, pad + colW * (i + 0.5f), weekTop, pMeta)
        }
        pMeta.textAlign = Paint.Align.LEFT

        // 网格
        val gridTop = weekTop + h * 0.022f
        val cellH = h * 0.062f
        val grid = Diary.monthGrid(month.year, month.monthValue)
        cells.clear()

        grid.forEachIndexed { idx, date ->
            val r = idx / 7
            val c = idx % 7
            val left = pad + colW * c
            val topY = gridTop + cellH * r
            val rect = RectF(left, topY, left + colW, topY + cellH)
            cells.add(rect)
            if (date == null) return@forEachIndexed

            val centerX = rect.centerX()
            val centerY = rect.centerY()

            if (date == selected) {
                canvas.drawCircle(centerX, centerY, colW * 0.36f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent })
            } else if (date == LocalDate.now()) {
                pLine.color = accent
                pLine.strokeWidth = 1.8f
                canvas.drawCircle(centerX, centerY, colW * 0.36f, pLine)
            }

            pBody.textSize = w * 0.038f
            pBody.color = if (date == selected) paper else ink
            pBody.textAlign = Paint.Align.CENTER
            canvas.drawText(date.dayOfMonth.toString(), centerX, centerY + w * 0.013f, pBody)
            pBody.textAlign = Paint.Align.LEFT

            // 该日所属书目的分类点
            if (date != selected) {
                val book = Diary.bookOn(date)
                val hue = colorFor(book.id)
                canvas.drawCircle(centerX, centerY + colW * 0.26f, w * 0.006f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = hue })
            }
        }

        // 选中日期的内容
        val entry = Diary.entryOn(selected)
        val contentTop = gridTop + cellH * 6 + h * 0.035f

        pLine.color = dot
        pLine.strokeWidth = 1.2f
        canvas.drawLine(pad, contentTop - h * 0.022f, w - pad, contentTop - h * 0.022f, pLine)

        pMeta.textSize = w * 0.028f
        pMeta.color = accent
        pMeta.letterSpacing = 0.10f
        canvas.drawText(
            "${selected.year}.${"%02d".format(selected.monthValue)}.${"%02d".format(selected.dayOfMonth)}",
            pad, contentTop + h * 0.012f, pMeta
        )

        pBody.color = ink
        pBody.textSize = w * 0.062f
        canvas.drawText(entry.book.title, pad, contentTop + h * 0.072f, pBody)

        pMeta.color = soft
        pMeta.textSize = w * 0.028f
        pMeta.letterSpacing = 0.04f
        canvas.drawText("${entry.book.author} · ${entry.book.nation}", pad, contentTop + h * 0.108f, pMeta)
        pMeta.letterSpacing = 0f

        // 主推句
        pBody.textSize = w * 0.050f
        pBody.color = ink
        val quote = entry.quote.removeSuffix("。")
        val maxW = w - pad * 2
        var lines = com.mingzhu.core.Wrap.split(quote, pBody, maxW)
        while (lines.size > 5 && pBody.textSize > w * 0.038f) {
            pBody.textSize *= 0.94f
            lines = com.mingzhu.core.Wrap.split(quote, pBody, maxW)
        }
        var y = contentTop + h * 0.165f
        val lh = pBody.textSize * 1.62f
        lines.forEach { line ->
            canvas.drawText(line, pad, y, pBody)
            y += lh
        }

        // 底部提示
        pMeta.textSize = w * 0.026f
        val bottom = h - h * 0.030f
        pMeta.color = soft
        canvas.drawText("点日期查看当天书摘 · 左右滑动换月", pad, bottom - h * 0.04f, pMeta)
        pMeta.textAlign = Paint.Align.RIGHT
        pMeta.color = accent
        canvas.drawText("第 ${entry.quoteIndex + 1} / ${entry.book.count} 句", w - pad, bottom - h * 0.04f, pMeta)
        pMeta.textAlign = Paint.Align.LEFT
    }

    private fun subtle() = Color.parseColor("#A8A396")

    private fun colorFor(id: String): Int {
        val palette = intArrayOf(
            Color.parseColor("#3E6B57"), Color.parseColor("#8A5A3B"), Color.parseColor("#4E5A78"),
            Color.parseColor("#7A4B55"), Color.parseColor("#5B6B3E"), Color.parseColor("#6B5470"),
        )
        return palette[(id.hashCode() and 0x7fffffff) % palette.size]
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x
                downY = e.y
                return true
            }
            MotionEvent.ACTION_UP -> {
                // 左右滑动换月
                if (kotlin.math.abs(e.x - downX) > width * 0.15f) {
                    month = if (e.x < downX) month.plusMonths(1) else month.minusMonths(1)
                    invalidate()
                    performClick()
                    return true
                }
                // 点月份标题右侧切换月
                if (downY < height * 0.13f && e.x > width * 0.82f) {
                    month = if (e.x > width - width * 0.075f - width * 0.04f) month.plusMonths(1) else month.minusMonths(1)
                    invalidate()
                    performClick()
                    return true
                }
                // 点日历格子
                val idx = cells.indexOfFirst { it.contains(e.x, e.y) }
                if (idx >= 0) {
                    val grid = Diary.monthGrid(month.year, month.monthValue)
                    grid.getOrNull(idx)?.let { d ->
                        selected = d
                        onSelect?.invoke(d)
                        invalidate()
                    }
                }
                performClick()
                return true
            }
        }
        return super.onTouchEvent(e)
    }

    private var downX = 0f
    private var downY = 0f

    /** 按当前选中日期导出整屏版面（不含底部操作条）。 */
    fun exportPoster(): android.graphics.Bitmap {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        return com.mingzhu.core.Share.render({ c, cw, ch ->
            val ow = this.width; val oh = this.height
            val sc = c.save()
            c.scale(cw / ow, ch / oh)
            draw(c)
            c.restoreToCount(sc)
        }, w, h, 2.4f)
    }

    fun goToday(): Boolean {
        val changed = selected != LocalDate.now()
        selected = LocalDate.now()
        month = YearMonth.now()
        invalidate()
        return changed
    }

    fun turnPage(dir: Int) {
        month = if (dir > 0) month.plusMonths(1) else month.minusMonths(1)
        invalidate()
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
