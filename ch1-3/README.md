# System Design Ch.1 — Weather Forecast API Demo

這是 Alex Xu《System Design Interview》**第一章** 基礎系統組件的實作展示，
示範單一機器如何逐步演進為具備下列能力的分散式系統:

| Layer             | 組件                                     | 技術                               |
| ----------------- | ---------------------------------------- | ---------------------------------- |
| Load Balancer     | 將流量分發給多個 Web Server              | Nginx (Round Robin)                |
| Web Tier          | 兩個 Stateless Spring Boot 3 App 實例    | `app1`, `app2` (Java 17)           |
| Cache Tier        | Read-Through Cache, TTL = 1 小時         | Redis 7                            |
| Data Tier         | 讀寫分離 (Master 寫 / Slave 讀)          | PostgreSQL 16 Streaming Replication|
| Message Queue     | 解耦非同步任務 (搜尋日誌 / 統計分析)      | RabbitMQ 3 (+ Management UI)       |

---

## 目錄結構

```
ch2/
├── docker-compose.yml
├── README.md
├── nginx/
│   └── nginx.conf                  # Round Robin 設定
├── postgres/
│   └── master/init.sql             # weather_forecast 表格 schema
└── webapp/
    ├── Dockerfile                  # 兩階段建置
    ├── pom.xml
    └── src/main/...                # Spring Boot 應用程式
```

---

## 快速啟動

> 前置條件: Docker Desktop (或 Docker Engine + docker compose v2)

```bash
# 1) 於專案根目錄 (ch2/) 執行
docker compose up -d --build

# 2) 觀察所有容器狀態 (等到全部 healthy/running)
docker compose ps

# 3) 觀察 app 日誌 (兩個實例 + Consumer 訊息)
docker compose logs -f app1 app2
```

啟動後可訪問:

- **API (走 LB)**:    http://localhost/api/weather/Taipei
- **RabbitMQ UI**:   http://localhost:15672 (guest / guest)
- **Redis**:         `localhost:6379`
- **PG Master**:     `localhost:5432`  (weather / weatherpwd)
- **PG Slave (RO)**: `localhost:5433`  (weather / weatherpwd)

重啟單一服務 / 查看日誌:

```bash
docker compose stop app2
docker compose start app2
docker compose logs -f app1 app2
```

停止與清除:

```bash
docker compose down          # 停止但保留 volumes
docker compose down -v       # 連同資料 volumes 一起清除
```

---

## 容器連線速查

| 容器             | 連線指令                                                                 | 帳密                          |
| ---------------- | ------------------------------------------------------------------------ | ----------------------------- |
| PG Master (讀寫) | `docker exec -it postgres-master psql -U weather -d weatherdb`           | weatherpwd / superuser: postgres_pwd |
| PG Slave (唯讀)  | `docker exec -it postgres-slave psql -U weather -d weatherdb`            | weatherpwd                    |
| Redis            | `docker exec -it redis redis-cli`                                        | 無密碼                        |
| RabbitMQ UI      | `open http://localhost:15672`                                            | guest / guest                 |
| app1 / app2      | `docker exec -it app1 sh` / `docker exec -it app2 sh`                   | —                             |
| Nginx            | `docker exec -it nginx sh`                                               | —                             |

---

## Demo 測試指令

### 1. Load Balancing — 驗證請求被輪流分發到 app1 / app2

```bash
# 連打 100 次，觀察流量被分散
for i in $(seq 1 100); do
  curl -s http://localhost/api/weather/Taipei > /dev/null
done

# 確認 Nginx Response Header 中的 X-Upstream（顯示實際打到哪個 app）
curl -sI http://localhost/api/weather/Taipei | grep -i x-upstream
```

> 由於 Nginx 預設使用 Round Robin，回應會以 `app1 -> app2 -> app1 -> app2 ...` 交替。

```bash
# 也可以直接看 servedBy 欄位
for i in 1 2 3 4 5 6; do
  curl -s http://localhost/api/weather/Taipei | jq '.servedBy, .cache'
  echo '---'
done
```

---

### 2. Cache Miss / Cache Hit — 驗證 Read-Through Cache

第一次請求某城市 (例如 `Tokyo`) 時，會經歷:

```
Cache MISS -> DB (Slave) MISS -> External API -> 寫入 Master -> 寫入 Redis
```

第二次同樣城市請求則直接 Cache HIT。

```bash
# 首次請求 - 預期 cache=MISS
curl -s http://localhost/api/weather/Tokyo | jq '{city, cache, servedBy}'

# 第二次請求 - 預期 cache=HIT
curl -s http://localhost/api/weather/Tokyo | jq '{city, cache, servedBy}'
```

觀察 Redis 內容:

```bash
docker exec redis redis-cli KEYS 'weather:*'
docker exec redis redis-cli TTL weather:tokyo        # 初始應 ≈ 3600 秒
docker exec redis redis-cli GET weather:taipei       # 查看快取內容
```

觀察 PostgreSQL 資料:

```bash
# 寫入 Master
docker exec -e PGPASSWORD=weatherpwd postgres-master \
  psql -U weather -d weatherdb \
  -c "SELECT city, forecast_date, temperature, description FROM weather_forecast ORDER BY forecast_date;"

# Slave 已複製同樣資料 (READ)
docker exec -e PGPASSWORD=weatherpwd postgres-slave \
  psql -U weather -d weatherdb \
  -c "SELECT COUNT(*) FROM weather_forecast;"
```

---

### 3. Message Queue — 驗證非同步解耦

每次呼叫 `GET /api/weather/{city}` 時，App 都會發送一則 `{city, timestamp}` 訊息到
RabbitMQ queue `weather.search.log`。
Consumer 會模擬 2 秒耗時任務後印出:

```
[Consumer] 非同步更新 [Taipei] 天氣統計分析完成
```

即時觀察:

```bash
# Tail Consumer 日誌
docker compose logs -f app1 app2 | grep -E "Publisher|Consumer|非同步"

# 或打開 RabbitMQ Management UI 觀察 queue 狀態
open http://localhost:15672         # macOS
# username: guest / password: guest
```

用 curl 連打一輪觀察:

```bash
for city in Taipei Tokyo Osaka Seoul Paris; do
  curl -s http://localhost/api/weather/$city > /dev/null
done
docker compose logs --tail=50 app1 app2 | grep -E "Publisher|Consumer"
```

---

## 架構圖 (文字版)

```
                      ┌──────────────┐
    client ──► :80 ──►│    Nginx     │  (Round Robin LB)
                      └──────┬───────┘
                 ┌───────────┴───────────┐
                 ▼                       ▼
            ┌─────────┐             ┌─────────┐
            │  app1   │             │  app2   │  (Stateless Spring Boot)
            └──┬──┬───┘             └──┬──┬───┘
               │  └────────┐   ┌───────┘  │
               │           ▼   ▼          │     ← 每次請求都發送 (async)
               │      ┌──────────┐        │
               │      │ RabbitMQ │        │
               │      │  Queue   │        │
               │      └──────────┘        │
               └─────────────┬────────────┘
                             ▼  ① 先查 Cache
                       ┌──────────┐
                       │  Redis   │  (TTL=1h)
                       └────┬─────┘
                         MISS↓ ② Cache Miss 才查 DB
                             ▼
                    ┌──────────────────┐
                    │   PG Slave (RO)  │  (reads)
                    └────────┬─────────┘
                          MISS↓ ③ DB Miss 才呼叫外部 API 並寫入
                             ▼
                    ┌──────────────────┐
                    │   PG Master      │──replication──► PG Slave
                    │   (writes)       │
                    └──────────────────┘
```

### 請求流程 (Cache Miss 情況)
1. Client → Nginx → 輪流挑 app1 / app2
2. App 在 Redis 查 `weather:{city}` → MISS
3. App 用 `@Transactional(readOnly=true)` 到 **Slave** 查 DB → MISS
4. 呼叫第三方 Weather API (本範例以 `ExternalWeatherClient` mock)
5. 以 `@Transactional`(寫) 寫入 **Master**
6. 寫入 Redis (TTL 1 小時)
7. Publisher 丟一則 `{city, timestamp}` 訊息到 RabbitMQ
8. 回傳 JSON (含 `cache: MISS`, `servedBy: app1|app2`)
9. Consumer 非同步處理 (不阻塞 HTTP 回應)

---

## Troubleshooting

| 症狀                                        | 處置                                                         |
| ------------------------------------------- | ------------------------------------------------------------ |
| `app1/app2` 啟動後立刻 exit                 | 通常是 DB 還沒 ready，已在 compose 設 healthcheck；重新 `docker compose up -d` 即可 |
| Slave 無法同步                              | `docker compose down -v` 清掉舊 volume 後重新啟動             |
| `curl: (52) Empty reply from server`        | 確認 `docker compose ps` 中 `nginx` 狀態為 Up                 |
| Redis 內容看不到                            | key 一律用小寫 (`weather:tokyo`)，因 Service 端做了 `.toLowerCase()` |
