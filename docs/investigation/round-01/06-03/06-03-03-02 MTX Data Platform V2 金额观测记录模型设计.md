# 06-03-03-02 MTX Data Platform V2 金额观测记录模型设计

本文设计原始金额提取结果怎样形成可追溯的金额观测，以及观测怎样引用映射快照、契约版本和后续计算。它不编写 Java，不执行 DDL，也不批准任何生产映射。

依据是已经完成的五份设计：

- [06-03-01 金额领域模型](06-03-01%20MTX%20Data%20Platform%20V2%20多平台订单金额领域模型设计.md)
- [06-03-02-01 标准金额类型](06-03-02-01%20MTX%20Data%20Platform%20V2%20标准金额类型体系与编码规范.md)
- [06-03-02-02 四平台字段映射](06-03-02-02%20MTX%20Data%20Platform%20V2%20四平台原始金额字段与标准金额类型映射设计.md)
- [06-03-02-03 映射规则配置与版本](06-03-02-03%20MTX%20Data%20Platform%20V2%20金额映射规则配置模型与版本管理设计.md)
- [06-03-03-01 原始金额提取模型](06-03-03-01%20MTX%20Data%20Platform%20V2%20原始金额提取模型设计.md)

架构依据是 [ADR-0002](../../../decisions/0002-v2-module-architecture.md)。

文中的标记含义：

| 标记 | 含义 |
| --- | --- |
| 已确认事实 | 上述文档或 ADR 已经写明 |
| 设计决定 | 本文为了把提取结果收成观测而作出的选择。它不改写已有文档 |
| 待确认 | 还没有证据，或存储产品尚未设计 |

本文是设计初稿，待架构一致性审查。它不代表数据库已建表、接口已实现，或生产金额映射已批准。

## 1. 设计目标与范围

目标是：一次已经完成或已经阻断的提取，在固定的原文、契约版本和映射快照下，形成不可覆盖的 `ObservedAmount`，或明确地不形成观测。观测只说明“这个路径在这次处理中被记下了，以及当次匹配得出了哪种执行结果”。它不说明营业额、实付、优惠或退款已经成立。

范围内：三类金额对象的边界、观测字段、执行状态与提取结果的关系、未识别和冲突的保存、逻辑幂等身份、换快照重放、Pipeline 中的位置。

范围外：不重新定义 39 个标准金额类型，不把 `DRAFT` 类型或候选映射改成生效，不设计映射匹配算法的内部步骤，不设计标准化和单位换算，不设计未识别金额的专门处理流程，不设计计算规则，不新增 Maven 模块，不编写物理表。

## 2. 设计原则

### 2.1 原始值、观测值、计算值分开

已确认事实：`RawPayloadRecord`、`RawAmountValue` 和 `ObservedAmount` 是三份记录。`AmountCalculation` 是另一次执行记录。四者不能合成一个字段。

| 对象 | 职责 | 对已有事实 |
| --- | --- | --- |
| `RawAmountValue` | 一个具体路径的原始形态和字面量 | 不改写原文，不解释业务类型 |
| `ObservedAmount` | 这次处理对这个路径作出的观测和匹配结果 | 不覆盖更早的观测 |
| `AmountCalculation` | 已批准规则的一次计算结果 | 重算生成新记录，不改原文，也不改已有观测 |

### 2.2 观测不是业务事实

一条观测表示：某份已保存原文中的一个具体路径，在某个契约版本和某份映射快照下，被提取并完成了一次匹配。

它不自动表示金额含义已经确认、币种或单位已经确认、承担主体已经确认、该金额已经具备营业额或实付等指标资格，也不表示可以参与汇总或计算。

### 2.3 不用推断补成金额

已确认事实：`ABSENT`、`PRESENT_NULL`、`EMPTY_STRING` 都不补 0。`NUMERIC_ZERO` 是真实的 0，不能当成缺失。空的 `Money` 不是数字 0。非标量路径不拆开加总。

因此，字段缺失、JSON null、空字符串、非数字文本、类型不符、单位未确认、语义未确认、多个候选冲突，都不写成 0，也不互相顶替。

### 2.4 历史不覆盖

观测保留当次的原文引用、来源、契约版本、契约正文哈希、映射快照和匹配结果。后来的映射规则变化不修改这条观测。

同一逻辑身份再次处理时，返回已有观测。换一份映射快照再处理时，新建观测，并可以指向原先那条观测。这个指针只说明处理上的先后，不说明两条金额相等。

## 3. 对象关系

### 3.1 逻辑关系

```text
RawPayloadRecord
      |
      | rawPayloadRef
      v
RawAmountValue
      |
      | 字段结果 + AmountSource
      v
ObservedAmount
      |
      | snapshotId
      | mappingId + mappingVersion
      | 或 conflictDecisionId
      v
AmountMapping 或冲突决定
      |
      | 只有已确认观测才可能进入
      v
AmountCalculation
```

已确认事实：

1. `RawPayloadRecord` 保存不可变原文。
2. `RawAmountValue` 只记录路径、七种形态和字面量。
3. `ObservedAmount` 引用原始取值、来源和当次匹配结果。
4. 映射定义侧使用 `mappingId` 与 `mappingVersion`。执行侧使用 `MAPPED`、`UNMAPPED`、`CONFLICT`。
5. `AmountCalculation` 只消费已经允许计算的观测。当前没有这种观测。

以上是逻辑关系。本文不规定五张物理表，也不把逻辑对象写成已经建表。

### 3.2 三类对象的边界

| 对比项 | `RawAmountValue` | `ObservedAmount` | `AmountCalculation` |
| --- | --- | --- | --- |
| 具体路径 | 有。它是内存中的字段结果 | 观测形成时记下这个路径。不另建原始值表 | 通过来源观测追溯 |
| 原始字面量 | 有。缺失和 null 没有字面量 | 只保存形成观测时交给它的那一份。截断后的字面量不能冒充原文 | 不代替原始字面量 |
| `presence` | 有。七种形态的唯一事实 | 随观测记下形成时的形态 | 不直接参与计算 |
| 来源 | 不解释来源 | 关联一份 `AmountSource` | 通过观测追溯 |
| 契约版本 | 不选择契约 | 固定 `contractVersionId` 和 `contractBodySha256` | 通过观测追溯 |
| 映射快照 | 不负责 | 固定 `snapshotId` | 通过观测追溯 |
| 标准金额类型 | 无 | `proposedType` 只在当次匹配写出时才有 | 计算输入必须已经明确 |
| 金额数值 | 不解析成 `Money` | 本阶段不填写 `Money` | 有结果时必须是明确数值，或明确的空值原因 |
| 单位转换 | 不执行 | 不执行 | 只按已批准规则执行 |
| 聚合 | 不允许 | 不允许 | 只按已批准规则执行 |

设计决定：观测形成时 `Money` 保持为空。06-03-01 允许观测上存在可空的 `Money`，空值不是 0。是否在后续标准化阶段填写，由尚未编写的标准化设计决定。本文不提前填写。

## 4. ObservedAmount

### 4.1 定义

`ObservedAmount` 是某个具体路径在一次确定的原文、来源、契约版本和映射快照下形成的不可变观测。

它保存当次匹配结果和对原始取值、来源的引用。它不复制一套可以单独改写的报文字段。

### 4.2 逻辑字段

已确认事实来自 06-03-01，后两份设计补充了内部契约版本和快照。下表是逻辑字段，不是物理列类型。

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `observationId` | 是 | 观测标识。06-03-01 写明生成算法未设计 |
| `businessObjectType` | 否 | 06-03-01 放入观测，用来区分订单、支付流水、退款单、退款明细。谁在创建观测前提供它，尚未设计。缺失时不阻断观测 |
| `businessObjectId` | 否 | 与上一列一起指向具体业务对象。标识来源未设计，因此不是创建观测的强制条件。不从金额字段推断 |
| `fieldPath` | 是 | 形成观测时的具体路径。逻辑上属于 `RawAmountValue`，持久化时写在观测记录中 |
| `presence` | 是 | 形成观测时的七种形态之一 |
| `rawText` | 条件 | 沿用 06-03-03-01。`ABSENT` 和 `PRESENT_NULL` 没有字面量 |
| `declaredUnitText` | 否 | 契约材料中的单位原文。没有则空着 |
| `money` | 否 | 本阶段保持为空 |
| `source` | 是 | 一份 `AmountSource`。平台、接口、消息类型、外部契约版本、原文引用、接入方式、事件号、消息号和 `receivedAt` 都在这里 |
| `snapshotId` | 是 | 任务上已经固定的映射快照 |
| `contractVersionId` | 是 | 该快照契约条目中的内部契约版本 |
| `contractBodySha256` | 是 | 该版本行上的正文哈希。校验通过后才写入 |
| `mappingId` | 否 | 命中的映射定义。未命中或冲突未选定类型时为空 |
| `mappingVersion` | 否 | 上一列的正文版本。不是契约版本 |
| `conflictDecisionId` | 否 | 命中的冲突决定。与单一 `mappingVersion` 不同时成立 |
| `proposedType` | 否 | 当次写出的类型编码。语义是否成立看 `semanticStatus` |
| `mappingStatus` | 是 | 执行结果：`MAPPED`、`UNMAPPED`、`CONFLICT` |
| `semanticStatus` | 是 | 沿用六种语义状态 |
| `evidenceStatus` | 是 | 沿用八种证据状态，含 `NONE` |
| `mappingEligibility` | 否 | 命中映射版本时，抄录该版本的五种资格之一。未命中时为空 |
| `evidenceRef` | 否 | 当次依据的证据引用。没有依据时为空 |
| `extractionFieldCode` | 是 | 能形成观测时，这个值是 `OK`。三种失败码留在字段结果上，不借观测改名 |
| `replayOfObservationId` | 否 | 换快照新建观测时，可以指向先前那条观测 |
| `observedAt` | 是 | 这条观测形成的时间 |

设计决定：

1. 不增加 `tenantId`。06-03-01 已说明租户模型未定义，观测不单造租户字段。
2. `sourceEventId`、`sourceMessageId`、`receivedAt` 留在 `AmountSource`。`fieldPath`、`presence`、`rawText`、`declaredUnitText` 在观测形成时写入观测记录，因为没有单独的原始值表可以回指。详见第 4.3 节。
3. 不增加 `extractionRequestId`。06-03-03-01 的提取请求身份就是 `rawPayloadRef + snapshotId + contractVersionId`，提取结果不单独落库。
4. `sourceMessageType` 可以是空字符串。空字符串和 NULL 不合并，沿用契约身份里的空消息类型。
5. `contractVersionId` 是内部版本。`AmountSource.sourceContractVersion` 仍是外部版本号，没有时写“未提供”。二者不互相代替。
6. `replayOfObservationId` 不进入幂等身份。它不表示两条观测的金额相等。

### 4.3 RawAmountValue 的生命周期

三个层次分开：

| 层次 | `RawAmountValue` | 说明 |
| --- | --- | --- |
| 逻辑领域对象 | 是 | 一个具体路径的形态和字面量。06-03-01 与 06-03-03-01 都使用它 |
| 持久化记录 | 不是独立记录 | 06-03-03-01 规定提取是纯函数，提取结果不单独落库 |
| 数据库物理表 | 不建 | 本文不新增表 |

因此 `RawAmountValue` 是一次提取在内存中的过程对象。任务结束之后，它不靠自己的存储继续存在。

已经形成的 `ObservedAmount` 要还原当时的路径、形态和字面量时，读取观测记录上形成时写下的 `fieldPath`、`presence`、`rawText`、`declaredUnitText`。这些值是交给观测的那一份字段结果，不是事后再解析出来替换历史。

`RawPayloadRecord` 仍通过 `rawPayloadRef` 保存原文。`rawPayloadRef` 加上具体 `fieldPath`，可以再次按同一契约版本定位节点。再次提取用来核对。它不覆盖已经写下的观测。原文保存、字段提取、观测生成、历史重放的顺序是：先有不可变原文，再按任务固定的快照和契约在内存中提取，只有 06-03-03-01 允许生成观测的字段结果才写成观测，重放同一身份时返回该观测。

待确认：解析器版本没有出现在 06-03-01 或 06-03-03-01 中。解析器以后变化时，再次提取是否仍得到当初的形态和字面量，上游没有规则。本文不增加解析器版本字段。历史观测以形成时写下的字段结果为准。

### 4.4 业务对象标识

06-03-01 把 `businessObjectType` 和 `businessObjectId` 放进观测，并写明标识来源未设计。订单、支付、退款应当能区分，但没有任何一份冻结文档规定由接入、提取还是匹配来提供这对标识，也没有规定每个消息类型用报文里的哪个字段。

因此：

- 它们不是观测创建的强制条件。暂时无法确定时，两列为空，观测仍可按原文、快照、契约和路径形成。
- 不从金额字段、金额数值或字段名推断业务对象标识。
- 不同契约的业务对象种类可以不同。京东创建消息和订单查询摘录是两份契约。支付观测和退款观测在 06-03-01 中关联不同业务对象。具体标识字段仍待确认。
- 一份报文是否同时关联多个业务对象，06-03-03-01 已列为待确认。本文不把一份原文拆成多条仅靠业务对象区分的观测。观测身份也不包含这两列。

## 5. 状态

### 5.1 沿用已有状态

已确认事实：定义侧生命周期是 `DRAFT`、`ACTIVE`、`RETIRED`。观测上的 `mappingStatus` 只使用执行侧 `MAPPED`、`UNMAPPED`、`CONFLICT`。配置生命周期不写入观测执行状态。

语义状态沿用 `部分确认`、`待平台确认`、`待业务确认`、`待财务确认`、`存在冲突`、`无法验证`。证据状态沿用 `E1-A`、`E1-B`、`E2`、`E3`、`E4`、`E5`、`E6`、`NONE`。映射资格沿用 `ALLOWED_IF_CONTRACT_DEFINES`、`NOT_UNTIL_CONFIRMED`、`DISALLOWED`、`ALLOWED_AS_UNIDENTIFIED`、`CONFLICT_ONLY`。

`candidateDisposition` 只存在于映射设计评估，不写入观测。

### 5.2 不另造观测形态码

设计决定：不增加 `OBSERVED`、`SOURCE_VALUE_ABSENT`、`SOURCE_VALUE_NULL`、`SOURCE_VALUE_EMPTY`、`SOURCE_VALUE_NON_NUMERIC`、`SOURCE_VALUE_NON_SCALAR` 或 `SOURCE_VALUE_UNRETAINABLE`。这些名字会把七种 `presence` 和已有字段码再命名一遍。

已经存在的表达是：

| 要说明的事 | 使用 |
| --- | --- |
| 节点是缺失、null、空字符串、数字 0、数字文本、其他文本或非标量 | `RawAmountValue.presence` |
| 字段提取是正常形态、类型不符、路径不唯一或字面量无法完整保留 | `extractionFieldCode` |
| 这次匹配有没有成为已确认观测 | `mappingStatus` |
| 整份报文被阻断 | 处理尝试上的 `payloadCode`。不生成 `ObservedAmount` |

`presence` 仍是形态的唯一事实。`extractionFieldCode` 不替代它。`ABSENT`、`PRESENT_NULL`、`EMPTY_STRING` 和 `NUMERIC_ZERO` 的字段码仍是 `OK`。

`TYPE_MISMATCH`、`PATH_NOT_UNIQUE`、`RAW_TEXT_UNRETAINABLE` 保持 06-03-03-01 的原名，不合并成一个“非数字”或“不可保留”。

整份提取被阻断时，不伪造一条金额观测。阻断码包括 `PAYLOAD_MALFORMED`、`PAYLOAD_REF_INVALID`、`CONTRACT_VERSION_UNRESOLVED`、`CONTRACT_IDENTITY_MISMATCH`、`CONTRACT_PATHS_UNDEFINED`、`CONTRACT_PATH_INTEGRITY_FAILED`、`CONTRACT_PATHS_CORRUPT`、`CONTRACT_VERSION_HASH_MISMATCH`。它们记在处理尝试上。

`CONTRACT_PATHS_EMPTY` 是一次成功的空清单。它不产生字段观测，也不是阻断。

### 5.3 提取结果与观测的边界

06-03-03-01 把字段结果、字段失败、整份阻断和观测分开。本文不改那些规则，也不新造错误码。

| 输入 | 内存中的字段结果 | 选定一个节点的 `RawAmountValue` | 进入映射匹配 | `ObservedAmount` | 失败信息放在哪里 | 是否阻断任务 |
| --- | --- | --- | --- | --- | --- | --- |
| `fieldCode = OK` | 有。`presence` 是七种形态之一 | 有。字面量按 06-03-03-01 保留 | 是 | 匹配可以生成。今天只能是 `UNMAPPED` 或 `CONFLICT` | 字段码就是 `OK`，形态由 `presence` 表达 | 否 |
| `TYPE_MISMATCH` | 有。按实际节点记下真实形态，字段码保持原名 | 有形态，但不是一个已按金额标量接受的值 | 字段结果交给匹配；上游只写明它不能生成 `MAPPED` | 待确认。上游没有写可以生成未确认观测 | 字段结果上的 `TYPE_MISMATCH` | 否。其他路径继续 |
| `PATH_NOT_UNIQUE` | 有这个字段码。不挑选节点 | 没有。不能把其中一个节点当成该路径的原始值 | 同上，不能生成 `MAPPED` | 待确认。不得挑一个节点做成正常观测 | 字段结果上的 `PATH_NOT_UNIQUE` | 否。其他路径继续 |
| `RAW_TEXT_UNRETAINABLE` | 有这个字段码。不保存截断字面量 | 没有完整字面量，不能当成可追溯的原始值 | 同上，不能生成 `MAPPED` | 不形成。完整字面量不存在，不能伪装成正常金额观测 | 字段结果上的 `RAW_TEXT_UNRETAINABLE` | 否。其他路径继续 |
| 整份 `payloadCode` 不是 `OK` | 无 | 无 | 否 | 无 | 处理尝试上的阻断码 | 是。匹配不补造金额 |

`TYPE_MISMATCH` 与 `PATH_NOT_UNIQUE` 是否另外持久化一条不含 `MAPPED` 的失败记录，06-03-03-01 只写了“失败字段没有成功观测”和“不能生成 `MAPPED`”，没有写必须插入 `ObservedAmount`。本文不补充这条插入规则。

## 6. 匹配结果怎样写入观测

匹配只读取提取结果和任务上的 `snapshotId`。它不回平台再取报文，也不改 `rawText`。

### 6.1 未识别

字段结果已经形成，但快照里没有命中这个具体路径的映射条目时：

- 保存 `ObservedAmount`；
- `mappingStatus = UNMAPPED`；
- `mappingId`、`mappingVersion`、`proposedType`、`mappingEligibility` 为空；
- `evidenceStatus = NONE`；
- `semanticStatus = 无法验证`；
- `Money` 为空；
- 不进入计算。

未识别观测要保留。它不是可以丢掉的脏数据。今天的候选映射全部是 `DRAFT`，而且尚未进入已发布生产快照，所以当前能形成的业务类型观测都落在这一类，或者落在下面的冲突类。不能产生 `MAPPED`。

### 6.2 候选还不能执行

没有进入该快照映射条目或冲突条目的草稿，不参与这次匹配。草稿上的多个候选仍然可以同时存在于配置里。它们不能因为同时存在，就被观测挑成一个 `MAPPED`。

`ALLOWED_AS_UNIDENTIFIED` 只对应 `AMT.UNK.UNIDENTIFIED`，执行状态仍是 `UNMAPPED`。本文不把这个编码写成已经可以落库的生产类型。

设计决定：观测不保存“草稿候选版本”来冒充当次生效映射。没有进入该快照条目的 `mappingVersion`，不写到 `mappingId` 或 `mappingVersion` 上。快照条目里的 `RETIRED` 不是草稿，窗口匹配见第 6.4 节。

### 6.3 冲突

同一具体路径在该快照中命中冲突决定时：

- `mappingStatus = CONFLICT`；
- `conflictDecisionId` 指向该决定；
- `proposedType` 为空，不从候选里选一个；
- 不按字段名、数值大小或历史出现次数选择；
- 候选正文通过决定上的候选引用读取，不在观测上改写候选；
- 不进入计算。

冲突决定的发布、审核和封存沿用 06-03-02-03。观测只引用已经在该快照里的决定。

### 6.4 窗口匹配

已确认事实来自 06-03-02-03，观测侧照此执行，不另写一套窗口规则：

1. 只读任务上的 `snapshotId`。不改读最新快照，不用 `processedAt` 选择窗口。
2. 取出该契约版本、该具体路径的全部映射条目和全部冲突条目。条目上的 `ACTIVE` 和 `RETIRED` 都参加。`RETIRED` 只表示这条窗口已经写下右端点，不把落在窗口内的 `receivedAt` 判成 `UNMAPPED`。
3. 窗口是左闭右开 `[effectiveFrom, effectiveTo)`。`receivedAt` 等于起点则命中，等于终点则不命中。右端点为空表示从起点向后一直有效。
4. 恰好命中一条映射时，使用该 `mappingVersionId`，执行状态是 `MAPPED`。恰好命中一条冲突决定时，执行状态是 `CONFLICT`，并保留 `conflictDecisionId`。都没有命中则是 `UNMAPPED`。
5. 同一字段同时命中一条映射和一条冲突，或同时命中两条映射，说明快照违反了发布检查。任务失败。不选择其中一条，也不把该字段降成 `UNMAPPED`。

发布时才检查的条件，包括目标类型在该快照目录中为 `ACTIVE`、资格、语义、证据和 `NO_CONVERT`，决定这条映射当时能不能进入快照。它们不是运行时把 `RETIRED` 窗口改写成 `UNMAPPED` 的理由。`ALLOWED_AS_UNIDENTIFIED` 的执行状态仍是 `UNMAPPED`。

已确认事实：标准类型中 38 个是 `DRAFT`，1 个是 `RETIRED`，0 个是 `ACTIVE`。06-03-02-02 的映射全部是 `DRAFT`。现在没有已发布的生产映射窗口，因此现在没有 `MAPPED` 观测，也没有观测进入 `AmountCalculation`。这是因为没有可命中的已发布窗口，不是因为将来的 `RETIRED` 窗口一律无效。

`fieldCode` 不是 `OK` 的字段不能生成 `MAPPED`。未批准的草稿映射不能进入正式计算。聚合仍只按已批准规则执行。06-03-02-03 没有写“窗口标成 `RETIRED` 之后禁止计算”。本文不增加这条约。

## 7. 幂等身份

### 7.1 目标

相同原文、相同契约版本、相同映射快照、相同具体路径，重复处理只对应一条观测。换快照或换契约版本，身份不同，可以新建观测。

### 7.2 逻辑身份

已确认事实：06-03-03-01 已给出观测的逻辑身份，并写明存储上的唯一约束还没有实现。06-03-01 写明重试幂等键的算法未设计。

```text
observationIdentity =
    rawPayloadRef
  + snapshotId
  + contractVersionId
  + fieldPath
```

`fieldPath` 是具体路径。`extras[0].reduce_fee` 和 `extras[1].reduce_fee` 是两条身份。声明路径 `extras[].reduce_fee` 本身不是观测身份。

设计决定：本阶段不冻结这组身份的字节编码、哈希函数和测试向量，也不计算 `observationKey`。编码不复用 `request-v2`、`request-v3`、`contract-version-v2`、`contract-version-v3` 或 `contract-material-v1`。那些编码服务于配置命令和契约正文。观测身份要等存储产品设计时单独规定字段顺序、空值编码和摘要算法。

身份不含 `tenantId`，因为观测模型没有这个字段。身份也不含 `businessObjectType` 和 `businessObjectId`。一份原文若必须拆成多个业务对象的观测，要先修订 06-03-03-01 的身份，再改本文。当前不把业务对象悄悄加进身份。

`ingressMode` 不单独加入身份。`CALLBACK` 和 `ACTIVE_QUERY` 是两份来源、两份原文，因而 `rawPayloadRef` 不同。订单号相同也不合并。

### 7.3 重复执行与并发

| 场景 | 结果 |
| --- | --- |
| 相同身份再次执行 | 返回已有观测，不插入第二条 |
| 相同原文，另一份 `snapshotId` | 新建观测。旧观测保持原样 |
| 相同原文，另一个 `contractVersionId` | 新建观测 |
| 另一份原文，路径相同 | 另一条观测 |
| 同一原文，另一个具体路径 | 另一条观测 |
| 两个 Worker 同时处理同一身份 | 最终只保留一条。第二个写入发现身份已占用时返回已有观测 |
| 观测已写入，后续任务状态没有记下 | 重试通过同一身份找到已有观测 |
| 观测写入失败 | 原文和契约版本保持不变。重试重新提取，只补写尚未占用身份的路径 |

`fieldCode = OK` 的路径可以先形成观测。三种失败字段没有 `MAPPED`。`RAW_TEXT_UNRETAINABLE` 不形成正常观测。重试不把失败字段改成 0，也不覆盖已经写好的观测。

### 7.4 与配置幂等表分开

已确认事实：`amount_mapping_idempotency_request` 只服务于映射配置命令。Worker 的提取和观测写入不使用那张表。

观测身份的唯一约束属于以后的存储设计。本文不把它写成已经实现。

## 8. 历史与重放

### 8.1 不覆盖旧观测

同一份原文先后用两份快照处理时，两份观测都保留：

```text
原文 A
   |
   +-- snapshot V1 --> 观测 O1
   |
   +-- snapshot V2 --> 观测 O2
```

V2 的规则即使更完整，也不修改 O1 的 `proposedType`、`mappingStatus` 或原始取值。

同一 `snapshotId` 的重试不是这个图。它返回 O1，不创建 O2。

### 8.2 重放指针

设计决定：只有新建的那条观测可以在插入时写入 `replayOfObservationId`。调用方必须明确给出上一条观测。系统不按订单号搜索“最新观测”来自动填写。

这个指针可以空着。空着时，两条观测仍然靠各自的原文、快照和路径追溯，只是没有处理继承关系。

指针不参与匹配，不参与计算，也不能用来把旧观测改成新类型。

### 8.3 每条观测要能追溯到

- `rawPayloadRef`，以及当时的原文；
- 具体 `fieldPath`、`presence` 和按规则保留的 `rawText`；
- `AmountSource` 中的平台、接口、消息类型和 `ingressMode`；
- `contractVersionId` 与 `contractBodySha256`；
- `snapshotId`；
- 命中时的 `mappingId` 与 `mappingVersion`，或 `conflictDecisionId`；
- `observedAt`；
- 若插入时写了，则还有 `replayOfObservationId`。

已确认事实：已发布快照的批次、条目和所引用的封存版本不再修改。观测引用的就是这些不可变对象。

## 9. 持久化原则

这些名称是逻辑职责。MySQL 尚未决策，本文不给出 DDL，也不表示下列对象都要独立建表。

| 逻辑对象 | 职责 | 本文的选择 |
| --- | --- | --- |
| `RawPayloadRecord` | 不可变原文和 `rawPayloadRef` | 沿用已有对象。存储产品待确认 |
| `ObservedAmount` | 一条不可变观测，含形成时的路径、形态、字面量和匹配结果 | 一条观测记录。不指向一张原始值表 |
| `RawAmountValue` | 提取过程中的内存对象 | 不单独持久化，不建物理表 |
| 冲突关联 | 观测指向哪条冲突决定 | 使用观测上的一个 `conflictDecisionId`。一条路径在一份快照中至多命中一个冲突窗口，不另建关联表 |
| 重放关系 | 新观测指向旧观测 | 使用观测上的一个可空 `replayOfObservationId`。不另建关系表 |
| 处理尝试 | 整份报文阻断时的 `payloadCode`，以及没有形成观测的原因 | 是否独立保存待存储设计决定。它不是 `ObservedAmount` |

已创建观测的下列事实不通过更新改写：原文引用、具体路径、`presence`、`rawText`、`snapshotId`、`contractVersionId`、`contractBodySha256`、来源身份、`ingressMode`、`mappingStatus`、`proposedType`、映射版本和冲突决定引用。

映射规则后来变化时，新建观测或新建已发布的冲突决定。不在原观测上改审核结论。

若界面需要“当前处理到哪一步”，用独立的处理投影表达。投影可以更新，原始观测不更新。

## 10. Pipeline

### 10.1 顺序

已确认事实：Pipeline 先保存原文，再进入映射。提取在映射实现的前半段。计算只看已经允许的观测。

```text
接入
  |
  v
保存 RawPayloadRecord
  |
  v
使用任务上已经固定的 snapshotId
  |
  v
按契约版本提取
  |
  +-- 整份阻断：记下 payloadCode，不写 ObservedAmount
  |
  +-- 空路径清单：成功，观测条数为 0
  |
  v
字段结果已经形成
  |
  +-- fieldCode 不是 OK：不按第 6.4 节写成观测
  |
  v
只对 fieldCode = OK 按第 6.4 节匹配
        |
        +-- 命中一条映射：MAPPED
        +-- 命中一条冲突：CONFLICT，保留 conflictDecisionId
        +-- 都没有：UNMAPPED
        +-- 两条映射，或映射与冲突同时命中：任务失败，不挑选
```

`TYPE_MISMATCH`、`PATH_NOT_UNIQUE` 不在这里写成观测。`RAW_TEXT_UNRETAINABLE` 不写成正常观测。今天没有已发布生产映射窗口，因此走不到 `MAPPED`，也走不到 `AmountCalculation`。

### 10.2 模块

| 模块 | 职责 |
| --- | --- |
| `platform-core` | 观测模型、端口和结果对象 |
| `platform-transform` | 实现提取、匹配和观测形成。不写存储 |
| `platform-writer` | 写出原文引用、字段结果和观测。不把失败字段改成 0 |
| `platform-pipeline` | 只调用 core 端口。不依赖 Transform 的类 |
| `app-worker` | 装配实现并执行任务 |
| `platform-quality` | 不修改原始观测。未确认的金额关系不改写成提取错误 |

不新增模块。不把观测做成独立发布系统。

### 10.3 失败

| 情况 | 处理 |
| --- | --- |
| 原文取不回 | `PAYLOAD_REF_INVALID`。不生成观测，不改读最新报文 |
| 契约版本无法确定，或正文哈希不一致 | 阻断。不猜最新契约，不使用部分路径 |
| 快照不可用或快照校验失败 | 不换另一份快照 |
| 路径分类是 `CONTRACT_PATHS_UNDEFINED`、`CONTRACT_PATHS_CORRUPT` 或 `CONTRACT_PATH_INTEGRITY_FAILED` | 阻断。不生成观测 |
| 路径分类是 `CONTRACT_PATHS_EMPTY` | 成功。观测条数为 0 |
| 字段码是 `TYPE_MISMATCH` 或 `PATH_NOT_UNIQUE` | 不阻断其他路径。不能 `MAPPED`。是否另存失败记录，见第 5.3 节，本文不补规则 |
| 字段码是 `RAW_TEXT_UNRETAINABLE` | 不保存截断字面量，不形成正常观测。其他路径继续 |
| 未命中映射 | 保存 `UNMAPPED` |
| 命中一条冲突决定 | 保存 `CONFLICT`，保留 `conflictDecisionId` |
| `receivedAt` 落在 `RETIRED` 窗口内 | 仍使用该映射，执行状态按第 6.4 节。不因 `RETIRED` 改成 `UNMAPPED` |
| 同一字段命中两条映射，或映射与冲突同时命中 | 任务失败。不挑选，不改读其他快照 |
| 观测写入失败 | 重试同一身份。不改原文 |
| 计算条件不成立 | 保留观测。不生成计算结果 |

## 11. 示例

这些例子只说明观测模型。字段来自 06-03-02-02 的调查清单，映射仍是 `DRAFT`。示例里的数字若是冻结文档中的文档示例，按该文档的原值引用。它们不是生产报文，也不是已批准映射。

### 11.1 美团四个字段

已确认事实：`SC-MT-DOC-175` 的文档示例中，`total` 是 `22.1`，`shipping_fee` 是 `1.9`，`wmPoiReceiveCent` 是 `1234`，`package_bag_fee` 是 `12.9`。前两个单位原文空着，后两个分别是“分”和“元”。

四个具体路径形成四条 `RawAmountValue`，再形成四条观测。`presence` 都是 `NUMERIC_TEXT`，`extractionFieldCode` 都是 `OK`。当前 `mappingStatus` 都是 `UNMAPPED`。`proposedType` 为空。

不因为字段名像营业额、运费、商家应收或包装费就写入这些类型。不把 `22.1` 改成 `2210`，也不把 `1234` 改成 `12.34`。`Money` 为空。

### 11.2 未识别文本

假设某个没有进入封存契约路径清单的名字出现在原文里。提取不扫描它，因此不形成观测。

假设某个已经声明的路径，值是字符串 `18.50`，并且快照没有命中映射。结果是一条观测：`presence = NUMERIC_TEXT`，`rawText = 18.50`，`mappingStatus = UNMAPPED`，`proposedType` 为空，不进入计算。这里的路径和数字是假设，不是生产契约。

### 11.3 冲突

假设同一具体路径在快照中命中一条已发布的冲突决定。观测的 `mappingStatus` 是 `CONFLICT`，`conflictDecisionId` 指向该决定，`proposedType` 为空。不自动选择候选。

今天没有这样的已发布生产决定。本例只说明以后的写入形状。

### 11.4 换快照

同一 `rawPayloadRef` 在快照 V1 和 V2 下各处理一次：两条观测共享原文引用，`snapshotId` 不同。V1 的匹配结果保持不变。V2 可以在插入时把 `replayOfObservationId` 指到 V1。再次用 V1 处理时，返回 V1 那条，不插入第三条。

## 12. 测试场景

本文件不执行这些测试。存储唯一约束也还没有实现，涉及并发的场景只定义应有结果。

| 编号 | 场景 | 预期 |
| --- | --- | --- |
| T01 | 声明路径是 JSON 数字，字段码 `OK`，快照无映射条目 | 一条观测。`presence = NUMERIC_TEXT`，`mappingStatus = UNMAPPED`，`Money` 为空 |
| T02 | 声明路径不存在 | `presence = ABSENT`，字段码 `OK`。不补 0，不写 `rawText` |
| T03 | 节点是 JSON null | `presence = PRESENT_NULL`。不写成 0 或空字符串 |
| T04 | 节点是空字符串 | `presence = EMPTY_STRING`。字面量是空字符串 |
| T05 | 节点是数字文本 | `presence = NUMERIC_TEXT`。`rawText` 保持原文 |
| T06 | 节点是不能当作十进制数字的字符串 | `presence = OTHER_TEXT`。不生成 `Money` |
| T07 | 节点是对象或数组，路径没有重复标记 | `presence = NON_SCALAR`。不加总 |
| T08 | 数字 0 | `presence = NUMERIC_ZERO`。保留原始 token。它不是 T02 |
| T09 | 无映射条目 | `UNMAPPED`。映射版本和 `proposedType` 为空 |
| T10 | 快照命中冲突决定 | `CONFLICT`。不选择候选类型 |
| T11 | `receivedAt` 落在该快照一条 `RETIRED` 映射窗口内 | 使用该 `mappingVersion`。执行状态是 `MAPPED`。不改成 `UNMAPPED`。今天还没有这样的已发布窗口 |
| T11a | 同一字段命中两条映射，或同时命中映射和冲突 | 任务失败。不挑选 |
| T12 | 相同身份再执行 | 返回已有观测 |
| T13 | 两个 Worker 并发同一身份 | 最终一条观测。存储约束落地前，这仍是待实现的保证 |
| T14 | 换 `snapshotId` | 新建观测，不修改旧观测 |
| T15 | 写入失败后重试 | 已成功的路径不重复。未成功的路径再写。不改原文 |
| T16 | `rawPayloadRef` 取不回原文 | 阻断。观测条数为 0 |
| T17 | 契约正文哈希不一致 | 阻断。不使用部分路径 |
| T18 | 相同业务标识、尚未定义的租户 | 不写入 `tenantId`。本阶段不能用租户字段假装已经隔离 |
| T19 | 相同字面量、不同具体路径 | 两条观测 |
| T20 | 相同路径、不同原文 | 两条观测 |
| T21 | 字段码是 `TYPE_MISMATCH` 或 `PATH_NOT_UNIQUE` | 其他路径继续。不能 `MAPPED`，也不挑选节点。不把它们写成已经规定的未确认观测 |
| T21a | 字段码是 `RAW_TEXT_UNRETAINABLE` | 不保存截断字面量。不形成可完整追溯的正常观测 |
| T21b | 创建观测时没有业务对象标识 | 仍可形成观测。两列为空。不从金额字段推断 |
| T22 | `CONTRACT_PATHS_EMPTY` | 成功。观测条数为 0 |
| T23 | v2 契约或 v3 路径损坏 | 阻断。不生成观测，也不把 v2 当成空清单 |
| T24 | 同一订单号，接入方式一个是 `CALLBACK`，一个是 `ACTIVE_QUERY` | 两份原文、两组观测 |

## 13. 风险与待确认事项

1. 观测和原文的物理存储都未确定。本文的逻辑字段不是 DDL。
2. `rawPayloadRef` 怎样长期取回原文，仍待确认。
3. `observationId` 的生成算法未设计。观测身份的字节编码、摘要算法和唯一索引未冻结。
4. `businessObjectType` 与 `businessObjectId` 由哪个阶段提供，未设计。缺失不阻止观测。不能从金额字段推断。
5. 一份原文对应多个业务对象时，现有观测身份不会拆开。要拆开必须先改 06-03-03-01。
6. 租户模型未定义。观测不包含 `tenantId`。
7. 处理尝试是否单独保存，以及阻断码保留多久，未决定。
8. 冲突决定与观测之间目前只保存一个引用。若以后一份快照对同一路径允许多条决定，再修订这个形状。
9. 保留周期、归档和敏感数据保护未决定。
10. 今天没有 `ACTIVE` 标准类型，也没有已发布的生产映射。示例不能用来绕过这个限制。

与已有设计的一致性：

| 已有要求 | 本文 |
| --- | --- |
| 不新增模块 | 端口在 core，形成逻辑在 transform，写入在 writer，Worker 装配 |
| 七种 `presence` 不扩展 | 不增加 `SOURCE_VALUE_*` |
| 执行状态只有三种 | 观测的 `mappingStatus` 不用 `DRAFT`、`ACTIVE`、`RETIRED` |
| 当前不能 `MAPPED` | 第 6.4 节的条件今天不成立 |
| 缺失不补 0 | 观测保存真实形态，`Money` 为空 |
| 快照和契约不可变 | 观测固定 `snapshotId`、`contractVersionId` 和正文哈希 |
| 幂等表只服务配置命令 | 观测不写入 `amount_mapping_idempotency_request` |
| 不把 `tenantId` 放进金额对象 | 身份和字段都不含租户 |

本文没有修改 06-03-01、06-03-02-01、06-03-02-02、06-03-02-03 和 06-03-03-01。

## 14. 设计决策

| 决策 | 结论 | 理由 |
| --- | --- | --- |
| 三个金额对象是否合并 | 不合并 | 06-03-01 已分开保存、映射和计算 |
| 未识别观测是否保存 | 保存 | 未识别仍然是一次可追溯的处理结果 |
| 冲突观测是否保存 | 保存 | 冲突决定已经在快照中时，观测引用它，不另选类型 |
| 缺失是否补 0 | 不允许 | 七种形态不能互相顶替 |
| 观测阶段是否换算单位 | 不允许 | 当前转换策略只有 `NO_CONVERT`。本阶段不填写 `Money` |
| 是否覆盖历史观测 | 不允许 | 同一身份返回原观测。新快照才新建 |
| 是否复用配置幂等表 | 不允许 | 06-03-03-01 已分开 |
| 未确认映射是否进入计算 | 不允许 | 今天也没有任何 `MAPPED` |
| Pipeline 是否依赖 Transform 实现类 | 不允许 | ADR-0002 的依赖方向 |
| 是否新增观测模块或提取发布系统 | 不新增 | 沿用现有十个模块 |
| 是否把 `tenantId` 放进观测 | 不放入 | 06-03-01 已排除。租户模型未定义 |
| 是否新增一套观测形态码 | 不新增 | 形态用 `presence`，字段问题用原字段码，整份阻断不写观测 |
| 是否在本文冻结观测身份的 SHA-256 | 不冻结 | 逻辑身份沿用 06-03-03-01。字节编码留给存储设计，并避免复用契约哈希 |

## 15. 本阶段核对

1. `RawAmountValue`、`ObservedAmount`、`AmountCalculation` 的边界沿用已有对象。
2. 观测字段覆盖来源、原文、契约版本、快照和当次匹配，并且不复制已有来源字段。
3. 执行状态和定义侧生命周期分开。形态、字段码、阻断码也分开。
4. 未识别和冲突都有保存规则。当前未识别是实际结果，冲突形状只在快照已有决定时使用。
5. 逻辑幂等身份和并发时的返回方式已经写明。字节级唯一约束尚未实现。
6. 换快照新建观测，同快照不覆盖。
7. 模块职责符合 ADR-0002。
8. 与五份已有设计的冲突处，本文改为服从已有定义，没有回头修改那些文档。
9. 存储、业务对象来源、身份编码和租户仍保留为待确认。
10. 没有 Java 实现，没有 DDL，没有激活类型或映射。

## 16. 后续阶段

下列文档还没有编写。本文完成后才进入下一项，不在本文展开：

- 06-03-03-03 金额映射匹配引擎设计
- 06-03-03-04 金额标准化与约束校验设计
- 06-03-03-05 未识别金额与冲突金额处理设计
- 06-03-03-06 Pipeline 集成与端到端处理设计

后续设计沿用本文的观测身份、来源追溯，以及同快照不覆盖、换快照才新建的规则。若要改变逻辑身份的组成，先修订 06-03-03-01，再修订本文。

## 17. 第二轮修订记录

本轮只修订本文，不改五份上游文档。修订后的文本仍是设计，不是已经实现的提取、匹配或存储。

| 问题 | 解决情况 |
| --- | --- |
| R1 映射生命周期 | 第 6.2、6.4、10、12 节改为 `ACTIVE` 与 `RETIRED` 都按 `receivedAt` 参加窗口匹配。`RETIRED` 不再被写成 `UNMAPPED`。同一字段命中两条映射，或映射与冲突同时命中，任务失败且不挑选。任务仍固定 `snapshotId` |
| R2 提取失败与观测 | 第 5.3 节按 06-03-03-01 分开字段结果、字段失败、匹配、观测和整份阻断。`RAW_TEXT_UNRETAINABLE` 不形成正常观测。`TYPE_MISMATCH` 与 `PATH_NOT_UNIQUE` 不能 `MAPPED`；上游没有规定必须插入观测，本文不补 |
| R3 原始值生命周期 | 第 4.3、9 节写明 `RawAmountValue` 是内存中的逻辑对象，不单独持久化，也不建物理表。历史形态和字面量以观测形成时写下的字段结果为准。原文仍由 `rawPayloadRef` 保存 |
| R4 业务对象标识 | 第 4.2、4.4 节取消 `businessObjectId` 的必填。来源未设计时允许为空，不从金额字段推断，也不因缺失阻断观测 |

尚未解决：

1. `TYPE_MISMATCH`、`PATH_NOT_UNIQUE` 的字段结果是否要持久化成非观测记录，06-03-03-01 没有规定。
2. 解析器版本变化后，再次提取与当初字面量不一致时如何处理，上游没有字段，也没有规则。
3. `businessObjectType` 和 `businessObjectId` 由哪个阶段、从哪个非金额字段取得，仍然待确认。一份报文关联多个业务对象也仍待确认。
4. 同一字段同时命中两条冲突决定时，06-03-02-03 的匹配步骤没有单独原句。发布阶段已经拒绝重叠窗口。运行中若仍发生，本文不挑选；是否与第 6 步一样判定任务失败，待上游写明。
5. 外部版本号“未提供”对应零个或多个内部修订时，06-03-03-01 写的是提取阻断，06-03-02-03 的匹配步骤写的是 `UNMAPPED`。本文不新增第三种结果，也不改这两份文档。提取已阻断时没有观测。

设计边界：没有 Java 类，没有 SQL，没有新表，没有新模块。39 个标准金额类型和候选映射都没有被激活。当前文档状态仍是设计稿，待架构审查。
