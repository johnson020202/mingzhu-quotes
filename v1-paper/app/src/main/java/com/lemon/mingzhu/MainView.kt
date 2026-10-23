package com.lemon.mingzhu

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

/**
 * 版本一「纸页」：米白纸面、朱红小印、左右翻页。
 * 整屏自己绘制，保证分享出去的图片和屏幕上看到的一模一样。
 */
class MainView(context: Context, private val store: QuoteStore) : View(context) {

    private val ink = Color.parseColor("#211E19")
    private val inkSoft = Color.parseColor("#5A544A")
    private val accent = Color.parseColor("#A6392C")
    private val paper = Color.parseColor("#EAE2D1")
    private val card = Color.parseColor("#F6F1E6")
    private val hairline = Color.parseColor("#C9BFAA")

    private val pBody = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = UIFont.body
        color = ink
        letterSpacing = 0.02f
    }
    private val pMeta = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = UIFont.meta
        textSize = 32f
    }
    private val pAccent = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = UIFont.body
        color = accent
    }
    private val pLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.6f
        color = hairline
    }

    private var quote: Quote = store.current()
    private var dragX = 0f
    private var settle = 0f
    private var animator: ValueAnimator? = null
    private var lastTurn = 0L
    private var glyphTop = 0f
    private var glyphRight = 0f
    private var cardRect = RectF()

    private val grain: List<FloatArray> = run {
        val r = Random(7)
        (0 until 900).map { floatArrayOf(r.nextFloat(), r.nextFloat(), r.nextFloat()) }
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
        return Poster.withColophon(bmp, card, ink, accent, Dates.dotted())
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height * contentHeightScale
        drawContent(canvas, w, h)
    }

    /** 按给定尺寸完整绘制版面，导出时传入整屏高度。 */
    private fun drawContent(canvas: Canvas, w: Float, h: Float) {
        canvas.drawColor(paper)
        drawGrain(canvas, w, h)

        val pad = w * 0.085f
        val top = h * 0.115f
        cardRect.set(pad, top, w - pad, h - h * 0.10f)

        // 纸张投影：一层淡淡的错位影子
        val shadow = RectF(cardRect)
        shadow.offset(w * 0.014f, h * 0.009f)
        shadow.inset(-w * 0.006f, -w * 0.006f)
        canvas.drawRoundRect(shadow, w * 0.03f, w * 0.03f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#143A342A")
        })

        canvas.save()
        canvas.clipRect(cardRect)
        canvas.drawColor(card)
        drawGrain(canvas, w, h, cardOnly = true)
        drawPage(canvas, w, h, pad, top)
        canvas.restore()

        // 细边
        pLine.color = hairline
        canvas.drawRoundRect(cardRect, w * 0.02f, w * 0.02f, pLine)
    }

    private fun drawGrain(c: Canvas, w: Float, h: Float, cardOnly: Boolean = false) {
        val p = Paint()
        for (g in grain) {
            val x = g[0] * w
            val y = g[1] * h
            if (cardOnly && (y < cardRect.top || y > cardRect.bottom || x < cardRect.left || x > cardRect.right)) continue
            p.color = Color.argb((g[2] * 16).toInt(), 90, 74, 50)
            c.drawCircle(x, y, 0.8f + g[2] * 1.6f, p)
        }
    }

    private fun drawPage(c: Canvas, w: Float, h: Float, pad: Float, top: Float) {
        val innerL = pad + w * 0.085f
        val innerR = w - pad - w * 0.085f
        val colW = innerR - innerL

        // —— 顶部眉批 ——
        pMeta.typeface = UIFont.meta
        pMeta.textSize = w * 0.028f
        pMeta.color = accent
        pMeta.letterSpacing = 0.18f
        c.drawText("READING · 明著摘录", innerL, top + h * 0.052f, pMeta)

        pMeta.letterSpacing = 0f
        pMeta.typeface = UIFont.body
        pMeta.textSize = w * 0.055f
        pMeta.color = accent
        pMeta.textAlign = Paint.Align.RIGHT
        c.drawText(Dates.toCn(java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_MONTH)), innerR, top + h * 0.058f, pMeta)
        pMeta.textAlign = Paint.Align.LEFT

        pLine.color = hairline
        c.drawLine(innerL, top + h * 0.075f, innerR, top + h * 0.075f, pLine)

        // —— 正文 ——
        pBody.textSize = w * 0.088f
        val text = Typography.trimPeriod(quote.text)
        val zoneTop = top + h * 0.11f
        val maxBodyBottom = top + h * 0.575f
        var bodyTop = zoneTop
        val drop = pBody.textSize * 1.42f
        var lineH = pBody.textSize * 1.66f

        // 首字下沉：第一个字单独排，其余文字按剩下宽度折行
        val gap = pBody.textSize * 0.30f
        val restW = colW - drop * 0.92f - gap
        val restText = if (store.lang == QuoteLang.ZH) text.drop(1) else text
        var lines = Typography.wrap(restText, pBody, restW)
        while (bodyTop + drop + (lines.size - 1) * lineH > maxBodyBottom && pBody.textSize > w * 0.048f) {
            pBody.textSize *= 0.93f
            lineH = pBody.textSize * 1.66f
            lines = Typography.wrap(restText, pBody, restW)
        }
        // 垂直居中：短句不贴着眉批，长句不压到底部
        val blockH = drop + (lines.size - 1) * lineH
        bodyTop = zoneTop + (((maxBodyBottom - zoneTop) - blockH).coerceAtLeast(0f) / 2f)
                .coerceAtMost(h * 0.045f)

        // 首字
        pAccent.textSize = drop * 0.92f
        c.drawText(text.take(1), innerL, bodyTop + drop * 0.80f, pAccent)
        var y = bodyTop + pBody.textSize * 1.12f
        pBody.color = ink
        lines.forEachIndexed { i, line ->
            c.drawText(line, innerL + drop * 0.92f + gap, y, pBody)
            y += lineH
        }
        val bodyBottom = y - lineH + pBody.textSize * 0.35f

        // —— 引文与落款 ——
        val baseY = minOf(maxOf(bodyBottom + h * 0.10f, top + h * 0.40f), top + h * 0.62f)
        pAccent.textSize = w * 0.043f
        pAccent.color = accent
        pAccent.letterSpacing = 0.04f
        c.drawText("《${quote.book}》", innerL, baseY, pAccent)
        pAccent.color = inkSoft
        pAccent.letterSpacing = 0.01f
        c.drawText(quote.author + if (quote.origin.isNotEmpty()) " · ${quote.origin}" else "", innerL, baseY + w * 0.068f, pAccent)

        // —— 印章 ——
        val seal = w * 0.20f
        val sx = innerR - seal
        val sy = baseY - seal * 0.42f
        drawSeal(c, sx, sy, seal)

        // —— 页码与操作提示 ——
        val bottom = h * 0.90f - h * 0.06f
        pLine.color = hairline
        c.drawLine(innerL, bottom - h * 0.035f, innerR, bottom - h * 0.035f, pLine)
        pMeta.typeface = UIFont.meta
        pMeta.textSize = w * 0.027f
        pMeta.color = inkSoft
        pMeta.letterSpacing = 0.1f
        val n = QuoteRepo.count(store.lang)
        val i = store.index(store.lang) + 1
        c.drawText(Dates.dotted(), innerL, bottom, pMeta)
        pMeta.textAlign = Paint.Align.RIGHT
        c.drawText("${"%02d".format(i)} / ${"%02d".format(n)}", innerR, bottom, pMeta)
        pMeta.textAlign = Paint.Align.LEFT
        pMeta.letterSpacing = 0f

        // 底部留白处一枚极淡的书法感大字，作为版面的"压角"
        val ghost = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = UIFont.body
            color = accent
            alpha = 14
            textSize = w * 0.18f
        }
        c.drawText("讀", innerL - w * 0.01f, bottom - h * 0.115f, ghost)
        pMeta.textSize = w * 0.026f
        pMeta.color = inkSoft
        pMeta.alpha = 150
        pMeta.letterSpacing = 0.22f
        c.drawText("每 日 一 摘 · 慢 慢 读", innerL + w * 0.20f, bottom - h * 0.05f, pMeta)
        pMeta.alpha = 255
        pMeta.letterSpacing = 0f
    }

    /** 手绘印章：两个竖排字，用矩形笔画 + 缺口做出粗糙边。 */
    private fun drawSeal(c: Canvas, x: Float, y: Float, s: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
        p.style = Paint.Style.STROKE
        p.strokeWidth = s * 0.065f
        val r = RectF(x, y, x + s, y + s)
        c.drawRoundRect(r, s * 0.06f, s * 0.06f, p)
        p.style = Paint.Style.FILL
        p.typeface = UIFont.body
        p.textAlign = Paint.Align.CENTER
        p.textSize = s * 0.40f
        c.drawText("明", x + s * 0.5f, y + s * 0.46f, p)
        c.drawText("著", x + s * 0.5f, y + s * 0.94f, p)
        p.textAlign = Paint.Align.LEFT
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                animator?.cancel()
                settle = 0f
                dragX = 0f
                isPressed = true
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                dragX = e.x - width / 2f
                if (abs(dragX) > width * 0.12f && System.currentTimeMillis() - lastTurn > 520) {
                    turn(if (dragX < 0) 1 else -1)
                    lastTurn = System.currentTimeMillis()
                    dragX = 0f
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isPressed = false
                dragX = 0f
                performClick()
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(e)
    }

    fun turn(dir: Int) {
        quote = if (dir > 0) store.next() else store.prev()
        animator?.cancel()
        settle = dir * 1f
        animator = ValueAnimator.ofFloat(dir * 1f, 0f).apply {
            duration = 340
            interpolator = DecelerateInterpolator()
            addUpdateListener { a ->
                settle = a.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    /** 暴露给 Activity 的按钮操作。 */
    fun flick(dir: Int) {
        turn(dir)
        val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(8, 40))
    }
}
