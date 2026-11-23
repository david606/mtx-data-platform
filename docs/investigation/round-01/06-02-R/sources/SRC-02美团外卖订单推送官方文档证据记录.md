# SRC-02 美团外卖订单推送官方文档证据记录

- 官方 URL：https://developer.waimai.meituan.com/home/docDetail/175
- 页面标题：创建读取时浏览器标题为“美团技术服务合作中心……”；正文标题为“APP方URL 推送已确认订单”。
- 平台：美团外卖
- 产品线与接口：餐饮美食 / 订单推送 / APP方URL 推送已确认订单。HTTP POST form。
- 获取时间：2026-11-23 16:42:10 +0800
- 页面版本或更新时间：页面未提供文档版本号或更新日期。
- 获取方式：浏览器打开 URL，读取正文。页面提示“您还没登录”。字段表在未登录时已展示。
- 读取是否完整：应用级参数表和打包费、活动说明已读到。请求示例为“暂无”。嵌套对象 `poi_receive_detail` 内部字段没有在根表展开。

## 关键原文

接口说明：美团向 APP 方推送订单状态为已确认的订单。

打包费说明原文要点：`package_bag_mode`、`package_bag_fee`、`package_bag_cart_detail` 为一组。`mode=0` 未返回时用顶层 `detail` 的 `box_num×box_price` 加总，不要再乘 `quantity`，也不要加 `sub_detail_list`。该组与 `package_bag_money` 不是同一口径。

| 字段 | 类型 | 示例 | 页面描述中的单位和含义 |
| --- | --- | --- | --- |
| `total` | double | 22.1 | 总价。金额包含 1 对 1 急送费。该行未写“元”，示例是小数 |
| `original_price` | double | 23.1 | 原价。订单原价包含 1 对 1 急送费。该行未写“元” |
| `shipping_fee` | float | 1.9 | 门店配送费。该行未写“元” |
| `wmPoiReceiveCent` | long | 1234 | 商家应收金额，单位分。商家承担急送营销时会扣减，不重复扣减 |
| `package_bag_fee` | double | 12.9 | 订单维度打包费总额，单位元。有该字段时直接使用，不要用商品行加总覆盖 |
| `package_bag_money` | int | 100 | 打包袋金额，单位分。不是 `package_bag_fee` |
| `package_bag_money_yuan` | string | 1.00 | 打包袋金额，单位元 |
| `phf_ssf` | double | 1.20 | 拼好饭春节服务费，单位元。已计入 `shipping_fee`，不能再加到配送费 |
| `poi_receive_detail` | object | 无示例 | 商家对账。默认不推送，需订阅 |
| `poi_receive_detail_yuan` | object | 无示例 | 商家对账，金额单位元。默认不推送 |
| `extras` | list | 含满减和 `rider_fee` | 优惠元素与骑手应付款元素可能同数组。有 `rider_fee` 时按骑手元素处理 |
| `consumer_prepays` | list&lt;string&gt; | `["10.0","0.0"]` | 与优惠明细按顺序对应的顾客预付金额字符串。可能不下发 |

活动兼容说明：不应仅因未知活动类型拒单，应按订单返回的金额字段处理。

## 本轮补录的模式枚举

同一页面 2026-11-23 16:42 已读到，第一轮 SRC 没有单独成表。原文没有再推断。

`package_bag_mode` 类型 int，必填为否，示例 2。页面描述：

- 0 = 按商品餐盒费。仅部分应用返回该组字段。未返回不代表金额为 0，按商品行餐盒费回退。
- 1 = 按订单总价。会同时返回 `package_bag_fee` 与 `package_bag_cart_detail`，无需单独开通。
- 2 = 按订单口袋。同样会同时返回上述两个字段。

`package_bag_fee` 的优先级写在字段行：有本字段时直接使用，不要用商品行自行加总覆盖。mode=0 时它等于本单商品餐盒费总额；mode=1 是整单打包费；mode=2 是各口袋 `original_package_bag_fee` 之和。

回退计算只在 mode=0 且该组未返回时成立：顶层 `detail` 的 `box_num×box_price` 加总，不要再乘 `quantity`，不要加 `sub_detail_list`。mode=1 或 2 没有这条回退授权。

页面没有把“返回 0”定义成另一种缺失。字段不存在与数值 0 因此不能写成同义词。


## 与 trade-flow

trade-flow 订单回调事件名是 `managedCancelAndConfirmOrder`，不是本页标题里的“已确认订单推送”这个接口名。查询详情会把 `original_price`、`shipping_fee`、`wmPoiReceiveCent`、`extras` 转成代码使用的驼峰字段。见 `MeituanOrderDetailNormalizer` 117–145 行。

因此：这些官方定义可以用来核对同名字段；不能把本页未经字段对照就说成 trade-flow 回调报文的完整格式。回调测试夹具使用 `originalPrice`、`wmAppOrderFoods`，与本页的 `original_price`、`detail` 名称不同。

证据等级：上表字段定义为 E1。与 trade-flow 回调是否同一接口，待确认。
