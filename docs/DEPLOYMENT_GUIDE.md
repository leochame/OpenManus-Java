# OpenManus Java 部署指南

## 🔧 系统要求

### 基本要求
- **Java**: OpenJDK 21+
- **Maven**: 3.9+ (构建用)  
- **Docker**: 20.0+ (沙箱环境)
- **内存**: 4GB+ (推荐8GB)
- **存储**: 10GB+

## 🏠 本地开发部署

### 1. 环境准备
```bash
# 检查环境
java --version
mvn --version
docker --version
```

### 2. 获取和配置
```bash
# 克隆项目
git clone https://github.com/OpenManus/OpenManus-Java.git
cd OpenManus-Java

# 配置API密钥
cp env.example .env
# 编辑 .env 文件，设置 OPENMANUS_LLM_API_KEY
```

### 3. 启动应用
```bash
# Web模式 (默认)
mvn spring-boot:run

# 命令行模式
mvn spring-boot:run -Dspring-boot.run.arguments=--cli

# 访问 Web 界面: http://localhost:8080
```

## 🚀 生产环境部署

### 1. 构建应用
```bash
# 打包
mvn clean package -DskipTests

# 生成的文件: target/openmanus-1.0-SNAPSHOT.jar
```

### 2. 直接运行
```bash
# 设置环境变量
export OPENMANUS_LLM_API_KEY="your-api-key"

# 启动应用
java -jar target/openmanus-1.0-SNAPSHOT.jar
```

### 3. 后台运行
```bash
# 使用 nohup 后台运行
nohup java -jar openmanus-1.0-SNAPSHOT.jar > openmanus.log 2>&1 &

# 或使用 systemd 服务 (Linux)
sudo systemctl enable openmanus
sudo systemctl start openmanus
```

## 🐳 Docker 部署

### 1. 使用现有 Dockerfile
```bash
# 构建镜像
docker build -t openmanus-java .

# 运行容器
docker run -d \
  --name openmanus \
  -p 8080:8080 \
  -e OPENMANUS_LLM_API_KEY="your-api-key" \
  -v $(pwd)/workspace:/workspace \
  openmanus-java
```

### 2. 使用 Docker Compose
```yaml
# docker-compose.yml
version: '3.8'
services:
  openmanus:
    build: .
    ports:
      - "8080:8080"
    environment:
      - OPENMANUS_LLM_API_KEY=${OPENMANUS_LLM_API_KEY}
    volumes:
      - ./workspace:/workspace
    restart: unless-stopped
```

启动：
```bash
docker-compose up -d
```

## ☁️ 云平台部署

### 常见平台
- **阿里云**: ECS + Docker
- **腾讯云**: CVM + 容器服务  
- **AWS**: EC2 + ECS
- **Azure**: VM + Container Instances

### 基本步骤
1. 创建云服务器 (2核4GB+)
2. 安装 Java 21 和 Docker
3. 上传应用文件
4. 配置环境变量
5. 启动服务

## ⚙️ 配置优化

### JVM 优化
```bash
# 推荐 JVM 参数
java -Xmx2g -Xms1g -server \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -jar openmanus.jar
```

### 应用配置 (application.yml)
```yaml
server:
  port: 8080
  
openmanus:
  llm:
    default-llm:
      api-key: "${OPENMANUS_LLM_API_KEY}"
      timeout: 60
      
  sandbox:
    memory-limit: "1g"
    cpu-limit: 2.0
```

## 📊 监控和维护

### 健康检查
- Web界面: `http://localhost:8080`
- API健康: `http://localhost:8080/api/v1/agent/health`
- 系统监控: `http://localhost:8080/actuator/health`

### 日志查看
```bash
# 查看实时日志
tail -f openmanus.log

# 或 Docker 日志
docker logs -f openmanus
```

### 常见维护
- 定期检查磁盘空间
- 监控内存使用情况
- 备份工作空间数据
- 更新 Docker 镜像

## 🔍 故障排除

### 常见问题

**启动失败**
- 检查 Java 版本 (需要21+)
- 确认端口8080未被占用
- 验证 API 密钥配置

**Docker 相关错误**  
- 确保 Docker 服务运行
- 检查 Docker 权限
- 验证容器资源限制

**API 调用失败**
- 检查网络连接
- 验证 API 密钥有效性
- 确认服务可达性

### 获取帮助
- 查看日志文件
- 检查系统资源
- 参考项目文档
- 提交 GitHub Issue 