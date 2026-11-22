# 调度与工作流边界

本文固定初始化阶段已经确认的职责划分。它不描述具体 Job 类、Cron 表达式或 DolphinScheduler 工作流 JSON。

## Quartz

Quartz 是初期任务调度框架，负责：

- Cron 定时任务。
- 定时任务触发。
- Job 执行入口。
- 任务触发状态管理。
- 基础任务调度。

Quartz 不负责数据处理 Pipeline 的业务逻辑。触发之后应调用平台自己的任务端口。Job 实现留在调度适配模块内。

## 自研 Pipeline

Pipeline 负责：

- 数据处理流程定义。
- 处理节点组织。
- 节点执行上下文。
- 数据转换和质量检查。
- 数据写入。
- 执行状态和结果管理。

Pipeline、Connector、Transform、Writer 的核心逻辑不依赖 Quartz，也不依赖 DolphinScheduler。它们可以被任一调度适配层调用，也可以由测试直接调用。

## Apache DolphinScheduler

DolphinScheduler 是后续的工作流编排和分布式调度能力，负责：

- DAG 工作流编排。
- 任务依赖管理。
- 工作流实例管理。
- 分布式任务调度。
- 工作流运行状态管理。
- 失败重试和运行治理。

当前工程不引入 DolphinScheduler 依赖，也不预置工作流定义。

## 演进

初期使用 Quartz。后续通过调度适配层接入 DolphinScheduler。两个框架都只能看到任务触发端口，不能把各自的 API 传到 Pipeline 及其下游。

禁止 Quartz 与 DolphinScheduler 对同一个任务重复调度。迁移某个任务时，需要明确该任务只保留一个触发源，并在运行记录中能够区分触发来源。

初始化代码里还没有调度适配层。`rdp-server` 不得临时直接创建 Quartz `Scheduler`。
