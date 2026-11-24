# 06-03-03-03 MTX Data Platform V2 金额映射匹配引擎设计

本文设计金额映射匹配引擎：在已经完成的字段提取之上，用任务固定的映射快照，为每个允许匹配的具体路径得出确定性的执行结果。它不编写 Java，不执行 DDL，也不批准任何生产映射。

依据是已经完成的六份设计：

- [06-03-01 金额领域模型](06-03-01%20MTX%20Data%20Platform%20V2%20多平台订单金额领域模型设计.md)
- [06-03-02-01 标准金额类型](06-03-02-01%20MTX%20Data%20Platform%20V2%20标准金额类型体系与编码规范.md)
- [06-03-02-02 四平台字段映射](06-03-02-02%20MTX%20Data%20Platform%20V2%20四平台原始金额字段与标准金额类型映射设计.md)
- [06-03-02-03 映射规则配置与版本](06-03-02-03%20MTX%20Data%20Platform%20V2%20金额映射规则配置模型与版本管理设计.md)
- [06-03-03-01 原始金额提取模型](06-03-03-01%20MTX%20Data%20Platform%20V2%20原始金额提取模型设计.md)
- [06-03-03-02 金额观测记录模型](06-03-03-02%20MTX%20Data%20Platform%20V2%20金额观测记录模型设计.md)

架构依据是 [ADR-0002](../../../decisions/0002-v2-module-architecture.md)、[ADR-0003](../../../decisions/0003-unmapped-conflict-observation.md) 和 [ADR-0004](../../../decisions/0004-external-contract-version-resolution.md)。契约版本解析以已接受的 ADR-0004 为准。匹配引擎不负责这次解析。

文中的标记含义：

| 标记 | 含义 |
| --- | --- |
| 已确认事实 | 上述文档或 ADR 已经写明 |
| 设计决定 | 本文为了把已冻结的匹配步骤收成可调用的引擎而作出的选择。它不改写已有文档 |
| 待确认 | 还没有证据，或存储产品尚未设计 |
| 实现前置待决事项 | 进入 Java 实现前必须先完成的设计。本文只标出缺口，不在本轮补一套新规则 |

本文是设计初稿，待架构一致性审查。它不代表匹配引擎已经实现，也不代表数据库已建表或生产映射已批准。

## 1. 设计目标与范围

目标是：对一份已经提取完成、契约版本已经确定、快照已经固定的报文，按具体路径给出可重放的匹配结果。结果只能是 `MAPPED`、`UNMAPPED`、`CONFLICT`，或一次明确的任务级失败。相同输入必须得到相同结果。失败时不改读另一份快照，也不挑选多条命中中的一条。

范围内：匹配输入和输出、匹配所读的条件、历史窗口、执行结果如何写入观测、模块调用顺序、字段级继续与任务级失败、测试场景。

范围外：不重新定义 39 个标准金额类型，不把 `DRAFT` 类型或候选映射改成生效，不执行单位转换、金额计算、金额聚合或营业额、实付等指标判断，不设计标准化，不设计未识别金额的后续处理流程，不设计解析器版本体系，不新增 Maven 模块，不编写物理表。

已确认事实：当前标准类型 38 个是 `DRAFT`，1 个是 `RETIRED`（`AMT.FEE.DELIVERY`），0 个是 `ACTIVE`。06-03-02-02 的映射全部是 `DRAFT`，已确认生产映射数量是 0。因此现在没有可命中的已发布生产映射窗口，匹配引擎今天不能产出 `MAPPED`。本文中的 `MAPPED` 和 `CONFLICT` 只说明窗口算法在快照条目存在时的形状。

## 2. 上游差异

下列差异已经写在冻结文档里。本文不修改那些文件，也不另造第三种执行状态。

1. 06-03-01 写“只有 `ACTIVE` 能用于新观测。停用后的版本仍解释旧观测”。06-03-02-03 写：快照条目上的 `RETIRED` 只表示窗口已有右端点；`receivedAt` 落在该窗口内时仍然命中，执行状态按命中的那条映射写成 `MAPPED`。06-03-03-02 已按 06-03-02-03 执行。本文同样按 06-03-02-03 和 06-03-03-02 匹配历史窗口。`RETIRED` 命中不是把该类型重新批准，也不是允许计算。
2. 零个或多个对应修订的统一规则以已接受的 ADR-0004 为准：返回 `CONTRACT_VERSION_UNRESOLVED`，不产生字段结果，不产生 `ObservedAmount`，不降级成 `UNMAPPED`，匹配引擎不启动。本任务不修改 06-03-02-03，也不把该文件的修订算作本轮完成的工作。核对当前文件时，其第 8 节第 2 步和 06-03-03-01 第 8、9 节已经是这一结果，不再把零个或多个写成 `UNMAPPED`。本文与这两份当前条款对齐。06-03-03-06 仍保留“尚未统一”的暂缓句。本文不修改它，也不把那句暂缓当成匹配引擎可以补造 `UNMAPPED` 的许可。
3. 06-03-02-03 第 8 节第 6 步写明：同时命中映射和冲突，或命中两条映射，任务失败。它没有单独写“同时命中两条冲突决定”。06-03-03-02 已把后一种定为快照完整性异常，任务失败，不降成 `UNMAPPED`。本文沿用 06-03-03-02。发布阶段的 `WINDOW_OVERLAP` 不改变。

## 3. 匹配模型

### 3.1 三类对象

已确认事实：定义侧生命周期是 `DRAFT`、`ACTIVE`、`RETIRED`。观测上的 `mappingStatus` 只使用 `MAPPED`、`UNMAPPED`、`CONFLICT`。配置生命周期不写入执行状态。

| 对象 | 回答什么 | 不回答什么 |
| --- | --- | --- |
| 映射定义 `AmountMapping` | 某一版规则正文准备指向哪个类型 | 这一次报文有没有命中 |
| 映射快照条目 | 在这份 `snapshotId` 里，该版本的哪一段窗口可以参加匹配 | 报文的 `receivedAt` 落在哪一段 |
| 执行结果 | 这个具体路径在这次任务中命中了什么 | 语义是否已被业务确认，计算是否允许 |

`contractVersionId` 标识契约正文。`mappingId` 加 `mappingVersion` 标识映射正文。契约版本变化不自动产生新的映射版本。映射版本变化也不代替契约版本。一份契约下可以有多条映射。

`snapshotId` 就是发布批次。它固定这次任务能看见的契约条目、映射条目、冲突条目、目录和正文哈希。匹配不读取最新快照，也不读取尚未进入该快照的草稿。

### 3.2 匹配输入

设计决定：匹配请求是一次任务上的调用对象。它引用提取结果，不复制一份可以改写的原文。

| 输入 | 来源 | 作用 |
| --- | --- | --- |
| `snapshotId` | 任务上已经固定的值 | 只装载这一份快照。匹配不改选最新快照 |
| `contractVersionId` | 上游已经唯一确定的内部版本 ID | 只读取这一版契约下的条目。匹配不再用外部版本号选择修订，也不自行选择最新契约版本 |
| `contractBodySha256` | 上游已经校验通过的正文哈希 | 防御性再核对。不一致时不进入窗口算法，不写成 `UNMAPPED` |
| `AmountExtractionResult` | 契约解析成功并完成提取后的 06-03-03-01 输出 | 只消费 `payloadCode = OK` 且 `fieldCode = OK` 的字段 |
| `AmountSource` | 接入端已经保存的来源 | 提供 `platform`、`sourceInterface`、`sourceMessageType`、`receivedAt`、`rawPayloadRef`、`ingressMode` |
| `receivedAt` | `AmountSource` | 唯一参加窗口判断的业务时间 |

`mappingId` 和 `mappingVersion` 不是输入条件。它们是命中一条映射条目之后从该条目读出的结果。

提取的 `payloadCode` 不是 `OK` 时，没有字段结果。匹配引擎不被调用，也不补造观测。`CONTRACT_PATHS_EMPTY` 的 `payloadCode` 仍是 `OK`，字段列表为空，匹配成功结束，观测条数为 0。

### 3.3 匹配上下文

上下文只能在下面条件全部满足后建立：`snapshotId` 已固定；`contractVersionId` 已唯一确定；该版本属于当前快照；契约家族身份和 `contractId` 已通过；外部版本号一致；正文哈希已通过；提取 `payloadCode = OK`。上下文是这次调用中只读的装载结果，加上本次提取结果和 `receivedAt`。它不包含 `processedAt`。处理时间只记日志。

`CONTRACT_VERSION_UNRESOLVED`、`CONTRACT_IDENTITY_MISMATCH` 和 `CONTRACT_VERSION_HASH_MISMATCH` 属于上游契约解析或校验阻断。匹配引擎不启动，不把它们改写成 `UNMAPPED`。匹配可以对已经给出的快照上下文做一致性防御，但不得因此重新按外部版本号解析契约版本。

装载未通过时没有匹配上下文。任务失败，不使用已经读到的部分条目。

### 3.4 匹配输出

设计决定：输出分成字段结果和任务结果。字段结果只存在于任务没有失败的时候。

| 输出 | 何时出现 |
| --- | --- |
| 一条 `ObservedAmount`，`mappingStatus = MAPPED` | 该具体路径恰好命中一条映射，并且资格是 `ALLOWED_IF_CONTRACT_DEFINES` |
| 一条 `ObservedAmount`，`mappingStatus = UNMAPPED` | 没有命中；或命中的版本资格是 `ALLOWED_AS_UNIDENTIFIED` |
| 一条 `ObservedAmount`，`mappingStatus = CONFLICT` | 恰好命中一条冲突决定 |
| 任务失败 | 快照装载失败、同一字段多重命中，或唯一映射的资格是 `NOT_UNTIL_CONFIRMED`、`DISALLOWED`、`CONFLICT_ONLY`。不写该字段的 `MAPPED`，不改读最新快照。上游契约解析阻断不是这里的匹配失败 |
| 不调用匹配 | `CONTRACT_VERSION_UNRESOLVED`、`CONTRACT_IDENTITY_MISMATCH`、`CONTRACT_VERSION_HASH_MISMATCH`，或提取 `payloadCode` 不是 `OK`。不产生 `ObservedAmount`，不降级为 `UNMAPPED` |
| 不写观测 | `fieldCode` 不是 `OK`。这不是任务失败 |

`MAPPED` 只表示这次执行引用了哪一版映射。它不表示 `semanticStatus` 已经升级，不表示证据已经足够，也不表示可以进入 `AmountCalculation`。

## 4. 匹配条件

已确认事实：映射身份包含契约，不能只按字段名匹配。创建消息和订单查询是两份契约。同名字段在未证明是同一契约之前不能共用语义。

引擎不把下面这些列再做成另一套独立的窗口条件：

| 字段 | 在匹配中的位置 |
| --- | --- |
| `platform`、`sourceInterface`、`sourceMessageType` | 属于契约家族身份。提取阶段已经与 `contractVersionId` 核对，不一致时是 `CONTRACT_IDENTITY_MISMATCH`，匹配不启动。匹配不再用平台名或消息名单独挑一条映射 |
| `sourceMessageType` 空字符串 | 沿用契约身份。空字符串和 NULL 不合并 |
| `contractVersionId` | 在该 `snapshotId` 的条目中定位这一版契约 |
| 具体 `fieldPath` | 与条目上的路径比较。`extras[0].reduce_fee` 和 `extras[1].reduce_fee` 分开匹配。声明路径 `extras[].reduce_fee` 本身不是观测身份 |
| `receivedAt` | 判断窗口。不用 `processedAt`，不用契约材料上的说明性时间 |
| `snapshotId` | 决定读取哪一批条目 |
| `mappingId`、`mappingVersion` | 命中后写入观测。未命中时为空。不作为查找键去“挑选最接近的版本” |

不增加租户、业务对象标识、金额数值、字段名相似度或历史出现次数作为匹配条件。业务对象标识缺失不阻止匹配。不从金额字段推断业务对象标识。

## 5. 生命周期与历史窗口

### 5.1 谁参加匹配

已确认事实：

- 没有进入该快照映射条目或冲突条目的 `DRAFT`，不参加。多个草稿候选可以同时存在于配置中，不能被挑成一个 `MAPPED`。
- 快照映射条目上的 `ACTIVE` 和 `RETIRED` 都参加。`RETIRED` 不是草稿。
- 冲突条目占用自己的窗口，不把候选映射标成 `ACTIVE`。
- 发布门槛，包括目标类型在该快照目录中为 `ACTIVE`、资格、语义、证据和 `NO_CONVERT`，决定当时能不能进入快照。匹配步骤不把这些门槛再执行一遍，来把已经装载的 `RETIRED` 窗口改写成 `UNMAPPED`。

### 5.2 窗口

已确认事实：窗口是左闭右开 `[effectiveFrom, effectiveTo)`。

- `receivedAt` 等于起点：命中。
- `receivedAt` 等于终点：不命中。
- 右端点为空：从起点向后一直有效。同一字段在同一快照里最多只能有一条这样的映射，也最多只能有一条这样的冲突决定，并且两者不能同时存在。
- 相邻窗口允许首尾相接。右端点属于后一段。

历史报文重放使用任务上已经固定的 `snapshotId`，再用该报文自己的 `receivedAt` 选择窗口。处理时间更晚，不把落在历史窗口内的接收时间改成未命中。

新到达的报文在任务第一次开始时固定当时最新且校验通过的快照。任务开始之后，即使发布了更新的快照，本次和本次重试仍使用原来的 `snapshotId`。

匹配引擎不选择快照，也不选择契约版本，更不自行创建重放任务。确定性契约解析失败不能因重复重试或新快照发布而变成成功。临时读取失败且候选查找尚未完成时，允许原任务在原 `snapshotId` 下重试，不得记成 `CONTRACT_VERSION_UNRESOLVED`。新快照修正配置后的重新处理必须是显式重放任务。它是新任务，不覆盖旧观测。上一句“当时最新且校验通过”与 06-03-02-03 第 14 节“没有子批次的最新已发布快照”的措辞差异仍待决，本文不决定。

## 6. 匹配算法

前置：任务 `snapshotId` 已固定；上游已经唯一确定 `contractVersionId`，且它属于当前快照；契约家族身份、`contractId` 和外部版本号已通过；正文哈希已通过；06-03-02-03 第 14 节的快照装载已经通过；提取 `payloadCode = OK`。任一契约解析或提取前置校验失败时，不进入下列窗口算法。只对 `fieldCode = OK` 的具体路径执行下列步骤。

1. 读取任务上的 `snapshotId`。不读取最新快照。
2. 防御性确认输入里已经确定的 `contractVersionId` 出现在该快照的契约条目中，正文哈希与条目和版本行一致。这一步不按外部版本号重新选择修订，也不改选最新契约版本。条目中没有时，上游阻断码仍是 `CONTRACT_VERSION_UNRESOLVED`。哈希不一致时，上游阻断码仍是 `CONTRACT_VERSION_HASH_MISMATCH`，不使用部分路径。家族身份、`contractId` 或外部版本号不一致时，上游阻断码仍是 `CONTRACT_IDENTITY_MISMATCH`。三种都不进入后续步骤，也不写成 `UNMAPPED`。
3. 取出该契约版本、该具体路径的全部映射条目和全部冲突条目。`ACTIVE` 和 `RETIRED` 都保留。
4. 保留 `effectiveFrom <= receivedAt`，且右端点为空或 `receivedAt < effectiveTo` 的条目。
5. 恰好一条映射、零条冲突：读取该版本的 `mappingEligibility`。按下面五种分别处理，不用“不是 `ALLOWED_AS_UNIDENTIFIED` 就是 `MAPPED`”：
   - `ALLOWED_IF_CONTRACT_DEFINES`：使用该 `mappingVersionId`，执行状态是 `MAPPED`。
   - `ALLOWED_AS_UNIDENTIFIED`：执行状态是 `UNMAPPED`，保留该资格和类型编码。
   - `NOT_UNTIL_CONFIRMED`、`DISALLOWED`、`CONFLICT_ONLY`：快照完整性异常，任务失败。不形成该字段的 `MAPPED`，不降成 `UNMAPPED`，不改读最新快照。
6. 恰好一条冲突、零条映射：执行状态是 `CONFLICT`，保留这一个 `conflictDecisionId`。`proposedType` 为空。
7. 映射和冲突都没有：执行状态是 `UNMAPPED`。映射引用和 `proposedType` 为空。
8. 两条及以上映射，或至少一条映射与至少一条冲突同时命中：任务失败。不选择，不降成 `UNMAPPED`，不改读最新快照。
9. 两条及以上冲突决定：任务失败，视为快照完整性异常。不任选 `conflictDecisionId`，不降成 `UNMAPPED`，不使用该字段已经读到的部分匹配。

同一报文的其他具体路径各自按自己的条目匹配。某一个路径是唯一 `CONFLICT` 或 `UNMAPPED` 时，其他路径继续。第 5 步的非法资格、第 8 步或第 9 步失败的是整次任务，不是把该路径改写成另一种执行状态后继续提交。

`candidateDisposition` 不读取。Worker 不读取草稿。

## 7. 结果如何写入观测

写入形状沿用 06-03-03-02。匹配不改 `rawText`，不回平台取报文，不把 `presence` 改成别的形态，不填写 `Money`。

| 执行结果 | 写入 |
| --- | --- |
| `MAPPED` | `mappingId`、`mappingVersion`、`proposedType` 取该版本的目标类型，`mappingEligibility`、`semanticStatus`、`evidenceStatus`、`evidenceRef` 抄录该版本。`conflictDecisionId` 为空。`mappingStatus = MAPPED` |
| `UNMAPPED` 且没有命中 | `mappingId`、`mappingVersion`、`proposedType`、`mappingEligibility` 为空。`evidenceStatus = NONE`。`semanticStatus = 无法验证`。`conflictDecisionId` 为空 |
| `UNMAPPED` 且资格是 `ALLOWED_AS_UNIDENTIFIED` | 执行状态仍是 `UNMAPPED`。抄录该版本的资格和类型编码。本文不把 `AMT.UNK.UNIDENTIFIED` 写成已经可以落库的生产类型 |
| `CONFLICT` | `conflictDecisionId` 指向唯一决定。`proposedType` 为空。不从候选里选一个类型。候选正文只通过决定上的引用读取 |

`extractionFieldCode` 在能形成观测时是 `OK`。三种失败码留在字段结果上。

逻辑身份沿用 06-03-03-02：

```text
observationIdentity =
    rawPayloadRef
  + snapshotId
  + contractVersionId
  + fieldPath
```

同一身份再次执行：返回已有观测，不重新匹配覆盖 `mappingStatus`、`proposedType`、`rawText` 或 `presence`。更换 `snapshotId` 或 `contractVersionId` 才是另一条身份，可以新建观测。新建时可以写入调用方给出的 `replayOfObservationId`。系统不按订单号搜索最新观测来自动填写。旧观测保持原样。

今天没有已发布生产窗口。`fieldCode = OK` 的路径按第 6 节会落到“没有命中”的 `UNMAPPED`，或者在将来的快照里落到 `CONFLICT`。不能因为 06-03-02-02 的草稿候选看起来像某个类型，就写成 `MAPPED`。

## 8. 状态与资格边界

这些状态同时出现在一条观测上时，各自保留原意：

| 名称 | 匹配引擎怎么用 |
| --- | --- |
| 定义生命周期 `DRAFT` / `ACTIVE` / `RETIRED` | 只出现在版本或快照条目上。不写入 `mappingStatus` |
| 执行状态 `MAPPED` / `UNMAPPED` / `CONFLICT` | 本次窗口结果 |
| `semanticStatus` | 命中映射版本时抄录。未命中时用 `无法验证`。不因为 `MAPPED` 改成已确认 |
| `evidenceStatus` | 命中时抄录。未命中时用 `NONE`。有 `E1-A` 不表示业务已确认 |
| `mappingEligibility` | 五种取值沿用 06-03-02-01。命中时抄录，未命中时为空。资格不是执行状态 |

发布门槛负责阻止 `NOT_UNTIL_CONFIRMED`、`DISALLOWED`、`CONFLICT_ONLY` 作为生效映射进入快照。Worker 在快照已经加载之后，仍按第 6 节第 5 步检查唯一命中映射的资格。这三种资格是快照完整性异常：任务失败，不形成该字段的 `MAPPED`，不降成 `UNMAPPED`，不忽略资格，也不改读最新快照。06-03-02-03 第 14 节没有单独写这一步。本文只补匹配引擎的防御，不改那份冻结文档。

`ALLOWED_IF_CONTRACT_DEFINES` 是唯一可以写成 `MAPPED` 的资格。`ALLOWED_AS_UNIDENTIFIED` 可以进入快照，但执行状态保持 `UNMAPPED`，并保留资格和类型编码。

转换策略只有 `NO_CONVERT`。匹配不把分换成元，不补 0，不取绝对值，不舍入。

## 9. 模块与调用

已确认事实：V2 只有 ADR-0002 的十个模块。Pipeline 只依赖 `platform-common` 和 `platform-core`。

| 模块 | 职责 |
| --- | --- |
| `platform-core` | 匹配端口、匹配请求、匹配结果和 `ObservedAmount` 模型。不依赖 Transform 的类 |
| `platform-transform` | 实现匹配算法。不写存储，不调用质量模块，不执行计算 |
| `platform-pipeline` | 按顺序调用 core 端口：保存原文、提取、匹配、写出。不依赖 Transform 实现类 |
| `app-worker` | 装配 Transform 实现，装载任务上的 `snapshotId` 并执行 |
| `platform-writer` | 写出原文引用、字段结果和已经形成的观测。不把失败字段改成 0，也不把失败字段写入观测 |
| `platform-quality` | 不修改匹配结果。未确认的金额关系不改写成匹配错误 |
| `platform-connector` | 只提供已经取到的报文和来源。匹配不反向调用它 |

不新增模块。不引入 `rdp-server`。调度 API 不进入 Pipeline、Transform 或 Writer。

调用顺序：

```text
app-worker 固定 snapshotId
        |
        v
契约版本解析与校验
        |
        +-- 确定性失败：任务阻断，不调用提取和匹配
        |
        +-- 临时读取失败：原任务使用原 snapshotId 重试
        |
        v
原始金额提取
        |
        +-- payloadCode 不是 OK：不调用匹配
        |
        v
platform-pipeline 调用 core 匹配端口
        |
        v
platform-transform 按第 6 节匹配
        |
        v
platform-writer 写出允许形成的观测
```

Pipeline 仍只调用 `platform-core` 端口。它不依赖 Transform 的实现类。上面的解析与提取由 Worker 装配的实现完成，不新增模块。

## 10. 时序

下列图只说明调用顺序。图中的映射窗口都是假设。今天没有这样的已发布生产窗口。

### 10.1 正常命中

```text
Worker          Pipeline         Transform              Writer
  |                |                |                      |
  | 固定 snapshotId|                |                      |
  |--------------->|                |                      |
  |                | 契约版本已唯一确定并校验通过          |
  |                | 提取，fieldCode = OK                  |
  |                |--------------->|                      |
  |                |                | 恰好一条映射窗口命中 |
  |                |                | MAPPED               |
  |                |                |--------------------->|
  |                |                |                      | 插入观测
```

`Money` 仍为空。不因为 `MAPPED` 进入计算。

### 10.2 未命中

```text
Transform：该路径在快照中没有落在 receivedAt 上的条目
        |
        v
UNMAPPED，映射引用为空，semanticStatus = 无法验证
        |
        v
Writer 插入观测。任务不失败
```

草稿候选即使存在，也不出现在这次读取中。

### 10.3 唯一冲突

```text
Transform：恰好一条冲突窗口命中，没有映射窗口命中
        |
        v
CONFLICT，保留这一个 conflictDecisionId，proposedType 为空
        |
        v
同一报文的其他 fieldCode = OK 的路径继续匹配
```

### 10.4 历史报文重放

```text
任务开始时固定 snapshot V1
        |
        v
重试或事后回看仍读 V1
        |
        v
用报文自己的 receivedAt
        |
        +-- 落在 V1 里的 RETIRED 窗口：MAPPED，不改成 UNMAPPED
        |
        +-- 期间已经发布 V2：本次不改读 V2
        |
        v
同一身份已有观测：返回原观测，不覆盖
```

换用 V2 必须是另一次显式重放任务，固定另一个 `snapshotId`，然后新建观测。匹配引擎不创建这次任务。旧观测保持原样。

### 10.5 快照完整性异常

```text
装载哈希不符、契约条目缺失，
或同一字段命中两条映射、映射与冲突同时命中、两条冲突决定，
或唯一映射的资格是 NOT_UNTIL_CONFIRMED、DISALLOWED、CONFLICT_ONLY
        |
        v
任务失败
        |
        v
不挑选，不降成 UNMAPPED，不改读最新快照，不写回修复
```

## 11. 异常处理

| 情况 | 级别 | 结果 |
| --- | --- | --- |
| `fieldCode = OK`，唯一映射命中，资格是 `ALLOWED_IF_CONTRACT_DEFINES` | 字段 | `MAPPED`。不阻断任务 |
| `fieldCode = OK`，唯一映射命中，资格是 `ALLOWED_AS_UNIDENTIFIED` | 字段 | `UNMAPPED`。保留资格和类型编码。不阻断任务 |
| `fieldCode = OK`，唯一映射命中，资格是 `NOT_UNTIL_CONFIRMED`、`DISALLOWED` 或 `CONFLICT_ONLY` | 任务 | 快照完整性异常。不形成该字段的 `MAPPED`，不降成 `UNMAPPED`，不改读最新快照 |
| `fieldCode = OK`，没有命中 | 字段 | `UNMAPPED`。不阻断任务 |
| `fieldCode = OK`，唯一冲突决定 | 字段 | `CONFLICT`。其他成功字段继续。不阻断任务 |
| `TYPE_MISMATCH` | 字段 | 保留实际 `presence`。不进入匹配，不形成观测。其他成功字段继续 |
| `PATH_NOT_UNIQUE` | 字段 | 不挑选节点，不形成 `RawAmountValue`，不形成观测。其他成功字段继续 |
| `RAW_TEXT_UNRETAINABLE` | 字段 | 不保存截断字面量，不进入匹配，不形成正常观测。其他成功字段继续 |
| 整份提取 `payloadCode` 不是 `OK` | 任务 | 匹配不启动，不生成观测 |
| 契约版本无法唯一确定，或 `contractVersionId` 不属于当前快照 | 上游 | `CONTRACT_VERSION_UNRESOLVED`。不进入提取和匹配，不产生字段结果和 `ObservedAmount`，不写成 `UNMAPPED` |
| 契约家族身份、`contractId` 或外部版本号不一致 | 上游 | `CONTRACT_IDENTITY_MISMATCH`。不进入路径提取和匹配 |
| 正文哈希与封存值不一致 | 上游 | `CONTRACT_VERSION_HASH_MISMATCH`。不使用部分路径，不产生字段结果和 `ObservedAmount` |
| 临时读取失败，候选查找尚未完成 | 原任务 | 使用原 `snapshotId` 重试。不记成 `CONTRACT_VERSION_UNRESOLVED`，不切换最新快照 |
| 确定性解析失败后发布了新快照 | 原任务 | 仍然失败，不自动切换。重新处理必须是显式重放任务。匹配引擎不创建该任务 |
| 快照不存在、条目数量不符、正文或决定哈希不符 | 任务 | 失败。不使用部分条目 |
| 两条映射，或映射与冲突同时命中 | 任务 | 失败。不挑选，不降成 `UNMAPPED` |
| 两条冲突决定 | 任务 | 失败，视为快照完整性异常。不任选决定，不降成 `UNMAPPED` |
| 同一身份已有观测 | 字段 | 返回原观测。不阻断 |
| 观测写入失败 | 任务中的这一次写入 | 原文和契约版本保持不变。重试只补写尚未占用身份的路径，不覆盖已写下的观测 |

字段级失败不把整份报文标成没有来过。任务级失败不自动切换快照。

同一任务里，一个路径触发第 6 节第 5 步的非法资格、第 8 步或第 9 步之后，其他路径是否已经写出观测、写入是否要一起撤销，06-03-02-03 和 06-03-03-02 都没有规定存储事务。本文不补充事务规则。已经按同一身份写成功的观测，重试时仍然返回原观测，不被后来的失败改写。

## 12. 测试场景

本文件不执行这些测试。涉及 `MAPPED` 或 `CONFLICT` 的行，都假设快照里已经有对应的已发布条目。这个假设不是当前生产事实。当前生产映射数量是 0。

| 编号 | 输入 | 预期 | 是否阻断任务 |
| --- | --- | --- | --- |
| T01 | `fieldCode = OK`。一条映射窗口的 `effectiveFrom` 等于 `receivedAt`，右端点更晚，资格是 `ALLOWED_IF_CONTRACT_DEFINES` | 命中该映射，执行状态是 `MAPPED` | 否 |
| T02 | 同一条窗口的 `effectiveTo` 等于 `receivedAt` | 这条窗口不命中。若没有其他命中，则为 `UNMAPPED` | 否 |
| T03 | 快照条目生命周期是 `ACTIVE`，`receivedAt` 落在窗口内，资格是 `ALLOWED_IF_CONTRACT_DEFINES` | `MAPPED`。抄录 `mappingId`、`mappingVersion`、`proposedType`。`Money` 为空 | 否 |
| T04 | 快照条目生命周期是 `RETIRED`，`receivedAt` 落在该窗口内，`processedAt` 已经晚于右端点，资格是 `ALLOWED_IF_CONTRACT_DEFINES` | 仍命中该映射，执行状态是 `MAPPED`。不因 `RETIRED` 改成 `UNMAPPED` | 否 |
| T05 | 该具体路径在快照中没有任何落入窗口的映射或冲突。配置里可以有未发布草稿 | `UNMAPPED`。映射引用和 `proposedType` 为空。`semanticStatus = 无法验证` | 否 |
| T06 | 恰好一条冲突决定落入窗口，没有映射落入窗口 | `CONFLICT`。保留这一个 `conflictDecisionId`。`proposedType` 为空。不从候选中选择 | 否 |
| T07 | 同一字段、同一 `receivedAt` 落入两条映射窗口 | 任务失败。不挑选，不降成 `UNMAPPED`，不改读最新快照 | 是 |
| T08 | 同一字段、同一 `receivedAt` 同时落入一条映射和一条冲突 | 任务失败。不挑选，不改读最新快照 | 是 |
| T09 | 同一字段、同一 `receivedAt` 落入两条冲突决定 | 任务失败，视为快照完整性异常。不任选 `conflictDecisionId`，不降成 `UNMAPPED` | 是 |
| T10 | 同一 `rawPayloadRef`、`snapshotId`、`contractVersionId`、具体路径再次执行，观测已存在 | 返回已有观测。不覆盖 `mappingStatus` 和字面量 | 否 |
| T11 | 同一原文改为另一个 `snapshotId` 再处理 | 新建观测。旧观测的匹配结果不变。可写入调用方给出的 `replayOfObservationId` | 否 |
| T12 | 同一原文引用下使用另一个已经确定的 `contractVersionId` | 另一条观测身份。按该契约版本自己的条目匹配，不沿用前一个版本的命中 | 否 |
| T13 | 同一报文中一个路径是 `TYPE_MISMATCH`、`PATH_NOT_UNIQUE` 或 `RAW_TEXT_UNRETAINABLE`，另一个路径 `fieldCode = OK` | 三个失败码都不进入匹配，都不形成正常观测。`OK` 路径继续。对象或数组在标量路径上时，`presence = NON_SCALAR` 且 `fieldCode = TYPE_MISMATCH`，不加总，不取第一个元素 | 否 |
| T14 | 唯一命中的映射版本资格是 `ALLOWED_AS_UNIDENTIFIED` | 执行状态是 `UNMAPPED`。不写成 `MAPPED` | 否 |
| T15 | 快照行缺失、`itemCount` 不符或正文哈希不符 | 任务失败。不使用部分条目，不改读最新快照 | 是 |
| T16 | 输入的 `contractVersionId` 不在该快照契约条目中 | 任务失败。不猜“未提供”对应的另一个修订 | 是 |
| T17 | 提取 `payloadCode` 不是 `OK` | 匹配不启动。观测条数为 0 | 是。阻断发生在提取，匹配不补造金额 |
| T18 | 用当前仓库的真实状态：没有已发布生产映射窗口 | `fieldCode = OK` 的路径是 `UNMAPPED`。没有 `MAPPED`，也没有 `AmountCalculation` | 否 |
| T19 | 唯一映射命中，资格是 `NOT_UNTIL_CONFIRMED` | 任务失败，视为快照完整性异常。不形成该字段的 `MAPPED`，不降成 `UNMAPPED`，不切换快照 | 是 |
| T20 | 唯一映射命中，资格是 `DISALLOWED` | 任务失败，视为快照完整性异常。不形成该字段的 `MAPPED`，不降成 `UNMAPPED`，不切换快照 | 是 |
| T21 | 唯一映射命中，资格是 `CONFLICT_ONLY` | 任务失败，视为快照完整性异常。不形成该字段的 `MAPPED`，不降成 `UNMAPPED`，不切换快照 | 是 |
| T22 | `contractVersionId` 属于当前快照，家族身份、外部版本号和正文哈希都通过，提取 `payloadCode = OK` | 提取已经成功完成。进入匹配。匹配不再根据外部版本号选择修订。本行未执行 | 否 |
| T23 | 版本表有该 `contractVersionId`，当前快照契约条目没有 | `CONTRACT_VERSION_UNRESOLVED`。不进入提取和匹配，无字段结果，无 `ObservedAmount`。本行未执行 | 是。阻断在上游 |
| T24 | 明确外部版本号在当前固定快照内对应零个修订 | `CONTRACT_VERSION_UNRESOLVED`。不降级为 `UNMAPPED`。本行未执行 | 是。阻断在上游 |
| T33 | 接入端未提供 `contractVersionId`，提供了明确外部版本号，当前固定快照内该契约家族对应多个内部修订 | `CONTRACT_VERSION_UNRESOLVED`。不猜最新修订，不产生字段结果，不产生 `ObservedAmount`，不进入路径提取和匹配。本行未执行 | 是。阻断在上游 |
| T25 | 外部版本号为“未提供”，当前固定快照内恰好一个同为“未提供”的修订 | 采用该 `contractVersionId`。校验通过后才提取和匹配。本行未执行 | 否 |
| T26 | 外部版本号为“未提供”，当前固定快照内对应零个或多个修订 | `CONTRACT_VERSION_UNRESOLVED`。不猜最新修订，不进入匹配。本行未执行 | 是。阻断在上游 |
| T27 | 契约家族五段身份、`contractId` 或外部版本号与版本行不一致 | `CONTRACT_IDENTITY_MISMATCH`。不进入路径提取和匹配。本行未执行 | 是。阻断在上游 |
| T28 | 契约正文与封存 `bodySha256` 不一致 | `CONTRACT_VERSION_HASH_MISMATCH`。不使用部分路径，无字段结果，无 `ObservedAmount`。本行未执行 | 是。阻断在上游 |
| T29 | 快照或契约版本读取尚未完成，没有得出候选数量 | 原任务用原 `snapshotId` 重试。不记成 `CONTRACT_VERSION_UNRESOLVED`，不切换最新快照。本行未执行 | 否。本次读取未完成 |
| T30 | 确定性解析失败之后发布了新快照 | 原任务仍失败，不自动切换 `snapshotId`。本行未执行 | 是 |
| T31 | 新快照修正配置后显式重放同一原文 | 新任务、新身份，不覆盖旧观测。匹配引擎不创建该任务。本行未执行 | 否 |
| T32 | 提取 `pathStatus = CONTRACT_PATHS_EMPTY`，`payloadCode = OK`，字段列表为空 | 提取成功。匹配成功结束，观测数为 0。这不是契约版本解析失败。本行未执行 | 否 |

## 13. 设计决策

| 决策 | 结论 | 理由 |
| --- | --- | --- |
| 是否重写窗口算法 | 不重写 | 步骤来自 06-03-02-03，两条冲突的失败来自 06-03-03-02 |
| `RETIRED` 是否不能匹配 | 不能这样解释 | 它只表示窗口已有右端点 |
| 平台、接口、消息类型是否再做一次字段名匹配 | 否 | 它们已经收进契约身份。匹配键是契约版本加具体路径加接收时间 |
| `MAPPED` 是否表示可以计算 | 否 | 计算要另有已批准规则。本阶段 `Money` 为空，策略只有 `NO_CONVERT` |
| 草稿能否被选成 `MAPPED` | 否 | 草稿不在快照条目中 |
| 失败时是否改读最新快照 | 否 | 任务继续使用已经固定的 `snapshotId` |
| 是否新增模块或错误码 | 否 | 任务失败沿用“快照完整性异常”和已有阻断码的含义，不另起一套状态 |
| 是否设计解析器版本 | 否 | 仍是 06-03-03-02 记下的实现前置待决事项 |
| 是否在本文激活类型或映射 | 否 | 当前 0 个 `ACTIVE` 类型，0 条已确认生产映射 |

## 14. 风险与待确认事项

1. 第 2 节第 1 点和第 3 点的上游差异仍在。第 2 点按 ADR-0004 以及当前 06-03-02-03、06-03-03-01 对齐。06-03-03-06 的“尚未统一”暂缓句还没有删除，本文不修改它。
2. 一个路径多重命中导致任务失败时，同一任务中其他路径已经写出的观测是否一起撤销，上游没有存储事务。本文不规定。
3. 06-03-02-03 第 14 节没有写加载后的资格防御。本文已在第 6 节第 5 步规定这三种资格使任务失败。上游原文没有改。同一任务中其他路径已经写出的观测是否一起撤销，仍见上一条。
4. `observationId` 的生成算法、观测身份的字节编码和唯一索引仍未冻结。逻辑身份沿用 06-03-03-02。
5. 业务对象标识由谁提供，仍然待确认。匹配不推断。
6. 失败字段是否另存独立记录，仍然待存储设计决定。匹配不把它们写入 `ObservedAmount`。
7. 解析器版本管理、重放策略和兼容性策略是 Java 实现前置待决事项。本文不设计这套体系。已有观测重试返回原观测。
8. 今天没有已发布生产窗口。第 10 节和第 12 节里的 `MAPPED`、`CONFLICT` 不能当作当前可以执行的生产结果。

## 15. 一致性核对

| 已有要求 | 本文 |
| --- | --- |
| 七种 `presence` 不扩展 | 匹配不改形态，不新增形态码 |
| 只有 `fieldCode = OK` 进入正常匹配 | 第 6、11、12 节 |
| 三种失败码不形成正常观测 | 第 11、12 节。标量路径上的对象或数组仍是 `NON_SCALAR` 加 `TYPE_MISMATCH` |
| `ACTIVE` 与 `RETIRED` 都按 `receivedAt` 左闭右开匹配 | 第 5、6 节 |
| 两条映射、映射加冲突、两条冲突都任务失败 | 第 6、10.5、12 节 |
| 同快照不覆盖，换快照才新建 | 第 7 节 |
| 五种资格、六种语义、证据状态不并进执行状态 | 第 8 节。只有 `ALLOWED_IF_CONTRACT_DEFINES` 可以是 `MAPPED` |
| 非法资格不形成 `MAPPED` | 第 6、8、11、12 节。`NOT_UNTIL_CONFIRMED`、`DISALLOWED`、`CONFLICT_ONLY` 任务失败，不降成 `UNMAPPED` |
| Pipeline 不依赖 Transform 实现 | 第 9 节，符合 ADR-0002 |
| 不把 `DRAFT` 写成生产映射 | 第 1、7、13 节 |
| 不设计解析器版本体系 | 第 13、14 节 |
| 契约版本解析先于提取和匹配 | 第 2、3、6、9、11 节。匹配不选择 `contractVersionId`，也不把上游阻断写成 `UNMAPPED` |
| 原任务不换快照，显式重放不覆盖旧观测 | 第 5.2、10.4、11、12 节，符合 ADR-0003 与 ADR-0004 |

本文没有修改 06-03-01、06-03-02-01、06-03-02-02、06-03-02-03、06-03-03-01 和 06-03-03-02。

本文没有 Java 实现，没有 DDL，没有新表，没有新模块。39 个标准金额类型和候选映射都没有被激活。

## 16. 第一轮修订记录

本轮只修订本文，不改六份上游文档。

| 问题 | 解决情况 |
| --- | --- |
| 唯一映射命中后的资格默认分支 | 第 6 节第 5 步改为按五种 `mappingEligibility` 分别处理。只有 `ALLOWED_IF_CONTRACT_DEFINES` 是 `MAPPED`。`ALLOWED_AS_UNIDENTIFIED` 仍是 `UNMAPPED`，并保留资格和类型编码。`NOT_UNTIL_CONFIRMED`、`DISALLOWED`、`CONFLICT_ONLY` 是快照完整性异常，任务失败 |

同步修订了第 3.4、8、10.5、11、12、14、15 节，避免其他章节仍写“不是未识别资格就是 `MAPPED`”。增加 T19、T20、T21。T14 保留。窗口算法、观测身份、模块依赖和 Pipeline 顺序没有改。没有新增状态码、领域对象、表或 Java 类。

## 17. 第二轮修订记录

本轮只修订本文，同步已接受的 ADR-0004。不修改 ADR，也不修改 06-03-02-03、06-03-03-01、06-03-03-02 和 06-03-03-06。

| 内容 | 位置 |
| --- | --- |
| 契约版本解析先于金额提取和匹配 | 第 2、6、9、10 节 |
| 匹配引擎只消费已经确定的 `contractVersionId` | 第 3.2、3.3、6 节 |
| 上游阻断不降级为 `UNMAPPED` | 第 3.4、11 节 |
| 临时读取失败与确定性解析失败分开，显式重放不覆盖旧观测 | 第 5.2、10.4、11 节 |
| 补充 T22 至 T32 | 第 12 节。这些行都写明未执行 |

窗口算法、资格、冲突、观测身份和模块边界没有改。没有新增错误码、执行状态、领域对象、表或 Java 类。没有执行这些验收场景。

## 18. 第三轮修订记录

本轮只修订第 12 节的验收场景，并记下这次修改。不修改第 6 节匹配算法，不修改模块依赖、观测身份和历史窗口规则。

| 内容 | 位置 |
| --- | --- |
| T22 的输入已经包含提取成功，预期改为进入匹配，不再写成进入提取 | 第 12 节 |
| 新增 T33：接入端未提供 `contractVersionId`，明确外部版本号在当前固定快照内对应多个内部修订时上游阻断 | 第 12 节 |

T33 和修改后的 T22 都标明未执行。没有新增错误码、状态、领域对象或数据库结构。没有执行这些验收场景。
