# 06-03-03-06 MTX Data Platform V2 金额处理 Pipeline 集成与端到端处理设计

本文把已经冻结的金额提取、观测、匹配、标准化、未识别和冲突处理收成一条可重试的处理链路。它不编写 Java，不执行 DDL，也不发布生产映射。

依据：

- [06-03-01 金额领域模型](06-03-01%20MTX%20Data%20Platform%20V2%20多平台订单金额领域模型设计.md)
- [06-03-02-01 标准金额类型](06-03-02-01%20MTX%20Data%20Platform%20V2%20标准金额类型体系与编码规范.md)
- [06-03-02-02 四平台字段映射](06-03-02-02%20MTX%20Data%20Platform%20V2%20四平台原始金额字段与标准金额类型映射设计.md)
- [06-03-02-03 映射规则配置与版本](06-03-02-03%20MTX%20Data%20Platform%20V2%20金额映射规则配置模型与版本管理设计.md)
- [06-03-03-01 原始金额提取模型](06-03-03-01%20MTX%20Data%20Platform%20V2%20原始金额提取模型设计.md)
- [06-03-03-02 金额观测记录模型](06-03-03-02%20MTX%20Data%20Platform%20V2%20金额观测记录模型设计.md)
- [06-03-03-03 金额映射匹配引擎](06-03-03-03%20MTX%20Data%20Platform%20V2%20金额映射匹配引擎设计.md)
- [06-03-03-04 金额标准化与约束校验](06-03-03-04%20MTX%20Data%20Platform%20V2%20金额标准化与约束校验设计.md)
- [06-03-03-05 未识别金额与冲突金额处理](06-03-03-05%20MTX%20Data%20Platform%20V2%20未识别金额与冲突金额处理设计.md)

架构依据是 [ADR-0002](../../../decisions/0002-v2-module-architecture.md)、[ADR-0003](../../../decisions/0003-unmapped-conflict-observation.md) 和 [调度边界](../../../architecture/scheduling.md)。

| 标记 | 含义 |
| --- | --- |
| 已确认事实 | 上游设计或 ADR 已经写明 |
| 设计决定 | 本文为了把各阶段接成一条链路而作出的选择。它不改写已冻结文档 |
| 待确认 | 上游没有给出执行状态机、物理索引或计算规则正文 |

本文是设计初稿，待架构审查。它不代表 Pipeline、任务状态或生产映射已经存在。当前标准类型 38 个是 `DRAFT`，`AMT.FEE.DELIVERY` 是 `RETIRED`，0 个是 `ACTIVE`。已确认生产映射数量是 0。文中的 `MAPPED`、标准化和计算都是规则，不是已经发生的生产处理。测试都没有执行。

## 1. 目标、范围和非目标

目标是：一次金额处理使用同一份原文、同一契约版本和同一个 `snapshotId`，按字段形成不可变观测，再按已冻结门禁决定是否标准化和是否计算。重试返回已有结果。换快照才是另一条观测。

范围内：处理顺序、执行上下文、三条映射分支、字段与任务的失败边界、幂等和重放、模块端口、持久化边界、追踪和端到端测试预期。

范围外：不实现 Java，不编写物理表和 DDL，不重做 Kafka 协议，不实现通用 DAG 引擎，不重新定义金额口径，不发布未经审核的生产映射，不回灌生产数据，不新增 Maven 模块，不修改已冻结的领域对象、类型编码、映射模型、观测模型和状态定义。

## 2. 上游基线

已确认事实：

- 执行侧 `mappingStatus` 只有 `MAPPED`、`UNMAPPED`、`CONFLICT`。Pipeline 的任务状态、步骤状态和字段处理状态不是这三种状态，不能混用，也不能因此新增第四种映射执行状态。
- `MAPPED` 只在唯一映射命中且资格是 `ALLOWED_IF_CONTRACT_DEFINES` 时成立。`ALLOWED_AS_UNIDENTIFIED` 的执行状态是 `UNMAPPED`，并保留该版本的资格和类型编码。`NOT_UNTIL_CONFIRMED`、`DISALLOWED`、`CONFLICT_ONLY` 是任务级快照完整性异常。
- 没有命中时是 `UNMAPPED`。恰好一条冲突决定且没有映射窗口同时命中时是 `CONFLICT`。两条映射、映射加冲突、两条冲突决定都是任务失败。不挑选，不降成 `UNMAPPED`，不改读最新快照。
- 只有 `fieldCode = OK` 进入正常匹配并可以形成观测。`TYPE_MISMATCH`、`PATH_NOT_UNIQUE`、`RAW_TEXT_UNRETAINABLE` 不形成正常观测。对象或数组落在标量金额路径上时，`presence = NON_SCALAR`，`fieldCode = TYPE_MISMATCH`，不求和，不取第一项。
- 整份阻断不生成观测。阻断码包括 `PAYLOAD_MALFORMED`、`PAYLOAD_REF_INVALID`、`CONTRACT_VERSION_UNRESOLVED`、`CONTRACT_IDENTITY_MISMATCH`、`CONTRACT_PATHS_UNDEFINED`、`CONTRACT_PATH_INTEGRITY_FAILED`、`CONTRACT_PATHS_CORRUPT`、`CONTRACT_VERSION_HASH_MISMATCH`。`CONTRACT_PATHS_EMPTY` 是成功的空清单，观测条数为 0。
- 观测身份是 `rawPayloadRef + snapshotId + contractVersionId + fieldPath`。不含租户，不含业务对象，不含解析器版本。同一身份重试返回原观测，不覆盖 `mappingStatus`、`presence` 和 `rawText`。
- `RawAmountValue` 是内存过程对象。持久化的字段事实写在观测上。再次提取只用于核对。
- `UNMAPPED` 和 `CONFLICT` 不进入标准化，也不进入计算。`MAPPED` 不表示标准化成功。标准化成功不是计算授权。
- 冲突观测沿用 06-03-03-05：`conflictDecisionId` 指向唯一决定；`mappingId`、`mappingVersion`、`proposedType`、`mappingEligibility`、`evidenceRef` 为空；`semanticStatus = 无法验证`；`evidenceStatus = NONE`。不抄录候选的语义或证据。
- 冲突决定创建后不可修改。封存后不能追加证据。新证据另建决定。窗口重叠时发布失败。新快照不修改旧观测。
- 历史 `RETIRED` 映射窗口仍按 `receivedAt` 的左闭右开区间命中。类型在该快照目录中已是 `RETIRED` 时，不生成新的标准化结果，也不把类型改回 `ACTIVE`。
- 转换策略只有 `NO_CONVERT`。`normalizationRuleVersion`、`conversionRuleVersion` 和 `parserVersion` 是三个字段。`NO_CONVERT` 不是标准化规则版本号。版本都未分配时，同一观测在“不转换、转换版本为空”这一档至多一个逻辑结果。
- `platform-pipeline` 只依赖 `platform-common` 和 `platform-core`。它调用 core 端口，不依赖 Transform、Connector、Quality、Writer 的实现类，也不依赖 Quartz 或 DolphinScheduler。Worker 负责装配。

已知差异，本文不修改上游：外部版本“未提供”对应零个或多个修订时，06-03-03-01 阻断提取，06-03-02-03 第 8 节第 2 步写 `UNMAPPED`。提取已经阻断时，本文不生成观测。

## 3. 总体架构

### 3.1 调用顺序

已确认事实：06-03-03-03 的调用顺序是 Worker 固定 `snapshotId`，Pipeline 调用提取端口，整份阻断时不调用匹配，然后调用匹配端口，再由 Writer 写出允许形成的观测。06-03-03-04 在匹配之后做标准化，计算不在标准化这一步。

设计决定：新观测的匹配在第一次写出之前完成。写出的观测已经带有当次 `mappingStatus`。同一身份再次进入时直接返回原观测，不再匹配一遍去改写它。标准化和计算都读取已经形成的观测，不回写观测。

```text
app-worker 固定原文、契约版本、receivedAt 和 snapshotId
        |
        v
platform-pipeline 只调用 platform-core 端口
        |
        v
提取实现读原文和契约路径，得到内存中的字段结果
        |
        +-- 整份 payloadCode 不是 OK：任务阻断，观测条数为 0
        |
        v
对每个 fieldCode = OK 的具体路径
        |
        +-- 同一观测身份已存在：返回原观测，不覆盖
        |
        +-- 尚不存在：按固定快照匹配，再一次写出观测
        |
        +-- UNMAPPED 或 CONFLICT：保留观测，不标准化，不计算
        |
        +-- MAPPED：再走 06-03-03-04 全部门禁
                |
                +-- 未通过：没有 NormalizedAmount
                |
                +-- 通过：写出 NormalizedAmount
                        |
                        +-- 没有生效计算规则：没有 AmountCalculation
                        |
                        +-- 有生效规则且输入满足：另一次计算
        |
        v
汇总字段结果和任务结果
```

三种失败码留在字段结果上，不进入上面的匹配写出。同一报文的其他成功字段继续。

### 3.2 分层职责

| 层 | 负责 | 不负责 |
| --- | --- | --- |
| Pipeline | 固定上下文、按顺序调用端口、区分字段结果和任务阻断、重试时沿用原快照 | 提取规则、匹配算法、单位换算、选择冲突候选、直接写具体表 |
| 提取 | 按契约路径得到 `presence`、`rawText` 和 `fieldCode` | 判断业务含义、选择类型、转换单位、计算营业额 |
| 观测 | 按冻结身份保存不可变事实 | 用后一次匹配覆盖前一次 |
| 匹配 | 用固定快照和 `receivedAt` 得到三种执行结果之一，或明确的任务失败 | 审核、发布、选择候选、标准化 |
| 标准化 | 执行 06-03-03-04 的全部门禁 | 放宽门禁，或把失败改成 0 |
| 计算 | 只使用已经标准化且被生效规则允许的输入 | 直接使用 `RawAmountValue`，或套用旧系统营业额公式 |

`platform-quality` 不修改金额事实、匹配结果或语义。`platform-connector` 只提供已经取到的报文和来源，匹配不反向调用它。`platform-writer` 写出已经形成的观测和通过门禁的结果，不推断单位，不把未识别改成 0，不把冲突改成某个类型。`app-admin` 管理契约、映射、审核和发布，不执行这条处理链路，也不依赖 Worker。

## 4. 执行上下文

设计决定：一次任务开始时建立一份执行上下文，后续阶段只读这份上下文里的原文引用、契约版本、`receivedAt` 和 `snapshotId`。任何步骤都不得改读当时最新的另一份快照。

建议包含这些逻辑字段。它们不是已经存在的 Java 类，也没有全部进入观测：

| 字段 | 用途 |
| --- | --- |
| `processingId` | 一次金额处理的执行标识。算法未设计 |
| `taskId` | 所属任务。算法未设计 |
| `taskAttemptId` | 一次尝试。重试可以更换它 |
| `rawPayloadRef` | 原文引用。进入观测身份 |
| 来源平台、接口、消息类型、事件号、消息号 | 属于 `AmountSource`。空消息类型保留空字符串 |
| `contractVersionId`、`contractBodySha256` | 本次固定的内部契约版本和正文哈希 |
| `receivedAt` | 唯一参与窗口判断的业务时间 |
| `snapshotId` | 本次固定的映射快照 |
| `parserVersion` | 实现前置事项。不进入观测身份，也不在这里设计版本体系 |
| `businessObjectType`、`businessObjectId` | 可选。来源未设计时为空。不从金额字段推断。不进入观测身份 |

`tenantId` 不进入上下文的必填项，也不进入观测身份。06-03-01 已说明租户模型未定义。本文不单造租户字段。

创建顺序：确定原文引用和来源，解析并校验契约版本，固定 `receivedAt`，读取当时最新且完整性校验通过的快照，再开始提取。契约版本不能唯一确定时按 06-03-03-01 阻断，不继续提取。快照读不到或哈希不符时任务阻断，不临时换一份快照。

重试复用 `rawPayloadRef`、`contractVersionId`、`contractBodySha256`、`receivedAt` 和 `snapshotId`。可以有新的 `taskAttemptId`。已经固定的 `snapshotId` 在重试时不变。

## 5. 端到端阶段

### 5.1 读取原文

已确认事实：原文通过 `rawPayloadRef` 定位。取不回是 `PAYLOAD_REF_INVALID`，任务阻断，观测条数为 0。处理不修改原文，也不在这一步提取金额。

设计决定：上下文中的来源身份与原文记录不一致时，同样阻断，不换成另一份报文继续。

### 5.2 契约

已确认事实：使用快照中的 `contractVersionId`。正文哈希不符则阻断。重试不切换契约版本。不能在多个修订里自动挑一个。

### 5.3 提取

已确认事实：只按契约声明的金额路径提取。七种 `presence` 都保留：`ABSENT`、`PRESENT_NULL`、`EMPTY_STRING`、`NUMERIC_ZERO`、`NUMERIC_TEXT`、`OTHER_TEXT`、`NON_SCALAR`。前四种和后两种里的数字文本、其他文本，在字段码为 `OK` 时都可以进入观测。非标量不是金额。不转换，不求和。`parserVersion` 的注册和兼容仍未设计。

### 5.4 观测

已确认事实：观测保存具体 `fieldPath`、`presence`、`rawText`、`declaredUnitText`、来源、`snapshotId`、`contractVersionId` 和 `contractBodySha256`。形成时 `Money` 为空。`extractionFieldCode = OK`。创建不表示已经标准化。

设计决定：尚不存在该身份时，匹配结果和观测同一次写出。已存在时返回原观测。后续映射发布不回写这条记录。

### 5.5 匹配

已确认事实：只读任务上的 `snapshotId`。窗口是左闭右开。`ACTIVE` 和 `RETIRED` 条目都参加。`receivedAt` 选窗口，`processedAt` 不选窗口。`candidateDisposition` 不读取。草稿不参加。

| 情况 | 结果 |
| --- | --- |
| 没有命中 | `UNMAPPED` |
| 唯一命中且资格是 `ALLOWED_AS_UNIDENTIFIED` | `UNMAPPED`，保留资格和类型编码 |
| 唯一命中且资格是 `ALLOWED_IF_CONTRACT_DEFINES` | `MAPPED` |
| 唯一命中且资格是三种禁止值之一 | 任务失败。不写成 `MAPPED`，不降成 `UNMAPPED` |
| 恰好一条冲突决定，没有映射同时命中 | `CONFLICT` |
| 多条映射、映射加冲突、多条冲突决定 | 任务失败 |

### 5.6 标准化

已确认事实：只有 `MAPPED` 进入。门禁是 06-03-02-03 第 13 节的发布条件，加上单位、币种、字面量和 `NO_CONVERT`。语义必须是 `部分确认`，且没有未排除的第二种解释；含义只限于当前契约写明的字段。其余五种语义、只靠 `E4`/`E5`/`E6`、或三种禁止资格出现在 `MAPPED` 上，是任务级快照完整性异常。类型必须在该快照封存的目录里是 `ACTIVE`。不默认值，不截断，不取绝对值，不四舍五入。失败不生成 `NormalizedAmount`，观测保持原样。

当前没有已发布映射，所以现在不会写出标准化结果。

### 5.7 计算

已确认事实：标准化成功不是计算授权。没有生效计算规则时不生成 `AmountCalculation`。旧代码里的四平台营业额公式不是 V2 已批准规则。

设计决定：计算只读取已经形成的 `NormalizedAmount` 和一条已经发布的计算规则。规则必须写明输入类型、聚合方式、资金方向、正负号、空值、重复输入和精度。未写明这些内容时不执行。当前没有这样的规则，因此当前不会生成计算结果。

禁止把多个字段直接相加当成通用处理。下面这种写法不是 V2 规则：

```java
amounts.stream()
    .map(Amount::getValue)
    .reduce(BigDecimal.ZERO, BigDecimal::add);
```

### 5.8 汇总

设计决定：汇总同时留下报文结果、每个具体路径的提取码、是否形成观测、`mappingStatus`、是否标准化、是否计算、失败原因、`snapshotId` 和 `contractVersionId`。字段条数、观测条数和标准化条数不是营业额、实付或成交额。

## 6. 三条分支

### 6.1 MAPPED

```text
观测第一次写出时 mappingStatus = MAPPED
        |
        v
06-03-03-04 全部门禁
        |
        +-- 字段级未通过：保留观测，没有标准化结果
        |
        +-- 任务级完整性异常：任务失败，不降成字段失败
        |
        v
NormalizedAmount
        |
        +-- 没有生效计算规则：保留标准化结果，没有计算结果
        |
        v
AmountCalculation
```

`MAPPED` 只表示这次引用了哪一版映射。

### 6.2 UNMAPPED

保留原文路径、契约版本和 `snapshotId`。不生成标准化结果，也不生成计算结果。后续补映射只进入新快照，只影响之后新开始的任务。历史观测不覆盖。`UNMAPPED` 不是技术故障，不因此自动重试。

### 6.3 CONFLICT

观测字段按第 2 节的冻结赋值。决定上的候选和证据行通过 `conflictDecisionId` 读取。不选择候选，不生成标准化结果，也不生成计算结果。`CONFLICT` 不是技术故障，不因此自动重试。封存后的新证据按 06-03-03-05 另建决定。

## 7. 多个金额字段

已确认事实：一份报文可以有多条声明路径。每条路径有自己的观测身份。不能因为它们属于同一张订单就合成一条观测。一份原文是否对应多个业务对象，上游仍待确认。本文不按业务对象把一份原文拆开。

设计决定：这些路径共享同一份执行上下文和同一个 `snapshotId`，处理结果彼此独立。

| 级别 | 例子 | 处理 |
| --- | --- | --- |
| 字段级合法结果 | `MAPPED`、`UNMAPPED`、`CONFLICT` | 按第 6 节继续。不把后两种写成任务失败 |
| 字段级失败 | 三种提取失败码；单位、币种或字面量未通过标准化 | 该字段没有相应结果。其他字段继续。不把失败写进观测 |
| 任务级阻断 | 原文不可读、契约不能唯一确定、快照损坏、多重命中、`MAPPED` 上的非法资格或非法语义 | 停止尚未处理的字段。不挑选，不换快照 |

设计决定：一个字段失败，不删除其他字段已经写出的观测和标准化结果，也不覆盖它们。任务级阻断同样不改写已经写出的领域结果。06-03-03-04 和 06-03-03-05 都写明当时没有撤销事务。本文只规定不删除、不覆盖，不声称数据库已经能做补偿事务。任务状态要能看出还有字段未完成或任务已阻断。物理检查点仍待确认。

多个字段能否相加，只由第 5.7 节的已发布计算规则决定。

## 8. 幂等、重试和重放

观测身份沿用第 2 节。同一身份返回原观测，不改金额，不改 `mappingStatus`，不重新选择映射。

标准化逻辑身份沿用 06-03-03-04：观测身份加上 `normalizationRuleVersion` 和 `conversionRuleVersion`。同一档返回已有结果。新的标准化规则版本或转换规则版本形成新结果。解析器版本变化不重新解析、不覆盖已有观测和已有标准化结果。物理唯一索引未实现。

设计决定：计算若将来发生，逻辑身份至少包括参与的标准化结果、计算规则标识和计算规则版本。输入集合要有稳定的成员和顺序，不能因为遍历顺序不同就变成另一次计算。同一身份重试不产生第二条有效结果。哈希算法、排序和物理唯一约束都还没有，不能写成已经冻结。当前没有计算规则，这条身份不会被生产结果占用。

| 情况 | 处理 |
| --- | --- |
| 原文或存储暂时不可用 | 原上下文、原快照重试 |
| 契约版本缺失，后来补上 | 新任务。不把旧的阻断尝试改写成观测 |
| 契约哈希不符、快照完整性异常 | 阻断。不换快照 |
| 多重命中 | 修复配置并发布新快照后，由新任务处理。旧观测不改 |
| 标准化约束失败 | 保留观测。规则或数据修正后按新的规则版本或新任务处理 |
| `UNMAPPED`、`CONFLICT` | 不作为技术异常自动重试 |

重试使用原处理身份和原快照。重放是另一次任务，调用方明确指定另一份快照，因此得到新的观测身份。新观测可以写入调用方给出的 `replayOfObservationId`。系统不按订单号搜索旧观测。原观测、原快照和原匹配结果都保留。

配置命令的幂等表不写入观测、标准化结果或计算结果。

## 9. 持久化与事务边界

设计决定：领域结果和执行管理信息分开保存。任务状态变化不覆盖观测、标准化结果或计算结果。

| 内容 | 边界 |
| --- | --- |
| 执行上下文 | 独立于单条观测。保存本次固定的快照和契约 |
| 观测 | 按观测身份一次写出。匹配结果属于这次写出 |
| 标准化结果 | 按标准化逻辑身份。没有观测依据则不能写 |
| 计算结果 | 按第 8 节的计算身份。没有标准化输入则不能写 |
| 任务汇总 | 单独更新。不回写领域结果 |

不要求一份报文的全部金额字段共用一个长事务。每个具体路径的领域结果是独立的幂等单元。这是集成边界，不是已经实现的事务。

必须满足：标准化结果能指回观测；计算结果能指回标准化输入和规则版本；`UNMAPPED` 和 `CONFLICT` 没有标准化结果；同一逻辑身份没有第二条有效结果；重试不更换 `snapshotId`。

待确认：三类结果的物理唯一索引、哈希字段、并发插入后的读取、多 Worker 竞争、隔离级别、跨模块事务和失败后的检查点。这些都还不是现有能力。

## 10. 模块与端口

已确认事实：模块只有 ADR-0002 的十个。`platform-pipeline` 的允许依赖是 `platform-common` 和 `platform-core`。Worker 可以依赖 connector、transform、quality、writer 和 pipeline，并负责装配。Admin 与 Worker 互不依赖。

设计决定：不采用“Pipeline 直接依赖 Connector、Transform、Quality、Writer”的依赖图。那会违反 ADR-0002。Pipeline 只调用 core 里的端口。实现类由 Worker 装配。

| 模块 | 本阶段职责 |
| --- | --- |
| `platform-core` | 上下文、观测、匹配、标准化和计算的端口与模型 |
| `platform-transform` | 实现提取、匹配和标准化门禁。不写存储，不选择候选 |
| `platform-pipeline` | 编排端口调用和任务汇总 |
| `platform-writer` | 持久化已经形成的领域结果 |
| `platform-quality` | 不改金额语义 |
| `platform-connector` | 提供已经取到的报文 |
| `app-worker` | 装配并执行。装载任务上的快照 |
| `app-admin` | 配置、审核和发布。不跑这条链路 |
| `platform-messaging` | 不在本文重做消息协议 |
| `platform-common` | 无业务框架的公共类型 |

下面的方法只说明端口职责，不是仓库里已经存在的 Java 接口：

```java
AmountExtractionResult extract(AmountExtractionRequest request);
ObservedAmountResult createOrGet(AmountObservationCommand command);
AmountMappingMatchResult match(AmountMappingMatchRequest request);
AmountNormalizationResult normalize(AmountNormalizationRequest request);
AmountCalculationResult calculate(AmountCalculationRequest request);
AmountProcessingResult execute(AmountProcessingCommand command);
```

`createOrGet` 在身份已存在时返回原观测。身份不存在时，命令里必须已经带有本次匹配结果，避免先插入一条空状态再更新。`execute` 属于 Pipeline 对 Worker 暴露的入口，内部仍只调用 core 端口。

调度只触发 Worker。Quartz API 不进入 Pipeline、Transform 或 Writer。本文不实现 DolphinScheduler，也不新增调度模块。

## 11. 执行状态

设计决定：任务状态、步骤状态、字段处理情况和 `mappingStatus` 分属不同记录。字段记录至少说明提取是否成功、是否形成观测、匹配结果、标准化有没有执行、标准化是否成功、计算有没有执行、计算是否成功，以及失败原因。这些字段记录不替代 `mappingStatus`。

任务状态使用下面这组执行管理取值。它们不是映射状态，也还没有实现：

| 取值 | 含义 |
| --- | --- |
| `PENDING` | 等待执行 |
| `RUNNING` | 正在执行 |
| `PARTIAL_SUCCESS` | 已有字段完成，同时存在字段级失败或尚未完成的字段 |
| `SUCCESS` | 本次任务要求的字段步骤都已结束，且没有任务级阻断 |
| `FAILED` | 任务级阻断 |
| `RETRY_WAITING` | 等待按原上下文重试 |
| `CANCELLED` | 已取消 |

`UNMAPPED` 和 `CONFLICT` 是合法业务结果。只有它们时，任务不因此进入 `FAILED`。`SUCCESS` 不表示每条路径都已标准化或已计算。

待确认：`PARTIAL_SUCCESS` 的终态、哪些字段失败允许自动重试，以及取消之后已经写出的领域结果如何保留。本文只规定取消和失败都不覆盖已写出的领域结果。

## 12. 追踪

设计决定：日志要能关联 `processingId`、`taskId`、`taskAttemptId`、`rawPayloadRef`、`contractVersionId`、`snapshotId`、观测身份和 `fieldPath`。普通日志不写入原始报文字节。

建议的技术计数包括提取次数、提取失败次数、新建观测次数、三种映射结果次数、标准化成功和失败次数、计算成功次数、任务失败次数、重试次数和耗时。这些不是财务指标。计数名称没有进入现有系统。

审计链路是：原文、契约版本、映射快照、观测、标准化结果、计算结果。后一步必须能指回前一步和所用规则版本。`UNMAPPED` 与 `CONFLICT` 的链路停在观测，并保留快照和契约。

## 13. 端到端测试

这些测试没有执行。涉及 `MAPPED`、标准化或计算的行都假设将来存在相应的已发布规则。当前生产映射数量是 0，当前也不会先通过标准化。

| 编号 | 输入 | 设计预期 | 阻断任务 | 当前执行 |
| --- | --- | --- | --- | --- |
| E2E-001 | 一个 `fieldCode = OK` 的字段，快照无命中 | 一条 `UNMAPPED` 观测。`Money` 为空 | 否 | 未执行 |
| E2E-002 | 假设唯一映射资格为 `ALLOWED_IF_CONTRACT_DEFINES`，标准化门禁全部通过 | 保留观测，并形成 `NormalizedAmount`。含义只限于该契约写明的字段 | 否 | 未执行。现在没有这条映射 |
| E2E-003 | E2E-002 之外还有一条生效计算规则，输入满足该规则 | 形成 `AmountCalculation` | 否 | 未执行。现在没有计算规则 |
| E2E-004 | 同一报文多条 `OK` 路径 | 每条路径独立观测。不合并 | 否 | 未执行 |
| E2E-005 | 没有落入窗口的映射或冲突 | `UNMAPPED`。不标准化，不计算 | 否 | 未执行 |
| E2E-006 | 唯一命中 `ALLOWED_AS_UNIDENTIFIED` | `UNMAPPED`，保留资格和类型编码 | 否 | 未执行 |
| E2E-007 | 恰好一条已发布冲突决定 | `CONFLICT`，字段按 06-03-03-05 第 5.2 节。不标准化 | 否 | 未执行。现在没有这样的决定 |
| E2E-008 | 把 `UNMAPPED` 送入标准化 | 不生成标准化结果 | 否 | 未执行 |
| E2E-009 | 把 `CONFLICT` 送入标准化 | 不生成标准化结果 | 否 | 未执行 |
| E2E-010 | 声明路径缺失 | `presence = ABSENT`，`fieldCode = OK`，可以形成观测 | 否 | 未执行 |
| E2E-011 | 声明路径为 null | `PRESENT_NULL`，`fieldCode = OK` | 否 | 未执行 |
| E2E-012 | 数字 0 | `NUMERIC_ZERO`，`fieldCode = OK`。不是缺失 | 否 | 未执行 |
| E2E-013 | 标量路径上是对象 | `NON_SCALAR`，`TYPE_MISMATCH`。无观测，不求和 | 否 | 未执行 |
| E2E-014 | 标量路径上是数组 | 同 E2E-013。不取第一项，不求和 | 否 | 未执行 |
| E2E-015 | 其他文本 | `OTHER_TEXT`。字段码按 06-03-03-01。不把文本改成 0 | 否 | 未执行 |
| E2E-016 | 同一观测身份再执行 | 返回原观测 | 否 | 未执行。物理索引未实现 |
| E2E-017 | 同一任务重试 | 仍用原 `snapshotId` 和原契约版本 | 否 | 未执行 |
| E2E-018 | 同一原文改用新快照 | 新观测。可带调用方给出的 `replayOfObservationId`。旧结果不变 | 否 | 未执行 |
| E2E-019 | 标准化规则版本或转换规则版本变化 | 新的逻辑结果。旧结果保留 | 否 | 未执行。两个版本号都未分配 |
| E2E-020 | 计算规则版本变化 | 新的计算结果。旧结果保留 | 否 | 未执行。现在没有计算规则 |
| E2E-021 | 观测写出后执行中断再重试 | 返回原观测，不覆盖 | 否 | 未执行 |
| E2E-022 | 标准化写出后执行中断再重试 | 同一逻辑身份不形成第二条有效结果 | 否 | 未执行 |
| E2E-023 | 一个字段提取失败或标准化约束失败 | 其他已写出的字段结果保留 | 否 | 未执行 |
| E2E-024 | 同一字段命中多条映射、映射加冲突，或两条冲突决定 | 任务失败。不任选 | 是 | 未执行 |
| E2E-025 | 快照缺失或哈希不符 | 任务阻断。不换快照 | 是 | 未执行 |
| E2E-026 | 外部版本对应零个或多个修订 | 按 06-03-03-01 阻断。不生成观测 | 是 | 未执行 |
| E2E-027 | `MAPPED` 上出现非法资格、非 `部分确认` 的语义，或只靠 `E4`/`E5`/`E6` | 任务失败。不降成字段失败，不改 `UNMAPPED` | 是 | 未执行 |
| E2E-028 | 标准化已通过，但没有生效计算规则 | 保留标准化结果。没有计算结果 | 否 | 未执行。当前也不会先通过标准化 |

进入实现之后还需要单元测试、端口装配测试、幂等并发测试和故障重试测试。那些测试现在也不存在。

## 14. 风险与待确认

1. `parserVersion` 的定义、分配、发布、保留、兼容和重放都未设计。上下文可以留出位置，但不能声称换版本后字面量不变。
2. 观测、标准化和计算的物理唯一索引、并发插入和多 Worker 竞争都未设计。
3. `normalizationRuleVersion` 尚未分配。`NO_CONVERT` 不是这个版本号。
4. 计算规则的标识、版本、运算符、聚合、精度、空值、生效窗口和审核发布都未设计。旧系统公式不能直接使用。
5. 任务检查点、`PARTIAL_SUCCESS` 的终态和自动重试范围未定。已写出的领域结果不因后续失败而覆盖。
6. 业务对象标识的来源未定。租户模型未定。
7. “未提供”在提取阻断和匹配 `UNMAPPED` 之间的上游差异仍在。本文在提取已阻断时不生成观测。
8. 失败记录是否单独持久化，仍待存储设计。失败不写入 `ObservedAmount`。

## 15. 架构决策

| 编号 | 结论 |
| --- | --- |
| D01 | 任务开始时固定 `snapshotId` |
| D02 | 重试不读取另一份最新快照 |
| D03 | 每条具体路径独立形成观测 |
| D04 | `UNMAPPED` 不标准化 |
| D05 | `CONFLICT` 不标准化，也不选择候选 |
| D06 | `MAPPED` 先过标准化门禁，再看计算授权 |
| D07 | 一个字段失败不删除其他字段已写出的领域结果 |
| D08 | 历史观测不覆盖 |
| D09 | 新快照不修改历史观测 |
| D10 | 不自动选择冲突候选 |
| D11 | 没有已发布计算规则时不聚合金额 |
| D12 | 不新增 Maven 模块 |
| D13 | Pipeline 只依赖 common 和 core，通过端口编排 |
| D14 | 本阶段不实现 Java 和 DDL |

这些编号只存在于本文，不另建 ADR。它们不表示生产映射或计算口径已经批准。

## 16. 审查与冻结条件

本文尚未冻结。正式冻结前要确认：

1. 与 06-03-03-01 至 06-03-03-05 一致，并且不改那些文档。
2. 不新增 `mappingStatus`。
3. `UNMAPPED` 和 `CONFLICT` 不进入标准化。
4. 未经标准化和计算授权的金额不进入计算。
5. `snapshotId` 的固定和重试已经写明。
6. 字段级失败和任务级阻断已经分开。
7. 模块依赖符合 ADR-0002。
8. 物理索引、解析器版本、计算规则和任务终态仍标为待确认。
9. 测试只写设计预期，不写成已经执行。

即使本文以后冻结，也只冻结这条集成设计。它不表示代码、数据库结构、生产映射或业务计算口径已经完成。

本文没有修改 06-03-01、06-03-02-01、06-03-02-02、06-03-02-03、06-03-03-01、06-03-03-02、06-03-03-03、06-03-03-04 和 06-03-03-05。没有 Java 实现，没有 DDL，没有新表，没有新模块。
