# 本地开发

当前构建对象是 V2 十模块反应器。模块边界以 [ADR-0002](../decisions/0002-v2-module-architecture.md) 为准。

## JDK

项目编译和测试使用 JDK 25 LTS。不要使用 JDK 27 作为本项目的当前基线。

验收时 `./mvnw -version` 使用的是 Temurin `25.0.5.1+2`（版本字符串 `25.0.5.1`）。父 POM 的 Enforcer 要求 `[25,26)`。本机默认 `java` 若不是 25，先把 `JAVA_HOME` 指到 JDK 25，或执行 `scripts/verify.sh`，该脚本会选择 JDK 25。

## Maven

使用仓库内的 Maven Wrapper。Wrapper 固定 Apache Maven 3.9.6，分发方式是 `only-script`，不依赖未提交的 `maven-wrapper.jar`。

```bash
./mvnw -version
```

根 POM `packaging` 为 `pom`，只聚合这十个模块：

- `platform-common`
- `platform-core`
- `platform-messaging`
- `platform-connector`
- `platform-transform`
- `platform-quality`
- `platform-writer`
- `platform-pipeline`
- `app-admin`
- `app-worker`

`docs/`、`sql/`、`deploy/`、`scripts/` 是工程目录，不是 Maven 模块。

`rdp-common` 与 `rdp-server` 已从当前工程删除。不要把它们加回反应器，也不要改名后当作 V2 模块。

当前可执行应用是 `app-admin` 与 `app-worker`。八个 `platform-*` 能力模块在骨架阶段允许没有 Java 源文件；`verify` 时出现空 JAR 是当前预期，不表示业务能力已经实现。

依赖版本由根 POM 和 Spring Boot 3.5.16 BOM 管理。子模块不要重复声明 Spring Boot、Spring Framework、MyBatis-Plus、Quartz、Kafka 和 JUnit 的版本号。`dependencyManagement` 中的坐标不等于已经进入类路径。

内部依赖只允许：

| 模块 | 允许的内部模块 |
| --- | --- |
| `platform-common` | 无 |
| `platform-core` | `platform-common` |
| `platform-messaging` | `platform-common`、`platform-core` |
| `platform-connector` | `platform-common`、`platform-core` |
| `platform-transform` | `platform-common`、`platform-core` |
| `platform-quality` | `platform-common`、`platform-core` |
| `platform-writer` | `platform-common`、`platform-core` |
| `platform-pipeline` | `platform-common`、`platform-core` |
| `app-admin` | `platform-common`、`platform-core`、`platform-messaging` |
| `app-worker` | `platform-common`、`platform-core`、`platform-messaging`、`platform-connector`、`platform-transform`、`platform-quality`、`platform-writer`、`platform-pipeline` |

`platform-pipeline` 不依赖 Connector、Transform、Quality、Writer 的实现，也不依赖 Quartz 或 DolphinScheduler。`app-admin` 与 `app-worker` 互不依赖。

## 构建与测试

```bash
./mvnw -version
./mvnw -B validate
./mvnw -B dependency:tree -Dincludes=com.mtx.rdp
./mvnw -B verify
./mvnw -B clean verify
```

`./mvnw -B verify` 编译、执行现有测试并打包，不删除已有 `target`。`./mvnw -B clean verify` 会先删除构建产物，提交前和 CI 使用这条。CI 命令是 `./mvnw -B clean verify`。

当前测试包括两个应用的 Spring 上下文启动测试，以及 `platform-common` 中的启动校验和模块边界测试。根 POM 的 Enforcer 只要求 JDK `[25,26)`，不全局禁止 Kafka、MyBatis-Plus、MySQL、Doris 或 Quartz。`platform-core` 的 Enforcer 检查直接依赖和传递依赖，禁止 Spring、JDBC、MyBatis、Kafka、Quartz、Redis 和 Doris 客户端进入该模块。其余能力模块没有业务测试类。Maven Failsafe 没有启用。这些命令不连接 Doris、Kafka 或数据库，也没有业务启动参数。

两个应用配置进程名、`mtx.rdp.role`、统一控制台日志和进程内健康状态。`web-application-type` 为 `none`，不提供 HTTP 接口，也不单独保证进程退出。没有数据库、Kafka 或业务接口配置。
