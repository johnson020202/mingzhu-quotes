package com.lemon.mingzhu

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/**
 * 版本三「朱砂」：整屏一枚巨大的朱红印章，文字压在印里；
 * 极简三色（朱砂 / 墨 / 宣纸），点击左右翻页。
 */
class MainView(context: Context, private val store: QuoteStore) : View(context) {

    private val paper = Color.parseColor("#F2EDE4")
    private val ink = Color.parseColor("#161616")
    private val cinnabar = Color.parseColor("#C0392B")
    private val cinnabarDeep = Color.parseColor("#9E2B20")

    private val pBody = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = UIFont.body
        color = ink
        letterSpacing = 0.02f
    }
    private val pMeta = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = UIFont.meta
    }
    private val pAccent = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = UIFont.body
        color = cinnabar
    }

    private var quote: Quote = store.current()
    private var downX = 0f
    private var downY = 0f

    /** 屏幕绘制只占 88% 高度，底部留给操作条；导出时单独按整高渲染。 */
    private val contentHeightScale = 0.88f

    fun refresh() {
        quote = store.current()
        invalidate()
    }

    fun share(): android.graphics.Bitmap {
        // 导出不带操作条的完整版面：按屏幕宽度、整屏高度重绘
        val fullH = (height / contentHeightScale).toInt()
        val raw = android.graphics.Bitmap.createBitmap(width.coerceAtLeast(1), fullH, android.graphics.Bitmap.Config.ARGB_8888)
        val sc = android.graphics.Canvas(raw)
        sc.scale(1f, 1f)
        drawContent(sc, width.toFloat(), fullH.toFloat())
        val bmp = android.graphics.Bitmap.createScaledBitmap(raw, (width * 2.4f).toInt(), (fullH * 2.4f).toInt(), true)
        return Poster.withColophon(bmp, paper, ink, cinnabar, Dates.chinese())
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height * contentHeightScale
        drawContent(canvas, w, h)
    }

    /** 按给定尺寸完整绘制版面，导出时传入整屏高度。 */
    private fun drawContent(canvas: Canvas, w: Float, h: Float) {
        canvas.drawColor(paper)

        val m = w * 0.055f
        val top = h * 0.095f
        val bottom = h * 0.885f

        // —— 外框：朱砂细线双框 ——
        val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = cinnabar
            strokeWidth = w * 0.012f
        }
        canvas.drawRect(m, top, w - m, bottom, frame)
        val inner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = cinnabar
            strokeWidth = 1.4f
        }
        canvas.drawRect(m + w * 0.028f, top + w * 0.028f, w - m - w * 0.028f, bottom - w * 0.028f, inner)

        val pad = m + w * 0.095f
        val right = w - pad

        // —— 顶部：序号 + 日期 ——
        pMeta.textSize = w * 0.030f
        pMeta.letterSpacing = 0.28f
        pMeta.color = cinnabar
        val n = QuoteRepo.count(store.lang)
        val idx = store.index(store.lang)
        canvas.drawText("第 ${Dates.toCn(idx + 1)} 阕", pad, top + h * 0.075f, pMeta)
        pMeta.textAlign = Paint.Align.RIGHT
        pMeta.color = ink
        pMeta.alpha = 150
        pMeta.letterSpacing = 0.12f
        canvas.drawText(Dates.dotted(), right, top + h * 0.075f, pMeta)
        pMeta.textAlign = Paint.Align.LEFT
        pMeta.alpha = 255
        pMeta.letterSpacing = 0f

        // —— 正文 ——
        val zoneTop = top + h * 0.14f
        val zoneBottom = top + h * 0.47f
        pBody.textSize = w * 0.094f
        var lineH = pBody.textSize * 1.62f
        val text = Typography.trimPeriod(quote.text)
        var lines = Typography.wrap(text, pBody, right - pad)
        while (zoneTop + lines.size * lineH > zoneBottom && pBody.textSize > w * 0.055f) {
            pBody.textSize *= 0.93f
            lineH = pBody.textSize * 1.62f
            lines = Typography.wrap(text, pBody, right - pad)
        }
        var y = zoneTop + pBody.textSize
        lines.forEach { line ->
            canvas.drawText(line, pad, y, pBody)
            // 每行尾部一小段朱砂短线，右对齐
            y += lineH
        }

        // —— 巨大的印章：朱红方块，压住正文下半部 ——
        val sealSize = w * 0.40f
        val sealX = right - sealSize
        val sealY = top + h * 0.455f
        drawBigSeal(canvas, sealX, sealY, sealSize)

        // —— 书名与作者（左侧，与印章错落） ——
        val citeTop = sealY + h * 0.045f
        pAccent.textSize = w * 0.060f
        pAccent.color = ink
        pAccent.letterSpacing = 0.02f
        canvas.drawText(quote.book, pad, citeTop, pAccent)
        pMeta.textSize = w * 0.032f
        pMeta.color = cinnabar
        pMeta.letterSpacing = 0.14f
        canvas.drawText(quote.author, pad, citeTop + w * 0.075f, pMeta)
        if (quote.origin.isNotEmpty()) {
            pMeta.color = ink
            pMeta.alpha = 140
            pMeta.letterSpacing = 0.06f
            canvas.drawText(quote.origin, pad, citeTop + w * 0.128f, pMeta)
            pMeta.alpha = 255
        }
        pMeta.letterSpacing = 0f

        // —— 底部：翻页提示 ——
        pMeta.textSize = w * 0.028f
        pMeta.color = ink
        pMeta.alpha = 120
        pMeta.letterSpacing = 0.18f
        canvas.drawText("轻点左右翻页", pad, bottom - h * 0.045f, pMeta)
        pMeta.textAlign = Paint.Align.RIGHT
        canvas.drawText("${idx + 1} / $n", right, bottom - h * 0.045f, pMeta)
        pMeta.textAlign = Paint.Align.LEFT
        pMeta.alpha = 255
        pMeta.letterSpacing = 0f

        // 底部一条朱砂粗线，作为整幅的"压底"
        val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cinnabar }
        canvas.drawRect(pad, bottom - h * 0.075f, pad + (right - pad) * ((idx + 1f) / n), bottom - h * 0.075f + w * 0.008f, bar)
    }

    /** 方正的大印：实心朱块 + 反白的"明著摘录"四字，边缘有缺口。 */
    private fun drawBigSeal(c: Canvas, x: Float, y: Float, s: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = cinnabar
            style = Paint.Style.FILL
        }
        c.drawRect(x, y, x + s, y + s, p)

        // 内框线（反白）
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = paper
            style = Paint.Style.STROKE
            strokeWidth = s * 0.022f
        }
        val inset = s * 0.07f
        c.drawRect(x + inset, y + inset, x + s - inset, y + s - inset, line)

        // 2x2 反白字
        val gp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = UIFont.body
            color = paper
            textAlign = Paint.Align.CENTER
            textSize = s * 0.34f
        }
        val chars = "明著摘录"
        val cell = (s - inset * 2 - s * 0.04f) / 2f
        val ox = x + inset + s * 0.02f
        val oy = y + inset + s * 0.02f
        chars.forEachIndexed { i, ch ->
            val col = i % 2
            val row = i / 2
            c.drawText(ch.toString(), ox + cell * (col + 0.5f), oy + cell * (row + 0.5f) + s * 0.10f, gp)
        }

        // 几处残缺：用纸色的小块切掉边缘，模拟盖章磨损
        val chip = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = paper }
        c.drawRect(x - 1f, y + s * 0.30f, x + s * 0.035f, y + s * 0.34f, chip)
        c.drawRect(x + s * 0.62f, y + s - s * 0.03f, x + s * 0.70f, y + s + 1f, chip)
        c.drawRect(x + s - s * 0.012f, y + s * 0.58f, x + s + 1f, y + s * 0.63f, chip)
        c.drawRect(x + s * 0.20f, y - 1f, x + s * 0.24f, y + s * 0.025f, chip)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x
                downY = e.y
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (abs(e.x - downX) > width * 0.06f) {
                    turn(if (e.x < downX) 1 else -1)
                } else {
                    // 轻点：左三成上一页，右七成下一页
                    turn(if (e.x > width * 0.3f) 1 else -1)
                }
                performClick()
                return true
            }
        }
        return super.onTouchEvent(e)
    }

    fun turn(dir: Int) {
        quote = if (dir > 0) store.next() else store.prev()
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        invalidate()
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
