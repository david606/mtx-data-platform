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

当前没有调度适配层，Quartz 也尚未进入任何模块的依赖。不要在 `app-admin` 或 `app-worker` 的业务类里直接创建 Quartz `Scheduler`。不在现有十模块之外新增模块。

## 到期接管

运行中的平台任务到期后，需要一次新的领取才会更换执行代次。租约到期本身不撤销旧代次，分区再分配也不代替这次领取。这项触发的候选调用链写在 [06-04-11](../investigation/round-01/06-04-11%20MTX%20Data%20Platform%20V2%20Worker%20Task%20Ownership%20与并发执行控制专项设计.md) 第 6.1 节。

候选分工是：`platform-core` 只定义执行权端口；`app-worker` 装配该端口的数据库实现，并在本应用内的调度适配中周期触发扫描。Quartz 类型不得进入 `platform-pipeline`。`app-admin` 不参与这次自动扫描。多个 Worker 同时扫描时，正确性靠任务行上的条件更新，不靠另一套分布式锁。

这只收窄「到期接管」这一项的候选归属。扫描查询只发现候选任务。每个任务的领取是独立事务，一个任务失败不撤销同一次扫描里已经提交的其他领取。重复触发、延迟触发和同一进程内的重叠扫描仍由任务行上的条件更新仲裁，不另写执行权。端口结果和失败分类见 06-04-11 第 6.1 节。`COMMIT` 结果不能确认时，结果是领取结果不确定，不是未领到，也不授予执行权。

其他定时任务的模块归属仍未逐项设计。本段不是已接受的 ADR，也不表示 Quartz 已经集成。本轮不引入 Quartz 依赖。进入实现前，Worker 执行权仍要单独的 ADR。扫描间隔、租约时长和 misfire 都尚未选定。DolphinScheduler 以后不得再对同一批任务触发这次扫描。
