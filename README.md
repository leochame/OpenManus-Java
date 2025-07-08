# OpenManus Java

一个基于 **langchain4j** 和 **langgraph4j** 的智能 AI Agent 框架，支持 ReAct 推理和 Chain of Thought。

## 🎯 核心理念

**最大化复用 langchain4j/graph4j，拒绝重复造轮子**

- ✅ **ReAct 推理链路**：使用 langchain4j 的 AI Services
- ✅ **Chain of Thought**：完整的推理过程展示
- ✅ **工具调用**：支持 Python、文件操作、网页浏览等
- ✅ **任务反思**：自动任务反思和总结

## 🚀 快速开始

### 1. 环境要求

- Java 17+
- Maven 3.6+
- OpenAI API Key (或其他支持的LLM)

### 2. 配置

复制环境变量模板：

```bash
cp env.example .env
```

编辑 `.env` 文件，设置你的 API 配置：

```bash
# OpenAI 配置
OPENAI_API_KEY=your_openai_api_key_here
OPENAI_MODEL=gpt-4

# 或者使用其他LLM
QWEN_API_KEY=your_qwen_api_key_here
QWEN_MODEL=qwen-plus
```

### 3. 运行

```bash
# 启动应用
mvn spring-boot:run

# 或者使用脚本
./start.sh
```

### 4. 访问应用

打开浏览器访问：http://localhost:8089

## 🛠️ 核心功能

### 智能对话
- **ReAct 推理**：基于推理和行动的智能对话
- **工具调用**：自动选择合适的工具执行任务
- **推理过程**：完整的 Chain of Thought 展示

### 支持的工具
- **Python 执行**：运行 Python 代码进行计算和数据处理
- **文件操作**：读取、写入、列出文件和目录
- **网页浏览**：访问网页获取实时信息
- **网络搜索**：搜索网络获取最新信息
- **Bash 命令**：执行系统命令
- **人机交互**：在需要时询问用户

### 界面特性
- **响应式设计**：适配不同屏幕尺寸
- **长回答折叠**：智能处理长篇回答
- **推理过程展示**：左右分栏布局
- **苹果风格UI**：现代化的界面设计

## 🏗️ 架构特点

### 简化设计
- **无自定义基类**：直接使用 langchain4j 的官方实现
- **AI Services**：使用 langchain4j 的 AI Services 框架
- **工具统一**：所有工具使用 @Tool 注解

### 核心组件

#### ManusAgent
基于 AI Services 的智能 Agent 实现：

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
    
    public Map<String, Object> chatWithCot(String userMessage) {
        // 执行 ReAct 推理并返回完整过程
    }
}
```

#### 工具系统
所有工具都使用标准的 @Tool 注解：

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

## 📚 项目结构

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

## 🔧 开发指南

### 添加新工具

1. 创建工具类并实现功能
2. 在 ToolProvider 中添加 @Tool 注解方法
3. 在 ManusAgent 中注册工具

```java
@Tool("我的自定义工具")
public String myCustomTool(String parameter) {
    // 实现工具逻辑
    return "执行结果";
}
```

### 自定义 LLM

在 `application.yml` 中配置不同的 LLM：

```yaml
openmanus:
  llm:
    type: openai  # 或 qwen, anthropic 等
    model: gpt-4
    api-key: ${OPENAI_API_KEY}
```

## 📖 文档

- [架构文档](docs/ARCHITECTURE.md) - 系统架构详解
- [部署指南](docs/DEPLOYMENT_GUIDE.md) - 部署和配置说明
- [用户指南](docs/USER_GUIDE.md) - 使用说明
- [开发文档](docs/DEVELOPMENT.md) - 开发指南
- [更新总结](docs/UPDATE_SUMMARY.md) - 最新更新

## 🤝 贡献指南

1. Fork 项目
2. 创建功能分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 打开 Pull Request

## 📄 许可证

本项目采用 MIT 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情。

## 🙏 致谢

- [LangChain4j](https://docs.langchain4j.dev/) - Java 版本的 LangChain
- [LangGraph4j](https://langgraph4j.github.io/) - Java 版本的 LangGraph
- [Spring Boot](https://spring.io/projects/spring-boot) - 应用框架
- [Element Plus](https://element-plus.org/) - Vue.js UI 组件库

---

🎉 **OpenManus Java - 让 AI Agent 开发变得简单而强大！**