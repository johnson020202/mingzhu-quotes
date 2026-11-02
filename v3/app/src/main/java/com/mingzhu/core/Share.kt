package com.mingzhu.core

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.view.View
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

    /**
     * 分享：优先把图片写进系统媒体库并用 MediaStore 的 content:// URI，
     * 这样不依赖 FileProvider 的 authorities 配置，也不会因为没有 file_paths 而崩。
     * 媒体库写入失败时才回落到 FileProvider。
     */
    fun intent(context: Context, file: File, title: String): Intent {
        val uri = publishToMediaStore(context, file)
            ?: throw IllegalStateException("海报写入相册失败，请检查存储权限")
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newRawUri("poster", uri)
        }
        return Intent.createChooser(send, "分享摘抄海报").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** 写入相册 Pictures/明著摘录，返回 content URI；失败返回 null。 */
    private fun publishToMediaStore(context: Context, file: File): Uri? = try {
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, file.name)
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,
                android.os.Environment.DIRECTORY_PICTURES + "/明著摘录")
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri == null) null else {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().copyTo(out) }
            uri
        }
    } catch (_: Throwable) {
        null
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
