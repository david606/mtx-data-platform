# 06-03-03-06 MTX Data Platform V2 金额处理 Pipeline 集成与端到端处理设计

本文把已经冻结的金额提取、观测、匹配、标准化、未识别和冲突处理收成一条可重试的处理链路。它不编写 Java，不执行 DDL，也不发布生产映射。

依据：

- [06-03-01 金额领域模型](06-03-01%20MTX%20Data%20Platform%20V2%20多平台订单金额领域模型设计.md)
- [06-03-02-01 标准金额类型](06-03-02-01%20MTX%20Data%20Platform%20V2%20标准金额类型体系与编码规范.md)
- [06-03-02-02 四平台字段映射](06-03-02-02%20MTX%20Data%20Platform%20V2%20四平台原始金额字段与标准金额类型映射设计.md)
- [06-03-02-03 映射规则配置与版本](06-03-02-03%20MTX%20Data%20Platform%20V2%20金额映射规则配置模型与版本管理设计.md)
- [06-03-03-01 原始金额提取模型](06-03-03-01%20MTX%20Data%20Platform%20V2%20原始金额提取模型设计.md)
- [06-03-03-02 金额观测记录模型](06-03-03-02%20MTX%20Data%20Platform%20V2%20金额观测记录模型设计.md)
- [06-03-03-03 金额映射匹配引擎](06-03-03-03%20MTX%20Data%20Platform%20V2%20金额映射匹配引擎设计.md)
- [06-03-03-04 金额标准化与约束校验](06-03-03-04%20MTX%20Data%20Platform%20V2%20金额标准化与约束校验设计.md)
- [06-03-03-05 未识别金额与冲突金额处理](06-03-03-05%20MTX%20Data%20Platform%20V2%20未识别金额与冲突金额处理设计.md)

架构依据是 [ADR-0002](../../../decisions/0002-v2-module-architecture.md)、[ADR-0003](../../../decisions/0003-unmapped-conflict-observation.md) 和 [调度边界](../../../architecture/scheduling.md)。

| 标记 | 含义 |
| --- | --- |
| 已确认事实 | 上游设计或 ADR 已经写明 |
| 设计决定 | 本文为了把各阶段接成一条链路而作出的选择。它不改写已冻结文档 |
| 待确认 | 上游没有给出执行状态机、物理索引或计算规则正文 |

本文已完成第五轮架构审查整改，待第六轮架构审查。尚未正式冻结。本次整改不表示设计已经冻结。它不代表 Pipeline、任务状态或生产映射已经存在。当前标准类型 38 个是 `DRAFT`，`AMT.FEE.DELIVERY` 是 `RETIRED`，0 个是 `ACTIVE`。已确认生产映射数量是 0。文中的 `MAPPED`、标准化和计算都是规则，不是已经发生的生产处理。测试都没有执行。

## 1. 目标、范围和非目标

目标是：一次金额处理使用同一份原文、同一契约版本和同一个 `snapshotId`，按字段形成不可变观测，再按已冻结门禁决定是否标准化和是否计算。重试返回已有结果。换快照才是另一条观测。

范围内：处理顺序、执行上下文、三条映射分支、字段与任务的失败边界、幂等和重放、模块端口、持久化边界、追踪和端到端测试预期。

范围外：不实现 Java，不编写物理表和 DDL，不重做 Kafka 协议，不实现通用 DAG 引擎，不重新定义金额口径，不发布未经审核的生产映射，不回灌生产数据，不新增 Maven 模块，不修改已冻结的领域对象、类型编码、映射模型、观测模型和状态定义。

## 2. 上游基线

已确认事实：

- 执行侧 `mappingStatus` 只有 `MAPPED`、`UNMAPPED`、`CONFLICT`。Pipeline 的任务状态、步骤状态和字段处理状态不是这三种状态，不能混用，也不能因此新增第四种映射执行状态。
- `MAPPED` 只在唯一映射命中且资格是 `ALLOWED_IF_CONTRACT_DEFINES` 时成立。`ALLOWED_AS_UNIDENTIFIED` 的执行状态是 `UNMAPPED`，并保留该版本的资格和类型编码。`NOT_UNTIL_CONFIRMED`、`DISALLOWED`、`CONFLICT_ONLY` 是任务级快照完整性异常。
- 没有命中时是 `UNMAPPED`。恰好一条冲突决定且没有映射窗口同时命中时是 `CONFLICT`。两条映射、映射加冲突、两条冲突决定都是任务失败。不挑选，不降成 `UNMAPPED`，不改读最新快照。
- 只有 `fieldCode = OK` 进入正常匹配并可以形成观测。`TYPE_MISMATCH`、`PATH_NOT_UNIQUE`、`RAW_TEXT_UNRETAINABLE` 不形成正常观测。对象或数组落在标量金额路径上时，`presence = NON_SCALAR`，`fieldCode = TYPE_MISMATCH`，不求和，不取第一项。
- 整份阻断不生成观测。阻断码包括 `PAYLOAD_MALFORMED`、`PAYLOAD_REF_INVALID`、`CONTRACT_VERSION_UNRESOLVED`、`CONTRACT_IDENTITY_MISMATCH`、`CONTRACT_PATHS_UNDEFINED`、`CONTRACT_PATH_INTEGRITY_FAILED`、`CONTRACT_PATHS_CORRUPT`、`CONTRACT_VERSION_HASH_MISMATCH`。`CONTRACT_PATHS_EMPTY` 是成功的空清单，观测条数为 0。
- 观测身份是 `rawPayloadRef + snapshotId + contractVersionId + fieldPath`。不含租户，不含业务对象，不含解析器版本。同一身份重试返回原观测，不覆盖 `mappingStatus`、`presence` 和 `rawText`。
- `RawAmountValue` 是内存过程对象。持久化的字段事实写在观测上。再次提取只用于核对。
- `UNMAPPED` 和 `CONFLICT` 不进入标准化，也不进入计算。`MAPPED` 不表示标准化成功。标准化成功不是计算授权。
- 冲突观测沿用 06-03-03-05：`conflictDecisionId` 指向唯一决定；`mappingId`、`mappingVersion`、`proposedType`、`mappingEligibility`、`evidenceRef` 为空；`semanticStatus = 无法验证`；`evidenceStatus = NONE`。不抄录候选的语义或证据。
- 冲突决定创建后不可修改。封存后不能追加证据。新证据另建决定。窗口重叠时发布失败。新快照不修改旧观测。
- 历史 `RETIRED` 映射窗口仍按 `receivedAt` 的左闭右开区间命中。类型在该快照目录中已是 `RETIRED` 时，不生成新的标准化结果，也不把类型改回 `ACTIVE`。
- 转换策略只有 `NO_CONVERT`。`normalizationRuleVersion`、`conversionRuleVersion` 和 `parserVersion` 是三个字段。`NO_CONVERT` 不是标准化规则版本号。版本都未分配时，同一观测在“不转换、转换版本为空”这一档至多一个逻辑结果。
- `platform-pipeline` 只依赖 `platform-common` 和 `platform-core`。它调用 core 端口，不依赖 Transform、Connector、Quality、Writer 的实现类，也不依赖 Quartz 或 DolphinScheduler。Worker 负责装配。

已知差异，本文不修改上游，也不声称已经统一：

1. 06-03-02-03 第 8 节第 2 步：只有外部版本号、且该家族在快照里不是恰好一个修订时，匹配结果写 `UNMAPPED`。
2. 06-03-03-01：快照里没有这个契约版本，或“未提供”不能唯一对应一个修订时，提取阻断，码是 `CONTRACT_VERSION_UNRESOLVED`。
3. 06-03-03-03：整份提取阻断时不调用匹配，不补造 `UNMAPPED`。

当前 Pipeline 文档里曾写：契约版本不能在本次固定快照内唯一确定时，不启动字段提取，也不进入匹配，不生成观测。这只是沿用 06-03-03-01 和 06-03-03-03 的摘录，不是已经作出的跨文档决策。

两种规则会得到不同结果。按 06-03-02-03，外部版本不能唯一对应修订时，匹配可以写出 `UNMAPPED`。按 06-03-03-01 与 06-03-03-03，提取已经阻断，匹配不会启动，也不会补造 `UNMAPPED`。本文不删除任何一方，也不把某一方写成已经统一的执行契约。

这是跨文档架构决策依赖。正式决策完成前，该场景不能实现成一套已定契约，也不能用测试验收其中一种结果。它阻断后续实现和该场景的验收。本文不修改那三份冻结文档，也不新写 ADR 来伪装决策已经完成。R1 的顺序仍然有效：先固定快照，再只在该快照内解析，不猜全局最新版本。

## 3. 总体架构

### 3.1 调用顺序

已确认事实：06-03-03-03 的调用顺序是 Worker 固定 `snapshotId`，Pipeline 调用提取端口，整份阻断时不调用匹配，然后调用匹配端口，再由 Writer 写出允许形成的观测。06-03-03-04 在匹配之后做标准化，计算不在标准化这一步。

设计决定：新观测的匹配在第一次写出之前完成。写出的观测已经带有当次 `mappingStatus`。同一身份再次进入时直接返回原观测，不再匹配一遍去改写它。标准化和计算都读取已经形成的观测，不回写观测。

```text
读取原文和来源
        |
        v
校验原文引用
        |
        v
固定 receivedAt，并固定本次 parserVersion
        |
        v
读取并校验本次映射快照，得到 snapshotId
        |
        v
只在该快照内确定 contractVersionId，再校验契约正文和哈希
        |
        +-- 不能唯一确定，或版本不在该快照内：任务阻断
        |     不提取，不匹配，不生成观测
        |
        v
固定完整执行上下文。多个 Worker 并发时走第 4.1 节，胜出后才提取
        |
        +-- 整份 payloadCode 不是 OK：任务阻断，观测条数为 0
        |
        v
对每个 fieldCode = OK 的具体路径，先按观测身份查询
        |
        +-- 已存在：返回原观测和原匹配结果
        |     不重新解析，不重新匹配，不覆盖
        |     UNMAPPED 或 CONFLICT：该字段分支结束
        |     MAPPED：按已经固定的标准化版本和转换版本继续，见第 8 节
        |
        +-- 不存在：只对这条新路径做匹配
              persistObservation 一次写出观测和匹配结果
              CREATED：返回本次持久化结果
              ALREADY_EXISTS：返回胜出方的观测和匹配结果
                不重新匹配，不覆盖 mappingStatus
              UNMAPPED 或 CONFLICT：该字段分支结束
              MAPPED：门禁通过后先持久化版本固定记录
                成功后才写出标准化结果，见第 8 节
        |
        v
汇总字段结果，再按第 11 节确定任务状态
```

三种失败码留在字段结果上，不进入上面的匹配写出。同一报文的其他成功字段继续。

### 3.2 分层职责

| 层 | 负责 | 不负责 |
| --- | --- | --- |
| Pipeline | 固定上下文、按顺序调用端口、区分字段结果和任务阻断、重试时沿用原快照 | 提取规则、匹配算法、单位换算、选择冲突候选、直接写具体表 |
| 提取 | 按契约路径得到 `presence`、`rawText` 和 `fieldCode` | 判断业务含义、选择类型、转换单位、计算营业额 |
| 观测 | 按冻结身份保存不可变事实 | 用后一次匹配覆盖前一次 |
| 匹配 | 只用固定快照和 `receivedAt` 计算新路径的三种执行结果之一，或明确的任务失败。不写存储 | 审核、发布、选择候选、标准化、改写已有观测 |
| 标准化 | 执行 06-03-03-04 的全部门禁 | 放宽门禁，或把失败改成 0 |
| 计算 | 只使用已经标准化且被生效规则允许的输入 | 直接使用 `RawAmountValue`，或套用旧系统营业额公式 |

`platform-quality` 不修改金额事实、匹配结果或语义。`platform-connector` 只提供已经取到的报文和来源，匹配不反向调用它。`platform-writer` 写出已经形成的观测和通过门禁的结果，不推断单位，不把未识别改成 0，不把冲突改成某个类型。`app-admin` 管理契约、映射、审核和发布，不执行这条处理链路，也不依赖 Worker。

## 4. 执行上下文

设计决定：一次任务开始时建立一份执行上下文。后续阶段只读这份上下文里的原文引用、`receivedAt`、`parserVersion`、`snapshotId` 和已经在该快照内确定的契约版本。任何步骤都不得改读当时最新的另一份快照，也不得改用全局最新契约版本。

建议包含这些逻辑字段。它们不是已经存在的 Java 类，也没有全部进入观测：

| 字段 | 用途 |
| --- | --- |
| `processingId` | 一次金额处理的执行标识。算法未设计 |
| `taskId` | 逻辑任务标识。第 4.2 节定义生命周期。生成算法未设计 |
| `taskAttemptId` | 一次尝试。重试可以更换它 |
| `rawPayloadRef` | 原文引用。进入观测身份 |
| 来源平台、接口、消息类型、事件号、消息号 | 属于 `AmountSource`。空消息类型保留空字符串 |
| `contractVersionId`、`contractBodySha256` | 本次固定的内部契约版本和正文哈希 |
| `receivedAt` | 唯一参与窗口判断的业务时间 |
| `snapshotId` | 本次固定的映射快照 |
| `parserVersion` | 首次执行时固定。重试复用。不进入观测身份。不在这里设计版本体系 |
| `normalizationRuleVersion` | 门禁通过后写入可恢复记录，成功后才写出标准化结果。重试读这份记录。未分配时不编造。不在任务开始时确定 |
| `conversionRuleVersion` | 需要转换时与上一列一起固定。不转换时为逻辑空值 NULL。不用 `NO_CONVERT` 填充。不表示数据库类型已选定 |
| `businessObjectType`、`businessObjectId` | 可选。来源未设计时为空。不从金额字段推断。不进入观测身份 |

`tenantId` 不进入上下文的必填项，也不进入观测身份。06-03-01 已说明租户模型未定义。本文不单造租户字段。

初始化顺序是：

1. 读取原始报文和来源信息。
2. 校验原始报文引用。取不回则阻断，不提取。
3. 固定 `receivedAt`。同时固定本次任务的 `parserVersion`。
4. 读取并校验本次映射快照。读不到或完整性校验失败则阻断，不换另一份快照，也不解析契约。
5. 只在这份 `snapshotId` 的契约条目内确定 `contractVersionId`。
6. 校验该版本正文和 `contractBodySha256`。
7. 固定完整执行上下文。
8. 然后才开始金额字段提取。

契约版本的规则：

- 接入已经给出 `contractVersionId` 时，该版本必须出现在本次固定快照的契约条目中，并且哈希校验通过。不允许绕过快照去读全局最新契约版本。
- 接入没有给出 `contractVersionId` 时，只在本次固定快照内解析。06-03-03-01 规定，只有恰好一个匹配修订时才填入。不得用全局最新版本猜测。
- 无法唯一确定时，按 06-03-03-01 阻断，码是 `CONTRACT_VERSION_UNRESOLVED`。不启动字段提取，不进入匹配，不生成观测。
- 重试复用首次固定的 `rawPayloadRef`、`snapshotId`、`contractVersionId`、`contractBodySha256`、`receivedAt` 和 `parserVersion`。可以有新的 `taskAttemptId`。契约配置后来变化，也不在重试时切换版本。

`parserVersion`、`contractVersionId`、`snapshotId`、`normalizationRuleVersion` 和 `conversionRuleVersion` 是五个不同概念。解析器版本不选择契约，快照不代替解析器，标准化规则版本和转换规则版本也不写入观测身份。契约正文哈希沿用已有的 `contractBodySha256`，不另起一个哈希名。解析器的注册、发布、保留、兼容和历史重放仍是 Java 实现前的设计事项。本文不设计那套子系统。

### 4.1 执行上下文的原子固定

设计决定：金额提取开始前，必须先有一份该任务的权威执行上下文。一个 `taskId` 只有一份。多个 Worker 并发启动时，用创建或读取决定唯一上下文，不靠 JVM 本地锁。

```text
ExecutionContextFixResult fixExecutionContext(ExecutionContextFixRequest request)
```

这不是已经存在的 Java 接口。返回只有 `CREATED` 和 `ALREADY_EXISTS`。两种都带上已持久化的完整上下文。

| 返回 | 行为 |
| --- | --- |
| `CREATED` | 本次首次写成权威上下文 |
| `ALREADY_EXISTS` | 读取胜出方的完整上下文。不重新选择最新快照、契约版本或 `parserVersion` |

已有上下文与本次候选不一致时，任务阻断，不覆盖。响应在写入成功后丢失时，重试得到 `ALREADY_EXISTS` 并读取原上下文。

字段分成三类：

| 时机 | 字段 | 重试 |
| --- | --- | --- |
| 逻辑任务被接受时已经确定 | `taskId`、`rawPayloadRef`、来源平台、`sourceSystem`、来源接口、消息类型、事件号、消息号、`receivedAt` | 只读。重复投递不重新确定 |
| 首次执行、提取开始前固定 | `snapshotId`、`contractVersionId`、`contractBodySha256`、`parserVersion` | 只读。`ALREADY_EXISTS` 不得重选 |
| 标准化门禁通过之后才固定 | `normalizationRuleVersion`、`conversionRuleVersion` | 只读已有版本固定记录。见第 8 节 |

`sourceSystem` 属于 06-03-03-01 已有的来源身份，不是新的业务对象。

### 4.2 taskId 的生命周期

设计决定：`taskId` 标识一次逻辑任务。`taskAttemptId` 只标识这次逻辑任务上的一次执行尝试。技术重试更换 `taskAttemptId`，不更换 `taskId`。

| 情况 | 逻辑任务 |
| --- | --- |
| 技术重试 | 沿用原 `taskId` |
| 同一命令被重复投递 | 不创建新的逻辑任务。沿用原 `taskId` |
| 多个 Worker 启动同一任务 | 不能绕过该 `taskId` 的幂等。上下文按第 4.1 节收敛 |
| 调用方显式发起的历史重放 | 新建逻辑任务，并带上调用方给出的原 `taskId` 关联。不按订单号自动查找 |
| 把普通重复投递当成重放 | 不允许 |

非重放命令的逻辑任务键是：`rawPayloadRef`、来源平台、`sourceSystem`、来源接口、消息类型、事件号、消息号、`receivedAt`。相同键返回已有 `taskId`。空字符串保持空字符串，不与缺失合并。标识生成算法留到存储设计。

显式重放必须在命令中声明重放，并给出原 `taskId`。同一条重放命令再次投递仍回到这次新任务，不把投递本身解释成又一次重放。重放任务的观测身份是否变化，只看它固定的快照和契约是否与原任务相同。

`versionFixKey` 保持不变：

```text
versionFixKey =
    observationIdentity
  + taskId
  + 阶段标记“标准化”
```

同一逻辑任务的多次尝试共用这个键。重放产生的新 `taskId` 不改写原任务的版本固定记录。

## 5. 端到端阶段

### 5.1 读取原文

已确认事实：原文通过 `rawPayloadRef` 定位。取不回是 `PAYLOAD_REF_INVALID`，任务阻断，观测条数为 0。处理不修改原文，也不在这一步提取金额。

设计决定：上下文中的来源身份与原文记录不一致时，同样阻断，不换成另一份报文继续。

### 5.2 契约

已确认事实：`contractVersionId` 必须出现在任务已经固定的 `snapshotId` 里。正文哈希不符则阻断。外部版本号不负责在多个修订中选择。重试不切换契约版本。

设计决定：这一步发生在快照校验之后、字段提取之前，顺序见第 4 节。不能先在快照外选定版本，再回头找一份快照来容纳它。

### 5.3 提取

已确认事实：只按契约声明的金额路径提取。七种 `presence` 都保留：`ABSENT`、`PRESENT_NULL`、`EMPTY_STRING`、`NUMERIC_ZERO`、`NUMERIC_TEXT`、`OTHER_TEXT`、`NON_SCALAR`。前四种和后两种里的数字文本、其他文本，在字段码为 `OK` 时都可以进入观测。非标量不是金额。不转换，不求和。

设计决定：提取使用上下文里已经固定的 `parserVersion`。同一任务的重试，包括观测尚未形成的重试，仍用这一版。解析器后来发布了新版本，也不改正在执行或处于 `RETRY_WAITING` 的任务。已经形成观测时，重试不重新解析原文。

### 5.4 观测

已确认事实：观测保存具体 `fieldPath`、`presence`、`rawText`、`declaredUnitText`、来源、`snapshotId`、`contractVersionId` 和 `contractBodySha256`。形成时 `Money` 为空。`extractionFieldCode = OK`。创建不表示已经标准化。

设计决定：先按观测身份查询。已存在时返回原观测和原匹配结果，不重新匹配。不存在时才匹配，并把观测与匹配结果一次持久化。不先插入一条没有匹配结果的观测。后续映射发布不回写这条记录。查询、匹配和持久化的职责见第 10 节，恢复分支见第 8 节。

### 5.5 匹配

已确认事实：只读任务上的 `snapshotId`。窗口是左闭右开。`ACTIVE` 和 `RETIRED` 条目都参加。`receivedAt` 选窗口，`processedAt` 不选窗口。`candidateDisposition` 不读取。草稿不参加。

| 情况 | 结果 |
| --- | --- |
| 没有命中 | `UNMAPPED` |
| 唯一命中且资格是 `ALLOWED_AS_UNIDENTIFIED` | `UNMAPPED`，保留资格和类型编码 |
| 唯一命中且资格是 `ALLOWED_IF_CONTRACT_DEFINES` | `MAPPED` |
| 唯一命中且资格是三种禁止值之一 | 任务失败。不写成 `MAPPED`，不降成 `UNMAPPED` |
| 恰好一条冲突决定，没有映射同时命中 | `CONFLICT` |
| 多条映射、映射加冲突、多条冲突决定 | 任务失败 |

### 5.6 标准化

已确认事实：只有 `mappingStatus = MAPPED` 进入标准化。`UNMAPPED` 和 `CONFLICT` 不进入。资格必须是 `ALLOWED_IF_CONTRACT_DEFINES`。`ALLOWED_AS_UNIDENTIFIED` 的执行状态仍是 `UNMAPPED`。语义必须是 `部分确认`，且没有未排除的第二种解释；含义只限于当前契约写明的字段。其余五种语义出现在 `MAPPED` 上，是任务级快照完整性异常。

证据要分开看，不能把发布原文和运行时门禁合成一句：

- 06-03-02-03 第 13 节的发布门槛写的是：不是只靠 `E4`、`E5`、`E6`。它没有把 `NONE` 写进那一句。
- 06-03-03-04 的运行时表规定：证据只是 `E4`、`E5`、`E6` 之一、仅由它们构成的组合，或 `NONE` 时，不能据此标准化。这时若观测已经是 `MAPPED`，任务失败；不降成字段失败，不改成 `UNMAPPED`，不改读最新快照。含有其他证据等级、同时出现 `E4` 的组合，不因此被这条规则禁止。该文档没有在 `E1-A`、`E1-B`、`E2`、`E3` 之间排优先顺序，也没有另给一套允许清单。本文不补充允许等级，也不建立这四者的优先级。

类型以该观测所引用快照封存的目录为准，必须是 `ACTIVE` 才能形成新的标准化结果。映射条目 `RETIRED` 只表示窗口已有右端点；该快照目录中的类型仍是 `ACTIVE` 时，其余门禁通过后仍可标准化。类型在该目录中已是 `RETIRED` 时，不生成新结果，也不把类型改回 `ACTIVE`。

币种必须是契约已经写明的值。单位必须识别为该契约允许的来源单位。识别失败不补成元、分或人民币。精度沿用 06-03-03-04：不发明目标小数位或业务最小最大值，也不把存储宽度当成业务范围。字面量在技术上无法按原数字容纳时，该字段失败，不舍入。转换策略只有 `NO_CONVERT`。没有封存的 `conversionRuleVersion` 时，不生成换算后的结果。`NO_CONVERT` 不是 `normalizationRuleVersion`。

任一门禁失败，都不生成有效的 `NormalizedAmount`，也不为了失败补一个规则版本号。观测保持原样。不默认值，不截断，不取绝对值，不四舍五入。门禁通过之后，先按第 8 节把规则版本固定记录可靠保存，保存成功后才写出 `NormalizedAmount`。

当前没有已发布映射，所以现在不会写出标准化结果。

### 5.7 计算

已确认事实：标准化成功不是计算授权。没有生效计算规则时不生成 `AmountCalculation`。旧代码里的四平台营业额公式不是 V2 已批准规则。

设计决定：计算只读取已经形成的 `NormalizedAmount` 和一条已经发布的计算规则。规则必须写明输入类型、聚合方式、资金方向、正负号、空值、重复输入和精度。未写明这些内容时不执行。当前没有这样的规则，因此当前不会生成计算结果。

禁止把多个字段直接相加当成通用处理。下面这种写法不是 V2 规则：

```java
amounts.stream()
    .map(Amount::getValue)
    .reduce(BigDecimal.ZERO, BigDecimal::add);
```

### 5.8 汇总

设计决定：汇总同时留下报文结果、每个具体路径的提取码、是否形成观测、`mappingStatus`、是否标准化、是否计算、失败原因、`snapshotId` 和 `contractVersionId`。字段条数、观测条数和标准化条数不是营业额、实付或成交额。

## 6. 三条分支

### 6.1 MAPPED

```text
观测第一次写出时 mappingStatus = MAPPED
        |
        v
第 5.6 节全部门禁，含证据不是 E4、E5、E6 或 NONE 的运行时规则
        |
        +-- 字段级未通过：保留观测，没有标准化结果
        |
        +-- 任务级完整性异常：任务失败，不降成字段失败
        |
        v
NormalizedAmount
        |
        +-- 没有生效计算规则：保留标准化结果，没有计算结果
        |
        v
AmountCalculation
```

`MAPPED` 只表示这次引用了哪一版映射。

### 6.2 UNMAPPED

保留原文路径、契约版本和 `snapshotId`。不生成标准化结果，也不生成计算结果。后续补映射只进入新快照，只影响之后新开始的任务。历史观测不覆盖。`UNMAPPED` 不是技术故障，不因此自动重试。

### 6.3 CONFLICT

观测字段按第 2 节的冻结赋值。决定上的候选和证据行通过 `conflictDecisionId` 读取。不选择候选，不生成标准化结果，也不生成计算结果。`CONFLICT` 不是技术故障，不因此自动重试。封存后的新证据按 06-03-03-05 另建决定。

## 7. 多个金额字段

已确认事实：一份报文可以有多条声明路径。每条路径有自己的观测身份。不能因为它们属于同一张订单就合成一条观测。一份原文是否对应多个业务对象，上游仍待确认。本文不按业务对象把一份原文拆开。

设计决定：这些路径共享同一份执行上下文和同一个 `snapshotId`，处理结果彼此独立。

| 级别 | 例子 | 处理 |
| --- | --- | --- |
| 字段级合法结果 | `MAPPED`、`UNMAPPED`、`CONFLICT` | 按第 6 节继续。不把后两种写成任务失败 |
| 字段级失败 | 三种提取失败码；单位、币种或字面量未通过标准化 | 该字段没有相应结果。其他字段继续。不把失败写进观测 |
| 任务级阻断 | 原文不可读、快照损坏、多重命中、`MAPPED` 上的非法资格、非法语义，或证据仅由 `E4`、`E5`、`E6` 构成，或证据为 `NONE` | 停止尚未处理的字段。不挑选，不换快照。任务状态是 `FAILED`，不降成 `PARTIAL_SUCCESS` 或 `ALL_FIELDS_FAILED`。外部版本不能唯一对应修订时见第 2 节，不在这里写成已经统一的阻断 |

设计决定：一个字段失败，不删除其他字段已经写出的观测和标准化结果，也不覆盖它们。全部要求处理的字段都已结束且全部是字段级失败时，任务是 `ALL_FIELDS_FAILED`，见第 11 节。这仍不是任务级阻断。任务级阻断同样不改写已经写出的领域结果。06-03-03-04 和 06-03-03-05 都写明当时没有撤销事务。本文只规定不删除、不覆盖，不声称数据库已经能做补偿事务。物理检查点仍待确认。

多个字段能否相加，只由第 5.7 节的已发布计算规则决定。

## 8. 幂等、重试和重放

观测身份沿用第 2 节。同一身份返回原观测，不改金额，不改 `mappingStatus`，不重新选择映射，不重新解析。

已有观测的恢复是：

```text
固定执行上下文
    |
    v
提取金额字段
    |
    v
构造观测身份并查询
    |
    +-- 已存在
    |      |
    |      +-- 返回原观测和原匹配结果
    |      +-- 不重新匹配
    |      +-- UNMAPPED 或 CONFLICT：该字段分支结束
    |      +-- MAPPED：按下面已经固定的规则版本继续
    |
    +-- 不存在
           |
           +-- 只对这条新路径执行匹配
           +-- persistObservation
           |     CREATED：返回本次持久化的观测和匹配结果
           |     ALREADY_EXISTS：返回胜出方的观测和匹配结果
           |       不重新匹配，不覆盖
           +-- UNMAPPED 或 CONFLICT：该字段分支结束
           +-- MAPPED：按第 8 节的版本时序继续
```

这是逻辑顺序，不是三个独立服务，也不是三个独立数据库事务。

标准化逻辑身份沿用 06-03-03-04，写出时的并发契约见本节后文，不在这里另写一套身份。

设计身份已经确定。版本的分配和发布机制尚未设计。当前没有可执行的生产标准化规则，也没有已发布的转换规则。

首次进入标准化的顺序是：

1. 已经取得这条 `ObservedAmount`。
2. 读取该观测上原有的 `snapshotId` 和匹配结果。不改读最新快照。
3. 通过 06-03-03-04 的全部标准化门禁。未通过则不固定版本，也不生成结果。
4. 按当时已经存在的规则确定 `normalizationRuleVersion`。当前尚未分配时，确定的内容是“未分配”，不编造版本号。
5. 需要单位转换时，同时确定 `conversionRuleVersion`。不转换时该版本保持空，逻辑上是 NULL。`NO_CONVERT` 不是版本号，不能写入这一列。本文不把 NULL 写成已经选定的数据库类型。
6. 调用 `createOrGet` 把候选版本写入版本固定记录。返回成功之前，内存中的候选还不是已固定版本。
7. 只有返回 `CREATED` 或 `ALREADY_EXISTS`，才允许写出 `NormalizedAmount`。写出时使用返回记录里的版本，不用调用方内存中落选的版本。写入固定记录失败则不写标准化结果。
8. 结果写出之后，版本固定记录和标准化结果必须能用返回版本组成的 `normalizationIdentity` 相互核对。

版本固定记录不是 06-03-03-04 的 `normalizationIdentity`。那个身份含有两个规则版本，而版本尚未确定时还不能用它查找“这一次待固定的处理”。本文也不另造金额业务身份。

待固定的是同一次处理任务上、同一条观测、处在标准化阶段的那一次版本确定。查找键只用已经存在的执行字段：

```text
versionFixKey =
    observationIdentity
  + taskId
  + 阶段标记“标准化”
```

`observationIdentity` 沿用 06-03-03-02。`taskId` 是第 4 节已有的任务标识。`taskAttemptId` 不进入这个键，同一次任务的重试和新尝试仍找到同一条记录。两个规则版本是记录的内容，不是查找键。

因此：同一任务的两个 Worker 竞争这一条记录。另一个任务如果将来使用新的规则版本，使用自己的 `taskId`，不覆盖旧任务已经固定的版本。新结果仍按 06-03-03-04 的逻辑身份另计，不改写旧观测。

`createOrGet` 是版本固定端口，不是 `persistObservation`，也不是标准化结果的写出：

| 返回 | 含义 | 调用方 |
| --- | --- | --- |
| `CREATED` | 本次首次写成固定记录 | 使用已持久化的版本 |
| `ALREADY_EXISTS` | 同一 `versionFixKey` 已有记录 | 使用记录中的原版本。丢弃自己内存中的候选。不覆盖记录 |

记录一旦写成就不可修改。同一键不能留下两套互相矛盾的有效版本。竞争失败方不得继续用自己的内存版本去写 `NormalizedAmount`。不依赖 JVM 本地锁。响应在写入成功后丢失时，重试再次调用 `createOrGet`，得到 `ALREADY_EXISTS` 并复用原记录。

三个操作按这个顺序调用，不要求放进同一个长事务：

1. `persistObservation` 只解决观测和匹配结果。
2. 版本固定只解决上述 `versionFixKey` 的首次确定和并发恢复。
3. `NormalizedAmount` 使用已经返回的固定版本，按 06-03-03-04 的逻辑身份幂等写出。写出端口与观测持久化分开。

标准化结果的逻辑身份保持不变，不加入 `taskId` 或 `taskAttemptId`：

```text
normalizationIdentity =
    observationIdentity
  + normalizationRuleVersion
  + conversionRuleVersion
```

写出返回 `CREATED` 或 `ALREADY_EXISTS`，两种都带已持久化的结果。这不是已经存在的 Java 接口，也不是数据库唯一索引设计。

| 返回 | 行为 |
| --- | --- |
| `CREATED` | 该逻辑身份第一次写成 |
| `ALREADY_EXISTS` | 返回原有结果。不因为另一个 `taskId` 或 `taskAttemptId` 再插一条 |

相同逻辑身份但结果内容不一致，是完整性异常。不静默覆盖。竞争失败方使用返回的胜出结果。写入成功但响应丢失时，重试得到 `ALREADY_EXISTS`。

两个版本同时为空，仍是 06-03-03-04 的那一档，只计一个逻辑结果。不能假设普通复合唯一索引已经处理空值。不同任务可以固定不同版本，从而形成不同的逻辑身份；若两个任务算出的版本相同，则共用这一条标准化结果。物理实现留到存储设计。

故障恢复：

| 故障位置 | 重试行为 |
| --- | --- |
| 门禁通过前 | 不固定规则版本。按原观测和原上下文重新执行门禁 |
| 门禁通过后、固定记录持久化前 | 再次确定候选版本并调用 `createOrGet`。还没有记录时，不能声称已经有固定版本 |
| 两个 Worker 同时固定 | 一个 `CREATED`，另一个 `ALREADY_EXISTS`。失败方改用胜出记录 |
| 固定记录写入失败 | 不写出 `NormalizedAmount` |
| 写入成功但响应丢失 | 重试查询并复用已保存记录，不重新选择 |
| 记录已保存、结果尚未写出 | 读取该记录里的原版本。不改读最新发布的规则 |
| 结果写出后 | 读取并复用已有 `NormalizedAmount` |
| 已固定版本不可用 | 任务阻断并报告。不自动切换 |
| 不转换 | `conversionRuleVersion` 保持逻辑空值 NULL。不伪造版本号 |

重试不得因为后来发布了新规则而替换已经固定的版本。未分配且不转换时，仍沿用 06-03-03-04：同一观测在“不转换、且转换版本为空”这一档至多一个逻辑结果。物理表、DDL、索引、隔离级别和事务实现仍属于后续存储设计。本文不声称这些能力已经实现。

将来规则版本变化时，新任务形成新的版本固定记录和新的逻辑结果。旧记录、旧结果和原观测都不修改。新版本不应用于已经写下固定记录的旧任务重试。门禁失败时重复处理仍然没有结果。

计算逻辑身份在上游没有冻结。本文不规定它的最终字段组合，也不把它写成已经可用的生产身份。在后续详细设计冻结之前，只遵守这几条：

- 重试不切换 `snapshotId`、`parserVersion`、`normalizationRuleVersion` 或 `conversionRuleVersion`。
- 已经形成的计算结果不覆盖。
- 计算规则版本变化时保留历史结果，不把新旧版本并成一条。
- 同一个尚未冻结的逻辑身份，不得写出互相矛盾的两条有效结果。已有结果在重试时复用。
- 没有标准化输入，或没有生效计算规则，就不生成计算结果。

当前没有计算规则，所以上面不会被生产结果占用。哈希、输入顺序和物理唯一约束都还没有。

| 情况 | 处理 |
| --- | --- |
| 原文或存储暂时不可用 | 原上下文重试，含原 `parserVersion` 和已固定的两个规则版本 |
| 契约版本缺失，后来补上 | 新任务。不把旧的阻断尝试改写成观测 |
| 契约哈希不符、快照完整性异常 | 阻断。不换快照，不换契约版本 |
| 已固定的标准化或转换版本不可用 | 阻断并报告。不切换到最新版本 |
| 多重命中 | 修复配置并发布新快照后，由新任务处理。旧观测不改 |
| 标准化约束失败 | 保留观测。不在本次重试中更换规则版本 |
| `UNMAPPED`、`CONFLICT` | 不作为技术异常自动重试。恢复时分支结束 |
| 全部要求处理的字段都是字段级失败 | 不作为技术异常自动重试。任务终态是 `ALL_FIELDS_FAILED` |

重试使用原处理身份、原快照、原契约版本和原 `parserVersion`。重放是另一次任务，调用方明确指定另一份快照，因此得到新的观测身份。新观测可以写入调用方给出的 `replayOfObservationId`。系统不按订单号搜索旧观测。原观测、原快照和原匹配结果都保留。正在执行或 `RETRY_WAITING` 的任务不因为解析器升级或规则发布而改用新版本。

配置命令的幂等表不写入观测、标准化结果、版本固定记录或计算结果。

## 9. 持久化与事务边界

设计决定：领域结果和执行管理信息分开保存。任务状态变化不覆盖观测、标准化结果或计算结果。

| 内容 | 边界 |
| --- | --- |
| 执行上下文 | 独立于单条观测。保存本次固定的快照、契约版本和 `parserVersion`。标准化版本固定记录必须在 `NormalizedAmount` 写出前保存成功 |
| 观测与匹配结果 | 按观测身份一次写出。不允许只留下观测而没有匹配结果，也不允许后再覆盖 `mappingStatus` |
| 版本固定记录 | 键是观测身份、`taskId` 和标准化阶段。版本是记录内容。必须在 `NormalizedAmount` 写出前由 `createOrGet` 返回成功 |
| 标准化结果 | 按 06-03-03-04 的标准化逻辑身份，使用已经返回的固定版本。没有观测依据则不能写。版本为空时不另造版本号 |
| 计算结果 | 身份尚未冻结，见第 8 节。没有标准化输入则不能写 |
| 任务汇总 | 单独更新。不回写领域结果，也不改变已持久化的 `mappingStatus` |

不要求 `persistObservation`、版本固定和 `NormalizedAmount` 写出共用一个长事务。三者顺序见第 8 节。每个具体路径的领域结果是独立的幂等单元。这是集成边界，不是已经实现的事务。

两个 Worker 同时处理同一观测身份时，行为约束是：不能因为两边都先查到不存在，就留下两条有效观测；不能留下有观测但没有匹配结果的有效中间状态；不能让两个 `mappingStatus` 同时成为该身份的有效结果。`persistObservation` 只返回 `CREATED` 或 `ALREADY_EXISTS`，并且两种结果都带上最终持久化的观测和匹配结果。`ALREADY_EXISTS` 的一方使用返回值里的胜出记录，不重新匹配，不用自己内存中的匹配结果覆盖已有 `mappingStatus`。不得用 JVM 本地锁代替跨 Worker 的幂等。物理唯一索引、锁、隔离级别、事务和 DDL 仍归后续存储设计，本文不设计它们，也不声称已经实现。

必须满足：标准化结果能指回观测；计算结果能指回标准化输入和规则版本；`UNMAPPED` 和 `CONFLICT` 没有标准化结果；同一已定义的逻辑身份没有第二条有效结果；重试不更换 `snapshotId`、`contractVersionId`、`parserVersion`、`normalizationRuleVersion` 或 `conversionRuleVersion`。逻辑身份必须能按同样的输入再次算出。

待确认：三类结果的物理唯一索引、哈希字段、隔离级别、跨模块事务和失败后的检查点。这些都还不是现有能力。

## 10. 模块与端口

已确认事实：模块只有 ADR-0002 的十个。`platform-pipeline` 的允许依赖是 `platform-common` 和 `platform-core`。Worker 可以依赖 connector、transform、quality、writer 和 pipeline，并负责装配。Admin 与 Worker 互不依赖。

设计决定：不采用“Pipeline 直接依赖 Connector、Transform、Quality、Writer”的依赖图。那会违反 ADR-0002。Pipeline 只调用 core 里的端口。实现类由 Worker 装配。

| 模块 | 本阶段职责 |
| --- | --- |
| `platform-core` | 上下文、观测、匹配、标准化和计算的端口与模型 |
| `platform-transform` | 实现提取、匹配和标准化门禁。不写存储，不选择候选 |
| `platform-pipeline` | 编排端口调用和任务汇总 |
| `platform-writer` | 持久化已经形成的领域结果 |
| `platform-quality` | 不改金额语义 |
| `platform-connector` | 提供已经取到的报文 |
| `app-worker` | 装配并执行。装载任务上的快照 |
| `app-admin` | 配置、审核和发布。不跑这条链路 |
| `platform-messaging` | 不在本文重做消息协议 |
| `platform-common` | 无业务框架的公共类型 |

下面的方法只说明端口职责，不是仓库里已经存在的 Java 接口，也不表示三个独立服务：

```java
AmountExtractionResult extract(AmountExtractionRequest request);
ExecutionContextFixResult fixExecutionContext(ExecutionContextFixRequest request);
ObservedAmount findByIdentity(ObservationIdentity identity);
AmountMappingMatchResult match(AmountMappingMatchRequest request);
PersistObservationResult persistObservation(ObservedAmount observation);
NormalizationVersionFixResult createOrGet(NormalizationVersionFixRequest request);
AmountNormalizationResult normalize(AmountNormalizationRequest request);
AmountCalculationResult calculate(AmountCalculationRequest request);
AmountProcessingResult execute(AmountProcessingCommand command);
```

`findByIdentity` 是观测仓储端口。它按冻结身份查询。不存在时由 Pipeline 继续后面的匹配，不在这个端口里匹配或标准化。已存在时返回原观测和其中已经保存的匹配结果。

`match` 是纯领域计算，只用于本次查询未命中的新路径。它使用本次固定的 `snapshotId` 和已经提取的字段结果，得到 `MAPPED`、`UNMAPPED`、`CONFLICT` 或任务失败。它不写存储，不切换快照，不修改已有观测。

`persistObservation` 由 Writer 实现，返回 `PersistObservationResult`，不是空返回。结果只有两种：

| 结果 | 含义 | Pipeline 怎么用 |
| --- | --- | --- |
| `CREATED` | 本次成功创建这一身份 | 返回本次持久化的观测和匹配结果 |
| `ALREADY_EXISTS` | 同一身份已经由其他执行方创建 | 返回结果中的胜出观测和匹配结果。不重新匹配，不覆盖 `mappingStatus` |

两种结果都必须带上最终持久化的完整 `ObservedAmount`，其中包含匹配结果。不允许返回一个没有匹配结果的观测。

`createOrGet` 只处理版本固定记录，不创建观测，也不写出 `NormalizedAmount`。请求带观测身份、`taskId`、阶段标记，以及本次候选的两个规则版本。返回 `CREATED` 或 `ALREADY_EXISTS`，都带已持久化的版本。`ALREADY_EXISTS` 时调用方丢弃自己的候选。查找键和并发规则见第 8 节。这个签名同样不是已经存在的 Java 实现。

调用顺序是：

```text
findByIdentity
    |
    +-- 已存在：返回原观测及匹配结果
    |
    +-- 不存在
           |
           +-- match
           +-- persistObservation
                  |
                  +-- CREATED：返回本次持久化结果
                  |
                  +-- ALREADY_EXISTS：使用胜出方的持久化结果
                         不重新匹配，不覆盖 mappingStatus
```

Pipeline 负责这个顺序。Worker 只装配端口实现。依赖方向仍按 ADR-0002：Pipeline 只依赖 `platform-common` 和 `platform-core`。不新增模块。

`execute` 属于 Pipeline 对 Worker 暴露的入口，内部仍只调用 core 端口。

调度只触发 Worker。Quartz API 不进入 Pipeline、Transform 或 Writer。本文不实现 DolphinScheduler，也不新增调度模块。

## 11. 执行状态

设计决定：任务状态、步骤状态、字段处理情况和 `mappingStatus` 分属不同记录。字段记录至少说明提取是否成功、是否形成观测、匹配结果、标准化有没有执行、标准化是否成功、计算有没有执行、计算是否成功，以及失败原因。这些字段记录不替代 `mappingStatus`。

任务状态使用下面这组执行管理取值。它们不是映射状态，也还没有实现：

| 取值 | 判定条件 |
| --- | --- |
| `PENDING` | 等待执行 |
| `RUNNING` | 正在执行。要求处理的字段还没有全部结束，且尚未进入下面的终态或等待重试 |
| `SUCCESS` | 所有要求处理的字段都已结束，没有字段级失败，也没有任务级阻断 |
| `PARTIAL_SUCCESS` | 所有要求处理的字段都已结束，至少一个字段成功，同时至少一个字段级失败，没有任务级阻断 |
| `ALL_FIELDS_FAILED` | 所有要求处理的字段都已结束，全部是字段级失败，没有字段成功，也没有任务级阻断 |
| `FAILED` | 发生任务级阻断。此前已有字段成功，也不降成 `PARTIAL_SUCCESS` 或 `ALL_FIELDS_FAILED` |
| `RETRY_WAITING` | 存在可重试的技术故障，等待用原执行上下文重试 |
| `CANCELLED` | 已取消 |

这四类终态互斥：`SUCCESS`、`PARTIAL_SUCCESS`、`ALL_FIELDS_FAILED`、`FAILED`。`RETRY_WAITING` 不是终态。

```text
PENDING -> RUNNING
RUNNING -> SUCCESS
RUNNING -> PARTIAL_SUCCESS
RUNNING -> ALL_FIELDS_FAILED
RUNNING -> FAILED
RUNNING -> RETRY_WAITING
RUNNING -> CANCELLED
RETRY_WAITING -> RUNNING
```

`UNMAPPED` 和 `CONFLICT` 是合法业务结果，计入字段成功，不是字段级失败。全部要求处理的字段都是这两种结果时，任务是 `SUCCESS`，不是 `ALL_FIELDS_FAILED`。`SUCCESS` 不表示每条路径都已标准化或已计算。

字段级失败只表示该金额字段没有形成预期结果。它是三种提取失败码，以及单位、币种、字面量或其他 06-03-03-04 规定的字段级门禁失败。任务级阻断表示这次任务不能继续按既定流程完成，范围是第 7 节表中的那一类。状态转换不修改已经持久化的 `mappingStatus`。本文不新增映射执行状态。

没有要求处理的金额字段时，单独看 `CONTRACT_PATHS_EMPTY`。06-03-03-01 规定它是成功的空路径清单，`payloadCode = OK`，字段结果为空，不是任务阻断。

设计决定：任务状态用 `SUCCESS`。不新增任务终态，以免和“没有字段级失败、也没有任务级阻断”重叠，也不破坏现有四类终态的互斥。执行结果另记 `pathStatus = CONTRACT_PATHS_EMPTY`，监控不能把它当成已处理金额或全部字段失败。它不是 `ALL_FIELDS_FAILED`。不生成观测，也不生成标准化结果。

字段结果与任务终态的收敛：

设计决定：字段集合在本次权威上下文完成提取后确定，就是该契约版本声明的具体路径。`CONTRACT_PATHS_EMPTY` 的集合为空。字段处理结果的逻辑键是 `taskId + contractVersionId + fieldPath`，不含 `taskAttemptId`，也不是 `mappingStatus`。

字段结果的持久化同样返回 `CREATED` 或 `ALREADY_EXISTS`。重复执行同一字段时返回已有结果。内容不一致则阻断，不覆盖。已经成功的字段在新的 `taskAttemptId` 上直接恢复，不重做。多个 Worker 可以处理不同字段，各自只写自己的字段结果，不能凭自己完成就改任务终态。

任务终态只在字段集合完整、且每个字段结果都已持久化之后，由读取全部字段结果的收敛步骤写出。集合尚未齐时保持 `RUNNING`，任何一个 Worker 都不能提前写成 `SUCCESS`。并发收敛时，先写入的终态保留；另一方读到相同终态则复用，读到不同终态则阻断且不覆盖。`taskAttemptId` 只表示这次尝试，已完成字段属于 `taskId`。

这四层不能合并：字段处理状态、Pipeline 步骤状态、任务状态、`mappingStatus`。`UNMAPPED` 和 `CONFLICT` 是合法业务结果，不是字段失败。字段处理成功也不表示语义已经确认，也不表示可以计算。

重试从 `RETRY_WAITING` 进入新的 `taskAttemptId`，仍用第 4 节固定的上下文，以及第 8 节已经固定的规则版本。已持久化的字段结果按上面的键恢复。尚未完成的字段继续。全部结果齐了以后再判定：没有字段级失败且没有任务级阻断，则为 `SUCCESS`；既有成功字段又有字段级失败，则为 `PARTIAL_SUCCESS`；全部是字段级失败，则为 `ALL_FIELDS_FAILED`；出现任务级阻断，则为 `FAILED`。不保留 `RETRY_WAITING` 当作终态。

待确认：哪些字段级失败允许自动进入 `RETRY_WAITING`，以及取消之后执行记录如何保留。取消和失败都不覆盖已写出的领域结果。字段结果和任务终态的物理索引未设计。

## 12. 追踪

设计决定：日志要能关联 `processingId`、`taskId`、`taskAttemptId`、`rawPayloadRef`、`contractVersionId`、`snapshotId`、观测身份和 `fieldPath`。普通日志不写入原始报文字节。

建议的技术计数包括提取次数、提取失败次数、新建观测次数、三种映射结果次数、标准化成功和失败次数、计算成功次数、任务失败次数、全部字段失败次数、重试次数和耗时。这些不是财务指标。计数名称没有进入现有系统。

审计链路是：原文、契约版本、映射快照、观测、标准化结果、计算结果。后一步必须能指回前一步和所用规则版本。`UNMAPPED` 与 `CONFLICT` 的链路停在观测，并保留快照和契约。

## 13. 端到端测试

这些测试没有执行。涉及 `MAPPED`、标准化或计算的行都假设将来存在相应的已发布规则。当前生产映射数量是 0，当前也不会先通过标准化。

| 编号 | 输入 | 设计预期 | 阻断任务 | 当前执行 |
| --- | --- | --- | --- | --- |
| E2E-001 | 一个 `fieldCode = OK` 的字段，快照无命中 | 一条 `UNMAPPED` 观测。`Money` 为空 | 否 | 未执行 |
| E2E-002 | 假设唯一映射资格为 `ALLOWED_IF_CONTRACT_DEFINES`，第 5.6 节全部门禁通过。证据不是仅由 `E4`、`E5`、`E6` 构成，也不是 `NONE`。含有其他证据等级时，不因同时出现 `E4` 而被这条规则单独禁止 | 保留观测，并形成 `NormalizedAmount`。含义只限于该契约写明的字段。不给 `E1-A`、`E1-B`、`E2`、`E3` 排序 | 否 | 未执行。现在没有这条映射 |
| E2E-003 | E2E-002 之外还有一条生效计算规则，输入满足该规则 | 形成 `AmountCalculation` | 否 | 未执行。现在没有计算规则 |
| E2E-004 | 同一报文多条 `OK` 路径 | 每条路径独立观测。不合并 | 否 | 未执行 |
| E2E-005 | 没有落入窗口的映射或冲突 | `UNMAPPED`。不标准化，不计算 | 否 | 未执行 |
| E2E-006 | 唯一命中 `ALLOWED_AS_UNIDENTIFIED` | `UNMAPPED`，保留资格和类型编码 | 否 | 未执行 |
| E2E-007 | 恰好一条已发布冲突决定 | `CONFLICT`，字段按 06-03-03-05 第 5.2 节。不标准化 | 否 | 未执行。现在没有这样的决定 |
| E2E-008 | 把 `UNMAPPED` 送入标准化 | 不生成标准化结果 | 否 | 未执行 |
| E2E-009 | 把 `CONFLICT` 送入标准化 | 不生成标准化结果 | 否 | 未执行 |
| E2E-010 | 声明路径缺失 | `presence = ABSENT`，`fieldCode = OK`，可以形成观测 | 否 | 未执行 |
| E2E-011 | 声明路径为 null | `PRESENT_NULL`，`fieldCode = OK` | 否 | 未执行 |
| E2E-012 | 数字 0 | `NUMERIC_ZERO`，`fieldCode = OK`。不是缺失 | 否 | 未执行 |
| E2E-013 | 标量路径上是对象 | `NON_SCALAR`，`TYPE_MISMATCH`。无观测，不求和 | 否 | 未执行 |
| E2E-014 | 标量路径上是数组 | 同 E2E-013。不取第一项，不求和 | 否 | 未执行 |
| E2E-015 | 其他文本 | `OTHER_TEXT`。字段码按 06-03-03-01。不把文本改成 0 | 否 | 未执行 |
| E2E-016 | 同一观测身份再执行 | 返回原观测。不重新解析，不重新匹配 | 否 | 未执行。物理索引未实现 |
| E2E-017 | 同一任务重试 | 仍用原 `snapshotId`、`contractVersionId` 和 `parserVersion` | 否 | 未执行 |
| E2E-018 | 同一原文改用新快照 | 新观测。可带调用方给出的 `replayOfObservationId`。旧结果不变 | 否 | 未执行 |
| E2E-019 | 标准化规则版本或转换规则版本变化 | 新的逻辑结果。旧结果保留 | 否 | 未执行。两个版本号都未分配 |
| E2E-020 | 计算规则版本变化 | 新的计算结果。旧结果保留 | 否 | 未执行。现在没有计算规则 |
| E2E-021 | 观测写出后执行中断再重试 | 返回原观测，不覆盖 | 否 | 未执行 |
| E2E-022 | 标准化写出后执行中断再重试 | 同一逻辑身份不形成第二条有效结果 | 否 | 未执行 |
| E2E-023 | 一个字段提取失败或标准化约束失败 | 其他已写出的字段结果保留 | 否 | 未执行 |
| E2E-024 | 同一字段命中多条映射、映射加冲突，或两条冲突决定 | 任务失败。不任选 | 是 | 未执行 |
| E2E-025 | 快照缺失或哈希不符 | 任务阻断。不换快照 | 是 | 未执行 |
| E2E-026 | 外部版本在本次固定快照内对应零个或多个修订 | 第 2 节的两套冻结规则结果不同。决策完成前没有统一契约。不验收成 `UNMAPPED`，也不验收成已经选定的提取阻断。不猜最新版本 | 是。阻断的是实现和验收，不是宣称某条上游规则已经胜出 | 未执行 |
| E2E-027 | `MAPPED` 上出现非法资格、非 `部分确认` 的语义，或证据仅由 `E4`、`E5`、`E6` 构成，或证据为 `NONE` | 任务失败。不降成字段失败，不改 `UNMAPPED`。不把“含有 `E4`”扩大成禁止。不给 `E1-A`、`E1-B`、`E2`、`E3` 排序 | 是 | 未执行 |
| E2E-028 | 标准化已通过，但没有生效计算规则 | 保留标准化结果。没有计算结果 | 否 | 未执行。当前也不会先通过标准化 |
| E2E-029 | 接入给出的 `contractVersionId` 不在本次固定快照中 | 阻断。不读取全局最新契约，不提取 | 是 | 未执行 |
| E2E-030 | 重试时解析器已有更新的版本，观测已经形成 | 仍用首次固定的 `parserVersion`。返回原观测，不重新解析 | 否 | 未执行 |
| E2E-031 | 重试时观测尚未形成，解析器已有更新的版本 | 仍用首次固定的 `parserVersion`。不静默切换 | 否 | 未执行 |
| E2E-032 | 已有 `UNMAPPED` 或 `CONFLICT` 观测后重试 | 返回原观测。该字段分支结束。不标准化 | 否 | 未执行 |
| E2E-033 | 已有 `MAPPED` 观测，同一标准化逻辑身份已有结果 | 返回原标准化结果。不更换规则版本 | 否 | 未执行。现在没有这条映射 |
| E2E-034 | 已有 `MAPPED` 观测，同一标准化逻辑身份还没有结果 | 用任务上原固定的两个规则版本继续。版本未分配时仍是“不转换、转换版本为空”那一档 | 否 | 未执行 |
| E2E-035 | 要求处理的字段全部是合法 `UNMAPPED` | `SUCCESS`。不是 `ALL_FIELDS_FAILED` | 否 | 未执行 |
| E2E-036 | 一个字段 `UNMAPPED`，另一个字段 `TYPE_MISMATCH` | `PARTIAL_SUCCESS`。不进入 `FAILED` | 否 | 未执行 |
| E2E-037 | 已有字段成功之后出现任务级阻断 | `FAILED`。不降成 `PARTIAL_SUCCESS` 或 `ALL_FIELDS_FAILED`。已写出的 `mappingStatus` 不改 | 是 | 未执行 |
| E2E-038 | 每个要求处理的字段都是字段级失败，没有任务级阻断 | `ALL_FIELDS_FAILED` | 否 | 未执行 |
| E2E-039 | 要求处理的字段全部是合法 `CONFLICT` | `SUCCESS`。不是 `ALL_FIELDS_FAILED` | 否 | 未执行。现在没有这样的决定 |
| E2E-040 | `CONTRACT_PATHS_EMPTY`，`payloadCode = OK` | 任务状态 `SUCCESS`。执行结果另记空路径清单。不是 `ALL_FIELDS_FAILED`。观测和标准化结果都是 0 | 否 | 未执行 |
| E2E-041 | 同一标准化逻辑身份重试 | 复用原 `normalizationRuleVersion` 和 `conversionRuleVersion`。已有结果直接返回 | 否 | 未执行 |
| E2E-042 | 已固定的规则版本不可用 | 阻断并报告。不换成最新版本 | 是 | 未执行。当前也没有已分配版本 |
| E2E-043 | 标准化或转换规则版本变化 | 新逻辑结果。旧结果和原观测保留。旧任务重试不改用新版本 | 否 | 未执行。版本号尚未分配 |
| E2E-044 | 两个 Worker 同时处理同一观测身份 | 一个 `CREATED`，另一个 `ALREADY_EXISTS`。最终只有一条有效观测和唯一的匹配结果 | 否 | 未执行。物理索引未实现 |
| E2E-045 | `persistObservation` 返回 `ALREADY_EXISTS` | 返回竞争胜出方的完整观测和匹配结果。不重新匹配，不覆盖 `mappingStatus` | 否 | 未执行 |
| E2E-046 | 已有观测后重试 | 返回原观测和原匹配结果。不重新匹配 | 否 | 未执行 |
| E2E-047 | 不转换，`conversionRuleVersion` 为空 | 逻辑空值 NULL。不把 `NO_CONVERT` 或任何虚构版本写入该列。未分配的标准化版本保持未分配 | 否 | 未执行 |
| E2E-048 | 版本固定记录已保存，标准化结果尚未写出时故障 | 重试读取原固定版本。不重新选择最新规则 | 否 | 未执行 |
| E2E-049 | 旧任务已有版本固定记录后，规则又发布了新版本 | 该任务重试仍用原固定版本。新版本不替换它 | 否 | 未执行。当前没有已分配版本 |
| E2E-050 | 版本固定记录持久化失败 | 不写出 `NormalizedAmount`。不能把内存中的版本当成已固定 | 否 | 未执行 |
| E2E-051 | 并发持久化结束 | 只有一个最终有效观测。不存在没有匹配结果的有效观测，也不存在两个有效 `mappingStatus` | 否 | 未执行 |
| E2E-052 | 同一观测、同一 `taskId` 的两个 Worker 同时固定规则版本 | 一个 `CREATED`，另一个 `ALREADY_EXISTS`。只有一套有效版本 | 否 | 未执行 |
| E2E-053 | 版本固定返回 `ALREADY_EXISTS` | 失败方使用胜出记录中的版本。不用自己的内存版本写结果，也不覆盖记录 | 否 | 未执行 |
| E2E-054 | 版本固定写入成功但响应丢失 | 重试 `createOrGet` 得到 `ALREADY_EXISTS`，复用已保存版本 | 否 | 未执行 |
| E2E-055 | 已有版本固定记录后规则发布新版本 | 旧任务仍用原版本。新版本不替换该记录 | 否 | 未执行。当前没有已分配版本 |
| E2E-056 | 版本固定记录尚未可靠保存 | 不写出 `NormalizedAmount` | 否 | 未执行 |
| E2E-057 | 已固定版本不可用 | 阻断。不自动切换到其他版本 | 是 | 未执行 |
| E2E-058 | 不转换时并发固定版本 | `conversionRuleVersion` 保持逻辑空值 NULL。不伪造版本号 | 否 | 未执行 |
| E2E-059 | 两个 Worker 并发固定同一任务的执行上下文 | 一个 `CREATED`，另一个 `ALREADY_EXISTS` 并读取胜出上下文。不重选快照、契约或解析器 | 否 | 未执行 |
| E2E-060 | 执行上下文写入成功但响应丢失 | 重试得到 `ALREADY_EXISTS`，读取原上下文 | 否 | 未执行 |
| E2E-061 | 同一非重放命令再次投递 | 沿用原 `taskId`。不新建逻辑任务 | 否 | 未执行 |
| E2E-062 | 技术重试 | 沿用原 `taskId`，更换 `taskAttemptId` | 否 | 未执行 |
| E2E-063 | 调用方显式历史重放 | 新 `taskId`，并保留调用方给出的原任务关联。重复投递本身不是重放 | 否 | 未执行 |
| E2E-064 | 两个任务为同一观测固定不同规则版本 | 各自的 `versionFixKey` 保留自己的版本。形成两个标准化逻辑身份 | 否 | 未执行。版本号尚未分配 |
| E2E-065 | 两个任务算出相同的 `normalizationIdentity` | 标准化结果一个 `CREATED`，另一个 `ALREADY_EXISTS`。不因 `taskId` 不同再插一条 | 否 | 未执行 |
| E2E-066 | 相同标准化逻辑身份，结果内容不一致 | 完整性异常。不覆盖已有结果 | 否 | 未执行 |
| E2E-067 | 多个 Worker 同时处理不同金额字段 | 各字段结果按 `taskId + contractVersionId + fieldPath` 幂等保存。未齐时任务保持 `RUNNING` | 否 | 未执行 |
| E2E-068 | 部分字段已完成后任务重试 | 新 `taskAttemptId` 恢复已有字段结果，不重做。终态等全部字段结果持久化后再收敛 | 否 | 未执行 |
| E2E-069 | 多个 Worker 同时写任务终态 | 先写入的终态保留。相同终态复用。不同终态阻断且不覆盖。不得提前写成 `SUCCESS` | 否 | 未执行 |

进入实现之后还需要单元测试、端口装配测试、幂等并发测试和故障重试测试。那些测试现在也不存在。

## 14. 风险与待确认

1. `parserVersion` 的注册、发布、保留、兼容和历史重放都未设计。一次任务内部已经规定首次固定、重试复用。不能声称换版本后字面量不变。
2. 观测、标准化和计算的物理唯一索引、锁和事务都未设计。跨 Worker 的行为约束已经写明，但还没有实现。
3. `normalizationRuleVersion` 的分配和发布尚未设计。身份规则已经确定。版本固定在版本确定前使用观测身份、`taskId` 和标准化阶段，不使用 `normalizationIdentity` 当查找键。`taskId` 的生成算法仍未设计；同一任务的并发和重试必须使用同一个 `taskId`。`NO_CONVERT` 不是版本号。当前没有可执行的生产规则。
4. 计算规则正文和计算逻辑身份的最终字段组合都未冻结。旧系统公式不能直接使用。
5. 哪些字段级失败可以自动进入 `RETRY_WAITING` 未定。`CONTRACT_PATHS_EMPTY` 的任务状态是 `SUCCESS`，并用空路径清单与有字段的成功区分。已写出的领域结果不因后续失败而覆盖。
6. 业务对象标识的来源未定。租户模型未定。逻辑任务键的标识生成算法未设计。
7. 外部版本不能唯一对应修订时，06-03-02-03 与 06-03-03-01、06-03-03-03 仍不一致。这是未完成的跨文档决策。当前没有统一契约，该场景不能进入实现验收。
8. 失败记录是否单独持久化，仍待存储设计。失败不写入 `ObservedAmount`。

## 15. 架构决策

| 编号 | 结论 |
| --- | --- |
| D01 | 先固定 `snapshotId`，再只在该快照内确定 `contractVersionId` |
| D02 | 重试不更换快照、契约版本、`parserVersion` 和已固定的两个规则版本 |
| D03 | 每条具体路径独立形成观测 |
| D04 | `UNMAPPED` 不标准化 |
| D05 | `CONFLICT` 不标准化，也不选择候选 |
| D06 | `MAPPED` 先过标准化门禁，再看计算授权 |
| D07 | 一个字段失败不删除其他字段已写出的领域结果 |
| D08 | 历史观测不覆盖 |
| D09 | 新快照不修改历史观测 |
| D10 | 不自动选择冲突候选 |
| D11 | 没有已发布计算规则时不聚合金额 |
| D12 | 不新增 Maven 模块 |
| D13 | Pipeline 只依赖 common 和 core，通过端口编排 |
| D14 | 本阶段不实现 Java 和 DDL |

这些编号只存在于本文，不另建 ADR。它们不表示生产映射或计算口径已经批准。

## 16. 审查与冻结条件

本文尚未冻结。正式冻结前要确认：

1. 与 06-03-03-01 至 06-03-03-05 一致，并且不改那些文档。
2. 不新增 `mappingStatus`。
3. `UNMAPPED` 和 `CONFLICT` 不进入标准化。
4. 未经标准化和计算授权的金额不进入计算。
5. `snapshotId` 的固定和重试已经写明。
6. 字段级失败和任务级阻断已经分开。
7. 模块依赖符合 ADR-0002。
8. 物理索引、解析器版本体系和计算规则仍标为待确认。外部版本不能唯一对应修订的统一规则也待跨文档决策。
9. 测试只写设计预期，不写成已经执行。

即使本文以后冻结，也只冻结这条集成设计。它不表示代码、数据库结构、生产映射或业务计算口径已经完成。

## 17. 第一轮修订

第一轮架构审查整改，待第二轮架构审查。本轮调整了快照与契约的固定顺序，补上 `NONE` 的运行时证据门禁，规定重试复用 `parserVersion`，分开 `SUCCESS` 与 `PARTIAL_SUCCESS`，并写明已有观测的恢复分支。没有正式冻结。没有新增 `mappingStatus`。E2E 测试都没有执行。

## 18. 第二轮修订

第二轮架构审查整改完成，待第三轮架构审查。本轮增加 `ALL_FIELDS_FAILED`，写明标准化与转换版本在写出前固定并供重试复用，并把查询、匹配和持久化分成三项职责。没有正式冻结。没有新增 `mappingStatus`。没有设计 DDL。E2E 测试都没有执行。

## 19. 第三轮修订

第三轮架构审查整改完成，待第四轮架构审查。本轮规定版本固定记录必须先于标准化结果保存，并让 `persistObservation` 用 `CREATED` 或 `ALREADY_EXISTS` 返回最终观测和匹配结果。没有正式冻结。没有新增 `mappingStatus`。没有设计 DDL。E2E 测试都没有执行。

## 20. 第四轮修订

第四轮架构审查整改完成，待第五轮架构审查。本轮用观测身份、`taskId` 和标准化阶段识别尚未确定版本的同一次固定，并用 `createOrGet` 保证并发时只保留胜出版本。没有改写 06-03-03-04 的标准化逻辑身份。没有正式冻结。没有设计 DDL。E2E 测试都没有执行。

## 21. 第五轮修订

第五轮架构审查整改完成，待第六轮架构审查。本轮补上执行上下文的原子固定、`taskId` 与重放的边界、标准化结果的跨任务返回契约、字段结果与任务终态的收敛，以及空路径清单的 `SUCCESS`。外部版本不能唯一对应修订的冲突仍未决策。没有正式冻结。没有设计 DDL。E2E 测试都没有执行。

本文没有修改 06-03-01、06-03-02-01、06-03-02-02、06-03-02-03、06-03-03-01、06-03-03-02、06-03-03-03、06-03-03-04 和 06-03-03-05。没有 Java 实现，没有 DDL，没有新表，没有新模块。
