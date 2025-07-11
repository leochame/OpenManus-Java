package com.openmanus.java.config;

import com.openmanus.java.agent.ManusAgentService;
import com.openmanus.java.tool.ToolCatalog;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.agentexecutor.AgentExecutor;
import org.bsc.langgraph4j.CompiledGraph;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

/**
 * Configuration class for LangChain4j, focusing on creating a modern, stateful,
 * and multi-turn capable agent using AiServices.
 */
@Configuration
public class LangChain4jConfig {

    @Autowired
    private OpenManusProperties openManusProperties;

    /**
     * Provides a chat memory store that holds the conversation history in memory.
     * In a production environment, this could be replaced with a persistent store
     * like Redis or a database.
     *
     * @return A ChatMemoryProvider instance.
     */
    @Bean
    public ChatMemoryProvider chatMemoryProvider() {
        return conversationId -> MessageWindowChatMemory.builder()
                .id(conversationId)
                .maxMessages(50) // Retain the last 50 messages for context
                .chatMemoryStore(new InMemoryChatMemoryStore())
                .build();
    }

    /**
     * Creates a shared ChatModel bean based on the application's configuration properties.
     * This bean is a prerequisite for both AiServices and LangGraph.
     *
     * @return A configured ChatModel instance.
     */
    @Bean
    public ChatModel chatModel() {
        OpenManusProperties.LlmConfig.DefaultLLM llmConfig = openManusProperties.getLlm().getDefaultLlm();

        return OpenAiChatModel.builder()
                .baseUrl(llmConfig.getBaseUrl())
                .apiKey(llmConfig.getApiKey())
                .modelName(llmConfig.getModel())
                .temperature(llmConfig.getTemperature())
                .maxTokens(llmConfig.getMaxTokens())
                .timeout(Duration.ofSeconds(llmConfig.getTimeout()))
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    /**
     * Creates and compiles the AgentExecutor graph, making it available as a Spring bean.
     * This compiled graph is the core of the langgraph4j-based agent and can be
     * injected into other components, such as the LangGraphStudioConfig for visualization.
     *
     * @param chatModel   The ChatModel bean to be used by the agent.
     * @param toolCatalog The catalog of tools available to the agent.
     * @return A {@link CompiledGraph} instance representing the agent.
     * @throws GraphStateException if there is an error compiling the graph.
     */
    @Bean
    public CompiledGraph<AgentExecutor.State> compiledGraph(ChatModel chatModel, ToolCatalog toolCatalog) throws GraphStateException {
        AgentExecutor.Builder builder = AgentExecutor.builder()
                .chatModel(chatModel)
                .toolsFromObject(toolCatalog.getTools().toArray(new Object[0]));

        return builder.build().compile();
    }

    @Bean
    public EmbeddingModel embeddingModel(OpenManusProperties properties) {
        OpenManusProperties.LlmConfig.DefaultLLM llmConfig = properties.getLlm().getDefaultLlm();
        return OpenAiEmbeddingModel.builder()
                .baseUrl(llmConfig.getBaseUrl())
                .apiKey(llmConfig.getApiKey())
                .modelName(llmConfig.getEmbeddingModel())
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    /**
     * Creates the ManusAgentService using LangChain4j's AiServices.
     * This is the core of our agent, where the AI model, tools, and memory
     * are wired together into a declarative, service-oriented interface.
     * The framework handles the underlying ReAct agent loop automatically.
     *
     * @param chatModel          The conversational AI model to use.
     * @param toolCatalog        The catalog of available tools for the agent.
     * @param chatMemoryProvider The provider for managing conversation memory.
     * @return A fully configured instance of our agent service.
     */
    @Bean
    public ManusAgentService manusAgentService(ChatModel chatModel,
                                             ToolCatalog toolCatalog,
                                             ChatMemoryProvider chatMemoryProvider) {
        return AiServices.builder(ManusAgentService.class)
                .chatModel(chatModel)
                .tools(toolCatalog.getTools())
                .chatMemoryProvider(chatMemoryProvider)
                .build();
    }
} 