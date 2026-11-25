# 贡献指南

MTX Data Platform（MTX RDP）的代码、文档和配置都进入本仓库。Maven `groupId` 与 Java 基础包名固定为 `com.mtx.rdp`。

## 分支

| 分支 | 用途 |
| --- | --- |
| `main` | 默认分支。只通过已通过 CI 的 Pull Request 更新。 |
| `feature/*` | 新功能或新模块骨架。 |
| `bugfix/*` | 非生产紧急的缺陷修复。 |
| `hotfix/*` | 需要尽快回到 `main` 的生产修复。 |

从最新的 `main` 拉出分支。不要在 `main` 上直接提交功能代码。

## 构建

使用 JDK 25 和仓库内的 Maven Wrapper。Wrapper 固定 Maven 3.9.6。标准命令是：

```bash
./mvnw -version
./mvnw -B validate
./mvnw -B verify
./mvnw -B clean verify
```

不要把 `mvn clean verify` 当作本项目的标准构建命令。提交前执行 `./mvnw -B clean verify`。

## 提交前

1. 使用 JDK 25 执行 `./mvnw -B clean verify`。
2. 不要提交密钥、令牌、密码、证书私钥、`.env` 或本地编辑器状态。
3. 不要把 Quartz 或 DolphinScheduler 的 API 写入 Pipeline、Connector、Transform、Writer。
4. 不要把同一任务同时交给 Quartz 和 DolphinScheduler。
5. 版本基线沿用 [docs/decisions/0001-technology-baseline.md](docs/decisions/0001-technology-baseline.md)。模块结构和依赖是否进入类路径以 [docs/decisions/0002-v2-module-architecture.md](docs/decisions/0002-v2-module-architecture.md) 为准。MySQL 8.0.46 与 Doris 4.0.7 以 [docs/decisions/0005-mysql-doris-storage-baseline.md](docs/decisions/0005-mysql-doris-storage-baseline.md) 为准。替换这些版本、Kafka、Quartz、JUnit 5 或 JDK 25 需要新的决策记录，不能在功能分支里顺手更换。

当前测试只有 `app-admin` 与 `app-worker` 的上下文启动测试。不要在说明里把尚未存在的集成测试或业务功能写成已经验证。

## Pull Request

PR 说明需要包含：

- 变更目的。
- 影响的模块。
- 验证方式，至少包括 `./mvnw -B clean verify` 的结果。
- 若改动了调度边界、数据模型或对外接口，链接对应的设计或决策文档。

`.github/workflows/ci.yml` 在 `main` 的 push 和所有 Pull Request 上执行 `./mvnw -B clean verify`，使用 Temurin JDK 25。

## 文档

正式详细设计尚未入库时，不要在 `docs/design/` 中编写看起来已经评审通过的设计。架构约束写在 `docs/architecture/`，版本选择写在 `docs/decisions/`。

## 模块

V2 根 POM 只聚合十个模块：`platform-common`、`platform-core`、`platform-messaging`、`platform-connector`、`platform-transform`、`platform-quality`、`platform-writer`、`platform-pipeline`、`app-admin`、`app-worker`。

不要增加第十一模块，也不要恢复已删除的 `rdp-common`、`rdp-server`。当前应用入口只有 `app-admin` 与 `app-worker`。依赖方向以 ADR-0002 为准。`docs/architecture/module-plan.md` 是历史规划，不能作为新增模块的依据。
