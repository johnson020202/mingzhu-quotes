package com.mingzhu.core

import android.content.Context
import android.graphics.Typeface
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * 可切换字体。默认得意黑，其余均为 SIL OFL / IPA 许可的开源中文字体。
 * [asset] 指向 assets/fonts 下的文件名，缺字时回落到得意黑。
 */
enum class AppFont(
    val id: String,
    val label: String,
    val desc: String,
    val asset: String,
    val license: String,
    /** 是否随包内置；false 表示需联网下载。 */
    val builtin: Boolean,
) {
    SMILEY("smiley", "得意黑", "默认 · 锐利斜体", "smiley.otf", "OFL 1.1", true),
    MARKER("marker", "马克黑", "马克笔手写", "marker.ttf", "OFL 1.1", true),
    NEO_XIHEI("neo_xihei", "新细黑", "现代黑体", "neo_xihei.ttf", "IPA 1.0", true),
    WENKAI("wenkai", "文楷", "手写感 · 温和", "wenkai.ttf", "OFL 1.1", false),
    WENKAI_LITE("wenkai_lite", "文楷轻", "更细的手写体", "wenkai_lite.ttf", "OFL 1.1", false),
    WENKAI_TC("wenkai_tc", "文楷 TC", "繁体风格细楷", "wenkai_tc.ttf", "OFL 1.1", false),
    NEO_ZHISONG("neo_zhisong", "新致宋", "宋体韵味的黑体", "neo_zhisong.ttf", "IPA 1.0", false),
    ZHISONG("zhisong", "致宋", "经典宋体", "zhisong.ttf", "IPA 1.0", false),
    HUIWEN("huiwen", "汇文明朝", "古籍明朝体", "huiwen.ttf", "OFL 1.1", false),
    ;

    /** 服务器上的文件名，非内置字体必填。 */
    val remote: String get() = asset

    companion object {
        val DEFAULT = SMILEY
        fun byId(id: String?): AppFont = values().firstOrNull { it.id == id } ?: DEFAULT
    }
}

/**
 * 字体加载与下载。内置字体从 assets 读，下载字体从 filesDir/fonts 读；
 * 切换字体只换 [body]，所有界面与海报同步生效。
 */
object Typography2 {

    @Volatile private var current: AppFont = AppFont.DEFAULT
    private val cache = HashMap<String, Typeface>()

    @Volatile var body: Typeface = Typeface.DEFAULT
        private set
    @Volatile var meta: Typeface = Typeface.MONOSPACE
        private set

    fun currentFont(): AppFont = current

    fun install(context: Context, font: AppFont) {
        current = font
        body = load(context, font)
        meta = Typeface.create("monospace", Typeface.NORMAL)
    }

    private fun dirFor(context: Context): File =
        File(context.filesDir, "fonts").apply { mkdirs() }

    fun fileFor(context: Context, font: AppFont): File = File(dirFor(context), font.asset)

    /** 该字体现在能不能用：内置的，或已经下载完的。 */
    fun isAvailable(context: Context, font: AppFont): Boolean =
        font.builtin || fileFor(context, font).exists()

    /** 当前可选的字体列表（内置 + 已下载）。 */
    fun available(context: Context): List<AppFont> =
        AppFont.values().filter { isAvailable(context, it) }

    private fun load(context: Context, font: AppFont): Typeface {
        cache[font.id]?.let { return it }
        val tf: Typeface = try {
            if (font.builtin) {
                Typeface.createFromAsset(context.assets, "fonts/${font.asset}")
            } else {
                val f = fileFor(context, font)
                if (f.exists()) Typeface.createFromFile(f) else fallback(context)
            }
        } catch (_: Throwable) {
            fallback(context)
        }
        cache[font.id] = tf
        return tf
    }

    private fun fallback(context: Context): Typeface =
        cache[AppFont.DEFAULT.id] ?: try {
            Typeface.createFromAsset(context.assets, "fonts/${AppFont.DEFAULT.asset}")
        } catch (_: Throwable) {
            Typeface.DEFAULT
        }

    /**
     * 下载一款字体到 filesDir/fonts。
     * [onResult] 在后台线程回调，成功时返回 true 并清除该字体的缓存。
     */
    fun download(context: Context, font: AppFont, onResult: (Boolean, String) -> Unit) {
        thread(isDaemon = true) {
            val url = Api.BASE + "/fonts/" + font.remote
            val tmp = File(dirFor(context), font.asset + ".part")
            val target = fileFor(context, font)
            var conn: java.net.HttpURLConnection? = null
            try {
                conn = (URL(url).openConnection() as java.net.HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 120000
                }
                if (conn.responseCode != 200) {
                    onResult(false, "字体服务器返回 " + conn.responseCode)
                    return@thread
                }
                conn.inputStream.use { input ->
                    FileOutputStream(tmp).use { out -> input.copyTo(out, 64 * 1024) }
                }
                if (tmp.length() < 200_000) {
                    tmp.delete()
                    onResult(false, "文件不完整")
                    return@thread
                }
                if (target.exists()) target.delete()
                if (!tmp.renameTo(target)) {
                    tmp.delete()
                    onResult(false, "写入失败")
                    return@thread
                }
                cache.remove(font.id)
                onResult(true, "已下载 · " + (target.length() / 1024 / 1024) + "MB")
            } catch (t: Throwable) {
                tmp.delete()
                onResult(false, t.message ?: "下载失败")
            } finally {
                conn?.disconnect()
            }
        }
    }
}
