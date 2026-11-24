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
| 具体路径 | 有 | 通过原始取值读取 | 通过来源观测追溯 |
| 原始字面量 | 有。缺失和 null 没有字面量 | 通过原始取值读取，不另存一份可能分叉的副本 | 不代替原始字面量 |
| `presence` | 有 | 通过原始取值保留 | 不直接参与计算 |
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
| `businessObjectType` | 是 | 订单、支付流水、退款单、退款明细必须分开 |
| `businessObjectId` | 是 | 与上一列一起指向具体业务对象。标识从哪里取得仍待确认 |
| `rawValue` | 是 | 指向本次的 `RawAmountValue`。路径、`presence`、`rawText`、`declaredUnitText` 以它为准 |
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
| `extractionFieldCode` | 是 | 06-03-03-01 的字段码原值：`OK`、`TYPE_MISMATCH`、`PATH_NOT_UNIQUE`、`RAW_TEXT_UNRETAINABLE` |
| `replayOfObservationId` | 否 | 换快照新建观测时，可以指向先前那条观测 |
| `observedAt` | 是 | 这条观测形成的时间 |

设计决定：

1. 不增加 `tenantId`。06-03-01 已说明租户模型未定义，观测不单造租户字段。业务对象关联继续用 `businessObjectType` 和 `businessObjectId`。
2. 不把 `sourceEventId`、`sourceMessageId`、`receivedAt`、`fieldPath` 再复制到观测上。它们分别属于 `AmountSource` 和 `RawAmountValue`。观测与这两份对象一起保存，避免两份路径或两份时间以后不一致。
3. 不增加 `extractionRequestId`。06-03-03-01 的提取请求身份就是 `rawPayloadRef + snapshotId + contractVersionId`，提取结果不单独落库。
4. `sourceMessageType` 可以是空字符串。空字符串和 NULL 不合并，沿用契约身份里的空消息类型。
5. `contractVersionId` 是内部版本。`AmountSource.sourceContractVersion` 仍是外部版本号，没有时写“未提供”。二者不互相代替。
6. `replayOfObservationId` 不进入幂等身份。它不表示两条观测的金额相等。

`businessObjectId` 的来源尚未设计。本文不编造从报文哪个字段读取订单号或退款单号。

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

快照里即使以后出现映射条目，只要规则生命周期不是 `ACTIVE`，目标类型在该快照引用的目录里不是 `ACTIVE`，资格是 `NOT_UNTIL_CONFIRMED`、`DISALLOWED` 或 `CONFLICT_ONLY`，或者语义、证据不满足 06-03-02-03 的发布条件，都不能写成 `MAPPED`。

`ALLOWED_AS_UNIDENTIFIED` 只对应 `AMT.UNK.UNIDENTIFIED`，执行状态仍是 `UNMAPPED`。本文不把这个编码写成已经可以落库的生产类型。

设计决定：观测不保存“草稿候选版本”来冒充当次生效映射。没有进入该快照条目的 `mappingVersion`，不写到 `mappingId` 或 `mappingVersion` 上。

### 6.3 冲突

同一具体路径在该快照中命中冲突决定时：

- `mappingStatus = CONFLICT`；
- `conflictDecisionId` 指向该决定；
- `proposedType` 为空，不从候选里选一个；
- 不按字段名、数值大小或历史出现次数选择；
- 候选正文通过决定上的候选引用读取，不在观测上改写候选；
- 不进入计算。

冲突决定的发布、审核和封存沿用 06-03-02-03。观测只引用已经在该快照里的决定。

### 6.4 已确认映射

只有下列条件同时成立，执行状态才是 `MAPPED`，这条观测才可能成为后续计算的输入：

1. 快照映射条目中的生命周期是 `ACTIVE`，并且接收时间落在该窗口内。
2. 使用的就是任务固定的 `snapshotId`，不是后来的新快照。
3. `contractVersionId` 属于该快照，正文哈希与提取时校验的哈希一致。
4. 目标类型在该快照引用的类型目录中是 `ACTIVE`。
5. `mappingEligibility` 允许这条规则执行。`NOT_UNTIL_CONFIRMED`、`DISALLOWED`、`CONFLICT_ONLY` 不可以。`ALLOWED_AS_UNIDENTIFIED` 的执行状态仍是 `UNMAPPED`。
6. 业务发布所要求的 `semanticStatus` 是 `部分确认`。
7. 证据不只有 `E4`、`E5`、`E6`。
8. 单位、币种、精度约束通过，并且转换策略仍是 `NO_CONVERT`，直到另有已批准规则。
9. 该路径没有未解决的冲突决定。
10. 聚合策略已经明确授权。未授权时不汇总。

已确认事实：标准类型中 38 个是 `DRAFT`，1 个是 `RETIRED`，0 个是 `ACTIVE`。06-03-02-02 的映射全部是 `DRAFT`。因此现在任何观测都不能是 `MAPPED`，也不能进入 `AmountCalculation`。

`extractionFieldCode` 不是 `OK` 的字段，也不能变成 `MAPPED`。

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

部分字段可以先形成未确认观测。失败字段没有成功观测。重试不把失败字段改成 0，也不覆盖已经写好的成功字段。

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
| `ObservedAmount` | 一条不可变观测及其来源、原始取值引用 | 一条观测记录承载第 4 节字段 |
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
按每个字段结果匹配
  |
  +-- UNMAPPED：保存未识别观测
  +-- CONFLICT：保存冲突观测
  +-- MAPPED：只有第 6.4 节全部成立才保存
                |
                v
             本阶段不填写 Money，也不调用计算
```

今天走不到 `MAPPED`，也走不到 `AmountCalculation`。

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
| 字段码是 `TYPE_MISMATCH`、`PATH_NOT_UNIQUE` 或 `RAW_TEXT_UNRETAINABLE` | 该路径仍可保存未确认观测。`mappingStatus` 不是 `MAPPED`。其他路径继续 |
| 未命中映射 | 保存 `UNMAPPED` |
| 命中冲突决定 | 保存 `CONFLICT` |
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
| T11 | 资格不允许，或类型不是 `ACTIVE` | 不是 `MAPPED`。不进入计算。当前全部属于这种情况 |
| T12 | 相同身份再执行 | 返回已有观测 |
| T13 | 两个 Worker 并发同一身份 | 最终一条观测。存储约束落地前，这仍是待实现的保证 |
| T14 | 换 `snapshotId` | 新建观测，不修改旧观测 |
| T15 | 写入失败后重试 | 已成功的路径不重复。未成功的路径再写。不改原文 |
| T16 | `rawPayloadRef` 取不回原文 | 阻断。观测条数为 0 |
| T17 | 契约正文哈希不一致 | 阻断。不使用部分路径 |
| T18 | 相同业务标识、尚未定义的租户 | 不写入 `tenantId`。本阶段不能用租户字段假装已经隔离 |
| T19 | 相同字面量、不同具体路径 | 两条观测 |
| T20 | 相同路径、不同原文 | 两条观测 |
| T21 | 字段码是 `TYPE_MISMATCH` | 保留该字段码和真实 `presence`。可以有未确认观测，不能 `MAPPED` |
| T22 | `CONTRACT_PATHS_EMPTY` | 成功。观测条数为 0 |
| T23 | v2 契约或 v3 路径损坏 | 阻断。不生成观测，也不把 v2 当成空清单 |
| T24 | 同一订单号，接入方式一个是 `CALLBACK`，一个是 `ACTIVE_QUERY` | 两份原文、两组观测 |

## 13. 风险与待确认事项

1. 观测和原文的物理存储都未确定。本文的逻辑字段不是 DDL。
2. `rawPayloadRef` 怎样长期取回原文，仍待确认。
3. `observationId` 的生成算法未设计。观测身份的字节编码、摘要算法和唯一索引未冻结。
4. `businessObjectType` 与 `businessObjectId` 从哪里取得，未设计。在此之前不能从字段名推断订单号。
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
