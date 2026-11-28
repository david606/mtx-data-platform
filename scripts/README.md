# 脚本

仓库脚本只做工程辅助，不包含业务数据处理。

- `verify.sh`：在 JDK 25 上执行 `mvn clean verify`。
- `start-company.sh admin|worker`：固定使用 `company` profile。仓库根目录存在仅所有者可读写的 `.env` 时只读取四个账号变量，否则使用已经导出的变量。缺少变量或账号为 root 时失败。不执行 DDL。
- `start-local.sh admin|worker`：固定使用 `local` profile。加载规则与公司脚本相同，不会改用公司地址。不执行 DDL。

不要在脚本中写入令牌、密码或生产连接串。
