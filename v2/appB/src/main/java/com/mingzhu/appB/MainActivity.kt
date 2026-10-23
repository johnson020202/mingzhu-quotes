package com.mingzhu.appB

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.mingzhu.core.Store
import com.mingzhu.core.Typography2

class MainActivity : AppCompatActivity() {
    private lateinit var store: Store
    private lateinit var view: BookView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        store = Store(this)
        Typography2.install(this, store.font)
        view = BookView(this)
        setContentView(view)
        handleTestIntent(intent)
    }

    private fun handleTestIntent(i: android.content.Intent) {
        val n = i.getIntExtra("next", 0)
        if (n != 0) view.postDelayed({ repeat(kotlin.math.abs(n)) {
            view.dispatchTouchEvent(android.view.MotionEvent.obtain(0, 0, android.view.MotionEvent.ACTION_DOWN, 500f, 1200f, 0))
            view.dispatchTouchEvent(android.view.MotionEvent.obtain(0, 1, android.view.MotionEvent.ACTION_UP, 500f, 900f, 0))
        } }, 400)
        if (i.getBooleanExtra("poster", false)) view.postDelayed({ exportTest() }, 700)
    }

    private fun exportTest() {
        try {
            val bmp = com.mingzhu.core.Share.capture(view, 2.4f)
            val full = com.mingzhu.core.Share.withColophon(bmp, 0xFFF4EFE4.toInt(), 0xFF1F1D1A.toInt(), 0xFFA8452F.toInt(), java.time.LocalDate.now(), "明 著 · 书页")
            com.mingzhu.core.Share.save(this, full, "poster_test")
            Toast.makeText(this, "海报 ${full.width}x${full.height}", Toast.LENGTH_LONG).show()
        } catch (t: Throwable) {
            Toast.makeText(this, "失败 ${t.message}", Toast.LENGTH_LONG).show()
        }
    }
}
