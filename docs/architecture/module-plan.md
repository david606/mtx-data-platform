# 模块规划

本文描述 MTX Data Platform 的目标模块、依赖方向，以及初始化阶段实际创建的 Maven 模块。它不是详细设计，不定义 Pipeline 节点、表或接口字段。

## 初始化阶段实际创建

只创建了能够验证 JDK 25 与 Spring Boot 基线的模块：

| 模块 | 职责 | 原因 |
| --- | --- | --- |
| `rdp-common` | 平台标识等稳定共享类型 | 后续模块需要一个不依赖框架的公共位置 |
| `rdp-server` | Spring Boot 进程入口 | 用来验证应用能在 JDK 25 上完成上下文启动 |

其余目标模块没有创建。当前没有调用方，创建空模块会固定一套尚未评审的包结构。

## 目标模块

| 模块 | 职责 | 允许依赖 | 禁止 |
| --- | --- | --- | --- |
| `rdp-common` | 无业务框架的公共类型、错误码、时间与标识工具 | 无内部模块 | Spring、Quartz、Kafka、Doris 客户端 |
| `rdp-api` | 对外或跨模块的契约对象 | `rdp-common` | 持久化实现、调度框架 |
| `rdp-core` | 平台核心模型与端口定义 | `rdp-common` | 具体连接器、Quartz、DolphinScheduler |
| `rdp-connector` | 外部数据源读取适配 | `rdp-core`、`rdp-common` | 调度框架、写入 Doris 的策略 |
| `rdp-transform` | 数据转换 | `rdp-core`、`rdp-common` | 调度框架、直接写存储 |
| `rdp-quality` | 数据质量检查 | `rdp-core`、`rdp-common` | 调度框架 |
| `rdp-writer` | 数据写入，包括未来的 Doris 写入 | `rdp-core`、`rdp-common` | 调度框架、转换规则 |
| `rdp-pipeline` | 流程定义、节点组织、执行上下文、状态与结果 | connector、transform、quality、writer、core、common | Quartz API、DolphinScheduler API |
| `rdp-task` | 任务定义，以及与调度器无关的触发端口 | `rdp-core`、`rdp-common` | 直接 import Quartz Job |
| `rdp-scheduler` | Quartz 适配层：Cron、触发、Job 入口、触发状态 | `rdp-task` 的端口，而不是 Pipeline 内部类型 | 把 `org.quartz` 暴露给下游 |
| `rdp-worker` | 执行进程，调用 Pipeline 或任务端口 | pipeline、task 的公开入口 | 在 Worker 中实现第二套调度 |
| `rdp-messaging` | Kafka 生产与消费适配 | `rdp-core`、`rdp-common` | 替换为其他消息队列 |
| `rdp-integration` | 后续 DolphinScheduler 适配 | 与 `rdp-scheduler` 相同的任务触发端口 | 与 Quartz 同时调度同一任务 |
| `rdp-server` | 应用装配与进程入口 | 各模块的公开配置 | 堆放转换、质量检查和写入实现 |

依赖只能向下。`rdp-pipeline` 不依赖 `rdp-scheduler` 或 `rdp-integration`。调度器调用 Pipeline 或任务端口，而不是相反。

## 建议的落地顺序

1. 保持 `rdp-common` 与 `rdp-server` 可构建。
2. 评审任务触发端口后再创建 `rdp-core` 与 `rdp-task`。
3. 创建 `rdp-pipeline` 及其上下游（connector、transform、quality、writer）时，先落端口和测试，再接入真实 Doris 或业务源。
4. 创建 `rdp-scheduler` 时只放 Quartz 适配。Pipeline 模块的编译依赖中不能出现 Quartz。
5. Kafka 接入放在 `rdp-messaging`。DolphinScheduler 接入放在 `rdp-integration`，并先确定如何避免与 Quartz 双重触发。
6. `rdp-worker` 在执行模型明确后创建。`rdp-api` 在第一条对外契约明确后创建。

## 尚未决定

- 元数据库产品选型与表结构。
- Doris 库、表模型和官方 JDBC 驱动版本。
- Pipeline 节点的配置格式。
- Quartz JDBC JobStore 是否使用，以及集群部署方式。
- DolphinScheduler 的版本、部署形态，以及任务如何从 Quartz 迁移。
- `rdp-server` 与 `rdp-worker` 是一个进程还是两个进程。
