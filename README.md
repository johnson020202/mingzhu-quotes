# 明著摘录 · 安卓

一句一页，读一点名著。

每次显示一段世界名著摘抄，左右滑动或点下方按钮切换，带上当天日期，一键把当前这一页导出成摘抄海报分享出去。

全部界面只用**得意黑（Smiley Sans）**一种字体，配等宽体处理数字与英文小标签。

## 五个版本

同一个内核，五种完全不同的版面语言。挑你喜欢的装，互不冲突（包名各不相同，可以同时装五个）。

| # | 版本 | 风格 | 下载 |
|---|------|------|------|
| 1 | 纸页 | 米白纸面、朱红小印、首字下沉，左右滑动翻页 | [MingZhu-1-Paper.apk](../../releases/latest/download/MingZhu-1-Paper.apk) |
| 2 | 夜读 | 纯黑底、暖金强调、星点，适合睡前低亮度阅读 | [MingZhu-2-Night.apk](../../releases/latest/download/MingZhu-2-Night.apk) |
| 3 | 朱砂 | 红黑白三色、朱砂边框、整版大印章 | [MingZhu-3-Cinnabar.apk](../../releases/latest/download/MingZhu-3-Cinnabar.apk) |
| 4 | 青瓷 | 青灰底、可见格栅网格、手绘波线、尺子刻度进度 | [MingZhu-4-Celadon.apk](../../releases/latest/download/MingZhu-4-Celadon.apk) |
| 5 | 极简 | 纯黑、超大序号与总数、终端感元信息 | [MingZhu-5-Mono.apk](../../releases/latest/download/MingZhu-5-Mono.apk) |

## 安装

1. 点上面的链接下载任意一个 `.apk`
2. 手机上打开下载文件，允许「安装未知来源应用」
3. 装好后从桌面打开，图标是白底一个「明」字

系统要求：Android 8.0（API 26）及以上。已在小米 10 Pro（Android 16 / HyperOS）真机验证。

## 功能

- **切换**：左右滑动、点底部 ← / →，或直接点画面
- **日期**：每页都带当天日期，五个版本用不同写法（`2026.10.23` / `OCTOBER 23, 2026` / `2026 年 10 月 23 日 周五` / `十月二十三日`）
- **每日一条**：每天首次打开会自动翻到当天的条目，当天不再变；手动翻页随时可以覆盖
- **分享海报**：点「分享海报」把当前页面按 2.4 倍超采样渲染成一张竖版长图（约 2980×6300），底部带日期落款，通过系统分享面板发到微信、相册或任何应用
- **复制文字**：长按底部操作条，把摘抄和出处复制到剪贴板

## 内容

内置 40 条中文摘抄与 25 条英文原文摘抄，全部来自已进入公有领域的作品（《小王子》《老人与海》《瓦尔登湖》《飞鸟集》《简·爱》《月亮与六便士》等），每条都标注书名、作者和章节。

摘抄与出处集中在 `shared/data/Quotes.kt`，增删改只改这一个文件，再分发到五个版本即可。

## 从源码构建

需要 JDK 17 或 21、Android SDK（compileSdk 35）。

```bash
# 单个版本
./build.sh v1-paper assembleDebug

# 正式包（需要先准备签名文件，见下）
./build.sh v1-paper assembleRelease
```

签名：在项目根目录放 `signing.properties`：

```properties
storeFile=/绝对路径/release.keystore
storePassword=你的密码
keyAlias=mingzhu
keyPassword=你的密码
```

生成 keystore：

```bash
keytool -genkeypair -keystore release.keystore -alias mingzhu \
  -keyalg RSA -keysize 2048 -validity 10000
```

`local.properties` 里写 `sdk.dir=/你的/Android/Sdk`。

## 目录结构

```
shared/data/Quotes.kt      摘抄库（唯一内容源）
shared/data/QuoteStore.kt  每日轮换、收藏、语言状态
shared/data/Typography.kt  中文折行（避头尾、消孤字）
shared/data/Poster.kt      海报导出与分享
shared/data/ActionBar.kt   底部操作条
shared/fonts/              得意黑字体
v1-paper/ … v5-mono/       五个版本各自的 MainView.kt 与配色
```

## 设计说明

- **中文本地排版**：自己实现了按字素测量的折行，避头标点不放行首，末行只剩一个字时把上一行末字拉下来，不让段落出现孤字行
- **句尾零句号**：正文结尾的句号统一隐去，问号、叹号、省略号保留
- **首字下沉**：第一个字单独放大成朱红色，不参与折行计算，避免挤压后续文字
- **导出与屏幕一致**：内容区与底部按钮分离绘制，导出时按整屏高度重新渲染，所以海报里没有按钮，构图也完整

## 字体

得意黑 Smiley Sans © atelier-anchor，SIL Open Font License 1.1，见 `shared/fonts/LICENSE`。

---

## 版本记录

- **v1.0.0**（2026-10-23）五个版本首发。真机环境：小米 10 Pro，Android 16（SDK 37），HyperOS V816。
- 构建环境：JDK 21（`/opt/homebrew/opt/openjdk@21`），AGP 8.9.2，Kotlin 2.0.21，compileSdk 35，minSdk 26。
- 五个包签名一致（SHA-256 `ce0d38e4…c152d`），后续升级请沿用同一个 `release.keystore`。
