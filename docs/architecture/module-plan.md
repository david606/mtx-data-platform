# 模块规划

> 状态：历史规划。本文自下一节起保留初始化阶段的原文，不作为当前实施依据。
>
> 当前 V2 模块结构以 [ADR-0002](../decisions/0002-v2-module-architecture.md) 为准。当前唯一有效的 Maven 结构是十模块：`platform-common`、`platform-core`、`platform-messaging`、`platform-connector`、`platform-transform`、`platform-quality`、`platform-writer`、`platform-pipeline`、`app-admin`、`app-worker`。
>
> `rdp-common` 与 `rdp-server` 是旧工程模块。依赖与代码价值审查后，它们已从当前工程删除，不属于 V2 十模块。下文表格仍是当时的历史规划，不是要求把这些目录加回来。
>
> 下文中的 `rdp-*` 名单、依赖方向和落地顺序都是当时的规划。其中 `rdp-pipeline` 依赖具体能力模块、以及单独的 `rdp-scheduler`、`rdp-task`、`rdp-api`、`rdp-integration`，与 ADR-0002 冲突。后续实施不得用这些旧结构覆盖 ADR-0002，也不得据此增加第十一模块。

## 初始化阶段实际创建

以下为历史原文。它记录的是十模块骨架落地前实际创建过的模块，不是当前反应器。

只创建了能够验证 JDK 25 与 Spring Boot 基线的模块：

| 模块 | 职责 | 原因 |
| --- | --- | --- |
| `rdp-common` | 平台标识等稳定共享类型 | 后续模块需要一个不依赖框架的公共位置 |
| `rdp-server` | Spring Boot 进程入口 | 用来验证应用能在 JDK 25 上完成上下文启动 |

其余目标模块没有创建。当前没有调用方，创建空模块会固定一套尚未评审的包结构。

## 目标模块

以下表格是历史目标，不是 V2 当前模块。

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

以下顺序是历史建议，不要按此创建 `rdp-*` 模块。

1. 保持 `rdp-common` 与 `rdp-server` 可构建。
2. 评审任务触发端口后再创建 `rdp-core` 与 `rdp-task`。
3. 创建 `rdp-pipeline` 及其上下游（connector、transform、quality、writer）时，先落端口和测试，再接入真实 Doris 或业务源。
4. 创建 `rdp-scheduler` 时只放 Quartz 适配。Pipeline 模块的编译依赖中不能出现 Quartz。
5. Kafka 接入放在 `rdp-messaging`。DolphinScheduler 接入放在 `rdp-integration`，并先确定如何避免与 Quartz 双重触发。
6. `rdp-worker` 在执行模型明确后创建。`rdp-api` 在第一条对外契约明确后创建。

## 尚未决定

以下事项在这份历史规划写下时尚未决定。其中模块形态已被 ADR-0002 取代；数据产品、表结构和调度集成细节仍待后续详细设计，本文不补写结论。

- 元数据库产品选型与表结构。
- Doris 库、表模型和官方 JDBC 驱动版本。
- Pipeline 节点的配置格式。
- Quartz JDBC JobStore 是否使用，以及集群部署方式。
- DolphinScheduler 的版本、部署形态，以及任务如何从 Quartz 迁移。
- `rdp-server` 与 `rdp-worker` 是一个进程还是两个进程。
