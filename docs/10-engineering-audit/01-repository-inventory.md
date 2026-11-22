# 01 工程现状

证据范围：本地工作区与 GitHub `main` 文件树一致，唯一提交 `0abaaef`。以下清单按仓库文件记录，不按设计文档补文件。

## 目录

```
mtx-data-platform/
  .cursor/rules/
  .github/ISSUE_TEMPLATE/
  .github/workflows/ci.yml
  deploy/README.md
  docs/          architecture, api, database, decisions, deployment, design, development, operations
  rdp-common/
  rdp-server/
  scripts/verify.sh
  sql/README.md
  pom.xml
```

没有 `platform-*`、`app-admin`、`app-worker` 目录。没有 `Dockerfile`、Flyway 目录、`src/main/resources/db/migration`。

## 实际 Maven 模块

根 POM `modules` 只有：

- `rdp-common`（jar）
- `rdp-server`（jar）

根工程 `com.mtx.rdp:mtx-data-platform:0.1.0-SNAPSHOT`，`packaging` 为 `pom`。

本次审查基线中的十个模块均不存在：`platform-common`、`platform-core`、`platform-messaging`、`platform-connector`、`platform-transform`、`platform-quality`、`platform-writer`、`platform-pipeline`、`app-admin`、`app-worker`。

## 各模块 POM

| 模块 | parent | 直接依赖 |
| --- | --- | --- |
| 根 POM | 无 | 无运行时依赖；`dependencyManagement` 导入 Spring Boot BOM，并管理 `rdp-common`、MyBatis-Plus starter、Quartz starter、spring-kafka |
| `rdp-common` | 根 POM `0.1.0-SNAPSHOT` | `junit-jupiter`，test |
| `rdp-server` | 根 POM `0.1.0-SNAPSHOT` | `rdp-common`、`spring-boot-starter`、`spring-boot-starter-test`（test） |

## Java 包

| 路径 | 包 |
| --- | --- |
| `rdp-common/src/main/java/com/mtx/rdp/Platform.java` | `com.mtx.rdp` |
| `rdp-common/src/test/java/com/mtx/rdp/PlatformTest.java` | `com.mtx.rdp` |
| `rdp-server/src/main/java/com/mtx/rdp/server/RdpServerApplication.java` | `com.mtx.rdp.server` |
| `rdp-server/src/test/java/com/mtx/rdp/server/RdpServerApplicationTest.java` | `com.mtx.rdp.server` |

全仓库 Java 源文件共 4 个。没有 Controller、Consumer、Connector、Transform、Quality、Writer、Pipeline、Task、Worker 类。

## 启动类

唯一启动类是 `com.mtx.rdp.server.RdpServerApplication`。它调用 `SpringApplication.run`，并返回 `Platform.NAME`。没有第二个应用入口。`app-admin` 与 `app-worker` 的启动类未发现。

## 实际技术版本

来自根 `pom.xml` 属性，以及本次 `mvn dependency:tree` 在 `rdp-server` 上解析出的版本。

| 项 | 仓库中的值 | 是否进入已构建模块的类路径 |
| --- | --- | --- |
| Java 编译 | `maven.compiler.release` 25 | 是。编译日志为 `javac [release 25]` |
| 本次构建 JDK | Temurin 25.0.5.1（2026-11-17 LTS） | 构建进程使用 |
| Spring Boot | 3.5.16 | 是。`rdp-server` 测试日志打印 `Spring Boot :: (v3.5.16)` |
| Spring Framework | 6.2.19 | 是，由 Boot BOM 传递 |
| JUnit Jupiter | 5.12.2 | 是，test |
| MyBatis-Plus | 3.5.18.1 | 否，只在 dependencyManagement |
| Quartz | 2.5.2 | 否，starter 未被模块引用 |
| Kafka client 属性 | 3.9.2 | 否 |
| spring-kafka | 3.3.16 | 否 |
| MySQL、Redis、Doris、Flyway | 无版本属性，无依赖 | 否 |

## 配置文件

运行时配置只有 `rdp-server/src/main/resources/application.yml`：

```yaml
spring:
  application:
    name: mtx-rdp
```

没有数据源、Redis、Kafka、Quartz、安全或管理端口配置。

## SQL

`sql/` 下只有 `README.md`，声明当前没有 `.sql` 文件。全仓库文件名检索没有 `*.sql`。

## 测试文件

- `rdp-common/src/test/java/com/mtx/rdp/PlatformTest.java`
- `rdp-server/src/test/java/com/mtx/rdp/server/RdpServerApplicationTest.java`

没有 `src/test/resources`，没有测试容器或集成测试目录。
