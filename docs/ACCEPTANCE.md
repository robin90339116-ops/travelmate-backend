# 验收记录（2026-09-07）

## 本轮范围

Java后端修复与补齐，不包含新前端制作、鸿蒙真机、支付或应用商店发布。
原仓库基线6de333e；首次核验发现令牌轮换、会话撤销、实时越权、任务状态丢失等问题。

## 本轮验收结果

- JUnit：21项通过（上下文1、JWT3、业务集成10、AI契约5、语音契约2）。
- 独立进程HTTP/WebSocket/SSE：31项通过，见runtime-results.json。
- MySQL/Redis/RabbitMQ及双实例：9项通过，见infrastructure-results.json。
- AI和语音成功响应使用测试替身；基础设施为真实Docker容器。这些数字不是生产负载或模型质量指标。

## 实现及证据

- refresh token完整摘要与悲观行锁：旧token重放401；两个并发刷新只有一个成功。
- HTTP/STOMP关联真实设备会话：全端退出及设备撤销后失效；匿名WS不能修改房间，合法成员可收发。
- 成员鉴权、自动队长交接、输入校验、事务提交后广播。
- 数据库保存AI任务，原子领取、终态查询、归属检查、取消及失联超时。
- 受控模型HTTP替身验证请求/响应与503重试；验证路线规划拒绝候选集外ID。未测真实模型准确率。
- 补充共享提问、数据导出/注销、密码注册登录、高德步行接口与NLS短语音适配。
- Redis真实容器验证城市列表/路线详情重复命中，并验证两实例同游广播。
- MySQL真实容器验证启动与跨实例会话共享。
- RabbitMQ真实容器验证消费、跨实例任务结果、失败状态及死信队列。

## 可重复执行

需要Java17、Python3、支持全局WebSocket的Node.js（如Node22）。先执行 `./mvnw verify` 生成JAR，再：

```sh
python3 scripts/smoke.py
docker compose -p travelmate-validation -f scripts/infra-test.yml up -d --wait
python3 scripts/infra_audit.py
docker compose -p travelmate-validation -f scripts/infra-test.yml down -v
```

smoke使用18787；infra使用18788/18789/18890及23306/26379/25672/25673。务必确保这些是空闲测试端口。测试脚本仅创建临时账号并关闭自己启动的Java进程，失败返回非零状态。最后的down仅用于这里创建的独立测试项目，不要替换为真实部署项目名。

## 未完成的外部验收

1. 百炼/其他模型：需要真实API Key、可用模型ID，验证文本、图片、视频（各模型能力不同）、计费及质量。
2. 高德：需要Web服务Key验证真实地点和步行路径。
3. 阿里云NLS：需要AppKey和有效Token验证识别与MP3；Token自动续期未实现。
4. 鸿蒙/网页：客户端需要按新REST和STOMP契约联调。没有真机视频流、锁屏音频同步验收。
5. 正式上线：仍需版本化数据库迁移、备份恢复、HTTPS、监控告警、共享限流及真实用户测试。

不能将上述未测项写成已上线或“全功能生产稳定”。简历可写实际实现和测试；AI辅助编程、复现问题、设计修复与验收过程可以如实说明。
