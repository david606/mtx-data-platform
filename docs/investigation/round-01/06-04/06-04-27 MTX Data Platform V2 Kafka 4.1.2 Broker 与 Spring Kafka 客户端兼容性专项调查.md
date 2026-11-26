# 06-04-27 MTX Data Platform V2 Kafka 4.1.2 Broker 与 Spring Kafka 客户端兼容性专项调查

本文是专项调查，不是已接受 ADR。它不表示 Kafka 客户端已经进入运行时类路径，不表示已经连接 Broker，不表示消费语义、rebalance 或 offset 提交已经验证。

访问日期：2026-11-26。没有连接、重启或修改任何 Kafka 环境，没有创建 Topic，没有提交测试消息，没有改 Consumer Group offset，没有执行 Maven 构建或测试。没有修改 Java、POM、模块、DDL 或 ADR。

编号 06-04-27 此前未被占用。既有 06-04-01 至 06-04-26 放在上一级 [round-01](../) 目录，仓库里原来没有 `06-04/` 子目录。本文件按本次任务指定的路径存放。相对链接指向同系列文档时使用上一级目录。`docs/README.md` 不收录 `docs/investigation/`，该目录也没有索引。本轮不新建索引，也不把未接受的调查写进文档总索引。

## 1. 调查背景与范围

调查对象是任务给定的组合：Broker 发行版 `kafka_2.13-4.1.2`，Spring Boot 3.5.16，Spring for Apache Kafka 3.3.16，Kafka Java Client 3.9.2，应用侧 JDK 25。Broker 版本是本次调查输入。仓库配置不能证明这台 Broker 已经部署。

分开四层，不用一层代替另一层：

| 层次 | 本轮回答什么 |
| --- | --- |
| Client/Broker 协议 | 官方兼容页是否把 3.x Client 标为可与 Kafka 4.0 通信 |
| Java API | 4.1 发行说明里的 API 删改是否落在 3.9.2 客户端上 |
| Spring Kafka 框架 | 3.3.16、兼容矩阵和 Boot BOM 是否对得上 |
| 业务行为 | IR-03 的 offset 与分区规则是否已被该组合运行验证 |

Broker 的 JDK 和应用的 JDK 是两个运行环境。应用使用 JDK 25，不能推出 Broker 也必须使用 JDK 25。

## 2. 当前版本事实

| 组件 | 本任务给定或 ADR 已写的版本 | 仓库能否证明它正在运行 |
| --- | --- | --- |
| Kafka Broker | `kafka_2.13-4.1.2`，仅为本任务调查对象 | 不能。标记 `NOT_VERIFIED_IN_REPOSITORY` |
| Spring Boot | 3.5.16 | 根 POM 导入该 BOM。两个应用使用 `spring-boot-starter` |
| Spring Kafka | 3.3.16 | 只出现在官方 3.5 依赖坐标表和 ADR 转述中。模块没有声明 |
| Kafka Client | 3.9.2 | 同上。没有模块把它放上类路径 |
| JDK | 25，应用基线 | 根 POM `java.version` 与 `maven.compiler.release` 为 25。这不是 Broker 的 JDK |

[ADR-0001](../../../decisions/0001-technology-baseline.md) 锁定的是客户端 3.9.2 和 Spring Kafka 3.3.16，依据写的是 Spring Boot 3.5.16 BOM 的 `kafka.version` 与 `spring-kafka.version`。它没有 Broker 版本，也写明初始化模块尚未引入客户端。[ADR-0002](../../../decisions/0002-v2-module-architecture.md) 同样写客户端由 BOM 管理、骨架不把客户端写入模块依赖。

## 3. 仓库依赖管理核验

根 `pom.xml` 以 `scope=import` 导入 `spring-boot-dependencies` 3.5.16。根 POM 和全部子模块 POM 都没有 `kafka.version`、`spring-kafka.version`，也没有 `kafka-clients`、`spring-kafka`、`spring-kafka-test` 的显式版本覆盖。

| 类别 | 仓库事实 |
| --- | --- |
| BOM 管理 | 官方 Spring Boot 3.5 Managed Dependency Coordinates 列出 `org.apache.kafka:kafka-clients` 3.9.2、`org.springframework.kafka:spring-kafka` 3.3.16、`org.springframework.kafka:spring-kafka-test` 3.3.16。根 POM 导入的就是这条 3.5 BOM，且没有覆盖这些属性 |
| 实际声明的依赖 | 无。`platform-messaging` 的说明写明骨架不加入 Kafka 客户端。它只依赖 `platform-common` 和 `platform-core` |
| 运行时 classpath | 无 Kafka 客户端。`app-admin` 与 `app-worker` 的生产依赖是 `spring-boot-starter` 和内部模块，不是 `spring-boot-starter-kafka` |
| 仅被 dependencyManagement 管理、尚未引用 | `kafka-clients`、`spring-kafka`、`spring-kafka-test`，以及坐标表里的其他 `org.apache.kafka` 构件。它们没有被模块引用，所以还不在类路径上 |
| 测试依赖 | 两个应用使用 `spring-boot-starter-test`。没有 `spring-kafka-test`，没有 Testcontainers Kafka。测试依赖里没有 Kafka 客户端 |

`app-admin` 与 `app-worker` 的 `application.yml` 只有应用名。`docs/deployment/` 写明初始化阶段没有 Dockerfile 或编排文件。仓库没有 `zookeeper.connect`、`process.roles`、`controller.quorum.voters` 或 `enable.auto.commit` 配置。

因此：BOM 管理版本不是已经部署的客户端版本。当前工程没有正在运行的 Kafka Java Client 3.9.2。

## 4. Broker/Client 协议兼容性

来源是 Apache Kafka 4.1 文档 [Compatibility](https://kafka.apache.org/41/getting-started/compatibility/)，标题为 Compatibility。页面开篇写的是 Kafka 4.0 的兼容变化。Client/Broker Forward Compatibility 表的列名是 “Compatibility with Kafka 4.0”。

该表对 3.x Client 的格子是 Fully Compatible，Key Differences/Limitations 为空。0.x、1.x、2.0 为不兼容，原因是 KIP-896 移除了 0.10.x 之前的协议。2.1 至 2.8 为部分兼容，并指向 Consumer、Producer、Admin Client 各节。本次抓取的正文在 3.x 这一行和 AdminClient 命令说明之后结束，没有单独的 3.9.2 或 4.1.2 行，也没有把 Producer、Consumer、AdminClient 再拆成 3.9.x 的限制表。

同页还写：从 Kafka 4.0 起，AdminClient 命令去掉 `--zookeeper`，必须使用 `--bootstrap-server`。这是命令行用法，不是 3.9.2 Java 客户端的协议限制。

[Upgrading](https://kafka.apache.org/41/getting-started/upgrade/) 里和本组合有关的发行说明：

| 版本 | 官方 notable changes 中与客户端协议相关的句子 | 对本组合的含义 |
| --- | --- | --- |
| 4.1.2 | 修复罕见的 producer 缺陷 KAFKA-19012，一条记录可能进入错误的 topic | 这是 4.1.2 发行物中的修复。页面没有写 3.9.2 客户端是否包含同一缺陷 |
| 4.1.1 | Kafka Streams 内存泄漏和潜在数据丢失 | Streams。V2 基线没有使用 Streams |
| 4.1.0 | Queues for Kafka 预览；producer `flush` 不得在 callback 内调用；`Admin.listConsumerGroups` 标记为将在下一主版本删除 | 这些是 4.1 服务器或 4.1 客户端 API 的变化。3.9.2 客户端不是 4.1 客户端 |
| 4.0.0 | 旧协议 API 版本已删除。升级到 4.0 之前，Java 客户端应不低于 2.1。ZooKeeper 模式已删除 | 3.9.2 高于 2.1。这支持 4.0 页上 “3.x Fully Compatible” 的前提，不单独证明 4.1.2 已联通 |

4.0.0 还写：使用 4.0 producer 客户端时，每个事务都会提升 producer epoch（KIP-890）。3.9.2 不是 4.0 producer。不能把这句写成 3.9.2 已经具备该行为。新的消费者重平衡协议在升级到 4.0 并完成 finalize 后于服务器侧自动启用；一旦消费组使用新协议，集群只能降级到 3.4.1 或更新。这是集群元数据与协议启用后的降级限制，不是对本仓库消费者的实测。

协议层结论：官方 4.1 文档沿用的 4.0 兼容表把 3.x Client 标为 Fully Compatible，且没有列出 3.x 的额外限制。4.1.0 至 4.1.2 的 notable changes 没有写一条 “3.9.x 客户端不能连接 4.1 Broker”。状态是 `CONDITIONALLY_CONFIRMED`：前提是沿用该页对 3.x 与 Kafka 4.0 的协议表，并且 4.1.2 的升级说明没有宣布协议中断。它不是对 4.1.2 与 3.9.2 的连接实验，也不是 Java API 或 Spring Kafka 的结论。

## 5. Spring Kafka 兼容性

三项证据同时成立，范围并不相同。本调查不把它们收成 “完全一致”，也不判定为不兼容。

| 证据 | 原文范围 | 状态 |
| --- | --- | --- |
| [Spring for Apache Kafka 项目页](https://spring.io/projects/spring-kafka/) 的 Kafka Client and Spring Boot Compatibility 表 | 页面标题是 Spring for Apache Kafka 4.2.0。3.3.x 一行写 `kafka-clients` 为 3.8.0 to 3.9.0，Spring Boot 为 3.4.x，Spring Integration for Apache Kafka 为 6.4.x。表下写明这是 client compatibility，Broker 兼容另见 Kafka Compatibility Matrix | 3.9.2 不在写明的 “3.8.0 to 3.9.0” 上限之内。Boot 3.5 也不在该行的 3.4.x 之内 |
| [Spring Boot 3.5 Managed Dependency Coordinates](https://docs.spring.io/spring-boot/3.5/appendix/dependency-versions/coordinates.html) | `kafka-clients` 3.9.2，`spring-kafka` 3.3.16，`spring-kafka-test` 3.3.16。同表还有 `spring-integration-kafka` 6.5.10 | `CONFIRMED`：3.5 线 BOM 管理这两个版本。仓库导入 3.5.16 且没有覆盖 |
| [2026-06-09 发布说明](https://spring.io/blog/2026/06/09/spring-kafka-4/) | 标题包含 4.1.0、4.0.6 和 3.3.16。文末写 Spring Kafka 3.3.16 will be integrated into Spring Boot 3.5.15 | 正式集成对象写的是 3.5.15。3.5 坐标表仍然列出 3.3.16，所以 3.5.16 的管理坐标与这句不冲突，但发布说明没有点名 3.5.16 |

[Override Spring Boot Dependencies](https://docs.spring.io/spring-kafka/reference/appendix/override-boot-dependencies.html) 当前参考文档写：Both Spring Boot 3.5.x and 3.4.x use the `kafka-clients` version 3.8.x，若要使用 3.9.x 需要手工设置 `kafka.version`。这句和上面的 3.5 坐标表不一致。坐标表是 3.5 线实际列出的管理版本；覆盖说明是当前 Spring Kafka 参考文档中的一句。本调查保留两句，不选其中一句改写 BOM。仓库没有设置 `kafka.version`。

3.3.16 在该发布说明中的 backport 是：`DefaultConsumerFactory` 非字符串属性、`StreamsBuilderFactoryBean` 空指针，以及 value serializer 的 classloader 处理，外加 Jackson 维护更新。同一篇文章里，批量监听器静默提交 offset、回滚后 seek 的修复写在 4.1.0 一节，没有列入 3.3.16 的 backport 清单。不能由此证明 3.3.16 含有该缺陷，也不能证明它已经包含该修复。本轮没有核对 Spring Kafka 源码。

Spring Kafka 3.3.16 与 Client 3.9.2 的官方依据是：Boot 3.5 坐标表把二者放在同一 BOM 中，发布说明把 3.3.16 集成进 Boot 3.5.15。通用兼容矩阵的客户端上限写到 3.9.0、Boot 列写 3.4.x。状态是 `CONDITIONALLY_CONFIRMED`。条件是接受 BOM 的管理坐标，同时承认矩阵和覆盖说明的文字范围更窄或互相不一致。

## 6. KRaft 部署要求

[Upgrading](https://kafka.apache.org/41/getting-started/upgrade/) 在 “Upgrading Servers to 4.0.1 from any version 3.3.x through 3.9.x” 写明：Apache Kafka 4.0 only supports KRaft mode，ZooKeeper mode has been removed。升级到 4.0.0 及更高版本的 Broker 必须已经是 KRaft，软件和元数据版本至少 3.3.x。仍在 ZooKeeper 模式的集群必须先迁到 KRaft，才能升到 4.0.x。4.0.0 notable changes 重复了同一句。4.1.2 是 4.0 之后的发行版，升级节没有把 ZooKeeper 模式加回来。

同节写：此次升级包含元数据变化，不支持集群元数据降级。4.0 的新消费者重平衡协议一旦被消费组使用，集群只能降级到 3.4.1 或更新。4.0 默认配置不再放在单独的 `config/kraft` 目录，因为 ZooKeeper 已删除，配置文件都在 `config`。

仓库侧：`docs/deployment/README.md` 没有 Kafka 拓扑。两个 `application.yml` 没有 Broker 地址、`process.roles` 或 ZooKeeper 地址。没有任何文件能证明当前集群是 KRaft 还是 ZooKeeper。

KRaft 是否已被当前部署满足：`NOT_VERIFIED_IN_REPOSITORY`。官方要求 4.0 及之后的 Broker 使用 KRaft，这是发行说明，不是本仓库的部署证据。本轮不连接外部 Kafka。

## 7. JDK 兼容性

| 运行环境 | 官方写了什么 | Java 25 |
| --- | --- | --- |
| Kafka 4.x Broker | 4.1 Compatibility 的 JDK 表只列出 Kafka 4.0.0。Server 列：Java 11 不支持，Java 17 与 Java 23 支持。4.0.0 notable changes 写：clients 与 Streams 的最低 Java 从 8 升到 11；brokers、Connect 和 tools 要求 Java 17；4.0 增加 Java 23 | 表中没有 Java 21，也没有 Java 25。不能宣称 Broker 4.1.2 正式支持 JDK 25，也不能从应用 JDK 推出 Broker 必须使用 JDK 25 |
| Kafka Client 3.9.2 | 3.9 升级文档的 3.9.0 至 3.9.2 notable changes 没有 Java 25。更早的 3.1.0 写 Apache Kafka supports Java 17。3.0 起 Java 8 被标记为将在 4.0 移除。3.7.0 起 Broker 与 tools 的 Java 11 支持被标记为将在 4.0 移除 | 没有读到 Client 3.9.2 对 Java 25 的明确支持行。证据不足，不宣称正式支持 |
| MTX V2 应用 | ADR-0001 将应用 JDK 定为 25，并引用 Spring Boot 自 3.5.5 起声明 Java 25 可用 | 这是应用与 Spring Boot 的基线，不是 Kafka 兼容矩阵 |

应用跑在 JDK 25、Broker 跑在官方表已列出的 Java 17 或 23，在文档上是两个环境。本调查不选择 Broker 的 JDK。Broker 与 Client 对 JDK 25 的正式支持都是 `NOT_VERIFIED`。

## 8. IR-03 消费语义影响

本节只评估版本材料会不会碰到既有规则，不重做 IR-03。规则仍以 [06-04-26](../06-04-26%20MTX%20Data%20Platform%20V2%20IR-03%20稳定接入事件身份与任务创建幂等专项调查.md) 第 7 节为准，并与 [06-04-10](../06-04-10%20MTX%20Data%20Platform%20V2%20Source%20Event%20Identity%20与%20Kafka%20消息接入幂等专项设计.md) 第 7 节的否定结论并存。06-04-10 里 “不提交 offset 会让分区停住” 的表述，仍以 06-04-26 第 7.1 节的区分为准。本轮不改这两份文档。

协议兼容不能推出下列规则已经得到运行时验证：

1. 不提交某一条消息的 offset，不会让 Kafka 自动停止该分区后续消息的处理。
2. 已提交 offset 不得越过尚未完成可靠处理的消息。批量提交和异步提交遵守同一边界。
3. 身份无法验证时，不自动建任务，并停止该分区后续业务处理，或使用能够保证连续提交位置的等效机制。
4. MySQL `COMMIT` 结果不确定时不提交 Kafka offset。MySQL 与 Kafka 不是一个本地事务。
5. rebalance 和进程重启后，未完成消息不能视为成功。恢复按已提交位置重新处理，不依赖进程内存。

仓库没有消费者，因此下列行为都未验证：`enable.auto.commit` 的生效值、手动提交、批量提交、分区暂停与恢复的客户端实现、consumer group generation、重启后的再投递，以及 Kafka 事务。4.0 文档里的 KIP-848 与 KIP-890 是 Broker 4.x 的协议变化。它们不改变上面五条设计约束，也没有在本仓库执行。

3.3.16 发布说明没有把 4.1.0 的批量监听器 offset 修复列入自己的 backport。以后若采用批量监听器，要单独核对这一行为。当前没有监听器实现，不能把该差异写成已经发生的生产缺陷。

KAFKA-19012 说明 4.1.2 修过 “记录进入错误 topic” 的 producer 问题。3.9.2 客户端是否仍会触发该问题，发行说明没有写。这是后续实验要单列的风险，不是本轮的不兼容结论。

## 9. 官方证据索引

| 编号 | 标题 | 地址 | 用于哪一节 | 证据性质 |
| --- | --- | --- | --- | --- |
| E1 | Compatibility | https://kafka.apache.org/41/getting-started/compatibility/ | 第 4、7 节 | 官方页面。表头写的是与 Kafka 4.0 的兼容，JDK 表的版本列是 4.0.0 |
| E2 | Upgrading | https://kafka.apache.org/41/getting-started/upgrade/ | 第 4、6、7、8 节 | 官方升级说明。4.1.2、4.1.1、4.1.0、4.0.0 的 notable changes |
| E3 | Apache Kafka 4.1.2 Release Announcement | https://kafka.apache.org/blog/2026/03/17/apache-kafka-4.1.2-release-announcement/ | 第 4 节 | 官方公告。正文指向升级文档，没有单独的 3.9.2 兼容句 |
| E4 | Spring for Apache Kafka，Kafka Client and Spring Boot Compatibility | https://spring.io/projects/spring-kafka/ | 第 5 节 | 官方项目页。当前页标题为 4.2.0，矩阵含 3.3.x 一行 |
| E5 | Override Spring Boot Dependencies | https://docs.spring.io/spring-kafka/reference/appendix/override-boot-dependencies.html | 第 5 节 | 当前 Spring Kafka 参考文档。其中 Boot 3.5.x 使用 3.8.x 的句子与 E6 不一致 |
| E6 | Managed Dependency Coordinates | https://docs.spring.io/spring-boot/3.5/appendix/dependency-versions/coordinates.html | 第 3、5 节 | Spring Boot 3.5 线官方坐标表 |
| E7 | Spring for Apache Kafka 4.1.0, 4.0.6, and 3.3.16 Available | https://spring.io/blog/2026/06/09/spring-kafka-4/ | 第 5、8 节 | 官方发布说明。集成句写的是 Boot 3.5.15 |
| E8 | Upgrading，Kafka 3.9 文档 | https://kafka.apache.org/39/getting-started/upgrade/ | 第 7 节 | 3.9.0 至 3.9.2 的 notable changes，以及更早的 Java 17 / Java 8 句子 |

仓库证据：根 `pom.xml`、`platform-messaging/pom.xml`、`app-admin/pom.xml`、`app-worker/pom.xml`、两个 `application.yml`、`docs/deployment/README.md`、ADR-0001、ADR-0002。

## 10. 风险和未验证事项

| 事项 | 状态 |
| --- | --- |
| 用 3.9.2 客户端实际连接 `kafka_2.13-4.1.2` | `NOT_VERIFIED` |
| 集群是 KRaft 还是仍留有 ZooKeeper 配置 | `NOT_VERIFIED_IN_REPOSITORY` |
| 元数据版本、能否降级、消费组是否已启用 KIP-848 | `NOT_VERIFIED` |
| JDK 25 上运行 Client 3.9.2 | `NOT_VERIFIED`。官方矩阵没有 Java 25 一行 |
| Broker 进程使用哪一个 JDK | `NOT_VERIFIED_IN_REPOSITORY` |
| offset 连续提交、暂停后续处理、rebalance、重启 | `NOT_VERIFIED`。仓库没有消费者 |
| KAFKA-19012 对 3.9.2 producer 是否仍存在 | `NOT_VERIFIED` |
| 3.3.16 批量监听器是否包含 4.1.0 那次 offset 修复 | `NOT_VERIFIED`。发布说明未列入 backport，也未核对源码 |
| 兼容矩阵 “3.8.0 to 3.9.0 / Boot 3.4.x” 与 BOM 3.9.2 / Boot 3.5.16 的文字差 | 已记录。不是运行时失败证据 |
| 覆盖文档所说 Boot 3.5.x 使用 clients 3.8.x | 与 E6 不一致。不是本仓库的版本覆盖 |

没有把社区经验写成官方承诺。

## 11. 最终结论

| 问题 | 结论 | 状态 |
| --- | --- | --- |
| 1. Kafka 4.1.2 Broker 能否与 Client 3.9.2 通信 | 4.1 文档中的 4.0 协议表把 3.x Client 标为 Fully Compatible，4.1.0–4.1.2 说明没有宣布 3.x 协议中断。没有连接实验，不能写成已经联通 | `CONDITIONALLY_CONFIRMED` |
| 2. Spring Kafka 3.3.16 与 Client 3.9.2 是否有足够官方依据 | Boot 3.5 坐标表同时管理二者，发布说明把 3.3.16 集成进 Boot 3.5.15。通用矩阵写到 Client 3.9.0 和 Boot 3.4.x。覆盖说明又写 Boot 3.5.x 使用 3.8.x。三项并存 | `CONDITIONALLY_CONFIRMED` |
| 3. Spring Boot 3.5.16 BOM 是否管理这两个版本 | 3.5 坐标表列出 `kafka-clients` 3.9.2 与 `spring-kafka` 3.3.16。仓库导入 3.5.16 且没有版本覆盖。这不是类路径上已经存在的客户端 | `CONFIRMED` |
| 4. 当前版本组合是否需要调整 | 不建议为本调查覆盖 `kafka.version` 或 `spring-kafka.version`，也不建议把客户端升到 4.x。矩阵文字差留给后续决策，不在本轮改 POM | `DECISION_REQUIRED` 仅针对要不要在 ADR 里解释矩阵差。依赖调整本身不是本调查的建议 |
| 5. 是否在 ADR-0001 补充 Broker 版本 | ADR-0001 只有客户端版本，没有 Broker。若要把 `kafka_2.13-4.1.2` 写成基线，需要单独决策。本轮不改 ADR | `DECISION_REQUIRED` |
| 6. KRaft 要求是否已被当前部署满足 | 4.0 及之后的官方要求是 KRaft。仓库没有部署配置，不能证明满足 | `NOT_VERIFIED_IN_REPOSITORY` |
| 7. JDK 25 与 Broker、Client 的证据是否充分 | 不充分。Broker 表只到 Java 23 且版本列是 4.0.0。Client 3.9 说明没有 Java 25。应用 JDK 与 Broker JDK 分开 | `NOT_VERIFIED` |
| 8. 是否有需要后续工程实验的风险 | 有。连接、KRaft 与元数据版本、JDK 25 上的客户端、offset 连续提交、rebalance、重启、KAFKA-19012、批量监听器差异。实验未做 | `NOT_VERIFIED` |
| 9. 是否建议 IR-09 增加 4.1.2 验收项 | 建议在客户端真正进入类路径之后，把第 8 节五条规则列为对 Broker 4.1.2 的验收，而不是把协议表当作验收通过。本轮不改台账，不改变 IR-09 状态 | `DECISION_REQUIRED` |
| 10. 能否认定为设计基线 | 客户端 3.9.2 与 Spring Kafka 3.3.16 已经是 ADR-0001 的 BOM 管理基线，并且尚未进入类路径。加上 Broker 4.1.2 的这一组合，只能作为待运行时验证的候选，不能当成已经验证的运行基线 | 候选组合，`NOT_VERIFIED` |

没有 `INCOMPATIBLE` 项。官方材料没有给出 4.1.2 Broker 拒绝 3.9.2 客户端的句子。

## 12. 后续行动建议

1. 保持现有依赖管理。不覆盖 Kafka 客户端或 Spring Kafka 版本，不把客户端写入模块。
2. 若架构复审接受 Broker 版本进入基线，另开任务修改 ADR-0001。本文件不代替该决策。
3. 部署配置入库之前，KRaft、Controller/Broker 角色和元数据版本保持 `NOT_VERIFIED_IN_REPOSITORY`。不要用应用 JDK 25 填写 Broker 的 JDK。
4. 客户端进入类路径之后，再做连接实验，并按 06-04-26 第 7 节做 offset 连续提交、暂停后续处理、rebalance 和重启实验。这些实验归入 IR-09 的待验证项，不关闭 IR-03。
5. 单独核对 KAFKA-19012 是否影响 3.9.2 producer，以及 3.3.16 批量监听器的 offset 行为。核对前不升级依赖。

本轮未执行的测试：无 Maven 命令，无 Kafka 客户端调用，无故障注入。未验证事项保持未验证。
