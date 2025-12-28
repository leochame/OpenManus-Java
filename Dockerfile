# 多阶段构建 Dockerfile for OpenManus Java

# 阶段1: 构建阶段
FROM maven:3.9-openjdk-21-slim AS builder

# 设置工作目录
WORKDIR /app

# 复制 Maven 配置文件
COPY pom.xml .

# 下载依赖(利用Docker层缓存)
RUN mvn dependency:go-offline -B

# 复制源代码
COPY src ./src

# 构建应用
RUN mvn clean package -DskipTests -B

# 阶段2: 运行时镜像
FROM openjdk:21-jre-slim

# 设置维护者信息
LABEL maintainer="OpenManus Team <team@openmanus.io>"
LABEL version="1.0.0"
LABEL description="OpenManus Java - AI Agent System"

# 安装必要的系统包
RUN apt-get update && apt-get install -y \
    curl \
    wget \
    && rm -rf /var/lib/apt/lists/*

# 创建非root用户
RUN groupadd -r openmanus && useradd -r -g openmanus openmanus

# 设置工作目录
WORKDIR /app

# 从构建阶段复制JAR文件
COPY --from=builder /app/target/*.jar app.jar

# 创建必要的目录
RUN mkdir -p /app/workspace /app/logs \
    && chown -R openmanus:openmanus /app

# 切换到非root用户
USER openmanus

# 默认服务端口（可在 docker-compose / 运行时覆盖）
ENV SERVER_PORT=8089

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD curl -f http://localhost:${SERVER_PORT:-8089}/actuator/health || exit 1

# 暴露端口
EXPOSE 8089

# 设置JVM参数
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -XX:+UseStringDeduplication"

# 启动应用
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"] 