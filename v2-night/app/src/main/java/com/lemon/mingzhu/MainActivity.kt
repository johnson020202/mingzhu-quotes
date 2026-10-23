package com.lemon.mingzhu

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import java.io.File

/**
 * 上方是纯绘制的内容区，下方是原生操作条（上一篇 / 下一篇 / 分享海报）。
 * 两者分离，导出图片时不受按钮影响。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var store: QuoteStore
    private lateinit var canvas: MainView
    private lateinit var bar: ActionBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        UIFont.install(this)
        store = QuoteStore(this)
        store.syncDaily()

        val d = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
        }
        canvas = MainView(this, store)
        root.addView(canvas, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        bar = ActionBar(
            this,
            onPrev = { canvas.turn(-1) },
            onNext = { canvas.turn(1) },
            onShare = { sharePoster() },
        ).apply {
            ink = INK
            accent = ACCENT
            fill = FILL
            label = "分享海报"
        }
        root.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (82 * d).toInt()))

        setContentView(root)
        handleTestIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleTestIntent(intent)
    }

    fun next() = canvas.turn(1)
    fun prev() = canvas.turn(-1)

    fun sharePoster() {
        try {
            val bmp = canvas.share()
            val f = Poster.save(this, bmp)
            startActivity(Poster.share(this, f, store.current().book))
        } catch (t: Throwable) {
            Toast.makeText(this, "导出失败：" + t.message, Toast.LENGTH_SHORT).show()
        }
    }

    fun copyQuote() {
        val q = store.current()
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("摘抄", q.text + "\n——《${q.book}》 ${q.author}"))
        Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show()
    }

    private fun handleTestIntent(i: Intent) {
        val n = i.getIntExtra("next", 0)
        if (n != 0) Handler(Looper.getMainLooper()).postDelayed({
            repeat(kotlin.math.abs(n)) { if (n > 0) next() else prev() }
        }, 300)
        if (i.getBooleanExtra("poster", false)) Handler(Looper.getMainLooper()).postDelayed({ exportForTest() }, 700)
        if (i.getBooleanExtra("share", false)) Handler(Looper.getMainLooper()).postDelayed({ sharePoster() }, 700)
    }

    private fun exportForTest() {
        try {
            val bmp = canvas.share()
            Poster.save(this, bmp)
            val out = File(filesDir, "poster_test.png")
            out.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            Toast.makeText(this, "海报已导出 ${bmp.width}x${bmp.height}", Toast.LENGTH_LONG).show()
        } catch (t: Throwable) {
            Toast.makeText(this, "导出失败：" + t.message, Toast.LENGTH_LONG).show()
        }
    }

    private companion object {
        const val BG = 0xFF0B0D10.toInt()
        const val INK = 0xFFEFE7D8.toInt()
        const val ACCENT = 0xFFD9A253.toInt()
        const val FILL = 0xFF0B0D10.toInt()
    }
}
