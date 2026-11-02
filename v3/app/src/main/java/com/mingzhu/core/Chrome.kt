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

    /**
     * 字体选择：内置三款直接选中；其余七款显示「下载」，
     * 点一下从服务器拉取，完成后自动应用。
     */
    fun pickFont(activity: Activity, current: AppFont, onPicked: (AppFont) -> Unit) {
        val all = AppFont.values()
        val labels = all.map { f ->
            val state = when {
                f.id == current.id -> "● 当前"
                Typography2.isAvailable(activity, f) -> ""
                else -> "需下载"
            }
            listOf(f.label, f.desc, f.license, state).filter { it.isNotEmpty() }.joinToString("   ")
        }.toTypedArray()

        fun apply(f: AppFont) {
            Typography2.install(activity, f)
            Store(activity).font = f
            onPicked(f)
        }

        AlertDialog.Builder(activity)
            .setTitle("选择字体（内置三款，其余可下载）")
            .setSingleChoiceItems(labels, all.indexOf(current)) { d, which ->
                val f = all[which]
                if (Typography2.isAvailable(activity, f)) {
                    apply(f); d.dismiss()
                } else {
                    d.dismiss()
                    val wait = AlertDialog.Builder(activity)
                        .setMessage("正在下载「${f.label}」…")
                        .setCancelable(false)
                        .create()
                    wait.show()
                    Typography2.download(activity, f) { ok, msg ->
                        activity.runOnUiThread {
                            wait.dismiss()
                            if (ok) {
                                apply(f)
                                Toast.makeText(activity, "${f.label} $msg，已启用", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(activity, "下载失败：$msg", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
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
