package com.mingzhu.appB

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import com.mingzhu.core.Diary
import com.mingzhu.core.Library
import com.mingzhu.core.Typography2
import com.mingzhu.core.Wrap
import java.time.LocalDate

/**
 * 版本 B「书页」：一屏一本书，20 句逐句往下翻；横滑换书。
 */
class BookView(context: Context) : View(context) {

    private val paper = Color.parseColor("#F4EFE4")
    private val ink = Color.parseColor("#1F1D1A")
    private val soft = Color.parseColor("#8B8477")
    private val accent = Color.parseColor("#A8452F")
    private val rule = Color.parseColor("#D6CDB9")

    private val pBody = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typography2.body }
    private val pMeta = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typography2.meta }
    private val pLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private val date: LocalDate = LocalDate.now()
    private var entry = Diary.entryOn(date)
    private var bookIndex: Int = Library.books.indexOfFirst { it.id == entry.book.id }.coerceAtLeast(0)
    var quoteIndex: Int = entry.quoteIndex
        private set

    private var downY = 0f
    private var downX = 0f
    private var onChanged: (() -> Unit)? = null

    fun setListener(l: () -> Unit) { onChanged = l }

    val title: String get() = Library.books[bookIndex].title
    val currentLine: String get() = Library.books[bookIndex].lines[quoteIndex]

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val book = Library.books[bookIndex]
        canvas.drawColor(paper)

        pLine.color = rule
        pLine.strokeWidth = 2f
        canvas.drawLine(w * 0.085f, h * 0.10f, w * 0.085f, h * 0.87f, pLine)

        val pad = w * 0.145f
        val right = w * 0.915f

        pMeta.textSize = w * 0.027f
        pMeta.color = soft
        pMeta.letterSpacing = 0.12f
        canvas.drawText("${date.year}.${"%02d".format(date.monthValue)}.${"%02d".format(date.dayOfMonth)}", pad, h * 0.115f, pMeta)
        pMeta.textAlign = Paint.Align.RIGHT
        canvas.drawText("${quoteIndex + 1} / ${book.count}", right, h * 0.115f, pMeta)
        pMeta.textAlign = Paint.Align.LEFT
        pMeta.letterSpacing = 0f

        pBody.color = ink
        pBody.textSize = w * 0.088f
        canvas.drawText(book.title, pad, h * 0.195f, pBody)

        pMeta.textSize = w * 0.030f
        pMeta.color = soft
        pMeta.letterSpacing = 0.06f
        canvas.drawText("${book.author} · ${book.nation} · ${book.year}", pad, h * 0.235f, pMeta)
        pMeta.letterSpacing = 0f

        pLine.color = rule
        pLine.strokeWidth = 1.2f
        canvas.drawLine(pad, h * 0.258f, right, h * 0.258f, pLine)

        pBody.color = ink
        pBody.textSize = w * 0.070f
        val quote = Wrap.trimEnd(book.lines[quoteIndex])
        var lines = Wrap.split(quote, pBody, right - pad)
        var lh = pBody.textSize * 1.62f
        while (h * 0.31f + lines.size * lh > h * 0.66f && pBody.textSize > w * 0.045f) {
            pBody.textSize *= 0.94f
            lh = pBody.textSize * 1.62f
            lines = Wrap.split(quote, pBody, right - pad)
        }
        var y = h * 0.31f + pBody.textSize
        lines.forEach { line ->
            canvas.drawText(line, pad, y, pBody)
            y += lh
        }

        // 导读
        pMeta.textSize = w * 0.028f
        pMeta.color = soft
        var iy = maxOf(y + h * 0.025f, h * 0.705f)
        Wrap.split(book.intro, pMeta, right - pad).forEach { line ->
            canvas.drawText(line, pad, iy, pMeta)
            iy += pMeta.textSize * 1.7f
        }

        // 20 格刻度
        val trackY = h * 0.845f
        val seg = (right - pad) / (book.count - 1)
        for (i in 0 until book.count) {
            val x = pad + seg * i
            val on = i == quoteIndex
            pLine.color = if (on) accent else rule
            pLine.strokeWidth = if (on) w * 0.008f else 1.6f
            canvas.drawLine(x, trackY - (if (on) h * 0.018f else h * 0.008f), x, trackY, pLine)
        }

        pMeta.textSize = w * 0.026f
        pMeta.color = soft
        canvas.drawText("上下滑逐句 · 横滑换书", pad, h * 0.895f, pMeta)
        pMeta.textAlign = Paint.Align.RIGHT
        canvas.drawText("第 ${quoteIndex + 1} 句", right, h * 0.895f, pMeta)
        pMeta.textAlign = Paint.Align.LEFT
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downY = e.y; downX = e.x; return true }
            MotionEvent.ACTION_UP -> {
                val dy = e.y - downY
                val dx = e.x - downX
                val n = Library.books[bookIndex].count
                if (kotlin.math.abs(dy) > height * 0.05f) {
                    quoteIndex = if (dy < 0) (quoteIndex + 1) % n else (quoteIndex - 1 + n) % n
                } else if (kotlin.math.abs(dx) > width * 0.15f) {
                    bookIndex = if (dx < 0) (bookIndex + 1) % Library.books.size
                                else (bookIndex - 1 + Library.books.size) % Library.books.size
                    quoteIndex = 0
                }
                invalidate()
                onChanged?.invoke()
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
