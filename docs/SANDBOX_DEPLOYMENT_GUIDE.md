# VNC 沙箱浏览器部署指南

## 概述

本指南介绍如何部署和配置 OpenManus 的 VNC 沙箱浏览器功能，使 Agent 的浏览器操作能够实时展示在前端界面。

## 系统架构

```
┌─────────────┐         ┌──────────────┐         ┌──────────────────┐
│   前端页面   │ ◄─────► │  Spring Boot │ ◄─────► │  Docker Engine   │
│  (Vue.js)   │         │   后端服务    │         │  (VNC 容器)      │
└─────────────┘         └──────────────┘         └──────────────────┘
      │                                                     │
      │                                                     │
      └─────────────────── iframe ─────────────────────────┘
                      (VNC Web 界面)
```

## 环境要求

### 必需组件

- **Java 21+**
- **Maven 3.9+**
- **Docker Engine 20.10+** (必须)
- **Docker 权限**: 运行 Spring Boot 的用户需要 Docker 操作权限

### 推荐配置

- **内存**: 至少 4GB RAM (每个 VNC 容器约需 1GB)
- **CPU**: 2 核心或更多
- **磁盘**: 至少 10GB 可用空间
- **网络**: 允许动态端口绑定 (6080+ 端口范围)

## 部署步骤

### 1. 配置环境变量

在启动应用之前，设置以下环境变量（可选）：

```bash
# 宿主机地址（用于生成 VNC URL）
export OPENMANUS_HOST_ADDRESS=localhost  # 开发环境
# export OPENMANUS_HOST_ADDRESS=your-server-ip  # 生产环境
```

### 2. 验证 Docker 环境

```bash
# 检查 Docker 是否正常运行
docker ps

# 测试拉取 VNC 镜像
docker pull dorowu/ubuntu-desktop-lxde-vnc:latest

# 测试启动 VNC 容器
docker run -d --name test-vnc -p 6080:80 dorowu/ubuntu-desktop-lxde-vnc:latest

# 验证容器是否运行
docker ps | grep test-vnc

# 浏览器访问: http://localhost:6080/vnc.html
# 应该能看到 Ubuntu 桌面

# 清理测试容器
docker stop test-vnc
docker rm test-vnc
```

### 3. 配置 Spring Boot

在 `application.yml` 中添加或确认以下配置：

```yaml
openmanus:
  sandbox:
    # 启用沙箱功能
    use-sandbox: true
    
    # VNC 镜像（已在代码中硬编码，此处仅供参考）
    # vnc-image: dorowu/ubuntu-desktop-lxde-vnc:latest
    
    # 资源限制
    memory-limit: 1g
    cpu-limit: 2.0
    
    # 超时设置
    timeout: 60
    
    # 网络设置
    network-enabled: true

# 定时任务（用于清理过期沙箱）
spring:
  task:
    scheduling:
      enabled: true
```

### 4. 启动应用

```bash
# 开发模式
./mvnw spring-boot:run

# 或打包运行
./mvnw clean package
java -jar target/openmanus-*.jar
```

### 5. 验证部署

#### 后端验证

```bash
# 1. 启动一个会话并获取 sessionId
curl -X POST http://localhost:8089/api/agent/think-do-reflect-stream \
  -H "Content-Type: application/json" \
  -d '{"input": "帮我访问 https://www.baidu.com"}'
  
# 返回示例: {"success":true,"sessionId":"abc-123","topic":"/topic/executions/abc-123"}

# 2. 等待几秒后查询会话信息
curl http://localhost:8089/api/agent/session/abc-123

# 如果沙箱已创建，应返回：
# {
#   "sessionId": "abc-123",
#   "sandboxVncUrl": "http://localhost:32768/vnc.html",
#   "sandboxContainerId": "...",
#   "sandboxStatus": "RUNNING",
#   "sandboxAvailable": true
# }
```

#### 前端验证

1. 打开浏览器访问: `http://localhost:8089`
2. 在聊天框中输入: "帮我搜索最新的AI新闻"
3. 观察右侧是否出现"Agent 浏览器工作台"面板
4. 检查 iframe 是否正确加载 VNC 桌面环境

#### Docker 容器验证

```bash
# 查看正在运行的 VNC 容器
docker ps | grep vnc-sandbox

# 查看容器日志
docker logs <container-id>

# 检查端口映射
docker port <container-id>
```

## 生产环境配置

### 使用 Nginx 反向代理

为了安全和稳定，建议在生产环境使用 Nginx 代理 VNC 端口：

```nginx
# /etc/nginx/sites-available/openmanus-vnc

upstream vnc_backend {
    # 动态上游需要使用变量或脚本生成
    # 或使用域名而非端口映射
}

server {
    listen 443 ssl;
    server_name vnc.yourdomain.com;

    ssl_certificate /path/to/cert.pem;
    ssl_certificate_key /path/to/key.pem;

    location / {
        proxy_pass http://localhost:$vnc_port;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        
        # WebSocket 支持
        proxy_read_timeout 3600;
        proxy_send_timeout 3600;
    }
}
```

### Docker 网络隔离

为沙箱容器创建专用网络：

```bash
# 创建专用网络
docker network create openmanus-sandbox-net

# 在代码中配置使用该网络
# 修改 VncSandboxClient.java 的网络配置
```

### 资源限制和监控

```yaml
# application-prod.yml

openmanus:
  sandbox:
    # 生产环境资源限制
    memory-limit: 512m  # 降低内存使用
    cpu-limit: 1.0      # 限制 CPU 使用
    
    # 缩短超时时间
    timeout: 30
    
    # 清理策略
    cleanup-interval: 1800  # 30 分钟清理一次
    max-idle-hours: 1       # 1 小时无活动则清理
```

## 故障排查

### 问题1: Docker 权限错误

**错误信息**: `Permission denied while trying to connect to the Docker daemon socket`

**解决方案**:
```bash
# 将当前用户添加到 docker 组
sudo usermod -aG docker $USER

# 重新登录或重启 Docker 服务
sudo systemctl restart docker

# 或临时使用 sudo 运行应用（不推荐）
```

### 问题2: 端口冲突

**错误信息**: `port is already allocated`

**解决方案**:
```bash
# 查找占用端口的容器
docker ps -a | grep 6080

# 停止并删除冲突的容器
docker stop <container-id>
docker rm <container-id>

# 或修改代码使用不同的端口范围
```

### 问题3: VNC 界面无法访问

**可能原因**:
1. 容器未正常启动
2. 端口映射失败
3. 防火墙阻止

**解决步骤**:
```bash
# 1. 检查容器状态
docker inspect <container-id>

# 2. 查看容器日志
docker logs <container-id>

# 3. 测试端口连通性
telnet localhost <mapped-port>

# 4. 检查防火墙
sudo ufw status
sudo firewall-cmd --list-all
```

### 问题4: 内存不足

**错误信息**: `Cannot allocate memory`

**解决方案**:
```bash
# 1. 检查系统内存
free -h

# 2. 清理未使用的 Docker 资源
docker system prune -a

# 3. 降低沙箱资源限制
# 修改 application.yml:
# memory-limit: 256m
# cpu-limit: 0.5
```

### 问题5: 沙箱创建超时

**现象**: 前端一直显示"正在启动浏览器工作台..."

**解决方案**:
```bash
# 1. 检查镜像是否已下载
docker images | grep ubuntu-desktop-lxde-vnc

# 2. 预先拉取镜像
docker pull dorowu/ubuntu-desktop-lxde-vnc:latest

# 3. 增加容器启动等待时间
# 修改 VncSandboxClient.java 中的 waitForContainerReady 超时时间
```

## 性能优化

### 1. 预热镜像

```bash
# 在服务启动前预先拉取镜像
docker pull dorowu/ubuntu-desktop-lxde-vnc:latest
```

### 2. 容器池化（高级）

实现容器池，预先创建几个 VNC 容器待用：

```java
// 在 SessionSandboxManager 中实现容器池
private final Queue<String> containerPool = new ConcurrentLinkedQueue<>();

// 应用启动时预创建容器
@PostConstruct
public void initContainerPool() {
    for (int i = 0; i < 3; i++) {
        String containerId = vncSandboxClient.createVncSandbox("pool-" + i);
        containerPool.offer(containerId);
    }
}
```

### 3. 使用更轻量的镜像

考虑使用更轻量的 VNC 镜像：

```bash
# 替代镜像选项
docker pull consol/ubuntu-xfce-vnc:latest  # 更轻量
docker pull kasmweb/chrome:latest          # 仅浏览器
```

## 监控和日志

### 启用 Docker 容器监控

```bash
# 实时查看容器资源使用
docker stats

# 查看特定容器
docker stats <container-id>
```

### Spring Boot Actuator

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,info
  
  endpoint:
    health:
      show-details: always
```

访问监控端点：
- 健康检查: `http://localhost:8089/actuator/health`
- 指标数据: `http://localhost:8089/actuator/metrics`

## 安全建议

1. **限制访问**: 仅允许可信 IP 访问 VNC 端口
2. **使用 HTTPS**: 生产环境必须使用 HTTPS
3. **设置密码**: VNC 容器设置强密码（已在代码中配置）
4. **定期清理**: 启用定时清理过期容器
5. **资源限制**: 严格限制每个容器的资源使用
6. **网络隔离**: 使用 Docker 网络隔离沙箱容器

## 备份和恢复

### 备份会话数据（可选）

```bash
# 导出容器快照
docker commit <container-id> openmanus-sandbox-backup:latest

# 保存镜像
docker save openmanus-sandbox-backup:latest > sandbox-backup.tar
```

### 恢复

```bash
# 加载镜像
docker load < sandbox-backup.tar

# 从镜像启动容器
docker run -d -p 6080:80 openmanus-sandbox-backup:latest
```

## 总结

完成以上步骤后，您的 OpenManus 系统应该能够：

✅ 自动创建 VNC 沙箱容器  
✅ 在前端实时展示 Agent 的浏览器操作  
✅ 定期清理过期的沙箱  
✅ 处理多个并发会话  

如有问题，请查看日志文件或提交 Issue。

