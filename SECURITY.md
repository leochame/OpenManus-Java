# 安全策略

## 支持的版本

我们为以下版本提供安全更新：

| 版本 | 支持 |
| --- | --- |
| 1.x.x | ✅ |
| 0.x.x | ❌ |

## 报告安全漏洞

我们非常重视安全问题。如果您发现了安全漏洞，请**不要**在公共的GitHub Issues中报告。

### 如何报告

1. **私密报告**: 请发送邮件到 security@openmanus.io
2. **包含信息**: 
   - 详细的漏洞描述
   - 重现步骤
   - 受影响的版本
   - 如果可能，提供修复建议

### 响应时间

- **确认收到**: 24小时内
- **初步评估**: 72小时内
- **详细分析**: 7天内
- **修复发布**: 根据严重程度，1-30天内

## 安全最佳实践

### 部署建议

1. **环境变量**
   - 使用环境变量存储敏感信息
   - 不要在代码中硬编码API密钥
   - 定期轮换API密钥

2. **网络安全**
   - 在生产环境中使用HTTPS
   - 限制网络访问
   - 使用防火墙保护

3. **Docker安全**
   - 使用最新的基础镜像
   - 不要以root用户运行
   - 限制容器权限

4. **依赖管理**
   - 定期更新依赖
   - 使用OWASP依赖检查
   - 扫描已知漏洞

### 配置安全

#### 1. API密钥管理
```yaml
# application.yml
openmanus:
  llm:
    api-key: ${OPENMANUS_LLM_API_KEY}  # 从环境变量读取
```

#### 2. 沙箱安全
```yaml
# application.yml
openmanus:
  sandbox:
    use-sandbox: true  # 启用沙箱模式
    memory-limit: 512m  # 限制内存使用
    cpu-limit: 1.0     # 限制CPU使用
    timeout: 120       # 设置超时时间
    network-enabled: false  # 禁用网络访问（如果不需要）
```

#### 3. 文件系统安全
```yaml
# application.yml
openmanus:
  app:
    workspace-root: ./workspace  # 限制工作空间
```

#### 4. 启用安全头
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