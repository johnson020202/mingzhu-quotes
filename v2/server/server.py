#!/usr/bin/env python3
"""明著摘录 · 内容服务 v2

设计原则：
- 只用标准库，零依赖，单文件部署
- 读接口全部幂等、可缓存；写接口（统计/反馈）只做轻量追加
- 无数据库：统计数据落在 JSON 文件 + 每日轮转，20MB 内存足够
"""
import json
import os
import threading
import time
import hashlib
from datetime import date, datetime, timedelta
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
BOOKS_FILE = os.path.join(BASE_DIR, "books.json")
DATA_DIR = os.path.join(BASE_DIR, "data")
FEEDBACK_FILE = os.path.join(DATA_DIR, "feedback.jsonl")
STATS_FILE = os.path.join(DATA_DIR, "stats.json")

EPOCH = date(2025, 1, 1)
MAX_PAGE = 100

_lock = threading.Lock()


def _load_books():
    with open(BOOKS_FILE, encoding="utf-8") as f:
        return json.load(f)


BOOKS = _load_books()
BOOKS_BY_ID = {b["id"]: b for b in BOOKS}

# 内容指纹：客户端据此判断是否需要刷新本地缓存
CONTENT_VERSION = hashlib.sha1(
    json.dumps(BOOKS, ensure_ascii=False, sort_keys=True).encode()
).hexdigest()[:12]


def day_index(d: date) -> int:
    return (d - EPOCH).days


def book_for(d: date):
    return BOOKS[day_index(d) % len(BOOKS)]


def brief(b):
    return {k: b[k] for k in ("id", "title", "author", "nation", "year", "intro")}


def entry(d: date):
    b = book_for(d)
    n = len(b["lines"])
    qi = day_index(d) % n
    return {
        "date": d.isoformat(),
        "dayIndex": day_index(d),
        "book": brief(b),
        "quote": b["lines"][qi],
        "quoteIndex": qi,
    }


# ---------- 统计（进程内聚合 + 定期落盘） ----------

def _load_stats():
    try:
        with open(STATS_FILE, encoding="utf-8") as f:
            return json.load(f)
    except Exception:
        return {}


STATS = _load_stats()


def bump(key: str, n: int = 1):
    with _lock:
        STATS[key] = STATS.get(key, 0) + n


def flush_stats():
    with _lock:
        snapshot = dict(STATS)
    tmp = STATS_FILE + ".tmp"
    try:
        os.makedirs(DATA_DIR, exist_ok=True)
        with open(tmp, "w", encoding="utf-8") as f:
            json.dump(snapshot, f, ensure_ascii=False, indent=1)
        os.replace(tmp, STATS_FILE)
    except Exception:
        pass


# ---------- HTTP ----------

ERROR_CODES = {
    400: "bad_request",
    404: "not_found",
    405: "method_not_allowed",
    429: "too_many_requests",
    500: "server_error",
}


class H(BaseHTTPRequestHandler):
    server_version = "mingzhu/2.0"

    def log_message(self, *a):
        pass

    # --- 输出 ---
    def _send(self, payload, code=200, cache=300, ctype="application/json; charset=utf-8"):
        if isinstance(payload, (dict, list)):
            body = json.dumps(payload, ensure_ascii=False).encode()
        else:
            body = payload.encode() if isinstance(payload, str) else payload
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("X-Content-Version", CONTENT_VERSION)
        self.send_header("Cache-Control", f"public, max-age={cache}")
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(body)

    def ok(self, data, cache=300):
        return self._send({"ok": True, "version": CONTENT_VERSION, "data": data}, 200, cache)

    def fail(self, code, msg):
        return self._send(
            {"ok": False, "error": {"code": ERROR_CODES.get(code, "error"), "message": msg}},
            code, cache=0)

    # --- 入口 ---
    def do_OPTIONS(self):
        self.send_response(204)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.end_headers()

    def do_HEAD(self):
        self.do_GET()

    def do_POST(self):
        u = urlparse(self.path)
        length = int(self.headers.get("Content-Length") or 0)
        if length > 8 * 1024:
            return self.fail(400, "body too large")
        raw = self.rfile.read(length) if length else b"{}"
        try:
            body = json.loads(raw or b"{}")
        except Exception:
            return self.fail(400, "invalid json")

        if u.path == "/api/feedback":
            text = str(body.get("text", "")).strip()
            if not text or len(text) > 500:
                return self.fail(400, "text required (<=500)")
            rec = {
                "at": datetime.utcnow().isoformat() + "Z",
                "book": str(body.get("bookId", ""))[:64],
                "index": body.get("quoteIndex"),
                "text": text,
                "kind": str(body.get("kind", "report"))[:32],
            }
            os.makedirs(DATA_DIR, exist_ok=True)
            with _lock:
                with open(FEEDBACK_FILE, "a", encoding="utf-8") as f:
                    f.write(json.dumps(rec, ensure_ascii=False) + "\n")
            bump("feedback_total")
            return self._send({"ok": True, "data": {"received": True}}, 200, cache=0)

        if u.path == "/api/track":
            ev = str(body.get("event", ""))[:48]
            if not ev:
                return self.fail(400, "event required")
            bump("ev_" + ev)
            bump("ev_total")
            return self._send({"ok": True, "data": {"received": True}}, 200, cache=0)

        return self.fail(404, "no such POST endpoint")

    def do_GET(self):
        u = urlparse(self.path)
        q = parse_qs(u.query)
        p = u.path
        try:
            if p in ("/", "/docs"):
                return self._send(DOCS_HTML, 200, cache=600, ctype="text/html; charset=utf-8")

            if p == "/health":
                return self._send({
                    "ok": True,
                    "books": len(BOOKS),
                    "lines": sum(len(b["lines"]) for b in BOOKS),
                    "version": CONTENT_VERSION,
                    "uptime": int(time.time() - START_AT),
                }, 200, cache=0)

            # ---- 内容读接口 ----
            if p == "/api/meta":
                return self.ok({
                    "version": CONTENT_VERSION,
                    "books": len(BOOKS),
                    "lines": sum(len(b["lines"]) for b in BOOKS),
                    "epoch": EPOCH.isoformat(),
                    "endpoints": ["books", "book", "today", "day", "history",
                                  "random", "search", "authors", "fonts", "feedback", "track"],
                }, cache=3600)

            if p == "/api/books":
                bump("ev_books")
                return self.ok({"books": [brief(b) | {"count": len(b["lines"])} for b in BOOKS]}, 1800)

            if p == "/api/book":
                bid = q.get("id", [""])[0]
                b = BOOKS_BY_ID.get(bid)
                if not b:
                    return self.fail(404, f"book '{bid}' not found")
                bump("ev_book")
                full = q.get("full", ["1"])[0] != "0"
                out = brief(b) | {"count": len(b["lines"]), "firstDate": _first_date(b).isoformat()}
                if full:
                    out["lines"] = b["lines"]
                return self.ok(out, 3600)

            if p == "/api/today":
                d = date.today()
                days = min(int(q.get("recent", ["0"])[0] or 0), 60)
                bump("ev_today")
                out = {"today": entry(d)}
                if days:
                    out["recent"] = [entry(d - timedelta(days=i)) for i in range(days)]
                return self.ok(out, 300)

            if p == "/api/day":
                ds = q.get("date", [date.today().isoformat()])[0]
                try:
                    d = date.fromisoformat(ds)
                except ValueError:
                    return self.fail(400, "date must be YYYY-MM-DD")
                if d < date(2000, 1, 1) or d > date(2100, 12, 31):
                    return self.fail(400, "date out of range")
                bump("ev_day")
                return self.ok(entry(d), 86400)

            if p == "/api/history":
                try:
                    start = date.fromisoformat(q.get("start", ["2025-01-01"])[0])
                    end = date.fromisoformat(q.get("end", [date.today().isoformat()])[0])
                except ValueError:
                    return self.fail(400, "start/end must be YYYY-MM-DD")
                if end < start:
                    return self.fail(400, "end before start")
                n = min((end - start).days + 1, 400)
                bump("ev_history")
                return self.ok({
                    "start": start.isoformat(),
                    "end": (start + timedelta(days=n - 1)).isoformat(),
                    "entries": [entry(start + timedelta(days=i)) for i in range(n)],
                }, 600)

            if p == "/api/random":
                bid = q.get("book", [""])[0]
                if bid:
                    b = BOOKS_BY_ID.get(bid)
                    if not b:
                        return self.fail(404, f"book '{bid}' not found")
                    i = int(time.time() * 997) % len(b["lines"])
                    return self.ok({"book": brief(b), "quote": b["lines"][i], "quoteIndex": i}, 0)
                b = BOOKS[int(time.time() * 991) % len(BOOKS)]
                i = int(time.time() * 977) % len(b["lines"])
                return self.ok({"book": brief(b), "quote": b["lines"][i], "quoteIndex": i}, 0)

            if p == "/api/authors":
                seen = {}
                for b in BOOKS:
                    seen.setdefault(b["author"], {"author": b["author"], "nation": b["nation"], "books": []})
                    seen[b["author"]]["books"].append(b["id"])
                return self.ok({"authors": list(seen.values())}, 3600)

            # ---- 搜索 ----
            if p == "/api/search":
                kw = q.get("q", [""])[0].strip()
                if not kw:
                    return self.fail(400, "q required")
                if len(kw) > 40:
                    return self.fail(400, "q too long")
                author = q.get("author", [""])[0]
                book_id = q.get("book", [""])[0]
                limit = min(int(q.get("limit", ["30"])[0] or 30), MAX_PAGE)
                hits = []
                for b in BOOKS:
                    if author and b["author"] != author:
                        continue
                    if book_id and b["id"] != book_id:
                        continue
                    for i, line in enumerate(b["lines"]):
                        if kw in line:
                            hits.append({
                                "bookId": b["id"], "title": b["title"],
                                "author": b["author"], "quoteIndex": i, "quote": line,
                            })
                            if len(hits) >= limit:
                                break
                    if len(hits) >= limit:
                        break
                bump("ev_search")
                return self.ok({"q": kw, "total": len(hits), "results": hits}, 0)

            # ---- 字体清单 ----
            if p == "/api/fonts":
                return self.ok({"fonts": [
                    {"id": "smiley", "name": "得意黑", "desc": "默认 · 锐利斜体", "license": "OFL 1.1", "default": True},
                    {"id": "wenkai", "name": "文楷", "desc": "手写感 · 温和", "license": "OFL 1.1"},
                    {"id": "wenkai_lite", "name": "文楷轻", "desc": "更细的手写体", "license": "OFL 1.1"},
                    {"id": "wenkai_tc", "name": "文楷 TC", "desc": "繁体风格细楷", "license": "OFL 1.1"},
                    {"id": "neo_xihei", "name": "新细黑", "desc": "现代黑体", "license": "IPA 1.0"},
                    {"id": "neo_zhisong", "name": "新致宋", "desc": "宋体韵味的黑体", "license": "IPA 1.0"},
                    {"id": "zhisong", "name": "致宋", "desc": "经典宋体", "license": "IPA 1.0"},
                    {"id": "marker", "name": "马克黑", "desc": "马克笔手写", "license": "OFL 1.1"},
                    {"id": "huiwen", "name": "汇文明朝", "desc": "古籍明朝体", "license": "OFL 1.1"},
                ]}, 86400)

            # ---- 运维 ----
            if p == "/api/stats":
                with _lock:
                    snap = dict(STATS)
                top = sorted(((k, v) for k, v in snap.items() if not k.startswith("ev_")),
                             key=lambda x: -x[1])[:20]
                return self.ok({"counters": dict(top), "contentVersion": CONTENT_VERSION}, 0)

            return self.fail(404, "no such endpoint")

        except Exception as e:
            return self.fail(500, str(e))


def _first_date(b):
    d = EPOCH
    for _ in range(len(BOOKS) * 2):
        if book_for(d)["id"] == b["id"]:
            return d
        d += timedelta(days=1)
    return EPOCH


DOCS_HTML = """<!doctype html><html lang="zh-CN"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>明著摘录 API</title><style>
body{font:15px/1.7 system-ui,-apple-system,"PingFang SC",sans-serif;max-width:760px;
margin:0 auto;padding:28px 20px 60px;color:#222;background:#fbf9f4}
h1{font-size:24px;margin:0 0 6px}h2{font-size:17px;margin:30px 0 10px;
border-left:3px solid #2d5d48;padding-left:9px}
code{background:#eee9de;padding:1px 5px;border-radius:4px;font-size:13px}
pre{background:#efeadf;padding:11px 13px;border-radius:7px;overflow-x:auto;font-size:12.5px}
table{border-collapse:collapse;width:100%;font-size:14px}
td,th{border-bottom:1px solid #e4ded0;padding:7px 6px;text-align:left}
th{color:#777;font-weight:500}.m{color:#888;font-size:13px}
</style></head><body>
<h1>明著摘录 · 内容 API</h1>
<p class="m">基础地址 <code>https://api.dognosay.dpdns.org/mingzhu</code>　
版本 <code>__VER__</code>　10 本书 · 200 句</p>

<h2>读接口（GET，可缓存）</h2>
<table>
<tr><th>路径</th><th>说明</th></tr>
<tr><td><code>/health</code></td><td>存活与内容版本</td></tr>
<tr><td><code>/api/meta</code></td><td>内容版本、书目数、可用端点</td></tr>
<tr><td><code>/api/books</code></td><td>全部书目</td></tr>
<tr><td><code>/api/book?id=walden</code></td><td>单本详情，<code>full=0</code> 只要简介</td></tr>
<tr><td><code>/api/today?recent=7</code></td><td>今日条目，可带最近 N 天</td></tr>
<tr><td><code>/api/day?date=2026-10-24</code></td><td>指定日期</td></tr>
<tr><td><code>/api/history?start=…&amp;end=…</code></td><td>区间（最多 400 天）</td></tr>
<tr><td><code>/api/random?book=walden</code></td><td>随机一句</td></tr>
<tr><td><code>/api/search?q=孤独&amp;author=…</code></td><td>全文搜索</td></tr>
<tr><td><code>/api/authors</code></td><td>作者列表</td></tr>
<tr><td><code>/api/fonts</code></td><td>字体清单</td></tr>
</table>

<h2>写接口（POST，JSON）</h2>
<pre>POST /api/track     {"event":"share"}           // 轻量计数
POST /api/feedback  {"bookId":"walden","quoteIndex":3,"text":"错字…"}</pre>

<h2>返回格式</h2>
<pre>{"ok":true,"version":"__VER__","data":…}
{"ok":false,"error":{"code":"not_found","message":"…"}}</pre>
<p class="m">所有响应带 <code>X-Content-Version</code> 头，客户端据此判断是否需要刷新本地缓存。</p>
</body></html>"""

START_AT = time.time()
DOCS_HTML = DOCS_HTML.replace("__VER__", CONTENT_VERSION)


if __name__ == "__main__":
    os.makedirs(DATA_DIR, exist_ok=True)
    def _flusher():
        while True:
            time.sleep(30)
            flush_stats()
    threading.Thread(target=_flusher, daemon=True).start()
    srv = ThreadingHTTPServer(("0.0.0.0", 8848), H)
    srv.daemon_threads = True
    print(f"mingzhu api v{CONTENT_VERSION} on :8848, {len(BOOKS)} books", flush=True)
    srv.serve_forever()
