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

与任务基线的差异只有数据库：任务要求按 MySQL 设计配置表，ADR-0002 仍写 MySQL 未决策。因此第 9 节的表是“若配置库采用 MySQL 时的结构”。在该决策接受之前，不能创建这些表，也不能在模块里引入 MySQL 驱动。

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

`DRAFT`、`ACTIVE`、`RETIRED` 仍是 06-03-01 的规则生命周期，但不再做成版本行上可反复改写的一列。未进入任何已完成快照时是 `DRAFT`。进入某个快照后，这一版正文封存；它在该快照里是 `ACTIVE` 还是 `RETIRED`，只写在该快照的条目上。后一个快照可以把同一封存版本看成 `RETIRED`，旧快照里的 `ACTIVE` 不跟着改变。

## 4. SourceContract 配置模型

一个平台有多份契约。京东创建消息、京东查询摘录和京东代码发现字段是三份契约。美团官方页和回调代码路径是两份契约。淘宝详情查询和消息 217 也是两份契约。官方材料和代码调查不能因为字段名相同而合并。

`sourceType` 属于契约家族，不属于版本，也不单独再做一层“证据来源”对象。家族回答“这是哪一份来源”。版本回答“这份来源的哪一版材料”。证据行回答“这个版本依据了哪些材料”。来源种类如果写错，必须新建另一个家族；旧家族的 `contractId` 不变，只停止继续加版本。

因此唯一身份是：

`platform + sourceSystem + sourceInterface + sourceMessageType + sourceType`

上一版的唯一索引没有 `sourceType`，来源种类写错后无法新建同接口的另一个家族。本轮把 `sourceType` 放进唯一索引。它创建后不可修改，版本行也不再复制一份，避免两处将来不一致。

| 对象 | 职责 | 唯一性 |
| --- | --- | --- |
| SourceContract | 稳定家族。包含来源种类 | 上面的五元组。`contractId` 只生成一次 |
| SourceContractVersion | 该家族的一份不可覆盖材料 | 家族内 `contractVersion` 不重复 |
| SourceContractEvidence | 该版本的证据，只追加 | 不把证据种类当成第二份契约 |

`sourceType` 仍只允许 `OFFICIAL_DOCUMENT`、`OFFICIAL_MESSAGE_PROTOCOL`、`CODE_INVESTIGATION`、`BUSINESS_CONFIRMED`、`OTHER_EVIDENCE`。`CODE_INVESTIGATION` 不是官方契约，没有接口把它改成 `OFFICIAL_DOCUMENT`。

没有消息类型时写入空字符串，不写 NULL。没有官方版本号时，`contractVersion` 写入“未提供”，不表示永远有效。

契约版本在进入已发布快照之前可以编辑。一旦被某个已完成快照引用，版本行就封存，不能改正文。某个快照里它是否仍可供新报文引用，记在该快照的条目上，不回写旧快照。

## 5. AmountMapping 配置模型

`mappingId` 沿用 06-03-01：同一契约家族、同一字段路径、同一个目标类型，是一条映射。正文变化增加 `mappingVersion`。路径或目标类型变化则新建 `mappingId`。

映射版本行保存规则正文、资格、语义、证据等级和审核人。它不保存会随下一次发布改变的生效窗口。窗口写在发布快照条目里。版本一旦进入已完成快照就封存。

`candidateDisposition` 只放在草稿上，表示设计评估。它不是 MappingStatus，Worker 也不读取草稿。

十个实现问题的推荐保持不变，并补充一条：同一路径的现行规则由“某个已完成快照中的条目”表达。不能靠把多条冲突规则同时标成 `ACTIVE` 来表示冲突。

## 6. 状态与生命周期

版本行用 `publicationState` 表示正文是否封存：`DRAFT` 或 `SEALED`。这不是用来替换生命周期。`SEALED` 只表示正文已经进入快照且不能再改。该版本在某一份快照中的生命周期，仍是条目上的 `ACTIVE` 或 `RETIRED`。

一个快照内部，对某个契约版本的某个字段，条目只能是下面三种之一：

| 快照条目 | 含义 | 不是 |
| --- | --- | --- |
| 一条生效映射 | 该快照认为这条映射版本可用于匹配 | 修改旧快照 |
| 一条已退役映射 | 该快照认为它已经离开生效窗口 | 删除历史条目 |
| 一条字段冲突决定 | 该快照要求这个字段写成运行时 `CONFLICT` | 第二条生效映射 |

审核状态仍是 `NOT_SUBMITTED`、`IN_REVIEW`、`APPROVED`、`REJECTED`。它只存在于尚未封存的草稿上。

创建草稿时写入 `authorOperatorId`。提交审核时写入 `submittedBy`。审核时写入 `reviewedBy` 和审核结论。审核人必须是另一名操作者，不能靠“最后一次编辑的人”推断作者。

## 7. 配置冲突和运行时 CONFLICT

这两件事分开。

配置候选冲突发生在发布前。同一契约版本、同一字段有多个目标类型，或者 `candidateDisposition` 是 `MULTI_CANDIDATE` 或 `CONFLICTING_CANDIDATES` 时，不能把其中一条发布成生效映射。发布器直接拒绝，数据库的快照约束也不允许同一快照里出现两条生效映射。

如果审核结论就是“这个字段不能选定类型”，发布的不是映射，而是一条字段冲突决定。决定列出参与比较的草稿版本，但那些版本不会变成生效映射。Worker 读到这条决定后，只把该字段的 `ObservedAmount.mappingStatus` 写成 `CONFLICT`。

因此运行时 `CONFLICT` 只有一个来源：当前任务固定的快照里，这个契约版本和字段有一条冲突决定，并且报文时间落在该决定的适用窗口内。它不来自两条同时生效的映射，也不来自 Worker 临时比较最新配置。

同一报文的其他字段各自匹配自己的快照条目。一个字段冲突，不改变其他字段的原文和匹配结果。

未识别保存仍然不是 `MAPPED`，也不是 `CONFLICT`。没有生效映射、也没有冲突决定时，结果是 `UNMAPPED`。

## 8. 时间模型

四个时间不能混用：

| 名称 | 含义 | 用来做什么 |
| --- | --- | --- |
| `contractEffectiveFrom` / `contractEffectiveTo` | 这份契约版本材料被记录的适用说明 | 只随快照条目保存。不用来猜测报文属于哪一版契约 |
| `mappingEffectiveFrom` / `mappingEffectiveTo` | 一条映射在某个快照视图中的适用窗口 | 契约版本已经确定后，用 `receivedAt` 选择映射 |
| `receivedAt` | 报文接收时间 | 唯一参与映射窗口判断的业务时间 |
| `processedAt` | Worker 开始或重试的处理时间 | 只记运行日志。不参与选版 |

匹配顺序：

1. 用报文上的契约身份和契约版本号找到 `contractVersionId`。找不到，或者该版本不在任务固定的快照里，结果是 `UNMAPPED`。
2. 不按接收时间、处理时间或“最新生效契约”挑选契约版本。
3. 契约版本确定后，只在该快照的条目中查找这个字段。生效映射的窗口必须包含 `receivedAt`：起点小于等于接收时间，终点为空或晚于接收时间。
4. 若该窗口内是字段冲突决定，写 `CONFLICT`。
5. 迟到报文仍使用原来的 `snapshotId` 和自己的 `receivedAt`。处理时间再晚，也不改用后来的快照。
6. 报文没有契约版本时，不猜测，结果是 `UNMAPPED`，原文仍保存。

同一个快照内，同一契约版本和字段的生效映射窗口不能重叠，冲突决定的窗口也不能和生效映射重叠。MySQL 没有区间排他约束，所以由发布事务在写入条目前检查。两个发布不能同时基于同一个父快照提交，因此不会各自通过检查后同时留下重叠窗口。

## 9. 不可变发布快照

不再使用 `audit_id` 或“当前最大审计号”作为快照号。审计会继续增长，不能代表一组永不改变的规则。

快照就是发布批次。不另建第三张内容相同的快照表。

| 对象 | 职责 |
| --- | --- |
| `mapping_publish_batch` | 一个 `batchId`，也就是 `snapshotId`。完成后状态只有 `PUBLISHED` |
| `mapping_publish_batch_item` | 这个快照包含的契约版本、映射版本或字段冲突决定，以及当时的窗口 |

要求：

- `batchId` 独立生成，不复用审计号。
- 条目写明 `contractVersionId`，映射条目再写明 `mappingVersionId`。
- 批次和全部条目在同一个事务里插入，状态直接是 `PUBLISHED`。失败则整笔回滚，不留下可以被 Worker 读到的半成品。
- 不提供修改条目的接口。
- 每个批次记录父批次。父批次唯一，所以同一个父快照只能长出一个子快照。
- 批次保存条目数量和正文哈希。Worker 读完后重新计算，不一致就视为快照损坏。
- 新发布生成新批次，不更新旧批次的条目。映射退役只改变新批次里的视图。

Worker 只查询 `PUBLISHED` 批次。历史任务保存自己的 `batchId`，以后按这个标识重读同一组条目。

## 10. 发布事务

一次发布或退役做完下面的事，否则全部回滚：

1. 按请求号查找是否已经有同一操作的审计。有则返回原来的 `batchId`。
2. 读取父批次。第一个批次的父标识是 `ROOT`。
3. 校验类型目录、映射资格、语义、证据、作者和审核人。任一发布门槛不满足就停止。
4. 以父批次的全部条目为底稿，应用这一次变更，得到新的完整条目集合。
5. 检查新集合里没有重复生效路径，也没有重叠窗口。
6. 插入状态为 `PUBLISHED` 的批次和全部条目。
7. 把本次首次进入快照的版本行改为 `SEALED`，写入封存哈希。已经封存的行不更新正文。
8. 写审计，带上新的 `publishBatchId`。

并发时，后一个事务因为父批次唯一而失败，返回 `PUBLISH_PARENT_CONFLICT`。它不会留下批次，也不会把版本改到一半。

回滚同样是一次新发布：新批次的条目恢复到目标历史快照中的映射视图，并加上新的批次标识。目标历史批次本身不修改。

## 11. 数据库详细设计

这些表仍依赖尚未接受的 MySQL 决策。不建物理外键。配置和审计不自动删除。观测表仍不在本文创建。

| 表 | 决定 | 理由 |
| --- | --- | --- |
| `source_contract` | 保留并修改唯一索引 | 家族身份必须包含 `sourceType` |
| `source_contract_version` | 保留 | 版本正文封存 |
| `source_contract_evidence` | 保留 | 多份证据只追加 |
| `amount_mapping` | 保留 | 映射身份 |
| `amount_mapping_version` | 修改 | 去掉可变生效窗口，增加作者和审核人 |
| `amount_mapping_evidence` | 保留 | 规则证据 |
| `mapping_publish_batch` | 新增 | 不可变快照头 |
| `mapping_publish_batch_item` | 新增 | 不可变快照条目 |
| `amount_mapping_audit_log` | 修改 | 幂等键不再含 NULL |
| 审批表、独立发布记录表、另一张快照表 | 不建 | 审核在版本行和审计里，发布批次本身就是快照 |

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
  evidence_status VARCHAR(16) NOT NULL,
  semantic_status VARCHAR(32) NOT NULL,
  source_reference VARCHAR(512) NOT NULL,
  publication_state VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
  body_sha256 CHAR(64) NOT NULL DEFAULT '',
  row_version INT NOT NULL DEFAULT 1,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (contract_version_id),
  UNIQUE KEY uk_contract_version_no (contract_id, contract_version),
  KEY idx_contract_version_state (contract_id, publication_state),
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

CREATE TABLE mapping_publish_batch (
  batch_id VARCHAR(64) NOT NULL,
  parent_batch_id VARCHAR(64) NOT NULL,
  batch_status VARCHAR(16) NOT NULL,
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

CREATE TABLE mapping_publish_batch_item (
  batch_id VARCHAR(64) NOT NULL,
  item_seq INT NOT NULL,
  item_kind VARCHAR(32) NOT NULL,
  contract_version_id VARCHAR(64) NOT NULL,
  mapping_version_id BIGINT NOT NULL DEFAULT 0,
  field_path VARCHAR(512) NOT NULL DEFAULT '',
  lifecycle_in_snapshot VARCHAR(16) NOT NULL,
  contract_effective_from DATETIME(3) NULL,
  contract_effective_to DATETIME(3) NULL,
  mapping_effective_from DATETIME(3) NULL,
  mapping_effective_to DATETIME(3) NULL,
  body_sha256 CHAR(64) NOT NULL,
  path_claim VARCHAR(600) GENERATED ALWAYS AS (
    CASE
      WHEN item_kind = 'MAPPING_RULE' AND lifecycle_in_snapshot = 'ACTIVE'
        THEN CONCAT(contract_version_id, '#', field_path)
      WHEN item_kind = 'FIELD_CONFLICT'
        THEN CONCAT(contract_version_id, '#', field_path)
      ELSE NULL
    END
  ) STORED,
  PRIMARY KEY (batch_id, item_seq),
  UNIQUE KEY uk_snapshot_path_claim (batch_id, path_claim),
  KEY idx_snapshot_lookup (batch_id, contract_version_id, field_path),
  CONSTRAINT ck_snapshot_item_kind CHECK (item_kind IN (
    'CONTRACT_VERSION', 'MAPPING_RULE', 'FIELD_CONFLICT')),
  CONSTRAINT ck_snapshot_item_life CHECK (lifecycle_in_snapshot IN (
    'REFERENCEABLE', 'ACTIVE', 'RETIRED', 'CONFLICT'))
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

`path_claim` 只在生效映射和字段冲突决定上有值，并且两者使用同一个契约版本加路径。唯一索引因此同时禁止两条生效映射，以及“一条生效映射再加一条冲突决定”。退役条目不占用这个键。NULL 不参与冲突，所以同一快照可以保留多条历史退役条目。

审计唯一键的每一列都是 `NOT NULL`。不适用的值用空字符串，避免 MySQL 对 NULL 不去重。

幂等键：

| 操作 | eventType | aggregateType | aggregateId |
| --- | --- | --- | --- |
| 创建契约 | `CREATE_CONTRACT` | `SOURCE_CONTRACT` | 平台、系统、接口、消息类型、来源种类拼成的身份 |
| 新增契约版本 | `CREATE_CONTRACT_VERSION` | `SOURCE_CONTRACT_VERSION` | `contractId` 加版本号 |
| 创建映射 | `CREATE_MAPPING` | `AMOUNT_MAPPING` | `contractId`、字段路径、目标类型 |
| 编辑草稿 | `EDIT_DRAFT` | `AMOUNT_MAPPING_VERSION` | `mappingVersionId` 加提交时的 `rowVersion` |
| 提交审核 | `SUBMIT_REVIEW` | `AMOUNT_MAPPING_VERSION` | `mappingVersionId` |
| 审核 | `APPROVE` 或 `REJECT` | `AMOUNT_MAPPING_VERSION` | `mappingVersionId` |
| 发布 | `PUBLISH` | `PUBLISH_BATCH` | 请求号。成功后审计保存实际 `batchId` |
| 退役 | `RETIRE` | `PUBLISH_BATCH` | 请求号 |
| 回滚 | `ROLLBACK` | `PUBLISH_BATCH` | 请求号加目标历史 `batchId` |

重复提交返回第一次的结果，不生成第二个快照。

## 12. 管理 API 设计

接口仍在 `app-admin`。查询用 GET，变更用 POST。响应外层仍是建议形状。权限仍是建议角色，不是已实现的账号系统。每个变更都带 `requestId`。

| 动作 | 方法与路径 | 成功时返回 | 主要错误 |
| --- | --- | --- | --- |
| 创建契约 | `POST /api/admin/source-contracts` | `contractId` | `CONTRACT_DUPLICATE` |
| 查询契约 | `GET /api/admin/source-contracts` | 契约列表 | 无 |
| 契约详情 | `GET /api/admin/source-contracts/{contractId}` | 家族和版本 | `CONTRACT_NOT_FOUND` |
| 新增契约版本 | `POST /api/admin/source-contracts/{contractId}/versions` | `contractVersionId` | `VERSION_DUPLICATE` |
| 创建映射草稿 | `POST /api/admin/amount-mappings` | `mappingId` 和草稿版本 | `TYPE_UNKNOWN` |
| 编辑草稿 | `POST /api/admin/amount-mappings/{mappingId}/versions/{mappingVersion}/edit` | 新的 `rowVersion` | `VERSION_SEALED` |
| 校验 | `POST /api/admin/amount-mappings/{mappingId}/versions/{mappingVersion}/validate` | 阻断项 | `MAPPING_NOT_FOUND` |
| 提交审核 | `POST /api/admin/amount-mappings/{mappingId}/versions/{mappingVersion}/submit-review` | 审核中 | `VALIDATION_FAILED` |
| 审核 | `POST /api/admin/amount-mappings/{mappingId}/versions/{mappingVersion}/review` | 通过或驳回 | `REVIEWER_IS_AUTHOR` |
| 发布 | `POST /api/admin/mapping-snapshots/publish` | 新的 `snapshotId` | `TYPE_NOT_ACTIVE`、`ELIGIBILITY_BLOCKED`、`SEMANTIC_NOT_READY`、`WINDOW_OVERLAP`、`PUBLISH_PARENT_CONFLICT` |
| 发布冲突决定 | `POST /api/admin/mapping-snapshots/publish-conflict` | 新的 `snapshotId` | `CONFLICT_HAS_ACTIVE_RULE` |
| 退役 | `POST /api/admin/mapping-snapshots/retire` | 新的 `snapshotId` | `RULE_NOT_IN_PARENT_SNAPSHOT` |
| 读取快照 | `GET /api/admin/mapping-snapshots/{snapshotId}` | 批次头、条目数和哈希 | `SNAPSHOT_NOT_FOUND` |
| 历史版本 | `GET /api/admin/amount-mappings/{mappingId}/versions` | 封存前后的版本 | `MAPPING_NOT_FOUND` |
| 比较 | `GET /api/admin/mapping-snapshots/diff` | 两个快照的条目差异 | `SNAPSHOT_NOT_FOUND` |
| 回滚 | `POST /api/admin/mapping-snapshots/rollback` | 新的 `snapshotId` | `TARGET_SNAPSHOT_NOT_PUBLISHED` |
| 审计 | `GET /api/admin/amount-mappings/audit` | 含作者、审核人、角色和批次号 | 无 |

发布、退役和回滚都创建新快照。校验不写库。Worker 不调用这些接口。

发布门槛与上一轮相同：类型必须已经是 `ACTIVE`，资格不能是 `NOT_UNTIL_CONFIRMED`、`DISALLOWED` 或 `CONFLICT_ONLY`，未识别类型不能伪装成业务映射，只靠 E4、E5、E6 不能发布。按今天的类型目录，业务映射发布仍会全部被拒绝。

## 13. Worker 快照读取与任务重试

core 只定义端口：按 `snapshotId` 读取一个已完成快照。实现由 Worker 装配。Worker 不引用 Admin 内部类，也不读取草稿。

新任务开始时，读取没有子批次的那个已发布批次，得到最新 `snapshotId`，并写进任务记录。这个动作只发生在任务第一次开始时。

任务重试必须继续使用任务记录里的原 `snapshotId`。不能因为重试而重新读取最新快照。原快照读取失败或哈希不一致时，重试失败，不改用别的快照。

Worker 缓存的键是 `snapshotId`。缓存内容包含条目和哈希。同一个标识再次读取时，条目数和哈希必须与批次头一致，也必须与封存版本行的 `body_sha256` 一致。不一致就拒绝执行。

多个 Worker 使用同一个 `snapshotId` 时，读到的是同一批不可变条目。它们不靠本地时钟对齐。

正在执行的任务不刷新快照。新发布只影响还没有固定 `snapshotId` 的新任务。历史任务可以按原标识重新读取规则，因为旧条目不被后来的退役修改。

快照不可用时，新任务失败并记录原因。不允许改用草稿或内存中一份无法对上哈希的旧缓存。

## 14. Pipeline 集成

顺序仍是原文、原始金额、来源、按固定快照匹配、观测，然后才可能进入已经批准的计算。映射模块不计算合计。

匹配使用第 8 节的顺序。Pipeline 只依赖 core 端口，不依赖 Admin、MySQL、Redis 或 Kafka。未经发布的草稿不会出现在快照里，因此不能进入 Pipeline。

观测除了原来的契约版本和映射版本，还要保存 `snapshotId`。没有命中映射时，映射版本为空字符串，不能写成 NULL 后再在唯一索引里失去含义；观测存储本身不在本文建表。

## 15. 并发、安全与审计

父批次唯一保证一次只提交一个子快照。事务失败不留下 `PUBLISHED` 批次，也不会只封存版本而不写条目。

没有配置表达式。转换策略仍只有 `NO_CONVERT`。来源种类没有修改接口。没有可区分的操作者时，审核和发布必须失败。

审计保存聚合类型、聚合标识、事件、请求号、操作者、角色、前后状态、原因、批次号和创建时间。发布类事件的批次号指向新快照。其他事件用空字符串。

## 16. 测试与验收

本文件不执行这些测试。实现时至少覆盖：

| 测试 | 输入 | 预期 |
| --- | --- | --- |
| 快照不可变 | 发布第二个快照后读取第一个 | 条目、窗口和哈希都不变 |
| 发布失败 | 事务中途违反唯一约束 | 没有 `PUBLISHED` 批次，版本仍是草稿 |
| 并发发布 | 两个请求使用同一父快照 | 一个成功，另一个 `PUBLISH_PARENT_CONFLICT` |
| 来源种类 | 同一接口分别用官方和代码调查创建 | 两个 `contractId` 都成功 |
| 来源种类修改 | 更新已有家族的 `sourceType` | 没有这个接口，请求被拒绝 |
| 草稿类型发布 | 类型快照仍是 `DRAFT` | 拒绝，不产生快照 |
| 三种禁止资格 | `NOT_UNTIL_CONFIRMED`、`DISALLOWED`、`CONFLICT_ONLY` | 都拒绝 |
| 配置冲突 | 两个候选试图一起生效 | 拒绝，快照里没有两条生效映射 |
| 冲突决定 | 发布一条字段冲突决定 | 该字段运行时得到 `CONFLICT`，相邻字段仍按自己的规则 |
| 窗口重叠 | 新条目与同一路径的生效窗口重叠 | `WINDOW_OVERLAP`，整笔回滚 |
| 迟到报文 | 原快照中的窗口包含 `receivedAt`，处理时间已经更晚 | 仍按原快照和接收时间命中 |
| 缺少契约版本 | 报文没有版本号 | `UNMAPPED`，不选最新契约 |
| 重试 | 任务已经固定旧快照，之后又发布了新快照 | 重试仍读旧 `snapshotId` |
| 快照损坏 | 条目哈希与批次头不一致 | Worker 拒绝执行，不更换快照 |
| 幂等 | 同一请求号和同一事件重复提交 | 返回第一次的批次或对象，不新增第二份 |
| 审核人 | 作者审核自己的草稿 | `REVIEWER_IS_AUTHOR`，审计里作者和审核人分别可见 |
| 未识别 | 快照没有业务规则 | 保存原文和未识别类型，执行状态是 `UNMAPPED`，不能进入计算 |
| 未知单位 | 单位未知且不是 `NO_CONVERT` | 校验失败，不能发布 |

## 17. 风险和待确认事项

MySQL、账号体系和机器可读的类型目录仍未接受。今天仍不能建表，也不能发布业务映射。

契约适用时间只作为快照中的记录，不自动识别报文。若以后要按时间推断契约版本，必须单独设计，不能在 Worker 里临时使用处理时间。

重处理历史报文仍要另开批次，并写明使用哪个 `snapshotId`。本阶段不把重处理表放进配置模型。

本轮没有修改 06-03-01、06-03-02-01 和 06-03-02-02。映射身份、五种资格和三种执行状态都保持原定义。

## 18. 设计结论

相同的 `snapshotId` 永远对应同一组已封存条目。两个 Worker 读取同一标识并通过哈希校验后，得到同一组规则。任务重试使用原标识，不读取最新规则。后一次发布里的退役只写入新快照，不改变旧任务已经固定的快照。

草稿、未确认资格、禁止资格和仅有代码证据的规则都不会进入已完成快照，因此也不会进入 Pipeline。字段冲突通过快照中的一条冲突决定变成运行时 `CONFLICT`，不是通过两条同时生效的映射。

这仍是第二轮修订稿，用来提交审查。它还不是冻结设计，也没有批准任何生产金额映射。

## 19. 修订记录

| 轮次 | 内容 |
| --- | --- |
| 初稿 | 建立映射配置、版本、发布准入、建议表结构、Admin 接口和 Worker 读取边界 |
| 第二轮 | 用不可变发布批次替换审计号快照。契约唯一身份加入来源种类。运行时冲突改为快照中的字段决定。生效窗口与接收时间、处理时间分开。审计幂等键改为全部非空 |
