# 11 符合度

符合度只比较本仓库证据和本次审查指令。百分比不是测试覆盖率。

## 十模块

| 模块 | 符合度 |
| --- | --- |
| platform-common | 不符合。不存在。`rdp-common` 只有名称常量，不能视为该模块 |
| platform-core | 不符合。不存在 |
| platform-messaging | 不符合。不存在 |
| platform-connector | 不符合。不存在 |
| platform-transform | 不符合。不存在 |
| platform-quality | 不符合。不存在 |
| platform-writer | 不符合。不存在 |
| platform-pipeline | 不符合。不存在 |
| app-admin | 不符合。不存在 |
| app-worker | 不符合。不存在 |

额外事实：存在被禁止的 `rdp-server`。十模块符合度为 0/10。

已满足的命名约束：工程目录名 `mtx-data-platform`，GroupId `com.mtx.rdp`，现有 Java 包以 `com.mtx.rdp` 开头。

## 技术栈

| 技术 | 符合度 | 证据 |
| --- | --- | --- |
| Java | 部分符合 | 编译 release 25，本次 JDK 25.0.5.1 |
| Spring Boot | 部分符合 | 3.5.16 已用于启动骨架，没有业务功能 |
| Maven | 符合构建工具要求 | 多模块构建通过，但模块名单不符 |
| MyBatis-Plus | 未接入 | 仅 dependencyManagement 3.5.18.1 |
| MySQL | 未发现 | 无依赖、配置、SQL |
| Redis | 未发现 | 无依赖和配置 |
| Kafka | 未接入 | 版本在 dependencyManagement，无客户端代码 |
| Doris | 未发现 | 无依赖和 Writer |
| Quartz | 未接入 | 版本在 dependencyManagement，无 Job |
| DolphinScheduler | 未发现 | 决策文档写明后续演进且当前不引入依赖 |
| JUnit 5 | 部分符合 | Jupiter 5.12.2，两个骨架测试通过 |
| Docker | 未发现 | 无 Dockerfile |
| Flyway | 未发现 | 无迁移目录 |

## 数据库

四张指定表的符合度均为“未发现”，不是“结构不一致”。没有列可以比对。

## 代码与链路完成度

核心链路 17 项全部是“未发现实现”。完成度不能标成已集成、已测试或已验收。
