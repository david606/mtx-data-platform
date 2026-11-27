# MTX Data Platform

MTX Data Platform（MTX RDP）是面向连锁餐饮企业的统一数据平台。

本仓库当前是 V2 十模块 Maven 工程骨架、技术基线锁定、架构约束和持续集成。数据处理 Pipeline、连接器、调度实现和业务表结构尚未开始。

## 命名

| 项 | 值 |
| --- | --- |
| 项目名称 | MTX Data Platform |
| 简称 | MTX RDP |
| Maven `groupId` | `com.mtx.rdp` |
| Java 基础包名 | `com.mtx.rdp` |
| 根工程 `artifactId` | `mtx-data-platform` |
| 根工程 `packaging` | `pom` |
| 默认分支 | `main` |

## 技术基线

版本依据见 [docs/decisions/0001-technology-baseline.md](docs/decisions/0001-technology-baseline.md)。V2 模块结构见 [docs/decisions/0002-v2-module-architecture.md](docs/decisions/0002-v2-module-architecture.md)。MySQL 与 Doris 的服务器版本见 [docs/decisions/0005-mysql-doris-storage-baseline.md](docs/decisions/0005-mysql-doris-storage-baseline.md)。

| 领域 | 方案 | 当前状态 |
| --- | --- | --- |
| Java | JDK 25 LTS | Temurin 25.0.5.1（2026-11-17） |
| 应用框架 | Spring Boot | 3.5.16 |
| 构建 | Maven Wrapper | 3.9.6 |
| ORM | MyBatis-Plus | 3.5.18.1 保留选型，尚未进入运行时依赖 |
| 事务数据库 | MySQL 8.0.46 | 目标服务器版本已接受。驱动、连接和库表尚未接入 |
| OLAP | Apache Doris 4.0.7 | 服务器版本已接受。客户端和 Writer 尚未完成 |
| 消息队列 | Apache Kafka | 客户端 3.9.2，Spring Kafka 3.3.16。均由 Spring Boot BOM 管理，骨架尚未引入 |
| 初期调度 | Quartz | 2.5.2，由 Spring Boot BOM 管理。尚未进入运行时，调度实现未完成 |
| 数据处理 | 自研 Transform / Pipeline | 尚未实现 |
| 工作流演进 | Apache DolphinScheduler | 未来演进方向，当前不引入依赖 |
| 未决策 | Redis、Flyway | 均未选定，当前不引入。MySQL 驱动、字符集、排序规则和隔离级别也未选定 |
| 测试 | JUnit 5 | JUnit Jupiter 5.12.2 |
| 容器 | Docker | 镜像构建尚未添加 |

## 模块

根 POM 只聚合这十个 V2 模块：

- `platform-common`、`platform-core`
- `platform-messaging`、`platform-connector`、`platform-transform`、`platform-quality`、`platform-writer`
- `platform-pipeline`
- `app-admin`、`app-worker`

`app-admin` 是管理端，启动类为 `com.mtx.rdp.admin.AdminApplication`。`app-worker` 是执行端，启动类为 `com.mtx.rdp.worker.WorkerApplication`。两者互不依赖。

`rdp-common` 与 `rdp-server` 已从当前工程删除，不参与聚合。旧代码留在 Git 历史中，不迁移进十个模块。两个应用在启动时核对进程名、职责和进程内健康状态。`spring.main.web-application-type` 为 `none`，因此不启动嵌入式 HTTP 服务。主方法在上下文刷新和启动校验返回后结束，代码没有调用退出 API。没有非守护线程时 JVM 会结束；以后若引入保持非守护线程的组件，进程不会仅因该配置退出。当前骨架没有业务 Controller、任务处理、Kafka 监听、数据库配置或 Pipeline 实现。

## 本地构建

构建必须使用 JDK 25。本机 SDKMAN 默认 Java 可能仍是其他版本，执行前指定 `JAVA_HOME`。

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.5.1+2-tem"
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -version
./mvnw -B validate
./mvnw -B verify
./mvnw -B clean verify
```

标准构建使用 Maven Wrapper 3.9.6，不要改用未固定版本的 `mvn`。`scripts/verify.sh` 会选择 JDK 25，然后执行 `./mvnw --batch-mode clean verify`。

## 文档

文档索引见 [docs/README.md](docs/README.md)。

## 分支

- `main`：可发布主线
- `feature/*`：功能开发
- `bugfix/*`：缺陷修复
- `hotfix/*`：生产紧急修复

协作约定见 [CONTRIBUTING.md](CONTRIBUTING.md)。
