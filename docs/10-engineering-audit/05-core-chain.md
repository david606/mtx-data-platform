# 05 核心链路

状态取值：未发现实现、只有接口、有基础实现、已集成、已测试、已验收。

检索范围是本仓库全部 Java、配置、SQL 和文档文件名。除启动类和平台常量外，没有对应类型、接口或测试。

| 环节 | 状态 | 证据 |
| --- | --- | --- |
| 外部回调接入 | 未发现实现 | 无 Controller。`application.yml` 无 Web 或端口配置。`rdp-server` 依赖的是 `spring-boot-starter`，依赖树中无 `spring-boot-starter-web` |
| 原始报文留存 | 未发现实现 | 无相关类、表、SQL |
| Outbox | 未发现实现 | 全仓库无 `outbox` 匹配 |
| Kafka Producer | 未发现实现 | `spring-kafka` 只在父 POM dependencyManagement。没有模块依赖它，无 Producer 类和 Kafka 配置 |
| Kafka Consumer | 未发现实现 | 同上 |
| 消费幂等 | 未发现实现 | 无消费代码，也无幂等存储 |
| Task Definition | 未发现实现 | 无任务定义类或表 |
| Task Execution | 未发现实现 | 无 `dp_task_execution`，无执行类 |
| Worker 注册和心跳 | 未发现实现 | 无 `app-worker`，无心跳接口 |
| 任务领取与租约 | 未发现实现 | 无领取或租约代码 |
| Connector | 未发现实现 | 无 `platform-connector` |
| Transform | 未发现实现 | 无 `platform-transform` |
| Quality | 未发现实现 | 无 `platform-quality` |
| Writer | 未发现实现 | 无 `platform-writer` |
| Doris Stream Load | 未发现实现 | 无 Doris 依赖、客户端或 HTTP 加载代码 |
| UNKNOWN 对账恢复 | 未发现实现 | 无 UNKNOWN 状态类型或对账任务 |
| Pipeline 执行 | 未发现实现 | 无 `platform-pipeline`，无执行入口 |

没有任何一项达到“只有接口”或更高状态。不存在已验收证据。
