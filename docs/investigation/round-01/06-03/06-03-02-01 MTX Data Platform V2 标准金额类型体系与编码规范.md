# 06-03-02-01 MTX Data Platform V2 标准金额类型体系与编码规范

- 文档性质：详细设计建议。未经评审，不是已确认架构决策，也不是实现说明。
- 基础：`06-03-01 MTX Data Platform V2 多平台订单金额领域模型设计.md`。本文不推翻 Money、RawAmountValue、ObservedAmount、AmountSource、三种证据与映射状态，以及原文、观测、计算三份记录。
- 范围：标准金额类型和编码。不写字段映射全表，不写 SQL，不写 Java。
- V2 现状：没有金额领域实现。trade-flow 的字段和公式不是 V2 能力。

相对链接：

- [06-03-01 金额领域模型](06-03-01%20MTX%20Data%20Platform%20V2%20多平台订单金额领域模型设计.md)
- [R-09 主报告](../06-02-R/06-02-R-09四平台金额、支付、退款与团购核销业务口径确认及证据闭环报告.md)
- [R-09 矩阵](../06-02-R/06-02-R-09金额业务口径确认与证据闭环矩阵.md)
- [团购八列核验](../06-02-R/06-02-R-07团购核销金额精度单位与写入一致性专项核验.md)

## 1. 设计背景与目标

类型编码回答“这是哪一种业务含义”。它不回答平台字段名，也不回答营业额怎么算。

不能因为字段名相近，就把订单原价和销售价总额、门店配送费和用户支付运费放进同一个编码。契约没有写出共同语义时，字段保持待确认或未识别，不伪造统一口径。

事实标签：

| 标签 | 含义 |
| --- | --- |
| 代码事实 | trade-flow 源码已经这样写，多为 E4 |
| 官方或摘录事实 | 打开过的官方页是 E1-A。仓库摘录是 E1-B |
| 业务已确认 | 本文没有把跨平台经营公式标成已确认 |
| 待报文 | R-09 真实报文列为“无” |
| 待业务或财务 | 矩阵中的待确认、冲突或无法验证 |
| 设计建议 | 本文的编码和约束 |

`06-02-11` 把多个字段放在“用户实付”一行，是因为旧代码都写入了 `userPayAmount`。R-09 和 06-03-01 否定了这种业务等同。本文只保留那一行作为代码归类。

## 2. 设计原则

1. 编码不含平台名，不含数据库字段名。
2. 一个 typeCode 只表达一种已经说清的语义。说不清就拆开，或者不让它被映射。
3. 不为了凑数量拆分。同一契约、同一包含范围、同一主体，不另建编码。
4. 原文、标准观测、计算结果仍是三份记录。
5. 用户应付、用户实付、支付成功金额、商家应收、结算金额、经营成交额彼此不能代替。
6. `turnover_v1_trade_flow` 不分配编码。
7. 单位未知不转换。缺失、null、空字符串和数字 0 继续区分。
8. 退款不默认记成负数。优惠承担方继续分开。
9. 团购核销不进入订单金额对象，八列都不做元分转换。
10. 当前全部类型定义都还是草稿。草稿阶段可以收紧错误定义。一旦某个编码进入 ACTIVE，就不能再改含义，只能停用并另给新码。

## 3. 金额分类与语义边界

| 类别 | 处理 |
| --- | --- |
| 订单原价 | 只用 `AMT.ORD.ORIGINAL`。指契约称为原价的订单级金额。不包含销售价总额 |
| 销售价总额 | 新增 `AMT.ORD.SALE_TOTAL`。京东摘录用的是这个词，不是原价 |
| 订单总价 | `AMT.ORD.TOTAL` 只接收契约明确称为“总价”的字段。它不是跨平台业务总价的统称 |
| 商品原价 | `AMT.ORD.GOODS_ORIGINAL` 保留，但在各平台商品口径被写明之前不允许映射 |
| 优惠后订单金额 | `AMT.ORD.AFTER_DISCOUNT` 保留为待确认槽位。没有契约定义之前禁止映射，避免把它写成总价或实付 |
| 订单级优惠、优惠明细、承担方 | 继续分码。明细不能加总后冒充订单级优惠 |
| 用户应付、用户实付、支付成功 | 三个码。应付不能映射成实付或支付成功 |
| 退款单、退款成功、用户实退 | 三个码。实退没有定义，不能由成功合计改名 |
| 商家应收、结算 | 两个码。应收不能推导结算 |
| 配送 | 废止共用的 `AMT.FEE.DELIVERY`。门店配送费和用户实际支付运费拆开 |
| 包装 | 商品餐盒、订单打包费、打包袋仍然三个码。京东“包装金额”不自动进入其中任何一个 |
| 服务费 | 不建跨平台服务费。美团春节服务费已含在门店配送费内。团购服务费单独编码 |
| 手续费、充值、指标 | 手续费单位未确认。充值本金和赠送分开，且不进订单成交额。指标码禁止当映射目标 |
| 团购 | 八个字段各自一码。单位全部未知 |
| 未识别 | `AMT.UNK.UNIDENTIFIED` |

`AMT.ORD.ORIGINAL` 的旧文字把“原价或销售价总额”写在一起。这是错误边界。美团 `original_price` 的官方词是原价，且含 1 对 1 急送费。京东 `orderTotalMoney` 的摘录词是销售价总额。两者不能共用一个编码。

`AMT.ORD.TOTAL` 不是“凡是叫 total 的字段”。只有来源契约把该字段定义成总价时才能指向它。目前只有美团官方页符合。淘宝 `totalPrice`、富智云 `payAmt` 都不符合。

## 4. 金额属性维度

这些维度代替原先混合的 `amountDirection`。一个类型不必填满每一维。不适用就写 `NOT_APPLICABLE`，不知道就写 `UNKNOWN`。不要为了填满而猜测。

### 4.1 amountNature

金额性质。它回答“这是价、费、优惠、资金结果，还是指标”。

| 值 | 含义 |
| --- | --- |
| ORDER_PRICE | 订单级价格或价额 |
| GOODS_PRICE | 商品级价格 |
| DISCOUNT | 优惠额本身 |
| DISCOUNT_BEAR | 优惠的承担部分 |
| FEE | 费用 |
| USER_FUND | 用户侧应付、实付或退款结果 |
| MERCHANT_FUND | 商家应收一类资金结果 |
| PLATFORM_FUND | 平台补贴一类资金结果 |
| SETTLEMENT | 结算结果 |
| METRIC | 经营指标结果 |
| RECHARGE | 充值本金或赠送 |
| GROUP_BUY | 团购核销金额 |
| UNIDENTIFIED | 尚未识别 |

示例：订单原价是 `ORDER_PRICE`。商家承担优惠是 `DISCOUNT_BEAR`，不是一笔付款。经营成交额是 `METRIC`。

### 4.2 fundDirection

资金是否已经发生流动，以及从哪一方到哪一方。价格本身通常没有流动方向。

| 值 | 含义 |
| --- | --- |
| NOT_APPLICABLE | 这一类型不是资金流动 |
| UNKNOWN | 可能是资金结果，但流向没有被确认 |
| USER_TO_COUNTERPARTY | 用户付出。对手方是谁，未在类型里写死 |
| COUNTERPARTY_TO_USER | 退回用户 |
| TO_MERCHANT | 流向商家。是否已经结算，不由本值表示 |
| FROM_PLATFORM | 平台付出 |

示例：订单原价是 `NOT_APPLICABLE`。用户应付是 `UNKNOWN`，因为“应付”还不是已经支付。退款成功金额在状态定义确认前也是 `UNKNOWN`，不能直接写成 `COUNTERPARTY_TO_USER`。

### 4.3 bearerType

谁承担这笔优惠或费用。不是资金流向的别名。

| 值 | 含义 |
| --- | --- |
| NOT_APPLICABLE | 该类型不表达承担主体 |
| UNKNOWN | 应有承担主体，但主体未确认 |
| USER | 用户承担 |
| MERCHANT | 商家承担 |
| PLATFORM | 平台承担 |
| SHARED | 多个主体分担，且契约写明是分担 |

示例：商家承担优惠是 `MERCHANT`。它的 fundDirection 仍是 `NOT_APPLICABLE`，因为承担优惠不等于商家已经付款或收款。平台承担优惠不是平台补贴。

### 4.4 metricImpact

对经营成交额等指标的影响。没有生效公式时，不能写成增加或减少。

| 值 | 含义 |
| --- | --- |
| NOT_APPLICABLE | 不讨论指标 |
| NO_CONFIRMED_IMPACT | 当前没有确认它会改变经营成交额 |
| UNKNOWN | 指标类型自己的影响方式还没定义 |

示例：退款类型是 `NO_CONFIRMED_IMPACT`。这表示“现在不能拿它去减营业额”，不是“退款永远不影响”。净成交额类型自己是 `UNKNOWN`。

### 4.5 signConvention

来源数值的正负号怎么保存。它不是资金方向，也不是指标加减。

| 值 | 含义 |
| --- | --- |
| PRESERVE_SOURCE | 保留报文里的正负号。不因为类型是退款就改成负数，也不把负优惠取绝对值 |

当前所有类型都用这一条。旧代码里的取绝对值和“负数收成 0”只是 E4，不进入本约定。

## 5. 状态维度

三个维度分开。它们也不代替 06-03-01 里观测上的 EvidenceStatus 和映射执行状态。

### 5.1 semanticStatus

业务语义确认到哪一步。取值与 06-03-01 相同：

| 值 | 含义 | 进入条件 |
| --- | --- | --- |
| 部分确认 | 至少有一个来源把这个词说清了，但还不能当成全部平台或全部包含项已确认 | 有 E1-A 或有限定范围的 E1-B，且没有把它推广到其他平台 |
| 待平台确认 | 平台定义、契约是否有效，或字段是否存在，还没确认 | 缺官方定义，或摘录与消息页尚未证明是同一契约 |
| 待业务确认 | 平台词已经够用，但是否采用为经营口径还要业务书面确认 | 例如实付、成交额 |
| 待财务确认 | 要财务确认结算、手续费或退款最终额 | 结算、手续费、退款成功合计 |
| 存在冲突 | 官方说法和另一种解释不能同时成立 | 例如美团总价被旧代码当成实付 |
| 无法验证 | 没有足够材料判断 | 未识别金额，或没有公式的平衡关系 |

EvidenceStatus 仍写在观测上，不写进 semanticStatus。E4 不能把语义改成已确认。

### 5.2 mappingEligibility

这个类型能不能被字段映射指向。它是类型定义上的门禁，不是某一次映射的执行结果。

| 值 | 含义 | 使用场景 |
| --- | --- | --- |
| ALLOWED_IF_CONTRACT_DEFINES | 只有来源契约的文字落在本类型定义内才可映射 | 美团原价、美团总价、美团商家应收 |
| NOT_UNTIL_CONFIRMED | 类型先占位，确认前禁止映射 | 用户实付、销售价总额在 SRC-002 关闭前、淘宝金额 |
| DISALLOWED | 禁止作为映射目标 | 经营成交额、净成交额、结算额、退款成功合计 |
| CONFLICT_ONLY | 只能作为冲突候选，不能成为已确认观测 | 目前不把任何类型设成这个值。冲突记在映射上 |

06-03-01 的 MappingStatus 仍表示映射定义是草稿、生效或停用，以及某次执行是已映射、未映射或冲突。mappingEligibility 不取代它。一个类型是 `ALLOWED_IF_CONTRACT_DEFINES`，具体映射仍可以是 `DRAFT`。

### 5.3 typeLifecycleStatus

类型编码自身的生命周期。

| 值 | 含义 | 转换 |
| --- | --- | --- |
| DRAFT | 设计稿。含义还可以收紧 | 评审采用后到 ACTIVE |
| ACTIVE | 可以用于新映射。含义冻结 | 发现含义错误时到 RETIRED，同时发布新码 |
| RETIRED | 禁止新映射。旧观测若曾经使用，仍保留原码 | 不再回到 ACTIVE，也不把编码改指别的金额 |

当前除 `AMT.FEE.DELIVERY` 为 `RETIRED` 外，其余都是 `DRAFT`。没有任何编码已经 ACTIVE。因此收紧“原价”的定义还不算废弃一个已生效编码。`AMT.FEE.DELIVERY` 被停用，是因为它在草稿中已经被写成两种费用的混合桶，继续留用会让门店配送费和用户运费混在一起。

## 6. 币种、单位与精度落在哪一层

不用“聚合前必须”这类含糊句子充当字段值。

| 属性 | 属于 | 取值 |
| --- | --- | --- |
| currencyRequirement | 类型 | `REQUIRED_BEFORE_AGGREGATION` 或 `NOT_APPLICABLE` |
| unitRequirement | 类型 | 同上 |
| precisionPolicy | 类型给出默认约束 | `NO_IMPLICIT_ROUNDING`。真正的舍入只出现在计算规则里 |
| aggregationPolicy | 类型 | `FORBIDDEN`、`SAME_TYPE_AFTER_CONFIRMATION`、`NOT_APPLICABLE` |
| currency | 金额实例 Money | `CNY` 或 `UNKNOWN`。类型不默认 CNY |
| unit | 金额实例 Money | `YUAN`、`FEN` 或 `UNKNOWN`。类型不默认元或分 |
| unitEvidence | 金额实例 | `CONTRACT_DECLARED`、`UNCONFIRMED`、`UNKNOWN`。DDL 注释不是 `CONTRACT_DECLARED` |
| calculationPermission | 实例相对计算规则 | `BLOCKED` 或 `OPEN`。单位或币种为 `UNKNOWN` 时必须 `BLOCKED` |

类型上的 `REQUIRED_BEFORE_AGGREGATION` 表示：实例可以暂时是 `UNKNOWN`，但只要还是 `UNKNOWN`，aggregationPolicy 即使写成 `SAME_TYPE_AFTER_CONFIRMATION` 也不能加总。未识别类型的币种和单位要求是 `NOT_APPLICABLE`，因为还没有语义。

团购八列的 unitEvidence 固定按 `UNCONFIRMED` 设计。禁止写成 `FEN` 或 `YUAN`。

## 7. 标准金额类型目录

性质、资金方向、承担主体、指标影响分别用第 4 节的枚举。正负号全部是 `PRESERVE_SOURCE`，目录中不再重复。币种要求和单位要求，除未识别类型外都是 `REQUIRED_BEFORE_AGGREGATION`。未识别类型这两项是 `NOT_APPLICABLE`。精度约束除特别注明外都是 `NO_IMPLICIT_ROUNDING`。

| typeCode | typeName | 性质 | 资金方向 | 承担主体 | 指标影响 | 聚合 | 语义状态 | 映射资格 | 生命周期 | 边界 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| AMT.ORD.ORIGINAL | 订单原价 | ORDER_PRICE | NOT_APPLICABLE | NOT_APPLICABLE | NO_CONFIRMED_IMPACT | FORBIDDEN | 部分确认 | ALLOWED_IF_CONTRACT_DEFINES | DRAFT | 只接受契约称为原价的订单级金额。美团 `original_price` 含急送费。不是销售价总额，不是商品原价合计 |
| AMT.ORD.SALE_TOTAL | 销售价总额 | ORDER_PRICE | NOT_APPLICABLE | NOT_APPLICABLE | NO_CONFIRMED_IMPACT | FORBIDDEN | 待平台确认 | NOT_UNTIL_CONFIRMED | DRAFT | 只接受契约称为销售价总额的金额。京东摘录 `orderTotalMoney` 是候选，SRC-002 未关闭前不映射 |
| AMT.ORD.TOTAL | 订单总价 | ORDER_PRICE | NOT_APPLICABLE | NOT_APPLICABLE | NO_CONFIRMED_IMPACT | FORBIDDEN | 部分确认 | ALLOWED_IF_CONTRACT_DEFINES | DRAFT | 只接受契约称为总价的金额。现仅美团 `total`，含急送费，未写用户实付，未写“元”。其他平台的 total 或 totalPrice 不映射 |
| AMT.ORD.GOODS_ORIGINAL | 商品原价 | GOODS_PRICE | NOT_APPLICABLE | NOT_APPLICABLE | NO_CONFIRMED_IMPACT | FORBIDDEN | 待平台确认 | NOT_UNTIL_CONFIRMED | DRAFT | 商品口径的原价。旧代码有汇总，但没有共同官方定义。不代替订单原价 |
| AMT.ORD.AFTER_DISCOUNT | 优惠后订单金额 | ORDER_PRICE | NOT_APPLICABLE | NOT_APPLICABLE | NO_CONFIRMED_IMPACT | FORBIDDEN | 待平台确认 | NOT_UNTIL_CONFIRMED | DRAFT | 优惠之后的订单级金额。京东 `orderGoodsMoney` 未被官方参考登记。不是总价，不是实付 |
| AMT.ORD.DISCOUNT_ORDER | 订单级优惠 | DISCOUNT | NOT_APPLICABLE | UNKNOWN | NO_CONFIRMED_IMPACT | FORBIDDEN | 待平台确认 | NOT_UNTIL_CONFIRMED | DRAFT | 订单头优惠。京东摘录写明不含单品和运费优惠。不能把明细再加一遍 |
| AMT.ORD.DISCOUNT_DETAIL | 优惠明细金额 | DISCOUNT | NOT_APPLICABLE | UNKNOWN | NO_CONFIRMED_IMPACT | FORBIDDEN | 待平台确认 | NOT_UNTIL_CONFIRMED | DRAFT | 一条优惠明细。美团数组里可以有无优惠额的骑手元素，不能当成 0 |
| AMT.USR.PAYABLE | 用户应付金额 | USER_FUND | UNKNOWN | NOT_APPLICABLE | NO_CONFIRMED_IMPACT | FORBIDDEN | 待平台确认 | NOT_UNTIL_CONFIRMED | DRAFT | 平台写明的应付。京东摘录是候选且单位写分，但是否含餐盒和运费、摘录是否现行，都未确认。不是实付 |
| AMT.USR.PAID | 用户实付金额 | USER_FUND | UNKNOWN | NOT_APPLICABLE | NO_CONFIRMED_IMPACT | FORBIDDEN | 待业务确认 | NOT_UNTIL_CONFIRMED | DRAFT | 用户最终付出。当前没有可映射字段 |
| AMT.PAY.SUCCESS | 支付成功金额 | USER_FUND | UNKNOWN | NOT_APPLICABLE | NO_CONFIRMED_IMPACT | FORBIDDEN | 待业务确认 | DISALLOWED | DRAFT | 成功流水的合计槽位。不是订单字段的映射目标。PAY-001 |
| AMT.RFD.DOCUMENT | 退款单金额 | USER_FUND | UNKNOWN | NOT_APPLICABLE | NO_CONFIRMED_IMPACT | FORBIDDEN | 待平台确认 | NOT_UNTIL_CONFIRMED | DRAFT | 退款单上的金额。不表示成功，不表示实退，不默认负数 |
| AMT.RFD.SUCCEEDED | 退款成功金额 | USER_FUND | UNKNOWN | NOT_APPLICABLE | NO_CONFIRMED_IMPACT | FORBIDDEN | 待财务确认 | DISALLOWED | DRAFT | 成功合计槽位。旧代码按退款号相加只是 E4 |
| AMT.RFD.USER_ACTUAL | 用户实退金额 | USER_FUND | UNKNOWN | NOT_APPLICABLE | NO_CONFIRMED_IMPACT | FORBIDDEN | 待财务确认 | NOT_UNTIL_CONFIRMED | DRAFT | R-09 没有这个定义。不能由成功合计改名 |
| AMT.MCH.RECEIVABLE | 商家应收金额 | MERCHANT_FUND | UNKNOWN | NOT_APPLICABLE | NO_CONFIRMED_IMPACT | FORBIDDEN | 部分确认 | ALLOWED_IF_CONTRACT_DEFINES | DRAFT | 现仅美团 `wmPoiReceiveCent`：应收，单位分，可空，不等于总价。不是结算额 |
| AMT.PLT.SUBSIDY | 平台补贴金额 | PLATFORM_FUND | UNKNOWN | NOT_APPLICABLE | NO_CONFIRMED_IMPACT | FORBIDDEN | 待业务确认 | NOT_UNTIL_CONFIRMED | DRAFT | 没有已确认来源。不是平台承担优惠 |
| AMT.DSC.MERCHANT_BEAR | 商家承担优惠 | DISCOUNT_BEAR | NOT_APPLICABLE | MERCHANT | NO_CONFIRMED_IMPACT | FORBIDDEN | 待业务确认 | NOT_UNTIL_CONFIRMED | DRAFT | 承担部分。旧代码有列。平衡等式未确认 |
| AMT.DSC.PLATFORM_BEAR | 平台承担优惠 | DISCOUNT_BEAR | NOT_APPLICABLE | PLATFORM | NO_CONFIRMED_IMPACT | FORBIDDEN | 待业务确认 | NOT_UNTIL_CONFIRMED | DRAFT | 不是平台补贴 |
| AMT.DSC.USER_BEAR | 用户承担优惠 | DISCOUNT_BEAR | NOT_APPLICABLE | USER | NO_CONFIRMED_IMPACT | FORBIDDEN | 待平台确认 | NOT_UNTIL_CONFIRMED | DRAFT | 必须来自活动原文，不能用实付差额倒挤 |
| AMT.FEE.DELIVERY | 配送费 | FEE | NOT_APPLICABLE | NOT_APPLICABLE | NO_CONFIRMED_IMPACT | FORBIDDEN | 存在冲突 | DISALLOWED | RETIRED | 草稿中把门店配送费和用户支付运费放在一起。停用。不复用此码 |
| AMT.FEE.STORE_DELIVERY | 门店配送费 | FEE | NOT_APPLICABLE | NOT_APPLICABLE | NO_CONFIRMED_IMPACT | FORBIDDEN | 部分确认 | ALLOWED_IF_CONTRACT_DEFINES | DRAFT | 美团 `shipping_fee`。春节服务费已包含，不能再加。急送费是否包含，未证明 |
| AMT.FEE.USER_FREIGHT | 用户支付运费 | FEE | UNKNOWN | NOT_APPLICABLE | NO_CONFIRMED_IMPACT | FORBIDDEN | 待平台确认 | NOT_UNTIL_CONFIRMED | DRAFT | 京东摘录 `orderFreightMoney` 的词是用户实际支付运费。不是门店配送费，也不是整单应付 |
| AMT.FEE.PACKAGE_ITEM | 商品餐盒费 | FEE | NOT_APPLICABLE | NOT_APPLICABLE | NO_CONFIRMED_IMPACT | FORBIDDEN | 存在冲突 | NOT_UNTIL_CONFIRMED | DRAFT | 商品餐盒。美团 mode 1/2 与商品行求和冲突。京东 `canteenMoney` 是否采用，AMT-002 未完成 |
| AMT.FEE.PACKAGE_ORDER | 订单打包费 | FEE | NOT_APPLICABLE | NOT_APPLICABLE | NO_CONFIRMED_IMPACT | FORBIDDEN | 部分确认 | ALLOWED_IF_CONTRACT_DEFINES | DRAFT | 美团 `package_bag_fee`。有该字段时不得用商品餐盒覆盖。不是打包袋 |
| AMT.FEE.PACKAGE_BAG | 打包袋金额 | FEE | NOT_APPLICABLE | NOT_APPLICABLE | NO_CONFIRMED_IMPACT | FORBIDDEN | 部分确认 | ALLOWED_IF_CONTRACT_DEFINES | DRAFT | 美团打包袋。分和元两种字段只能选一个来源，不能相加。京东 `packagingMoney` 不映射到这里 |
| AMT.FEE.PAYMENT | 支付手续费 | FEE | UNKNOWN | UNKNOWN | NO_CONFIRMED_IMPACT | FORBIDDEN | 待财务确认 | NOT_UNTIL_CONFIRMED | DRAFT | 富智云 `feeAmt` 是代码事实。单位未确认，不能推断为分 |
| AMT.FIN.SETTLEMENT | 结算金额 | SETTLEMENT | UNKNOWN | NOT_APPLICABLE | NOT_APPLICABLE | FORBIDDEN | 待财务确认 | DISALLOWED | DRAFT | 无公式。不能由应收或实付推导 |
| AMT.IND.TURNOVER | 经营成交额 | METRIC | NOT_APPLICABLE | NOT_APPLICABLE | UNKNOWN | FORBIDDEN | 待业务确认 | DISALLOWED | DRAFT | 不是旧营业额公式 |
| AMT.IND.NET | 净成交额 | METRIC | NOT_APPLICABLE | NOT_APPLICABLE | UNKNOWN | FORBIDDEN | 待业务确认 | DISALLOWED | DRAFT | 不能定义成原额减退款 |
| AMT.RCG.PRINCIPAL | 充值本金 | RECHARGE | UNKNOWN | NOT_APPLICABLE | NO_CONFIRMED_IMPACT | FORBIDDEN | 待业务确认 | NOT_UNTIL_CONFIRMED | DRAFT | 不进入订单成交额。旧代码取绝对值只是 E4 |
| AMT.RCG.GIFT | 充值赠送 | RECHARGE | UNKNOWN | NOT_APPLICABLE | NO_CONFIRMED_IMPACT | FORBIDDEN | 待业务确认 | NOT_UNTIL_CONFIRMED | DRAFT | 与本金分开。不默认取绝对值 |
| AMT.GBV.BUY_PRICE | 团购购买价 | GROUP_BUY | UNKNOWN | NOT_APPLICABLE | NOT_APPLICABLE | FORBIDDEN | 待平台确认 | NOT_UNTIL_CONFIRMED | DRAFT | `buyPriceAmt` / `buy_price_amt`。单位未确认 |
| AMT.GBV.FACE | 团购面值 | GROUP_BUY | UNKNOWN | NOT_APPLICABLE | NOT_APPLICABLE | FORBIDDEN | 待平台确认 | NOT_UNTIL_CONFIRMED | DRAFT | `faceAmt` / `face_amt`。DDL 注释写券面值，注释不是单位证明 |
| AMT.GBV.FACE_VALUE | 团购实际抵扣 | GROUP_BUY | UNKNOWN | NOT_APPLICABLE | NOT_APPLICABLE | FORBIDDEN | 待平台确认 | NOT_UNTIL_CONFIRMED | DRAFT | 第八列。见第 11 节。不与面值合并，不换算 |
| AMT.GBV.COUPON | 团购券金额 | GROUP_BUY | UNKNOWN | NOT_APPLICABLE | NOT_APPLICABLE | FORBIDDEN | 待平台确认 | NOT_UNTIL_CONFIRMED | DRAFT | `couponAmt`。DDL 注释写商家优惠，业务关系未确认 |
| AMT.GBV.PLAT_HONGBAO | 团购平台红包 | GROUP_BUY | UNKNOWN | NOT_APPLICABLE | NOT_APPLICABLE | FORBIDDEN | 待平台确认 | NOT_UNTIL_CONFIRMED | DRAFT | `platHongBao`。不是订单上的平台承担优惠 |
| AMT.GBV.MCHNT_INCOME | 团购商家总收入 | GROUP_BUY | UNKNOWN | NOT_APPLICABLE | NOT_APPLICABLE | FORBIDDEN | 待平台确认 | NOT_UNTIL_CONFIRMED | DRAFT | `mchntIncome`。不是商家应收，也不是结算额 |
| AMT.GBV.THIRD_MCHNT_INCOME | 团购第三方商家收入 | GROUP_BUY | UNKNOWN | NOT_APPLICABLE | NOT_APPLICABLE | FORBIDDEN | 待平台确认 | NOT_UNTIL_CONFIRMED | DRAFT | `thirdMchntIncome`。不与上一列相加 |
| AMT.GBV.THIRD_SERVICE_FEE | 团购服务费 | GROUP_BUY | UNKNOWN | UNKNOWN | NOT_APPLICABLE | FORBIDDEN | 待平台确认 | NOT_UNTIL_CONFIRMED | DRAFT | `thirdServiceFee`。不是美团春节服务费，也不从收入列里扣 |
| AMT.UNK.UNIDENTIFIED | 未识别金额 | UNIDENTIFIED | NOT_APPLICABLE | NOT_APPLICABLE | NOT_APPLICABLE | FORBIDDEN | 无法验证 | ALLOWED_IF_CONTRACT_DEFINES | DRAFT | 语义未知时的唯一映射目标。必须保留路径和契约 |

团购类型的“允许映射”只表示将来可以指向该字段自己的编码。在单位和业务词被确认前，映射资格仍是 `NOT_UNTIL_CONFIRMED`，不能换算后写入订单类型。

目录共 39 个编码。其中 1 个已停用，38 个为草稿。

### 7.1 定义示例

#### AMT.ORD.ORIGINAL

主体是订单，不是商品行。环节是订单计价，不是支付，也不是结算。包含范围只以该契约对“原价”的说明为准。美团说明含 1 对 1 急送费。销售价总额不在范围内。

#### AMT.ORD.SALE_TOTAL

主体是订单。环节仍是计价。包含范围是契约称为销售价总额的部分。京东摘录如此描述 `orderTotalMoney`，并把它和商品原价合计区分开。这只是 E1-B。SRC-002 未关闭前不映射。

#### AMT.ORD.TOTAL

主体是订单。它的业务定义不是“标准总价”，而是“这份契约自己称为总价的那个金额”。美团总价含急送费，未定义为用户实付。所以即使用了这个编码，semanticStatus 在实付问题上仍是冲突，不能再生成一条实付观测。

#### AMT.ORD.GOODS_ORIGINAL

主体是商品集合。旧代码会汇总商品原价。各平台是否同一口径，没有官方共同定义。确认前不映射，避免和订单原价重复。

#### AMT.FEE.STORE_DELIVERY

主体是门店配送费这一费用项。美团官方是门店配送费，春节服务费已在里面。它不包含“用户实际支付运费”这个京东摘录概念。

#### AMT.FEE.USER_FREIGHT

主体仍是费用，但产生环节是用户支付的运费。京东摘录用的是这个句子。它不是整单应付，也不是门店配送费。

#### AMT.FEE.PACKAGE_ORDER 与 AMT.FEE.PACKAGE_BAG

订单打包费的主体是订单上的打包费。打包袋的主体是口袋耗材。美团官方把两者分开。分和元两个打包袋字段描述的是同一袋的两种写法，映射时只能选一个来源，不能建成两个类型再相加。

#### AMT.USR.PAYABLE 与 AMT.USR.PAID

应付的主体是用户，环节是下单时的应付要求。实付的环节是最终付出。当前只有应付有摘录候选。实付没有候选字段。

#### AMT.MCH.RECEIVABLE 与 AMT.FIN.SETTLEMENT

应收的主体是商家，环节是平台给出的应收，不是打款。结算的环节是财务结算规则。后者禁止映射。

#### AMT.GBV.FACE 与 AMT.GBV.FACE_VALUE

面值和实际抵扣是两列。DDL 注释分别写“券面值”和“实际抵扣金额”，并都写了单位分。注释不是接口单位证明，也不是已经确认的业务定义。两列都保持待平台确认，都不换算。

## 8. 编码规范

形式是 `AMT.<域>.<概念>`。域是 ORD、USR、PAY、RFD、MCH、PLT、DSC、FEE、FIN、IND、RCG、GBV、UNK。概念词发布后不改拼写。

类型定义若只补充说明、不改变语义，增加类型定义版本，不改 typeCode。版本放在注册记录上。当前没有 ACTIVE 编码，因此本次对原价和总价的收紧记在第 18 节，不另发一套旧码。

停用的 `AMT.FEE.DELIVERY` 不再指门店配送费，也不再指运费。新映射使用新码。

## 9. 类型与实例的边界

typeCode 只携带语义身份。数值、币种、单位、精度、正负号、平台、接口、字段路径、契约版本、映射版本、业务对象、观测时间、证据状态和这次映射的语义状态，都在实例、来源或映射上。

观测只有在映射资格允许、且这次 MappingStatus 为已映射时，才算一条标准金额观测。候选映射和冲突不是标准观测。

## 10. 正负号

资金方向、承担主体、指标影响、来源符号是四件事。退款类型使用 `PRESERVE_SOURCE`，不使用“退款即负数”。负优惠是否合法，待业务确认。原文 presence 和 rawText 不因标准化符号被改写。

## 11. 团购八个金额字段

06-02-08 只说明有八个 `DECIMAL(24,6)` 列，并点名了购买价、面额、券金额、平台红包、两种商家收入和服务费，没有写出第八个名字。

06-02-R-07 列出全部八列：`buy_price_amt`、`coupon_amt`、`face_amt`、`face_value`、`mchnt_income`、`plat_hong_bao`、`third_mchnt_income`、`third_service_fee`。

代码事实：`FuiouGroupBuyVerificationRows` 的金额集合是 `buyPriceAmt`、`couponAmt`、`faceAmt`、`faceValue`、`mchntIncome`、`platHongBao`、`thirdMchntIncome`、`thirdServiceFee`。第八个 JSON 字段名是 `faceValue`。

结构事实：06-DB-01-03 中 `face_value` 的列注释是“实际抵扣金额，单位分”。同表其他七列也有“单位分”注释。06-02-R-07 写明接口摘录没有这八个字段的单位说明，代码不乘除 100。GBV-001 仍是待平台确认。

因此第八个字段已经由调查列名和代码字段名证明，不是编造。它的业务定义和单位没有被证明。`AMT.GBV.FACE_VALUE` 只把列和字段接上，不把注释升级成口径，也不做元分转换。八列都是如此。

## 12. 原 35 个编码的复核

| 编码 | 结果 | 原因 |
| --- | --- | --- |
| AMT.ORD.ORIGINAL | 收紧 | 去掉销售价总额。只留原价 |
| AMT.ORD.TOTAL | 收紧 | 不再代表跨平台业务总价。只接收契约中的“总价” |
| AMT.ORD.GOODS_ORIGINAL | 保留 | 与订单原价不是同一主体。确认前不映射 |
| AMT.ORD.AFTER_DISCOUNT | 保留 | 独立槽位。无定义前禁止映射，避免并进总价或实付 |
| AMT.ORD.DISCOUNT_ORDER | 保留 | 与明细、承担方不重叠 |
| AMT.ORD.DISCOUNT_DETAIL | 保留 | 同上 |
| AMT.USR.PAYABLE | 保留 | 不是实付 |
| AMT.USR.PAID | 保留 | 指标和资金结果都不是它。当前不可映射 |
| AMT.PAY.SUCCESS | 保留 | 资金合计槽位，禁止当订单字段映射 |
| AMT.RFD.DOCUMENT | 保留 | 不是成功额，不是实退 |
| AMT.RFD.SUCCEEDED | 保留 | 计算槽位，禁止映射 |
| AMT.RFD.USER_ACTUAL | 保留 | 语义未确认，不与成功额合并 |
| AMT.MCH.RECEIVABLE | 保留 | 不是结算，不是营业额 |
| AMT.PLT.SUBSIDY | 保留 | 与平台承担优惠不重叠。无来源则不映射 |
| AMT.DSC.MERCHANT_BEAR | 保留 | 承担主体，不是资金方向 |
| AMT.DSC.PLATFORM_BEAR | 保留 | 不是补贴 |
| AMT.DSC.USER_BEAR | 保留 | 不能倒挤 |
| AMT.FEE.DELIVERY | 停用 | 混合了两种费用 |
| AMT.FEE.PACKAGE_ITEM | 保留 | 与订单打包费、打包袋不重叠。确认前不映射 |
| AMT.FEE.PACKAGE_ORDER | 保留 | 美团订单打包费 |
| AMT.FEE.PACKAGE_BAG | 保留 | 打包袋。分和元不是两个类型 |
| AMT.FEE.PAYMENT | 保留 | 单位未确认，不参与聚合 |
| AMT.FIN.SETTLEMENT | 保留 | 指标与原始金额都不是它。禁止映射 |
| AMT.IND.TURNOVER | 保留 | 不是原始金额，也不是旧公式 |
| AMT.IND.NET | 保留 | 同上 |
| AMT.RCG.PRINCIPAL | 保留 | 不进订单成交额 |
| AMT.RCG.GIFT | 保留 | 与本金分开 |
| AMT.GBV.BUY_PRICE | 保留 | 单位未确认 |
| AMT.GBV.FACE | 保留 | 不与实际抵扣合并 |
| AMT.GBV.COUPON | 保留 | 不解释成订单优惠 |
| AMT.GBV.PLAT_HONGBAO | 保留 | 不并入平台承担 |
| AMT.GBV.MCHNT_INCOME | 保留 | 不并入商家应收 |
| AMT.GBV.THIRD_MCHNT_INCOME | 保留 | 不与上一列相加 |
| AMT.GBV.THIRD_SERVICE_FEE | 保留 | 不并入门店配送费 |
| AMT.UNK.UNIDENTIFIED | 保留 | 未知语义的唯一去向 |

没有编码被标成“语义已确认且可以随意映射”。部分确认只限于契约文字已经读到的那一个含义。

新增：`AMT.ORD.SALE_TOTAL`、`AMT.FEE.STORE_DELIVERY`、`AMT.FEE.USER_FREIGHT`、`AMT.GBV.FACE_VALUE`。

没有删除编码。停用的编码仍留在目录里，防止以后被重新解释。

## 13. 未识别金额

未识别观测要保存：RawAmountValue 的 presence、rawText、fieldPath，AmountSource 的平台、接口、消息类型、契约编号、契约版本、原始报文引用。typeCode 用 `AMT.UNK.UNIDENTIFIED`。

候选映射是一条 MappingStatus 为 `DRAFT` 的提议。它不产生已确认的标准观测。

一个字段有两个解释时，映射执行状态是 `CONFLICT`。两条候选都不是已确认观测，也不能两套都写成正式金额。原文仍然只有一份。

语义确认后，增加映射版本，让新观测指向正式类型。已经保存的未识别观测不改写。

## 14. 与业务指标的边界

用户实付不能作为营业额。商家应收不能作为营业额或结算额。退款不直接冲减营业额。优惠必须区分承担方，且没有五项平衡等式。结算额不能由订单金额推导。支付成功合计不能由应付代替。团购金额不进入订单成交额。

## 15. 四平台约束

| 平台 | 约束 |
| --- | --- |
| 京东 | 创建消息没有金额。销售价总额、应付、用户支付运费都还停在摘录。SRC-002 未关闭前不映射。两个包装字段不进入现有三个包装码 |
| 美团 | 原价、总价、门店配送费、商家应收、订单打包费、打包袋可以按契约文字进入对应编码。总价不产生实付。应收不用总价填充 |
| 淘宝闪购 | 217 只有消息名。金额路径保持未识别 |
| 富智云 | `payAmt` 不进入实付。`feeAmt` 单位未知。`expressAmt` 不进入门店配送费或用户运费，直到摘录被证明就是其中一种。团购八列不换算 |

## 16. 场景

1. 美团 `original_price` 可以成为 `AMT.ORD.ORIGINAL` 的观测。京东 `orderTotalMoney` 不能使用这个编码。
2. 美团 `total` 可以成为 `AMT.ORD.TOTAL` 的观测。不复制一条 `AMT.USR.PAID`。
3. 美团 `shipping_fee` 使用 `AMT.FEE.STORE_DELIVERY`。京东 `orderFreightMoney` 在确认前保持未识别，确认后才可能是 `AMT.FEE.USER_FREIGHT`。
4. 团购 `faceValue` 只准备使用 `AMT.GBV.FACE_VALUE`。单位保持未知，不乘 100。
5. 同一字段既像总价又像实付时，只保留冲突候选和原文，不生成两条已确认观测。

## 17. 设计决策

| 编号 | 决定 |
| --- | --- |
| D1 | 原价和销售价总额拆开 |
| D2 | 总价编码只表示契约中的“总价”，不是万能总价 |
| D3 | 停用混合配送费编码，拆成门店配送费和用户支付运费 |
| D4 | 包装保持三码，京东包装金额不硬塞进去 |
| D5 | 属性拆成性质、资金方向、承担主体、指标影响、符号约定 |
| D6 | 语义状态、映射资格、类型生命周期分开 |
| D7 | 团购第八列采用已核验的 `faceValue`，单位仍未知 |
| D8 | 全部新码和收紧后的旧码都还是 DRAFT |

## 18. 未决问题

AMT-001 至 AMT-010、PAY-001 至 PAY-003、REF-001 至 REF-004、GBV-001 至 GBV-003、SRC-001、SRC-002 仍然有效。

另外未决：用户实退没有定义；平台补贴没有来源；京东包装金额最终进入哪一个类型；富智云运费属于哪一种配送语义；团购八列的单位和列间关系；真实报文和生产金额行都还没有。

## 19. 一致性

与 06-03-01：Money、presence、三份生命周期、EvidenceStatus、SemanticStatus、MappingStatus 都保留。本文新增的 mappingEligibility 和 typeLifecycleStatus 是类型定义上的门禁，不替换观测上的证据状态和映射执行状态。没有把旧公式写成经营成交额。

与 R-09：应付不等于实付，总价不是实付，应收不是结算，团购单位未确认，退款不自动改营业额。没有冲突。

与 06-02-11 的“用户实付”列：不采纳。

## 20. 给 06-03-02-02 的输入

1. 一个字段路径只能指向一个未停用编码，或指向未识别。
2. 映射资格是 `NOT_UNTIL_CONFIRMED` 或 `DISALLOWED` 的编码，不能出现在生效映射里。
3. 不得把京东销售价总额映射到 `AMT.ORD.ORIGINAL`。
4. 不得把非“总价”字段映射到 `AMT.ORD.TOTAL`。
5. 不得使用 `AMT.FEE.DELIVERY`。
6. 团购八列在单位确认前不写转换。
7. 两个候选解释写成冲突，不写成两条已确认观测。
8. 淘宝金额路径保持未识别。

06-03-02-02 尚未创建。

## 21. 本轮修订记录

| 问题 | 处理 |
| --- | --- |
| 原价与销售价总额混用 | 原价收紧，新增销售价总额 |
| 总价被写成通用业务定义 | 改为只接收契约中的总价 |
| 配送费混用 | 停用旧码，拆成两码 |
| 一个方向字段承担过多含义 | 拆成五个维度，并允许不适用 |
| 确认、禁映射、停用混在一个状态 | 拆成三个状态 |
| 币种和单位要求含糊 | 改成枚举，并区分类型与实例 |
| 团购第八列未命名 | 按 R-07 和代码定为 `faceValue`，单位仍未知 |
| 未知字段可能变成两条已确认观测 | 区分为候选、冲突和正式观测 |

本轮只改本文件。未执行测试，未提交，未推送。
