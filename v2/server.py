#!/usr/bin/env python3
"""明著摘录 · 内容服务
无第三方依赖，仅用标准库。提供每日一书、书目、历史日期与字体清单。
"""
import json, math
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from datetime import date, timedelta
from urllib.parse import urlparse, parse_qs

EPOCH = date(2025, 1, 1)

def load_books():
    with open("books.json", encoding="utf-8") as f:
        return json.load(f)

BOOKS = load_books()

def day_index(d: date) -> int:
    return (d - EPOCH).days

def book_for(d: date):
    return BOOKS[day_index(d) % len(BOOKS)]

def entry(d: date):
    b = book_for(d)
    n = len(b["lines"])
    qi = day_index(d) % n
    return {
        "date": d.isoformat(),
        "dayIndex": day_index(d),
        "book": {k: b[k] for k in ("id", "title", "author", "nation", "year", "intro")},
        "quote": b["lines"][qi],
        "quoteIndex": qi,
    }

class H(BaseHTTPRequestHandler):
    def log_message(self, *a):
        pass

    def send_json(self, obj, code=200):
        body = json.dumps(obj, ensure_ascii=False).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Cache-Control", "public, max-age=300")
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        u = urlparse(self.path)
        q = parse_qs(u.query)
        try:
            if u.path == "/health":
                return self.send_json({"ok": True, "books": len(BOOKS)})

            if u.path == "/api/books":
                return self.send_json({"books": [
                    {k: b[k] for k in ("id", "title", "author", "nation", "year", "intro")} | {"count": len(b["lines"])}
                    for b in BOOKS]})

            if u.path == "/api/book":
                bid = q.get("id", [""])[0]
                b = next((x for x in BOOKS if x["id"] == bid), None)
                return self.send_json(b or {"error": "not found"}, 200 if b else 404)

            if u.path == "/api/today":
                d = date.today()
                days = int(q.get("recent", ["0"])[0] or 0)
                out = {"today": entry(d)}
                if days:
                    out["recent"] = [entry(d - timedelta(days=i)) for i in range(days)]
                return self.send_json(out)

            if u.path == "/api/day":
                ds = q.get("date", [date.today().isoformat()])[0]
                return self.send_json(entry(date.fromisoformat(ds)))

            if u.path == "/api/history":
                start = date.fromisoformat(q.get("start", ["2025-01-01"])[0])
                end = date.fromisoformat(q.get("end", [date.today().isoformat()])[0])
                n = max(0, min((end - start).days + 1, 400))
                return self.send_json({"entries": [entry(start + timedelta(days=i)) for i in range(n)]})

            if u.path == "/api/fonts":
                return self.send_json({"fonts": [
                    {"id": "smiley", "name": "得意黑", "license": "OFL 1.1", "default": True},
                    {"id": "wenkai", "name": "文楷", "license": "OFL 1.1"},
                    {"id": "wenkai_lite", "name": "文楷轻", "license": "OFL 1.1"},
                    {"id": "wenkai_tc", "name": "文楷 TC", "license": "OFL 1.1"},
                    {"id": "neo_xihei", "name": "新细黑", "license": "IPA 1.0"},
                    {"id": "neo_zhisong", "name": "新致宋", "license": "IPA 1.0"},
                    {"id": "zhisong", "name": "致宋", "license": "IPA 1.0"},
                    {"id": "marker", "name": "马克黑", "license": "OFL 1.1"},
                    {"id": "huiwen", "name": "汇文明朝", "license": "OFL 1.1"}]})

            return self.send_json({"error": "not found"}, 404)
        except Exception as e:
            return self.send_json({"error": str(e)}, 400)

if __name__ == "__main__":
    ThreadingHTTPServer(("127.0.0.1", 8848), H).serve_forever()
