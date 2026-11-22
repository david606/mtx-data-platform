# 04 数据库

## 扫描结果

仓库中没有 `.sql` 文件，没有 Flyway 迁移目录，没有 MyBatis Mapper XML。`sql/README.md` 写明当前没有已确认的表，因此没有脚本。`docs/database/README.md` 同样声明库表尚未确认。

## 四张历史表

在全部受控文本中检索 `dp_task_execution`、`dp_write_batch`、`dp_write_config`、`dp_writer_batch_ledger`，匹配数为 0。

| 表 | 本仓库中的结构 | 索引 | 唯一约束 | 迁移 |
| --- | --- | --- | --- | --- |
| `dp_task_execution` | 未发现 | 未发现 | 未发现 | 未发现 |
| `dp_write_batch` | 未发现 | 未发现 | 未发现 | 未发现 |
| `dp_write_config` | 未发现 | 未发现 | 未发现 | 未发现 |
| `dp_writer_batch_ledger` | 未发现 | 未发现 | 未发现 | 未发现 |

没有从其他目录补字段。本仓库给不出这些表的列定义。
