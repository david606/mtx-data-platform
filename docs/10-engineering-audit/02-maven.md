# 02 Maven

命令在 2026-11-22 执行，`JAVA_HOME` 指向 Temurin `25.0.5.1+2`。三条构建命令和依赖树的进程退出码均为 0。

| 命令 | 结果 |
| --- | --- |
| `mvn -B clean validate` | BUILD SUCCESS。Enforcer `require-jdk-25` 通过 |
| `mvn -B clean test` | BUILD SUCCESS。`PlatformTest` 1 个测试通过；`RdpServerApplicationTest` 1 个测试通过 |
| `mvn -B clean verify` | BUILD SUCCESS。verify 在依赖树之前执行，整条命令链退出码为 0 |
| `mvn -B -Dverbose dependency:tree` | BUILD SUCCESS |

`mvn clean` 删除了 `rdp-common/target` 与 `rdp-server/target`。这是验证命令的构建输出，不是源码变更。

## 坐标

- GroupId：`com.mtx.rdp`。与审查基线一致。
- 根 ArtifactId：`mtx-data-platform`。与审查基线一致。
- 子 ArtifactId：`rdp-common`、`rdp-server`。与十模块名单不一致。
- Parent：两个子模块都指向根 POM `0.1.0-SNAPSHOT`，没有 Spring Boot parent。Spring Boot 通过 BOM import 管理。

## 依赖与冲突

`rdp-server` 直接依赖只有 `rdp-common`、`spring-boot-starter`、`spring-boot-starter-test`。verbose 树里重复项标记为 `omitted for duplicate`。没有打印冲突失败，构建成功。

观察到的版本收敛：AssertJ 请求的 Byte Buddy 在树中显示为 `byte-buddy:1.17.8 (version managed from 1.18.3)`。最终使用 1.17.8，构建未失败。

`rdp-common` 不依赖 `rdp-server`。模块依赖是 `rdp-server -> rdp-common`，没有模块循环。

MyBatis-Plus、Quartz starter、spring-kafka 出现在父 POM 的 `dependencyManagement`，没有任何子模块声明这些依赖，所以它们没有进入本次 reactor 的类路径。这不是传递依赖泄漏，而是声明了未使用的版本管理。

`spring-boot-starter` 传递了日志、Spring Context、SnakeYAML 和 Micrometer Observation。没有 Web、JDBC、Security、Kafka 或 Quartz 传递依赖。
