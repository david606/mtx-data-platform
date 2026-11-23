# 06-03-02-01 MTX Data Platform V2 标准金额类型体系与编码规范

- 文档性质：详细设计建议。未经评审，不是已确认架构决策，也不是实现说明。
- 基础：`06-03-01 MTX Data Platform V2 多平台订单金额领域模型设计.md`。本文不推翻其中的 Money、RawAmountValue、ObservedAmount、AmountSource、三种状态和三份生命周期。
- 范围：只定义标准金额类型和编码。不设计字段映射表的全部行，不设计 Doris 表，不写 Java。
- V2 现状：仓库没有金额领域实现。trade-flow 的 Mapper、计算器和 Doris 列都不是 V2 能力。

相对链接：

- [06-03-01 金额领域模型](06-03-01%20MTX%20Data%20Platform%20V2%20多平台订单金额领域模型设计.md)
- [R-09 主报告](../06-02-R/06-02-R-09四平台金额、支付、退款与团购核销业务口径确认及证据闭环报告.md)
- [R-09 矩阵](../06-02-R/06-02-R-09金额业务口径确认与证据闭环矩阵.md)

## 1. 设计背景与目标

V2 要让京东、美团、淘宝闪购、富智云的金额在进入观测和计算之前，先落到稳定的类型编码上。编码回答“这是哪一种业务含义”，不回答“这个平台字段叫什么”，也不回答“营业额怎么算”。

本文要解决的问题是：同名或相近字段不能并成一个类型；未确认的语义不能被塞进已有类型；后续平台字段增加时，已发布编码保持不变。

事实标签在全文里这样用：

| 标签 | 含义 |
| --- | --- |
| 代码事实 | trade-flow 当前源码已经这样写。证据多为 E4 |
| 官方或摘录事实 | 已打开的官方页是 E1-A。仓库摘录是 E1-B，不证明摘录仍然有效 |
| 业务已确认 | 本文没有把任何跨平台经营公式标成已确认 |
| 待报文 | R-09 的真实报文列是“无” |
| 待业务或财务 | 矩阵结论为待业务确认、待财务确认、存在冲突或无法验证 |
| 设计建议 | 本文给出的类型编码和约束。不是已经上线的规则 |

资料冲突：`06-02-11` 的对比表把富智云 `payAmt`、京东 `orderBuyerPayableMoney`、美团 `total`、淘宝 `totalPrice` 放在“用户实付”一行，因为旧代码都写入了 `userPayAmount`。R-09 收尾和 06-03-01 写明这只是代码字段名，不是共同的业务定义。本文采用后两份文档。06-02-11 那一行只保留为代码归类，不进入类型定义。

## 2. 设计原则

1. 类型编码不包含平台名，也不包含 Doris 或 MySQL 字段名。
2. 一个 `typeCode` 只表达一种语义。名称相近仍分开。
3. 原始报文、标准观测、计算结果继续是 06-03-01 的三份记录。类型定义不是这三份记录本身。
4. 未确认语义使用 `AMT.UNK.UNIDENTIFIED`，或者使用单独编码但 `semanticStatus` 保持未确认。禁止为了“先有数”映射到用户实付、商家应收或结算额。
5. 经营成交额、净成交额、结算额可以有类型编码，表示将来的计算结果槽位。它们没有生效公式，也不能由订单字段直接映射得到。
6. `turnover_v1_trade_flow` 不分配标准类型编码。
7. 单位未知就不转换。精度未知就不舍入。缺失、null、空字符串和数字 0 继续按 06-03-01 的 presence 区分。
8. 编码发布后只增不改含义。停用编码，不把旧编码改去指另一种金额。

## 3. 金额分类体系

| 类别 | 在模型中的位置 | 是否适合作为标准类型 | 原因 |
| --- | --- | --- | --- |
| 订单原始金额 | 标准化观测 | 不适合合成一个类型 | “原始”只说明它来自报文。原价、总价、销售价总额不是同一个语义，必须拆开 |
| 订单商品金额 | 标准化观测 | 适合单独类型 | 商品原价合计与订单原价在旧代码里就不是同一列。官方是否相等，未确认 |
| 订单优惠金额 | 标准化观测 | 必须再拆 | 订单级优惠、优惠明细、商家承担、平台承担不是一个数 |
| 用户应付金额 | 标准化观测 | 适合 | 目前只有京东摘录使用这个词。其他平台不得借用 |
| 用户实付金额 | 标准化观测槽位 | 类型保留，但禁止当前映射 | 四平台都没有被 R-09 确认的唯一定义 |
| 用户退款金额 | 标准化观测 | 适合，且与实退分开 | 退款单上的金额不等于已经退给用户的金额 |
| 用户实退金额 | 资金结果槽位 | 类型保留，语义未确认 | R-09 只有退款成功金额。实退是否等于成功合计，待财务确认 |
| 商家应收金额 | 标准化观测 | 适合 | 美团 `wmPoiReceiveCent` 有 E1-A。它不是结算额 |
| 平台补贴金额 | 待确认观测 | 类型保留，当前无确认来源 | 不能把美团 `mtCharge` 或富智云红包直接叫补贴 |
| 商家承担优惠金额 | 标准化观测 | 适合，与补贴分开 | 旧代码有承担列。平衡等式未确认 |
| 平台承担优惠金额 | 标准化观测 | 适合，与补贴分开 | 同上 |
| 配送费 | 标准化观测 | 适合 | 各平台包含项不同，所以类型只有一个语义槽位，映射必须写明来源字段，不能把急送费再加一次 |
| 包装费 | 必须拆成多个类型 | 商品餐盒、订单打包费、打包袋不是同一个数 | 见 AMT-007 |
| 服务费 | 暂不并成一个可加类型 | 美团春节服务费官方写明已在配送费内。团购服务费单位未确认 | 若合成一个“服务费”再相加，会重复计算 |
| 支付手续费 | 资金流转 | 适合 | 只有富智云旧代码写入。单位待财务确认，PAY-003 |
| 结算金额 | 财务结果槽位 | 类型保留，禁止由订单金额推导 | 无公式 |
| 营业额相关指标 | 计算结果槽位 | 经营成交额、净成交额可以有编码。旧营业额公式不能有编码 | 见 AMT-001、REF-004 |
| 团购核销金额 | 独立观测，不进入订单金额对象 | 每个已点名字段单独编码。单位一律未知 | 06-03-01 规定团购不进入订单金额对象。类型仍要单独存在，避免被并进实付 |
| 充值金额 | 独立资金记录 | 本金与赠送分开 | 旧代码不把充值加入营业额。这是代码事实，不是经营口径 |
| 无法识别的金额 | 观测 | 必须有独立类型 | 这是兼容机制，不是废纸篓公式 |

层次对照：

| 层次 | 谁持有类型编码 | 例子 |
| --- | --- | --- |
| 原始业务语义 | 将来的 AmountMapping 把某契约字段指到一个 typeCode | 京东应付字段可以指向用户应付，不能指向用户实付 |
| 标准化观测金额 | ObservedAmount.proposedType | 一次报文里的一个字段 |
| 业务计算指标 | AmountCalculation.outputType | 经营成交额。当前没有 EFFECTIVE 规则 |
| 资金流转金额 | 支付或退款观测上的 typeCode | 支付成功金额、退款单金额 |
| 结算或财务金额 | 结算结果槽位 | 结算金额。当前不能从应收推导 |

## 4. 标准金额类型目录

编码格式是 `AMT.<域>.<概念>`。域是语义分组，不是平台。概念用稳定英文词，发布后不改拼写。

目录列里的 `confirmationStatus` 使用 06-03-01 的 SemanticStatus，外加“类型保留但禁止映射”。证据等级不写进 typeCode。

`amountDirection` 是业务方向，不是正负号。`signConvention` 默认 `PRESERVE_SOURCE`：标准值保留来源符号，不因为“这是退款”就改成负数。

聚合列的“禁止”表示：在语义未确认、单位未知或币种未知时，不能把多条加总成这个类型的合计。

### 4.1 目录

| typeCode | typeName | englishName | category | semanticDefinition | amountDirection | signConvention | currencyRequired | unitRequired | precisionPolicy | aggregationPolicy | sourceTraceRequired | confirmationStatus | applicableScope | remarks |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| AMT.ORD.ORIGINAL | 订单原价 | Order original price | 订单观测 | 平台称为原价或销售价总额的订单级金额。包含哪些费，以该字段自己的说明为准 | UNSPECIFIED | PRESERVE_SOURCE | 聚合前必须 | 聚合前必须 | 不隐式舍入 | 禁止跨字段相加 | 是 | 部分确认 | 订单 | 美团 `original_price` 含急送费，E1-A。京东 `orderTotalMoney` 只是摘录中的销售价总额，E1-B。淘宝无官方定义 |
| AMT.ORD.TOTAL | 订单总价 | Order total | 订单观测 | 平台称为总价的订单级金额 | UNSPECIFIED | PRESERVE_SOURCE | 聚合前必须 | 聚合前必须 | 不隐式舍入 | 禁止当成实付后相加 | 是 | 部分确认 | 订单 | 仅美团 `total` 有此官方词，含急送费。该行未写“元”，也未写用户实付。AMT-003 存在冲突 |
| AMT.ORD.GOODS_ORIGINAL | 商品原价 | Goods original amount | 订单商品 | 商品行原价按平台自己的商品口径汇总后的金额 | UNSPECIFIED | PRESERVE_SOURCE | 聚合前必须 | 聚合前必须 | 不隐式舍入 | 未确认前不与订单原价互替 | 是 | 待平台确认 | 订单商品 | 旧代码有汇总。是否等于订单原价，没有官方等式 |
| AMT.ORD.AFTER_DISCOUNT | 优惠后订单金额 | Order amount after discount | 订单观测 | 优惠之后的订单级金额。它不是用户实付，也不是总价的别名 | UNSPECIFIED | PRESERVE_SOURCE | 聚合前必须 | 聚合前必须 | 不隐式舍入 | 禁止 | 是 | 待平台确认 | 订单 | 京东 `orderGoodsMoney` 在旧代码注释中写明未在官方参考单独登记。不能当成已确认 |
| AMT.ORD.DISCOUNT_ORDER | 订单级优惠 | Order-level discount | 订单优惠 | 订单头上的优惠，不含尚未说明的单品、运费或包装优惠 | MERCHANT_OR_PLATFORM_BEAR | PRESERVE_SOURCE | 聚合前必须 | 聚合前必须 | 保留来源符号 | 禁止把明细再加一次 | 是 | 待确认 | 订单 | 京东摘录写明不含单品和运费优惠，E1-B。富智云旧代码禁止把优惠行再加进 `fullOrderDisAmt`。这是代码事实 |
| AMT.ORD.DISCOUNT_DETAIL | 优惠明细金额 | Discount detail amount | 订单优惠 | 一条优惠明细自己的金额 | UNSPECIFIED | PRESERVE_SOURCE | 聚合前必须 | 聚合前必须 | 保留来源符号 | 未确认前不加总成订单级优惠 | 是 | 待确认 | 优惠明细 | 美团 `reduce_fee` 与骑手元素可能同数组。不能把无优惠额的骑手元素当成 0 元优惠。AMT-009 |
| AMT.USR.PAYABLE | 用户应付金额 | User payable amount | 用户资金 | 下单时平台写明的用户应付。不等于最终付出，也不等于支付成功合计 | USER_PAY | PRESERVE_SOURCE | 聚合前必须 | 聚合前必须 | 不隐式舍入 | 禁止与实付、支付成功额相加或互替 | 是 | 部分确认 | 订单 | 仅京东摘录 `orderBuyerPayableMoney`，单位分，E1-B。未写是否含餐盒和运费。摘录版本仍待确认 |
| AMT.USR.PAID | 用户实付金额 | User paid amount | 用户资金 | 用户最终实际付出的金额 | USER_PAY | PRESERVE_SOURCE | 聚合前必须 | 聚合前必须 | 不隐式舍入 | 禁止 | 是 | 待业务确认 | 订单或支付 | 当前没有可映射字段。美团总价、京东应付、淘宝 `totalPrice`、富智云 `payAmt` 都不得直接映射到本编码 |
| AMT.PAY.SUCCESS | 支付成功金额 | Successful payment amount | 资金流转 | 状态已被确认为支付成功的流水金额。合计规则未生效 | USER_PAY | PRESERVE_SOURCE | 聚合前必须 | 聚合前必须 | 不把未知状态当成 0 | 仅同币种同单位且状态定义确认后才可加总 | 是 | 待业务确认 | 支付流水 | 与订单侧金额的差异只记录。PAY-001。京东原收款使用应付字段，不能因此变成支付成功额 |
| AMT.RFD.DOCUMENT | 用户退款金额 | Refund document amount | 资金流转 | 某一张退款单上的金额，不表示已经成功 | USER_REFUND | PRESERVE_SOURCE | 聚合前必须 | 聚合前必须 | 不截断小数 | 不把未成功单加进成功合计 | 是 | 待平台确认 | 退款单 | 不等于实退，也不等于退款成功合计。京东售后小数问题见 REF-001 |
| AMT.RFD.SUCCEEDED | 退款成功金额 | Successful refund amount | 资金流转 | 状态已确认成功的退款合计槽位 | USER_REFUND | PRESERVE_SOURCE | 聚合前必须 | 聚合前必须 | 禁止用截断代替精度规则 | 旧代码按退款号相加只是 E4 | 是 | 待财务确认 | 退款单 | REF-003。不自动冲减经营成交额 |
| AMT.RFD.USER_ACTUAL | 用户实退金额 | Amount actually returned to user | 资金流转 | 实际退回到用户的金额 | USER_REFUND | PRESERVE_SOURCE | 聚合前必须 | 聚合前必须 | 不隐式舍入 | 禁止 | 是 | 待财务确认 | 退款 | R-09 没有单独定义。禁止把退款成功合计改名为实退 |
| AMT.MCH.RECEIVABLE | 商家应收金额 | Merchant receivable | 商家资金 | 平台定义的商家应收 | MERCHANT_RECEIVE | PRESERVE_SOURCE | 聚合前必须 | 聚合前必须 | 不隐式舍入 | 禁止用总价或营业额填充 | 是 | 部分确认 | 订单 | 美团 `wmPoiReceiveCent`，单位分，可空，不等于总价，E1-A。不是结算额。AMT-006 |
| AMT.PLT.SUBSIDY | 平台补贴金额 | Platform subsidy | 平台资金 | 平台给予商家或用户的补贴。承担优惠不等于补贴 | PLATFORM_SUBSIDY | PRESERVE_SOURCE | 聚合前必须 | 聚合前必须 | 不隐式舍入 | 禁止 | 是 | 待业务确认 | 未指定 | 当前没有已确认来源字段 |
| AMT.DSC.MERCHANT_BEAR | 商家承担优惠金额 | Merchant-borne discount | 优惠承担 | 优惠中由商家承担的部分 | MERCHANT_BEAR | PRESERVE_SOURCE | 聚合前必须 | 聚合前必须 | 保留来源符号。旧代码把负数收成 0 或取绝对值，不作为本类型规则 | 五项平衡式未确认，禁止互推 | 是 | 待业务确认 | 优惠明细 | 美团 `poiCharge`、淘宝 `restaurantPart`、富智云仅 `ROUNDING` 行。都是代码事实 |
| AMT.DSC.PLATFORM_BEAR | 平台承担优惠金额 | Platform-borne discount | 优惠承担 | 优惠中由平台承担的部分 | PLATFORM_BEAR | PRESERVE_SOURCE | 聚合前必须 | 聚合前必须 | 同上 | 禁止当成平台补贴 | 是 | 待业务确认 | 优惠明细 | 美团 `mtCharge`、淘宝 `elemePart`、富智云红包行。代码事实 |
| AMT.DSC.USER_BEAR | 用户承担优惠金额 | User-borne discount | 优惠承担 | 活动原文写明由用户承担的部分 | USER_PAY | PRESERVE_SOURCE | 聚合前必须 | 聚合前必须 | 不使用订单差额倒挤 | 禁止 | 是 | 待平台确认 | 优惠明细 | 淘宝旧代码写明不能用实付差额代替。官方字段页仍缺 |
| AMT.FEE.DELIVERY | 配送费 | Delivery fee | 费用 | 订单上的配送相关金额。包含项以字段说明为准 | UNSPECIFIED | PRESERVE_SOURCE | 聚合前必须 | 聚合前必须 | 不隐式舍入 | 禁止再加已声明包含的费用 | 是 | 部分确认 | 订单 | 美团门店配送费含春节服务费，E1-A。急送费是否也在其中，未证明。AMT-008 |
| AMT.FEE.PACKAGE_ITEM | 商品餐盒费 | Item box fee | 包装 | 按商品餐盒统计的金额 | UNSPECIFIED | PRESERVE_SOURCE | 聚合前必须 | 聚合前必须 | 不隐式舍入 | 不覆盖订单打包费 | 是 | 存在冲突 | 订单商品 | 美团 mode=0 的回退与旧代码接近，但 mode 1/2 冲突。AMT-007 |
| AMT.FEE.PACKAGE_ORDER | 订单打包费 | Order package fee | 包装 | 按订单或口袋收取的打包费 | UNSPECIFIED | PRESERVE_SOURCE | 聚合前必须 | 聚合前必须 | 不隐式舍入 | 有该字段时不得用商品餐盒覆盖 | 是 | 部分确认 | 订单 | 美团 `package_bag_fee`，E1-A |
| AMT.FEE.PACKAGE_BAG | 打包袋金额 | Bag settlement amount | 包装 | 打包袋金额，不是订单打包费 | UNSPECIFIED | PRESERVE_SOURCE | 聚合前必须 | 聚合前必须 | 不把分和元两个字段加在一起 | 禁止与订单打包费合并 | 是 | 部分确认 | 订单 | 美团 `package_bag_money` 与 `package_bag_money_yuan` 是同一袋的两种单位表达。映射时只能选一个来源，不能相加。E1-A |
| AMT.FEE.PAYMENT | 支付手续费 | Payment fee | 资金流转 | 支付渠道收取的手续费 | FEE | PRESERVE_SOURCE | 聚合前必须 | 聚合前必须 | 单位未确认前不转换，也不规定负数 | 禁止 | 是 | 待财务确认 | 支付或充值 | 仅富智云 `feeAmt`。支付摘录未写单位。PAY-003 |
| AMT.FIN.SETTLEMENT | 结算金额 | Settlement amount | 财务 | 按结算规则确认的金额 | MERCHANT_RECEIVE | 未确认 | 聚合前必须 | 聚合前必须 | 未定 | 禁止由订单字段推导 | 是 | 待财务确认 | 结算 | 无公式。商家应收不能填入 |
| AMT.IND.TURNOVER | 经营成交额 | Business turnover | 指标 | 业务书面确认后的经营成交额 | INDICATOR | 未确认 | 聚合前必须 | 聚合前必须 | 未定 | 禁止映射，禁止用旧公式填充 | 是 | 待业务确认 | 订单指标 | 不是 `turnover_v1_trade_flow`。AMT-001 |
| AMT.IND.NET | 净成交额 | Net turnover | 指标 | 经营成交额再按已确认规则考虑退款或其他扣减 | INDICATOR | 未确认 | 聚合前必须 | 聚合前必须 | 未定 | 禁止把原额减退款直接写入 | 是 | 待业务确认 | 订单指标 | REF-004 |
| AMT.RCG.PRINCIPAL | 充值本金 | Recharge principal | 充值 | 充值本金。冲正是另一条方向，不是把本金改成负数的别名 | RECHARGE | PRESERVE_SOURCE | 聚合前必须 | 聚合前必须 | 旧代码取绝对值只是 E4 | 不进入订单营业额 | 是 | 待业务确认 | 充值 | 仅富智云旧代码。单位待确认 |
| AMT.RCG.GIFT | 充值赠送 | Recharge gift | 充值 | 充值附带的赠送金额 | RECHARGE | PRESERVE_SOURCE | 聚合前必须 | 聚合前必须 | 不默认取绝对值 | 不进入订单营业额 | 是 | 待业务确认 | 充值 | 旧代码不取绝对值。是否冲减商家收入，只是富智云代码行为 |
| AMT.GBV.BUY_PRICE | 团购购买价 | Group-buy purchase amount | 团购 | `buyPriceAmt` 的原样金额。业务含义未解释 | UNSPECIFIED | PRESERVE_SOURCE | 聚合前必须 | 必须，但当前为未知 | 保留原标度，scale 规则未作为业务舍入 | 禁止换算，禁止与订单实付相加 | 是 | 待平台确认 | 团购核销 | 单位未确认。GBV-001 |
| AMT.GBV.FACE | 团购面额 | Group-buy face amount | 团购 | `faceAmt` 的原样金额 | UNSPECIFIED | PRESERVE_SOURCE | 聚合前必须 | 未知 | 同上 | 禁止与购买价互算 | 是 | 待平台确认 | 团购核销 | 代码不解释它和购买价的差 |
| AMT.GBV.COUPON | 团购券金额 | Group-buy coupon amount | 团购 | `couponAmt` 的原样金额 | UNSPECIFIED | PRESERVE_SOURCE | 聚合前必须 | 未知 | 同上 | 禁止 | 是 | 待平台确认 | 团购核销 | 不解释与红包的关系 |
| AMT.GBV.PLAT_HONGBAO | 团购平台红包 | Group-buy platform red packet | 团购 | `platHongBao` 的原样金额 | UNSPECIFIED | PRESERVE_SOURCE | 聚合前必须 | 未知 | 同上 | 禁止当成订单平台承担 | 是 | 待平台确认 | 团购核销 | 名称不能升级成订单优惠定义 |
| AMT.GBV.MCHNT_INCOME | 团购商家收入 | Group-buy merchant income | 团购 | `mchntIncome` 的原样金额 | UNSPECIFIED | PRESERVE_SOURCE | 聚合前必须 | 未知 | 同上 | 禁止当成商家应收或结算额 | 是 | 待平台确认 | 团购核销 | 代码不扣服务费 |
| AMT.GBV.THIRD_MCHNT_INCOME | 团购第三方商家收入 | Group-buy third merchant income | 团购 | `thirdMchntIncome` 的原样金额 | UNSPECIFIED | PRESERVE_SOURCE | 聚合前必须 | 未知 | 同上 | 禁止与上一列相加 | 是 | 待平台确认 | 团购核销 | 两列关系未解释 |
| AMT.GBV.THIRD_SERVICE_FEE | 团购第三方服务费 | Group-buy third service fee | 团购 | `thirdServiceFee` 的原样金额 | FEE | PRESERVE_SOURCE | 聚合前必须 | 未知 | 同上 | 禁止并入订单服务费或配送费 | 是 | 待平台确认 | 团购核销 | 不是美团春节服务费 |
| AMT.UNK.UNIDENTIFIED | 未识别金额 | Unidentified amount | 未识别 | 尚不能对应到任一已发布语义的原始金额 | UNSPECIFIED | PRESERVE_SOURCE | 否。未知就保持未知 | 否。未知就保持未知 | 不转换、不舍入、不补 0 | 禁止聚合 | 是 | 无法验证 | 任何对象 | 保留字段路径、原文和契约。确认语义后新增映射版本，不改这个编码的含义 |

06-02-08 写明团购有八个 `DECIMAL(24,6)` 金额列，并点名了上表这些字段。本轮没有在该文里读到其余列名。未点名的列不得编造 typeCode，先用 `AMT.UNK.UNIDENTIFIED`，直到列名被引用到。

DDL 注释写“分”只是旧库注释。代码原样保存且不换算。单位仍是待平台确认。本目录不把这些类型定义成元或分。

### 4.2 十条完整定义示例

下列示例把边界写全。目录表与这里冲突时，以这里的“不能映射”为准。

#### AMT.USR.PAYABLE

用户应付金额。语义是下单时平台要求用户应付的数。现在只有京东仓库摘录把 `orderBuyerPayableMoney` 写成“用户应付金额，单位分”。这是 E1-B，不是 E1-A，也不能证明摘录仍是现行接口。它未说明是否含餐盒和运费。旧代码把它写入 `userPayAmount` 并生成原收款，这不能成为“它等于实付”的证据。本类型禁止映射美团 `total`、淘宝 `totalPrice`、富智云 `payAmt`。

#### AMT.USR.PAID

用户实付金额。语义是用户最终实际付出的金额。R-09 没有确认四平台中哪一个字段等于它。富智云摘录对 `payAmt` 有组成说明，仍只是 E1-B，且不是跨平台定义。因此本类型当前 `mappingAllowed=false`。任何“字段名里有 pay”的映射都要被拒绝到未识别或冲突，而不是落入本编码。

#### AMT.ORD.TOTAL

订单总价。语义只采用美团官方页对 `total` 的描述：总价，金额包含 1 对 1 急送费。该行没有写用户实付，也没有写单位“元”。旧代码同时把它写入优惠后金额、`userPayAmount` 和支付事实，与官方语义存在冲突，AMT-003。标准类型只接收“总价”这一个解释。优惠后金额若要保留，使用 `AMT.ORD.AFTER_DISCOUNT`，并且在官方定义出现之前保持待确认，不能由总价自动复制一份。

#### AMT.MCH.RECEIVABLE

商家应收金额。美团 `wmPoiReceiveCent` 的官方描述是商家应收，单位分，可空，不等于总价。商家承担急送营销时已经扣减。它不是结算金额，不是营业额，也不能在字段缺失时用总价填充。淘宝旧代码的 `income`、富智云“实付减退款和赠送”都不得映射到本编码。

#### AMT.FIN.SETTLEMENT

结算金额。语义是按平台结算或财务结算规则确认的金额。现在没有规则，也没有生产结算样本。本类型存在，只为了防止有人把应收或实付改名成结算。它不能由订单金额推导，也不能作为映射目标。

#### AMT.IND.TURNOVER

经营成交额。语义要等业务书面定义。旧系统四套公式的结果没有类型编码。美团原价减配送费、京东应付加订单级优惠，都只能留在 06-03-01 的参照规则里。参照规则的输出若被保存，必须标成“仅旧代码”，不能写入本 typeCode。

#### AMT.FEE.DELIVERY

配送费。美团 `shipping_fee` 是门店配送费，且春节服务费已包含在内，不能再加 `phf_ssf`。京东摘录的 `orderFreightMoney` 是“用户实际支付运费”，E1-B，它是运费不是整单应付。淘宝 `deliverFee` 无官方定义。富智云 `expressAmt` 只是摘录和代码映射。四者可以在各自契约里映射到本类型，但映射说明必须引用各自证据，不能写成“四个配送费定义相同”。

#### AMT.FEE.PACKAGE_ORDER

订单打包费。美团在 `package_bag_fee` 存在时要求直接使用，禁止用商品餐盒行覆盖。它不是 `package_bag_money`。商品餐盒使用 `AMT.FEE.PACKAGE_ITEM`，打包袋使用 `AMT.FEE.PACKAGE_BAG`。京东 `canteenMoney` 与 `packagingMoney` 也不得都映射成订单打包费。前者更接近商品餐盒，且选用关系仍是 AMT-002，未完成业务确认。

#### AMT.FEE.PAYMENT

支付手续费。只有富智云旧代码把 `feeAmt` 写入手续费。支付摘录写了 Long，没写是不是分。负数是否允许，待财务确认。单位保持未知，禁止按“其他富智云字段是分”来推断。

#### AMT.GBV.BUY_PRICE

团购购买价。只指核销记录里的 `buyPriceAmt`。代码按十进制原样保存，不换算。生产列是 `DECIMAL(24,6)`，注释写分，但 R-09 认为注释不是单位证明。本类型的单位是未知。它不进入订单金额对象，也不与 `AMT.USR.PAID` 相加。

#### AMT.RFD.SUCCEEDED

退款成功金额。它是成功状态确认之后的合计槽位，不是退款单原文，也不是用户实退。旧代码按退款号累加成功额，低版本不覆盖，成功不被后来的非成功撤销。这是 E4。是否就是最终应退，待财务确认。未成功的单据留在 `AMT.RFD.DOCUMENT`，不进入本类型。

#### AMT.UNK.UNIDENTIFIED

未识别金额。语义就是“还不知道”。它必须带字段路径、原始 presence、原始文字、平台、接口、消息类型、契约版本和映射版本。后续确认后，新建或升版映射，把新观测指到正式类型。历史未识别观测不改写。

## 5. 编码规范

| 规则 | 约定 |
| --- | --- |
| 形式 | `AMT.<域>.<概念>`。域取 ORD、USR、PAY、RFD、MCH、PLT、DSC、FEE、FIN、IND、RCG、GBV、UNK |
| 稳定性 | 编码一旦在本文件被评审采用，拼写不再修改。含义变了就停用旧码、发布新码 |
| 版本 | 类型定义若只补充说明、不改变语义，使用类型定义版本号，不改 typeCode。版本号放在类型注册记录上，不放进编码字符串 |
| 平台无关 | 禁止 `AMT.MEITUAN.TOTAL` 这类编码。平台差异写在 AmountMapping |
| 字段无关 | 禁止把 `user_pay_amount` 或 `wmPoiReceiveCent` 用作 typeCode |
| 新增平台 | 新平台复用已确认语义的编码。语义未确认则用 `AMT.UNK.UNIDENTIFIED` |
| 新增类型 | 使用新的概念词。不占用已停用编码 |
| 停用 | `mappingStatus` 对应该类型的映射改为 `RETIRED`。旧观测仍指向旧码 |
| 复用 | 禁止把停用编码改指另一种金额 |

建议的类型注册记录只含：typeCode、typeDefinitionVersion、semanticDefinition、confirmationStatus、retiredAt。它不是 Maven 模块，也不是表结构。

## 6. 金额类型与金额实例边界

不要把下列属性塞进 typeCode。

| 属性 | 放在哪里 | 原因 |
| --- | --- | --- |
| 金额类型 | 类型注册，以及观测或计算结果上的引用 | 类型是定义。实例只引用编码和类型定义版本 |
| 金额数值 | Money.decimalValue，或仅留在 RawAmountValue | 类型没有数值 |
| 币种 | Money.currency | 类型只声明聚合前必须已知。实例可以是 `UNKNOWN` |
| 单位 | Money.unit，原始声明在 RawAmountValue.declaredUnitText | 类型不写死元或分，除非该语义本身已被官方写成唯一单位。当前没有这种跨平台类型 |
| 精度 | Money.scale，计算时另看规则的 calculationPrecision | 类型只声明“不隐式舍入” |
| 正负方向 | 实例保留来源符号。类型给出 signConvention | 方向枚举不是符号 |
| 来源平台 | AmountSource.platform | 同一类型可来自多个平台 |
| 来源接口 | AmountSource.sourceInterface | 与 06-03-01 一致 |
| 来源字段路径 | RawAmountValue.fieldPath | 同名字段不同路径不能合成 |
| 来源契约版本 | AmountSource.sourceContractVersion | 不是 mappingVersion |
| 映射规则及版本 | ObservedAmount.mappingId 与 mappingVersion | 不是 typeCode 的一部分 |
| 业务对象关联 | ObservedAmount 的 businessObjectType 与 businessObjectId | 订单、支付、退款、团购、充值分开 |
| 观测时间 | AmountSource.receivedAt | 技术时间，不改变语义 |
| 证据状态 | ObservedAmount.evidenceStatus | E1-A 不能写成业务已确认 |
| 业务语义确认状态 | 类型上是跨平台语义状态。实例上是这次映射的 semanticStatus | 两者可以不同。类型未确认时，实例不得标成已确认 |

一次观测因此是：一个 typeCode，加一份 Money 或空的 Money，加一份 RawAmountValue，加一份 AmountSource。计算结果另用 AmountCalculation，输出类型只能是允许作为计算输出的编码，当前 `AMT.IND.TURNOVER`、`AMT.IND.NET`、`AMT.FIN.SETTLEMENT`、`AMT.PAY.SUCCESS`、`AMT.RFD.SUCCEEDED` 都还没有 EFFECTIVE 规则。

## 7. 金额方向和正负号规范

三套方向分开记录。

| 场景 | 资金方向 | 来源数值的符号 | 指标增减 |
| --- | --- | --- | --- |
| 用户付款 | 用户付出。类型用 `USER_PAY` | 保留报文符号。不强制为正 | 不自动增加经营成交额 |
| 用户退款 | 退回用户。类型用 `USER_REFUND` | 保留报文符号。不强制为负 | 不自动减少经营成交额 |
| 商家收款 | 商家应收方向 | 保留报文符号 | 不等于营业额增加 |
| 平台补贴 | 平台支出方向 | 未确认前保留原文 | 未确认前不影响成交额 |
| 商家优惠承担 | 商家承担，不是一笔独立收款 | 保留原文。旧代码把负数收成 0 只是 E4 | 不单独变成负的营业额 |
| 平台优惠承担 | 平台承担 | 同上 | 不等于平台补贴 |
| 支付手续费 | 费用 | 正负未确认。PAY-003 | 不从实付里自动扣 |
| 充值 | 充值本金或赠送 | 旧代码对本金取绝对值、对赠送不取绝对值，只是 E4。V2 默认保留原文 | 不进入订单成交额 |
| 退款冲减 | 只有在净成交额规则生效后才有指标方向 | 退款单本身仍保留原符号 | 当前没有这条规则。REF-004 |
| 营业额调整 | 指标方向，未定义 | 富智云套餐调整的正负号是旧代码行为，AMT-005 | 不把调整额映射成实付的负数 |

标准化之后允许保存带符号的 Money，因为来源里确实可能出现负数。允许保存，不等于把退款定义成负数，也不等于负数优惠要取绝对值。是否接受负优惠，待业务确认。

原始金额始终按 presence 和 rawText 另存。标准化符号只存在于 Money。两者不一致时，以未识别或冲突处理，不静默改写原文。

## 8. 币种、单位与精度规范

| 项目 | 规范 |
| --- | --- |
| 币种 | 与单位分开。当前业务以人民币为主，但字段没写币种时实例用 `UNKNOWN`。不能由“分”推出 `CNY` |
| 单位 | 实例使用 `YUAN`、`FEN`、`UNKNOWN`。类型定义不替具体字段宣布单位 |
| 数值 | 使用精确定点小数。禁止用二进制浮点数表达金额。Java 实现若出现，应使用 `BigDecimal`，且禁止用 `double` 构造。本文不产生实现代码 |
| 元与分 | 只有单位已经被该契约声明时才允许转换。转换必须是显式规则，写明乘以或除以 100、舍入方式和溢出时的结果。溢出不得回绕。未声明单位时不转换 |
| 旧系统元转分 | 美团、淘宝旧代码使用 `movePointRight(2)` 后 `HALF_UP`。这是 E4，不是 V2 默认舍入 |
| 旧系统整数读取 | 富智云、京东订单金额遇到小数会变成空，不是四舍五入。京东售后 `longValue()` 会截断。两种行为都不得写成 V2 精度策略。REF-001 仍待平台确认 |
| 小数位 | 未确认前不主动 `setScale`。来源有几位就保留几位。精度未知则 Money.scale 为空 |
| 舍入 | 默认不舍入。将来的规则必须同时给出 roundingMode 和 roundingScale |
| 单位未知 | Money.unit=`UNKNOWN`。禁止相加、相减和单位转换 |
| 精度未知 | 保留 RawAmountValue。不补 0，不截断 |
| 币种扩展 | 新币种只扩展币种代码。不改变 typeCode |
| 团购八列 | `DECIMAL(24,6)` 是旧库列类型。注释中的“分”不是单位证明。禁止定义成元或分，禁止规定转换 |

空值沿用 06-03-01：`ABSENT`、`PRESENT_NULL`、`EMPTY_STRING`、`NUMERIC_ZERO` 两两不同。`NUMERIC_ZERO` 可以成为数值 0。前三种不能。

## 9. 未知类型与扩展机制

1. 映射找不到已确认语义时，typeCode 使用 `AMT.UNK.UNIDENTIFIED`，MappingStatus 为 `UNMAPPED` 或保留为草稿映射。原始路径、presence、契约编号和契约版本仍要写入。
2. 不允许把未知字段映射到 `AMT.USR.PAID`、`AMT.MCH.RECEIVABLE`、`AMT.FIN.SETTLEMENT` 或 `AMT.IND.TURNOVER`。
3. 语义后来确认时，增加映射版本，让新观测指向正式类型。旧的未识别观测保持原样，便于对照。
4. 新平台或新字段不修改已有 typeCode 的定义。需要新语义就发布新编码。
5. 一个原始字段有两种解释时，例如美团 `total` 既被官方称为总价，又被旧代码当作实付：生成冲突，SemanticStatus 为 `存在冲突`，MappingStatus 为 `CONFLICT`。可以保留两条候选观测，但两条都不得标成已确认。不选其中一个当事实。
6. 同一业务对象上的订单金额、支付金额、退款金额使用不同 typeCode，也使用不同 businessObjectType。不能因为金额数字相同就合成一条。

## 10. 标准金额与业务指标边界

| 问题 | 结论 | 依据 |
| --- | --- | --- |
| 用户实付能否直接作为营业额 | 不能。实付本身都未确认，更不能当成经营成交额 | AMT-001，06-03-01 |
| 商家应收能否直接作为营业额 | 不能。应收也不是结算额 | AMT-006，美团 E1-A |
| 退款是否直接冲减营业额 | 不能。旧营业额公式不减。富智云商家收入会减，只是另一条代码。净成交额无公式 | REF-004 |
| 优惠是否要区分承担方 | 要。商家承担、平台承担、用户承担、其他承担是不同编码。五项是否平衡，代码没有等式，待业务确认 | 06-02-04 |
| 结算额能否由订单金额推导 | 不能 | 无财务规则 |
| 支付成功合计能否由订单应付代替 | 不能 | PAY-001 |
| 团购金额能否进入订单成交额 | 不能。两条链路在旧代码里也没有桥接。这仍不是经营确认 | 06-02-08，GBV-001 |

原始金额是 RawAmountValue。标准金额是带 typeCode 的 ObservedAmount。派生金额是 AmountCalculation 的输出。业务指标只包括 `AMT.IND.*`，且当前不可计算。财务结算金额只包括 `AMT.FIN.SETTLEMENT`，当前不可计算。

## 11. 四平台已知问题与适用约束

| 平台 | 约束 |
| --- | --- |
| 京东 | `orderCreated` 消息没有金额，E1-A。金额字段来自查询摘录，E1-B，且与消息是否同一契约仍是 SRC-002。`orderBuyerPayableMoney` 只能进入 `AMT.USR.PAYABLE`。`canteenMoney` 与 `packagingMoney` 不能合成一个包装类型。售后字段不在 SRC-03，小数截断不是标准精度 |
| 美团 | `total` 只进入 `AMT.ORD.TOTAL`。`wmPoiReceiveCent` 只进入 `AMT.MCH.RECEIVABLE`。三种包装分开。春节服务费不单独再加。回调是不是官方已确认订单页，仍未证明。没有报文 |
| 淘宝闪购 | 217 只有消息名，E1-A。`originalPrice`、`totalAmount`、`totalPrice`、`deliverFee`、`income` 都没有官方定义。在字段页出现之前，这些路径使用 `AMT.UNK.UNIDENTIFIED`，SRC-001、AMT-004 |
| 富智云 | 摘录是 E1-B。`payAmt` 不得直接进入 `AMT.USR.PAID`。`feeAmt` 单位未知。`packagePriceDisAmt` 的正负待确认，不能当成经营调整。团购八列单位未知，且不进入订单类型。充值不进入订单成交额 |

跨平台同名字段，例如都叫 total、payAmt 或 income，默认视为不同契约上的不同路径。只有语义状态成为“已确认相同”之后，才允许指向同一个 typeCode。当前没有这种跨平台已确认。

## 12. 典型场景

1. 京东详情出现 `orderBuyerPayableMoney=1234`，摘录单位为分。保存原文 presence=`NUMERIC_TEXT` 或数字形态，标准观测类型为 `AMT.USR.PAYABLE`，单位在摘录有效性确认前仍应视为“摘录声明为分、契约版本未最终确认”。在 SRC-002 关闭前，这条映射保持待平台确认，不能复制成 `AMT.USR.PAID`。
2. 美团回调只有 `total`，详情没有 `wmPoiReceiveCent`。总价观测可以是 `AMT.ORD.TOTAL`。应收观测不存在，不能写成 0，也不能写成总价。
3. 美团同时给出 `package_bag_fee` 和商品餐盒。前者是 `AMT.FEE.PACKAGE_ORDER`，后者是 `AMT.FEE.PACKAGE_ITEM`。官方要求有订单打包费时不要用商品行覆盖。两条都留着，不做覆盖。
4. 退款单金额先到，状态未知。类型是 `AMT.RFD.DOCUMENT`。不产生 `AMT.RFD.SUCCEEDED`，也不把经营成交额减掉。
5. 团购 `buyPriceAmt` 是小数。类型是 `AMT.GBV.BUY_PRICE`，单位 `UNKNOWN`。不乘 100，不写成订单实付。
6. 新平台字段无法归类。类型是 `AMT.UNK.UNIDENTIFIED`，路径和契约版本保留。

场景 1 里的单位处理是设计建议：E1-B 可以记为“摘录声明”，但在摘录被证明为现行契约之前，不把该声明升级成可聚合的已确认单位。若实现时需要更严，可以连单位也保持 `UNKNOWN`，直到平台确认。两种都不允许换算到另一个类型。

## 13. 设计决策记录

这些是建议，不改变 ADR-0002，也不新增模块。

| 编号 | 建议 | 没有选择的做法 |
| --- | --- | --- |
| D1 | 类型编码使用 `AMT.<域>.<概念>` | 按平台或按数据库列编码 |
| D2 | 用户应付、用户实付、支付成功、商家应收、结算、经营成交额全部分开 | 沿用旧代码的 `userPayAmount` 作为统一实付 |
| D3 | 旧营业额公式不分配 typeCode | 建一个 `AMT.IND.TURNOVER_V1` 并当成经营额 |
| D4 | 未识别类型允许长期存在 | 上线前必须把所有字段映射进已有类型 |
| D5 | 团购类型独立且单位未知 | 按 DDL 注释把八列定义成分并换算 |
| D6 | 退款不默认记负数 | 规定所有退款金额为负 |
| D7 | 淘宝尚未有官方字段的路径先未识别 | 按旧 Mapper 字段名发布正式语义 |

## 14. 未决问题清单

| 编号 | 问题 | 影响的类型 |
| --- | --- | --- |
| AMT-001 | 经营成交额没有书面定义 | `AMT.IND.TURNOVER` |
| AMT-002 | 京东餐盒费和包装金额取哪列 | 两个包装类型的京东映射 |
| AMT-003 | 美团总价是不是用户支付金额 | 禁止进入 `AMT.USR.PAID` |
| AMT-004、SRC-001 | 淘宝金额字段无定义 | 相关路径保持未识别 |
| AMT-005 | 套餐调整能否为负 | 不单建经营调整类型 |
| AMT-006、AMT-010 | 应收是否每单都有 | `AMT.MCH.RECEIVABLE` 可空 |
| AMT-007 | 打包费口径冲突 | 三个包装类型不得合并 |
| AMT-008 | 急送费是否也在配送费内 | `AMT.FEE.DELIVERY` 的包含项 |
| AMT-009 | 骑手费用和预付 | 不进优惠合计 |
| PAY-001 | 订单侧金额与支付成功合计 | `AMT.PAY.SUCCESS` |
| PAY-002 | 组合支付 | 不拆类型，直到有报文 |
| PAY-003 | 手续费单位 | `AMT.FEE.PAYMENT` |
| REF-001 至 REF-004 | 小数、上限、累计、是否冲减成交额 | 退款三个类型和净成交额 |
| GBV-001 至 GBV-003 | 团购单位、平衡、跨月 | 全部 `AMT.GBV.*` |
| SRC-002 | 京东两份文档是不是同一契约 | 京东映射能否生效 |
| 无编号 | 平台补贴没有来源定义 | `AMT.PLT.SUBSIDY` |
| 无编号 | 用户实退没有定义 | `AMT.RFD.USER_ACTUAL` |
| 无编号 | 真实报文无，生产金额行未抽样 | 所有类型都还不能用样本证明 |

## 15. 与 06-03-01 的一致性检查

| 06-03-01 约束 | 本文 |
| --- | --- |
| Money 只含数值、币种、单位、标度 | 这些属性不进入 typeCode |
| presence 区分缺失、null、空字符串和 0 | 第 8 节沿用 |
| 三个生命周期分开 | 类型引用可以出现在观测或计算上，但不把保存状态写进类型 |
| EvidenceStatus、SemanticStatus、MappingStatus 分开 | 类型目录只用语义状态。证据留在观测 |
| 映射四编号不混用 | 类型编码也不是这四个编号 |
| 未确认公式不生效 | 指标和结算类型明确禁止映射、禁止推导 |
| 团购不进订单金额对象 | 团购有独立编码，作用域是核销 |
| 十模块和端口不变 | 本文不新增模块，不指定存储 |
| 京东应付、美团总价、美团应收的边界 | 分别落到三个 typeCode，并写明禁止合并 |

没有发现必须推翻 06-03-01 的冲突。06-02-11 的“用户实付”列与 06-03-01 冲突，本文不采纳该列作为类型定义。

## 16. 给 06-03-02-02 的输入

下一份文档若做标准字段映射，只能做这些事：

1. 每个平台、每个契约、每个字段路径，选择本目录中的一个 typeCode，或选择 `AMT.UNK.UNIDENTIFIED`。
2. 写明 mappingId、mappingVersion、sourceContractId、sourceContractVersion、单位声明和证据编号。
3. 不得新增“实付等于总价”这类公式。
4. 不得把 `AMT.IND.TURNOVER` 或 `AMT.FIN.SETTLEMENT` 当作映射目标。
5. 淘宝在官方字段页出现之前，金额路径保持未识别。
6. 团购列在单位确认之前，单位写 `UNKNOWN`，转换规则留空。
7. 一份字段若对应两个类型，映射状态写 `CONFLICT`，不要删掉其中一个。

06-03-02-02 还不存在。本文不创建该文件。

## 17. 自检

| 项 | 结果 |
| --- | --- |
| 读取的文档 | 06-03-01 全文结构及术语、Money、状态、规则节；R-09 矩阵全文；06-02-02、04、06、08、10、11 全文。R-09 主报告与 SRC 的结论通过 06-03-01 第 4 节和矩阵引用，本轮没有改它们 |
| 新增文件 | 本文件 |
| 标准类型数量 | 目录表 35 个 typeCode。另有团购未点名列，明确不编造编码 |
| 业务语义仍未确认 | 用户实付、用户实退、支付成功合计、平台补贴、结算、经营成交额、净成交额、手续费单位、团购全部类型、淘宝订单金额、京东应付是否含餐盒运费 |
| 与 06-03-01 | 无核心冲突。指标类型只是槽位，没有公式 |
| 与 R-09 | 无冲突。AMT、PAY、REF、GBV、SRC 编号保留 |
| 文件名和链接 | 文件名无《》。文内相对链接指向 06-03-01 与两份 R-09。仓库里没有其他文档需要改索引 |
| 未执行 | 测试、构建、SQL、代码修改、提交和推送都没有做 |
