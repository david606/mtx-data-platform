# MTX Data Platform 工程现状审查

- 审查日期：2026-11-22
- 审查对象：本仓库工作区与 GitHub `main`
- 提交：`0abaaef`（`chore: initialize MTX Data Platform project`）
- 远程：`git@github.com:david606/mtx-data-platform.git`
- 性质：只读审查。未改 Java、POM、SQL、配置，未推送。

## 清单文件

在本仓库全部受控文件、GitHub `main` 文件树，以及工作区内文件名检索中，没有找到《MTX Data Platform V2 工程现状审查与最终实施差异清单》。因此该清单规定的十三份正式文件名标记为**待核实**。

本目录的十三份文档按本次审查指令的检查项落盘，不表示这些文件名已由缺失清单确认。

| 文件 | 内容 |
| --- | --- |
| [01-repository-inventory.md](01-repository-inventory.md) | 真实目录、模块、包、版本、配置、SQL、测试 |
| [02-maven.md](02-maven.md) | Maven 坐标、版本、构建命令结果 |
| [03-module-dependencies.md](03-module-dependencies.md) | 依赖树与十模块单向依赖 |
| [04-database.md](04-database.md) | SQL、Flyway 与四张历史表 |
| [05-core-chain.md](05-core-chain.md) | 回调到 Doris 的链路状态 |
| [06-security.md](06-security.md) | 租户、敏感信息、API 与 Worker 身份 |
| [07-observability.md](07-observability.md) | 日志、指标、告警 |
| [08-testing.md](08-testing.md) | 单元、集成、端到端 |
| [09-deployment-cicd.md](09-deployment-cicd.md) | Docker、CI、备份恢复 |
| [10-gap-register.md](10-gap-register.md) | 差异登记 |
| [11-conformance-summary.md](11-conformance-summary.md) | 十模块与技术栈符合度 |
| [12-remediation-order.md](12-remediation-order.md) | 整改顺序与生产试点结论 |

证据只来自本仓库文件和本次执行的 Maven 命令。未把其他目录中的工程算作本仓库实现。
