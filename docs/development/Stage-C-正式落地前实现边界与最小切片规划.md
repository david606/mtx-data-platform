# Stage C 正式落地前实现边界与最小切片规划

- 状态：审查与规划。不是 Stage C 启动批准，不关闭任何 IR，不修改已有设计结论
- 后续路线：[Stage C 第一轮架构骨架落地路线与端到端链路规划](Stage-C-第一轮架构骨架落地路线与端到端链路规划.md)。该文件不改写本文第 11 节。
- 审查基线：`0d538f1edb013789f3f8309e7d050902d8707bd8`
- 审查方式：只读源码、测试、SQL、POM 和已入库文档。本轮不连接服务器，不执行测试，不改代码

本文里的 Stage C 沿用 [Stage B 整体能力盘点与阶段准入总结](stage-b/Stage-B整体能力盘点与阶段准入总结.md) 第 7 节：持久化、消息消费、执行权和 Pipeline 编排这一层实现。它不是 `06-04-24` 的 MySQL 实验阶段 C。

## 1. 审查结论摘要

1. 不能立即开始 Stage C 正式代码。现行结论仍是：没有一份写明范围、输入和排除项的单独批准之前，Stage C 不能整体启动，也不能先写局部代码、表或接口。见 Stage B 总结第 7.1 节和第 8 节，以及 [当前工程准入边界与下一阶段实施路线评估](当前工程准入边界与下一阶段实施路线评估.md)。DEV_ONLY 链路没有取代这份批准。
2. 可直接复用的正式能力只有已经独立存在的纯逻辑，主要是 `platform-core` 的 `ContractVersionUniqueness.resolve`。DEV_ONLY 调用了它，并没有把它变成正式链路。
3. Kafka 客户端、JDBC 写法和 Doris HTTP Stream Load 的若干传输约束有提取价值，但当前类都绑在 `devonly` 包、测试夹具和测试表上。本轮禁止把它们复制、改名或搬进正式包。
4. `DevOnlyChainMain`、虚构报文、`mtx_rdp_dev_only` 表、`READY/CLAIMED/FAILED/COMPLETED`、显式 `rerun-failed` 和夹具完成核对只属于测试入口。
5. 正式 Task、offset 提交、第一次快照、执行权、审计、原文介质、金额和 Doris 写入记录仍分别被未关闭的 IR 挡住。不能用 DEV_ONLY 单次成功代替这些能力。
6. 第一批正式代码没有可以绕开这些阻断的切片。下面第 7 节的三个切片是批准和对应决策完成之后的顺序，不是本轮开工清单。

## 2. 仓库基线

| 项 | 结果 |
| --- | --- |
| 分支 | `main`，跟踪 `origin/main` |
| HEAD | `0d538f1edb013789f3f8309e7d050902d8707bd8` |
| 与预期提交 | 一致，没有后续提交 |
| 审查开始时工作区 | 干净。本报告是本轮唯一新增文件 |

DEV_ONLY 链路的直接提交：

| 提交 | 说明 |
| --- | --- |
| `b148893` | `feat(dev-only): 接通 Kafka 到 Doris 的 DEV_ONLY 最小链路` |
| `0d538f1` | `fix(dev-only): 核对通过后才完成测试任务` |

更早的 `1889599` 只增加 [MVP-01端到端最小可运行链路实施准备](MVP-01端到端最小可运行链路实施准备.md)。该文件写于代码之前，其中“messaging、transform、writer、pipeline 没有 Java”已经不是当前树的事实。当前事实以源码为准。MVP-01 自己写明不启动 Stage C。

[06-04-01](../investigation/round-01/06-04-01%20MTX%20Data%20Platform%20V2%20工程实施就绪度评估报告.md) 第 9 节仍然写明：不可以开始金额领域、Pipeline 业务或持久化的 Java 实现，不可以开始 Worker 领取和执行实现，不可以开始端到端集成。DEV_ONLY 是后来的局部测试实现，本轮没有看到它被写进已接受 ADR，也没有看到它修订第 9 节。因此第 9 节继续约束正式代码。

## 3. DEV_ONLY 实际调用链

入口不在 `WorkerApplication.main`。`com.mtx.rdp.worker.WorkerApplication` 只启动 Spring。链路入口是 `com.mtx.rdp.worker.devonly.DevOnlyChainMain.main`。无参数调用 `DevOnlyChainRunner.run`；`rerun-failed <testTaskId>` 调用 `rerunFailed`。

### 3.1 首次运行

`DevOnlyChainRunner.run`：

1. 用 `FictionalMessageSource.message()` 取固定字节，再用 `DevOnlyPayload.marked` 要求同时含有 `DEV_ONLY` 和 `虚构数据`。
2. `DevOnlyRawMessageSender.send` 把字节发到 `DevOnlyKafkaSettings.TOPIC`（`mtx.dev-only.raw-message`）。生产者在 `platform-messaging`，`acks=all`，值是字节数组。
3. `DevOnlyRawMessageReceiver.read` 对刚返回的分区和 offset 做 `assign` + `seek`，关闭自动提交，读到同一 offset 的字节。这是单条测试读取。类注释写明不是正式消费组位点方案。
4. `DevOnlyMysqlStore.open` 用 JDBC 连接 MySQL。口令在连接属性里，不在 URL 里。`applySchema` 执行 `dev-only/mysql-schema.sql`。
5. `findByPosition` 按 topic、partition、offset 查找。已有行且状态不是 `READY` 时直接返回，不自动重试。没有行时 `insertRawAndTask` 在一个本地事务里插入 `dev_only_raw_message` 和 `dev_only_task`。标识是 `raw`、`task` 加上本次运行的 UUID，不是来源事件身份。
6. 插入成功后 `receiver.commit` 提交 offset+1。这一步早于 Doris。
7. `claim` 只把 `dev_only=1` 且 `READY` 的行改成 `CLAIMED`。随后 `beginRun` 插入 `dev_only_task_run`，`outcome=STARTED`。
8. `DevOnlyDorisQuery.applySchema` 执行 `dev-only/doris-schema.sql`，表是 `dev_only_result`。
9. `DevOnlySingleExecution.execute` 要求夹具 `devOnly=true`，并且已经带一份快照和条目。它调用 `ContractVersionUniqueness.resolve`，内部版本标识用 `InternalContractVersionId.absent()`。未解析则不写。解析后调用 `DevOnlyFieldPassthrough.pass`，再调用 Worker 传入的写入函数。
10. 写入函数拼一条 JSON，交给 `DorisStreamLoadWriter.load`。标签是 `devonly` 加运行标识。成功条件是 HTTP 200、`Status=Success`、`NumberLoadedRows>=1` 且 `NumberFilteredRows=0`。
11. `finishAfterWrite` 先查 Doris，再查 MySQL。`DevOnlyCompletion.mismatch` 核对运行标识、原文引用、Task ID、`dev-only-contract`、`DEV_ONLY fixture field`、DEV_ONLY 标记，以及 MySQL 原文和 Task 的关联，并要求 Task 仍是 `CLAIMED`。通过之后才 `markCompleted`。

### 3.2 持久化、事务、异常和重试

| 步骤 | 持久化位置 | 事务 | 失败时 |
| --- | --- | --- | --- |
| Kafka 发送和按 offset 读取 | 开发 Broker 上的测试主题 | 与 MySQL、Doris 都不是同一事务 | 字节不一致时不提交 offset |
| 原文和 Task 插入 | `mtx_rdp_dev_only.dev_only_raw_message`、`dev_only_task` | `insertRawAndTask` 内关闭自动提交，失败回滚 | 不提交 offset |
| offset 提交 | Kafka 消费位点 | 在 MySQL 插入提交之后、Doris 之前 | 之后失败不会撤回这个位点 |
| 领取 | 同一 Task 行，`READY` 到 `CLAIMED` | 单条条件更新 | 更新行数不是 1 则本轮不执行 |
| 执行记录 | `dev_only_task_run` | 重新进入时与状态更新同一事务；首次 `beginRun` 单独插入 | 见重试 |
| Doris 表和行 | `mtx_rdp_dev_only.dev_only_result` | HTTP Stream Load，不参与 MySQL 事务 | 不把 HTTP 200 且 `Fail` 当成成功 |
| 完成或失败 | Task 与当次 run 行 | `updateFinish` 同一事务 | 核对失败只在仍为 `CLAIMED` 时写成 `FAILED` |

`rerunFailed` 不读 Kafka。`DevOnlyRetryGuard.rejection` 拒绝空任务、`devOnly!=1`、`COMPLETED`，以及不是 `FAILED` 的状态。通过后 `reopenFailed` 锁定任务行，把当时的 `failure_reason` 复制到新 run 的 `prior_failure_reason`，再把状态从 `FAILED` 改回 `CLAIMED`。成功完成时不把任务行上的旧失败说明改成空。这不是正式恢复、接管或 fencing。`DevOnlyChainRunner` 的类注释写了同一边界。

异常信息会进入运行报告。Doris 重定向失败使用固定句子，不把 Location 或 `Authorization` 放进返回文本。MySQL 异常包装成固定中文句子，根因仍可能留在 `cause` 里。

### 3.3 对 DEV_ONLY 条件的依赖

整条 `run` 依赖：虚构来源、固定夹具快照 `dev-only-snapshot`、家族 `dev-only-family`、外部版本 `dev-only-v1`、契约 `dev-only-contract`、字段文本 `DEV_ONLY fixture field`、库名只允许 `mtx_rdp_dev_only`、BE 默认 `192.168.1.50:8040` 与 `192.168.1.51:8040`，以及调用方环境变量中的非 root 账号。`WorkerApplication` 不读取这些变量，默认启动不会跑这条链路。

### 3.4 代码、骨架、测试和未复核运行

| 类别 | 当前事实 |
| --- | --- |
| 实际代码 | 第 3.1 节列出的类和两份 SQL 都在树里 |
| 只有骨架 | `platform-quality` 仍没有 Java。正式 Task、正式消费组、正式原文介质、正式审计都不存在 |
| 测试覆盖 | `DevOnlyChainReviewTest` 用内存 `DevOnlyTaskBook` 覆盖完成前核对和重入拒绝。`DorisStreamLoadWriterTest` 用本地套接字覆盖重定向。它们不连接 MySQL 或 Doris |
| 本轮未独立验证 | `b148893` 与 `0d538f1` 的提交说明表示开发服务器上跑通过。本轮没有重跑，不把那次运行重新记成已验证 |

台账 IR-17 仍写 `platform-writer` 没有 Java 实现。当前树里已有 `devonly` 包的 Stream Load 类。这是台账句子相对后到代码的滞后。本轮不改台账，也不把滞后理解成 IR-17 已关闭。

## 4. DEV_ONLY 代码复用分类

### 4.1 A. 可直接复用的正式平台能力

| 能力 | 位置 | 为什么可以复用 |
| --- | --- | --- |
| 固定快照内的契约版本唯一性 | `platform-core` 的 `ContractVersionUniqueness.resolve` | 已实现的纯函数。不选快照，不访问存储，不依赖 `devonly`。`DevOnlySingleExecution.execute` 只是调用方之一 |

启动类、模块依赖和 ADR-0002 的十模块边界也是现行约束，但它们不是这条链路新产生的能力。

不能放进 A 的例子：Kafka 客户端已经能收发，JDBC 已经能写表，Stream Load 已经能导入一行。能运行只说明测试链路成立。

### 4.2 B. 可提取或重构后才可能复用

这些项现在都不能提取。提取本身就是正式代码，而第 6 节的批准还不存在。

| 当前代码 | 当前问题 | 以后的目标模块 | 抽取前必须先有 |
| --- | --- | --- | --- |
| `DorisStreamLoadWriter.acceptedTarget` 与成功条件 | 类在 `devonly`，成功后写入的是测试 JSON 和测试表；允许的 BE 是开发机地址 | `platform-writer` 里一个只负责 HTTP 传输的类型 | IR-17 把传输和写入账本分开，并明确这不是任务状态、不是原文库。不能把本类改名后当成正式 Writer |
| `DevOnlyRawMessageSender`、`DevOnlyRawMessageReceiver` | `assign` + `seek` 到刚发送的 offset，组名是测试组 | `platform-messaging` 的正式消费适配 | IR-03 的 offset 提交条件。06-04-28 的消费端设计仍是调查，不是已接受实现 |
| `DevOnlyMysqlStore.connect` | 只证明口令可以不进 URL、root 可以被拒绝 | 不单独立项。正式访问要等库表和字符集决定 | ADR-0005 仍未接入驱动、字符集、排序规则和隔离级别。06-04-61 未选定比较语义 |
| `DevOnlyCompletion` 的“先核对再写终态” | 核对的是夹具字段 | 不能把这个类提升为正式终态 | 正式终态属于 IR-07、IR-09，且物理列未建 |

### 4.3 C. 仅限 DEV_ONLY

下列实现不能当成正式生产能力：

- `DevOnlyChainMain`、`DevOnlyChainRunner.run`、`rerunFailed`
- `FictionalMessageSource`、`DevOnlyPayload`、`DevOnlyFixture`、`DevOnlyFieldPassthrough`
- `DevOnlyDatabaseName` 只允许 `mtx_rdp_dev_only`
- `dev_only_raw_message`、`dev_only_task`、`dev_only_task_run`、`dev_only_result`
- `READY`、`CLAIMED`、`FAILED`、`COMPLETED` 和 `DevOnlyRetryGuard`
- `DevOnlyCompletion` 对 `dev-only-contract` 与 `DEV_ONLY fixture field` 的核对
- `DevOnlyChainSettings`、`.env.example`、`DorisBeEndpoint` 的开发机默认地址
- `InMemoryRawMessageStore`。它不是这条链路的持久化，进程结束即消失

`platform-pipeline` 依赖仍只有 `platform-common` 和 `platform-core`。这个边界要保持。保持边界不等于 `DevOnlySingleExecution` 已经是正式编排。

### 4.4 D. 当前不能实现或不能定案

台账原文见 [06-04-02](../investigation/round-01/06-04-02%20MTX%20Data%20Platform%20V2%20工程实施未决设计决策台账.md)。下表不改变任何状态。

| 事项 | 台账状态 | 和正式链路的关系 |
| --- | --- | --- |
| IR-03 稳定事件身份、`taskId`、`replayRequestId` | 部分确认 / 待决策 | 无身份来源不建任务、不提交 offset。DEV_ONLY 的 UUID 不是这个身份 |
| IR-04 第一次快照 | 待决策 | `resolve` 不选择快照。DEV_ONLY 把快照写死在夹具里 |
| IR-07 Worker 执行权 | 设计已写明，ADR 未接受，实现未做 | `claim` 只更新测试行，没有代次、租约和 fencing |
| IR-10 审计 | 待决策 | 没有审计实体。运行报告不是审计 |
| IR-11 `parserVersion` | 待决策 | 链路没有解析器版本 |
| IR-12 阶段执行 | 待决策 | 禁止另造通用阶段实体。测试 run 行不是这个实体 |
| IR-13 金额计算身份 | 待决策 | 链路不解释金额 |
| IR-14 标准化版本 | 待决策 | `DevOnlyFieldPassthrough` 原样返回字符串，没有规则版本 |
| IR-15 原文保留 | 待决策 | 测试表中的 BLOB 不是已选介质 |
| IR-17 质量与 Writer 记录 | 待决策 | Doris 测试行不是写入账本，也不是任务库 |
| IR-19 E2E-001 至 E2E-183 | 待验证 | DEV_ONLY 测试不代替这些预期 |

另外仍挡住相关实现、但不在本轮指定清单里的有：IR-02 物理唯一约束待决策，IR-05 与 IR-06 待决策，IR-09 设计已明确但待验证。

## 5. Stage C 正式能力缺口

| 能力 | 状态 | 代码位置 | 约束 | 能否正式实现 | 是否进入第一批代码 |
| --- | --- | --- | --- | --- | --- |
| Kafka 接收与位点 | 仅 DEV_ONLY | `DevOnlyRawMessageSender`、`DevOnlyRawMessageReceiver.commit` | IR-03；06-04-28 仍是调查 | 否。无身份时不能提交 offset | 否 |
| 原始报文持久化 | 仅 DEV_ONLY | `insertRawAndTask` 与测试表 | IR-15 | 否。介质未选 | 否 |
| 稳定身份与 Create-or-Get | 尚未实现 | 无正式类。测试标识在 `DevOnlyChainRunner.run` | IR-03、IR-02 | 否 | 否 |
| Task 生命周期 | 仅 DEV_ONLY | `DevOnlyMysqlStore.claim`、`markCompleted`、`markFailed` | IR-07、IR-09 | 否 | 否 |
| Claim、Takeover、并发执行权 | 尚未实现 | 无代次和租约代码 | IR-07 | 否。设计未验证，ADR 未接受 | 否 |
| Pipeline 与 fencing | 仅 DEV_ONLY 的一次调用 | `DevOnlySingleExecution.execute` | IR-07；Pipeline 文档未冻结 | 否 | 否 |
| 快照固定与契约解析 | 解析已实现；选择和读取尚未实现 | `ContractVersionUniqueness`；夹具在 `DevOnlyChainRunner.fixture` | ADR-0004、IR-04 | 解析函数已可调用。第一次选择不能实现 | 否。不新增适配层 |
| 阶段上下文与恢复 | 尚未实现 | 无正式恢复点 | IR-12 | 否。不能先造阶段表 | 否 |
| 金额观察和标准化 | 尚未实现 | 无金额代码。passthrough 不是标准化 | ADR-0003、IR-13、IR-14 | 否 | 否 |
| 审计 | 尚未实现 | 无 | IR-10 | 否 | 否 |
| Doris 写入和结果核验 | 仅 DEV_ONLY | `DorisStreamLoadWriter`、`DevOnlyCompletion` | ADR-0005、IR-17 | 否。服务器版本不是写入记录设计 | 否 |
| 失败、重试、显式 Replay | 仅 DEV_ONLY 的显式重跑 | `rerunFailed`、`DevOnlyRetryGuard` | IR-03 的重放标识算法 | 否 | 否 |
| 可观测性与 E2E | 尚未实现正式验收 | `DevOnlyRunReport` 只是测试打印 | IR-16、IR-19 | 否 | 否 |

DEV_ONLY 一次成功不证明正式 Task 唯一、跨 Worker 执行权、fencing、exactly-once 或跨 MySQL 与 Doris 的事务。代码里的 offset 提交和 Stream Load 就是两次独立副作用。

## 6. ADR/IR 对正式开发的约束

- ADR-0002：仍是十个模块。持久化不新增模块。Pipeline 不依赖 Connector、Transform、Quality、Writer 的实现。Worker 装配，Core 不依赖 JDBC、Kafka 或 Doris。
- ADR-0003：只覆盖未识别金额和冲突观测。链路没有实现它，也不能把 passthrough 写成金额规则。
- ADR-0004：只覆盖固定快照内、提取前的外部契约版本解析。`resolve` 符合“不选择快照”。它不包含表、事务和 Java 之外的存储设计。
- ADR-0005：MySQL 8.0.46 与 Doris 4.0.7 是目标服务器版本。驱动、库表和字符集未完成。DEV_ONLY 使用的服务器实际版本不是本轮重新测量的对象；即使以前查询过，也不能把那次查询写成 8.0.46 已经接入。
- ADR-0001：只提供历史版本基线，不决定模块名单，也不批准这条链路。
- 06-04-01 第 9 节和第 10 节：正式实现仍排在存储职责、物理约束、事务提交点和 Worker 恢复之后。
- Stage B 总结第 7.1 节：身份、契约上下文、执行权、金额质量和审计分列阻断。不是每一条 IR 都挡住所有工作，但每一条都挡住自己那一列实现。

本轮没有发现可以在不触碰上表的前提下新增正式类的空隙。

## 7. 第一批正式开发切片

三个切片都依赖一份尚未存在的单独批准。批准必须写明范围和排除项，并写明不关闭未列出的 IR。在该批准和各自前置决策出现之前，不创建下列类，也不把 DEV_ONLY 类改名。

### 7.1 SC-1 正式原文保存

- 业务目标：把一份已经允许保存的来源字节留在选定介质中，并能按引用读回。
- 技术目标：只保存字节和引用。不建 Task，不提交“无任务也继续消费”的 offset。
- 模块：介质选定后落在现有模块。Worker 只装配。不新增模块。
- 预计的主要类型：一个原文写入端口和选定介质的一个实现。名称现在不定，避免提前占住正式 API。
- 已批准设计：2026-11-28 批准引用使用 UUIDv4、数据库类型 `BINARY(16)`、`raw_payload_ref` 为主键。正文列类型、最大报文字节数、表名和库名仍未批准。ADR-0005 只定了服务器版本。
- 阻断：IR-15。在介质、保留和删除规则写明之前不能建表，也不能沿用 `dev_only_raw_message`。
- 2026-11-28 的临时例外只覆盖这一张 SC-1 正式原文表的插入、按引用读取和读回核验。不允许删除、归档和自动清理。不关闭 IR-15。不是 DDL 执行授权。上面的阻断句不删除，例外也不扩大到其他表。
- 数据库：仅在介质决定为 MySQL 之后另写迁移。本轮不写 SQL。
- 测试：字节往返、无标记或未授权字节拒绝、重复引用不覆盖。不测 Task。
- 验收：读回字节与写入相同，且没有 Task 行。
- 不包含：消费组、offset、Task、快照、Doris。

### 7.2 SC-2 有稳定身份之后的 Task Create-or-Get

- 业务目标：同一来源身份只得到同一个非重放 Task。
- 技术目标：按来源契约已经声明的字段创建或取回。无身份则不建 Task，也不提交 offset。
- 模块：身份规则在 `platform-core`。持久化实现留在 Worker 所装配的现有模块。Pipeline 不直接依赖该实现。
- 预计的主要类型：身份解析与一次创建或取回。不使用 `task` 加 UUID。
- 已批准设计：逻辑规则来自已有 Pipeline 与 06-04-10 的调查结论，不是已接受 ADR。
- 阻断：IR-03 的字段和算法；IR-02 的物理唯一和并发失败后读取胜出行。若创建同时写 `snapshotId`，还受 IR-04 阻断。
- 数据库：任务行和唯一约束。列宽、字符集、排序规则未定，见 06-04-61。本轮不写 DDL。
- 测试：同一身份并发只留一行；无身份零行且不提交 offset；失败方读到胜出行。
- 验收：不能用先查后插、`INSERT IGNORE` 或 `ON DUPLICATE KEY UPDATE` 代替已有候选规则。
- 不包含：领取、接管、金额、Doris。依赖 SC-1 的原文引用只作为任务属性，不把引用当成全局正文身份。

### 7.3 SC-3 已验证执行权之后的一次领取

- 业务目标：同一时刻只有一个当前尝试可以执行。
- 技术目标：按 06-04-11 已写明、但尚未被 ADR 接受的代次和 `CURRENT` 规则领取。不把 DEV_ONLY 的 `CLAIMED` 映射成这套状态。
- 模块：执行权端口在 `platform-core`，装配在 `app-worker`。Quartz API 不进入 Pipeline。
- 预计的主要类型：领取端口和一次条件更新。正式类名等 ADR 接受后再定。
- 已批准设计：没有接受记录。06-04-11 是设计，不是 ADR。
- 阻断：IR-07 的列类型和 MySQL 8.0.46 并发证明；IR-09 仍待验证。依赖 SC-2 的任务行。
- 数据库：代次、租约、尝试归属。本轮不建实验表，也不把 06-04-24 的实验阶段 C 当成产品 Stage C。
- 测试：旧代次不能写入；领取结果不确定时不调用 Pipeline。
- 验收：单任务失败不撤销同批已提交领取；没有 fencing 测试则本切片不算完成。
- 不包含：金额、审计物理实体、Doris、自动接管的生产调度。

SC-1、SC-2、SC-3 都不是当前可以编码的工作。顺序只表示解除阻断之后谁先谁后。Doris 正式写入不在这三片里，因为它还要 IR-17，并且要有已提交的正式结果，而不是测试 JSON。

## 8. 实施顺序与里程碑

1. 审阅本规划。审阅不自动产生 Stage C 批准。
2. 若要做 SC-1，先决定 IR-15 的介质和保留规则，再写只覆盖原文保存的批准。
3. 若要做 SC-2，先有来源契约中的稳定身份，并有 IR-02 的物理约束。创建时写快照则先关闭 IR-04。
4. 若要做 SC-3，先接受并验证 IR-07，再写领取代码。
5. 之后才是正式契约读取、阶段恢复点、审计、金额规则和 Doris 分析副本。金额二十项仍是 `PENDING` 时不进入计算。
6. IR-19 的对应 E2E 行随上述实现补测。未运行的行保持未执行。

从当前树到第一条正式业务报文进入 Doris，至少还要：

- 一份 Stage C 局部批准。
- 原文介质（IR-15）。
- 能建 Task 的稳定身份，以及与之匹配的 offset 规则（IR-03）。
- 第一次快照规则（IR-04），如果这条报文要固定快照。
- 该报文要写入的记录的物理唯一约束（IR-02）。
- 若由 Worker 执行，则要已验证的执行权（IR-07）。
- 写入记录与 Doris 副本的边界（IR-17）。
- 针对这条路径的验收（IR-19）。

本报告不把“第一条报文不含金额”选成默认。报文若含金额，IR-13、IR-14 和二十项确认也在这条路径上。

## 9. 当前阻断事项

立即编码被下面几项同时挡住：

1. 没有 Stage C 范围批准。Stage B 总结第 8 节写明不能做局部代码准备。
2. 06-04-01 第 9 节仍禁止正式的持久化 Java、Worker 领取实现和端到端集成。
3. IR-15 挡住正式原文。
4. IR-03 与 IR-02 挡住 Task 和 offset。
5. IR-04 挡住第一次快照选择。
6. IR-07 与 IR-09 挡住正式领取和事务实现。
7. IR-17 挡住把 Doris 写成正式结果库或任务库。
8. 字符集、排序规则、列宽未选定。06-04-61 明确不把某种 collation 当成已选定。

不要求先关闭与所选切片无关的 IR。当前没有一个正式切片能够不碰到自己的阻断项。

## 10. 本轮未验证事项

- 没有执行 `./mvnw`，没有运行单元测试或 DEV_ONLY 链路。
- 没有连接 `dp`、`dp1`、Kafka、MySQL 或 Doris。
- 没有复核历史运行里的 `dev_run_id`、表行和 BE 端口。那些只存在于先前提交说明和当时的运行输出。
- 没有逐份重读 Pipeline 全文。IR 状态以 06-04-02 台账当前行和本报告点名的源码为准。
- MVP-01 与台账 IR-17 关于“writer 没有 Java”的句子没有改。它们和当前 `devonly` 源码不一致，留给以后的文档修订，不在本轮选择一边覆盖另一边。

## 11. 最终建议

1. 现在不开始 Stage C 正式代码。
2. 第一件正式工作不是新类，而是一份写明范围和排除项的批准。若只批准一个技术切口，优先问 IR-15 的原文介质，而不是继续加厚 DEV_ONLY。
3. 身份、第一次快照、执行权、原文介质、物理唯一约束和 Doris 写入记录必须先有决定，才能做第 7 节里对应的切片。
4. 第 4.3 节的类、表和入口保持隔离。不复制、不改名为正式实现。
5. DEV_ONLY 以后只作为测试夹具和回归入口。它不接入 `WorkerApplication` 的默认启动，也不处理正式业务报文。
6. 第一条正式业务报文进入 Doris 之前，仍缺第 8 节列出的批准、身份、快照、物理约束、执行权、写入边界和验收。金额报文还要另过金额规则。

本规划不重新盘点 Stage B，不关闭 IR，也不授权下一轮写 Java。
