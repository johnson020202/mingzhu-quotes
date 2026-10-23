package com.mingzhu.core

import android.graphics.Paint

/**
 * 中文折行：按字素测量宽度，避头标点不落行首；
 * 末行只剩一个字时把上一行末字拉下来，避免出现孤字行。
 */
object Wrap {

    private const val NO_START = "，。、；：？！）》」』】〕…—％”’·"
    private const val NO_END = "（《「『【〔“‘"

    fun split(text: String, paint: Paint, maxWidth: Float): List<String> {
        val src = text.replace(Regex("\\s+"), " ").trim()
        if (src.isEmpty()) return emptyList()
        val out = ArrayList<String>()
        val buf = StringBuilder()
        var w = 0f

        for (ch in src) {
            val cw = paint.measureText(ch.toString())
            val punct = NO_START.indexOf(ch) >= 0
            if (w + cw > maxWidth && buf.isNotEmpty() && !punct) {
                if (NO_END.indexOf(buf.last()) >= 0 && buf.length > 1) {
                    val carry = buf.last()
                    buf.setLength(buf.length - 1)
                    out.add(buf.toString())
                    buf.setLength(0)
                    buf.append(carry)
                    w = paint.measureText(carry.toString())
                } else {
                    out.add(buf.toString())
                    buf.setLength(0)
                    w = 0f
                }
            }
            buf.append(ch)
            w += cw
        }
        if (buf.isNotEmpty()) out.add(buf.toString())

        while (out.size >= 2 && out.last().length == 1) {
            val prev = out[out.size - 2]
            if (prev.length <= 2) break
            out[out.size - 2] = prev.dropLast(1)
            out[out.size - 1] = prev.last() + out.last()
        }
        return out
    }

    /** 句尾零句号：中文句号与英文句点隐去，问号叹号保留。 */
    fun trimEnd(s: String): String {
        var t = s.trim()
        if (t.endsWith("。")) t = t.dropLast(1) else if (t.endsWith(".")) t = t.dropLast(1)
        return t
    }
}
