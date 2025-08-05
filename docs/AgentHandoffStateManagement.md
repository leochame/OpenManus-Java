

---

# 🚀 SearchAgent智能RAG升级方案

## 📋 升级概述

本次升级将SearchAgent从简单的网页搜索工具升级为具备自适应RAG（检索增强生成）能力的智能信息检索专家。升级采用**"最小改动，最大保留"**的策略，完整保留AdaptiveRag的StateGraph核心逻辑，同时集成到Spring架构中。

## 🎯 升级目标与架构变更

### 升级前架构
```
SearchAgent → BrowserTool (已失效)
```

### 升级后架构
```
SearchAgent → AdaptiveRAGTool → AdaptiveRagService → {
    StateGraph流程控制 {
        ├── 智能路由 (QuestionRouter)
        ├── 向量检索 (ChromaStore + RetrievalGrader)  
        ├── 本地搜索 (LocalSearchService替代Tavily)
        ├── 质量控制 (HallucinationGrader + AnswerGrader)
        ├── 内容生成 (Generation)
        └── 问题重写 (QuestionRewriter)
    }
}
```

## 💡 **核心设计理念：最小改动，最大保留**

**原则**：
1. **保留StateGraph核心** - 完整保留`buildGraph()`方法的复杂状态图逻辑
2. **Spring化改造** - 仅添加Spring注解和依赖注入，不改变核心逻辑
3. **本地化替代** - 用LocalSearchService替代Tavily，移除外部API依赖
4. **模型适配** - 注入Qwen模型替代硬编码OpenAI模型创建

## 📂 详细文件升级方案

### **1. SearchAgent.java 核心升级**
**文件路径**: `src/main/java/com/openmanus/agent/impl/executor/SearchAgent.java`

**升级策略**: 仅替换工具集成，保持原有架构

```java
public static class Builder extends AbstractAgentExecutor.Builder<Builder> {
    // 删除：private BrowserTool browserTool;
    // 新增：
    private AdaptiveRAGTool adaptiveRAGTool;
    
    public Builder adaptiveRAGTool(AdaptiveRAGTool tool) {
        this.adaptiveRAGTool = tool;
        return this;
    }
    
    public SearchAgent build() throws GraphStateException {
        this.name("search_agent")
            .description("智能搜索代理，支持自适应RAG检索")
            .systemMessage(SystemMessage.from("""
                你是升级版的智能信息检索专家，拥有先进的自适应RAG检索能力：
                
                🛠️ 可用工具：
                - intelligentSearch(query): 🧠 自适应智能搜索，自动选择最佳检索策略
                
                🚀 智能检索策略：
                1. 系统自动进行问题路由和质量控制
                2. 支持向量检索和本地搜索的智能切换
                3. 所有答案经过幻觉检测和相关性验证
                4. 支持问题重写和迭代优化
                """));
        
        // 集成RAG工具
        if (adaptiveRAGTool != null) {
            this.toolFromObject(adaptiveRAGTool);
        }
        
        return new SearchAgent(this);
    }
}
```

### **2. AdaptiveRAGTool.java 工具封装层**
**文件路径**: `src/main/java/com/openmanus/agent/tool/search/AdaptiveRAGTool.java`

**设计目的**: 将AdaptiveRag能力封装为LangChain4j工具

```java
@Component
public class AdaptiveRAGTool {
    
    private final AdaptiveRagService ragService;
    
    public AdaptiveRAGTool(AdaptiveRagService ragService) {
        this.ragService = ragService;
    }
    
    @Tool("智能自适应RAG检索 - 包含完整的状态图流程控制和质量保证")
    public String intelligentSearch(@P("搜索查询或问题") String query) {
        return ragService.processQuery(query);
    }
}
```

### **3. AdaptiveRagService.java Spring化核心**
**文件路径**: `src/main/java/com/openmanus/agent/tool/rag/AdaptiveRagService.java`

**改造策略**: **90%保持原有逻辑不变**，仅进行Spring化改造

### **4. QuestionRouter.java适配升级**
**文件路径**: `src/main/java/com/openmanus/agent/tool/rag/service/QuestionRouterService.java`

**源文件**: `src/test/java/com/openmanus/java/adaptiverag/QuestionRouter.java`

**升级类型**: 移植改造 - 保持核心逻辑，适配新架构

**主要变更**:
```java
// 原文件结构
public record QuestionRouter(String openApiKey) implements Function<String, QuestionRouter.Type>

// 升级后结构
@Service
public class QuestionRouterService implements Function<String, RouteType> {
    
    private final ChatModel chatModel; // 注入Qwen模型
    
    // 路由类型调整
    public enum RouteType {
        vectorstore,    // 向量存储搜索
        local_search    // 本地搜索（替代web_search）
    }
    
    // 中文化系统提示
    @SystemMessage("""
        你是一个专业的问题路由专家，负责将用户问题路由到最合适的数据源。
        
        路由规则：
        1. 向量存储(vectorstore) - 适用于：
           - 专业技术概念和知识
           - 代理(Agent)相关问题  
           - 编程和开发问题
           - 已知领域的深度问题
           
        2. 本地搜索(local_search) - 适用于：
           - 时效性信息需求
           - 广泛的常识性问题
           - 新闻和实时信息
           - 需要多样化信息源的查询
        """)
}
```

### **5. ChromaStore.java服务化升级**
**文件路径**: `src/main/java/com/openmanus/agent/tool/rag/store/VectorStoreService.java`

**源文件**: `src/test/java/com/openmanus/java/adaptiverag/ChromaStore.java`

**升级类型**: 服务化改造 - 从静态工厂模式升级为Spring服务

**主要变更**:
```java
// 原文件结构
public final class ChromaStore {
    public static ChromaStore of(String openApiKey) {
        return new ChromaStore(openApiKey);
    }
    
    private ChromaStore(String openApiKey) {
        this.embeddingModel = OpenAiEmbeddingModel.builder()
                .apiKey(openApiKey)
                .build();
    }
}

// 升级后结构
@Service
public class VectorStoreService {
    
    private final ChromaEmbeddingStore chroma;
    private final EmbeddingModel embeddingModel;
    
    public VectorStoreService(EmbeddingModel embeddingModel, RAGProperties ragProperties) {
        this.embeddingModel = embeddingModel;
        this.chroma = new ChromaEmbeddingStore(
                ragProperties.getVectorStore().getUrl(),
                ragProperties.getVectorStore().getCollection(),
                Duration.ofMinutes(2),
                true,
                true);
    }
    
    public EmbeddingSearchResult<TextSegment> search(String query) {
        // 保持原有搜索逻辑，增加配置化参数
    }
}
```

### **6. WebSearchTool.java替换升级**
**文件路径**: `src/main/java/com/openmanus/agent/tool/rag/search/LocalSearchService.java`

**源文件**: `src/test/java/com/openmanus/java/adaptiverag/WebSearchTool.java`

**升级类型**: 替换升级 - 移除Tavily依赖，实现本地搜索

**主要变更**:
```java
// 原文件依赖Tavily
public record WebSearchTool(String tavilyApiKey) implements Function<String, List<Content>> {
    @Override
    public List<Content> apply(String query) {
        WebSearchEngine webSearchEngine = TavilyWebSearchEngine.builder()
                .apiKey(tavilyApiKey)
                .build();
    }
}

// 升级后实现
@Service  
public class LocalSearchService implements Function<String, List<Content>> {
    
    private final RAGProperties ragProperties;
    
    @Override
    public List<Content> apply(String query) {
        String engine = ragProperties.getLocalSearch().getEngine();
        
        switch (engine) {
            case "duckduckgo":
                return searchWithDuckDuckGo(query);
            case "bing":
                return searchWithBing(query);
            case "custom":
                return searchWithCustomEngine(query);
            default:
                return searchWithDuckDuckGo(query);
        }
    }
    
    // 实现不同搜索引擎的本地化方案
}
```

### **7. 评估组件服务化升级**

#### A. RetrievalGrader.java → RetrievalGraderService.java
**文件路径**: `src/main/java/com/openmanus/agent/tool/rag/service/RetrievalGraderService.java`

#### B. HallucinationGrader.java → HallucinationGraderService.java  
**文件路径**: `src/main/java/com/openmanus/agent/tool/rag/service/HallucinationGraderService.java`

#### C. AnswerGrader.java → AnswerGraderService.java
**文件路径**: `src/main/java/com/openmanus/agent/tool/rag/service/AnswerGraderService.java`

#### D. QuestionRewriter.java → QuestionRewriterService.java
**文件路径**: `src/main/java/com/openmanus/agent/tool/rag/service/QuestionRewriterService.java`

#### E. Generation.java → ContentGeneratorService.java
**文件路径**: `src/main/java/com/openmanus/agent/tool/rag/service/ContentGeneratorService.java`

**统一升级模式**:
```java
// 原结构 (以RetrievalGrader为例)
public record RetrievalGrader(String openApiKey) implements Function<Arguments, Score> {
    public Score apply(Arguments args) {
        ChatModel chatLanguageModel = OpenAiChatModel.builder()
                .apiKey(openApiKey)
                .modelName("gpt-3.5-turbo-0125")
                .build();
    }
}

// 升级后结构
@Service
public class RetrievalGraderService implements Function<GradeRequest, GradeScore> {
    
    private final ChatModel chatModel; // 注入Qwen模型
    
    public RetrievalGraderService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }
    
    @Override
    public GradeScore apply(GradeRequest request) {
        GraderService service = AiServices.create(GraderService.class, chatModel);
        // 保持原有逻辑，替换模型创建方式
    }
    
    // 中文化系统提示
    interface GraderService {
        @SystemMessage("""
            你是一个专业的文档相关性评估专家。你的任务是评估检索到的文档是否与用户问题相关。
            
            评估标准：
            1. 如果文档包含与用户问题相关的关键词或语义内容，标记为相关
            2. 不需要进行严格的匹配测试，目标是过滤明显无关的检索结果
            3. 即使文档只部分回答了问题，也应该标记为相关
            
            请给出二进制评分：'yes' 表示相关，'no' 表示不相关。
            """)
        GradeScore invoke(String promptText);
    }
}
```

### **8. 配置管理升级**

#### A. 新增RAGConfiguration.java
**文件路径**: `src/main/java/com/openmanus/infra/config/RAGConfiguration.java`

**文件类型**: 新增配置类

**核心内容**:
```java
@Configuration
@EnableConfigurationProperties(RAGProperties.class)
public class RAGConfiguration {
    
    @Bean
    @ConditionalOnProperty(name = "openmanus.rag.enabled", havingValue = "true", matchIfMissing = true)
    public AdaptiveRAGEngine adaptiveRAGEngine(
            ChatModel chatModel,
            QuestionRouterService questionRouter,
            VectorStoreService vectorStore,
            LocalSearchService localSearch,
            RetrievalGraderService retrievalGrader,
            HallucinationGraderService hallucinationGrader,
            AnswerGraderService answerGrader,
            QuestionRewriterService questionRewriter,
            ContentGeneratorService contentGenerator) {
        return new AdaptiveRAGEngine(chatModel, questionRouter, vectorStore, 
                localSearch, retrievalGrader, hallucinationGrader, 
                answerGrader, questionRewriter, contentGenerator);
    }
    
    @Bean
    public AdaptiveRAGTool adaptiveRAGTool(AdaptiveRAGEngine ragEngine) {
        return new AdaptiveRAGTool(ragEngine);
    }
    
    // 各种服务Bean的配置
}
```

#### B. 新增RAGProperties.java
**文件路径**: `src/main/java/com/openmanus/infra/config/RAGProperties.java`

#### C. LangChain4jConfig.java升级
**文件路径**: `src/main/java/com/openmanus/infra/config/LangChain4jConfig.java`

**升级内容**: 新增EmbeddingModel Bean配置

```java
// 新增方法
@Bean
public EmbeddingModel embeddingModel() {
    OpenManusProperties.LlmConfig.DefaultLLM llmConfig = openManusProperties.getLlm().getDefaultLlm();
    return OpenAiEmbeddingModel.builder()
            .baseUrl(llmConfig.getBaseUrl())
            .apiKey(llmConfig.getApiKey())
            .modelName("text-embedding-v1") // Qwen embedding模型
            .logRequests(true)
            .logResponses(true)
            .build();
}
```

### **9. 应用配置文件升级**

#### application.yml升级
**文件路径**: `src/main/resources/application.yml`

**升级内容**: 新增RAG相关配置

```yaml
# 在现有配置基础上新增
openmanus:
  # 现有配置保持不变
  
  # 新增RAG配置
  rag:
    enabled: true
    vector-store:
      type: "chroma"
      url: "http://localhost:8000"
      collection: "rag-collection"
      max-results: 5
      min-score: 0.7
    local-search:
      enabled: true
      engine: "duckduckgo"  # 可选: duckduckgo, bing, custom
      max-results: 3
      timeout: 30
    quality-control:
      hallucination-detection: true
      relevance-grading: true
      answer-grading: true
      max-retries: 2
    model:
      temperature: 0.0
      max-tokens: 2000
      timeout: 120
```

### **10. ThinkDoReflectWorkflow.java集成升级**
**文件路径**: `src/main/java/com/openmanus/agent/workflow/ThinkDoReflectWorkflow.java`

**升级类型**: 依赖注入升级

**主要变更**:
```java
// 原构造函数
public ThinkDoReflectWorkflow(
        ChatModel chatModel,
        ThinkingAgent thinkingAgent,
        SearchAgent searchAgent,  // SearchAgent保持不变
        CodeAgent codeAgent,
        FileAgent fileAgent,
        ReflectionAgent reflectionAgent) throws GraphStateException

// 升级后：保持构造函数不变，但SearchAgent内部已集成RAG能力
// 系统消息更新，强调SearchAgent的新能力
.systemMessage(dev.langchain4j.data.message.SystemMessage.from("""
    你是一个智能的任务执行系统，遵循"Think-Do-Reflect"工作流程：

    🧠 THINK阶段：对于新任务，首先使用thinking_agent进行任务分析和规划
    🔧 DO阶段：根据规划使用适当的执行工具：
       - search_agent：智能信息检索，支持自适应RAG、向量检索、本地搜索
       - code_agent：编写代码、执行计算、数据分析
       - file_agent：文件读写、目录操作
    🤔 REFLECT阶段：执行完成后使用reflection_agent评估结果
    """))
```

## 📊 升级影响评估

### **保持不变的组件**:
- AgentHandoff架构
- ThinkDoReflectWorkflow主体结构  
- 其他Agent (ThinkingAgent, CodeAgent, FileAgent, ReflectionAgent)
- 现有配置文件的基础结构

### **新增的组件**:
- AdaptiveRAGTool (工具封装层)
- AdaptiveRAGEngine (引擎协调层)
- 9个RAG服务组件 (QuestionRouterService等)
- RAG配置管理组件
- 本地搜索实现

### **升级的组件**:
- SearchAgent (集成RAG工具，移除BrowserTool)
- LangChain4jConfig (新增EmbeddingModel)
- application.yml (新增RAG配置)

### **删除的组件**:
- BrowserTool相关代码和依赖

## 🎯 升级优势

### **功能提升**:
1. **智能路由**: 自动选择最佳检索策略 
2. **质量保证**: 多层质量控制机制
3. **本地化**: 摆脱外部API依赖
4. **模型适配**: 充分利用Qwen模型能力

### **架构优势**:
1. **向后兼容**: 保持现有AgentHandoff架构
2. **模块化**: 组件解耦，易于维护
3. **可配置**: 灵活的配置管理
4. **可扩展**: 支持新搜索引擎集成

这次升级将SearchAgent从简单的搜索工具升级为具备先进RAG能力的智能信息检索专家，同时完美融入现有的Think-Do-Reflect多智能体协作框架。

---

# 🔧 StateGraph保留与简化Spring化方案

## 🚨 **重要架构修正**

### **问题识别**
在之前的设计中，我们犯了一个重大错误：**丢失了AdaptiveRag的核心价值** - `buildGraph()`方法建立的复杂状态图！

### **AdaptiveRag的真正价值**
```java
// AdaptiveRag的核心 - 复杂的状态图流程控制
public StateGraph<State> buildGraph() throws Exception {
    return new StateGraph<>(State::new)
        .addNode("web_search", node_async(this::webSearch))
        .addNode("retrieve", node_async(this::retrieve))  
        .addNode("grade_documents", node_async(this::gradeDocuments))
        .addNode("generate", node_async(this::generate))
        .addNode("transform_query", node_async(this::transformQuery))
        
        // 复杂的条件边和自适应循环控制
        .addConditionalEdges(START, edge_async(this::routeQuestion), ...)
        .addConditionalEdges("grade_documents", edge_async(this::decideToGenerate), ...)
        .addConditionalEdges("generate", edge_async(this::gradeGeneration_v_documentsAndQuestion), ...)
}
```

## 🎯 **最简化的Spring化改造方案**

### **核心理念**：**最小改动，最大保留**

不需要大幅重构，只需要：
1. **加Spring注解** (@Service, @Component, @Configuration)
2. **依赖注入替代硬编码** (ChatModel注入替代OpenAI创建)
3. **配置外部化** (@ConfigurationProperties)
4. **本地搜索替代Tavily** (LocalSearchService替代WebSearchTool)

### **具体改造方案**

#### **1. AdaptiveRag.java → AdaptiveRagService.java**
**文件路径**: `src/main/java/com/openmanus/agent/tool/rag/AdaptiveRagService.java`

**改造策略**: **最小化修改，保持核心逻辑**

```java
// 原文件结构保持90%不变，只修改依赖注入部分
@Service
public class AdaptiveRagService {
    
    private final ChatModel chatModel;           // 注入Qwen模型
    private final EmbeddingModel embeddingModel; // 注入embedding模型
    private final LocalSearchService localSearchService; // 替代Tavily
    private final ChromaStore chroma;
    
    private final CompiledGraph<State> ragGraph; // 保持不变！
    
    // Spring构造函数 - 替代原有的API Key构造
    public AdaptiveRagService(
            ChatModel chatModel,
            EmbeddingModel embeddingModel, 
            LocalSearchService localSearchService,
            ChromaStore chroma) {
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
        this.localSearchService = localSearchService;
        this.chroma = chroma;
        
        // 关键：保持原有的buildGraph逻辑！
        try {
            this.ragGraph = buildGraph().compile();
        } catch (Exception e) {
            throw new RuntimeException("Failed to build RAG graph", e);
        }
    }
    
    // 🔥 核心：完全保持原有的buildGraph方法！
    public StateGraph<State> buildGraph() throws Exception {
        return new StateGraph<>(State::new)
            .addNode("local_search", node_async(this::localSearch))     // 替代web_search
            .addNode("retrieve", node_async(this::retrieve))
            .addNode("grade_documents", node_async(this::gradeDocuments))
            .addNode("generate", node_async(this::generate))
            .addNode("transform_query", node_async(this::transformQuery))
            
            // 🔥 完全保持原有的条件边逻辑！
            .addConditionalEdges(START,
                    edge_async(this::routeQuestion),
                    Map.of(
                        "local_search", "local_search",    // 只需改这一行
                        "vectorstore", "retrieve"
                    ))
            .addEdge("local_search", "generate")          // 只需改这一行
            .addEdge("retrieve", "grade_documents")
            .addConditionalEdges("grade_documents",
                    edge_async(this::decideToGenerate),
                    Map.of(
                        "transform_query","transform_query",
                        "generate", "generate"
                    ))
            .addEdge("transform_query", "retrieve")
            .addConditionalEdges("generate",
                    edge_async(this::gradeGeneration_v_documentsAndQuestion),
                    Map.of(
                        "not supported", "generate",
                        "useful", END,
                        "not useful", "transform_query"
                    ));
    }
    
    // 🔥 保持原有的节点方法，只修改模型创建部分
    private Map<String,Object> retrieve(State state) {
        // 完全保持原有逻辑，只是使用注入的chroma
        String question = state.question();
        EmbeddingSearchResult<TextSegment> relevant = this.chroma.search(question);
        List<String> documents = relevant.matches().stream()
                .map(m -> m.embedded().text())
                .toList();
        return Map.of("documents", documents, "question", question);
    }
    
    private Map<String,Object> generate(State state) {
        // 保持原有逻辑，使用注入的chatModel替代硬编码创建
        String question = state.question();
        List<String> documents = state.documents();
        
        String generation = (new Generation(chatModel)).apply(question, documents);
        return Map.of("generation", generation);
    }
    
    private Map<String,Object> gradeDocuments(State state) {
        // 保持原有逻辑，使用注入的chatModel
        String question = state.question();
        List<String> documents = state.documents();
        
        final RetrievalGrader grader = new RetrievalGrader(chatModel);
        // ... 其余逻辑完全保持不变
    }
    
    // 🔥 本地搜索替代网页搜索
    private Map<String,Object> localSearch(State state) {
        String question = state.question();
        
        // 使用LocalSearchService替代WebSearchTool
        List<Content> result = localSearchService.apply(question);
        String searchResult = result.stream()
                            .map(content -> content.textSegment().text())
                            .collect(Collectors.joining("\n"));
        
        return Map.of("documents", List.of(searchResult));
    }
    
    private String routeQuestion(State state) {
        String question = state.question();
        
        // 使用注入的chatModel替代硬编码创建
        QuestionRouter.Type source = (new QuestionRouter(chatModel)).apply(question);
        if (source == QuestionRouter.Type.local_search) { // 替代web_search
            // 日志输出
        } else {
            // 日志输出
        }
        return source.name();
    }
    
    // 其他方法完全保持不变...
    
    // 🔥 对外接口：简化调用
    public String processQuery(String question) {
        return ragGraph.invoke(Map.of("question", question))
                .map(State::generation)
                .orElse("无法生成回答");
    }
}
```

#### **2. 各组件最小化Spring改造**

**QuestionRouter.java → QuestionRouterService.java**
```java
// 原record结构
public record QuestionRouter(String openApiKey) implements Function<String, Type>

// 最小改造
@Service  
public record QuestionRouterService(ChatModel chatModel) implements Function<String, Type> {
    
    // 路由类型简单替换
    public enum Type {
        vectorstore,
        local_search    // 替代web_search
    }
    
    // 核心逻辑完全保持不变，只替换模型创建
    @Override
    public Type apply(String question) {
        // 去掉模型创建代码，直接使用注入的chatModel
        Service extractor = AiServices.create(Service.class, chatModel);
        Result ds = extractor.invoke(question);
        return ds.datasource;
    }
    
    // 系统消息中文化
    interface Service {
        @SystemMessage("""
            你是问题路由专家，将用户问题路由到合适的数据源。
            向量存储适用于：专业知识、代理相关、编程问题
            本地搜索适用于：时效信息、常识问题、实时信息
            """)
        Result invoke(String question);
    }
}
```

**其他组件同样最小化改造**：
- `RetrievalGrader` → `RetrievalGraderService` (只替换模型注入)
- `HallucinationGrader` → `HallucinationGraderService` (只替换模型注入)
- `AnswerGrader` → `AnswerGraderService` (只替换模型注入)
- `QuestionRewriter` → `QuestionRewriterService` (只替换模型注入)
- `Generation` → `ContentGeneratorService` (只替换模型注入)

#### **3. 配置最小化**

**RAGConfiguration.java**
```java
@Configuration
public class RAGConfiguration {
    
    @Bean
    public AdaptiveRagService adaptiveRagService(
            ChatModel chatModel,
            EmbeddingModel embeddingModel,
            LocalSearchService localSearchService,
            ChromaStore chromaStore) {
        return new AdaptiveRagService(chatModel, embeddingModel, localSearchService, chromaStore);
    }
    
    @Bean
    public AdaptiveRAGTool adaptiveRAGTool(AdaptiveRagService ragService) {
        return new AdaptiveRAGTool(ragService);
    }
}
```

## 🎯 **最终效果**

### **保留的核心价值**：
1. ✅ **完整的StateGraph流程控制**
2. ✅ **自适应循环优化逻辑**
3. ✅ **多层质量检测机制**
4. ✅ **智能路由决策**

### **最小化改动**：
1. 🔄 **依赖注入替代硬编码** (ChatModel注入)
2. 🔄 **本地搜索替代Tavily** (LocalSearchService)
3. 🔄 **配置外部化** (@ConfigurationProperties)
4. 🔄 **Spring Bean管理** (@Service注解)

### **架构优势**：
1. **最小风险** - 核心逻辑99%保持不变
2. **最大兼容** - 完美融入Spring生态
3. **最快实施** - 改动量最小
4. **最优效果** - 保留所有AdaptiveRAG优势

这种方案真正做到了**"最小改动，最大保留"**，既获得了AdaptiveRAG的强大能力，又完美集成到了OpenManus的Spring架构中！