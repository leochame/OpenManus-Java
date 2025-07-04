# 开发文档

本文档包含了OpenManus Java项目的详细开发指南。

## 目录

- [架构概述](#架构概述)
- [开发环境搭建](#开发环境搭建)
- [项目结构](#项目结构)
- [核心组件](#核心组件)
- [API文档](#api文档)
- [测试指南](#测试指南)
- [调试技巧](#调试技巧)
- [性能优化](#性能优化)
- [常见问题](#常见问题)

## 架构概述

OpenManus Java是一个基于Spring Boot的智能代理系统，采用了以下架构模式：

### 系统架构

```
┌─────────────────────────────────────────────────────────────┐
│                    OpenManus Java                            │
├─────────────────────────────────────────────────────────────┤
│                   交互层 (Interactive Layer)                 │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐│
│  │   CLI Interface │  │   Web Interface │  │   API Interface ││
│  └─────────────────┘  └─────────────────┘  └─────────────────┘│
├─────────────────────────────────────────────────────────────┤
│                   智能体层 (Agent Layer)                     │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐│
│  │   ManusAgent    │  │  PlanningAgent  │  │  ReActAgent     ││
│  └─────────────────┘  └─────────────────┘  └─────────────────┘│
├─────────────────────────────────────────────────────────────┤
│                   服务层 (Service Layer)                     │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐│
│  │   LLM Client    │  │   Tool Registry │  │   Memory        ││
│  └─────────────────┘  └─────────────────┘  └─────────────────┘│
├─────────────────────────────────────────────────────────────┤
│                   工具层 (Tool Layer)                        │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐│
│  │   Python Tool   │  │   File Tool     │  │   Bash Tool     ││
│  └─────────────────┘  └─────────────────┘  └─────────────────┘│
├─────────────────────────────────────────────────────────────┤
│                   基础设施层 (Infrastructure Layer)           │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐│
│  │   Docker        │  │   Configuration │  │   Logging       ││
│  └─────────────────┘  └─────────────────┘  └─────────────────┘│
└─────────────────────────────────────────────────────────────┘
```

### 核心设计原则

1. **模块化**: 每个组件都是独立的模块，便于测试和维护
2. **可扩展**: 工具系统支持动态添加新工具
3. **安全性**: 所有代码执行都在Docker沙箱中进行
4. **高性能**: 使用异步处理和连接池优化性能
5. **易用性**: 提供简单的CLI和Web界面

## 开发环境搭建

### 必要工具

1. **Java 21**: 项目使用Java 21的最新特性
2. **Maven 3.9+**: 依赖管理和构建工具
3. **Docker**: 沙箱环境和容器化
4. **IDE**: 推荐IntelliJ IDEA或Eclipse

### 环境配置

```bash
# 1. 克隆项目
git clone https://github.com/OpenManus/OpenManus-Java.git
cd OpenManus-Java

# 2. 配置环境变量
cp env.example .env
# 编辑 .env 文件，填入必要的配置

# 3. 构建项目
mvn clean compile

# 4. 运行测试
mvn test

# 5. 启动项目
./run_interactive.sh
```

### IDE配置

#### IntelliJ IDEA

1. **导入项目**
   - File → Open → 选择项目目录
   - 选择"Maven项目"

2. **配置JDK**
   - File → Project Structure → Project → SDK选择Java 21

3. **配置代码风格**
   - File → Settings → Code Style → Java
   - 导入 Google Java Style

4. **配置运行配置**
   - Run → Edit Configurations
   - 添加Application配置
   - Main class: `com.openmanus.java.OpenManusApplication`

## 项目结构

```
src/
├── main/
│   ├── java/com/openmanus/java/
│   │   ├── agent/              # 智能体实现
│   │   │   ├── BaseAgent.java
│   │   │   ├── ManusAgent.java
│   │   │   ├── PlanningAgent.java
│   │   │   └── ReActAgent.java
│   │   ├── config/             # 配置管理
│   │   │   ├── ConfigurationConfig.java
│   │   │   ├── LlmClientConfig.java
│   │   │   └── OpenManusProperties.java
│   │   ├── exception/          # 异常类
│   │   │   ├── OpenManusException.java
│   │   │   └── TokenLimitExceededException.java
│   │   ├── flow/               # 流程控制
│   │   │   ├── Plan.java
│   │   │   ├── PlanningFlow.java
│   │   │   └── PlanStep.java
│   │   ├── llm/                # LLM客户端
│   │   │   └── LlmClient.java
│   │   ├── model/              # 数据模型
│   │   │   ├── Message.java
│   │   │   ├── ToolCall.java
│   │   │   └── Function.java
│   │   ├── sandbox/            # 沙箱环境
│   │   │   └── SandboxClient.java
│   │   ├── tool/               # 工具集合
│   │   │   ├── BashTool.java
│   │   │   ├── FileTool.java
│   │   │   ├── PythonTool.java
│   │   │   └── ToolRegistry.java
│   │   ├── InteractiveRunner.java
│   │   ├── Main.java
│   │   └── OpenManusApplication.java
│   └── resources/
│       └── application.yml
└── test/                       # 测试代码
    ├── java/com/openmanus/java/
    │   ├── agent/
    │   ├── config/
    │   ├── integration/
    │   └── tool/
    └── resources/
        └── application-test.yml
```

## 核心组件

### 1. 智能体系统 (Agent System)

#### BaseAgent

所有智能体的基类，提供基本的思考和记忆功能。

```java
public abstract class BaseAgent {
    protected Memory memory;
    protected LlmClient llm;
    protected AgentState state;
    
    public abstract CompletableFuture<Boolean> think();
    public abstract void act();
}
```

#### ManusAgent

主要的智能体实现，支持工具调用和复杂推理。

```java
public class ManusAgent extends ToolCallAgent {
    private final ToolRegistry toolRegistry;
    private final SandboxClient sandboxClient;
    
    @Override
    public CompletableFuture<Boolean> think() {
        // 复杂的推理逻辑
    }
}
```

### 2. 工具系统 (Tool System)

#### 工具接口

```java
public interface Tool {
    String getName();
    String getDescription();
    String execute(String input);
    Function getFunction();
}
```

#### 工具注册

```java
@Component
public class ToolRegistry {
    private final Map<String, Tool> tools = new HashMap<>();
    
    public void registerTool(Tool tool) {
        tools.put(tool.getName(), tool);
    }
    
    public Tool getTool(String name) {
        return tools.get(name);
    }
}
```

### 3. LLM客户端 (LLM Client)

#### 基本用法

```java
@Service
public class LlmClient {
    private final ChatLanguageModel chatModel;
    
    public Message sendMessage(List<Message> messages) {
        // 发送消息到LLM并返回响应
    }
    
    public Message sendMessageWithTools(List<Message> messages, List<Tool> tools) {
        // 支持工具调用的消息发送
    }
}
```

### 4. 沙箱系统 (Sandbox System)

#### Docker沙箱

```java
@Component
public class SandboxClient {
    private final DockerClient dockerClient;
    
    public ExecutionResult executeCode(String code, String language) {
        // 在Docker容器中执行代码
    }
    
    public ExecutionResult executeCommand(String command) {
        // 在Docker容器中执行命令
    }
}
```

## API文档

### 消息格式

#### 请求消息

```json
{
  "role": "user",
  "content": "请帮我分析这个CSV文件",
  "attachments": [
    {
      "type": "file",
      "path": "/path/to/file.csv"
    }
  ]
}
```

#### 响应消息

```json
{
  "role": "assistant",
  "content": "我来帮您分析CSV文件...",
  "toolCalls": [
    {
      "id": "call_123",
      "function": {
        "name": "python",
        "arguments": "{\"code\": \"import pandas as pd; df = pd.read_csv('file.csv'); print(df.head())\"}"
      }
    }
  ]
}
```

### 工具调用格式

#### Python工具

```json
{
  "name": "python",
  "description": "Execute Python code in a sandbox",
  "parameters": {
    "type": "object",
    "properties": {
      "code": {
        "type": "string",
        "description": "Python code to execute"
      }
    },
    "required": ["code"]
  }
}
```

#### 文件工具

```json
{
  "name": "str_replace_editor",
  "description": "Edit files using string replacement",
  "parameters": {
    "type": "object",
    "properties": {
      "command": {
        "type": "string",
        "enum": ["str_replace", "view", "create"]
      },
      "path": {
        "type": "string",
        "description": "File path"
      },
      "old_str": {
        "type": "string",
        "description": "String to replace"
      },
      "new_str": {
        "type": "string",
        "description": "Replacement string"
      }
    },
    "required": ["command", "path"]
  }
}
```

## 测试指南

### 单元测试

```java
@SpringBootTest
class LlmClientTest {
    
    @Autowired
    private LlmClient llmClient;
    
    @Test
    void shouldSendMessageSuccessfully() {
        // Arrange
        List<Message> messages = List.of(
            Message.userMessage("Hello")
        );
        
        // Act
        Message response = llmClient.sendMessage(messages);
        
        // Assert
        assertThat(response.getContent()).isNotEmpty();
    }
}
```

### 集成测试

```java
@SpringBootTest
@TestPropertySource(properties = {
    "spring.profiles.active=test"
})
class EndToEndIntegrationTest {
    
    @Autowired
    private ManusAgent agent;
    
    @Test
    void shouldProcessUserRequestEndToEnd() {
        // 端到端测试
    }
}
```

### 运行测试

```bash
# 运行所有测试
mvn test

# 运行特定测试类
mvn test -Dtest=LlmClientTest

# 运行集成测试
mvn verify

# 生成测试报告
mvn jacoco:report
```

## 调试技巧

### 1. 日志配置

```yaml
logging:
  level:
    com.openmanus.java: DEBUG
    org.springframework: INFO
    dev.langchain4j: DEBUG
```

### 2. 远程调试

```bash
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 -jar openmanus.jar
```

### 3. 性能分析

```bash
# 使用JProfiler
java -agentpath:/path/to/jprofiler/bin/agent.jar -jar openmanus.jar

# 使用JVisualVM
jvisualvm --jdkhome $JAVA_HOME
```

## 性能优化

### 1. JVM调优

```bash
java -Xms512m -Xmx2g \
     -XX:+UseG1GC \
     -XX:+UseStringDeduplication \
     -XX:+PrintGCDetails \
     -jar openmanus.jar
```

### 2. 连接池优化

```yaml
openmanus:
  llm:
    connection-pool:
      max-connections: 10
      connection-timeout: 30s
      read-timeout: 60s
```

### 3. 异步处理

```java
@Async
public CompletableFuture<String> processAsync(String input) {
    // 异步处理逻辑
}
```

## 常见问题

### Q: 如何添加新工具？

A: 实现Tool接口并在ToolRegistry中注册：

```java
@Component
public class CustomTool implements Tool {
    @Override
    public String getName() {
        return "custom_tool";
    }
    
    @Override
    public String getDescription() {
        return "Custom tool description";
    }
    
    @Override
    public String execute(String input) {
        // 工具逻辑
        return "result";
    }
}
```

### Q: 如何处理Token限制？

A: 系统已经实现了自动上下文管理：

```java
// 检查是否需要重置上下文
if (shouldResetContext()) {
    resetContext();
}
```

### Q: 如何配置不同的LLM Provider？

A: 在application.yml中配置：

```yaml
openmanus:
  llm:
    provider: openai  # 或其他provider
    api-key: ${API_KEY}
    model: gpt-4
```

### Q: 如何处理Docker权限问题？

A: 确保Docker daemon正在运行，并且用户有权限：

```bash
# 添加用户到docker组
sudo usermod -aG docker $USER

# 重启Docker服务
sudo systemctl restart docker
```

## 贡献指南

请阅读 [CONTRIBUTING.md](../CONTRIBUTING.md) 了解如何贡献代码。

## 许可证

本项目使用 MIT 许可证。详见 [LICENSE](../LICENSE) 文件。 