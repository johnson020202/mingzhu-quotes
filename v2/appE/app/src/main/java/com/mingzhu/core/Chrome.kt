package com.mingzhu.core

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Bitmap
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/**
 * 每个版本共用的两个入口：字体选择、分享海报。
 * 按钮由各版本自己摆放，这里只提供行为。
 */
object Chrome {

    /** 弹字体选择：选完立即回调，让当前界面重绘。 */
    fun pickFont(activity: Activity, current: AppFont, onPicked: (AppFont) -> Unit) {
        val available = Fonts2.available()
        val names = available.map { "${it.label}   ${it.desc}   ${it.license}" }.toTypedArray()
        AlertDialog.Builder(activity)
            .setTitle("选择字体（默认得意黑）")
            .setSingleChoiceItems(names, available.indexOf(current)) { d, which ->
                val f = available[which]
                Typography2.install(activity, f)
                Store(activity).font = f
                onPicked(f)
                d.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /** 导出并调起系统分享。 */
    fun shareBitmap(activity: Activity, bmp: Bitmap, title: String) {
        try {
            val f: File = Share.save(activity, bmp, "mingzhu_" + System.currentTimeMillis())
            activity.startActivity(Share.intent(activity, f, title))
        } catch (t: Throwable) {
            Toast.makeText(activity, "导出失败：" + t.message, Toast.LENGTH_SHORT).show()
        }
    }

    /** 仅导出到 filesDir，方便自测或用户后续取用。 */
    fun saveOnly(activity: Activity, bmp: Bitmap, name: String = "poster_test"): File =
        Share.save(activity, bmp, name)
}
