# 06-DB-01-05 trade-flow 数据库调查问题清单

未发现不等于线上不存在。本轮没有连上数据库，所有“线上有”或“线上没有”的判断都还不能做。

## 1. 无法确认的对象

| 编号 | 事项 | 原因 |
| --- | --- | --- |
| D01 | 本机 3306、9030 上的真实库、表、视图 | 连接被拒绝 |
| D02 | 表行数、数据大小、索引大小、实际分区数 | 需要 `information_schema`，本次没有连上。禁止全表扫描，所以也不能用 `COUNT(*)` 代替 |
| D03 | 最早和最晚业务时间 | 字段含义虽能从脚本看到，但没有必要在连不上库时猜测，也没有做扫描 |
| D04 | 生产、开发、测试三套库是否都已按当前 init 建完 | 仓库里还有 `docs/sql/` 下未移入执行历史的日期脚本。未读执行历史，不能判断哪些已经执行 |
| D05 | `member_info` 的库名、字段和索引 | 只在 Flink 源码中作为外部表名出现 |

## 2. 配置和脚本可能不一致的地方

| 编号 | 观察 | 等级 |
| --- | --- | --- |
| D06 | Analytics 生产 MySQL 写本机 3306，Query 生产 MySQL 写另一台内网 3306，库名都是 `trade_analytics` | 待确认是不是同一实例 |
| D07 | Doris 主脚本和 `trade-doris-fact-v1-init.sql` 都定义同一批事实表和 current 视图 | 待确认目标库以哪份为准。不能看成两个数据库 |
| D08 | 脚本头部写目标 Doris 4.0.7。驱动和服务器版本本次没有从实例读到 | 待确认 |
| D09 | 测试配置含明文口令，生产配置是环境变量 | 待确认测试口令是否仍指向真实测试库 |

## 3. 缺失的 DDL

| 编号 | 对象 | 说明 |
| --- | --- | --- |
| D10 | `member_info` | 代码引用，三份主初始化脚本都没有 |
| D11 | Flink checkpoint 或位点表 | 本次初始化脚本未发现。作业可能把状态放在 Flink 自己的目录，待确认 |
| D12 | 线上相对 init 的增量 | `docs/sql/` 还有日期脚本，例如查询模板分组、Doris 索引、会员表。它们是否已经并入 init，需要逐份和 init 对差异。本次没有把日期脚本当成第二套权威结构 |

## 4. 代码引用但脚本未找到

除 `member_info` 外，本次 `@TableName` 都能在 MySQL init 找到。Doris 写入使用的 `dwd_trade_*_fact` 会在代码里加上 `_v1`，脚本里的物理表名因此是匹配的。

100 张 Storage 分表没有各自的实体类。这是分片映射，不是缺失。

## 5. 脚本中有、实体类未找到

- `trade_ssm_group_sync_checkpoint`
- `trade_analysis_recovery_admission_control`
- `dim_unified_shop_wide`
- 六张 `dwd_*_version`：有 Flink 作业，没有 MyBatis 实体
- current 视图：有查询字符串，没有实体类

这些表仍可能被注解 SQL 或字符串 SQL 使用。下一轮应按表名搜 XML 和字符串，不能因为没有 `@TableName` 就当成死表。

## 6. 需要业务或运维确认的字段

| 字段或对象 | 原因 |
| --- | --- |
| `trade_analysis_order_state.order_key` | 脚本注释说“来源系统与平台订单号”。代码实际是 `平台编码:平台订单号`，例如 `FZY:`，不是 `SourceSystem` 数字。注释和代码用词不一致 |
| `trade_storage.content_type=3` | 注释写历史主数据快照兼容值。当前写入是否还产生 3，待确认 |
| Doris 版本表里的 `order_no`、`user_id`、`mchnt_cd` | 字段来自富智云报文投影。和统一事实表的 `platform_order_id` 不是同一套模型 |
| `dwd_trade_complaint_fact_v1`、`dwd_trade_settlement_entry_fact_v1` | 写入类存在，但是哪类来源事件会真正产生行，仍要对照映射器 |
| 团购事实只有 Doris | 业务上的团购订单是否还应该有 MySQL 来源事件。当前代码是查询后直接装载 |

## 7. 最终总结

1. 配置里确认了 6 个应用数据源：Ingress MySQL、Analytics 运行 MySQL、Analytics 使用的 Storage MySQL、Analytics Doris、Query MySQL、Query Doris。后两组分别和前面的 `trade_analytics`、`trade_warehouse` 同名，但是配置文件和主机写法分开。另外还有 Flink 作业自己的连接，以及一张外部表 `member_info`。本机两个端口都没有连上。
2. 初始化脚本确认 2 个 MySQL Schema：`trade_flow` 16 张逻辑表，`trade_analytics` 47 张表。其中发号表和保留租约表在两个库各有一份。Storage 另外按脚本生成 200 张物理分表。这些数字是脚本对象数，不是线上 `SHOW TABLES` 的结果。
3. Doris 初始化脚本确认 1 个数据库 `trade_warehouse`、32 张表、11 个视图。模型都是 UNIQUE KEY。没有发现 Aggregate 表。测试配置里还有库名 `trade_warehouse_test`，本次没有连到。
4. 已经建立映射的领域是：原文、来源事件、Outbox、转发、处理账本、订单归并、Flush、补拉、回灌、门店商品维表、查询模板、统一事实、CDC 版本表、团购核销和会员。优惠和退款没有独立 MySQL 明细表。
5. 后续订单状态归并继续看 `trade_analysis_order_state`、`trade_analysis_order_replay_context`、`trade_analysis_flush_task_index`，以及来源表 `trade_order_event`、`trade_payment_event`、`trade_thirdparty_event`、`trade_source_query_event`。Doris 侧看 `dwd_trade_order_fact_v1` 的状态列，不要把 `dwd_order_version` 当成同一套状态机。
6. 后续金额计算看 Doris 的订单、明细、优惠、支付、充值、退款、退款明细和团购事实表，以及 `trade_analysis_order_state.accumulator_json` 里的归并金额。MySQL 来源表只保存键和报文指针，不保存拆开的金额列。
7. 可以进入 06-DB-02 的脚本级字段语义分析。字段清单已经从 DDL 抽出。还不能把结论写成生产结构，也不能写数据量。06-DB-02 应先读事实表字段和映射器，等只读库可连接后，再用 `information_schema` 核对差异。
