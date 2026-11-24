# 06-03-03-01 MTX Data Platform V2 原始金额提取模型设计

本文设计原始报文里的金额字段怎样被定位、识别并交给后续观测。它不编写 Java，不执行 DDL，也不批准任何生产映射。

依据是已经冻结的四份设计：

- [06-03-01 金额领域模型](06-03-01%20MTX%20Data%20Platform%20V2%20多平台订单金额领域模型设计.md)
- [06-03-02-01 标准金额类型](06-03-02-01%20MTX%20Data%20Platform%20V2%20标准金额类型体系与编码规范.md)
- [06-03-02-02 四平台字段映射](06-03-02-02%20MTX%20Data%20Platform%20V2%20四平台原始金额字段与标准金额类型映射设计.md)
- [06-03-02-03 映射规则配置与版本](06-03-02-03%20MTX%20Data%20Platform%20V2%20金额映射规则配置模型与版本管理设计.md)

架构依据是 [ADR-0002](../../../decisions/0002-v2-module-architecture.md)。

文中的标记含义：

| 标记 | 含义 |
| --- | --- |
| 已确认事实 | 冻结文档或 ADR 已经写明 |
| 设计决定 | 本文为了完成提取而作出的选择。它不改写冻结文档 |
| 待确认 | 还没有证据或还没有存储设计 |

## 1. 设计目标与范围

目标是：一份已经保存的原始报文，按当时固定的契约版本，把声明过的金额路径读成多条 `RawAmountValue`，再交给观测。提取成功只说明“这个路径上的原始形态被记下来了”，不说明它是实付、应收、结算或经营额。

范围内：字段定位、七种原始形态、一报文多字段、数组和嵌套路径、提取所依赖的契约版本、异常是继续还是阻断、提取端口，以及它在既有 Pipeline 里的位置。

范围外：不新增标准金额类型，不把 `DRAFT` 类型或候选映射改成生效，不计算营业额、实付或结算额，不把元和分互相转换，不覆盖 `rawText`，不设计报文存储产品，不新增 Maven 模块。

## 2. 现有设计基线

已确认事实：

- V2 只有 ADR-0002 的十个模块。金额对象和端口放在 `platform-core`。`platform-pipeline` 只依赖 `platform-common` 和 `platform-core`。Worker 装配实现。
- `RawPayloadRecord` 保存不可变原文。`RawAmountValue` 保存一个路径的原始形态。`ObservedAmount` 保存一次观测。`AmountCalculation` 保存计算。三者不能合成一个字段。
- `RawAmountValue.presence` 只有 `ABSENT`、`PRESENT_NULL`、`EMPTY_STRING`、`NUMERIC_ZERO`、`NUMERIC_TEXT`、`OTHER_TEXT`、`NON_SCALAR`。
- `AmountSource.ingressMode` 只有 `CALLBACK` 和 `ACTIVE_QUERY`。订单号相同也不能把两种来源合成一次观测。
- 06-03-01 的处理顺序是：接入、保存原文、映射、计算、检查、写入。映射端口生成 `RawAmountValue` 和 `ObservedAmount`。Pipeline 不依赖 Connector、Transform、Quality、Writer 的实现。
- 06-03-02-03 规定任务固定 `snapshotId`。契约版本用 `contractVersionId` 识别。外部版本号“未提供”对应多个内部修订时，不能猜最新。重试沿用原快照。
- 06-03-02-02 的映射全部是 `DRAFT`。今天不能产生 `mappingStatus = MAPPED`。`extras`、`poi_receive_detail`、`consumer_prepays` 不能在映射阶段被拆开后加总。

trade-flow 怎样读取字段，只说明旧系统行为，不是平台官方契约，也不是 V2 提取规则。

## 3. 职责边界

| 角色 | 负责 | 不负责 |
| --- | --- | --- |
| Connector | 接收回调，或按查询取回响应，并填写平台、接口、消息类型、接入方式 | 判断字段是不是实付。不把 `CALLBACK` 和 `ACTIVE_QUERY` 合成一种来源 |
| 原始保存端口 | 先把原文不可变保存，给出 `rawPayloadRef` | 不定位金额，不生成观测 |
| 金额提取 | 按已封存契约版本中的路径，读出每个路径的 `presence`、`rawText` 和具体 `fieldPath` | 不选择标准类型，不写 `mappingStatus`，不建 `Money`，不换算，不计算 |
| 映射匹配 | 使用提取结果和任务固定的快照，生成 `ObservedAmount` | 不回平台再取一份“更合适”的报文，不改提取留下的原文 |
| 计算、质量、写入 | 只处理已经生成的观测和已批准的规则 | 不在提取失败时补一个 0 |

设计决定：提取是 06-03-01 映射端口里面的前半段，不是新的 Pipeline 箭头，也不是新模块。映射实现先提取，再匹配。Pipeline 仍然只调用 core 的映射端口。

交接给 `ObservedAmount` 的是一条字段结果：具体路径、七种形态之一、原始字面量、单位原文、字段结果码，以及同一份 `AmountSource`。观测上的类型、证据等级、语义状态和 `mappingStatus` 由匹配填写。提取阶段看不到这些字段，就不能提前做语义判断。

## 4. 核心领域模型

本文不新增业务对象。提取只使用冻结对象，并定义一个过程结果。过程结果不是第四份金额记录。

| 名称 | 作用 | 不保存成 |
| --- | --- | --- |
| `RawPayloadRecord` | 当时的原文和 `rawPayloadRef` | 金额类型 |
| `RawAmountValue` | 一个具体路径的形态和字面量 | `Money` 或标准类型 |
| `AmountSource` | 平台、接口、消息、契约版本、接入方式、原文引用、接收时间 | 数值 |
| 字段结果 | 一次提取中某个路径的成功或失败 | `ObservedAmount` |
| `ObservedAmount` | 匹配之后的观测。一个具体路径最多对应一条新观测 | 多个路径拼成的合计 |
| `AmountCalculation` | 已批准规则的计算结果 | 提取输出 |

一个 `RawPayloadRecord` 可以对应多条 `ObservedAmount`。一条 `ObservedAmount` 只指向一个 `RawAmountValue`，因此只对应一个具体路径。数值相同不能把两条观测并成一条。

`CALLBACK` 和 `ACTIVE_QUERY` 即使订单号、字段名和数字都相同，仍然是两次提取、两组观测。

## 5. 字段定位模型

评估后，真正需要的定位是：

| 属性 | 是否单独保存 | 理由 |
| --- | --- | --- |
| `platform` | 放在 `AmountSource` | 已有属性。不再另造 `sourcePlatform` |
| `sourceInterface` | 放在 `AmountSource` | 创建消息和订单查询不是同一个接口 |
| `sourceMessageType` | 放在 `AmountSource` | 查询可以空。不能把空消息类型填成另一个消息名 |
| `sourceContractId` | 放在 `AmountSource` | 同一字段名在两份契约里必须分开 |
| `sourceContractVersion` | 放在 `AmountSource` | 外部版本号。没有时写“未提供” |
| `contractVersionId` | 提取请求必须带 | 06-03-02-03 的内部版本。只靠“未提供”不能定位 |
| `fieldPath` | 放在 `RawAmountValue` | 具体路径就是字段身份。不再另造 `fieldIdentifier` 或第二套 `payloadPath` |
| `payloadFormat` | 放在契约版本上，并进入 `contract-version-v3` 正文哈希 | 整份报文一个值。当前枚举只有 `JSON`。一种契约版本只有一种格式，不必每个字段复制 |
| `extractionStrategy` | 不增加 | 现在只有“按声明路径读取节点”。没有第二种读法，单独的策略名没有职责 |

设计决定：金额路径清单和 `payloadFormat` 都是契约版本正文的一部分，不单独建提取档案。06-03-02-03 用 `contract-version-v3` 把 `payloadFormat` 和路径列表算进正文哈希，并用 `source_contract_amount_path` 保存这些路径。`payload_format` 列与哈希同时生效。只增加列、不参加哈希，不能作为本设计。`contract-version-v2` 的哈希定义不含这两项，也不能被重算成 v3。

路径可以是普通路径，例如 `total`；也可以明确写出重复标记，例如 `extras[].reduce_fee`。契约路径的语法、排序、数量和重复规则以 06-03-02-03 为准。具体下标不写入契约。没有写重复标记的数组或对象，整段按一个节点读取，不自动拆开。

已确认事实：06-03-02-02 的调查路径仍是候选，不是已封存清单，也不是生效映射。提取只接受已封存且分类为 `CONTRACT_PATHS_READY` 的 v3。五种路径结果分别是：

| 分类 | 提取怎么处理 |
| --- | --- |
| `CONTRACT_PATHS_UNDEFINED` | v2 没有路径定义。阻断。不补成空列表 |
| `CONTRACT_PATHS_EMPTY` | v3 声明了空路径列表，完整性通过。`payloadCode` 为 `OK`，字段结果为空 |
| `CONTRACT_PATH_INTEGRITY_FAILED` | v3 路径完整性校验失败。阻断。不使用部分路径 |
| `CONTRACT_PATHS_CORRUPT` | v3 路径数据缺失或损坏。阻断。不使用已读到的子集 |
| `CONTRACT_PATHS_READY` | v3 路径正常。按完整清单提取 |

`CONTRACT_PATHS_ABSENT` 不再使用。它以前同时表示 v2 和 v3 校验失败，盖住了上面这些不同结果。

## 6. 原始值识别模型

读取一个具体路径时，只按下表分类。提取不把字符串解析成 `Money`，也不改写 `rawText`。

| 看到的节点 | presence | 说明 |
| --- | --- | --- |
| 路径没有这个节点 | `ABSENT` | 不是 null，也不是 0 |
| 节点存在，值是 JSON null | `PRESENT_NULL` | 不是缺失 |
| 节点存在，值是 `""` | `EMPTY_STRING` | 字面量是空字符串 |
| 节点是数字，数值正好是 0；或节点是字符串，字面量按十进制看正好是 0 | `NUMERIC_ZERO` | 保留原始 token，例如 `0`、`0.0` 或 `"0.00"` |
| 节点是 JSON 数字，且不是 0 | `NUMERIC_TEXT` | `rawText` 保留 JSON 里的数字字面量，不改成另一种单位 |
| 节点是字符串，并且整个字符串是十进制数字 | `NUMERIC_TEXT` | 它仍然是原文。映射没有允许之前，不能变成 `Money` |
| 其他字符串 | `OTHER_TEXT` | 包括带单位的句子、空格数字、多个数字拼在一起 |
| 数组或对象 | `NON_SCALAR` | 不是一个金额标量。不取第一个元素，也不把元素加总 |

`ABSENT` 和 `PRESENT_NULL` 没有字面量。其余形态保留看到的字面量。`declaredUnitText` 只抄契约材料里的单位原文；材料没写就空着，不填 `FEN` 或 `YUAN`。

设计决定：数字 0 与字符串 `"0"` 都是 `NUMERIC_ZERO`，因为冻结定义看的是“值是数字 0”，同时要求保留 token。两者的 `rawText` 不同，所以仍然分得开。这不表示它们已经按同一个单位解析。

## 7. 多金额提取机制

提取请求先确定一个 `contractVersionId`，再只遍历该版本路径清单。报文里多出来的数字，如果没有声明路径，就留在原文里，不单独变成金额值。这样提取不会扫描全文字段并猜测哪些是金额。

每产生一个具体路径，就准备一条字段结果，随后至多一条 `ObservedAmount`：

- 普通路径命中一个节点，具体路径等于声明路径。
- 带重复标记的路径，按数组实际下标展开。`extras[].reduce_fee` 读到两个元素时，具体路径是 `extras[0].reduce_fee` 和 `extras[1].reduce_fee`。下标从 0 开始，按报文顺序编号。
- 同一数组里缺某个子字段，只给该下标记 `ABSENT`。不能写成 0。06-03-02-02 已说明，骑手元素可以没有 `reduce_fee`。
- 没有重复标记的 `extras`、`poi_receive_detail`、`consumer_prepays` 各自是一条 `NON_SCALAR`。不因为它们里面有数字就自动展开或加总。

数值相同的字段靠具体路径区分。两条路径都是 `100`，仍然是两条 `RawAmountValue`。路径相同且来自同一份原文、同一次快照，才是同一次提取，不能写第二条。

重复 JSON 键不是正常路径。解析器如果能发现同一个对象里有重复键，该声明路径记为不唯一，不挑选其中一个。解析器如果静默覆盖，V2 看不到被覆盖的值；这是待确认的解析限制，不能把它说成平台只传了一个值。

## 8. 规则版本管理

设计决定：提取不另建一套规则版本和发布批次。路径清单和 `payloadFormat` 属于 `contract-version-v3` 正文。契约材料、格式或路径变化都会改变 `materialSha256`，从而得到新的 `sourceRevision` 和 `contractVersionId`，然后再计算包含该修订号的 `bodySha256`。旧的 v2 哈希保持原定义。提取只读取已经保存的版本，不在提取时重新分配修订号。分配顺序以 06-03-02-03 为准。

提取规则和 `AmountMapping` 的区别：

| 问题 | 提取使用的契约版本 | `AmountMapping` |
| --- | --- | --- |
| 回答什么 | 这个路径在不在、节点是什么形态 | 这个路径准备指向哪个类型，以及能否成为已确认观测 |
| 版本 | `contractVersionId` | `mappingId` 和 `mappingVersion` |
| 今天的状态 | 路径清单尚未封存进契约版本 | 候选映射全部是 `DRAFT`，不能产生 `MAPPED` |

历史报文重放时，使用任务上已经固定的 `snapshotId`。从该快照的契约条目取得 `contractVersionId`，再按该行记录的 `body_encoding` 校验正文，并使用 06-03-02-03 的路径分类。只有 `CONTRACT_PATHS_READY` 才读取路径清单。`CONTRACT_PATHS_EMPTY` 成功返回空字段结果。`CONTRACT_PATHS_UNDEFINED`、`CONTRACT_PATHS_CORRUPT` 和 `CONTRACT_PATH_INTEGRITY_FAILED` 都阻断。不按 `processedAt` 选择，也不因为后来发布了新契约就改读最新清单。

外部版本号缺失或多个“未提供”同时存在时，沿用 06-03-02-03：不能猜一个修订。这次提取阻断，原文仍保留。

已确认事实：06-03-01 写明重试幂等键尚未设计。本文只给出逻辑身份，不把它写成已经实现的存储，也不新增数据库表。

提取是纯函数。相同的已保存原文、同一个 `snapshotId` 和同一条通过校验的 v3 契约，得到同一组字段结果。因此提取结果不单独落库，避免和原文、契约正文形成第二份事实。

| 身份 | 组成 | 作用 |
| --- | --- | --- |
| 提取请求 | `rawPayloadRef + snapshotId + contractVersionId` | 一次重放的输入。契约正文哈希不一致时整次失败 |
| 字段结果 | 提取请求身份 + 具体 `fieldPath` | 区分同一次提取里的多个路径 |
| 观测 | `rawPayloadRef + snapshotId + contractVersionId + 具体 fieldPath` | 与字段结果分开。观测写入有自己的唯一身份 |

并发执行同一次提取时，两边都应算出同一组字段结果。观测写入只能占住上面的观测身份一次。第二个写入发现身份已存在时，返回已有观测，不追加第二条。

提取在内存中成功、观测写入失败时，原文和契约版本都还在。重试重新计算提取，只补写尚未占用身份的观测。已经写成功的字段保持不变。部分字段失败时，成功字段可以先形成未确认观测；失败字段没有成功观测。重试不能把失败字段改写成 0，也不能覆盖已经写好的成功字段。

06-03-02-03 的幂等请求表只服务管理配置命令。Worker 提取不写入那张表。观测身份的唯一约束要等存储产品设计时落实，当前不能称为已实现。

## 9. 异常处理

阻断表示这一份报文不再产生字段结果，匹配不能补造金额。继续表示其他路径照常提取。原文一旦保存，任何异常都不删除原文。

| 情况 | 结果 | 是否阻断整份报文 |
| --- | --- | --- |
| JSON 无法解析 | `PAYLOAD_MALFORMED`。不猜测每个字段 | 阻断 |
| `rawPayloadRef` 取不回当时原文 | `PAYLOAD_REF_INVALID`。不改向最新报文 | 阻断 |
| 快照里没有这个契约版本，或“未提供”对应多个修订 | `CONTRACT_VERSION_UNRESOLVED` | 阻断 |
| 声明路径不存在 | 该路径 `ABSENT`。这是正常形态，不是故障 | 继续 |
| 节点类型不是金额标量，或与路径期望的标量不符 | 按实际节点记 `NON_SCALAR`、`OTHER_TEXT` 或其他真实形态，字段码 `TYPE_MISMATCH` | 继续 |
| 同一声明路径落到多个节点，或对象内重复键 | 该路径 `PATH_NOT_UNIQUE`，不挑选 | 继续 |
| 字符串不是十进制数字 | `OTHER_TEXT`。不截断出其中的数字 | 继续 |
| 原始字面量无法完整保存 | 字段码 `RAW_TEXT_UNRETAINABLE`。不能保存截断后的数字冒充原文 | 继续 |
| 数组或对象出现在标量路径上 | `NON_SCALAR` | 继续 |
| v2 契约没有路径定义 | `CONTRACT_PATHS_UNDEFINED` | 阻断 |
| v3 声明空路径列表，且数量、排序、内容和哈希都通过 | `CONTRACT_PATHS_EMPTY`。`payloadCode` 为 `OK`，字段结果为空 | 不阻断 |
| v3 路径重复、语法不合，或规范重算与库存哈希不一致 | `CONTRACT_PATH_INTEGRITY_FAILED` | 阻断 |
| v3 路径行数与 `path_count` 不一致，路径读不到，或 v3 缺少合法 `payloadFormat` | `CONTRACT_PATHS_CORRUPT` | 阻断 |
| v3 路径数量、内容、排序和哈希都通过，且至少一条路径 | `CONTRACT_PATHS_READY`。随后按清单产生字段结果 | 不阻断 |

完整性失败和路径数据损坏都停止这一次提取。不允许丢掉坏路径后再用剩下的路径继续。空列表是一份完整声明，字段结果为空；它和 v2 的“没有路径定义”是两种分类。

这五类问题不能混在一个字段码里：

| 问题 | 发生位置 | 提取怎么处理 |
| --- | --- | --- |
| 原始报文读不到或不是 JSON | 读取原文 | 阻断。不产生字段结果 |
| 某个路径的字面量无法完整保留 | 保存 `rawText` | 只该字段失败。不截断 |
| 字符串不是十进制，或还没有按映射规则解析 | 形态识别 | 记成 `OTHER_TEXT` 或 `NUMERIC_TEXT`。提取不解析成数值 |
| 小数位或单位不符合映射约束 | 映射 | 提取不检查。当前映射约束仍是 `NO_CONVERT` |
| 金额是否超出业务范围 | 尚未定义 | 提取不设最大金额，也不引用旧代码的位数限制 |

旧代码里“小数位大于 6 整页失败”或 `longValue()` 截断，都不是 V2 提取规则。

## 10. 接口契约

端口放在 `platform-core`。它不依赖 Spring、Kafka、JDBC 或某个平台的解析库。

输入 `AmountExtractionRequest`。接入端提供的是它实际看到的来源，封存契约提供身份正文。两边不一致就阻断，错误是 `CONTRACT_IDENTITY_MISMATCH`。提取不用接入端传来的路径覆盖封存清单。

| 字段 | 谁提供 | 怎么核对 |
| --- | --- | --- |
| `platform`、`sourceSystem`、`sourceInterface`、`sourceMessageType`、`sourceType` | 接入端按实际报文填写。消息类型可以是空字符串 | 必须等于 `contractVersionId` 所属契约家族的五段身份 |
| `sourceContractId` | 接入端给出它认为的契约 | 必须等于该版本行上的 `contractId` |
| `sourceContractVersion` | 接入端给出外部版本号。没有时写“未提供” | 必须等于该版本行上的外部版本号。它不负责在多个修订中做选择 |
| `contractVersionId` | 接入端能够提供时直接给。否则只在该快照里恰好有一个匹配修订时由读取方填入 | 必须出现在 `snapshotId` 的契约条目中，并且正文哈希校验通过 |
| `snapshotId` | 任务固定值，不是报文内容 | 不匹配最新快照。重放不替换 |
| `ingressMode` | 接入端 | 只写入 `AmountSource`。契约没有这个属性，不用它选择路径 |
| `rawPayloadRef` | 原始保存端口 | 必须取回当时原文。取回的平台和接入方式也要与请求一致 |
| `receivedAt` | 接入端 | 只随来源保存，不选择契约版本或路径 |

输出 `AmountExtractionResult`：

| 字段 | 含义 |
| --- | --- |
| `snapshotId`、`contractVersionId`、`contractBodySha256` | 证明用的是哪一版封存清单 |
| `payloadCode` | `OK`，或下面的阻断码 |
| `pathStatus` | `CONTRACT_PATHS_READY`、`CONTRACT_PATHS_EMPTY`、`CONTRACT_PATHS_UNDEFINED`、`CONTRACT_PATH_INTEGRITY_FAILED`、`CONTRACT_PATHS_CORRUPT` 之一。v2 正文哈希不一致时改为 `CONTRACT_VERSION_HASH_MISMATCH` |
| `fieldResults` | `pathStatus` 为 `CONTRACT_PATHS_READY` 时才有字段。每项含具体 `fieldPath`、`presence`、`rawText`、`declaredUnitText`、`fieldCode`。`CONTRACT_PATHS_EMPTY` 时列表为空，`payloadCode` 仍为 `OK` |

`fieldCode` 为 `OK`、`TYPE_MISMATCH`、`PATH_NOT_UNIQUE` 或 `RAW_TEXT_UNRETAINABLE`。`ABSENT`、`PRESENT_NULL`、`EMPTY_STRING` 和 `NUMERIC_ZERO` 的 `fieldCode` 仍是 `OK`，因为形态本身就是提取结果。`CONTRACT_PATHS_UNDEFINED`、`CONTRACT_PATH_INTEGRITY_FAILED`、`CONTRACT_PATHS_CORRUPT` 和 `CONTRACT_VERSION_HASH_MISMATCH` 都让 `payloadCode` 等于该码，并且 `fieldResults` 为空。读取报文使用契约上的 `payloadFormat`。当前只有 `JSON` 能进入已封存 v3。格式列与 v3 哈希不一致时，路径分类已经是 `CONTRACT_PATH_INTEGRITY_FAILED` 或 `CONTRACT_PATHS_CORRUPT`，提取不再另选一种解析器。

失败不使用一个空的 `rawText` 代替原因。阻断时 `fieldResults` 为空，`payloadCode` 说明原因。字段失败时，该字段和成功字段一起返回。

幂等：同一输入身份重复调用，返回同一组字段结果。实现不得为了重放去平台再拉一次报文。

交给匹配引擎的是这份结果和同一个 `snapshotId`。匹配可以给 `fieldCode = OK` 的结果生成观测。`TYPE_MISMATCH`、`PATH_NOT_UNIQUE`、`RAW_TEXT_UNRETAINABLE` 不能生成 `MAPPED`。今天所有候选映射仍是 `DRAFT`，因此即使提取成功，匹配也只能是 `UNMAPPED` 或 `CONFLICT`。

## 11. Pipeline 集成

位置沿用 06-03-01：接入之后先保存原文，再进入映射。提取发生在映射实现的前半段。计算只看映射已经允许的观测；提取结果不能直接进入计算。

| 模块 | 关系 |
| --- | --- |
| `platform-core` | 持有提取端口、`RawAmountValue` 和结果对象 |
| `platform-transform` | 实现提取和匹配。不写存储，不调用质量模块 |
| `platform-connector` | 只提供已经取到的报文和来源事实。提取不反向调用它 |
| `platform-quality` | 不修改提取结果。未确认的金额关系不能登记成提取错误 |
| `platform-writer` | 写出原文引用、字段结果和观测。不把失败字段改写成 0 |
| `platform-pipeline` | 只调用 core 端口。不依赖 Transform 的类 |
| `app-worker` | 把 Transform 的实现装配到提取端口和映射端口上 |

部分成功：`payloadCode = OK` 时，匹配处理全部字段结果。成功路径和失败路径都保留。整体失败：`payloadCode` 不是 `OK` 时，匹配不生成金额观测，计算不运行。Pipeline 仍保留原文和这次阻断码，不把整条 Worker 任务伪装成没有来过这份报文。

## 12. 端到端示例

这些例子只检查提取模型。字段来自 06-03-02-02 的调查清单。它们都还不是生效映射。示例里的数字若不是冻结文档中的示例，就标成假设。

### 12.1 京东查询摘录

已确认事实：`orderCreated` 消息没有金额字段。订单金额、用户应付、优惠和运费出现在另一份契约 `SC-JD-QUERY-EXCERPT`，接入方式是 `ACTIVE_QUERY`，`sourceMessageType` 为空。

假设一份查询原文含有 `orderTotalMoney`、`orderBuyerPayableMoney`、`orderDiscountMoney`、`orderFreightMoney`。提取产生四条 `RawAmountValue`，每条一个路径。四者数值即使相同也不合并。匹配不得把它们写成已确认的营业额、实付或运费类型。

### 12.2 美团已确认订单推送

已确认事实：`SC-MT-DOC-175` 有 `total`、`wmPoiReceiveCent`、`shipping_fee`、`package_bag_fee`。该页是平台推送，接入方式按 `CALLBACK` 理解。它尚未证明等于回调代码契约。

四条路径分别提取。`total` 的文档示例是 `22.1`，`shipping_fee` 是 `1.9`，`wmPoiReceiveCent` 是 `1234`，`package_bag_fee` 是 `12.9`。单位按各行原文记录：前两个单位空着，`wmPoiReceiveCent` 的单位原文是“分”，`package_bag_fee` 的单位原文是“元”。提取不把前两个换算成分。`wmPoiReceiveCent` 若是 JSON null，结果是 `PRESENT_NULL`，不用 `total` 填上。

### 12.3 淘宝闪购

已确认事实：官方消息目录没有给出嵌套金额结构。详情查询和消息 217 是两份代码调查契约，登记的是扁平名字，例如 `originalPrice`、`totalAmount`、`totalPrice`、`deliverFee`。这些名字没有被证明存在于官方报文，更没有被证明是嵌套路径。

因此本例不能构造一份“淘宝嵌套金额报文”。提取模型的结论是：路径没有进入已封存契约版本之前，不提取这些名字。若将来某版契约明确写入嵌套路径，才按第 7 节逐个标量读取。现在把它们提取出来，会把代码调查误当成官方报文。

### 12.4 富智云订单与团购

已确认事实：订单摘录 `SC-FY-ORDER-EXCERPT` 有 `orderDisAmt`、`payAmt`、`expressAmt`、`fullOrderDisAmt`、`packagePriceDisAmt`、`lunchBoxFee`。团购八个字段属于另一份契约 `SC-FY-GBV-CODE`。支付摘录上的 `payAmt` 又是第三份契约。

设计决定：一份原文只用它自己的契约路径。订单摘录不会顺带提取团购八字段，也不会把订单 `payAmt` 和支付 `payAmt` 当成同一个路径。待确认：是否存在一份官方报文同时包含订单金额和团购核销金额。没有这种契约之前，不能把两组字段放进同一次提取。

### 12.5 数值相同

假设京东查询里 `orderDiscountMoney` 和 `orderFreightMoney` 的字面量都是 `100`。结果仍是两条 `RawAmountValue`。匹配不能因为数字相同就认定优惠等于运费。

## 13. 测试场景

本文件不执行这些测试。

| 场景 | 输入 | 预期 |
| --- | --- | --- |
| 京东四个查询字段 | `SC-JD-QUERY-EXCERPT` 的四个路径都有数字 | 四条原始值。接入方式是 `ACTIVE_QUERY`。没有 `MAPPED` |
| 美团四个字段 | `total`、`wmPoiReceiveCent`、`shipping_fee`、`package_bag_fee` | 四条原始值。单位原文互不转换 |
| 淘宝嵌套 | 只有代码里的扁平字段名，没有封存路径 | 不生成这些字段的原始值，也不编造嵌套报文 |
| 富智云两组契约 | 订单摘录和团购契约各一份原文 | 两次提取，路径不串用 |
| 相同数字 | 两个路径字面量相同 | 两条原始值 |
| 缺失、null、空字符串、0 | 四个路径分别是这四种节点 | `ABSENT`、`PRESENT_NULL`、`EMPTY_STRING`、`NUMERIC_ZERO`。都不补成另一种 |
| 历史重放 | 新契约版本已发布，任务仍带旧 `snapshotId` | 仍用旧路径清单。结果不追加第二份 |
| 部分失败 | 一个路径是对象，另一个路径是数字 | 对象为 `NON_SCALAR` 且 `TYPE_MISMATCH`。数字路径仍成功。整份报文不阻断 |
| 坏 JSON | 原文不是 JSON | `PAYLOAD_MALFORMED`。原文还在，没有字段结果 |
| 引用失效 | 保存引用取不回原文 | `PAYLOAD_REF_INVALID`。不读取最新报文 |
| 非标量容器 | 声明路径就是 `extras` | 一条 `NON_SCALAR`。不自动求和 |
| 重复标记 | `extras[].reduce_fee` 有两个元素，第二个没有该字段 | 下标 0 按实际形态记录。下标 1 为 `ABSENT`，不是 0 |
| v2 契约 | 已封存 v2，哈希与 v2 定义一致，没有路径行 | `CONTRACT_PATHS_UNDEFINED`。没有字段结果 |
| v3 空列表 | `path_count = 0`，正文哈希为 `60f96aca692a7dff254fd1efd4362afe4d0f2e28529cdeea566cfd60c5523959` | `payloadCode = OK`，`pathStatus = CONTRACT_PATHS_EMPTY`，字段结果为空 |
| 路径损坏 | v3 的 `path_count` 为 2，路径表只剩 1 行 | `CONTRACT_PATHS_CORRUPT`。不提取剩下的那一条 |
| 路径完整性失败 | 库存正文哈希等于未排序向量 `013692f475dcb5b3188d4f5477dc1476b756cd893f59e7c5b7adb65b0fcf3bbd` | `CONTRACT_PATH_INTEGRITY_FAILED`。不改用排序后的部分结果继续 |
| 格式不同 | 同一路径清单，`payloadFormat` 从 `JSON` 改为另一个字符串 | 材料指纹变化，成为新的契约版本。未登记的字符串不能封存，也不能用来提取 |

## 14. 风险与待确认事项

- `payloadFormat` 和金额路径都已经纳入 `contract-version-v3` 的正文哈希。调查表里的路径仍未封存，不能直接拿来提取。v2 契约行的分类是 `CONTRACT_PATHS_UNDEFINED`，v3 的空列表是 `CONTRACT_PATHS_EMPTY`。完整性失败时不降级使用部分路径。
- 报文保存产品未定。`rawPayloadRef` 怎样取回原文仍待确认。
- 06-03-01 的观测重试幂等键仍未设计。本文只定义逻辑身份。提取不落库，观测唯一约束也还没有存储实现。
- JSON 解析器是否暴露重复键，待实现时确认。不能在设计里假设平台没有重复键。
- 淘宝官方嵌套结构、富智云订单与团购是否同报文，都没有证据。
- 今天没有 `ACTIVE` 标准类型，也没有已发布的生产映射。提取模型不能被用来绕过这个限制。

与冻结文档的一致性：

| 冻结要求 | 本文 |
| --- | --- |
| 不新增模块，Pipeline 不依赖实现 | 端口在 core，实现在 transform，Worker 装配 |
| 七种 presence 不扩展 | 异常用字段码表达，不新增第八种形态 |
| 提取成功不是语义确认 | 提取不写类型和 `mappingStatus`。当前匹配也不能写 `MAPPED` |
| 回调和主动查询分开 | 输入强制带 `ingressMode`，相同数字也不合并 |
| 快照和契约版本不可变 | 重放只读任务上的 `snapshotId` 和其中的 `contractVersionId` |
| 非标量列表不加总 | 没有重复标记的数组保持 `NON_SCALAR` |

第二轮审查发现 `payloadFormat` 还没有进入 06-03-02-03 的字段和 v3 哈希，路径结果也还合在 `CONTRACT_PATHS_ABSENT` 里。这两份文档一起修正。这是设计纠错。没有修改 06-03-01、06-03-02-01 和 06-03-02-02，也没有激活任何类型或映射。

第三轮审查核对后，提取模型与 06-03-02-03 的当前定义一致，本文件不再改字段或状态码。`payloadFormat` 已经进入 `contract-version-v3` 正文。材料是否相同由 `contract-material-v1` 判断。`sourceRevision` 在正文哈希计算之前分配。路径状态仍是 `CONTRACT_PATHS_UNDEFINED`、`CONTRACT_PATHS_EMPTY`、`CONTRACT_PATH_INTEGRITY_FAILED`、`CONTRACT_PATHS_CORRUPT` 和 `CONTRACT_PATHS_READY`，与 06-03-02-03 相同。v2 历史哈希保持原定义。06-03-02-03 本轮另将 `ck_contract_payload_shape` 改为在写入时拒绝 v3 的空列和非法材料指纹。提取仍只读取已经通过该约束并完成路径分类的版本。

## 15. 设计决策记录

| 决策 | 理由 | 没有采用的做法 |
| --- | --- | --- |
| 提取放在映射端口前半段 | 06-03-01 已规定 Pipeline 先保存再映射。再加一个外部步骤会改变已冻结时序 | 新建提取模块或让 Pipeline 调用 Transform |
| 路径清单和 `payloadFormat` 进入 `contract-version-v3` | 独立提取档案会让快照、发布和 Worker 多读一份对象。格式和路径都是契约声明，放进正文哈希后才能被旧快照固定 | 只在表上增加 `payload_format`，哈希仍沿用没有该字段的字节 |
| 五种路径分类分开 | v2 没有定义、v3 空列表、完整性失败、数据损坏和正常清单对提取的含义不同 | 用一个 `CONTRACT_PATHS_ABSENT` 同时表示 v2 和任何 v3 失败 |
| 修订号先于正文哈希 | `sourceRevision` 在 v3 正文里。材料指纹不含它，所以可以先决定修订号，再计算正文哈希 | 用包含修订号的正文哈希判断要不要新建修订 |
| 只提取声明路径 | 全文字段扫描会把未声明数字当成金额，属于语义猜测 | 发现数字就生成一条金额 |
| 重复标记才展开 | `extras[].reduce_fee` 是已经声明的子路径；`extras` 本身被冻结文档禁止拆开加总 | 看到数组就展开并求和 |
| 字段失败不阻断兄弟字段 | 一个坏节点不应丢掉同报文里已经能识别的原始值 | 任一字段异常就丢弃整份原文 |
| 坏 JSON 和失效引用阻断 | 没有可靠原文时继续提取会产生编造的路径 | 用最新查询结果代替失效原文 |
| 不在提取阶段建 `Money` | 单位和是否允许解析字符串都属于映射约束。当前约束是 `NO_CONVERT` | 提取时把分统一换成元并覆盖原文 |
| 字面量保留失败单独编码 | 读报文失败、保留原文失败、解析数值、精度和金额范围是五件不同的事 | 用一个“数值太大”同时表示这五类 |

## 16. 修订记录

| 轮次 | 内容 |
| --- | --- |
| 初稿 | 建立原始金额提取的职责、路径读取和与映射端口的边界 |
| 第一轮审查 | 设计纠错。路径清单改为 `contract-version-v3` 正文。区分报文读取、字面量保留和后续数值校验。提取结果不单独落库 |
| 第二轮审查 | 设计纠错。`payloadFormat` 进入 v3 正文哈希。路径结果拆成五种分类。完整性失败阻断整次提取。修订号的分配顺序改由 06-03-02-03 的材料指纹决定 |
| 第三轮审查 | 与 06-03-02-03 对齐，不改提取模型。`payloadFormat` 已纳入 `contract-version-v3` 正文。`contract-material-v1` 已用于材料相同性判断。`sourceRevision` 在正文哈希计算前分配。路径状态分类与 06-03-02-03 保持一致。v2 历史哈希保持原定义 |
