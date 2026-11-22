# 09 部署与 CI/CD

| 检查项 | 结论 | 证据 |
| --- | --- | --- |
| Docker | 未发现实现 | `deploy/` 只有 README，声明没有 Dockerfile 或 Compose。文件树中无 `Dockerfile` |
| CI | 有基础工作流 | `.github/workflows/ci.yml` 在 `main` push 和 pull request 上执行 `mvn --batch-mode clean verify`，`actions/setup-java@v4`，`java-version: "25"`，Maven 缓存 |
| 本地 CI 结果 | 本次命令通过 | 见 [02-maven.md](02-maven.md) |
| 远程 Actions | 初始化提交曾经通过 | 2026-11-22 运行 `44975136726` 成功。该次日志提示 `actions/checkout@v4` 与 `actions/setup-java@v4` 弃用。本次审查没有重跑远程 Actions |
| 备份恢复 | 未发现实现 | 无备份脚本或恢复步骤。`docs/operations/README.md` 无操作内容 |

CI 验证的是当前骨架能编译和通过两个测试。它不部署镜像，也不覆盖数据链路。
