package com.lemon.mingzhu

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.view.View
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * 海报导出：把屏幕上的卡片按 2.6 倍超采样重绘为图片，再补一条落款带。
 */
object Poster {

    fun capture(view: View, scale: Float = 2.6f): Bitmap {
        val w = (view.width * scale).toInt().coerceAtLeast(1)
        val h = (view.height * scale).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(scale, scale)
        view.draw(c)
        return bmp
    }

    /** 底部落款：左侧日期，右侧"明 著 摘 录"，上沿一条细线。 */
    fun withColophon(
        bmp: Bitmap,
        bg: Int,
        ink: Int,
        accent: Int,
        dateText: String,
        brand: String = "明 著 摘 录",
    ): Bitmap {
        val pad = (bmp.width * 0.075f).toInt()
        val band = (bmp.width * 0.135f).toInt()
        val out = Bitmap.createBitmap(bmp.width + pad * 2, bmp.height + band + pad, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(bg)
        c.drawBitmap(bmp, pad.toFloat(), pad.toFloat(), null)

        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.typeface = UIFont.body
        p.textSize = bmp.width * 0.034f
        val baseY = bmp.height + pad + band * 0.72f

        p.color = accent
        p.letterSpacing = 0.06f
        c.drawText(dateText, pad.toFloat(), baseY, p)

        p.color = ink
        p.alpha = 210
        p.textAlign = Paint.Align.RIGHT
        c.drawText(brand, (out.width - pad).toFloat(), baseY, p)
        p.alpha = 255
        p.textAlign = Paint.Align.LEFT
        p.letterSpacing = 0f

        p.color = accent
        p.alpha = 80
        p.style = Paint.Style.STROKE
        p.strokeWidth = bmp.width * 0.0018f
        val y = bmp.height + pad + band * 0.22f
        c.drawLine(pad.toFloat(), y, (out.width - pad).toFloat(), y, p)
        return out
    }

    fun save(context: Context, bmp: Bitmap, name: String = "明著摘录"): File {
        val dir = File(context.cacheDir, "posters").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val f = File(dir, name + "_" + System.currentTimeMillis() + ".png")
        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f
    }

    fun share(context: Context, file: File, title: String): Intent {
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

    fun roundedRect(l: Float, t: Float, r: Float, b: Float, rad: Float) = RectF(l, t, r, b)
}

/** 全局字体：正文全部用得意黑斜体，小字与数字用等宽体拉开层次。 */
object UIFont {
    var headline: Typeface = Typeface.DEFAULT
    var body: Typeface = Typeface.DEFAULT
    var meta: Typeface = Typeface.MONOSPACE

    fun install(context: Context) {
        headline = try {
            Typeface.createFromAsset(context.assets, "fonts/smiley.otf")
        } catch (t: Throwable) {
            Typeface.DEFAULT
        }
        body = headline
        meta = Typeface.create("monospace", Typeface.NORMAL)
    }
}
