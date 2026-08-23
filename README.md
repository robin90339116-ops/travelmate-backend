# TravelMate Backend · AI 旅游导游后端(Spring Boot）

一款「边走边听」AI 旅游导游 App 的服务端。基于 **Spring Boot 3 + Java 17**,提供 AI 讲解/问答/视觉多模态、异步讲解生成、多人同游实时同步、账号鉴权与地图检索能力。

> 客户端为 HarmonyOS/ArkTS(独立仓库）。本仓库是完整、可独立运行的后端工程。

## ✨ 后端能力亮点

| 能力 | 实现 |
|---|---|
| **JWT 鉴权** | HMAC 签名的 access/refresh 双令牌,**refresh token 轮换**,设备会话管理,单设备踢下线 / 全端退出 |
| **Redis 缓存** | 热点目录读走缓存;`@Cacheable(sync=true)` 防**击穿**、缓存空值防**穿透**、随机 TTL 防**雪崩** |
| **异步 + 消息队列** | AI 讲解生成异步化:dev 走线程池、prod 走 **RabbitMQ**(死信队列 + 重试),经 **SSE** 向客户端推送进度 |
| **实时同步** | **WebSocket(STOMP)** 房间广播;`app.realtime.redis=true` 时经 **Redis Pub/Sub** 跨实例扇出 |
| **AI 多模态** | 接入通义千问(百炼,OpenAI 兼容)文本 + 视觉;system prompt + availableFacts 白名单**抑制幻觉** |
| **数据层** | Spring Data JPA;dev 用 H2、prod 用 MySQL;实体带索引,启动种子三城 City Walk 数据 |
| **地图** | 高德 Web 服务 POI 搜索 |
| **工程化** | 统一响应/全局异常、Bean 校验、JUnit 测试、Maven Wrapper、Docker Compose |

## 🚀 快速开始(零外部依赖)

dev 默认使用内存 H2、本地线程池异步、本地缓存,**无需数据库/Redis/MQ** 即可启动:

```bash
./mvnw spring-boot:run
```

启动后 `http://localhost:8787`。示例:

```bash
# 健康检查
curl http://localhost:8787/api/health
# 城市(种子数据)
curl http://localhost:8787/api/catalog/cities
# 登录(开发验证码 246810),拿到 accessToken
curl -X POST http://localhost:8787/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"phone":"13800000000","code":"246810","deviceName":"demo"}'
# 用 accessToken 创建同游房间
curl -X POST http://localhost:8787/api/teams \
  -H "Authorization: Bearer <accessToken>" -H 'Content-Type: application/json' -d '{"routeId":"route-beijing-axis"}'
```

## 🏭 生产模式(MySQL + Redis + RabbitMQ）

```bash
docker compose up -d           # 起 MySQL / Redis / RabbitMQ
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod,redis,mq
```

`prod` 切 MySQL 与 Redis 缓存,`redis` 开启跨实例广播,`mq` 启用 RabbitMQ 异步管线。真实 AI/地图能力需在 `.env` 配置 `DASHSCOPE_API_KEY`、`AMAP_WEB_KEY`(见 `.env.example`)。

## 📚 主要接口

| 模块 | 端点 |
|---|---|
| 认证 | `POST /api/auth/sms/code` · `POST /api/auth/login` · `POST /api/auth/refresh` · `POST /api/auth/logout` · `POST /api/auth/logout-all` · `GET /api/auth/sessions` · `DELETE /api/auth/sessions/{id}` |
| 目录 | `GET /api/catalog/cities` · `.../cities/{cityKey}/spots` · `.../cities/{cityKey}/routes` · `.../routes/{routeKey}` |
| AI | `POST /api/ai/explanations` · `/chat` · `/vision` · `POST /api/ai/explanations/async` · `GET /api/ai/jobs/{jobId}/stream`(SSE) |
| 同游 | `POST /api/teams` · `/join` · `GET /{id}` · `/{id}/playback` · `/{id}/leave` · `DELETE /{id}/members/{userId}` · `POST /{id}/transfer/{userId}` |
| 实时 | STOMP `ws://.../ws`,订阅 `/topic/teams/{teamId}`,发送 `/app/teams/{teamId}/playback` |
| 收藏 | `GET/POST /api/favorites` · `DELETE /api/favorites/{id}` |
| 地图 | `POST /api/map/search` |
| 运维 | `GET /api/health` · `GET /api/config/status` |

## 🧱 技术栈

Java 17 · Spring Boot 3.3 · Spring Web · Spring Security · Spring Data JPA · Spring Data Redis · Spring AMQP(RabbitMQ)· Spring WebSocket(STOMP)· MySQL / H2 · JJWT · Lombok · JUnit 5 · Maven · Docker Compose

## 🗂 架构分层

```
controller  →  service  →  repository(JPA)  →  MySQL/H2
                   │
                   ├─ QwenClient        (通义千问多模态)
                   ├─ Redis Cache       (穿透/击穿/雪崩)
                   ├─ RabbitMQ + SSE    (异步讲解 + 进度)
                   └─ STOMP + Redis PubSub (多人实时同步)
```

## 🧪 测试

```bash
./mvnw test
```

包含上下文装配测试与 JWT 轮换/签名单元测试。
