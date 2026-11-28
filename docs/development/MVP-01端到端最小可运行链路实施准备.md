# MVP-01 端到端最小可运行链路实施准备

- 代码基线：`cca0c87`。本轮只读代码，没有执行 Maven，没有改 Java、POM、SQL 或数据库
- 效力：实施顺序和授权边界。不关闭任何 IR，不启动 Stage C，不把 DEV_ONLY 写成正式来源身份

目标链路是：原文进入 Kafka，落到 MySQL，创建 Task，由 Worker 领取并执行 Pipeline，完成契约解析、字段提取、转换和 Writer，再写入 Doris 并查询核对。

## 1. 现状

十个模块都在根 POM 里。`platform-messaging`、`platform-transform`、`platform-quality`、`platform-writer`、`platform-pipeline` 只有 POM，没有 Java。`platform-core` 只有契约版本唯一性解析，调用前必须已经有一份读完的快照，不创建 Task。`platform-connector` 只有 `FictionalMessageSource` 和 `InMemoryRawMessageStore`：报文带 `DEV_ONLY` 与「虚构数据」，存储随进程关闭消失。注释写明类型在 classpath 上不等于运行时许可。Worker 依赖 connector，所以这两个类在 Worker 的 classpath 上，Worker 源码没有调用它们。

Admin 与 Worker 都能作为独立 Spring Boot 进程启动。两边都是 `web-application-type: none`，配置只绑定 `mtx.rdp.role`。启动核对角色和健康状态，不连接数据库或消息队列。当前启动不需要外部服务，也没有对应的环境变量。

仓库没有 MySQL 驱动、JDBC、MyBatis、Kafka 客户端、Doris 客户端，也没有连接配置。根 POM 只在 `dependencyManagement` 登记了 MyBatis-Plus，没有模块使用它。

| 链路步骤 | 状态 |
| --- | --- |
| 原文入口 | 只有模拟实现。虚构来源不连接平台，也不创建 Task |
| 原文存储 | 只有进程内模拟。没有 MySQL 原文表 |
| Kafka | 完全缺失 |
| Task 创建与查询 | 完全缺失。正式唯一身份仍受 IR-03 阻断 |
| Worker 领取与执行权 | 完全缺失。正式执行权仍受 IR-07 阻断 |
| Pipeline | 完全缺失。模块没有实现，也不依赖调度器 |
| Snapshot 与契约解析 | 解析逻辑已实现。快照读取、第一次快照选择和调用链完全缺失。第一次选择受 IR-04 阻断 |
| 字段提取、金额转换、质量检查 | 完全缺失 |
| Writer 与 Doris | 完全缺失。ADR-0005 只接受 Doris 4.0.7 服务器版本，客户端版本未锁定 |
| 查询验证 | 完全缺失 |

Admin 静态演示清单是四条固定文案，不是这条链路的查询结果。

## 2. 最短调用链

启动本身已经具备，不必再做一轮启动改造。Doris 客户端版本还没有被接受，所以第一条能运行的闭环先到 MySQL 中的 DEV_ONLY 记录为止；Doris 放在客户端版本获得单独授权之后。

已有类只复用，不改语义：`FictionalMessageSource` 提供带标记的字节，`ContractVersionUniqueness` 只在夹具已经给出快照条目后调用。`InMemoryRawMessageStore` 不作为这条链路的存储。

获得授权后的调用关系：

1. `app-worker` 在 DEV_ONLY 开关打开时调用 `FictionalMessageSource`。无标记的字节直接拒绝。
2. `platform-messaging` 把这些字节发给本地 Kafka。这里放 Kafka 客户端，不放 JDBC。
3. 同一个 Worker 消费后，用 Worker 自己的 MySQL 访问把原文和一条 DEV_ONLY Task 写入本地 MySQL 8.0.46。Task 标识只来自夹具，代码拒绝把它当成来源契约里的稳定事件身份。
4. Worker 用条件更新领取这一条夹具任务，然后调用 `platform-pipeline` 的单次执行入口。Pipeline 只依赖 `platform-common` 和 `platform-core`。
5. 夹具带齐快照条目时才调用 `ContractVersionUniqueness`。缺快照就停在未执行，不选择快照，不提取金额。
6. `platform-transform` 先做原字段直通。`platform-quality` 这一条链路不新增规则。
7. `platform-writer` 在 Doris 客户端版本确定前不写 Doris，只返回未写入。

Core 不加入 Spring、JDBC 或 Kafka。不新增模块。Admin 不执行这条链路。

## 3. 连续任务

这五步都还没有授权。本文件不批准它们。

### E2E-A 本地 Kafka 与 MySQL 连接

- 模块：`platform-messaging`、`app-worker`。
- 新增：messaging 中的 Kafka 发送与消费配置；Worker 中的 MySQL 连接配置和一次连通检查。驱动和 `spring-kafka` 使用 Spring Boot BOM 已管理的坐标，子模块不另写版本。
- 调用链：Worker 启动后分别检查 Kafka 与 MySQL。任一不可用就失败并退出，不记成成功。
- 前置：一份明确允许加入这两个依赖、并且只连接本地实例的授权。
- 不做：建表、消费业务消息、Doris、把检查接到 Admin。
- 验收：本地 MySQL 8.0.46 和 Kafka 都可达时检查通过；缺任何一个时进程失败。配置使用占位符，不写真实口令。
- 测试：`./mvnw -pl app-worker -am test`，再加一次本地连通检查。本轮没有跑。
- 授权：需要。

### E2E-B DEV_ONLY 原文经 Kafka 写入 MySQL

- 模块：`platform-connector`、`platform-messaging`、`app-worker`。
- 新增：Worker 侧的 DEV_ONLY 发布与消费，以及只接受带 `DEV_ONLY` 和「虚构数据」字节的原文写入。本地表结构与这次授权一起给出，不当作已批准的物理模型。
- 调用链：`FictionalMessageSource` → Kafka → Worker 消费 → MySQL 原文行。
- 前置：E2E-A。IR-15 仍未选定正式介质，所以这张表只保存夹具字节。
- 不做：正式 `rawPayloadRef` 唯一约束，不替换内存存储的语义，不把无标记报文写入。
- 验收：夹具报文可在 MySQL 查到，且带 DEV_ONLY 标记。去掉标记的字节被拒绝，库中无新行。
- 测试：模块测试覆盖拒绝无标记字节；本地链路用一条夹具报文核对。本轮没有跑。
- 授权：需要。范围只含本地 DDL 和 DEV_ONLY 写入。

### E2E-C DEV_ONLY Task 与一次领取

- 模块：`app-worker`。Pipeline 只接收已经领到的夹具任务。
- 新增：夹具 Task 行，以及按该行状态做条件更新的领取。标识写在夹具里。
- 调用链：原文行 → 夹具 Task → 条件更新领取。
- 前置：E2E-B。
- 不做：正式 Create-or-Get、来源稳定事件身份、跨 Worker 执行权、终态和审计。
- 验收：同一条夹具任务只有一次领取成功。无 DEV_ONLY 标记时不建 Task。代码和查询结果都标明这不是正式任务身份。
- 测试：并发条件更新测试，加上一次本地查询。本轮没有跑。
- 授权：需要。授权文本必须写明不关闭 IR-03 和 IR-07。

### E2E-D 夹具快照上的解析与直通

- 模块：`platform-pipeline`、`platform-transform`、`app-worker`。
- 新增：Pipeline 的单次执行入口，以及 Worker 装配。转换先返回原字段。
- 调用链：已领取夹具任务 → Pipeline → 仅当夹具含快照条目时调用 `ContractVersionUniqueness` → 直通转换。
- 前置：E2E-C，以及夹具中的快照条目。没有条目则停在未执行。
- 不做：第一次快照选择、金额规则、质量表、覆盖已有解析结果。
- 验收：夹具条目唯一时得到该版本；条目缺失或冲突时不提取。直通结果等于输入字段。
- 测试：`./mvnw -pl platform-pipeline,platform-transform,app-worker -am test`。本轮没有跑。
- 授权：需要。这是 Pipeline 业务代码的最小入口，不是 Stage C 的整体准入。

### E2E-E Doris 写入与查询

- 模块：`platform-writer`、`app-worker`。
- 新增：在客户端版本被单独接受之后，写入一行带 DEV_ONLY 标记的结果，并按该标记查询。
- 调用链：直通结果 → Writer → Doris 4.0.7 → 按标记查询。
- 前置：E2E-D，加上 Doris 客户端坐标的单独决定。
- 不做：在版本未定前引入客户端，不把 Doris 当任务库，不写正式金额结果。
- 验收：查询只返回这条带标记的结果。未授权时 Writer 返回未写入，任务不假装成功。
- 测试：Writer 模块测试，加一次本地 Doris 查询。本轮没有跑。
- 授权：需要，而且与前面四步分开。

## 4. 现在可以做什么

现在可以写的代码：没有。现有授权只覆盖启动核对、契约版本解析、确认键解析、虚构来源、进程内原文存储和 Admin 静态清单。把其中任何一个接到 Kafka、MySQL 或 Pipeline 上都超出授权。

可以直接复用的实现：两个启动类和 `StartupChecklist`；`FictionalMessageSource` 的现成标记字节；`ContractVersionUniqueness` 的已有解析。内存存储不进入正式链路。

当前最大阻塞是：没有 Kafka、MySQL、Doris 的客户端和连接；没有 Task、原文和 Pipeline 的实现；正式任务身份、执行权和第一次快照选择仍不能用夹具冒充。

第一批在 E2E-A 获得授权后改这些位置：`platform-messaging/pom.xml` 增加 BOM 管理的 Kafka 依赖，并新增该模块的发送与消费配置；`app-worker/pom.xml` 增加 BOM 管理的 MySQL 驱动；Worker 的本地配置使用占位符；新增 Worker 侧连通检查。不改 Core，不改 Pipeline，不建表。

验证：依赖缺失或服务不可达时 Worker 启动失败；两者都可达时检查通过。Admin 仍按现有配置单独启动，不连接这两项服务。

## 5. 需要单独授权的事项

最小授权按 E2E-A 到 E2E-E 分开。每一份都要写明只用于本地 DEV_ONLY，不关闭 IR-03、IR-04、IR-07、IR-15，不接受物理模型，不批准正式 Create-or-Get，也不等于 Stage C 已经启动。

Doris 客户端版本、本地表结构、Pipeline 执行入口各自再要一份授权。没有这些授权时，不添加依赖，不写 DDL，不把虚构来源接到 Worker 的运行入口。
