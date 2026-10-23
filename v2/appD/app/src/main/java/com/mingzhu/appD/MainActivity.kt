package com.mingzhu.appD

import android.os.Bundle
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.mingzhu.core.ActionRow
import com.mingzhu.core.AppFont
import com.mingzhu.core.Chrome
import com.mingzhu.core.Library
import com.mingzhu.core.Store
import com.mingzhu.core.Typography2
import java.time.LocalDate

class MainActivity : AppCompatActivity() {

    private lateinit var store: Store
    private lateinit var content: ShelfView
    private lateinit var row: ActionRow

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        store = Store(this)
        Typography2.install(this, store.font)
        Typography2.warmUp(this)

        content = ShelfView(this)
        row = ActionRow(
            this,
            onFont = { Chrome.pickFont(this, Typography2.currentFont()) { refreshAll() } },
            onShare = { Chrome.shareBitmap(this, content.exportPoster(), shareTitle()) },
            onExtra = { if (content.goToday()) {} else { /* 已在今天 */ }; content.invalidate() },
            extraLabel = "回到今天",
        ).apply {
            ink = 0xFF22211E.toInt()
            accent = 0xFF2F3A33.toInt()
            fill = 0xFFF0E9DC.toInt()
        }
        row.fontLabel = "字体"

        val d = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFF0E9DC.toInt())
        }
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(row, LinearLayout.LayoutParams(-1, (64 * d).toInt()))
        setContentView(root)

        handleTest(intent)
    }

    private fun refreshAll() {
        row.fontLabel = "字体"
        row.invalidate()
        content.invalidate()
    }

    private fun shareTitle(): String = "明著摘录"

    private fun handleTest(i: android.content.Intent) {
        if (i.getBooleanExtra("poster", false)) {
            content.postDelayed({
                try {
                    val bmp = content.exportPoster()
                    val f = com.mingzhu.core.Share.save(this, bmp, "poster_test")
                    android.widget.Toast.makeText(this, "海报 ${bmp.width}x${bmp.height}", android.widget.Toast.LENGTH_LONG).show()
                } catch (t: Throwable) {
                    android.widget.Toast.makeText(this, "失败 ${t.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }, 800)
        }
        val n = i.getIntExtra("next", 0)
        if (n != 0) content.postDelayed({ repeat(kotlin.math.abs(n)) { content.turnPage(if (n > 0) 1 else -1) } }, 500)
        if (i.getBooleanExtra("font", false)) {
            content.postDelayed({
                val all = AppFont.values()
                val cur = all.indexOf(Typography2.currentFont())
                val next = all[(cur + 1) % all.size]
                Typography2.install(this, next)
                store.font = next
                refreshAll()
            }, 500)
        }
    }
}
