package com.lemon.mingzhu

import android.graphics.Paint

/**
 * 中文折行：按字素测量宽度，避头尾标点；末行孤字时把上一行末字拉下来，
 * 让整段呈现"两端顿齐"的节奏而不是机械断行。
 */
object Typography {

    private const val NO_LINE_START = "，。、；：？！）》」』】〕…—％”’·"
    private const val NO_LINE_END = "（《「『【〔“‘"

    fun wrap(text: String, paint: Paint, width: Float): List<String> {
        val clean = text.replace(Regex("\\s+"), " ").trim()
        if (clean.isEmpty()) return emptyList()
        val lines = ArrayList<String>()
        val sb = StringBuilder()
        var w = 0f
        for (ch in clean) {
            val cw = paint.measureText(ch.toString())
            val isPunct = NO_LINE_START.indexOf(ch) >= 0
            if (w + cw > width && sb.isNotEmpty() && !isPunct) {
                if (NO_LINE_END.indexOf(sb.last()) >= 0 && sb.length > 1) {
                    val moved = sb.last()
                    sb.setLength(sb.length - 1)
                    lines.add(sb.toString())
                    sb.setLength(0)
                    sb.append(moved)
                    w = paint.measureText(moved.toString())
                } else {
                    lines.add(sb.toString())
                    sb.setLength(0)
                    w = 0f
                }
            }
            sb.append(ch)
            w += cw
        }
        if (sb.isNotEmpty()) lines.add(sb.toString())

        while (lines.size >= 2 && lines.last().length == 1) {
            val prev = lines[lines.size - 2]
            if (prev.length <= 2) break
            val moved = prev.last()
            lines[lines.size - 2] = prev.dropLast(1)
            lines[lines.size - 1] = moved + lines.last()
        }
        return lines
    }

    /** 句尾零句号：中文句号与英文句点不落在最后，问号叹号省略号保留。 */
    fun trimPeriod(s: String): String {
        var t = s.trim()
        if (t.endsWith("。")) t = t.dropLast(1)
        else if (t.endsWith(".")) t = t.dropLast(1)
        return t
    }
}
