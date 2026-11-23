# 06-DB-01-04 trade-flow 数据库与代码映射关系

表结构明细在 MySQL 清单和 Doris 清单。这里只回答表被谁使用、MySQL 和 Doris 如何对应。证据来自 `@TableName`、初始化脚本和已读写入类。实际库没有连上，所以“代码引用的表在库里不存在”无法用元数据证实，只能说初始化脚本里有没有。

## 1. 哪些表被代码引用

主代码 `@TableName` 覆盖了绝大多数 MySQL 业务表。字段级清单里的“代码引用”一行就是对应实体。

已经对上的主链路：

| 业务流程 | MySQL | 代码 | Doris |
| --- | --- | --- | --- |
| 原始报文 | `trade_flow.trade_storage`、`trade_storage_blob` 及 100 分表 | `StorageDO`、`StorageBlobDO`、`StorageWriteService` | 不直接存原文 |
| 富智云订单/支付事件 | `trade_order_event`、`trade_payment_event` | `OrderEventDO`、`PaymentEventDO` | CDC 版本表，不是统一事实表 |
| 外卖回调事件 | `trade_thirdparty_event` | `ThirdpartyEventDO` | 不直接订阅这张表 |
| 主动查询结果 | `trade_source_query_event` | `SourceQueryEventDO` | 查询正文仍回 Storage |
| 分析通知 | `trade_analysis_event_outbox` | `AnalysisEventOutboxDO` | 无同名 Doris 表 |
| 下游转发 | `trade_event_delivery_task`、`trade_event_delivery_attempt_log` | 对应 DO | 无 |
| 接入失败 | `trade_event_ingest_failure_log` | `EventIngestFailureLogDO` | 无 |
| 处理账本 | `trade_analytics.trade_analysis_event_process` 及 attempt、dead letter | 对应 DO | 无 |
| 订单归并 | `trade_analysis_order_state`、`trade_analysis_order_replay_context` | `AnalysisOrderStateDO`、`CanonicalWriteCoordinator` | 归并结果经 Flush 写入事实表 |
| 刷写任务 | `trade_analysis_flush_task_index`、`trade_analysis_flush_task_payload` | `CanonicalWriteCoordinator.freeze` | 快照 JSON 里是待写入的事实 |
| 补拉 | `trade_analysis_pickup_task` | `AnalysisPickupTaskDO` | 补拉结果仍走来源事件和归并 |
| 回灌 | `trade_analysis_backfill_task`、`_shard`、`_event_failure` | 对应 DO | 回灌复用归并和 Flush |
| 门店商品维表 | `dim_*` 商品和门店表 | Analytics catalog DO | 同名 Doris 表由 shop CDC、goods CDC 同步 |
| 查询模板 | `trade_analysis_query_template` 及 group、stash | Query 模块 DO | 模板 SQL 查询 Doris 事实和视图 |
| 团购核销 | MySQL 无同名明细表 | `FuiouGroupBuyVerificationSyncService` | `dwd_trade_group_buy_verification_fact_v1` |
| 门店会员 | `trade_analytics.dwd_store_member_info_fact` | `StoreMemberInfoDO` | Doris 同名表，member CDC 再同步 |

统一事实的写入类是 `DorisCanonicalFactWriter`。逻辑表名加上物理版本 `v1` 后，对应 `dwd_trade_order_fact_v1`、明细、优惠、支付、充值、退款、退款明细、投诉、结算分录。配置项是 `trade.doris.doris-facts-physical-version: v1`。

查询侧会读 `dwd_trade_*_fact_current`。这些视图在主 Doris 脚本里就有定义，不是只存在于辅助脚本。

## 2. 脚本里有、主代码实体未发现的表

| 对象 | 位置 | 结论 |
| --- | --- | --- |
| `trade_ssm_group_sync_checkpoint` | `trade_analytics` 脚本 | 未发现 `@TableName`。可能由手写 SQL 使用。待确认 |
| `trade_analysis_recovery_admission_control` | 同上 | 未发现 `@TableName`。待确认 |
| `dim_unified_shop_wide` | Doris 主脚本 | 未发现 `@TableName`。待确认谁查询 |
| `dwd_*_version` 六张 | Doris 主脚本 | 无实体类。`TradeCdcJob` 负责写入。已确认是 CDC 目标，不是 Analytics 归并的事实表 |
| `mtx_id_pool_contract`、`mtx_id_worker` | 两个 MySQL 脚本都有 | 发号器使用。业务订单不存放在这里 |

未发现独立的“优惠 MySQL 表”或“退款 MySQL 表”。优惠和退款金额在原文 BLOB、归并 JSON，以及 Doris 事实表里。

未发现 MySQL 团购核销明细表。团购只确认写到 Doris。

未发现本仓库 DDL 中的 Flink checkpoint 表或 CDC 位点表。位点如果存在，不在这三份初始化脚本里。

## 3. 代码引用但初始化脚本没有的表

| 名字 | 证据 | 结论 |
| --- | --- | --- |
| `member_info` | `MemberInfoDeserializer.SOURCE_TABLE` | 外部会员源表。本仓库三份主初始化脚本都没有它 |
| `trade_storage_00` 到 `_99`、`trade_storage_blob_00` 到 `_99` | `trade-flow-init.sql` 的动态 `LIKE` | 不是漏表。脚本会建，实体类仍映射逻辑名 `trade_storage` |

除此之外，本次扫描到的 `@TableName` 都能在两份 MySQL 初始化脚本里找到同名表。Doris 事实表是逻辑名加 `_v1`，不是缺失。

因为没有连上数据库，不能回答“线上有一张表但脚本没有”。这个方向全部待确认。

## 4. 同一业务对象是否两边都有

| 业务对象 | MySQL | Doris | 关系 |
| --- | --- | --- | --- |
| 原始报文 | 有，分 100 表 | 无同名表 | 只在 MySQL。CDC 读完后写成版本行，不搬 BLOB |
| 来源事件 | 有 | 无同名表 | 事件头进入 CDC 或 Analytics，原文另读 |
| 统一订单、明细、优惠、支付、退款 | 无独立列式表。状态在 `trade_analysis_order_state.accumulator_json` | `dwd_trade_*_fact_v1` | 归并在 MySQL，发布在 Doris |
| 门店、商品维表 | `trade_analytics.dim_*` | `trade_warehouse` 同名表 | CDC 从 MySQL 同步到 Doris |
| 门店会员 | MySQL `dwd_store_member_info_fact` | Doris 同名表 | 先写 MySQL，再由 member CDC 同步 |
| 品牌会员 | 外部 `member_info` | `dwd_member_info_fact_v1` | 不经过 trade_analytics 的同名业务表 |
| 团购核销 | 无明细表 | 有事实表 | 查询接口的响应直接 Stream Load |
| 订单版本 | 无 | `dwd_order_version` 等六张 | 另一条 Flink 路径，只覆盖富智云订单和支付事件 |

## 5. MySQL 与 Doris 的同步路径

已确认三条路径，不要合成一条。

1. Analytics 归并路径。Ingress 把原文和来源事件写进 `trade_flow`。Analytics 归并到 `trade_analytics.trade_analysis_order_state`，冻结到 Flush 任务，再由 `DorisCanonicalFactWriter` 用 JDBC 或 Stream Load 写入 `dwd_trade_*_fact_v1`。这不是 binlog CDC。
2. 事件 CDC。`TradeCdcJob` 订阅 `trade_order_event`、`trade_payment_event` 的插入，按 Storage 双键读原文，写入六张 `dwd_*_version`。它不读 `trade_thirdparty_event`。
3. 维表 CDC。`ShopDimensionCdcJob` 同步六张门店表。`GoodsDimensionCdcJob` 同步商品维表。`MemberInfoCdcJob` 同步外部 `member_info` 和 MySQL 里的 `dwd_store_member_info_fact`。

查询从 `trade-query` 读已发布模板，执行 Doris SQL。模板定义本身在 MySQL `trade_analysis_query_template`。

## 6. 多个数据源是否指向不同环境

已确认。生产、本地、测试三套 YAML 的主机和库名不同。测试 Doris 库名是 `trade_warehouse_test`。生产配置内部也不完全相同：Analytics 的 MySQL 写本机，Query 的 MySQL 写另一台内网地址。它们是不是同一台机器的两种写法，待确认。
