# 调度与工作流边界

调度职责以本文和 [ADR-0002](../decisions/0002-v2-module-architecture.md) 为准。本文只固定边界，不规定 Job 类、Cron 表达式、DolphinScheduler 工作流，也不确定调度实现最终放在哪个现有模块。那部分以后续详细设计为准。

当前骨架没有调度实现。Quartz 尚未进入任何模块的运行时依赖。不要把下面的目标职责写成已经完成的能力，也不要新增 `platform-scheduler` 模块。

## Quartz

Quartz 2.5.2 是初期调度方向，版本由 Spring Boot 3.5.16 BOM 管理。它的目标职责是：

- Cron 定时任务。
- 定时任务触发。
- Job 执行入口。
- 任务触发状态管理。
- 基础任务调度。

这些职责尚未实现。Quartz 不负责数据处理 Pipeline。将来的调度适配只能通过 `platform-core` 中的抽象与执行机制协作，不能把 Quartz API 传入 `platform-pipeline`、`platform-connector`、`platform-transform` 或 `platform-writer`。

## 自研 Pipeline

`platform-pipeline` 的目标职责是：

- 数据处理流程定义。
- 处理节点组织。
- 节点执行上下文。
- 数据转换和质量检查的编排。
- 数据写入的编排。
- 执行状态和结果管理。

这些职责尚未实现。`platform-pipeline` 只依赖 `platform-common` 与 `platform-core`。它不依赖 Connector、Transform、Quality、Writer 的实现，也不依赖 Quartz 或 DolphinScheduler。

## Apache DolphinScheduler

DolphinScheduler 只作为未来复杂 DAG 与工作流场景的演进方向。目标范围是：

- DAG 工作流编排。
- 任务依赖管理。
- 工作流实例管理。
- 分布式任务调度。
- 工作流运行状态管理。
- 失败重试和运行治理。

当前工程不引入 DolphinScheduler 依赖，也不预置工作流定义。它不是当前正在使用的调度器。

## 演进

初期方向是 Quartz。复杂工作流以后可以评估 DolphinScheduler。两个框架都只能通过 Core 的抽象协作，不能把各自的 API 传到 Pipeline 及其下游。

禁止 Quartz 与 DolphinScheduler 对同一个任务重复调度。真的迁移某个任务时，需要明确该任务只保留一个触发源。触发来源如何记入运行记录，尚未设计。

当前没有调度适配层。不要在 `app-admin` 或 `app-worker` 里临时直接创建 Quartz `Scheduler`。调度实现的模块归属和集成方式待详细设计，不在现有十模块之外新增模块。
