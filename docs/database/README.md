# 数据库

此目录用于说明元数据库和 Apache Doris 的模型。

事务型元数据的目标服务器版本是 MySQL 8.0.46，OLAP 服务器版本是 Apache Doris 4.0.7。两者由 [ADR-0005](../decisions/0005-mysql-doris-storage-baseline.md) 接受。版本确定不等于驱动已引入或库表已创建。

库、表、分桶和字段尚未确认，因此这里没有建表说明。确认后再把脚本放进仓库根目录的 `sql/`。Doris 不承担事务型元数据。MySQL 8.4 的手册行为不能写成 8.0.46 已验证能力。
