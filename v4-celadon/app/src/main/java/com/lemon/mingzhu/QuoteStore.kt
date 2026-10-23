package com.lemon.mingzhu

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 备忘录：今天读到哪一条、收藏过什么、语言与字号。
 * 每天首次打开时按"日期序号"推进，保证同一天多次打开看到同一条。
 */
class QuoteStore(context: Context) {

    private val sp: SharedPreferences = context.getSharedPreferences("mingzhu", Context.MODE_PRIVATE)

    var lang: QuoteLang
        get() = if (sp.getString(KEY_LANG, "zh") == "en") QuoteLang.EN else QuoteLang.ZH
        set(v) = sp.edit().putString(KEY_LANG, v.code).apply()

    /** 当前索引：按语言分别记忆。 */
    fun index(lang: QuoteLang): Int = sp.getInt(KEY_INDEX + lang.code, DEFAULT_INDEX(lang)).mod(QuoteRepo.count(lang))

    fun setIndex(lang: QuoteLang, i: Int) {
        val n = QuoteRepo.count(lang)
        sp.edit().putInt(KEY_INDEX + lang.code, ((i % n) + n) % n).apply()
    }

    fun current(): Quote = QuoteRepo.all(lang)[index(lang)]

    fun next(): Quote {
        setIndex(lang, index(lang) + 1)
        return current()
    }

    fun prev(): Quote {
        setIndex(lang, index(lang) - 1)
        return current()
    }

    /** 日期变了就自动翻到"今天"该看的那一条（每天一条，基于 2026-10-23 起算的日序）。 */
    fun syncDaily() {
        val today = dayStamp()
        val last = sp.getLong(KEY_LAST_DAY, 0L)
        if (last == today) return
        val anchor = sp.getLong(KEY_ANCHOR_DAY, today)
        if (last == 0L) {
            sp.edit().putLong(KEY_ANCHOR_DAY, today).putLong(KEY_LAST_DAY, today).apply()
            return
        }
        val gap = (today - anchor).toInt().coerceAtLeast(0)
        for (l in QuoteLang.values()) {
            val n = QuoteRepo.count(l)
            sp.edit().putInt(KEY_INDEX + l.code, (ROTATION_START(l) + gap) % n).apply()
        }
        sp.edit().putLong(KEY_LAST_DAY, today).apply()
    }

    private fun DEFAULT_INDEX(l: QuoteLang): Int = if (l == QuoteLang.ZH) 9 else 8

    private fun ROTATION_START(l: QuoteLang): Int = if (l == QuoteLang.ZH) 9 else 8

    private fun dayStamp(): Long {
        val cal = Calendar.getInstance()
        return cal.get(Calendar.YEAR) * 10000L + (cal.get(Calendar.MONTH) + 1) * 100L + cal.get(Calendar.DAY_OF_MONTH)
    }

    fun isFav(lang: QuoteLang, i: Int): Boolean = sp.getStringSet(KEY_FAV, emptySet())!!.contains("${lang.code}:$i")

    fun toggleFav(lang: QuoteLang, i: Int): Boolean {
        val s = sp.getStringSet(KEY_FAV, emptySet())!!.toMutableSet()
        val k = "${lang.code}:$i"
        val added = if (s.contains(k)) { s.remove(k); false } else { s.add(k); true }
        sp.edit().putStringSet(KEY_FAV, s).apply()
        return added
    }

    fun favCount(): Int = sp.getStringSet(KEY_FAV, emptySet())!!.size

    fun savedDrafts(): Int = sp.getInt(KEY_DRAFTS, 0)

    fun bumpDraft(): Int {
        val n = savedDrafts() + 1
        sp.edit().putInt(KEY_DRAFTS, n).apply()
        return n
    }

    fun themeUnlockedEarly(): Boolean = sp.getBoolean(KEY_UNLOCK, false)

    fun unlockTheme() = sp.edit().putBoolean(KEY_UNLOCK, true).apply()

    companion object {
        private const val KEY_LANG = "lang"
        private const val KEY_INDEX = "idx_"
        private const val KEY_LAST_DAY = "last_day"
        private const val KEY_ANCHOR_DAY = "anchor_day"
        private const val KEY_FAV = "fav"
        private const val KEY_DRAFTS = "drafts"
        private const val KEY_UNLOCK = "unlock"
    }
}

object Dates {
    private val WEEK = arrayOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")
    private val EN_MONTH = arrayOf("JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE",
        "JULY", "AUGUST", "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER")

    fun now(): Date = Date()

    /** 2026.10.23 */
    fun dotted(d: Date = now()): String = SimpleDateFormat("yyyy.MM.dd", Locale.CHINA).format(d)

    /** 2026 年 10 月 23 日 周五 */
    fun chinese(d: Date = now()): String {
        val c = Calendar.getInstance().apply { time = d }
        return "${c.get(Calendar.YEAR)} 年 ${c.get(Calendar.MONTH) + 1} 月 ${c.get(Calendar.DAY_OF_MONTH)} 日 ${WEEK[c.get(Calendar.DAY_OF_WEEK) - 1]}"
    }

    /** 十月二十三日 · 周五 */
    fun monthDay(d: Date = now()): String {
        val c = Calendar.getInstance().apply { time = d }
        return "十月${toCn(c.get(Calendar.DAY_OF_MONTH))}日"
    }

    /** OCTOBER 23, 2026 */
    fun english(d: Date = now()): String {
        val c = Calendar.getInstance().apply { time = d }
        return "${EN_MONTH[c.get(Calendar.MONTH)]} ${c.get(Calendar.DAY_OF_MONTH)}, ${c.get(Calendar.YEAR)}"
    }

    /** 一年中的第几天 / 全年天数 */
    fun dayOfYear(d: Date = now()): Pair<Int, Int> {
        val c = Calendar.getInstance().apply { time = d }
        return c.get(Calendar.DAY_OF_YEAR) to (if (c.get(Calendar.YEAR) % 4 == 0) 366 else 365)
    }

    private val CN = arrayOf("零", "一", "二", "三", "四", "五", "六", "七", "八", "九", "十")

    fun toCn(n: Int): String = when {
        n <= 10 -> CN[n]
        n < 20 -> "十" + CN[n - 10]
        n < 100 -> CN[n / 10] + "十" + (if (n % 10 == 0) "" else CN[n % 10])
        else -> n.toString()
    }
}
