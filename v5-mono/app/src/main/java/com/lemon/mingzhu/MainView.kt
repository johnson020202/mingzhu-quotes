package com.lemon.mingzhu

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.min

/**
 * 版本五「极简」：纯黑底、超大号码、自动化式的时间戳，
 * 像一块显示摘抄的极简终端。左右滑动翻页。
 */
class MainView(context: Context, private val store: QuoteStore) : View(context) {

    private val black = Color.parseColor("#0A0A0A")
    private val white = Color.parseColor("#F7F7F5")
    private val grey = Color.parseColor("#6E6E6E")
    private val dim = Color.parseColor("#2E2E2E")

    private val pBody = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = UIFont.body
        color = white
        letterSpacing = 0.01f
    }
    private val pMeta = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = UIFont.meta
        color = grey
        letterSpacing = 0.10f
    }
    private val pBig = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = UIFont.body
        color = white
    }

    private var quote: Quote = store.current()
    private var downX = 0f
    private var lastTurn = 0L
    private var pulse = 0f
    private var lampAnim: android.animation.ValueAnimator? = null

    /** 屏幕内容只画到 82% 高度，底部留给操作栏；导出时按完整高度单独渲染。 */
    private companion object { const val CONTENT_SCALE = 0.82f }
    private var contentHeightScale = CONTENT_SCALE

    fun refresh() {
        quote = store.current()
        invalidate()
    }

    fun share(): android.graphics.Bitmap {
        val fullH = (height / contentHeightScale).toInt()
        val bmp = android.graphics.Bitmap.createBitmap(width, fullH, android.graphics.Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(black)
        drawContent(c, width.toFloat(), fullH.toFloat())
        val scaled = android.graphics.Bitmap.createScaledBitmap(bmp, (width * 2.4f).toInt(), (fullH * 2.4f).toInt(), true)
        return Poster.withColophon(scaled, black, white, grey, Dates.english())
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height * contentHeightScale
        canvas.drawColor(black)
        drawContent(canvas, w, h)
    }

    private fun drawContent(canvas: Canvas, w: Float, h: Float) {

        val pad = w * 0.075f
        val right = w - pad

        // 顶部：细线 + 系统状态字样
        pMeta.textSize = w * 0.0235f
        pMeta.color = grey
        pMeta.letterSpacing = 0.16f
        canvas.drawText("MINGZHU", pad, h * 0.075f, pMeta)
        pMeta.textAlign = Paint.Align.RIGHT
        canvas.drawText(Dates.english(), right, h * 0.075f, pMeta)
        pMeta.textAlign = Paint.Align.LEFT

        val lineY = h * 0.095f
        canvas.drawRect(pad, lineY, right, lineY + 1.2f, Paint().apply { color = dim })

        // 超大序号
        val n = QuoteRepo.count(store.lang)
        val idx = store.index(store.lang)
        val num = "%02d".format(idx + 1)
        pBig.textSize = min(w * 0.34f, h * 0.20f)
        pBig.color = white
        canvas.drawText(num, pad - w * 0.012f, h * 0.30f, pBig)
        // 总数放在右上角独立留白区，与左下的大号序号分列两端
        pBig.textSize = w * 0.040f
        pBig.color = dim
        pBig.textAlign = Paint.Align.RIGHT
        canvas.drawText("TOTAL", right, h * 0.155f, pBig)
        pBig.textSize = w * 0.13f
        pBig.color = white
        pBig.alpha = 60
        canvas.drawText("%02d".format(n), right, h * 0.245f, pBig)
        pBig.alpha = 255
        pBig.textAlign = Paint.Align.LEFT

        // 右侧一条极短的进度线
        val barTop = h * 0.235f
        val barH = h * 0.065f
        canvas.drawRect(right - 3f, barTop, right, barTop + barH, Paint().apply { color = dim })
        val progH = barH * ((idx + 1f) / n)
        canvas.drawRect(right - 3f, barTop, right, barTop + progH, Paint().apply { color = white })

        // 正文：左上角对齐，越短字号越大（视觉上更"满"）
        val zoneTop = h * 0.40f
        val zoneBottom = h * 0.69f
        val text = Typography.trimPeriod(quote.text)
        var size = w * 0.115f
        var lines: List<String>
        var lineH: Float
        while (true) {
            val probe = Paint(pBody).apply { textSize = size }
            lines = Typography.wrap(text, probe, right - pad)
            lineH = size * 1.42f
            if (zoneTop + lines.size * lineH <= zoneBottom || size <= w * 0.058f) break
            size *= 0.94f
        }
        pBody.textSize = size
        var y = zoneTop + size
        lines.forEach { line ->
            canvas.drawText(line, pad, y, pBody)
            y += lineH
        }

        // 出处：右对齐，与正文形成对角
        val citeY = maxOf(y + h * 0.06f, h * 0.755f)
        pMeta.textSize = w * 0.028f
        pMeta.color = white
        pMeta.letterSpacing = 0.02f
        pMeta.textAlign = Paint.Align.RIGHT
        pBig.let {
            val cp = Paint(pBody).apply { textSize = w * 0.056f; textAlign = Paint.Align.RIGHT }
            canvas.drawText("《${quote.book}》", right, citeY, cp)
        }
        pMeta.color = grey
        pMeta.letterSpacing = 0.12f
        canvas.drawText((quote.author + if (quote.origin.isNotEmpty()) "  ${quote.origin}" else "").uppercase(),
            right, citeY + w * 0.065f, pMeta)
        pMeta.textAlign = Paint.Align.LEFT
        pMeta.letterSpacing = 0f

        // 底部：一条粗白线和提示
        val baseY = h * 0.895f
        canvas.drawRect(pad, baseY, right, baseY + w * 0.012f, Paint().apply { color = white })
        pMeta.textSize = w * 0.0235f
        pMeta.color = grey
        pMeta.letterSpacing = 0.18f
        canvas.drawText("← 滑 · 摘 · 滑 →", pad, baseY + h * 0.042f, pMeta)
        pMeta.textAlign = Paint.Align.RIGHT
        pMeta.color = white
        canvas.drawText(Dates.dotted(), right, baseY + h * 0.042f, pMeta)
        pMeta.textAlign = Paint.Align.LEFT
        pMeta.letterSpacing = 0f
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x
                return true
            }
            MotionEvent.ACTION_UP -> {
                val dx = e.x - downX
                if (abs(dx) < width * 0.05f) {
                    turn(1) // 轻点也是下一页，符合"点一下换一条"
                } else {
                    turn(if (dx < 0) 1 else -1)
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
        animate().cancel()
        alpha = 0f
        animate().alpha(1f).setDuration(180).start()
        invalidate()
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
