# 12 整改顺序与试点结论

## 试点结论

当前不具备进入生产试点的条件。

依据：十个正式模块都不存在；数据链路 17 项都没有实现；四张历史表和 Flyway 迁移都不存在；没有认证、租户隔离、告警、镜像和备份。`mvn clean verify` 通过只证明初始化骨架在 JDK 25 上可以编译并通过两个测试。

## 推荐顺序

1. 确认唯一有效基线。把缺失的 V2 清单纳入仓库，并处理它与 `docs/decisions/0001-technology-baseline.md` 在 MySQL、Redis、Flyway、模块名上的差别。
2. 在书面模块名单确认后，再调整 Maven 模块。先消除 `rdp-server` 与十模块名单的冲突，再按依赖方向增加模块。
3. 为已确认的表提交 Flyway 或 SQL 迁移。字段以确认后的设计为准。
4. 实现任务定义、执行、领取租约和 Worker 心跳，并补测试。
5. 实现 Outbox、Kafka 生产和消费幂等。
6. 实现 Connector、Transform、Quality、Writer。Doris Stream Load 和 UNKNOWN 对账放在 Writer 之后。
7. Pipeline 只编排上述端口，不反向依赖具体调度框架。
8. 与 `app-admin`、`app-worker` 一起加入认证、租户、追踪、指标和告警。
9. 增加集成测试、一条端到端测试、Docker 部署和备份恢复演练。
10. 升级 Actions 中已弃用的 v4 动作。

本审查到此停止，不开始整改。
