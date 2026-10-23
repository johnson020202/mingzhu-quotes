package com.lemon.mingzhu

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.LinearGradient
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

/**
 * 版本二「夜读」：纯黑底、暖金强调、星点点缀，上下滑动切换。
 * 适合睡前把手机亮度调低读一句。
 */
class MainView(context: Context, private val store: QuoteStore) : View(context) {

    private val night = Color.parseColor("#0B0D10")
    private val deep = Color.parseColor("#11151A")
    private val cream = Color.parseColor("#EFE7D8")
    private val creamSoft = Color.parseColor("#8E8878")
    private val amber = Color.parseColor("#D9A253")
    private val hairline = Color.parseColor("#2A2F36")

    private val pBody = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = UIFont.body
        color = cream
        letterSpacing = 0.03f
    }
    private val pMeta = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = UIFont.meta
        letterSpacing = 0.12f
    }
    private val pAccent = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = UIFont.body
        color = amber
    }

    private var quote: Quote = store.current()
    private var dragY = 0f
    private var lastTurn = 0L
    private val glow = RadialGradient(0f, 0f, 1f, intArrayOf(Color.parseColor("#26D9A253"), 0x00000000), null, Shader.TileMode.CLAMP)
    private val stars: List<FloatArray> = run {
        val r = Random(23)
        (0 until 120).map { floatArrayOf(r.nextFloat(), r.nextFloat(), 0.25f + r.nextFloat() * 0.75f) }
    }

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
        return Poster.withColophon(bmp, night, cream, amber, Dates.english())
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        glow?.let { }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height * contentHeightScale
        drawContent(canvas, w, h)
    }

    /** 按给定尺寸完整绘制版面，导出时传入整屏高度。 */
    private fun drawContent(canvas: Canvas, w: Float, h: Float) {
        canvas.drawColor(night)

        // 顶部一片极淡的金色，像台灯的余晖，覆盖整屏不出现边界
        val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, 0f, h,
                intArrayOf(Color.parseColor("#12D9A253"), Color.parseColor("#07D9A253"), Color.parseColor("#03000000"), 0x00000000),
                floatArrayOf(0f, 0.22f, 0.5f, 0.8f), Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, w, h, halo)

        drawStars(canvas, w, h)

        val pad = w * 0.10f
        pMeta.typeface = UIFont.meta

        // 顶部：日期与"夜读"字样
        pMeta.textSize = w * 0.028f
        pMeta.color = amber
        c(canvas) { it.drawText("NIGHT READING", pad, h * 0.135f, pMeta) }
        pMeta.textAlign = Paint.Align.RIGHT
        pMeta.color = creamSoft
        c(canvas) { it.drawText(Dates.dotted(), w - pad, h * 0.135f, pMeta) }
        pMeta.textAlign = Paint.Align.LEFT

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = hairline
            strokeWidth = 1.5f
            style = Paint.Style.STROKE
        }
        val lineY = h * 0.152f
        c(canvas) { it.drawLine(pad, lineY, w - pad, lineY, linePaint) }
        // 线上的一小段金色刻度
        val marker = lineY
        c(canvas) {
            val mp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = amber; strokeWidth = 4f; style = Paint.Style.STROKE }
            it.drawLine(pad, marker, pad + w * 0.09f, marker, mp)
        }

        // 正文区
        val zoneTop = h * 0.20f
        val zoneBottom = h * 0.645f
        pBody.textSize = w * 0.082f
        pBody.color = cream
        var lineH = pBody.textSize * 1.78f
        var lines = Typography.wrap(Typography.trimPeriod(quote.text), pBody, w - pad * 2)
        while (zoneTop + lines.size * lineH > zoneBottom && pBody.textSize > w * 0.052f) {
            pBody.textSize *= 0.94f
            lineH = pBody.textSize * 1.78f
            lines = Typography.wrap(Typography.trimPeriod(quote.text), pBody, w - pad * 2)
        }
        val blockH = lines.size * lineH
        var y = zoneTop + (zoneBottom - zoneTop - blockH) / 2f + pBody.textSize
        lines.forEach { line ->
            c(canvas) { it.drawText(line, pad, y, pBody) }
            y += lineH
        }

        // 下方：书名大字 + 作者
        val citeY = maxOf(zoneBottom + h * 0.06f, h * 0.70f)
        pAccent.textSize = w * 0.062f
        pAccent.color = cream
        pAccent.letterSpacing = 0.01f
        c(canvas) { it.drawText("《${quote.book}》", pad, citeY, pAccent) }

        pMeta.textSize = w * 0.030f
        pMeta.color = amber
        pMeta.letterSpacing = 0.16f
        val sub = (quote.author + if (quote.origin.isNotEmpty()) " · ${quote.origin}" else "").uppercase()
        c(canvas) { it.drawText(sub, pad, citeY + w * 0.075f, pMeta) }
        pMeta.letterSpacing = 0f

        // 右下一枚金色圆环，里面是"夜"字
        val ringR = w * 0.085f
        val cx = w - pad - ringR
        val cy = citeY + w * 0.02f
        c(canvas) {
            val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = w * 0.004f
                color = amber
            }
            it.drawCircle(cx, cy, ringR, ring)
            val inner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.2f
                color = Color.parseColor("#4DD9A253")
            }
            it.drawCircle(cx, cy, ringR * 0.82f, inner)
            pAccent.textSize = ringR * 1.05f
            pAccent.textAlign = Paint.Align.CENTER
            pAccent.color = amber
            it.drawText("夜", cx, cy + ringR * 0.38f, pAccent)
            pAccent.textAlign = Paint.Align.LEFT
        }

        // 底部进度刻度
        val trackY = h * 0.905f
        val n = QuoteRepo.count(store.lang)
        val idx = store.index(store.lang)
        val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#23282F") }
        c(canvas) { it.drawRoundRect(pad, trackY - 2f, w - pad, trackY + 2f, 2f, 2f, track) }
        val prog = (idx + 1).toFloat() / n
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = amber }
        c(canvas) { it.drawRoundRect(pad, trackY - 2f, pad + (w - pad * 2) * prog, trackY + 2f, 2f, 2f, fill) }

        pMeta.textSize = w * 0.026f
        pMeta.color = creamSoft
        pMeta.letterSpacing = 0.08f
        c(canvas) { it.drawText("${"%02d".format(idx + 1)} / ${"%02d".format(n)}", pad, trackY - h * 0.018f, pMeta) }
        pMeta.textAlign = Paint.Align.RIGHT
        c(canvas) { it.drawText("上滑下一篇", w - pad, trackY - h * 0.018f, pMeta) }
        pMeta.textAlign = Paint.Align.LEFT
        pMeta.letterSpacing = 0f
    }

    private inline fun c(canvas: Canvas, block: (Canvas) -> Unit) = block(canvas)

    private fun drawStars(c: Canvas, w: Float, h: Float) {
        val p = Paint()
        stars.forEach { s ->
            val x = s[0] * w
            val y = s[1] * h
            p.color = Color.argb((s[2] * 70).toInt(), 230, 220, 200)
            c.drawCircle(x, y, s[2] * 1.4f, p)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragY = e.y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = dragY - e.y
                if (abs(dy) > height * 0.10f && System.currentTimeMillis() - lastTurn > 520) {
                    turn(if (dy > 0) 1 else -1)
                    lastTurn = System.currentTimeMillis()
                    dragY = e.y
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
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
        animate().cancel()
        alpha = 0.55f
        animate().alpha(1f).setDuration(260).setInterpolator(DecelerateInterpolator()).start()
        invalidate()
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
