# 03 模块依赖

## 实际依赖

```
mtx-data-platform
  rdp-common          无内部模块依赖
  rdp-server          -> rdp-common
```

`rdp-common` 只有平台名称、简称和 GroupId 常量，见 `Platform.java`。它没有成为连接器、消息或持久化的汇聚点。就现有代码而言，common 万能模块问题**未发生**，因为业务代码不存在。

## 对照十模块

| 检查项 | 证据 | 结论 |
| --- | --- | --- |
| `platform-core` 是否依赖具体实现 | 模块目录不存在 | 未发现实现。不能证明存在向下依赖，也不能证明分层已建立 |
| 能力模块横向依赖 | `platform-messaging`、`connector`、`transform`、`quality`、`writer` 均不存在 | 未发现不合理横向依赖，也未发现这些模块 |
| `platform-pipeline` 职责 | 模块不存在；Java 源码无 Pipeline 类型 | 未发现实现 |
| `app-admin` 与 `app-worker` 代码依赖 | 两个模块都不存在 | 未发现二者之间的依赖 |
| 禁止新增的模块 | 仓库存在 `rdp-server`。不存在 `platform-domain`、`platform-adapter`、`platform-plugin`、`platform-gateway`、`platform-scheduler` | `rdp-server` 与“禁止新增 rdp-server”冲突。其余禁止模块未发现 |

`docs/architecture/module-plan.md` 规划了另一套 `rdp-*` 模块，并写明当时只创建 `rdp-common` 与 `rdp-server`。那是仓库内的初始化说明，不是十模块已经落地的证据。
