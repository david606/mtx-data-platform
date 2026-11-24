# 06-03-02-03 MTX Data Platform V2 金额映射规则配置模型与版本管理设计

本文设计金额映射规则怎样配置、版本化、审核、发布和被 Worker 使用。它不编写 Java，不执行 DDL，也不批准任何生产映射。

依据是已经完成的三份设计：

- [06-03-01 金额领域模型](06-03-01%20MTX%20Data%20Platform%20V2%20多平台订单金额领域模型设计.md)
- [06-03-02-01 标准金额类型](06-03-02-01%20MTX%20Data%20Platform%20V2%20标准金额类型体系与编码规范.md)
- [06-03-02-02 四平台字段映射](06-03-02-02%20MTX%20Data%20Platform%20V2%20四平台原始金额字段与标准金额类型映射设计.md)

架构依据是 [ADR-0002](../../../decisions/0002-v2-module-architecture.md)、[ADR-0003](../../../decisions/0003-unmapped-conflict-observation.md)、[ADR-0004](../../../decisions/0004-external-contract-version-resolution.md) 和 [调度边界](../../../architecture/scheduling.md)。契约版本解析以已接受的 ADR-0004 为准。

## 1. 设计目标与范围

目标是让映射规则在生效前经过校验和发布，生效后能被 Worker 按固定版本执行，并且旧观测始终能回到当时的契约版本和映射版本。

范围内：来源契约、映射规则、版本、发布准入、建议的 MySQL 配置表、Admin API、Worker 读取方式和 Pipeline 边界。

范围外：不重新定义 39 个金额类型，不把 06-03-02-02 的候选写成已发布规则，不设计经营额或结算公式，不实现调度，不决定原始报文的存储产品。

当前没有任何映射可以发布为 `ACTIVE`。原因不是流程没画完，而是标准类型里 38 个仍是 `DRAFT`、1 个是 `RETIRED`，0 个是 `ACTIVE`。发布校验会拒绝用映射发布绕过类型治理。

## 2. 既有架构基线

根 POM 实际聚合且只聚合这十个模块：`platform-common`、`platform-core`、`platform-messaging`、`platform-connector`、`platform-transform`、`platform-quality`、`platform-writer`、`platform-pipeline`、`app-admin`、`app-worker`。不新增第 11 个模块，不恢复 `rdp-server`。

已核对的约束：

| 事项 | 仓库事实 | 本文怎么用 |
| --- | --- | --- |
| Core | 只依赖 common，不依赖 Spring、Kafka、JDBC、MyBatis、Quartz、Redis | 配置模型和端口放在 core，数据库访问不放进 core |
| Pipeline | 只依赖 common 和 core，不依赖 Quartz 或 DolphinScheduler | 映射只通过 core 端口进入流程 |
| Admin 与 Worker | 独立部署，互不依赖 | Admin 写配置，Worker 读已发布快照，不调用 Admin 内部类 |
| Kafka、Doris、MyBatis-Plus、Quartz | 方向已确认，骨架还没有把对应 starter 放上运行时类路径 | 不把它们写成已经完成的能力 |
| MySQL、Redis、Flyway | ADR-0002 写明未决策 | 下文的 MySQL DDL 是配置库建议，不是已经接入的数据库 |
| 调度 | 初期方向是 Quartz，当前没有调度实现 | 映射发布不新建调度任务，也不把 Quartz API 放进 Pipeline |

与任务基线的差异只有数据库：任务要求按 MySQL 设计配置表，ADR-0002 仍写 MySQL 未决策。因此第 12 节的表是“若配置库采用 MySQL 时的结构”。在该决策接受之前，不能创建这些表，也不能在模块里引入 MySQL 驱动。

## 3. 领域对象与职责

沿用既有对象，不合并它们的状态。

| 对象 | 职责 | 不负责 |
| --- | --- | --- |
| RawPayloadRecord | 不可变保存原文 | 解释金额 |
| RawAmountValue | 保存一个路径的原始形态 | 决定标准类型 |
| AmountSource | 记录平台、接口、消息、契约版本、原文引用和接收时间 | 保存金额数值 |
| ObservedAmount | 记录一次观测及其执行结果 | 修改历史观测 |
| AmountMapping | 一条映射规则的身份和版本 | 计算合计 |
| AmountCalculationRule 与 AmountCalculation | 批准后的计算和计算结果 | 把原始字段直接改成标准类型 |
| MappingConflictDecision | 保存一次不能选定类型的审核决定、参与比较的映射版本和证据 | 把候选映射标成生效 |
| AmountTypeCatalogRelease | 封存某一时刻的类型生命周期、资格、语义状态和聚合策略 | 新建或改写金额类型编码 |

状态边界：

| 名称 | 取值 | 属于 |
| --- | --- | --- |
| AmountMapping.lifecycleStatus | `DRAFT`、`ACTIVE`、`RETIRED` | 规则生命周期。06-03-01 的定义侧 MappingStatus |
| ObservedAmount.mappingStatus | `MAPPED`、`UNMAPPED`、`CONFLICT` | 一次执行结果。06-03-01 的执行侧 MappingStatus |
| candidateDisposition | `SINGLE_CANDIDATE`、`MULTI_CANDIDATE`、`HOLD_UNIDENTIFIED`、`CONFLICTING_CANDIDATES`、`NO_AMOUNT`、`BLOCKED_BY_ELIGIBILITY` | 只来自 06-03-02-02 的设计评估，不写入执行状态 |
| mappingEligibility | `ALLOWED_IF_CONTRACT_DEFINES`、`NOT_UNTIL_CONFIRMED`、`DISALLOWED`、`ALLOWED_AS_UNIDENTIFIED`、`CONFLICT_ONLY` | 类型定义上的资格，发布时读取，不在配置里改写 |
| EvidenceStatus | `E1-A`、`E1-B`、`E2`、`E3`、`E4`、`E5`、`E6`、`NONE` | 证据来自哪里 |
| SemanticStatus | `部分确认`、`待平台确认`、`待业务确认`、`待财务确认`、`存在冲突`、`无法验证` | 语义确认到哪一步 |
| typeLifecycleStatus | `DRAFT`、`ACTIVE`、`RETIRED` | 标准类型自身。与映射生命周期不是同一个字段 |

另外增加一个治理字段 `approvalStatus`，取值 `NOT_SUBMITTED`、`IN_REVIEW`、`APPROVED`、`REJECTED`。它只表示草稿有没有通过审核，不是第四个生命周期，也不是执行状态。

`DRAFT`、`ACTIVE`、`RETIRED` 仍是 06-03-01 的规则生命周期，但不再做成版本行上可反复改写的一列。还没有进入快照、也没有被已发布冲突决定引用时，版本行是未封存的。进入映射快照或被冲突决定发布引用后，正文封存。只有映射条目才把这一版写成 `ACTIVE` 或 `RETIRED`。后一个快照可以把同一封存版本看成 `RETIRED`，旧快照里的 `ACTIVE` 不跟着改变。同一份快照也可以同时留下同一字段的多段映射窗口。`RETIRED` 只表示这段窗口已经有右端点，接收时间落在窗口内时仍然要使用它。

## 4. SourceContract 配置模型

一个平台有多份契约。京东创建消息、京东查询摘录和京东代码发现字段是三份契约。美团官方页和回调代码路径是两份契约。淘宝详情查询和消息 217 也是两份契约。官方材料和代码调查不能因为字段名相同而合并。

`sourceType` 属于契约家族。唯一身份是：

`platform + sourceSystem + sourceInterface + sourceMessageType + sourceType`

来源种类写错时新建家族。旧 `contractId` 不改，也没有接口把 `CODE_INVESTIGATION` 改成 `OFFICIAL_DOCUMENT`。

契约版本使用下面这些标识。外部版本号不再充当内部唯一键。

| 标识 | 含义 |
| --- | --- |
| `contractVersion` | 外部版本号。官方没有版本号时写“未提供”。它可以在同一家族里重复 |
| `contractVersionId` | 内部分配的唯一版本 ID。主键。分配时正文哈希还没有计算 |
| `sourceRevision` | 该家族内的材料修订号，从 1 递增。只在材料指纹不同时增加 |
| `materialSha256` | 材料指纹。编码名是 `contract-material-v1`。不含 `contractVersionId` 和 `sourceRevision` |
| `bodySha256` | 封存正文指纹。v3 使用 `contract-version-v3`，包含已经分配的版本 ID、修订号、`payloadFormat` 和路径列表 |
| `payloadFormat` | 整份报文的格式。v3 正文中的一个字段。当前接受的值只有 `JSON` |

这些标识属于契约版本对象 `SourceContractVersion`。同一对象还保存证据状态、语义状态、来源引用和生效区间。建议表是 `source_contract_version`。v3 行的 `payload_format` 保存 `payloadFormat`，并与正文哈希使用同一个值。

因此，官方没有版本号、后来材料变了、`payloadFormat` 变了、路径清单变了，或者封存前补充了会改变正文的证据，都会得到新的 `sourceRevision` 和新的 `contractVersionId`。材料指纹相同则返回已有版本，避免空修订。

创建时先计算 `materialSha256`，用它判断材料是不是新的，再分配 `sourceRevision`，最后计算 `bodySha256`。`sourceRevision` 进入正文哈希，不进入材料指纹。这样先有修订号，再有正文哈希；判断是否新建版本时也不读取修订号。完整顺序见第 14 节。

已经进入快照的版本行封存。封存后不能改正文，不能修改 `payloadFormat`，也不能追加会改变指纹的证据或路径。

这是设计纠错，不是另一套提取产品。金额路径清单和 `payloadFormat` 都属于契约版本正文。`contract-version-v2` 的字段顺序不包含路径，也不包含 `payloadFormat`，这个定义保持不变。新的契约版本用 `contract-version-v3` 把这两项算进正文哈希。没有单独的提取档案，也不为提取再做一次发布。

没有消息类型时写入空字符串。接入记录必须尽量带上 `contractVersionId`。运行时解析见第 8 节第 2 步：外部版本号无论是明确版本还是“未提供”，都只在任务已经固定的快照内判断对应修订是否恰好一个。恰好一个才确定 `contractVersionId`。零个或多个返回 `CONTRACT_VERSION_UNRESOLVED`，阻断，不产生字段结果和 `ObservedAmount`。Worker 不猜测最新修订，也不把尚未解析的契约版本写成 `UNMAPPED`。

## 5. AmountMapping 配置模型

`mappingId` 沿用 06-03-01：同一契约家族、同一字段路径、同一个目标类型，是一条映射。正文变化增加 `mappingVersion`。路径或目标类型变化则新建 `mappingId`。

映射版本行保存规则正文、资格、语义、证据等级和审核人。生效窗口不写在版本行上，而写在发布快照的映射条目上。版本进入已完成快照，或者作为候选被一条已发布的冲突决定引用后，正文封存。

`candidateDisposition` 只放在草稿上。它不是 MappingStatus，Worker 也不读取草稿。

同一路径不能靠两条同时生效、窗口无限重叠的映射来表示冲突。冲突使用第 7 节的冲突决定。

## 6. 状态与生命周期

版本行用 `publicationState` 表示正文是否封存：`DRAFT` 或 `SEALED`。这不替换生命周期。还没有进入快照、也没有被已发布冲突决定引用的版本是 `DRAFT`。封存后，只有真正进入映射条目的版本才在快照里写成 `ACTIVE` 或 `RETIRED`。只作为冲突候选被封存的版本不写成生效映射。

一个快照里，同一契约版本的同一字段可以有多条映射条目。每条有自己的窗口。窗口之间不能重叠。`RETIRED` 表示这条窗口已经写下右端点，不表示这段历史不能再被匹配。

字段冲突决定是另一类条目。它占用自己的窗口，不把任何候选映射标成 `ACTIVE`。

审核状态仍是 `NOT_SUBMITTED`、`IN_REVIEW`、`APPROVED`、`REJECTED`。创建草稿时写入作者。提交时写入提交人。审核时写入审核人和结论。审核人必须是另一名操作者。

## 7. 配置冲突和运行时 CONFLICT

配置候选冲突发生在发布前。存在多个未排除的目标类型，或者 `candidateDisposition` 是 `MULTI_CANDIDATE` 或 `CONFLICTING_CANDIDATES` 时，不能把其中一条发布成该窗口内的生效映射。

若审核结论就是“这个字段不能选定类型”，要先形成一条不可变的冲突决定，再把它放进新快照。决定本身不是 `ACTIVE` 映射。

冲突决定保存这些内容，不只保存哈希：

| 内容 | 作用 |
| --- | --- |
| `conflictDecisionId` | 决定的稳定 ID |
| `contractVersionId` 和 `fieldPath` | 决定落在哪一版契约的哪个字段 |
| 候选明细 | 每一个参与比较的 `mappingVersionId` |
| `conflictType` 和原因 | 为什么不能选定 |
| 证据行 | 可以还原当时依据了什么 |
| 作者、审核人、审核结论 | 不能只靠推断 |
| 审核时确认的窗口 | 决定正文的一部分 |
| `bodySha256` | 防止封存后正文被改。哈希覆盖的是上面这些正文，不是代替它们 |

这里用三张表，而不是一个无法查询的 JSON 块，也不是只存哈希。决定头、候选明细和证据行的职责不同。候选数量不固定，所以不能塞进决定头的一个虚构 ID。

审核由另一名操作者完成。未通过审核的决定不能进入快照。本文不另设修改冲突决定的接口。决定创建之后正文若要变化，另建一条新决定。

发布时，新快照增加一条冲突条目，指向这个 `conflictDecisionId`。第一次发布的窗口必须等于决定里审核确认的窗口。候选映射不写入映射条目，因此不会变成 `ACTIVE`。

发布同一事务按下面的依赖闭包处理候选，缺任何一步都整笔回滚：

1. 读取该决定的全部候选映射版本。候选少于两条，或任一候选没有 `contractVersionId`，返回 `CONTRACT_VERSION_MISSING`。
2. 重算每个候选的映射正文哈希，以及它引用的契约版本正文哈希。映射哈希不一致返回 `CANDIDATE_BODY_CHANGED`。契约版本不存在或哈希不一致返回 `CONTRACT_VERSION_HASH_MISMATCH`。
3. 尚未封存的候选映射版本和对应契约版本，在本事务内改为 `SEALED` 并写入正文哈希。已经封存的行不更新。
4. 父快照里还没有的 `contractVersionId`，在新快照增加一条契约条目。同一个 `contractVersionId` 只留一条，不因为两个候选引用它就复制两份。
5. 这些新增契约条目计入 `itemCount`，并进入 `contentSha256`。只封存契约版本、却不写入新快照，是不完整发布。

封存后该版本整行不能再改，证据也不能再追加。

候选明细只保存已有的 `mappingVersionId` 和当时的 `candidateBodySha256`，不复制整份映射配置。映射正文仍在原来的版本行和证据行。冲突决定自己的 `bodySha256` 覆盖每一条候选的版本号和正文哈希。还原历史决定时，先核对候选哈希，再读取那一版映射正文。缺行或哈希不一致，就视为快照损坏。

其他草稿受到的影响如下：

- 同一个 `mappingVersionId` 上还没写完的编辑会失败，返回 `VERSION_SEALED`。要改规则必须新建映射版本。新版本不会自动进入已经封存的冲突决定。
- 同一个 `mappingId` 的其他版本不封存，那些草稿可以继续编辑。
- 另一条尚未发布的冲突决定如果引用同一版本，且候选哈希仍然一致，以后可以发布。它不能再改这个版本的正文。
- 发布前已经审核通过的同一版本，以后仍可以进入映射条目，但窗口不能和冲突窗口重叠。没有审核通过的版本不能因为被封存就变成生效映射。

发布时如果版本正文已经和候选哈希不一致，整笔回滚，错误是 `CANDIDATE_BODY_CHANGED`。两条并发写入用版本行上的 `rowVersion` 判断先后，不使用数据库锁。后写入的一方得到 `VERSION_SEALED` 或 `CANDIDATE_BODY_CHANGED`。

Worker 只读任务固定的快照。接收时间落在冲突条目窗口内时，读取决定、候选和证据，把该字段的 `ObservedAmount.mappingStatus` 写成 `CONFLICT`。同一报文的其他字段继续按自己的条目处理。

解除冲突时发布另一个新快照：把冲突条目的右端点收到解除时刻，并在同一时刻开始一条新的映射窗口。旧决定、候选哈希和旧快照都不改。不通过把多条映射同时标成没有右端点的 `ACTIVE` 来制造冲突。

## 8. 时间模型

四个时间不能混用：

| 名称 | 含义 | 用来做什么 |
| --- | --- | --- |
| `contractEffectiveFrom` / `contractEffectiveTo` | 契约材料上的说明性时间 | 写在契约版本正文里，随 `contractVersionId` 进入快照。不用来选择版本 |
| `mappingEffectiveFrom` / `mappingEffectiveTo` | 某条映射或冲突决定在该快照中的窗口 | 契约版本确定后，用 `receivedAt` 选择 |
| `receivedAt` | 报文接收时间 | 唯一参与窗口判断的业务时间 |
| `processedAt` | Worker 处理或重试的时间 | 只记日志 |

窗口使用左闭右开区间 `[effectiveFrom, effectiveTo)`。`receivedAt` 等于起点时命中，等于终点时不命中。没有右端点表示从起点一直向后，同一字段在同一快照里最多只能有一条这样的映射，也最多只能有一条这样的冲突决定，并且两者不能同时存在。

匹配算法：

1. 读取任务上的 `snapshotId`。不读取最新快照，也不使用 `processedAt`。
2. 在第 1 步读到的 `snapshotId` 内解析契约版本。这一步发生在金额路径提取之前。接入端给出 `contractVersionId` 时直接采用该值，不再用外部版本号在多个修订中选择。没有给出时，按契约家族和外部版本号查找；外部版本号为“未提供”时，只与同为“未提供”的修订比较。恰好一个对应修订才确定 `contractVersionId`，随后校验正文并进入路径提取。零个或多个都返回 `CONTRACT_VERSION_UNRESOLVED`，阻断，不产生字段结果，不产生 `ObservedAmount`，也不写成 `UNMAPPED`。不猜测最新修订。候选不属于本快照契约条目时，同样是 `CONTRACT_VERSION_UNRESOLVED`。家族身份、`contractId` 或外部版本号字符串与版本行不一致时，是 `CONTRACT_IDENTITY_MISMATCH`。正文哈希不符时，是 `CONTRACT_VERSION_HASH_MISMATCH`。这三种都不进入路径提取。
3. 在该快照中取出这个契约版本和字段的全部映射条目、全部冲突条目。`ACTIVE` 和 `RETIRED` 都参加，不把 `RETIRED` 滤掉。
4. 保留 `effectiveFrom <= receivedAt` 且右端点为空或 `receivedAt < effectiveTo` 的条目。
5. 只有第 2 步已经确定契约版本并且路径提取得到具体字段后，才做本步。命中一条映射时，使用该 `mappingVersionId`，执行状态是 `MAPPED`。命中一条冲突决定时，执行状态是 `CONFLICT`。都没有则是 `UNMAPPED`。这里的 `UNMAPPED` 是字段映射未命中，不是契约版本未解析。
6. 同时命中映射和冲突，或命中两条映射，说明快照违反了发布检查。任务失败，不擅自选一条。

已经开始的任务继续使用任务上的 `snapshotId`。新到达的迟到报文在第一次开始时固定当时最新的快照，再用自己的 `receivedAt` 选择窗口。当前快照里已经标成 `RETIRED` 的历史窗口仍然参加匹配，处理时间不参加。

原任务重试继续使用该任务已经固定的 `snapshotId`。确定性解析失败不得因反复重试而变成成功，也不得因此改读最新快照。修正配置并发布新快照后，重新处理已保存的原始报文必须创建显式重放任务。显式重放是新任务，不复用原任务的任务身份和幂等键。新任务的观测遵守 ADR-0003，不覆盖原任务已经写下的观测。观测身份仍是 `rawPayloadRef + snapshotId + contractVersionId + fieldPath`。本段不改变上一句和本文件第 14 节的快照选择措辞。第 8 节与第 14 节对最新快照的用词差异，以及校验失败时能否跳过该快照，仍是 ADR-0004 留下的待决事项，本文不决定。

发布事务对同一契约版本、同一字段的映射窗口和冲突窗口做一次排序检查。后一段的起点若早于前一段的终点，或者前一段没有终点而又存在后一段，就拒绝整笔发布。相邻窗口允许首尾相接，因为右端点不属于前一段。

## 9. 不可变发布快照

快照就是发布批次。`batchId` 就是 `snapshotId`。不再使用审计号。

条目按三种结构分开，避免用 `0` 表示不存在的映射版本：

| 表 | 保存什么 | 不保存什么 |
| --- | --- | --- |
| `mapping_publish_batch_contract_item` | 这份快照引用的 `contractVersionId` | 映射和冲突 |
| `mapping_publish_batch_mapping_item` | 一条 `mappingVersionId`、生命周期和窗口 | 虚构的冲突 ID |
| `mapping_publish_batch_conflict_item` | 一条 `conflictDecisionId` 和窗口 | 虚构的映射版本 |

拆开是因为三种条目的必填标识不同。一张表只能靠空值或假 ID 混合它们，数据库就无法强制“映射条目必须有映射版本、冲突条目必须有冲突决定”。

批次和全部条目在同一个事务中插入，状态直接是 `PUBLISHED`。失败整笔回滚。父批次唯一。批次保存三类条目的合计数量和 `contentSha256`。哈希使用第 14 节的长度前缀编码。冲突候选不单独计入 `itemCount`，它们的正文哈希已经算进冲突决定的 `bodySha256`。Worker 重算后不一致就拒绝执行，不改读最新配置，也不补写快照。

新发布不修改旧批次。新快照复制父快照的全部历史窗口。退役只在新快照里给被退役的那条补上右端点。

## 10. 发布事务

一次发布、退役、冲突发布或回滚都在同一个数据库事务里做完。审计记录和幂等请求记录也在这个事务里写完。任一步失败就整笔回滚，不留下批次，也不留下 `SUCCEEDED` 请求记录。

普通配置变更使用同一套请求记录，只是没有 `snapshotId`。查询接口不写请求记录。

请求进入后的顺序：

1. 用第 13 节的 `request-v3` 计算 `requestHash`。字段按该节的固定顺序取值，不按 JSON 出现顺序。集合先按该节的排序键排好；排序键全部相同的元素，编码字节也相同，交换它们不改变哈希。不使用数据库返回顺序、JSON 属性顺序或 Map 迭代顺序。
2. 插入幂等请求行，状态先写 `PROCESSING`。`responsePayload` 和 `responseSnapshotId` 先写空字符串，`completedAt` 为空。`requestId` 是主键。同一请求号的并发插入只有一个能拿到这行，另一个等待该事务结束。这里用的是主键冲突，不另加 `SELECT FOR UPDATE`。等待超时则本事务回滚并返回 `REQUEST_IN_PROGRESS`，不写快照。
3. 若请求号已存在，本事务回滚，再读已提交的那一行。哈希不同返回 `REQUEST_REUSED`。哈希相同且状态是 `SUCCEEDED`，原样返回第一次的 `responsePayload`。发布类同时用其中的 `responseSnapshotId` 读取原快照，读不到则返回 `SNAPSHOT_INTEGRITY_FAILED`，不另发一份。对方若已回滚，本事务的插入会成功，由它继续完成本次业务。
4. 读取父批次。第一个父标识是 `ROOT`。父批次不存在时回滚。
5. 读取本批次声明的类型目录。目录必须已经封存，条目数量和目录哈希必须正确。`ADD_MAPPING` 再检查第 13 节的发布门槛。发布冲突决定只确认目录本身完整，不用候选类型仍是 `DRAFT` 来拒绝冲突记录。
6. 以父快照的全部条目为底稿。历史窗口原样复制。新映射窗口只在新快照里把前一条的右端点写成新起点，并把该条标成 `RETIRED`。不删除历史条目，也不回写旧快照。
7. 发布冲突决定时执行第 7 节的候选依赖闭包。映射发布、退役和回滚也要收集映射条目、冲突条目和候选映射引用的全部 `contractVersionId`，把父快照中还没有的契约版本补进新快照。按 `contractVersionId` 去重。契约正文缺失或哈希不符则回滚。
8. 把同一契约版本、同一字段的映射窗口和冲突窗口放在一起排序。后一段起点早于前一段终点，或前一段没有终点又存在后一段，就拒绝。冲突决定第一次进入快照时，条目窗口必须等于决定正文里审核确认的窗口。以后的快照只能把冲突条目的右端点提前。
9. 用补齐后的三类条目计算 `itemCount` 和 `snapshot-v2` 哈希。新增的契约条目既计入数量，也进入哈希。
10. 插入 `PUBLISHED` 批次和全部条目，并写一条审计。审计记录操作者、状态变化和批次号，不保存响应正文。
11. 把幂等行改为 `SUCCEEDED`，写入 `responseSnapshotId`、完整 `responsePayload` 和 `completedAt`。非发布操作的 `responseSnapshotId` 为空字符串。

提交成功后，相同请求号和相同 `requestHash` 永远返回这次写好的响应。事务失败时，幂等行随批次一起消失，不会变成可重放的成功。另一个请求号即使内容相同，也是另一次发布，由父批次唯一约束决定谁能成为下一个快照。

回滚发布是按目标历史快照的视图再发布一个新快照。目标快照和中间快照都不改。

## 11. 标准金额类型目录追溯

类型定义仍以 06-03-02-01 为来源，本文不另做一套类型审核流程。

为了让历史任务复现当时的资格，增加最小的不可变目录版本：

| 对象 | 职责 |
| --- | --- |
| `amount_type_catalog_release` | 一个 `catalogVersionId`、条目数量和整份目录的指纹 |
| `amount_type_catalog_entry` | 该版本中每个类型编码的生命周期、映射资格、语义状态和聚合策略 |

目录版本一旦写下就不能改条目。封存时必须包含 06-03-02-01 当前全部 39 个类型编码，`itemCount` 必须等于条目行数。类型从 `DRAFT` 变成 `ACTIVE` 时，封存一份新目录，不回写旧目录。映射批次记录 `typeCatalogVersionId`。旧批次仍指向旧目录，所以旧快照不受后来的类型状态变化影响。

Worker 不单独再选“最新目录”。它只跟随任务固定的快照，再读取该快照上的目录版本。因此重试和另一个 Worker 看到的类型资格与快照内容一致。

今天的目录仍是 38 个 `DRAFT` 和 1 个 `RETIRED`。在一份把目标类型标成 `ACTIVE` 的目录版本存在之前，业务映射发布继续全部拒绝。

## 12. 数据库详细设计

这些表仍依赖尚未接受的 MySQL 决策。不建物理外键。配置、目录、冲突决定和审计不自动删除。

```sql
CREATE TABLE source_contract (
  contract_id VARCHAR(64) NOT NULL,
  platform VARCHAR(32) NOT NULL,
  source_system VARCHAR(128) NOT NULL,
  source_interface VARCHAR(256) NOT NULL,
  source_message_type VARCHAR(128) NOT NULL DEFAULT '',
  source_type VARCHAR(32) NOT NULL,
  family_status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (contract_id),
  UNIQUE KEY uk_source_contract_identity (
    platform, source_system, source_interface, source_message_type, source_type),
  KEY idx_source_contract_platform (platform, source_type),
  CONSTRAINT ck_source_contract_type CHECK (source_type IN (
    'OFFICIAL_DOCUMENT', 'OFFICIAL_MESSAGE_PROTOCOL', 'CODE_INVESTIGATION',
    'BUSINESS_CONFIRMED', 'OTHER_EVIDENCE')),
  CONSTRAINT ck_source_contract_family CHECK (family_status IN ('OPEN', 'CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE source_contract_version (
  contract_version_id VARCHAR(64) NOT NULL,
  contract_id VARCHAR(64) NOT NULL,
  contract_version VARCHAR(128) NOT NULL,
  source_revision INT NOT NULL,
  evidence_status VARCHAR(16) NOT NULL,
  semantic_status VARCHAR(32) NOT NULL,
  source_reference VARCHAR(512) NOT NULL,
  contract_effective_from DATETIME(3) NULL,
  contract_effective_to DATETIME(3) NULL,
  payload_format VARCHAR(32) NULL,
  path_count INT NULL,
  publication_state VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
  material_sha256 VARCHAR(64) NULL,
  body_sha256 CHAR(64) NOT NULL,
  body_encoding VARCHAR(32) NOT NULL,
  row_version INT NOT NULL DEFAULT 1,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (contract_version_id),
  UNIQUE KEY uk_contract_source_revision (contract_id, source_revision),
  UNIQUE KEY uk_contract_body_sha256 (contract_id, body_sha256),
  UNIQUE KEY uk_contract_material_sha256 (contract_id, material_sha256),
  KEY idx_contract_version_state (contract_id, publication_state),
  CONSTRAINT ck_contract_revision_positive CHECK (source_revision > 0),
  CONSTRAINT ck_contract_version_state CHECK (publication_state IN ('DRAFT', 'SEALED')),
  CONSTRAINT ck_contract_version_evidence CHECK (evidence_status IN (
    'E1-A', 'E1-B', 'E2', 'E3', 'E4', 'E5', 'E6', 'NONE')),
  CONSTRAINT ck_contract_body_encoding CHECK (body_encoding IN (
    'contract-version-v2', 'contract-version-v3')),
  CONSTRAINT ck_contract_payload_shape CHECK (
    (body_encoding = 'contract-version-v2'
      AND payload_format IS NULL
      AND path_count IS NULL
      AND material_sha256 IS NULL)
    OR (body_encoding = 'contract-version-v3'
      AND payload_format IS NOT NULL
      AND payload_format = 'JSON'
      AND path_count IS NOT NULL
      AND path_count >= 0
      AND material_sha256 IS NOT NULL
      AND CHAR_LENGTH(material_sha256) = 64
      AND material_sha256 REGEXP '^[0-9a-f]{64}$'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE source_contract_amount_path (
  contract_version_id VARCHAR(64) NOT NULL,
  field_path VARCHAR(512) NOT NULL,
  PRIMARY KEY (contract_version_id, field_path)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE source_contract_evidence (
  evidence_id BIGINT NOT NULL AUTO_INCREMENT,
  contract_version_id VARCHAR(64) NOT NULL,
  evidence_status VARCHAR(16) NOT NULL,
  source_reference VARCHAR(512) NOT NULL,
  quoted_note VARCHAR(1024) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (evidence_id),
  KEY idx_contract_evidence_version (contract_version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE amount_mapping (
  mapping_id VARCHAR(64) NOT NULL,
  platform VARCHAR(32) NOT NULL,
  contract_id VARCHAR(64) NOT NULL,
  field_path VARCHAR(512) NOT NULL,
  candidate_amount_type_code VARCHAR(64) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (mapping_id),
  UNIQUE KEY uk_mapping_identity (contract_id, field_path, candidate_amount_type_code),
  KEY idx_mapping_platform (platform, contract_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE amount_mapping_version (
  mapping_version_id BIGINT NOT NULL AUTO_INCREMENT,
  mapping_id VARCHAR(64) NOT NULL,
  mapping_version VARCHAR(64) NOT NULL,
  contract_version_id VARCHAR(64) NOT NULL,
  platform VARCHAR(32) NOT NULL,
  field_path VARCHAR(512) NOT NULL,
  source_data_type VARCHAR(64) NOT NULL,
  candidate_amount_type_code VARCHAR(64) NOT NULL,
  candidate_disposition VARCHAR(32) NOT NULL DEFAULT '',
  mapping_eligibility VARCHAR(40) NOT NULL,
  semantic_status VARCHAR(32) NOT NULL,
  evidence_status VARCHAR(16) NOT NULL,
  unit_constraint VARCHAR(64) NOT NULL,
  currency_constraint VARCHAR(64) NOT NULL,
  precision_constraint VARCHAR(64) NOT NULL,
  transformation_policy VARCHAR(32) NOT NULL,
  aggregation_policy VARCHAR(40) NOT NULL,
  publication_state VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
  approval_status VARCHAR(16) NOT NULL DEFAULT 'NOT_SUBMITTED',
  author_operator_id VARCHAR(64) NOT NULL,
  submitted_by VARCHAR(64) NOT NULL DEFAULT '',
  reviewed_by VARCHAR(64) NOT NULL DEFAULT '',
  review_decision VARCHAR(16) NOT NULL DEFAULT '',
  body_sha256 CHAR(64) NOT NULL DEFAULT '',
  row_version INT NOT NULL DEFAULT 1,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (mapping_version_id),
  UNIQUE KEY uk_mapping_version_no (mapping_id, mapping_version),
  KEY idx_mapping_version_contract_field (contract_version_id, field_path, publication_state),
  CONSTRAINT ck_mapping_publication CHECK (publication_state IN ('DRAFT', 'SEALED')),
  CONSTRAINT ck_mapping_approval CHECK (approval_status IN (
    'NOT_SUBMITTED', 'IN_REVIEW', 'APPROVED', 'REJECTED')),
  CONSTRAINT ck_mapping_eligibility CHECK (mapping_eligibility IN (
    'ALLOWED_IF_CONTRACT_DEFINES', 'NOT_UNTIL_CONFIRMED', 'DISALLOWED',
    'ALLOWED_AS_UNIDENTIFIED', 'CONFLICT_ONLY')),
  CONSTRAINT ck_mapping_transform CHECK (transformation_policy IN ('NO_CONVERT')),
  CONSTRAINT ck_mapping_aggregation CHECK (aggregation_policy IN (
    'FORBIDDEN', 'SAME_TYPE_AFTER_CONFIRMATION', 'RULE_CONTROLLED', 'NOT_APPLICABLE')),
  CONSTRAINT ck_mapping_disposition CHECK (candidate_disposition IN (
    '', 'SINGLE_CANDIDATE', 'MULTI_CANDIDATE', 'HOLD_UNIDENTIFIED',
    'CONFLICTING_CANDIDATES', 'NO_AMOUNT', 'BLOCKED_BY_ELIGIBILITY')),
  CONSTRAINT ck_mapping_sealed_hash CHECK (
    publication_state <> 'SEALED' OR body_sha256 <> '')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE amount_mapping_evidence (
  evidence_id BIGINT NOT NULL AUTO_INCREMENT,
  mapping_version_id BIGINT NOT NULL,
  evidence_status VARCHAR(16) NOT NULL,
  source_reference VARCHAR(512) NOT NULL,
  quoted_note VARCHAR(1024) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (evidence_id),
  KEY idx_mapping_evidence_version (mapping_version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE mapping_conflict_decision (
  conflict_decision_id VARCHAR(64) NOT NULL,
  contract_version_id VARCHAR(64) NOT NULL,
  field_path VARCHAR(512) NOT NULL,
  conflict_type VARCHAR(64) NOT NULL,
  reason VARCHAR(1024) NOT NULL,
  approved_effective_from DATETIME(3) NOT NULL,
  approved_effective_to DATETIME(3) NULL,
  publication_state VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
  approval_status VARCHAR(16) NOT NULL DEFAULT 'NOT_SUBMITTED',
  author_operator_id VARCHAR(64) NOT NULL,
  submitted_by VARCHAR(64) NOT NULL DEFAULT '',
  reviewed_by VARCHAR(64) NOT NULL DEFAULT '',
  review_decision VARCHAR(16) NOT NULL DEFAULT '',
  body_sha256 CHAR(64) NOT NULL DEFAULT '',
  row_version INT NOT NULL DEFAULT 1,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (conflict_decision_id),
  KEY idx_conflict_field (contract_version_id, field_path, publication_state),
  CONSTRAINT ck_conflict_publication CHECK (publication_state IN ('DRAFT', 'SEALED')),
  CONSTRAINT ck_conflict_approval CHECK (approval_status IN (
    'NOT_SUBMITTED', 'IN_REVIEW', 'APPROVED', 'REJECTED')),
  CONSTRAINT ck_conflict_sealed_hash CHECK (
    publication_state <> 'SEALED' OR body_sha256 <> '')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE mapping_conflict_candidate (
  conflict_decision_id VARCHAR(64) NOT NULL,
  mapping_version_id BIGINT NOT NULL,
  candidate_body_sha256 CHAR(64) NOT NULL,
  PRIMARY KEY (conflict_decision_id, mapping_version_id),
  CONSTRAINT ck_candidate_hash_present CHECK (candidate_body_sha256 <> '')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE mapping_conflict_evidence (
  evidence_id BIGINT NOT NULL AUTO_INCREMENT,
  conflict_decision_id VARCHAR(64) NOT NULL,
  evidence_status VARCHAR(16) NOT NULL,
  source_reference VARCHAR(512) NOT NULL,
  quoted_note VARCHAR(1024) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (evidence_id),
  KEY idx_conflict_evidence (conflict_decision_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE amount_type_catalog_release (
  catalog_version_id VARCHAR(64) NOT NULL,
  item_count INT NOT NULL,
  content_sha256 CHAR(64) NOT NULL,
  sealed_at DATETIME(3) NOT NULL,
  PRIMARY KEY (catalog_version_id),
  UNIQUE KEY uk_type_catalog_sha256 (content_sha256),
  CONSTRAINT ck_type_catalog_count CHECK (item_count > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE amount_type_catalog_entry (
  catalog_version_id VARCHAR(64) NOT NULL,
  type_code VARCHAR(64) NOT NULL,
  type_lifecycle_status VARCHAR(16) NOT NULL,
  mapping_eligibility VARCHAR(40) NOT NULL,
  semantic_status VARCHAR(32) NOT NULL,
  aggregation_policy VARCHAR(40) NOT NULL,
  PRIMARY KEY (catalog_version_id, type_code),
  CONSTRAINT ck_type_catalog_life CHECK (type_lifecycle_status IN ('DRAFT', 'ACTIVE', 'RETIRED')),
  CONSTRAINT ck_type_catalog_eligibility CHECK (mapping_eligibility IN (
    'ALLOWED_IF_CONTRACT_DEFINES', 'NOT_UNTIL_CONFIRMED', 'DISALLOWED',
    'ALLOWED_AS_UNIDENTIFIED', 'CONFLICT_ONLY')),
  CONSTRAINT ck_type_catalog_semantic CHECK (semantic_status IN (
    '部分确认', '待平台确认', '待业务确认', '待财务确认', '存在冲突', '无法验证')),
  CONSTRAINT ck_type_catalog_aggregation CHECK (aggregation_policy IN (
    'FORBIDDEN', 'SAME_TYPE_AFTER_CONFIRMATION', 'RULE_CONTROLLED', 'NOT_APPLICABLE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE mapping_publish_batch (
  batch_id VARCHAR(64) NOT NULL,
  parent_batch_id VARCHAR(64) NOT NULL,
  batch_status VARCHAR(16) NOT NULL,
  type_catalog_version_id VARCHAR(64) NOT NULL,
  item_count INT NOT NULL,
  content_sha256 CHAR(64) NOT NULL,
  operator_id VARCHAR(64) NOT NULL,
  request_id VARCHAR(64) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (batch_id),
  UNIQUE KEY uk_publish_parent (parent_batch_id),
  UNIQUE KEY uk_publish_request (request_id),
  CONSTRAINT ck_publish_status CHECK (batch_status IN ('PUBLISHED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE mapping_publish_batch_contract_item (
  batch_id VARCHAR(64) NOT NULL,
  contract_version_id VARCHAR(64) NOT NULL,
  body_sha256 CHAR(64) NOT NULL,
  PRIMARY KEY (batch_id, contract_version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE mapping_publish_batch_mapping_item (
  batch_id VARCHAR(64) NOT NULL,
  mapping_version_id BIGINT NOT NULL,
  contract_version_id VARCHAR(64) NOT NULL,
  field_path VARCHAR(512) NOT NULL,
  lifecycle_in_snapshot VARCHAR(16) NOT NULL,
  mapping_effective_from DATETIME(3) NOT NULL,
  mapping_effective_to DATETIME(3) NULL,
  body_sha256 CHAR(64) NOT NULL,
  open_ended_claim VARCHAR(600) GENERATED ALWAYS AS (
    CASE WHEN mapping_effective_to IS NULL
      THEN CONCAT(contract_version_id, '#', field_path)
      ELSE NULL END
  ) STORED,
  PRIMARY KEY (batch_id, mapping_version_id),
  UNIQUE KEY uk_one_open_mapping (batch_id, open_ended_claim),
  KEY idx_mapping_item_field (batch_id, contract_version_id, field_path, mapping_effective_from),
  CONSTRAINT ck_mapping_item_life_window CHECK (
    (lifecycle_in_snapshot = 'ACTIVE' AND mapping_effective_to IS NULL)
    OR (lifecycle_in_snapshot = 'RETIRED' AND mapping_effective_to IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE mapping_publish_batch_conflict_item (
  batch_id VARCHAR(64) NOT NULL,
  conflict_decision_id VARCHAR(64) NOT NULL,
  contract_version_id VARCHAR(64) NOT NULL,
  field_path VARCHAR(512) NOT NULL,
  conflict_effective_from DATETIME(3) NOT NULL,
  conflict_effective_to DATETIME(3) NULL,
  body_sha256 CHAR(64) NOT NULL,
  open_ended_claim VARCHAR(600) GENERATED ALWAYS AS (
    CASE WHEN conflict_effective_to IS NULL
      THEN CONCAT(contract_version_id, '#', field_path)
      ELSE NULL END
  ) STORED,
  PRIMARY KEY (batch_id, conflict_decision_id),
  UNIQUE KEY uk_one_open_conflict (batch_id, open_ended_claim),
  KEY idx_conflict_item_field (batch_id, contract_version_id, field_path, conflict_effective_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE amount_mapping_audit_log (
  audit_id BIGINT NOT NULL AUTO_INCREMENT,
  aggregate_type VARCHAR(32) NOT NULL,
  aggregate_id VARCHAR(128) NOT NULL,
  event_type VARCHAR(32) NOT NULL,
  request_id VARCHAR(64) NOT NULL,
  operator_id VARCHAR(64) NOT NULL,
  actor_role VARCHAR(32) NOT NULL,
  from_status VARCHAR(32) NOT NULL DEFAULT '',
  to_status VARCHAR(32) NOT NULL DEFAULT '',
  reason VARCHAR(1024) NOT NULL DEFAULT '',
  publish_batch_id VARCHAR(64) NOT NULL DEFAULT '',
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (audit_id),
  KEY idx_audit_request (request_id),
  KEY idx_audit_aggregate (aggregate_type, aggregate_id, audit_id),
  KEY idx_audit_batch (publish_batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE amount_mapping_idempotency_request (
  request_id VARCHAR(64) NOT NULL,
  event_type VARCHAR(32) NOT NULL,
  aggregate_type VARCHAR(32) NOT NULL,
  aggregate_id VARCHAR(128) NOT NULL,
  request_hash CHAR(64) NOT NULL,
  processing_status VARCHAR(16) NOT NULL,
  response_snapshot_id VARCHAR(64) NOT NULL DEFAULT '',
  response_payload TEXT NOT NULL,
  created_at DATETIME(3) NOT NULL,
  completed_at DATETIME(3) NULL,
  PRIMARY KEY (request_id),
  CONSTRAINT ck_idempotency_status CHECK (processing_status IN ('PROCESSING', 'SUCCEEDED')),
  CONSTRAINT ck_idempotency_completed CHECK (
    processing_status <> 'SUCCEEDED' OR completed_at IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
```

映射条目的唯一键只限制“同一映射版本在一个快照里出现一次”和“同一字段最多一条没有右端点的映射”。它不再禁止同一字段的多段已闭合窗口。冲突条目同样只限制一条没有右端点的决定。跨表的窗口重叠、以及有右端点的窗口重叠，由发布事务检查。MySQL 没有区间排他约束，不能把这项检查伪装成一个简单的路径唯一索引。

`RETIRED` 必须有右端点。没有右端点的当前映射使用 `ACTIVE`。两者都按窗口参加匹配。

审计表只回答“谁在什么时候把哪个对象从哪个状态改到了哪个状态”。它可以按 `requestId` 查到同一次操作，但没有请求哈希，也没有当时的完整响应，所以不能用来重放结果。

`amount_mapping_idempotency_request` 只负责识别同一次提交并保存第一次的成功响应。它不记录审核理由和状态变化。两张表用 `requestId` 关联，不把响应正文复制进审计。已提交的请求行只有 `SUCCEEDED`。`PROCESSING` 只出现在尚未提交的事务里，事务回滚后不会留下。

创建冲突决定、审核冲突决定和发布冲突决定的聚合类型是 `CONFLICT_DECISION`，事件名分别是 `CREATE_CONFLICT_DECISION`、`REVIEW_CONFLICT` 和 `PUBLISH_CONFLICT`。

`payload_format`、`path_count` 和 `material_sha256` 是契约版本上的建议列。`ck_contract_payload_shape` 在插入和更新时检查这三列，不等待 Worker 读取。MySQL 只在 CHECK 结果为 FALSE 时拒绝行；结果为 UNKNOWN 时放行。`IN`、`>=`、`CHAR_LENGTH(...) = 64` 和 `REGEXP` 遇到 NULL 都是 UNKNOWN，所以 v3 分支对每一列先写 `IS NOT NULL`，再写取值条件。v2 分支只使用 `IS NULL`，结果是 TRUE 或 FALSE。

v2 行要求 `payload_format`、`path_count`、`material_sha256` 都为 NULL。三者有一个不是 NULL，v2 分支为 FALSE；编码也不是 v3，整条 CHECK 为 FALSE，插入或更新被拒绝。

v3 行要求 `payload_format` 不是 NULL 且等于 `JSON`，`path_count` 不是 NULL 且大于或等于 0，`material_sha256` 不是 NULL、字符长度是 64，并且每个字符都属于 `0-9` 或 `a-f`。`XML`、大写十六进制、空字符串、长度不是 64 的字符串，以及任一列为 NULL，都使 v3 分支为 FALSE。

`material_sha256` 使用 `VARCHAR(64)`。`CHAR(64)` 会用空格补足长度，短字符串的 `CHAR_LENGTH` 会变成 64，长度条件就会放行。`body_sha256` 仍是 `CHAR(64) NOT NULL`。本轮不改它的类型，也不改任何正文哈希字节。MySQL 唯一索引把 NULL 看成互不相同，所以多条 v2 行可以同时让 `material_sha256` 为空。同一 `contract_id` 下，一个非空材料指纹只对应一行。

两层校验的分工如下。应用层在写库前检查请求，并返回 `PAYLOAD_FORMAT_UNKNOWN`、`AMOUNT_PATH_DUPLICATE`、`AMOUNT_PATH_INVALID` 或 `REQUEST_INVALID`。数据库约束不产生这些业务错误码；它负责在绕过接口的写入上直接回滚。路径语法、排序、重复、路径行数是否等于 `path_count`，以及正文哈希是否重算一致，都依赖路径表或整份编码。MySQL 的 CHECK 不能写子查询，这些规则由创建事务在写入时执行，Worker 读取时再执行一次。创建事务失败就整笔回滚，不留下三个扩展字段合法、路径行却残缺的版本。Worker 仍拒绝损坏快照，但不代替写入时的约束。第 16 节列出这个 CHECK 的允许和拒绝场景。

`source_contract_amount_path` 保存 v3 路径列表的可查询投影。行数必须等于 `path_count`。这张表没有自己的哈希列。路径字节进入 `contract-version-v3` 和 `contract-material-v1`。版本封存后，不能再插入、修改或删除该版本的路径行。v2 版本一旦出现路径行，第 14 节把它定为 `CONTRACT_PATHS_CORRUPT`。

## 13. 管理 API 设计

查询用 GET，变更用 POST。下面的角色只是调用约定，不是已经实现的账号、权限或审批系统。服务当前能够强制的是：变更请求必须带 `operatorId`，审核人必须是另一名操作者。`actorRole` 写入审计，供以后的账号系统对照。

建议角色是 `EDITOR`、`REVIEWER`、`PUBLISHER`。没有 `operatorId` 的变更返回 `OPERATOR_REQUIRED`，不写任何行。

响应外层是建议形状，仓库里还没有这个类：

| 字段 | 含义 |
| --- | --- |
| `success` | `true` 或 `false` |
| `data` | 成功时的业务内容。失败时为空对象 |
| `errorCode` | 成功时为空字符串。失败时是下表中的代码 |

每个变更接口按第 10 节写入一条幂等请求和一条审计。`requestHash` 从本轮起按 `request-v3` 计算。`request-v2` 的字段表和测试向量保留，用来证明旧编码没有被改写。请求号相同且 `requestHash` 相同，返回第一次的完整 `data`。请求号相同但内容不同，返回 `REQUEST_REUSED`。对方事务尚未结束时返回 `REQUEST_IN_PROGRESS`。缺必填字段、列表为 null、整数为负或时间格式不对时返回 `REQUEST_INVALID`，不写请求行。查询不写库。失败回滚后不存在可重放的成功行。

发布类接口成功时，`data` 至少包含 `snapshotId`、`parentSnapshotId`、`contentSha256`、`itemCount` 和 `publishResult`。`publishResult` 固定为 `PUBLISHED`。

### request-v2 字段顺序

`requestHash` 是 `request-v2` 字节的 SHA-256，64 位小写十六进制。字节规则与第 14 节相同。编码时按下面的固定位置取值，JSON 把字段写成另一种先后顺序，哈希也不变。未知 JSON 字段不进入哈希。

每个变更请求都从这四项开始：

1. 字符串 `request-v2`。
2. 事件名。
3. 聚合类型。
4. 聚合 ID。

然后是该接口的路径参数，再是请求体字段。没有路径参数的接口不补空位。不同事件的字段个数可以不同。`requestId` 只在它被规定为聚合 ID 时进入第 4 项；它不另外再编码一次。

可空时间缺省和 JSON `null` 都编码为 NULL。两者是同一种业务请求。列表必须出现，不能为 null，也不能因为缺省就被改写成空列表。空列表的元素个数是 0。必填字符串、整数缺省或为 null 时返回 `REQUEST_INVALID`。空字符串只允许出现在 `sourceMessageType` 和 `candidateDisposition`。

整数按十进制解析后用 8 字节整数编码，不保留输入里的前导零。`rowVersion` 和 `mappingVersionId` 必须大于或等于 0。路径里的 `mappingVersion` 仍按原始字符串编码。需要 `mappingVersionId` 的聚合 ID，使用解析后的十进制文本，不带前导零。

证据对象的字段顺序是 `evidenceStatus`、`sourceReference`、`quotedNote`。候选对象只有 `mappingVersionId`。目录条目的顺序是 `typeCode`、`typeLifecycleStatus`、`mappingEligibility`、`semanticStatus`、`aggregationPolicy`。`ADD_MAPPING` 的顺序是 `changeType`、`mappingVersionId`、`effectiveFrom`、`effectiveTo`。`CLOSE_CONFLICT` 的顺序是 `changeType`、`conflictDecisionId`、`effectiveTo`。这些对象先按自己的字段顺序拼成字节，再取 SHA-256，列表里放类型 `06` 和这 32 字节。对象内部字段顺序固定，所以嵌套内容变化会改变外层哈希。

证据列表的排序键依次是 `evidenceStatus`、`sourceReference`、`quotedNote`，都按 UTF-8 字节升序。三个键都相同，两条证据的嵌套哈希也相同，谁先谁后都不改变列表字节。候选列表按 `mappingVersionId` 数值升序。同一个决定里重复的 `mappingVersionId` 返回 `REQUEST_INVALID`。目录条目按 `typeCode` 的 UTF-8 字节升序，重复的类型编码同样拒绝。

`changes` 的排序键依次是：

1. `changeType` 的 UTF-8 字节。
2. 目标 ID。`ADD_MAPPING` 用 `mappingVersionId` 的整数，`CLOSE_CONFLICT` 用 `conflictDecisionId` 的 UTF-8 字节。
3. `effectiveFrom`。`CLOSE_CONFLICT` 没有起点，这一键按 NULL。
4. `effectiveTo`。NULL 比任何具体时间都大。

四键都相同的变更，嵌套哈希相同，交换位置不改变字节。不追加数据库序号、JSON 下标或 Map 迭代顺序作为隐藏排序键。

| 事件名 | 聚合类型 | 聚合 ID | 路径参数 | 请求体字段 |
| --- | --- | --- | --- | --- |
| `CREATE_CONTRACT` | `SOURCE_CONTRACT` | `requestId` | 无 | `operatorId`、`actorRole`、`platform`、`sourceSystem`、`sourceInterface`、`sourceMessageType`、`sourceType` |
| `CREATE_CONTRACT_VERSION` | `SOURCE_CONTRACT` | 路径中的 `contractId` | `contractId` | `operatorId`、`actorRole`、`contractVersion`、`evidenceStatus`、`semanticStatus`、`sourceReference`、`contractEffectiveFrom`、`contractEffectiveTo`、`evidences` |
| `CREATE_MAPPING` | `AMOUNT_MAPPING` | `requestId` | 无 | `operatorId`、`actorRole`、`platform`、`contractId`、`contractVersionId`、`fieldPath`、`sourceDataType`、`candidateAmountTypeCode`、`candidateDisposition`、`mappingEligibility`、`semanticStatus`、`evidenceStatus`、`unitConstraint`、`currencyConstraint`、`precisionConstraint`、`transformationPolicy`、`aggregationPolicy`、`evidences` |
| `EDIT_DRAFT` | `MAPPING_VERSION` | 解析后的 `mappingVersionId` | `mappingId`、`mappingVersion` | `operatorId`、`actorRole`、`rowVersion`、`sourceDataType`、`candidateDisposition`、`mappingEligibility`、`semanticStatus`、`evidenceStatus`、`unitConstraint`、`currencyConstraint`、`precisionConstraint`、`transformationPolicy`、`aggregationPolicy`、`evidences` |
| `SUBMIT_REVIEW` | `MAPPING_VERSION` | 解析后的 `mappingVersionId` | `mappingId`、`mappingVersion` | `operatorId`、`actorRole`、`rowVersion` |
| `APPROVE` 或 `REJECT` | `MAPPING_VERSION` | 解析后的 `mappingVersionId` | `mappingId`、`mappingVersion` | `operatorId`、`actorRole`、`rowVersion`、`reviewDecision`。`reviewDecision` 为 `APPROVED` 时事件名是 `APPROVE`，为 `REJECTED` 时是 `REJECT` |
| `CREATE_CONFLICT_DECISION` | `CONFLICT_DECISION` | `requestId` | 无 | `operatorId`、`actorRole`、`contractVersionId`、`fieldPath`、`conflictType`、`reason`、`approvedEffectiveFrom`、`approvedEffectiveTo`、`candidates`、`evidences` |
| `REVIEW_CONFLICT` | `CONFLICT_DECISION` | 路径中的 `conflictDecisionId` | `conflictDecisionId` | `operatorId`、`actorRole`、`rowVersion`、`reviewDecision` |
| `SEAL_CATALOG` | `TYPE_CATALOG` | `requestId` | 无 | `operatorId`、`actorRole`、`entries` |
| `PUBLISH` | `MAPPING_SNAPSHOT` | `parentSnapshotId` | 无 | `operatorId`、`actorRole`、`parentSnapshotId`、`typeCatalogVersionId`、`changes` |
| `PUBLISH_CONFLICT` | `CONFLICT_DECISION` | `conflictDecisionId` | 无 | `operatorId`、`actorRole`、`parentSnapshotId`、`typeCatalogVersionId`、`conflictDecisionId` |
| `RETIRE` | `MAPPING_VERSION` | `mappingVersionId` 的十进制文本 | 无 | `operatorId`、`actorRole`、`parentSnapshotId`、`mappingVersionId`、`effectiveTo` |
| `ROLLBACK` | `MAPPING_SNAPSHOT` | `targetSnapshotId` | 无 | `operatorId`、`actorRole`、`parentSnapshotId`、`targetSnapshotId` |

校验接口和全部 GET 不计算 `requestHash`。编辑草稿仍不能修改字段路径、目标类型和契约版本，所以这些字段不在 `EDIT_DRAFT` 的请求体里。

上表是 `request-v2` 的字段顺序，第一项版本名是字符串 `request-v2`。新的变更请求使用 `request-v3`：版本名改为 `request-v3`，其余事件的字段顺序与上表相同。只有 `CREATE_CONTRACT_VERSION` 在两个生效时间之后、`evidences` 之前增加字符串 `payloadFormat`，并在 `evidences` 之后增加 `amountPaths`。路径按 UTF-8 字节升序，元素是字符串，不是嵌套哈希。重复路径返回 `AMOUNT_PATH_DUPLICATE`。路径不符合第 14 节的语法时返回 `AMOUNT_PATH_INVALID`。`payloadFormat` 缺省、为空或为 null 时返回 `REQUEST_INVALID`。值不是 `JSON` 时返回 `PAYLOAD_FORMAT_UNKNOWN`。空路径列表表示这份契约版本声明没有金额路径。省略 `amountPaths` 返回 `REQUEST_INVALID`，调用方必须显式传列表。

因此，第六轮记录的 `request-v2` 测试向量仍然只属于 `request-v2`。不能把那些字节改名为 `request-v3` 后继续使用原来的哈希。第七轮的 `request-v3` 示例还没有 `payloadFormat`，那组字节和哈希已在第八轮废止，不再作为当前请求哈希。

### 创建契约

`POST /api/admin/source-contracts`

请求：`requestId`、`operatorId`、`actorRole`、`platform`、`sourceSystem`、`sourceInterface`、`sourceMessageType`、`sourceType`。没有消息类型时传空字符串。

成功：`contractId`。

错误：`OPERATOR_REQUIRED`、`CONTRACT_DUPLICATE`、`SOURCE_TYPE_UNKNOWN`。

事件：`CREATE_CONTRACT`。聚合是 `SOURCE_CONTRACT`，聚合 ID 使用 `requestId`。

事务：插入契约家族和一条审计。身份重复时回滚。

### 查询契约

`GET /api/admin/source-contracts`

请求参数：`platform`、`sourceType`，都可以空，表示不按该项过滤。

成功：契约家族列表。每项含 `contractId`、身份五段和 `familyStatus`。

错误：无写入错误。参数无法识别时返回 `QUERY_INVALID`。

幂等：查询不生成键，重复查询不改变数据。

事务：只读。

### 创建契约版本

`POST /api/admin/source-contracts/{contractId}/versions`

请求：`requestId`、`operatorId`、`actorRole`、`contractVersion`、`evidenceStatus`、`semanticStatus`、`sourceReference`、`contractEffectiveFrom`、`contractEffectiveTo`、`payloadFormat`、`evidences`、`amountPaths`。官方没有版本号时，`contractVersion` 传“未提供”。`payloadFormat` 当前只能是 `JSON`。`amountPaths` 必须显式给出，可以是空列表。

成功：`contractVersionId`、`sourceRevision`、`bodySha256`、`materialSha256`、`result`。`result` 为 `CREATED` 或 `EXISTING`。材料指纹已存在时返回原版本，不新增修订。

错误：`OPERATOR_REQUIRED`、`CONTRACT_NOT_FOUND`、`VERSION_SEALED`、`EVIDENCE_INVALID`、`PAYLOAD_FORMAT_UNKNOWN`、`AMOUNT_PATH_DUPLICATE`、`AMOUNT_PATH_INVALID`。

事件：`CREATE_CONTRACT_VERSION`。聚合是 `SOURCE_CONTRACT`，聚合 ID 是 `contractId`。

事务：按第 14 节先计算 `contract-material-v1`。锁定契约家族行之后，材料指纹已存在则只写审计，并返回原来的 `contractVersionId`、`sourceRevision` 和 `bodySha256`。否则分配下一号 `sourceRevision` 和新的 `contractVersionId`，写入证据并取得 `evidenceId`，再按 `contract-version-v3` 计算 `bodySha256`。然后插入版本行、金额路径和审计。`body_encoding` 写成 `contract-version-v3`，`path_count` 写成路径个数。已封存版本不在这个接口改正文、`payloadFormat` 或路径。新请求不再创建 `contract-version-v2`。

### 创建映射草稿

`POST /api/admin/amount-mappings`

请求：`requestId`、`operatorId`、`actorRole`、`platform`、`contractId`、`contractVersionId`、`fieldPath`、`sourceDataType`、`candidateAmountTypeCode`、`candidateDisposition`、`mappingEligibility`、`semanticStatus`、`evidenceStatus`、`unitConstraint`、`currencyConstraint`、`precisionConstraint`、`transformationPolicy`、`aggregationPolicy`、`evidences`。

成功：`mappingId`、`mappingVersionId`、`mappingVersion`、`rowVersion`。

错误：`OPERATOR_REQUIRED`、`CONTRACT_VERSION_NOT_FOUND`、`TYPE_UNKNOWN`、`MAPPING_DUPLICATE`。

事件：`CREATE_MAPPING`。聚合是 `AMOUNT_MAPPING`，聚合 ID 使用 `requestId`。

事务：按身份查找或创建 `mappingId`，再插入一条 `DRAFT` 版本和证据。作者写 `operatorId`。这个接口不发布，也不封存。

### 编辑映射草稿

`POST /api/admin/amount-mappings/{mappingId}/versions/{mappingVersion}/edit`

请求：`requestId`、`operatorId`、`actorRole`、`rowVersion`，以及允许修改的正文字段和证据全量。路径、目标类型和契约版本不在这里修改；那些变化要新建 `mappingId`。

成功：新的 `rowVersion`。

错误：`OPERATOR_REQUIRED`、`MAPPING_NOT_FOUND`、`VERSION_SEALED`、`VERSION_CONFLICT`。

事件：`EDIT_DRAFT`。聚合是 `MAPPING_VERSION`，聚合 ID 是 `mappingVersionId`。

事务：只更新 `publicationState = DRAFT` 且 `rowVersion` 相符的一行，并替换该版本的证据。封存行匹配不到，回滚。冲突候选被发布封存后，这个接口拒绝继续修改。

### 校验映射

`POST /api/admin/amount-mappings/{mappingId}/versions/{mappingVersion}/validate`

请求：`operatorId`、`typeCatalogVersionId`。不需要 `requestId`，因为不写配置。

成功：`valid`，以及 `blockingErrors`。阻断项使用本节的错误码。

错误：`MAPPING_NOT_FOUND`、`CATALOG_NOT_FOUND`。

幂等：重复调用不写版本、快照或审计。

事务：只读。它不代替发布事务，发布时仍要再检查一次。

### 提交审核

`POST /api/admin/amount-mappings/{mappingId}/versions/{mappingVersion}/submit-review`

请求：`requestId`、`operatorId`、`actorRole`、`rowVersion`。

成功：`approvalStatus = IN_REVIEW`、新的 `rowVersion`。

错误：`OPERATOR_REQUIRED`、`VERSION_SEALED`、`VERSION_CONFLICT`、`APPROVAL_STATE_INVALID`。

事件：`SUBMIT_REVIEW`。聚合是 `MAPPING_VERSION`，聚合 ID 是 `mappingVersionId`。

事务：把 `NOT_SUBMITTED` 改为 `IN_REVIEW`，写入 `submittedBy`。其他状态回滚。

### 审核映射

`POST /api/admin/amount-mappings/{mappingId}/versions/{mappingVersion}/review`

请求：`requestId`、`operatorId`、`actorRole`、`rowVersion`、`reviewDecision`。`reviewDecision` 只能是 `APPROVED` 或 `REJECTED`。

成功：`approvalStatus`、`reviewedBy`、新的 `rowVersion`。

错误：`OPERATOR_REQUIRED`、`REVIEWER_IS_AUTHOR`、`VERSION_SEALED`、`VERSION_CONFLICT`、`APPROVAL_STATE_INVALID`。

事件：决定为通过时是 `APPROVE`，否则是 `REJECT`。聚合是 `MAPPING_VERSION`，聚合 ID 是 `mappingVersionId`。

事务：审核人必须不同于 `authorOperatorId`。只接受当前状态 `IN_REVIEW`。通过不表示已经发布，也不把类型改成 `ACTIVE`。

### 创建冲突决定

`POST /api/admin/mapping-conflict-decisions`

请求：`requestId`、`operatorId`、`actorRole`、`contractVersionId`、`fieldPath`、`conflictType`、`reason`、`approvedEffectiveFrom`、`approvedEffectiveTo`、`candidates`、`evidences`。每个候选只传 `mappingVersionId`。服务读取该版本当前正文并计算哈希，调用方不传哈希。

成功：`conflictDecisionId`、每个候选的 `mappingVersionId` 和 `candidateBodySha256`。

错误：`OPERATOR_REQUIRED`、`CANDIDATE_VERSION_UNKNOWN`、`CANDIDATE_TOO_FEW`、`CONTRACT_VERSION_MISMATCH`。候选少于两个，或候选不属于这个契约版本和字段时回滚。

事件：`CREATE_CONFLICT_DECISION`。聚合是 `CONFLICT_DECISION`，聚合 ID 使用 `requestId`。

事务：插入决定头、候选行和证据行。此时还不封存候选版本。决定创建后不提供修改接口。

### 审核冲突决定

`POST /api/admin/mapping-conflict-decisions/{conflictDecisionId}/review`

请求：`requestId`、`operatorId`、`actorRole`、`rowVersion`、`reviewDecision`。

成功：`approvalStatus`、`reviewedBy`。

错误：`OPERATOR_REQUIRED`、`REVIEWER_IS_AUTHOR`、`DECISION_NOT_FOUND`、`VERSION_SEALED`、`VERSION_CONFLICT`、`APPROVAL_STATE_INVALID`。已经通过或驳回的决定不能再次审核。

事件：`REVIEW_CONFLICT`。聚合是 `CONFLICT_DECISION`，聚合 ID 是 `conflictDecisionId`。

事务：只接受当前状态 `NOT_SUBMITTED`。审核人不是作者时，把决定写成通过或驳回。其他状态返回 `APPROVAL_STATE_INVALID`。这一步不封存候选，也不发布快照。驳回后的决定不能进入快照。

### 封存类型目录

`POST /api/admin/amount-type-catalogs`

请求：`requestId`、`operatorId`、`actorRole`、`entries`。每项含 `typeCode`、`typeLifecycleStatus`、`mappingEligibility`、`semanticStatus`、`aggregationPolicy`。

成功：`catalogVersionId`、`itemCount`、`contentSha256`、`result`。内容已存在时 `result = EXISTING`。

错误：`OPERATOR_REQUIRED`、`CATALOG_INCOMPLETE`、`CATALOG_DUPLICATE`。条目不是 06-03-02-01 的 39 个编码时返回 `CATALOG_INCOMPLETE`。

事件：`SEAL_CATALOG`。聚合是 `TYPE_CATALOG`，聚合 ID 使用 `requestId`。

事务：按第 14 节计算目录哈希。哈希已存在则返回原版本。否则一次插入目录头、39 条条目和审计。插入后不提供修改接口。

### 发布快照

`POST /api/admin/mapping-snapshots/publish`

请求：`requestId`、`operatorId`、`actorRole`、`parentSnapshotId`、`typeCatalogVersionId`、`changes`。第一个快照的父 ID 是 `ROOT`。

`changes` 的每一项是两种之一：

| `changeType` | 内容 | 作用 |
| --- | --- | --- |
| `ADD_MAPPING` | `mappingVersionId`、`effectiveFrom`、`effectiveTo` | 在新快照增加一条映射窗口 |
| `CLOSE_CONFLICT` | `conflictDecisionId`、`effectiveTo` | 只在新快照里把冲突窗口的右端点提前 |

成功：`snapshotId`、`parentSnapshotId`、`contentSha256`、`itemCount`、`publishResult`。

错误：`OPERATOR_REQUIRED`、`PARENT_SNAPSHOT_NOT_FOUND`、`PUBLISH_PARENT_CONFLICT`、`CATALOG_NOT_FOUND`、`TYPE_NOT_ACTIVE`、`ELIGIBILITY_BLOCKED`、`SEMANTIC_NOT_CONFIRMED`、`EVIDENCE_INSUFFICIENT`、`MAPPING_NOT_APPROVED`、`WINDOW_OVERLAP`、`VERSION_SEALED`。

事件：`PUBLISH`。聚合是 `MAPPING_SNAPSHOT`，聚合 ID 是 `parentSnapshotId`。

事务：执行第 10 节的全部步骤。新快照复制父快照的历史窗口。失败不留批次。

### 发布冲突决定

`POST /api/admin/mapping-snapshots/publish-conflict`

请求：`requestId`、`operatorId`、`actorRole`、`parentSnapshotId`、`typeCatalogVersionId`、`conflictDecisionId`。窗口以决定正文为准，请求里不能另传窗口。

成功：`snapshotId`、`parentSnapshotId`、`contentSha256`、`itemCount`、`publishResult`。

错误：`OPERATOR_REQUIRED`、`CONFLICT_NOT_APPROVED`、`CANDIDATE_BODY_CHANGED`、`CONTRACT_VERSION_MISSING`、`CONTRACT_VERSION_HASH_MISMATCH`、`WINDOW_OVERLAP`、`PUBLISH_PARENT_CONFLICT`、`PARENT_SNAPSHOT_NOT_FOUND`、`REQUEST_REUSED`、`REQUEST_IN_PROGRESS`。

事件：`PUBLISH_CONFLICT`。聚合是 `CONFLICT_DECISION`，聚合 ID 是 `conflictDecisionId`。

事务：执行第 10 节。候选映射引用的契约版本要封存，并按 `contractVersionId` 去重后写入新快照。只封存而不写入条目的发布视为失败。

### 退役

`POST /api/admin/mapping-snapshots/retire`

请求：`requestId`、`operatorId`、`actorRole`、`parentSnapshotId`、`mappingVersionId`、`effectiveTo`。

成功：`snapshotId`、`parentSnapshotId`、`contentSha256`、`itemCount`、`publishResult`。

错误：`OPERATOR_REQUIRED`、`RULE_NOT_IN_PARENT_SNAPSHOT`、`WINDOW_OVERLAP`、`PUBLISH_PARENT_CONFLICT`。

事件：`RETIRE`。聚合是 `MAPPING_VERSION`，聚合 ID 是 `mappingVersionId`。

事务：父快照中该版本必须是没有右端点的 `ACTIVE`。新快照复制全部历史条目，只把这一条补上右端点并改为 `RETIRED`。`effectiveTo` 必须晚于起点，并且不穿过下一段窗口。

### 回滚

`POST /api/admin/mapping-snapshots/rollback`

请求：`requestId`、`operatorId`、`actorRole`、`parentSnapshotId`、`targetSnapshotId`。不接收新的目录版本。新快照使用目标快照原来的 `typeCatalogVersionId`。

成功：`snapshotId`、`parentSnapshotId`、`contentSha256`、`itemCount`、`publishResult`。这里的 `snapshotId` 是新批次，不是目标批次。

错误：`OPERATOR_REQUIRED`、`TARGET_SNAPSHOT_NOT_PUBLISHED`、`PUBLISH_PARENT_CONFLICT`。

事件：`ROLLBACK`。聚合是 `MAPPING_SNAPSHOT`，聚合 ID 是 `targetSnapshotId`。

事务：把目标快照的三类条目复制到新批次。目标快照和中间快照都不修改，已封存版本也不重新打开。

### 查询快照

`GET /api/admin/mapping-snapshots/{snapshotId}`

请求：路径中的 `snapshotId`。

成功：第 14 节的 `MappingSnapshot`。任何部分缺失或哈希不一致时成功标志为假，错误码是 `SNAPSHOT_INTEGRITY_FAILED`。

错误：`SNAPSHOT_NOT_FOUND`、`SNAPSHOT_INTEGRITY_FAILED`。

幂等：只读。重复查询不修复数据。

事务：只读。Worker 不调用这个 HTTP 接口，但读取端口返回同一个对象。

### 查询历史

`GET /api/admin/mapping-snapshots/{snapshotId}/history`

请求：路径中的 `snapshotId`。

成功：从该快照沿 `parentSnapshotId` 走到 `ROOT` 的列表。每项含 `snapshotId`、`parentSnapshotId`、`typeCatalogVersionId`、`contentSha256`、`itemCount`、`createdAt`。

错误：`SNAPSHOT_NOT_FOUND`。父 ID 为 `ROOT` 时停止，不把 `ROOT` 当成缺失批次。链上其他父批次缺失时返回 `SNAPSHOT_INTEGRITY_FAILED`，不用最新快照补齐。

幂等：只读。

事务：只读。

### 审计查询

`GET /api/admin/amount-mappings/audit`

请求参数：`aggregateType`、`aggregateId`、`requestId`。至少提供 `requestId` 或成对提供聚合类型和聚合 ID。

成功：审计行列表，含操作者、角色、来源状态、目标状态、原因、`publishBatchId` 和 `createdAt`。

错误：`QUERY_INVALID`。

幂等：只读。

事务：只读。

发布门槛不变。类型在所引用目录里必须是 `ACTIVE`。资格不能是 `NOT_UNTIL_CONFIRMED`、`DISALLOWED` 或 `CONFLICT_ONLY`。业务发布的语义状态必须是 `部分确认`，并且没有未排除的第二种解释。`ALLOWED_AS_UNIDENTIFIED` 只用于 `AMT.UNK.UNIDENTIFIED`，保存后的执行状态仍是 `UNMAPPED`。单位、币种或精度未知时只能保持 `NO_CONVERT`，不能补成元、分或人民币。只靠 E4、E5、E6 不能发布。

## 14. Worker 快照读取、校验与匹配

`MappingSnapshot` 是 Worker 和查询接口共用的读取对象。它不是新的业务聚合，也不对应新的总表。

| 部分 | 来源 | 缺少时 |
| --- | --- | --- |
| `publishBatch` | `mapping_publish_batch` | 拒绝执行 |
| `contractItems` | 契约条目 | 数量对不上就拒绝 |
| `mappingItems` | 映射条目 | 数量对不上就拒绝 |
| `conflictItems` | 冲突条目 | 数量对不上就拒绝 |
| `conflictDecisions` | 冲突条目引用的决定头 | 读不到或未封存就拒绝 |
| `conflictCandidates` | 这些决定的候选行 | 缺少候选或哈希为空就拒绝 |
| `conflictEvidence` | 这些决定的证据行 | 重算决定哈希失败就拒绝 |
| `contractVersions` | 条目和候选引用的契约版本及证据 | 未封存或正文哈希不符就拒绝 |
| `mappingVersions` | 映射条目和候选引用的映射版本及证据 | 未封存或正文哈希不符就拒绝 |
| `typeCatalog` | 批次指向的目录头 | 读不到就拒绝 |
| `typeCatalogEntries` | 该目录的全部条目 | 数量或哈希不符就拒绝 |

`itemCount` 只等于契约条目、映射条目、冲突条目三项之和。候选、证据、版本正文和目录条目都不重复计入。

### 规范编码

所有哈希都是规范字节的 SHA-256，结果写成 64 位小写十六进制。字符串使用 UTF-8，不带 BOM，也不做 Unicode 归一化，按库存原值取字节，不另做 trim。多字节整数用大端序。

每个值都以一个类型字节开头，长度只出现在需要变长的类型上。因此字段内容里出现任何分隔符，都只是字符串长度内部的字节，不会被看成下一个字段。

| 类型 | 类型字节 | 其后的字节 | 含义 |
| --- | --- | --- | --- |
| NULL | `00` | 无 | 没有值。没有右端点的时间也用它 |
| 字符串 | `01` | 4 字节长度，再加相应 UTF-8 字节 | 长度 0 是空字符串，和 NULL 不同 |
| 整数 | `02` | 8 字节有符号整数 | 不用十进制文本，没有前导零问题 |
| 时间 | `03` | 8 字节有符号整数 | UTC 的 Unix 毫秒。展示格式不进入哈希 |
| 布尔 | `04` | 1 字节，`00` 为假，`01` 为真 | 当前业务字段没有布尔值，编码先保留 |
| 列表 | `05` | 4 字节元素个数，再连接每个元素自己的编码 | 个数是元素数，不是字节数 |
| 嵌套哈希 | `06` | 32 字节 SHA-256 | 下层对象先按自己的版本名编码并哈希，上层只放这 32 字节 |

字符串长度和列表元素个数都是 4 字节无符号整数，大端序。格式能表示的范围是 0 到 4294967295。本设计拒绝 UTF-8 字节长度超过 65535 的字符串，也拒绝元素超过 10000 的列表，直接返回 `REQUEST_INVALID`。空字符串的长度是 0，字节是 `0100000000`。空列表的元素个数是 0，字节是 `0500000000`。NULL 只有 `00`，它既不是空字符串，也不是空列表。

8 字节整数是大端序的二进制补码。能表示的范围是 -9223372036854775808 到 9223372036854775807。请求里的 `rowVersion` 和 `mappingVersionId` 不能为负。时间使用同一个 8 字节整数，单位是 UTC 的 Unix 毫秒。可接受的文本只有 `yyyy-MM-dd'T'HH:mm:ss.SSS'Z'`，毫秒必须是三位，后缀只能是 `Z`。按公历把这个 UTC 时间换成毫秒，不接受其他时区偏移。业务时间的范围是 `1970-01-01T00:00:00.000Z` 到 `9999-12-31T23:59:59.999Z`。没有右端点仍编码为 NULL，不编码成一个很远的时间。

列表元素是对象时，先连接该对象的固定字段，计算 SHA-256，再写入类型 `06` 和 32 字节。列表元素是整数时，直接写类型 `02` 和 8 字节。两种元素都自带类型，不能靠分隔符区分。

对象本身不另设类型。一个对象就是按该版本规定顺序排列的值。版本名是对象的第一个字符串。字段名不重复写入，缺少的可空字段必须写成 NULL，不能省略，否则后面的字段会错位。

正文里的集合在编码前排序。证据按 `evidenceId` 数值升序。候选按 `mappingVersionId` 升序。目录条目按 `typeCode` 的 UTF-8 字节升序。契约条目按 `contractVersionId` 升序。映射条目按 `mappingVersionId` 升序。冲突条目按 `conflictDecisionId` 升序。请求里的证据和 `changes` 使用第 13 节的排序键。排序键覆盖元素的全部业务字段。键全部相同的元素，编码字节也相同，因此不再用数据库返回顺序、JSON 属性顺序或 Map 迭代顺序打破平局。排序后才写列表长度。

下面五个值的十六进制都是完整编码：

| 值 | 字节 |
| --- | --- |
| NULL | `00` |
| 空字符串 | `0100000000` |
| 字符串，三个字符依次是 `a`、竖线、`b` | `0100000003617c62` |
| 字符串中间含字节 `1F` | `0100000003611f62` |
| 时间 `2026-01-02T03:04:05.006Z` | `030000019b7ca98c8e` |

空字符串的长度是 0。NULL 没有长度字节。竖线和 `1F` 都落在长度 `00000003` 的内部，解码时不会把它们当成字段边界。

下层对象先计算 SHA-256。上层对应位置使用类型 `06` 和这 32 字节。下层正文变化时，下层哈希变化，上层哈希也变化。上层不复制下层正文。

契约版本 `contract-version-v2` 的值顺序保持不变：版本名、`contractVersionId`、`contractId`、`contractVersion`、`sourceRevision`、`evidenceStatus`、`semanticStatus`、`sourceReference`、`contractEffectiveFrom`、`contractEffectiveTo`、证据列表。每条证据的顺序是 `evidenceId`、`evidenceStatus`、`sourceReference`、`quotedNote`。封存状态、行版本、时间戳和 `body_encoding` 都不进入正文哈希。

`contract-version-v3` 的值顺序是：版本名 `contract-version-v3`、`contractVersionId`、`contractId`、`contractVersion`、`sourceRevision`、`evidenceStatus`、`semanticStatus`、`sourceReference`、`contractEffectiveFrom`、`contractEffectiveTo`、`payloadFormat`、证据列表、金额路径列表。证据元素的顺序仍是 `evidenceId`、`evidenceStatus`、`sourceReference`、`quotedNote`。路径列表按 `fieldPath` 的 UTF-8 字节升序，每个元素是一个字符串。封存状态、行版本、时间戳、`body_encoding`、`path_count` 和 `material_sha256` 都不进入正文哈希。`path_count` 必须等于路径列表的元素个数；它保存这份个数，不在哈希里再占一个字段。

`payloadFormat` 是这份正文里的字符串，必须参加哈希。当前枚举只有 `JSON`，表示整份报文按 JSON 文本读取。要增加格式，先修订本设计，写明该格式怎样定位节点，再把新字符串加入枚举和表约束。编码名保持 `contract-version-v3`。新字符串改变字段内容，`materialSha256` 和 `bodySha256` 都会变；字段顺序不变，不为此新建 v4。未登记的字符串、空字符串、NULL，以及大小写不同的 `json`，都不能写成 v3 行。v2 行没有这个字段，列值保持空。空列表示 v2 编码不含格式，不把该行当成 `JSON`。

已经按 v2 算出的哈希不能改用 v3 重算。第七轮示例里的 v3 字节还没有 `payloadFormat`，已在第八轮废止，不作为当前 v3 定义。废止的正文哈希是 `8fcf8e33c4782176e79fee30c329a22f9180f5890b03507fea4b028e7df317ee` 和 `6fee099e591e9c029097fb16e3d14f5e350dd1f9e1316d5708b96e71240a85ec`。废止的创建请求哈希是 `af14c43c4d110c55fc2ec34470af446e7754cdc243725510402b8e02f0d0b9b5` 和 `21cb8dc0cd33fe2a5a4e54fec335701e14dd6b800f9169002abe542eb4804268`。这些值没有写入数据库。快照条目里保存的是 64 位哈希文本。`snapshot-v2` 的字段顺序不变，因为它只携带这个文本，不展开契约正文。新快照若引用 v3 契约，条目中的 `bodySha256` 就是包含 `payloadFormat` 和路径列表的 v3 哈希。旧快照若引用 v2 契约，仍按 v2 校验那一行。v2 行没有路径定义，校验不把它补成空清单。

映射版本 `mapping-version-v2` 的值顺序：版本名、`mappingVersionId`、`mappingId`、`mappingVersion`、`contractVersionId`、`platform`、`fieldPath`、`sourceDataType`、`candidateAmountTypeCode`、`candidateDisposition`、`mappingEligibility`、`semanticStatus`、`evidenceStatus`、`unitConstraint`、`currencyConstraint`、`precisionConstraint`、`transformationPolicy`、`aggregationPolicy`、证据列表。审核状态不进入正文哈希。封存后审核状态也不允许再改。

冲突决定 `conflict-decision-v2` 的值顺序：版本名、`conflictDecisionId`、`contractVersionId`、`fieldPath`、`conflictType`、`reason`、`approvedEffectiveFrom`、`approvedEffectiveTo`、`authorOperatorId`、`reviewedBy`、`reviewDecision`、候选列表、证据列表。每条候选是 `mappingVersionId`、`candidateBodySha256`。

类型目录 `type-catalog-v2` 的值顺序：版本名、`itemCount`、条目列表。每条是 `typeCode`、`typeLifecycleStatus`、`mappingEligibility`、`semanticStatus`、`aggregationPolicy`。

快照 `snapshot-v2` 的值顺序：版本名、`typeCatalogVersionId`、目录哈希、契约条目列表、映射条目列表、冲突条目列表。契约条目是 `contractVersionId`、`bodySha256`。映射条目是 `mappingVersionId`、`contractVersionId`、`fieldPath`、`lifecycleInSnapshot`、`mappingEffectiveFrom`、`mappingEffectiveTo`、`bodySha256`。冲突条目是 `conflictDecisionId`、`contractVersionId`、`fieldPath`、`conflictEffectiveFrom`、`conflictEffectiveTo`、`bodySha256`。候选契约版本补进契约条目后，才会影响这个哈希。

请求 `request-v2` 使用同一套类型编码。它只用于幂等请求的 `requestHash`，不替代上面五种正文哈希。`contract-material-v1` 也使用这套类型编码。它只负责判断材料是不是同一份，不写入 `body_encoding`，也不代替 `bodySha256`。

### 路径列表

契约中的一条路径是 UTF-8 字符串。编码时不做 Unicode 归一化，也不去掉首尾空白。字节长度是 1 到 512，中间不能有字节 `00`。

按 `.` 分成一段或多段，段不能为空。每一段符合 `^[A-Za-z_][A-Za-z0-9_]*(\[\])?$`。`[]` 是重复标记，只放在段名后面。具体下标由提取时按报文展开，不写入契约路径。

因此 `total`、`shipping_fee`、`wmPoiReceiveCent` 和 `extras[].reduce_fee` 符合语法。`extras[0].reduce_fee`、空字符串、`.total` 和 `extras.` 不符合语法，创建请求返回 `AMOUNT_PATH_INVALID`。

四项检查在哈希之前完成：

1. 数量。`path_count`、路径行数、v3 路径列表的元素个数必须相等。任一不同，分类为 `CONTRACT_PATHS_CORRUPT`。已经读到的较少行不进入提取。
2. 排序。全部路径按 UTF-8 无符号字节升序。`shipping_fee` 在 `total` 之前，因为首字节 `73` 小于 `74`。创建和校验都先排序再编码。数据库返回顺序不进入哈希。用未排序字节算出的哈希不能保存，也不能拿来通过校验。
3. 内容。每一条都要符合上面的语法。不符合时分类为 `CONTRACT_PATH_INTEGRITY_FAILED`。
4. 重复。UTF-8 字节完全相同的路径只保留一条。请求返回 `AMOUNT_PATH_DUPLICATE`。库内仍出现重复时，分类为 `CONTRACT_PATH_INTEGRITY_FAILED`。

路径表与正文哈希的关系：路径列表是 v3 正文的最后一项。路径表不另存哈希。校验时把路径行按字节序排好，放回整份 v3 正文，重算结果必须等于 `bodySha256`。只改路径表、不改 `bodySha256`，校验失败。材料指纹使用同一份排好序的路径列表。

封存约束：`publication_state = SEALED` 之后，进入 v3 正文的列不可更新，该版本的路径行和证据行也不可插入、修改或删除。`payload_format` 和 `path_count` 属于正文。路径或格式要变化时，创建新版本。提取只读取已封存、并且分类为 `CONTRACT_PATHS_READY` 或 `CONTRACT_PATHS_EMPTY` 的 v3。未封存行即使当时能够重算哈希，也不供提取。

### sourceRevision 生成

`bodySha256` 含有 `sourceRevision`。材料是否相同由 `materialSha256` 决定，这份指纹不含 `sourceRevision`，也不含 `contractVersionId` 和 `evidenceId`。修订号因此可以在正文哈希之前确定。

`contract-material-v1` 的值顺序是：编码名 `contract-material-v1`、`contractId`、`contractVersion`、`evidenceStatus`、`semanticStatus`、`sourceReference`、`contractEffectiveFrom`、`contractEffectiveTo`、`payloadFormat`、证据列表、金额路径列表。证据元素不含 `evidenceId`，排序键与请求相同，依次是 `evidenceStatus`、`sourceReference`、`quotedNote`。元素先按这个顺序连接，再取 SHA-256，列表里放类型 `06` 和这 32 字节。路径规则与 v3 正文相同。

同一次事务按下面的顺序执行：

1. 校验 `payloadFormat`、路径语法、重复路径和证据。失败时不分配修订号，也不计算正文哈希。
2. 计算 `materialSha256`。这一步还没有 `contractVersionId`、`sourceRevision` 和 `evidenceId`。
3. 锁定该 `contractId` 的家族行，直到事务结束。
4. 查找同一 `contractId` 下 `material_sha256` 相等的版本。找到则返回该行已有的 `contractVersionId`、`sourceRevision` 和 `bodySha256`，`result = EXISTING`。这次请求不再编码一个更大的修订号。
5. 找不到时，`sourceRevision` 取该家族当前最大 `source_revision` 加 1。家族还没有版本时取 1。已经存在的 v2 行继续占用它们的修订号，新的 v3 往后续编。
6. 分配新的 `contractVersionId`。它是新的身份，不从哈希截取。
7. 插入证据行并读回 `evidenceId`。没有证据则跳过。
8. 按 `contract-version-v3` 计算 `bodySha256`。此时修订号、版本 ID 和证据 ID 都已经确定，只计算这一次。
9. 插入版本行和路径行。`path_count` 等于路径元素个数。`body_encoding` 写成 `contract-version-v3`。
10. 提交后返回 `CREATED`。

这个顺序把循环拆开：材料指纹决定是否新建；修订号在确定要新建之后，用家族最大值加一得到；正文哈希使用这个已经确定的修订号，并只保存这一份。查找旧材料时比较 `materialSha256`，不临时改写修订号再重算正文。

若材料指纹也写入 `sourceRevision`，顺序会套住自己：要先知道修订号才能计算指纹，又要先比较指纹才知道该用旧修订号还是下一号。本设计不使用这个顺序。下面的测试向量给出修订号 1 和修订号 2 的两个正文哈希，用来说明二者不同。创建算法只保存步骤 5 分配到的那一个。

### 契约路径分类

分类读取一条契约版本、它的证据行和它的路径行。提取和 Worker 都使用这份结果。完整性失败时停止，不把已读到的路径子集交给提取。

| 结果 | 含义 |
| --- | --- |
| `CONTRACT_PATHS_UNDEFINED` | v2 契约没有路径定义。提取阻断。快照仍可按 v2 校验这行历史契约 |
| `CONTRACT_PATHS_EMPTY` | v3 声明了空路径列表，数量和两份哈希都一致。提取的 `payloadCode` 为 `OK`，字段结果为空 |
| `CONTRACT_PATH_INTEGRITY_FAILED` | v3 路径重复、语法不合，或按规范顺序重算的正文哈希或材料指纹与库存值不一致。提取阻断 |
| `CONTRACT_PATHS_CORRUPT` | v3 路径数据缺失或损坏，也包括 v2 行混入了路径或格式。提取阻断 |
| `CONTRACT_PATHS_READY` | v3 至少有一条路径，数量、语法、排序和两份哈希都一致。提取按完整清单继续 |
| `CONTRACT_VERSION_HASH_MISMATCH` | v2 行按 v2 重算不一致。提取阻断。这不是空路径 |

算法：

1. 版本行不存在时，调用方返回 `CONTRACT_VERSION_UNRESOLVED`。缺版本不归入空路径。
2. `body_encoding = contract-version-v2`。`payload_format`、`path_count`、`material_sha256` 任一有值，或路径表有行，返回 `CONTRACT_PATHS_CORRUPT`。否则按 v2 重算。不一致返回 `CONTRACT_VERSION_HASH_MISMATCH`。一致返回 `CONTRACT_PATHS_UNDEFINED`。
3. `body_encoding = contract-version-v3`。`payload_format` 不是 `JSON`、`path_count` 为空、`material_sha256` 为空、路径行读不到，或某条 `field_path` 为空，返回 `CONTRACT_PATHS_CORRUPT`。
4. v3 的路径行数与 `path_count` 不同，返回 `CONTRACT_PATHS_CORRUPT`。到这里就停止，不用较少的那一侧重新编码。
5. 路径字节重复，或任一路径不符合语法，返回 `CONTRACT_PATH_INTEGRITY_FAILED`。
6. 将路径按 UTF-8 字节升序排列，连同 `payloadFormat` 和其余 v3 字段重算 `bodySha256`，再用不含版本 ID 和修订号的材料编码重算 `materialSha256`。任一与库存值不同，返回 `CONTRACT_PATH_INTEGRITY_FAILED`。不改用未排序列表再试。
7. 两份哈希都一致且 `path_count = 0`，返回 `CONTRACT_PATHS_EMPTY`。`path_count > 0` 时返回 `CONTRACT_PATHS_READY`。
8. 其他 `body_encoding` 返回 `CONTRACT_PATHS_CORRUPT`。

正文重算失败时，不再区分“只改了路径”和“改了证据”。SHA-256 不能从结果还原被改的字段。两种都返回 `CONTRACT_PATH_INTEGRITY_FAILED`，也都不提取部分路径。

### request-v2 测试向量

下面三组字节都按本节和上一节的规则算出。SHA-256 是这组字节的小写十六进制。实现要用同样的输入得到同样的字节和哈希。本文件记录的是这三组计算值，还没有把它放进数据库。

第一组是创建契约。聚合 ID 是 `req-1`，`sourceMessageType` 是空字符串。字段按固定顺序编码，JSON 把 `platform` 写在最后也不改变字节。

```text
010000000a726571756573742d7632010000000f4352454154455f434f4e5452414354010000000f534f555243455f434f4e545241435401000000057265712d3101000000046f702d310100000006454449544f5201000000074d45495455414e01000000026d740100000003646f63010000000001000000114f4646494349414c5f444f43554d454e54
```

SHA-256：`36cff941ba280ad6a2b9d2d88ee60525e177af01e5842e804f004fefe816463d`。

只把 `platform` 从 `MEITUAN` 改成 `JD`，其余不变，SHA-256 变为 `96e0784821b086361aa92b49dbb92005c5ba0a76e8f76920ba4e38d7e8a24c4a`。

第二组是发布快照。父快照和聚合 ID 都是 `ROOT`，目录是 `catalog-1`。两个变更都是 `ADD_MAPPING`，起点都是 `2026-01-02T03:04:05.006Z`，右端点都是 NULL。输入若先给版本 2 再给版本 1，规范编码仍先写版本 1。版本 1 的对象字节是 `010000000b4144445f4d415050494e47020000000000000001030000019b7ca98c8e00`，嵌套项是 `06622131b6c4ebe50dc562771f2d10b49a9aa9df7d20bccf2281934592630da6c2`。

```text
010000000a726571756573742d763201000000075055424c49534801000000104d415050494e475f534e415053484f540100000004524f4f5401000000046f702d3101000000095055424c49534845520100000004524f4f540100000009636174616c6f672d31050000000206622131b6c4ebe50dc562771f2d10b49a9aa9df7d20bccf2281934592630da6c206d8a44bbf9869301ab28298fbb987e22b2784277a8faecf5f23559e3a518d6a94
```

SHA-256：`ee7a6057b3c9df84a1c38b6993ce0b8ca92eb4544b44271172241495b24a1d71`。

不排序、按版本 2 在前编码时，SHA-256 会变成 `21436b752689506bdb9fdae90f1c19b7008c0275e39b188e2eee239a1b86ba91`。那不是规范结果。把版本 2 的右端点改成同一个起点时间后，规范 SHA-256 变为 `a85730f7f7a7821e8b626d15e0758270bfb3ba58937ee1be2aa70eb24e24e500`。

第三组是创建契约版本。路径和聚合 ID 都是 `c-1`，外部版本是“未提供”，两个时间都是 NULL。证据输入顺序是 `ref-b` 然后 `ref-a`，规范顺序按来源字节排成 `ref-a` 然后 `ref-b`。

```text
010000000a726571756573742d763201000000174352454154455f434f4e54524143545f56455253494f4e010000000f534f555243455f434f4e54524143540100000003632d310100000003632d3101000000046f702d310100000006454449544f520100000009e69caae68f90e4be9b010000000445312d41010000000ce983a8e58886e7a1aee8aea401000000075352432d3030310000050000000206c6fde31695236b2b97640027d8c8b080948f2aec06edda90bea0f1e34bfbc8e406bc413a8c009f9ec52c5ef09ce08126191b157676766f9b95c9f1ed53b5e55b97
```

SHA-256：`c34a9479a0c2c56156fe77779568434750ac99aeaa4bbf4ba9e7074c9ebd9919`。

不排序时 SHA-256 是 `a510b723a97a388376bf1b3d5a9e6d573bfc40c17c35aedbb2621a5ee2d5a7e7`，这不是规范结果。只把 `ref-a` 的摘录从 `note-a` 改成 `note-c`，规范 SHA-256 变为 `d17b58861a21306159362695347766aaeb81c64f51e347e94f7147a06cf85e2e`。路径和聚合 ID 一起改成 `c-2` 时，SHA-256 变为 `86418731a850eb63918c4d66e41b9711d2eef93c57c61872161b3f3603b8233a`。

三个单独值用于区分空值：

| 值 | 字节 | SHA-256 |
| --- | --- | --- |
| NULL | `00` | `6e340b9cffb37a989ca544e6bb780a2c78901d3fb33738768511a30617afa01d` |
| 空字符串 | `0100000000` | `957b88b12730e646e0f33d3618b77dfa579e8231e3c59c7104be7165611c8027` |
| 空列表 | `0500000000` | `49e8e3297545c15ab6a79471a7a34d43e24a8f1cb25ea3d8417c61f699267a3f` |

`v1` 是分隔符编码，没有写入过数据库。读到 `-v1` 时校验失败，不转换成后续版本。

`contract-version-v2` 与 `contract-version-v3` 可以同时被校验，但一行契约版本只使用 `body_encoding` 指定的一种。映射、冲突、目录和快照仍使用各自的 `v2`。新的管理请求使用 `request-v3`。下面这组向量用同一份标量材料。v2 没有 `payloadFormat`，也没有路径。v3 的 `payloadFormat` 是 `JSON`，路径输入顺序是 `total` 然后 `shipping_fee`，规范顺序是 `shipping_fee` 然后 `total`。证据列表为空，两个时间都是 NULL，`contractVersionId` 是 `cv-1`，`contractId` 是 `c-1`，外部版本是“未提供”，`sourceRevision` 是 1。

这些新增哈希用两套独立的 SHA-256 实现复算过，结果一致：Python `hashlib.sha256` 与 OpenSSL `dgst -sha256`。同一方法再次得到历史 v2 哈希，原值不变。本文件记录的是设计向量，还没有写入数据库。

v2 字节保持原定义：

```text
0100000013636f6e74726163742d76657273696f6e2d7632010000000463762d310100000003632d310100000009e69caae68f90e4be9b020000000000000001010000000445312d41010000000ce983a8e58886e7a1aee8aea401000000075352432d30303100000500000000
```

SHA-256：`3174d60d23276ba947fc796c7c59d8dbca00283b6d33a699bdb61facad80af05`。

v3 在两个 NULL 之后写入 `payloadFormat`。`JSON` 的编码是 `01000000044a534f4e`。规范字节：

```text
0100000013636f6e74726163742d76657273696f6e2d7633010000000463762d310100000003632d310100000009e69caae68f90e4be9b020000000000000001010000000445312d41010000000ce983a8e58886e7a1aee8aea401000000075352432d303031000001000000044a534f4e05000000000500000002010000000c7368697070696e675f6665650100000005746f74616c
```

SHA-256：`2f67578050831aba6ff3b3c28733daa74d898a995265d82c242b976882cdde78`。

只把 `payloadFormat` 的字符串改成 `XML`，路径和修订号仍是上面这一组。`XML` 未进入枚举，接口返回 `PAYLOAD_FORMAT_UNKNOWN`，不插入行。编码本身的字节和哈希用来证明该字段参加正文：

```text
0100000013636f6e74726163742d76657273696f6e2d7633010000000463762d310100000003632d310100000009e69caae68f90e4be9b020000000000000001010000000445312d41010000000ce983a8e58886e7a1aee8aea401000000075352432d30303100000100000003584d4c05000000000500000002010000000c7368697070696e675f6665650100000005746f74616c
```

SHA-256：`9782c9680ab7a4430be2ce7526f366a8406af8224265d379f14ea8bea20c941e`。

只把 `JSON` 改成小写 `json`，其余与规范 v3 相同。SHA-256 是 `8c10faf953f6185bbfde50de2855eb50d8eecce6e992c6c8cc075a6c3cd100b7`。完整字节：

```text
0100000013636f6e74726163742d76657273696f6e2d7633010000000463762d310100000003632d310100000009e69caae68f90e4be9b020000000000000001010000000445312d41010000000ce983a8e58886e7a1aee8aea401000000075352432d303031000001000000046a736f6e05000000000500000002010000000c7368697070696e675f6665650100000005746f74616c
```

这个值同样不能入库。大小写不同就是另一份正文。

`payloadFormat` 仍是 `JSON`，路径只保留 `total`：

```text
0100000013636f6e74726163742d76657273696f6e2d7633010000000463762d310100000003632d310100000009e69caae68f90e4be9b020000000000000001010000000445312d41010000000ce983a8e58886e7a1aee8aea401000000075352432d303031000001000000044a534f4e050000000005000000010100000005746f74616c
```

SHA-256：`5ae79c53d432d6ca2a27e6605bcaf11acbeb423a04c1b01d5afb8d9ee47c9709`。

`payloadFormat` 仍是 `JSON`，路径列表为空。这是 `CONTRACT_PATHS_EMPTY` 使用的正文，分类还要求库存 `path_count = 0` 且材料指纹一致：

```text
0100000013636f6e74726163742d76657273696f6e2d7633010000000463762d310100000003632d310100000009e69caae68f90e4be9b020000000000000001010000000445312d41010000000ce983a8e58886e7a1aee8aea401000000075352432d303031000001000000044a534f4e05000000000500000000
```

SHA-256：`60f96aca692a7dff254fd1efd4362afe4d0f2e28529cdeea566cfd60c5523959`。

同一份 `JSON` 和两条规范路径，若正文里的修订号写成 2，整数编码变成 `020000000000000002`。这个哈希只证明修订号参加正文。创建算法在材料指纹已存在时返回修订号 1 的哈希，不保存下面这一份：

```text
0100000013636f6e74726163742d76657273696f6e2d7633010000000463762d310100000003632d310100000009e69caae68f90e4be9b020000000000000002010000000445312d41010000000ce983a8e58886e7a1aee8aea401000000075352432d303031000001000000044a534f4e05000000000500000002010000000c7368697070696e675f6665650100000005746f74616c
```

SHA-256：`045b1aa179edd32136069f8266b065078c9813f88c43ad6332c8b8973f5a4c0d`。

不排序、把 `total` 放在 `shipping_fee` 前面，得到的不是规范哈希。这份字节不能通过校验：

```text
0100000013636f6e74726163742d76657273696f6e2d7633010000000463762d310100000003632d310100000009e69caae68f90e4be9b020000000000000001010000000445312d41010000000ce983a8e58886e7a1aee8aea401000000075352432d303031000001000000044a534f4e050000000005000000020100000005746f74616c010000000c7368697070696e675f666565
```

SHA-256：`013692f475dcb5b3188d4f5477dc1476b756cd893f59e7c5b7adb65b0fcf3bbd`。

对应的材料指纹不含 `cv-1` 和修订号。`JSON` 与两条规范路径的字节是：

```text
0100000014636f6e74726163742d6d6174657269616c2d76310100000003632d310100000009e69caae68f90e4be9b010000000445312d41010000000ce983a8e58886e7a1aee8aea401000000075352432d303031000001000000044a534f4e05000000000500000002010000000c7368697070696e675f6665650100000005746f74616c
```

SHA-256：`939ca9bfc9af4e1124ef53e34cabf20ff099ab8f3162fce383cc98e423d690a0`。

同一材料把格式字符串改成 `XML` 后，材料指纹变为：

```text
0100000014636f6e74726163742d6d6174657269616c2d76310100000003632d310100000009e69caae68f90e4be9b010000000445312d41010000000ce983a8e58886e7a1aee8aea401000000075352432d30303100000100000003584d4c05000000000500000002010000000c7368697070696e675f6665650100000005746f74616c
```

SHA-256：`de49b76e9e9156bc1ee96fb75f781458f3b464c2191306f60538c926da82c471`。

`JSON` 且路径为空时，材料指纹是 `f46ab2d25fed711cda8fab694660d9b4cff59339b5343a25d01bd639f7f9073f`。完整字节：

```text
0100000014636f6e74726163742d6d6174657269616c2d76310100000003632d310100000009e69caae68f90e4be9b010000000445312d41010000000ce983a8e58886e7a1aee8aea401000000075352432d303031000001000000044a534f4e05000000000500000000
```

`JSON` 且只有 `total` 时，材料指纹是 `1419b3f929df27cab17c3256b1ff884db9d154ee30e9d273c14d815539e4e98d`。完整字节：

```text
0100000014636f6e74726163742d6d6174657269616c2d76310100000003632d310100000009e69caae68f90e4be9b010000000445312d41010000000ce983a8e58886e7a1aee8aea401000000075352432d303031000001000000044a534f4e050000000005000000010100000005746f74616c
```

对应的 `request-v3` 创建契约版本请求，在两个 NULL 之后写入 `payloadFormat`，路径按规范排序。证据列表为空，操作者是 `op-1`，角色是 `EDITOR`。

```text
010000000a726571756573742d763301000000174352454154455f434f4e54524143545f56455253494f4e010000000f534f555243455f434f4e54524143540100000003632d310100000003632d3101000000046f702d310100000006454449544f520100000009e69caae68f90e4be9b010000000445312d41010000000ce983a8e58886e7a1aee8aea401000000075352432d303031000001000000044a534f4e05000000000500000002010000000c7368697070696e675f6665650100000005746f74616c
```

SHA-256：`ecec9251c88f1b9a606025bc5466060e27988eacb0ad50daa98186cb66d875a3`。

只声明 `total` 时，请求字节和哈希是：

```text
010000000a726571756573742d763301000000174352454154455f434f4e54524143545f56455253494f4e010000000f534f555243455f434f4e54524143540100000003632d310100000003632d3101000000046f702d310100000006454449544f520100000009e69caae68f90e4be9b010000000445312d41010000000ce983a8e58886e7a1aee8aea401000000075352432d303031000001000000044a534f4e050000000005000000010100000005746f74616c
```

SHA-256：`e6927deca9a0c389bb0d0aa85edc2cf06386b52129320b28c0d78d43085d8efa`。

`amountPaths` 为空列表时，请求哈希是 `4167bfe3a9bf77872a2d17b70c28baab5068d05dda840c75ad32f7dd8238b260`。完整字节：

```text
010000000a726571756573742d763301000000174352454154455f434f4e54524143545f56455253494f4e010000000f534f555243455f434f4e54524143540100000003632d310100000003632d3101000000046f702d310100000006454449544f520100000009e69caae68f90e4be9b010000000445312d41010000000ce983a8e58886e7a1aee8aea401000000075352432d303031000001000000044a534f4e05000000000500000000
```

### 完整性校验

Worker 只按任务上的 `snapshotId` 装载。任何一步失败都停止任务。不允许改读最新快照，不允许跳过坏掉的部分，也不允许写回修复。

1. 读取 `publishBatch`。不存在则失败。
2. 读取三类条目。三项数量之和必须等于 `itemCount`。
3. 按 `snapshot-v2` 重算哈希，必须等于 `contentSha256`。
4. 读取目录头和全部目录条目。条目数必须等于目录 `itemCount`，重算结果必须等于目录 `contentSha256`，目录 ID 必须等于批次上的 `typeCatalogVersionId`。
5. 每条契约条目都能读到已封存契约版本。按该行 `body_encoding` 选择 v2 或 v3 重算，结果必须等于条目和版本行上的 `bodySha256`。不能换一种编码再试。然后执行本节的路径分类。`CONTRACT_PATHS_UNDEFINED` 表示 v2 没有路径定义，这行仍可作为历史契约条目；提取阶段会阻断，快照装载本身不因此失败。`CONTRACT_PATHS_READY` 和 `CONTRACT_PATHS_EMPTY` 通过。`CONTRACT_PATHS_CORRUPT`、`CONTRACT_PATH_INTEGRITY_FAILED` 和 `CONTRACT_VERSION_HASH_MISMATCH` 使本任务失败。失败后不使用已经读到的部分路径。
6. 每条映射条目都能读到已封存映射版本，重算结果必须同时等于条目和版本行上的 `bodySha256`。
7. 每条冲突条目都能读到已封存决定。候选至少两条。每条候选都能读到已封存映射版本，版本重算哈希必须等于 `candidateBodySha256`，也必须等于版本行上的 `bodySha256`。决定按 `conflict-decision-v2` 重算后，必须等于决定行和冲突条目上的 `bodySha256`。
8. 映射条目、冲突条目、冲突决定和候选映射引用的契约版本，都必须出现在该快照的契约条目中。Worker 从这些契约条目取得 `contractVersionId`，再读取对应正文。条目里没有的契约版本，即使版本表里还在，也不能拿来执行，结果是 `CONTRACT_VERSION_UNRESOLVED`，不写成 `UNMAPPED`。

通过之后才允许按第 8 节匹配。校验失败的任务不能改用另一份快照继续。

新任务第一次开始时，读取没有子批次的最新已发布快照，先做上面的校验，再把 `snapshotId` 写入任务。重试和事后回看都用这个标识。新到达的迟到报文也是先固定当时最新且校验通过的快照，再用 `receivedAt` 命中其中的历史窗口。观测记录同时保存这个 `snapshotId`。

多个 Worker 对同一份通过校验的快照看到同一批条目、同一个目录版本和同一组候选哈希。

匹配使用第 8 节的算法。`processedAt` 不参与选择。`RETIRED` 条目只要窗口包含 `receivedAt` 就命中。

## 15. Pipeline、并发与审计

Pipeline 的顺序不变：原文、原始金额、来源、按固定快照匹配、观测，然后才可能进入已经批准的计算。未经发布的草稿和未确认资格不会进入快照，因此不能进入正式业务计算。

父批次唯一保证两个不同请求不会基于同一个父快照各发一份。同一请求号的重复提交由幂等请求表返回第一次的响应。窗口检查、候选封存和契约条目补齐在同一事务内完成。审计不承担结果重放。没有配置表达式，转换策略仍只有 `NO_CONVERT`。

## 16. 测试与验收

本文件不执行这些测试。实现时至少覆盖：

| 测试 | 输入 | 预期 |
| --- | --- | --- |
| 同一快照多段窗口 | 同一字段两条窗口首尾相接 | 两条映射条目都保存 |
| 窗口重叠 | 后一段起点早于前一段终点 | `WINDOW_OVERLAP`，整笔回滚 |
| 左闭右开 | 接收时间等于起点、等于终点 | 起点命中，终点不命中 |
| 迟到报文 | 接收时间落在 `RETIRED` 窗口，处理时间已经更晚 | 命中该历史映射，不变成 `UNMAPPED` |
| 没有右端点 | 同一字段两条都不写右端点 | 唯一索引拒绝第二条 |
| 冲突决定还原 | 发布一条含两个候选和证据的决定 | 能读回决定、候选正文、证据、审核人、窗口和全部哈希 |
| 候选封存 | 冲突决定发布后编辑候选版本 | `VERSION_SEALED`，旧正文哈希不变 |
| 其他版本仍可编辑 | 同一 `mappingId` 的另一个版本仍是草稿 | 只有被引用的那个版本封存 |
| 候选被改写 | 发布前改了候选正文 | `CANDIDATE_BODY_CHANGED`，不产生快照 |
| 解除冲突 | 新快照结束冲突窗口并开始一条映射 | 旧决定、候选哈希和旧快照不变 |
| 外部版本重复 | 两次“未提供”，材料指纹不同 | 两个 `sourceRevision` 和两个 `contractVersionId` |
| 相同材料 | 再次提交同一个 `materialSha256` | 返回原版本、原修订号和原 `bodySha256`，不新增修订 |
| 修订号顺序 | 规范 v3 材料提交两次 | 第一次保存修订号 1，正文哈希 `2f67578050831aba6ff3b3c28733daa74d898a995265d82c242b976882cdde78`。第二次仍返回这一对。不保存修订号 2 的哈希 `045b1aa179edd32136069f8266b065078c9813f88c43ad6332c8b8973f5a4c0d` |
| `payloadFormat` 不同 | 路径相同，格式字符串分别为 `JSON` 和 `XML` | 材料指纹分别是 `939ca9bfc9af4e1124ef53e34cabf20ff099ab8f3162fce383cc98e423d690a0` 和 `de49b76e9e9156bc1ee96fb75f781458f3b464c2191306f60538c926da82c471`。`XML` 返回 `PAYLOAD_FORMAT_UNKNOWN`，不入库 |
| 空路径声明 | v3，`path_count = 0`，正文哈希 `60f96aca692a7dff254fd1efd4362afe4d0f2e28529cdeea566cfd60c5523959` | 分类 `CONTRACT_PATHS_EMPTY` |
| v2 没有路径定义 | v2 哈希与历史向量一致，路径行数为 0 | 分类 `CONTRACT_PATHS_UNDEFINED`。提取阻断。不补成空列表 |
| 路径数量不符 | `path_count` 大于实际路径行数 | `CONTRACT_PATHS_CORRUPT`。不提取剩余路径 |
| 路径未排序入库 | 库存哈希等于未排序向量 `013692f475dcb5b3188d4f5477dc1476b756cd893f59e7c5b7adb65b0fcf3bbd` | `CONTRACT_PATH_INTEGRITY_FAILED` |
| v2 三个扩展字段均为 NULL | `body_encoding` 为 v2，`payload_format`、`path_count`、`material_sha256` 都是 NULL | `ck_contract_payload_shape` 允许 |
| v2 任意扩展字段非 NULL | 三个扩展字段里至少一列有值 | 约束拒绝。包括只填 `JSON`、只填 `0` 或只填 64 位小写哈希 |
| v3 `payload_format` 为 NULL | 其余 v3 列合法 | 约束拒绝。`IS NOT NULL` 为 FALSE，不把 NULL 比较留成 UNKNOWN |
| v3 `payload_format` 为 `XML` | 路径和材料指纹列合法 | 约束拒绝 |
| v3 `path_count` 为 NULL | 格式为 `JSON`，材料指纹合法 | 约束拒绝 |
| v3 `path_count` 为 -1 | 格式为 `JSON`，材料指纹合法 | 约束拒绝 |
| v3 `material_sha256` 为 NULL | 格式为 `JSON`，`path_count` 为 0 | 约束拒绝 |
| v3 `material_sha256` 长度不足 | 值为 `abc`，或 63 个小写十六进制字符 | 约束拒绝。`CHAR_LENGTH` 不是 64，正则也不匹配 |
| v3 `material_sha256` 含非十六进制字符 | 64 个字符，其中含 `g`，或含大写 `A` | 约束拒绝 |
| 合法 v3、空路径 | `payload_format` 为 `JSON`，`path_count` 为 0，材料指纹是 64 位小写十六进制 | 约束允许。路径表行数由创建事务保持为 0 |
| 合法 v3、非空路径 | `payload_format` 为 `JSON`，`path_count` 大于 0，材料指纹是 64 位小写十六进制 | 约束允许。路径表行数由创建事务保持等于 `path_count` |
| 封存后修改路径 | 修改已封存版本的路径或 `payloadFormat` | 被拒绝。变化必须成为新版本 |
| 封存后修改 | 修改已进入快照的契约版本 | 被拒绝 |
| 恰好一个修订 | 外部版本号或“未提供”在当前固定快照内恰好一个对应修订 | 确定 `contractVersionId`。正文校验通过后进入路径提取。本行未执行 |
| 零个修订 | 外部版本号在当前固定快照内没有对应修订 | `CONTRACT_VERSION_UNRESOLVED`。阻断，无字段结果，无 `ObservedAmount`，不写成 `UNMAPPED`。本行未执行 |
| 多个“未提供” | 报文只有外部版本号，当前固定快照内对应修订多于一个 | `CONTRACT_VERSION_UNRESOLVED`。阻断，不猜最新修订，无字段结果，无 `ObservedAmount`，不写成 `UNMAPPED`。本行未执行 |
| 明确外部版本号重复 | 同一契约家族、同一固定快照内，两个不同 `contractVersionId` 的外部版本号相同且不是“未提供” | `CONTRACT_VERSION_UNRESOLVED`。阻断，无字段结果，无 `ObservedAmount`，不猜最新修订。本行未执行 |
| 接入端提供有效 `contractVersionId` | 该 ID 在当前快照契约条目中，家族身份、外部版本号和正文哈希都通过 | 采用该 ID 并继续路径提取。不再用外部版本号选择修订。本行未执行 |
| `contractVersionId` 不在当前快照 | 版本表有该 ID，当前 `snapshotId` 的契约条目没有 | `CONTRACT_VERSION_UNRESOLVED`。阻断，无字段结果，无 `ObservedAmount`。本行未执行 |
| 契约身份不一致 | 候选版本与接入来源的契约家族身份、`contractId` 或外部版本号不一致 | `CONTRACT_IDENTITY_MISMATCH`。阻断，不进入路径提取。本行未执行 |
| 正文哈希不一致 | 候选契约版本的正文哈希与封存值不一致 | `CONTRACT_VERSION_HASH_MISMATCH`。阻断，不使用部分路径，无字段结果，无 `ObservedAmount`。本行未执行 |
| 临时读取失败 | 快照或契约版本读取尚未完成，没有得出零个、一个或多个修订 | 允许原任务重试，仍用原 `snapshotId`，不切换最新快照。不得记成 `CONTRACT_VERSION_UNRESOLVED`。本行未执行 |
| 确定性解析失败 | 固定快照内查找已经完成，对应修订为零个或多个 | `CONTRACT_VERSION_UNRESOLVED`。原任务重复重试仍然失败，不切换 `snapshotId`。新快照发布后必须创建显式重放任务。本行未执行 |
| 目录变化 | 新目录把类型改为 `ACTIVE` 后发布新快照 | 旧快照仍指向旧目录，类型资格不变 |
| 目录数量 | 条目数和 `itemCount` 不一致 | 封存失败；已封存的目录被改少时 Worker 拒绝 |
| 重试 | 新快照已发布 | 原任务仍用原 `snapshotId` 和原目录版本。确定性解析失败不因此恢复，也不改读新快照。本行未执行 |
| 显式重放 | 修正配置并发布新快照后重新处理原报文 | 必须是新任务，不复用原任务身份和幂等键。按 ADR-0003 另建观测，不覆盖历史观测。观测身份不变。本行未执行 |
| 快照损坏 | 删除一条候选、证据或目录条目 | `SNAPSHOT_INTEGRITY_FAILED`，不改读最新配置，也不自动修复 |
| JSON 字段顺序 | 第一组创建契约，只改变 JSON 字段先后 | 字节和 SHA-256 仍是 `36cff941ba280ad6a2b9d2d88ee60525e177af01e5842e804f004fefe816463d` |
| 集合输入顺序 | 第二组、第三组按相反顺序提交集合 | 规范哈希仍分别是 `ee7a6057b3c9df84a1c38b6993ce0b8ca92eb4544b44271172241495b24a1d71` 和 `c34a9479a0c2c56156fe77779568434750ac99aeaa4bbf4ba9e7074c9ebd9919` |
| 空值区分 | NULL、空字符串、空列表 | 三个 SHA-256 分别是 `6e340b9cffb37a989ca544e6bb780a2c78901d3fb33738768511a30617afa01d`、`957b88b12730e646e0f33d3618b77dfa579e8231e3c59c7104be7165611c8027`、`49e8e3297545c15ab6a79471a7a34d43e24a8f1cb25ea3d8417c61f699267a3f` |
| 相同请求重放 | 同一请求号，请求字节与第一组相同 | 返回第一次的完整响应，不写第二份业务数据 |
| 请求内容不同 | 同一请求号，但 `platform` 改为 `JD` | `REQUEST_REUSED`。该内容自己的哈希是 `96e0784821b086361aa92b49dbb92005c5ba0a76e8f76920ba4e38d7e8a24c4a` |
| 路径变化 | 第三组的 `contractId` 改为 `c-2` | 哈希变为 `86418731a850eb63918c4d66e41b9711d2eef93c57c61872161b3f3603b8233a` |
| 嵌套内容变化 | 第三组把 `note-a` 改为 `note-c` | 哈希变为 `d17b58861a21306159362695347766aaeb81c64f51e347e94f7147a06cf85e2e` |
| 候选契约进入快照 | 两个候选引用父快照中没有的同一契约版本 | 新快照只有一条该契约条目，`itemCount` 和 `contentSha256` 都包含它 |
| 候选契约缺失 | 候选的 `contractVersionId` 读不到正文 | 整笔回滚，不留下 `SUCCEEDED` 请求 |
| 发布幂等 | 同一请求号、同一请求内容再次提交 | 返回第一次的完整响应和原来的 `snapshotId`，不产生第二个批次 |
| 请求内容不同 | 同一请求号但请求哈希不同 | `REQUEST_REUSED`，不返回另一份快照 |
| 并发重复请求 | 两个事务同时使用同一请求号 | 只有一个快照。另一个得到第一次的响应或 `REQUEST_IN_PROGRESS` |
| 发布失败 | 窗口重叠导致回滚 | 幂等表没有 `SUCCEEDED` 行，审计也没有这次成功记录 |
| 草稿类型发布 | 目录中类型仍是 `DRAFT` | 拒绝，不产生业务映射快照 |
| 未识别计算 | 只有未识别观测 | 执行状态是 `UNMAPPED`，不能进入计算 |

## 17. 风险和待确认事项

MySQL、账号体系和第一份可发布的类型目录都还没有接受。本文的表不能执行，今天也不能发布业务映射。角色名称不是已经上线的权限系统。

跨映射表和冲突表的窗口重叠依赖发布事务。数据库只保证每类条目各自最多一条没有右端点的记录。实现时必须把两类条目放进同一次检查，不能只依赖唯一索引。

候选正文不复制到新表。历史还原依赖快照中的契约条目、封存后的版本行、证据行和候选哈希始终一起保留。只删其中一层，校验就会失败。这是预期结果，Worker 不能忽略，也不能改读最新契约。

本文没有修改 06-03-01、06-03-02-01 和 06-03-02-02。五种映射资格、三种执行状态和 39 个类型编码保持原定义。

## 18. 设计结论

同一份快照可以保留同一字段的多个不重叠窗口。迟到报文按接收时间命中其中的历史映射，包括已经标成 `RETIRED` 的窗口。冲突决定保存候选版本号和正文哈希，并在发布时封存候选版本，因此历史决定可以还原当时比较过的映射正文。官方没有版本号时，用 `sourceRevision` 和材料指纹管理材料修订。运行时仍须按第 8 节第 2 步在固定快照内唯一确定 `contractVersionId`，不能把零个或多个修订写成 `UNMAPPED`。正文哈希在修订号分配之后计算，并包含 v3 的 `payloadFormat` 与路径列表。类型目录版本写在发布批次上，目录自己记录条目数量和指纹。

Worker 只接受完整通过校验的 `MappingSnapshot`。候选依赖的契约版本必须已经在该快照的契约条目里。任一数量、正文或哈希对不上，就停止该任务。它不改读最新配置，也不修复快照。

相同 `snapshotId` 的批次、三类条目和目录版本在发布后不再修改。相同请求号只有在请求哈希也相同的时候，才返回这一次的完整响应。

没有发现用配置状态绕过语义确认的路径。类型不是 `ACTIVE`、资格未确认或被禁止、冲突尚未形成已审核决定，都不能进入正式业务映射。

这是第九轮修订稿，用来提交审查。它还不是冻结设计，也没有批准任何生产金额映射。第八轮已经把 `payloadFormat` 纳入 v3 正文哈希，并规定修订号先于正文哈希分配。本轮只收紧 `ck_contract_payload_shape`：v3 的空列在 MySQL 里不再因为 CHECK 结果是 UNKNOWN 而被写入。历史哈希字节没有改动。

## 19. 修订记录

| 轮次 | 内容 |
| --- | --- |
| 初稿 | 建立映射配置、版本、发布准入、建议表结构和 Worker 读取边界 |
| 第二轮 | 用不可变发布批次替换审计号快照，契约身份加入来源种类 |
| 第三轮 | 同一快照保留多段不重叠映射窗口。冲突决定单独存全文。契约材料用内部修订号和正文指纹区分。发布批次固定类型目录版本 |
| 第四轮 | 发布冲突决定时封存候选版本并保存正文哈希。定义快照读取对象和规范哈希。补充管理接口的请求、响应、幂等和事务 |
| 第五轮 | 候选依赖的契约版本写入新快照。用独立请求表重放第一次的完整响应。哈希改为带类型和长度的编码 |
| 第六轮 | 固定每种变更请求的 `request-v2` 字段顺序、排序平局和二进制范围，并记录三组哈希测试向量 |
| 第七轮 | 设计纠错。新增 `contract-version-v3` 和金额路径行，使路径清单进入契约正文哈希。`contract-version-v2` 的字节定义不改 |
| 第八轮 | 设计纠错。`payloadFormat` 进入 v3 正文和 `contract-material-v1`。路径结果拆成五种分类。`sourceRevision` 先由材料指纹分配，再计算包含修订号的正文哈希。v2 历史哈希保持原字节 |
| 第九轮 | 设计纠错。`ck_contract_payload_shape` 对 v3 先判断非空，再判断 `JSON`、非负路径数和 64 位小写十六进制材料指纹。`material_sha256` 改为 `VARCHAR(64)`，避免 `CHAR` 补空格后放行短字符串。写入时拒绝不合法行，不把这项留给 Worker |
| 第十轮 | 按已接受的 ADR-0004 修订契约版本解析。零个或多个对应修订改为 `CONTRACT_VERSION_UNRESOLVED` 并阻断，不再写成 `UNMAPPED`。原任务重试不换 `snapshotId`。新快照上的重新处理改为显式重放。第 8 节与第 14 节的快照选择措辞差异未决。本节测试未执行 |
