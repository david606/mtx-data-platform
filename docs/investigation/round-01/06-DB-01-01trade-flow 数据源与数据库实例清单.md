# 06-DB-01-01 trade-flow 数据源与数据库实例清单

本轮用初始化脚本、Spring 配置和只读连接尝试核对数据源。连接失败，所以实例上的库、表和数据量都还没有被实际数据库证明。地址只写到端口和库名，不写账号口令。

## 1. 连接尝试

| 目标 | 结果 | 证据 |
| --- | --- | --- |
| 本机端口 3306 | 连接被拒绝 | 只读客户端返回 `Connection refused`。可信度：已确认这次访问失败 |
| 本机端口 9030 | 连接被拒绝 | 同上 |

没有改用配置文件里的其他主机。那些地址属于开发或生产配置，本次没有对应的只读授权说明。数据量、索引大小、分区数量和最早最晚业务时间因此都是未取得，不是零。

## 2. 配置里的数据源

证据类型都是代码或配置。可信度：配置内容已确认；这些配置是否就是当前运行实例，待确认。

| 名称 | 类型 | 配置位置 | 使用模块 | Schema | 环境差异 |
| --- | --- | --- | --- | --- | --- |
| Ingress 主库 | MySQL，JDBC | `trade-ingress/src/main/resources/nacos/trade-ingress-infra-prod.yaml` 的 `spring.datasource` | Ingress，含 Storage 本地实现 | `trade_flow` | 生产写本机 3306。`nacos-local`、`nacos-test` 指向开发网段主机 |
| Analytics 运行库 | MySQL | `trade-analytics/.../nacos/trade-analytics-infra-prod.yaml` 的 `trade.analytics.datasource` | `AnalyticsDataSourceConfiguration` | `trade_analytics` | 生产写本机 3306。本地和测试配置指向开发网段 |
| Analytics 读取的 Storage 库 | MySQL | 同文件 `trade.storage.datasource` | Analytics 进程里的 `trade-storage-local` | `trade_flow` | 与 Ingress 使用同一个逻辑库名。生产配置也是本机 3306 |
| Analytics Doris | Doris，MySQL 协议 9030 | 同文件 `trade.doris.datasource` | `DorisDataSourceConfiguration`，事实写入和部分查询 | `trade_warehouse` | 生产是内网 9030。本地配置是本机 9030。测试库名是 `trade_warehouse_test` |
| Query 模板库 | MySQL | `trade-query/.../nacos/trade-query-infra-prod.yaml` 的 `spring.datasource` | `QueryTemplateDataSourceConfiguration` | `trade_analytics` | 生产主机与 Analytics 生产配置不是同一个写法。Query 写的是内网 3306，Analytics 写的是本机 3306 |
| Query Doris | Doris | 同文件 `trade.doris.doris-datasource` | Query 只读查询 | `trade_warehouse` | 生产端口 9030。本地是本机 9030 |

密码在生产配置里是环境变量占位，例如 `TRADE_DORIS_PASSWORD`、`TRADE_STORAGE_DATASOURCE_PASSWORD`。测试配置里出现过明文口令。本报告不抄录口令。明文是否只属于测试库，待确认。

Bean 与配置前缀：

- `analyticsDataSource` 绑定 `trade.analytics.datasource`。
- Doris 数据源绑定 `trade.doris.datasource`。
- `queryTemplateDataSource` 绑定 Query 自己的 MySQL 配置。

Ingress 使用 Spring Boot 默认 `spring.datasource`，没有单独的 `@ConfigurationProperties` 前缀类。

## 3. 逻辑库

| Schema | 引擎 | 初始化脚本 | 脚本中的对象 |
| --- | --- | --- | --- |
| `trade_flow` | MySQL | `docs/sql/init/trade-flow-init.sql` | 16 张逻辑表。另有脚本创建 `trade_storage_00` 到 `_99`、`trade_storage_blob_00` 到 `_99` |
| `trade_analytics` | MySQL | `docs/sql/init/trade-analytics-init.sql` | 47 张表 |
| `trade_warehouse` | Doris | `docs/sql/init/trade-doris-init.sql` | 32 张表、11 个视图 |

三个名字在脚本里是三个逻辑库。生产配置把 `trade_flow` 和 `trade_analytics` 都写到本机 3306，所以它们可以在同一台 MySQL 上。这只是配置写法，待连接后用 `SHOW DATABASES` 确认。

`docs/sql/init/trade-doris-fact-v1-init.sql` 再次声明 10 张事实表和 10 个 current 视图。它是辅助脚本，不增加新的逻辑库。

## 4. 未纳入上述三个库的源

`MemberInfoCdcJob` 订阅外部表 `member_info`。常量在 `MemberInfoDeserializer.SOURCE_TABLE`。这张表不在 `trade-flow` 或 `trade-analytics` 的初始化脚本里。它所在的库名由 Flink 作业配置 `mysql.database` 决定。库名和表结构本次未发现于本仓库 DDL。可信度：代码引用已确认，库归属待确认。

Flink 的 MySQL 和 Doris 地址来自作业自己的 properties 或环境变量，不在三个 Spring 服务的 `nacos/` 文件里。`TradeCdcJob` 的源表是 `trade_order_event` 和 `trade_payment_event`。

## 5. 环境是否多个

已确认仓库同时保存三套配置目录：

- `src/main/resources/nacos/`：生产基准。
- `nacos-local/`：本地。
- `nacos-test/`：测试，Doris 库名是 `trade_warehouse_test`。

因此不能把某一份 YAML 里的主机当成唯一实例。本次实际只尝试了本机端口，并且失败。

## 6. 待确认

1. 本机 3306 和 9030 上的服务是否本应启动，还是配置只是占位。
2. Query 生产 MySQL 与 Analytics 生产 MySQL 是否同一实例。配置写法不同。
3. 测试 YAML 中的明文口令是否仍有效。
4. `member_info` 所在实例和库名。
5. 线上是否已经执行主 Doris 脚本、辅助事实脚本，或两者都执行过。
