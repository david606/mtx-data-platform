# 06-DB-01-02 trade-flow MySQL 表结构清单

这些字段、类型、空值、默认值和索引来自初始化脚本，不是已连接数据库的元数据。本机 3306 连接被拒绝。因此不能把本清单写成生产库的真实结构。

已确认的约束范围：

- 外键只在 `trade_analytics.dim_jd_account_binding` 的 `fk_jd_binding_anchor`。
- 初始化脚本里的 MySQL 表没有 `PARTITION BY`。
- `trade_flow-init.sql` 用存储过程按 `_00` 到 `_99` 创建 `trade_storage` 和 `trade_storage_blob` 各 100 张物理表。下面只列模板。

## Schema `trade_flow`

文件 `docs/sql/init/trade-flow-init.sql`。表 16 张。

### `trade_flow.trade_storage`

- 证据类型：DDL。文件 `docs/sql/init/trade-flow-init.sql`。实际数据库未核对。
- 表注释：原始数据元数据模板表，按SHA-256模100分表
- 引擎：`ENGINE=InnoDB`
- 代码引用：StorageDO。StorageWriteService 写入。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `id` | BIGINT | NO |  | storage领域雪花ID，与payload_sha256共同定位Storage |
| `source_system` | TINYINT UNSIGNED | NO | 0 | 来源系统：0未知；1富智云；2淘宝闪购；3美团外卖；4京东秒送 |
| `content_type` | TINYINT UNSIGNED | NO |  | 内容类型：1订单；2支付；3历史主数据快照兼容值 |
| `payload_sha256` | BINARY(32) | NO |  | 原始请求体字节SHA-256，同时作为分片键 |
| `payload_length` | INT UNSIGNED | NO | 0 | 原始请求体字节长度 |
| `content_storage_type` | TINYINT UNSIGNED | NO | 1 | 存储类型：1 BLOB；2本地归档；3 OSS归档 |
| `content_ref` | VARCHAR(512) | NO | '' | 归档文件相对路径或对象Key；BLOB类型为空 |
| `content_offset` | BIGINT UNSIGNED | NO | 0 | 内容在归档文件内的字节偏移量；BLOB类型为0 |
| `content_length` | INT UNSIGNED | NO | 0 | 归档文件内存储内容长度；未归档时为0 |
| `received_time` | DATETIME(3) | NO |  | 原始数据接收时间 |
| `content_received_time` | DATETIME(3) | YES |  | 当前正文最近接收时间 |
| `create_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) | 记录创建时间 |
| `update_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) | 记录更新时间 |

主键、唯一键和二级索引：
- `PRIMARY KEY (`id`)`
- `UNIQUE KEY `uk_source_sha256` (`source_system`, `payload_sha256`)`
- `KEY `idx_received_time` (`received_time`)`
- `KEY `idx_archive_scan` (`content_storage_type`, `received_time`, `id`)`
- `KEY `idx_archive_content_time` (`content_storage_type`, `content_received_time`, `id`)`

### `trade_flow.trade_storage_blob`

- 证据类型：DDL。文件 `docs/sql/init/trade-flow-init.sql`。实际数据库未核对。
- 表注释：原始内容热存储模板表，按SHA-256模100分表
- 引擎：`ENGINE=InnoDB`
- 代码引用：StorageBlobDO。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `id` | BIGINT | NO |  | 与trade_storage.id严格相等 |
| `payload_sha256` | BINARY(32) | NO |  | 与trade_storage.payload_sha256严格相等，同时作为分片键 |
| `content` | MEDIUMBLOB | NO |  | 原始请求体字节 |
| `create_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) | 记录创建时间 |

主键、唯一键和二级索引：
- `PRIMARY KEY (`id`)`

### `trade_flow.trade_storage_archive_control`

- 证据类型：DDL。文件 `docs/sql/init/trade-flow-init.sql`。实际数据库未核对。
- 表注释：Storage归档任务跨Ingress实例租约
- 引擎：`ENGINE=InnoDB`
- 代码引用：StorageArchiveControlDO。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `task_name` | VARCHAR(64) | NO |  | 归档任务唯一名称 |
| `lease_owner` | VARCHAR(128) | YES |  | 当前归档写实例随机owner |
| `lease_until` | DATETIME(3) | YES |  | 归档写租约截止时间 |
| `create_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |
| `update_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |

主键、唯一键和二级索引：
- `PRIMARY KEY (`task_name`)`

### `trade_flow.trade_data_retention_control`

- 证据类型：DDL。文件 `docs/sql/init/trade-flow-init.sql`。实际数据库未核对。
- 表注释：Trade Ingress数据保留任务跨实例租约
- 引擎：`ENGINE=InnoDB`
- 代码引用：保留任务租约。Ingress 与 Analytics 各有同名表。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `task_name` | VARCHAR(64) | NO |  | 保留任务唯一名称 |
| `lease_owner` | VARCHAR(128) | YES |  | 当前执行实例随机owner |
| `lease_until` | DATETIME(3) | YES |  | 跨实例租约截止时间 |
| `create_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |
| `update_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |

主键、唯一键和二级索引：
- `PRIMARY KEY (`task_name`)`

### `trade_flow.trade_order_event`

- 证据类型：DDL。文件 `docs/sql/init/trade-flow-init.sql`。实际数据库未核对。
- 表注释：订单接入事实事件
- 引擎：`ENGINE=InnoDB`
- 代码引用：OrderEventDO。TradeCdcJob 订阅。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `id` | BIGINT UNSIGNED | NO |  | 自增主键 |
| `source_system` | TINYINT UNSIGNED | NO | 0 | 来源系统：0未知；1富友 |
| `source_object_id` | VARCHAR(128) | YES |  | 第三方稳定订单号；历史缺失值进入LEGACY_UNROUTED桶 |
| `route_bucket` | TINYINT UNSIGNED GENERATED ALWAYS AS ( MOD(CRC32(CONCAT(`source_system`, '#', COALESCE(NULLIF(`source_object_id`, ''), 'LEGACY_UNROUTED'))), 64) ) VIRTUAL | YES |  | 稳定三方业务键CRC32模64 |
| `third_event_key` | VARCHAR(128) | NO | '' | 第三方事件唯一键 |
| `message_version` | BIGINT UNSIGNED | NO |  | 第三方消息版本，来源适配器转换为非负整数 |
| `raw_id` | BIGINT | NO |  | 关联trade_storage.id |
| `payload_sha256` | BINARY(32) | NO |  | 原始报文SHA-256，与raw_id共同定位Storage |
| `acked` | TINYINT UNSIGNED | NO | 0 | Pipeline接管状态：0未ACK；1已ACK |
| `acked_time` | DATETIME(3) | YES |  | Pipeline ACK时间 |
| `received_time` | DATETIME(3) | NO |  | 事件接收时间 |
| `create_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |
| `update_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |

主键、唯一键和二级索引：
- `PRIMARY KEY (`id`)`
- `UNIQUE KEY `uk_raw_id` (`raw_id`)`
- `UNIQUE KEY `uk_event_version` (`source_system`, `third_event_key`, `message_version`)`
- `KEY `idx_acked_create_id` (`acked`, `create_time`, `id`)`
- `KEY `idx_received_time_id` (`received_time`, `id`)`
- `KEY `idx_backfill_source_route_page` (`source_system`, `route_bucket`, `received_time`, `id`)`
- `KEY `idx_backfill_source_object_version` (`source_system`, `source_object_id`, `message_version`, `id`, `received_time`)`

### `trade_flow.trade_payment_event`

- 证据类型：DDL。文件 `docs/sql/init/trade-flow-init.sql`。实际数据库未核对。
- 表注释：支付接入事实事件
- 引擎：`ENGINE=InnoDB`
- 代码引用：PaymentEventDO。TradeCdcJob 订阅。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `id` | BIGINT UNSIGNED | NO |  | 自增主键 |
| `source_system` | TINYINT UNSIGNED | NO | 0 | 来源系统：0未知；1富友 |
| `source_object_id` | VARCHAR(128) | YES |  | 所属第三方稳定订单号；历史缺失值进入LEGACY_UNROUTED桶 |
| `route_bucket` | TINYINT UNSIGNED GENERATED ALWAYS AS ( MOD(CRC32(CONCAT(`source_system`, '#', COALESCE(NULLIF(`source_object_id`, ''), 'LEGACY_UNROUTED'))), 64) ) VIRTUAL | YES |  | 稳定三方业务键CRC32模64 |
| `third_event_key` | VARCHAR(128) | NO | '' | 第三方支付事件唯一键 |
| `message_version` | BIGINT UNSIGNED | NO |  | 第三方消息版本，来源适配器转换为非负整数 |
| `raw_id` | BIGINT | NO |  | 关联trade_storage.id |
| `payload_sha256` | BINARY(32) | NO |  | 原始报文SHA-256，与raw_id共同定位Storage |
| `acked` | TINYINT UNSIGNED | NO | 0 | Pipeline接管状态：0未ACK；1已ACK |
| `acked_time` | DATETIME(3) | YES |  | Pipeline ACK时间 |
| `received_time` | DATETIME(3) | NO |  | 事件接收时间 |
| `create_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |
| `update_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |

主键、唯一键和二级索引：
- `PRIMARY KEY (`id`)`
- `UNIQUE KEY `uk_raw_id` (`raw_id`)`
- `UNIQUE KEY `uk_payment_business_event` (`source_system`, `third_event_key`)`
- `KEY `idx_acked_create_id` (`acked`, `create_time`, `id`)`
- `KEY `idx_received_time_id` (`received_time`, `id`)`
- `KEY `idx_backfill_source_route_page` (`source_system`, `route_bucket`, `received_time`, `id`)`

### `trade_flow.trade_thirdparty_event`

- 证据类型：DDL。文件 `docs/sql/init/trade-flow-init.sql`。实际数据库未核对。
- 表注释：淘宝、美团、京东等第三方不可变来源事件
- 引擎：`ENGINE=InnoDB`
- 代码引用：ThirdpartyEventDO。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `id` | BIGINT UNSIGNED | NO |  | 自增主键 |
| `source_system` | TINYINT UNSIGNED | NO |  | 来源系统：2淘宝闪购；3美团外卖；4京东秒送 |
| `source_event_type` | VARCHAR(64) | NO |  | 第三方原始事件类型或状态 |
| `source_object_id` | VARCHAR(128) | NO |  | 平台订单号等来源对象ID |
| `route_bucket` | TINYINT UNSIGNED GENERATED ALWAYS AS (MOD(CRC32(CONCAT(`source_system`, '#', `source_object_id`)), 64)) VIRTUAL | YES |  | 稳定三方业务键CRC32模64 |
| `third_event_key` | VARCHAR(128) | NO |  | 平台+回调类型+应用标识+来源业务键组成的事件键 |
| `message_version` | BIGINT UNSIGNED | NO |  | 第三方报文提取并转换的非负消息版本 |
| `ingest_request_id` | CHAR(32) | NO |  | Ingress单次接入排障ID |
| `third_request_id` | VARCHAR(128) | YES |  | 第三方请求ID |
| `raw_id` | BIGINT | NO |  | 关联trade_storage.id |
| `raw_event_type` | VARCHAR(64) | YES |  | 原文唯一约束类型维度，非限定类型为空串 |
| `payload_sha256` | BINARY(32) | NO |  | 原始报文SHA-256，与raw_id共同定位Storage |
| `received_time` | DATETIME(3) | NO |  | 事件接收时间 |
| `create_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |
| `update_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |

主键、唯一键和二级索引：
- `PRIMARY KEY (`id`)`
- `UNIQUE KEY `uk_raw_event_type` (`raw_id`, `raw_event_type`)`
- `UNIQUE KEY `uk_event_version` (`source_system`, `third_event_key`, `message_version`)`
- `KEY `idx_source_object_version` (`source_system`, `source_object_id`, `message_version`)`
- `KEY `idx_source_received_id` (`source_system`, `received_time`, `id`)`
- `KEY `idx_source_type_received_id` (`source_system`, `source_event_type`, `received_time`, `id`)`
- `KEY `idx_ingest_request_received_id` (`ingest_request_id`, `received_time`, `id`)`
- `KEY `idx_received_time_id` (`received_time`, `id`)`
- `KEY `idx_backfill_source_route_page` (`source_system`, `route_bucket`, `received_time`, `id`)`
- `KEY `idx_create_time_id` (`create_time`, `id`)`

### `trade_flow.trade_source_query_event`

- 证据类型：DDL。文件 `docs/sql/init/trade-flow-init.sql`。实际数据库未核对。
- 表注释：第三方主动查询响应的不可变来源事件；响应正文只保存在Storage
- 引擎：`ENGINE=InnoDB`
- 代码引用：SourceQueryEventDO。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `id` | BIGINT UNSIGNED | NO |  | 自增主键 |
| `source_system` | TINYINT UNSIGNED | NO |  | 来源系统：2淘宝闪购；3美团；4京东 |
| `fetch_type` | VARCHAR(32) | NO |  | 查询类型：ORDER_DETAIL等 |
| `source_object_id` | VARCHAR(128) | NO |  | 平台订单号等来源对象ID |
| `owner_order_id` | VARCHAR(128) | YES | NULL | 原订单号 |
| `route_bucket` | TINYINT UNSIGNED GENERATED ALWAYS AS (MOD(CRC32(CONCAT(`source_system`, '#', `source_object_id`)), 64)) VIRTUAL | YES |  | 稳定三方业务键CRC32模64 |
| `source_message_version` | BIGINT UNSIGNED | NO |  | 触发查询的最高来源消息版本 |
| `pickup_result_sequence` | BIGINT UNSIGNED | NO | 0 | Pickup接受结果代次 |
| `fetch_task_id` | BIGINT UNSIGNED | NO |  | Analytics Pickup任务ID；字段名为兼容历史数据保留 |
| `storage_id` | BIGINT | NO |  | 查询响应关联trade_storage.id |
| `payload_sha256` | BINARY(32) | NO |  | 查询响应SHA-256，与storage_id共同定位Storage |
| `received_time` | DATETIME(3) | NO |  | 查询响应接收时间 |
| `create_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |

主键、唯一键和二级索引：
- `PRIMARY KEY (`id`)`
- `UNIQUE KEY `uk_source_object_version` (`source_system`, `fetch_type`, `source_object_id`, `source_message_version`, `pickup_result_sequence`, (CASE WHEN (`source_system` = 4 AND `fetch_type` IN ('JD_AFS_INSTANCE_SNAPSHOT','JD_AFS_ORDER_SNAPSHOT')) OR (`source_system` = 3 AND `fetch_type` = 'ORDER_DETAIL' AND `pickup_result_sequence` > 0) OR `fetch_type` = 'ORDER_COMPLETION' THEN `fetch_task_id` ELSE 0 END))`
- `UNIQUE KEY `uk_completion_task_attempt` ((CASE WHEN `fetch_type` = 'ORDER_COMPLETION' THEN `fetch_task_id` ELSE NULL END), `pickup_result_sequence`)`
- `UNIQUE KEY `uk_jd_afs_task_result` (`source_system`, `fetch_type`, (CASE WHEN `source_system` = 4 AND `fetch_type` IN ('JD_AFS_INSTANCE_SNAPSHOT','JD_AFS_ORDER_SNAPSHOT') THEN `fetch_task_id` ELSE NULL END), `pickup_result_sequence`)`
- `UNIQUE KEY `uk_refund_food_result` (`source_system`, `fetch_type`, `source_object_id`, (CASE WHEN `source_system` = 2 AND `fetch_type` = 'REFUND_FOOD_SNAPSHOT' THEN `pickup_result_sequence` ELSE NULL END))`
- `KEY `idx_query_owner_history` (`source_system`, `fetch_type`, `owner_order_id`, `source_message_version`, `id`)`
- `KEY `idx_fetch_task` (`fetch_task_id`)`
- `KEY `idx_storage` (`storage_id`, `payload_sha256`)`
- `KEY `idx_received_time_id` (`received_time`, `id`)`
- `KEY `idx_backfill_source_route_page` (`source_system`, `route_bucket`, `received_time`, `id`)`

### `trade_flow.trade_jd_authorization_token`

- 证据类型：DDL。文件 `docs/sql/init/trade-flow-init.sql`。实际数据库未核对。
- 表注释：京东商家维度授权Token签发历史；每个Token追加一行
- 引擎：`ENGINE=InnoDB`
- 代码引用：JdAuthorizationTokenDO。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `id` | BIGINT UNSIGNED | NO |  | 自增主键 |
| `invoke_id` | VARCHAR(128) | NO |  | 京东Token下发调用标识；平台缺失时按应用、商家和签发时间稳定生成 |
| `app_key` | VARCHAR(128) | NO |  | 京东开放平台应用标识 |
| `vendor_id` | VARCHAR(128) | NO |  | 京东商家标识，对应报文venderId |
| `access_token` | TEXT | NO |  | 京东商家授权Token明文 |
| `token_sha256` | BINARY(32) | NO |  | Token SHA-256，用于幂等校验和脱敏排障 |
| `issued_time_ms` | BIGINT UNSIGNED | NO |  | 京东签发时间，Epoch毫秒 |
| `expires_in_seconds` | BIGINT UNSIGNED | NO |  | 京东返回的有效秒数 |
| `expires_at` | DATETIME(3) | NO |  | 按签发时间与有效秒数计算的过期时间 |
| `received_time` | DATETIME(3) | NO |  | Ingress成功接收时间 |
| `create_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |

主键、唯一键和二级索引：
- `PRIMARY KEY (`id`)`
- `UNIQUE KEY `uk_invoke_id` (`invoke_id`)`
- `UNIQUE KEY `uk_app_vendor_issued` (`app_key`, `vendor_id`, `issued_time_ms`)`
- `KEY `idx_current_token` (`app_key`, `vendor_id`, `issued_time_ms`, `id`)`
- `KEY `idx_expires_at` (`expires_at`, `id`)`

### `trade_flow.trade_thirdparty_config`

- 证据类型：DDL。文件 `docs/sql/init/trade-flow-init.sql`。实际数据库未核对。
- 表注释：Ingress平台应用级第三方配置；不承载品牌维度
- 引擎：`ENGINE=InnoDB`
- 代码引用：ThirdpartyConfigDO。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `id` | BIGINT UNSIGNED | NO |  | 自增主键 |
| `platform_code` | VARCHAR(32) | NO |  | 平台编码：FZY/TAOBAO/MEITUAN/JD；取自Common PlatformCode |
| `platform_app_id` | VARCHAR(128) | NO |  | 平台配置标识：JD为venderId，美团为appId，淘宝为应用ID，FZY为mchntCd |
| `third_config` | JSON | NO |  | Ingress对接该平台应用所需的地址、标识和明文凭据 |
| `is_deleted` | TINYINT UNSIGNED | NO | 0 | 0有效；1已删除 |
| `active_platform_app_id` | VARCHAR(128) GENERATED ALWAYS AS (IF(`is_deleted` = 0, `platform_app_id`, | YES |  | 仅有效行参与唯一约束 |
| `create_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |
| `update_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |

主键、唯一键和二级索引：
- `PRIMARY KEY (`id`)`
- `UNIQUE KEY `uk_active_platform_app` (`platform_code`, `active_platform_app_id`)`
- `KEY `idx_platform_app` (`platform_code`, `platform_app_id`)`

### `trade_flow.trade_analysis_event_outbox`

- 证据类型：DDL。文件 `docs/sql/init/trade-flow-init.sql`。实际数据库未核对。
- 表注释：统一分析事件可靠投递outbox；与来源event同事务创建
- 引擎：`ENGINE=InnoDB`
- 代码引用：AnalysisEventOutboxDO。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `id` | BIGINT UNSIGNED | NO |  |  |
| `schema_version` | INT UNSIGNED | NO | 1 | 分析消息合同版本；外卖平台新消息使用2 |
| `source_event_kind` | VARCHAR(32) | NO |  | 来源事件表类型：ORDER/PAYMENT/THIRDPARTY/QUERY |
| `source_event_id` | BIGINT UNSIGNED | NO |  | 来源事件表主键 |
| `event_kind` | VARCHAR(16) | NO |  | 分析事件类型：CALLBACK/QUERY |
| `source_system` | TINYINT UNSIGNED | NO |  | 来源系统编码 |
| `content_type` | TINYINT UNSIGNED | NO |  | 内容类型：1订单；2支付；3历史主数据快照兼容值 |
| `source_event_type` | VARCHAR(64) | YES |  | 来源事件类型 |
| `source_object_id` | VARCHAR(128) | YES |  | 平台订单号等来源对象ID |
| `event_key` | VARCHAR(128) | NO |  | 来源业务事件键 |
| `message_version` | BIGINT UNSIGNED | NO |  | 来源消息版本 |
| `storage_id` | BIGINT | NO |  | 关联trade_storage.id |
| `payload_sha256` | BINARY(32) | NO |  | 与storage_id共同定位Storage |
| `status` | TINYINT UNSIGNED | NO | 0 | 0待发布；1发布中；2已发布；3重试；4成功ACK；5耗尽；6历史目录；7已接管 |
| `ack_result` | VARCHAR(16) | YES |  | Analytics终态：SUCCESS/DEAD |
| `publish_attempt_count` | INT UNSIGNED | NO | 0 |  |
| `next_execute_time` | DATETIME(3) | NO |  |  |
| `lease_owner` | VARCHAR(128) | YES |  |  |
| `lease_until` | DATETIME(3) | YES |  |  |
| `last_stream_record_id` | VARCHAR(64) | YES |  |  |
| `published_time` | DATETIME(3) | YES |  | 最近一次发布成功时间 |
| `acked_time` | DATETIME(3) | YES |  | Analytics终态ACK时间 |
| `last_error` | VARCHAR(1024) | YES |  | 最近一次发布失败摘要 |
| `create_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |
| `update_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |
| `route_bucket` | TINYINT UNSIGNED GENERATED ALWAYS AS ( MOD(CRC32(CONCAT( `source_system`, '#', COALESCE(NULLIF(`source_object_id`, ''), 'LEGACY_UNROUTED') )), 64) ) VIRTUAL | YES |  | 稳定三方业务键CRC32模64；缺少对象ID的旧数据进入单一串行桶 |

主键、唯一键和二级索引：
- `PRIMARY KEY (`id`)`
- `UNIQUE KEY `uk_source_event` (`source_event_kind`, `source_event_id`)`
- `KEY `idx_publish_claim_v2` (`status`, `next_execute_time`, `id`, `lease_until`)`
- `KEY `idx_outbox_pull_claim` (`status`, `create_time`, `id`, `next_execute_time`, `lease_until`)`
- `KEY `idx_monitor_status_source_create` (`status`, `source_system`, `create_time`)`
- `KEY `idx_terminal_cleanup` (`status`, `update_time`, `id`)`
- `KEY `idx_lease_owner` (`lease_owner`)`
- `KEY `idx_storage` (`storage_id`, `payload_sha256`)`
- `KEY `idx_source_object_version` (`source_system`, `source_object_id`, `message_version`)`
- `KEY `idx_analysis_backfill_route_page` (`source_system`, `content_type`, `route_bucket`, `create_time`, `id`)`

### `trade_flow.trade_event_delivery_task`

- 证据类型：DDL。文件 `docs/sql/init/trade-flow-init.sql`。实际数据库未核对。
- 表注释：第三方来源事件面向接收方的可靠投递任务
- 引擎：`ENGINE=InnoDB`
- 代码引用：EventDeliveryTaskDO。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `id` | BIGINT UNSIGNED | NO |  | 自增主键 |
| `event_id` | BIGINT UNSIGNED | NO |  | 关联trade_thirdparty_event.id |
| `receiver_code` | VARCHAR(32) | NO |  | 接收方稳定编码，如SSM |
| `delivery_mode` | VARCHAR(16) | NO |  | HTTP_PUSH/PULL/MQ |
| `source_system` | TINYINT UNSIGNED | NO |  | 来源系统编码 |
| `source_event_type` | VARCHAR(64) | NO |  | 第三方原始事件类型 |
| `third_event_key` | VARCHAR(128) | NO |  | 第三方业务事件键 |
| `message_version` | BIGINT UNSIGNED | NO |  | 第三方非负消息版本 |
| `storage_id` | BIGINT | NO |  | 关联trade_storage.id |
| `payload_sha256` | BINARY(32) | NO |  | 与storage_id共同定位Storage |
| `status` | TINYINT UNSIGNED | NO | 0 | 0待投递；1投递中；2成功；3待重试；4已耗尽 |
| `attempt_count` | INT UNSIGNED | NO | 0 | 已执行投递尝试次数 |
| `max_attempt_count` | INT UNSIGNED | NO | 3 | 总尝试次数：即时1次+定时阶梯重试2次 |
| `next_execute_time` | DATETIME(3) | NO |  | 下次允许执行时间 |
| `lease_owner` | VARCHAR(128) | YES |  | 当前执行实例 |
| `lease_until` | DATETIME(3) | YES |  | 执行租约截止时间 |
| `last_error` | VARCHAR(1024) | YES |  | 最近失败摘要 |
| `last_response` | VARCHAR(1024) | YES |  | 最近成功响应摘要 |
| `delivered_time` | DATETIME(3) | YES |  | 最近成功投递或ACK时间 |
| `create_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |
| `update_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |

主键、唯一键和二级索引：
- `PRIMARY KEY (`id`)`
- `UNIQUE KEY `uk_event_receiver` (`event_id`, `receiver_code`)`
- `KEY `idx_task_claim` (`receiver_code`, `delivery_mode`, `status`, `next_execute_time`, `lease_until`, `id`)`
- `KEY `idx_task_lease_claim` (`receiver_code`, `delivery_mode`, `status`, `lease_until`, `id`)`
- `KEY `idx_terminal_cleanup` (`status`, `update_time`, `id`)`
- `KEY `idx_storage` (`storage_id`, `payload_sha256`)`
- `KEY `idx_event_version` (`source_system`, `third_event_key`, `message_version`)`

### `trade_flow.trade_event_delivery_attempt_log`

- 证据类型：DDL。文件 `docs/sql/init/trade-flow-init.sql`。实际数据库未核对。
- 表注释：第三方来源事件投递单次尝试审计
- 引擎：`ENGINE=InnoDB`
- 代码引用：EventDeliveryAttemptLogDO。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `id` | BIGINT UNSIGNED | NO |  | 自增主键 |
| `delivery_task_id` | BIGINT UNSIGNED | NO |  | 关联trade_event_delivery_task.id |
| `attempt_no` | INT UNSIGNED | NO |  | 任务投递尝试序号 |
| `trigger_type` | VARCHAR(16) | NO |  | IMMEDIATE/SCHEDULED/PULL_ACK |
| `success` | TINYINT UNSIGNED | NO |  | 0失败；1成功 |
| `response_summary` | VARCHAR(1024) | YES |  | 成功或失败响应体摘要 |
| `exception_type` | VARCHAR(255) | YES |  | 失败异常完整类名 |
| `error_message` | VARCHAR(1024) | YES |  | 失败原因摘要 |
| `duration_ms` | BIGINT UNSIGNED | NO | 0 | 本次尝试耗时毫秒 |
| `create_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |

主键、唯一键和二级索引：
- `PRIMARY KEY (`id`)`
- `KEY `idx_task_time` (`delivery_task_id`, `create_time`)`
- `KEY `idx_create_time` (`create_time`, `id`)`
- `KEY `idx_success_time` (`success`, `create_time`)`

### `trade_flow.trade_event_ingest_failure_log`

- 证据类型：DDL。文件 `docs/sql/init/trade-flow-init.sql`。实际数据库未核对。
- 表注释：第三方事件接入全阶段失败审计
- 引擎：`ENGINE=InnoDB`
- 代码引用：EventIngestFailureLogDO。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `id` | BIGINT | NO |  | 自增主键 |
| `request_id` | CHAR(32) | NO |  | 单次接入请求排障ID，与响应及服务端日志一致 |
| `source_system` | TINYINT UNSIGNED | NO |  | 来源系统：0未知；1富智云；2淘宝闪购；3美团外卖；4京东秒送 |
| `content_type` | TINYINT UNSIGNED | NO |  | 内容类型：1订单；2支付；3历史主数据快照兼容值 |
| `raw_id` | BIGINT | YES |  | Storage成功时关联trade_storage.id；前置失败时为空 |
| `payload_sha256` | BINARY(32) | NO |  | 原始内容SHA-256；Storage成功后与raw_id共同定位内容 |
| `failure_stage` | VARCHAR(32) | NO |  | 失败阶段：SIGNATURE_VERIFY/STORAGE_PERSIST/EVENT_FIELD_PARSE/EVENT_PERSIST |
| `error_code` | INT | NO |  | 标准错误码 |
| `exception_type` | VARCHAR(255) | NO |  | 最外层异常完整类名 |
| `failure_reason` | VARCHAR(1024) | NO |  | 最多8层异常链摘要 |
| `third_event_key` | VARCHAR(128) | YES |  | 已成功提取时记录第三方事件键 |
| `message_version` | BIGINT UNSIGNED | YES |  | 已成功提取时记录第三方消息版本 |
| `create_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) | 失败发生时间 |

主键、唯一键和二级索引：
- `PRIMARY KEY (`id`)`
- `UNIQUE KEY `uk_request_id` (`request_id`)`
- `KEY `idx_storage` (`raw_id`, `payload_sha256`)`
- `KEY `idx_payload_time` (`payload_sha256`, `create_time`)`
- `KEY `idx_type_stage_time` (`content_type`, `failure_stage`, `create_time`)`
- `KEY `idx_create_time_id` (`create_time`, `id`)`

### `trade_flow.mtx_id_pool_contract`

- 证据类型：DDL。文件 `docs/sql/init/trade-flow-init.sql`。实际数据库未核对。
- 引擎：`ENGINE=InnoDB`
- 代码引用：发号契约。两个 MySQL 脚本都建。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `singleton_id` | TINYINT | NO |  |  |
| `schema_version` | INT | NO |  |  |
| `fingerprint` | CHAR(64) | NO |  |  |
| `CHECK` | (singleton_id = 1) | YES |  |  |

### `trade_flow.mtx_id_worker`

- 证据类型：DDL。文件 `docs/sql/init/trade-flow-init.sql`。实际数据库未核对。
- 引擎：`ENGINE=InnoDB`
- 代码引用：发号 worker。两个 MySQL 脚本都建。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `datacenter_id` | TINYINT UNSIGNED | NO |  |  |
| `worker_id` | TINYINT UNSIGNED | NO |  |  |
| `application_name` | VARCHAR(96) | YES |  |  |
| `instance_name` | VARCHAR(160) | YES |  |  |
| `process_id` | BINARY(16) | YES |  |  |
| `engine_name` | VARCHAR(96) | YES |  |  |
| `claim_token` | BINARY(16) | YES |  |  |
| `revision` | BIGINT | NO | 0 |  |
| `window_lower_ms` | BIGINT | YES |  |  |
| `high_watermark_ms` | BIGINT | NO |  |  |
| `expire_at` | DATETIME(3) | YES |  |  |
| `CHECK` | (datacenter_id <= 31 AND worker_id <= 31) | YES |  |  |
| `CHECK` | (revision >= 0) | YES |  |  |

主键、唯一键和二级索引：
- `PRIMARY KEY (datacenter_id, worker_id)`

## Schema `trade_analytics`

文件 `docs/sql/init/trade-analytics-init.sql`。表 47 张。

### `trade_analytics.trade_ssm_group_sync_checkpoint`

- 证据类型：DDL。文件 `docs/sql/init/trade-analytics-init.sql`。实际数据库未核对。
- 引擎：`ENGINE=InnoDB`
- 代码引用：本次未发现 @TableName。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `id` | BIGINT | NO |  |  |
| `tenant_id` | VARCHAR(32) | NO |  | SSM来源租户原始十进制字符串，不是本地品牌 |
| `domain` | VARCHAR(8) | NO |  | SHOP/GOODS |
| `next_version` | BIGINT | NO | 1 |  |
| `lease_owner` | VARCHAR(128) | YES |  |  |
| `lease_epoch` | BIGINT | NO | 0 |  |
| `lease_until` | DATETIME(3) | YES |  |  |
| `inflight_version` | BIGINT | YES |  |  |
| `load_label` | VARCHAR(128) | YES |  |  |
| `request_digest` | CHAR(64) | YES |  | 冻结请求完整SHA256；不保存正文 |
| `expected_rows` | INT | NO | 0 |  |
| `state` | VARCHAR(32) | NO | 'IDLE' |  |
| `send_attempts` | INT | NO | 0 |  |
| `last_visible_version` | BIGINT | NO | 0 |  |
| `mapping_applied_version` | BIGINT | NO | 0 |  |
| `last_error_code` | VARCHAR(64) | YES |  |  |
| `update_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |

主键、唯一键和二级索引：
- `PRIMARY KEY (`id`)`
- `UNIQUE KEY `uk_ssm_group_scope` (`tenant_id`, `domain`)`
- `CONSTRAINT `ck_ssm_group_scope` CHECK (`tenant_id` REGEXP '^[0-9]+$' AND `domain` IN ('SHOP', 'GOODS'))`
- `CONSTRAINT `ck_ssm_group_versions` CHECK ( `next_version` > `last_visible_version` AND `mapping_applied_version` <= `last_visible_version` AND `mapping_applied_version` >= 0 AND `lease_epoch` >= 0)`
- `CONSTRAINT `ck_ssm_group_capacity` CHECK (`expected_rows` BETWEEN 0 AND 100000 AND `send_attempts` BETWEEN 0 AND 3)`

### `trade_analytics.trade_analysis_event_process`

- 证据类型：DDL。文件 `docs/sql/init/trade-analytics-init.sql`。实际数据库未核对。
- 表注释：Analytics事件处理当前账本、重试与Ingress ACK状态
- 引擎：`ENGINE=InnoDB`
- 代码引用：AnalysisEventProcessDO。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `outbox_id` | BIGINT UNSIGNED | NO |  | Ingress分析outbox主键 |
| `schema_version` | INT UNSIGNED | NO | 1 | 分析消息合同版本；外卖平台新消息使用2 |
| `source_event_id` | BIGINT UNSIGNED | NO |  | Ingress来源事件表主键 |
| `event_kind` | VARCHAR(16) | NO |  | CALLBACK/QUERY |
| `source_system` | TINYINT UNSIGNED | NO |  | 来源系统编码 |
| `content_type` | TINYINT UNSIGNED | NO |  | 内容类型：1订单；2支付 |
| `source_event_type` | VARCHAR(64) | YES |  | 来源事件类型 |
| `source_object_id` | VARCHAR(128) | YES |  | 来源业务对象ID |
| `event_key` | VARCHAR(128) | NO |  | 来源业务事件键 |
| `message_version` | BIGINT UNSIGNED | NO |  | 来源消息版本 |
| `storage_id` | BIGINT | NO |  | 关联trade_storage.id |
| `payload_sha256` | BINARY(32) | NO |  | 与storage_id共同定位Storage |
| `event_received_time` | DATETIME(3) | YES |  | 来源事件在Ingress的接收时间；历史记录允许为空 |
| `status` | TINYINT UNSIGNED | NO |  | 0处理中；1待重试；2成功；3人工隔离；4缓冲；5冲突；6带冲突成功；7等待恢复 |
| `attempt_count` | INT UNSIGNED | NO | 0 | 处理尝试次数 |
| `max_attempt_count` | INT UNSIGNED | NO |  | 达到后转人工隔离 |
| `next_execute_time` | DATETIME(3) | NO |  | 下次处理时间 |
| `lease_owner` | VARCHAR(128) | YES |  | 处理或ACK租约持有者 |
| `lease_until` | DATETIME(3) | YES |  | 租约截止时间 |
| `last_stream_record_id` | VARCHAR(64) | YES |  |  |
| `last_trigger` | VARCHAR(16) | NO |  |  |
| `last_error_type` | VARCHAR(255) | YES |  | 最近异常类名 |
| `last_error` | VARCHAR(1024) | YES |  | 最近异常摘要 |
| `processed_time` | DATETIME(3) | YES |  | 最近成功或隔离时间 |
| `flush_task_id` | BIGINT | YES |  | 绑定的不可变Flush任务ID |
| `fact_write_task_id` | BIGINT | YES |  | 绑定的通用事实写仓任务ID |
| `sink_revision` | BIGINT | YES |  | 冻结快照的全事实写入版本 |
| `conflict_count` | INT | NO | 0 | 本事件发生同版本冲突的Domain数量 |
| `ingress_ack_status` | TINYINT UNSIGNED | NO | 0 | 0待ACK；1已ACK；2重试耗尽待人工处理 |
| `ingress_ack_result` | VARCHAR(16) | YES |  | SUCCESS/DEAD |
| `next_ack_time` | DATETIME(3) | NO |  | 下次Ingress ACK时间 |
| `ack_attempt_count` | INT UNSIGNED | NO | 0 | Ingress ACK尝试次数 |
| `last_ack_error` | VARCHAR(1024) | YES |  | 最近Ingress ACK失败摘要 |
| `create_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |
| `update_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |

主键、唯一键和二级索引：
- `PRIMARY KEY (`outbox_id`)`
- `KEY `idx_process_claim` (`status`, `next_execute_time`, `lease_until`, `outbox_id`)`
- `KEY `idx_ack_claim_v3` (`ingress_ack_status`, `status`, `next_ack_time`, `outbox_id`, `lease_until`)`
- `KEY `idx_monitor_status_source_create` (`status`, `source_system`, `create_time`)`
- `KEY `idx_event_recovery_exact` (`status`, `source_system`, `content_type`, `event_kind`, `source_event_type`, `last_error_type`, `max_attempt_count`, `create_time`, `outbox_id`, `attempt_count`)`
- `KEY `idx_terminal_cleanup` (`status`, `update_time`, `outbox_id`)`
- `KEY `idx_processed_terminal` (`processed_time`, `status`)`
- `KEY `idx_fact_write_task` (`fact_write_task_id`)`
- `KEY `idx_orphan_detection` (`source_system`, `content_type`, `event_kind`, `source_object_id`, `event_received_time`, `source_event_type`)`
- `KEY `idx_storage` (`storage_id`, `payload_sha256`)`

### `trade_analytics.trade_analysis_orphan_event_failure`

- 证据类型：DDL。文件 `docs/sql/init/trade-analytics-init.sql`。实际数据库未核对。
- 表注释：第三方业务键粒度的孤儿事件旁路失败记录；不自动回灌
- 引擎：`ENGINE=InnoDB`
- 代码引用：AnalysisOrphanEventFailureDO。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `id` | BIGINT UNSIGNED | NO |  |  |
| `platform_code` | VARCHAR(16) | NO |  | 第三方平台编码，如TAOBAO/MEITUAN |
| `thirdparty_key` | VARCHAR(128) | NO |  | 平台内业务唯一键，订单场景为第三方订单号 |
| `status` | TINYINT UNSIGNED | NO |  | 0可疑；1确诊；2已恢复 |
| `failure_code` | VARCHAR(64) | NO |  | 通用失败码：REQUIRED_EVENT_MISSING |
| `required_event_code` | VARCHAR(64) | NO |  | 当前缺失的平台必需事件码 |
| `failure_detail` | VARCHAR(512) | YES |  | 最近判定摘要 |
| `first_event_received_time` | DATETIME(3) | NO |  | 该业务键首次事件接收时间 |
| `suspected_time` | DATETIME(3) | NO |  | 首次事件后30分钟仍缺必需事件的登记时间 |
| `confirm_after_time` | DATETIME(3) | NO |  | 首次事件后6小时的最早确诊时间 |
| `confirmed_time` | DATETIME(3) | YES |  | 全量事件复核后确诊时间 |
| `resolved_time` | DATETIME(3) | YES |  | 必需事件后到后的恢复时间 |
| `last_check_time` | DATETIME(3) | NO |  | 最近一次权威来源事件检查时间 |
| `create_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |
| `update_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |

主键、唯一键和二级索引：
- `PRIMARY KEY (`id`)`
- `UNIQUE KEY `uk_platform_thirdparty_key` (`platform_code`, `thirdparty_key`)`
- `KEY `idx_status_confirm` (`status`, `confirm_after_time`, `id`)`
- `KEY `idx_platform_status_check` (`platform_code`, `status`, `last_check_time`, `id`)`

### `trade_analytics.trade_analysis_event_attempt_log`

- 证据类型：DDL。文件 `docs/sql/init/trade-analytics-init.sql`。实际数据库未核对。
- 表注释：Analytics事件每次处理尝试审计
- 引擎：`ENGINE=InnoDB`
- 代码引用：AnalysisEventAttemptLogDO。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `id` | BIGINT UNSIGNED | NO |  |  |
| `outbox_id` | BIGINT UNSIGNED | NO |  | 关联处理账本 |
| `attempt_no` | INT UNSIGNED | NO |  | 处理尝试序号 |
| `trigger_type` | VARCHAR(16) | NO |  |  |
| `success` | TINYINT UNSIGNED | NO |  | 0失败；1成功 |
| `terminal` | TINYINT UNSIGNED | NO | 0 | 本次失败后是否已隔离 |
| `stream_record_id` | VARCHAR(64) | YES |  |  |
| `error_type` | VARCHAR(255) | YES |  | 异常类名 |
| `error_message` | VARCHAR(1024) | YES |  | 异常摘要 |
| `duration_ms` | BIGINT UNSIGNED | NO | 0 | 处理耗时毫秒 |
| `create_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |

主键、唯一键和二级索引：
- `PRIMARY KEY (`id`)`
- `UNIQUE KEY `uk_outbox_attempt` (`outbox_id`, `attempt_no`)`
- `KEY `idx_outbox_time` (`outbox_id`, `create_time`)`
- `KEY `idx_create_time_id` (`create_time`, `id`)`
- `KEY `idx_failure_time` (`success`, `create_time`, `id`)`

### `trade_analytics.trade_analysis_stream_dead_letter`

- 证据类型：DDL。文件 `docs/sql/init/trade-analytics-init.sql`。实际数据库未核对。
- 引擎：`ENGINE=InnoDB`
- 代码引用：AnalysisStreamDeadLetterDO。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `id` | BIGINT UNSIGNED | NO |  |  |
| `stream_key` | VARCHAR(128) | NO |  |  |
| `consumer_group` | VARCHAR(128) | NO |  |  |
| `stream_record_id` | VARCHAR(64) | NO |  |  |
| `payload_json` | JSON | NO |  |  |
| `source_record_missing` | TINYINT UNSIGNED | NO | 0 |  |
| `delivery_count` | BIGINT UNSIGNED | NO |  |  |
| `isolation_reason` | VARCHAR(64) | NO |  |  |
| `create_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |
| `update_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |

主键、唯一键和二级索引：
- `PRIMARY KEY (`id`)`
- `UNIQUE KEY `uk_stream_group_record` (`stream_key`, `consumer_group`, `stream_record_id`)`
- `KEY `idx_create_time_id` (`create_time`, `id`)`

### `trade_analytics.trade_data_retention_control`

- 证据类型：DDL。文件 `docs/sql/init/trade-analytics-init.sql`。实际数据库未核对。
- 表注释：Trade Ingress数据保留任务跨实例租约
- 引擎：`ENGINE=InnoDB`
- 代码引用：保留任务租约。Ingress 与 Analytics 各有同名表。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `task_name` | VARCHAR(64) | NO |  | 保留任务稳定名称 |
| `lease_owner` | VARCHAR(128) | YES |  | 当前执行实例 |
| `lease_until` | DATETIME(3) | YES |  | 租约截止时间 |
| `create_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |
| `update_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |

主键、唯一键和二级索引：
- `PRIMARY KEY (`task_name`)`

### `trade_analytics.trade_analysis_backfill_task`

- 证据类型：DDL。文件 `docs/sql/init/trade-analytics-init.sql`。实际数据库未核对。
- 表注释：独立历史事实回灌主任务；不复用实时事件账本
- 引擎：`ENGINE=InnoDB`
- 代码引用：AnalysisBackfillTaskDO。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `id` | BIGINT UNSIGNED | NO |  | 历史回灌主任务ID |
| `source_system` | TINYINT UNSIGNED | NO |  | 来源系统编码 |
| `content_type` | TINYINT UNSIGNED | NO |  | 内容类型：1订单；2支付 |
| `start_date` | DATE | NO |  | 回灌开始自然日，包含 |
| `end_date` | DATE | NO |  | 回灌结束自然日，包含 |
| `target_facts` | VARCHAR(512) | NO |  | 来源Adapter自动选择的目标事实类型CSV |
| `target_table_version` | VARCHAR(16) | NO |  | 目标Doris物理表版本，如v1 |
| `source_range_json` | TEXT | YES |  | 任务来源上界与目标范围JSON |
| `status` | TINYINT UNSIGNED | NO |  | 0待执行；1执行中；2成功；3部分失败；4取消；5停止待核实；6准备范围 |
| `total_shard_count` | INT UNSIGNED | NO |  | 固定业务哈希桶总数，当前为64 |
| `success_shard_count` | INT UNSIGNED | NO | 0 | 成功桶数 |
| `failed_shard_count` | INT UNSIGNED | NO | 0 | 失败桶数 |
| `estimated_event_count` | BIGINT UNSIGNED | YES |  | 任务创建时固化的来源工作单元数；淘宝为去重217订单锚点数；历史任务为空 |
| `processed_event_count` | BIGINT UNSIGNED | NO | 0 | 已提交并检查点确认的来源工作单元数 |
| `isolated_event_count` | BIGINT UNSIGNED | NO | 0 | 已持久化隔离且检查点已越过的来源事件数 |
| `created_by` | VARCHAR(128) | NO |  | 操作人或发布单号 |
| `create_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |
| `update_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |

主键、唯一键和二级索引：
- `CONSTRAINT `chk_backfill_range_size` CHECK (`source_range_json` IS NULL OR (JSON_VALID(`source_range_json`) AND OCTET_LENGTH(`source_range_json`) <= 16384))`
- `PRIMARY KEY (`id`)`
- `KEY `idx_task_status_time` (`status`, `update_time`, `id`)`

### `trade_analytics.trade_analysis_backfill_shard`

- 证据类型：DDL。文件 `docs/sql/init/trade-analytics-init.sql`。实际数据库未核对。
- 表注释：历史事实回灌业务哈希桶；桶间并行、桶内按日期和复合游标串行
- 引擎：`ENGINE=InnoDB`
- 代码引用：AnalysisBackfillShardDO。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `id` | BIGINT UNSIGNED | NO |  | 业务哈希桶任务ID |
| `task_id` | BIGINT UNSIGNED | NO |  | 关联回灌主任务ID |
| `route_bucket` | TINYINT UNSIGNED | NO |  | 稳定三方业务键哈希桶：0-63 |
| `current_date` | DATE | NO |  | 桶内当前顺序处理的自然日 |
| `status` | TINYINT UNSIGNED | NO |  | 0待执行；1执行中；2待重试；3成功；4失败；5取消；6完成但有事件隔离 |
| `cursor_event_received_time` | DATETIME(3) | NO |  | Ingress接收时间复合游标 |
| `cursor_source_event_kind` | VARCHAR(32) | NO | '' | 来源事件表类型；空字符串表示当天初始游标 |
| `cursor_source_event_id` | BIGINT UNSIGNED | NO | 0 | 同一接收时间及来源事件表内的事件主键游标 |
| `processed_event_count` | BIGINT UNSIGNED | NO | 0 | 已提交并检查点确认的事件数 |
| `isolated_event_count` | BIGINT UNSIGNED | NO | 0 | 已持久化隔离且检查点已越过的来源事件数 |
| `attempt_count` | INT UNSIGNED | NO | 0 | 连续处理失败次数；任一页检查点成功后清零，领取和租约接管不计数 |
| `max_attempt_count` | INT UNSIGNED | NO |  | 最大连续失败次数；达到后分片失败 |
| `next_execute_time` | DATETIME(3) | NO |  | 下次可执行时间 |
| `lease_generation` | BIGINT | NO | 0 | 分片执行领取代次 |
| `source_baseline_json` | VARCHAR(2048) | YES |  | 发现集合基准数量与摘要JSON |
| `source_progress_json` | VARCHAR(2048) | YES |  | 已扫描集合数量与摘要JSON |
| `lease_owner` | VARCHAR(128) | YES |  | 当前执行实例 |
| `lease_until` | DATETIME(3) | YES |  | 执行租约截止时间 |
| `last_error` | VARCHAR(2000) | YES |  | 最近失败摘要 |
| `create_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |
| `update_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |

主键、唯一键和二级索引：
- `CONSTRAINT `chk_backfill_baseline_size` CHECK (`source_baseline_json` IS NULL OR (JSON_VALID(`source_baseline_json`) AND OCTET_LENGTH(`source_baseline_json`) <= 2048))`
- `CONSTRAINT `chk_backfill_progress_size` CHECK (`source_progress_json` IS NULL OR (JSON_VALID(`source_progress_json`) AND OCTET_LENGTH(`source_progress_json`) <= 2048))`
- `CONSTRAINT `chk_backfill_generation` CHECK (`lease_generation` >= 0)`
- `PRIMARY KEY (`id`)`
- `UNIQUE KEY `uk_task_route_bucket` (`task_id`, `route_bucket`)`
- `KEY `idx_shard_claim` (`status`, `next_execute_time`, `lease_until`, `id`)`
- `KEY `idx_task_status` (`task_id`, `status`, `id`)`

### `trade_analytics.trade_analysis_backfill_event_failure`

- 证据类型：DDL。文件 `docs/sql/init/trade-analytics-init.sql`。实际数据库未核对。
- 表注释：历史回灌中已持久化隔离的来源冲突事件
- 引擎：`ENGINE=InnoDB`
- 代码引用：AnalysisBackfillEventFailureDO。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `id` | BIGINT UNSIGNED | NO |  | 回灌事件失败ID |
| `task_id` | BIGINT UNSIGNED | NO |  | 关联回灌主任务ID |
| `shard_id` | BIGINT UNSIGNED | NO |  | 关联回灌桶ID |
| `route_bucket` | TINYINT UNSIGNED | NO |  | 稳定业务键路由桶：0-63 |
| `business_date` | DATE | NO |  | 事件所在回灌自然日 |
| `source_event_kind` | VARCHAR(32) | NO |  | 来源事件表类型 |
| `source_event_id` | BIGINT UNSIGNED | NO |  | 来源事件表主键 |
| `source_object_id` | VARCHAR(128) | YES |  | 来源稳定业务对象ID |
| `source_received_time` | DATETIME(3) | NO |  | Ingress权威接收时间 |
| `storage_id` | BIGINT UNSIGNED | NO |  | Storage元数据ID |
| `payload_sha256` | BINARY(32) | NO |  | Storage原文完整SHA-256 |
| `order_key` | VARCHAR(160) | NO |  | 平台码与订单号组成的Canonical订单键 |
| `conflict_domains` | VARCHAR(255) | NO |  | 发生来源内容冲突的Domain列表 |
| `error_message` | VARCHAR(2000) | NO |  | 冲突摘要 |
| `retry_status` | TINYINT UNSIGNED | NO | 0 | 0待重试；1重试中；2已修复；3重试失败 |
| `retry_count` | INT UNSIGNED | NO | 0 | 精确重放尝试次数 |
| `last_retry_time` | DATETIME(3) | YES |  | 最近一次精确重放开始时间 |
| `last_retry_error` | VARCHAR(2000) | YES |  | 最近一次精确重放失败摘要 |
| `resolved_time` | DATETIME(3) | YES |  | 精确重放成功时间 |
| `create_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |
| `update_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |

主键、唯一键和二级索引：
- `PRIMARY KEY (`id`)`
- `UNIQUE KEY `uk_task_source_event` (`task_id`, `source_event_kind`, `source_event_id`)`
- `KEY `idx_task_shard_event` (`task_id`, `shard_id`, `source_event_id`)`
- `KEY `idx_task_retry_status` (`task_id`, `retry_status`, `id`)`
- `KEY `idx_source_event_retry` (`source_event_kind`, `source_event_id`, `retry_status`)`
- `KEY `idx_order_key` (`order_key`, `id`)`

### `trade_analytics.dim_brand`

- 证据类型：DDL。文件 `docs/sql/init/trade-analytics-init.sql`。实际数据库未核对。
- 表注释：租户中心同步的内部品牌维表
- 引擎：`ENGINE=InnoDB`
- 代码引用：BrandDimDO。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `brand_id` | BIGINT UNSIGNED | NO |  | 内部品牌ID，雪花领域internal-brand生成 |
| `tenant_id` | VARCHAR(32) | YES |  | 租户中心租户ID，按原始字符串保留前导零 |
| `brand_name` | VARCHAR(128) | NO |  | 品牌名称 |
| `is_deleted` | TINYINT UNSIGNED | NO | 0 | 0有效；1已删除或SYSTEM_OTHER尚未初始化 |
| `active_brand_name` | VARCHAR(128) GENERATED ALWAYS AS (IF(`is_deleted` = 0, `brand_name`, | YES |  | 仅有效行参与唯一约束 |
| `create_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |
| `update_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |

主键、唯一键和二级索引：
- `PRIMARY KEY (`brand_id`)`
- `UNIQUE KEY `uk_tenant_id` (`tenant_id`)`
- `UNIQUE KEY `uk_active_brand_name` (`active_brand_name`)`

### `trade_analytics.dim_brand_thirdparty`

- 证据类型：DDL。文件 `docs/sql/init/trade-analytics-init.sql`。实际数据库未核对。
- 表注释：品牌与第三方账号关联表
- 引擎：`ENGINE=InnoDB`
- 代码引用：BrandThirdpartyRelDO。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `id` | BIGINT UNSIGNED | NO |  | 雪花ID |
| `thirdparty_type` | VARCHAR(32) | NO |  | 平台类型：FZY/TAOBAO/MEITUAN/JD |
| `third_uniq_id` | VARCHAR(128) | NO |  | 第三方唯一标识：富友=mchntCd；淘宝=开放平台账号；美团=回调app_id；京东=appKey |
| `brand_id` | BIGINT UNSIGNED | NO |  | 归属内部品牌ID |
| `third_config` | JSON | YES |  | 第三方账号配置：同步开关、扩展参数等 |
| `is_deleted` | TINYINT UNSIGNED | NO | 0 | 0有效；1已删除 |
| `active_third_uniq_id` | VARCHAR(128) GENERATED ALWAYS AS (IF(`is_deleted` = 0, `third_uniq_id`, | YES |  | 仅有效行参与唯一约束 |
| `active_brand_id` | BIGINT UNSIGNED GENERATED ALWAYS AS (IF(`is_deleted` = 0, `brand_id`, | YES |  | 仅有效行参与同租户同平台唯一约束 |
| `create_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |
| `update_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |

主键、唯一键和二级索引：
- `PRIMARY KEY (`id`)`
- `UNIQUE KEY `uk_active_type_uniq_id` (`thirdparty_type`, `active_third_uniq_id`)`
- `UNIQUE KEY `uk_active_brand_type` (`active_brand_id`, `thirdparty_type`)`
- `KEY `idx_brand_id` (`brand_id`)`

### `trade_analytics.dim_jd_account_anchor`

- 证据类型：DDL。文件 `docs/sql/init/trade-analytics-init.sql`。实际数据库未核对。
- 表注释：京东appKey与商家号组合记录
- 引擎：`ENGINE=InnoDB`
- 代码引用：JdAccountAnchorDO。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `id` | BIGINT UNSIGNED | NO |  | 账号锚点ID |
| `app_key` | VARCHAR(128) | NO |  | 京东应用appKey |
| `vender_id` | VARCHAR(128) | NO |  | 京东商家编码；沿用官方字段拼写 |
| `create_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |
| `update_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |

主键、唯一键和二级索引：
- `PRIMARY KEY (`id`)`
- `UNIQUE KEY `uk_jd_account` (`app_key`, `vender_id`)`

### `trade_analytics.dim_jd_account_binding`

- 证据类型：DDL。文件 `docs/sql/init/trade-analytics-init.sql`。实际数据库未核对。
- 表注释：京东账号与品牌的接收时间区间记录
- 引擎：`ENGINE=InnoDB`
- 代码引用：JdAccountBindingDO。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `id` | BIGINT UNSIGNED | NO |  | 绑定记录ID |
| `anchor_id` | BIGINT UNSIGNED | NO |  | 账号锚点ID |
| `binding_version` | BIGINT UNSIGNED | NO |  | 同账号单调递增绑定版本 |
| `app_key` | VARCHAR(128) | NO |  |  |
| `vender_id` | VARCHAR(128) | NO |  |  |
| `brand_id` | BIGINT UNSIGNED | NO |  | 内部租户品牌ID |
| `effective_start_received_time` | DATETIME(3) | NO |  | 来源接收时间区间下界，含边界 |
| `effective_end_received_time` | DATETIME(3) | YES |  | 来源接收时间区间上界，不含边界 |
| `status` | VARCHAR(16) | NO | 'ACTIVE' |  |
| `create_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |
| `update_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |

主键、唯一键和二级索引：
- `PRIMARY KEY (`id`)`
- `UNIQUE KEY `uk_jd_binding_version` (`anchor_id`, `binding_version`)`
- `KEY `idx_jd_binding_brand_time` (`brand_id`, `status`, `effective_start_received_time`, `effective_end_received_time`)`
- `CONSTRAINT `fk_jd_binding_anchor` FOREIGN KEY (`anchor_id`) REFERENCES `dim_jd_account_anchor` (`id`)`

### `trade_analytics.thirdparty_sync_watermark`

- 证据类型：DDL。文件 `docs/sql/init/trade-analytics-init.sql`。实际数据库未核对。
- 表注释：第三方同步水位表
- 引擎：`ENGINE=InnoDB`
- 代码引用：ThirdpartySyncWatermarkDO。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `id` | BIGINT UNSIGNED | NO |  | 雪花ID |
| `thirdparty_type` | VARCHAR(32) | NO |  | 平台类型：FZY/MEITUAN |
| `third_uniq_id` | VARCHAR(128) | NO |  | 同步主体：富友=mchntCd；美团=app_id:app_poi_code |
| `sync_type` | VARCHAR(32) | NO |  | 同步业务类型：GOODS=商品目录 |
| `last_sync_time` | DATETIME(3) | NO |  | 上次成功同步时间或窗口截止时间 |
| `last_full_sync_time` | DATETIME(3) | YES |  | 上次成功全量对账时间 |
| `create_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |
| `update_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |

主键、唯一键和二级索引：
- `PRIMARY KEY (`id`)`
- `UNIQUE KEY `uk_type_uniq_sync` (`thirdparty_type`, `third_uniq_id`, `sync_type`)`

### `trade_analytics.trade_mapping_compensation`

- 证据类型：DDL。文件 `docs/sql/init/trade-analytics-init.sql`。实际数据库未核对。
- 表注释：商品、门店等统一映射补偿任务
- 引擎：`ENGINE=InnoDB`
- 代码引用：MappingCompensationDO。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `id` | BIGINT UNSIGNED | NO |  |  |
| `mapping_type` | VARCHAR(32) | NO |  | PRODUCT/SHOP/MEITUAN_SHOP/MEITUAN_GOODS/JD_PRODUCT/GOODS_FACT |
| `task_key` | VARCHAR(512) | NO |  | 类型内稳定幂等键 |
| `source_system` | VARCHAR(32) | NO |  | 来源系统：FUIOU/TAOBAO/MEITUAN/JD |
| `source_merchant_code` | VARCHAR(64) | NO |  | 富友=mchntCd；美团=app_id |
| `source_shop_id` | VARCHAR(128) | NO |  | 富友=shopId；美团=app_poi_code |
| `source_object_id` | VARCHAR(128) | NO |  | PRODUCT=goodsId；门店/美团商品目录任务=来源门店ID |
| `source_object_subtype` | VARCHAR(32) | NO | '' | NORMAL/PACKAGE/ORDER/REFUND，门店为空 |
| `source_event_kind` | VARCHAR(32) | NO |  | ORDER/PAYMENT |
| `source_event_id` | BIGINT UNSIGNED | NO |  | 来源event主键 |
| `storage_id` | BIGINT UNSIGNED | NO |  | Storage主键 |
| `payload_sha256` | BINARY(32) | NO |  | Storage原文完整SHA-256 |
| `fact_date` | DATE | YES |  | 事实分区键，商品任务必填 |
| `platform_code` | VARCHAR(16) | NO |  | Doris平台编码 |
| `platform_order_id` | VARCHAR(128) | NO | '' | 事实平台订单号 |
| `fact_row_key` | VARCHAR(128) | NO | '' | PRODUCT=itemLineKey，SHOP为空 |
| `status` | TINYINT UNSIGNED | NO | 0 | 0待处理 1执行中 2待重试 4已耗尽 |
| `attempt_count` | INT UNSIGNED | NO | 0 |  |
| `next_execute_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |
| `lease_owner` | VARCHAR(128) | YES |  |  |
| `lease_until` | DATETIME(3) | YES |  |  |
| `last_error` | VARCHAR(1000) | YES |  |  |
| `create_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |
| `update_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |

主键、唯一键和二级索引：
- `PRIMARY KEY (`id`)`
- `UNIQUE KEY `uk_mapping_task` (`mapping_type`, `task_key`)`
- `KEY `idx_candidate` (`status`, `next_execute_time`, `lease_until`)`
- `KEY `idx_mapping_type_due` (`mapping_type`, `status`, `next_execute_time`, `id`, `attempt_count`, `lease_until`)`
- `KEY `idx_exhausted_cleanup` (`status`, `update_time`)`
- `KEY `idx_source_object` (`mapping_type`, `source_system`, `source_merchant_code`, `source_object_id`)`
- `KEY `idx_source_event` (`source_event_kind`, `source_event_id`)`

### `trade_analytics.trade_analysis_order_state`

- 证据类型：DDL。文件 `docs/sql/init/trade-analytics-init.sql`。实际数据库未核对。
- 表注释：统一订单状态机持久化聚合状态
- 引擎：`ENGINE=InnoDB`
- 代码引用：AnalysisOrderStateDO。订单归并状态。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `order_key` | VARCHAR(256) | NO |  | 来源系统与平台订单号组成的订单聚合键 |
| `state_token` | CHAR(36) | NO |  | 行更新唯一凭证 |
| `replay_owner` | CHAR(36) | YES |  | 回灌执行唯一标识 |
| `replay_phase` | VARCHAR(16) | YES |  | 回灌执行阶段标识 |
| `source_system` | INT | NO |  | 统一来源系统编码 |
| `platform_code` | VARCHAR(32) | NO |  | 统一平台编码 |
| `platform_order_id` | VARCHAR(128) | NO |  | 第三方平台订单号 |
| `accumulator_json` | LONGTEXT | NO |  | 订单聚合状态、各Domain水位及字段来源JSON |
| `dirty_mask` | VARCHAR(512) | YES |  | 尚未写入Doris的脏Domain集合 |
| `dirty` | TINYINT | NO | 0 | 是否存在待Flush更新：0否，1是 |
| `flush_requested` | TINYINT | NO | 0 | 是否存在事件屏障要求立即Flush：0否，1是 |
| `first_dirty_time` | DATETIME(3) | YES |  | 当前脏窗口首次接受有效更新的时间 |
| `last_dirty_time` | DATETIME(3) | YES |  | 当前脏窗口最近接受有效更新的时间 |
| `flush_deadline` | DATETIME(3) | YES |  | Debounce策略计算出的最早Flush时间 |
| `max_wait_time` | DATETIME(3) | YES |  | 当前脏窗口强制Flush的最晚时间 |
| `next_sink_revision` | BIGINT | NO |  | 下一次冻结快照待分配的全事实写入版本 |
| `active_flush_revision` | BIGINT | YES |  | 当前正在执行或重试的冻结快照写入版本 |
| `create_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) | 记录创建时间 |
| `update_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) | 记录更新时间 |

主键、唯一键和二级索引：
- `PRIMARY KEY (order_key)`
- `KEY idx_order_state_flush (dirty, active_flush_revision, flush_deadline)`
- `KEY idx_order_state_monitor_dirty (dirty, source_system, flush_requested, active_flush_revision, flush_deadline, first_dirty_time, update_time)`
- `KEY idx_order_state_monitor_active (active_flush_revision, source_system, first_dirty_time, update_time)`
- `KEY idx_order_state_cleanup (dirty, flush_requested, active_flush_revision, update_time)`

### `trade_analytics.trade_analysis_order_replay_context`

- 证据类型：DDL。文件 `docs/sql/init/trade-analytics-init.sql`。实际数据库未核对。
- 表注释：实时及精确重放原计划上下文
- 引擎：`ENGINE=InnoDB`
- 代码引用：AnalysisOrderReplayContextDO。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `order_key` | VARCHAR(256) | NO |  | 来源系统与平台订单号组成的订单聚合键 |
| `replay_context_json` | LONGTEXT | YES |  | 实时或精确重放的不可变原计划JSON |
| `create_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |
| `update_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |

主键、唯一键和二级索引：
- `PRIMARY KEY (order_key)`

### `trade_analytics.trade_analysis_flush_task_index`

- 证据类型：DDL。文件 `docs/sql/init/trade-analytics-init.sql`。实际数据库未核对。
- 表注释：拆分后的统一事实Flush任务索引
- 引擎：`ENGINE=InnoDB`
- 代码引用：AnalysisFlushTaskIndexDO。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `task_id` | BIGINT | NO |  | Flush任务ID；高位号段标识拆分表任务 |
| `order_key` | VARCHAR(256) | NO |  | 来源系统与平台订单号组成的订单聚合键 |
| `sink_revision` | BIGINT | NO |  | 本次不可变快照的全事实写入版本 |
| `flush_reason` | VARCHAR(32) | NO |  | Flush原因：EVENT、DEBOUNCE、MAX_WAIT或SNAPSHOT |
| `status` | TINYINT | NO | 0 | 任务状态：0待执行，1执行中，2成功，3重试，4隔离 |
| `attempt_count` | INT | NO | 0 | 已执行次数 |
| `max_attempt_count` | INT | NO |  | 允许的最大执行次数 |
| `next_execute_time` | DATETIME(3) | NO |  | 下一次允许执行或重试的时间 |
| `lease_owner` | VARCHAR(128) | YES |  | 当前任务租约持有实例 |
| `lease_until` | DATETIME(3) | YES |  | 当前任务租约截止时间 |
| `last_error` | VARCHAR(1024) | YES |  | 最近一次执行失败摘要 |
| `failure_code` | VARCHAR(64) | YES |  | 等值检索的稳定失败分类；历史隔离任务由有界扫描渐进分类 |
| `success_time` | DATETIME(3) | YES |  | 全部目标事实写入成功的时间 |
| `create_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) | 任务创建时间 |
| `update_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) | 任务更新时间 |

主键、唯一键和二级索引：
- `PRIMARY KEY (`task_id`)`
- `UNIQUE KEY `uk_flush_order_revision` (`order_key`,`sink_revision`)`
- `KEY `idx_flush_retry` (`status`,`next_execute_time`,`lease_until`)`
- `KEY `idx_flush_monitor` (`status`,`order_key`,`create_time`)`
- `KEY `idx_flush_core_recovery` (`failure_code`,`status`,`max_attempt_count`,`task_id`,`attempt_count`)`
- `KEY `idx_flush_cleanup` (`status`,`update_time`,`task_id`)`

### `trade_analytics.trade_analysis_flush_task_payload`

- 证据类型：DDL。文件 `docs/sql/init/trade-analytics-init.sql`。实际数据库未核对。
- 表注释：拆分后的统一事实Flush任务不可变JSON
- 引擎：`ENGINE=InnoDB`
- 代码引用：AnalysisFlushTaskPayloadDO。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `task_id` | BIGINT | NO |  | 对应Flush任务索引ID |
| `snapshot_json` | LONGTEXT | NO |  | 创建后不可修改的CanonicalChangeSet快照 |
| `event_ids_json` | JSON | NO |  | 创建后不可修改的关联事件ID集合 |
| `create_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |

主键、唯一键和二级索引：
- `PRIMARY KEY (`task_id`)`

### `trade_analytics.trade_analysis_fact_write_task_index`

- 证据类型：DDL。文件 `docs/sql/init/trade-analytics-init.sql`。实际数据库未核对。
- 表注释：拆分后的通用事实写仓任务索引
- 引擎：`ENGINE=InnoDB`
- 代码引用：AnalysisFactWriteTaskIndexDO。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `task_id` | BIGINT | NO |  | 通用事实写仓任务ID；高位号段标识拆分表任务 |
| `outbox_id` | BIGINT UNSIGNED | NO |  | Ingress分析outbox主键 |
| `source_event_id` | BIGINT UNSIGNED | NO |  | Ingress来源事件表主键 |
| `source_system` | TINYINT UNSIGNED | NO |  | 来源系统编码 |
| `content_type` | TINYINT UNSIGNED | NO |  | 内容类型编码 |
| `storage_id` | BIGINT | NO |  | 关联trade_storage.id |
| `payload_sha256` | BINARY(32) | NO |  | 与storage_id共同定位Storage |
| `status` | TINYINT UNSIGNED | NO | 0 | 0待执行；1执行中；2成功；3待重试；4隔离 |
| `execution_class` | TINYINT UNSIGNED | NO | 0 | 调度类别：0正常实时任务；1历史恢复任务；不得由重试次数推断 |
| `attempt_count` | INT UNSIGNED | NO | 0 | 已领取执行次数 |
| `max_attempt_count` | INT UNSIGNED | NO | 10 | 最大执行次数 |
| `next_execute_time` | DATETIME(3) | NO |  | 下次执行时间 |
| `lease_owner` | VARCHAR(128) | YES |  | 当前租约持有者 |
| `lease_until` | DATETIME(3) | YES |  | 当前租约截止时间 |
| `claim_available_time` | DATETIME(3) GENERATED ALWAYS AS ( CASE WHEN `status` IN (0, 3) THEN `next_execute_time` WHEN `status` = 1 THEN `lease_until` ELSE | YES |  | 生成领取时间：待执行/重试取next_execute_time，执行中取lease_until，终态为空 |
| `last_error` | VARCHAR(1024) | YES |  | 最近失败摘要 |
| `failure_code` | VARCHAR(64) | YES |  | 等值检索的稳定失败分类；历史隔离任务由有界扫描渐进分类 |
| `event_received_time` | DATETIME(3) | YES |  | Ingress接收事件时间 |
| `success_time` | DATETIME(3) | YES |  | Doris写入成功时间 |
| `create_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |
| `update_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |

主键、唯一键和二级索引：
- `PRIMARY KEY (`task_id`)`
- `UNIQUE KEY `uk_fact_write_outbox` (`outbox_id`)`
- `KEY `idx_fact_write_claim` (`status`,`next_execute_time`,`lease_until`,`task_id`)`
- `KEY `idx_fact_fair_claim` (`execution_class`,`source_system`,`content_type`,`claim_available_time`,`task_id`)`
- `KEY `idx_fact_recovery_admission` (`execution_class`,`source_system`,`content_type`,`status`,`task_id`)`
- `KEY `idx_fact_write_cleanup` (`status`,`update_time`,`task_id`)`
- `KEY `idx_fact_write_source` (`source_system`,`content_type`,`status`,`create_time`)`
- `KEY `idx_fact_recovery_exact` (`source_system`,`content_type`,`status`,`max_attempt_count`,`last_error`(64), `create_time`,`task_id`,`outbox_id`,`attempt_count`)`
- `KEY `idx_fact_core_recovery` (`failure_code`,`source_system`,`content_type`,`status`,`max_attempt_count`, `outbox_id`,`attempt_count`)`
- `KEY `idx_fact_backlog_status` (`status`,`source_system`,`content_type`,`create_time`)`
- `KEY `idx_fact_write_storage` (`storage_id`,`payload_sha256`)`

### `trade_analytics.trade_analysis_recovery_admission_control`

- 证据类型：DDL。文件 `docs/sql/init/trade-analytics-init.sql`。实际数据库未核对。
- 表注释：跨实例事实恢复准入互斥控制
- 引擎：`ENGINE=InnoDB`
- 代码引用：本次未发现 @TableName。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `recovery_code` | VARCHAR(64) | NO |  | 恢复类别稳定编码 |
| `update_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |

主键、唯一键和二级索引：
- `PRIMARY KEY (`recovery_code`)`

### `trade_analytics.trade_analysis_fact_write_task_payload`

- 证据类型：DDL。文件 `docs/sql/init/trade-analytics-init.sql`。实际数据库未核对。
- 表注释：拆分后的通用事实写仓任务不可变JSON
- 引擎：`ENGINE=InnoDB`
- 代码引用：AnalysisFactWriteTaskPayloadDO。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `task_id` | BIGINT | NO |  | 对应事实写仓任务索引ID |
| `snapshot_json` | LONGTEXT | NO |  | 创建后不可修改的CanonicalChangeSet快照 |
| `create_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |

主键、唯一键和二级索引：
- `PRIMARY KEY (`task_id`)`

### `trade_analytics.trade_analysis_pickup_task`

- 证据类型：DDL。文件 `docs/sql/init/trade-analytics-init.sql`。实际数据库未核对。
- 表注释：Analytics统一第三方Pickup持久化任务；Ingress只提供幂等HTTP查询能力
- 引擎：`ENGINE=InnoDB`
- 代码引用：AnalysisPickupTaskDO。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `task_id` | BIGINT | NO |  | Analytics Pickup任务ID |
| `trigger_outbox_id` | BIGINT UNSIGNED | YES |  | 触发回调的Ingress分析outbox主键；历史迁移允许为空 |
| `schema_version` | INT UNSIGNED | NO | 1 | 触发消息合同版本；外卖平台新任务使用2 |
| `source_event_id` | BIGINT UNSIGNED | YES |  | Ingress来源事件主键 |
| `source_system` | TINYINT UNSIGNED | NO |  | 来源系统编码 |
| `pickup_type` | VARCHAR(32) | NO |  | 查询类型：ORDER_DETAIL等 |
| `source_object_id` | VARCHAR(128) | NO |  | 平台订单号等来源对象ID |
| `source_message_version` | BIGINT UNSIGNED | YES |  | 触发查询的来源消息版本 |
| `trigger_storage_id` | BIGINT | YES |  | 触发事件关联trade_storage.id |
| `trigger_payload_sha256` | BINARY(32) | YES |  | 触发事件原文SHA-256 |
| `ext_info` | JSON | YES |  | 平台类型化执行上下文；不保存凭据 |
| `subject_key` | CHAR(64) | YES |  | 共享任务主体SHA-256 |
| `status` | TINYINT UNSIGNED | NO | 0 | 0待执行；1执行中；2成功；3待重试；4已耗尽；5不可用；6身份待补；7身份冲突；8订单号无效；9已合并；10版本已占用 |
| `attempt_count` | INT UNSIGNED | NO | 0 | Ingress查询执行尝试次数；线程池拒绝不计数 |
| `max_attempt_count` | INT UNSIGNED | NO | 5 | 最大HTTP调用次数 |
| `next_execute_time` | DATETIME(3) | NO |  | 下次执行时间 |
| `lease_owner` | VARCHAR(128) | YES |  | 当前租约持有者 |
| `lease_until` | DATETIME(3) | YES |  | 当前租约截止时间 |
| `ingress_query_event_id` | BIGINT UNSIGNED | YES |  | Ingress事件化后的query event主键 |
| `query_storage_id` | BIGINT | YES |  | 查询结果关联trade_storage.id |
| `query_payload_sha256` | BINARY(32) | YES |  | 查询结果原文SHA-256 |
| `coalesced_to_task_id` | BIGINT | YES |  | 京东已合并任务所指向的同类型赢家任务ID |
| `last_error` | VARCHAR(1024) | YES |  | 最近失败摘要 |
| `success_time` | DATETIME(3) | YES |  | Ingress确认事件化时间 |
| `create_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |
| `update_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |

主键、唯一键和二级索引：
- `CONSTRAINT `ck_pickup_trigger_reference` CHECK ( (`pickup_type` NOT IN ('JD_GOODS_CATALOG', 'JD_GOODS_PACKAGE', 'FZY_SHOP_ACCOUNT', 'TAOBAO_SHOP_ACCOUNT', 'MEITUAN_SHOP_ACCOUNT', 'JD_SHOP_ACCOUNT', 'FZY_SHOP_OBJECT', 'TAOBAO_SHOP_OBJECT', 'MEITUAN_SHOP_OBJECT', 'FZY_SHOP_IDENTITY', 'TAOBAO_SHOP_IDENTITY', 'MEITUAN_SHOP_IDENTITY', 'JD_SHOP_IDENTITY', 'GOODS_ACCOUNT', 'GOODS_CATALOG') AND `source_event_id` IS NOT NULL AND `source_event_id` > 0 AND `source_message_version` IS NOT NULL AND `source_message_version` >= 0 AND `trigger_storage_id` IS NOT NULL AND `trigger_storage_id` > 0 AND `trigger_payload_sha256` IS NOT NULL) OR (`source_system` = 4 AND `pickup_type` IN ('JD_SHOP_DISCOVERY', 'JD_SHOP_DETAIL', 'JD_GOODS_CATALOG', 'JD_GOODS_PACKAGE') AND `source_event_id` IS NULL AND `source_message_version` IS NULL AND `trigger_storage_id` IS NULL AND `trigger_payload_sha256` IS NULL AND (`pickup_type` NOT IN ('JD_GOODS_CATALOG', 'JD_GOODS_PACKAGE') OR `trigger_outbox_id` IS NULL)) OR (((`source_system` = 1 AND `pickup_type` IN ('FZY_SHOP_ACCOUNT', 'FZY_SHOP_OBJECT', 'FZY_SHOP_IDENTITY')) OR (`source_system` = 2 AND `pickup_type` IN ('TAOBAO_SHOP_ACCOUNT', 'TAOBAO_SHOP_OBJECT', 'TAOBAO_SHOP_IDENTITY')) OR (`source_system` = 3 AND `pickup_type` IN ('MEITUAN_SHOP_ACCOUNT', 'MEITUAN_SHOP_OBJECT', 'MEITUAN_SHOP_IDENTITY')) OR (`source_system` = 4 AND `pickup_type` IN ('JD_SHOP_ACCOUNT', 'JD_SHOP_IDENTITY')) OR (`source_system` IN (2,3) AND `pickup_type` IN ('GOODS_ACCOUNT','GOODS_CATALOG')) OR (`source_system` IN (1,4) AND `pickup_type` = 'GOODS_ACCOUNT')) AND `source_event_id` IS NULL AND `source_message_version` IS NULL AND `trigger_storage_id` IS NULL AND `trigger_payload_sha256` IS NULL AND `trigger_outbox_id` IS NULL) )`
- `PRIMARY KEY (`task_id`)`
- `UNIQUE KEY `uk_jd_shop_subject` (`source_system`, `pickup_type`, (CASE WHEN `source_system` = 4 AND `pickup_type` IN ('JD_SHOP_DISCOVERY', 'JD_SHOP_DETAIL', 'JD_GOODS_CATALOG', 'JD_GOODS_PACKAGE') THEN `source_object_id` ELSE NULL END))`
- `UNIQUE KEY `uk_platform_shop_subject` (`source_system`, `pickup_type`, (CASE WHEN `pickup_type` IN ('FZY_SHOP_ACCOUNT', 'TAOBAO_SHOP_ACCOUNT', 'MEITUAN_SHOP_ACCOUNT', 'JD_SHOP_ACCOUNT', 'FZY_SHOP_OBJECT', 'TAOBAO_SHOP_OBJECT', 'MEITUAN_SHOP_OBJECT', 'FZY_SHOP_IDENTITY', 'TAOBAO_SHOP_IDENTITY', 'MEITUAN_SHOP_IDENTITY', 'JD_SHOP_IDENTITY') THEN `source_object_id` ELSE NULL END))`
- `UNIQUE KEY `uk_refund_food_subject` (`source_system`, `pickup_type`, (CASE WHEN `source_system` = 2 AND `pickup_type` = 'REFUND_FOOD_SNAPSHOT' THEN `source_object_id` ELSE NULL END))`
- `UNIQUE KEY `uk_goods_subject` (`source_system`, `pickup_type`, (CASE WHEN (`source_system` IN (2,3) AND `pickup_type` IN ('GOODS_ACCOUNT', 'GOODS_CATALOG')) OR (`source_system` IN (1,4) AND `pickup_type` = 'GOODS_ACCOUNT') THEN `source_object_id` ELSE NULL END))`
- `UNIQUE KEY `uk_pickup_subject` (`subject_key`)`
- `UNIQUE KEY `uk_pickup_source_event` (`source_system`, `pickup_type`, `source_event_id`)`
- `KEY `idx_pickup_claim` (`source_system`, `pickup_type`, `status`, `next_execute_time`, `lease_until`, `task_id`)`
- `KEY `idx_pickup_cleanup` (`status`, `update_time`, `task_id`)`
- `KEY `idx_pickup_storage` (`trigger_storage_id`, `trigger_payload_sha256`)`
- `KEY `idx_pickup_created` (`create_time`, `task_id`)`
- `KEY `idx_pickup_source_created` (`source_system`, `create_time`, `task_id`)`
- `KEY `idx_pickup_source_object_created` (`source_system`, `source_object_id`, `create_time`, `task_id`)`
- `KEY `idx_jd_due` (`source_system`, `status`, `next_execute_time`, `source_object_id`, `task_id`)`
- `KEY `idx_jd_order_active` (`source_system`, `source_object_id`, `task_id`, `status`)`
- `KEY `idx_pickup_query_event` (`source_system`, `ingress_query_event_id`)`
- `KEY `idx_pickup_coalesced_to` (`coalesced_to_task_id`)`

### `trade_analytics.trade_analysis_query_template_group`

- 证据类型：DDL。文件 `docs/sql/init/trade-analytics-init.sql`。实际数据库未核对。
- 表注释：查询模板分组
- 引擎：`ENGINE=InnoDB`
- 代码引用：QueryTemplateGroupDO。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `id` | BIGINT UNSIGNED | NO |  | 分组ID |
| `brand_id` | BIGINT UNSIGNED | NO |  | 分组所属品牌ID |
| `parent_id` | BIGINT UNSIGNED | NO | 0 | 父分组ID，0表示根目录 |
| `group_name` | VARCHAR(100) | NO |  | 分组名称 |

主键、唯一键和二级索引：
- `PRIMARY KEY (`id`)`
- `UNIQUE KEY `uk_template_group_name` (`brand_id`, `parent_id`, `group_name`)`

### `trade_analytics.trade_analysis_query_template`

- 证据类型：DDL。文件 `docs/sql/init/trade-analytics-init.sql`。实际数据库未核对。
- 表注释：经过审核的MyBatis Doris只读查询模板
- 引擎：`ENGINE=InnoDB`
- 代码引用：QueryTemplateDO。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `id` | BIGINT UNSIGNED | NO |  | 查询模板ID |
| `brand_id` | BIGINT UNSIGNED | NO |  | 模板所属品牌ID |
| `system_code` | VARCHAR(64) | NO |  | 调用系统编码 |
| `template_code` | VARCHAR(100) | NO |  | 模板编码；修改后形成新业务版本 |
| `template_name` | VARCHAR(200) | NO |  | 模板名称 |
| `sql_template` | LONGTEXT | NO |  | MyBatis script动态SELECT模板 |
| `param_schema` | JSON | YES |  | 模板公开参数、类型、默认值与长度限制 |
| `timeout_ms` | INT UNSIGNED | NO | 1000 | 单次查询超时毫秒数 |
| `max_rows` | INT UNSIGNED | NO | 500 | 最大允许返回行数 |
| `enabled` | TINYINT UNSIGNED | NO | 0 | 0未发布；1已发布 |
| `template_ver` | INT UNSIGNED | NO | 1 | 模板业务版本；仅模板内容形成新版本时递增 |
| `safety_status` | VARCHAR(16) | NO | 'UNVERIFIED' | UNVERIFIED/PASSED/FAILED |
| `safety_verified_template_ver` | INT UNSIGNED | YES |  | 通过安全执行测试的模板业务版本 |
| `safety_verified_time` | DATETIME(3) | YES |  | 最近安全测试通过时间 |
| `safety_failure_reason` | VARCHAR(500) | YES |  | 最近安全测试失败原因 |
| `version` | INT UNSIGNED | NO | 1 | 乐观锁版本 |
| `description` | VARCHAR(500) | YES |  | 模板用途与数据口径 |
| `created_by` | VARCHAR(64) | YES |  | 创建人 |
| `review_status` | VARCHAR(20) | NO | 'NOT_SUBMITTED' | NOT_SUBMITTED/APPROVED；待审和驳回状态见暂存表 |
| `submitted_by` | VARCHAR(64) | YES |  | 最近一次已通过版本的提交人 |
| `submitted_time` | DATETIME(3) | YES |  | 最近一次已通过版本的提审时间 |
| `reviewed_by` | VARCHAR(64) | YES |  | 最近一次已通过版本的审核人 |
| `reviewed_time` | DATETIME(3) | YES |  | 最近一次已通过版本的审核时间 |
| `review_comment` | VARCHAR(500) | YES |  | 最近一次已通过版本的审核意见 |
| `create_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |
| `update_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |
| `del_flag` | TINYINT UNSIGNED | NO | 0 | 0有效；1伪删除 |
| `group_id` | BIGINT UNSIGNED | NO | 0 | 分组ID，0表示未分组 |

主键、唯一键和二级索引：
- `PRIMARY KEY (`id`)`
- `UNIQUE KEY `uk_template_scope_code` (`brand_id`, `system_code`, `template_code`)`
- `KEY `idx_template_status_time` (`enabled`, `update_time`, `id`)`
- `KEY `idx_template_scope` (`brand_id`, `system_code`, `enabled`, `update_time`, `id`)`

### `trade_analytics.trade_analysis_query_template_stash`

- 证据类型：DDL。文件 `docs/sql/init/trade-analytics-init.sql`。实际数据库未核对。
- 表注释：Doris查询模板的待审核暂存版本
- 引擎：`ENGINE=InnoDB`
- 代码引用：QueryTemplateStashDO。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `id` | BIGINT UNSIGNED | NO |  | 模板暂存ID |
| `template_id` | BIGINT UNSIGNED | YES |  | 主模板ID；首次发布前为空 |
| `brand_id` | BIGINT UNSIGNED | NO |  | 模板所属品牌ID |
| `system_code` | VARCHAR(64) | NO |  | 调用系统编码 |
| `created_by` | VARCHAR(64) | YES |  | 草稿创建人 |
| `base_template_ver` | INT UNSIGNED | NO |  | 所基于的正式版本；首次发布为0 |
| `template_ver` | INT UNSIGNED | NO |  | 审核通过后生效的目标模板版本 |
| `template_code` | VARCHAR(100) | NO |  | 审核通过后生效的模板编码 |
| `template_name` | VARCHAR(200) | NO |  | 模板名称 |
| `sql_template` | LONGTEXT | NO |  | MyBatis script动态SELECT模板 |
| `param_schema` | JSON | YES |  | 模板公开参数、类型、默认值与长度限制 |
| `timeout_ms` | INT UNSIGNED | NO |  | 单次查询超时毫秒数 |
| `max_rows` | INT UNSIGNED | NO |  | 最大允许返回行数 |
| `description` | VARCHAR(500) | YES |  | 模板用途与数据口径 |
| `review_status` | VARCHAR(20) | NO | 'DRAFT' | DRAFT/PENDING_REVIEW/APPROVED/REJECTED |
| `safety_status` | VARCHAR(16) | NO | 'UNVERIFIED' | UNVERIFIED/PASSED/FAILED |
| `safety_verified_template_ver` | INT UNSIGNED | YES |  | 通过安全测试的目标模板版本 |
| `safety_verified_time` | DATETIME(3) | YES |  | 最近安全测试通过时间 |
| `safety_failure_reason` | VARCHAR(500) | YES |  | 最近安全测试失败原因 |
| `submitted_by` | VARCHAR(64) | YES |  | 提交审核人 |
| `submitted_time` | DATETIME(3) | YES |  | 提交审核时间 |
| `reviewed_by` | VARCHAR(64) | YES |  | 最近审核人 |
| `reviewed_time` | DATETIME(3) | YES |  | 最近审核时间 |
| `review_comment` | VARCHAR(500) | YES |  | 审核意见 |
| `version` | INT UNSIGNED | NO | 1 | 行级乐观锁版本 |
| `create_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |
| `update_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |
| `group_id` | BIGINT UNSIGNED | NO | 0 | 未发布模板的分组ID，0表示未分组 |

主键、唯一键和二级索引：
- `PRIMARY KEY (`id`)`
- `UNIQUE KEY `uk_query_template_stash_template` (`template_id`)`
- `KEY `idx_query_template_stash_review` (`review_status`, `submitted_time`, `id`)`
- `KEY `idx_query_template_stash_scope` (`brand_id`, `system_code`, `template_code`, `update_time`, `id`)`

### `trade_analytics.trade_analysis_quality_rule`

- 证据类型：DDL。文件 `docs/sql/init/trade-analytics-init.sql`。实际数据库未核对。
- 表注释：旁路数据质检规则与循环游标
- 引擎：`ENGINE=InnoDB`
- 代码引用：AnalysisQualityRuleDO。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `id` | BIGINT UNSIGNED | NO |  |  |
| `rule_code` | VARCHAR(100) | NO |  | 稳定规则编码 |
| `rule_name` | VARCHAR(200) | NO |  |  |
| `platform_code` | VARCHAR(16) | YES |  | 空表示跨平台规则 |
| `severity` | VARCHAR(16) | NO | 'WARNING' |  |
| `detection_sql` | LONGTEXT | NO |  | 只读候选发现SQL模板 |
| `recheck_sql` | LONGTEXT | NO |  | 按entityKeys有界核验SQL模板 |
| `maturity_delay_minutes` | INT UNSIGNED | NO | 30 |  |
| `bootstrap_lookback_minutes` | INT UNSIGNED | NO | 10080 |  |
| `lookback_minutes` | INT UNSIGNED | NO | 60 |  |
| `schedule_interval_minutes` | INT UNSIGNED | NO | 5 |  |
| `timeout_ms` | INT UNSIGNED | NO | 3000 |  |
| `max_rows` | INT UNSIGNED | NO | 1000 |  |
| `enabled` | TINYINT UNSIGNED | NO | 0 |  |
| `rule_ver` | INT UNSIGNED | NO | 1 |  |
| `review_status` | VARCHAR(20) | NO | 'NOT_SUBMITTED' |  |
| `created_by` | VARCHAR(64) | YES |  |  |
| `reviewed_by` | VARCHAR(64) | YES |  |  |
| `reviewed_time` | DATETIME(3) | YES |  |  |
| `description` | VARCHAR(500) | YES |  |  |
| `remediation_hint` | VARCHAR(500) | YES |  |  |
| `scan_cycle` | BIGINT UNSIGNED | NO | 0 | 自动扫描完整回绕轮次 |
| `scan_partition_date` | DATE | YES |  | 自动扫描当前业务分区日 |
| `scan_page_after` | VARCHAR(256) | NO | '' | 当前分区稳定分页断点 |
| `lease_owner` | VARCHAR(128) | YES |  |  |
| `lease_token` | VARCHAR(64) | YES |  | 每次规则租约唯一 fencing token |
| `lease_until` | DATETIME(3) | YES |  |  |
| `last_run_time` | DATETIME(3) | YES |  |  |
| `next_run_time` | DATETIME(3) | YES |  | 调度时间，不承担租约语义 |
| `create_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |
| `update_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |
| `del_flag` | TINYINT UNSIGNED | NO | 0 |  |

主键、唯一键和二级索引：
- `PRIMARY KEY (`id`)`
- `UNIQUE KEY `uk_quality_rule_code` (`rule_code`)`
- `KEY `idx_quality_rule_due` (`enabled`,`review_status`,`next_run_time`,`lease_until`,`id`)`

### `trade_analytics.trade_analysis_quality_candidate`

- 证据类型：DDL。文件 `docs/sql/init/trade-analytics-init.sql`。实际数据库未核对。
- 表注释：旁路质检持久候选队列
- 引擎：`ENGINE=InnoDB`
- 代码引用：AnalysisQualityCandidateDO。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `id` | BIGINT UNSIGNED | NO |  |  |
| `rule_id` | BIGINT UNSIGNED | NO |  |  |
| `entity_type` | VARCHAR(32) | NO |  |  |
| `entity_key` | VARCHAR(256) | NO |  | 稳定业务实体键；富智云仅为platform_order_id |
| `partition_anchor_date` | DATE | NO |  | 最近发现该实体的业务分区日 |
| `first_fact_date` | DATE | NO |  |  |
| `last_fact_date` | DATE | NO |  |  |
| `pending_rule_ver` | INT UNSIGNED | NO |  | 最新待核验规则版本 |
| `pending_cycle` | BIGINT UNSIGNED | NO |  | 最新待核验发现轮次 |
| `processing_rule_ver` | INT UNSIGNED | YES |  | 当前租约冻结的规则版本 |
| `processing_cycle` | BIGINT UNSIGNED | YES |  | 当前租约冻结的发现轮次 |
| `processed_rule_ver` | INT UNSIGNED | YES |  | 最后完成的规则版本 |
| `processed_cycle` | BIGINT UNSIGNED | YES |  | 最后完成的发现轮次 |
| `status` | VARCHAR(16) | NO | 'PENDING' | PENDING/PROCESSING/RETRYING/COMPLETED |
| `attempt_count` | INT UNSIGNED | NO | 0 |  |
| `next_execute_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |
| `lease_owner` | VARCHAR(128) | YES |  |  |
| `lease_token` | VARCHAR(64) | YES |  | 与规则租约相同的fencing token |
| `lease_until` | DATETIME(3) | YES |  |  |
| `last_error` | VARCHAR(1024) | YES |  |  |
| `completed_time` | DATETIME(3) | YES |  |  |
| `create_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |
| `update_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |

主键、唯一键和二级索引：
- `PRIMARY KEY (`id`)`
- `UNIQUE KEY `uk_quality_candidate_entity` (`rule_id`,`entity_type`,`entity_key`)`
- `KEY `idx_quality_candidate_claim` (`rule_id`,`pending_rule_ver`,`status`,`next_execute_time`,`lease_until`,`id`)`
- `KEY `idx_quality_candidate_cycle` (`rule_id`,`pending_rule_ver`,`pending_cycle`,`id`)`
- `KEY `idx_quality_candidate_cleanup` (`status`,`last_fact_date`,`id`)`

### `trade_analytics.trade_analysis_quality_run`

- 证据类型：DDL。文件 `docs/sql/init/trade-analytics-init.sql`。实际数据库未核对。
- 表注释：旁路数据质检运行记录
- 引擎：`ENGINE=InnoDB`
- 代码引用：AnalysisQualityRunDO。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `id` | BIGINT UNSIGNED | NO |  |  |
| `rule_id` | BIGINT UNSIGNED | NO |  |  |
| `rule_code` | VARCHAR(100) | NO |  |  |
| `rule_ver` | INT UNSIGNED | NO |  |  |
| `run_mode` | VARCHAR(16) | NO |  | AUTOMATIC/MANUAL |
| `requested_by` | VARCHAR(64) | YES |  | 手工运行操作人；自动运行为空 |
| `lease_token` | VARCHAR(64) | NO |  | 规则运行fencing token |
| `window_start` | DATETIME(3) | NO |  |  |
| `window_end` | DATETIME(3) | NO |  |  |
| `manual_mature_cutoff` | DATETIME(3) | YES |  | 手工任务冻结的成熟截止点；自动运行为空 |
| `manual_page_after` | VARCHAR(256) | NO | '' | 手工任务已提交的独立续页键；不影响自动水位 |
| `manual_processed_count` | BIGINT UNSIGNED | NO | 0 | 手工任务累计完成复核的实体数 |
| `status` | VARCHAR(16) | NO |  |  |
| `anomaly_count` | INT UNSIGNED | NO | 0 |  |
| `doris_cost_ms` | BIGINT UNSIGNED | YES |  |  |
| `error_message` | VARCHAR(1024) | YES |  |  |
| `started_time` | DATETIME(3) | NO |  |  |
| `completed_time` | DATETIME(3) | YES |  |  |

主键、唯一键和二级索引：
- `PRIMARY KEY (`id`)`
- `KEY `idx_quality_run_rule` (`rule_id`,`started_time`,`id`)`
- `KEY `idx_quality_run_cleanup` (`status`,`completed_time`,`id`)`

### `trade_analytics.trade_analysis_quality_issue`

- 证据类型：DDL。文件 `docs/sql/init/trade-analytics-init.sql`。实际数据库未核对。
- 表注释：旁路数据质检异常生命周期
- 引擎：`ENGINE=InnoDB`
- 代码引用：AnalysisQualityIssueDO。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `id` | BIGINT UNSIGNED | NO |  |  |
| `rule_id` | BIGINT UNSIGNED | NO |  |  |
| `rule_code` | VARCHAR(100) | NO |  |  |
| `entity_type` | VARCHAR(32) | NO |  |  |
| `entity_key` | VARCHAR(256) | NO |  | 稳定业务实体键 |
| `issue_code` | VARCHAR(64) | NO |  | 同一实体上的稳定异常类型 |
| `partition_anchor_date` | DATE | YES |  | 最近检测使用的业务分区锚点 |
| `status` | VARCHAR(16) | NO | 'OPEN' |  |
| `actual_value` | VARCHAR(500) | YES |  |  |
| `expected_value` | VARCHAR(500) | YES |  |  |
| `difference_value` | VARCHAR(500) | YES |  |  |
| `detail_json` | JSON | YES |  |  |
| `first_detected_time` | DATETIME(3) | NO |  |  |
| `last_detected_time` | DATETIME(3) | NO |  |  |
| `resolved_time` | DATETIME(3) | YES |  |  |
| `last_run_id` | BIGINT UNSIGNED | NO |  |  |
| `alert_status` | VARCHAR(16) | NO | 'PENDING' | PENDING/PROCESSING/RETRYING/SENT |
| `alert_attempt_count` | INT UNSIGNED | NO | 0 |  |
| `next_alert_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |
| `alert_lease_token` | VARCHAR(64) | YES |  |  |
| `alert_lease_until` | DATETIME(3) | YES |  |  |
| `alerted_time` | DATETIME(3) | YES |  |  |
| `alert_error` | VARCHAR(1024) | YES |  |  |
| `create_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |
| `update_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |

主键、唯一键和二级索引：
- `PRIMARY KEY (`id`)`
- `UNIQUE KEY `uk_quality_issue_identity` (`rule_id`,`entity_type`,`entity_key`,`issue_code`)`
- `KEY `idx_quality_issue_status` (`status`,`last_detected_time`,`id`)`
- `KEY `idx_quality_issue_alert` (`status`,`alert_status`,`next_alert_time`,`alert_lease_until`,`id`)`
- `KEY `idx_quality_issue_cleanup` (`status`,`resolved_time`,`id`)`

### `trade_analytics.dim_trade_shop_id_mapping`

- 证据类型：DDL。文件 `docs/sql/init/trade-analytics-init.sql`。实际数据库未核对。
- 引擎：`ENGINE=InnoDB`
- 代码引用：TradeShopIdMappingDO。ShopDimensionCdcJob。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `internal_shop_id` | BIGINT | NO |  |  |
| `platform_code` | VARCHAR(16) | NO |  |  |
| `platform_shop_id` | VARCHAR(128) | NO |  |  |
| `platform_shop_name` | VARCHAR(256) | YES |  |  |
| `brand_id` | BIGINT | NO |  |  |
| `effective_start_time` | DATETIME(3) | NO |  |  |
| `effective_end_time` | DATETIME(3) | YES |  |  |
| `mapping_status` | VARCHAR(16) | YES |  |  |
| `mapping_version` | BIGINT | YES |  |  |
| `updated_time` | DATETIME(3) | YES |  |  |

主键、唯一键和二级索引：
- `PRIMARY KEY (`internal_shop_id`)`
- `KEY `idx_shop_subject` (`brand_id`, `platform_code`, `platform_shop_id`, `mapping_status`, `effective_start_time`, `effective_end_time`)`

### `trade_analytics.dim_fuiou_shop_info`

- 证据类型：DDL。文件 `docs/sql/init/trade-analytics-init.sql`。实际数据库未核对。
- 引擎：`ENGINE=InnoDB`
- 代码引用：ShopDO。ShopDimensionCdcJob。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `shop_id` | BIGINT | NO |  |  |
| `shop_code` | VARCHAR(64) | NO | '' |  |
| `shop_name` | VARCHAR(128) | NO | '' |  |
| `shop_type` | VARCHAR(8) | NO | '' |  |
| `open_state` | VARCHAR(64) | YES |  |  |
| `mchnt_cd` | VARCHAR(32) | NO | '' |  |
| `term_user_cd` | VARCHAR(32) | NO | '' |  |
| `phone` | VARCHAR(32) | NO | '' |  |
| `address` | VARCHAR(256) | NO | '' |  |
| `prov_cd` | VARCHAR(16) | NO | '' |  |
| `region_cd` | VARCHAR(16) | NO | '' |  |
| `county_cd` | VARCHAR(16) | NO | '' |  |
| `city_name` | VARCHAR(64) | NO | '' |  |
| `gitu` | VARCHAR(32) | NO | '' |  |
| `titu` | VARCHAR(32) | NO | '' |  |
| `eat_in_open_time` | VARCHAR(64) | NO | '' |  |
| `take_out_open_time` | VARCHAR(64) | NO | '' |  |
| `active_tm` | DATETIME(3) | YES |  |  |
| `create_tm` | DATETIME(3) | YES |  |  |
| `update_tm` | DATETIME(3) | YES |  |  |
| `create_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |
| `update_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP(3) |  |
| `del_flag` | TINYINT | NO | '0' |  |

主键、唯一键和二级索引：
- `PRIMARY KEY (`shop_id`)`
- `KEY `idx_shop_merchant` (`mchnt_cd`, `del_flag`, `shop_id`)`

### `trade_analytics.dim_taobao_shop_info`

- 证据类型：DDL。文件 `docs/sql/init/trade-analytics-init.sql`。实际数据库未核对。
- 引擎：`ENGINE=InnoDB`
- 代码引用：TaobaoShopInfoDO。ShopDimensionCdcJob。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `brand_id` | BIGINT | NO |  |  |
| `shop_id` | VARCHAR(128) | NO |  |  |
| `app_id` | VARCHAR(128) | YES |  |  |
| `shop_name` | VARCHAR(256) | YES |  |  |
| `open_id` | VARCHAR(256) | YES |  |  |
| `address` | VARCHAR(1024) | YES |  |  |
| `province_name` | VARCHAR(128) | YES |  |  |
| `city_name` | VARCHAR(128) | YES |  |  |
| `district_name` | VARCHAR(128) | YES |  |  |
| `latitude` | DOUBLE | YES |  |  |
| `longitude` | DOUBLE | YES |  |  |
| `is_open` | INT | YES |  |  |
| `phones_json` | VARCHAR(1024) | YES |  |  |
| `province_code` | VARCHAR(16) | YES |  |  |
| `city_code` | VARCHAR(16) | YES |  |  |
| `district_code` | VARCHAR(16) | YES |  |  |
| `region_resolution_status` | VARCHAR(16) | YES |  |  |
| `region_resolution_method` | VARCHAR(32) | YES |  |  |
| `updated_time` | DATETIME(3) | YES |  |  |

主键、唯一键和二级索引：
- `PRIMARY KEY (`brand_id`, `shop_id`)`
- `KEY `idx_shop_account` (`brand_id`, `app_id`, `shop_id`)`

### `trade_analytics.dim_meituan_shop_info`

- 证据类型：DDL。文件 `docs/sql/init/trade-analytics-init.sql`。实际数据库未核对。
- 引擎：`ENGINE=InnoDB`
- 代码引用：MeituanShopInfoDO。ShopDimensionCdcJob。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `brand_id` | BIGINT | NO |  |  |
| `app_poi_code` | VARCHAR(128) | NO |  |  |
| `wm_poi_id` | VARCHAR(128) | YES |  | 美团平台门店ID |
| `app_id` | VARCHAR(128) | YES |  |  |
| `shop_name` | VARCHAR(256) | YES |  |  |
| `address` | VARCHAR(1024) | YES |  |  |
| `phone` | VARCHAR(64) | YES |  |  |
| `latitude` | DOUBLE | YES |  |  |
| `longitude` | DOUBLE | YES |  |  |
| `is_online` | INT | YES |  |  |
| `source_utime` | BIGINT | YES |  |  |
| `province_code` | VARCHAR(16) | YES |  |  |
| `city_code` | VARCHAR(16) | YES |  |  |
| `district_code` | VARCHAR(16) | YES |  |  |
| `region_resolution_status` | VARCHAR(16) | YES |  |  |
| `region_resolution_method` | VARCHAR(32) | YES |  |  |
| `updated_time` | DATETIME(3) | YES |  |  |

主键、唯一键和二级索引：
- `PRIMARY KEY (`brand_id`, `app_poi_code`)`
- `KEY `idx_shop_account` (`brand_id`, `app_id`, `app_poi_code`)`

### `trade_analytics.dim_jd_shop_info`

- 证据类型：DDL。文件 `docs/sql/init/trade-analytics-init.sql`。实际数据库未核对。
- 引擎：`ENGINE=InnoDB`
- 代码引用：JdShopInfoDO。ShopDimensionCdcJob。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `brand_id` | BIGINT | NO |  |  |
| `platform_shop_id` | VARCHAR(128) | NO |  |  |
| `app_key` | VARCHAR(128) | YES |  |  |
| `vender_id` | VARCHAR(128) | YES |  |  |
| `vender_name` | VARCHAR(256) | YES |  |  |
| `shop_name` | VARCHAR(256) | YES |  |  |
| `latitude` | DECIMAL(18, 8) | YES |  |  |
| `longitude` | DECIMAL(18, 8) | YES |  |  |
| `province_code` | VARCHAR(32) | YES |  |  |
| `province_name` | VARCHAR(128) | YES |  |  |
| `city_code` | VARCHAR(32) | YES |  |  |
| `city_name` | VARCHAR(128) | YES |  |  |
| `county_code` | VARCHAR(32) | YES |  |  |
| `county_name` | VARCHAR(128) | YES |  |  |
| `station_address` | VARCHAR(1024) | YES |  |  |
| `phone` | VARCHAR(64) | YES |  |  |
| `mobile` | VARCHAR(64) | YES |  |  |
| `enabled_status` | INT | YES |  |  |
| `close_status` | INT | YES |  |  |
| `identity_status` | VARCHAR(16) | YES |  |  |
| `region_resolution_status` | VARCHAR(16) | YES |  |  |
| `region_resolution_method` | VARCHAR(32) | YES |  |  |
| `source_updated_time` | DATETIME(3) | YES |  |  |
| `updated_time` | DATETIME(3) | YES |  |  |

主键、唯一键和二级索引：
- `PRIMARY KEY (`brand_id`, `platform_shop_id`)`
- `KEY `idx_shop_account` (`brand_id`, `app_key`, `identity_status`, `platform_shop_id`)`

### `trade_analytics.dim_trade_shop_group`

- 证据类型：DDL。文件 `docs/sql/init/trade-analytics-init.sql`。实际数据库未核对。
- 引擎：`ENGINE=InnoDB`
- 代码引用：TradeShopGroupDO。ShopDimensionCdcJob。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `brand_id` | BIGINT | NO |  |  |
| `id` | VARCHAR(64) | NO |  |  |
| `rule_version` | BIGINT | NO |  |  |
| `row_kind` | VARCHAR(8) | NO |  |  |
| `group_key` | VARCHAR(512) | NO |  |  |
| `parent_id` | VARCHAR(64) | YES |  |  |
| `member_ordinal` | INT | YES |  |  |
| `platform_raw` | VARCHAR(128) | YES |  |  |
| `platform_code` | VARCHAR(16) | YES |  |  |
| `platform_shop_id` | VARCHAR(128) | YES |  |  |
| `is_deleted` | TINYINT | NO | '0' |  |
| `platform_source_app_id` | VARCHAR(128) | YES |  |  |
| `extra_fields` | JSON | YES |  |  |
| `rule_status` | VARCHAR(32) | NO |  |  |
| `conflict_diagnostics` | JSON | YES |  |  |
| `observed_time` | DATETIME(3) | NO |  |  |
| `origin` | VARCHAR(16) | NO |  |  |
| `internal_shop_id` | BIGINT | YES |  |  |
| `shop_name` | VARCHAR(256) | YES |  |  |
| `open_state` | VARCHAR(64) | YES |  |  |

主键、唯一键和二级索引：
- `PRIMARY KEY (`brand_id`, `id`)`
- `KEY `idx_shop_root` (`brand_id`, `row_kind`, `group_key`, `is_deleted`)`
- `KEY `idx_shop_parent` (`brand_id`, `parent_id`, `row_kind`, `is_deleted`)`
- `KEY `idx_shop_member` (`brand_id`, `platform_raw`, `platform_shop_id`, `is_deleted`)`
- `KEY `idx_shop_rule_version` (`brand_id`, `rule_version`)`

### `trade_analytics.dim_goods_product_v1`

- 证据类型：DDL。文件 `docs/sql/init/trade-analytics-init.sql`。实际数据库未核对。
- 引擎：`ENGINE=InnoDB`
- 代码引用：ProductDimDO。GoodsDimensionCdcJob。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `internal_product_id` | BIGINT | NO |  |  |
| `brand_id` | BIGINT | YES |  |  |
| `internal_category_id` | BIGINT | YES |  |  |
| `product_name` | VARCHAR(512) | NO |  |  |
| `product_type` | VARCHAR(32) | YES |  |  |
| `stock_unit` | VARCHAR(32) | YES |  |  |
| `product_status` | VARCHAR(32) | YES |  |  |
| `is_deleted` | BOOLEAN | NO | '0' |  |
| `meta_process_time` | DATETIME(3) | NO |  |  |
| `meta_sink_sequence` | BIGINT | NO |  |  |

主键、唯一键和二级索引：
- `PRIMARY KEY (`internal_product_id`)`
- `KEY `idx_goods_product_brand` (`brand_id`,`is_deleted`)`
- `KEY `idx_goods_product_category` (`internal_category_id`)`

### `trade_analytics.dim_goods_product_source_v1`

- 证据类型：DDL。文件 `docs/sql/init/trade-analytics-init.sql`。实际数据库未核对。
- 引擎：`ENGINE=InnoDB`
- 代码引用：ProductSourceMapDO。GoodsDimensionCdcJob。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `source_system` | VARCHAR(32) | NO |  |  |
| `source_merchant_code` | VARCHAR(64) | NO |  |  |
| `source_shop_id` | VARCHAR(128) | NO |  |  |
| `source_product_id` | VARCHAR(128) | NO |  |  |
| `internal_product_id` | BIGINT | YES |  |  |
| `source_product_name` | VARCHAR(512) | YES |  |  |
| `mapping_type` | VARCHAR(32) | NO |  |  |
| `is_deleted` | BOOLEAN | NO | '0' |  |
| `meta_process_time` | DATETIME(3) | NO |  |  |
| `meta_sink_sequence` | BIGINT | NO |  |  |

主键、唯一键和二级索引：
- `PRIMARY KEY (`source_system`,`source_merchant_code`,`source_shop_id`,`source_product_id`)`
- `KEY `idx_goods_source_internal` (`internal_product_id`,`source_system`,`source_merchant_code`,`source_shop_id`)`
- `KEY `idx_goods_source_deleted` (`source_system`,`source_merchant_code`,`is_deleted`)`
- `KEY `idx_goods_source_snapshot` (`source_product_id`)`

### `trade_analytics.dim_goods_category_v1`

- 证据类型：DDL。文件 `docs/sql/init/trade-analytics-init.sql`。实际数据库未核对。
- 引擎：`ENGINE=InnoDB`
- 代码引用：CategoryDimDO。GoodsDimensionCdcJob。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `internal_category_id` | BIGINT | NO |  |  |
| `brand_id` | BIGINT | YES |  |  |
| `category_name` | VARCHAR(256) | NO |  |  |
| `category_type` | VARCHAR(32) | NO | 'NORMAL' |  |
| `sort_order` | INT | YES |  |  |
| `is_deleted` | BOOLEAN | NO | '0' |  |
| `meta_process_time` | DATETIME(3) | NO |  |  |
| `meta_sink_sequence` | BIGINT | NO |  |  |

主键、唯一键和二级索引：
- `PRIMARY KEY (`internal_category_id`)`
- `KEY `idx_goods_category_brand_type` (`brand_id`,`category_type`,`is_deleted`)`

### `trade_analytics.dim_goods_platform_catalog_v1`

- 证据类型：DDL。文件 `docs/sql/init/trade-analytics-init.sql`。实际数据库未核对。
- 引擎：`ENGINE=InnoDB`
- 代码引用：GoodsPlatformCatalogDO。GoodsDimensionCdcJob。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `brand_id` | BIGINT | NO |  |  |
| `source_system` | VARCHAR(32) | NO |  |  |
| `source_merchant_code` | VARCHAR(64) | NO |  |  |
| `source_shop_id` | VARCHAR(128) | NO |  |  |
| `id_namespace` | VARCHAR(32) | NO |  |  |
| `source_id` | VARCHAR(128) | NO |  |  |
| `parent_id_namespace` | VARCHAR(32) | YES |  |  |
| `parent_source_id` | VARCHAR(128) | YES |  |  |
| `product_name` | VARCHAR(512) | YES |  |  |
| `spec_name` | VARCHAR(512) | YES |  |  |
| `attributes_json` | LONGTEXT | YES |  |  |
| `is_deleted` | BOOLEAN | NO | '0' |  |
| `catalog_round` | VARCHAR(128) | NO |  |  |
| `meta_process_time` | DATETIME(3) | NO |  |  |
| `meta_sink_sequence` | BIGINT | NO |  |  |

主键、唯一键和二级索引：
- `PRIMARY KEY (`brand_id`,`source_system`,`source_merchant_code`,`source_shop_id`,`id_namespace`,`source_id`)`
- `KEY `idx_goods_catalog_parent` (`brand_id`,`source_system`,`source_merchant_code`,`source_shop_id`,`parent_id_namespace`,`parent_source_id`)`
- `KEY `idx_goods_catalog_snapshot` (`source_id`)`

### `trade_analytics.dim_goods_product_package_rel_v1`

- 证据类型：DDL。文件 `docs/sql/init/trade-analytics-init.sql`。实际数据库未核对。
- 引擎：`ENGINE=InnoDB`
- 代码引用：ProductPackageRelDO。GoodsDimensionCdcJob。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `source_system` | VARCHAR(32) | NO |  |  |
| `source_merchant_code` | VARCHAR(64) | NO |  |  |
| `source_shop_id` | VARCHAR(128) | NO |  |  |
| `source_package_product_id` | VARCHAR(128) | NO |  |  |
| `option_index` | INT | NO |  |  |
| `goods_index` | INT | NO |  |  |
| `source_component_product_id` | VARCHAR(128) | NO |  |  |
| `package_internal_product_id` | BIGINT | YES |  |  |
| `component_internal_product_id` | BIGINT | YES |  |  |
| `component_product_name` | VARCHAR(512) | YES |  |  |
| `combine_desc` | VARCHAR(256) | YES |  |  |
| `package_type` | VARCHAR(16) | YES |  |  |
| `goods_number` | DECIMAL(18,3) | YES |  |  |
| `min_number` | INT | YES |  |  |
| `option_number` | INT | YES |  |  |
| `is_default_selected` | BOOLEAN | NO | '0' |  |
| `up_price_amount` | BIGINT | NO | '0' |  |
| `is_deleted` | BOOLEAN | NO | '0' |  |
| `meta_process_time` | DATETIME(3) | NO |  |  |
| `meta_sink_sequence` | BIGINT | NO |  |  |

主键、唯一键和二级索引：
- `PRIMARY KEY (`source_system`,`source_merchant_code`,`source_shop_id`,`source_package_product_id`,`option_index`,`goods_index`,`source_component_product_id`)`
- `KEY `idx_goods_package_deleted` (`source_system`,`source_merchant_code`,`is_deleted`)`
- `KEY `idx_goods_package_snapshot` (`source_package_product_id`)`

### `trade_analytics.dim_trade_goods_group`

- 证据类型：DDL。文件 `docs/sql/init/trade-analytics-init.sql`。实际数据库未核对。
- 引擎：`ENGINE=InnoDB`
- 代码引用：TradeGoodsGroupDO。GoodsDimensionCdcJob。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `tenant_id` | BIGINT | NO |  |  |
| `id` | VARCHAR(64) | NO |  |  |
| `rule_version` | BIGINT | NO |  |  |
| `row_kind` | VARCHAR(8) | NO |  |  |
| `group_key` | VARCHAR(512) | NO |  |  |
| `parent_id` | VARCHAR(64) | YES |  |  |
| `member_ordinal` | INT | YES |  |  |
| `platform_raw` | VARCHAR(128) | YES |  |  |
| `platform_code` | VARCHAR(16) | YES |  |  |
| `platform_shop_id` | VARCHAR(128) | YES |  |  |
| `brand_id` | BIGINT | YES |  |  |
| `platform_source_app_id` | VARCHAR(128) | YES |  |  |
| `extra_fields` | JSON | YES |  |  |
| `rule_status` | VARCHAR(32) | NO |  |  |
| `conflict_diagnostics` | JSON | YES |  |  |
| `observed_time` | DATETIME(3) | NO |  |  |
| `origin` | VARCHAR(16) | NO |  |  |
| `internal_product_id` | BIGINT | YES |  |  |
| `root_goods_name` | VARCHAR(512) | YES |  |  |
| `barcode` | VARCHAR(128) | YES |  |  |
| `bind_count` | INT | YES |  |  |
| `shop_name` | VARCHAR(256) | YES |  |  |
| `platform_product_id` | VARCHAR(128) | YES |  |  |
| `product_name` | VARCHAR(512) | YES |  |  |

主键、唯一键和二级索引：
- `PRIMARY KEY (`tenant_id`,`id`)`
- `KEY `idx_goods_group_rule` (`tenant_id`,`rule_version`)`
- `KEY `idx_goods_group_members` (`tenant_id`,`row_kind`,`rule_version`)`

### `trade_analytics.trade_analysis_order_fact_index_anchor`

- 证据类型：DDL。文件 `docs/sql/init/trade-analytics-init.sql`。实际数据库未核对。
- 表注释：订单事实分区索引定位
- 引擎：`ENGINE=InnoDB`
- 代码引用：AnalysisOrderFactIndexAnchorDO。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `order_key` | VARCHAR(256) | NO |  | 平台代码与订单号组合键 |
| `first_seen_time` | DATETIME(3) | NO |  | 首次建立索引的时间 |

主键、唯一键和二级索引：
- `PRIMARY KEY (order_key)`

### `trade_analytics.trade_analysis_order_fact_index`

- 证据类型：DDL。文件 `docs/sql/init/trade-analytics-init.sql`。实际数据库未核对。
- 表注释：订单事实分区索引正文
- 引擎：`ENGINE=InnoDB`
- 分区：`PARTITION BY RANGE COLUMNS(first_seen_date) (`
- 代码引用：AnalysisOrderFactIndexDO。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `order_key` | VARCHAR(256) | NO |  | 平台代码与订单号组合键 |
| `first_seen_date` | DATE | NO |  | 首次建立索引的日期 |
| `physical_version` | VARCHAR(32) | NO |  | 物理事实版本 |
| `index_text` | MEDIUMTEXT | NO |  | 分区字典及业务主体映射文本 |
| `complete` | TINYINT(1) | NO | 0 | 全部适用事实的历史日期是否核对完整 |
| `index_version` | BIGINT UNSIGNED | NO | 0 | 目录更新版本 |

主键、唯一键和二级索引：
- `PRIMARY KEY (order_key, first_seen_date)`

### `trade_analytics.mtx_id_pool_contract`

- 证据类型：DDL。文件 `docs/sql/init/trade-analytics-init.sql`。实际数据库未核对。
- 引擎：`ENGINE=InnoDB`
- 代码引用：发号契约。两个 MySQL 脚本都建。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `singleton_id` | TINYINT | NO |  |  |
| `schema_version` | INT | NO |  |  |
| `fingerprint` | CHAR(64) | NO |  |  |
| `CHECK` | (singleton_id = 1) | YES |  |  |

### `trade_analytics.mtx_id_worker`

- 证据类型：DDL。文件 `docs/sql/init/trade-analytics-init.sql`。实际数据库未核对。
- 引擎：`ENGINE=InnoDB`
- 代码引用：发号 worker。两个 MySQL 脚本都建。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `datacenter_id` | TINYINT UNSIGNED | NO |  |  |
| `worker_id` | TINYINT UNSIGNED | NO |  |  |
| `application_name` | VARCHAR(96) | YES |  |  |
| `instance_name` | VARCHAR(160) | YES |  |  |
| `process_id` | BINARY(16) | YES |  |  |
| `engine_name` | VARCHAR(96) | YES |  |  |
| `claim_token` | BINARY(16) | YES |  |  |
| `revision` | BIGINT | NO | 0 |  |
| `window_lower_ms` | BIGINT | YES |  |  |
| `high_watermark_ms` | BIGINT | NO |  |  |
| `expire_at` | DATETIME(3) | YES |  |  |
| `CHECK` | (datacenter_id <= 31 AND worker_id <= 31) | YES |  |  |
| `CHECK` | (revision >= 0) | YES |  |  |

主键、唯一键和二级索引：
- `PRIMARY KEY (datacenter_id, worker_id)`

### `trade_analytics.dwd_store_member_info_fact`

- 证据类型：DDL。文件 `docs/sql/init/trade-analytics-init.sql`。实际数据库未核对。
- 表注释：门店会员基础资料维护表
- 引擎：`ENGINE=InnoDB`
- 代码引用：StoreMemberInfoDO。MemberInfoCdcJob 的门店会员源表。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `brand_id` | BIGINT | NO |  | 内部品牌ID |
| `mchnt_cd` | VARCHAR(32) | NO |  | 来源商户号 |
| `open_id` | VARCHAR(128) | NO |  | 来源会员OpenID |
| `tenant_id` | VARCHAR(32) | YES |  | 租户中心租户ID |
| `sex` | LONGTEXT | YES |  | 性别原值 |
| `level_exp` | BIGINT | YES |  | 来源levelExp原值 |
| `level_name` | LONGTEXT | YES |  | 等级名称 |
| `union_phone` | LONGTEXT | YES |  | 来源unionPhone原值 |
| `user_birth` | LONGTEXT | YES |  | 生日原文 |
| `user_name` | LONGTEXT | YES |  | 用户名称 |
| `point` | LONGTEXT | YES |  | 可用积分原文 |
| `offline_card_no` | LONGTEXT | YES |  | 线下实体卡号 |
| `table_name` | LONGTEXT | YES |  | 来源tableName原值 |
| `exp_days` | BIGINT | YES |  | 来源expDays原值 |
| `real_name` | LONGTEXT | YES |  | 用户实名原值 |
| `subscribe_date` | DATE | YES |  | 注册日期 |
| `phone` | LONGTEXT | YES |  | 来源手机号原值 |
| `all_phone` | LONGTEXT | YES |  | 来源allPhone原值 |
| `platform_shop_id` | VARCHAR(128) | YES |  | 来源门店号 |
| `level_value` | BIGINT | YES |  | 会员等级值 |
| `update_type` | LONGTEXT | YES |  | 来源updateType原值 |
| `meta_sync_time` | DATETIME(3) | NO |  | 本行查询同步时间 |

主键、唯一键和二级索引：
- `PRIMARY KEY (`brand_id`, `mchnt_cd`, `open_id`)`
- `KEY `idx_store_member_open_id` (`open_id`)`
