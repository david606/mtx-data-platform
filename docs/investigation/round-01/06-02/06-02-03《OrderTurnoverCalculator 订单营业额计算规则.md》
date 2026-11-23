# 06-02-03 OrderTurnoverCalculator 订单营业额计算规则

证据等级：代码已确认、生产结构已确认、自动化测试已验证、真实报文已验证、生产样本已验证、代码推断、待业务确认、未发现。本轮没有导出生产订单样本，因此没有“生产样本已验证”的金额口径。

类：`OrderTurnoverCalculator`，`preparation/state/OrderTurnoverCalculator.java`。唯一入口是 `calculate(CanonicalChangeSet)`，15–61 行。调用点只有 `CanonicalOrderReducer` 477–479 行。输入是归并后的订单头和富智云优惠集合，不读支付表，不读退款表。

订单必须恰好一条，否则返回 `null`。金额为负或 `null` 时对应分支返回 `null`。加法溢出捕获后返回 `null`，不回绕。

| 场景 | 输入 | 表达式 | 输出 | 平台 | 条件 | 证据 |
| --- | --- | --- | --- | --- | --- | --- |
| 京东 | `userPayAmount`、`totalDiscountAmount` | `userPay + totalDiscount` | `turnoverAmount` | JD | 两者都 `>= 0` | 19–22 行。注释：实付已含餐盒费，只加回 `orderDiscountMoney`，不加单品或配送优惠 |
| 淘宝、美团 | `orderDiscountBaseAmount`、`userDeliveryFeeAmount` | `orderDiscountBase - userDeliveryFee` | 同上 | TAOBAO、MEITUAN | 两者都 `>= 0`。不要求优惠集合齐全 | 27–30 行 |
| 富智云 | 计价基数、用户配送费、同一详情快照里的 `PACKAGE_PRICE_DISCOUNT` | `orderDiscountBase - userDeliveryFee + 套餐调整合计` | 同上 | FZY | `discountReady`、快照存在、`packagePriceAdjustmentKnown`。套餐调整保留正负号 | 32–58 行 |
| 其他平台 | 无 | 返回 `null` | 清空营业额 | 非上述四平台 | 24–25 行 | 代码已确认 |

三个概念不是同一个字段：

- `orderOriginalAmount`：Mapper 写入。富智云固定不写。京东订单 Mapper 本次未给该字段赋值。美团来自 `originalPrice`，淘宝快照来自 `totalAmount`。
- `userPayAmount`：各平台实付或应付原文字段，见 06-02-02。
- `turnoverAmount`：只由本计算器产生。Reducer 每次接受变更都重算；算不出就写入 `null`，注释写明未知值明确清空。

退款不会进入这个公式。退款后营业额是否应下降，代码没有做，不能自行补成“退款后重算营业额”。取消单也没有单独分支。富智云商家收入是另一条公式，见退款报告，不能当成营业额。

测试类 `OrderTurnoverCalculatorTest` 存在。本轮没有把它的断言当成生产样本。等级：公式代码已确认；生产金额是否符合该公式，生产样本未验证。
