# OpenManus Java 开发文档

本文档为 OpenManus Java 项目的开发者提供开发指南。

## 📋 项目概述

OpenManus Java 是一个基于 Spring Boot 和 LangGraph4j 的智能代理系统。

### 核心特性
- **状态图架构**: 基于 LangGraph4j 的稳定执行流程
- **智能记忆系统**: 短期和长期记忆结合
- **智能搜索引擎**: 多数据源并发搜索
- **安全沙箱环境**: Docker 容器隔离执行
- **原生函数调用**: 利用 LLM 原生函数调用能力

## 🛠️ 技术栈

| 分类 | 技术 | 版本 |
|------|------|------|
| **核心框架** | Java | 21 (LTS) |
| | Spring Boot | 3.2.0 |
| | Maven | 3.9+ |
| **AI 框架** | LangGraph4j | 1.6.0-beta5 |
| | LangChain4j | 0.36.2 |
| **容器化** | Docker | 20.0+ |
| **测试框架** | JUnit 5, Mockito | - |

## 🏗️ 项目结构

```
src/main/java/com/openmanus/java/
├── agent/              # 智能体实现
├── config/             # 配置管理
├── web/                # Web层组件
├── tool/               # 工具集合
├── memory/             # 记忆系统
├── sandbox/            # 沙箱环境
├── llm/                # LLM客户端
└── flow/               # 流程控制
```

## 🚀 开发环境搭建

### 前置要求
- Java 21+
- Maven 3.9+
- Docker
- 阿里云百炼 API Key

### 快速启动
```bash
# 1. 克隆项目
git clone https://github.com/OpenManus/OpenManus-Java.git
cd OpenManus-Java

# 2. 配置环境变量
cp env.example .env
# 编辑 .env 文件，填入阿里云百炼 API Key

# 3. 启动开发服务器
mvn spring-boot:run
```

## 🔧 核心模块

### Agent 模块
- `OpenManusAgent`: 主代理实现
- `EnhancedOpenManusAgent`: Web版本增强代理  
- `OpenManusAgentState`: 状态管理

### Tool 模块
- `ToolRegistry`: 工具注册和管理
- `FileTool`: 文件操作工具
- `PythonTool`: Python代码执行
- `BrowserTool`: 网络搜索工具

### Web 模块
- `AgentController`: REST API控制器
- `StateMonitoringService`: 状态监控服务
- `WebSocketHandler`: WebSocket处理器

### 添加新工具
```java
@Component
public class MyCustomTool {
    @Tool("自定义工具的描述")
    public String myToolFunction(@ToolParameter("参数描述") String input) {
        // 工具逻辑实现
        return "执行结果";
    }
}
```

## 🔄 开发流程

1. **创建分支**: 从 `main` 分支创建功能分支
```bash
git checkout -b feature/your-feature-name
```

2. **代码开发**: 遵循项目代码规范

3. **编写测试**: 为新功能添加测试

4. **本地验证**: 确保所有测试通过
```bash
mvn clean test
```

5. **提交代码**: 使用规范的提交信息
```bash
git commit -m "feat(scope): add a new feature"
```

6. **创建 Pull Request**: 推送分支并发起 PR

## 🧪 测试指南

### 运行测试
```bash
# 运行所有测试
mvn clean test

# 生成覆盖率报告
mvn jacoco:report

# 运行特定测试
mvn test -Dtest=YourTestClass
```

### 测试类型
- **单元测试**: 针对单个类或方法
- **集成测试**: 测试多个组件协同工作
- **功能测试**: 验证完整的用户场景

## 📝 代码规范

### 命名规范
- 类名：PascalCase (`OpenManusAgent`)
- 方法名：camelCase (`executeAgent`)
- 常量：UPPER_SNAKE_CASE (`MAX_RETRY_COUNT`)

### 注释规范
```java
/**
 * 智能代理的主要实现类
 * 
 * @author OpenManus Team
 * @since 1.0.0
 */
public class OpenManusAgent {
    
    /**
     * 执行代理任务
     * 
     * @param input 用户输入
     * @return 执行结果
     */
    public String execute(String input) {
        // 实现代码
    }
}
```

## 🔍 调试技巧

### 启用调试日志
```yaml
# application.yml
logging:
  level:
    com.openmanus.java: DEBUG
```

### 使用断点调试
- 在 IDE 中设置断点
- 使用 `mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005"`

### 监控工具
- Web界面监控: `http://localhost:8080`
- Actuator端点: `http://localhost:8080/actuator/health`
- API文档: `http://localhost:8080/swagger-ui.html`

## 📦 构建和部署

### 构建应用
```bash
# 清理并打包
mvn clean package

# 跳过测试打包
mvn clean package -DskipTests
```

### Docker 构建
```bash
# 构建镜像
docker build -t openmanus-java .

# 运行容器
docker run -p 8080:8080 openmanus-java
```

## 🐛 常见问题

### Q: 如何处理Token限制？
A: 系统已实现自动上下文管理，会在需要时自动重置上下文。

### Q: 如何配置不同的LLM Provider？
A: 在 `application.yml` 中修改 LLM 配置：
```yaml
openmanus:
  llm:
    default-llm:
      api-key: "your-api-key"
      model: "qwen-plus"
      base-url: "https://dashscope.aliyuncs.com/compatible-mode/v1/"
```

### Q: 如何添加新的工具？
A: 创建新的 `@Component` 类，使用 `@Tool` 注解标记方法即可自动注册。

### Q: 如何处理并发请求？
A: 系统支持多会话并发，每个会话有独立的状态管理。

## 📞 获取帮助

- **查看文档**: 参考其他文档文件
- **查看示例**: 查看现有工具的实现
- **调试日志**: 启用DEBUG级别日志
- **社区支持**: 在GitHub Issues中提问

---

🚀 **开始您的OpenManus Java开发之旅！**