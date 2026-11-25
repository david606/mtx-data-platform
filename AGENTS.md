# MTX Data Platform 编码代理指南

本文件是 AI 编码代理的仓库级入口。它不替代已接受的架构决策，也不覆盖人类贡献指南或 Cursor 专用规则。

事实分为四类：

| 分类 | 含义 |
| --- | --- |
| 已确认架构约束 | 已接受的 ADR 或 `docs/architecture/` 中要求遵守的规则 |
| 当前实际实现 | 当前仓库代码或 POM 中可以核对到的能力 |
| 规划中的能力 | 已经写明方向，但仓库中尚未实现 |
| 待决策事项 | 文档写明尚未选定，或仍待单独决定 |

本文件中的编码纪律是对编码代理的工作要求。它们不是新的 ADR。

## 1. 项目定位与建设目标

MTX Data Platform（简称 MTX RDP）是面向连锁餐饮企业的统一数据平台，在本仓库从零建设。Maven `groupId` 与 Java 包名是 `com.mtx.rdp`。根工程 `artifactId` 是 `mtx-data-platform`，`packaging` 是 `pom`，版本是 `0.1.0-SNAPSHOT`。默认分支是 `main`。

旧系统 `trade-flow` 是现存系统，不是 V2 的既有实现。它的代码、表结构、字段和公式不能写成 V2 已经具备的能力。

| 分类 | 当前事实 |
| --- | --- |
| 当前实际实现 | 十模块 Maven 骨架；`app-admin` 与 `app-worker` 各有一个 Spring Boot 启动类和上下文启动测试。八个 `platform-*` 模块允许没有 Java 源文件 |
| 已确认架构约束 | [ADR-0001](docs/decisions/0001-technology-baseline.md) 的技术基线，以及 [ADR-0002](docs/decisions/0002-v2-module-architecture.md) 的十模块和依赖矩阵 |
| 规划中的能力 | 连接器、消息消费、转换、质量检查、写入、Pipeline、调度、业务 API 和表结构 |
| 待决策事项 | MySQL、Redis、Flyway；Doris 客户端版本；调度适配放在哪个现有模块 |

正式详细设计未入库前，不要把 `docs/investigation/` 中的调查或设计稿写成已经实现的生产功能。金额处理相关的已接受决策只以 [ADR-0003](docs/decisions/0003-unmapped-conflict-observation.md) 和 [ADR-0004](docs/decisions/0004-external-contract-version-resolution.md) 的适用范围为准。

## 2. 权威文档与规则优先级

| 顺序 | 文件 | 职责 |
| --- | --- | --- |
| 1 | 状态为已接受的 ADR | 架构与技术基线的权威依据 |
| 2 | [docs/architecture/](docs/architecture/) | 架构边界。模块名单以 ADR-0002 为准，不以历史规划为准 |
| 3 | [CONTRIBUTING.md](CONTRIBUTING.md) | 人类贡献者的分支、构建和 PR 约定 |
| 4 | [.cursor/rules/](.cursor/rules/) | Cursor 专用规则 |
| 5 | 本文件 | 编码代理的通用入口 |

具体入口：

- ADR-0001：已确认的技术基线。它是历史决策，不再决定当前模块名单。
- ADR-0002：已确认的十模块和依赖关系。
- ADR-0003：已接受。只覆盖未识别金额与冲突金额观测，不替代字段和窗口细节文档，也不表示观测功能已经编码。
- ADR-0004：状态是 Accepted。只覆盖固定快照内、金额路径提取之前的外部契约版本解析。它不包含表结构、索引、事务或 Java 实现。
- [docs/README.md](docs/README.md)：文档目录索引。
- [docs/decisions/README.md](docs/decisions/README.md)：决策索引。

规则冲突时：

1. 不覆盖 ADR，不改技术基线，不改模块依赖。
2. 不修改 ADR、架构文档、贡献指南或 Cursor Rules 来消除冲突。
3. 记录涉及的文件、章节、两边的原句和影响。
4. 暂停受影响的变更，等待人工确认。

本文件不能覆盖其他权威文档。

## 3. 已确认技术基线

版本以 ADR-0001 和根 `pom.xml` 为准。不要按互联网上的更新版本升级。`dependencyManagement` 中出现坐标，不等于该依赖已进入类路径。

| 技术 | 已确认基线 | 当前工程状态 |
| --- | --- | --- |
| JDK | 25 LTS。开发运行时记录为 Temurin 25.0.5.1+2 | 根 POM `java.version` 与 `maven.compiler.release` 为 25。Enforcer 要求 `[25,26)` |
| Maven | Wrapper 固定 Apache Maven 3.9.6 | `.mvn/wrapper/maven-wrapper.properties` 的 `distributionUrl` 指向 3.9.6。标准命令是 `./mvnw` |
| Spring Boot | 3.5.16。只导入 BOM，不继承 `spring-boot-starter-parent` | 根 POM 导入 `spring-boot-dependencies` 3.5.16。两个应用模块使用 `spring-boot-starter` |
| Spring Framework | 6.2.19，由 Spring Boot 3.5.16 BOM 解析 | 子模块没有单独覆盖 |
| JUnit Jupiter | 5.12.2，由 Spring Boot BOM 管理 | 两个应用使用 `spring-boot-starter-test`。没有另导入 JUnit BOM，没有启用 Failsafe |
| MyBatis-Plus | 3.5.18.1，构件 `mybatis-plus-spring-boot3-starter` | 只登记在根 POM 的 `dependencyManagement`。没有模块把它写入 `dependencies` |
| Apache Kafka | Client 3.9.2，Spring Kafka 3.3.16 | 由 BOM 管理。骨架没有引入客户端 |
| Quartz | 2.5.2 | 初期调度方向。骨架没有引入 `spring-boot-starter-quartz`，也没有调度实现 |
| Apache Doris | OLAP 方向已确认 | 客户端和驱动版本未锁定，Writer 未实现 |
| Apache DolphinScheduler | 未来复杂 DAG 与工作流的演进方向 | 当前不引入依赖，不是正在使用的调度器 |
| Compiler Plugin | 3.15.0 | 根 POM 已配置 |

明确不采用：JDK 27、Spring Boot 4、JUnit 6、RocketMQ、XXL-JOB、TestNG。MySQL、Redis、Flyway 未决策，不要引入对应依赖。

## 4. 固定十模块架构

已确认约束与当前反应器一致。根 POM 只聚合：

1. `platform-common`
2. `platform-core`
3. `platform-messaging`
4. `platform-connector`
5. `platform-transform`
6. `platform-quality`
7. `platform-writer`
8. `platform-pipeline`
9. `app-admin`
10. `app-worker`

不增加第十一模块。不恢复 `rdp-common`、`rdp-server`。不新增 `platform-persistence`、`platform-scheduler`、`platform-plugin`、`platform-gateway`、`platform-monitor`。普通代码组织问题用现有模块内的包解决。

[docs/architecture/module-plan.md](docs/architecture/module-plan.md) 是历史规划。其中的 `rdp-*` 名单不得作为当前实施依据。

改变模块数量、名称或依赖方向，必须先有独立架构评审和正式 ADR，获得批准后再改 POM。

## 5. 模块职责和依赖边界

允许的内部依赖以 ADR-0002 为准，并与当前各模块 `pom.xml` 一致：

| 模块 | 允许依赖 |
| --- | --- |
| `platform-common` | 无内部模块 |
| `platform-core` | `platform-common` |
| `platform-messaging` | `platform-common`、`platform-core` |
| `platform-connector` | `platform-common`、`platform-core` |
| `platform-transform` | `platform-common`、`platform-core` |
| `platform-quality` | `platform-common`、`platform-core` |
| `platform-writer` | `platform-common`、`platform-core` |
| `platform-pipeline` | `platform-common`、`platform-core` |
| `app-admin` | `platform-common`、`platform-core`、`platform-messaging` |
| `app-worker` | `platform-common`、`platform-core`、`platform-messaging`、`platform-connector`、`platform-transform`、`platform-quality`、`platform-writer`、`platform-pipeline` |

职责按 ADR-0002 和 [docs/architecture/scheduling.md](docs/architecture/scheduling.md) 理解。除两个启动类外，下列职责都是约束或规划，不是已实现能力。

- `platform-common`：无业务框架的公共类型。不依赖 Spring 或其他内部模块，不承载业务流程编排。不要把它扩成无边界工具集合。
- `platform-core`：核心模型、规则和抽象。不依赖 Spring、Kafka、JDBC、MyBatis、Quartz、Redis 或 Doris 客户端。
- `platform-messaging`：消息基础设施适配。消息中间件实现留在本模块，不进入 Core。当前没有 Kafka 依赖。
- `platform-connector`：外部数据源和业务平台接入。具体平台实现不进入 Core。当前没有接入实现。
- `platform-transform`：数据转换实现。Pipeline 不依赖本模块。当前没有转换实现。
- `platform-quality`：数据质量检查实现。Pipeline 不依赖本模块。当前没有质量检查实现。
- `platform-writer`：目标存储写入，包括未来的 Doris 写入。Pipeline 不依赖本模块。当前没有 Writer 实现。
- `platform-pipeline`：流程编排和处理生命周期。只通过 Core 中的抽象组织流程。不依赖 Connector、Transform、Quality、Writer 的实现，不依赖 Quartz 或 DolphinScheduler，不承担基础设施装配。当前没有 Pipeline 实现。
- `app-admin`：管理端应用。启动类是 `com.mtx.rdp.admin.AdminApplication`。不执行数据处理链路，不依赖 Worker。
- `app-worker`：执行端应用。启动类是 `com.mtx.rdp.worker.WorkerApplication`。负责装配具体实现并连接 Pipeline 所需抽象。不把基础设施依赖推入 Core 或 Pipeline。当前启动类没有业务执行代码。

## 6. Core、Pipeline 与 Worker

- Core 保持模型和规则独立，用抽象定义边界。技术框架变化不得迫使 Core 依赖具体基础设施。
- Pipeline 负责编排，不负责装配。它通过接口协作，不直接调用具体处理实现，也不把 Quartz 或 DolphinScheduler 当作执行引擎。
- Worker 是实际执行的应用入口，负责执行侧装配。不得为了少写一层而让 Core 或 Pipeline 依赖具体实现。

调度边界见 `docs/architecture/scheduling.md`：Quartz API 不得进入 Pipeline、Connector、Transform、Writer。当前没有调度适配层，不要在两个应用里直接创建 Quartz `Scheduler`。同一个任务不得由 Quartz 和 DolphinScheduler 重复调度。调度实现放在哪个现有模块，仍待详细设计。

## 7. 面向对象与代码设计

编码代理使用 OOP，并遵守 SOLID、依赖倒置、接口隔离、单一职责、组合优于继承和显式依赖。

不采用 DDD。不要为了贴合某种架构流派增加领域层、聚合根、仓储体系或额外分层。抽象以当前业务复杂度和维护成本为准。仓库里没有 DDD 模型，不要补造。

## 8. 设计模式

确实存在变化点时，可以使用 Strategy、Factory、Adapter、Template Method、Observer、Decorator 或 Builder。

先说明变化点和要解决的问题，再使用模式。优先直接实现。不为单一稳定实现增加抽象，不预建没有真实调用方的扩展框架，不重复封装 Java 或 Spring 已经提供的能力。

## 9. Java 编码

仓库没有 checkstyle、spotless 或 P3C 插件。编码代理遵循《阿里巴巴 Java 开发手册》P3C，不切换到 Google Java Style。不要为了执行 P3C 而新增 Maven 插件或格式化配置；那需要单独批准。

同时遵守：

- 包名以 `com.mtx.rdp` 开头，并与模块职责一致。源码使用 UTF-8，编译 `--release 25`。
- 类和方法职责单一，命名表达业务含义，控制复杂度，避免重复和过度封装。
- 合理使用不可变对象。依赖通过构造器或明确工厂传入，避免不必要的静态全局状态。
- 不使用魔法值。不忽略编译器警告和已有检查结果。
- 父 POM 未管理的第三方库，不要在子模块随意写版本。
- 详细约束见 [.cursor/rules/02-java-development.mdc](.cursor/rules/02-java-development.mdc)。

## 10. 异常处理

使用能表达错误语义的异常类型。不使用空 catch，不吞掉异常，不用笼统的 `RuntimeException` 掩盖原因。可恢复错误和不可恢复错误分开。包装异常时保留根因，同一失败不要重复记录到失去原因。

对外响应不返回内部堆栈、口令或敏感报文。业务错误与基础设施错误保持边界。本文件不定义新的业务错误码。金额领域已有错误码以对应已接受决策和已确认设计为准，不要在这里增补。

## 11. 日志

使用 Spring Boot 默认的 SLF4J。日志要能关联已有的任务或执行标识，但不新增未经批准的身份字段。避免无意义的大量 INFO，避免在高频循环中刷日志。不用日志代替异常处理。

不记录密码、Token、密钥、完整证件号、完整敏感报文或真实连接串。敏感原始数据也不写入异常消息。

## 12. 事务与并发

事务边界要明确，范围保持可控。不在长事务中插入不必要的远程调用。消息消费、数据库事务和外部调用没有默认的统一原子性。

重复投递、重试和多个 Worker 并发时，不得破坏已经批准的任务状态和结果一致性。不用未经论证的全局锁，也不靠增加线程数解决性能问题。

本节只约束实现纪律。物理唯一索引、锁、事务、跨 Worker 证明和 Pipeline 持久化模型尚未批准。不要借实现任务把它们写进代码或 ADR。

## 13. 测试与构建

新增可观察行为时补充测试。修复缺陷时补充回归测试。覆盖正常、异常、边界，以及任务实际涉及的并发场景。空能力模块没有可测行为时，不要为了凑数添加无意义测试。

不允许删除测试、削弱断言或无依据地跳过测试来获得通过。失败时记录命令、阶段和原因，并区分原有失败与本次变更引入的失败。没有执行的测试不得写成通过。

当前事实：

- 测试框架是 JUnit Jupiter 5.12.2。
- 现有测试只有 `AdminApplicationTest` 和 `WorkerApplicationTest`，验证各自 Spring 上下文能在 JDK 25 上启动。
- 单元测试位置在被测能力模块。`platform-common` 与 `platform-core` 的测试不加载 Spring，也不依赖 Kafka、JDBC、MyBatis、Quartz、Redis。
- `platform-pipeline` 的测试只依赖 `platform-common` 与 `platform-core`。
- 两个应用的测试互不依赖。
- 单元测试不启动外部 Kafka、Doris 或数据库。当前没有真实集成测试。Failsafe 未启用。

构建使用 JDK 25 和 `./mvnw`。不要把未固定版本的 `mvn` 当作标准命令。提交前的标准命令见 [CONTRIBUTING.md](CONTRIBUTING.md) 和 [docs/development/local-build.md](docs/development/local-build.md)：

```bash
./mvnw -B clean verify
```

只在 JDK 17 或 JDK 27 上通过，不算满足基线。

## 14. 数据安全

不在代码、文档、配置、测试样例或提交中写入真实密码、Token、密钥、证书私钥或生产连接串。不提交生产敏感配置，不把真实用户数据或生产原始报文放进测试。示例配置使用占位符。

外部输入需要校验。数据访问和外部调用遵循已经批准的安全设计；仓库当前没有单独的安全 ADR。没有批准的设计时，不要自行发明认证、授权或加密方案。

数据库脚本约束见 [.cursor/rules/03-database.mdc](.cursor/rules/03-database.mdc)。`sql/` 只放已经确认的结构变更。

## 15. 编码代理工作流程

1. 理解任务：确认要求、模块和文件。阅读当前代码、文档和 ADR。不按历史印象直接改。
2. 确认约束：核对相关 ADR、模块依赖和现有接口。区分已接受决策、调查文档和未实现能力。
3. 最小变更：只解决当前任务。不顺手重构，不增加未经批准的依赖、模块或抽象。
4. 实施：只改任务允许的文件。保持现有风格和模块边界。破坏性变更需要明确批准。
5. 验证：运行与变更相称的测试和 `./mvnw -B clean verify`（任务明确不要求构建时除外）。检查 diff、依赖和文档是否一致。
6. 汇报：列出修改的文件和内容、实际执行的命令和结果、架构风险、未决事项，以及是否需要新的 ADR。没有执行的操作不得写成已执行。

## 16. 文档与 ADR

架构约束变化先新增决策记录，再改实现。不要直接改写已接受 ADR 来掩盖冲突。Proposed 或历史规划不能写成现行决策。不删除历史决策。

代码变更如果改变已确认边界，要同步相应文档。只改文档不能声称功能已经实现。文档链接使用仓库内相对路径，并确认目标文件存在。Markdown 文件名沿用现有目录习惯，不使用书名号包裹文件名。

发现文档互相冲突时，记录并报告，不自行选一边覆盖另一边。

## 17. 需要单独评审的架构变更

以下事项编码代理不得自行实施：

- Maven 模块数量、名称或依赖方向变化。
- Core 依赖基础设施实现。
- Pipeline 直接依赖具体处理实现或调度 API。
- Worker、Admin 的职责变化。
- 技术基线替换或升级。
- 核心任务模型、执行模型、持久化模型或数据处理生命周期变化。
- 已确认公共接口和契约变化。

先给出变更方案、影响分析和必要的 ADR，等待人工批准后再改代码。

## 18. 禁止事项

1. 增加第十一 Maven 模块。
2. 恢复 `rdp-common` 或 `rdp-server`。
3. 新增 `platform-persistence`、`platform-scheduler`、`platform-plugin` 等未批准模块。
4. 让 Core 依赖 Spring、Kafka、JDBC、MyBatis、Quartz、Redis 或 Doris 客户端。
5. 让 Pipeline 依赖 Connector、Transform、Quality、Writer 的实现。
6. 让 Pipeline、Connector、Transform 或 Writer 引用 Quartz 或 DolphinScheduler API。
7. 把 Worker 的装配职责移到 Core 或 Pipeline。
8. 擅自升级或替换技术栈。
9. 擅自修改已接受 ADR。
10. 引入 DDD 分层或仓储体系。
11. 为了使用设计模式增加无调用方的抽象。
12. 以未来扩展为由预建复杂框架。
13. 把 `trade-flow` 写成 V2 已有能力。
14. 把尚未实现的功能写成已实现。
15. 为了构建通过而修改无关代码、依赖或测试。
16. 未经批准改变模块结构或依赖方向。
17. 提交生产密码、Token、密钥或敏感数据。
18. 超出任务授权扩大修改范围。

## 19. 提交前检查

- [ ] 已阅读相关 ADR。
- [ ] 已确认模块职责和依赖边界。
- [ ] 没有擅自增加 Maven 模块。
- [ ] 没有引入未经批准的依赖。
- [ ] 没有突破 Core、Pipeline、Worker 边界。
- [ ] 没有引入未经批准的架构决策。
- [ ] 遵循 P3C 和现有编码约束。
- [ ] 已执行必要测试。
- [ ] 已执行必要构建。
- [ ] 已检查异常、日志和敏感信息。
- [ ] 已检查文档和 ADR 是否一致。
- [ ] 已检查 Git diff。
- [ ] 没有提交无关文件或 `target/`。
- [ ] 已如实记录测试和构建结果。

## 20. 已知待核实事项

这些事项不是新决策。受影响的实现应暂停并报告。

- ADR-0004 开篇仍写：接受时不修改 06-03-02-03、06-03-03-01、06-03-03-03 和 06-03-03-06，那些文件中的旧条款尚未按该决策修订。[docs/decisions/README.md](docs/decisions/README.md) 也保留这句。后续调查文档若已改写相关句子，以各文件当前正文与 ADR-0004 的决策正文对照。两边不一致时，不选一边实现，也不改 ADR。
- ADR-0004 仍把 R15 快照选择措辞差异标为待决。不要在实现中选定“校验失败能否跳过快照”。
- ADR-0001 正文有初始化当时“尚无连接器模块”等句子。当前模块名单以 ADR-0002 和根 POM 为准。ADR-0001 继续只提供版本基线。
- ADR-0001 的已知限制写首次引入 starter 时执行 `mvn clean verify`。仓库入口规定的标准命令是 `./mvnw -B clean verify`。执行构建时用 Wrapper，不把这句理解成改用未固定的 `mvn`。
