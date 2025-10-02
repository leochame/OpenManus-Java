package com.openmanus.infra.config;

import com.openmanus.agent.tool.OmniToolCatalog;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.agentexecutor.AgentExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * LangChain4j配置类
 * 
 * 配置LLM、嵌入模型、对话记忆和Agent执行器
 * 采用构建者模式创建各组件实例
 */
@Configuration
public class LangChain4jConfig {

    private static final int MAX_MEMORY_MESSAGES = 50;
    
    private final OpenManusProperties properties;

    public LangChain4jConfig(OpenManusProperties properties) {
        this.properties = properties;
    }

    /**
     * 对话记忆提供者
     * 开发环境使用内存存储，生产环境可替换为Redis等持久化存储
     */
    @Bean
    public ChatMemoryProvider chatMemoryProvider() {
        return conversationId -> MessageWindowChatMemory.builder()
                .id(conversationId)
                .maxMessages(MAX_MEMORY_MESSAGES)
                .chatMemoryStore(new InMemoryChatMemoryStore())
                .build();
    }

    /**
     * 聊天模型
     * 基于配置文件创建LLM实例
     */
    @Bean
    public ChatModel chatModel() {
        OpenManusProperties.LlmConfig.DefaultLLM llmConfig = properties.getLlm().getDefaultLlm();

        return OpenAiChatModel.builder()
                .baseUrl(llmConfig.getBaseUrl())
                .apiKey(llmConfig.getApiKey())
                .modelName(llmConfig.getModel())
                .temperature(llmConfig.getTemperature())
                .maxTokens(llmConfig.getMaxTokens())
                .timeout(Duration.ofSeconds(llmConfig.getTimeout()))
                .build();
    }

    /**
     * Agent执行器图
     * 编译后的图可被注入到其他组件中使用
     */
    @Bean
    public CompiledGraph<AgentExecutor.State> compiledGraph(ChatModel chatModel, 
                                                            OmniToolCatalog omniToolCatalog) throws GraphStateException {
        AgentExecutor.Builder builder = AgentExecutor.builder()
                .chatModel(chatModel)
                .toolsFromObject(omniToolCatalog.getTools().toArray(new Object[0]));

        return builder.build().compile();
    }

    /**
     * 嵌入模型
     * 用于文本向量化和语义搜索
     */
    @Bean
    public EmbeddingModel embeddingModel() {
        OpenManusProperties.LlmConfig.DefaultLLM llmConfig = properties.getLlm().getDefaultLlm();
        
        return OpenAiEmbeddingModel.builder()
                .baseUrl(llmConfig.getBaseUrl())
                .apiKey(llmConfig.getApiKey())
                .modelName(llmConfig.getEmbeddingModel())
                .logRequests(true)
                .logResponses(true)
                .build();
    }
} 