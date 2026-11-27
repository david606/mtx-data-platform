# Stage B 整体能力盘点与阶段准入总结

- 总结基线：`5d4b2d2a4394a6e9a2609751fcbf342e05792a0d`
- 分支：`main`
- 状态：阶段总结。不新增实现，不改变 ADR 或 IR 状态。

本文中的 Stage B 指可独立验证的业务纯逻辑。`06-04-24` 里的阶段 A、阶段 B、阶段 C 是 MySQL 8.0.46 实验阶段，不是本文的 Stage B 或 Stage C。

## 1. 调查基线

| 材料 | Commit | 说明 |
| --- | --- | --- |
| 本次总结 | `5d4b2d2a4394a6e9a2609751fcbf342e05792a0d` | `docs(development): 完成Stage B无持久化上下文能力盘点` |
| 契约版本唯一性授权 | `baf658a414d18c3794fac4a849ef5e6c2874b1d7` | 状态记为 `APPROVED`。准备与边界收紧分别是 `f57bae57`、`0b6360a` |
| 契约版本唯一性实现 | `d408e1c52239ff398405af79b561e9dba0d74fe8` | `platform-core` 的纯函数与单元测试。没有单独的实现报告文件 |
| 配置校验盘点 | `f676d3838ae489d25c6c8788413089388c02d631` | [配置校验能力盘点与实现准入评估](配置校验能力盘点与实现准入评估.md) |
| 质量与转换盘点 | `ac873d568a813a51db3204a2e311b9265fede97a` | [质量规则与转换规则能力盘点](质量规则与转换规则能力盘点.md) |
| 无持久化上下文盘点 | `5d4b2d2a4394a6e9a2609751fcbf342e05792a0d` | [无持久化执行上下文能力盘点](无持久化执行上下文能力盘点.md) |

调查范围是上述材料、`AGENTS.md`、ADR-0001 至 ADR-0005、台账中第 3 节列出的 IR，以及十个模块的当前 Java。集成审查仍以 [WP-MA-INT-integration-review.md](../multi-agent-trial/WP-MA-INT-integration-review.md) 的现文为准。本轮没有执行 `./mvnw`，也没有重跑测试。`d408e1c` 提交说明里的测试结果不属于本次验证。

## 2. Stage B 的目标与完成标准

Stage B 负责实现已经确认、可独立验证的业务纯逻辑。它不引入数据库、Kafka、Worker 执行权或 Pipeline 编排，也不提前决定未冻结的业务语义。它可以为后续阶段提供已经实现的规则边界，但不能代替尚未作出的决策。

| 标准 | 当前判断 |
| --- | --- |
| 调查完成 | 五个领域都有仓库内证据。契约领域的证据是授权和代码，其余四个领域各有盘点 |
| 设计决策完成 | 否。金额二十项、IR-02 至 IR-05、IR-10 至 IR-14、IR-17 仍未关闭 |
| 实现授权完成 | 只有契约版本唯一性计数获得有限批准 |
| 代码实现完成 | 只有该计数函数，以及更早已经存在的启动核对 |
| 测试验证完成 | 本轮未执行。不能把历史提交中的测试写成这次通过 |

五个领域的调查可以收口。这不等于业务设计已经冻结，也不等于所有将来可独立实现的纯逻辑都已经写完。

## 3. 五个能力领域

| 能力领域 | 调查 | 已有实现 | 实现授权 | 新增候选 | 阻断 | 阶段结论 |
| --- | --- | --- | --- | --- | --- | --- |
| 契约版本唯一性解析 | 已完成 | `ContractVersionUniqueness.resolve` 与 `ContractVersionUniquenessTest` | 已批准，只限内存计数和纯单元测试 | 0。不扩展为完整 ADR-0004 | ADR-0004 第 155 行仍写 Java 等待文档修订。本授权不改这句。身份、哈希和双失败仍是 IR-05 | 已实现这一段 |
| 配置校验 | 已完成 | `StartupChecklist.check`、`ApplicationRole.parse` | 不需要新的授权。规则已经在代码中 | 0。CONFIRMED 的新规则数量是 0 | 其余 yml 键没有已接受合法性规则。健康检查依赖 `HealthEndpoint` | 无新增候选 |
| 质量规则 | 已完成 | 无。`platform-quality` 没有 Java | 未批准 | 0 | IR-17 待决策。ADR-0003 不含标准化算法 | 无新增候选 |
| 数据转换 | 已完成 | 无。`platform-transform` 没有 Java | 未批准 | 0 | 金额二十项全部 `PENDING`。IR-13、IR-14 待决策 | 阻断 |
| 无持久化执行上下文 | 已完成 | 无 `Context` 类 | 未批准 | 0 | Pipeline 第 16 节尚未冻结。物理承载由 IR-02 的 A-12 保持不选 | 阻断 |

契约实现不包含快照选择、家族身份、`contractId`、外部版本一致性或正文哈希。`CONFLICT` 不生成 `NormalizedAmount` 是 ADR-0003 的观测边界，不是质量引擎。`NO_CONVERT` 在未冻结设计稿中表示不换算，不是已批准的转换算法。

## 4. 当前已实现能力

业务纯逻辑只有一项。

| 类 | 模块 | 职责 | 入口 | 测试 | 依赖 | 状态 |
| --- | --- | --- | --- | --- | --- | --- |
| `ContractVersionUniqueness` | `platform-core` | 在调用方已经给出的快照条目中定位或计数契约版本 | `resolve(ContractVersionUniquenessQuery)` | `ContractVersionUniquenessTest` | 只依赖 `platform-common`。测试依赖 `junit-jupiter` | 已实现。输入类型包括 `ExternalContractVersion`、`InternalContractVersionId`、`ContractFamilyId`、`SnapshotContractVersionEntry` |

工程基础能力已经存在，但不是业务纯逻辑：

| 类 | 模块 | 职责 | 入口 | 测试 | 依赖 | 状态 |
| --- | --- | --- | --- | --- | --- | --- |
| `StartupChecklist` | `platform-common` | 核对应用名、角色和激活 profile | `check`。由 `AdminProcessStartup` 与 `WorkerProcessStartup` 调用 | `StartupChecklistTest` | 不依赖 Spring | 已实现 |
| `ApplicationRole` | `platform-common` | 识别 `admin` 与 `worker` | `parse` | 同上 | 不依赖 Spring | 已实现 |
| `AdminApplication`、`WorkerApplication` | `app-admin`、`app-worker` | 启动各自进程 | `main` | `AdminApplicationTest`、`WorkerApplicationTest` | Spring Boot 启动器 | 骨架。不执行数据处理 |

`platform-messaging`、`platform-connector`、`platform-transform`、`platform-quality`、`platform-writer`、`platform-pipeline` 只有 POM，没有 Java。模块存在不等于功能已实现。`trade-flow` 的表和类不是 V2 能力。

## 5. 未实现能力与阻断

状态均按当前 [06-04-02 台账](../../investigation/round-01/06-04-02 MTX Data Platform V2 工程实施未决设计决策台账.md) 核对，不是根据前几份盘点转述。

| 能力 | 当前状态 | 阻断来源 | 挡住的实现 | 是否有不依赖它的独立工作 | 能否在 Stage C 开始时直接做 |
| --- | --- | --- | --- | --- | --- |
| 稳定事件身份与 Task Create-or-Get | IR-03：部分确认 / 待决策 | 台账 IR-03；Pipeline 第 4.2 节 | 建任务、重复投递回到原任务、安全提交 offset | 已实现的版本计数不依赖它 | 否 |
| 快照选择 | IR-04：待决策 | 台账 IR-04；ADR-0004 仍把 R15 标为待决 | 无子批次时选择哪份快照，以及校验失败后能否改选 | 版本计数要求调用方已经固定 `snapshotId` | 否 |
| 契约身份与正文哈希 | 规则已写入 ADR-0004，代码未做 | ADR-0004 决策节；实现授权第 2 节明确排除 | 权威版本确认 | 唯一性计数已经分开实现 | 否。它依赖已胜出快照，不是另一个纯函数授权 |
| 双失败错误码 | IR-05：待决策 | 台账 IR-05 | 身份与哈希同时失败时保留哪一个已有码 | 唯一性计数不返回这两个码 | 否 |
| `parserVersion` 登记 | IR-11：待决策 | 台账 IR-11 | 解析器版本的注册、发布和重放 | 无 | 否 |
| `TaskExecutionContext` 两阶段持久化 | 逻辑身份已写，承载不选 | `06-04-23` 第 6.2 节；`06-04-24` 第 8.1、10 节。A-12 未执行 | 嵌入任务行或独立成表，以及两阶段条件写入 | 无。内存对象不能代替这个选择 | 否 |
| 金额标准化与转换 | 二十项全部 `PENDING` | `06-03-03-11` 第 11、13 节；IR-13、IR-14 待决策 | 分类、拒绝、`Money`、换算和计算 | 无。第 11 节写明当前一项都不能写 | 否。确认后的第一段仍是纯逻辑，不是持久化 |
| 质量规则 | 没有规则目录 | IR-17：待决策 | 质量引擎和质量结果表 | 无 | 否 |
| Worker 执行权与事务 | IR-07、IR-09：设计已明确 / 待验证 | 台账两行都写 ADR 未接受，MySQL 验证未执行 | 领取、接管、租约、终态和消费位点 | 无。设计明确不等于可以编码 | 否 |
| 审计物理存储与恢复 | IR-10：待决策 | 台账 IR-10。定义 B 与规则 C 的架构栏仍空，见集成审查 INT-03 | 审计表、补写和跨进程恢复 | 无。它不决定 `TaskExecutionContext` 的承载 | 否 |

集成审查 INT-01 至 INT-27 的状态列都仍以未关闭开头。本总结不关闭、不重判这些行。

IR-18A 是待验证：八条契约版本规则的现行正文被记为一致，核验不授权修改 ADR。IR-18B 是已确定：它要求引用时分开 ADR、专项设计、Pipeline、实现和测试的层级。这两行都不提供新的实现许可。

## 6. 独立实现候选

当前已经实现的独立业务纯逻辑是 1 项：契约版本唯一性计数。

当前没有另一项已经确认、可以新增实现的候选。没有需要用户再批一项实现授权的候选项。不应继续编写 Stage B 代码，也不为了增加代码而新增抽象。

金额分类在二十项变为 `CONFIRMED` 且设计稿按该选择修订之后，`06-03-03-11` 第 11 节才允许进入纯逻辑。那是将来的再入条件，不是现在的候选。

## 7. Stage C 前置条件

仓库没有名为 Stage C 的已接受 ADR。下文的 Stage C 只表示持久化、消息消费、执行权和 Pipeline 编排这一层实现。它不是 `06-04-24` 的实验阶段 C。

### 7.1 最低启动条件

在没有一份写明范围、输入和排除项的单独批准之前，Stage C 不能整体启动，也不能先写局部代码、表或接口。

不是每一项未关闭 IR 都同等阻断所有工作。下面按类别列出真正挡住对应实现的事项。

| 类别 | 必须先有明确决定的事项 | 当前不能开始的实现 |
| --- | --- | --- |
| A. 业务身份与任务 | IR-03 的稳定事件身份、`taskId` 和 Replay 身份 | Task Create-or-Get、重复投递和 offset 提交 |
| B. 契约与上下文 | IR-02 的物理承载、IR-04、IR-05、IR-11、IR-12 | 快照固定、权威契约确认、解析器版本和阶段执行实体 |
| C. 执行权与事务 | IR-07、IR-09 从“待验证”进入已验证，并有接受记录 | 行锁、租约、接管、fencing 和消费端实现 |
| D. 金额、质量、转换 | 二十项、IR-13、IR-14、IR-17 | 标准化、计算、质量规则。金额纯逻辑即使获准，也不属于 Stage C |
| E. 审计与文档层级 | IR-10。IR-18A 只要求不把旧说明当成规则冲突。IR-18B 已经确定引用方式 | 审计存储。文档层级本身不启动代码 |

### 7.2 可以并行、但不是代码

下列工作可以单独继续，而且都还不是 Stage C 实现：

- 由负责人填写金额二十项。`PENDING` 不能直接改成 `CONFIRMED`。
- 单独决定 IR-04 与 IR-05。
- 核对台账 IR-14 的 `normalizationRuleVersion` 与 `06-04-24` 的空 `conversionRuleVersion` 是否同一事项。`06-04-24` 第 11 节写明当时不选一句覆盖另一句。
- 文档治理可以以后修订 IR-18A 指出的过时交叉引用。该核验不授权改 ADR。

`06-04-24` 的实验阶段 A 仍规定不连接数据库。实验阶段 C 的正式 PASS 还要另有授权，不能借本文启动。

## 8. 阶段准入结论

| 问题 | 结论 |
| --- | --- |
| Stage B 调查是否完成 | 是。五个领域都已有证据 |
| 是否还有新增代码 | 否 |
| Stage B 能否标记为完成 | 调查可以收口。不能标记为业务设计已冻结，也不能标记为全部纯逻辑已实现 |
| Stage C 能否整体启动 | 否 |
| Stage C 能否做局部代码准备 | 否 |
| 进入 Stage C 前还要什么批准 | 一份写明范围的单独批准，并且该范围所依赖的上表事项已经关闭。现有 ADR 没有给出这份批准 |

具体缺口是：除唯一性计数外，其余业务语义仍停在未决或未冻结文档。金额二十项确认后，还可能回到纯逻辑，而不是直接进入 Stage C。

## 9. 下一步

先审阅本总结。审阅通过前不编写 Stage C 代码，也不再追加 Stage B 实现。

审阅后若要继续，优先顺序是决定，不是编码：

1. 金额二十项的书面确认，或明确继续搁置。
2. IR-04 与 IR-05 的单独决定。
3. IR-03 的稳定事件身份。
4. IR-02 中 A-12 的承载选择。该选择仍是两个分支不能合并。
5. IR-07 与 IR-09 的验证前置条件。验证本身不是现在开始写 Worker。

## 10. 证据索引

| 结论 | 证据 |
| --- | --- |
| 唯一性授权范围 | [契约版本唯一性解析纯逻辑实现授权](../契约版本唯一性解析纯逻辑实现授权.md) 文首与第 2、3 节 |
| 唯一性代码 | `platform-core` 的 `com.mtx.rdp.core.contract.ContractVersionUniqueness.resolve` |
| 启动核对 | `platform-common` 的 `StartupChecklist.check`、`ApplicationRole.parse` |
| 配置无新候选 | [配置校验盘点](配置校验能力盘点与实现准入评估.md) 第 4、9 节 |
| 质量与转换无候选 | [质量与转换盘点](质量规则与转换规则能力盘点.md) 第 2、3、9 节 |
| 上下文无候选 | [无持久化上下文盘点](无持久化执行上下文能力盘点.md) 第 2、5、10 节 |
| 上下文承载不选 | `06-04-23` 第 6.2 节；`06-04-24` 第 8.1、10 节 |
| 金额二十项 | `06-03-03-11` 第 389 行与第 13 节 `11-BIZ-01` 至 `11-JOINT-01` |
| IR 状态 | 台账 IR-02、IR-03、IR-04、IR-05、IR-07、IR-09、IR-10、IR-11、IR-12、IR-13、IR-14、IR-17、IR-18A、IR-18B 的状态列 |
| 集成项未关闭 | [WP-MA-INT-integration-review.md](../multi-agent-trial/WP-MA-INT-integration-review.md)。INT-01 至 INT-27 的状态列都仍以未关闭开头 |
| 模块骨架 | `AGENTS.md` 第 4、5 节；六个 `platform-*` 模块没有 Java 文件 |
| ADR 不包含这些实现 | ADR-0002 的模块边界；ADR-0003 的范围句；ADR-0004 的范围句；ADR-0005 只接受服务器版本 |

## 11. 本轮证据缺口

- 契约版本唯一性没有独立的实现报告。实现证据是授权、Java 和 `d408e1c`。
- 本轮没有重跑 `ContractVersionUniquenessTest` 或 `StartupChecklistTest`。
- Stage C 没有已接受的范围文档。本文只回答能否启动，不定义 Stage C 的设计。
