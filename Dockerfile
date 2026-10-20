# ============================================================
# 多模块 Maven 项目的两阶段构建
#
# 关键点：这是个 sr-common / sr-pojo / sr-server 三模块的聚合工程，
# 根目录没有 src，jar 产物也不在根 target 里 —— 照单模块项目的写法一定会失败。
# ============================================================

# ---------- 阶段 1：编译打包 ----------
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app

# 先只拷 4 个 pom 再拉依赖：之后改 Java 代码不会让这一层缓存失效，重建快很多
COPY pom.xml .
COPY sr-common/pom.xml sr-common/
COPY sr-pojo/pom.xml sr-pojo/
COPY sr-server/pom.xml sr-server/
RUN mvn -B -DskipTests dependency:go-offline || true

# 再拷源码（.dockerignore 已经把 target 排除掉了）
COPY sr-common/src sr-common/src
COPY sr-pojo/src sr-pojo/src
COPY sr-server/src sr-server/src
RUN mvn -B clean package -DskipTests

# ---------- 阶段 2：运行镜像 ----------
# 只用 JRE，镜像比 builder 小一个数量级，也没有 Maven 和源码
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

ENV TZ=Asia/Shanghai

# 可执行 jar 由 spring-boot-maven-plugin 产出在 sr-server/target 下
COPY --from=builder /app/sr-server/target/sr-server-*.jar app.jar

EXPOSE 8080

# 用 prod profile 启动；配置全部走环境变量，镜像里不留任何密码
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=prod"]
