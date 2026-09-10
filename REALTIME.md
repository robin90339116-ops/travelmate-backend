# 实时视频讲解（Web / Java）

实时协议是持续16kHz单声道PCM音频、约1fps JPEG视频帧输入，24kHz PCM语音和字幕增量输出。不是单张照片接口轮询，也不是WebRTC视频通话。模型固定为 `qwen3-omni-flash-realtime-2025-12-01`，目前只开放已经确认的北京业务空间域名。

## 启动

私密配置保存在已忽略的 `.env.local`，不要提交或放入Vite变量。

```sh
./mvnw verify
./scripts/run-local.sh --server.port=18791 --app.realtime.enabled=true
```

前端启动时设置 `BACKEND_URL=http://127.0.0.1:18791`。默认不开启实时服务，避免无意调用。`local` 使用文件H2；生产需数据库迁移方案，不能照搬自动DDL。

- `GET /api/realtime/status`：需要Bearer登录，返回模型、可用状态和剩余验收会话数。
- `WS /ws/realtime`：首个消息为 `{type:"start",token:"<access token>",spotId:"1"}`。令牌不在URL；服务端验证后才连接模型，密钥永远不发往浏览器。
- 客户端只能追加PCM、JPEG，清空音频、取消回答或停止；不允许覆盖模型、system prompt或输出限制。
- 原有SockJS通道也匹配`/ws/**`；实时精确路由优先级为-1，已增加真实握手测试防止404回归。

## 验收安全限额

每账号一条活动连接，每次最多60秒和3轮输出；每个数据库最多5次验收会话（失败连接也扣次数）。记录保存在 `realtime_budget`，文件数据库重启不重置。

这不是人民币账单，也不能防止其他接口/账号产生费用；绝不能宣称已实现50元账户级硬上限。当前没有浏览器或公开API重置额度入口。核对账单后再决定后续额度策略，不要通过删除数据库恢复次数。

未保存原始音视频。用户停止、离页/后台、权限拒绝、设备断开和网络错误都会清理设备与连接。第三方保留策略不由本站控制。输入帧做格式/频率/大小防护，并非恶意图像内容安全检测。实例内连接上限不等于生产分布式限流。

## 已执行真实验证

2026-09-10，一次合成语音+480×360蓝色方块JPEG验证，经Java网关调用真实模型，收到31个音频块（476160字节）和34个字幕增量，描述正确。用量：输入659 token（文本292、音频37、视频330），输出158（文本34、音频124）。精确收费以百炼账单为准。此前四次文本/图片API测试见Web验收报告。

实机摄像头、回声/打断效果、移动端音频权限和真实景点准确性尚未因此得到证明。HTTPS部署、高德和NLS配置仍是独立验收项。

官方协议：
- https://help.aliyun.com/zh/model-studio/realtime
- https://help.aliyun.com/zh/model-studio/client-events
- https://help.aliyun.com/zh/model-studio/qwen3-omni-flash-realtime
