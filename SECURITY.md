# Security Policy

## Supported Versions

We provide security updates for the following versions:

| Version | Support |
| --- | --- |
| 1.x.x | ? |
| 0.x.x | ? |

## Reporting a Security Vulnerability

We take security issues very seriously. If you discover a security vulnerability, please **DO NOT** report it through public GitHub Issues.

### How to Report

1. **Private Report**: Send an email to security@openmanus.io
2. **Include Information**: 
   - Detailed vulnerability description
   - Steps to reproduce
   - Affected versions
   - Suggested fix if possible

### Response Time

- **Confirmation**: Within 24 hours
- **Initial Assessment**: Within 72 hours
- **Detailed Analysis**: Within 7 days
- **Fix Release**: 1-30 days depending on severity

## Security Best Practices

### Deployment Recommendations

1. **Environment Variables**
   - Use environment variables for sensitive information
   - Never hardcode API keys in code
   - Rotate API keys periodically

2. **Network Security**
   - Use HTTPS in production
   - Limit network access
   - Use firewall protection

3. **Docker Security**
   - Use latest base images
   - Don't run as root
   - Limit container privileges

4. **Dependency Management**
   - Update dependencies regularly
   - Use OWASP dependency check
   - Scan for known vulnerabilities

### Security Configuration

#### 1. API Key Management
```yaml
# application.yml
openmanus:
  llm:
    api-key: ${OPENMANUS_LLM_API_KEY}  # Read from environment variable
```

#### 2. Sandbox Security
```yaml
# application.yml
openmanus:
  sandbox:
    use-sandbox: true  # Enable sandbox mode
    memory-limit: 512m  # Limit memory usage
    cpu-limit: 1.0     # Limit CPU usage
    timeout: 120       # Set timeout
    network-enabled: false  # Disable network access (if not needed)
```

#### 3. File System Security
```yaml
# application.yml
openmanus:
  app:
    workspace-root: ./workspace  # Limit workspace
```

#### 4. Enable Security Headers
```yaml
# application.yml
server:
  servlet:
    session:
      cookie:
        secure: true
        http-only: true
```

### 运行时安全

#### 1. JVM参数
```bash
java -Djava.security.manager \
     -Djava.security.policy=app.policy \
     -jar openmanus.jar
```

#### 2. Docker运行参数
```bash
docker run --rm -it \
  --memory=512m \
  --cpus=1.0 \
  --read-only \
  --no-new-privileges \
  --security-opt=no-new-privileges \
  openmanus:latest
```

## 安全检查清单

### 开发阶段
- [ ] 代码审查包含安全检查
- [ ] 使用静态代码分析工具（SpotBugs, SonarQube）
- [ ] 运行OWASP依赖检查
- [ ] 进行单元测试和集成测试

### 部署阶段
- [ ] 使用非root用户运行
- [ ] 配置适当的文件权限
- [ ] 启用防火墙
- [ ] 使用TLS/SSL加密传输
- [ ] 定期备份数据

### 运维阶段
- [ ] 监控系统日志
- [ ] 定期更新依赖
- [ ] 扫描安全漏洞
- [ ] 审计系统访问

## 常见安全问题

### 1. API密钥泄露
**问题**: API密钥被意外提交到代码库
**解决方案**: 
- 使用环境变量
- 配置.gitignore排除敏感文件
- 定期轮换密钥

### 2. Docker容器逃逸
**问题**: 容器获得宿主机权限
**解决方案**:
- 使用非特权用户
- 限制容器权限
- 使用安全的基础镜像

### 3. 代码注入
**问题**: 恶意代码被执行
**解决方案**:
- 输入验证和过滤
- 使用沙箱环境
- 限制执行权限

### 4. 依赖漏洞
**问题**: 第三方库存在安全漏洞
**解决方案**:
- 定期更新依赖
- 使用漏洞扫描工具
- 监控安全公告

## 安全工具

### 1. 代码扫描
- **SpotBugs**: 静态代码分析
- **SonarQube**: 代码质量和安全扫描
- **Checkmarx**: 商业安全扫描工具

### 2. 依赖扫描
- **OWASP Dependency Check**: 依赖漏洞扫描
- **Snyk**: 依赖和容器扫描
- **WhiteSource**: 开源安全和许可证管理

### 3. 容器扫描
- **Docker Scout**: Docker官方安全扫描
- **Trivy**: 轻量级容器扫描器
- **Clair**: 开源容器漏洞分析

### 4. 运行时保护
- **Falco**: 运行时威胁检测
- **AppArmor/SELinux**: 系统级访问控制
- **gVisor**: 容器沙箱

## 联系我们

如果您有任何安全相关的问题或建议，请联系：

- **安全团队**: security@openmanus.io
- **项目维护者**: team@openmanus.io
- **加密通信**: 请使用我们的PGP公钥

---

**注意**: 这个安全策略会根据项目的发展和威胁环境的变化而更新。请定期查看最新版本。 