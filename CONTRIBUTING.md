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

## 提交前

1. 使用 JDK 25 执行 `mvn clean verify`。
2. 不要提交密钥、令牌、密码、证书私钥、`.env` 或本地编辑器状态。
3. 不要把 Quartz 或 DolphinScheduler 的 API 写入 Pipeline、Connector、Transform、Writer。
4. 不要把同一任务同时交给 Quartz 和 DolphinScheduler。
5. 技术基线以 [docs/decisions/0001-technology-baseline.md](docs/decisions/0001-technology-baseline.md) 为准。替换 Doris、Kafka、Quartz、JUnit 5 或 JDK 25 需要新的决策记录，不能在功能分支里顺手更换。

## Pull Request

PR 说明需要包含：

- 变更目的。
- 影响的模块。
- 验证方式，至少包括 `mvn clean verify` 的结果。
- 若改动了调度边界、数据模型或对外接口，链接对应的设计或决策文档。

CI 在 `main` 的 push 和所有 Pull Request 上执行 `mvn clean verify`，使用 JDK 25。

## 文档

正式详细设计尚未入库时，不要在 `docs/design/` 中编写看起来已经评审通过的设计。架构约束写在 `docs/architecture/`，版本选择写在 `docs/decisions/`。

## 模块

新增 Maven 子模块前，先更新 [docs/architecture/module-plan.md](docs/architecture/module-plan.md) 中的依赖方向。没有明确调用方时，不要提交空模块。
