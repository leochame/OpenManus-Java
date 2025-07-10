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
- **LangGraph4j 1.6.0-beta5** - 状态图工作流引擎，提供强大的Agent状态管理和工作流编排能力
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

### StateGraph架构

OpenManus Java现在使用LangGraph4j的StateGraph架构来管理Agent的状态和工作流：

```
用户输入 → StateGraph节点处理 → 状态更新 → 条件路由 → 工具执行 → 状态合并 → 最终输出
```

#### 核心组件

1. **StateGraph**: 定义Agent的状态转换图
2. **AgentState**: 管理对话状态、消息历史和工具调用结果
3. **节点处理器**: 处理不同类型的任务（思考、工具调用、响应生成）
4. **条件路由**: 根据状态动态决定下一个执行节点
5. **状态合并**: 将工具执行结果合并到Agent状态中

### ReAct 推理流程

基于StateGraph的ReAct推理流程：

```
用户输入 → 思考节点 → 行动节点 → 观察节点 → 条件判断 → 循环或结束
```

1. **思考节点**: Agent分析当前状态，决定下一步行动
2. **行动节点**: 执行具体的工具调用或生成回答
3. **观察节点**: 分析工具执行结果并更新状态
4. **条件判断**: 根据状态决定是否继续循环或结束

### 工具调用流程
1. 用户输入 → ManusAgent
2. StateGraph 分析状态，决定工具调用
3. 执行相应工具 (Python、文件、网页等)
4. 收集工具结果，更新状态，继续推理或给出答案
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
基于 LangGraph4j StateGraph 的智能 Agent：

```java
@Component
public class ManusAgent {
    private final StateGraph<AgentState> stateGraph;
    
    public ManusAgent(ChatModel chatModel, List<Tool> tools) {
        this.stateGraph = StateGraph.builder(AgentState.class)
            .addNode("think", this::thinkNode)
            .addNode("act", this::actNode)
            .addNode("observe", this::observeNode)
            .addConditionalEdges("think", this::shouldContinue)
            .setEntryPoint("think")
            .build();
    }
}
```

#### StateGraph工作流

- **节点定义**: 定义思考、工具调用、响应等处理节点
- **状态转换**: 管理Agent在不同状态间的转换
- **条件路由**: 根据执行结果动态选择下一个节点
- **并行处理**: 支持多个工具的并行执行
- **错误处理**: 内置错误恢复和重试机制

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