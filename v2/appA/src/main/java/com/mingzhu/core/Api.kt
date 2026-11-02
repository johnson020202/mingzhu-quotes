package com.mingzhu.core

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

/**
 * 内容服务客户端。
 * 本地 Books.kt 始终是权威副本，服务器只做补充（搜索、随机、内容版本），
 * 所以断网或服务器故障时 App 功能不受影响。
 */
object Api {

    const val BASE = "https://api.dognosay.dpdns.org/mingzhu"

    data class Status(val reachable: Boolean, val version: String, val latencyMs: Long)
    data class Hit(val bookId: String, val title: String, val author: String, val index: Int, val quote: String)

    private fun get(path: String, timeout: Int = 6000): String? = try {
        val c = (URL(BASE + path).openConnection() as HttpURLConnection).apply {
            connectTimeout = timeout
            readTimeout = timeout
            requestMethod = "GET"
        }
        val body = if (c.responseCode == 200) c.inputStream.bufferedReader().readText() else null
        c.disconnect()
        body
    } catch (_: Throwable) {
        null
    }

    /** 探活：拿到服务器上的内容版本，用于判断本地数据是否需要更新。 */
    fun probe(onResult: (Status) -> Unit) {
        thread(isDaemon = true) {
            val t0 = System.currentTimeMillis()
            val body = get("/health", 5000)
            val ver = body?.let { Regex("\"version\"\\s*:\\s*\"([^\"]+)\"").find(it)?.groupValues?.get(1) } ?: ""
            onResult(Status(body != null, ver, System.currentTimeMillis() - t0))
        }
    }

    /** 全文搜索；失败返回空列表，调用方自行回落到本地过滤。 */
    fun search(q: String, limit: Int = 30): List<Hit> {
        if (q.isBlank()) return emptyList()
        val enc = URLEncoder.encode(q, "UTF-8")
        val body = get("/api/search?q=$enc&limit=$limit") ?: return emptyList()
        val hits = ArrayList<Hit>()
        Regex("\\{\"bookId\":\"(.*?)\",\"title\":\"(.*?)\",\"author\":\"(.*?)\",\"quoteIndex\":(\\d+),\"quote\":\"(.*?)\"\\}")
            .findAll(body).forEach { m ->
                hits.add(Hit(m.groupValues[1], m.groupValues[2], m.groupValues[3],
                    m.groupValues[4].toIntOrNull() ?: 0, m.groupValues[5]))
            }
        return hits
    }

    /** 本地搜索兜底：服务器不可用时按同样语义在本地过滤。 */
    fun searchLocal(q: String, limit: Int = 30): List<Hit> {
        if (q.isBlank()) return emptyList()
        val out = ArrayList<Hit>()
        for (b in Library.books) {
            for ((i, line) in b.lines.withIndex()) {
                if (line.contains(q)) {
                    out.add(Hit(b.id, b.title, b.author, i, line))
                    if (out.size >= limit) return out
                }
            }
        }
        return out
    }

    /** 先走服务器，失败自动回落本地。 */
    fun searchSmart(q: String): List<Hit> {
        val remote = search(q)
        return if (remote.isNotEmpty()) remote else searchLocal(q)
    }

    /** 随机一句；服务器不可用时本地随机。 */
    fun random(bookId: String? = null): Hit {
        val path = if (bookId.isNullOrBlank()) "/api/random" else "/api/random?book=$bookId"
        val body = get(path)
        if (body != null) {
            val m = Regex("\"quote\":\"(.*?)\"").find(body)
            val bid = Regex("\"id\":\"(.*?)\"").find(body)?.groupValues?.get(1)
            if (m != null && bid != null) {
                val b = Library.byId(bid)
                if (b != null) return Hit(b.id, b.title, b.author, 0, m.groupValues[1])
            }
        }
        val b = if (bookId.isNullOrBlank()) Library.books.random() else (Library.byId(bookId) ?: Library.books.random())
        val i = (0 until b.count).random()
        return Hit(b.id, b.title, b.author, i, b.lines[i])
    }

    /** 上报一个轻量事件（分享、读完全书等）。失败静默。 */
    fun track(event: String) {
        thread(isDaemon = true) {
            try {
                val c = (URL("$BASE/api/track").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 4000; readTimeout = 4000
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
                c.outputStream.use { it.write("{\"event\":\"$event\"}".toByteArray()) }
                c.responseCode
                c.disconnect()
            } catch (_: Throwable) { }
        }
    }
}
