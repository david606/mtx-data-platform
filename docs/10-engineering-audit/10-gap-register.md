# 10 差异登记

目标设计来自本次审查指令中的十模块与技术基线。仓库内 `docs/decisions/0001-technology-baseline.md` 是另一份已提交决策，差异中单独引用，避免把两份基线混成一份。

## GAP-001

- 编号：GAP-001
- 所属领域：模块
- 审查对象：正式 Maven 模块
- 当前实现：只有 `rdp-common`、`rdp-server`
- 代码证据：根 `pom.xml` 的 `<modules>`；GitHub `main` 文件树无 `platform-*` 与 `app-*`
- 目标设计：十个正式模块，从 `platform-common` 到 `app-worker`
- 差异：十个目标模块全部缺失，实际模块名也不在名单中
- 风险：后续代码没有落点，依赖方向无法执行
- 优先级：P0
- 整改建议：先确认模块名单，再按单向依赖创建模块。本次不创建
- 涉及文件：`pom.xml`
- 测试方案：`mvn clean validate` 能列出十个模块
- 验收标准：根 POM 模块列表与十个正式模块一致，且没有名单外模块

## GAP-002

- 编号：GAP-002
- 所属领域：模块
- 审查对象：`rdp-server`
- 当前实现：存在启动类 `RdpServerApplication`
- 代码证据：`rdp-server/pom.xml`，`rdp-server/src/main/java/com/mtx/rdp/server/RdpServerApplication.java`
- 目标设计：禁止新增 `rdp-server`。应用入口应是 `app-admin` 与 `app-worker`
- 差异：仓库包含被本次基线明确禁止的模块
- 风险：启动职责和后续两个应用入口重叠
- 优先级：P0
- 整改建议：在授权后决定保留、改名或退役。审查阶段不删除
- 涉及文件：`rdp-server/**`，根 `pom.xml`
- 测试方案：模块调整后重新 `mvn clean verify`，并确认两个应用能分别启动
- 验收标准：不存在违反已确认名单的模块；`app-admin` 与 `app-worker` 没有相互 Maven 依赖

## GAP-003

- 编号：GAP-003
- 所属领域：技术栈
- 审查对象：运行时技术选型
- 当前实现：类路径上有 Spring Boot 3.5.16 与 JUnit Jupiter 5.12.2。MyBatis-Plus 3.5.18.1、Quartz 2.5.2、Kafka 3.9.2 / spring-kafka 3.3.16 只写在 dependencyManagement。MySQL、Redis、Doris、Flyway、DolphinScheduler 无依赖也无代码
- 代码证据：根 `pom.xml`；`rdp-server` 依赖树；全仓库无对应 import
- 目标设计：Java、Spring Boot、Maven、MyBatis-Plus、MySQL、Redis、Kafka、Doris、Quartz、DolphinScheduler、JUnit 5、Docker、Flyway
- 差异：选型多数未进入实现。仓库决策 0001 甚至还没有把 MySQL、Redis、Flyway 列为已锁定产品
- 风险：按 V2 基线开发时会和 0001 冲突；空的版本管理会被误认为功能已接入
- 优先级：P0
- 整改建议：先修订决策，统一 0001 与 V2 基线，再引入依赖。不要在未使用的模块上预先加 starter
- 涉及文件：`pom.xml`，`docs/decisions/0001-technology-baseline.md`
- 测试方案：依赖树能看到已批准且被模块使用的组件；未批准组件不出现
- 验收标准：每项已启用技术都有模块依赖、配置和至少一条验证；未启用项在决策中标明

## GAP-004

- 编号：GAP-004
- 所属领域：数据库
- 审查对象：`dp_task_execution`、`dp_write_batch`、`dp_write_config`、`dp_writer_batch_ledger`
- 当前实现：无 SQL、无 Flyway、无这四张表
- 代码证据：`sql/README.md`；文件检索匹配数为 0
- 目标设计：审查这些历史表的真实结构、索引、唯一约束和迁移
- 差异：没有可核对的结构，也没有迁移历史
- 风险：任务执行和写入幂等没有持久化基础
- 优先级：P0
- 整改建议：以已确认设计单独提交迁移。不要在审查文档里编造字段
- 涉及文件：`sql/`，未来的 Flyway 目录
- 测试方案：迁移在空库和已有库上可重复验证
- 验收标准：四张表若仍属于目标设计，则迁移脚本、索引和唯一约束与已确认设计一致

## GAP-005

- 编号：GAP-005
- 所属领域：核心链路
- 审查对象：回调、报文、Outbox、Kafka、任务、Worker、Connector、Transform、Quality、Writer、Doris Stream Load、UNKNOWN 对账、Pipeline
- 当前实现：全部未发现实现
- 代码证据：[05-core-chain.md](05-core-chain.md)
- 目标设计：上述环节可集成并测试
- 差异：链路没有任何代码起点
- 风险：不存在可运行的数据平台路径
- 优先级：P0
- 整改建议：按 [12-remediation-order.md](12-remediation-order.md) 分步实现，每步独立验收
- 涉及文件：当前无对应实现文件
- 测试方案：每个环节先有单元测试，再有跨模块集成测试，最后有一条端到端测试
- 验收标准：一条从接入到 Doris 的路径在测试中跑通，UNKNOWN 有对账证据

## GAP-006

- 编号：GAP-006
- 所属领域：安全与可观测
- 审查对象：租户、API 权限、Worker 身份、追踪、指标、告警、备份
- 当前实现：均未发现。日志框架只是 Spring Boot 默认传递依赖
- 代码证据：[06-security.md](06-security.md)，[07-observability.md](07-observability.md)
- 目标设计：审查指令第六步所列控制项
- 差异：控制项不存在
- 风险：当前没有对外接口，所以还没有已暴露的未授权入口。进入试点前这些缺口会变成直接风险
- 优先级：P1
- 整改建议：与第一条对外 API 和 Worker 同时设计认证、租户和追踪，而不是事后补
- 涉及文件：无现成实现文件
- 测试方案：未认证请求被拒绝；租户数据不交叉；一条请求能用同一个追踪号穿过 Worker
- 验收标准：试点检查单中的权限、追踪、告警和备份都有可执行证据

## GAP-007

- 编号：GAP-007
- 所属领域：测试与交付
- 审查对象：集成测试、端到端测试、Docker、备份
- 当前实现：两个骨架测试通过。无镜像，无备份
- 代码证据：[08-testing.md](08-testing.md)，[09-deployment-cicd.md](09-deployment-cicd.md)
- 目标设计：集成测试、端到端测试、Docker 部署、备份恢复
- 差异：交付材料停留在初始化
- 风险：CI 绿灯只说明骨架可编译
- 优先级：P1
- 整改建议：功能模块出现后再补对应测试和镜像，不为空模块写部署文件
- 涉及文件：`.github/workflows/ci.yml`，`deploy/README.md`
- 测试方案：镜像启动与端到端测试使用同一版本
- 验收标准：试点环境能从镜像启动，并有一次恢复演练记录

## GAP-008

- 编号：GAP-008
- 所属领域：CI
- 审查对象：GitHub Actions 动作版本
- 当前实现：`actions/checkout@v4`、`actions/setup-java@v4`
- 代码证据：`.github/workflows/ci.yml`。运行 `44975136726` 曾报告这两项弃用
- 目标设计：CI 持续可维护
- 差异：构建已通过，但动作主版本已被平台标记弃用
- 风险：后续 runner 收紧后 CI 可能失败。不影响当前功能正确性
- 优先级：P2
- 整改建议：授权后把动作升到当前维护版本，并重跑 verify
- 涉及文件：`.github/workflows/ci.yml`
- 测试方案：升级后的 Actions 运行成功
- 验收标准：工作流无弃用注解，且 `mvn clean verify` 成功

## GAP-009

- 编号：GAP-009
- 所属领域：文档
- 审查对象：V2 差异清单与现有架构文档
- 当前实现：仓库架构文档描述 `rdp-*` 规划。本次点名的 V2 差异清单不在仓库中
- 代码证据：`docs/architecture/module-plan.md`；本目录 README 的检索记录
- 目标设计：审查对照一份既定 V2 清单
- 差异：实施基线有两套说法，且清单文件缺失
- 风险：后续整改会选错模块名或技术项
- 优先级：P1
- 整改建议：把确认后的清单纳入仓库，并标明它与 0001 的关系
- 涉及文件：`docs/architecture/module-plan.md`，`docs/decisions/0001-technology-baseline.md`
- 测试方案：文档审查，不需要业务测试
- 验收标准：一份基线被标记为当前有效，模块名单唯一
