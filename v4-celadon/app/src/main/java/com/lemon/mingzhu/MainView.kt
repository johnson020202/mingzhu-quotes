package com.lemon.mingzhu

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs
import kotlin.math.sin

/**
 * 版本四「青瓷」：青灰底、格栅网格、左下角手绘感青绿波线；
 * 每一页都是排版参考线可见的"设计稿"。
 */
class MainView(context: Context, private val store: QuoteStore) : View(context) {

    private val bg = Color.parseColor("#93A99C")
    private val panel = Color.parseColor("#DCE5DC")
    private val deep = Color.parseColor("#0B3A2E")
    private val deepSoft = Color.parseColor("#4E6B60")
    private val wave = Color.parseColor("#7FA391")
    private val highlight = Color.parseColor("#C9DCC9")

    private val pBody = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = UIFont.body
        color = deep
    }
    private val pMeta = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = UIFont.meta
        color = deepSoft
    }
    private val pAccent = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = UIFont.body
        color = deep
    }

    private var quote: Quote = store.current()
    private var slide = 0f
    private var lastTurn = 0L
    private var animator: ValueAnimator? = null

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
        return Poster.withColophon(bmp, bg, deep, deepSoft, Dates.dotted())
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height * contentHeightScale
        drawContent(canvas, w, h)
    }

    /** 按给定尺寸完整绘制版面，导出时传入整屏高度。 */
    private fun drawContent(canvas: Canvas, w: Float, h: Float) {
        canvas.drawColor(bg)

        // 整块内凹面板
        val panelRect = RectF(w * 0.045f, h * 0.065f, w * 0.955f, h * 0.915f)
        val radius = w * 0.055f
        canvas.drawRoundRect(panelRect, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = panel })

        drawGrid(canvas, panelRect)

        val pad = panelRect.left + w * 0.075f
        val right = panelRect.right - w * 0.075f
        val colW = right - pad

        val n = QuoteRepo.count(store.lang)
        val idx = store.index(store.lang)

        // 顶部模块标签
        pMeta.textSize = w * 0.026f
        pMeta.letterSpacing = 0.14f
        pMeta.color = deepSoft
        canvas.drawText("MINGZHU / 摘抄  " + "%02d".format(idx + 1), pad, panelRect.top + h * 0.05f, pMeta)

        // 右上：一个可点击的胶囊（语言切换外观）
        val pill = RectF(right - w * 0.235f, panelRect.top + h * 0.030f, right, panelRect.top + h * 0.062f)
        val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.TRANSPARENT
            style = Paint.Style.STROKE
            strokeWidth = 2f
            this.color = deepSoft
        }
        canvas.drawRoundRect(pill, pill.height() / 2f, pill.height() / 2f, pillPaint)
        pMeta.textSize = w * 0.026f
        pMeta.letterSpacing = 0.06f
        pMeta.color = deep
        pMeta.textAlign = Paint.Align.CENTER
        canvas.drawText(if (store.lang == QuoteLang.ZH) "中文" else "EN", pill.centerX(), pill.centerY() + w * 0.010f, pMeta)
        pMeta.textAlign = Paint.Align.LEFT
        pMeta.letterSpacing = 0f


        // 正文：从大索引右侧/下方开始
        pBody.textSize = w * 0.082f
        var lineH = pBody.textSize * 1.60f
        val text = Typography.trimPeriod(quote.text)
        val bodyTop = panelRect.top + h * 0.215f
        val bodyBottom = panelRect.top + h * 0.555f
        var lines = Typography.wrap(text, pBody, colW)
        while (bodyTop + lines.size * lineH > bodyBottom && pBody.textSize > w * 0.050f) {
            pBody.textSize *= 0.94f
            lineH = pBody.textSize * 1.60f
            lines = Typography.wrap(text, pBody, colW)
        }
        var y = bodyTop + pBody.textSize
        lines.forEach { line ->
            canvas.drawText(line, pad, y, pBody)
            y += lineH
        }

        // 分隔线 + 出处信息，两栏对齐网格
        val ruleY = panelRect.top + h * 0.60f
        val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = deep
            strokeWidth = w * 0.0035f
            style = Paint.Style.STROKE
        }
        canvas.drawRect(pad, ruleY, pad + w * 0.06f, ruleY, rule)
        canvas.drawRect(pad + w * 0.075f, ruleY, right, ruleY, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = deepSoft
            strokeWidth = 1.5f
            style = Paint.Style.STROKE
        })

        pAccent.textSize = w * 0.070f
        pAccent.color = deep
        canvas.drawText(quote.book, pad, ruleY + h * 0.055f, pAccent)

        pMeta.textSize = w * 0.028f
        pMeta.color = deepSoft
        pMeta.letterSpacing = 0.10f
        canvas.drawText(quote.author + if (quote.origin.isNotEmpty()) " · ${quote.origin}" else "",
            pad, ruleY + h * 0.085f, pMeta)

        // 日期，做成网格里的一个单元格
        val cellW = colW / 2f
        pMeta.letterSpacing = 0.12f
        pMeta.color = deep
        canvas.drawText("日期  " + Dates.dotted(), pad, ruleY + h * 0.105f, pMeta)
        pMeta.textAlign = Paint.Align.RIGHT
        pMeta.color = deepSoft
        canvas.drawText("${idx + 1} / $n", right, ruleY + h * 0.105f, pMeta)
        pMeta.textAlign = Paint.Align.LEFT
        pMeta.letterSpacing = 0f

        // 底部波线
        drawWaves(canvas, pad, panelRect.bottom - h * 0.05f, colW, h)

        // 进度刻度，像尺子一样排在面板最底部
        val tickY = panelRect.bottom - h * 0.018f
        val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = deepSoft; strokeWidth = 1.2f }
        for (i in 0 until n) {
            val x = pad + colW * (i.toFloat() / (n - 1))
            val long = i == idx
            tickPaint.strokeWidth = if (long) w * 0.004f else 1.2f
            tickPaint.color = if (long) deep else deepSoft
            canvas.drawLine(x, tickY - (if (long) h * 0.014f else h * 0.007f), x, tickY, tickPaint)
        }
    }

    private fun drawGrid(c: Canvas, r: RectF) {
        val gp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = deep
            alpha = 16
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }
        val cell = r.width() / 6f
        var x = r.left + cell
        while (x < r.right) {
            c.drawLine(x, r.top + 0f, x, r.bottom, gp)
            x += cell
        }
        var y = r.top + cell
        while (y < r.bottom) {
            c.drawLine(r.left, y, r.right, y, gp)
            y += cell
        }
        // 外描边
        gp.alpha = 30
        gp.strokeWidth = 1.5f
        c.drawRoundRect(r, r.width() * 0.058f, r.width() * 0.058f, gp)
    }

    private fun drawWaves(c: Canvas, x: Float, baseY: Float, w: Float, h: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.2f
            color = wave
        }
        for (layer in 0 until 4) {
            p.alpha = 120 + layer * 30
            p.strokeWidth = 1.4f + layer * 0.5f
            val path = android.graphics.Path()
            val amp = h * (0.008f + layer * 0.004f)
            val phase = layer * 0.9f + slide * 0.6f
            var i = 0f
            while (i <= w) {
                val yy = baseY - layer * h * 0.014f + sin((i / w) * 6.0f + phase) * amp
                if (i == 0f) path.moveTo(x + i, yy) else path.lineTo(x + i, yy)
                i += 4f
            }
            c.drawPath(path, p)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                animator?.cancel()
                slide = e.x
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.x - slide
                if (abs(dx) > width * 0.13f && System.currentTimeMillis() - lastTurn > 500) {
                    turn(if (dx < 0) 1 else -1)
                    lastTurn = System.currentTimeMillis()
                    slide = e.x
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                performClick()
                return true
            }
        }
        return super.onTouchEvent(e)
    }

    fun turn(dir: Int) {
        quote = if (dir > 0) store.next() else store.prev()
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        animator?.cancel()
        animator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 420
            interpolator = DecelerateInterpolator()
            addUpdateListener { a ->
                slide = a.animatedValue as Float
                invalidate()
            }
            start()
        }
        invalidate()
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
