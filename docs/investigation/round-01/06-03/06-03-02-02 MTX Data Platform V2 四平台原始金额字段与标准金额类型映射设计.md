# 06-03-02-02 MTX Data Platform V2 四平台原始金额字段与标准金额类型映射设计

本文只设计京东、美团、淘宝闪购、富智云的原始金额字段如何指向 [06-03-02-01 标准金额类型体系](06-03-02-01%20MTX%20Data%20Platform%20V2%20标准金额类型体系与编码规范.md)。它不新增、不删除、不改写那 39 个编码，也不实现代码、SQL 或生产配置。

领域对象沿用 [06-03-01 金额领域模型](06-03-01%20MTX%20Data%20Platform%20V2%20多平台订单金额领域模型设计.md)。证据沿用 [R-09 口径报告](../06-02-R/06-02-R-09四平台金额、支付、退款与团购核销业务口径确认及证据闭环报告.md)、[R-09 矩阵](../06-02-R/06-02-R-09金额业务口径确认与证据闭环矩阵.md)、[SRC-01](../06-02-R/sources/SRC-01淘宝闪购订单消息官方文档证据记录.md)、[SRC-02](../06-02-R/sources/SRC-02美团外卖订单推送官方文档证据记录.md)、[SRC-03](../06-02-R/sources/SRC-03京东外卖订单消息官方文档证据记录.md) 和 [R-07](../06-02-R/06-02-R-07团购核销金额精度单位与写入一致性专项核验.md)。trade-flow 源码只说明旧系统怎么读字段，不能单独证明业务语义。

本轮没有真实原始报文，没有生产金额行抽样，没有业务或财务书面确认。因此已确认生产映射的数量是 0。所有映射规则的生命周期都是 `DRAFT`。标准类型里也没有任何 `ACTIVE` 编码。

## 1. 设计目标与范围

目标是给每个已经出现在证据里的金额字段一条可追溯的映射规则：能回到契约、字段路径和证据，也能在规则改版后仍解释旧观测。

范围内：

- 四个平台在既有调查中已经点名的订单、支付、退款、充值、优惠和团购核销金额字段。
- 这些字段与 39 个标准类型之间的候选、冲突、未识别和禁止关系。
- 映射版本、历史保留，以及映射和计算的分界。

范围外：

- 不重新定义标准类型，不把 `DRAFT` 类型写成已经批准的生产映射。
- 不设计经营成交额、净成交额、结算额公式。
- 不把多平台金额合并。
- 不决定查询适配器、原始报文存储产品或调度模块。

调查用的 `sourceContractId`、`mappingId` 是本文分配的身份，不是平台颁发的契约号。平台没有给出版本号时，`sourceContractVersion` 写“未提供”，不写成永远有效。

## 2. 既有标准金额类型体系引用

06-03-02-01 已冻结。目录仍是 39 个编码：38 个 `DRAFT`，1 个 `RETIRED`，0 个 `ACTIVE`。停用的编码只有 `AMT.FEE.DELIVERY`。映射不得指向它。

类型上的门禁继续有效：

| mappingEligibility | 本文件怎么用 |
| --- | --- |
| `ALLOWED_IF_CONTRACT_DEFINES` | 只有契约原文落在该类型定义里，才可以成为候选目标。规则仍是草稿，不是生产生效配置 |
| `NOT_UNTIL_CONFIRMED` | 可以记下候选编码，确认前不得生成该类型的正式观测 |
| `DISALLOWED` | 不得作为字段映射目标。支付成功合计、退款成功合计、结算额、经营成交额、净成交额属于这一类 |
| `ALLOWED_AS_UNIDENTIFIED` | 只用于 `AMT.UNK.UNIDENTIFIED` |
| `CONFLICT_ONLY` | 类型目录当前没有使用这个值。冲突记在映射上 |

一个字段路径在同一契约版本里，只能准备一个未停用编码，或者准备未识别。两个解释同时成立时，结果是冲突，不是两条已确认观测。

## 3. 映射领域对象及关系

三条记录回答三个不同问题：

| 记录 | 回答 | 不回答 |
| --- | --- | --- |
| RawPayloadRecord | 这份原文是否已不可变保存 | 字段有没有标准语义 |
| ObservedAmount | 某个路径是否已按某一版映射产生观测 | 经营额有没有算出来 |
| AmountCalculation | 某一版计算规则是否已产出结果 | 原文在不在，映射完没完成 |

四个名字回答四个问题，不能互相代替：

| 名称 | 放在哪 | 取值 | 回答 |
| --- | --- | --- | --- |
| AmountMapping.lifecycleStatus | 映射规则 | `DRAFT`、`ACTIVE`、`RETIRED` | 这版规则能不能用于新的正式观测。它就是 06-03-01 映射定义侧的 MappingStatus。当前全部为 `DRAFT` |
| ObservedAmount.mappingStatus | 一次观测的执行结果 | `MAPPED`、`UNMAPPED`、`CONFLICT` | 这次有没有把路径采纳为正式标准金额。它就是 06-03-01 映射执行侧的 MappingStatus |
| candidateDisposition | 只存在于本设计文档 | `SINGLE_CANDIDATE`、`MULTI_CANDIDATE`、`HOLD_UNIDENTIFIED`、`CONFLICTING_CANDIDATES`、`NO_AMOUNT`、`BLOCKED_BY_ELIGIBILITY` | 设计阶段如何看待候选。它不是 MappingStatus，也不能批准规则 |
| mappingEligibility | 标准类型定义 | 冻结文档的五个值 | 这个类型允不允许被字段映射指向 |

06-03-01 把定义侧和执行侧都叫 MappingStatus。本文不再另造 `CANDIDATE`、`MULTI_CANDIDATE`、`UNIDENTIFIED`、`DISALLOWED` 这些 MappingStatus。`DISALLOWED` 只保留为类型资格。证据状态和语义状态仍与 06-03-01 相同，E4 不能升成已确认语义。

只有 `lifecycleStatus=ACTIVE` 的规则，才能让新报文产生正式标准金额观测，此时执行状态才是 `MAPPED`。目标类型仍是 `DRAFT` 时，规则不能批成 `ACTIVE`。当前因此不存在 `MAPPED` 的正式业务金额观测。

两个解释都不能选时，设计评估写 `CONFLICTING_CANDIDATES` 或 `MULTI_CANDIDATE`。若执行时仍把同一路径写成两个正式类型，ObservedAmount.mappingStatus 必须是 `CONFLICT`，原文仍只有一份，不能有两条 `MAPPED`。

`mappingId` 标识“哪个平台、哪份契约、哪条路径、准备指向哪个类型”。`mappingVersion` 标识这一定义的正文版本。`sourceContractId` 标识契约。`sourceContractVersion` 标识契约材料自己的版本。四者不能互相代替。

AmountSource 至少保留：`platform`、`sourceInterface`、`sourceMessageType`、`sourceContractId`、`sourceContractVersion`、`rawPayloadRef`、`ingressMode`、`sourceEventId`、`sourceMessageId`、`receivedAt`。`fieldPath` 放在 RawAmountValue 上。京东 `billId` 是消息单据号，不是金额，也不代替 `sourceMessageId`。

同一字段名出现在两份尚未证明相同的契约里时，必须使用两个 `sourceContractId`。美团官方页和 trade-flow 回调、京东创建消息和订单查询摘录，都按这个规则拆开。

## 4. 四个平台原始金额字段清单

阅读约定：

- `documentedUnit`、`documentedCurrency`、`documentedPrecision` 只填写官方页或摘录原文已经写明的内容。没写就是 `UNKNOWN`。示例里的小数不能升成“单位是元”。
- `rawValueExample` 只填写证据里的示例。没有示例就是 `UNKNOWN`。这些示例不是生产报文。
- `E1-A` 是本次读到的官方页。`E1-B` 是仓库摘录，不证明摘录仍是现行接口。`E4` 是旧代码。`E5` 是 trade-flow 设计或 DDL 注释。`E2` 真实报文和金额行级 `E3` 本轮都没有。
- 源码字段名不是官方定义。

### 4.1 契约身份

| sourceContractId | 平台 | 材料 | 版本 | 证据 |
| --- | --- | --- | --- | --- |
| `SC-JD-MSG-ORDER-CREATED` | `JD` | 京东“创建新订单消息”，消息名 `orderCreated`。URL 见 SRC-03 | 页面写更新于 2025-12-25 13:19:44 | E1-A |
| `SC-JD-QUERY-EXCERPT` | `JD` | 仓库摘录 `docs/references/jd/jd.o2o.order.es.query.md`。不是 SRC-03 那一页 | 未提供。是否现行，SRC-002 未关闭 | E1-B |
| `SC-JD-CODE-DETAIL` | `JD` | 旧代码从订单详情读取、但摘录字段表没有单独登记的金额名 | 未提供。不是官方契约号 | E4 |
| `SC-JD-AFS-CODE` | `JD` | 旧代码 `JdAfsRefundMapper` 读取的售后字段。SRC-03 没有这些字段 | 未提供 | E4 |
| `SC-MT-DOC-175` | `MEITUAN` | 美团“APP方URL 推送已确认订单”，docDetail/175。HTTP POST form | 未提供 | E1-A |
| `SC-MT-CALLBACK-CODE` | `MEITUAN` | trade-flow 回调事件 `managedCancelAndConfirmOrder` 及详情归一化后的驼峰字段 | 未提供。尚未证明等于 docDetail/175 | E4 |
| `SC-TB-MSG-CATALOG` | `TAOBAO_FLASH` | 淘宝闪购订单服务消息目录。不是某一条消息的字段页 | 未提供 | E1-A，仅消息名 |
| `SC-TB-CODE-DETAIL-QUERY` | `TAOBAO_FLASH` | 旧代码订单详情查询路径。不是官方契约，也不能代表消息 217 | 未提供 | E4 |
| `SC-TB-CODE-MSG-217` | `TAOBAO_FLASH` | 旧代码把消息 217 也当快照的路径。官方目录只有消息名 | 未提供 | E4 |
| `SC-TB-CODE-REFUND-HEADER` | `TAOBAO_FLASH` | 旧代码退款头。未证明与退款行是同一契约 | 未提供 | E4 |
| `SC-TB-CODE-REFUND-ITEM` | `TAOBAO_FLASH` | 旧代码退款行 | 未提供 | E4 |
| `SC-FY-ORDER-EXCERPT` | `FUIOU` | 仓库摘录 `docs/references/fuiou/api.md` 已经写明的订单推送字段 | 未提供。摘录是否现行未证明 | E1-B |
| `SC-FY-CODE-ORDER` | `FUIOU` | 只在旧代码中出现、摘录未按字段写明的订单金额名 | 未提供。不是官方契约号 | E4 |
| `SC-FY-PAY-EXCERPT` | `FUIOU` | 支付摘录已经写明的金额字段 | 未提供。摘录写 Long，未写分 | E1-B |
| `SC-FY-PAY-CODE` | `FUIOU` | 支付代码另读、摘录未写明的金额名 | 未提供 | E4 |
| `SC-FY-REFUND-CODE` | `FUIOU` | `FuiouRefundFactMapper` 与退款累计 | 未提供 | E4 |
| `SC-FY-RECHARGE-CODE` | `FUIOU` | `FuiouRechargeFactMapper` | 未提供 | E4 |
| `SC-FY-GBV-CODE` | `FUIOU` | 团购核销 JSON。接口摘录没有这八列的单位 | 未提供 | E4；DDL 注释是 E5 |

本轮没有把其他淘宝业务线的接口文档拿来充当淘宝闪购契约。

### 4.2 京东

`SC-JD-MSG-ORDER-CREATED` 的消息体只有 `venderId`、`billId`、`statusId`、`timestamp`。触发条件是客户支付订单后推送。该契约不产生金额字段，也不包含下表任何一个金额名。

下表属于 `SC-JD-QUERY-EXCERPT`，除非备注写成售后代码契约。`sourceSystem` 为京东到家或秒送查询摘录。`sourceInterface` 为订单查询摘录。`sourceMessageType` 为空，不能填成 `orderCreated`。

| fieldPath | fieldName | rawDataType | rawValueExample | documentedDescription | documentedUnit | documentedCurrency | documentedPrecision | evidenceStatus | semanticStatus | remarks |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `orderTotalMoney` | `orderTotalMoney` | UNKNOWN | UNKNOWN | 摘录称销售价总额，并和商品原价合计区分 | UNKNOWN | UNKNOWN | UNKNOWN | E1-B | 待平台确认 | 旧代码写入营业额基数。不是官方“原价” |
| `orderBuyerPayableMoney` | `orderBuyerPayableMoney` | UNKNOWN | UNKNOWN | 摘录原文是“用户应付金额” | 分 | UNKNOWN | UNKNOWN | E1-B | 待平台确认 | 未写是否含餐盒和运费。不是最终实付，也不是支付成功合计 |
| `orderDiscountMoney` | `orderDiscountMoney` | UNKNOWN | UNKNOWN | 订单级优惠，不含单品和运费优惠 | UNKNOWN | UNKNOWN | UNKNOWN | E1-B | 待平台确认 | 摘录未写包装优惠。不能当成订单全部优惠 |
| `orderFreightMoney` | `orderFreightMoney` | UNKNOWN | UNKNOWN | 用户实际支付的运费 | UNKNOWN | UNKNOWN | UNKNOWN | E1-B | 待平台确认 | 是运费，不是整单应付，也不是美团那种门店配送费 |
| `canteenMoney` | `canteenMoney` | UNKNOWN | UNKNOWN | 商品总餐盒费 | UNKNOWN | UNKNOWN | UNKNOWN | E1-B | 待平台确认 | 旧代码汇总后写入包装费。是否采用，AMT-002 未完成 |
| `packagingMoney` | `packagingMoney` | UNKNOWN | UNKNOWN | 包装金额 | UNKNOWN | UNKNOWN | UNKNOWN | E1-B | 待平台确认 | 旧代码不使用。不映射进现有三个包装码 |

`orderGoodsMoney` 不进入上一张摘录表。06-02-02 只记录旧代码读取它，并写明官方参考未单独登记。本轮不能证明它属于 `SC-JD-QUERY-EXCERPT`。它单独属于代码调查来源 `SC-JD-CODE-DETAIL`：`sourceInterface` 是旧代码订单详情读取，`sourceContractVersion`、`rawDataType`、`rawValueExample`、`documentedUnit`、`documentedCurrency`、`documentedPrecision` 都是 `UNKNOWN`。`documentedDescription` 只有代码注释里的“优惠后商品金额”，不是官方定义。证据保持 E4，语义待平台确认。代码注释中的等式不能升成契约。

售后契约 `SC-JD-AFS-CODE`：

| fieldPath | fieldName | rawDataType | rawValueExample | documentedDescription | documentedUnit | documentedCurrency | documentedPrecision | evidenceStatus | semanticStatus | remarks |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `refundUserMmoney` | `refundUserMmoney` | UNKNOWN | UNKNOWN | 无官方页。代码在成功状态写入退款金额。拼写以源码为准 | UNKNOWN | UNKNOWN | UNKNOWN | E4 | 待平台确认 | `longValue()` 对小数会截断。REF-001。不能当成用户实退 |
| `cashMoney` | `cashMoney` | UNKNOWN | UNKNOWN | 无官方页。代码把成功明细的该字段当行金额 | UNKNOWN | UNKNOWN | UNKNOWN | E4 | 待平台确认 | 同样可能截断。不代表整单退款 |

trade-flow 设计文档还出现 `platformPointsDeductionMoney`、`orderReceivableFreight`、`afsMoney`、`externalOnlinePayment`，以及商品行上的 `refundUserMmoney`。本轮没有打开对应官方页，也没有把设计文档里的“实付”句子当成摘录原文。这些名字只进入第 11 节，不建立正式类型候选。

### 4.3 美团

下表属于 `SC-MT-DOC-175`。`sourceSystem` 为美团外卖开放平台。`sourceInterface` 为“APP方URL 推送已确认订单”。`sourceMessageType` 为该页描述的已确认订单推送，不是 `managedCancelAndConfirmOrder`。`sourceContractVersion` 为未提供。

| fieldPath | fieldName | rawDataType | rawValueExample | documentedDescription | documentedUnit | documentedCurrency | documentedPrecision | evidenceStatus | semanticStatus | remarks |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `original_price` | `original_price` | double | 23.1 | 原价。订单原价包含 1 对 1 急送费 | UNKNOWN | UNKNOWN | UNKNOWN | E1-A | 部分确认 | 该行未写“元”。不是销售价总额，不是商家应收 |
| `total` | `total` | double | 22.1 | 总价。金额包含 1 对 1 急送费 | UNKNOWN | UNKNOWN | UNKNOWN | E1-A | 部分确认；实付解释存在冲突 | 该行未写“元”，也未写用户实付。AMT-003 |
| `shipping_fee` | `shipping_fee` | float | 1.9 | 门店配送费 | UNKNOWN | UNKNOWN | UNKNOWN | E1-A | 部分确认 | 该行未写“元”。急送费是否已含在其中，未证明 |
| `phf_ssf` | `phf_ssf` | double | 1.20 | 拼好饭春节服务费，已计入 `shipping_fee` | 元 | UNKNOWN | UNKNOWN | E1-A | 部分确认 | 不能再加进配送费。AMT-008 |
| `wmPoiReceiveCent` | `wmPoiReceiveCent` | long | 1234 | 商家应收金额。可空。商家承担急送营销时已扣，不重复扣 | 分 | UNKNOWN | UNKNOWN | E1-A | 部分确认 | 不是总价，不是结算额，不是营业额。AMT-006 |
| `package_bag_fee` | `package_bag_fee` | double | 12.9 | 订单维度打包费总额。有该字段时直接使用，不用商品行覆盖 | 元 | UNKNOWN | UNKNOWN | E1-A | 部分确认 | mode=0 时页面又说它等于商品餐盒费总额。见映射矩阵 |
| `package_bag_money` | `package_bag_money` | int | 100 | 打包袋金额。不是 `package_bag_fee` | 分 | UNKNOWN | UNKNOWN | E1-A | 部分确认 | 与元字符串是同一袋的两种写法 |
| `package_bag_money_yuan` | `package_bag_money_yuan` | string | 1.00 | 打包袋金额 | 元 | UNKNOWN | UNKNOWN | E1-A | 部分确认 | 字符串不是已经解析的 Money |
| `poi_receive_detail` | `poi_receive_detail` | object | UNKNOWN | 商家对账。默认不推送，需订阅 | UNKNOWN | UNKNOWN | UNKNOWN | E1-A | 待平台确认 | 根表没有展开内部字段 |
| `poi_receive_detail_yuan` | `poi_receive_detail_yuan` | object | UNKNOWN | 商家对账 | 元 | UNKNOWN | UNKNOWN | E1-A | 待平台确认 | 内部字段未展开。不代替 `wmPoiReceiveCent` |
| `extras` | `extras` | list | 含满减和 `rider_fee` | 优惠元素与骑手应付款元素可能在同一数组 | UNKNOWN | UNKNOWN | UNKNOWN | E1-A | 待平台确认 | 数组本身不是一个金额标量 |
| `extras[].reduce_fee` | `reduce_fee` | UNKNOWN | UNKNOWN | 页面把它描述为优惠元素上的金额。类型表未单列 | UNKNOWN | UNKNOWN | UNKNOWN | E1-A | 待平台确认 | 缺失不是 0。骑手元素可以没有它 |
| `extras[].poi_charge` | `poi_charge` | UNKNOWN | UNKNOWN | 代码读取承担字段。官方根表未单列完整定义 | UNKNOWN | UNKNOWN | UNKNOWN | E1-A 加 E4 | 待业务确认 | 不能单独证明五项平衡 |
| `extras[].mt_charge` | `mt_charge` | UNKNOWN | UNKNOWN | 同上 | UNKNOWN | UNKNOWN | UNKNOWN | E1-A 加 E4 | 待业务确认 | 不是平台补贴 |
| `extras[].rider_fee` | `rider_fee` | UNKNOWN | UNKNOWN | 有该字段时按骑手元素处理 | UNKNOWN | UNKNOWN | UNKNOWN | E1-A | 待平台确认 | 不是优惠额 0 |
| `consumer_prepays` | `consumer_prepays` | list of string | `["10.0","0.0"]` | 与优惠明细按顺序对应的顾客预付金额。可能不下发 | UNKNOWN | UNKNOWN | UNKNOWN | E1-A | 待平台确认 | 没有加减公式。AMT-009 |
| `detail[].box_price` | `box_price` | UNKNOWN | UNKNOWN | mode=0 且打包费组未返回时，回退计算使用它 | UNKNOWN | UNKNOWN | UNKNOWN | E1-A | 待平台确认 | 映射阶段不乘 `box_num` |
| `detail[].box_num` | `box_num` | UNKNOWN | UNKNOWN | 同上。页面要求不要再乘 `quantity` | UNKNOWN | UNKNOWN | UNKNOWN | E1-A | 待平台确认 | 它是数量，不是金额 |
| `package_bag_cart_detail[].original_package_bag_fee` | `original_package_bag_fee` | UNKNOWN | UNKNOWN | mode=2 时页面说订单打包费是各口袋该字段之和 | UNKNOWN | UNKNOWN | UNKNOWN | E1-A | 待平台确认 | 嵌套结构未在根表展开，路径仍待核对 |

`package_bag_mode` 是 int 开关，不是金额。0 表示按商品餐盒费，1 表示按订单总价，2 表示按订单口袋。未返回不代表 0。

`SC-MT-CALLBACK-CODE` 里，旧代码使用 `originalPrice`、`total`、`shippingFee`、`wmAppOrderFoods`，详情归一化后才出现 `wmPoiReceiveCent`。回调测试夹具的商品数组名与官方 `detail` 不同。这些驼峰名在被证明就是 docDetail/175 之前，单独保留为未识别，不共用上一张表的映射。

### 4.4 淘宝闪购

`SC-TB-MSG-CATALOG` 读到的是消息名。与金额有关、且旧代码登记过的包括 217、12、14、18、30–39、223。217 的页面描述是“订单生效，店铺可以看到新订单”。该页没有 `originalPrice`、`totalAmount`、`totalPrice`、`deliverFee`，也没有类型、单位和示例。其他淘宝业务线文档不能补这个缺口。SRC-001。

详情查询和消息 217 尚未证明是同一契约，因此不再共用 `SC-TB-CODE-SNAPSHOT`。下面每张表的 `sourceContractVersion`、`rawDataType`、`rawValueExample`、`documentedDescription`、`documentedUnit`、`documentedCurrency`、`documentedPrecision` 都是 `UNKNOWN` 或“无”。证据都是 E4，不升成官方字段定义。语义都是待平台确认。

`SC-TB-CODE-DETAIL-QUERY` 只表示旧代码的订单详情查询路径：

| fieldPath | 代码行为 | 不能当成 |
| --- | --- | --- |
| `originalPrice` | 写入商品原价列和优惠基数，并做元转分 | 官方订单原价 |
| `totalAmount` | 写入代码里的订单原价列 | 官方总价或 `AMT.ORD.TOTAL` |
| `totalPrice` | 同时写入优惠后金额和 `userPayAmount` | 用户实付 |
| `deliverFee` | 写入配送费 | 已确认的门店配送费或用户运费 |
| `packageFee` | 字段在场才转换后写入包装费 | 美团订单打包费 |
| `totalActivityAmount` | 有优惠摘要时取绝对值 | 已确认优惠额。取绝对值不是 V2 规则 |
| `income` | 只在详情查询写入商家收入列，不用实付代替 | 已确认商家应收或结算额 |

`SC-TB-CODE-MSG-217` 只表示旧代码把消息 217 当作快照的路径。官方目录没有这些字段，所以不能证明 217 报文里真有这些路径，也不能和详情查询共用 mappingId：

| fieldPath | 代码行为 | 不能当成 |
| --- | --- | --- |
| `originalPrice` | 217 被当成快照时，代码会走同一套金额赋值 | 官方已经定义的订单原价 |
| `totalAmount` | 同上 | 官方总价 |
| `totalPrice` | 同上，且代码会同时写入两个业务列 | 用户实付 |
| `deliverFee` | 同上 | 已确认运费类型 |
| `packageFee` | 同上 | 美团订单打包费 |
| `totalActivityAmount` | 同上 | 已确认优惠额 |

`income` 不放入 217 这张表。既有记录只把它记在详情查询。

`SC-TB-CODE-REFUND-HEADER` 的 `totalPrice`：代码元转分后当退款头金额。它不是详情查询或 217 上的 `totalPrice`。`SC-TB-CODE-REFUND-ITEM` 的 `money`：代码元转分后当退款行金额。两者都不是用户实退。

优惠代码还读取 `amount`、`restaurantPart`、`elemePart`、`familyPart`、`userPart`。报文路径本轮没有从官方页取得，所以不能写成已经定位的 `fieldPath`。用户承担必须来自活动原文；这条只是代码注释，不是官方定义。

### 4.5 富智云

订单摘录契约是 `SC-FY-ORDER-EXCERPT`。`sourceInterface` 为仓库订单推送摘录。`sourceMessageType` 未在本轮重新打开摘录后命名，写“未提供”。

| fieldPath | rawDataType | rawValueExample | documentedDescription | documentedUnit | documentedCurrency | documentedPrecision | evidenceStatus | semanticStatus | remarks |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `orderDisAmt` | UNKNOWN | UNKNOWN | 不含快递和若干优惠的原单金额 | 分 | UNKNOWN | UNKNOWN | E1-B | 待平台确认 | 摘录是否现行未证明。不是实付 |
| `payAmt` | UNKNOWN | UNKNOWN | 摘录写实付，并给出组成 | 分 | UNKNOWN | UNKNOWN | E1-B | 待业务确认 | 组成没有被证明全部来自用户。不是支付成功合计 |
| `expressAmt` | UNKNOWN | UNKNOWN | 摘录有快递金额这一列。本轮没有更细的官方句子 | UNKNOWN | UNKNOWN | UNKNOWN | E1-B | 待平台确认 | 不能先选门店配送费或用户运费 |
| `fullOrderDisAmt` | UNKNOWN | UNKNOWN | 摘录有整单优惠类名称 | UNKNOWN | UNKNOWN | UNKNOWN | E1-B | 待平台确认 | 旧代码禁止把明细再加进订单头 |
| `packagePriceDisAmt` | UNKNOWN | UNKNOWN | 只有名称，没有正负说明 | UNKNOWN | UNKNOWN | UNKNOWN | E1-B | 待业务确认 | AMT-005。符号必须保留 |
| `lunchBoxFee` | UNKNOWN | UNKNOWN | 餐盒 | 分 | UNKNOWN | UNKNOWN | E1-B | 待平台确认 | 不映射到美团订单打包费 |

`orderAmt`、`payAmtExtra`、`wipeZeroAmt` 不放入上一张摘录表。本轮没有摘录字段句证明它们属于 `SC-FY-ORDER-EXCERPT`。它们属于代码调查来源 `SC-FY-CODE-ORDER`，证据保持 E4：`orderAmt` 只知道代码不把它当跨平台原价；`payAmtExtra` 只知道旧代码不拆成第二支付行；`wipeZeroAmt` 只知道代码把抹零承担记到商家。三者的官方描述、单位、币种、精度和示例都是 `UNKNOWN`。负数线索不升成规则。

支付契约 `SC-FY-PAY-EXCERPT`。`sourceContractVersion`、`rawValueExample`、`documentedCurrency`、`documentedPrecision` 都是 `UNKNOWN`。`rawDataType` 只知道摘录写 Long，没有更细的精度。

| fieldPath | documentedDescription | documentedUnit | evidenceStatus | remarks |
| --- | --- | --- | --- | --- |
| `payAmt` | 支付摘录写 Long。代码写入 `paidAmount` | UNKNOWN | E1-B | 与订单 `payAmt` 不是同一契约，不能共用映射 |
| `feeAmt` | 代码写入手续费。摘录未写分 | UNKNOWN | E1-B | PAY-003。不能推断为分 |

`faceAmt` 不放入支付摘录表。06-02-05 只记录代码把它写入支付上的券面额字段，本轮没有摘录句子。它属于 `SC-FY-PAY-CODE`，证据保持 E4，单位 `UNKNOWN`，也不是团购 `faceAmt`。

退款契约 `SC-FY-REFUND-CODE` 的 `refundAmt`：代码在已退款状态使用；空则失败。无官方单位。负数在累计代码里会抛错，这是 E4，不是财务规则。

充值契约 `SC-FY-RECHARGE-CODE`：`payAmt` 是成功充值金额，冲正时可能改读 `refundAmt`；`giveAmt` 是赠送；`feeAmt` 是手续费。代码对充值本金取绝对值。这只是旧行为。三个字段的官方单位都是 `UNKNOWN`。京东、美团、淘宝未发现充值金额 Mapper。

团购契约 `SC-FY-GBV-CODE` 的八个 JSON 数字字段如下。接口摘录没有单位。DDL 注释写“单位分”，注释不是契约单位。代码按 JSON 数字原样保存，scale 大于 6 整页失败，不乘除 100。缺失是 null，数字 0 保留。

| fieldPath | Doris 列 | 候选类型只作登记 |
| --- | --- | --- |
| `buyPriceAmt` | `buy_price_amt` | `AMT.GBV.BUY_PRICE` |
| `couponAmt` | `coupon_amt` | `AMT.GBV.COUPON` |
| `faceAmt` | `face_amt` | `AMT.GBV.FACE` |
| `faceValue` | `face_value` | `AMT.GBV.FACE_VALUE` |
| `mchntIncome` | `mchnt_income` | `AMT.GBV.MCHNT_INCOME` |
| `platHongBao` | `plat_hong_bao` | `AMT.GBV.PLAT_HONGBAO` |
| `thirdMchntIncome` | `third_mchnt_income` | `AMT.GBV.THIRD_MCHNT_INCOME` |
| `thirdServiceFee` | `third_service_fee` | `AMT.GBV.THIRD_SERVICE_FEE` |

八列的 `documentedUnit`、`documentedCurrency`、`documentedPrecision`、`rawValueExample` 都是 `UNKNOWN`。`rawDataType` 只知道代码要求 JSON 数字。语义全部待平台确认。GBV-001、GBV-002。

## 5. 四平台标准金额类型候选映射矩阵

本节没有“已确认映射”。`mappingVersion` 全部是 `draft-1`。`lifecycleStatus` 全部是 `DRAFT`。`currencyConstraint` 全部是“币种 UNKNOWN，禁止跨币种运算，禁止从单位推断人民币”。`precisionConstraint` 全部是 `NO_IMPLICIT_ROUNDING`。`transformationPolicy` 全部是 `NO_CONVERT`：不换单位、不舍入、不截断、不取绝对值、不把缺失补 0。

矩阵列 `candidateDisposition` 只表示设计评估。它旁边的 `lifecycleStatus` 才是规则生命周期。现在这些规则都是 `DRAFT`，所以都还不能产生 `ObservedAmount.mappingStatus=MAPPED`。

| candidateDisposition | 设计含义 | 现在的执行状态 |
| --- | --- | --- |
| `SINGLE_CANDIDATE` | 契约用词只对上一个类型 | 规则未生效，不得 `MAPPED` |
| `MULTI_CANDIDATE` | 有多个类型或多种来源写法，不选默认 | 不得 `MAPPED`。若执行时写成两个正式类型，状态必须是 `CONFLICT` |
| `HOLD_UNIDENTIFIED` | 先不生成正式业务类型观测 | 对业务类型是 `UNMAPPED`。未识别保存见第 8 节，不是业务映射成功 |
| `CONFLICTING_CANDIDATES` | 两种解释都不能选 | 不得二选一。执行时状态是 `CONFLICT` |
| `NO_AMOUNT` | 该契约没有这个金额 | `UNMAPPED` |
| `BLOCKED_BY_ELIGIBILITY` | 类型资格禁止作为字段目标 | 不建立指向该类型的规则，没有 `MAPPED` |

目标资格是 `NOT_UNTIL_CONFIRMED` 或 `DISALLOWED` 时，不得生成该业务类型的正式观测。字段仍可按第 8 节保存未识别观测。

### 5.1 契约用词已经对上、但尚未生效的映射

这些行的 `evidenceReference` 是 SRC-02 对应字段行。`unitConstraint` 是：页面写了单位才记录该单位，未写则 `UNKNOWN`，两种都不转换。

这些行的目标资格是 `ALLOWED_IF_CONTRACT_DEFINES`。它们仍不是生产映射。

| mappingId | platform | sourceContractId | sourceContractVersion | fieldPath | candidateAmountTypeCode | candidateDisposition | semanticStatus | evidenceStatus | mappingEligibility | aggregationPolicy | confirmationOwner | remarks |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `MAP-MT-ORIGINAL-PRICE` | `MEITUAN` | `SC-MT-DOC-175` | 未提供 | `original_price` | `AMT.ORD.ORIGINAL` | `SINGLE_CANDIDATE` | 部分确认 | E1-A | `ALLOWED_IF_CONTRACT_DEFINES` | `FORBIDDEN` | 平台官方文档 | 含急送费。单位未写，禁止按示例小数当成元并转换 |
| `MAP-MT-TOTAL` | `MEITUAN` | `SC-MT-DOC-175` | 未提供 | `total` | `AMT.ORD.TOTAL` | `SINGLE_CANDIDATE` | 部分确认；实付解释存在冲突 | E1-A | `ALLOWED_IF_CONTRACT_DEFINES` | `FORBIDDEN` | 平台官方文档；业务负责人否决实付解释 | 只接受“总价”这个词。禁止第二观测 `AMT.USR.PAID` |
| `MAP-MT-SHIPPING` | `MEITUAN` | `SC-MT-DOC-175` | 未提供 | `shipping_fee` | `AMT.FEE.STORE_DELIVERY` | `SINGLE_CANDIDATE` | 部分确认 | E1-A | `ALLOWED_IF_CONTRACT_DEFINES` | `FORBIDDEN` | 平台官方文档 | 不包含 `phf_ssf` 的第二次加计 |
| `MAP-MT-RECEIVE` | `MEITUAN` | `SC-MT-DOC-175` | 未提供 | `wmPoiReceiveCent` | `AMT.MCH.RECEIVABLE` | `SINGLE_CANDIDATE` | 部分确认 | E1-A | `ALLOWED_IF_CONTRACT_DEFINES` | `FORBIDDEN` | 平台官方文档；财务负责人确认它不是结算额 | 可空。缺失不用 `total` 填充。单位按页面写成分，币种仍未知 |
| `MAP-MT-BAG-FEN` | `MEITUAN` | `SC-MT-DOC-175` | 未提供 | `package_bag_money` | `AMT.FEE.PACKAGE_BAG` | `MULTI_CANDIDATE` | 部分确认 | E1-A | `ALLOWED_IF_CONTRACT_DEFINES` | `FORBIDDEN` | 数据平台研发负责人选择来源写法 | 与元字符串二选一，不能相加 |
| `MAP-MT-BAG-YUAN` | `MEITUAN` | `SC-MT-DOC-175` | 未提供 | `package_bag_money_yuan` | `AMT.FEE.PACKAGE_BAG` | `MULTI_CANDIDATE` | 部分确认 | E1-A | `ALLOWED_IF_CONTRACT_DEFINES` | `FORBIDDEN` | 同上 | 字符串要先按契约允许的 `NUMERIC_TEXT` 解析。本规则未批准解析 |

`MAP-MT-BAG-FEE` 是一条完整的设计记录，不选择默认类型，也不新增计算规则：

| 属性 | 值 |
| --- | --- |
| mappingId | `MAP-MT-BAG-FEE` |
| mappingVersion | `draft-1` |
| platform | `MEITUAN` |
| sourceContractId | `SC-MT-DOC-175` |
| sourceContractVersion | 未提供 |
| fieldPath | `package_bag_fee` |
| candidateAmountTypeCode | `AMT.FEE.PACKAGE_ORDER` 与 `AMT.FEE.PACKAGE_ITEM` |
| candidateDisposition | `MULTI_CANDIDATE` |
| lifecycleStatus | `DRAFT` |
| mappingEligibility | `AMT.FEE.PACKAGE_ORDER` 为 `ALLOWED_IF_CONTRACT_DEFINES`；`AMT.FEE.PACKAGE_ITEM` 为 `NOT_UNTIL_CONFIRMED` |
| unitConstraint | 页面写明单位元，只记录该声明，不换算 |
| currencyConstraint | 币种 `UNKNOWN`，禁止推断人民币，禁止跨币种运算 |
| precisionConstraint | `NO_IMPLICIT_ROUNDING` |
| transformationPolicy | `NO_CONVERT` |
| aggregationPolicy | 两个候选类型都是 `FORBIDDEN` |
| evidenceStatus | E1-A |
| semanticStatus | 部分确认。mode=0 时页面又把它说成商品餐盒费总额，因此不能只保留一种解释 |
| evidenceReference | SRC-02 字段行及打包费模式说明 |
| confirmationOwner | 平台官方文档；业务负责人 |
| remarks | 有该字段时不得用商品行覆盖。确认前不选择默认类型。规则未生效，不得产生 `MAPPED`。若执行时同时写成两个正式类型，ObservedAmount.mappingStatus 为 `CONFLICT` |

### 5.2 只登记候选、确认前保持未识别

本表每一行若没有单独列出，则共用这些值：`mappingVersion` 为 `draft-1`；`sourceContractVersion` 为“未提供”；`lifecycleStatus` 为 `DRAFT`；`semanticStatus` 和 `evidenceStatus` 以第 4 节同一字段行为准；`evidenceReference` 指向第 4 节该行，以及 R-09、SRC 或 R-07 的对应段落；`unitConstraint` 为不转换；`currencyConstraint` 为币种 `UNKNOWN`；`precisionConstraint` 为 `NO_IMPLICIT_ROUNDING`；`transformationPolicy` 为 `NO_CONVERT`。

| mappingId | platform | sourceContractId | fieldPath | candidateAmountTypeCode | candidateDisposition | mappingEligibility | aggregationPolicy | confirmationOwner | remarks |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `MAP-JD-SALE-TOTAL` | `JD` | `SC-JD-QUERY-EXCERPT` | `orderTotalMoney` | `AMT.ORD.SALE_TOTAL` | `HOLD_UNIDENTIFIED` | `NOT_UNTIL_CONFIRMED` | `FORBIDDEN` | 平台技术支持 | SRC-002 关闭前不映射。禁止改指向 `AMT.ORD.ORIGINAL` |
| `MAP-JD-PAYABLE` | `JD` | `SC-JD-QUERY-EXCERPT` | `orderBuyerPayableMoney` | `AMT.USR.PAYABLE` | `HOLD_UNIDENTIFIED` | `NOT_UNTIL_CONFIRMED` | `FORBIDDEN` | 平台技术支持；业务负责人 | 禁止指向 `AMT.USR.PAID` 和 `AMT.PAY.SUCCESS` |
| `MAP-JD-DISCOUNT` | `JD` | `SC-JD-QUERY-EXCERPT` | `orderDiscountMoney` | `AMT.ORD.DISCOUNT_ORDER` | `HOLD_UNIDENTIFIED` | `NOT_UNTIL_CONFIRMED` | `FORBIDDEN` | 平台技术支持 | 不含单品和运费优惠，不能当成全部优惠 |
| `MAP-JD-FREIGHT` | `JD` | `SC-JD-QUERY-EXCERPT` | `orderFreightMoney` | `AMT.FEE.USER_FREIGHT` | `HOLD_UNIDENTIFIED` | `NOT_UNTIL_CONFIRMED` | `FORBIDDEN` | 平台技术支持 | 禁止先映射到 `AMT.FEE.STORE_DELIVERY` |
| `MAP-JD-CANTEEN` | `JD` | `SC-JD-QUERY-EXCERPT` | `canteenMoney` | `AMT.FEE.PACKAGE_ITEM` | `HOLD_UNIDENTIFIED` | `NOT_UNTIL_CONFIRMED` | `FORBIDDEN` | 平台技术支持；业务负责人 | 与 `packagingMoney` 不能合成一个码 |
| `MAP-JD-PACKAGING` | `JD` | `SC-JD-QUERY-EXCERPT` | `packagingMoney` | 无正式候选 | `HOLD_UNIDENTIFIED` | `ALLOWED_AS_UNIDENTIFIED` | `FORBIDDEN` | 平台技术支持 | 不进入三个包装码 |
| `MAP-JD-GOODS-MONEY` | `JD` | `SC-JD-CODE-DETAIL` | `orderGoodsMoney` | `AMT.ORD.AFTER_DISCOUNT` | `HOLD_UNIDENTIFIED` | `NOT_UNTIL_CONFIRMED` | `FORBIDDEN` | 平台技术支持 | 官方参考未登记 |
| `MAP-JD-AFS-ROOT` | `JD` | `SC-JD-AFS-CODE` | `refundUserMmoney` | `AMT.RFD.DOCUMENT` | `HOLD_UNIDENTIFIED` | `NOT_UNTIL_CONFIRMED` | `FORBIDDEN` | 平台技术支持；财务负责人 | 禁止截断。禁止指向成功合计和实退 |
| `MAP-JD-AFS-CASH` | `JD` | `SC-JD-AFS-CODE` | `cashMoney` | `AMT.RFD.DOCUMENT` | `MULTI_CANDIDATE` | `NOT_UNTIL_CONFIRMED` | `FORBIDDEN` | 平台技术支持 | 与根金额是不是同一语义，尚未证明 |
| `MAP-MT-PHF` | `MEITUAN` | `SC-MT-DOC-175` | `phf_ssf` | `AMT.UNK.UNIDENTIFIED` | `HOLD_UNIDENTIFIED` | `ALLOWED_AS_UNIDENTIFIED` | `FORBIDDEN` | 平台官方文档 | 禁止加进 `AMT.FEE.STORE_DELIVERY` |
| `MAP-MT-POI-DETAIL` | `MEITUAN` | `SC-MT-DOC-175` | `poi_receive_detail` | `AMT.UNK.UNIDENTIFIED` | `HOLD_UNIDENTIFIED` | `ALLOWED_AS_UNIDENTIFIED` | `FORBIDDEN` | 平台官方文档 | 非标量。内部字段未展开前不计算 |
| `MAP-MT-POI-DETAIL-YUAN` | `MEITUAN` | `SC-MT-DOC-175` | `poi_receive_detail_yuan` | `AMT.UNK.UNIDENTIFIED` | `HOLD_UNIDENTIFIED` | `ALLOWED_AS_UNIDENTIFIED` | `FORBIDDEN` | 平台官方文档 | 同上。不与分对象相加 |
| `MAP-MT-EXTRAS` | `MEITUAN` | `SC-MT-DOC-175` | `extras` | `AMT.UNK.UNIDENTIFIED` | `HOLD_UNIDENTIFIED` | `ALLOWED_AS_UNIDENTIFIED` | `FORBIDDEN` | 平台官方文档 | 数组不参与计算 |
| `MAP-MT-REDUCE` | `MEITUAN` | `SC-MT-DOC-175` | `extras[].reduce_fee` | `AMT.ORD.DISCOUNT_DETAIL` | `HOLD_UNIDENTIFIED` | `NOT_UNTIL_CONFIRMED` | `RULE_CONTROLLED` | 平台官方文档 | 无生效规则，现在不能加总，也不能写入订单级优惠 |
| `MAP-MT-POI-CHARGE` | `MEITUAN` | `SC-MT-DOC-175` | `extras[].poi_charge` | `AMT.DSC.MERCHANT_BEAR` | `HOLD_UNIDENTIFIED` | `NOT_UNTIL_CONFIRMED` | `FORBIDDEN` | 业务负责人 | 负值变 0 是旧代码，不是本映射 |
| `MAP-MT-MT-CHARGE` | `MEITUAN` | `SC-MT-DOC-175` | `extras[].mt_charge` | `AMT.DSC.PLATFORM_BEAR` | `HOLD_UNIDENTIFIED` | `NOT_UNTIL_CONFIRMED` | `FORBIDDEN` | 业务负责人 | 不是 `AMT.PLT.SUBSIDY` |
| `MAP-MT-RIDER` | `MEITUAN` | `SC-MT-DOC-175` | `extras[].rider_fee` | `AMT.UNK.UNIDENTIFIED` | `HOLD_UNIDENTIFIED` | `ALLOWED_AS_UNIDENTIFIED` | `FORBIDDEN` | 平台官方文档 | 不是优惠额 |
| `MAP-MT-PREPAY` | `MEITUAN` | `SC-MT-DOC-175` | `consumer_prepays` | `AMT.UNK.UNIDENTIFIED` | `HOLD_UNIDENTIFIED` | `ALLOWED_AS_UNIDENTIFIED` | `FORBIDDEN` | 平台官方文档 | 无公式。禁止直接参与计算 |
| `MAP-MT-CALLBACK` | `MEITUAN` | `SC-MT-CALLBACK-CODE` | 驼峰金额路径 | `AMT.UNK.UNIDENTIFIED` | `HOLD_UNIDENTIFIED` | `ALLOWED_AS_UNIDENTIFIED` | `FORBIDDEN` | 平台技术支持 | 未证明与 docDetail/175 同一契约 |
| `MAP-TB-DETAIL-FIELDS` | `TAOBAO_FLASH` | `SC-TB-CODE-DETAIL-QUERY` | 详情查询各金额路径，不含已单独列出的 `totalPrice` | `AMT.UNK.UNIDENTIFIED` | `HOLD_UNIDENTIFIED` | `ALLOWED_AS_UNIDENTIFIED` | `FORBIDDEN` | 平台技术支持 | 官方结构未取得。不与消息 217 共用映射 |
| `MAP-TB-DETAIL-TOTAL-PRICE` | `TAOBAO_FLASH` | `SC-TB-CODE-DETAIL-QUERY` | `totalPrice` | 无 | `CONFLICTING_CANDIDATES` | `NOT_UNTIL_CONFIRMED` | `FORBIDDEN` | 平台技术支持；业务负责人 | 旧代码把它同时当优惠后金额和实付。不选择 |
| `MAP-TB-MSG217-FIELDS` | `TAOBAO_FLASH` | `SC-TB-CODE-MSG-217` | 代码在 217 分支读取的金额名，不含 `totalPrice` 和 `income` | `AMT.UNK.UNIDENTIFIED` | `HOLD_UNIDENTIFIED` | `ALLOWED_AS_UNIDENTIFIED` | `FORBIDDEN` | 平台技术支持 | 官方目录没有这些字段。不与详情查询共用映射 |
| `MAP-TB-MSG217-TOTAL-PRICE` | `TAOBAO_FLASH` | `SC-TB-CODE-MSG-217` | `totalPrice` | 无 | `CONFLICTING_CANDIDATES` | `NOT_UNTIL_CONFIRMED` | `FORBIDDEN` | 平台技术支持；业务负责人 | 与详情查询的同名路径分开。不选择实付 |
| `MAP-TB-REFUND-HEADER` | `TAOBAO_FLASH` | `SC-TB-CODE-REFUND-HEADER` | `totalPrice` | `AMT.RFD.DOCUMENT` | `HOLD_UNIDENTIFIED` | `NOT_UNTIL_CONFIRMED` | `FORBIDDEN` | 平台技术支持；财务负责人 | 不是订单上的 `totalPrice` |
| `MAP-TB-REFUND-ITEM` | `TAOBAO_FLASH` | `SC-TB-CODE-REFUND-ITEM` | `money` | `AMT.RFD.DOCUMENT` | `HOLD_UNIDENTIFIED` | `NOT_UNTIL_CONFIRMED` | `FORBIDDEN` | 平台技术支持；财务负责人 | 不与退款头合成一条映射 |
| `MAP-FY-ORDER-DIS` | `FUIOU` | `SC-FY-ORDER-EXCERPT` | `orderDisAmt` | 无正式候选 | `HOLD_UNIDENTIFIED` | `ALLOWED_AS_UNIDENTIFIED` | `FORBIDDEN` | 平台技术支持 | 不映射到订单原价 |
| `MAP-FY-PAY` | `FUIOU` | `SC-FY-ORDER-EXCERPT` | `payAmt` | `AMT.USR.PAID` | `HOLD_UNIDENTIFIED` | `NOT_UNTIL_CONFIRMED` | `FORBIDDEN` | 业务负责人 | 禁止生效。也禁止指向支付成功合计 |
| `MAP-FY-EXPRESS` | `FUIOU` | `SC-FY-ORDER-EXCERPT` | `expressAmt` | `AMT.FEE.STORE_DELIVERY` 与 `AMT.FEE.USER_FREIGHT` | `CONFLICTING_CANDIDATES` | `NOT_UNTIL_CONFIRMED` | `FORBIDDEN` | 平台技术支持 | 两种费用都不选 |
| `MAP-FY-FULL-DIS` | `FUIOU` | `SC-FY-ORDER-EXCERPT` | `fullOrderDisAmt` | `AMT.ORD.DISCOUNT_ORDER` | `HOLD_UNIDENTIFIED` | `NOT_UNTIL_CONFIRMED` | `FORBIDDEN` | 平台技术支持 | 禁止把明细加进此字段 |
| `MAP-FY-PKG-DIS` | `FUIOU` | `SC-FY-ORDER-EXCERPT` | `packagePriceDisAmt` | `AMT.UNK.UNIDENTIFIED` | `HOLD_UNIDENTIFIED` | `ALLOWED_AS_UNIDENTIFIED` | `FORBIDDEN` | 业务负责人 | 保留符号。不进入经营成交额 |
| `MAP-FY-LUNCH` | `FUIOU` | `SC-FY-ORDER-EXCERPT` | `lunchBoxFee` | `AMT.FEE.PACKAGE_ITEM` | `HOLD_UNIDENTIFIED` | `NOT_UNTIL_CONFIRMED` | `FORBIDDEN` | 平台技术支持 | 单位虽摘录为分，币种仍未知 |
| `MAP-FY-FEE` | `FUIOU` | `SC-FY-PAY-EXCERPT` | `feeAmt` | `AMT.FEE.PAYMENT` | `HOLD_UNIDENTIFIED` | `NOT_UNTIL_CONFIRMED` | `FORBIDDEN` | 财务负责人 | 单位未知，禁止换成元或分 |
| `MAP-FY-ORDER-AMT` | `FUIOU` | `SC-FY-CODE-ORDER` | `orderAmt` | 无正式候选 | `HOLD_UNIDENTIFIED` | `ALLOWED_AS_UNIDENTIFIED` | `FORBIDDEN` | 平台技术支持 | 摘录未证明该字段。不映射到订单原价 |
| `MAP-FY-PAY-EXTRA` | `FUIOU` | `SC-FY-CODE-ORDER` | `payAmtExtra` | `AMT.UNK.UNIDENTIFIED` | `HOLD_UNIDENTIFIED` | `ALLOWED_AS_UNIDENTIFIED` | `FORBIDDEN` | 业务负责人 | 不拆成第二支付行。PAY-002 |
| `MAP-FY-WIPE` | `FUIOU` | `SC-FY-CODE-ORDER` | `wipeZeroAmt` | `AMT.UNK.UNIDENTIFIED` | `HOLD_UNIDENTIFIED` | `ALLOWED_AS_UNIDENTIFIED` | `FORBIDDEN` | 业务负责人 | 只作为代码线索。不取绝对值 |
| `MAP-FY-PAY-FACE` | `FUIOU` | `SC-FY-PAY-CODE` | `faceAmt` | `AMT.UNK.UNIDENTIFIED` | `HOLD_UNIDENTIFIED` | `ALLOWED_AS_UNIDENTIFIED` | `FORBIDDEN` | 平台技术支持 | 不指向团购面值 |
| `MAP-FY-REFUND` | `FUIOU` | `SC-FY-REFUND-CODE` | `refundAmt` | `AMT.RFD.DOCUMENT` | `HOLD_UNIDENTIFIED` | `NOT_UNTIL_CONFIRMED` | `FORBIDDEN` | 财务负责人 | 不指向退款成功合计 |
| `MAP-FY-RCG-PAY` | `FUIOU` | `SC-FY-RECHARGE-CODE` | `payAmt` | `AMT.RCG.PRINCIPAL` | `HOLD_UNIDENTIFIED` | `NOT_UNTIL_CONFIRMED` | `FORBIDDEN` | 业务负责人 | 禁止取绝对值。不进入订单成交额 |
| `MAP-FY-RCG-GIFT` | `FUIOU` | `SC-FY-RECHARGE-CODE` | `giveAmt` | `AMT.RCG.GIFT` | `HOLD_UNIDENTIFIED` | `NOT_UNTIL_CONFIRMED` | `FORBIDDEN` | 业务负责人 | 与本金分开 |
| `MAP-FY-GBV-BUY` | `FUIOU` | `SC-FY-GBV-CODE` | `buyPriceAmt` | `AMT.GBV.BUY_PRICE` | `HOLD_UNIDENTIFIED` | `NOT_UNTIL_CONFIRMED` | `FORBIDDEN` | 平台技术支持 | 不换算，不进入订单类型 |
| `MAP-FY-GBV-COUPON` | `FUIOU` | `SC-FY-GBV-CODE` | `couponAmt` | `AMT.GBV.COUPON` | `HOLD_UNIDENTIFIED` | `NOT_UNTIL_CONFIRMED` | `FORBIDDEN` | 平台技术支持 | DDL 注释不是单位证明 |
| `MAP-FY-GBV-FACE` | `FUIOU` | `SC-FY-GBV-CODE` | `faceAmt` | `AMT.GBV.FACE` | `HOLD_UNIDENTIFIED` | `NOT_UNTIL_CONFIRMED` | `FORBIDDEN` | 平台技术支持 | 不与实际抵扣合并 |
| `MAP-FY-GBV-FACE-VALUE` | `FUIOU` | `SC-FY-GBV-CODE` | `faceValue` | `AMT.GBV.FACE_VALUE` | `HOLD_UNIDENTIFIED` | `NOT_UNTIL_CONFIRMED` | `FORBIDDEN` | 平台技术支持 | 第八列。不换算 |
| `MAP-FY-GBV-HB` | `FUIOU` | `SC-FY-GBV-CODE` | `platHongBao` | `AMT.GBV.PLAT_HONGBAO` | `HOLD_UNIDENTIFIED` | `NOT_UNTIL_CONFIRMED` | `FORBIDDEN` | 平台技术支持 | 不是订单平台承担优惠 |
| `MAP-FY-GBV-INCOME` | `FUIOU` | `SC-FY-GBV-CODE` | `mchntIncome` | `AMT.GBV.MCHNT_INCOME` | `HOLD_UNIDENTIFIED` | `NOT_UNTIL_CONFIRMED` | `FORBIDDEN` | 平台技术支持；财务负责人 | 不是商家应收，也不是结算额 |
| `MAP-FY-GBV-THIRD-INCOME` | `FUIOU` | `SC-FY-GBV-CODE` | `thirdMchntIncome` | `AMT.GBV.THIRD_MCHNT_INCOME` | `HOLD_UNIDENTIFIED` | `NOT_UNTIL_CONFIRMED` | `FORBIDDEN` | 平台技术支持 | 不与上一列相加 |
| `MAP-FY-GBV-FEE` | `FUIOU` | `SC-FY-GBV-CODE` | `thirdServiceFee` | `AMT.GBV.THIRD_SERVICE_FEE` | `HOLD_UNIDENTIFIED` | `NOT_UNTIL_CONFIRMED` | `FORBIDDEN` | 平台技术支持 | 不从收入列扣，也不是春节服务费 |

`unitConstraint` 对上表的统一写法：官方或摘录写了单位的，只把该文字记入单位声明；没写的保持 `UNKNOWN`。两种情况都不执行转换。团购八列即使 DDL 写“分”，单位约束仍是 `UNCONFIRMED`。

### 5.3 明确不映射

| 对象 | candidateDisposition | 执行状态 | 原因 |
| --- | --- | --- | --- |
| `SC-JD-MSG-ORDER-CREATED` 全部字段 | `NO_AMOUNT` | `UNMAPPED` | 消息体没有金额 |
| `SC-TB-MSG-CATALOG` 的消息名 | `NO_AMOUNT` | `UNMAPPED` | 只有名称，没有金额结构 |
| 任意字段到 `AMT.PAY.SUCCESS` | `BLOCKED_BY_ELIGIBILITY` | 不产生 `MAPPED` | 类型资格 `DISALLOWED`。它不是 MappingStatus |
| 任意字段到 `AMT.RFD.SUCCEEDED` | `BLOCKED_BY_ELIGIBILITY` | 不产生 `MAPPED` | 同上 |
| 任意字段到 `AMT.FIN.SETTLEMENT` | `BLOCKED_BY_ELIGIBILITY` | 不产生 `MAPPED` | 无公式。应收、实付都不能推导 |
| 任意字段到 `AMT.IND.TURNOVER` 或 `AMT.IND.NET` | `BLOCKED_BY_ELIGIBILITY` | 不产生 `MAPPED` | 旧营业额公式不是经营成交额 |
| 任意字段到 `AMT.FEE.DELIVERY` | `BLOCKED_BY_ELIGIBILITY` | 不产生 `MAPPED` | 编码已停用，资格 `DISALLOWED` |
| 任意字段到 `AMT.USR.PAID` | `BLOCKED_BY_ELIGIBILITY` | 不产生 `MAPPED` | 资格 `NOT_UNTIL_CONFIRMED`，当前没有可映射字段 |
| `detail[].box_num × detail[].box_price` | 不在映射中计算 | 这是将来的计算规则。mode=0 且字段组缺失以外，没有回退授权 |
| 旧代码 `yuanToCent`、`longValue` 截断、`Math.abs` | 不进入转换策略 | 只作为 E4 风险保留 |

`AMT.PLT.SUBSIDY`、`AMT.DSC.USER_BEAR`、`AMT.ORD.GOODS_ORIGINAL` 没有已定位且允许生效的字段。商品原价汇总是旧代码行为，确认前不映射。

## 6. 映射状态和生命周期

AmountMapping.lifecycleStatus 只描述规则：

1. 新规则从 `DRAFT` 开始。草稿可以记录 candidateDisposition，不能给新报文生成正式标准金额观测。
2. 目标类型仍是 `DRAFT` 时，指向它的规则不能批成 `ACTIVE`。当前因此没有生效映射。
3. 平台文档、业务或财务按第 11 节确认，并且类型资格允许之后，才增加新的 `mappingVersion`。只有这个新版本可以变成 `ACTIVE`，并写下 `effectiveFrom`。
4. 旧版本写上 `effectiveTo`，生命周期改为 `RETIRED`。它不删除，继续解释旧观测。
5. 停用不是改写历史。已经保存的 ObservedAmount 仍指向当时的 `mappingId` 加 `mappingVersion`。

ObservedAmount.mappingStatus 只描述一次执行，取值只有三个：

- `MAPPED`：当时使用的规则是 `ACTIVE`，目标资格允许，并且这次采纳的是一个正式业务金额类型。本设计现在没有这种观测。
- `UNMAPPED`：这次没有把路径采纳为正式业务金额。契约本身没有金额，或规则仍是草稿，都使用这个结果。
- `CONFLICT`：同一次执行里，同一契约版本的同一路径被写成两个正式类型，或两种解释都不能排除。此时不能挑选一个默认类型，也不能生成两条 `MAPPED`。原文仍只有一份。

candidateDisposition 不写入观测，也不因为写成 `CONFLICTING_CANDIDATES` 就自动变成已经发生的 `CONFLICT`。只有执行真的遇到不能并存的解释时，才记录 `CONFLICT`。

## 7. 单位、币种、精度约束

| 条件 | 允许 | 禁止 |
| --- | --- | --- |
| `documentedUnit` 为 `UNKNOWN` 或团购单位未确认 | 保留 RawAmountValue | 元分转换，以及任何把单位填成 `FEN` 或 `YUAN` 的默认值 |
| 官方写了单位 | 把原文单位记入映射的单位声明 | 据此推断币种。分不是人民币的证明 |
| `documentedCurrency` 为 `UNKNOWN` | 币种记 `UNKNOWN` | 跨币种加减，以及从单位补成 `CNY` |
| `documentedPrecision` 为 `UNKNOWN` | 保留原始 token 和可解析时的原始标度 | 四舍五入、截断、为了变成整数而丢掉小数 |
| 同一语义有分和元两种字段 | 将来只选一个来源 | 两个字段各映射一次再相加 |

美团 `original_price`、`total`、`shipping_fee` 的官方行没有写“元”。旧代码元转分只能说明旧系统把它当元，不能改写本表的 `documentedUnit`。

京东售后 `longValue()` 截断小数。映射明确禁止把截断当作精度策略。

## 8. 原始金额异常与未识别处理

RawAmountValue 的七种形态继续分开：`ABSENT`、`PRESENT_NULL`、`EMPTY_STRING`、`NUMERIC_ZERO`、`NUMERIC_TEXT`、`OTHER_TEXT`、`NON_SCALAR`。只有真实数字 0 是 `NUMERIC_ZERO`。缺失、null 和空字符串都不是 0。

未识别金额的保存方式：

1. 原文可以保存，并且必须同时有 RawAmountValue、AmountSource、`fieldPath`、`sourceContractId`、`sourceContractVersion` 和 `rawPayloadRef`。
2. 观测上可以保留 `AMT.UNK.UNIDENTIFIED`。
3. 这不表示业务语义已经确认，semanticStatus 仍是无法验证。
4. 不允许参与计算，也不能直接改成用户实付、商家应收、结算额或经营成交额。
5. 这次保存对正式业务类型的执行状态是 `UNMAPPED`。不得写成 `MAPPED`，以免被看成普通业务金额映射成功。

`poi_receive_detail`、`extras`、`consumer_prepays` 是非标量或列表。它们可以保存为未识别观测，不能被映射阶段展开后直接加总。`extras` 里缺少 `reduce_fee` 的骑手元素，不能写成优惠额 0。

语义确认后，用新 `mappingVersion` 让以后的报文指向正式类型。旧的未识别观测保留。

## 9. 映射版本管理

契约版本变化时，先判断字段定义有没有变。只有定义、单位或路径变了，才新建映射版本。契约没变，映射仍可能因为确认结论改变而修订。

历史要求：

- 新规则生效只影响生效之后的新观测。
- 旧规则退役后仍能按 `mappingId + mappingVersion` 解释旧观测。
- 设计上的多个候选写在同一规则版本的 candidateDisposition 里，不挑选默认类型。执行若不能并存，ObservedAmount.mappingStatus 记为 `CONFLICT`。
- 不允许覆盖历史观测，也不允许改写历史映射结果。重放生成新观测。

同一 `mappingId` 的正文变化必须增加 `mappingVersion`。当前版本号是 `draft-1`。它没有 `effectiveFrom`。

## 10. 映射与计算的边界

字段映射只回答“这个路径准备代表哪一种金额”。金额计算另由 AmountCalculationRule 回答“哪些观测可以加起来”。

当前没有生效的计算规则。因此：

- `AMT.PAY.SUCCESS` 和 `AMT.RFD.SUCCEEDED` 即使聚合策略是 `RULE_CONTROLLED`，现在也不能加总。
- `AMT.ORD.DISCOUNT_DETAIL` 同样不能加总，更不能把结果写进 `AMT.ORD.DISCOUNT_ORDER`。
- 团购八列的聚合策略是 `FORBIDDEN`。没有平衡公式。
- 用户应付、用户实付、商家应收、结算额不能互相推导。
- 订单原价、销售价总额、优惠、营业额不能混用。
- 不同平台的金额不能合并。
- 旧系统四套营业额公式只可称作 `turnover_v1_trade_flow` 的参照，EvidenceStatus 为 E4。它们不是 `AMT.IND.TURNOVER`。

美团 mode=0 的餐盒回退、富智云退款按退款号相加、充值取绝对值，都是旧代码行为。若将来要采用，必须单独写成计算规则并经过对应责任方确认。映射文档不提前给出这些公式。

## 11. 证据等级和待确认事项

证据等级沿用 R-09。E1-A 不能外推成生产报文。E4 不能升成业务口径。E5 的 DDL 注释和 trade-flow 设计句不能代替官方单位。本轮 E2 为无，金额行级 E3 为无，业务和财务书面确认为无。

| 编号 | 平台 | 原始字段 | 当前已知事实 | 缺少的证据 | 当前候选 | 风险 | 确认责任 | 影响的类型 | 确认前禁止 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| SRC-002 | 京东 | 查询摘录全部金额 | 创建消息没有金额。摘录和消息是否同一契约未证明 | 现行查询契约 | 销售价总额、应付、运费、优惠、餐盒 | 把消息里不存在的字段当成已经到达 | 平台技术支持 | `AMT.ORD.SALE_TOTAL`、`AMT.USR.PAYABLE`、`AMT.FEE.USER_FREIGHT`、`AMT.ORD.DISCOUNT_ORDER` | 正式映射 |
| MAP-01 | 京东 | `orderBuyerPayableMoney` | 摘录写用户应付，单位分 | 是否含餐盒和运费；是否等于最终付出 | 只保留应付候选 | 被旧设计句子改成实付 | 平台技术支持；业务负责人 | `AMT.USR.PAYABLE`、`AMT.USR.PAID` | 映射实付或支付成功合计 |
| MAP-02 | 京东 | `orderDiscountMoney` | 不含单品和运费优惠 | 包装优惠是否另有字段 | 订单级优惠 | 被当成全部优惠 | 平台技术支持 | `AMT.ORD.DISCOUNT_ORDER` | 加总其他优惠后覆盖它 |
| REF-001 | 京东 | `refundUserMmoney`、`cashMoney` | 无官方类型。代码会截断小数 | 官方类型、单位、精度 | 退款单金额 | 截断后当成准确退款 | 平台技术支持；财务负责人 | `AMT.RFD.DOCUMENT` | 截断、成功合计、实退 |
| MAP-03 | 美团 | docDetail/175 与回调 | 字段名和商品数组名不同 | 回调报文就是该页的证明 | 回调侧全部未识别 | 把官方定义套到另一份契约 | 平台技术支持 | 美团全部候选 | 共用 mappingId |
| AMT-003 | 美团 | `total` | 官方称总价，含急送费 | 没有用户实付定义 | 订单总价 | 旧代码字段名造成实付口径 | 业务负责人 | `AMT.ORD.TOTAL`、`AMT.USR.PAID` | 生成实付观测 |
| MAP-04 | 美团 | `original_price`、`total`、`shipping_fee` | 示例是小数，行内未写元 | 官方单位句子 | 对应三个候选 | 沿用旧代码元转分 | 平台官方文档 | 原价、总价、门店配送费 | 单位转换 |
| AMT-007 | 美团 | `package_bag_fee` 与 mode | 有字段则不得用商品行覆盖。mode=0 又等于餐盒总额 | 模式与类型的对应是否被业务接受 | 两个包装候选 | 餐盒和订单打包费重复 | 平台官方文档；业务负责人 | `AMT.FEE.PACKAGE_ORDER`、`AMT.FEE.PACKAGE_ITEM` | 选择默认模式或加总 |
| MAP-05 | 美团 | 打包袋两个字段 | 一个是分，一个是元字符串 | 将来以哪个来源为准 | 同一打包袋类型 | 两种写法相加 | 数据平台研发负责人 | `AMT.FEE.PACKAGE_BAG` | 同时启用两条 |
| AMT-008 | 美团 | `phf_ssf`、急送费 | 春节服务费已在配送费内。急送费在原价和总价内 | 急送费是否也在配送费内 | 春节服务费不单独加计 | 重复计入 | 平台官方文档 | `AMT.FEE.STORE_DELIVERY` | 把 `phf_ssf` 加进配送费 |
| MAP-06 | 美团 | `poi_receive_detail` 及 `_yuan` | 默认不推送。内部字段未展开 | 子字段清单、单位和与应收的关系 | 未识别 | 把对账对象当成结算额 | 平台官方文档；财务负责人 | 无正式类型 | 展开后参与计算 |
| AMT-009 | 美团 | `extras`、`consumer_prepays` | 数组可含骑手元素。预付按顺序对应 | 元素级完整定义和加减公式 | 优惠明细仅作未识别候选 | 缺额当 0，或预付重复扣减 | 平台官方文档 | `AMT.ORD.DISCOUNT_DETAIL` | 直接参与计算 |
| AMT-006 | 美团 | `wmPoiReceiveCent` | 应收，单位分，可空 | 生产空值是否常见，本轮未抽样 | 商家应收 | 用总价填充，或写成结算额 | 财务负责人 | `AMT.MCH.RECEIVABLE`、`AMT.FIN.SETTLEMENT` | 填充和结算映射 |
| SRC-001 | 淘宝闪购 | 217 及其他消息 | 只有消息名 | 消息字段结构 | 全部未识别 | 用其他淘宝文档补字段 | 平台技术支持 | 无 | 任何正式金额映射 |
| AMT-004 | 淘宝闪购 | 快照金额字段 | 只在旧代码出现 | 官方类型、单位、含义 | 未识别；`totalPrice` 冲突 | 把 `totalPrice` 当成实付 | 平台技术支持；业务负责人 | `AMT.USR.PAID`、`AMT.ORD.AFTER_DISCOUNT` | 选择其中一个解释 |
| MAP-07 | 富智云 | `payAmt` | 摘录写实付并有组成 | 组成是否都来自用户；组合支付 | 实付候选但不生效 | 写成跨平台实付或支付合计 | 业务负责人 | `AMT.USR.PAID`、`AMT.PAY.SUCCESS` | 生效映射和加总 |
| PAY-002 | 富智云 | `payAmtExtra` | 旧代码不拆行 | 报文结构 | 未识别 | 丢列或重复计算 | 业务负责人 | 无 | 拆成第二支付行 |
| PAY-003 | 富智云 | `feeAmt` | 摘录只写 Long | 单位和是否为手续费 | 支付手续费候选但不生效 | 把 Long 当成单位分 | 财务负责人 | `AMT.FEE.PAYMENT` | 单位转换 |
| MAP-08 | 富智云 | `expressAmt` | 摘录有快递列 | 它是门店配送费还是用户支付运费 | 两个候选冲突 | 选错费用类型 | 平台技术支持 | 两个运费类型 | 选择其中一个 |
| AMT-005 | 富智云 | `packagePriceDisAmt` | 名称存在，正负未说明 | 业务含义和符号规则 | 未识别 | 取绝对值后进入营业额 | 业务负责人 | `AMT.IND.TURNOVER` | 取绝对值和计入成交额 |
| GBV-001 | 富智云 | 团购八列 | 代码不换算。DDL 注释写分 | 接口原始单位 | 各自的团购编码，均不生效 | 按注释乘除 100 | 平台技术支持 | 八个 `AMT.GBV.*` | 单位转换和正式映射 |
| GBV-002 | 富智云 | 团购八列之间 | 没有平衡公式 | 列间关系 | 无 | 互相加减后当成收入 | 财务负责人 | 团购收入和服务费 | 列间计算 |
| PAY-001 | 四平台 | 订单金额与支付流水 | 旧代码不做相等校验 | 支付成功状态的共同含义 | 无字段映射 | 用应付或总价代替成功合计 | 业务负责人 | `AMT.PAY.SUCCESS` | 字段映射和加总 |
| REF-003 | 四平台 | 成功退款 | 旧代码按退款号累加 | 这是否为最终退款额 | 无字段映射 | 把累加结果当成实退 | 财务负责人 | `AMT.RFD.SUCCEEDED`、`AMT.RFD.USER_ACTUAL` | 成功合计和实退映射 |
| AMT-001 | 四平台 | 旧营业额输入 | 三套代码公式已记录 | 经营成交额书面定义 | 禁止映射 | 旧公式改名成经营成交额 | 业务负责人 | `AMT.IND.TURNOVER` | 任何字段直接映射 |
| MAP-09 | 四平台 | 结算相关 | 没有公式。结算样本行数不能证明口径 | 财务结算规则 | 禁止映射 | 用应收或实付顶替 | 财务负责人 | `AMT.FIN.SETTLEMENT` | 映射和推导 |
| MAP-10 | 京东、美团、淘宝、富智云 | 设计文档中的额外金额名 | 本轮只看到名称，没有官方页 | 字段路径、类型、单位、含义 | 无 | 为了覆盖率强行归类 | 平台技术支持 | 无 | 建立正式候选 |

MAP-10 至少包括：京东 `platformPointsDeductionMoney`、`orderReceivableFreight`、`afsMoney`、`externalOnlinePayment`、商品行 `refundUserMmoney`，以及淘宝优惠拆分字段的报文路径。这些名字还没有定位到官方契约。富智云 `orderAmt`、`payAmtExtra`、`wipeZeroAmt` 已改到代码调查来源，不再算作订单摘录字段。路径尚未确定的名字，确认前不建正式观测。

## 12. 映射验收标准

一条映射在将来要变成 `ACTIVE`，至少同时满足：

1. 目标编码存在于冻结目录，且不是 `RETIRED`。
2. 目标类型已经被明确采用；在那之前，类型仍是 `DRAFT`，映射也不能生效。
3. `mappingEligibility` 不是 `NOT_UNTIL_CONFIRMED` 或 `DISALLOWED`。
4. 同一契约版本、同一路径只有一个正式目标。candidateDisposition 仍是 `MULTI_CANDIDATE` 或 `CONFLICTING_CANDIDATES` 时不能生效；执行中的 `CONFLICT` 也必须先消除。
5. 单位、币种、精度要么已由契约写明，要么规则明确声明不转换。未知不能被填默认值。
6. 规则能追溯 `sourceContractId`、`sourceContractVersion`、`fieldPath`、`mappingId`、`mappingVersion` 和证据引用。
7. 不依赖其他平台的同名字段。
8. 不把计算合计伪装成字段映射。

本文件自己的验收只检查设计是否守住这些门禁，不表示映射已经通过生产验收。

## 13. 风险清单

| 风险 | 后果 | 本设计的限制 |
| --- | --- | --- |
| 用字段名相似做映射 | 淘宝 `totalPrice`、美团 `total`、京东应付被收成同一个实付 | 相似名称不共用类型 |
| 官方页未证明等于回调 | 美团定义被套到另一份报文 | 两份契约分开 |
| 摘录过期 | 京东和富智云字段已变更仍被映射 | 版本写“未提供”，规则保持草稿 |
| 单位来自代码或 DDL | 团购和手续费被错误换算 | 未知单位禁止转换 |
| 非标量被提前计算 | 对账对象、优惠数组、预付被加进订单额 | 映射阶段不计算 |
| 旧营业额公式被改名 | 经营口径在没有书面定义时生效 | 指标类型禁止字段映射 |
| 历史被覆盖 | 确认后的新规则改写旧观测 | 新版本只作用于新观测 |

## 14. 设计结论

四个平台都有调查范围，但可追溯程度不同。美团 docDetail/175 有官方字段表。京东创建消息被证明没有金额，查询金额仍停在摘录。淘宝闪购只有消息目录。富智云订单和支付依赖仓库摘录，团购八列依赖代码和表结构。

现在可以写下的结论只有这些：

- 没有任何已确认、已批准或 `ACTIVE` 的生产映射。
- 美团原价、总价、门店配送费、商家应收、打包袋与官方用词对得上，所以保留单一或互斥候选。单位未写明的仍不转换。总价不产生实付。应收不产生结算额。
- 京东、淘宝、富智云订单金额，以及团购八列，确认前只保存未识别观测或继续保持无正式观测。
- 支付成功合计、退款成功合计、经营成交额、净成交额、结算额和已停用配送费，都不能由字段映射填入。
- 旧代码怎么赋值，全部保留为 E4 对照，不进入转换策略。

这是一套可审计的映射草案，不是已经冻结的金额口径。

## 15. 修订记录

| 轮次 | 内容 |
| --- | --- |
| 初稿 | 建立四平台字段清单和候选映射 |
| 第二轮 | 映射状态改回 06-03-01 的定义侧与执行侧。候选评估改称 candidateDisposition。代码字段不再写入未经证明的官方契约。补齐 `MAP-MT-BAG-FEE` |

标准金额类型编码不再扩展。下一步若要继续，只应补充本文件第 11 节所缺的证据，而不是新增类型编码。
