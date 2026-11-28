# 文档索引

本目录记录 MTX Data Platform 已经确认的约束，以及后续设计的存放位置。没有经过确认的详细设计不写入这些目录。

| 目录 | 用途 | 当前状态 |
| --- | --- | --- |
| [architecture](architecture/) | 模块边界、依赖方向、调度职责 | 已写入初始化阶段确认的结构 |
| [design](design/) | 经评审的详细设计 | 尚无正式设计，目录仅作占位 |
| [database](database/) | 元数据与 Doris 的库表说明 | 尚无已确认模型 |
| [api](api/) | 对外或模块间接口说明 | 尚无已确认契约 |
| [development](development/) | 本地开发、构建和调试约定，以及工程准入评估 | 已写入 JDK 25 构建说明和 [当前工程准入边界与下一阶段实施路线评估](development/当前工程准入边界与下一阶段实施路线评估.md) |
| [deployment](deployment/) | 部署拓扑与发布步骤 | 尚无已确认部署方案 |
| [operations](operations/) | 运行、告警和排障 | 尚无已确认运行手册 |
| [decisions](decisions/) | 架构与技术基线决策 | 已记录版本锁定 |

代码中的包名、依赖和调度隔离需要与这里的约束一致。约束变化时先新增决策记录，再改实现。
