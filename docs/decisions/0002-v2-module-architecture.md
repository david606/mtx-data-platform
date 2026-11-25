# 0002. V2 十模块结构

- 状态：已接受
- 日期：2026-11-22
- 范围：V2 的 Maven 模块边界。不包含 Pipeline 节点、表结构或调度实现。

## 决策背景

十模块决策作出时，工程采用旧的 `rdp-common`、`rdp-server` 结构，无法满足 V2 数据平台的模块职责和扩展要求。旧模块的后续处置见「旧模块清理」，不改变本节所记录的决策背景。

## 决策内容

V2 采用唯一的十模块 Maven 结构：

1. `platform-common`
2. `platform-core`
3. `platform-messaging`
4. `platform-connector`
5. `platform-transform`
6. `platform-quality`
7. `platform-writer`
8. `platform-pipeline`
9. `app-admin`
10. `app-worker`

根 POM 只聚合这十个模块。`rdp-common` 与 `rdp-server` 不属于这十个模块，也不进入聚合。

## 决策约束

- ADR-0001 保留为历史决策。
- ADR-0002 作为 V2 目标架构依据。
- 与 V2 十模块结构冲突的旧模块结构不再作为目标。
- 不增加第 11 个模块。不引入 `platform-persistence`、`platform-scheduler`、`platform-plugin`、`platform-gateway`、`platform-monitor`。
- Core 通过抽象定义能力边界，不依赖 Spring、Kafka、JDBC、MyBatis、Quartz 或 Redis 的具体实现。
- Worker 负责执行能力装配。
- Admin 与 Worker 独立部署，互不依赖。
- Pipeline 与具体调度实现解耦。Pipeline 不依赖 Connector、Transform、Quality、Writer 的实现，也不依赖 Quartz 或 DolphinScheduler。

允许的内部依赖：

| 模块 | 允许依赖 |
| --- | --- |
| platform-common | 无 |
| platform-core | platform-common |
| platform-messaging | platform-common、platform-core |
| platform-connector | platform-common、platform-core |
| platform-transform | platform-common、platform-core |
| platform-quality | platform-common、platform-core |
| platform-writer | platform-common、platform-core |
| platform-pipeline | platform-common、platform-core |
| app-admin | platform-common、platform-core、platform-messaging |
| app-worker | platform-common、platform-core、platform-messaging、platform-connector、platform-transform、platform-quality、platform-writer、platform-pipeline |

## 技术基线

已确认并沿用 ADR-0001 的版本：

| 技术 | 版本或定位 | 本决策中的状态 |
| --- | --- | --- |
| JDK | 25 | 已确认 |
| Spring Boot | 3.5.16 | 已确认。工程不继承 `spring-boot-starter-parent`，只导入其 BOM |
| Spring Framework | 6.2.19 | 由 Spring Boot 3.5.16 BOM 解析 |
| JUnit Jupiter | 5.12.2 | 由 Spring Boot BOM 管理，不另导入 JUnit BOM |
| Kafka Client | 3.9.2 | 由 Spring Boot BOM 管理。骨架不把客户端写入模块依赖 |
| Spring Kafka | 3.3.16 | 由 Spring Boot BOM 管理。骨架不覆盖该版本，也不提前引入 |
| Quartz | 2.5.2 | 初期调度方向已确认。版本由 Spring Boot BOM 管理。骨架不引入 starter，也不单独建调度模块 |
| MyBatis-Plus | 3.5.18.1 | 选型方向保留。可以在依赖管理中登记坐标，骨架不引入 starter |
| MySQL | 8.0.46 | 服务器版本由 ADR-0005 接受。尚未接入：骨架不引入驱动，不创建库表 |
| Doris | 4.0.7 | 服务器版本由 ADR-0005 接受。客户端和驱动未锁定，骨架不引入。不承担事务型元数据 |

尚未决策或明确不在本阶段接入：

| 技术 | 状态 |
| --- | --- |
| Redis | 未决策。不得默认引入 |
| Flyway | 未决策。不得默认引入 |
| DolphinScheduler | 未来演进。当前不引入依赖 |

## 旧模块清理

`rdp-common` 与 `rdp-server` 已完成依赖与代码价值审查。结论如下：

- 两个旧模块不包含需要迁移到 V2 的业务实现。没有业务 API、业务服务、数据访问、消息消费、任务调度或外部集成。
- `Platform` 只有 `NAME`、`SHORT_NAME`、`GROUP_ID` 三个项目标识常量，没有业务价值。不复制到 `platform-common`。
- `RdpServerApplication` 只有基础 Spring Boot 启动，并返回平台名称。当前应用入口是 `app-admin` 的 `com.mtx.rdp.admin.AdminApplication` 与 `app-worker` 的 `com.mtx.rdp.worker.WorkerApplication`。
- V2 十模块的 Java 源码没有引用旧模块类型。
- 当前 V2 反应器不构建旧模块。删除它们不改变十模块依赖矩阵。
- 两个旧模块不属于 V2 十模块架构，不进入 Maven 聚合。
- 经本次清理，批准从当前工程工作区删除。Git 历史仍可追溯旧代码。本次决策不伪造审批人或提交记录。
- 不将旧模块代码迁移到任何 V2 模块，也不把旧模块改名后重新加入聚合。
- 根 POM 删除 `rdp-common` 的 `dependencyManagement`。不再支持单独构建旧 `rdp-server`。

## 后果

V2 代码只进入上述十个模块。十模块结构、模块职责、依赖矩阵、技术基线与两个应用入口不因删除旧模块而改变。调度 API 仍不得进入 Pipeline、Connector、Transform、Writer。同一任务以后也不得由 Quartz 和 DolphinScheduler 重复触发。这些职责边界继续以 `docs/architecture/scheduling.md` 为准。
