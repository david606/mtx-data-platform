# 06-03-02-03 MTX Data Platform V2 金额映射规则配置模型与版本管理设计

本文设计金额映射规则怎样配置、版本化、审核、发布和被 Worker 使用。它不编写 Java，不执行 DDL，也不批准任何生产映射。

依据是已经完成的三份设计：

- [06-03-01 金额领域模型](06-03-01%20MTX%20Data%20Platform%20V2%20多平台订单金额领域模型设计.md)
- [06-03-02-01 标准金额类型](06-03-02-01%20MTX%20Data%20Platform%20V2%20标准金额类型体系与编码规范.md)
- [06-03-02-02 四平台字段映射](06-03-02-02%20MTX%20Data%20Platform%20V2%20四平台原始金额字段与标准金额类型映射设计.md)

架构依据是 [ADR-0002](../../../decisions/0002-v2-module-architecture.md) 和 [调度边界](../../../architecture/scheduling.md)。

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

契约版本分成四个标识，不再用外部版本号充当内部唯一键：

| 标识 | 含义 |
| --- | --- |
| `contractVersion` | 外部版本号。官方没有版本号时写“未提供”。它可以在同一家族里重复 |
| `contractVersionId` | 内部分配的唯一版本 ID。主键 |
| `sourceRevision` | 该家族内的材料修订号，从 1 递增。只在内容指纹不同时增加 |
| `bodySha256` | 材料正文指纹。同一家族里相同指纹不新建版本 |

因此，官方没有版本号、后来材料变了、或者封存前补充了会改变正文的证据，都会得到新的 `sourceRevision` 和新的 `contractVersionId`。指纹相同则返回已有版本，避免空修订。已经进入快照的版本行封存，不能改正文，也不能追加会改变指纹的证据。

没有消息类型时写入空字符串。报文若只带“未提供”，而该家族在这份快照里有多个内部修订，Worker 不猜测，结果是 `UNMAPPED`。接入记录必须尽量带上 `contractVersionId`。

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

发布同一事务还要封存全部候选映射版本，以及这些版本引用的、当时仍未封存的契约版本。封存把 `publicationState` 写成 `SEALED`，并写入该版本的 `bodySha256`。之后该版本整行不能再改，证据也不能再追加。

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
2. 用报文中的 `contractVersionId` 定位契约版本。只有外部版本号时，仅当这份快照里该家族恰好有一个对应修订才采用。零个或多个都得到 `UNMAPPED`。
3. 在该快照中取出这个契约版本和字段的全部映射条目、全部冲突条目。`ACTIVE` 和 `RETIRED` 都参加，不把 `RETIRED` 滤掉。
4. 保留 `effectiveFrom <= receivedAt` 且右端点为空或 `receivedAt < effectiveTo` 的条目。
5. 命中一条映射时，使用该 `mappingVersionId`，执行状态是 `MAPPED`。命中一条冲突决定时，执行状态是 `CONFLICT`。都没有则是 `UNMAPPED`。
6. 同时命中映射和冲突，或命中两条映射，说明快照违反了发布检查。任务失败，不擅自选一条。

已经开始的任务继续使用任务上的 `snapshotId`。新到达的迟到报文在第一次开始时固定当时最新的快照，再用自己的 `receivedAt` 选择窗口。当前快照里已经标成 `RETIRED` 的历史窗口仍然参加匹配，处理时间不参加。

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

批次和全部条目在同一个事务中插入，状态直接是 `PUBLISHED`。失败整笔回滚。父批次唯一。批次保存三类条目的合计数量和 `contentSha256`。哈希的字段顺序、排序和空值写法见第 14 节。冲突候选不单独计入 `itemCount`，它们的正文哈希已经算进冲突决定的 `bodySha256`。Worker 重算后不一致就拒绝执行，不改读最新配置，也不补写快照。

新发布不修改旧批次。新快照复制父快照的全部历史窗口。退役只在新快照里给被退役的那条补上右端点。

## 10. 发布事务

一次发布、退役、冲突发布或回滚都按下面的顺序做完，否则全部回滚：

1. 按请求号查找已有审计。有则返回原来的 `snapshotId`。
2. 读取父批次。第一个父标识是 `ROOT`。
3. 读取本批次声明的类型目录版本。目录必须已经封存，而且目标类型在该目录中的生命周期和资格满足第 13 节的门槛。
4. 以父快照的全部条目为底稿。历史窗口原样复制。本次接上的新窗口，只在新快照里把前一条的右端点写成新窗口的起点，并把该条标成 `RETIRED`。不删除历史条目，也不回写旧快照。
5. 把同一契约版本、同一字段的映射窗口和冲突窗口放在一起排序。后一段起点早于前一段终点，或前一段没有终点又存在后一段，就拒绝。冲突决定第一次进入快照时，条目窗口必须等于决定正文里审核确认的窗口。以后的快照只能把冲突条目的右端点提前，不能改起点，也不能晚于决定正文里的右端点。Worker 按快照条目的窗口匹配；决定正文保留审核当时的窗口。
6. 插入 `PUBLISHED` 批次、全部条目和审计。
7. 把首次进入快照的契约版本、映射版本和冲突决定改为 `SEALED`，写入正文哈希。发布冲突决定时，同时封存全部候选映射版本及其仍未封存的契约版本。已经封存的行不更新。候选哈希不一致则整笔回滚。

后一个并发事务因为父批次唯一而失败，不留下半成品。回滚是按目标历史快照的视图再发布一个新快照，历史快照不改。

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
  publication_state VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
  body_sha256 CHAR(64) NOT NULL,
  row_version INT NOT NULL DEFAULT 1,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (contract_version_id),
  UNIQUE KEY uk_contract_source_revision (contract_id, source_revision),
  UNIQUE KEY uk_contract_body_sha256 (contract_id, body_sha256),
  KEY idx_contract_version_state (contract_id, publication_state),
  CONSTRAINT ck_contract_revision_positive CHECK (source_revision > 0),
  CONSTRAINT ck_contract_version_state CHECK (publication_state IN ('DRAFT', 'SEALED')),
  CONSTRAINT ck_contract_version_evidence CHECK (evidence_status IN (
    'E1-A', 'E1-B', 'E2', 'E3', 'E4', 'E5', 'E6', 'NONE'))
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
  UNIQUE KEY uk_audit_idempotency (
    request_id, event_type, aggregate_type, aggregate_id),
  KEY idx_audit_aggregate (aggregate_type, aggregate_id, audit_id),
  KEY idx_audit_batch (publish_batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
```

映射条目的唯一键只限制“同一映射版本在一个快照里出现一次”和“同一字段最多一条没有右端点的映射”。它不再禁止同一字段的多段已闭合窗口。冲突条目同样只限制一条没有右端点的决定。跨表的窗口重叠、以及有右端点的窗口重叠，由发布事务检查。MySQL 没有区间排他约束，不能把这项检查伪装成一个简单的路径唯一索引。

`RETIRED` 必须有右端点。没有右端点的当前映射使用 `ACTIVE`。两者都按窗口参加匹配。

审计幂等键的每一列仍然都是非空。创建冲突决定、审核冲突决定和发布冲突决定分别使用 `CONFLICT_DECISION` 聚合和各自的事件名。

## 13. 管理 API 设计

查询用 GET，变更用 POST。下面的角色只是调用约定，不是已经实现的账号、权限或审批系统。服务当前能够强制的是：变更请求必须带 `operatorId`，审核人必须是另一名操作者。`actorRole` 写入审计，供以后的账号系统对照。

建议角色是 `EDITOR`、`REVIEWER`、`PUBLISHER`。没有 `operatorId` 的变更返回 `OPERATOR_REQUIRED`，不写任何行。

响应外层是建议形状，仓库里还没有这个类：

| 字段 | 含义 |
| --- | --- |
| `success` | `true` 或 `false` |
| `data` | 成功时的业务内容。失败时为空对象 |
| `errorCode` | 成功时为空字符串。失败时是下表中的代码 |

幂等键是 `requestId + eventType + aggregateType + aggregateId`，四列都非空。同一键再次提交时返回第一次的结果，不写第二份数据。服务不能证明两次内容相同时返回 `REQUEST_REUSED`，也不生成新快照。每个变更接口只使用一个数据库事务，任何失败都整笔回滚。查询不写库。

发布类接口成功时，`data` 至少包含 `snapshotId`、`parentSnapshotId`、`contentSha256`、`itemCount` 和 `publishResult`。`publishResult` 固定为 `PUBLISHED`。

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

请求：`requestId`、`operatorId`、`actorRole`、`contractVersion`、`evidenceStatus`、`semanticStatus`、`sourceReference`、`contractEffectiveFrom`、`contractEffectiveTo`、`evidences`。官方没有版本号时，`contractVersion` 传“未提供”。

成功：`contractVersionId`、`sourceRevision`、`bodySha256`、`result`。`result` 为 `CREATED` 或 `EXISTING`。指纹已存在时返回原版本，不新增修订。

错误：`OPERATOR_REQUIRED`、`CONTRACT_NOT_FOUND`、`VERSION_SEALED`、`EVIDENCE_INVALID`。

事件：`CREATE_CONTRACT_VERSION`。聚合是 `SOURCE_CONTRACT`，聚合 ID 是 `contractId`。

事务：计算第 14 节的契约正文哈希。哈希已存在则只写审计并返回原 ID。否则插入新版本、证据和审计。已封存版本不在这个接口改正文。

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

错误：`OPERATOR_REQUIRED`、`CONFLICT_NOT_APPROVED`、`CANDIDATE_BODY_CHANGED`、`WINDOW_OVERLAP`、`PUBLISH_PARENT_CONFLICT`、`PARENT_SNAPSHOT_NOT_FOUND`。

事件：`PUBLISH_CONFLICT`。聚合是 `CONFLICT_DECISION`，聚合 ID 是 `conflictDecisionId`。

事务：核对候选正文哈希，封存候选映射版本和相关契约版本，插入新的 `PUBLISHED` 快照。决定未通过审核、候选已被改写或窗口重叠时整笔回滚。

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

### 规范字节

所有哈希都是规范字节的 SHA-256，结果写成 64 位小写十六进制。编码是 UTF-8，不带 BOM，也不做 Unicode 归一化。字符串按库存原值进入字节，不另做 trim。

一条记录内部用单元分隔符 `\x1f` 连接字段。多条记录先按指定键排序，再用记录分隔符 `\x1e` 连接。空字符串就是两个分隔符之间没有字符。SQL NULL 和没有右端点都写成两个字符 `\N`，不能写成空字符串，也不能写成一个遥远的时间。时间一律是 UTC，格式为 `yyyy-MM-dd'T'HH:mm:ss.SSS'Z'`，毫秒必须是三位。整数不用前导零。每一种哈希前面加一行固定版本名，避免以后改字段时和旧哈希混淆。

嵌套内容先算自己的哈希。上层只放入这个 64 位哈希，不再把下层正文重抄一遍。下层任何一个参与字段变化，下层哈希变化，上层哈希也变化。

契约版本 `contract-version-v1` 的字段顺序：`contractVersionId`、`contractId`、`contractVersion`、`sourceRevision`、`evidenceStatus`、`semanticStatus`、`sourceReference`、`contractEffectiveFrom`、`contractEffectiveTo`、证据列表。证据按 `evidenceId` 数值升序，每条顺序是 `evidenceId`、`evidenceStatus`、`sourceReference`、`quotedNote`。封存状态、行版本和时间戳不进入正文哈希，因为封存动作本身不能把正文算成另一份。

映射版本 `mapping-version-v1` 的字段顺序：`mappingVersionId`、`mappingId`、`mappingVersion`、`contractVersionId`、`platform`、`fieldPath`、`sourceDataType`、`candidateAmountTypeCode`、`candidateDisposition`、`mappingEligibility`、`semanticStatus`、`evidenceStatus`、`unitConstraint`、`currencyConstraint`、`precisionConstraint`、`transformationPolicy`、`aggregationPolicy`、证据列表。证据排序和字段与契约证据相同。审核状态不进入正文哈希。封存后审核状态也不允许再改，所以不能靠改审核字段绕过哈希。

冲突决定 `conflict-decision-v1` 的字段顺序：`conflictDecisionId`、`contractVersionId`、`fieldPath`、`conflictType`、`reason`、`approvedEffectiveFrom`、`approvedEffectiveTo`、`authorOperatorId`、`reviewedBy`、`reviewDecision`、候选列表、证据列表。候选按 `mappingVersionId` 升序，每条是 `mappingVersionId`、`candidateBodySha256`。证据按 `evidenceId` 升序。

类型目录 `type-catalog-v1` 的字段顺序：`itemCount`，然后按 `typeCode` 字节升序排列条目。每条是 `typeCode`、`typeLifecycleStatus`、`mappingEligibility`、`semanticStatus`、`aggregationPolicy`。

快照 `snapshot-v1` 的字段顺序：`typeCatalogVersionId`、目录 `contentSha256`、契约条目、映射条目、冲突条目。契约条目按 `contractVersionId` 升序，字段是 `contractVersionId`、`bodySha256`。映射条目按 `mappingVersionId` 升序，字段是 `mappingVersionId`、`contractVersionId`、`fieldPath`、`lifecycleInSnapshot`、`mappingEffectiveFrom`、`mappingEffectiveTo`、`bodySha256`。冲突条目按 `conflictDecisionId` 升序，字段是 `conflictDecisionId`、`contractVersionId`、`fieldPath`、`conflictEffectiveFrom`、`conflictEffectiveTo`、`bodySha256`。

### 完整性校验

Worker 只按任务上的 `snapshotId` 装载。任何一步失败都停止任务。不允许改读最新快照，不允许跳过坏掉的部分，也不允许写回修复。

1. 读取 `publishBatch`。不存在则失败。
2. 读取三类条目。三项数量之和必须等于 `itemCount`。
3. 按 `snapshot-v1` 重算哈希，必须等于 `contentSha256`。
4. 读取目录头和全部目录条目。条目数必须等于目录 `itemCount`，重算结果必须等于目录 `contentSha256`，目录 ID 必须等于批次上的 `typeCatalogVersionId`。
5. 每条契约条目都能读到已封存契约版本，重算正文哈希必须同时等于条目和版本行上的 `bodySha256`。
6. 每条映射条目都能读到已封存映射版本，重算结果必须同时等于条目和版本行上的 `bodySha256`。
7. 每条冲突条目都能读到已封存决定。候选至少两条。每条候选都能读到已封存映射版本，版本重算哈希必须等于 `candidateBodySha256`，也必须等于版本行上的 `bodySha256`。决定按 `conflict-decision-v1` 重算后，必须等于决定行和冲突条目上的 `bodySha256`。
8. 映射条目、冲突条目和候选映射引用的契约版本，都必须出现在该快照的契约条目中。

通过之后才允许按第 8 节匹配。校验失败的任务不能改用另一份快照继续。

新任务第一次开始时，读取没有子批次的最新已发布快照，先做上面的校验，再把 `snapshotId` 写入任务。重试和事后回看都用这个标识。新到达的迟到报文也是先固定当时最新且校验通过的快照，再用 `receivedAt` 命中其中的历史窗口。观测记录同时保存这个 `snapshotId`。

多个 Worker 对同一份通过校验的快照看到同一批条目、同一个目录版本和同一组候选哈希。

匹配使用第 8 节的算法。`processedAt` 不参与选择。`RETIRED` 条目只要窗口包含 `receivedAt` 就命中。

## 15. Pipeline、并发与审计

Pipeline 的顺序不变：原文、原始金额、来源、按固定快照匹配、观测，然后才可能进入已经批准的计算。未经发布的草稿和未确认资格不会进入快照，因此不能进入正式业务计算。

父批次唯一保证并发发布只有一个成功。窗口检查和候选封存在同一事务内完成。审计继续使用全部非空的幂等键。没有配置表达式，转换策略仍只有 `NO_CONVERT`。

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
| 外部版本重复 | 两次“未提供”但正文指纹不同 | 两个 `sourceRevision` 和两个 `contractVersionId` |
| 相同正文 | 再次提交同一指纹 | 返回原版本，不新增修订 |
| 封存后修改 | 修改已进入快照的契约版本 | 被拒绝 |
| 多个“未提供” | 报文只有外部版本号 | `UNMAPPED`，不猜最新修订 |
| 目录变化 | 新目录把类型改为 `ACTIVE` 后发布新快照 | 旧快照仍指向旧目录，类型资格不变 |
| 目录数量 | 条目数和 `itemCount` 不一致 | 封存失败；已封存的目录被改少时 Worker 拒绝 |
| 重试 | 新快照已发布 | 重试仍用原 `snapshotId` 和原目录版本 |
| 快照损坏 | 删除一条候选、证据或目录条目 | `SNAPSHOT_INTEGRITY_FAILED`，不改读最新配置，也不自动修复 |
| 哈希输入稳定 | 同一内容按不同传入顺序计算 | 排序后哈希相同。NULL 和空字符串的哈希不同 |
| 发布幂等 | 同一发布请求重复提交 | 返回第一个 `snapshotId`，不产生第二个批次 |
| 草稿类型发布 | 目录中类型仍是 `DRAFT` | 拒绝，不产生业务映射快照 |
| 未识别计算 | 只有未识别观测 | 执行状态是 `UNMAPPED`，不能进入计算 |

## 17. 风险和待确认事项

MySQL、账号体系和第一份可发布的类型目录都还没有接受。本文的表不能执行，今天也不能发布业务映射。角色名称不是已经上线的权限系统。

跨映射表和冲突表的窗口重叠依赖发布事务。数据库只保证每类条目各自最多一条没有右端点的记录。实现时必须把两类条目放进同一次检查，不能只依赖唯一索引。

候选正文不复制到新表。历史还原依赖封存后的版本行、证据行和候选哈希始终一起保留。只删其中一层，校验就会失败，这是预期结果，不是允许 Worker 忽略的损坏。

本文没有修改 06-03-01、06-03-02-01 和 06-03-02-02。五种映射资格、三种执行状态和 39 个类型编码保持原定义。

## 18. 设计结论

同一份快照可以保留同一字段的多个不重叠窗口。迟到报文按接收时间命中其中的历史映射，包括已经标成 `RETIRED` 的窗口。冲突决定保存候选版本号和正文哈希，并在发布时封存候选版本，因此历史决定可以还原当时比较过的映射正文。官方没有版本号时，用 `sourceRevision` 和正文指纹管理材料修订。类型目录版本写在发布批次上，目录自己记录条目数量和指纹。

Worker 只接受完整通过校验的 `MappingSnapshot`。任一数量、正文或哈希对不上，就停止该任务。它不改读最新配置，也不修复快照。

没有发现用配置状态绕过语义确认的路径。类型不是 `ACTIVE`、资格未确认或被禁止、冲突尚未形成已审核决定，都不能进入正式业务映射。

这是第四轮修订稿，用来提交审查。它还不是冻结设计，也没有批准任何生产金额映射。

## 19. 修订记录

| 轮次 | 内容 |
| --- | --- |
| 初稿 | 建立映射配置、版本、发布准入、建议表结构和 Worker 读取边界 |
| 第二轮 | 用不可变发布批次替换审计号快照，契约身份加入来源种类 |
| 第三轮 | 同一快照保留多段不重叠映射窗口。冲突决定单独存全文。契约材料用内部修订号和正文指纹区分。发布批次固定类型目录版本 |
| 第四轮 | 发布冲突决定时封存候选版本并保存正文哈希。定义快照读取对象和规范哈希。补充管理接口的请求、响应、幂等和事务 |
