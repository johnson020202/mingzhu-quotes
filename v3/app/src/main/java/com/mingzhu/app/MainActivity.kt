package com.mingzhu.app

import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.mingzhu.core.ActionRow
import com.mingzhu.core.Api
import com.mingzhu.core.AppFont
import com.mingzhu.core.Chrome
import com.mingzhu.core.Store
import com.mingzhu.core.Typography2
import com.mingzhu.ui.ArchiveView
import com.mingzhu.ui.BookView
import com.mingzhu.ui.CalendarView
import com.mingzhu.ui.ShelfView
import com.mingzhu.ui.TimelineView

/**
 * 一个 App，五套界面。底部 TabBar 切换，顶部操作条跟随当前界面的主题配色。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var store: Store
    private lateinit var stack: FrameLayout
    private lateinit var tabs: TabBar
    private lateinit var row: ActionRow

    private var current = 0
    private var calendar: CalendarView? = null
    private var book: BookView? = null
    private var timeline: TimelineView? = null
    private var shelf: ShelfView? = null
    private var archive: ArchiveView? = null

    /** 每套界面的主题：背景 / 主文字 / 强调 / 按钮文字。 */
    private data class Theme(val bg: Int, val ink: Int, val accent: Int, val onAccent: Int, val dark: Boolean)

    private val themes = listOf(
        Theme(0xFFFBF8F2.toInt(), 0xFF20201D.toInt(), 0xFF3E6B57.toInt(), 0xFFFBF8F2.toInt(), false),
        Theme(0xFFF4EFE4.toInt(), 0xFF1F1D1A.toInt(), 0xFFA8452F.toInt(), 0xFFF4EFE4.toInt(), false),
        Theme(0xFF12151A.toInt(), 0xFFEDE6D6.toInt(), 0xFFD8A657.toInt(), 0xFF12151A.toInt(), true),
        Theme(0xFFF0E9DC.toInt(), 0xFF22211E.toInt(), 0xFF2F3A33.toInt(), 0xFFF0E9DC.toInt(), false),
        Theme(0xFFFCFBF7.toInt(), 0xFF1B1B19.toInt(), 0xFF2D5D48.toInt(), 0xFFFCFBF7.toInt(), false),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        store = Store(this)
        Typography2.install(this, store.font)

        val d = resources.displayMetrics.density
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        stack = FrameLayout(this)
        root.addView(stack, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        row = ActionRow(
            this,
            onFont = { Chrome.pickFont(this, Typography2.currentFont()) { refresh() } },
            onShare = { shareCurrent() },
            onExtra = { goToday() },
            extraLabel = "回到今天",
        )
        root.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (60 * d).toInt()))

        tabs = TabBar(this) { i -> show(i) }
        root.addView(tabs, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (60 * d).toInt()))

        setContentView(root)
        show(0)
        applyTheme(0)
    }

    private fun applyTheme(i: Int) {
        val t = themes[i]
        window.decorView.setBackgroundColor(t.bg)
        row.ink = t.ink
        row.accent = t.accent
        row.fill = t.onAccent
        row.invalidate()
        tabs.ink = t.ink
        tabs.accent = t.accent
        tabs.bg = t.bg
        tabs.dark = t.dark
        tabs.invalidate()

        val light = if (t.dark) 0 else android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        window.decorView.systemUiVisibility = light
    }

    private fun show(i: Int) {
        current = i
        stack.removeAllViews()
        val v = when (i) {
            0 -> calendar ?: CalendarView(this).also { calendar = it }
            1 -> book ?: BookView(this).also { book = it }
            2 -> timeline ?: TimelineView(this).also { timeline = it }
            3 -> shelf ?: ShelfView(this).also { shelf = it }
            else -> archive ?: ArchiveView(
                this,
                isFav = { b, q -> store.isFav(b, q) },
                onToggleFav = { b, q -> store.toggleFav(b, q) },
            ).also { archive = it }
        }
        stack.addView(v, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        tabs.index = i
        applyTheme(i)
    }

    private fun refresh() {
        (0 until stack.childCount).forEach { stack.getChildAt(it).invalidate() }
        row.invalidate()
        tabs.invalidate()
    }

    private fun goToday(): Boolean {
        val ok = when (current) {
            0 -> calendar?.goToday() ?: false
            1 -> book?.goToday() ?: false
            2 -> timeline?.goToday() ?: false
            3 -> shelf?.goToday() ?: false
            else -> archive?.goToday() ?: false
        }
        refresh()
        return ok
    }

    private fun shareCurrent() {
        try {
            val bmp = when (current) {
                0 -> calendar!!.exportPoster()
                1 -> book!!.exportPoster()
                2 -> timeline!!.exportPoster()
                3 -> shelf!!.exportPoster()
                else -> archive!!.exportPoster()
            }
            Chrome.shareBitmap(this, bmp, "明著摘录")
            Api.track("share")
        } catch (t: Throwable) {
            Toast.makeText(this, "导出失败：" + (t.message ?: "未知错误"), Toast.LENGTH_LONG).show()
        }
    }

    override fun onPause() {
        super.onPause()
        store.font = Typography2.currentFont()
    }
}
