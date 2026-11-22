# MTX Data Platform

MTX Data Platform（MTX RDP）是面向连锁餐饮企业的统一数据平台。

本仓库当前是工程初始化结果：Maven 多模块骨架、技术基线锁定、架构约束和持续集成。数据处理 Pipeline、连接器、调度实现和业务表结构尚未开始。

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

已锁定版本和选择依据见 [docs/decisions/0001-technology-baseline.md](docs/decisions/0001-technology-baseline.md)。

| 领域 | 方案 | 当前锁定 |
| --- | --- | --- |
| Java | JDK 25 LTS | Temurin 25.0.5.1（2026-11-17） |
| 应用框架 | Spring Boot | 3.5.16 |
| 构建 | Maven | 3.6.3 及以上；本机验证使用 3.9.6 |
| ORM | MyBatis-Plus | 3.5.18.1（`mybatis-plus-spring-boot3-starter`） |
| OLAP | Apache Doris | 基线已确定，客户端版本在连接器模块设计时锁定 |
| 消息队列 | Apache Kafka | 客户端 3.9.2，Spring for Apache Kafka 3.3.16（由 Spring Boot BOM 管理） |
| 初期调度 | Quartz | 2.5.2 |
| 数据处理 | 自研 Transform / Pipeline | 尚未实现 |
| 工作流演进 | Apache DolphinScheduler | 后续接入，当前不引入依赖 |
| 测试 | JUnit 5 | JUnit Jupiter 5.12.2 |
| 容器 | Docker | 镜像构建尚未添加 |

## 已创建模块

只创建了启动验证所必需的模块。其余模块的边界见 [docs/architecture/module-plan.md](docs/architecture/module-plan.md)，尚未建空目录。

- `rdp-common`：平台标识等共享类型，不依赖 Spring、Quartz 或 Pipeline。
- `rdp-server`：进程入口。只验证 Spring Boot 能在 JDK 25 上启动，不包含调度或数据处理实现。

## 本地构建

构建必须使用 JDK 25。本机 SDKMAN 默认 Java 可能仍是其他版本，执行前指定 `JAVA_HOME`。

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.5.1+2-tem"
export PATH="$JAVA_HOME/bin:$PATH"
java -version
mvn clean verify
```

`scripts/verify.sh` 会在 `JAVA_HOME` 未指向 25 时，尝试使用本机已安装的 Temurin 25，然后执行 `mvn clean verify`。

## 文档

文档索引见 [docs/README.md](docs/README.md)。

## 分支

- `main`：可发布主线
- `feature/*`：功能开发
- `bugfix/*`：缺陷修复
- `hotfix/*`：生产紧急修复

协作约定见 [CONTRIBUTING.md](CONTRIBUTING.md)。
