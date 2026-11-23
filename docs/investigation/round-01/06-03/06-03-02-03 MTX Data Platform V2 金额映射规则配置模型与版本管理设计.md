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

`DRAFT`、`ACTIVE`、`RETIRED` 仍是 06-03-01 的规则生命周期，但不再做成版本行上可反复改写的一列。未进入任何已完成快照时是 `DRAFT`。进入某个快照后，这一版正文封存；它在该快照里是 `ACTIVE` 还是 `RETIRED`，只写在该快照的条目上。后一个快照可以把同一封存版本看成 `RETIRED`，旧快照里的 `ACTIVE` 不跟着改变。同一份快照也可以同时留下同一字段的多段映射窗口。`RETIRED` 只表示这段窗口已经有右端点，接收时间落在窗口内时仍然要使用它。

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

映射版本行保存规则正文、资格、语义、证据等级和审核人。生效窗口不写在版本行上，而写在发布快照的映射条目上。版本一旦进入已完成快照就封存。

`candidateDisposition` 只放在草稿上。它不是 MappingStatus，Worker 也不读取草稿。

同一路径不能靠两条同时生效、窗口无限重叠的映射来表示冲突。冲突使用第 7 节的冲突决定。

## 6. 状态与生命周期

版本行用 `publicationState` 表示正文是否封存：`DRAFT` 或 `SEALED`。这不替换生命周期。未进入快照时是 `DRAFT`。进入某个快照后正文封存；它在该快照条目上的生命周期仍是 `ACTIVE` 或 `RETIRED`。

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

审核流程与映射草稿相同：编辑人创建，另一人审核。未通过审核的决定不能进入快照。发布时，新快照增加一条冲突条目，指向这个 `conflictDecisionId`，并带上该快照实际使用的窗口。候选映射版本保持未生效。

Worker 只读任务固定的快照。接收时间落在冲突条目窗口内时，读取决定全文，把该字段的 `ObservedAmount.mappingStatus` 写成 `CONFLICT`。同一报文的其他字段继续按自己的条目处理。

解除冲突时发布另一个新快照：把冲突条目的右端点收到解除时刻，并在同一时刻开始一条新的映射窗口。旧快照里的冲突决定和旧窗口都不改。不通过把多条映射同时标成没有右端点的 `ACTIVE` 来制造冲突。

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

批次和全部条目在同一个事务中插入，状态直接是 `PUBLISHED`。失败整笔回滚。父批次唯一。批次保存三类条目的合计数量和 `contentSha256`。哈希覆盖目录版本 ID、三类条目的标识、窗口、生命周期和各自正文哈希。Worker 重算后不一致就拒绝执行。

新发布不修改旧批次。新快照复制父快照的全部历史窗口。退役只在新快照里给被退役的那条补上右端点。

## 10. 发布事务

一次发布、退役、冲突发布或回滚都按下面的顺序做完，否则全部回滚：

1. 按请求号查找已有审计。有则返回原来的 `snapshotId`。
2. 读取父批次。第一个父标识是 `ROOT`。
3. 读取本批次声明的类型目录版本。目录必须已经封存，而且目标类型在该目录中的生命周期和资格满足第 13 节的门槛。
4. 以父快照的全部条目为底稿。历史窗口原样复制。本次接上的新窗口，只在新快照里把前一条的右端点写成新窗口的起点，并把该条标成 `RETIRED`。不删除历史条目，也不回写旧快照。
5. 把同一契约版本、同一字段的映射窗口和冲突窗口放在一起排序。后一段起点早于前一段终点，或前一段没有终点又存在后一段，就拒绝。冲突决定第一次进入快照时，条目窗口必须等于决定正文里审核确认的窗口。以后的快照只能把冲突条目的右端点提前，不能改起点，也不能晚于决定正文里的右端点。Worker 按快照条目的窗口匹配；决定正文保留审核当时的窗口。
6. 插入 `PUBLISHED` 批次、全部条目和审计。
7. 把首次进入快照的契约版本、映射版本和冲突决定改为 `SEALED`，写入正文哈希。已经封存的行不更新。

后一个并发事务因为父批次唯一而失败，不留下半成品。回滚是按目标历史快照的视图再发布一个新快照，历史快照不改。

## 11. 标准金额类型目录追溯

类型定义仍以 06-03-02-01 为来源，本文不另做一套类型审核流程。

为了让历史任务复现当时的资格，增加最小的不可变目录版本：

| 对象 | 职责 |
| --- | --- |
| `amount_type_catalog_release` | 一个 `catalogVersionId` 和整份目录的指纹 |
| `amount_type_catalog_entry` | 该版本中每个类型编码的生命周期、映射资格、语义状态和聚合策略 |

目录版本一旦写下就不能改条目。类型从 `DRAFT` 变成 `ACTIVE` 时，封存一份新目录，不回写旧目录。映射批次记录 `typeCatalogVersionId`。旧批次仍指向旧目录，所以旧快照不受后来的类型状态变化影响。

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
    'CONFLICTING_CANDIDATES', 'NO_AMOUNT', 'BLOCKED_BY_ELIGIBILITY'))
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
    'NOT_SUBMITTED', 'IN_REVIEW', 'APPROVED', 'REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE mapping_conflict_candidate (
  conflict_decision_id VARCHAR(64) NOT NULL,
  mapping_version_id BIGINT NOT NULL,
  PRIMARY KEY (conflict_decision_id, mapping_version_id)
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
  content_sha256 CHAR(64) NOT NULL,
  sealed_at DATETIME(3) NOT NULL,
  PRIMARY KEY (catalog_version_id),
  UNIQUE KEY uk_type_catalog_sha256 (content_sha256)
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
    'ALLOWED_AS_UNIDENTIFIED', 'CONFLICT_ONLY'))
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

查询用 GET，变更用 POST。权限仍是建议角色。每个变更都带 `requestId`。

| 动作 | 方法与路径 | 成功时返回 | 主要错误 |
| --- | --- | --- | --- |
| 创建契约 | `POST /api/admin/source-contracts` | `contractId` | `CONTRACT_DUPLICATE` |
| 查询契约 | `GET /api/admin/source-contracts` | 契约列表 | 无 |
| 新增契约版本 | `POST /api/admin/source-contracts/{contractId}/versions` | 新建的或已存在的 `contractVersionId` | `VERSION_SEALED` |
| 创建映射草稿 | `POST /api/admin/amount-mappings` | `mappingId` 和草稿版本 | `TYPE_UNKNOWN` |
| 编辑草稿 | `POST /api/admin/amount-mappings/{mappingId}/versions/{mappingVersion}/edit` | 新的 `rowVersion` | `VERSION_SEALED` |
| 校验 | `POST /api/admin/amount-mappings/{mappingId}/versions/{mappingVersion}/validate` | 阻断项 | `MAPPING_NOT_FOUND` |
| 提交审核和审核 | `POST /api/admin/amount-mappings/{mappingId}/versions/{mappingVersion}/submit-review` 与 `/review` | 审核状态 | `REVIEWER_IS_AUTHOR` |
| 创建冲突决定 | `POST /api/admin/mapping-conflict-decisions` | `conflictDecisionId` | `CANDIDATE_VERSION_UNKNOWN` |
| 审核冲突决定 | `POST /api/admin/mapping-conflict-decisions/{conflictDecisionId}/review` | 审核结论 | `REVIEWER_IS_AUTHOR` |
| 封存类型目录 | `POST /api/admin/amount-type-catalogs` | `catalogVersionId` | `CATALOG_DUPLICATE` |
| 发布快照 | `POST /api/admin/mapping-snapshots/publish` | 新的 `snapshotId` | `TYPE_NOT_ACTIVE`、`ELIGIBILITY_BLOCKED`、`WINDOW_OVERLAP`、`PUBLISH_PARENT_CONFLICT` |
| 发布冲突决定 | `POST /api/admin/mapping-snapshots/publish-conflict` | 新的 `snapshotId` | `CONFLICT_NOT_APPROVED`、`WINDOW_OVERLAP` |
| 退役 | `POST /api/admin/mapping-snapshots/retire` | 新的 `snapshotId`。新快照给旧映射补上右端点 | `RULE_NOT_IN_PARENT_SNAPSHOT` |
| 读取快照 | `GET /api/admin/mapping-snapshots/{snapshotId}` | 批次、三类条目、目录版本和哈希 | `SNAPSHOT_NOT_FOUND` |
| 回滚 | `POST /api/admin/mapping-snapshots/rollback` | 新的 `snapshotId` | `TARGET_SNAPSHOT_NOT_PUBLISHED` |
| 审计 | `GET /api/admin/amount-mappings/audit` | 作者、审核人、角色和批次号 | 无 |

发布门槛不变：类型在所引用的目录版本里必须是 `ACTIVE`；资格不能是 `NOT_UNTIL_CONFIRMED`、`DISALLOWED` 或 `CONFLICT_ONLY`；业务发布的语义状态必须是 `部分确认`，并且没有未排除的第二种解释。`ALLOWED_AS_UNIDENTIFIED` 只用于 `AMT.UNK.UNIDENTIFIED`，保存后的执行状态仍是 `UNMAPPED`。单位、币种或精度未知时只能保持 `NO_CONVERT`，不能补成元、分或人民币。只靠 E4、E5、E6 不能发布。

## 14. Worker 快照读取与匹配

core 只定义按 `snapshotId` 读取快照的端口。Worker 装配实现，不引用 Admin 内部类。

新任务第一次开始时，读取没有子批次的最新已发布快照，把 `snapshotId` 写入任务。重试和事后回看都用这个标识，不改读最新快照。新到达的迟到报文也是这样固定当时最新快照，再用 `receivedAt` 命中其中的历史窗口。观测记录同时保存这个 `snapshotId`。原快照哈希不一致或目录条目缺失时，任务失败，不更换快照。

多个 Worker 对同一 `snapshotId` 读到同一批条目和同一个 `typeCatalogVersionId`。

匹配使用第 8 节的算法。`processedAt` 不参与选择。`RETIRED` 条目只要窗口包含 `receivedAt` 就命中。

## 15. Pipeline、并发与审计

Pipeline 的顺序不变：原文、原始金额、来源、按固定快照匹配、观测，然后才可能进入已经批准的计算。未经发布的草稿和未确认资格不会进入快照，因此不能进入正式业务计算。

父批次唯一保证并发发布只有一个成功。窗口检查在同一事务内完成。审计继续使用全部非空的幂等键。没有配置表达式，转换策略仍只有 `NO_CONVERT`。

## 16. 测试与验收

本文件不执行这些测试。实现时至少覆盖：

| 测试 | 输入 | 预期 |
| --- | --- | --- |
| 同一快照多段窗口 | 同一字段两条窗口首尾相接 | 两条映射条目都保存 |
| 窗口重叠 | 后一段起点早于前一段终点 | `WINDOW_OVERLAP`，整笔回滚 |
| 左闭右开 | 接收时间等于起点、等于终点 | 起点命中，终点不命中 |
| 迟到报文 | 接收时间落在 `RETIRED` 窗口，处理时间已经更晚 | 命中该历史映射，不变成 `UNMAPPED` |
| 没有右端点 | 同一字段两条都不写右端点 | 唯一索引拒绝第二条 |
| 冲突决定还原 | 发布一条含两个候选和证据的决定 | 能读回 ID、候选、原因、审核人、结论、窗口和哈希 |
| 解除冲突 | 新快照结束冲突窗口并开始一条映射 | 旧快照的冲突条目不变 |
| 外部版本重复 | 两次“未提供”但正文指纹不同 | 两个 `sourceRevision` 和两个 `contractVersionId` |
| 相同正文 | 再次提交同一指纹 | 返回原版本，不新增修订 |
| 封存后修改 | 修改已进入快照的契约版本 | 被拒绝 |
| 多个“未提供” | 报文只有外部版本号 | `UNMAPPED`，不猜最新修订 |
| 目录变化 | 新目录把类型改为 `ACTIVE` 后发布新快照 | 旧快照仍指向旧目录，类型资格不变 |
| 重试 | 新快照已发布 | 重试仍用原 `snapshotId` 和原目录版本 |
| 草稿类型发布 | 目录中类型仍是 `DRAFT` | 拒绝，不产生业务映射快照 |
| 未识别计算 | 只有未识别观测 | 执行状态是 `UNMAPPED`，不能进入计算 |

## 17. 风险和待确认事项

MySQL、账号体系和第一份可发布的类型目录都还没有接受。本文的表不能执行，今天也不能发布业务映射。

跨映射表和冲突表的窗口重叠依赖发布事务。数据库只保证每类条目各自最多一条没有右端点的记录。实现时必须把两类条目放进同一次检查，不能只依赖唯一索引。

本文没有修改 06-03-01、06-03-02-01 和 06-03-02-02。五种映射资格、三种执行状态和 39 个类型编码保持原定义。

## 18. 设计结论

同一份快照可以保留同一字段的多个不重叠窗口。迟到报文按接收时间命中其中的历史映射，包括已经标成 `RETIRED` 的窗口。冲突决定以决定头、候选行和证据行保存，可以完整还原，历史快照不因后来解除冲突而被改写。官方没有版本号时，用 `sourceRevision` 和正文指纹管理材料修订。类型目录版本写在发布批次上，旧任务不跟随后来的类型状态。

没有发现用配置状态绕过语义确认的路径。类型不是 `ACTIVE`、资格未确认或被禁止、冲突尚未形成已审核决定，都不能进入正式业务映射。

这是第三轮修订稿，用来提交审查。它还不是冻结设计，也没有批准任何生产金额映射。

## 19. 修订记录

| 轮次 | 内容 |
| --- | --- |
| 初稿 | 建立映射配置、版本、发布准入、建议表结构和 Worker 读取边界 |
| 第二轮 | 用不可变发布批次替换审计号快照，契约身份加入来源种类 |
| 第三轮 | 同一快照保留多段不重叠映射窗口。冲突决定单独存全文。契约材料用内部修订号和正文指纹区分。发布批次固定类型目录版本 |
