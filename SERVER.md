# 杭州服务器 · 新 App 接入手册

服务器：`47.114.52.5`（阿里云 cn-hangzhou，内网 `172.31.129.22`）
SSH：`ssh aliyun-hangzhou`

## 一句话说明架构

公网入站被云侧拦截（网卡只有内网 IP，公网走 NAT，公网入口层另有过滤），
所以对外入口走 **Cloudflare 命名隧道 → Nginx → 各 App 后端**。

**入口域名固定：`https://api.dognosay.dpdns.org`**，不会随时间变化。
新 App 只需要在 Nginx 里加一段 `location`，然后把服务跑在某个本地端口上。

```
公网 ──> api.dognosay.dpdns.org (Cloudflare)
             │  命名隧道 hangzhou-gateway
             v
        Nginx(127.0.0.1:80) ──┬── /mingzhu/  ──> 127.0.0.1:8848  明著摘录
                              ├── /          ──> 127.0.0.1:8008  CineEnglish（历史服务）
                              └── /newapp/   ──> 127.0.0.1:PORT  你的新服务
```

隧道配置在服务器 `/etc/cloudflared/hangzhou-gateway.yml`，
Cloudflare 侧的 ingress 规则与 DNS CNAME 同样指向该 hostname。

## 新增一个 App 的完整步骤

### 1. 后端跑在本地端口

选一个 9000 以上的端口，写成 systemd 服务（参考 `/etc/systemd/system/mingzhu.service`）：

```ini
[Unit]
Description=YourApp API
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=/opt/yourapp
ExecStart=/usr/bin/python3 /opt/yourapp/server.py
Restart=always
RestartSec=30
StartLimitIntervalSec=600
StartLimitBurst=3

[Install]
WantedBy=multi-user.target
```

> ⚠️ 必须带 `StartLimitIntervalSec` / `StartLimitBurst`。缺了它们，一旦端口被占就会每 3 秒重启一次，CineEnglish 曾因此空转 41982 次。

```bash
sudo systemctl daemon-reload && sudo systemctl enable --now yourapp
curl http://127.0.0.1:PORT/health   # 先在本机通
```

### 2. Nginx 加路径

编辑 `/etc/nginx/sites-available/mingzhu`，在 `location /` 之前插入：

```nginx
location /yourapp/ {
    proxy_pass http://127.0.0.1:PORT/;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_connect_timeout 5s;
    proxy_read_timeout 30s;
}
```

```bash
sudo nginx -t && sudo systemctl reload nginx
curl http://127.0.0.1/yourapp/health
```

### 3. 从外网验证

```bash
curl https://api.dognosay.dpdns.org/yourapp/health
```

**不要只看服务器本机通就算完**：`127.0.0.1` 和内网 IP 都通、但公网 IP 不通是这台机的常态，必须走 `https://api.dognosay.dpdns.org/yourapp/health` 实测。

### 4. App 端配置

把 `https://api.dognosay.dpdns.org/yourapp/` 写进 App 常量即可，域名永久固定。
仍建议客户端做降级（网络失败时用本地缓存/内置数据），毕竟链路依赖 Cloudflare 与服务器在线。

## 为什么不用 80/443 直连

- 服务器网卡只有内网 IP `172.31.129.22`，公网是 NAT 映射
- 实测：`127.0.0.1:80` 200、`172.31.129.22:80` 200、**`47.114.52.5:80` 000**
- 服务器访问自己的公网 IP 都不通 → 卡在云的公网入口层，不是 Nginx 也不是本机防火墙
- 结论：安全组放行 80/443 是**必要条件**，但这台机上单放行未必够，需放行后再实测

如果哪天直连打通，只需把 App 地址换成 `http://47.114.52.5/...`，Nginx 不用改。


## 明著 API（v2）

基础地址 `https://api.dognosay.dpdns.org/mingzhu`，浏览器打开该地址即可见自动生成的接口文档。

### 读接口

| 路径 | 说明 |
|---|---|
| `/health` | 存活、书数、句数、内容版本 |
| `/api/meta` | 内容版本 + 可用端点清单 |
| `/api/books` | 全部书目 |
| `/api/book?id=walden` | 单本详情（`full=0` 只要简介） |
| `/api/today?recent=7` | 今日 + 最近 N 天 |
| `/api/day?date=2026-10-24` | 指定日期 |
| `/api/history?start=…&end=…` | 区间，最多 400 天 |
| `/api/random?book=walden` | 随机一句 |
| `/api/search?q=孤独` | 全文搜索，可加 `author=` / `book=` / `limit=` |
| `/api/authors` | 作者列表 |
| `/api/fonts` | 字体清单 |
| `/api/stats` | 运行统计 |

### 写接口

```
POST /api/track     {"event":"share"}
POST /api/feedback  {"bookId":"walden","quoteIndex":3,"text":"错字…"}
```

### 约定

- 统一返回 `{"ok":true,"version":"…","data":…}`，出错为 `{"ok":false,"error":{"code":…,"message":…}}`
- 所有响应带 `X-Content-Version` 头，客户端据此判断本地缓存是否过期
- 读接口带 `Cache-Control`；`/api/random`、`/api/stats` 不缓存
- 内容版本是 `books.json` 的 SHA1 前 12 位，改内容后自动变化

### 客户端策略

App 内置的 `Books.kt` 是权威副本，服务器只做补充：

- 搜索先问服务器，失败自动回落本地过滤（`Api.searchSmart`）
- 随机先问服务器，失败本地随机（`Api.random`）
- 服务器挂了或断网，App 照常可用，不会白屏

## 数据与运维

- 内容：`/opt/mingzhu/books.json`，改完 `systemctl restart mingzhu` 即生效
- 反馈：`/opt/mingzhu/data/feedback.jsonl`，一行一条
- 统计：`/opt/mingzhu/data/stats.json`，每 30 秒落盘
- 源码：项目内 `v2/server/`，与服务器 `/opt/mingzhu/` 保持一致

## 现有服务一览

| 服务 | 作用 | 端口 |
|---|---|---|
| `nginx` | 统一入口、按路径分发 | 80 |
| `mingzhu` | 明著摘录内容 API | 8848 |
| `cineenglish` | CineEnglish 后端（历史项目） | 8008 |
| `hangzhou-gateway` | Cloudflare 命名隧道（固定域名） | — |

已下线：`cinetunnel`（trycloudflare 临时隧道）、`entry-url`（记录临时地址），
换成固定域名后不再需要。

全部 `enabled`，开机自启。

## 排错顺序

1. `systemctl status 你服务` — 看是否 active
2. `ss -lntp | grep PORT` — 确认端口在听
3. `curl http://127.0.0.1/PORT/health` — 后端本身
4. `curl http://127.0.0.1/yourapp/health` — Nginx 转发
5. `curl https://api.dognosay.dpdns.org/yourapp/health` — 外网

哪一步断，就在那一步查，不要跳步。

## 别踩的坑

- **systemd 缺 StartLimit 配置** → 无限重启风暴（真实事故：41982 次）
- **用 nohup 手动起服务** → 脱离 systemd，重启丢失、抢不到端口还查不到（真实事故：孤儿进程占了 8008 近 17 天）
- **只看 localhost 通就以为上线了** → 这台机公网入口和内部是两个世界
- **把临时的 trycloudflare 地址写死进 App** → 隧道一重启就失效（现已改为固定域名）

## 隧道运维

```bash
ssh aliyun-hangzhou 'systemctl status hangzhou-gateway'
ssh aliyun-hangzhou 'tail -30 /var/log/cloudflared-gateway.log'
```

- 隧道 ID：`285dbd5e-85c1-4492-853c-9a1751027a43`
- 凭据：`/etc/cloudflared/285dbd5e-85c1-4492-853c-9a1751027a43.json`（600 权限）
- Cloudflare 侧：隧道 `hangzhou-gateway`，ingress 指向 `api.dognosay.dpdns.org`
- 换域名要改两处：该 yml 里的 `hostname`，以及 Cloudflare 后台的 ingress 与 DNS CNAME
- Cloudflare 账号 token 存在 `~/.hermes/workspace/.secrets/cloudflare-credentials.md`
