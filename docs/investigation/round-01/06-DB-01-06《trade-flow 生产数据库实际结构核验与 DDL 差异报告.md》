# 06-DB-01-06 trade-flow 生产数据库实际结构核验与 DDL 差异报告

本版保留 2026-11-23 14:12 已写出的生产证据，并补上 `trade_analytics` 的差异明细、金额字段、两套 CDC 表名核验，以及全部 Doris 基表的 `SHOW CREATE TABLE`。没有执行写入，没有修改 SQL、Java 或配置。

证据等级：生产已确认、DDL 已确认、代码已确认、配置推断、待确认、未发现。未发现只表示本次范围内没找到，不能写成不存在。代码里有 Sink，不能写成生产作业正在运行。初始化脚本里有表，不能写成生产已经建表。

## 1. 调查目标与范围

核验已经授权的两个只读连接：MySQL `172.21.0.7:3306` 上的 `trade_flow`、`trade_analytics`，以及 Doris `172.21.0.2:9030` 上的 `trade_warehouse`。对照文件是 `docs/sql/init/trade-flow-init.sql`、`docs/sql/init/trade-analytics-init.sql`、`docs/sql/init/trade-doris-init.sql`、`docs/sql/init/trade-doris-fact-v1-init.sql`，以及 `docs/sql/` 下尚未归档的日期脚本。

外部库 `172.21.0.9` 的 `mms_manage.member_info` 没有本次授权。未连接，不能据此判断该表不存在。

## 2. 调查方法与只读约束

元数据导出时间以 MySQL `NOW()` 为准：`2026-11-23 14:01:46`。同日 14:19 隧道仍然可用，并再次对 12 个版本表名执行 `SHOW CREATE TABLE`。

使用的语句限于 `SELECT` 版本与身份、`SHOW DATABASES`、`SHOW GRANTS`、`SHOW FULL TABLES`、`SHOW CREATE TABLE`、`SHOW PARTITIONS`、`DESC`、`SHOW FRONTENDS`、`SHOW BACKENDS`、`SHOW VARIABLES`，以及 `information_schema` 的库、表、列、索引、约束和视图。没有 `COUNT(*)`，没有导出业务行。数据是否存在，使用 Doris 分区元数据 `RowCount` 和 MySQL `information_schema.TABLES.TABLE_ROWS`。后者对 InnoDB 是估算值。

当前 MySQL 用户 `flink_cdc@%` 对 `trade\_%`.* 有 `SELECT` 和 `SHOW VIEW`，未见这两个库上的 `INSERT`、`UPDATE`、`DELETE`。Doris 用户能读取基表建表语句；对视图执行 `SHOW CREATE TABLE` 返回权限拒绝。视图定义改从 `information_schema.views` 读取。

## 3. MySQL 实例信息

| 项 | 结果 | 等级 |
| --- | --- | --- |
| 产品版本 | MySQL Community Server `8.0.45` | 生产已确认 |
| 实例 | 主机名 `VM-0-7-ubuntu`，`server_uuid` 前 8 位 `77e0e1e4`，监听 `*:3306` | 生产已确认 |
| 内网地址 | 该主机网卡有 `172.21.0.7/20` | 生产已确认 |
| 当前用户 | `flink_cdc@%` | 生产已确认 |
| 当前默认库 | 连接时未选库，`DATABASE()` 为空 | 生产已确认 |
| 字符集 | `trade_flow`、`trade_analytics` 均为 `utf8mb4` / `utf8mb4_0900_ai_ci` | 生产已确认 |

地址对应关系必须分开看：

| 配置 | 写法 | 判断 | 等级 |
| --- | --- | --- | --- |
| Query、Flink 门店/商品/门店会员 CDC | `172.21.0.7:3306/trade_analytics` | 该地址就是本次 mysqld | 生产已确认 |
| Analytics `trade.analytics.datasource` 与 `trade.storage.datasource` | `127.0.0.1:3306` | 只能说明进程连自己机器上的 3306。本次主机同时监听 `8127`，与 Analytics 绿槽端口约定一致，但没有读取进程命令行 | 配置推断 |
| Ingress `spring.datasource` | `127.0.0.1:3306/trade_flow` | 本次主机监听 `8115`，与 Ingress 蓝槽端口一致。同样不能单凭 YAML 把 `127.0.0.1` 写成已经证明 | 配置推断 |

`oms` 库名可见，账号看不到其中的表。不能解释成空库。待确认。

## 4. MySQL 数据库及表清单

`trade_flow` 共 216 张基表，其中逻辑表 16 张，`trade_storage_NN` 100 张，`trade_storage_blob_NN` 100 张。没有视图。

逻辑表：`mtx_id_pool_contract`、`mtx_id_worker`、`trade_analysis_event_outbox`、`trade_data_retention_control`、`trade_event_delivery_attempt_log`、`trade_event_delivery_task`、`trade_event_ingest_failure_log`、`trade_jd_authorization_token`、`trade_order_event`、`trade_payment_event`、`trade_source_query_event`、`trade_storage`、`trade_storage_archive_control`、`trade_storage_blob`、`trade_thirdparty_config`、`trade_thirdparty_event`。

`trade_analytics` 共 45 张基表，没有视图。表名：

`dim_brand`、`dim_brand_thirdparty`、`dim_fuiou_shop_info`、`dim_goods_category_v1`、`dim_goods_platform_catalog_v1`、`dim_goods_product_package_rel_v1`、`dim_goods_product_source_v1`、`dim_goods_product_v1`、`dim_jd_shop_info`、`dim_meituan_shop_info`、`dim_region`、`dim_taobao_shop_info`、`dim_trade_goods_group`、`dim_trade_shop_group`、`dim_trade_shop_id_mapping`、`dwd_store_member_info_fact`、`mtx_id_pool_contract`、`mtx_id_worker`、`thirdparty_sync_watermark`、`trade_analysis_backfill_event_failure`、`trade_analysis_backfill_shard`、`trade_analysis_backfill_task`、`trade_analysis_event_attempt_log`、`trade_analysis_event_process`、`trade_analysis_fact_write_task_index`、`trade_analysis_fact_write_task_payload`、`trade_analysis_flush_task_index`、`trade_analysis_flush_task_payload`、`trade_analysis_order_fact_index`、`trade_analysis_order_fact_index_anchor`、`trade_analysis_order_replay_context`、`trade_analysis_order_state`、`trade_analysis_orphan_event_failure`、`trade_analysis_pickup_task`、`trade_analysis_quality_candidate`、`trade_analysis_quality_issue`、`trade_analysis_quality_rule`、`trade_analysis_quality_run`、`trade_analysis_query_template`、`trade_analysis_query_template_group`、`trade_analysis_query_template_stash`、`trade_analysis_recovery_admission_control`、`trade_analysis_stream_dead_letter`、`trade_data_retention_control`、`trade_mapping_compensation`。

初始化脚本有、生产没有的表：`dim_jd_account_anchor`、`dim_jd_account_binding`、`trade_ssm_group_sync_checkpoint`。生产有、`trade-analytics-init.sql` 没有的表：`dim_region`。

空间与估算行数（`TABLE_ROWS` 为估算）：

| 范围 | 表数 | 估算行数 | 数据长度 | 索引长度 |
| --- | --- | --- | --- | --- |
| `trade_flow` 逻辑表 | 16 | 198,057,436 | 64,457,523,200 字节（60.0 GiB） | 74,586,095,616 字节（69.5 GiB） |
| `trade_flow` 分表 | 200 | 96,492,362 | 120,000,020,480 字节（111.8 GiB） | 11,790,729,216 字节（11.0 GiB） |
| `trade_analytics` | 45 | 43,069,127 | 83,165,282,304 字节（77.5 GiB） | 15,420,850,176 字节（14.4 GiB） |

## 5. trade_flow 生产结构与初始化 DDL 差异

16 张逻辑表全部存在，没有额外逻辑表。逐字段对比未发现缺字段、多字段、类型变化、长度或精度变化、NULL 变化、默认值变化、主键变化。生成列在生产中写成 MySQL 规范形式，例如 `MOD(x, 64)` 存成 `x % 64`，函数索引存成完整 `CASE` 表达式，`last_error(64)` 的前缀在 `SUB_PART=64`。这些是同一条定义的存储写法。字段顺序未发现与脚本不一致。

因此 `trade_flow` 与 `docs/sql/init/trade-flow-init.sql` 没有实质结构差异。等级：生产已确认，DDL 已确认。

来源事件、Outbox、转发、失败日志都在这个库，不在 `trade_analytics`。订单事件是 `trade_order_event`，支付事件是 `trade_payment_event`。它们保存键和 Storage 指针，不保存拆开的金额列。

## 6. trade_analytics 生产结构与初始化 DDL 差异

45 张生产表中，42 张与 `docs/sql/init/trade-analytics-init.sql` 逐字段对比没有实质差异。没有实质差异的范围包括订单归并 `trade_analysis_order_state`、`trade_analysis_order_replay_context`，Flush、补拉、回灌、事实写入任务、查询模板及 `group_id`、门店会员 `dwd_store_member_info_fact`。这些表的字段类型、NULL、默认值、主键和索引与脚本一致。

订单事件、支付事件、原始报文和 Outbox 不在 `trade_analytics`，已在第 5 章核对，不能记成 Analytics 缺表。

| 表名 | 差异类型 | 字段名 | 初始化脚本定义 | 生产实际定义 | 差异说明 | 业务影响 |
| --- | --- | --- | --- | --- | --- | --- |
| `dim_jd_account_anchor` | 表缺失 | 整表 | `trade-analytics-init.sql` 有建表。主键 `id`，唯一键 `uk_jd_account(app_key, vender_id)` | 无此表 | 脚本和 `JdAccountAnchorDO` 都引用它 | 不参与金额列。影响京东账号锚点，不影响 06-02 读取 Doris 金额字段 |
| `dim_jd_account_binding` | 表缺失 | 整表 | 脚本有建表，外键 `fk_jd_binding_anchor` 指向锚点 | 无此表，因此该外键也不存在 | `JdAccountBindingDO` 映射此表 | 不参与金额计算、订单归并或来源事件幂等。影响京东账号与品牌的时间区间 |
| `trade_ssm_group_sync_checkpoint` | 表缺失 | 整表 | 脚本有建表，测试 `GroupSchemaContractTest` 也要求它存在 | 无此表 | 分组同步检查点 | 属于同步进度，不是金额事实，不是订单状态水位。不影响 06-02 金额字段 |
| `dim_region` | 生产额外表 | 整表 | `trade-analytics-init.sql` 未定义。Doris 主脚本有同名表 | 生产列：`code char(6)` 主键，`name varchar(50) NOT NULL`，`level tinyint NOT NULL`，`parent_code char(6) NULL`，`deleted tinyint NOT NULL`，`create_time datetime NULL`，`update_time datetime NULL` | Analytics 与 Query 的 `RegionDO` 映射此表 | 行政区维表。不参与金额计算。新环境只执行 MySQL init 时会缺表 |

以下写法不同，但不是业务结构差异，不重复算进上表：`BOOLEAN` 在 MySQL 中实际存成 `tinyint(1)`，涉及商品维表的 `is_deleted`、`is_default_selected`；生成列 `IF(is_deleted = 0, ...)` 在生产中多一层括号；回灌 `CHECK` 里的 `OCTET_LENGTH` 显示为 `LENGTH`；补拉和查询事件上的函数索引在 `information_schema` 中保存完整 `CASE`。等级：生产已确认这些对象存在且语义与脚本相同。

生产外键数量为 0。脚本里唯一的外键属于缺失的 `dim_jd_account_binding`。读到 11 条 `CHECK`，对应脚本中的检查约束。

`docs/sql/trade-analytics-query-template-groups-2026-11-20.sql` 和 `trade-analytics-store-member-2026-11-14.sql` 描述的表和字段已经同时在 init 和生产中。未发现这两份日期脚本还留下未建的 MySQL 对象。

## 7. Storage 200 张分表结构核验

| 检查 | `trade_storage_00`～`_99` | `trade_storage_blob_00`～`_99` |
| --- | --- | --- | --- |
| 实际数量 | 100 | 100 |
| 缺失编号 | 无 | 无 |
| 额外数字后缀 | 无 | 无 |
| 与模板的字段、顺序、类型、NULL、默认值、索引、主键 | 100 张同一签名 | 100 张同一签名 |

等级：生产已确认。这只说明物理表结构一致。本次没有核对分片路由是否把每个 `payload_sha256` 写到对应编号，也没有扫描各分表里的业务行。不能据此认为读写路由已经正确。

## 8. Doris 实例与版本核验

| 项 | 结果 | 等级 |
| --- | --- | --- |
| 连接 | 内网 `172.21.0.2`，查询端口 `9030`，HTTP `8030`，BE `8040` | 生产已确认 |
| 协议版本 | `SELECT VERSION()` 返回 `5.7.99` | 这是 MySQL 协议兼容号，不是产品版本 |
| 产品版本 | `@@version_comment` 与 `SHOW FRONTENDS`、`SHOW BACKENDS` 都是 `doris-4.0.7-rc02-35854e7e92a` | 生产已确认 |
| 当前用户 | 隧道在 Doris 侧显示为 `doris_read_only@127.0.0.1` | 生产已确认 |
| 当前默认库 | 连接时未选库 | 生产已确认 |
| FE / BE | 同一内网地址，均为 alive，FE 是 master | 生产已确认 |

Analytics 与 Query 的生产 JDBC 都写 `172.21.0.2:9030/trade_warehouse`。等级：配置与本次 FE 地址一致，生产已确认是这一台。

## 9. Doris 生产数据库对象清单

`SHOW DATABASES` 可见：`__internal_schema`、`information_schema`、`mysql`、`trade_warehouse`。`trade_warehouse_test` 不在结果中。等级：生产已确认当前账号在本实例看不到该库。它是否在别的测试实例上，待确认。

`trade_warehouse` 基表 26 张，视图 11 个。视图没有计入基表。

基表：`dim_fuiou_shop_info`、`dim_goods_category_v1`、`dim_goods_platform_catalog_v1`、`dim_goods_product_package_rel_v1`、`dim_goods_product_source_v1`、`dim_goods_product_v1`、`dim_jd_shop_info`、`dim_meituan_shop_info`、`dim_region`、`dim_taobao_shop_info`、`dim_trade_goods_group`、`dim_trade_shop_group`、`dim_trade_shop_id_mapping`、`dim_unified_shop_wide`、`dwd_member_info_fact_v1`、`dwd_store_member_info_fact`、`dwd_trade_complaint_fact_v1`、`dwd_trade_group_buy_verification_fact_v1`、`dwd_trade_order_discount_fact_v1`、`dwd_trade_order_fact_v1`、`dwd_trade_order_item_fact_v1`、`dwd_trade_payment_fact_v1`、`dwd_trade_recharge_fact_v1`、`dwd_trade_refund_fact_v1`、`dwd_trade_refund_item_fact_v1`、`dwd_trade_settlement_entry_fact_v1`。

视图：`dwd_member_info_fact_current`、`dwd_trade_complaint_fact_current`、`dwd_trade_group_buy_verification_fact_current`、`dwd_trade_order_discount_fact_current`、`dwd_trade_order_fact_current`、`dwd_trade_order_item_fact_current`、`dwd_trade_payment_fact_current`、`dwd_trade_recharge_fact_current`、`dwd_trade_refund_fact_current`、`dwd_trade_refund_item_fact_current`、`dwd_trade_settlement_entry_fact_current`。

主初始化脚本有、生产没有的基表，只有六张 CDC 版本表，见第 13 章。未发现生产基表在主脚本中完全没有定义。`dim_region` 在 Doris 主脚本和生产中都有。

`information_schema.TABLES`：26 张基表 `TABLE_ROWS` 合计 67,197,076，`DATA_LENGTH` 合计 9,736,293,359 字节（9.1 GiB）。BE 已用容量 32.836 GB，含存储层开销，不能与表级数据长度直接相减。

## 10. Doris 生产基表 SHOW CREATE TABLE 完整结果

完整原文集中在附录 B，避免同一份语句在正文和附录各保存一遍后发生分叉。附录 B 的每张表都是 `SHOW CREATE TABLE` 的完整返回，没有删字段。第 11 章只做结构化摘录，便于对照键、分区和分桶。

## 11. Doris 关键事实表结构汇总

下表全部来自生产 `SHOW CREATE TABLE` 和 `SHOW PARTITIONS`，不是脚本摘录。

| 表名 | Key 模型 | 唯一键字段 | 分区字段 | 分桶字段 | Bucket 数 | Merge-on-Write | Sequence 配置 | 备注 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `dwd_trade_order_fact_v1` | UNIQUE | order_created_date, platform_code, platform_order_id | order_created_date | platform_code, platform_order_id | 16 | true | meta_sink_sequence | 状态列没有脚本中的 DEFAULT NONE；有 is_membership；分区 74 个，p202110 至 p202711，介质 SSD |
| `dwd_trade_order_item_fact_v1` | UNIQUE | order_created_date, platform_code, platform_order_id, item_line_key | order_created_date | platform_code, platform_order_id | 16 | true | meta_sink_sequence | 缺少 idx_order_item_internal_shop_id；分区 74 个，p202110 至 p202711，介质 SSD |
| `dwd_trade_order_discount_fact_v1` | UNIQUE | order_created_date, platform_code, platform_order_id, discount_line_key | order_created_date | platform_code, platform_order_id | 8 | true | meta_sink_sequence | 含 user_bear_amount、meta_is_active；分区 74 个，p202110 至 p202711，介质 SSD |
| `dwd_trade_payment_fact_v1` | UNIQUE | payment_date, platform_code, payment_id, payment_line_key | payment_date | platform_code, payment_id | 8 | true | meta_sink_sequence | 有 paid_amount、payment_fee_amount；分区 74 个，p202110 至 p202711，介质 SSD |
| `dwd_trade_recharge_fact_v1` | UNIQUE | recharge_date, platform_code, recharge_id | recharge_date | platform_code, recharge_id | 8 | true | meta_sink_sequence | 含 direction、recharge_amount、gift_amount；分区 74 个，p202110 至 p202711，介质 SSD |
| `dwd_trade_refund_fact_v1` | UNIQUE | refund_date, platform_code, refund_id | refund_date | platform_code, refund_id | 8 | true | meta_sink_sequence | refund_status、refund_type 无默认值；分区 74 个，p202110 至 p202711，介质 SSD |
| `dwd_trade_refund_item_fact_v1` | UNIQUE | refund_date, platform_code, refund_id, refund_item_line_key | refund_date | platform_code, refund_id | 8 | true | meta_sink_sequence | refund_quantity 为 decimal(18,3)；分区 74 个，p202110 至 p202711，介质 SSD |
| `dwd_trade_group_buy_verification_fact_v1` | UNIQUE | brand_id, coupon_info_id, check_month | date_trunc(`check_month`, 'month' | brand_id, coupon_info_id | 8 | true | 未发现 | 分区表达式 date_trunc(check_month, month)，4 个分区；分区 4 个，p20260801000000 至 p20261101000000，介质 SSD |
| `dwd_trade_complaint_fact_v1` | UNIQUE | complaint_created_date, platform_code, complaint_id | complaint_created_date | platform_code, complaint_id | 8 | true | meta_sink_sequence | 分区行数为 0；分区 74 个，p202110 至 p202711，介质 SSD |
| `dwd_trade_settlement_entry_fact_v1` | UNIQUE | bill_date, platform_code, finance_order_id, entry_line_key | bill_date | platform_code, finance_order_id | 8 | true | meta_sink_sequence | 分区行数为 0；分区 74 个，p202110 至 p202711，介质 SSD |

未发现这些表使用 `DUPLICATE KEY` 或 `AGGREGATE KEY`。未发现 `ORDER BY` 子句。未发现 `bloom_filter_columns`。表属性里的 `storage_medium` 是 `hdd`，但上表全部分区的 `StorageMedium` 都是 `SSD`。

26 张基表全部是 `UNIQUE KEY`，`enable_unique_key_merge_on_write=true`。副本脚本写 `replication_num=1`，生产写成 `replication_allocation=tag.location.default: 1`，分区 `ReplicationNum` 也是 1。等级：生产已确认副本数为 1。

## 12. Doris 与两份初始化 SQL 差异

主脚本是 `docs/sql/init/trade-doris-init.sql`。辅助脚本是 `docs/sql/init/trade-doris-fact-v1-init.sql`，只覆盖 10 张事实表，不包含版本表和维表。

| 表名 | 对比项目 | 主初始化脚本 | 辅助初始化脚本 | 生产实际 | 差异结论 | 影响 |
| --- | --- | --- | --- | --- | --- | --- |
| 六张 `dwd_order*_version`、`dwd_payment*_version` | 表是否存在 | 有定义 | 未定义 | `Unknown table` | 只有主脚本定义，生产没有 | 不提供统一金额列。阻断的是 CDC 版本落点 |
| `dwd_trade_order_fact_v1` | 字段与分区窗口 | 含 `src_core_snapshot_source`、`src_refund_pickup_result_sequence`；动态分区 start=`-2147483648`；三个状态列默认 `NONE` | 没有这两列；start=`-60` | 两列都在；start=`-2147483648`；三个状态列没有默认值 | 生产跟随主脚本的列和窗口，不跟随辅助脚本；默认值与主脚本不同 | 金额列本身没有因此缺失。省略状态列的插入不能靠默认值写成 NONE |
| `dwd_trade_order_item_fact_v1` | 字段与索引 | 有 `meta_is_active`；两个倒排索引 | 没有 `meta_is_active`；start=`-60` | 有 `meta_is_active`；只有 `idx_item_platform_order_id` | 列跟随主脚本；门店倒排索引两边脚本里主脚本有，生产没有 | 不改变金额字段。按内部门店过滤明细时少一个倒排索引 |
| `dwd_trade_order_discount_fact_v1` | 字段 | 有 `meta_is_active`、`user_bear_amount`；start=`-2147483648` | 没有 `meta_is_active`；start=`-60` | 与主脚本字段一致 | 辅助脚本较旧 | `user_bear_amount` 可纳入后续金额字段清单 |
| 支付、充值、退款、退款明细、投诉、结算 | 分区窗口 | start=`-2147483648` | start=`-60` | start=`-2147483648` | 生产与主脚本一致，辅助脚本更旧 | 退款事实的两个状态列另有默认值差异，见下 |
| `dwd_trade_refund_fact_v1` | 默认值 | `refund_status`、`refund_type` 默认 `NONE` | 与主脚本同类默认值，窗口更短 | 两列 `NOT NULL`，无默认值 | 仅默认值与主脚本不同 | 不影响已写入行的金额类型 |
| `dwd_trade_group_buy_verification_fact_v1` | 模型 | 与辅助脚本一致 | 与主脚本一致 | `UNIQUE KEY(brand_id, coupon_info_id, check_month)`，8 桶 | 两份脚本与生产模型一致 | `STRING` 列在生产显示为 `text` |
| `dim_trade_shop_id_mapping` | NULL | `brand_id BIGINT NOT NULL` | 辅助脚本无此表 | `brand_id bigint NULL` | 生产允许空品牌 | 不改变金额事实表 |

判断：

- 没有任何一份脚本与生产 `SHOW CREATE TABLE` 逐字相同。
- 主脚本更接近生产：多出的列、动态分区 `start=-2147483648`、merge-on-write 和 sequence 都对得上。
- 辅助脚本较旧：历史窗口只有 60 个月，并缺少生产已经存在的列。不能用它描述生产。
- 生产有主脚本尚未反映的现状：订单和退款的部分状态列没有默认值，门店映射 `brand_id` 可空，订单明细少一个倒排索引，分区已经物化，表级介质写 `hdd` 而分区是 `SSD`，`STRING` 显示为 `text`，`replication_num` 显示为 `replication_allocation`。

`is_membership`、`user_bear_amount`、两张会员表已经同时出现在主脚本和生产中。未归档日期脚本 `trade-doris-order-item-shop-index-2026-11-20.sql` 要建的 `idx_order_item_internal_shop_id` 还没有出现在生产。

## 13. 六张 CDC 版本表存在性核验

任务清单中的六个名字与代码、主脚本都不同。代码以 `DorisRowType` 为准，文件 `trade-flink-job/trade-cdc/src/main/java/com/mtx/trade/cdc/sink/DorisRowType.java`。`TradeCdcJob` 按这个枚举创建 Sink。两套名字都做了存在性检查。

| 名称来源 | 表名 | 查询结果 | 主脚本 | 代码 Sink |
| --- | --- | --- | --- | --- |
| 代码与主脚本 | `dwd_order_version` | `Unknown table`，不是权限拒绝 | 有 `CREATE TABLE` | `DorisRowType` 引用 |
| 代码与主脚本 | `dwd_order_item_version` | `Unknown table`，不是权限拒绝 | 有 `CREATE TABLE` | `DorisRowType` 引用 |
| 代码与主脚本 | `dwd_order_item_spec_version` | `Unknown table`，不是权限拒绝 | 有 `CREATE TABLE` | `DorisRowType` 引用 |
| 代码与主脚本 | `dwd_order_package_item_version` | `Unknown table`，不是权限拒绝 | 有 `CREATE TABLE` | `DorisRowType` 引用 |
| 代码与主脚本 | `dwd_payment_version` | `Unknown table`，不是权限拒绝 | 有 `CREATE TABLE` | `DorisRowType` 引用 |
| 代码与主脚本 | `dwd_payment_account_version` | `Unknown table`，不是权限拒绝 | 有 `CREATE TABLE` | `DorisRowType` 引用 |
| 本次任务清单 | `dwd_trade_order_version` | `Unknown table`，不是权限拒绝 | 未发现同名定义 | 未发现 Sink 引用 |
| 本次任务清单 | `dwd_trade_order_item_version` | `Unknown table`，不是权限拒绝 | 未发现同名定义 | 未发现 Sink 引用 |
| 本次任务清单 | `dwd_trade_payment_version` | `Unknown table`，不是权限拒绝 | 未发现同名定义 | 未发现 Sink 引用 |
| 本次任务清单 | `dwd_trade_refund_version` | `Unknown table`，不是权限拒绝 | 未发现同名定义 | 未发现 Sink 引用 |
| 本次任务清单 | `dwd_trade_order_discount_version` | `Unknown table`，不是权限拒绝 | 未发现同名定义 | 未发现 Sink 引用 |
| 本次任务清单 | `dwd_trade_recharge_version` | `Unknown table`，不是权限拒绝 | 未发现同名定义 | 未发现 Sink 引用 |

等级：这 12 个名字在生产 `trade_warehouse` 中都是 `Unknown table`，生产已确认。代码引用只证明仓库里有 Sink 类，不证明生产作业正在运行。本次没有查 Flink 作业状态，仓库里的 `trade-cdc` 模块也没有随包 properties 能证明作业已启动。运行状态待确认。

## 14. 金额字段生产结构核验

金额列的业务含义不能只靠字段名确定，这里只核类型、精度、空值和默认值是否与主脚本一致。未发现这些字段使用 `FLOAT` 或 `DOUBLE`。除 `refund_quantity` 外，列出的金额列都是 `bigint`，没有小数位；脚本注释写的单位是分。`direction` 是字符串，不是金额。

| 表名 | 字段名 | 生产类型 | 精度 | Scale | NULL 属性 | 默认值 | 与主脚本 | 金额分析影响 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `dwd_trade_order_fact_v1` | `turnover_amount` | `bigint` | 无长度参数 | 不适用 | NULL | 无 | 一致 | 可按整数分继续分析 |
| `dwd_trade_order_fact_v1` | `user_pay_amount` | `bigint` | 无长度参数 | 不适用 | NULL | 无 | 一致 | 可按整数分继续分析 |
| `dwd_trade_order_fact_v1` | `order_original_amount` | `bigint` | 无长度参数 | 不适用 | NULL | 无 | 一致 | 可按整数分继续分析 |
| `dwd_trade_order_item_fact_v1` | `original_amount` | `bigint` | 无长度参数 | 不适用 | NULL | 无 | 一致 | 可按整数分继续分析 |
| `dwd_trade_order_item_fact_v1` | `discounted_amount` | `bigint` | 无长度参数 | 不适用 | NULL | 无 | 一致 | 可按整数分继续分析 |
| `dwd_trade_order_item_fact_v1` | `user_pay_amount` | `bigint` | 无长度参数 | 不适用 | NULL | 无 | 一致 | 可按整数分继续分析 |
| `dwd_trade_order_discount_fact_v1` | `benefit_amount` | `bigint` | 无长度参数 | 不适用 | NULL | 无 | 一致 | 可按整数分继续分析 |
| `dwd_trade_order_discount_fact_v1` | `merchant_bear_amount` | `bigint` | 无长度参数 | 不适用 | NULL | 无 | 一致 | 可按整数分继续分析 |
| `dwd_trade_order_discount_fact_v1` | `platform_bear_amount` | `bigint` | 无长度参数 | 不适用 | NULL | 无 | 一致 | 可按整数分继续分析 |
| `dwd_trade_payment_fact_v1` | `paid_amount` | `bigint` | 无长度参数 | 不适用 | NOT NULL | 无 | 一致 | 可按整数分继续分析 |
| `dwd_trade_payment_fact_v1` | `payment_fee_amount` | `bigint` | 无长度参数 | 不适用 | NULL | 无 | 一致 | 可按整数分继续分析 |
| `dwd_trade_recharge_fact_v1` | `recharge_amount` | `bigint` | 无长度参数 | 不适用 | NOT NULL | 无 | 一致 | 可按整数分继续分析 |
| `dwd_trade_recharge_fact_v1` | `gift_amount` | `bigint` | 无长度参数 | 不适用 | NULL | 无 | 一致 | 可按整数分继续分析 |
| `dwd_trade_refund_fact_v1` | `refund_amount` | `bigint` | 无长度参数 | 不适用 | NULL | 无 | 一致 | 可按整数分继续分析 |
| `dwd_trade_refund_fact_v1` | `merchant_bear_amount` | `bigint` | 无长度参数 | 不适用 | NULL | 无 | 一致 | 可按整数分继续分析 |
| `dwd_trade_refund_item_fact_v1` | `refund_quantity` | `decimal(18,3)` | 18 | 3 | NULL | 无 | 一致 | 数量列，不是金额分 |
| `dwd_trade_refund_item_fact_v1` | `refund_amount` | `bigint` | 无长度参数 | 不适用 | NULL | 无 | 一致 | 可按整数分继续分析 |
| `dwd_trade_settlement_entry_fact_v1` | `direction` | `varchar(8)` | 8 | 不适用 | NOT NULL | 无 | 一致 | 方向码，不是金额 |
| `dwd_trade_settlement_entry_fact_v1` | `amount` | `bigint` | 无长度参数 | 不适用 | NOT NULL | 无 | 一致 | 可按整数分继续分析 |

基表中出现 float/double 的列：
- `dim_meituan_shop_info.latitude`：`double NULL`
- `dim_meituan_shop_info.longitude`：`double NULL`
- `dim_taobao_shop_info.latitude`：`double NULL`
- `dim_taobao_shop_info.longitude`：`double NULL`

未发现上述金额列缺失、新增，或与主脚本的类型、NULL、默认值不一致。等级：生产已确认，DDL 已确认。这不构成金额计算公式。

## 15. 生产数据存在性核验

核验方式是 `SHOW PARTITIONS` 的 `RowCount` 合计，对照 `information_schema.TABLES.TABLE_ROWS`。不是 `COUNT(*)`。行数大于 0 只说明分区元数据里有行，不能代替业务样本抽样。

| 表名 | 是否存在 | 是否有数据 | 核验方式 | 结果 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `dwd_trade_order_fact_v1` | 存在 | 有 | `SHOW PARTITIONS.RowCount` | 13028364；information_schema 为 13028364 | 分区 74 个 |
| `dwd_trade_order_item_fact_v1` | 存在 | 有 | `SHOW PARTITIONS.RowCount` | 33968557；information_schema 为 33968557 | 分区 74 个 |
| `dwd_trade_order_discount_fact_v1` | 存在 | 有 | `SHOW PARTITIONS.RowCount` | 7428765；information_schema 为 7428765 | 分区 74 个 |
| `dwd_trade_payment_fact_v1` | 存在 | 有 | `SHOW PARTITIONS.RowCount` | 10781165；information_schema 为 10781165 | 分区 74 个 |
| `dwd_trade_recharge_fact_v1` | 存在 | 有 | `SHOW PARTITIONS.RowCount` | 52354；information_schema 为 52354 | 分区 74 个 |
| `dwd_trade_refund_fact_v1` | 存在 | 有 | `SHOW PARTITIONS.RowCount` | 140757；information_schema 为 140757 | 分区 74 个 |
| `dwd_trade_refund_item_fact_v1` | 存在 | 有 | `SHOW PARTITIONS.RowCount` | 95590；information_schema 为 95590 | 分区 74 个 |
| `dwd_trade_group_buy_verification_fact_v1` | 存在 | 有 | `SHOW PARTITIONS.RowCount` | 432542；information_schema 为 432542 | 分区 4 个 |
| `dwd_trade_complaint_fact_v1` | 存在 | 无 | `SHOW PARTITIONS.RowCount` | 0；information_schema 为 0 | 分区 74 个 |
| `dwd_trade_settlement_entry_fact_v1` | 存在 | 无 | `SHOW PARTITIONS.RowCount` | 0；information_schema 为 0 | 分区 74 个 |

投诉表和结算分录表结构完整，但分区行数是 0。不能把它们当成已经有生产业务样本。等级：生产已确认。

## 16. 缺失对象与待确认事项

| 对象 | 代码 | 初始化 SQL | 生产 | 替代实现 | 对金额分析 |
| --- | --- | --- | --- | --- | --- |
| `dim_jd_account_anchor` | `JdAccountAnchorDO`，代码已确认 | `trade-analytics-init.sql` 已定义 | 不存在，生产已确认 | 未发现同职责的另一张生产表 | 不影响 Doris 金额列 |
| `dim_jd_account_binding` | `JdAccountBindingDO`，代码已确认 | 同上，并含外键 | 不存在，生产已确认 | 未发现 | 不影响 Doris 金额列；影响京东账号绑定是否能按当前代码运行 |
| `trade_ssm_group_sync_checkpoint` | `GroupSchemaContractTest` 要求该表，代码已确认 | 已定义 | 不存在，生产已确认 | 未发现 | 不影响金额列；影响分组同步检查点 |
| `mms_manage.member_info` | `MemberInfoDeserializer.SOURCE_TABLE`，库 `mms_manage`，主机配置 `172.21.0.9` | 三份主初始化脚本都没有 | 本次未获授权，未核验其生产实际结构，不能据此判断该表不存在 | Doris 目标 `dwd_member_info_fact_v1` 存在，29 个字段与代码列名一致 | 不影响交易金额事实表 |

## 17. 对 06-02 金额规则分析的影响评估

可以进入 06-02，范围限于生产中已经有分区行数的事实表：订单、明细、优惠、支付、充值、退款、退款明细、团购。这些表的本次列出的金额列都是 `bigint` 且与主脚本一致，分析时按整数分处理，不要改成 `DECIMAL` 元，也不要使用 `FLOAT`。

不要把以下对象当成金额样本或金额来源：

- 投诉表、结算分录表：表在，分区行数为 0。结算的 `amount` 类型已核对，但没有行。
- 六张 CDC 版本表：生产中没有这些表。统一金额在 `dwd_trade_*_fact_v1`，不在版本表。
- `dim_jd_account_anchor`、`dim_jd_account_binding`、`trade_ssm_group_sync_checkpoint`：缺失不改变金额列。
- 外部 `member_info`：未授权，与金额列无关。

结构差异里需要 06-02 避开或单独说明的只有：订单和退款的状态默认值与脚本不同，读数应以生产列值和 current 视图的 `COALESCE` 为准；订单明细少一个门店倒排索引，影响的是过滤方式，不是金额类型。

本报告不给出金额公式。

## 18. 调查结论

1. 本次 MySQL 是 `8.0.45`，实例内网地址 `172.21.0.7`。Query 配置直接指向它。Analytics 和 Ingress 的 `127.0.0.1` 仍是配置推断。
2. `trade_flow` 与当前初始化脚本没有实质差异。200 张 Storage 分表结构一致。
3. `trade_analytics` 缺少 3 张脚本表，并多出脚本没有的 `dim_region`。其余 42 张表未发现实质字段差异。
4. Doris 产品版本是 `doris-4.0.7-rc02-35854e7e92a`。`5.7.99` 不是产品版本。`trade_warehouse` 有 26 张基表和 11 个视图，全部基表为 `UNIQUE KEY`。
5. 生产 Doris 更接近主初始化脚本。辅助脚本更旧。两边都没有逐字描述生产：状态列默认值、`brand_id` 可空和明细门店倒排索引是生产与主脚本的差异。
6. 代码中的六张版本表和任务清单中的另外六个名字都不在生产库。错误是 `Unknown table`。
7. 列出的金额字段在生产中存在，类型与主脚本一致，没有使用 `FLOAT` 或 `DOUBLE`。
8. 订单到团购的事实表有分区数据。投诉和结算没有。

## 19. 后续调查建议

建议进入 06-02，只分析第 17 章列出的有数据事实表，并按整数分解释 `bigint` 金额列。进入前不需要先补 CDC 版本表或三张缺失的 MySQL 表。

若后续要核对外会员源，使用已授权的 `cdc_mms_ro` 连接 `172.21.0.9`，不要更换账号。若要确认 Analytics 的 `127.0.0.1` 就是本实例，需要看运行进程的监听关系，本次没有做。本次到此停止，不开始 06-02，不修改数据库和工程文件。

## 附录 A：MySQL 差异明细

实质差异只有第 6 章的 4 行。`trade_flow` 无实质差异。

同义写法，避免以后被当成类型漂移：

| 对象 | 脚本写法 | 生产 information_schema | 结论 |
| --- | --- | --- | --- |
| 商品维表 `is_deleted`、`is_default_selected` | `BOOLEAN` | `tinyint(1)` | MySQL 的 BOOLEAN 就是 TINYINT(1) |
| `dim_brand.active_brand_name` 等生成列 | `IF(is_deleted = 0, 列, NULL)` | 带括号的同一表达式 | 规范形式 |
| 回灌 `CHECK` | `OCTET_LENGTH` | `LENGTH` | 对非二进制字符串二者同义 |
| 补拉与查询事件的函数唯一索引 | 脚本中的 `CASE` | `information_schema` 中的完整 `CASE` | 同一索引 |
| `trade_analysis_fact_write_task_index.idx_fact_recovery_exact` | `` `last_error`(64) `` | 列 `last_error`，`SUB_PART=64` | 同一前缀索引 |

字段顺序：共有表未发现与脚本不一致。等级：生产已确认。

## 附录 B：Doris SHOW CREATE TABLE 原始结果

以下每段是基表 `SHOW CREATE TABLE` 的完整第二列。视图不在这里：对视图执行该语句被拒绝，错误见附录 D。

### `dim_fuiou_shop_info`

```sql
CREATE TABLE `dim_fuiou_shop_info` (
  `shop_id` bigint NOT NULL COMMENT "第三方门店ID",
  `shop_code` varchar(64) NOT NULL DEFAULT "" COMMENT "门店编码",
  `shop_name` varchar(128) NOT NULL DEFAULT "" COMMENT "门店名称",
  `shop_type` varchar(8) NOT NULL DEFAULT "" COMMENT "门店类型",
  `open_state` varchar(64) NULL COMMENT "第三方原始营业状态；仅留痕，不作为查询过滤条件",
  `mchnt_cd` varchar(32) NOT NULL DEFAULT "" COMMENT "商户编码",
  `term_user_cd` varchar(32) NOT NULL DEFAULT "" COMMENT "终端用户编码",
  `phone` varchar(32) NOT NULL DEFAULT "" COMMENT "联系电话",
  `address` varchar(256) NOT NULL DEFAULT "" COMMENT "门店地址",
  `prov_cd` varchar(16) NOT NULL DEFAULT "" COMMENT "省编码",
  `region_cd` varchar(16) NOT NULL DEFAULT "" COMMENT "区域编码",
  `county_cd` varchar(16) NOT NULL DEFAULT "" COMMENT "区县编码",
  `city_name` varchar(64) NOT NULL DEFAULT "" COMMENT "城市名称",
  `gitu` varchar(32) NOT NULL DEFAULT "" COMMENT "经度",
  `titu` varchar(32) NOT NULL DEFAULT "" COMMENT "纬度",
  `eat_in_open_time` varchar(64) NOT NULL DEFAULT "" COMMENT "堂食营业时间",
  `take_out_open_time` varchar(64) NOT NULL DEFAULT "" COMMENT "外卖营业时间",
  `active_tm` datetime(3) NULL COMMENT "激活时间",
  `create_tm` datetime(3) NULL COMMENT "第三方创建时间",
  `update_tm` datetime(3) NULL COMMENT "第三方更新时间",
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT "本地记录创建时间",
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT "本地记录更新时间",
  `del_flag` tinyint NOT NULL DEFAULT "0" COMMENT "逻辑删除：0=有效，1=已废弃"
) ENGINE=OLAP
UNIQUE KEY(`shop_id`)
DISTRIBUTED BY HASH(`shop_id`) BUCKETS 4
PROPERTIES (
"replication_allocation" = "tag.location.default: 1",
"min_load_replica_num" = "-1",
"is_being_synced" = "false",
"storage_medium" = "hdd",
"storage_format" = "V2",
"inverted_index_storage_format" = "V3",
"enable_unique_key_merge_on_write" = "true",
"light_schema_change" = "true",
"disable_auto_compaction" = "false",
"binlog.enable" = "true",
"binlog.ttl_seconds" = "604800",
"binlog.max_bytes" = "1073741824",
"binlog.max_history_nums" = "9223372036854775807",
"enable_single_replica_compaction" = "false",
"group_commit_interval_ms" = "10000",
"group_commit_data_bytes" = "134217728",
"enable_mow_light_delete" = "false"
);
```

### `dim_goods_category_v1`

```sql
CREATE TABLE `dim_goods_category_v1` (
  `internal_category_id` bigint NOT NULL COMMENT "内部标准分类ID",
  `brand_id` bigint NULL COMMENT "内部品牌ID，关联MySQL dim_brand.brand_id",
  `category_name` varchar(256) NOT NULL COMMENT "总店标准分类名称",
  `category_type` varchar(32) NOT NULL DEFAULT "NORMAL" COMMENT "分类类型：NORMAL普通分类；SYSTEM_OTHER租户系统兜底分类",
  `sort_order` int NULL,
  `is_deleted` boolean NOT NULL DEFAULT "0",
  `meta_process_time` datetime(3) NOT NULL,
  `meta_sink_sequence` bigint NOT NULL
) ENGINE=OLAP
UNIQUE KEY(`internal_category_id`)
COMMENT '商品域标准分类当前维表 v1'
DISTRIBUTED BY HASH(`internal_category_id`) BUCKETS 4
PROPERTIES (
"replication_allocation" = "tag.location.default: 1",
"min_load_replica_num" = "-1",
"is_being_synced" = "false",
"storage_medium" = "hdd",
"storage_format" = "V2",
"inverted_index_storage_format" = "V3",
"enable_unique_key_merge_on_write" = "true",
"light_schema_change" = "true",
"function_column.sequence_col" = "meta_sink_sequence",
"disable_auto_compaction" = "false",
"binlog.enable" = "true",
"binlog.ttl_seconds" = "604800",
"binlog.max_bytes" = "1073741824",
"binlog.max_history_nums" = "9223372036854775807",
"enable_single_replica_compaction" = "false",
"group_commit_interval_ms" = "10000",
"group_commit_data_bytes" = "134217728",
"enable_mow_light_delete" = "false"
);
```

### `dim_goods_platform_catalog_v1`

```sql
CREATE TABLE `dim_goods_platform_catalog_v1` (
  `brand_id` bigint NOT NULL COMMENT "品牌ID",
  `source_system` varchar(32) NOT NULL COMMENT "来源系统：FUIOU/TAOBAO/MEITUAN/JD",
  `source_merchant_code` varchar(64) NOT NULL COMMENT "来源账号",
  `source_shop_id` varchar(128) NOT NULL COMMENT "来源门店ID",
  `id_namespace` varchar(32) NOT NULL COMMENT "平台编号类型",
  `source_id` varchar(128) NOT NULL COMMENT "该类型的平台编号",
  `parent_id_namespace` varchar(32) NULL COMMENT "父商品编号类型",
  `parent_source_id` varchar(128) NULL COMMENT "父商品编号",
  `product_name` varchar(512) NULL COMMENT "官方商品名称",
  `spec_name` varchar(512) NULL COMMENT "官方规格名称",
  `attributes_json` text NULL COMMENT "其他官方原始编号及展示属性JSON",
  `is_deleted` boolean NOT NULL DEFAULT "0" COMMENT "目录节点状态：0有效/1关闭",
  `catalog_round` varchar(128) NOT NULL COMMENT "目录轮次标识",
  `meta_process_time` datetime(3) NOT NULL,
  `meta_sink_sequence` bigint NOT NULL
) ENGINE=OLAP
UNIQUE KEY(`brand_id`, `source_system`, `source_merchant_code`, `source_shop_id`, `id_namespace`, `source_id`)
COMMENT '平台商品与规格目录节点 v1'
DISTRIBUTED BY HASH(`brand_id`, `source_merchant_code`, `source_shop_id`) BUCKETS 16
PROPERTIES (
"replication_allocation" = "tag.location.default: 1",
"min_load_replica_num" = "-1",
"is_being_synced" = "false",
"storage_medium" = "hdd",
"storage_format" = "V2",
"inverted_index_storage_format" = "V3",
"enable_unique_key_merge_on_write" = "true",
"light_schema_change" = "true",
"function_column.sequence_col" = "meta_sink_sequence",
"disable_auto_compaction" = "false",
"binlog.enable" = "true",
"binlog.ttl_seconds" = "604800",
"binlog.max_bytes" = "1073741824",
"binlog.max_history_nums" = "9223372036854775807",
"enable_single_replica_compaction" = "false",
"group_commit_interval_ms" = "10000",
"group_commit_data_bytes" = "134217728",
"enable_mow_light_delete" = "false"
);
```

### `dim_goods_product_package_rel_v1`

```sql
CREATE TABLE `dim_goods_product_package_rel_v1` (
  `source_system` varchar(32) NOT NULL,
  `source_merchant_code` varchar(64) NOT NULL,
  `source_shop_id` varchar(128) NOT NULL COMMENT "套餐所属来源门店，按平台原值保存，0为总店",
  `source_package_product_id` varchar(128) NOT NULL,
  `option_index` int NOT NULL,
  `goods_index` int NOT NULL,
  `source_component_product_id` varchar(128) NOT NULL,
  `package_internal_product_id` bigint NULL,
  `component_internal_product_id` bigint NULL,
  `component_product_name` varchar(512) NULL,
  `combine_desc` varchar(256) NULL,
  `package_type` varchar(16) NULL,
  `goods_number` decimal(18,3) NULL,
  `min_number` int NULL,
  `option_number` int NULL,
  `is_default_selected` boolean NOT NULL DEFAULT "0",
  `up_price_amount` bigint NOT NULL DEFAULT "0" COMMENT "加价金额，分",
  `is_deleted` boolean NOT NULL DEFAULT "0" COMMENT "逻辑删除：0=有效，1=已废弃",
  `meta_process_time` datetime(3) NOT NULL,
  `meta_sink_sequence` bigint NOT NULL
) ENGINE=OLAP
UNIQUE KEY(`source_system`, `source_merchant_code`, `source_shop_id`, `source_package_product_id`, `option_index`, `goods_index`, `source_component_product_id`)
COMMENT '来源门店套餐当前配置关系 v1'
DISTRIBUTED BY HASH(`source_merchant_code`, `source_shop_id`) BUCKETS 16
PROPERTIES (
"replication_allocation" = "tag.location.default: 1",
"min_load_replica_num" = "-1",
"is_being_synced" = "false",
"storage_medium" = "hdd",
"storage_format" = "V2",
"inverted_index_storage_format" = "V3",
"enable_unique_key_merge_on_write" = "true",
"light_schema_change" = "true",
"function_column.sequence_col" = "meta_sink_sequence",
"disable_auto_compaction" = "false",
"binlog.enable" = "true",
"binlog.ttl_seconds" = "604800",
"binlog.max_bytes" = "1073741824",
"binlog.max_history_nums" = "9223372036854775807",
"enable_single_replica_compaction" = "false",
"group_commit_interval_ms" = "10000",
"group_commit_data_bytes" = "134217728",
"enable_mow_light_delete" = "false"
);
```

### `dim_goods_product_source_v1`

```sql
CREATE TABLE `dim_goods_product_source_v1` (
  `source_system` varchar(32) NOT NULL COMMENT "来源系统，如FUIOU/TAOBAO/MEITUAN",
  `source_merchant_code` varchar(64) NOT NULL COMMENT "来源商户标识，如mchntCd/appKey/app_id",
  `source_shop_id` varchar(128) NOT NULL COMMENT "来源门店ID，按平台原值保存，0为总店",
  `source_product_id` varchar(128) NOT NULL COMMENT "来源商品ID，如goodsId/app_food_code",
  `internal_product_id` bigint NULL COMMENT "映射后的内部标准商品ID；UNMAPPED时为空",
  `source_product_name` varchar(512) NULL COMMENT "该门店来源商品名称",
  `mapping_type` varchar(32) NOT NULL COMMENT "HEAD/RELATE/NAME/INDEPENDENT/MANUAL/UNMAPPED",
  `is_deleted` boolean NOT NULL DEFAULT "0" COMMENT "逻辑删除：0=有效，1=已废弃",
  `meta_process_time` datetime(3) NOT NULL,
  `meta_sink_sequence` bigint NOT NULL
) ENGINE=OLAP
UNIQUE KEY(`source_system`, `source_merchant_code`, `source_shop_id`, `source_product_id`)
COMMENT '来源门店商品到内部标准商品映射表 v1'
DISTRIBUTED BY HASH(`source_merchant_code`, `source_shop_id`) BUCKETS 16
PROPERTIES (
"replication_allocation" = "tag.location.default: 1",
"min_load_replica_num" = "-1",
"is_being_synced" = "false",
"storage_medium" = "hdd",
"storage_format" = "V2",
"inverted_index_storage_format" = "V3",
"enable_unique_key_merge_on_write" = "true",
"light_schema_change" = "true",
"function_column.sequence_col" = "meta_sink_sequence",
"disable_auto_compaction" = "false",
"binlog.enable" = "true",
"binlog.ttl_seconds" = "604800",
"binlog.max_bytes" = "1073741824",
"binlog.max_history_nums" = "9223372036854775807",
"enable_single_replica_compaction" = "false",
"group_commit_interval_ms" = "10000",
"group_commit_data_bytes" = "134217728",
"enable_mow_light_delete" = "false"
);
```

### `dim_goods_product_v1`

```sql
CREATE TABLE `dim_goods_product_v1` (
  `internal_product_id` bigint NOT NULL COMMENT "内部标准商品ID",
  `brand_id` bigint NULL COMMENT "内部品牌ID，关联MySQL dim_brand.brand_id",
  `internal_category_id` bigint NULL COMMENT "内部标准分类ID",
  `product_name` varchar(512) NOT NULL COMMENT "总店标准商品名称",
  `product_type` varchar(32) NULL COMMENT "NORMAL/PACKAGE",
  `stock_unit` varchar(32) NULL COMMENT "标准销售单位",
  `product_status` varchar(32) NULL COMMENT "总店商品状态",
  `is_deleted` boolean NOT NULL DEFAULT "0",
  `meta_process_time` datetime(3) NOT NULL,
  `meta_sink_sequence` bigint NOT NULL
) ENGINE=OLAP
UNIQUE KEY(`internal_product_id`)
COMMENT '商品域标准商品当前维表 v1'
DISTRIBUTED BY HASH(`internal_product_id`) BUCKETS 8
PROPERTIES (
"replication_allocation" = "tag.location.default: 1",
"min_load_replica_num" = "-1",
"is_being_synced" = "false",
"storage_medium" = "hdd",
"storage_format" = "V2",
"inverted_index_storage_format" = "V3",
"enable_unique_key_merge_on_write" = "true",
"light_schema_change" = "true",
"function_column.sequence_col" = "meta_sink_sequence",
"disable_auto_compaction" = "false",
"binlog.enable" = "true",
"binlog.ttl_seconds" = "604800",
"binlog.max_bytes" = "1073741824",
"binlog.max_history_nums" = "9223372036854775807",
"enable_single_replica_compaction" = "false",
"group_commit_interval_ms" = "10000",
"group_commit_data_bytes" = "134217728",
"enable_mow_light_delete" = "false"
);
```

### `dim_jd_shop_info`

```sql
CREATE TABLE `dim_jd_shop_info` (
  `brand_id` bigint NOT NULL,
  `platform_shop_id` varchar(128) NOT NULL COMMENT "京东原始stationNo",
  `app_key` varchar(128) NULL COMMENT "最近一次验证账号血缘",
  `vender_id` varchar(128) NULL COMMENT "最近一次验证商家编码血缘",
  `vender_name` varchar(256) NULL,
  `shop_name` varchar(256) NULL,
  `latitude` decimal(18,8) NULL,
  `longitude` decimal(18,8) NULL,
  `province_code` varchar(32) NULL,
  `province_name` varchar(128) NULL,
  `city_code` varchar(32) NULL,
  `city_name` varchar(128) NULL,
  `county_code` varchar(32) NULL,
  `county_name` varchar(128) NULL,
  `station_address` varchar(1024) NULL,
  `phone` varchar(64) NULL,
  `mobile` varchar(64) NULL,
  `enabled_status` int NULL,
  `close_status` int NULL,
  `identity_status` varchar(16) NULL COMMENT "来源核验状态",
  `region_resolution_status` varchar(16) NULL,
  `region_resolution_method` varchar(32) NULL,
  `source_updated_time` datetime(3) NULL,
  `updated_time` datetime(3) NULL
) ENGINE=OLAP
UNIQUE KEY(`brand_id`, `platform_shop_id`)
DISTRIBUTED BY HASH(`brand_id`, `platform_shop_id`) BUCKETS 4
PROPERTIES (
"replication_allocation" = "tag.location.default: 1",
"min_load_replica_num" = "-1",
"is_being_synced" = "false",
"storage_medium" = "hdd",
"storage_format" = "V2",
"inverted_index_storage_format" = "V3",
"enable_unique_key_merge_on_write" = "true",
"light_schema_change" = "true",
"disable_auto_compaction" = "false",
"binlog.enable" = "true",
"binlog.ttl_seconds" = "604800",
"binlog.max_bytes" = "1073741824",
"binlog.max_history_nums" = "9223372036854775807",
"enable_single_replica_compaction" = "false",
"group_commit_interval_ms" = "10000",
"group_commit_data_bytes" = "134217728",
"enable_mow_light_delete" = "false"
);
```

### `dim_meituan_shop_info`

```sql
CREATE TABLE `dim_meituan_shop_info` (
  `brand_id` bigint NOT NULL COMMENT "租户品牌ID",
  `app_poi_code` varchar(128) NOT NULL COMMENT "美团原始app_poi_code",
  `app_id` varchar(128) NULL COMMENT "美团开放平台应用ID",
  `shop_name` varchar(256) NULL,
  `address` varchar(1024) NULL,
  `phone` varchar(64) NULL,
  `latitude` double NULL,
  `longitude` double NULL,
  `is_online` int NULL,
  `source_utime` bigint NULL,
  `province_code` varchar(16) NULL,
  `city_code` varchar(16) NULL,
  `district_code` varchar(16) NULL,
  `region_resolution_status` varchar(16) NULL COMMENT "RESOLVED/PARTIAL/UNRESOLVED/AMBIGUOUS",
  `region_resolution_method` varchar(32) NULL COMMENT "ADDRESS/SSM_PLACEHOLDER",
  `updated_time` datetime(3) NULL,
  `wm_poi_id` varchar(128) NULL COMMENT "美团平台门店ID"
) ENGINE=OLAP
UNIQUE KEY(`brand_id`, `app_poi_code`)
DISTRIBUTED BY HASH(`brand_id`, `app_poi_code`) BUCKETS 4
PROPERTIES (
"replication_allocation" = "tag.location.default: 1",
"min_load_replica_num" = "-1",
"is_being_synced" = "false",
"storage_medium" = "hdd",
"storage_format" = "V2",
"inverted_index_storage_format" = "V3",
"enable_unique_key_merge_on_write" = "true",
"light_schema_change" = "true",
"disable_auto_compaction" = "false",
"binlog.enable" = "true",
"binlog.ttl_seconds" = "604800",
"binlog.max_bytes" = "1073741824",
"binlog.max_history_nums" = "9223372036854775807",
"enable_single_replica_compaction" = "false",
"group_commit_interval_ms" = "10000",
"group_commit_data_bytes" = "134217728",
"enable_mow_light_delete" = "false"
);
```

### `dim_region`

```sql
CREATE TABLE `dim_region` (
  `code` char(6) NOT NULL COMMENT "行政区划编码（国标6位）",
  `name` varchar(50) NOT NULL DEFAULT "" COMMENT "名称",
  `level` tinyint NOT NULL COMMENT "1-省 2-市 3-区/县",
  `parent_code` char(6) NULL COMMENT "上级编码",
  `deleted` tinyint NOT NULL DEFAULT "0" COMMENT "是否删除(0未删除1已删除)",
  `create_time` datetime NULL COMMENT "创建时间",
  `update_time` datetime NULL COMMENT "更新时间"
) ENGINE=OLAP
UNIQUE KEY(`code`)
DISTRIBUTED BY HASH(`code`) BUCKETS 4
PROPERTIES (
"replication_allocation" = "tag.location.default: 1",
"min_load_replica_num" = "-1",
"is_being_synced" = "false",
"storage_medium" = "hdd",
"storage_format" = "V2",
"inverted_index_storage_format" = "V3",
"enable_unique_key_merge_on_write" = "true",
"light_schema_change" = "true",
"disable_auto_compaction" = "false",
"binlog.enable" = "true",
"binlog.ttl_seconds" = "604800",
"binlog.max_bytes" = "1073741824",
"binlog.max_history_nums" = "9223372036854775807",
"enable_single_replica_compaction" = "false",
"group_commit_interval_ms" = "10000",
"group_commit_data_bytes" = "134217728",
"enable_mow_light_delete" = "false"
);
```

### `dim_taobao_shop_info`

```sql
CREATE TABLE `dim_taobao_shop_info` (
  `brand_id` bigint NOT NULL COMMENT "租户品牌ID",
  `shop_id` varchar(128) NOT NULL COMMENT "淘宝原始门店ID",
  `app_id` varchar(128) NULL COMMENT "淘宝开放平台应用ID",
  `shop_name` varchar(256) NULL,
  `open_id` varchar(256) NULL,
  `address` varchar(1024) NULL,
  `province_name` varchar(128) NULL,
  `city_name` varchar(128) NULL,
  `district_name` varchar(128) NULL,
  `latitude` double NULL,
  `longitude` double NULL,
  `is_open` int NULL,
  `phones_json` varchar(1024) NULL,
  `province_code` varchar(16) NULL,
  `city_code` varchar(16) NULL,
  `district_code` varchar(16) NULL,
  `region_resolution_status` varchar(16) NULL COMMENT "RESOLVED/PARTIAL/UNRESOLVED/AMBIGUOUS",
  `region_resolution_method` varchar(32) NULL COMMENT "SOURCE_NAMES/ADDRESS/SSM_PLACEHOLDER",
  `updated_time` datetime(3) NULL
) ENGINE=OLAP
UNIQUE KEY(`brand_id`, `shop_id`)
DISTRIBUTED BY HASH(`brand_id`, `shop_id`) BUCKETS 4
PROPERTIES (
"replication_allocation" = "tag.location.default: 1",
"min_load_replica_num" = "-1",
"is_being_synced" = "false",
"storage_medium" = "hdd",
"storage_format" = "V2",
"inverted_index_storage_format" = "V3",
"enable_unique_key_merge_on_write" = "true",
"light_schema_change" = "true",
"disable_auto_compaction" = "false",
"binlog.enable" = "true",
"binlog.ttl_seconds" = "604800",
"binlog.max_bytes" = "1073741824",
"binlog.max_history_nums" = "9223372036854775807",
"enable_single_replica_compaction" = "false",
"group_commit_interval_ms" = "10000",
"group_commit_data_bytes" = "134217728",
"enable_mow_light_delete" = "false"
);
```

### `dim_trade_goods_group`

```sql
CREATE TABLE `dim_trade_goods_group` (
  `tenant_id` bigint NOT NULL COMMENT "SSM来源租户",
  `id` varchar(64) NOT NULL COMMENT "完整SHA256稳定规则自然键",
  `rule_version` bigint NOT NULL COMMENT "scope单调版本；仅作sequence不进入唯一键",
  `row_kind` varchar(8) NOT NULL COMMENT "ROOT/MEMBER",
  `group_key` varchar(512) NOT NULL COMMENT "来源公司根身份，保留原始字符串",
  `parent_id` varchar(64) NULL,
  `member_ordinal` int NULL,
  `platform_raw` varchar(128) NULL,
  `platform_code` varchar(16) NULL,
  `platform_shop_id` varchar(128) NULL,
  `brand_id` bigint NULL COMMENT "解析结果，不参与规则落库前置条件",
  `platform_source_app_id` varchar(128) NULL,
  `extra_fields` json NULL COMMENT "未知业务扩展，不是原HTTP正文",
  `rule_status` varchar(32) NOT NULL,
  `conflict_diagnostics` json NULL,
  `observed_time` datetime(3) NOT NULL,
  `origin` varchar(16) NOT NULL COMMENT "SSM/LEGACY",
  `internal_product_id` bigint NULL,
  `root_goods_name` varchar(512) NULL,
  `barcode` varchar(128) NULL,
  `bind_count` int NULL,
  `shop_name` varchar(256) NULL,
  `platform_product_id` varchar(128) NULL,
  `product_name` varchar(512) NULL
) ENGINE=OLAP
UNIQUE KEY(`tenant_id`, `id`)
DISTRIBUTED BY HASH(`tenant_id`, `id`) BUCKETS 16
PROPERTIES (
"replication_allocation" = "tag.location.default: 1",
"min_load_replica_num" = "-1",
"is_being_synced" = "false",
"storage_medium" = "hdd",
"storage_format" = "V2",
"inverted_index_storage_format" = "V3",
"enable_unique_key_merge_on_write" = "true",
"light_schema_change" = "true",
"function_column.sequence_col" = "rule_version",
"disable_auto_compaction" = "false",
"binlog.enable" = "true",
"binlog.ttl_seconds" = "604800",
"binlog.max_bytes" = "1073741824",
"binlog.max_history_nums" = "9223372036854775807",
"enable_single_replica_compaction" = "false",
"group_commit_interval_ms" = "10000",
"group_commit_data_bytes" = "134217728",
"enable_mow_light_delete" = "false"
);
```

### `dim_trade_shop_group`

```sql
CREATE TABLE `dim_trade_shop_group` (
  `brand_id` bigint NOT NULL COMMENT "交易品牌ID，门店归组的租户边界",
  `id` varchar(64) NOT NULL COMMENT "完整SHA256稳定规则自然键",
  `rule_version` bigint NOT NULL COMMENT "品牌内单调规则版本",
  `row_kind` varchar(8) NOT NULL COMMENT "ROOT/MEMBER",
  `group_key` varchar(512) NOT NULL COMMENT "公司根门店号",
  `parent_id` varchar(64) NULL,
  `member_ordinal` int NULL,
  `platform_raw` varchar(128) NULL,
  `platform_code` varchar(16) NULL,
  `platform_shop_id` varchar(128) NULL,
  `is_deleted` tinyint NOT NULL DEFAULT "0" COMMENT "逻辑删除标记：0=未删除，1=已删除",
  `platform_source_app_id` varchar(128) NULL,
  `extra_fields` json NULL,
  `rule_status` varchar(32) NOT NULL,
  `conflict_diagnostics` json NULL,
  `observed_time` datetime(3) NOT NULL,
  `origin` varchar(16) NOT NULL,
  `internal_shop_id` bigint NULL,
  `shop_name` varchar(256) NULL,
  `open_state` varchar(64) NULL
) ENGINE=OLAP
UNIQUE KEY(`brand_id`, `id`)
DISTRIBUTED BY HASH(`brand_id`, `id`) BUCKETS 16
PROPERTIES (
"replication_allocation" = "tag.location.default: 1",
"min_load_replica_num" = "-1",
"is_being_synced" = "false",
"storage_medium" = "hdd",
"storage_format" = "V2",
"inverted_index_storage_format" = "V3",
"enable_unique_key_merge_on_write" = "true",
"light_schema_change" = "true",
"function_column.sequence_col" = "rule_version",
"disable_auto_compaction" = "false",
"binlog.enable" = "true",
"binlog.ttl_seconds" = "604800",
"binlog.max_bytes" = "1073741824",
"binlog.max_history_nums" = "9223372036854775807",
"enable_single_replica_compaction" = "false",
"group_commit_interval_ms" = "10000",
"group_commit_data_bytes" = "134217728",
"enable_mow_light_delete" = "false"
);
```

### `dim_trade_shop_id_mapping`

```sql
CREATE TABLE `dim_trade_shop_id_mapping` (
  `internal_shop_id` bigint NOT NULL COMMENT "交易域门店ID，本表唯一主键",
  `platform_code` varchar(16) NOT NULL COMMENT "FZY/TAOBAO/MEITUAN/JD",
  `platform_shop_id` varchar(128) NOT NULL COMMENT "平台门店号",
  `platform_shop_name` varchar(256) NULL,
  `brand_id` bigint NULL,
  `effective_start_time` datetime(3) NOT NULL,
  `effective_end_time` datetime(3) NULL,
  `mapping_status` varchar(16) NULL,
  `mapping_version` bigint NULL,
  `updated_time` datetime(3) NULL
) ENGINE=OLAP
UNIQUE KEY(`internal_shop_id`)
DISTRIBUTED BY HASH(`internal_shop_id`) BUCKETS 4
PROPERTIES (
"replication_allocation" = "tag.location.default: 1",
"min_load_replica_num" = "-1",
"is_being_synced" = "false",
"storage_medium" = "hdd",
"storage_format" = "V2",
"inverted_index_storage_format" = "V3",
"enable_unique_key_merge_on_write" = "true",
"light_schema_change" = "true",
"disable_auto_compaction" = "false",
"binlog.enable" = "true",
"binlog.ttl_seconds" = "604800",
"binlog.max_bytes" = "1073741824",
"binlog.max_history_nums" = "9223372036854775807",
"enable_single_replica_compaction" = "false",
"group_commit_interval_ms" = "10000",
"group_commit_data_bytes" = "134217728",
"enable_mow_light_delete" = "false"
);
```

### `dim_unified_shop_wide`

```sql
CREATE TABLE `dim_unified_shop_wide` (
  `brand_id` bigint NOT NULL COMMENT "交易品牌ID，归组租户边界",
  `member_id` varchar(64) NOT NULL COMMENT "成员规则行ID，完整SHA256稳定键",
  `root_id` varchar(64) NOT NULL COMMENT "根规则行ID",
  `root_group_key` varchar(512) NOT NULL COMMENT "公司根身份，FZY店号",
  `root_shop_name` varchar(256) NULL,
  `root_internal_shop_id` bigint NULL COMMENT "仅唯一ACTIVE映射填充",
  `root_prov_cd` varchar(16) NULL,
  `root_prov_name` varchar(50) NULL,
  `root_region_cd` varchar(16) NULL,
  `root_region_name` varchar(50) NULL,
  `root_county_cd` varchar(16) NULL,
  `root_county_name` varchar(50) NULL,
  `member_platform_raw` varchar(128) NULL,
  `member_platform_code` varchar(16) NULL,
  `member_platform_shop_id` varchar(128) NULL,
  `member_internal_shop_id` bigint NULL COMMENT "仅唯一ACTIVE映射填充",
  `member_shop_name` varchar(256) NULL,
  `member_ordinal` int NULL,
  `build_time` datetime NULL COMMENT "最近一次重建写入时间"
) ENGINE=OLAP
UNIQUE KEY(`brand_id`, `member_id`)
DISTRIBUTED BY HASH(`brand_id`, `member_id`) BUCKETS 4
PROPERTIES (
"replication_allocation" = "tag.location.default: 1",
"min_load_replica_num" = "-1",
"is_being_synced" = "false",
"storage_medium" = "hdd",
"storage_format" = "V2",
"inverted_index_storage_format" = "V3",
"enable_unique_key_merge_on_write" = "true",
"light_schema_change" = "true",
"disable_auto_compaction" = "false",
"binlog.enable" = "true",
"binlog.ttl_seconds" = "604800",
"binlog.max_bytes" = "1073741824",
"binlog.max_history_nums" = "9223372036854775807",
"enable_single_replica_compaction" = "false",
"group_commit_interval_ms" = "10000",
"group_commit_data_bytes" = "134217728",
"enable_mow_light_delete" = "false"
);
```

### `dwd_member_info_fact_v1`

```sql
CREATE TABLE `dwd_member_info_fact_v1` (
  `id` bigint NOT NULL COMMENT "来源会员主键ID",
  `tenant_id` varchar(20) NULL COMMENT "来源租户编号",
  `open_id` varchar(64) NULL COMMENT "微信openId",
  `user_name` varchar(64) NULL COMMENT "昵称",
  `experience` int NULL COMMENT "经验值",
  `level_value` int NULL COMMENT "等级值",
  `sex` tinyint NULL COMMENT "性别：1男，2女，0未知",
  `birthday` varchar(32) NULL COMMENT "生日原文",
  `country` varchar(64) NULL COMMENT "国家",
  `province` varchar(64) NULL COMMENT "省份",
  `city` varchar(64) NULL COMMENT "城市",
  `sho_name` varchar(128) NULL COMMENT "会员发展门店",
  `regist_time` varchar(32) NULL COMMENT "注册时间原文",
  `total_consume_amt` bigint NULL COMMENT "消费总金额，单位分",
  `total_consume_sum` int NULL COMMENT "消费总笔数",
  `shop_id` varchar(32) NULL COMMENT "来源门店ID",
  `phone` varchar(20) NULL COMMENT "手机号",
  `promter_no` varchar(32) NULL COMMENT "推广员编号",
  `member_code` varchar(32) NULL COMMENT "会员动态码",
  `offline_card_no` varchar(64) NULL COMMENT "实体卡号",
  `level_name` varchar(64) NULL COMMENT "等级名称",
  `level_exp_date` varchar(32) NULL COMMENT "等级有效期原文",
  `deleted` tinyint NULL COMMENT "逻辑删除：0未删除，1已删除",
  `create_time` datetime(3) NULL COMMENT "创建时间",
  `update_time` datetime(3) NULL COMMENT "更新时间",
  `creator` varchar(64) NULL COMMENT "创建人",
  `modifier` varchar(64) NULL COMMENT "更新人",
  `growth_experience` bigint NULL COMMENT "成长值",
  `growth_grade_code` varchar(16) NULL COMMENT "成长等级编码"
) ENGINE=OLAP
UNIQUE KEY(`id`)
COMMENT '会员当前事实'
DISTRIBUTED BY HASH(`id`) BUCKETS 1
PROPERTIES (
"replication_allocation" = "tag.location.default: 1",
"min_load_replica_num" = "-1",
"is_being_synced" = "false",
"storage_medium" = "hdd",
"storage_format" = "V2",
"inverted_index_storage_format" = "V3",
"enable_unique_key_merge_on_write" = "true",
"light_schema_change" = "true",
"disable_auto_compaction" = "false",
"binlog.enable" = "true",
"binlog.ttl_seconds" = "604800",
"binlog.max_bytes" = "1073741824",
"binlog.max_history_nums" = "9223372036854775807",
"enable_single_replica_compaction" = "false",
"group_commit_interval_ms" = "10000",
"group_commit_data_bytes" = "134217728",
"enable_mow_light_delete" = "false"
);
```

### `dwd_store_member_info_fact`

```sql
CREATE TABLE `dwd_store_member_info_fact` (
  `brand_id` bigint NOT NULL COMMENT "内部品牌ID",
  `mchnt_cd` varchar(32) NOT NULL COMMENT "来源商户号",
  `open_id` varchar(128) NOT NULL COMMENT "来源会员OpenID",
  `tenant_id` varchar(32) NULL COMMENT "租户中心租户ID",
  `sex` text NULL COMMENT "性别原值",
  `level_exp` bigint NULL COMMENT "来源levelExp原值",
  `level_name` text NULL COMMENT "等级名称",
  `union_phone` text NULL COMMENT "来源unionPhone原值",
  `user_birth` text NULL COMMENT "生日原文",
  `user_name` text NULL COMMENT "用户名称",
  `point` text NULL COMMENT "可用积分原文",
  `offline_card_no` text NULL COMMENT "线下实体卡号",
  `table_name` text NULL COMMENT "来源tableName原值",
  `exp_days` bigint NULL COMMENT "来源expDays原值",
  `real_name` text NULL COMMENT "用户实名原值",
  `subscribe_date` date NULL COMMENT "注册日期",
  `phone` text NULL COMMENT "来源手机号原值",
  `all_phone` text NULL COMMENT "来源allPhone原值",
  `platform_shop_id` varchar(128) NULL COMMENT "来源门店号",
  `level_value` bigint NULL COMMENT "会员等级值",
  `update_type` text NULL COMMENT "来源updateType原值",
  `meta_sync_time` datetime(3) NOT NULL COMMENT "本行查询同步时间"
) ENGINE=OLAP
UNIQUE KEY(`brand_id`, `mchnt_cd`, `open_id`)
COMMENT '富智云门店会员原始资料事实'
DISTRIBUTED BY HASH(`brand_id`, `mchnt_cd`, `open_id`) BUCKETS 8
PROPERTIES (
"replication_allocation" = "tag.location.default: 1",
"min_load_replica_num" = "-1",
"is_being_synced" = "false",
"storage_medium" = "hdd",
"storage_format" = "V2",
"inverted_index_storage_format" = "V3",
"enable_unique_key_merge_on_write" = "true",
"light_schema_change" = "true",
"disable_auto_compaction" = "false",
"binlog.enable" = "true",
"binlog.ttl_seconds" = "604800",
"binlog.max_bytes" = "1073741824",
"binlog.max_history_nums" = "9223372036854775807",
"enable_single_replica_compaction" = "false",
"group_commit_interval_ms" = "10000",
"group_commit_data_bytes" = "134217728",
"enable_mow_light_delete" = "false"
);
```

### `dwd_trade_complaint_fact_v1`

```sql
CREATE TABLE `dwd_trade_complaint_fact_v1` (
  `complaint_created_date` date NOT NULL COMMENT "投诉创建日期，稳定分区键",
  `platform_code` varchar(16) NOT NULL COMMENT "平台编码",
  `complaint_id` varchar(128) NOT NULL COMMENT "投诉单号",
  `platform_order_id` varchar(128) NOT NULL COMMENT "平台订单号",
  `internal_shop_id` bigint NULL COMMENT "跨服务商稳定的统一门店ID",
  `brand_id` bigint NULL COMMENT "内部品牌ID",
  `complaint_status` varchar(32) NOT NULL COMMENT "标准投诉状态",
  `complaint_type` varchar(64) NULL COMMENT "投诉类型",
  `complaint_reason_code` varchar(64) NULL COMMENT "投诉原因编码",
  `complaint_reason` varchar(1024) NULL COMMENT "投诉原因说明",
  `accepted_time` datetime(3) NULL COMMENT "商家接单时间",
  `resolved_time` datetime(3) NULL COMMENT "问题解决时间",
  `closed_time` datetime(3) NULL COMMENT "关闭时间",
  `handling_result` varchar(1024) NULL COMMENT "投诉处理结果",
  `meta_canonical_schema_version` varchar(16) NOT NULL COMMENT "Canonical模型版本",
  `meta_transform_version` varchar(64) NOT NULL COMMENT "转换逻辑版本",
  `meta_process_time` datetime(3) NOT NULL COMMENT "事实处理时间",
  `meta_sink_sequence` bigint NOT NULL COMMENT "Doris写入顺序版本",
  `src_updated_time` datetime(3) NULL COMMENT "来源对象更新时间",
  `src_version` bigint NOT NULL COMMENT "当前事实来源版本",
  `src_last_event_id` bigint NOT NULL COMMENT "最后写入的来源事件ID",
  `src_last_storage_id` bigint NOT NULL COMMENT "最后写入事件的Storage ID",
  `src_last_payload_sha256` char(64) NOT NULL COMMENT "最后写入原文SHA-256",
  `src_event_received_time` datetime(3) NOT NULL COMMENT "事件接收时间"
) ENGINE=OLAP
UNIQUE KEY(`complaint_created_date`, `platform_code`, `complaint_id`)
COMMENT '统一投诉当前事实物理表 v1'
PARTITION BY RANGE(`complaint_created_date`)
(PARTITION p202110 VALUES [('2021-10-01'), ('2021-11-01')),
PARTITION p202111 VALUES [('2021-11-01'), ('2021-12-01')),
PARTITION p202112 VALUES [('2021-12-01'), ('2022-01-01')),
PARTITION p202201 VALUES [('2022-01-01'), ('2022-02-01')),
PARTITION p202202 VALUES [('2022-02-01'), ('2022-03-01')),
PARTITION p202203 VALUES [('2022-03-01'), ('2022-04-01')),
PARTITION p202204 VALUES [('2022-04-01'), ('2022-05-01')),
PARTITION p202205 VALUES [('2022-05-01'), ('2022-06-01')),
PARTITION p202206 VALUES [('2022-06-01'), ('2022-07-01')),
PARTITION p202207 VALUES [('2022-07-01'), ('2022-08-01')),
PARTITION p202208 VALUES [('2022-08-01'), ('2022-09-01')),
PARTITION p202209 VALUES [('2022-09-01'), ('2022-10-01')),
PARTITION p202210 VALUES [('2022-10-01'), ('2022-11-01')),
PARTITION p202211 VALUES [('2022-11-01'), ('2022-12-01')),
PARTITION p202212 VALUES [('2022-12-01'), ('2023-01-01')),
PARTITION p202301 VALUES [('2023-01-01'), ('2023-02-01')),
PARTITION p202302 VALUES [('2023-02-01'), ('2023-03-01')),
PARTITION p202303 VALUES [('2023-03-01'), ('2023-04-01')),
PARTITION p202304 VALUES [('2023-04-01'), ('2023-05-01')),
PARTITION p202305 VALUES [('2023-05-01'), ('2023-06-01')),
PARTITION p202306 VALUES [('2023-06-01'), ('2023-07-01')),
PARTITION p202307 VALUES [('2023-07-01'), ('2023-08-01')),
PARTITION p202308 VALUES [('2023-08-01'), ('2023-09-01')),
PARTITION p202309 VALUES [('2023-09-01'), ('2023-10-01')),
PARTITION p202310 VALUES [('2023-10-01'), ('2023-11-01')),
PARTITION p202311 VALUES [('2023-11-01'), ('2023-12-01')),
PARTITION p202312 VALUES [('2023-12-01'), ('2024-01-01')),
PARTITION p202401 VALUES [('2024-01-01'), ('2024-02-01')),
PARTITION p202402 VALUES [('2024-02-01'), ('2024-03-01')),
PARTITION p202403 VALUES [('2024-03-01'), ('2024-04-01')),
PARTITION p202404 VALUES [('2024-04-01'), ('2024-05-01')),
PARTITION p202405 VALUES [('2024-05-01'), ('2024-06-01')),
PARTITION p202406 VALUES [('2024-06-01'), ('2024-07-01')),
PARTITION p202407 VALUES [('2024-07-01'), ('2024-08-01')),
PARTITION p202408 VALUES [('2024-08-01'), ('2024-09-01')),
PARTITION p202409 VALUES [('2024-09-01'), ('2024-10-01')),
PARTITION p202410 VALUES [('2024-10-01'), ('2024-11-01')),
PARTITION p202411 VALUES [('2024-11-01'), ('2024-12-01')),
PARTITION p202412 VALUES [('2024-12-01'), ('2025-01-01')),
PARTITION p202501 VALUES [('2025-01-01'), ('2025-02-01')),
PARTITION p202502 VALUES [('2025-02-01'), ('2025-03-01')),
PARTITION p202503 VALUES [('2025-03-01'), ('2025-04-01')),
PARTITION p202504 VALUES [('2025-04-01'), ('2025-05-01')),
PARTITION p202505 VALUES [('2025-05-01'), ('2025-06-01')),
PARTITION p202506 VALUES [('2025-06-01'), ('2025-07-01')),
PARTITION p202507 VALUES [('2025-07-01'), ('2025-08-01')),
PARTITION p202508 VALUES [('2025-08-01'), ('2025-09-01')),
PARTITION p202509 VALUES [('2025-09-01'), ('2025-10-01')),
PARTITION p202510 VALUES [('2025-10-01'), ('2025-11-01')),
PARTITION p202511 VALUES [('2025-11-01'), ('2025-12-01')),
PARTITION p202512 VALUES [('2025-12-01'), ('2026-01-01')),
PARTITION p202601 VALUES [('2026-01-01'), ('2026-02-01')),
PARTITION p202602 VALUES [('2026-02-01'), ('2026-03-01')),
PARTITION p202603 VALUES [('2026-03-01'), ('2026-04-01')),
PARTITION p202604 VALUES [('2026-04-01'), ('2026-05-01')),
PARTITION p202605 VALUES [('2026-05-01'), ('2026-06-01')),
PARTITION p202606 VALUES [('2026-06-01'), ('2026-07-01')),
PARTITION p202607 VALUES [('2026-07-01'), ('2026-08-01')),
PARTITION p202608 VALUES [('2026-08-01'), ('2026-09-01')),
PARTITION p202609 VALUES [('2026-09-01'), ('2026-10-01')),
PARTITION p202610 VALUES [('2026-10-01'), ('2026-11-01')),
PARTITION p202611 VALUES [('2026-11-01'), ('2026-12-01')),
PARTITION p202612 VALUES [('2026-12-01'), ('2027-01-01')),
PARTITION p202701 VALUES [('2027-01-01'), ('2027-02-01')),
PARTITION p202702 VALUES [('2027-02-01'), ('2027-03-01')),
PARTITION p202703 VALUES [('2027-03-01'), ('2027-04-01')),
PARTITION p202704 VALUES [('2027-04-01'), ('2027-05-01')),
PARTITION p202705 VALUES [('2027-05-01'), ('2027-06-01')),
PARTITION p202706 VALUES [('2027-06-01'), ('2027-07-01')),
PARTITION p202707 VALUES [('2027-07-01'), ('2027-08-01')),
PARTITION p202708 VALUES [('2027-08-01'), ('2027-09-01')),
PARTITION p202709 VALUES [('2027-09-01'), ('2027-10-01')),
PARTITION p202710 VALUES [('2027-10-01'), ('2027-11-01')),
PARTITION p202711 VALUES [('2027-11-01'), ('2027-12-01')))
DISTRIBUTED BY HASH(`platform_code`, `complaint_id`) BUCKETS 8
PROPERTIES (
"replication_allocation" = "tag.location.default: 1",
"min_load_replica_num" = "-1",
"is_being_synced" = "false",
"dynamic_partition.enable" = "true",
"dynamic_partition.time_unit" = "MONTH",
"dynamic_partition.time_zone" = "Asia/Shanghai",
"dynamic_partition.start" = "-2147483648",
"dynamic_partition.end" = "12",
"dynamic_partition.prefix" = "p",
"dynamic_partition.replication_allocation" = "tag.location.default: 1",
"dynamic_partition.buckets" = "8",
"dynamic_partition.create_history_partition" = "false",
"dynamic_partition.history_partition_num" = "-1",
"dynamic_partition.hot_partition_num" = "0",
"dynamic_partition.reserved_history_periods" = "NULL",
"dynamic_partition.storage_policy" = "",
"dynamic_partition.start_day_of_month" = "1",
"storage_medium" = "hdd",
"storage_format" = "V2",
"inverted_index_storage_format" = "V3",
"enable_unique_key_merge_on_write" = "true",
"light_schema_change" = "true",
"function_column.sequence_col" = "meta_sink_sequence",
"disable_auto_compaction" = "false",
"binlog.enable" = "true",
"binlog.ttl_seconds" = "604800",
"binlog.max_bytes" = "1073741824",
"binlog.max_history_nums" = "9223372036854775807",
"enable_single_replica_compaction" = "false",
"group_commit_interval_ms" = "10000",
"group_commit_data_bytes" = "134217728",
"enable_mow_light_delete" = "false"
);
```

### `dwd_trade_group_buy_verification_fact_v1`

```sql
CREATE TABLE `dwd_trade_group_buy_verification_fact_v1` (
  `brand_id` bigint NOT NULL COMMENT "内部品牌ID",
  `coupon_info_id` bigint NOT NULL COMMENT "券实例ID",
  `check_month` date NOT NULL COMMENT "核销月份，月初日期",
  `channel_desc` text NULL COMMENT "渠道名称",
  `channel_type` text NULL COMMENT "渠道类型ID",
  `check_tm` text NULL COMMENT "核销时间原文",
  `check_pay_num` bigint NULL COMMENT "核销张数",
  `check_pay_times` bigint NULL COMMENT "核销次数",
  `buy_price_amt` decimal(24,6) NULL COMMENT "券购买价，单位分",
  `coupon_amt` decimal(24,6) NULL COMMENT "商家优惠，单位分",
  `coupon_type` text NULL COMMENT "券类型",
  `face_amt` decimal(24,6) NULL COMMENT "券面值，单位分",
  `face_value` decimal(24,6) NULL COMMENT "实际抵扣金额，单位分",
  `group_buy_code` text NULL COMMENT "券码",
  `group_buy_id` text NULL COMMENT "团购ID",
  `group_buy_name` text NULL COMMENT "券名称",
  `group_buy_type` text NULL COMMENT "团购类型ID",
  `group_buy_type_desc` text NULL COMMENT "团购类型名",
  `mchnt_income` decimal(24,6) NULL COMMENT "商家总收入，单位分",
  `platform_order_id` varchar(128) NULL COMMENT "平台订单号",
  `plat_hong_bao` decimal(24,6) NULL COMMENT "平台优惠，单位分",
  `result` text NULL COMMENT "核销结果原值",
  `platform_shop_id` varchar(128) NULL COMMENT "平台门店号",
  `platform_shop_name` text NULL COMMENT "平台门店名称",
  `third_mchnt_income` decimal(24,6) NULL COMMENT "商家应得，单位分",
  `third_product_id` text NULL COMMENT "券项目ID",
  `third_service_fee` decimal(24,6) NULL COMMENT "服务费，单位分",
  `trans_id` text NULL COMMENT "交易ID",
  `internal_shop_id` bigint NULL COMMENT "跨服务商稳定的统一门店ID"
) ENGINE=OLAP
UNIQUE KEY(`brand_id`, `coupon_info_id`, `check_month`)
COMMENT '富智云券核销接口原始明细'
AUTO PARTITION BY RANGE (date_trunc(`check_month`, 'month'))
(PARTITION p20260801000000 VALUES [('2026-08-01'), ('2026-09-01')),
PARTITION p20260901000000 VALUES [('2026-09-01'), ('2026-10-01')),
PARTITION p20261001000000 VALUES [('2026-10-01'), ('2026-11-01')),
PARTITION p20261101000000 VALUES [('2026-11-01'), ('2026-12-01')))
DISTRIBUTED BY HASH(`brand_id`, `coupon_info_id`) BUCKETS 8
PROPERTIES (
"replication_allocation" = "tag.location.default: 1",
"min_load_replica_num" = "-1",
"is_being_synced" = "false",
"storage_medium" = "hdd",
"storage_format" = "V2",
"inverted_index_storage_format" = "V3",
"enable_unique_key_merge_on_write" = "true",
"light_schema_change" = "true",
"disable_auto_compaction" = "false",
"binlog.enable" = "true",
"binlog.ttl_seconds" = "604800",
"binlog.max_bytes" = "1073741824",
"binlog.max_history_nums" = "9223372036854775807",
"enable_single_replica_compaction" = "false",
"group_commit_interval_ms" = "10000",
"group_commit_data_bytes" = "134217728",
"enable_mow_light_delete" = "false"
);
```

### `dwd_trade_order_discount_fact_v1`

```sql
CREATE TABLE `dwd_trade_order_discount_fact_v1` (
  `order_created_date` date NOT NULL COMMENT "订单创建日期，稳定分区键",
  `platform_code` varchar(16) NOT NULL COMMENT "平台编码",
  `platform_order_id` varchar(128) NOT NULL COMMENT "平台订单号",
  `discount_line_key` varchar(128) NOT NULL COMMENT "订单内优惠稳定行键",
  `internal_shop_id` bigint NULL COMMENT "跨服务商稳定的统一门店ID",
  `brand_id` bigint NULL COMMENT "品牌ID",
  `platform_shop_id` varchar(128) NULL COMMENT "平台门店号",
  `platform_activity_id` varchar(128) NULL COMMENT "平台活动ID",
  `activity_name` varchar(256) NULL COMMENT "活动名称",
  `discount_type` varchar(32) NULL COMMENT "优惠类型",
  `benefit_type` varchar(32) NOT NULL COMMENT "优惠权益类型",
  `discount_scope` varchar(32) NOT NULL COMMENT "优惠作用范围",
  `item_line_key` varchar(128) NULL COMMENT "订单内商品稳定行键",
  `benefit_amount` bigint NULL COMMENT "优惠权益金额，单位分",
  `merchant_bear_amount` bigint NULL COMMENT "商家承担金额，单位分",
  `platform_bear_amount` bigint NULL COMMENT "平台承担金额，单位分",
  `other_bear_amount` bigint NULL COMMENT "其他方承担金额，单位分",
  `user_bear_amount` bigint NULL COMMENT "用户承担金额，单位分",
  `verification_quantity` decimal(18,3) NULL COMMENT "来源明确给出的团购券使用数量；完整核销过程见团购核销事实",
  `meta_canonical_schema_version` varchar(16) NOT NULL COMMENT "Canonical模型版本",
  `meta_transform_version` varchar(64) NOT NULL COMMENT "转换逻辑版本",
  `meta_process_time` datetime(3) NOT NULL COMMENT "事实处理时间",
  `meta_sink_sequence` bigint NOT NULL COMMENT "Doris写入顺序版本",
  `src_updated_time` datetime(3) NULL COMMENT "来源对象更新时间",
  `src_version` bigint NOT NULL COMMENT "当前事实来源版本",
  `src_last_event_id` bigint NOT NULL COMMENT "最后写入的来源事件ID",
  `src_last_storage_id` bigint NOT NULL COMMENT "最后写入事件的Storage ID",
  `src_last_payload_sha256` char(64) NOT NULL COMMENT "最后写入原文SHA-256",
  `src_event_received_time` datetime(3) NOT NULL COMMENT "事件接收时间",
  `meta_is_active` boolean NOT NULL DEFAULT "1" COMMENT "成员是否有效"
) ENGINE=OLAP
UNIQUE KEY(`order_created_date`, `platform_code`, `platform_order_id`, `discount_line_key`)
COMMENT '统一订单优惠当前事实物理表 v1'
PARTITION BY RANGE(`order_created_date`)
(PARTITION p202110 VALUES [('2021-10-01'), ('2021-11-01')),
PARTITION p202111 VALUES [('2021-11-01'), ('2021-12-01')),
PARTITION p202112 VALUES [('2021-12-01'), ('2022-01-01')),
PARTITION p202201 VALUES [('2022-01-01'), ('2022-02-01')),
PARTITION p202202 VALUES [('2022-02-01'), ('2022-03-01')),
PARTITION p202203 VALUES [('2022-03-01'), ('2022-04-01')),
PARTITION p202204 VALUES [('2022-04-01'), ('2022-05-01')),
PARTITION p202205 VALUES [('2022-05-01'), ('2022-06-01')),
PARTITION p202206 VALUES [('2022-06-01'), ('2022-07-01')),
PARTITION p202207 VALUES [('2022-07-01'), ('2022-08-01')),
PARTITION p202208 VALUES [('2022-08-01'), ('2022-09-01')),
PARTITION p202209 VALUES [('2022-09-01'), ('2022-10-01')),
PARTITION p202210 VALUES [('2022-10-01'), ('2022-11-01')),
PARTITION p202211 VALUES [('2022-11-01'), ('2022-12-01')),
PARTITION p202212 VALUES [('2022-12-01'), ('2023-01-01')),
PARTITION p202301 VALUES [('2023-01-01'), ('2023-02-01')),
PARTITION p202302 VALUES [('2023-02-01'), ('2023-03-01')),
PARTITION p202303 VALUES [('2023-03-01'), ('2023-04-01')),
PARTITION p202304 VALUES [('2023-04-01'), ('2023-05-01')),
PARTITION p202305 VALUES [('2023-05-01'), ('2023-06-01')),
PARTITION p202306 VALUES [('2023-06-01'), ('2023-07-01')),
PARTITION p202307 VALUES [('2023-07-01'), ('2023-08-01')),
PARTITION p202308 VALUES [('2023-08-01'), ('2023-09-01')),
PARTITION p202309 VALUES [('2023-09-01'), ('2023-10-01')),
PARTITION p202310 VALUES [('2023-10-01'), ('2023-11-01')),
PARTITION p202311 VALUES [('2023-11-01'), ('2023-12-01')),
PARTITION p202312 VALUES [('2023-12-01'), ('2024-01-01')),
PARTITION p202401 VALUES [('2024-01-01'), ('2024-02-01')),
PARTITION p202402 VALUES [('2024-02-01'), ('2024-03-01')),
PARTITION p202403 VALUES [('2024-03-01'), ('2024-04-01')),
PARTITION p202404 VALUES [('2024-04-01'), ('2024-05-01')),
PARTITION p202405 VALUES [('2024-05-01'), ('2024-06-01')),
PARTITION p202406 VALUES [('2024-06-01'), ('2024-07-01')),
PARTITION p202407 VALUES [('2024-07-01'), ('2024-08-01')),
PARTITION p202408 VALUES [('2024-08-01'), ('2024-09-01')),
PARTITION p202409 VALUES [('2024-09-01'), ('2024-10-01')),
PARTITION p202410 VALUES [('2024-10-01'), ('2024-11-01')),
PARTITION p202411 VALUES [('2024-11-01'), ('2024-12-01')),
PARTITION p202412 VALUES [('2024-12-01'), ('2025-01-01')),
PARTITION p202501 VALUES [('2025-01-01'), ('2025-02-01')),
PARTITION p202502 VALUES [('2025-02-01'), ('2025-03-01')),
PARTITION p202503 VALUES [('2025-03-01'), ('2025-04-01')),
PARTITION p202504 VALUES [('2025-04-01'), ('2025-05-01')),
PARTITION p202505 VALUES [('2025-05-01'), ('2025-06-01')),
PARTITION p202506 VALUES [('2025-06-01'), ('2025-07-01')),
PARTITION p202507 VALUES [('2025-07-01'), ('2025-08-01')),
PARTITION p202508 VALUES [('2025-08-01'), ('2025-09-01')),
PARTITION p202509 VALUES [('2025-09-01'), ('2025-10-01')),
PARTITION p202510 VALUES [('2025-10-01'), ('2025-11-01')),
PARTITION p202511 VALUES [('2025-11-01'), ('2025-12-01')),
PARTITION p202512 VALUES [('2025-12-01'), ('2026-01-01')),
PARTITION p202601 VALUES [('2026-01-01'), ('2026-02-01')),
PARTITION p202602 VALUES [('2026-02-01'), ('2026-03-01')),
PARTITION p202603 VALUES [('2026-03-01'), ('2026-04-01')),
PARTITION p202604 VALUES [('2026-04-01'), ('2026-05-01')),
PARTITION p202605 VALUES [('2026-05-01'), ('2026-06-01')),
PARTITION p202606 VALUES [('2026-06-01'), ('2026-07-01')),
PARTITION p202607 VALUES [('2026-07-01'), ('2026-08-01')),
PARTITION p202608 VALUES [('2026-08-01'), ('2026-09-01')),
PARTITION p202609 VALUES [('2026-09-01'), ('2026-10-01')),
PARTITION p202610 VALUES [('2026-10-01'), ('2026-11-01')),
PARTITION p202611 VALUES [('2026-11-01'), ('2026-12-01')),
PARTITION p202612 VALUES [('2026-12-01'), ('2027-01-01')),
PARTITION p202701 VALUES [('2027-01-01'), ('2027-02-01')),
PARTITION p202702 VALUES [('2027-02-01'), ('2027-03-01')),
PARTITION p202703 VALUES [('2027-03-01'), ('2027-04-01')),
PARTITION p202704 VALUES [('2027-04-01'), ('2027-05-01')),
PARTITION p202705 VALUES [('2027-05-01'), ('2027-06-01')),
PARTITION p202706 VALUES [('2027-06-01'), ('2027-07-01')),
PARTITION p202707 VALUES [('2027-07-01'), ('2027-08-01')),
PARTITION p202708 VALUES [('2027-08-01'), ('2027-09-01')),
PARTITION p202709 VALUES [('2027-09-01'), ('2027-10-01')),
PARTITION p202710 VALUES [('2027-10-01'), ('2027-11-01')),
PARTITION p202711 VALUES [('2027-11-01'), ('2027-12-01')))
DISTRIBUTED BY HASH(`platform_code`, `platform_order_id`) BUCKETS 8
PROPERTIES (
"replication_allocation" = "tag.location.default: 1",
"min_load_replica_num" = "-1",
"is_being_synced" = "false",
"dynamic_partition.enable" = "true",
"dynamic_partition.time_unit" = "MONTH",
"dynamic_partition.time_zone" = "Asia/Shanghai",
"dynamic_partition.start" = "-2147483648",
"dynamic_partition.end" = "12",
"dynamic_partition.prefix" = "p",
"dynamic_partition.replication_allocation" = "tag.location.default: 1",
"dynamic_partition.buckets" = "8",
"dynamic_partition.create_history_partition" = "false",
"dynamic_partition.history_partition_num" = "-1",
"dynamic_partition.hot_partition_num" = "0",
"dynamic_partition.reserved_history_periods" = "NULL",
"dynamic_partition.storage_policy" = "",
"dynamic_partition.start_day_of_month" = "1",
"storage_medium" = "hdd",
"storage_format" = "V2",
"inverted_index_storage_format" = "V3",
"enable_unique_key_merge_on_write" = "true",
"light_schema_change" = "true",
"function_column.sequence_col" = "meta_sink_sequence",
"disable_auto_compaction" = "false",
"binlog.enable" = "true",
"binlog.ttl_seconds" = "604800",
"binlog.max_bytes" = "1073741824",
"binlog.max_history_nums" = "9223372036854775807",
"enable_single_replica_compaction" = "false",
"group_commit_interval_ms" = "10000",
"group_commit_data_bytes" = "134217728",
"enable_mow_light_delete" = "false"
);
```

### `dwd_trade_order_fact_v1`

```sql
CREATE TABLE `dwd_trade_order_fact_v1` (
  `order_created_date` date NOT NULL COMMENT "稳定分区键",
  `platform_code` varchar(16) NOT NULL COMMENT "平台编码",
  `platform_order_id` varchar(128) NOT NULL COMMENT "平台订单号",
  `internal_order_id` varchar(128) NULL COMMENT "内部订单号",
  `internal_shop_id` bigint NULL COMMENT "跨服务商稳定的统一门店ID",
  `brand_id` bigint NULL COMMENT "品牌ID",
  `platform_parent_order_id` varchar(128) NULL COMMENT "平台父单号或拆单前订单号",
  `platform_shop_id` varchar(128) NOT NULL COMMENT "平台门店号",
  `business_scene` varchar(16) NOT NULL COMMENT "业务场景",
  `order_type` varchar(16) NOT NULL COMMENT "标准订单类型",
  `channel_category` varchar(32) NOT NULL COMMENT "订单渠道大类，含堂食与团购组合支付内部类型",
  `channel_code` varchar(32) NOT NULL COMMENT "渠道小类",
  `delivery_mode` varchar(32) NULL COMMENT "配送或履约方式",
  `currency_code` char(3) NOT NULL COMMENT "ISO 4217币种编码",
  `order_status` varchar(32) NOT NULL COMMENT "标准订单状态",
  `payment_status` varchar(32) NOT NULL COMMENT "标准支付状态",
  `refund_status` varchar(32) NULL COMMENT "标准退款操作状态",
  `delivery_status` varchar(32) NULL COMMENT "标准配送状态",
  `fulfillment_status` varchar(32) NOT NULL COMMENT "标准履约进度",
  `complaint_status` varchar(32) NOT NULL COMMENT "标准投诉状态",
  `group_buy_status` varchar(32) NOT NULL COMMENT "标准团购状态",
  `cancel_reason_code` varchar(64) NULL COMMENT "标准取消原因编码",
  `cancel_reason` varchar(1024) NULL COMMENT "取消原因说明",
  `goods_original_amount` bigint NULL COMMENT "商品优惠前总金额，单位分",
  `order_original_amount` bigint NULL COMMENT "订单原始应收金额，单位分",
  `order_discount_base_amount` bigint NULL COMMENT "执行订单级优惠前的计价基数",
  `order_after_discount_amount` bigint NULL COMMENT "优惠后订单应付金额，单位分",
  `turnover_amount` bigint NULL COMMENT "营业额，单位分",
  `user_pay_amount` bigint NULL COMMENT "用户实付金额，单位分",
  `total_discount_amount` bigint NULL COMMENT "优惠总金额，单位分",
  `packing_fee_amount` bigint NULL COMMENT "包装费，单位分",
  `user_delivery_fee_amount` bigint NULL COMMENT "用户承担配送费，单位分",
  `merchant_estimated_income_amount` bigint NULL COMMENT "商家预计收入，单位分",
  `order_refund_success_amount` bigint NULL COMMENT "订单累计成功退款金额，单位分",
  `item_quantity` decimal(18,3) NULL COMMENT "订单商品总数量",
  `guest_count` int NULL COMMENT "就餐人数",
  `is_membership` tinyint NULL DEFAULT "0" COMMENT "会员状态：0未映射，1会员，2非会员",
  `customer_name_masked` varchar(128) NULL COMMENT "脱敏客户姓名或展示名",
  `customer_phone_masked` varchar(32) NULL COMMENT "脱敏客户手机号",
  `customer_note` varchar(1024) NULL COMMENT "经治理后的客户备注",
  `order_created_time` datetime(3) NOT NULL COMMENT "下单时间",
  `appointment_time` datetime(3) NULL COMMENT "预约时间",
  `paid_time` datetime(3) NULL COMMENT "支付成功时间",
  `accepted_time` datetime(3) NULL COMMENT "商家接单时间",
  `completed_time` datetime(3) NULL COMMENT "订单完成时间",
  `cancelled_time` datetime(3) NULL COMMENT "取消时间",
  `closed_time` datetime(3) NULL COMMENT "关闭时间",
  `meta_canonical_schema_version` varchar(16) NOT NULL COMMENT "Canonical模型版本",
  `meta_transform_version` varchar(64) NOT NULL COMMENT "转换逻辑版本",
  `meta_process_time` datetime(3) NOT NULL COMMENT "事实处理时间",
  `meta_sink_sequence` bigint NOT NULL COMMENT "Doris写入顺序版本",
  `meta_detail_ready` boolean NOT NULL DEFAULT "0" COMMENT "商品和价格详情是否完整",
  `meta_discount_ready` boolean NOT NULL DEFAULT "0" COMMENT "优惠详情是否完整",
  `meta_payment_ready` boolean NOT NULL DEFAULT "0" COMMENT "支付详情是否完整",
  `meta_refund_ready` boolean NOT NULL DEFAULT "0" COMMENT "退款详情是否完整",
  `meta_settlement_ready` boolean NOT NULL DEFAULT "0" COMMENT "结算详情是否完整",
  `meta_group_buy_ready` boolean NOT NULL DEFAULT "0" COMMENT "团购核销信息是否完整",
  `meta_data_quality_status` varchar(32) NOT NULL COMMENT "数据质量状态",
  `src_channel_code` varchar(64) NULL COMMENT "平台原始渠道编码",
  `src_order_status` varchar(64) NULL COMMENT "平台原始订单状态",
  `src_payment_status` varchar(64) NULL COMMENT "平台原始支付状态",
  `src_refund_status` varchar(64) NULL COMMENT "平台原始退款状态",
  `src_delivery_status` varchar(64) NULL COMMENT "平台原始配送状态",
  `src_updated_time` datetime(3) NULL COMMENT "来源对象更新时间",
  `src_core_version` bigint NOT NULL COMMENT "订单核心字段来源版本",
  `src_status_version` bigint NOT NULL COMMENT "订单状态字段来源版本",
  `src_fulfillment_version` bigint NOT NULL COMMENT "订单履约字段来源版本",
  `src_payment_version` bigint NOT NULL COMMENT "订单支付字段来源版本",
  `src_refund_version` bigint NOT NULL COMMENT "订单退款字段来源版本",
  `src_detail_version` bigint NOT NULL COMMENT "订单详情字段来源版本",
  `src_last_event_id` bigint NOT NULL COMMENT "最后写入的来源事件ID",
  `src_last_storage_id` bigint NOT NULL COMMENT "最后写入事件的Storage ID",
  `src_last_payload_sha256` char(64) NOT NULL COMMENT "最后写入原文SHA-256",
  `src_last_status_event_id` bigint NULL COMMENT "状态字段最后来源事件ID",
  `src_last_status_storage_id` bigint NULL COMMENT "状态字段最后Storage ID",
  `src_last_status_payload_sha256` char(64) NULL COMMENT "状态字段最后原文SHA-256",
  `src_last_snapshot_event_id` bigint NULL COMMENT "详情快照最后来源事件ID",
  `src_last_snapshot_storage_id` bigint NULL COMMENT "详情快照最后Storage ID",
  `src_last_snapshot_payload_sha256` char(64) NULL COMMENT "详情快照最后原文SHA-256",
  `src_event_received_time` datetime(3) NOT NULL COMMENT "事件接收时间",
  `cancel_flow_status` varchar(64) NULL COMMENT "取消流程状态",
  `refund_flow_status` varchar(64) NULL COMMENT "退款流程状态",
  `src_refund_type` varchar(64) NULL COMMENT "平台原始退款类型",
  `src_delivery_version` bigint NULL COMMENT "配送状态Domain版本",
  `src_delivery_mode_version` bigint NULL COMMENT "配送方式Domain版本",
  `src_preorder_version` bigint NULL COMMENT "预订单Domain版本",
  `src_cancel_version` bigint NULL COMMENT "取消流程Domain版本",
  `src_refund_flow_version` bigint NULL COMMENT "退款流程Domain版本",
  `src_refund_summary_revision` bigint NULL COMMENT "退款汇总投影版本",
  `src_refund_pickup_result_sequence` bigint NOT NULL DEFAULT "0" COMMENT "退款商品查询结果序号",
  `src_order_payload_sha256` varchar(64) NULL COMMENT "订单Domain确定性指纹",
  `src_fulfillment_payload_sha256` varchar(64) NULL COMMENT "履约Domain确定性指纹",
  `src_delivery_payload_sha256` varchar(64) NULL COMMENT "配送Domain确定性指纹",
  `src_delivery_mode_payload_sha256` varchar(64) NULL COMMENT "配送方式Domain确定性指纹",
  `src_payment_payload_sha256` varchar(64) NULL COMMENT "支付Domain确定性指纹",
  `src_preorder_payload_sha256` varchar(64) NULL COMMENT "预订单Domain确定性指纹",
  `src_cancel_payload_sha256` varchar(64) NULL COMMENT "取消流程Domain确定性指纹",
  `src_refund_flow_payload_sha256` varchar(64) NULL COMMENT "退款流程Domain确定性指纹",
  `src_refund_summary_payload_sha256` varchar(64) NULL COMMENT "退款汇总投影确定性指纹",
  `src_core_event_id` bigint NULL COMMENT "核心快照最后来源事件ID",
  `src_core_storage_id` bigint NULL COMMENT "核心快照最后Storage ID",
  `src_core_payload_sha256` char(64) NULL COMMENT "核心快照最后原文SHA-256",
  `src_core_snapshot_source` varchar(32) NULL COMMENT "核心快照标准来源类型",
  `refund_type` varchar(32) NULL COMMENT "标准退款范围PARTIAL或FULL",
  INDEX idx_order_internal_shop_id (`internal_shop_id`) USING INVERTED
) ENGINE=OLAP
UNIQUE KEY(`order_created_date`, `platform_code`, `platform_order_id`)
COMMENT '统一订单当前事实物理表 v1'
PARTITION BY RANGE(`order_created_date`)
(PARTITION p202110 VALUES [('2021-10-01'), ('2021-11-01')),
PARTITION p202111 VALUES [('2021-11-01'), ('2021-12-01')),
PARTITION p202112 VALUES [('2021-12-01'), ('2022-01-01')),
PARTITION p202201 VALUES [('2022-01-01'), ('2022-02-01')),
PARTITION p202202 VALUES [('2022-02-01'), ('2022-03-01')),
PARTITION p202203 VALUES [('2022-03-01'), ('2022-04-01')),
PARTITION p202204 VALUES [('2022-04-01'), ('2022-05-01')),
PARTITION p202205 VALUES [('2022-05-01'), ('2022-06-01')),
PARTITION p202206 VALUES [('2022-06-01'), ('2022-07-01')),
PARTITION p202207 VALUES [('2022-07-01'), ('2022-08-01')),
PARTITION p202208 VALUES [('2022-08-01'), ('2022-09-01')),
PARTITION p202209 VALUES [('2022-09-01'), ('2022-10-01')),
PARTITION p202210 VALUES [('2022-10-01'), ('2022-11-01')),
PARTITION p202211 VALUES [('2022-11-01'), ('2022-12-01')),
PARTITION p202212 VALUES [('2022-12-01'), ('2023-01-01')),
PARTITION p202301 VALUES [('2023-01-01'), ('2023-02-01')),
PARTITION p202302 VALUES [('2023-02-01'), ('2023-03-01')),
PARTITION p202303 VALUES [('2023-03-01'), ('2023-04-01')),
PARTITION p202304 VALUES [('2023-04-01'), ('2023-05-01')),
PARTITION p202305 VALUES [('2023-05-01'), ('2023-06-01')),
PARTITION p202306 VALUES [('2023-06-01'), ('2023-07-01')),
PARTITION p202307 VALUES [('2023-07-01'), ('2023-08-01')),
PARTITION p202308 VALUES [('2023-08-01'), ('2023-09-01')),
PARTITION p202309 VALUES [('2023-09-01'), ('2023-10-01')),
PARTITION p202310 VALUES [('2023-10-01'), ('2023-11-01')),
PARTITION p202311 VALUES [('2023-11-01'), ('2023-12-01')),
PARTITION p202312 VALUES [('2023-12-01'), ('2024-01-01')),
PARTITION p202401 VALUES [('2024-01-01'), ('2024-02-01')),
PARTITION p202402 VALUES [('2024-02-01'), ('2024-03-01')),
PARTITION p202403 VALUES [('2024-03-01'), ('2024-04-01')),
PARTITION p202404 VALUES [('2024-04-01'), ('2024-05-01')),
PARTITION p202405 VALUES [('2024-05-01'), ('2024-06-01')),
PARTITION p202406 VALUES [('2024-06-01'), ('2024-07-01')),
PARTITION p202407 VALUES [('2024-07-01'), ('2024-08-01')),
PARTITION p202408 VALUES [('2024-08-01'), ('2024-09-01')),
PARTITION p202409 VALUES [('2024-09-01'), ('2024-10-01')),
PARTITION p202410 VALUES [('2024-10-01'), ('2024-11-01')),
PARTITION p202411 VALUES [('2024-11-01'), ('2024-12-01')),
PARTITION p202412 VALUES [('2024-12-01'), ('2025-01-01')),
PARTITION p202501 VALUES [('2025-01-01'), ('2025-02-01')),
PARTITION p202502 VALUES [('2025-02-01'), ('2025-03-01')),
PARTITION p202503 VALUES [('2025-03-01'), ('2025-04-01')),
PARTITION p202504 VALUES [('2025-04-01'), ('2025-05-01')),
PARTITION p202505 VALUES [('2025-05-01'), ('2025-06-01')),
PARTITION p202506 VALUES [('2025-06-01'), ('2025-07-01')),
PARTITION p202507 VALUES [('2025-07-01'), ('2025-08-01')),
PARTITION p202508 VALUES [('2025-08-01'), ('2025-09-01')),
PARTITION p202509 VALUES [('2025-09-01'), ('2025-10-01')),
PARTITION p202510 VALUES [('2025-10-01'), ('2025-11-01')),
PARTITION p202511 VALUES [('2025-11-01'), ('2025-12-01')),
PARTITION p202512 VALUES [('2025-12-01'), ('2026-01-01')),
PARTITION p202601 VALUES [('2026-01-01'), ('2026-02-01')),
PARTITION p202602 VALUES [('2026-02-01'), ('2026-03-01')),
PARTITION p202603 VALUES [('2026-03-01'), ('2026-04-01')),
PARTITION p202604 VALUES [('2026-04-01'), ('2026-05-01')),
PARTITION p202605 VALUES [('2026-05-01'), ('2026-06-01')),
PARTITION p202606 VALUES [('2026-06-01'), ('2026-07-01')),
PARTITION p202607 VALUES [('2026-07-01'), ('2026-08-01')),
PARTITION p202608 VALUES [('2026-08-01'), ('2026-09-01')),
PARTITION p202609 VALUES [('2026-09-01'), ('2026-10-01')),
PARTITION p202610 VALUES [('2026-10-01'), ('2026-11-01')),
PARTITION p202611 VALUES [('2026-11-01'), ('2026-12-01')),
PARTITION p202612 VALUES [('2026-12-01'), ('2027-01-01')),
PARTITION p202701 VALUES [('2027-01-01'), ('2027-02-01')),
PARTITION p202702 VALUES [('2027-02-01'), ('2027-03-01')),
PARTITION p202703 VALUES [('2027-03-01'), ('2027-04-01')),
PARTITION p202704 VALUES [('2027-04-01'), ('2027-05-01')),
PARTITION p202705 VALUES [('2027-05-01'), ('2027-06-01')),
PARTITION p202706 VALUES [('2027-06-01'), ('2027-07-01')),
PARTITION p202707 VALUES [('2027-07-01'), ('2027-08-01')),
PARTITION p202708 VALUES [('2027-08-01'), ('2027-09-01')),
PARTITION p202709 VALUES [('2027-09-01'), ('2027-10-01')),
PARTITION p202710 VALUES [('2027-10-01'), ('2027-11-01')),
PARTITION p202711 VALUES [('2027-11-01'), ('2027-12-01')))
DISTRIBUTED BY HASH(`platform_code`, `platform_order_id`) BUCKETS 16
PROPERTIES (
"replication_allocation" = "tag.location.default: 1",
"min_load_replica_num" = "-1",
"is_being_synced" = "false",
"dynamic_partition.enable" = "true",
"dynamic_partition.time_unit" = "MONTH",
"dynamic_partition.time_zone" = "Asia/Shanghai",
"dynamic_partition.start" = "-2147483648",
"dynamic_partition.end" = "12",
"dynamic_partition.prefix" = "p",
"dynamic_partition.replication_allocation" = "tag.location.default: 1",
"dynamic_partition.buckets" = "16",
"dynamic_partition.create_history_partition" = "false",
"dynamic_partition.history_partition_num" = "-1",
"dynamic_partition.hot_partition_num" = "0",
"dynamic_partition.reserved_history_periods" = "NULL",
"dynamic_partition.storage_policy" = "",
"dynamic_partition.start_day_of_month" = "1",
"storage_medium" = "hdd",
"storage_format" = "V2",
"inverted_index_storage_format" = "V3",
"enable_unique_key_merge_on_write" = "true",
"enable_unique_key_skip_bitmap_column" = "true",
"light_schema_change" = "true",
"function_column.sequence_col" = "meta_sink_sequence",
"disable_auto_compaction" = "false",
"binlog.enable" = "true",
"binlog.ttl_seconds" = "604800",
"binlog.max_bytes" = "1073741824",
"binlog.max_history_nums" = "9223372036854775807",
"enable_single_replica_compaction" = "false",
"group_commit_interval_ms" = "10000",
"group_commit_data_bytes" = "134217728",
"enable_mow_light_delete" = "false"
);
```

### `dwd_trade_order_item_fact_v1`

```sql
CREATE TABLE `dwd_trade_order_item_fact_v1` (
  `order_created_date` date NOT NULL COMMENT "订单创建日期，稳定分区键",
  `platform_code` varchar(16) NOT NULL COMMENT "平台编码",
  `platform_order_id` varchar(128) NOT NULL COMMENT "平台订单号",
  `item_line_key` varchar(128) NOT NULL COMMENT "订单内商品稳定行键",
  `internal_shop_id` bigint NULL COMMENT "跨服务商稳定的统一门店ID",
  `brand_id` bigint NULL COMMENT "品牌ID",
  `internal_product_id` bigint NULL COMMENT "内部商品ID",
  `internal_category_id` bigint NULL COMMENT "内部类目ID",
  `platform_shop_id` varchar(128) NULL COMMENT "平台门店号",
  `platform_item_id` varchar(128) NULL COMMENT "平台商品ID",
  `platform_sku_id` varchar(128) NULL COMMENT "平台SKU ID",
  `platform_category_id` varchar(128) NULL COMMENT "平台类目ID",
  `platform_category_name` varchar(256) NULL COMMENT "平台类目名称",
  `parent_item_line_key` varchar(128) NULL COMMENT "套餐父商品行键",
  `item_type` varchar(32) NOT NULL COMMENT "商品行类型",
  `item_name` varchar(2048) NOT NULL COMMENT "商品名称",
  `sku_name` varchar(256) NULL COMMENT "SKU名称",
  `spec_description` varchar(1024) NULL COMMENT "规格描述",
  `item_status` varchar(32) NULL COMMENT "商品行状态",
  `quantity` decimal(18,3) NOT NULL COMMENT "商品数量",
  `original_unit_price` bigint NULL COMMENT "商品原价单价，单位分",
  `original_amount` bigint NULL COMMENT "商品原价金额，单位分",
  `discounted_unit_price` bigint NULL COMMENT "商品折后单价，单位分",
  `discounted_amount` bigint NULL COMMENT "商品折后计价金额，单位分，不等同最终实付",
  `discount_amount` bigint NULL COMMENT "商品优惠金额，单位分；统一语义冻结前可为空",
  `user_pay_amount` bigint NULL COMMENT "用户实付金额，单位分",
  `merchant_income_amount` bigint NULL COMMENT "商品商家预计收入，单位分",
  `packing_fee_amount` bigint NULL COMMENT "包装费，单位分",
  `refund_success_quantity` decimal(18,3) NOT NULL DEFAULT "0" COMMENT "商品累计成功退款数量",
  `refund_success_amount` bigint NOT NULL DEFAULT "0" COMMENT "商品累计成功退款金额，单位分",
  `meta_canonical_schema_version` varchar(16) NOT NULL COMMENT "Canonical模型版本",
  `meta_transform_version` varchar(64) NOT NULL COMMENT "转换逻辑版本",
  `meta_process_time` datetime(3) NOT NULL COMMENT "事实处理时间",
  `meta_sink_sequence` bigint NOT NULL COMMENT "Doris写入顺序版本",
  `meta_merchant_income_ready` boolean NOT NULL DEFAULT "0" COMMENT "商品商家收入是否可信",
  `src_updated_time` datetime(3) NULL COMMENT "来源对象更新时间",
  `src_version` bigint NOT NULL COMMENT "当前事实来源版本",
  `src_last_event_id` bigint NOT NULL COMMENT "最后写入的来源事件ID",
  `src_last_storage_id` bigint NOT NULL COMMENT "最后写入事件的Storage ID",
  `src_last_payload_sha256` char(64) NOT NULL COMMENT "最后写入原文SHA-256",
  `src_event_received_time` datetime(3) NOT NULL COMMENT "事件接收时间",
  `source_merchant_code` varchar(64) NULL COMMENT "来源账号",
  `resolved_platform_sku_id` varchar(128) NULL COMMENT "已识别的可售规格编号",
  `meta_is_active` boolean NOT NULL DEFAULT "1" COMMENT "成员是否有效",
  `src_platform_product_id` varchar(128) NULL COMMENT "平台可售SKU编号，与SSM平台商品编号一致",
  INDEX idx_item_platform_order_id (`platform_order_id`) USING INVERTED
) ENGINE=OLAP
UNIQUE KEY(`order_created_date`, `platform_code`, `platform_order_id`, `item_line_key`)
COMMENT '统一订单商品当前事实物理表 v1'
PARTITION BY RANGE(`order_created_date`)
(PARTITION p202110 VALUES [('2021-10-01'), ('2021-11-01')),
PARTITION p202111 VALUES [('2021-11-01'), ('2021-12-01')),
PARTITION p202112 VALUES [('2021-12-01'), ('2022-01-01')),
PARTITION p202201 VALUES [('2022-01-01'), ('2022-02-01')),
PARTITION p202202 VALUES [('2022-02-01'), ('2022-03-01')),
PARTITION p202203 VALUES [('2022-03-01'), ('2022-04-01')),
PARTITION p202204 VALUES [('2022-04-01'), ('2022-05-01')),
PARTITION p202205 VALUES [('2022-05-01'), ('2022-06-01')),
PARTITION p202206 VALUES [('2022-06-01'), ('2022-07-01')),
PARTITION p202207 VALUES [('2022-07-01'), ('2022-08-01')),
PARTITION p202208 VALUES [('2022-08-01'), ('2022-09-01')),
PARTITION p202209 VALUES [('2022-09-01'), ('2022-10-01')),
PARTITION p202210 VALUES [('2022-10-01'), ('2022-11-01')),
PARTITION p202211 VALUES [('2022-11-01'), ('2022-12-01')),
PARTITION p202212 VALUES [('2022-12-01'), ('2023-01-01')),
PARTITION p202301 VALUES [('2023-01-01'), ('2023-02-01')),
PARTITION p202302 VALUES [('2023-02-01'), ('2023-03-01')),
PARTITION p202303 VALUES [('2023-03-01'), ('2023-04-01')),
PARTITION p202304 VALUES [('2023-04-01'), ('2023-05-01')),
PARTITION p202305 VALUES [('2023-05-01'), ('2023-06-01')),
PARTITION p202306 VALUES [('2023-06-01'), ('2023-07-01')),
PARTITION p202307 VALUES [('2023-07-01'), ('2023-08-01')),
PARTITION p202308 VALUES [('2023-08-01'), ('2023-09-01')),
PARTITION p202309 VALUES [('2023-09-01'), ('2023-10-01')),
PARTITION p202310 VALUES [('2023-10-01'), ('2023-11-01')),
PARTITION p202311 VALUES [('2023-11-01'), ('2023-12-01')),
PARTITION p202312 VALUES [('2023-12-01'), ('2024-01-01')),
PARTITION p202401 VALUES [('2024-01-01'), ('2024-02-01')),
PARTITION p202402 VALUES [('2024-02-01'), ('2024-03-01')),
PARTITION p202403 VALUES [('2024-03-01'), ('2024-04-01')),
PARTITION p202404 VALUES [('2024-04-01'), ('2024-05-01')),
PARTITION p202405 VALUES [('2024-05-01'), ('2024-06-01')),
PARTITION p202406 VALUES [('2024-06-01'), ('2024-07-01')),
PARTITION p202407 VALUES [('2024-07-01'), ('2024-08-01')),
PARTITION p202408 VALUES [('2024-08-01'), ('2024-09-01')),
PARTITION p202409 VALUES [('2024-09-01'), ('2024-10-01')),
PARTITION p202410 VALUES [('2024-10-01'), ('2024-11-01')),
PARTITION p202411 VALUES [('2024-11-01'), ('2024-12-01')),
PARTITION p202412 VALUES [('2024-12-01'), ('2025-01-01')),
PARTITION p202501 VALUES [('2025-01-01'), ('2025-02-01')),
PARTITION p202502 VALUES [('2025-02-01'), ('2025-03-01')),
PARTITION p202503 VALUES [('2025-03-01'), ('2025-04-01')),
PARTITION p202504 VALUES [('2025-04-01'), ('2025-05-01')),
PARTITION p202505 VALUES [('2025-05-01'), ('2025-06-01')),
PARTITION p202506 VALUES [('2025-06-01'), ('2025-07-01')),
PARTITION p202507 VALUES [('2025-07-01'), ('2025-08-01')),
PARTITION p202508 VALUES [('2025-08-01'), ('2025-09-01')),
PARTITION p202509 VALUES [('2025-09-01'), ('2025-10-01')),
PARTITION p202510 VALUES [('2025-10-01'), ('2025-11-01')),
PARTITION p202511 VALUES [('2025-11-01'), ('2025-12-01')),
PARTITION p202512 VALUES [('2025-12-01'), ('2026-01-01')),
PARTITION p202601 VALUES [('2026-01-01'), ('2026-02-01')),
PARTITION p202602 VALUES [('2026-02-01'), ('2026-03-01')),
PARTITION p202603 VALUES [('2026-03-01'), ('2026-04-01')),
PARTITION p202604 VALUES [('2026-04-01'), ('2026-05-01')),
PARTITION p202605 VALUES [('2026-05-01'), ('2026-06-01')),
PARTITION p202606 VALUES [('2026-06-01'), ('2026-07-01')),
PARTITION p202607 VALUES [('2026-07-01'), ('2026-08-01')),
PARTITION p202608 VALUES [('2026-08-01'), ('2026-09-01')),
PARTITION p202609 VALUES [('2026-09-01'), ('2026-10-01')),
PARTITION p202610 VALUES [('2026-10-01'), ('2026-11-01')),
PARTITION p202611 VALUES [('2026-11-01'), ('2026-12-01')),
PARTITION p202612 VALUES [('2026-12-01'), ('2027-01-01')),
PARTITION p202701 VALUES [('2027-01-01'), ('2027-02-01')),
PARTITION p202702 VALUES [('2027-02-01'), ('2027-03-01')),
PARTITION p202703 VALUES [('2027-03-01'), ('2027-04-01')),
PARTITION p202704 VALUES [('2027-04-01'), ('2027-05-01')),
PARTITION p202705 VALUES [('2027-05-01'), ('2027-06-01')),
PARTITION p202706 VALUES [('2027-06-01'), ('2027-07-01')),
PARTITION p202707 VALUES [('2027-07-01'), ('2027-08-01')),
PARTITION p202708 VALUES [('2027-08-01'), ('2027-09-01')),
PARTITION p202709 VALUES [('2027-09-01'), ('2027-10-01')),
PARTITION p202710 VALUES [('2027-10-01'), ('2027-11-01')),
PARTITION p202711 VALUES [('2027-11-01'), ('2027-12-01')))
DISTRIBUTED BY HASH(`platform_code`, `platform_order_id`) BUCKETS 16
PROPERTIES (
"replication_allocation" = "tag.location.default: 1",
"min_load_replica_num" = "-1",
"is_being_synced" = "false",
"dynamic_partition.enable" = "true",
"dynamic_partition.time_unit" = "MONTH",
"dynamic_partition.time_zone" = "Asia/Shanghai",
"dynamic_partition.start" = "-2147483648",
"dynamic_partition.end" = "12",
"dynamic_partition.prefix" = "p",
"dynamic_partition.replication_allocation" = "tag.location.default: 1",
"dynamic_partition.buckets" = "16",
"dynamic_partition.create_history_partition" = "false",
"dynamic_partition.history_partition_num" = "-1",
"dynamic_partition.hot_partition_num" = "0",
"dynamic_partition.reserved_history_periods" = "NULL",
"dynamic_partition.storage_policy" = "",
"dynamic_partition.start_day_of_month" = "1",
"storage_medium" = "hdd",
"storage_format" = "V2",
"inverted_index_storage_format" = "V3",
"enable_unique_key_merge_on_write" = "true",
"enable_unique_key_skip_bitmap_column" = "true",
"light_schema_change" = "true",
"function_column.sequence_col" = "meta_sink_sequence",
"disable_auto_compaction" = "false",
"binlog.enable" = "true",
"binlog.ttl_seconds" = "604800",
"binlog.max_bytes" = "1073741824",
"binlog.max_history_nums" = "9223372036854775807",
"enable_single_replica_compaction" = "false",
"group_commit_interval_ms" = "10000",
"group_commit_data_bytes" = "134217728",
"enable_mow_light_delete" = "false"
);
```

### `dwd_trade_payment_fact_v1`

```sql
CREATE TABLE `dwd_trade_payment_fact_v1` (
  `payment_date` date NOT NULL COMMENT "支付日期，稳定分区键",
  `platform_code` varchar(16) NOT NULL COMMENT "平台编码",
  `payment_id` varchar(128) NOT NULL COMMENT "支付流水ID",
  `payment_line_key` varchar(128) NOT NULL COMMENT "支付明细稳定行键",
  `internal_shop_id` bigint NULL COMMENT "跨服务商稳定的统一门店ID",
  `brand_id` bigint NULL COMMENT "品牌ID",
  `platform_order_id` varchar(128) NULL COMMENT "平台订单号",
  `platform_shop_id` varchar(128) NULL COMMENT "平台门店号",
  `business_scene` varchar(16) NOT NULL COMMENT "支付流水所属统一业务场景",
  `payment_status` varchar(32) NOT NULL COMMENT "标准支付状态",
  `payment_method` varchar(32) NOT NULL COMMENT "标准支付方式",
  `paid_amount` bigint NOT NULL COMMENT "支付金额，单位分",
  `payment_fee_amount` bigint NULL COMMENT "支付手续费，单位分",
  `voucher_face_amount` bigint NULL COMMENT "团购券券面金额，单位分；不等同于用户实付",
  `stored_value_principal_amount` bigint NULL COMMENT "储值本金支付金额，单位分",
  `stored_value_gift_amount` bigint NULL COMMENT "储值赠送金支付金额，单位分",
  `paid_time` datetime(3) NULL COMMENT "支付成功时间",
  `reversed_time` datetime(3) NULL COMMENT "冲正或撤销时间",
  `meta_canonical_schema_version` varchar(16) NOT NULL COMMENT "Canonical模型版本",
  `meta_transform_version` varchar(64) NOT NULL COMMENT "转换逻辑版本",
  `meta_process_time` datetime(3) NOT NULL COMMENT "事实处理时间",
  `meta_sink_sequence` bigint NOT NULL COMMENT "Doris写入顺序版本",
  `src_payment_method` varchar(64) NULL COMMENT "平台原始支付方式",
  `src_order_source` varchar(32) NULL COMMENT "平台原始订单来源编码",
  `src_updated_time` datetime(3) NULL COMMENT "来源对象更新时间",
  `src_version` bigint NOT NULL COMMENT "当前事实来源版本",
  `src_last_event_id` bigint NOT NULL COMMENT "最后写入的来源事件ID",
  `src_last_storage_id` bigint NOT NULL COMMENT "最后写入事件的Storage ID",
  `src_last_payload_sha256` char(64) NOT NULL COMMENT "最后写入原文SHA-256",
  `src_event_received_time` datetime(3) NOT NULL COMMENT "事件接收时间",
  INDEX idx_platform_order_id (`platform_order_id`) USING INVERTED
) ENGINE=OLAP
UNIQUE KEY(`payment_date`, `platform_code`, `payment_id`, `payment_line_key`)
COMMENT '统一支付当前事实物理表 v1'
PARTITION BY RANGE(`payment_date`)
(PARTITION p202110 VALUES [('2021-10-01'), ('2021-11-01')),
PARTITION p202111 VALUES [('2021-11-01'), ('2021-12-01')),
PARTITION p202112 VALUES [('2021-12-01'), ('2022-01-01')),
PARTITION p202201 VALUES [('2022-01-01'), ('2022-02-01')),
PARTITION p202202 VALUES [('2022-02-01'), ('2022-03-01')),
PARTITION p202203 VALUES [('2022-03-01'), ('2022-04-01')),
PARTITION p202204 VALUES [('2022-04-01'), ('2022-05-01')),
PARTITION p202205 VALUES [('2022-05-01'), ('2022-06-01')),
PARTITION p202206 VALUES [('2022-06-01'), ('2022-07-01')),
PARTITION p202207 VALUES [('2022-07-01'), ('2022-08-01')),
PARTITION p202208 VALUES [('2022-08-01'), ('2022-09-01')),
PARTITION p202209 VALUES [('2022-09-01'), ('2022-10-01')),
PARTITION p202210 VALUES [('2022-10-01'), ('2022-11-01')),
PARTITION p202211 VALUES [('2022-11-01'), ('2022-12-01')),
PARTITION p202212 VALUES [('2022-12-01'), ('2023-01-01')),
PARTITION p202301 VALUES [('2023-01-01'), ('2023-02-01')),
PARTITION p202302 VALUES [('2023-02-01'), ('2023-03-01')),
PARTITION p202303 VALUES [('2023-03-01'), ('2023-04-01')),
PARTITION p202304 VALUES [('2023-04-01'), ('2023-05-01')),
PARTITION p202305 VALUES [('2023-05-01'), ('2023-06-01')),
PARTITION p202306 VALUES [('2023-06-01'), ('2023-07-01')),
PARTITION p202307 VALUES [('2023-07-01'), ('2023-08-01')),
PARTITION p202308 VALUES [('2023-08-01'), ('2023-09-01')),
PARTITION p202309 VALUES [('2023-09-01'), ('2023-10-01')),
PARTITION p202310 VALUES [('2023-10-01'), ('2023-11-01')),
PARTITION p202311 VALUES [('2023-11-01'), ('2023-12-01')),
PARTITION p202312 VALUES [('2023-12-01'), ('2024-01-01')),
PARTITION p202401 VALUES [('2024-01-01'), ('2024-02-01')),
PARTITION p202402 VALUES [('2024-02-01'), ('2024-03-01')),
PARTITION p202403 VALUES [('2024-03-01'), ('2024-04-01')),
PARTITION p202404 VALUES [('2024-04-01'), ('2024-05-01')),
PARTITION p202405 VALUES [('2024-05-01'), ('2024-06-01')),
PARTITION p202406 VALUES [('2024-06-01'), ('2024-07-01')),
PARTITION p202407 VALUES [('2024-07-01'), ('2024-08-01')),
PARTITION p202408 VALUES [('2024-08-01'), ('2024-09-01')),
PARTITION p202409 VALUES [('2024-09-01'), ('2024-10-01')),
PARTITION p202410 VALUES [('2024-10-01'), ('2024-11-01')),
PARTITION p202411 VALUES [('2024-11-01'), ('2024-12-01')),
PARTITION p202412 VALUES [('2024-12-01'), ('2025-01-01')),
PARTITION p202501 VALUES [('2025-01-01'), ('2025-02-01')),
PARTITION p202502 VALUES [('2025-02-01'), ('2025-03-01')),
PARTITION p202503 VALUES [('2025-03-01'), ('2025-04-01')),
PARTITION p202504 VALUES [('2025-04-01'), ('2025-05-01')),
PARTITION p202505 VALUES [('2025-05-01'), ('2025-06-01')),
PARTITION p202506 VALUES [('2025-06-01'), ('2025-07-01')),
PARTITION p202507 VALUES [('2025-07-01'), ('2025-08-01')),
PARTITION p202508 VALUES [('2025-08-01'), ('2025-09-01')),
PARTITION p202509 VALUES [('2025-09-01'), ('2025-10-01')),
PARTITION p202510 VALUES [('2025-10-01'), ('2025-11-01')),
PARTITION p202511 VALUES [('2025-11-01'), ('2025-12-01')),
PARTITION p202512 VALUES [('2025-12-01'), ('2026-01-01')),
PARTITION p202601 VALUES [('2026-01-01'), ('2026-02-01')),
PARTITION p202602 VALUES [('2026-02-01'), ('2026-03-01')),
PARTITION p202603 VALUES [('2026-03-01'), ('2026-04-01')),
PARTITION p202604 VALUES [('2026-04-01'), ('2026-05-01')),
PARTITION p202605 VALUES [('2026-05-01'), ('2026-06-01')),
PARTITION p202606 VALUES [('2026-06-01'), ('2026-07-01')),
PARTITION p202607 VALUES [('2026-07-01'), ('2026-08-01')),
PARTITION p202608 VALUES [('2026-08-01'), ('2026-09-01')),
PARTITION p202609 VALUES [('2026-09-01'), ('2026-10-01')),
PARTITION p202610 VALUES [('2026-10-01'), ('2026-11-01')),
PARTITION p202611 VALUES [('2026-11-01'), ('2026-12-01')),
PARTITION p202612 VALUES [('2026-12-01'), ('2027-01-01')),
PARTITION p202701 VALUES [('2027-01-01'), ('2027-02-01')),
PARTITION p202702 VALUES [('2027-02-01'), ('2027-03-01')),
PARTITION p202703 VALUES [('2027-03-01'), ('2027-04-01')),
PARTITION p202704 VALUES [('2027-04-01'), ('2027-05-01')),
PARTITION p202705 VALUES [('2027-05-01'), ('2027-06-01')),
PARTITION p202706 VALUES [('2027-06-01'), ('2027-07-01')),
PARTITION p202707 VALUES [('2027-07-01'), ('2027-08-01')),
PARTITION p202708 VALUES [('2027-08-01'), ('2027-09-01')),
PARTITION p202709 VALUES [('2027-09-01'), ('2027-10-01')),
PARTITION p202710 VALUES [('2027-10-01'), ('2027-11-01')),
PARTITION p202711 VALUES [('2027-11-01'), ('2027-12-01')))
DISTRIBUTED BY HASH(`platform_code`, `payment_id`) BUCKETS 8
PROPERTIES (
"replication_allocation" = "tag.location.default: 1",
"min_load_replica_num" = "-1",
"is_being_synced" = "false",
"dynamic_partition.enable" = "true",
"dynamic_partition.time_unit" = "MONTH",
"dynamic_partition.time_zone" = "Asia/Shanghai",
"dynamic_partition.start" = "-2147483648",
"dynamic_partition.end" = "12",
"dynamic_partition.prefix" = "p",
"dynamic_partition.replication_allocation" = "tag.location.default: 1",
"dynamic_partition.buckets" = "8",
"dynamic_partition.create_history_partition" = "false",
"dynamic_partition.history_partition_num" = "-1",
"dynamic_partition.hot_partition_num" = "0",
"dynamic_partition.reserved_history_periods" = "NULL",
"dynamic_partition.storage_policy" = "",
"dynamic_partition.start_day_of_month" = "1",
"storage_medium" = "hdd",
"storage_format" = "V2",
"inverted_index_storage_format" = "V3",
"enable_unique_key_merge_on_write" = "true",
"light_schema_change" = "true",
"function_column.sequence_col" = "meta_sink_sequence",
"disable_auto_compaction" = "false",
"binlog.enable" = "true",
"binlog.ttl_seconds" = "604800",
"binlog.max_bytes" = "1073741824",
"binlog.max_history_nums" = "9223372036854775807",
"enable_single_replica_compaction" = "false",
"group_commit_interval_ms" = "10000",
"group_commit_data_bytes" = "134217728",
"enable_mow_light_delete" = "false"
);
```

### `dwd_trade_recharge_fact_v1`

```sql
CREATE TABLE `dwd_trade_recharge_fact_v1` (
  `recharge_date` date NOT NULL COMMENT "充值日期，稳定分区键",
  `platform_code` varchar(16) NOT NULL COMMENT "平台编码",
  `recharge_id` varchar(128) NOT NULL COMMENT "充值流水ID",
  `original_recharge_id` varchar(128) NULL COMMENT "原始充值流水ID",
  `internal_shop_id` bigint NULL COMMENT "跨服务商稳定的统一门店ID",
  `brand_id` bigint NULL COMMENT "内部品牌ID",
  `platform_shop_id` varchar(128) NULL COMMENT "平台门店号",
  `platform_shop_name` varchar(256) NULL COMMENT "平台门店名称",
  `recharge_status` varchar(32) NOT NULL COMMENT "充值状态",
  `recharge_type` varchar(32) NOT NULL COMMENT "充值类型",
  `recharge_channel` varchar(32) NOT NULL COMMENT "充值渠道",
  `direction` varchar(16) NOT NULL COMMENT "资金方向",
  `currency_code` char(3) NOT NULL COMMENT "ISO 4217币种编码",
  `payment_method` varchar(32) NOT NULL COMMENT "标准支付方式",
  `recharge_amount` bigint NOT NULL COMMENT "充值本金金额，单位分",
  `gift_amount` bigint NULL COMMENT "充值赠送金额，单位分",
  `payment_fee_amount` bigint NULL COMMENT "支付手续费，单位分",
  `member_name_masked` varchar(256) NULL COMMENT "脱敏会员姓名",
  `member_phone_masked` varchar(32) NULL COMMENT "脱敏会员手机号",
  `member_card_no_masked` varchar(128) NULL COMMENT "脱敏会员卡号",
  `member_level` varchar(32) NULL COMMENT "会员等级",
  `recharge_time` datetime(3) NOT NULL COMMENT "充值时间",
  `reversed_time` datetime(3) NULL COMMENT "冲正或撤销时间",
  `meta_canonical_schema_version` varchar(16) NOT NULL COMMENT "Canonical模型版本",
  `meta_transform_version` varchar(64) NOT NULL COMMENT "转换逻辑版本",
  `meta_process_time` datetime(3) NOT NULL COMMENT "事实处理时间",
  `meta_sink_sequence` bigint NOT NULL COMMENT "Doris写入顺序版本",
  `src_shop_id` varchar(128) NULL COMMENT "平台原始门店标识",
  `src_payment_method` varchar(64) NULL COMMENT "平台原始支付方式",
  `src_business_category` varchar(32) NULL COMMENT "来源业务大类",
  `src_business_subcategory` varchar(32) NULL COMMENT "来源业务小类",
  `src_order_source` varchar(32) NULL COMMENT "来源订单来源",
  `src_recharge_status` varchar(32) NULL COMMENT "平台原始充值状态",
  `src_updated_time` datetime(3) NULL COMMENT "来源对象更新时间",
  `src_version` bigint NOT NULL COMMENT "当前事实来源版本",
  `src_last_event_id` bigint NOT NULL COMMENT "最后写入的来源事件ID",
  `src_last_storage_id` bigint NOT NULL COMMENT "最后写入事件的Storage ID",
  `src_last_payload_sha256` char(64) NOT NULL COMMENT "最后写入原文SHA-256",
  `src_event_received_time` datetime(3) NOT NULL COMMENT "事件接收时间"
) ENGINE=OLAP
UNIQUE KEY(`recharge_date`, `platform_code`, `recharge_id`)
COMMENT '统一充值当前事实物理表 v1'
PARTITION BY RANGE(`recharge_date`)
(PARTITION p202110 VALUES [('2021-10-01'), ('2021-11-01')),
PARTITION p202111 VALUES [('2021-11-01'), ('2021-12-01')),
PARTITION p202112 VALUES [('2021-12-01'), ('2022-01-01')),
PARTITION p202201 VALUES [('2022-01-01'), ('2022-02-01')),
PARTITION p202202 VALUES [('2022-02-01'), ('2022-03-01')),
PARTITION p202203 VALUES [('2022-03-01'), ('2022-04-01')),
PARTITION p202204 VALUES [('2022-04-01'), ('2022-05-01')),
PARTITION p202205 VALUES [('2022-05-01'), ('2022-06-01')),
PARTITION p202206 VALUES [('2022-06-01'), ('2022-07-01')),
PARTITION p202207 VALUES [('2022-07-01'), ('2022-08-01')),
PARTITION p202208 VALUES [('2022-08-01'), ('2022-09-01')),
PARTITION p202209 VALUES [('2022-09-01'), ('2022-10-01')),
PARTITION p202210 VALUES [('2022-10-01'), ('2022-11-01')),
PARTITION p202211 VALUES [('2022-11-01'), ('2022-12-01')),
PARTITION p202212 VALUES [('2022-12-01'), ('2023-01-01')),
PARTITION p202301 VALUES [('2023-01-01'), ('2023-02-01')),
PARTITION p202302 VALUES [('2023-02-01'), ('2023-03-01')),
PARTITION p202303 VALUES [('2023-03-01'), ('2023-04-01')),
PARTITION p202304 VALUES [('2023-04-01'), ('2023-05-01')),
PARTITION p202305 VALUES [('2023-05-01'), ('2023-06-01')),
PARTITION p202306 VALUES [('2023-06-01'), ('2023-07-01')),
PARTITION p202307 VALUES [('2023-07-01'), ('2023-08-01')),
PARTITION p202308 VALUES [('2023-08-01'), ('2023-09-01')),
PARTITION p202309 VALUES [('2023-09-01'), ('2023-10-01')),
PARTITION p202310 VALUES [('2023-10-01'), ('2023-11-01')),
PARTITION p202311 VALUES [('2023-11-01'), ('2023-12-01')),
PARTITION p202312 VALUES [('2023-12-01'), ('2024-01-01')),
PARTITION p202401 VALUES [('2024-01-01'), ('2024-02-01')),
PARTITION p202402 VALUES [('2024-02-01'), ('2024-03-01')),
PARTITION p202403 VALUES [('2024-03-01'), ('2024-04-01')),
PARTITION p202404 VALUES [('2024-04-01'), ('2024-05-01')),
PARTITION p202405 VALUES [('2024-05-01'), ('2024-06-01')),
PARTITION p202406 VALUES [('2024-06-01'), ('2024-07-01')),
PARTITION p202407 VALUES [('2024-07-01'), ('2024-08-01')),
PARTITION p202408 VALUES [('2024-08-01'), ('2024-09-01')),
PARTITION p202409 VALUES [('2024-09-01'), ('2024-10-01')),
PARTITION p202410 VALUES [('2024-10-01'), ('2024-11-01')),
PARTITION p202411 VALUES [('2024-11-01'), ('2024-12-01')),
PARTITION p202412 VALUES [('2024-12-01'), ('2025-01-01')),
PARTITION p202501 VALUES [('2025-01-01'), ('2025-02-01')),
PARTITION p202502 VALUES [('2025-02-01'), ('2025-03-01')),
PARTITION p202503 VALUES [('2025-03-01'), ('2025-04-01')),
PARTITION p202504 VALUES [('2025-04-01'), ('2025-05-01')),
PARTITION p202505 VALUES [('2025-05-01'), ('2025-06-01')),
PARTITION p202506 VALUES [('2025-06-01'), ('2025-07-01')),
PARTITION p202507 VALUES [('2025-07-01'), ('2025-08-01')),
PARTITION p202508 VALUES [('2025-08-01'), ('2025-09-01')),
PARTITION p202509 VALUES [('2025-09-01'), ('2025-10-01')),
PARTITION p202510 VALUES [('2025-10-01'), ('2025-11-01')),
PARTITION p202511 VALUES [('2025-11-01'), ('2025-12-01')),
PARTITION p202512 VALUES [('2025-12-01'), ('2026-01-01')),
PARTITION p202601 VALUES [('2026-01-01'), ('2026-02-01')),
PARTITION p202602 VALUES [('2026-02-01'), ('2026-03-01')),
PARTITION p202603 VALUES [('2026-03-01'), ('2026-04-01')),
PARTITION p202604 VALUES [('2026-04-01'), ('2026-05-01')),
PARTITION p202605 VALUES [('2026-05-01'), ('2026-06-01')),
PARTITION p202606 VALUES [('2026-06-01'), ('2026-07-01')),
PARTITION p202607 VALUES [('2026-07-01'), ('2026-08-01')),
PARTITION p202608 VALUES [('2026-08-01'), ('2026-09-01')),
PARTITION p202609 VALUES [('2026-09-01'), ('2026-10-01')),
PARTITION p202610 VALUES [('2026-10-01'), ('2026-11-01')),
PARTITION p202611 VALUES [('2026-11-01'), ('2026-12-01')),
PARTITION p202612 VALUES [('2026-12-01'), ('2027-01-01')),
PARTITION p202701 VALUES [('2027-01-01'), ('2027-02-01')),
PARTITION p202702 VALUES [('2027-02-01'), ('2027-03-01')),
PARTITION p202703 VALUES [('2027-03-01'), ('2027-04-01')),
PARTITION p202704 VALUES [('2027-04-01'), ('2027-05-01')),
PARTITION p202705 VALUES [('2027-05-01'), ('2027-06-01')),
PARTITION p202706 VALUES [('2027-06-01'), ('2027-07-01')),
PARTITION p202707 VALUES [('2027-07-01'), ('2027-08-01')),
PARTITION p202708 VALUES [('2027-08-01'), ('2027-09-01')),
PARTITION p202709 VALUES [('2027-09-01'), ('2027-10-01')),
PARTITION p202710 VALUES [('2027-10-01'), ('2027-11-01')),
PARTITION p202711 VALUES [('2027-11-01'), ('2027-12-01')))
DISTRIBUTED BY HASH(`platform_code`, `recharge_id`) BUCKETS 8
PROPERTIES (
"replication_allocation" = "tag.location.default: 1",
"min_load_replica_num" = "-1",
"is_being_synced" = "false",
"dynamic_partition.enable" = "true",
"dynamic_partition.time_unit" = "MONTH",
"dynamic_partition.time_zone" = "Asia/Shanghai",
"dynamic_partition.start" = "-2147483648",
"dynamic_partition.end" = "12",
"dynamic_partition.prefix" = "p",
"dynamic_partition.replication_allocation" = "tag.location.default: 1",
"dynamic_partition.buckets" = "8",
"dynamic_partition.create_history_partition" = "false",
"dynamic_partition.history_partition_num" = "-1",
"dynamic_partition.hot_partition_num" = "0",
"dynamic_partition.reserved_history_periods" = "NULL",
"dynamic_partition.storage_policy" = "",
"dynamic_partition.start_day_of_month" = "1",
"storage_medium" = "hdd",
"storage_format" = "V2",
"inverted_index_storage_format" = "V3",
"enable_unique_key_merge_on_write" = "true",
"light_schema_change" = "true",
"function_column.sequence_col" = "meta_sink_sequence",
"disable_auto_compaction" = "false",
"binlog.enable" = "true",
"binlog.ttl_seconds" = "604800",
"binlog.max_bytes" = "1073741824",
"binlog.max_history_nums" = "9223372036854775807",
"enable_single_replica_compaction" = "false",
"group_commit_interval_ms" = "10000",
"group_commit_data_bytes" = "134217728",
"enable_mow_light_delete" = "false"
);
```

### `dwd_trade_refund_fact_v1`

```sql
CREATE TABLE `dwd_trade_refund_fact_v1` (
  `refund_date` date NOT NULL COMMENT "退款业务发生日期，优先申请时间、其次成功或关闭时间，稳定分区键",
  `platform_code` varchar(16) NOT NULL COMMENT "平台编码",
  `refund_id` varchar(128) NOT NULL COMMENT "退款单号",
  `platform_order_id` varchar(128) NOT NULL COMMENT "平台订单号",
  `original_payment_id` varchar(128) NULL COMMENT "原支付流水号，来源未提供稳定关联时为空",
  `internal_shop_id` bigint NULL COMMENT "跨服务商稳定的统一门店ID",
  `brand_id` bigint NULL COMMENT "内部品牌ID",
  `refund_type` varchar(32) NOT NULL COMMENT "退款类型",
  `refund_status` varchar(32) NOT NULL COMMENT "标准退款状态",
  `apply_time` datetime(3) NULL COMMENT "退款申请时间",
  `success_time` datetime(3) NULL COMMENT "退款成功时间",
  `closed_time` datetime(3) NULL COMMENT "关闭时间",
  `refund_amount` bigint NULL COMMENT "退款金额，单位分",
  `merchant_bear_amount` bigint NULL COMMENT "商家承担金额，单位分",
  `platform_bear_amount` bigint NULL COMMENT "平台承担金额，单位分",
  `other_bear_amount` bigint NULL COMMENT "其他方承担金额，单位分",
  `refund_reason_code` varchar(64) NULL COMMENT "退款原因编码",
  `refund_reason` varchar(1024) NULL COMMENT "退款原因说明",
  `arbitration_result` varchar(256) NULL COMMENT "仲裁处理结果",
  `meta_canonical_schema_version` varchar(16) NOT NULL COMMENT "Canonical模型版本",
  `meta_transform_version` varchar(64) NOT NULL COMMENT "转换逻辑版本",
  `meta_process_time` datetime(3) NOT NULL COMMENT "事实处理时间",
  `meta_sink_sequence` bigint NOT NULL COMMENT "Doris写入顺序版本",
  `src_updated_time` datetime(3) NULL COMMENT "来源对象更新时间",
  `src_version` bigint NOT NULL COMMENT "当前事实来源版本",
  `src_last_event_id` bigint NOT NULL COMMENT "最后写入的来源事件ID",
  `src_last_storage_id` bigint NOT NULL COMMENT "最后写入事件的Storage ID",
  `src_last_payload_sha256` char(64) NOT NULL COMMENT "最后写入原文SHA-256",
  `src_event_received_time` datetime(3) NOT NULL COMMENT "事件接收时间",
  `src_refund_type` varchar(64) NULL COMMENT "平台原始退款类型",
  `refund_origin` varchar(32) NULL COMMENT "退款来源分类",
  `flow_kind` varchar(32) NULL COMMENT "退款或取消流程类型",
  `src_refund_status` varchar(64) NULL COMMENT "平台原始退款状态"
) ENGINE=OLAP
UNIQUE KEY(`refund_date`, `platform_code`, `refund_id`)
COMMENT '统一退款当前事实物理表 v1'
PARTITION BY RANGE(`refund_date`)
(PARTITION p202110 VALUES [('2021-10-01'), ('2021-11-01')),
PARTITION p202111 VALUES [('2021-11-01'), ('2021-12-01')),
PARTITION p202112 VALUES [('2021-12-01'), ('2022-01-01')),
PARTITION p202201 VALUES [('2022-01-01'), ('2022-02-01')),
PARTITION p202202 VALUES [('2022-02-01'), ('2022-03-01')),
PARTITION p202203 VALUES [('2022-03-01'), ('2022-04-01')),
PARTITION p202204 VALUES [('2022-04-01'), ('2022-05-01')),
PARTITION p202205 VALUES [('2022-05-01'), ('2022-06-01')),
PARTITION p202206 VALUES [('2022-06-01'), ('2022-07-01')),
PARTITION p202207 VALUES [('2022-07-01'), ('2022-08-01')),
PARTITION p202208 VALUES [('2022-08-01'), ('2022-09-01')),
PARTITION p202209 VALUES [('2022-09-01'), ('2022-10-01')),
PARTITION p202210 VALUES [('2022-10-01'), ('2022-11-01')),
PARTITION p202211 VALUES [('2022-11-01'), ('2022-12-01')),
PARTITION p202212 VALUES [('2022-12-01'), ('2023-01-01')),
PARTITION p202301 VALUES [('2023-01-01'), ('2023-02-01')),
PARTITION p202302 VALUES [('2023-02-01'), ('2023-03-01')),
PARTITION p202303 VALUES [('2023-03-01'), ('2023-04-01')),
PARTITION p202304 VALUES [('2023-04-01'), ('2023-05-01')),
PARTITION p202305 VALUES [('2023-05-01'), ('2023-06-01')),
PARTITION p202306 VALUES [('2023-06-01'), ('2023-07-01')),
PARTITION p202307 VALUES [('2023-07-01'), ('2023-08-01')),
PARTITION p202308 VALUES [('2023-08-01'), ('2023-09-01')),
PARTITION p202309 VALUES [('2023-09-01'), ('2023-10-01')),
PARTITION p202310 VALUES [('2023-10-01'), ('2023-11-01')),
PARTITION p202311 VALUES [('2023-11-01'), ('2023-12-01')),
PARTITION p202312 VALUES [('2023-12-01'), ('2024-01-01')),
PARTITION p202401 VALUES [('2024-01-01'), ('2024-02-01')),
PARTITION p202402 VALUES [('2024-02-01'), ('2024-03-01')),
PARTITION p202403 VALUES [('2024-03-01'), ('2024-04-01')),
PARTITION p202404 VALUES [('2024-04-01'), ('2024-05-01')),
PARTITION p202405 VALUES [('2024-05-01'), ('2024-06-01')),
PARTITION p202406 VALUES [('2024-06-01'), ('2024-07-01')),
PARTITION p202407 VALUES [('2024-07-01'), ('2024-08-01')),
PARTITION p202408 VALUES [('2024-08-01'), ('2024-09-01')),
PARTITION p202409 VALUES [('2024-09-01'), ('2024-10-01')),
PARTITION p202410 VALUES [('2024-10-01'), ('2024-11-01')),
PARTITION p202411 VALUES [('2024-11-01'), ('2024-12-01')),
PARTITION p202412 VALUES [('2024-12-01'), ('2025-01-01')),
PARTITION p202501 VALUES [('2025-01-01'), ('2025-02-01')),
PARTITION p202502 VALUES [('2025-02-01'), ('2025-03-01')),
PARTITION p202503 VALUES [('2025-03-01'), ('2025-04-01')),
PARTITION p202504 VALUES [('2025-04-01'), ('2025-05-01')),
PARTITION p202505 VALUES [('2025-05-01'), ('2025-06-01')),
PARTITION p202506 VALUES [('2025-06-01'), ('2025-07-01')),
PARTITION p202507 VALUES [('2025-07-01'), ('2025-08-01')),
PARTITION p202508 VALUES [('2025-08-01'), ('2025-09-01')),
PARTITION p202509 VALUES [('2025-09-01'), ('2025-10-01')),
PARTITION p202510 VALUES [('2025-10-01'), ('2025-11-01')),
PARTITION p202511 VALUES [('2025-11-01'), ('2025-12-01')),
PARTITION p202512 VALUES [('2025-12-01'), ('2026-01-01')),
PARTITION p202601 VALUES [('2026-01-01'), ('2026-02-01')),
PARTITION p202602 VALUES [('2026-02-01'), ('2026-03-01')),
PARTITION p202603 VALUES [('2026-03-01'), ('2026-04-01')),
PARTITION p202604 VALUES [('2026-04-01'), ('2026-05-01')),
PARTITION p202605 VALUES [('2026-05-01'), ('2026-06-01')),
PARTITION p202606 VALUES [('2026-06-01'), ('2026-07-01')),
PARTITION p202607 VALUES [('2026-07-01'), ('2026-08-01')),
PARTITION p202608 VALUES [('2026-08-01'), ('2026-09-01')),
PARTITION p202609 VALUES [('2026-09-01'), ('2026-10-01')),
PARTITION p202610 VALUES [('2026-10-01'), ('2026-11-01')),
PARTITION p202611 VALUES [('2026-11-01'), ('2026-12-01')),
PARTITION p202612 VALUES [('2026-12-01'), ('2027-01-01')),
PARTITION p202701 VALUES [('2027-01-01'), ('2027-02-01')),
PARTITION p202702 VALUES [('2027-02-01'), ('2027-03-01')),
PARTITION p202703 VALUES [('2027-03-01'), ('2027-04-01')),
PARTITION p202704 VALUES [('2027-04-01'), ('2027-05-01')),
PARTITION p202705 VALUES [('2027-05-01'), ('2027-06-01')),
PARTITION p202706 VALUES [('2027-06-01'), ('2027-07-01')),
PARTITION p202707 VALUES [('2027-07-01'), ('2027-08-01')),
PARTITION p202708 VALUES [('2027-08-01'), ('2027-09-01')),
PARTITION p202709 VALUES [('2027-09-01'), ('2027-10-01')),
PARTITION p202710 VALUES [('2027-10-01'), ('2027-11-01')),
PARTITION p202711 VALUES [('2027-11-01'), ('2027-12-01')))
DISTRIBUTED BY HASH(`platform_code`, `refund_id`) BUCKETS 8
PROPERTIES (
"replication_allocation" = "tag.location.default: 1",
"min_load_replica_num" = "-1",
"is_being_synced" = "false",
"dynamic_partition.enable" = "true",
"dynamic_partition.time_unit" = "MONTH",
"dynamic_partition.time_zone" = "Asia/Shanghai",
"dynamic_partition.start" = "-2147483648",
"dynamic_partition.end" = "12",
"dynamic_partition.prefix" = "p",
"dynamic_partition.replication_allocation" = "tag.location.default: 1",
"dynamic_partition.buckets" = "8",
"dynamic_partition.create_history_partition" = "false",
"dynamic_partition.history_partition_num" = "-1",
"dynamic_partition.hot_partition_num" = "0",
"dynamic_partition.reserved_history_periods" = "NULL",
"dynamic_partition.storage_policy" = "",
"dynamic_partition.start_day_of_month" = "1",
"storage_medium" = "hdd",
"storage_format" = "V2",
"inverted_index_storage_format" = "V3",
"enable_unique_key_merge_on_write" = "true",
"light_schema_change" = "true",
"function_column.sequence_col" = "meta_sink_sequence",
"disable_auto_compaction" = "false",
"binlog.enable" = "true",
"binlog.ttl_seconds" = "604800",
"binlog.max_bytes" = "1073741824",
"binlog.max_history_nums" = "9223372036854775807",
"enable_single_replica_compaction" = "false",
"group_commit_interval_ms" = "10000",
"group_commit_data_bytes" = "134217728",
"enable_mow_light_delete" = "false"
);
```

### `dwd_trade_refund_item_fact_v1`

```sql
CREATE TABLE `dwd_trade_refund_item_fact_v1` (
  `refund_date` date NOT NULL COMMENT "退款业务发生日期，与退款主事实保持一致，稳定分区键",
  `platform_code` varchar(16) NOT NULL COMMENT "平台编码",
  `refund_id` varchar(128) NOT NULL COMMENT "退款单号",
  `refund_item_line_key` varchar(128) NOT NULL COMMENT "退款商品稳定行键",
  `platform_order_id` varchar(128) NOT NULL COMMENT "平台订单号",
  `internal_shop_id` bigint NULL COMMENT "跨服务商稳定的统一门店ID",
  `brand_id` bigint NULL COMMENT "内部品牌ID",
  `internal_product_id` bigint NULL COMMENT "内部商品ID",
  `internal_category_id` bigint NULL COMMENT "内部类目ID",
  `order_item_line_key` varchar(128) NULL COMMENT "原订单商品行键",
  `platform_item_id` varchar(128) NULL COMMENT "平台商品ID",
  `platform_sku_id` varchar(128) NULL COMMENT "平台SKU ID",
  `item_name` varchar(512) NULL COMMENT "商品名称",
  `refund_quantity` decimal(18,3) NULL COMMENT "退款商品数量",
  `refund_amount` bigint NULL COMMENT "退款金额，单位分",
  `merchant_bear_amount` bigint NULL COMMENT "商家承担金额，单位分",
  `platform_bear_amount` bigint NULL COMMENT "平台承担金额，单位分",
  `meta_canonical_schema_version` varchar(16) NOT NULL COMMENT "Canonical模型版本",
  `meta_transform_version` varchar(64) NOT NULL COMMENT "转换逻辑版本",
  `meta_process_time` datetime(3) NOT NULL COMMENT "事实处理时间",
  `meta_sink_sequence` bigint NOT NULL COMMENT "Doris写入顺序版本",
  `src_updated_time` datetime(3) NULL COMMENT "来源对象更新时间",
  `src_version` bigint NOT NULL COMMENT "当前事实来源版本",
  `src_last_event_id` bigint NOT NULL COMMENT "最后写入的来源事件ID",
  `src_last_storage_id` bigint NOT NULL COMMENT "最后写入事件的Storage ID",
  `src_last_payload_sha256` char(64) NOT NULL COMMENT "最后写入原文SHA-256",
  `src_event_received_time` datetime(3) NOT NULL COMMENT "事件接收时间",
  `source_merchant_code` varchar(64) NULL COMMENT "来源账号",
  `resolved_platform_sku_id` varchar(128) NULL COMMENT "已识别的可售规格编号",
  `platform_shop_id` varchar(128) NULL COMMENT "平台门店号",
  `src_platform_product_id` varchar(128) NULL COMMENT "平台可售SKU编号，与SSM平台商品编号一致"
) ENGINE=OLAP
UNIQUE KEY(`refund_date`, `platform_code`, `refund_id`, `refund_item_line_key`)
COMMENT '统一退款商品当前事实物理表 v1'
PARTITION BY RANGE(`refund_date`)
(PARTITION p202110 VALUES [('2021-10-01'), ('2021-11-01')),
PARTITION p202111 VALUES [('2021-11-01'), ('2021-12-01')),
PARTITION p202112 VALUES [('2021-12-01'), ('2022-01-01')),
PARTITION p202201 VALUES [('2022-01-01'), ('2022-02-01')),
PARTITION p202202 VALUES [('2022-02-01'), ('2022-03-01')),
PARTITION p202203 VALUES [('2022-03-01'), ('2022-04-01')),
PARTITION p202204 VALUES [('2022-04-01'), ('2022-05-01')),
PARTITION p202205 VALUES [('2022-05-01'), ('2022-06-01')),
PARTITION p202206 VALUES [('2022-06-01'), ('2022-07-01')),
PARTITION p202207 VALUES [('2022-07-01'), ('2022-08-01')),
PARTITION p202208 VALUES [('2022-08-01'), ('2022-09-01')),
PARTITION p202209 VALUES [('2022-09-01'), ('2022-10-01')),
PARTITION p202210 VALUES [('2022-10-01'), ('2022-11-01')),
PARTITION p202211 VALUES [('2022-11-01'), ('2022-12-01')),
PARTITION p202212 VALUES [('2022-12-01'), ('2023-01-01')),
PARTITION p202301 VALUES [('2023-01-01'), ('2023-02-01')),
PARTITION p202302 VALUES [('2023-02-01'), ('2023-03-01')),
PARTITION p202303 VALUES [('2023-03-01'), ('2023-04-01')),
PARTITION p202304 VALUES [('2023-04-01'), ('2023-05-01')),
PARTITION p202305 VALUES [('2023-05-01'), ('2023-06-01')),
PARTITION p202306 VALUES [('2023-06-01'), ('2023-07-01')),
PARTITION p202307 VALUES [('2023-07-01'), ('2023-08-01')),
PARTITION p202308 VALUES [('2023-08-01'), ('2023-09-01')),
PARTITION p202309 VALUES [('2023-09-01'), ('2023-10-01')),
PARTITION p202310 VALUES [('2023-10-01'), ('2023-11-01')),
PARTITION p202311 VALUES [('2023-11-01'), ('2023-12-01')),
PARTITION p202312 VALUES [('2023-12-01'), ('2024-01-01')),
PARTITION p202401 VALUES [('2024-01-01'), ('2024-02-01')),
PARTITION p202402 VALUES [('2024-02-01'), ('2024-03-01')),
PARTITION p202403 VALUES [('2024-03-01'), ('2024-04-01')),
PARTITION p202404 VALUES [('2024-04-01'), ('2024-05-01')),
PARTITION p202405 VALUES [('2024-05-01'), ('2024-06-01')),
PARTITION p202406 VALUES [('2024-06-01'), ('2024-07-01')),
PARTITION p202407 VALUES [('2024-07-01'), ('2024-08-01')),
PARTITION p202408 VALUES [('2024-08-01'), ('2024-09-01')),
PARTITION p202409 VALUES [('2024-09-01'), ('2024-10-01')),
PARTITION p202410 VALUES [('2024-10-01'), ('2024-11-01')),
PARTITION p202411 VALUES [('2024-11-01'), ('2024-12-01')),
PARTITION p202412 VALUES [('2024-12-01'), ('2025-01-01')),
PARTITION p202501 VALUES [('2025-01-01'), ('2025-02-01')),
PARTITION p202502 VALUES [('2025-02-01'), ('2025-03-01')),
PARTITION p202503 VALUES [('2025-03-01'), ('2025-04-01')),
PARTITION p202504 VALUES [('2025-04-01'), ('2025-05-01')),
PARTITION p202505 VALUES [('2025-05-01'), ('2025-06-01')),
PARTITION p202506 VALUES [('2025-06-01'), ('2025-07-01')),
PARTITION p202507 VALUES [('2025-07-01'), ('2025-08-01')),
PARTITION p202508 VALUES [('2025-08-01'), ('2025-09-01')),
PARTITION p202509 VALUES [('2025-09-01'), ('2025-10-01')),
PARTITION p202510 VALUES [('2025-10-01'), ('2025-11-01')),
PARTITION p202511 VALUES [('2025-11-01'), ('2025-12-01')),
PARTITION p202512 VALUES [('2025-12-01'), ('2026-01-01')),
PARTITION p202601 VALUES [('2026-01-01'), ('2026-02-01')),
PARTITION p202602 VALUES [('2026-02-01'), ('2026-03-01')),
PARTITION p202603 VALUES [('2026-03-01'), ('2026-04-01')),
PARTITION p202604 VALUES [('2026-04-01'), ('2026-05-01')),
PARTITION p202605 VALUES [('2026-05-01'), ('2026-06-01')),
PARTITION p202606 VALUES [('2026-06-01'), ('2026-07-01')),
PARTITION p202607 VALUES [('2026-07-01'), ('2026-08-01')),
PARTITION p202608 VALUES [('2026-08-01'), ('2026-09-01')),
PARTITION p202609 VALUES [('2026-09-01'), ('2026-10-01')),
PARTITION p202610 VALUES [('2026-10-01'), ('2026-11-01')),
PARTITION p202611 VALUES [('2026-11-01'), ('2026-12-01')),
PARTITION p202612 VALUES [('2026-12-01'), ('2027-01-01')),
PARTITION p202701 VALUES [('2027-01-01'), ('2027-02-01')),
PARTITION p202702 VALUES [('2027-02-01'), ('2027-03-01')),
PARTITION p202703 VALUES [('2027-03-01'), ('2027-04-01')),
PARTITION p202704 VALUES [('2027-04-01'), ('2027-05-01')),
PARTITION p202705 VALUES [('2027-05-01'), ('2027-06-01')),
PARTITION p202706 VALUES [('2027-06-01'), ('2027-07-01')),
PARTITION p202707 VALUES [('2027-07-01'), ('2027-08-01')),
PARTITION p202708 VALUES [('2027-08-01'), ('2027-09-01')),
PARTITION p202709 VALUES [('2027-09-01'), ('2027-10-01')),
PARTITION p202710 VALUES [('2027-10-01'), ('2027-11-01')),
PARTITION p202711 VALUES [('2027-11-01'), ('2027-12-01')))
DISTRIBUTED BY HASH(`platform_code`, `refund_id`) BUCKETS 8
PROPERTIES (
"replication_allocation" = "tag.location.default: 1",
"min_load_replica_num" = "-1",
"is_being_synced" = "false",
"dynamic_partition.enable" = "true",
"dynamic_partition.time_unit" = "MONTH",
"dynamic_partition.time_zone" = "Asia/Shanghai",
"dynamic_partition.start" = "-2147483648",
"dynamic_partition.end" = "12",
"dynamic_partition.prefix" = "p",
"dynamic_partition.replication_allocation" = "tag.location.default: 1",
"dynamic_partition.buckets" = "8",
"dynamic_partition.create_history_partition" = "false",
"dynamic_partition.history_partition_num" = "-1",
"dynamic_partition.hot_partition_num" = "0",
"dynamic_partition.reserved_history_periods" = "NULL",
"dynamic_partition.storage_policy" = "",
"dynamic_partition.start_day_of_month" = "1",
"storage_medium" = "hdd",
"storage_format" = "V2",
"inverted_index_storage_format" = "V3",
"enable_unique_key_merge_on_write" = "true",
"enable_unique_key_skip_bitmap_column" = "true",
"light_schema_change" = "true",
"function_column.sequence_col" = "meta_sink_sequence",
"disable_auto_compaction" = "false",
"binlog.enable" = "true",
"binlog.ttl_seconds" = "604800",
"binlog.max_bytes" = "1073741824",
"binlog.max_history_nums" = "9223372036854775807",
"enable_single_replica_compaction" = "false",
"group_commit_interval_ms" = "10000",
"group_commit_data_bytes" = "134217728",
"enable_mow_light_delete" = "false"
);
```

### `dwd_trade_settlement_entry_fact_v1`

```sql
CREATE TABLE `dwd_trade_settlement_entry_fact_v1` (
  `bill_date` date NOT NULL COMMENT "账单日期，稳定分区键",
  `platform_code` varchar(16) NOT NULL COMMENT "平台编码",
  `finance_order_id` varchar(128) NOT NULL COMMENT "财务订单号",
  `entry_line_key` varchar(128) NOT NULL COMMENT "结算科目稳定行键",
  `platform_order_id` varchar(128) NULL COMMENT "平台订单号",
  `internal_shop_id` bigint NULL COMMENT "跨服务商稳定的统一门店ID",
  `brand_id` bigint NULL COMMENT "内部品牌ID",
  `entry_type` varchar(32) NOT NULL COMMENT "结算科目类型",
  `direction` varchar(8) NOT NULL COMMENT "资金方向",
  `amount` bigint NOT NULL COMMENT "结算科目金额，单位分",
  `currency_code` char(3) NOT NULL COMMENT "ISO 4217币种编码",
  `settlement_status` varchar(32) NULL COMMENT "结算状态",
  `settlement_time` datetime(3) NULL COMMENT "结算时间",
  `meta_canonical_schema_version` varchar(16) NOT NULL COMMENT "Canonical模型版本",
  `meta_transform_version` varchar(64) NOT NULL COMMENT "转换逻辑版本",
  `meta_process_time` datetime(3) NOT NULL COMMENT "事实处理时间",
  `meta_sink_sequence` bigint NOT NULL COMMENT "Doris写入顺序版本",
  `src_updated_time` datetime(3) NULL COMMENT "来源对象更新时间",
  `src_version` bigint NOT NULL COMMENT "当前事实来源版本",
  `src_last_event_id` bigint NOT NULL COMMENT "最后写入的来源事件ID",
  `src_last_storage_id` bigint NOT NULL COMMENT "最后写入事件的Storage ID",
  `src_last_payload_sha256` char(64) NOT NULL COMMENT "最后写入原文SHA-256",
  `src_event_received_time` datetime(3) NOT NULL COMMENT "事件接收时间"
) ENGINE=OLAP
UNIQUE KEY(`bill_date`, `platform_code`, `finance_order_id`, `entry_line_key`)
COMMENT '统一结算科目当前事实物理表 v1'
PARTITION BY RANGE(`bill_date`)
(PARTITION p202110 VALUES [('2021-10-01'), ('2021-11-01')),
PARTITION p202111 VALUES [('2021-11-01'), ('2021-12-01')),
PARTITION p202112 VALUES [('2021-12-01'), ('2022-01-01')),
PARTITION p202201 VALUES [('2022-01-01'), ('2022-02-01')),
PARTITION p202202 VALUES [('2022-02-01'), ('2022-03-01')),
PARTITION p202203 VALUES [('2022-03-01'), ('2022-04-01')),
PARTITION p202204 VALUES [('2022-04-01'), ('2022-05-01')),
PARTITION p202205 VALUES [('2022-05-01'), ('2022-06-01')),
PARTITION p202206 VALUES [('2022-06-01'), ('2022-07-01')),
PARTITION p202207 VALUES [('2022-07-01'), ('2022-08-01')),
PARTITION p202208 VALUES [('2022-08-01'), ('2022-09-01')),
PARTITION p202209 VALUES [('2022-09-01'), ('2022-10-01')),
PARTITION p202210 VALUES [('2022-10-01'), ('2022-11-01')),
PARTITION p202211 VALUES [('2022-11-01'), ('2022-12-01')),
PARTITION p202212 VALUES [('2022-12-01'), ('2023-01-01')),
PARTITION p202301 VALUES [('2023-01-01'), ('2023-02-01')),
PARTITION p202302 VALUES [('2023-02-01'), ('2023-03-01')),
PARTITION p202303 VALUES [('2023-03-01'), ('2023-04-01')),
PARTITION p202304 VALUES [('2023-04-01'), ('2023-05-01')),
PARTITION p202305 VALUES [('2023-05-01'), ('2023-06-01')),
PARTITION p202306 VALUES [('2023-06-01'), ('2023-07-01')),
PARTITION p202307 VALUES [('2023-07-01'), ('2023-08-01')),
PARTITION p202308 VALUES [('2023-08-01'), ('2023-09-01')),
PARTITION p202309 VALUES [('2023-09-01'), ('2023-10-01')),
PARTITION p202310 VALUES [('2023-10-01'), ('2023-11-01')),
PARTITION p202311 VALUES [('2023-11-01'), ('2023-12-01')),
PARTITION p202312 VALUES [('2023-12-01'), ('2024-01-01')),
PARTITION p202401 VALUES [('2024-01-01'), ('2024-02-01')),
PARTITION p202402 VALUES [('2024-02-01'), ('2024-03-01')),
PARTITION p202403 VALUES [('2024-03-01'), ('2024-04-01')),
PARTITION p202404 VALUES [('2024-04-01'), ('2024-05-01')),
PARTITION p202405 VALUES [('2024-05-01'), ('2024-06-01')),
PARTITION p202406 VALUES [('2024-06-01'), ('2024-07-01')),
PARTITION p202407 VALUES [('2024-07-01'), ('2024-08-01')),
PARTITION p202408 VALUES [('2024-08-01'), ('2024-09-01')),
PARTITION p202409 VALUES [('2024-09-01'), ('2024-10-01')),
PARTITION p202410 VALUES [('2024-10-01'), ('2024-11-01')),
PARTITION p202411 VALUES [('2024-11-01'), ('2024-12-01')),
PARTITION p202412 VALUES [('2024-12-01'), ('2025-01-01')),
PARTITION p202501 VALUES [('2025-01-01'), ('2025-02-01')),
PARTITION p202502 VALUES [('2025-02-01'), ('2025-03-01')),
PARTITION p202503 VALUES [('2025-03-01'), ('2025-04-01')),
PARTITION p202504 VALUES [('2025-04-01'), ('2025-05-01')),
PARTITION p202505 VALUES [('2025-05-01'), ('2025-06-01')),
PARTITION p202506 VALUES [('2025-06-01'), ('2025-07-01')),
PARTITION p202507 VALUES [('2025-07-01'), ('2025-08-01')),
PARTITION p202508 VALUES [('2025-08-01'), ('2025-09-01')),
PARTITION p202509 VALUES [('2025-09-01'), ('2025-10-01')),
PARTITION p202510 VALUES [('2025-10-01'), ('2025-11-01')),
PARTITION p202511 VALUES [('2025-11-01'), ('2025-12-01')),
PARTITION p202512 VALUES [('2025-12-01'), ('2026-01-01')),
PARTITION p202601 VALUES [('2026-01-01'), ('2026-02-01')),
PARTITION p202602 VALUES [('2026-02-01'), ('2026-03-01')),
PARTITION p202603 VALUES [('2026-03-01'), ('2026-04-01')),
PARTITION p202604 VALUES [('2026-04-01'), ('2026-05-01')),
PARTITION p202605 VALUES [('2026-05-01'), ('2026-06-01')),
PARTITION p202606 VALUES [('2026-06-01'), ('2026-07-01')),
PARTITION p202607 VALUES [('2026-07-01'), ('2026-08-01')),
PARTITION p202608 VALUES [('2026-08-01'), ('2026-09-01')),
PARTITION p202609 VALUES [('2026-09-01'), ('2026-10-01')),
PARTITION p202610 VALUES [('2026-10-01'), ('2026-11-01')),
PARTITION p202611 VALUES [('2026-11-01'), ('2026-12-01')),
PARTITION p202612 VALUES [('2026-12-01'), ('2027-01-01')),
PARTITION p202701 VALUES [('2027-01-01'), ('2027-02-01')),
PARTITION p202702 VALUES [('2027-02-01'), ('2027-03-01')),
PARTITION p202703 VALUES [('2027-03-01'), ('2027-04-01')),
PARTITION p202704 VALUES [('2027-04-01'), ('2027-05-01')),
PARTITION p202705 VALUES [('2027-05-01'), ('2027-06-01')),
PARTITION p202706 VALUES [('2027-06-01'), ('2027-07-01')),
PARTITION p202707 VALUES [('2027-07-01'), ('2027-08-01')),
PARTITION p202708 VALUES [('2027-08-01'), ('2027-09-01')),
PARTITION p202709 VALUES [('2027-09-01'), ('2027-10-01')),
PARTITION p202710 VALUES [('2027-10-01'), ('2027-11-01')),
PARTITION p202711 VALUES [('2027-11-01'), ('2027-12-01')))
DISTRIBUTED BY HASH(`platform_code`, `finance_order_id`) BUCKETS 8
PROPERTIES (
"replication_allocation" = "tag.location.default: 1",
"min_load_replica_num" = "-1",
"is_being_synced" = "false",
"dynamic_partition.enable" = "true",
"dynamic_partition.time_unit" = "MONTH",
"dynamic_partition.time_zone" = "Asia/Shanghai",
"dynamic_partition.start" = "-2147483648",
"dynamic_partition.end" = "12",
"dynamic_partition.prefix" = "p",
"dynamic_partition.replication_allocation" = "tag.location.default: 1",
"dynamic_partition.buckets" = "8",
"dynamic_partition.create_history_partition" = "false",
"dynamic_partition.history_partition_num" = "-1",
"dynamic_partition.hot_partition_num" = "0",
"dynamic_partition.reserved_history_periods" = "NULL",
"dynamic_partition.storage_policy" = "",
"dynamic_partition.start_day_of_month" = "1",
"storage_medium" = "hdd",
"storage_format" = "V2",
"inverted_index_storage_format" = "V3",
"enable_unique_key_merge_on_write" = "true",
"light_schema_change" = "true",
"function_column.sequence_col" = "meta_sink_sequence",
"disable_auto_compaction" = "false",
"binlog.enable" = "true",
"binlog.ttl_seconds" = "604800",
"binlog.max_bytes" = "1073741824",
"binlog.max_history_nums" = "9223372036854775807",
"enable_single_replica_compaction" = "false",
"group_commit_interval_ms" = "10000",
"group_commit_data_bytes" = "134217728",
"enable_mow_light_delete" = "false"
);
```

## 附录 C：执行过的只读 SQL 清单

- `SELECT VERSION(), DATABASE(), CURRENT_USER(), @@hostname, @@port, @@server_uuid, @@version_comment, NOW()`
- `SHOW DATABASES`
- `SHOW GRANTS`
- `information_schema.SCHEMATA`、`TABLES`、`COLUMNS`、`STATISTICS`、`TABLE_CONSTRAINTS`、`KEY_COLUMN_USAGE`、`REFERENTIAL_CONSTRAINTS`、`CHECK_CONSTRAINTS`，限定 `trade_flow` 与 `trade_analytics`
- Doris：`SELECT VERSION(), DATABASE(), CURRENT_USER(), @@version_comment`
- `SHOW VARIABLES LIKE '%version%'`
- `SHOW FRONTENDS`、`SHOW BACKENDS`
- `SHOW FULL TABLES FROM trade_warehouse`
- `information_schema.tables`、`information_schema.views`，库名 `trade_warehouse`
- 对 26 张基表分别执行 `SHOW CREATE TABLE`、`DESC`、`SHOW PARTITIONS`
- 对 12 个版本表名再次执行 `SHOW CREATE TABLE`，结果均为 `Unknown table`

没有执行 `COUNT(*)`、DML 或 DDL。

## 附录 D：未完成核验及原因

| 项 | 原因 |
| --- | --- |
| Analytics、Ingress 的 `127.0.0.1:3306` 是否一定连到本次 mysqld | 只证明了主机端口和配置写法。没有读取进程命令行，保持配置推断 |
| `oms` 里有哪些表 | 当前账号没有该库的 `SELECT`，`information_schema` 不返回对象 |
| `trade_warehouse_test` | 本实例 `SHOW DATABASES` 没有它。未连接其他实例 |
| 视图的 `SHOW CREATE TABLE` | `doris_read_only` 被拒绝。已用 `information_schema.views` 确认 11 个视图存在，以及订单、优惠、退款视图含关键输出列。没有做逐字相等比较 |
| `member_info` 源表 | 本次未获授权，未核验其生产实际结构，不能据此判断该表不存在 |
| CDC 作业是否正在运行 | 只读数据库看不到 Flink 作业状态。代码有 Sink 不等于生产在跑 |
| Storage 分片路由和各分表数据分布 | 本轮只核结构，没有扫描业务数据 |
| 金额计算公式 | 超出结构核验范围 |

