# OpenManus Java 架构文档

## 🌐 系统概览

OpenManus Java 是一个基于 Spring Boot 和 LangChain4j 的智能代理系统，采用 ReAct 推理框架和 Chain of Thought 展示。

### 核心特性

- **ReAct 推理**: 基于推理和行动的智能对话
- **Chain of Thought**: 完整的推理过程展示
- **工具调用**: 支持 Python、文件操作、网页浏览等
- **任务反思**: 自动任务反思和总结
- **Web 界面**: 现代化的苹果风格 UI

## 🛠️ 技术栈

### 后端
- **Java 17+** - 运行环境
- **Spring Boot 3.2.0** - 应用框架
- **LangChain4j** - AI 服务和工具框架
- **LangGraph4j** - 状态图框架 (可选)
- **Docker** - 沙箱环境

### 前端  
- **Vue.js 3** - 前端框架
- **Element Plus** - UI组件库
- **WebSocket** - 实时通信

## 🏗️ 系统架构

```
┌─────────────────────────────────────────────────────────┐
│                     客户端层                            │
│  Web界面 (Vue.js + Element Plus)  |  命令行 (CLI)     │
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│                     Web服务层                          │
│  REST API  |  WebSocket Handler  |  静态资源服务      │
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│                    Agent层                            │
│  ManusAgent (基于 AI Services)                        │
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│                     工具层                              │
│  PythonTool  |  FileTool  |  BrowserTool  |  其他工具  │
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│                    LLM层                              │
│  OpenAI  |  Qwen  |  Anthropic  |  其他LLM            │
└─────────────────────────────────────────────────────────┘
```

## 🤖 Agent 工作流

### ReAct 推理流程
1. **思考 (Thought)**: AI 分析用户需求，制定计划
2. **行动 (Action)**: 选择合适的工具执行任务
3. **观察 (Observation)**: 观察工具执行结果
4. **答案 (Answer)**: 基于观察结果给出最终答案

### 工具调用流程
1. 用户输入 → ManusAgent
2. AI Services 分析意图，决定工具调用
3. 执行相应工具 (Python、文件、网页等)
4. 收集工具结果，继续推理或给出答案
5. 返回完整的推理过程

## 🌐 Web 功能

### API 端点
- `GET /api/v1/agent/health` - 健康检查
- `POST /api/v1/agent/chat` - 聊天接口
- `WebSocket /ws/agent` - 实时通信

### 前端功能
- **聊天界面** - 实时对话交互
- **推理过程展示** - 左右分栏布局
- **长回答折叠** - 智能处理长篇回答
- **响应式设计** - 适配不同屏幕尺寸

## 🔧 核心组件

### ManusAgent
基于 LangChain4j AI Services 的智能 Agent：

```java
@Component
public class ManusAgent {
    private final ReactAgent reactAgent;
    
    public ManusAgent(ChatModel chatModel) {
        this.reactAgent = AiServices.builder(ReactAgent.class)
            .chatModel(chatModel)
            .tools(new ToolProvider(pythonTool, fileTool, browserTool))
            .build();
    }
}
```

### 工具系统
所有工具使用标准的 @Tool 注解：

```java
public static class ToolProvider {
    @Tool("Execute Python code for calculations")
    public String executePython(String code) {
        return pythonTool.executePython(code);
    }
    
    @Tool("List files and directories")
    public String listDirectory(String path) {
        return fileTool.listDirectory(path);
    }
}
```

### 支持的工具
- **PythonTool**: 执行 Python 代码
- **FileTool**: 文件操作 (读取、写入、列表)
- **BrowserTool**: 网页浏览
- **WebSearchTool**: 网络搜索
- **BashTool**: Bash 命令执行
- **AskHumanTool**: 人机交互
- **TerminateTool**: 任务终止
- **ReflectionTool**: 任务反思

## 🚀 启动模式

### Web模式 (默认)
```bash
mvn spring-boot:run
```
启动Web服务器，提供Web界面和API服务。

### 命令行模式
```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--cli
```
启动传统的命令行交互界面。

## 📁 项目结构

```
src/main/java/com/openmanus/java/
├── agent/
│   └── ManusAgent.java          # 智能 Agent 实现
├── tool/
│   ├── PythonTool.java          # Python 执行工具
│   ├── FileTool.java            # 文件操作工具
│   ├── BrowserTool.java         # 网页访问工具
│   ├── WebSearchTool.java       # 网络搜索工具
│   ├── BashTool.java            # Bash 命令工具
│   ├── AskHumanTool.java        # 人机交互工具
│   ├── TerminateTool.java       # 任务终止工具
│   └── ReflectionTool.java      # 任务反思工具
├── config/
│   ├── LlmConfig.java           # LLM 配置
│   └── VectorDatabaseConfig.java # 向量数据库配置
├── controller/
│   └── AgentController.java     # REST API 控制器
├── llm/
│   └── LlmClient.java           # LLM 客户端
├── memory/
│   ├── ConversationBuffer.java  # 对话缓冲
│   └── MemoryTool.java          # 内存工具
├── model/
│   ├── Memory.java              # 内存模型
│   ├── CLIResult.java           # 命令行结果
│   ├── Message.java             # 消息模型
│   └── Role.java                # 角色枚举
└── WebApplication.java          # Spring Boot 启动类
```

## 🔒 安全设计

- **沙箱隔离**: Docker容器隔离代码执行
- **输入验证**: 严格的参数校验
- **资源限制**: 防止资源滥用
- **工具权限**: 细粒度的工具访问控制

## 📊 监控指标

- **Agent状态**: 实时推理过程展示
- **工具调用**: 工具执行状态和耗时
- **系统健康**: Spring Boot Actuator监控
- **WebSocket连接**: 连接状态和消息统计

## 🎯 设计原则

### 简化设计
- **无自定义基类**: 直接使用 LangChain4j 的官方实现
- **AI Services**: 使用 LangChain4j 的 AI Services 框架
- **工具统一**: 所有工具使用 @Tool 注解

### 易扩展性
- **工具即插即用**: 新增工具只需实现 @Tool 注解
- **LLM 支持**: 支持多种 LLM 提供商
- **配置灵活**: 通过配置文件轻松切换组件

### 标准化
- **完全对齐官方生态**: 使用 LangChain4j 的标准模式
- **持续享受生态升级**: 官方新功能自动可用
- **社区兼容性**: 与 LangChain4j 生态完全兼容 