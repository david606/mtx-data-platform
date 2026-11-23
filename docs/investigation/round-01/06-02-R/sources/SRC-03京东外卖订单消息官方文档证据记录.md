# SRC-03 京东外卖订单消息官方文档证据记录

- 官方 URL：https://open.jd.com/v2/#/doc/msgApi?apiCateId=201021&apiId=546
- 页面标题：创建新订单消息｜京东零售开放平台
- 平台：京东零售开放平台
- 产品线与接口：目录选中“秒送 / 秒送订单 / 创建新订单消息”。消息标识 `orderCreated`。Webhook。
- 获取时间：2026-11-23 16:42:10 +0800
- 页面版本或更新时间：页面写“更新于 2025-12-25 13:19:44”。
- 获取方式：浏览器打开带 hash 的 URL，读取正文。页面有登录入口。消息体字段在未登录时已展示。
- 读取是否完整：该消息页的触发条件、消息体和示例已读到。没有金额字段。不是订单查询接口全文。

## 实际读到的内容

触发条件：客户支付订单后，推送创建新订单消息。

消息体只有：

| 字段 | 类型 | 可空 | 示例 | 描述 |
| --- | --- | --- | --- | --- |
| `venderId` | String | 否 | 页面写不会下发 | 分发标识 |
| `billId` | String | 否 | 10003129 | 消息单据 ID |
| `statusId` | String | 否 | 32000 | 订单状态 ID |
| `timestamp` | String | 否 | 2025-10-16 13:23:30 | 时间戳 |

没有 `orderBuyerPayableMoney`、`orderTotalMoney`、`orderDiscountMoney`、`orderFreightMoney`、`packagingMoney`、`canteenMoney`，也没有售后字段。

## 与 trade-flow

`JdTradeEventHandler` 141 行：金额订单事实来自 `ORDER_DETAIL`，不是 `orderCreated`。`JdOrderFactMapper.map` 133–145 行从详情读金额。

仓库摘录 `trade-flow/docs/references/jd/jd.o2o.order.es.query.md` 是订单查询字段表，不是本消息页。两边都出现“秒送/到家”一类名称，但本页不能代替查询接口，也不能证明两者是同一可调用契约。

售后 `refundUserMmoney`、`cashMoney` 不在本页。

证据等级：本消息“无金额字段、支付后触发”为 E1。它与 trade-flow 查询接口是不是同一产品契约，标记为文档产品线或接口匹配关系待确认。
