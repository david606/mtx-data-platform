# 06-02-R-05 Doris 金额事实写入与 Sequence 一致性专项核验

本轮只读代码、仓库内接口摘录和已完成的 06-02、06-DB-01-06。没有改代码，没有查生产业务行，没有把设计文档或代码注释写成官方字段定义。

06-DB-01-06 已确认：订单、明细、优惠、支付、充值、退款、退款明细是 `UNIQUE KEY`，`enable_unique_key_merge_on_write=true`，sequence 列是 `meta_sink_sequence`。团购核销表唯一键是 `brand_id, coupon_info_id, check_month`，没有 `meta_sink_sequence`。

## 代码里的比较条件

| 路径 | 条件 | 相同序号 | 空序号 | 位置 |
| --- | --- | --- | --- | --- |
| 营业额更新 | `IS NULL OR <= 新序号` | 允许再写 | 允许被写入 | `orderTurnoverUpdateSql` 5341 行 |
| 订单 CORE / DETAIL 更新 | 同上 | 允许再写 | 允许被写入 | 5390、5436 行 |
| 京东、淘宝商品退款累计 | `<=` | 允许再写 | 这条 SQL 没写 `IS NULL`，空序号不会匹配 `<=` | 4132 行 |
| 富智云商品补丁 | `<` | 拒绝 | 同样不匹配 | 4188 行 |
| 过期商品删除条件改写 | `<=` | 允许 | 见该 SQL | 4578 行 |

所以各事实表的表属性都有 sequence，但 Java 更新条件并不一致。相同序号在订单营业额上可以覆盖，在富智云商品补丁上不行。

Doris 表属性的 sequence 还会在导入时比较。本轮没有把“表属性拒绝旧序号”和“SQL WHERE 再比较一次”合成一个未经代码证明的总规则。SQL 条件以上表为准。

## 序号从哪来

富智云支付、充值、退款的 `sinkSequence` 用 `FuiouFinancialSequence.ordinary`：业务时间毫秒 × 1_000_000 + 事件号尾数。同一毫秒、尾数相同会得到相同序号。相同序号在 `<=` 的 SQL 里可以再次写入。

京东、美团、淘宝订单级收款 Mapper 把 `sinkSequence` 先写成 0，见 `JdOrderReceiptMapper` 35 行、`MeituanPaymentFactMapper` 49 行、`TaobaoPaymentFactMapper` 31 行。0 是否在写入前被换成别的序号，要看协调器后续赋值。本轮只确认 Mapper 出口是 0，没有把 0 说成最终入库序号。

重复事件如果事件号不同，富智云普通序号的尾数可能不同。相同原文、相同业务时间、尾数碰撞时序号可以相同。代码没有另做冲突检测来禁止相同序号的不同内容。

团购表没有这列，见 R-07。

等级：表结构为生产结构已确认。比较条件为代码已确认。相同序号的生产覆盖结果没有抽样。
