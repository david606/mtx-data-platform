# 本地开发

## JDK

项目编译和测试使用 JDK 25 LTS。不要使用 JDK 27 作为本项目的当前基线。

已在开发机通过 SDKMAN 安装 Temurin `25.0.5.1+2`（版本字符串 `25.0.5.1`，2026-11-17 LTS）。该安装没有改写 SDKMAN 的默认 Java。默认 Java 仍可能是 17。构建前执行：

```bash
export JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.5.1+2-tem"
export PATH="$JAVA_HOME/bin:$PATH"
java -version
mvn clean verify
```

`java -version` 应显示 25。父 POM 的 Enforcer 拒绝 25 以外的主版本。

## Maven

使用 Maven 3.6.3 或更高版本。依赖版本由根 POM 管理，子模块不要重复声明 Spring Boot、MyBatis-Plus、Quartz、Kafka 和 JUnit 的版本号。

## 当前不会启动的组件

本地 `mvn clean verify` 不连接 Doris、Kafka 或数据库。`rdp-server` 的测试只启动没有数据源和消息监听器的 Spring 上下文。
