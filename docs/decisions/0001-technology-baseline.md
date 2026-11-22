# 0001. 初始化技术基线

- 状态：已接受
- 日期：2026-11-22
- 范围：工程初始化。不包含 Pipeline、表结构或调度实现的详细设计。

## 结论

| 组件 | 锁定结果 | 依据 |
| --- | --- | --- |
| JDK | 25 LTS，开发机使用 Temurin 25.0.5.1+2（2026-11-17） | 项目基线指定 JDK 25 LTS，不使用 JDK 27。该 Temurin 构建对应 2026-11-17 的 LTS CPU。 |
| Spring Boot | 3.5.16 | Spring Boot 从 3.5.5 起声明 Java 25 可用（spring-projects/spring-boot#47245）。Maven Central 上 3.5 线最新补丁是 3.5.16。该线继续使用 JUnit Jupiter 5，与已确认的 JUnit 5 基线一致。 |
| MyBatis-Plus | 3.5.18.1，构件 `mybatis-plus-spring-boot3-starter` | 3.5.18.1 发布于 2026-10-27，晚于 3.5.18（2026-10-25），并包含 Spring Boot starter 配置隔离修复。Boot 3 starter 导入的 Spring Boot BOM 是 3.5.9，与 3.5.16 属于同一功能线的后续补丁。传递的 MyBatis 版本是 3.5.19；starter 声明的 MyBatis-Spring 是 3.0.6。 |
| Quartz | 2.5.2 | Spring Boot 3.5.16 BOM 的 `quartz.version`。由 `spring-boot-starter-quartz` 管理，初始化模块尚未引入该 starter。 |
| Kafka | 客户端 3.9.2，Spring for Apache Kafka 3.3.16 | Spring Boot 3.5.16 BOM 的 `kafka.version` 与 `spring-kafka.version`。消息队列保持 Kafka，初始化模块尚未引入客户端。 |
| JUnit | Jupiter 5.12.2 | Spring Boot 3.5.16 BOM 的 `junit-jupiter.version`。 |
| Doris | 产品基线已确定，驱动版本未锁定 | 尚无连接器模块。不预选未经设计确认的 JDBC 驱动。 |
| DolphinScheduler | 演进目标，依赖未引入 | 调度适配层和版本都还没有设计。 |
| Maven 编译 | `maven.compiler.release=25` | 与 JDK 基线一致。Compiler Plugin 3.15.0。 |

## 未采用的版本

- JDK 27。机器上另有 Temurin 27，但它不是本项目基线，Enforcer 会拒绝。
- Spring Boot 4.2.0。它于 2026-11-19 发布，官方运行范围是 Java 17 至 27。MyBatis-Plus 的 Boot 4 starter 仍导入 Spring Boot 4.1.0，不能把 4.2.0 视为已经一起验证。
- Spring Boot 4.1.3。它支持 Java 25，并且 4.1 线仍在开源支持期内。它的测试栈是 JUnit Jupiter 6.0.3。项目基线写明 JUnit 5，初始化不替换测试框架。若后续接受 JUnit 6，应新开决策再迁移到 4.1.x，而不是直接跳到 4.2.0。
- MyBatis-Plus 3.5.18。3.5.18.1 是其后的修复版本。

## 已知限制

Spring Boot 3.5 的开源支持已于 2026-06-30 结束。选择 3.5.16 是为了同时满足 JDK 25 和 JUnit 5，不是因为它仍在开源支持窗口内。安全补丁策略需要单独决定：继续使用 3.5 的商业支持，或接受测试栈变为 JUnit Jupiter 6 后迁移到 Spring Boot 4.1。

MyBatis-Plus、Quartz 和 Kafka 目前只在父 POM 的 `dependencyManagement` 中锁定。除 Spring Boot 启动器本身外，初始化模块没有把它们放上运行时类路径。第一次引入对应 starter 时，要用 JDK 25 重新执行 `mvn clean verify`。

## 明确不改变的选择

初始化不引入 RocketMQ、XXL-JOB，也不把 Doris 换成其他 OLAP。Quartz 与未来的 DolphinScheduler 通过适配层隔离，约束见 `docs/architecture/scheduling.md`。
