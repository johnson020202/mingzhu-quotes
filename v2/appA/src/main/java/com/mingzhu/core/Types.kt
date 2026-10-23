package com.mingzhu.core

import android.content.Context
import android.graphics.Typeface

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
) {
    SMILEY("smiley", "得意黑", "默认 · 锐利斜体", "smiley.otf", "OFL 1.1"),
    WENKAI("wenkai", "文楷", "手写感 · 温和", "wenkai.ttf", "OFL 1.1"),
    WENKAI_LITE("wenkai_lite", "文楷轻", "更细的手写体", "wenkai_lite.ttf", "OFL 1.1"),
    WENKAI_TC("wenkai_tc", "文楷 TC", "繁体风格细楷", "wenkai_tc.ttf", "OFL 1.1"),
    NEO_XIHEI("neo_xihei", "新细黑", "现代黑体", "neo_xihei.ttf", "IPA 1.0"),
    NEO_ZHISONG("neo_zhisong", "新致宋", "宋体韵味的黑体", "neo_zhisong.ttf", "IPA 1.0"),
    ZHISONG("zhisong", "致宋", "经典宋体", "zhisong.ttf", "IPA 1.0"),
    MARKER("marker", "马克黑", "马克笔手写", "marker.ttf", "OFL 1.1"),
    HUIWEN("huiwen", "汇文明朝", "古籍明朝体", "huiwen.ttf", "OFL 1.1"),
    ;

    companion object {
        val DEFAULT = SMILEY
        fun byId(id: String?): AppFont = values().firstOrNull { it.id == id } ?: DEFAULT

    }
}

/**
 * 包内实际可用的字体集合。由 [Typography2.warmUp] 探测 assets 后写入，
 * 未内置的字体不出现在选择器里，避免选中后静默回落到得意黑。
 */
object Fonts2 {
    @Volatile var installed: Set<String> = setOf(AppFont.SMILEY.id)
    fun available(): List<AppFont> = AppFont.values().filter { installed.contains(it.id) }
}

/**
 * 字体缓存。切换字体只换 [body]，所有版本共用同一个 draw 入口，因此界面与海报会同步变化。
 * 大字体文件（10~26 MB）按需加载，避免启动卡顿。
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

    private fun load(context: Context, font: AppFont): Typeface {
        cache[font.id]?.let { return it }
        return try {
            Typeface.createFromAsset(context.assets, "fonts/${font.asset}").also { cache[font.id] = it }
        } catch (t: Throwable) {
            cache[AppFont.DEFAULT.id] ?: Typeface.DEFAULT
        }
    }

    /** 预加载随包字体，切换时不用等；同时记录哪些字体真的可用。 */
    fun warmUp(context: Context) {
        val ok = HashSet<String>()
        AppFont.values().forEach { f ->
            try {
                cache[f.id] = Typeface.createFromAsset(context.assets, "fonts/${f.asset}")
                ok.add(f.id)
            } catch (_: Throwable) { /* 未内置，忽略 */ }
        }
        if (ok.isNotEmpty()) Fonts2.installed = ok
    }
}
