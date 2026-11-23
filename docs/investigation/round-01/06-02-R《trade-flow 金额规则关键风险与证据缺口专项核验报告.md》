# 06-02-R trade-flow 金额规则关键风险与证据缺口专项核验报告

本轮在 06-02 已确认的代码公式和 06-DB-01-06 的生产表结构上做专项核对。没有改代码、没有查生产业务行、没有开始 06-03。

专题在 `investigation/round-01/06-02-R/`。

## 1. 已确认的金额规则

营业额公式仍是：京东实付加订单级优惠；美团和淘宝是计价基数减用户配送费；富智云再加套餐调整，保留正负号。入口是 `OrderTurnoverCalculator.calculate`，调用点是 `CanonicalOrderReducer` 第 478 行。

富智云 `orderDisAmt`、`payAmt` 的单位和组成，能对上仓库摘录 `docs/references/fuiou/api.md` 第 22–23 行。京东 `orderDiscountMoney` 不含单品和运费优惠，能对上 `docs/references/jd/jd.o2o.order.es.query.md` 第 202 行。`canteenMoney` 在摘录第 284 行是商品总餐盒费，代码把它汇总进包装费列。

退款跨事件归并按 `refundId` 和 `sourceVersion`。更小版本不覆盖。已经成功的退款不能被后来的非成功通知撤销，`CanonicalOrderReducer` 1639–1644 行。京东在现金原收款齐全时，退款额大于原收款会抛错，`JdAfsRefundMapper.refundType` 56 行。

团购八个金额不缩放。同月修正不改变 `check_month`。跨月修正会换成新的唯一键，同步代码不删除旧月行。

## 2. 仍待确认的业务定义

- 京东 `orderBuyerPayableMoney` 是否包含餐盒费和运费。摘录只写“用户应付金额，单位分”。
- 京东 `orderDiscountMoney` 是否不含包装优惠。摘录写了不含单品和运费优惠，没写包装。
- `packagingMoney` 和 `canteenMoney` 同时存在时，业务上是否只认餐盒费。
- 富智云 `packagePriceDisAmt` 会不会为负。摘录没有正负说明，代码不取绝对值。
- 美团 `originalPrice`、`total` 和淘宝 `originalPrice`、`totalAmount`、`totalPrice` 的官方定义。`docs/references` 没有这两家的字段摘录。
- 配送费是否应该从营业额扣除。代码对美团、淘宝、富智云扣除，对京东不扣除。
- 团购八个 DECIMAL 的原始单位。
- 退款是否允许大于实付。只有京东在原收款证据齐全时拒绝。

## 3. 高风险代码行为

- 京东售后 `refundUserMmoney`、`cashMoney` 用 `JsonNode.longValue()`，小数会截断。订单详情金额用 `cents`，小数会变成空，两条路径不一致。
- 京东原收款 `JdOrderReceiptMapper` 32 行对实付同样使用 `longValue()`。
- 美团、淘宝元转分仍是四舍五入。
- 商品首次插入把未知退款成功额和数量写成 0。

## 4. Doris 更新和空值

订单头多数金额更新用 `COALESCE`，空参数留旧值，不能表示撤销。营业额更新直接赋值，空值会清空，条件是序号为空或小于等于新序号。富智云商品补丁用小于，相同序号不能更新。京东、淘宝退款累计用小于等于，相同序号可以再写。

## 5. 退款和支付

富智云支付状态只有成功、已退款、未知，未知仍写支付事实。美团未支付、淘宝未在线支付不写支付事实。京东支付事实来自 `JdOrderReceiptMapper` 的 `ORDER_PAY`，不是订单 Mapper。没有统一校验订单实付等于支付事实金额。手续费只看到富智云 `feeAmt`。

## 6. 团购

没有 sequence。同一 `brand_id + coupon_info_id + check_month` 的后一次 upsert 提交整行。跨月时间修正可能留下旧月行。没有金额平衡公式，也不进入营业额。

## 7. 测试

营业额分支、富智云退款累加和冲突、顺序号边界、团购 0 值和月份键有测试断言。京东小数截断、相同序号覆盖、团购 `scale > 6`、订单实付与支付事实核对，没有在已读测试里看到。

## 8. 需要业务方提供的材料

1. 美团订单字段说明：`originalPrice`、`total`、`shippingFee`，以及 `total` 是否含配送费、包装费、商品券购买额。
2. 淘宝闪购订单字段说明：`originalPrice`、`totalAmount`、`totalPrice`、`deliverFee`、`packageFee`。
3. 京东应付金额的组成说明，以及 `orderGoodsMoney` 的官方定义。
4. 京东售后字段类型和单位：`refundUserMmoney`、`cashMoney`、`skuCount`、`afsServiceState` 各码含义。最好附一笔含小数或不含小数的脱敏报文。
5. 富智云 `packagePriceDisAmt` 的正负样例。
6. 团购核销查询字段单位，以及一笔八个金额都有值的脱敏行。
7. 各平台一笔部分退款、一笔多次退款的脱敏报文，用来核对累计是否允许超过实付。

## 9. 能否形成统一金额模型

现在可以列出统一字段和各平台来源，但不能把四个平台收成同一个业务公式。营业额算法已经分叉，空值含义分叉，支付事实的生成条件也分叉。统一模型要等上面的字段定义确认后再定口径。

## 10. 能否进入 06-03

代码风险和证据缺口已经列清，可以作为 06-03 的输入清单。还不具备把金额口径定稿、或开始改生产计算的条件。本轮停止，不开始 06-03。
