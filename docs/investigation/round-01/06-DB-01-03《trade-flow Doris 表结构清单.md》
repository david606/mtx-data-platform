# 06-DB-01-03 trade-flow Doris 表结构清单

表模型、Key、分区、分桶和字段来自 Doris 初始化脚本。本机 9030 连接被拒绝，没有用 `SHOW CREATE TABLE` 复核。

主脚本 `docs/sql/init/trade-doris-init.sql` 创建数据库 `trade_warehouse`。它包含事实表，以及 10 个 `dwd_trade_*_fact_current` 视图和 `dwd_member_info_fact_current`。辅助脚本 `docs/sql/init/trade-doris-fact-v1-init.sql` 再次声明同一批事实表和 current 视图。两份脚本都在仓库里，不能据此判断目标库执行了哪一份。

已解析表的模型都是 UNIQUE KEY。没有发现 DUPLICATE KEY 或 AGGREGATE KEY。没有发现 Bloom Filter 属性。倒排索引只出现在部分事实表。

## Database `trade_warehouse`

主脚本表 32 张，视图 11 个。

### `trade_warehouse.dwd_order_version`

- 证据类型：DDL。文件 `docs/sql/init/trade-doris-init.sql`。实际数据库未核对。
- 引擎：`ENGINE=OLAP`
- 模型键：`UNIQUE KEY(`event_date`, `event_id`)`
- 分区：`PARTITION BY RANGE(`event_date`) ()`
- 分桶：`DISTRIBUTED BY HASH(`event_id`) BUCKETS 16`
- 代码引用：TradeCdcJob 写入的版本表。无 @TableName。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `event_date` | DATE | NO |  | event接收日期，同时作为分区键 |
| `event_id` | BIGINT | NO |  | Ingress订单event ID |
| `source_system` | SMALLINT | NO |  | 对应Ingress TINYINT UNSIGNED，预留0~255来源编码 |
| `source_event_key` | VARCHAR(128) | NO |  |  |
| `message_version` | BIGINT | NO |  |  |
| `storage_id` | BIGINT | NO |  |  |
| `payload_sha256` | CHAR(64) | NO |  | Storage BINARY(32)的十六进制展示值 |
| `canonical_schema_version` | VARCHAR(16) | NO |  |  |
| `transform_version` | VARCHAR(64) | NO |  |  |
| `event_received_time` | DATETIME(3) | NO |  |  |
| `processed_time` | DATETIME(3) | NO |  |  |
| `order_no` | BIGINT | NO |  |  |
| `user_id` | BIGINT | YES |  |  |
| `mchnt_cd` | VARCHAR(32) | NO | '' |  |
| `shop_id` | BIGINT | YES |  |  |
| `express_id` | BIGINT | YES |  |  |
| `tm_fuiou_id` | VARCHAR(64) | NO | '' |  |
| `term_name` | VARCHAR(512) | NO | '' |  |
| `order_type` | VARCHAR(4) | NO | '' |  |
| `channel_type` | VARCHAR(4) | NO | '' |  |
| `order_state` | VARCHAR(4) | NO | '' |  |
| `order_pay_state` | TINYINT | NO | '0' |  |
| `express_state` | TINYINT | NO | '0' |  |
| `order_amt` | BIGINT | NO | '0' |  |
| `order_dis_amt` | BIGINT | NO | '0' |  |
| `pay_amt` | BIGINT | NO | '0' |  |
| `pay_amt_extra` | BIGINT | NO | '0' |  |
| `cash_received_amt` | BIGINT | NO | '0' |  |
| `refund_amt` | BIGINT | NO | '0' |  |
| `express_amt` | BIGINT | NO | '0' |  |
| `mchnt_express_cost` | BIGINT | NO | '0' |  |
| `lunch_box_fee` | BIGINT | NO | '0' |  |
| `invoice_amt` | BIGINT | NO | '0' |  |
| `third_mchnt_income` | BIGINT | NO | '0' |  |
| `order_create_time` | DATETIME(3) | NO |  |  |
| `pay_deadline_time` | DATETIME(3) | YES |  |  |
| `pay_time` | DATETIME(3) | YES |  |  |
| `cashier_confirm_time` | DATETIME(3) | YES |  |  |
| `delivery_start_time` | DATETIME(3) | YES |  |  |
| `comment_time` | DATETIME(3) | YES |  |  |
| `finish_time` | DATETIME(3) | YES |  |  |
| `source_update_time` | DATETIME(3) | NO |  |  |
| `refund_time` | DATETIME(3) | YES |  |  |
| `open_table_time` | DATETIME(3) | YES |  |  |
| `meal_time` | DATETIME(3) | YES |  |  |
| `estimated_finish_time` | DATETIME(3) | YES |  |  |
| `reverse_time` | DATETIME(3) | YES |  |  |
| `deliver_time_desc` | VARCHAR(256) | NO | '' |  |
| `pay_ssn` | VARCHAR(64) | NO | '' |  |
| `pay_type` | VARCHAR(32) | NO | '' |  |
| `pay_type_extra` | VARCHAR(32) | NO | '' |  |
| `third_order_no` | VARCHAR(64) | NO | '' |  |
| `app_open_id` | VARCHAR(128) | NO | '' |  |
| `out_user_id` | VARCHAR(64) | NO | '' |  |
| `meal_code` | VARCHAR(32) | NO | '' |  |
| `user_memo` | VARCHAR(2048) | NO | '' |  |
| `order_cancel_reason` | VARCHAR(2048) | NO | '' |  |
| `order_comment` | VARCHAR(4096) | NO | '' |  |
| `comment_state` | TINYINT | NO | '0' |  |
| `comment_level` | VARCHAR(4) | NO | '' |  |
| `order_addr_id` | BIGINT | YES |  |  |
| `phone` | VARCHAR(20) | NO | '' |  |
| `contact_mobile` | VARCHAR(20) | NO | '' |  |
| `express_company` | VARCHAR(4) | NO | '' |  |
| `cashier_id` | VARCHAR(32) | NO | '' |  |
| `cashier_dis_amt` | BIGINT | NO | '0' |  |
| `single_goods_dis_amt` | BIGINT | NO | '0' |  |
| `full_order_dis_amt` | BIGINT | NO | '0' |  |
| `cashier_discount` | DECIMAL(7,2) | YES |  |  |
| `cashier_dis_id` | BIGINT | YES |  |  |
| `cashier_dis_name` | VARCHAR(256) | NO | '' |  |
| `discount_type` | VARCHAR(4) | NO | '' |  |
| `discount_type_extra` | VARCHAR(4) | NO | '' |  |
| `coupon_id` | BIGINT | YES |  |  |
| `coupon_real_id` | BIGINT | YES |  |  |
| `special_coupon_id` | VARCHAR(64) | NO | '' |  |
| `special_mchnt_type` | VARCHAR(4) | NO | '' |  |
| `integral` | BIGINT | NO | '0' |  |
| `integral_deduction_amt` | BIGINT | NO | '0' |  |
| `coupon_amt` | BIGINT | NO | '0' |  |
| `full_minus_amt` | BIGINT | NO | '0' |  |
| `member_level_dis_amt` | BIGINT | NO | '0' |  |
| `member_price_dis_amt` | BIGINT | NO | '0' |  |
| `member_day_dis_amt` | BIGINT | NO | '0' |  |
| `unionpay_dis_amt` | BIGINT | NO | '0' |  |
| `times_card_dis_amt` | BIGINT | NO | '0' |  |
| `package_price_dis_amt` | BIGINT | NO | '0' |  |
| `group_pay_dis_amt` | BIGINT | NO | '0' |  |
| `plat_hong_bao` | BIGINT | NO | '0' |  |
| `wipe_zero_amt` | BIGINT | NO | '0' |  |
| `not_in_discount_amt` | BIGINT | NO | '0' |  |
| `free_consume_amt` | BIGINT | NO | '0' |  |
| `fixed_price_amt` | BIGINT | NO | '0' |  |
| `group_pay_num` | TINYINT | NO | '0' |  |
| `group_pay_num_extra` | TINYINT | NO | '0' |  |
| `is_membership` | TINYINT | NO | '0' |  |
| `is_meal_order` | TINYINT | NO | '0' |  |
| `is_order_locked` | TINYINT | NO | '0' |  |
| `has_reverse` | TINYINT | NO | '0' |  |
| `is_pad_confirm` | TINYINT | NO | '0' |  |
| `is_account_order` | TINYINT | NO | '0' |  |
| `print_settle_status` | TINYINT | NO | '0' |  |
| `third_basket_status` | TINYINT | NO | '0' |  |
| `invoice_state` | TINYINT | NO | '0' |  |
| `mqtt_send_state` | TINYINT | NO | '0' |  |
| `meal_confirm_channel` | VARCHAR(4) | NO | '' |  |
| `table_fuiou_id` | VARCHAR(64) | NO | '' |  |
| `table_term_name` | VARCHAR(512) | NO | '' |  |
| `promoter_no` | VARCHAR(32) | NO | '' |  |
| `account_memo` | VARCHAR(2048) | NO | '' |  |
| `member_phone` | VARCHAR(20) | NO | '' |  |
| `member_name` | VARCHAR(256) | NO | '' |  |
| `member_points` | BIGINT | NO | '0' |  |
| `user_balance` | BIGINT | NO | '0' |  |
| `guests_count` | SMALLINT | YES |  |  |
| `order_version` | SMALLINT | NO | '0' |  |

### `trade_warehouse.dwd_order_item_version`

- 证据类型：DDL。文件 `docs/sql/init/trade-doris-init.sql`。实际数据库未核对。
- 引擎：`ENGINE=OLAP`
- 模型键：`UNIQUE KEY(`event_date`, `event_id`, `detail_no`)`
- 分区：`PARTITION BY RANGE(`event_date`) ()`
- 分桶：`DISTRIBUTED BY HASH(`event_id`) BUCKETS 16`
- 代码引用：TradeCdcJob 版本明细。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `event_date` | DATE | NO |  |  |
| `event_id` | BIGINT | NO |  |  |
| `detail_no` | BIGINT | NO |  |  |
| `source_system` | SMALLINT | NO |  |  |
| `source_event_key` | VARCHAR(128) | NO |  |  |
| `message_version` | BIGINT | NO |  |  |
| `storage_id` | BIGINT | NO |  |  |
| `payload_sha256` | CHAR(64) | NO |  |  |
| `canonical_schema_version` | VARCHAR(16) | NO |  |  |
| `transform_version` | VARCHAR(64) | NO |  |  |
| `event_received_time` | DATETIME(3) | NO |  |  |
| `processed_time` | DATETIME(3) | NO |  |  |
| `order_no` | BIGINT | NO |  |  |
| `shop_id` | BIGINT | NO |  |  |
| `mchnt_cd` | VARCHAR(32) | NO | '' |  |
| `goods_id` | BIGINT | NO | '0' |  |
| `goods_name` | VARCHAR(512) | NO | '' |  |
| `goods_unit` | VARCHAR(128) | NO | '' |  |
| `goods_bar_code` | VARCHAR(64) | NO | '' |  |
| `goods_base_price` | BIGINT | NO | '0' |  |
| `goods_dis_price` | BIGINT | NO | '0' |  |
| `goods_price` | BIGINT | NO | '0' |  |
| `goods_number` | DECIMAL(14,3) | NO | '0.000' |  |
| `refund_goods_number` | DECIMAL(14,3) | NO | '0.000' |  |
| `goods_pay_amt` | BIGINT | NO | '0' |  |
| `goods_real_pay_amt` | BIGINT | NO | '0' |  |
| `goods_total_refund_amt` | BIGINT | NO | '0' |  |
| `cashier_dis_price` | BIGINT | NO | '0' |  |
| `goods_cashier_discount` | DECIMAL(5,2) | NO | '0.00' |  |
| `dis_discount_reason` | VARCHAR(256) | NO | '' |  |
| `goods_dis_type` | TINYINT | NO | '0' |  |
| `goods_member_price` | BIGINT | NO | '0' |  |
| `goods_member_dis_amt` | BIGINT | NO | '0' |  |
| `goods_member_points` | BIGINT | NO | '0' |  |
| `promotion_id` | BIGINT | YES |  |  |
| `goods_promotion_way` | TINYINT | NO | '0' |  |
| `unionpay_coupon_id` | VARCHAR(64) | NO | '' |  |
| `avg_purchase_price` | BIGINT | NO | '0' |  |
| `goods_sales_commission` | TINYINT | NO | '0' |  |
| `goods_employee_commission` | TINYINT | NO | '0' |  |
| `spec_id_list` | VARCHAR(128) | NO | '' |  |
| `spec_desc_list` | VARCHAR(512) | NO | '' |  |
| `is_package_goods` | TINYINT | NO | '0' |  |
| `in_pkg_goods_price` | BIGINT | NO | '0' |  |
| `goods_basket` | VARCHAR(16) | NO | '' |  |
| `goods_lunch_box_fee` | BIGINT | NO | '0' |  |
| `prepackaged_amt` | BIGINT | NO | '0' |  |
| `is_third_order` | TINYINT | NO | '0' |  |
| `add_dish_channel` | VARCHAR(4) | NO | '' |  |
| `dish_state` | TINYINT | NO | '1' |  |
| `dish_user_memo` | VARCHAR(512) | NO | '' |  |
| `dish_cashier_memo` | VARCHAR(512) | NO | '' |  |
| `dish_cancel_reason` | VARCHAR(1024) | NO | '' |  |
| `dish_fuiou_id` | VARCHAR(32) | NO | '' |  |
| `dish_user_id` | BIGINT | YES |  |  |
| `dish_cashier_id` | VARCHAR(32) | NO | '' |  |
| `dish_has_hurried` | TINYINT | NO | '0' |  |
| `dish_has_finish` | TINYINT | NO | '0' |  |
| `is_dish_confirm` | TINYINT | NO | '0' |  |
| `is_opr_by_pad` | TINYINT | NO | '0' |  |
| `is_weigh_goods` | TINYINT | NO | '0' |  |
| `mark_print` | TINYINT | NO | '0' |  |
| `dish_print_state` | TINYINT | NO | '0' |  |
| `kitchen_print` | TINYINT | NO | '0' |  |
| `print_serial_no` | VARCHAR(64) | NO | '' |  |
| `calc_model` | TINYINT | NO | '0' |  |
| `item_create_time` | DATETIME(3) | NO |  |  |
| `dish_update_time` | DATETIME(3) | NO |  |  |

### `trade_warehouse.dwd_order_item_spec_version`

- 证据类型：DDL。文件 `docs/sql/init/trade-doris-init.sql`。实际数据库未核对。
- 引擎：`ENGINE=OLAP`
- 模型键：`UNIQUE KEY(`event_date`, `event_id`, `detail_no`, `order_spec_id`)`
- 分区：`PARTITION BY RANGE(`event_date`) ()`
- 分桶：`DISTRIBUTED BY HASH(`event_id`) BUCKETS 16`
- 代码引用：TradeCdcJob 规格版本。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `event_date` | DATE | NO |  |  |
| `event_id` | BIGINT | NO |  |  |
| `detail_no` | BIGINT | NO |  |  |
| `order_spec_id` | BIGINT | NO |  |  |
| `source_system` | SMALLINT | NO |  |  |
| `source_event_key` | VARCHAR(128) | NO |  |  |
| `message_version` | BIGINT | NO |  |  |
| `storage_id` | BIGINT | NO |  |  |
| `payload_sha256` | CHAR(64) | NO |  |  |
| `canonical_schema_version` | VARCHAR(16) | NO |  |  |
| `transform_version` | VARCHAR(64) | NO |  |  |
| `event_received_time` | DATETIME(3) | NO |  |  |
| `processed_time` | DATETIME(3) | NO |  |  |
| `order_no` | BIGINT | NO |  |  |
| `spec_id` | BIGINT | NO | '0' |  |
| `spec_name` | VARCHAR(256) | NO | '' |  |
| `spec_detail_id` | BIGINT | NO | '0' |  |
| `spec_detail_desc` | VARCHAR(512) | NO | '' |  |
| `detail_extra_price` | BIGINT | NO | '0' |  |

### `trade_warehouse.dwd_order_package_item_version`

- 证据类型：DDL。文件 `docs/sql/init/trade-doris-init.sql`。实际数据库未核对。
- 引擎：`ENGINE=OLAP`
- 模型键：`UNIQUE KEY(`event_date`, `event_id`, `package_detail_no`)`
- 分区：`PARTITION BY RANGE(`event_date`) ()`
- 分桶：`DISTRIBUTED BY HASH(`event_id`) BUCKETS 16`
- 代码引用：TradeCdcJob 套餐版本。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `event_date` | DATE | NO |  |  |
| `event_id` | BIGINT | NO |  |  |
| `package_detail_no` | BIGINT | NO |  |  |
| `source_system` | SMALLINT | NO |  |  |
| `source_event_key` | VARCHAR(128) | NO |  |  |
| `message_version` | BIGINT | NO |  |  |
| `storage_id` | BIGINT | NO |  |  |
| `payload_sha256` | CHAR(64) | NO |  |  |
| `canonical_schema_version` | VARCHAR(16) | NO |  |  |
| `transform_version` | VARCHAR(64) | NO |  |  |
| `event_received_time` | DATETIME(3) | NO |  |  |
| `processed_time` | DATETIME(3) | NO |  |  |
| `relate_pkg_detail_no` | BIGINT | NO |  |  |
| `order_no` | BIGINT | NO |  |  |
| `goods_id` | BIGINT | NO | '0' |  |
| `shop_id` | BIGINT | NO |  |  |
| `mchnt_cd` | VARCHAR(32) | NO | '' |  |
| `goods_name` | VARCHAR(512) | NO | '' |  |
| `goods_unit` | VARCHAR(128) | NO | '' |  |
| `goods_bar_code` | VARCHAR(64) | NO | '' |  |
| `goods_base_price` | BIGINT | NO | '0' |  |
| `goods_dis_price` | BIGINT | NO | '0' |  |
| `goods_member_price` | BIGINT | NO | '0' |  |
| `goods_member_dis_amt` | BIGINT | NO | '0' |  |
| `goods_cashier_discount` | DECIMAL(7,2) | NO | '0.00' |  |
| `cashier_dis_price` | BIGINT | NO | '0' |  |
| `goods_price` | BIGINT | NO | '0' |  |
| `goods_pay_amt` | BIGINT | NO | '0' |  |
| `goods_number` | DECIMAL(14,3) | NO | '0.000' |  |
| `package_number` | DECIMAL(14,3) | NO | '0.000' |  |
| `pkg_goods_copies` | DECIMAL(14,3) | NO | '0.000' |  |
| `pkg_goods_extra_price` | BIGINT | NO | '0' |  |
| `avg_purchase_price` | BIGINT | NO | '0' |  |
| `goods_lunch_box_fee` | BIGINT | NO | '0' |  |
| `goods_member_points` | BIGINT | NO | '0' |  |
| `goods_index` | TINYINT | NO | '0' |  |
| `mark_print` | TINYINT | NO | '0' |  |
| `is_weigh_goods` | TINYINT | NO | '0' |  |
| `spec_id_list` | VARCHAR(64) | NO | '' |  |
| `spec_desc_list` | VARCHAR(512) | NO | '' |  |
| `third_detail_no` | VARCHAR(64) | NO | '' |  |
| `package_dish_memo` | VARCHAR(512) | NO | '' |  |
| `item_create_time` | DATETIME(3) | NO |  |  |

### `trade_warehouse.dwd_payment_version`

- 证据类型：DDL。文件 `docs/sql/init/trade-doris-init.sql`。实际数据库未核对。
- 引擎：`ENGINE=OLAP`
- 模型键：`UNIQUE KEY(`event_date`, `event_id`)`
- 分区：`PARTITION BY RANGE(`event_date`) ()`
- 分桶：`DISTRIBUTED BY HASH(`event_id`) BUCKETS 16`
- 代码引用：TradeCdcJob 支付版本。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `event_date` | DATE | NO |  |  |
| `event_id` | BIGINT | NO |  |  |
| `source_system` | SMALLINT | NO |  |  |
| `source_event_key` | VARCHAR(128) | NO |  |  |
| `message_version` | BIGINT | NO |  |  |
| `storage_id` | BIGINT | NO |  |  |
| `payload_sha256` | CHAR(64) | NO |  |  |
| `canonical_schema_version` | VARCHAR(16) | NO |  |  |
| `transform_version` | VARCHAR(64) | NO |  |  |
| `event_received_time` | DATETIME(3) | NO |  |  |
| `processed_time` | DATETIME(3) | NO |  |  |
| `pay_ssn` | VARCHAR(64) | NO |  |  |
| `source_pay_ssn` | VARCHAR(64) | NO | '' |  |
| `order_no` | BIGINT | NO | '0' |  |
| `mchnt_cd` | VARCHAR(32) | NO |  |  |
| `shop_id` | BIGINT | NO | '0' |  |
| `shop_name` | VARCHAR(256) | NO | '' |  |
| `pay_time` | DATETIME(3) | NO |  |  |
| `refund_time` | DATETIME(3) | YES |  |  |
| `pay_type` | VARCHAR(16) | NO | '' |  |
| `pay_name` | VARCHAR(128) | NO | '' |  |
| `pay_state` | TINYINT | NO |  |  |
| `pay_amt` | BIGINT | NO | '0' |  |
| `fee_amt` | BIGINT | NO | '0' |  |
| `refund_amt` | BIGINT | NO | '0' |  |
| `balance_dis_amt` | BIGINT | NO | '0' |  |
| `face_amt` | BIGINT | NO | '0' |  |
| `fy_settle` | TINYINT | NO | '0' |  |
| `third_order_no` | VARCHAR(64) | NO | '' |  |
| `channel_trade_no` | VARCHAR(64) | NO | '' |  |
| `big_category` | VARCHAR(4) | NO | '' |  |
| `small_category` | VARCHAR(4) | NO | '' |  |
| `order_source` | VARCHAR(4) | NO | '' |  |
| `open_id` | VARCHAR(64) | NO | '' |  |
| `order_type` | VARCHAR(4) | NO | '' |  |
| `channel_type` | VARCHAR(4) | NO | '' |  |
| `member_name` | VARCHAR(512) | NO | '' |  |
| `phone` | VARCHAR(20) | NO | '' |  |
| `member_card_no` | VARCHAR(64) | NO | '' |  |
| `member_level` | VARCHAR(4) | NO | '' |  |

### `trade_warehouse.dwd_payment_account_version`

- 证据类型：DDL。文件 `docs/sql/init/trade-doris-init.sql`。实际数据库未核对。
- 引擎：`ENGINE=OLAP`
- 模型键：`UNIQUE KEY(`event_date`, `event_id`, `account_seq`)`
- 分区：`PARTITION BY RANGE(`event_date`) ()`
- 分桶：`DISTRIBUTED BY HASH(`event_id`) BUCKETS 16`
- 代码引用：TradeCdcJob 支付账户版本。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `event_date` | DATE | NO |  |  |
| `event_id` | BIGINT | NO |  |  |
| `account_seq` | SMALLINT | NO |  |  |
| `source_system` | SMALLINT | NO |  |  |
| `source_event_key` | VARCHAR(128) | NO |  |  |
| `message_version` | BIGINT | NO |  |  |
| `storage_id` | BIGINT | NO |  |  |
| `payload_sha256` | CHAR(64) | NO |  |  |
| `canonical_schema_version` | VARCHAR(16) | NO |  |  |
| `transform_version` | VARCHAR(64) | NO |  |  |
| `event_received_time` | DATETIME(3) | NO |  |  |
| `processed_time` | DATETIME(3) | NO |  |  |
| `pay_ssn` | VARCHAR(64) | NO |  |  |
| `cust_acnt_tp` | VARCHAR(4) | NO | '' |  |
| `mchnt_cd` | VARCHAR(32) | NO | '' |  |
| `shop_id` | BIGINT | NO | '0' |  |
| `out_acnt_nm` | VARCHAR(512) | NO | '' |  |
| `out_acnt_no` | VARCHAR(128) | NO | '' |  |

### `trade_warehouse.dim_trade_shop_id_mapping`

- 证据类型：DDL。文件 `docs/sql/init/trade-doris-init.sql`。实际数据库未核对。
- 引擎：`ENGINE=OLAP`
- 模型键：`UNIQUE KEY(`internal_shop_id`)`
- 分桶：`DISTRIBUTED BY HASH(`internal_shop_id`) BUCKETS 4`
- 代码引用：TradeShopIdMappingDO。ShopDimensionCdcJob。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `internal_shop_id` | BIGINT | NO |  | 交易域门店ID，本表唯一主键 |
| `platform_code` | VARCHAR(16) | NO |  | FZY/TAOBAO/MEITUAN/JD |
| `platform_shop_id` | VARCHAR(128) | NO |  | 平台门店号 |
| `platform_shop_name` | VARCHAR(256) | YES |  |  |
| `brand_id` | BIGINT | NO |  | 租户品牌ID；与平台和门店号共同确定来源门店 |
| `effective_start_time` | DATETIME(3) | NO |  |  |
| `effective_end_time` | DATETIME(3) | YES |  |  |
| `mapping_status` | VARCHAR(16) | YES |  |  |
| `mapping_version` | BIGINT | YES |  |  |
| `updated_time` | DATETIME(3) | YES |  |  |

### `trade_warehouse.dim_fuiou_shop_info`

- 证据类型：DDL。文件 `docs/sql/init/trade-doris-init.sql`。实际数据库未核对。
- 引擎：`ENGINE=OLAP`
- 模型键：`UNIQUE KEY(`shop_id`)`
- 分桶：`DISTRIBUTED BY HASH(`shop_id`) BUCKETS 4`
- 代码引用：ShopDO。ShopDimensionCdcJob。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `shop_id` | BIGINT | NO |  | 第三方门店ID |
| `shop_code` | VARCHAR(64) | NO | '' | 门店编码 |
| `shop_name` | VARCHAR(128) | NO | '' | 门店名称 |
| `shop_type` | VARCHAR(8) | NO | '' | 门店类型 |
| `open_state` | VARCHAR(64) | YES |  | 第三方原始营业状态；仅留痕，不作为查询过滤条件 |
| `mchnt_cd` | VARCHAR(32) | NO | '' | 商户编码 |
| `term_user_cd` | VARCHAR(32) | NO | '' | 终端用户编码 |
| `phone` | VARCHAR(32) | NO | '' | 联系电话 |
| `address` | VARCHAR(256) | NO | '' | 门店地址 |
| `prov_cd` | VARCHAR(16) | NO | '' | 省编码 |
| `region_cd` | VARCHAR(16) | NO | '' | 区域编码 |
| `county_cd` | VARCHAR(16) | NO | '' | 区县编码 |
| `city_name` | VARCHAR(64) | NO | '' | 城市名称 |
| `gitu` | VARCHAR(32) | NO | '' | 经度 |
| `titu` | VARCHAR(32) | NO | '' | 纬度 |
| `eat_in_open_time` | VARCHAR(64) | NO | '' | 堂食营业时间 |
| `take_out_open_time` | VARCHAR(64) | NO | '' | 外卖营业时间 |
| `active_tm` | DATETIME(3) | YES |  | 激活时间 |
| `create_tm` | DATETIME(3) | YES |  | 第三方创建时间 |
| `update_tm` | DATETIME(3) | YES |  | 第三方更新时间 |
| `create_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP | 本地记录创建时间 |
| `update_time` | DATETIME(3) | NO | CURRENT_TIMESTAMP | 本地记录更新时间 |
| `del_flag` | TINYINT | NO | '0' | 全量同步保留标记：0=本轮存在，1=本轮未返回 |

### `trade_warehouse.dim_trade_shop_group`

- 证据类型：DDL。文件 `docs/sql/init/trade-doris-init.sql`。实际数据库未核对。
- 引擎：`ENGINE=OLAP`
- 模型键：`UNIQUE KEY(`brand_id`, `id`)`
- 分桶：`DISTRIBUTED BY HASH(`brand_id`, `id`) BUCKETS 16`
- Sequence 列：`rule_version`
- 代码引用：TradeShopGroupDO。ShopDimensionCdcJob。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `brand_id` | BIGINT | NO |  | 交易品牌ID，门店归组的租户边界 |
| `id` | VARCHAR(64) | NO |  | 完整SHA256稳定规则自然键 |
| `rule_version` | BIGINT | NO |  | 品牌内规则版本，作为sequence列 |
| `row_kind` | VARCHAR(8) | NO |  | ROOT/MEMBER |
| `group_key` | VARCHAR(512) | NO |  | 来源公司根身份，保留原始字符串 |
| `parent_id` | VARCHAR(64) | YES |  |  |
| `member_ordinal` | INT | YES |  |  |
| `platform_raw` | VARCHAR(128) | YES |  |  |
| `platform_code` | VARCHAR(16) | YES |  |  |
| `platform_shop_id` | VARCHAR(128) | YES |  |  |
| `is_deleted` | TINYINT | NO | '0' | 逻辑删除标记：0=未删除，1=已删除 |
| `platform_source_app_id` | VARCHAR(128) | YES |  |  |
| `extra_fields` | JSON | YES |  | 未知业务扩展，不是原HTTP正文 |
| `rule_status` | VARCHAR(32) | NO |  |  |
| `conflict_diagnostics` | JSON | YES |  |  |
| `observed_time` | DATETIME(3) | NO |  |  |
| `origin` | VARCHAR(16) | NO |  | SSM/LEGACY |
| `internal_shop_id` | BIGINT | YES |  |  |
| `shop_name` | VARCHAR(256) | YES |  |  |
| `open_state` | VARCHAR(64) | YES |  |  |

### `trade_warehouse.dim_trade_goods_group`

- 证据类型：DDL。文件 `docs/sql/init/trade-doris-init.sql`。实际数据库未核对。
- 引擎：`ENGINE=OLAP`
- 模型键：`UNIQUE KEY(`tenant_id`, `id`)`
- 分桶：`DISTRIBUTED BY HASH(`tenant_id`, `id`) BUCKETS 16`
- Sequence 列：`rule_version`
- 代码引用：TradeGoodsGroupDO。GoodsDimensionCdcJob。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `tenant_id` | BIGINT | NO |  | SSM来源租户 |
| `id` | VARCHAR(64) | NO |  | 完整SHA256稳定规则自然键 |
| `rule_version` | BIGINT | NO |  | scope单调版本；仅作sequence不进入唯一键 |
| `row_kind` | VARCHAR(8) | NO |  | ROOT/MEMBER |
| `group_key` | VARCHAR(512) | NO |  | 来源公司根身份，保留原始字符串 |
| `parent_id` | VARCHAR(64) | YES |  |  |
| `member_ordinal` | INT | YES |  |  |
| `platform_raw` | VARCHAR(128) | YES |  |  |
| `platform_code` | VARCHAR(16) | YES |  |  |
| `platform_shop_id` | VARCHAR(128) | YES |  |  |
| `brand_id` | BIGINT | YES |  | 解析结果，不参与规则落库前置条件 |
| `platform_source_app_id` | VARCHAR(128) | YES |  |  |
| `extra_fields` | JSON | YES |  | 未知业务扩展，不是原HTTP正文 |
| `rule_status` | VARCHAR(32) | NO |  |  |
| `conflict_diagnostics` | JSON | YES |  |  |
| `observed_time` | DATETIME(3) | NO |  |  |
| `origin` | VARCHAR(16) | NO |  | SSM/LEGACY |
| `internal_product_id` | BIGINT | YES |  |  |
| `root_goods_name` | VARCHAR(512) | YES |  |  |
| `barcode` | VARCHAR(128) | YES |  |  |
| `bind_count` | INT | YES |  |  |
| `shop_name` | VARCHAR(256) | YES |  |  |
| `platform_product_id` | VARCHAR(128) | YES |  |  |
| `product_name` | VARCHAR(512) | YES |  |  |

### `trade_warehouse.dim_region`

- 证据类型：DDL。文件 `docs/sql/init/trade-doris-init.sql`。实际数据库未核对。
- 引擎：`ENGINE=OLAP`
- 模型键：`UNIQUE KEY(`code`)`
- 分桶：`DISTRIBUTED BY HASH(`code`) BUCKETS 4`
- 代码引用：RegionDO，Query 侧 Doris 实体。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `code` | CHAR(6) | NO |  | 行政区划编码（国标6位） |
| `name` | VARCHAR(50) | NO | '' | 名称 |
| `level` | TINYINT | NO |  | 1-省 2-市 3-区/县 |
| `parent_code` | CHAR(6) | YES |  | 上级编码 |
| `deleted` | TINYINT | NO | 0 | 是否删除(0未删除1已删除) |
| `create_time` | DATETIME | YES |  | 创建时间 |
| `update_time` | DATETIME | YES |  | 更新时间 |

### `trade_warehouse.dim_taobao_shop_info`

- 证据类型：DDL。文件 `docs/sql/init/trade-doris-init.sql`。实际数据库未核对。
- 引擎：`ENGINE=OLAP`
- 模型键：`UNIQUE KEY(`brand_id`, `shop_id`)`
- 分桶：`DISTRIBUTED BY HASH(`brand_id`, `shop_id`) BUCKETS 4`
- 代码引用：TaobaoShopInfoDO。ShopDimensionCdcJob。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `brand_id` | BIGINT | NO |  | 租户品牌ID |
| `shop_id` | VARCHAR(128) | NO |  | 淘宝原始门店ID |
| `app_id` | VARCHAR(128) | YES |  | 淘宝开放平台应用ID |
| `shop_name` | VARCHAR(256) | YES |  |  |
| `open_id` | VARCHAR(256) | YES |  |  |
| `address` | VARCHAR(1024) | YES |  |  |
| `province_name` | VARCHAR(128) | YES |  | 淘宝返回省名称 |
| `city_name` | VARCHAR(128) | YES |  | 淘宝返回市名称 |
| `district_name` | VARCHAR(128) | YES |  | 淘宝返回区县名称 |
| `latitude` | DOUBLE | YES |  | 淘宝原始纬度，仅保真存储 |
| `longitude` | DOUBLE | YES |  | 淘宝原始经度，仅保真存储 |
| `is_open` | INT | YES |  |  |
| `phones_json` | VARCHAR(1024) | YES |  |  |
| `province_code` | VARCHAR(16) | YES |  | 按dim_region父子层级解析的省编码 |
| `city_code` | VARCHAR(16) | YES |  | 按dim_region父子层级解析的市编码 |
| `district_code` | VARCHAR(16) | YES |  | 按dim_region父子层级解析的区县编码 |
| `region_resolution_status` | VARCHAR(16) | YES |  | RESOLVED/PARTIAL/UNRESOLVED/AMBIGUOUS |
| `region_resolution_method` | VARCHAR(32) | YES |  | SOURCE_NAMES/ADDRESS/SSM_PLACEHOLDER |
| `updated_time` | DATETIME(3) | YES |  |  |

### `trade_warehouse.dim_meituan_shop_info`

- 证据类型：DDL。文件 `docs/sql/init/trade-doris-init.sql`。实际数据库未核对。
- 引擎：`ENGINE=OLAP`
- 模型键：`UNIQUE KEY(`brand_id`, `app_poi_code`)`
- 分桶：`DISTRIBUTED BY HASH(`brand_id`, `app_poi_code`) BUCKETS 4`
- 代码引用：MeituanShopInfoDO。ShopDimensionCdcJob。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `brand_id` | BIGINT | NO |  | 租户品牌ID |
| `app_poi_code` | VARCHAR(128) | NO |  | 美团原始app_poi_code |
| `wm_poi_id` | VARCHAR(128) | YES |  | 美团平台门店ID |
| `app_id` | VARCHAR(128) | YES |  | 美团开放平台应用ID |
| `shop_name` | VARCHAR(256) | YES |  |  |
| `address` | VARCHAR(1024) | YES |  |  |
| `phone` | VARCHAR(64) | YES |  |  |
| `latitude` | DOUBLE | YES |  | 美团原始纬度，仅保真存储，不参与本期地域解析 |
| `longitude` | DOUBLE | YES |  | 美团原始经度，仅保真存储，不参与本期地域解析 |
| `is_online` | INT | YES |  |  |
| `source_utime` | BIGINT | YES |  |  |
| `province_code` | VARCHAR(16) | YES |  | 按address和dim_region父子层级解析的省编码 |
| `city_code` | VARCHAR(16) | YES |  | 按address和dim_region父子层级解析的市编码 |
| `district_code` | VARCHAR(16) | YES |  | 按address和dim_region父子层级解析的区县编码 |
| `region_resolution_status` | VARCHAR(16) | YES |  | RESOLVED/PARTIAL/UNRESOLVED/AMBIGUOUS |
| `region_resolution_method` | VARCHAR(32) | YES |  | ADDRESS/SSM_PLACEHOLDER |
| `updated_time` | DATETIME(3) | YES |  |  |

### `trade_warehouse.dim_jd_shop_info`

- 证据类型：DDL。文件 `docs/sql/init/trade-doris-init.sql`。实际数据库未核对。
- 引擎：`ENGINE=OLAP`
- 模型键：`UNIQUE KEY(`brand_id`, `platform_shop_id`)`
- 分桶：`DISTRIBUTED BY HASH(`brand_id`, `platform_shop_id`) BUCKETS 4`
- 代码引用：JdShopInfoDO。ShopDimensionCdcJob。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `brand_id` | BIGINT | NO |  | 租户品牌ID |
| `platform_shop_id` | VARCHAR(128) | NO |  | 京东原始stationNo |
| `app_key` | VARCHAR(128) | YES |  | 最近一次验证账号血缘 |
| `vender_id` | VARCHAR(128) | YES |  | 最近一次验证商家编码血缘 |
| `vender_name` | VARCHAR(256) | YES |  |  |
| `shop_name` | VARCHAR(256) | YES |  |  |
| `latitude` | DECIMAL(18, 8) | YES |  | 门店接口lat原值 |
| `longitude` | DECIMAL(18, 8) | YES |  | 门店接口lng原值 |
| `province_code` | VARCHAR(32) | YES |  |  |
| `province_name` | VARCHAR(128) | YES |  |  |
| `city_code` | VARCHAR(32) | YES |  |  |
| `city_name` | VARCHAR(128) | YES |  |  |
| `county_code` | VARCHAR(32) | YES |  |  |
| `county_name` | VARCHAR(128) | YES |  |  |
| `station_address` | VARCHAR(1024) | YES |  |  |
| `phone` | VARCHAR(64) | YES |  |  |
| `mobile` | VARCHAR(64) | YES |  |  |
| `enabled_status` | INT | YES |  | 门店接口yn原值 |
| `close_status` | INT | YES |  | 门店接口closeStatus原值 |
| `identity_status` | VARCHAR(16) | YES |  | 来源核验状态 |
| `region_resolution_status` | VARCHAR(16) | YES |  |  |
| `region_resolution_method` | VARCHAR(32) | YES |  |  |
| `source_updated_time` | DATETIME(3) | YES |  |  |
| `updated_time` | DATETIME(3) | YES |  |  |

### `trade_warehouse.dim_goods_product_v1`

- 证据类型：DDL。文件 `docs/sql/init/trade-doris-init.sql`。实际数据库未核对。
- 表注释：商品域标准商品当前维表 v1
- 引擎：`ENGINE=OLAP`
- 模型键：`UNIQUE KEY(`internal_product_id`)`
- 分桶：`DISTRIBUTED BY HASH(`internal_product_id`) BUCKETS 8`
- Sequence 列：`meta_sink_sequence`
- 代码引用：ProductDimDO。GoodsDimensionCdcJob。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `internal_product_id` | BIGINT | NO |  | 内部统一商品ID |
| `brand_id` | BIGINT | YES |  | 内部品牌ID，关联MySQL dim_brand.brand_id |
| `internal_category_id` | BIGINT | YES |  | 当前内部统一类目ID |
| `product_name` | VARCHAR(512) | NO |  | 内部标准商品名称 |
| `product_type` | VARCHAR(32) | YES |  | NORMAL/PACKAGE等 |
| `stock_unit` | VARCHAR(32) | YES |  | 标准销售单位 |
| `product_status` | VARCHAR(32) | YES |  | 商品状态 |
| `is_deleted` | BOOLEAN | NO | '0' | 逻辑删除：0=有效，1=已删除 |
| `meta_process_time` | DATETIME(3) | NO |  |  |
| `meta_sink_sequence` | BIGINT | NO |  |  |

### `trade_warehouse.dim_goods_platform_catalog_v1`

- 证据类型：DDL。文件 `docs/sql/init/trade-doris-init.sql`。实际数据库未核对。
- 表注释：平台商品与规格目录节点 v1
- 引擎：`ENGINE=OLAP`
- 模型键：`UNIQUE KEY(`brand_id`, `source_system`, `source_merchant_code`, `source_shop_id`, `id_namespace`, `source_id`)`
- 分桶：`DISTRIBUTED BY HASH(`brand_id`, `source_merchant_code`, `source_shop_id`) BUCKETS 16`
- Sequence 列：`meta_sink_sequence`
- 代码引用：GoodsPlatformCatalogDO。GoodsDimensionCdcJob。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `brand_id` | BIGINT | NO |  | 品牌ID |
| `source_system` | VARCHAR(32) | NO |  | 来源系统：FUIOU/TAOBAO/MEITUAN/JD |
| `source_merchant_code` | VARCHAR(64) | NO |  | 来源账号 |
| `source_shop_id` | VARCHAR(128) | NO |  | 来源门店ID |
| `id_namespace` | VARCHAR(32) | NO |  | 平台编号类型 |
| `source_id` | VARCHAR(128) | NO |  | 该类型的平台编号 |
| `parent_id_namespace` | VARCHAR(32) | YES |  | 父商品编号类型 |
| `parent_source_id` | VARCHAR(128) | YES |  | 父商品编号 |
| `product_name` | VARCHAR(512) | YES |  | 官方商品名称 |
| `spec_name` | VARCHAR(512) | YES |  | 官方规格名称 |
| `attributes_json` | STRING | YES |  | 其他官方原始编号及展示属性JSON |
| `is_deleted` | BOOLEAN | NO | '0' | 目录节点状态：0有效/1关闭 |
| `catalog_round` | VARCHAR(128) | NO |  | 目录轮次标识 |
| `meta_process_time` | DATETIME(3) | NO |  |  |
| `meta_sink_sequence` | BIGINT | NO |  |  |

### `trade_warehouse.dim_goods_product_source_v1`

- 证据类型：DDL。文件 `docs/sql/init/trade-doris-init.sql`。实际数据库未核对。
- 表注释：来源门店商品到内部标准商品映射表 v1
- 引擎：`ENGINE=OLAP`
- 模型键：`UNIQUE KEY( `source_system`, `source_merchant_code`, `source_shop_id`, `source_product_id` )`
- 分桶：`DISTRIBUTED BY HASH(`source_merchant_code`, `source_shop_id`) BUCKETS 16`
- Sequence 列：`meta_sink_sequence`
- 代码引用：ProductSourceMapDO。GoodsDimensionCdcJob。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `source_system` | VARCHAR(32) | NO |  | 来源系统，如FUIOU/TAOBAO/MEITUAN/JD |
| `source_merchant_code` | VARCHAR(64) | NO |  | 来源商户标识，如mchntCd/appKey/app_id |
| `source_shop_id` | VARCHAR(128) | NO |  | 来源门店ID，按平台原值保存，0为总店 |
| `source_product_id` | VARCHAR(128) | NO |  | 来源商品ID：富智云goodsId、淘宝商品ID、美团mt_spu_id、京东skuId |
| `internal_product_id` | BIGINT | YES |  | 映射后的内部标准商品ID；UNMAPPED时为空 |
| `source_product_name` | VARCHAR(512) | YES |  | 该门店来源商品名称 |
| `mapping_type` | VARCHAR(32) | NO |  | 映射类型：HEAD/RELATE/NAME/INDEPENDENT/MANUAL/UNMAPPED/SSM |
| `is_deleted` | BOOLEAN | NO | '0' | 逻辑删除：0=有效，1=已废弃 |
| `meta_process_time` | DATETIME(3) | NO |  |  |
| `meta_sink_sequence` | BIGINT | NO |  |  |

### `trade_warehouse.dim_goods_category_v1`

- 证据类型：DDL。文件 `docs/sql/init/trade-doris-init.sql`。实际数据库未核对。
- 表注释：商品域标准分类当前维表 v1
- 引擎：`ENGINE=OLAP`
- 模型键：`UNIQUE KEY(`internal_category_id`)`
- 分桶：`DISTRIBUTED BY HASH(`internal_category_id`) BUCKETS 4`
- Sequence 列：`meta_sink_sequence`
- 代码引用：CategoryDimDO。GoodsDimensionCdcJob。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `internal_category_id` | BIGINT | NO |  | 内部统一分类ID |
| `brand_id` | BIGINT | YES |  | 内部品牌ID，关联MySQL dim_brand.brand_id |
| `category_name` | VARCHAR(256) | NO |  |  |
| `category_type` | VARCHAR(32) | NO | 'NORMAL' | 分类类型：NORMAL普通分类；SYSTEM_OTHER租户系统兜底分类 |
| `sort_order` | INT | YES |  |  |
| `is_deleted` | BOOLEAN | NO | '0' |  |
| `meta_process_time` | DATETIME(3) | NO |  |  |
| `meta_sink_sequence` | BIGINT | NO |  |  |

### `trade_warehouse.dim_goods_product_package_rel_v1`

- 证据类型：DDL。文件 `docs/sql/init/trade-doris-init.sql`。实际数据库未核对。
- 表注释：来源门店套餐当前配置关系 v1
- 引擎：`ENGINE=OLAP`
- 模型键：`UNIQUE KEY( `source_system`, `source_merchant_code`, `source_shop_id`, `source_package_product_id`, `option_index`, `goods_index`, `source_component_product_id` )`
- 分桶：`DISTRIBUTED BY HASH(`source_merchant_code`, `source_shop_id`) BUCKETS 16`
- Sequence 列：`meta_sink_sequence`
- 代码引用：ProductPackageRelDO。GoodsDimensionCdcJob。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `source_system` | VARCHAR(32) | NO |  |  |
| `source_merchant_code` | VARCHAR(64) | NO |  |  |
| `source_shop_id` | VARCHAR(128) | NO |  | 套餐所属来源门店，按平台原值保存，0为总店 |
| `source_package_product_id` | VARCHAR(128) | NO |  |  |
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
| `up_price_amount` | BIGINT | NO | '0' | 加价金额，分 |
| `is_deleted` | BOOLEAN | NO | '0' | 逻辑删除：0=有效，1=已废弃 |
| `meta_process_time` | DATETIME(3) | NO |  |  |
| `meta_sink_sequence` | BIGINT | NO |  |  |

### `trade_warehouse.dwd_trade_order_fact_v1`

- 证据类型：DDL。文件 `docs/sql/init/trade-doris-init.sql`。实际数据库未核对。
- 表注释：统一订单当前事实物理表 v1
- 引擎：`ENGINE=OLAP`
- 模型键：`UNIQUE KEY(`order_created_date`, `platform_code`, `platform_order_id`)`
- 分区：`PARTITION BY RANGE(`order_created_date`) ()`
- 分桶：`DISTRIBUTED BY HASH(`platform_code`, `platform_order_id`) BUCKETS 16`
- Sequence 列：`meta_sink_sequence`
- 代码引用：DorisCanonicalFactWriter，逻辑名 dwd_trade_order_fact 加物理版本 v1。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `platform_code` | VARCHAR(16) | NO |  | 平台编码 |
| `platform_order_id` | VARCHAR(128) | NO |  | 平台订单号 |
| `internal_shop_id` | BIGINT | YES |  | 跨服务商稳定的统一门店ID |
| `brand_id` | BIGINT | YES |  | 品牌ID |
| `platform_parent_order_id` | VARCHAR(128) | YES |  | 平台父单号或拆单前订单号 |
| `platform_shop_id` | VARCHAR(128) | NO |  | 平台门店号 |
| `business_scene` | VARCHAR(16) | NO |  | 业务场景 |
| `order_type` | VARCHAR(16) | NO |  | 标准订单类型 |
| `channel_category` | VARCHAR(32) | NO |  | 订单渠道大类，含堂食与团购组合支付内部类型 |
| `channel_code` | VARCHAR(32) | NO |  | 渠道小类 |
| `delivery_mode` | VARCHAR(32) | YES |  | 配送或履约方式 |
| `currency_code` | CHAR(3) | NO |  | ISO 4217币种编码 |
| `order_status` | VARCHAR(32) | NO |  | 标准订单状态 |
| `payment_status` | VARCHAR(32) | NO | "NONE" | 标准支付状态 |
| `refund_type` | VARCHAR(32) | YES | "NONE" | 标准退款范围NONE、PARTIAL或FULL |
| `refund_status` | VARCHAR(32) | YES | "NONE" | 标准退款操作状态 |
| `delivery_status` | VARCHAR(32) | YES |  | 标准配送状态 |
| `fulfillment_status` | VARCHAR(32) | NO |  | 标准履约进度 |
| `complaint_status` | VARCHAR(32) | NO |  | 标准投诉状态 |
| `group_buy_status` | VARCHAR(32) | NO |  | 标准团购状态 |
| `cancel_reason_code` | VARCHAR(64) | YES |  | 标准取消原因编码 |
| `cancel_reason` | VARCHAR(1024) | YES |  | 取消原因说明 |
| `goods_original_amount` | BIGINT | YES |  | 商品优惠前总金额，单位分 |
| `order_original_amount` | BIGINT | YES |  | 订单原始应收金额，单位分 |
| `order_discount_base_amount` | BIGINT | YES |  | 执行订单级优惠前的计价基数 |
| `order_after_discount_amount` | BIGINT | YES |  | 优惠后订单应付金额，单位分 |
| `turnover_amount` | BIGINT | YES |  | 营业额，单位分 |
| `user_pay_amount` | BIGINT | YES |  | 用户实付金额，单位分 |
| `total_discount_amount` | BIGINT | YES |  | 优惠总金额，单位分 |
| `packing_fee_amount` | BIGINT | YES |  | 包装费，单位分 |
| `user_delivery_fee_amount` | BIGINT | YES |  | 用户承担配送费，单位分 |
| `merchant_estimated_income_amount` | BIGINT | YES |  | 商家预计收入，单位分 |
| `order_refund_success_amount` | BIGINT | YES |  | 订单累计成功退款金额，单位分 |
| `item_quantity` | DECIMAL(18,3) | YES |  | 订单商品总数量 |
| `guest_count` | INT | YES |  | 就餐人数 |
| `is_membership` | TINYINT | YES | '0' | 会员状态：0未映射，1会员，2非会员 |
| `customer_name_masked` | VARCHAR(128) | YES |  | 脱敏客户姓名或展示名 |
| `customer_phone_masked` | VARCHAR(32) | YES |  | 脱敏客户手机号 |
| `customer_note` | VARCHAR(1024) | YES |  | 经治理后的客户备注 |
| `order_created_time` | DATETIME(3) | NO |  | 下单时间 |
| `appointment_time` | DATETIME(3) | YES |  | 预约时间 |
| `paid_time` | DATETIME(3) | YES |  | 支付成功时间 |
| `accepted_time` | DATETIME(3) | YES |  | 商家接单时间 |
| `completed_time` | DATETIME(3) | YES |  | 订单完成时间 |
| `cancelled_time` | DATETIME(3) | YES |  | 取消时间 |
| `closed_time` | DATETIME(3) | YES |  | 关闭时间 |
| `meta_transform_version` | VARCHAR(64) | NO |  | 转换逻辑版本 |
| `meta_process_time` | DATETIME(3) | NO |  | 事实处理时间 |
| `meta_sink_sequence` | BIGINT | NO |  | Doris写入顺序版本 |
| `meta_detail_ready` | BOOLEAN | NO | '0' | 商品和价格详情是否完整 |
| `meta_discount_ready` | BOOLEAN | NO | '0' | 优惠详情是否完整 |
| `meta_payment_ready` | BOOLEAN | NO | '0' | 支付详情是否完整 |
| `meta_refund_ready` | BOOLEAN | NO | '0' | 退款详情是否完整 |
| `meta_settlement_ready` | BOOLEAN | NO | '0' | 结算详情是否完整 |
| `meta_group_buy_ready` | BOOLEAN | NO | '0' | 团购核销信息是否完整 |
| `meta_data_quality_status` | VARCHAR(32) | NO |  | 数据质量状态 |
| `src_order_status` | VARCHAR(64) | YES |  | 平台原始订单状态 |
| `src_payment_status` | VARCHAR(64) | YES |  | 平台原始支付状态 |
| `src_refund_status` | VARCHAR(64) | YES |  | 平台原始退款状态 |
| `src_delivery_status` | VARCHAR(64) | YES |  | 平台原始配送状态 |
| `src_updated_time` | DATETIME(3) | YES |  | 来源对象更新时间 |
| `src_core_version` | BIGINT | NO |  | 订单核心字段来源版本 |
| `src_status_version` | BIGINT | NO |  | 订单状态字段来源版本 |
| `src_fulfillment_version` | BIGINT | NO |  | 订单履约字段来源版本 |
| `src_payment_version` | BIGINT | NO |  | 订单支付字段来源版本 |
| `src_refund_version` | BIGINT | NO |  | 订单退款字段来源版本 |
| `src_detail_version` | BIGINT | NO |  | 订单详情字段来源版本 |
| `src_last_event_id` | BIGINT | NO |  | 最后写入的来源事件ID |
| `src_last_storage_id` | BIGINT | NO |  | 最后写入事件的Storage ID |
| `src_last_payload_sha256` | CHAR(64) | NO |  | 最后写入原文SHA-256 |
| `src_last_status_event_id` | BIGINT | YES |  | 状态字段最后来源事件ID |
| `src_last_status_storage_id` | BIGINT | YES |  | 状态字段最后Storage ID |
| `src_last_status_payload_sha256` | CHAR(64) | YES |  | 状态字段最后原文SHA-256 |
| `src_last_snapshot_event_id` | BIGINT | YES |  | 详情快照最后来源事件ID |
| `src_last_snapshot_storage_id` | BIGINT | YES |  | 详情快照最后Storage ID |
| `src_last_snapshot_payload_sha256` | CHAR(64) | YES |  | 详情快照最后原文SHA-256 |
| `src_event_received_time` | DATETIME(3) | NO |  | 事件接收时间 |
| `refund_flow_status` | VARCHAR(64) | YES |  | 退款流程状态 |
| `src_refund_type` | VARCHAR(64) | YES |  | 平台原始退款类型 |
| `src_delivery_version` | BIGINT | YES |  | 配送状态Domain版本 |
| `src_delivery_mode_version` | BIGINT | YES |  | 配送方式Domain版本 |
| `src_preorder_version` | BIGINT | YES |  | 预订单Domain版本 |
| `src_cancel_version` | BIGINT | YES |  | 取消流程Domain版本 |
| `src_refund_flow_version` | BIGINT | YES |  | 退款流程Domain版本 |
| `src_refund_summary_revision` | BIGINT | YES |  | 退款汇总投影版本 |
| `src_refund_pickup_result_sequence` | BIGINT | NO | "0" | 退款商品查询结果序号 |
| `src_order_payload_sha256` | VARCHAR(64) | YES |  | 订单Domain确定性指纹 |
| `src_fulfillment_payload_sha256` | VARCHAR(64) | YES |  | 履约Domain确定性指纹 |
| `src_delivery_payload_sha256` | VARCHAR(64) | YES |  | 配送Domain确定性指纹 |
| `src_delivery_mode_payload_sha256` | VARCHAR(64) | YES |  | 配送方式Domain确定性指纹 |
| `src_payment_payload_sha256` | VARCHAR(64) | YES |  | 支付Domain确定性指纹 |
| `src_preorder_payload_sha256` | VARCHAR(64) | YES |  | 预订单Domain确定性指纹 |
| `src_cancel_payload_sha256` | VARCHAR(64) | YES |  | 取消流程Domain确定性指纹 |
| `src_refund_flow_payload_sha256` | VARCHAR(64) | YES |  | 退款流程Domain确定性指纹 |
| `src_refund_summary_payload_sha256` | VARCHAR(64) | YES |  | 退款汇总投影确定性指纹 |
| `src_core_event_id` | BIGINT | YES |  | 核心快照最后来源事件ID |
| `src_core_storage_id` | BIGINT | YES |  | 核心快照最后Storage ID |
| `src_core_payload_sha256` | CHAR(64) | YES |  | 核心快照最后原文SHA-256 |
| `src_core_snapshot_source` | VARCHAR(32) | YES |  | 核心快照标准来源类型 |

主键、唯一键和二级索引：
- `INDEX `idx_order_internal_shop_id` (`internal_shop_id`) USING INVERTED`

### `trade_warehouse.dwd_trade_order_item_fact_v1`

- 证据类型：DDL。文件 `docs/sql/init/trade-doris-init.sql`。实际数据库未核对。
- 表注释：统一订单商品当前事实物理表 v1
- 引擎：`ENGINE=OLAP`
- 模型键：`UNIQUE KEY(`order_created_date`, `platform_code`, `platform_order_id`, `item_line_key`)`
- 分区：`PARTITION BY RANGE(`order_created_date`) ()`
- 分桶：`DISTRIBUTED BY HASH(`platform_code`, `platform_order_id`) BUCKETS 16`
- Sequence 列：`meta_sink_sequence`
- 代码引用：DorisCanonicalFactWriter 明细。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `platform_code` | VARCHAR(16) | NO |  | 平台编码 |
| `platform_order_id` | VARCHAR(128) | NO |  | 平台订单号 |
| `item_line_key` | VARCHAR(128) | NO |  | 订单内商品稳定行键 |
| `brand_id` | BIGINT | YES |  | 品牌ID |
| `internal_product_id` | BIGINT | YES |  | 内部商品ID |
| `internal_category_id` | BIGINT | YES |  | 内部类目ID |
| `platform_shop_id` | VARCHAR(128) | YES |  | 平台门店号 |
| `platform_item_id` | VARCHAR(128) | YES |  | 平台商品ID |
| `platform_sku_id` | VARCHAR(128) | YES |  | 平台SKU ID |
| `source_merchant_code` | VARCHAR(64) | YES |  | 来源账号 |
| `resolved_platform_sku_id` | VARCHAR(128) | YES |  | 旧规格编号 |
| `src_platform_product_id` | VARCHAR(128) | YES |  | 平台可售SKU编号，与SSM平台商品编号一致 |
| `platform_category_id` | VARCHAR(128) | YES |  | 平台类目ID |
| `platform_category_name` | VARCHAR(256) | YES |  | 平台类目名称 |
| `parent_item_line_key` | VARCHAR(128) | YES |  | 套餐父商品行键 |
| `item_type` | VARCHAR(32) | NO |  | 商品行类型 |
| `item_name` | VARCHAR(2048) | NO |  | 商品名称 |
| `sku_name` | VARCHAR(256) | YES |  | SKU名称 |
| `spec_description` | VARCHAR(1024) | YES |  | 规格描述 |
| `item_status` | VARCHAR(32) | YES |  | 商品行状态 |
| `quantity` | DECIMAL(18,3) | NO |  | 商品数量 |
| `original_unit_price` | BIGINT | YES |  | 商品原价单价，单位分 |
| `original_amount` | BIGINT | YES |  | 商品原价金额，单位分 |
| `discounted_unit_price` | BIGINT | YES |  | 商品折后单价，单位分 |
| `discounted_amount` | BIGINT | YES |  | 商品折后计价金额，单位分，不等同最终实付 |
| `discount_amount` | BIGINT | YES |  | 商品优惠金额，单位分；统一语义冻结前可为空 |
| `user_pay_amount` | BIGINT | YES |  | 用户实付金额，单位分 |
| `merchant_income_amount` | BIGINT | YES |  | 商品商家预计收入，单位分 |
| `packing_fee_amount` | BIGINT | YES |  | 包装费，单位分 |
| `refund_success_quantity` | DECIMAL(18,3) | NO | '0' | 商品累计成功退款数量 |
| `refund_success_amount` | BIGINT | NO | '0' | 商品累计成功退款金额，单位分 |
| `meta_transform_version` | VARCHAR(64) | NO |  | 转换逻辑版本 |
| `meta_process_time` | DATETIME(3) | NO |  | 事实处理时间 |
| `meta_is_active` | BOOLEAN | NO | '1' | 成员是否有效 |
| `meta_sink_sequence` | BIGINT | NO |  | Doris写入顺序版本 |
| `meta_merchant_income_ready` | BOOLEAN | NO | '0' | 商品商家收入是否可信 |
| `src_version` | BIGINT | NO |  | 当前事实来源版本 |
| `src_last_event_id` | BIGINT | NO |  | 最后写入的来源事件ID |
| `src_last_storage_id` | BIGINT | NO |  | 最后写入事件的Storage ID |
| `src_last_payload_sha256` | CHAR(64) | NO |  | 最后写入原文SHA-256 |
| `src_event_received_time` | DATETIME(3) | NO |  | 事件接收时间 |

主键、唯一键和二级索引：
- `INDEX `idx_item_platform_order_id` (`platform_order_id`) USING INVERTED`
- `INDEX `idx_order_item_internal_shop_id` (`internal_shop_id`) USING INVERTED`

### `trade_warehouse.dwd_trade_order_discount_fact_v1`

- 证据类型：DDL。文件 `docs/sql/init/trade-doris-init.sql`。实际数据库未核对。
- 表注释：统一订单优惠当前事实物理表 v1
- 引擎：`ENGINE=OLAP`
- 模型键：`UNIQUE KEY(`order_created_date`, `platform_code`, `platform_order_id`, `discount_line_key`)`
- 分区：`PARTITION BY RANGE(`order_created_date`) ()`
- 分桶：`DISTRIBUTED BY HASH(`platform_code`, `platform_order_id`) BUCKETS 8`
- Sequence 列：`meta_sink_sequence`
- 代码引用：DorisCanonicalFactWriter 优惠。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `platform_code` | VARCHAR(16) | NO |  | 平台编码 |
| `platform_order_id` | VARCHAR(128) | NO |  | 平台订单号 |
| `discount_line_key` | VARCHAR(128) | NO |  | 订单内优惠稳定行键 |
| `brand_id` | BIGINT | YES |  | 品牌ID |
| `platform_shop_id` | VARCHAR(128) | YES |  | 平台门店号 |
| `platform_activity_id` | VARCHAR(128) | YES |  | 平台活动ID |
| `activity_name` | VARCHAR(256) | YES |  | 活动名称 |
| `discount_type` | VARCHAR(32) | YES |  | 优惠类型 |
| `benefit_type` | VARCHAR(32) | NO |  | 优惠权益类型 |
| `discount_scope` | VARCHAR(32) | NO |  | 优惠作用范围 |
| `item_line_key` | VARCHAR(128) | YES |  | 订单内商品稳定行键 |
| `benefit_amount` | BIGINT | YES |  | 优惠权益金额，单位分 |
| `merchant_bear_amount` | BIGINT | YES |  | 商家承担金额，单位分 |
| `platform_bear_amount` | BIGINT | YES |  | 平台承担金额，单位分 |
| `other_bear_amount` | BIGINT | YES |  | 其他方承担金额，单位分 |
| `user_bear_amount` | BIGINT | YES |  | 用户承担金额，单位分 |
| `verification_quantity` | DECIMAL(18,3) | YES |  | 来源明确给出的团购券使用数量；完整核销过程见团购核销事实 |
| `meta_transform_version` | VARCHAR(64) | NO |  | 转换逻辑版本 |
| `meta_process_time` | DATETIME(3) | NO |  | 事实处理时间 |
| `meta_is_active` | BOOLEAN | NO | '1' | 成员是否有效 |
| `meta_sink_sequence` | BIGINT | NO |  | Doris写入顺序版本 |
| `src_version` | BIGINT | NO |  | 当前事实来源版本 |
| `src_last_event_id` | BIGINT | NO |  | 最后写入的来源事件ID |
| `src_last_storage_id` | BIGINT | NO |  | 最后写入事件的Storage ID |
| `src_last_payload_sha256` | CHAR(64) | NO |  | 最后写入原文SHA-256 |
| `src_event_received_time` | DATETIME(3) | NO |  | 事件接收时间 |

### `trade_warehouse.dwd_trade_group_buy_verification_fact_v1`

- 证据类型：DDL。文件 `docs/sql/init/trade-doris-init.sql`。实际数据库未核对。
- 表注释：富智云券核销接口原始明细
- 引擎：`ENGINE=OLAP`
- 模型键：`UNIQUE KEY(`brand_id`, `coupon_info_id`, `check_month`)`
- 分区：`AUTO PARTITION BY RANGE (date_trunc(`check_month`, 'month')) ()`
- 分桶：`DISTRIBUTED BY HASH(`brand_id`, `coupon_info_id`) BUCKETS 8`
- 代码引用：FuiouGroupBuyVerificationSyncService Stream Load。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `brand_id` | BIGINT | NO |  | 内部品牌ID |
| `coupon_info_id` | BIGINT | NO |  | 券实例ID |
| `check_month` | DATE | NO |  | 核销月份，月初日期 |
| `channel_desc` | STRING | YES |  | 渠道名称 |
| `channel_type` | STRING | YES |  | 渠道类型ID |
| `check_tm` | STRING | YES |  | 核销时间原文 |
| `check_pay_num` | BIGINT | YES |  | 核销张数 |
| `check_pay_times` | BIGINT | YES |  | 核销次数 |
| `buy_price_amt` | DECIMAL(24,6) | YES |  | 券购买价，单位分 |
| `coupon_amt` | DECIMAL(24,6) | YES |  | 商家优惠，单位分 |
| `coupon_type` | STRING | YES |  | 券类型 |
| `face_amt` | DECIMAL(24,6) | YES |  | 券面值，单位分 |
| `face_value` | DECIMAL(24,6) | YES |  | 实际抵扣金额，单位分 |
| `group_buy_code` | STRING | YES |  | 券码 |
| `group_buy_id` | STRING | YES |  | 团购ID |
| `group_buy_name` | STRING | YES |  | 券名称 |
| `group_buy_type` | STRING | YES |  | 团购类型ID |
| `group_buy_type_desc` | STRING | YES |  | 团购类型名 |
| `mchnt_income` | DECIMAL(24,6) | YES |  | 商家总收入，单位分 |
| `platform_order_id` | VARCHAR(128) | YES |  | 平台订单号 |
| `plat_hong_bao` | DECIMAL(24,6) | YES |  | 平台优惠，单位分 |
| `result` | STRING | YES |  | 核销结果原值 |
| `platform_shop_id` | VARCHAR(128) | YES |  | 平台门店号 |
| `platform_shop_name` | STRING | YES |  | 平台门店名称 |
| `third_mchnt_income` | DECIMAL(24,6) | YES |  | 商家应得，单位分 |
| `third_product_id` | STRING | YES |  | 券项目ID |
| `third_service_fee` | DECIMAL(24,6) | YES |  | 服务费，单位分 |
| `trans_id` | STRING | YES |  | 交易ID |
| `internal_shop_id` | BIGINT | YES |  | 跨服务商稳定的统一门店ID |

### `trade_warehouse.dwd_trade_payment_fact_v1`

- 证据类型：DDL。文件 `docs/sql/init/trade-doris-init.sql`。实际数据库未核对。
- 表注释：统一支付当前事实物理表 v1
- 引擎：`ENGINE=OLAP`
- 模型键：`UNIQUE KEY(`payment_date`, `platform_code`, `payment_id`, `payment_line_key`)`
- 分区：`PARTITION BY RANGE(`payment_date`) ()`
- 分桶：`DISTRIBUTED BY HASH(`platform_code`, `payment_id`) BUCKETS 8`
- Sequence 列：`meta_sink_sequence`
- 代码引用：DorisCanonicalFactWriter 支付。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `platform_code` | VARCHAR(16) | NO |  | 平台编码 |
| `payment_id` | VARCHAR(128) | NO |  | 支付流水ID |
| `payment_line_key` | VARCHAR(128) | NO |  | 支付明细稳定行键 |
| `brand_id` | BIGINT | YES |  | 品牌ID |
| `platform_order_id` | VARCHAR(128) | YES |  | 平台订单号 |
| `platform_shop_id` | VARCHAR(128) | YES |  | 平台门店号 |
| `business_scene` | VARCHAR(16) | NO |  | 支付流水所属统一业务场景 |
| `payment_status` | VARCHAR(32) | NO |  | 标准支付状态 |
| `payment_method` | VARCHAR(32) | NO |  | 标准支付方式 |
| `paid_amount` | BIGINT | NO |  | 支付金额，单位分 |
| `payment_fee_amount` | BIGINT | YES |  | 支付手续费，单位分 |
| `voucher_face_amount` | BIGINT | YES |  | 团购券券面金额，单位分；不等同于用户实付 |
| `stored_value_principal_amount` | BIGINT | YES |  | 储值本金支付金额，单位分 |
| `stored_value_gift_amount` | BIGINT | YES |  | 储值赠送金支付金额，单位分 |
| `paid_time` | DATETIME(3) | YES |  | 支付成功时间 |
| `reversed_time` | DATETIME(3) | YES |  | 冲正或撤销时间 |
| `meta_transform_version` | VARCHAR(64) | NO |  | 转换逻辑版本 |
| `meta_process_time` | DATETIME(3) | NO |  | 事实处理时间 |
| `meta_sink_sequence` | BIGINT | NO |  | Doris写入顺序版本 |
| `src_order_source` | VARCHAR(32) | YES |  | 平台原始订单来源编码 |
| `src_updated_time` | DATETIME(3) | YES |  | 来源对象更新时间 |
| `src_version` | BIGINT | NO |  | 当前事实来源版本 |
| `src_last_event_id` | BIGINT | NO |  | 最后写入的来源事件ID |
| `src_last_storage_id` | BIGINT | NO |  | 最后写入事件的Storage ID |
| `src_last_payload_sha256` | CHAR(64) | NO |  | 最后写入原文SHA-256 |
| `src_event_received_time` | DATETIME(3) | NO |  | 事件接收时间 |

主键、唯一键和二级索引：
- `INDEX `idx_platform_order_id` (`platform_order_id`) USING INVERTED`

### `trade_warehouse.dwd_trade_recharge_fact_v1`

- 证据类型：DDL。文件 `docs/sql/init/trade-doris-init.sql`。实际数据库未核对。
- 表注释：统一充值当前事实物理表 v1
- 引擎：`ENGINE=OLAP`
- 模型键：`UNIQUE KEY(`recharge_date`, `platform_code`, `recharge_id`)`
- 分区：`PARTITION BY RANGE(`recharge_date`) ()`
- 分桶：`DISTRIBUTED BY HASH(`platform_code`, `recharge_id`) BUCKETS 8`
- Sequence 列：`meta_sink_sequence`
- 代码引用：DorisCanonicalFactWriter 充值。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `platform_code` | VARCHAR(16) | NO |  | 平台编码 |
| `recharge_id` | VARCHAR(128) | NO |  | 充值流水ID |
| `internal_shop_id` | BIGINT | YES |  | 跨服务商稳定的统一门店ID |
| `brand_id` | BIGINT | YES |  | 内部品牌ID |
| `platform_shop_id` | VARCHAR(128) | YES |  | 平台门店号 |
| `platform_shop_name` | VARCHAR(256) | YES |  | 平台门店名称 |
| `recharge_status` | VARCHAR(32) | NO |  | 充值状态 |
| `recharge_type` | VARCHAR(32) | NO |  | 充值类型 |
| `recharge_channel` | VARCHAR(32) | NO |  | 充值渠道 |
| `direction` | VARCHAR(16) | NO |  | 资金方向 |
| `currency_code` | CHAR(3) | NO |  | ISO 4217币种编码 |
| `payment_method` | VARCHAR(32) | NO |  | 标准支付方式 |
| `recharge_amount` | BIGINT | NO |  | 充值本金金额，单位分 |
| `gift_amount` | BIGINT | YES |  | 充值赠送金额，单位分 |
| `payment_fee_amount` | BIGINT | YES |  | 支付手续费，单位分 |
| `member_name_masked` | VARCHAR(256) | YES |  | 脱敏会员姓名 |
| `member_phone_masked` | VARCHAR(32) | YES |  | 脱敏会员手机号 |
| `member_card_no_masked` | VARCHAR(128) | YES |  | 脱敏会员卡号 |
| `member_level` | VARCHAR(32) | YES |  | 会员等级 |
| `recharge_time` | DATETIME(3) | NO |  | 充值时间 |
| `reversed_time` | DATETIME(3) | YES |  | 冲正或撤销时间 |
| `meta_transform_version` | VARCHAR(64) | NO |  | 转换逻辑版本 |
| `meta_process_time` | DATETIME(3) | NO |  | 事实处理时间 |
| `meta_sink_sequence` | BIGINT | NO |  | Doris写入顺序版本 |
| `src_payment_method` | VARCHAR(64) | YES |  | 平台原始支付方式 |
| `src_business_category` | VARCHAR(32) | YES |  | 来源业务大类 |
| `src_business_subcategory` | VARCHAR(32) | YES |  | 来源业务小类 |
| `src_order_source` | VARCHAR(32) | YES |  | 来源订单来源 |
| `src_recharge_status` | VARCHAR(32) | YES |  | 平台原始充值状态 |
| `src_updated_time` | DATETIME(3) | YES |  | 来源对象更新时间 |
| `src_version` | BIGINT | NO |  | 当前事实来源版本 |
| `src_last_event_id` | BIGINT | NO |  | 最后写入的来源事件ID |
| `src_last_storage_id` | BIGINT | NO |  | 最后写入事件的Storage ID |
| `src_last_payload_sha256` | CHAR(64) | NO |  | 最后写入原文SHA-256 |
| `src_event_received_time` | DATETIME(3) | NO |  | 事件接收时间 |

### `trade_warehouse.dwd_trade_refund_fact_v1`

- 证据类型：DDL。文件 `docs/sql/init/trade-doris-init.sql`。实际数据库未核对。
- 表注释：统一退款当前事实物理表 v1
- 引擎：`ENGINE=OLAP`
- 模型键：`UNIQUE KEY(`refund_date`, `platform_code`, `refund_id`)`
- 分区：`PARTITION BY RANGE(`refund_date`) ()`
- 分桶：`DISTRIBUTED BY HASH(`platform_code`, `refund_id`) BUCKETS 8`
- Sequence 列：`meta_sink_sequence`
- 代码引用：DorisCanonicalFactWriter 退款。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `platform_code` | VARCHAR(16) | NO |  | 平台编码 |
| `refund_id` | VARCHAR(128) | NO |  | 退款单号 |
| `original_payment_id` | VARCHAR(128) | YES |  | 原支付流水号，来源未提供稳定关联时为空 |
| `internal_shop_id` | BIGINT | YES |  | 跨服务商稳定的统一门店ID |
| `brand_id` | BIGINT | YES |  | 内部品牌ID |
| `refund_type` | VARCHAR(32) | NO | "NONE" | 退款类型 |
| `refund_status` | VARCHAR(32) | NO | "NONE" | 标准退款状态 |
| `apply_time` | DATETIME(3) | YES |  | 退款申请时间 |
| `success_time` | DATETIME(3) | YES |  | 退款成功时间 |
| `closed_time` | DATETIME(3) | YES |  | 关闭时间 |
| `refund_amount` | BIGINT | YES |  | 退款金额，单位分 |
| `merchant_bear_amount` | BIGINT | YES |  | 商家承担金额，单位分 |
| `platform_bear_amount` | BIGINT | YES |  | 平台承担金额，单位分 |
| `other_bear_amount` | BIGINT | YES |  | 其他方承担金额，单位分 |
| `refund_reason_code` | VARCHAR(64) | YES |  | 退款原因编码 |
| `refund_reason` | VARCHAR(1024) | YES |  | 退款原因说明 |
| `arbitration_result` | VARCHAR(256) | YES |  | 仲裁处理结果 |
| `meta_transform_version` | VARCHAR(64) | NO |  | 转换逻辑版本 |
| `meta_process_time` | DATETIME(3) | NO |  | 事实处理时间 |
| `meta_sink_sequence` | BIGINT | NO |  | Doris写入顺序版本 |
| `src_version` | BIGINT | NO |  | 当前事实来源版本 |
| `src_last_event_id` | BIGINT | NO |  | 最后写入的来源事件ID |
| `src_last_storage_id` | BIGINT | NO |  | 最后写入事件的Storage ID |
| `src_last_payload_sha256` | CHAR(64) | NO |  | 最后写入原文SHA-256 |
| `src_event_received_time` | DATETIME(3) | NO |  | 事件接收时间 |
| `src_refund_type` | VARCHAR(64) | YES |  | 平台原始退款类型 |
| `src_refund_status` | VARCHAR(64) | YES |  | 平台原始退款状态 |
| `flow_kind` | VARCHAR(32) | YES |  | 退款或取消流程类型 |

### `trade_warehouse.dwd_trade_refund_item_fact_v1`

- 证据类型：DDL。文件 `docs/sql/init/trade-doris-init.sql`。实际数据库未核对。
- 表注释：统一退款商品当前事实物理表 v1
- 引擎：`ENGINE=OLAP`
- 模型键：`UNIQUE KEY(`refund_date`, `platform_code`, `refund_id`, `refund_item_line_key`)`
- 分区：`PARTITION BY RANGE(`refund_date`) ()`
- 分桶：`DISTRIBUTED BY HASH(`platform_code`, `refund_id`) BUCKETS 8`
- Sequence 列：`meta_sink_sequence`
- 代码引用：DorisCanonicalFactWriter 退款明细。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `platform_code` | VARCHAR(16) | NO |  | 平台编码 |
| `refund_id` | VARCHAR(128) | NO |  | 退款单号 |
| `refund_item_line_key` | VARCHAR(128) | NO |  | 退款商品稳定行键 |
| `internal_shop_id` | BIGINT | YES |  | 跨服务商稳定的统一门店ID |
| `brand_id` | BIGINT | YES |  | 内部品牌ID |
| `internal_product_id` | BIGINT | YES |  | 内部商品ID |
| `internal_category_id` | BIGINT | YES |  | 内部类目ID |
| `order_item_line_key` | VARCHAR(128) | YES |  | 原订单商品行键 |
| `platform_item_id` | VARCHAR(128) | YES |  | 平台商品ID |
| `platform_sku_id` | VARCHAR(128) | YES |  | 平台SKU ID |
| `source_merchant_code` | VARCHAR(64) | YES |  | 来源账号 |
| `resolved_platform_sku_id` | VARCHAR(128) | YES |  | 旧规格编号 |
| `src_platform_product_id` | VARCHAR(128) | YES |  | 平台可售SKU编号，与SSM平台商品编号一致 |
| `platform_shop_id` | VARCHAR(128) | YES |  | 平台门店号 |
| `item_name` | VARCHAR(512) | YES |  | 商品名称 |
| `refund_quantity` | DECIMAL(18,3) | YES |  | 退款商品数量 |
| `refund_amount` | BIGINT | YES |  | 退款金额，单位分 |
| `merchant_bear_amount` | BIGINT | YES |  | 商家承担金额，单位分 |
| `platform_bear_amount` | BIGINT | YES |  | 平台承担金额，单位分 |
| `meta_transform_version` | VARCHAR(64) | NO |  | 转换逻辑版本 |
| `meta_process_time` | DATETIME(3) | NO |  | 事实处理时间 |
| `meta_sink_sequence` | BIGINT | NO |  | Doris写入顺序版本 |
| `src_version` | BIGINT | NO |  | 当前事实来源版本 |
| `src_last_event_id` | BIGINT | NO |  | 最后写入的来源事件ID |
| `src_last_storage_id` | BIGINT | NO |  | 最后写入事件的Storage ID |
| `src_last_payload_sha256` | CHAR(64) | NO |  | 最后写入原文SHA-256 |
| `src_event_received_time` | DATETIME(3) | NO |  | 事件接收时间 |

### `trade_warehouse.dwd_trade_complaint_fact_v1`

- 证据类型：DDL。文件 `docs/sql/init/trade-doris-init.sql`。实际数据库未核对。
- 表注释：统一投诉当前事实物理表 v1
- 引擎：`ENGINE=OLAP`
- 模型键：`UNIQUE KEY(`complaint_created_date`, `platform_code`, `complaint_id`)`
- 分区：`PARTITION BY RANGE(`complaint_created_date`) ()`
- 分桶：`DISTRIBUTED BY HASH(`platform_code`, `complaint_id`) BUCKETS 8`
- Sequence 列：`meta_sink_sequence`
- 代码引用：DorisCanonicalFactWriter 投诉。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `platform_code` | VARCHAR(16) | NO |  | 平台编码 |
| `complaint_id` | VARCHAR(128) | NO |  | 投诉单号 |
| `internal_shop_id` | BIGINT | YES |  | 跨服务商稳定的统一门店ID |
| `brand_id` | BIGINT | YES |  | 内部品牌ID |
| `complaint_status` | VARCHAR(32) | NO |  | 标准投诉状态 |
| `complaint_type` | VARCHAR(64) | YES |  | 投诉类型 |
| `complaint_reason_code` | VARCHAR(64) | YES |  | 投诉原因编码 |
| `complaint_reason` | VARCHAR(1024) | YES |  | 投诉原因说明 |
| `accepted_time` | DATETIME(3) | YES |  | 商家接单时间 |
| `resolved_time` | DATETIME(3) | YES |  | 问题解决时间 |
| `closed_time` | DATETIME(3) | YES |  | 关闭时间 |
| `handling_result` | VARCHAR(1024) | YES |  | 投诉处理结果 |
| `meta_transform_version` | VARCHAR(64) | NO |  | 转换逻辑版本 |
| `meta_process_time` | DATETIME(3) | NO |  | 事实处理时间 |
| `meta_sink_sequence` | BIGINT | NO |  | Doris写入顺序版本 |
| `src_version` | BIGINT | NO |  | 当前事实来源版本 |
| `src_last_event_id` | BIGINT | NO |  | 最后写入的来源事件ID |
| `src_last_storage_id` | BIGINT | NO |  | 最后写入事件的Storage ID |
| `src_last_payload_sha256` | CHAR(64) | NO |  | 最后写入原文SHA-256 |
| `src_event_received_time` | DATETIME(3) | NO |  | 事件接收时间 |

### `trade_warehouse.dwd_trade_settlement_entry_fact_v1`

- 证据类型：DDL。文件 `docs/sql/init/trade-doris-init.sql`。实际数据库未核对。
- 表注释：统一结算科目当前事实物理表 v1
- 引擎：`ENGINE=OLAP`
- 模型键：`UNIQUE KEY(`bill_date`, `platform_code`, `finance_order_id`, `entry_line_key`)`
- 分区：`PARTITION BY RANGE(`bill_date`) ()`
- 分桶：`DISTRIBUTED BY HASH(`platform_code`, `finance_order_id`) BUCKETS 8`
- Sequence 列：`meta_sink_sequence`
- 代码引用：DorisCanonicalFactWriter 结算分录。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `platform_code` | VARCHAR(16) | NO |  | 平台编码 |
| `finance_order_id` | VARCHAR(128) | NO |  | 财务订单号 |
| `entry_line_key` | VARCHAR(128) | NO |  | 结算科目稳定行键 |
| `internal_shop_id` | BIGINT | YES |  | 跨服务商稳定的统一门店ID |
| `brand_id` | BIGINT | YES |  | 内部品牌ID |
| `entry_type` | VARCHAR(32) | NO |  | 结算科目类型 |
| `direction` | VARCHAR(8) | NO |  | 资金方向 |
| `amount` | BIGINT | NO |  | 结算科目金额，单位分 |
| `currency_code` | CHAR(3) | NO |  | ISO 4217币种编码 |
| `settlement_status` | VARCHAR(32) | YES |  | 结算状态 |
| `settlement_time` | DATETIME(3) | YES |  | 结算时间 |
| `meta_transform_version` | VARCHAR(64) | NO |  | 转换逻辑版本 |
| `meta_process_time` | DATETIME(3) | NO |  | 事实处理时间 |
| `meta_sink_sequence` | BIGINT | NO |  | Doris写入顺序版本 |
| `src_version` | BIGINT | NO |  | 当前事实来源版本 |
| `src_last_event_id` | BIGINT | NO |  | 最后写入的来源事件ID |
| `src_last_storage_id` | BIGINT | NO |  | 最后写入事件的Storage ID |
| `src_last_payload_sha256` | CHAR(64) | NO |  | 最后写入原文SHA-256 |
| `src_event_received_time` | DATETIME(3) | NO |  | 事件接收时间 |

### 视图 `trade_warehouse.dwd_trade_order_fact_current`

- 证据类型：DDL。`docs/sql/init/trade-doris-init.sql`
- 定义摘要：`CREATE OR REPLACE VIEW `dwd_trade_order_fact_current` AS SELECT `order_created_date`, `platform_code`, `platform_order_id`, `platform_parent_order_id`, `internal_order_id`, `platform_shop_id`, `internal_shop_id`, `brand_id`, `business_scene`, `order_type`, `channel_category`, `channel_code`, `src_channel_code`, `delivery_mode`, `currency_code`, `order_status`, COALESCE(`payment_status`, 'NONE') AS`

### 视图 `trade_warehouse.dwd_trade_order_item_fact_current`

- 证据类型：DDL。`docs/sql/init/trade-doris-init.sql`
- 定义摘要：`CREATE OR REPLACE VIEW `dwd_trade_order_item_fact_current` AS SELECT `order_created_date`, `platform_code`, `platform_order_id`, `item_line_key`, `platform_shop_id`, `internal_shop_id`, `brand_id`, `platform_item_id`, `platform_sku_id`, `platform_category_id`, `platform_category_name`, `internal_product_id`, `internal_category_id`, `parent_item_line_key`, `item_type`, `item_name`, `sku_name`, `spe`

### 视图 `trade_warehouse.dwd_trade_order_discount_fact_current`

- 证据类型：DDL。`docs/sql/init/trade-doris-init.sql`
- 定义摘要：`CREATE OR REPLACE VIEW `dwd_trade_order_discount_fact_current` AS SELECT `order_created_date`, `platform_code`, `platform_order_id`, `discount_line_key`, `platform_shop_id`, `internal_shop_id`, `brand_id`, `platform_activity_id`, `activity_name`, `discount_type`, `benefit_type`, `discount_scope`, `item_line_key`, `benefit_amount`, `merchant_bear_amount`, `platform_bear_amount`, `other_bear_amount``

### 视图 `trade_warehouse.dwd_trade_group_buy_verification_fact_current`

- 证据类型：DDL。`docs/sql/init/trade-doris-init.sql`
- 定义摘要：`CREATE OR REPLACE VIEW `dwd_trade_group_buy_verification_fact_current` AS SELECT `brand_id`, `coupon_info_id`, `check_month`, `channel_desc`, `channel_type`, `check_tm`, `check_pay_num`, `check_pay_times`, `buy_price_amt`, `coupon_amt`, `coupon_type`, `face_amt`, `face_value`, `group_buy_code`, `group_buy_id`, `group_buy_name`, `group_buy_type`, `group_buy_type_desc`, `mchnt_income`, `platform_ord`

### 视图 `trade_warehouse.dwd_trade_payment_fact_current`

- 证据类型：DDL。`docs/sql/init/trade-doris-init.sql`
- 定义摘要：`CREATE OR REPLACE VIEW `dwd_trade_payment_fact_current` AS SELECT `payment_date`, `platform_code`, `payment_id`, `payment_line_key`, `platform_order_id`, `platform_shop_id`, `internal_shop_id`, `brand_id`, `business_scene`, `payment_status`, `payment_method`, `src_payment_method`, `src_order_source`, `paid_amount`, `payment_fee_amount`, `voucher_face_amount`, `stored_value_principal_amount`, `stor`

### 视图 `trade_warehouse.dwd_trade_recharge_fact_current`

- 证据类型：DDL。`docs/sql/init/trade-doris-init.sql`
- 定义摘要：`CREATE OR REPLACE VIEW `dwd_trade_recharge_fact_current` AS SELECT `recharge_date`, `platform_code`, `recharge_id`, `original_recharge_id`, `internal_shop_id`, `brand_id`, `platform_shop_id`, `platform_shop_name`, `src_shop_id`, `recharge_status`, `recharge_type`, `recharge_channel`, `direction`, `currency_code`, `payment_method`, `src_payment_method`, `recharge_amount`, `gift_amount`, `payment_fe`

### 视图 `trade_warehouse.dwd_trade_refund_fact_current`

- 证据类型：DDL。`docs/sql/init/trade-doris-init.sql`
- 定义摘要：`CREATE OR REPLACE VIEW `dwd_trade_refund_fact_current` AS SELECT `refund_date`, `platform_code`, `refund_id`, `platform_order_id`, `original_payment_id`, `internal_shop_id`, `brand_id`, COALESCE(`refund_type`, 'NONE') AS `refund_type`, COALESCE(`refund_status`, 'NONE') AS `refund_status`, `apply_time`, `success_time`, `closed_time`, `refund_amount`, `merchant_bear_amount`, `platform_bear_amount`, `

### 视图 `trade_warehouse.dwd_trade_refund_item_fact_current`

- 证据类型：DDL。`docs/sql/init/trade-doris-init.sql`
- 定义摘要：`CREATE OR REPLACE VIEW `dwd_trade_refund_item_fact_current` AS SELECT `refund_date`, `platform_code`, `refund_id`, `refund_item_line_key`, `platform_order_id`, `internal_shop_id`, `brand_id`, `order_item_line_key`, `platform_item_id`, `platform_sku_id`, `item_name`, `refund_quantity`, `refund_amount`, `merchant_bear_amount`, `platform_bear_amount`, `src_updated_time`, `src_version`, `src_last_even`

### 视图 `trade_warehouse.dwd_trade_complaint_fact_current`

- 证据类型：DDL。`docs/sql/init/trade-doris-init.sql`
- 定义摘要：`CREATE OR REPLACE VIEW `dwd_trade_complaint_fact_current` AS SELECT `complaint_created_date`, `platform_code`, `complaint_id`, `platform_order_id`, `internal_shop_id`, `brand_id`, `complaint_status`, `complaint_type`, `complaint_reason_code`, `complaint_reason`, `accepted_time`, `resolved_time`, `closed_time`, `handling_result`, `src_updated_time`, `src_version`, `src_last_event_id`, `src_last_sto`

### 视图 `trade_warehouse.dwd_trade_settlement_entry_fact_current`

- 证据类型：DDL。`docs/sql/init/trade-doris-init.sql`
- 定义摘要：`CREATE OR REPLACE VIEW `dwd_trade_settlement_entry_fact_current` AS SELECT `bill_date`, `platform_code`, `finance_order_id`, `entry_line_key`, `platform_order_id`, `internal_shop_id`, `brand_id`, `entry_type`, `direction`, `amount`, `currency_code`, `settlement_status`, `settlement_time`, `src_updated_time`, `src_version`, `src_last_event_id`, `src_last_storage_id`, `src_last_payload_sha256`, `met`

### `trade_warehouse.dim_unified_shop_wide`

- 证据类型：DDL。文件 `docs/sql/init/trade-doris-init.sql`。实际数据库未核对。
- 引擎：`ENGINE=OLAP`
- 模型键：`UNIQUE KEY(`brand_id`, `member_id`)`
- 分桶：`DISTRIBUTED BY HASH(`brand_id`, `member_id`) BUCKETS 4`
- 代码引用：门店宽表。本次未发现 @TableName。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `brand_id` | BIGINT | NO |  | 交易品牌ID，归组租户边界 |
| `member_id` | VARCHAR(64) | NO |  | 成员规则行ID，完整SHA256稳定键 |
| `root_id` | VARCHAR(64) | NO |  | 根规则行ID |
| `root_group_key` | VARCHAR(512) | NO |  | 公司根身份，FZY店号 |
| `root_shop_name` | VARCHAR(256) | YES |  |  |
| `root_internal_shop_id` | BIGINT | YES |  | 仅唯一ACTIVE映射填充 |
| `root_prov_cd` | VARCHAR(16) | YES |  |  |
| `root_prov_name` | VARCHAR(50) | YES |  |  |
| `root_region_cd` | VARCHAR(16) | YES |  |  |
| `root_region_name` | VARCHAR(50) | YES |  |  |
| `root_county_cd` | VARCHAR(16) | YES |  |  |
| `root_county_name` | VARCHAR(50) | YES |  |  |
| `member_platform_raw` | VARCHAR(128) | YES |  |  |
| `member_platform_code` | VARCHAR(16) | YES |  |  |
| `member_platform_shop_id` | VARCHAR(128) | YES |  |  |
| `member_internal_shop_id` | BIGINT | YES |  | 仅唯一ACTIVE映射填充 |
| `member_shop_name` | VARCHAR(256) | YES |  |  |
| `member_ordinal` | INT | YES |  |  |
| `build_time` | DATETIME | YES |  | 最近一次重建写入时间 |

### `trade_warehouse.dwd_member_info_fact_v1`

- 证据类型：DDL。文件 `docs/sql/init/trade-doris-init.sql`。实际数据库未核对。
- 表注释：会员当前事实
- 引擎：`ENGINE=OLAP`
- 模型键：`UNIQUE KEY(`id`)`
- 分桶：`DISTRIBUTED BY HASH(`id`) BUCKETS 1`
- 代码引用：MemberInfoCdcJob 目标。源表是外部 member_info。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `id` | BIGINT | NO |  | 来源会员主键ID |
| `tenant_id` | VARCHAR(20) | YES |  | 来源租户编号 |
| `open_id` | VARCHAR(64) | YES |  | 微信openId |
| `user_name` | VARCHAR(64) | YES |  | 昵称 |
| `experience` | INT | YES |  | 经验值 |
| `level_value` | INT | YES |  | 等级值 |
| `sex` | TINYINT | YES |  | 性别：1男，2女，0未知 |
| `birthday` | VARCHAR(32) | YES |  | 生日原文 |
| `country` | VARCHAR(64) | YES |  | 国家 |
| `province` | VARCHAR(64) | YES |  | 省份 |
| `city` | VARCHAR(64) | YES |  | 城市 |
| `sho_name` | VARCHAR(128) | YES |  | 会员发展门店 |
| `regist_time` | VARCHAR(32) | YES |  | 注册时间原文 |
| `total_consume_amt` | BIGINT | YES |  | 消费总金额，单位分 |
| `total_consume_sum` | INT | YES |  | 消费总笔数 |
| `shop_id` | VARCHAR(32) | YES |  | 来源门店ID |
| `phone` | VARCHAR(20) | YES |  | 手机号 |
| `promter_no` | VARCHAR(32) | YES |  | 推广员编号 |
| `member_code` | VARCHAR(32) | YES |  | 会员动态码 |
| `offline_card_no` | VARCHAR(64) | YES |  | 实体卡号 |
| `level_name` | VARCHAR(64) | YES |  | 等级名称 |
| `level_exp_date` | VARCHAR(32) | YES |  | 等级有效期原文 |
| `deleted` | TINYINT | YES |  | 逻辑删除：0未删除，1已删除 |
| `create_time` | DATETIME(3) | YES |  | 创建时间 |
| `update_time` | DATETIME(3) | YES |  | 更新时间 |
| `creator` | VARCHAR(64) | YES |  | 创建人 |
| `modifier` | VARCHAR(64) | YES |  | 更新人 |
| `growth_experience` | BIGINT | YES |  | 成长值 |
| `growth_grade_code` | VARCHAR(16) | YES |  | 成长等级编码 |

### 视图 `trade_warehouse.dwd_member_info_fact_current`

- 证据类型：DDL。`docs/sql/init/trade-doris-init.sql`
- 定义摘要：`CREATE OR REPLACE VIEW `dwd_member_info_fact_current` AS SELECT `id`, `tenant_id`, `open_id`, `user_name`, `experience`, `level_value`, `sex`, `birthday`, `country`, `province`, `city`, `sho_name`, `regist_time`, `total_consume_amt`, `total_consume_sum`, `shop_id`, `phone`, `promter_no`, `member_code`, `offline_card_no`, `level_name`, `level_exp_date`, `deleted`, `create_time`, `update_time`, `cre`

### `trade_warehouse.dwd_store_member_info_fact`

- 证据类型：DDL。文件 `docs/sql/init/trade-doris-init.sql`。实际数据库未核对。
- 表注释：富智云门店会员原始资料事实
- 引擎：`ENGINE=OLAP`
- 模型键：`UNIQUE KEY(`brand_id`, `mchnt_cd`, `open_id`)`
- 分桶：`DISTRIBUTED BY HASH(`brand_id`, `mchnt_cd`, `open_id`) BUCKETS 8`
- 代码引用：StoreMemberInfoDO。MemberInfoCdcJob 的门店会员源表。

| 字段 | 类型 | 允许 NULL | 默认值 | 注释 |
| --- | --- | --- | --- | --- |
| `brand_id` | BIGINT | NO |  | 内部品牌ID |
| `mchnt_cd` | VARCHAR(32) | NO |  | 来源商户号 |
| `open_id` | VARCHAR(128) | NO |  | 来源会员OpenID |
| `tenant_id` | VARCHAR(32) | YES |  | 租户中心租户ID |
| `sex` | STRING | YES |  | 性别原值 |
| `level_exp` | BIGINT | YES |  | 来源levelExp原值 |
| `level_name` | STRING | YES |  | 等级名称 |
| `union_phone` | STRING | YES |  | 来源unionPhone原值 |
| `user_birth` | STRING | YES |  | 生日原文 |
| `user_name` | STRING | YES |  | 用户名称 |
| `point` | STRING | YES |  | 可用积分原文 |
| `offline_card_no` | STRING | YES |  | 线下实体卡号 |
| `table_name` | STRING | YES |  | 来源tableName原值 |
| `exp_days` | BIGINT | YES |  | 来源expDays原值 |
| `real_name` | STRING | YES |  | 用户实名原值 |
| `subscribe_date` | DATE | YES |  | 注册日期 |
| `phone` | STRING | YES |  | 来源手机号原值 |
| `all_phone` | STRING | YES |  | 来源allPhone原值 |
| `platform_shop_id` | VARCHAR(128) | YES |  | 来源门店号 |
| `level_value` | BIGINT | YES |  | 会员等级值 |
| `update_type` | STRING | YES |  | 来源updateType原值 |
| `meta_sync_time` | DATETIME(3) | NO |  | 本行查询同步时间 |

## 辅助脚本中的 current 视图

文件 `docs/sql/init/trade-doris-fact-v1-init.sql`。`DorisCanonicalFactWriter` 会查询带 `_current` 后缀的名字。这些视图是否已经在目标库执行，待确认。

- `dwd_trade_order_fact_current`
- `dwd_trade_order_item_fact_current`
- `dwd_trade_order_discount_fact_current`
- `dwd_trade_group_buy_verification_fact_current`
- `dwd_trade_payment_fact_current`
- `dwd_trade_recharge_fact_current`
- `dwd_trade_refund_fact_current`
- `dwd_trade_refund_item_fact_current`
- `dwd_trade_complaint_fact_current`
- `dwd_trade_settlement_entry_fact_current`
