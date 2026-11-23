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

## 4. SourceContract 配置模型

一个平台有多份契约。京东创建消息、京东查询摘录、京东代码发现字段是三份契约。美团 docDetail/175 和回调代码路径是两份契约。淘宝详情查询和消息 217 也是两份契约。官方材料和代码调查不能因为字段名相同而合并。

三个对象：

| 对象 | 职责 | 唯一性 |
| --- | --- | --- |
| SourceContract | 契约家族的稳定身份：平台、来源系统、接口、消息类型、来源种类 | `contractId`。同一平台、系统、接口、消息类型只建一个家族 |
| SourceContractVersion | 该家族的一个不可覆盖版本 | `contractVersionId`。同一家族内 `contractVersion` 不重复 |
| SourceContractEvidence | 这个版本依赖的证据 | 一条证据一行。删除证据不被允许，只能追加 |

`sourceType` 只允许：

| 值 | 含义 |
| --- | --- |
| `OFFICIAL_DOCUMENT` | 读到的官方接口或字段页 |
| `OFFICIAL_MESSAGE_PROTOCOL` | 官方消息协议 |
| `CODE_INVESTIGATION` | 旧代码或仓库摘录没有证明为现行官方契约的调查来源 |
| `BUSINESS_CONFIRMED` | 已有业务或财务书面确认的来源 |
| `OTHER_EVIDENCE` | 其他已登记证据。使用时必须写明证据等级 |

`CODE_INVESTIGATION` 不是官方契约。它不能通过改 `sourceType` 变成 `OFFICIAL_DOCUMENT`。来源种类写错时，新建契约家族，旧家族退役。

契约版本从 `DRAFT` 开始。成为 `ACTIVE` 只表示这份材料可以被新的映射草稿引用，不表示字段语义已经确认，更不表示映射规则已经生效。旧版本改为 `RETIRED` 后仍保留。没有更新覆盖接口。

契约版本上的 `contractVersion` 没有官方版本号时，必须显式写入“未提供”，不能留空，也不能默认成“永远有效”。

建议字段与第 5 节的用户清单对齐：`contractId`、`contractVersionId`、`platform`、`sourceSystem`、`sourceInterface`、`sourceMessageType`、`contractVersion`、`sourceType`、`evidenceStatus`、`semanticStatus`、`effectiveFrom`、`effectiveTo`、`lifecycleStatus`、`sourceReference`、`createdAt`、`updatedAt`。稳定身份放在家族上，版本行冗余保存平台和来源种类，避免 Worker 为了过滤再依赖 Admin。冗余字段必须与家族一致，由同一次写入校验。

## 5. AmountMapping 配置模型

`mappingId` 沿用 06-03-01：同一平台、同一契约家族、同一字段路径、同一个目标类型，是一条映射。正文变化增加 `mappingVersion`，不更换 `mappingId`。

字段路径变了，或者目标类型变了，就新建 `mappingId`，旧规则退役。不把路径变化伪装成同一条规则的新版本，否则旧观测会指向一个已经改变含义的身份。

一条映射版本至少包含：`mappingId`、`mappingVersion`、`contractVersionId`、`platform`、`fieldPath`、`sourceDataType`、`candidateAmountTypeCode`、`mappingEligibility`、`semanticStatus`、`evidenceStatus`、`unitConstraint`、`currencyConstraint`、`precisionConstraint`、`transformationPolicy`、`aggregationPolicy`、`lifecycleStatus`、`effectiveFrom`、`effectiveTo`、`createdAt`、`updatedAt`。`candidateDisposition` 可以附在草稿上，只作设计痕迹。

十个实现问题的推荐答案：

| 问题 | 推荐 | 不采用的做法 |
| --- | --- | --- |
| 一个字段能否映射多个标准类型 | 草稿阶段可以有多条不同 `mappingId` 的候选。同一契约版本、同一路径，同时只允许一条 `ACTIVE` | 一条 ACTIVE 规则带两个正式类型 |
| 多个候选如何存储 | 每个候选一条映射身份。设计评估写入 `candidateDisposition` | 把 `MULTI_CANDIDATE` 写成执行状态 |
| 如何避免冲突的 ACTIVE | 数据库用“仅 ACTIVE 时生效”的唯一键，发布事务用乐观锁复核 | 先查再插入且不带条件更新 |
| 一条规则如何关联多份证据 | `amount_mapping_evidence` 追加多行 | 把证据塞进一个不可查询的长文本 |
| 一个类型如何被多平台使用 | 类型编码不是唯一键。每个平台、每份契约各有自己的 `mappingId` | 按类型编码全局只留一条映射 |
| 路径变化如何留版本 | 新路径是新 `mappingId` | 在旧身份下直接改路径 |
| 历史观测如何留旧版本 | 观测保存 `mappingId + mappingVersion + contractVersionId`。规则退役不更新观测 | 用当前规则解释旧观测 |
| 能否配置转换表达式 | 不能。`transformationPolicy` 只允许封闭代码，当前只有 `NO_CONVERT` | 保存脚本、SpEL 或 SQL 表达式 |
| 如何避免任意代码执行 | 策略代码只能随 core 的已审查枚举发布。Admin 不能提交新代码 | 用配置热加载执行用户输入 |
| 租户或业务线是否独立映射 | 业务线差异用不同 SourceContract 表达。租户模型尚未设计，配置主键不含租户 | 现在就加必填 `tenantId` |

`mappingEligibility` 由类型目录决定，配置只保存发布时读到的值，供审计。它不能把 `DISALLOWED` 改成允许。

单位、币种、精度可以显式是 `UNKNOWN`，但必须同时是 `NO_CONVERT`。未知不能换算，也不能默认成人民币、元或分。

## 6. 状态与生命周期

映射规则：

```text
创建 DRAFT / NOT_SUBMITTED
  -> 编辑，仍是 DRAFT
  -> 提交审核 IN_REVIEW
      -> 驳回 REJECTED，改回 NOT_SUBMITTED 后才能再编辑
      -> 通过 APPROVED，生命周期仍是 DRAFT
  -> 发布 ACTIVE，写 effectiveFrom
  -> 退役 RETIRED，写 effectiveTo
```

已是 `ACTIVE` 或 `RETIRED` 的版本行禁止更新正文。回滚不修改旧行，而是按旧正文新建一个版本再发布。

执行结果与规则状态的关系：

| 运行时情况 | ObservedAmount.mappingStatus |
| --- | --- |
| 任务固定的快照里，该路径有一条适用的 `ACTIVE` 业务映射，且类型资格允许 | `MAPPED` |
| 没有适用规则、契约没有金额、规则仍是草稿，或只保存了未识别金额 | `UNMAPPED` |
| 同一路径在同一次执行中被写成两个正式类型，或两个解释都不能排除 | `CONFLICT` |

未识别保存可以带 `AMT.UNK.UNIDENTIFIED`，语义保持无法验证，不参与计算。它的执行状态仍是 `UNMAPPED`，不能记成业务映射成功。

契约版本无法识别时，不套用“最新 ACTIVE 契约”。执行结果是 `UNMAPPED`，原文仍保存。

## 7. 映射准入与发布治理

发布前必须同时满足：

1. `SourceContractVersion` 已存在，身份字段完整，且该版本本身是 `ACTIVE`。
2. 字段路径有证据行支持，证据等级不是 `NONE`。
3. `candidateAmountTypeCode` 存在于冻结的 39 个编码中。
4. 该类型的 `typeLifecycleStatus` 是 `ACTIVE`。`DRAFT` 和 `RETIRED` 都不能发布。因此按今天的类型目录，全部发布请求都会停在这里。
5. `mappingEligibility` 是 `ALLOWED_IF_CONTRACT_DEFINES` 或 `ALLOWED_AS_UNIDENTIFIED`。`NOT_UNTIL_CONFIRMED`、`DISALLOWED`、`CONFLICT_ONLY` 都拒绝。
6. `ALLOWED_AS_UNIDENTIFIED` 只能指向 `AMT.UNK.UNIDENTIFIED`。它发布后仍不能把观测写成业务 `MAPPED`。
7. 语义状态只允许“部分确认”，且不能同时存在另一种未排除的解释。待平台、待业务、待财务、存在冲突、无法验证，都不能发布为业务映射。
8. 单位、币种、精度三者都有明确约束。值为 `UNKNOWN` 时，转换策略必须是 `NO_CONVERT`。
9. 只靠 E4、E5、E6 不能发布。`CODE_INVESTIGATION` 即使证据等级被误填，也不能改写成官方契约。
10. 同一契约版本和字段路径没有另一条 `ACTIVE` 映射。
11. 校验器通过，审核状态是 `APPROVED`，且审核人不是草稿作者。
12. 规则生命周期仍是 `DRAFT`。不能把已经退役的行直接改回 `ACTIVE`。

操作和责任：

| 操作 | 谁可以做 | 结果 |
| --- | --- | --- |
| 创建草稿、编辑草稿 | 映射编辑人 | 只影响 `DRAFT` |
| 校验草稿 | 编辑人或系统在提交时重跑 | 不改变生命周期 |
| 提交审核 | 编辑人 | `IN_REVIEW` |
| 审核通过或驳回 | 另一名审核人 | 通过后仍未生效 |
| 发布生效 | 发布人，不能与作者相同 | `ACTIVE` |
| 退役 | 发布人 | `RETIRED`，不改观测 |
| 查询历史、比较版本 | 只读角色 | 无副作用 |
| 回滚 | 发布人 | 新建版本并发布旧正文，旧观测不重写 |

每次操作追加审计。审计至少有操作者、时间、对象、版本、原状态、新状态、请求号和原因。审计行不更新、不删除。

映射 `ACTIVE` 不等于类型 `ACTIVE`。类型必须先有自己的治理结果。本文不新建类型注册表，也不修改 06-03-02-01。发布器读取一份带版本号的类型目录快照；这份快照的装载方式留到类型治理设计。在快照仍显示 `DRAFT` 时，映射发布失败。

## 8. 版本管理与历史追溯

`contractId` 不变，契约材料变化则新增 `contractVersionId`。`mappingId` 不变，约束、证据或确认结论变化则新增 `mappingVersion`。契约版本升级不会自动复制映射，避免把旧字段定义带到新契约。

已生效版本不允许修改。新版本发布时，同一路径上一条 `ACTIVE` 被标成 `RETIRED` 并写上 `effectiveTo`。新版本写 `effectiveFrom`。两条的生效窗口不能重叠。

观测保存四个追溯值：`contractId`、`contractVersionId`、`mappingId`、`mappingVersion`。查询某条观测时直接读这四个值，不按当前配置反推。

回滚是发布一个内容相同的新版本。被回滚替代的现行版本退役。历史观测仍指向它们原来的版本。配置回滚不等于重算金额。

若要重跑历史报文，必须另开重处理批次，记录批次号、原因、操作者、使用的映射快照号和契约版本。批次产生新观测或新计算结果，不覆盖旧记录。本阶段不建重处理表，避免把运行数据放进配置模型。

迟到报文按报文上的契约版本，到任务开始时固定的快照里查找“接收时间落在生效窗口内”的版本。不用处理时刻的最新规则替换它。报文没有契约版本时，不猜测，结果是 `UNMAPPED`。

## 9. 数据库详细设计

下面是配置库建议。它依赖第 2 节尚未接受的 MySQL 决策。不建物理外键，避免历史版本被级联删除；引用由发布事务检查。

表的取舍：

| 候选表 | 决定 | 理由 |
| --- | --- | --- |
| `source_contract` | 建立 | 契约家族身份 |
| `source_contract_version` | 建立 | 版本不能覆盖 |
| `source_contract_evidence` | 建立 | 一个版本可以有多份证据 |
| `amount_mapping` | 建立 | `mappingId` 的稳定身份 |
| `amount_mapping_version` | 建立 | 版本正文和生命周期 |
| `amount_mapping_evidence` | 建立 | 规则证据与契约证据分开 |
| `amount_mapping_approval` | 不建立 | 审核动作写入审计，避免两套历史 |
| `amount_mapping_publish_record` | 不建立 | 发布和退役也是审计事件。快照号使用审计行的自增序号 |
| `amount_mapping_audit_log` | 建立 | 追加审计是追溯所必需的 |

配置表和金额观测分开。观测表不在本文创建，因为原始存储产品尚未决策。配置和审计不设自动删除期限。

`source_message_type` 使用空字符串表示“这份契约没有消息类型”，不用 NULL。MySQL 唯一索引对多个 NULL 不视为重复，空字符串才能拦住重复契约。

```sql
CREATE TABLE source_contract (
  contract_id VARCHAR(64) NOT NULL,
  platform VARCHAR(32) NOT NULL,
  source_system VARCHAR(128) NOT NULL,
  source_interface VARCHAR(256) NOT NULL,
  source_message_type VARCHAR(128) NOT NULL DEFAULT '',
  source_type VARCHAR(32) NOT NULL,
  lifecycle_status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (contract_id),
  UNIQUE KEY uk_source_contract_identity (platform, source_system, source_interface, source_message_type),
  KEY idx_source_contract_platform (platform, lifecycle_status),
  CONSTRAINT ck_source_contract_type CHECK (source_type IN (
    'OFFICIAL_DOCUMENT', 'OFFICIAL_MESSAGE_PROTOCOL', 'CODE_INVESTIGATION',
    'BUSINESS_CONFIRMED', 'OTHER_EVIDENCE')),
  CONSTRAINT ck_source_contract_life CHECK (lifecycle_status IN ('DRAFT', 'ACTIVE', 'RETIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE source_contract_version (
  contract_version_id VARCHAR(64) NOT NULL,
  contract_id VARCHAR(64) NOT NULL,
  contract_version VARCHAR(128) NOT NULL,
  platform VARCHAR(32) NOT NULL,
  source_type VARCHAR(32) NOT NULL,
  evidence_status VARCHAR(16) NOT NULL,
  semantic_status VARCHAR(32) NOT NULL,
  effective_from DATETIME(3) NULL,
  effective_to DATETIME(3) NULL,
  lifecycle_status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
  source_reference VARCHAR(512) NOT NULL,
  row_version INT NOT NULL DEFAULT 1,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (contract_version_id),
  UNIQUE KEY uk_contract_version_no (contract_id, contract_version),
  KEY idx_contract_version_contract (contract_id, lifecycle_status),
  CONSTRAINT ck_contract_version_life CHECK (lifecycle_status IN ('DRAFT', 'ACTIVE', 'RETIRED')),
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
  candidate_disposition VARCHAR(32) NULL,
  mapping_eligibility VARCHAR(40) NOT NULL,
  semantic_status VARCHAR(32) NOT NULL,
  evidence_status VARCHAR(16) NOT NULL,
  unit_constraint VARCHAR(64) NOT NULL,
  currency_constraint VARCHAR(64) NOT NULL,
  precision_constraint VARCHAR(64) NOT NULL,
  transformation_policy VARCHAR(32) NOT NULL,
  aggregation_policy VARCHAR(40) NOT NULL,
  lifecycle_status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
  approval_status VARCHAR(16) NOT NULL DEFAULT 'NOT_SUBMITTED',
  effective_from DATETIME(3) NULL,
  effective_to DATETIME(3) NULL,
  row_version INT NOT NULL DEFAULT 1,
  active_guard VARCHAR(600) GENERATED ALWAYS AS (
    CASE WHEN lifecycle_status = 'ACTIVE'
      THEN CONCAT(contract_version_id, '#', field_path)
      ELSE NULL END
  ) STORED,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (mapping_version_id),
  UNIQUE KEY uk_mapping_version_no (mapping_id, mapping_version),
  UNIQUE KEY uk_one_active_field (active_guard),
  KEY idx_mapping_version_lookup (contract_version_id, field_path, lifecycle_status),
  KEY idx_mapping_version_effective (contract_version_id, field_path, effective_from, effective_to),
  CONSTRAINT ck_mapping_life CHECK (lifecycle_status IN ('DRAFT', 'ACTIVE', 'RETIRED')),
  CONSTRAINT ck_mapping_approval CHECK (approval_status IN (
    'NOT_SUBMITTED', 'IN_REVIEW', 'APPROVED', 'REJECTED')),
  CONSTRAINT ck_mapping_eligibility CHECK (mapping_eligibility IN (
    'ALLOWED_IF_CONTRACT_DEFINES', 'NOT_UNTIL_CONFIRMED', 'DISALLOWED',
    'ALLOWED_AS_UNIDENTIFIED', 'CONFLICT_ONLY')),
  CONSTRAINT ck_mapping_transform CHECK (transformation_policy IN ('NO_CONVERT')),
  CONSTRAINT ck_mapping_aggregation CHECK (aggregation_policy IN (
    'FORBIDDEN', 'SAME_TYPE_AFTER_CONFIRMATION', 'RULE_CONTROLLED', 'NOT_APPLICABLE'))
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

CREATE TABLE amount_mapping_audit_log (
  audit_id BIGINT NOT NULL AUTO_INCREMENT,
  request_id VARCHAR(64) NOT NULL,
  event_type VARCHAR(32) NOT NULL,
  mapping_id VARCHAR(64) NULL,
  mapping_version VARCHAR(64) NULL,
  contract_version_id VARCHAR(64) NULL,
  from_lifecycle VARCHAR(16) NULL,
  to_lifecycle VARCHAR(16) NULL,
  operator_id VARCHAR(64) NOT NULL,
  reason VARCHAR(1024) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (audit_id),
  UNIQUE KEY uk_audit_request (request_id, event_type, mapping_id, mapping_version),
  KEY idx_audit_mapping (mapping_id, mapping_version, audit_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
```

`active_guard` 只在 `ACTIVE` 时有值。MySQL 唯一索引允许多个 NULL，所以多条草稿或退役行不互相冲突，同一契约版本的同一路径却不能有两条 `ACTIVE`。

并发发布使用 `row_version`。更新语句带上读到的版本号；影响行数为 0 就返回冲突，不使用 `SELECT ... FOR UPDATE`。发布事务内同时退役旧行和激活新行。任一约束失败则整笔回滚。

索引覆盖三类查询：按契约列版本、按契约版本加路径找规则、按映射身份查审计。配置量是规则而不是订单量，不按报文量分区。

多租户现在不加列。将来若租户模型成立，应新增契约或映射维度，不把租户塞进现有主键。

## 10. 管理 API 设计

接口放在 `app-admin`。查询用 GET，变更用 POST。V2 还没有统一响应包，建议外层包含 `success`、`data` 和 `errorCode`，这只是接口形状，不是已有公共类。

权限也尚未实现。建议角色是映射编辑人、映射审核人、映射发布人和只读审计人。下表的权限是建议，不是已经存在的鉴权系统。

幂等靠 `requestId`。同一个请求号和同一动作重复提交时，返回第一次的结果，不重复发布。

| 动作 | 方法与路径 | 请求要点 | 成功响应 | 主要错误 | 权限 | 事务 |
| --- | --- | --- | --- | --- | --- | --- |
| 创建契约 | `POST /api/admin/source-contracts` | 平台、系统、接口、消息类型、sourceType | `contractId` | `CONTRACT_DUPLICATE` | 编辑人 | 插入家族 |
| 查询契约 | `GET /api/admin/source-contracts` | 平台、接口、来源种类 | 契约列表 | 无 | 只读 | 无 |
| 查询契约详情 | `GET /api/admin/source-contracts/{contractId}` | 路径参数 | 家族和版本摘要 | `CONTRACT_NOT_FOUND` | 只读 | 无 |
| 新增契约版本 | `POST /api/admin/source-contracts/{contractId}/versions` | 版本号、证据、语义、引用 | `contractVersionId` | `VERSION_DUPLICATE` | 编辑人 | 插入版本和证据 |
| 启用契约版本 | `POST /api/admin/source-contracts/{contractId}/versions/{contractVersionId}/activate` | `rowVersion` | 版本变为 `ACTIVE` | `OPTIMISTIC_CONFLICT` | 发布人 | 更新一行 |
| 创建映射 | `POST /api/admin/amount-mappings` | 契约、路径、目标类型 | `mappingId` 和首个草稿版本 | `TYPE_UNKNOWN`、`IDENTITY_DUPLICATE` | 编辑人 | 插入身份和草稿 |
| 编辑草稿 | `POST /api/admin/amount-mappings/{mappingId}/versions/{mappingVersion}/edit` | 允许修改的约束和证据 | 新的 `rowVersion` | `VERSION_IMMUTABLE`、`NOT_EDITABLE` | 编辑人 | 只更新草稿 |
| 校验 | `POST /api/admin/amount-mappings/{mappingId}/versions/{mappingVersion}/validate` | 无正文 | 通过项和阻断项 | `MAPPING_NOT_FOUND` | 编辑人 | 只读 |
| 提交审核 | `POST /api/admin/amount-mappings/{mappingId}/versions/{mappingVersion}/submit-review` | `requestId` | `IN_REVIEW` | `VALIDATION_FAILED` | 编辑人 | 更新审核状态并写审计 |
| 审核 | `POST /api/admin/amount-mappings/{mappingId}/versions/{mappingVersion}/review` | 通过或驳回、原因 | 审核结果 | `REVIEWER_IS_AUTHOR` | 审核人 | 更新审核状态并写审计 |
| 发布 | `POST /api/admin/amount-mappings/{mappingId}/versions/{mappingVersion}/publish` | `requestId`、`rowVersion` | `ACTIVE` 和快照号 | `TYPE_NOT_ACTIVE`、`ELIGIBILITY_BLOCKED`、`SEMANTIC_NOT_READY`、`ACTIVE_FIELD_CONFLICT`、`CONTRACT_VERSION_NOT_ACTIVE` | 发布人 | 退役旧行、激活新行、写审计 |
| 退役 | `POST /api/admin/amount-mappings/{mappingId}/versions/{mappingVersion}/retire` | 原因、`rowVersion` | `RETIRED` | `NOT_ACTIVE` | 发布人 | 更新窗口并写审计 |
| 历史 | `GET /api/admin/amount-mappings/{mappingId}/versions` | 无 | 全部版本 | `MAPPING_NOT_FOUND` | 只读 | 无 |
| 差异 | `GET /api/admin/amount-mappings/{mappingId}/diff` | 两个版本号 | 字段差异 | `VERSION_NOT_FOUND` | 只读 | 无 |
| 回滚 | `POST /api/admin/amount-mappings/{mappingId}/rollback` | 目标历史版本、`requestId` | 新版本号 | `TARGET_NOT_PUBLISHED` | 发布人 | 复制正文为新版本并走发布事务 |
| 审计 | `GET /api/admin/amount-mappings/{mappingId}/audit` | 可选版本号 | 审计行 | 无 | 审计人 | 无 |

编辑、审核、发布都检查 `rowVersion`。校验接口不改变数据。Worker 不调用这些接口。

## 11. Worker 配置加载

core 只定义端口 `PublishedMappingSnapshot`：按快照号读取全部已发布和已退役版本，不读取草稿。实现放在 Worker 的装配里。Admin 写库的 Mapper 不放进 core，Worker 也不引用 Admin 的类。

加载规则：

1. 任务开始时读取当前最大已发布审计号，得到快照 S，并固定到该任务。
2. 任务执行中即使发生新的发布，仍使用 S。因此同一任务不会前后使用两套规则。
3. 多个 Worker 使用同一个快照号时，看到的已发布版本集合相同。一致性来自快照号，不依赖各机器时钟。
4. Worker 可以在内存中缓存快照。缓存键是快照号。不引入 Redis，因为 Redis 尚未决策。
5. 新任务开始前可以刷新“当前最大快照号”。刷新失败时，新任务失败并记录原因，不使用草稿，也不沿用无法确认已经完整的缓存。
6. 正在执行的任务不因刷新失败而改用另一套规则。
7. Worker 只读，不能修改规则，也不能跳过发布状态。
8. 配置更新和任务执行的竞态由“任务开始时固定快照”消除，不在映射过程中重新查询当前 ACTIVE。

Kafka 可以在以后用作“有新快照”的通知，但现在客户端还没接入。本设计不依赖通知才能正确执行；没有通知时，下一个新任务仍会读取最大快照号。

## 12. Pipeline 集成

处理顺序：

```text
RawPayloadRecord
  -> RawAmountValue
  -> AmountSource
  -> 用任务固定的快照匹配 AmountMapping
  -> ObservedAmount
  -> 只有后续已批准的 AmountCalculationRule 才进入 AmountCalculation
```

职责落点：

| 步骤 | 落点 | 边界 |
| --- | --- | --- |
| 保存原文 | core 的原文端口，具体产品未定 | 映射失败也不能丢原文 |
| 提取原始金额 | Worker 装配的提取实现 | 只产生 RawAmountValue，不决定业务类型 |
| 匹配规则 | core 中的纯匹配逻辑，规则来自快照 | 不访问数据库 |
| 资格校验 | 同一匹配逻辑 | 读规则上保存的资格，并拒绝转换未知单位 |
| 保存观测 | core 的观测端口 | 写入追溯字段和执行状态 |
| 计算 | 独立的计算规则 | 映射模块不实现合计、营业额或结算 |

Pipeline 只依赖 core 端口。它不依赖 Admin，不依赖 MySQL、Redis 或 Kafka。Transform、Writer 的实现由 Worker 装配。

未识别观测停在观测阶段。没有生效计算规则时，不产生 AmountCalculation。

## 13. 并发与一致性

同一字段的两次发布靠 `row_version` 和 `uk_one_active_field` 决定胜负。失败的一方收到 `OPTIMISTIC_CONFLICT` 或 `ACTIVE_FIELD_CONFLICT`，不会留下两条 ACTIVE。

生效窗口在同一事务中闭合。事务失败时，旧规则保持原状态。

Worker 的任务级快照保证执行期间规则不变。发布人看到的“当前生效”和某个正在运行的任务看到的快照可以不同；这是故意的。任务记录自己的快照号，便于事后解释。

审计的 `requestId` 唯一键挡住重复发布。回滚生成新版本，不把退役行改回 ACTIVE，因此不会抹掉中间版本。

## 14. 安全与审计

配置不能携带可执行表达式。`transformation_policy` 由数据库检查约束限制为 `NO_CONVERT`。新增转换策略必须修改 core 枚举、检查约束和发布校验，并经过代码评审，不能由接口传入脚本。

作者不能审核或发布自己的草稿。这个职责分离现在只是设计，因为 V2 还没有账号体系。实现时如果没有可区分的操作者身份，发布接口必须失败，不能用空操作者绕过。

审计记录操作者、请求号、状态变化和原因。它不记录支付密钥或原文全文。原文仍由 RawPayloadRecord 的引用追溯。

`CODE_INVESTIGATION` 不能在更新接口里改成官方来源。来源种类写在契约家族上，版本只复制它，不提供修改接口。

## 15. 测试与验收

以下测试在实现阶段执行。本文件不运行它们。

| 测试 | 输入 | 预期 | 验收 |
| --- | --- | --- | --- |
| 契约版本 | 同一契约插入两个相同版本号 | 第二次被拒绝 | 唯一键生效，旧版本内容不变 |
| 映射版本 | 修改已发布版本 | 被拒绝 | 只能新增版本 |
| DRAFT 类型发布 | 目标类型快照为 `DRAFT` | `TYPE_NOT_ACTIVE` | 没有 ACTIVE 行 |
| NOT_UNTIL_CONFIRMED | 资格为该值，即使其他项齐备 | `ELIGIBILITY_BLOCKED` | 没有 ACTIVE 行 |
| DISALLOWED | 目标是结算、成交额、支付成功合计或停用配送费 | `ELIGIBILITY_BLOCKED` | 没有映射身份被发布 |
| CONFLICT_ONLY | 资格为该值 | `ELIGIBILITY_BLOCKED` | 不产生普通 ACTIVE |
| 同字段重复 ACTIVE | 两条不同候选同时发布 | 第二条失败 | 唯一的 ACTIVE 仍是第一条 |
| 并发发布 | 两个请求带着同一个 `rowVersion` | 一个成功，一个 `OPTIMISTIC_CONFLICT` | 生效窗口不重叠 |
| 历史追溯 | 发布第二个版本后查询旧观测 | 旧观测仍显示第一版 | 四个追溯字段不变 |
| 回滚 | 指定第一版回滚 | 产生第三版，第二版退役 | 第一版行本身不被改写 |
| 迟到报文 | 接收时间落在旧窗口，处理时间在新版本之后 | 命中旧窗口 | 不改用处理时刻的规则 |
| 契约版本缺失 | 报文没有契约版本 | `UNMAPPED` | 不猜测最新契约 |
| Worker 一致性 | 两个 Worker 固定同一快照号 | 规则集合相同 | 比较快照内容而不是本地时间 |
| 配置竞态 | 任务开始后发布新规则 | 该任务仍用原快照 | 任务记录的快照号不变 |
| 未识别保存 | 没有适用业务规则但有原始金额 | 保存原文和未识别类型 | 执行状态是 `UNMAPPED` |
| 未识别计算 | 上一条观测进入计算 | 被阻断 | 不产生 AmountCalculation |
| 同报文多字段 | 一笔报文含多个金额路径 | 每个路径独立匹配 | 一个路径冲突不影响其他路径的原文 |
| 未知单位转换 | 单位 `UNKNOWN` 且策略不是 `NO_CONVERT` | 草稿校验失败 | 不能发布，也不能在运行时换算 |

## 16. 风险和待确认事项

| 事项 | 现状 | 影响 |
| --- | --- | --- |
| MySQL 未决策 | ADR-0002 明确未接入 | DDL 不能执行 |
| 类型目录没有机器可读快照 | 39 个编码在设计文档中，类型都未 ACTIVE | 发布器现在必须全部拒绝 |
| 账号和权限未设计 | 只有角色建议 | 没有操作者身份时不能发布 |
| 原文存储未决策 | 只有端口 | 观测追溯值要等存储端口确定后落地 |
| 租户模型未设计 | 06-03-01 不把租户放进金额语义 | 现在按契约区分业务线 |
| 重处理批次未建表 | 只规定必须独立 | 不能用配置回滚代替重算 |
| 审核人与业务确认人 | 发布门槛要求语义已经达到“部分确认” | 系统审核不能代替业务或财务书面确认 |

没有发现需要修改 06-03-01、06-03-02-01 或 06-03-02-02 的冲突。本文把路径变化定为新 `mappingId`，这是在遵守 06-03-01，不是改写它。

## 17. 设计结论

配置模型由契约家族、契约版本、映射身份、映射版本和追加审计组成。规则只有草稿、生效、退役三种生命周期。执行结果只有已映射、未映射、冲突。候选评估和类型资格都不混进这两个状态。

今天不能发布任何业务映射，也不能发布未识别映射来伪装确认。原因是类型仍是草稿。这是准入条件，不是实现遗漏。

对五个实施问题的回答：

1. 本文可以指导后续 Java 实现的边界、表结构和接口，但不能直接开工建表。MySQL、账号和类型目录快照都还没有接受。
2. 没有为审批和发布另建两张表，也没有加入表达式引擎、Redis、租户列或重处理表。复杂点只留在版本不可变、单路径一条 ACTIVE 和任务级快照上。
3. 不存在用配置状态绕过语义确认的路径。类型草稿、未确认资格、禁止资格、冲突资格和单纯代码证据都会阻断发布。
4. 历史观测保存契约版本和映射版本。退役、回滚和重处理都不覆盖旧观测。
5. 新平台和新接口通过新增 SourceContract 接入，不需要新模块，也不按字段名自动复用其他业务线的契约。

## 18. 修订记录

| 轮次 | 内容 |
| --- | --- |
| 初稿 | 建立映射配置、版本、发布准入、建议表结构、Admin 接口和 Worker 快照边界 |
