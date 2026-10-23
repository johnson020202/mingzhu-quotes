package com.mingzhu.appE

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
import com.mingzhu.core.Wrap

/**
 * 版本 E「归档」：把全部书籍的每一句摊平成一张张索引卡，
 * 顶部可按作者筛选，点卡片展开全文，右侧书签可收藏。
 */
class ArchiveView(
    context: Context,
    private val isFav: (String, Int) -> Boolean,
    private val onToggleFav: (String, Int) -> Boolean,
) : View(context) {

    private val bg = Color.parseColor("#FCFBF7")
    private val ink = Color.parseColor("#1B1B19")
    private val soft = Color.parseColor("#8E8A80")
    private val line = Color.parseColor("#E2DDD2")
    private val accent = Color.parseColor("#2D5D48")
    private val mark = Color.parseColor("#B4553A")

    private val pBody = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typography2.body }
    private val pMeta = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typography2.meta }
    private val pLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    /** 扁平索引：书 + 句号。 */
    private data class Card(val bookId: String, val title: String, val author: String, val index: Int, val text: String)

    private val all: List<Card> = Library.books.flatMap { b ->
        b.lines.mapIndexed { i, t -> Card(b.id, b.title, b.author, i, t) }
    }

    private val authors: List<String> = listOf("全部") + Library.books.map { it.author }.distinct()
    private var filter = 0
    private var expanded: Int = -1
    private var scroll = 0f
    private var maxScroll = 0f
    private var downY = 0f
    private var lastY = 0f
    private var dragging = false

    private val visible: List<Card>
        get() = if (filter == 0) all else all.filter { it.author == authors[filter] }

    private val headerH: Float get() = width * 0.30f

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawColor(bg)

        pBody.color = ink
        pBody.textSize = w * 0.072f
        canvas.drawText("归档", w * 0.075f, h * 0.072f, pBody)

        pMeta.color = soft
        pMeta.textSize = w * 0.026f
        pMeta.letterSpacing = 0.09f
        canvas.drawText("${Library.books.size} 本 · ${all.size} 句", w * 0.075f + w * 0.155f, h * 0.072f, pMeta)
        pMeta.textAlign = Paint.Align.RIGHT
        pMeta.color = accent
        pMeta.letterSpacing = 0.02f
        canvas.drawText("${Diary.entryOn(java.time.LocalDate.now()).book.title} · 今日", w * 0.925f, h * 0.072f, pMeta)
        pMeta.textAlign = Paint.Align.LEFT
        pMeta.letterSpacing = 0f

        // 筛选条
        val chipY = h * 0.10f
        var cx = w * 0.075f
        pMeta.textSize = w * 0.026f
        authors.forEachIndexed { i, a ->
            val label = if (a == "全部") "全部" else a
            val cw = pMeta.measureText(label) + w * 0.048f
            if (cx + cw > w * 0.94f) return@forEachIndexed
            val r = RectF(cx, chipY, cx + cw, chipY + h * 0.030f)
            if (i == filter) {
                canvas.drawRoundRect(r, r.height() / 2f, r.height() / 2f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent })
                pMeta.color = bg
            } else {
                pLine.color = line
                pLine.strokeWidth = 1.2f
                canvas.drawRoundRect(r, r.height() / 2f, r.height() / 2f, pLine)
                pMeta.color = soft
            }
            pMeta.textAlign = Paint.Align.CENTER
            canvas.drawText(label, r.centerX(), r.centerY() + w * 0.009f, pMeta)
            pMeta.textAlign = Paint.Align.LEFT
            cx += cw + w * 0.018f
        }

        pLine.color = line
        pLine.strokeWidth = 1f
        canvas.drawLine(w * 0.075f, h * 0.155f, w * 0.925f, h * 0.155f, pLine)

        // 卡片列表
        val pad = w * 0.075f
        val right = w * 0.925f
        var y = h * 0.155f - scroll
        val listTop = h * 0.155f
        val listBottom = h - h * 0.02f
        visible.forEachIndexed { i, c ->
            val open = i == expanded
            val cardH = if (open) w * 0.34f else w * 0.255f
            if (y + cardH > listTop - cardH && y < listBottom) {
                drawCard(canvas, c, i, y, cardH, pad, right, w, open)
            }
            y += cardH
        }
        maxScroll = (y + scroll - listBottom).coerceAtLeast(0f)

        // 顶部渐隐遮罩，滚动时文字不会硬切
        val fade = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.LinearGradient(0f, listTop, 0f, listTop + h * 0.02f,
                intArrayOf(bg, Color.TRANSPARENT), null, android.graphics.Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, listTop, w, listTop + h * 0.02f, fade)
    }

    private fun drawCard(c: Canvas, card: Card, i: Int, y: Float, height: Float, left: Float, right: Float, w: Float, open: Boolean) {
        val pad = w * 0.02f
        val bottom = y + height - w * 0.018f
        val r = RectF(left, y, right, bottom)

        // 卡片左侧色条：按书区分
        val hue = Color.HSVToColor(floatArrayOf(((card.bookId.hashCode() and 0xFF) / 255f) * 360f, 0.32f, 0.62f))
        c.drawRoundRect(RectF(r.left, r.top, r.left + w * 0.010f, r.bottom), w * 0.005f, w * 0.005f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = hue })

        pMeta.textSize = w * 0.024f
        pMeta.color = soft
        pMeta.letterSpacing = 0.05f
        c.drawText("${card.title} · ${card.author}", r.left + pad + w * 0.02f, r.top + w * 0.045f, pMeta)
        pMeta.letterSpacing = 0f

        pMeta.color = Color.parseColor("#B5AFA2")
        pMeta.textSize = w * 0.022f
        pMeta.textAlign = Paint.Align.RIGHT
        c.drawText("%02d".format(card.index + 1), r.right - pad, r.top + w * 0.045f, pMeta)
        pMeta.textAlign = Paint.Align.LEFT

        // 收藏标记
        if (isFav(card.bookId, card.index)) {
            c.drawCircle(r.right - pad - w * 0.015f, r.top + w * 0.095f, w * 0.011f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = mark })
        }

        pBody.color = ink
        pBody.textSize = w * 0.042f
        val full = Wrap.trimEnd(card.text)
        val shown = if (open) full else if (full.length > 34) full.take(34) + "…" else full
        val lines = Wrap.split(shown, pBody, right - left - pad * 2 - w * 0.03f)
        var ty = r.top + w * 0.090f
        val lh = pBody.textSize * 1.58f
        lines.forEach { l ->
            c.drawText(l, r.left + pad + w * 0.02f, ty, pBody)
            ty += lh
        }

        pLine.color = line
        pLine.strokeWidth = 1f
        c.drawLine(r.left + pad + w * 0.02f, r.bottom - w * 0.012f, r.right - pad, r.bottom - w * 0.012f, pLine)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downY = e.y; lastY = e.y; dragging = false; return true }
            MotionEvent.ACTION_MOVE -> {
                if (kotlin.math.abs(e.y - downY) > height * 0.01f) dragging = true
                scroll = (scroll + (lastY - e.y)).coerceIn(0f, maxScroll)
                lastY = e.y
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!dragging) tap(e.x, e.y)
                performClick()
                return true
            }
        }
        return super.onTouchEvent(e)
    }

    private fun tap(x: Float, y: Float) {
        // 筛选条
        if (y > height * 0.098f && y < height * 0.135f) {
            val w = width.toFloat()
            pMeta.textSize = w * 0.026f
            var cx = w * 0.075f
            authors.forEachIndexed { i, a ->
                val label = a
                val cw = pMeta.measureText(label) + w * 0.048f
                if (x >= cx && x < cx + cw) {
                    filter = i
                    expanded = -1
                    scroll = 0f
                    invalidate()
                    return
                }
                cx += cw + w * 0.018f
            }
            return
        }
        // 卡片
        val top = height * 0.155f
        var yy = top - scroll
        val w = width.toFloat()
        visible.forEachIndexed { i, c ->
            val cardH = if (i == expanded) w * 0.34f else w * 0.255f
            if (y >= yy && y < yy + cardH) {
                // 右上角点击切换收藏，其余区域展开
                if (x > w * 0.80f && y < yy + w * 0.06f) {
                    onToggleFav(c.bookId, c.index)
                } else {
                    expanded = if (expanded == i) -1 else i
                }
                invalidate()
                return
            }
            yy += cardH
        }
    }

    fun exportPoster(): android.graphics.Bitmap {
        val ow = width; val oh = height
        val keepScroll = scroll
        val keepExp = expanded
        scroll = 0f
        expanded = -1
        val bmp = com.mingzhu.core.Share.render({ c, cw, ch ->
            val sc = c.save()
            c.scale(cw / ow, ch / oh)
            draw(c)
            c.restoreToCount(sc)
        }, ow, oh, 2.4f)
        scroll = keepScroll
        expanded = keepExp
        return bmp
    }

    fun goToday(): Boolean {
        filter = 0
        expanded = 0
        scroll = 0f
        invalidate()
        return true
    }

    fun turnPage(dir: Int) {
        scroll = (scroll + dir * width * 0.6f).coerceIn(0f, maxScroll)
        invalidate()
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
