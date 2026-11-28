# 脚本

仓库脚本只做工程辅助，不包含业务数据处理。

- `verify.sh`：在 JDK 25 上执行 `mvn clean verify`。
- `start-company.sh admin|worker`：导出已有环境变量后，用 `company` profile 启动对应应用。不执行 DDL。
- `start-local.sh admin|worker`：用 `local` profile 启动对应应用。账号变量与公司环境相同。不执行 DDL。

不要在脚本中写入令牌、密码或生产连接串。
