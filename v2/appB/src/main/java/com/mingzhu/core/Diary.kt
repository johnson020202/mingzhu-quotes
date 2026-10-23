package com.mingzhu.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 每日一书：给定日期，算出当天该读哪本书、读到第几句。
 * 全部基于日期离线推算，所以在没有网络时也能翻看任意历史日期。
 */
object Diary {

    /** 纪元日。这天读第 0 本书。 */
    private val EPOCH: LocalDate = LocalDate.of(2025, 1, 1)

    fun dayIndex(date: LocalDate): Long = ChronoUnit.DAYS.between(EPOCH, date)

    fun bookOn(date: LocalDate): Book = Library.bookForDay(dayIndex(date))

    /** 一天的记录：日期 + 书 + 当天主推的那一句（第几天读第几句，书内循环）。 */
    fun entryOn(date: LocalDate): DayEntry {
        val book = bookOn(date)
        val d = dayIndex(date)
        val qi = (((d % book.count) + book.count) % book.count).toInt()
        return DayEntry(date, book, qi)
    }

    /** 最近 n 天，从今天往前。 */
    fun recent(from: LocalDate, n: Int): List<DayEntry> =
        (0 until n).map { entryOn(from.minusDays(it.toLong())) }

    /** 某个月里所有天（含空白对齐）。 */
    fun monthGrid(year: Int, month: Int): List<LocalDate?> {
        val first = LocalDate.of(year, month, 1)
        val len = first.lengthOfMonth()
        val lead = first.dayOfWeek.value - 1  // 周一为一周之首
        val cells = ArrayList<LocalDate?>()
        repeat(lead) { cells.add(null) }
        for (d in 1..len) cells.add(LocalDate.of(year, month, d))
        while (cells.size % 7 != 0) cells.add(null)
        return cells
    }

    /** 这本书在 App 里第一次出现的日期（用来判断"首次阅读"）。 */
    fun firstDateOf(book: Book): LocalDate {
        var d = EPOCH
        repeat(Library.books.size * 2) {
            if (bookOn(d).id == book.id) return d
            d = d.plusDays(1)
        }
        return EPOCH
    }
}

data class DayEntry(val date: LocalDate, val book: Book, val quoteIndex: Int) {
    val quote: String get() = book.lines[quoteIndex.coerceIn(0, book.count - 1)]
    val isToday: Boolean get() = date == LocalDate.now()
}
