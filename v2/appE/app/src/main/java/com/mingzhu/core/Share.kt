package com.mingzhu.core

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.view.View
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate

/**
 * 海报导出：按给定尺寸重绘一次版面再补落款，
 * 所以导出图里不会出现按钮，也不依赖屏幕当前状态。
 */
object Share {

    fun render(draw: (Canvas, Float, Float) -> Unit, w: Int, h: Int, scale: Float = 2.4f): Bitmap {
        val bw = (w * scale).toInt()
        val bh = (h * scale).toInt()
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(scale, scale)
        draw(c, w.toFloat(), h.toFloat())
        return bmp
    }

    fun withColophon(bmp: Bitmap, bg: Int, ink: Int, accent: Int, date: LocalDate, brand: String): Bitmap {
        val pad = (bmp.width * 0.072f).toInt()
        val band = (bmp.width * 0.13f).toInt()
        val out = Bitmap.createBitmap(bmp.width + pad * 2, bmp.height + band + pad, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(bg)
        c.drawBitmap(bmp, pad.toFloat(), pad.toFloat(), null)

        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.typeface = Typography2.body
        p.textSize = bmp.width * 0.032f
        p.color = accent
        p.letterSpacing = 0.05f
        val baseY = bmp.height + pad + band * 0.70f
        c.drawText("${date.year}.${"%02d".format(date.monthValue)}.${"%02d".format(date.dayOfMonth)}", pad.toFloat(), baseY, p)

        p.color = ink
        p.alpha = 200
        p.textAlign = Paint.Align.RIGHT
        c.drawText(brand, (out.width - pad).toFloat(), baseY, p)
        p.alpha = 255
        p.textAlign = Paint.Align.LEFT
        p.letterSpacing = 0f

        p.color = accent
        p.alpha = 70
        p.style = Paint.Style.STROKE
        p.strokeWidth = bmp.width * 0.0016f
        val y = bmp.height + pad + band * 0.20f
        c.drawLine(pad.toFloat(), y, (out.width - pad).toFloat(), y, p)
        return out
    }

    fun save(context: Context, bmp: Bitmap, name: String): File {
        val dir = File(context.filesDir, "posters").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val f = File(dir, name + ".png")
        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f
    }

    fun intent(context: Context, file: File, title: String): Intent {
        val uri: Uri = FileProvider.getUriForFile(context, context.packageName + ".posts", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "分享摘抄海报").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** 直接抓一个已经排好版的 View，用于分享当前画面。 */
    fun capture(v: View, scale: Float = 2.4f): Bitmap {
        val bmp = Bitmap.createBitmap(
            (v.width * scale).toInt().coerceAtLeast(1),
            (v.height * scale).toInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        val c = Canvas(bmp)
        c.scale(scale, scale)
        v.draw(c)
        return bmp
    }
}
