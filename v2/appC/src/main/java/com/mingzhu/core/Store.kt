package com.mingzhu.core

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate

/**
 * 本地状态：字体选择、收藏、每日阅读记录。
 * 阅读记录按日期落盘，所以历史日期即使离线也能回看，
 * 服务器只负责下发内容，不存在"没网就读不了昨天"的问题。
 */
class Store(context: Context) {

    private val sp: SharedPreferences = context.getSharedPreferences("mingzhu_v2", Context.MODE_PRIVATE)

    var font: AppFont
        get() = AppFont.byId(sp.getString("font", AppFont.DEFAULT.id))
        set(v) {
            sp.edit().putString("font", v.id).apply()
        }

    /** 记录"这天读过"，用于日历上打点。 */
    fun markRead(date: LocalDate) {
        sp.edit().putStringSet("read", sp.getStringSet("read", emptySet())!! + date.toString()).apply()
    }

    fun hasRead(date: LocalDate): Boolean = sp.getStringSet("read", emptySet())!!.contains(date.toString())

    fun readCount(): Int = sp.getStringSet("read", emptySet())!!.size

    fun toggleFav(bookId: String, quoteIndex: Int): Boolean {
        val set = sp.getStringSet("fav", emptySet())!!.toMutableSet()
        val key = "$bookId#$quoteIndex"
        val added = if (set.contains(key)) { set.remove(key); false } else { set.add(key); true }
        sp.edit().putStringSet("fav", set).apply()
        return added
    }

    fun isFav(bookId: String, quoteIndex: Int): Boolean =
        sp.getStringSet("fav", emptySet())!!.contains("$bookId#$quoteIndex")

    fun favKeys(): Set<String> = sp.getStringSet("fav", emptySet())!!

    /** 上次打开时停在哪个日期，方便跨天后续读。 */
    var lastDate: LocalDate
        get() = runCatching { LocalDate.parse(sp.getString("last_date", LocalDate.now().toString())) }
            .getOrDefault(LocalDate.now())
        set(v) = sp.edit().putString("last_date", v.toString()).apply()

    // 服务器同步状态：只记录是否成功，不缓存敏感信息
    var lastSyncOk: Boolean
        get() = sp.getBoolean("sync_ok", false)
        set(v) = sp.edit().putBoolean("sync_ok", v).apply()

    var lastSyncAt: Long
        get() = sp.getLong("sync_at", 0L)
        set(v) = sp.edit().putLong("sync_at", v).apply()
}
