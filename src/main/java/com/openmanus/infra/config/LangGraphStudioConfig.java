package com.openmanus.infra.config;

import com.openmanus.agent.tool.OmniToolCatalog;
import com.openmanus.agent.workflow.MultiAgentHandoffWorkflow;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.agentexecutor.AgentExecutor;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.studio.springboot.AbstractLangGraphStudioConfig;
import org.bsc.langgraph4j.studio.springboot.LangGraphFlow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.bsc.langgraph4j.CompiledGraph;

/**
 * LangGraph4j Studio Configuration Class
 *
 * Provides a visual debugging interface for the OpenManus project,
 * centered around the pre-compiled AgentExecutor graph.
 */
@Configuration
public class LangGraphStudioConfig extends AbstractLangGraphStudioConfig {

    private static final Logger logger = LoggerFactory.getLogger(LangGraphStudioConfig.class);

    private final LangGraphFlow flow;


    public LangGraphStudioConfig(CompiledGraph<AgentExecutor.State> compiledGraph) throws GraphStateException {

        logger.info("Initializing LangGraph4j Studio configuration with AgentExecutor graph...");

        // Create Studio flow configuration from the injected compiled graph
        this.flow = createStudioFlow(compiledGraph.stateGraph);

        logger.info("LangGraph4j Studio configuration initialized - Access URL: http://localhost:8089/");
    }

    /**
     * Create Studio flow configuration
     */
    private LangGraphFlow createStudioFlow(StateGraph<AgentExecutor.State> workflow) throws GraphStateException {
        return LangGraphFlow.builder()
                .title("OpenManus AgentExecutor - Visual Debugging")
                .addInputStringArg("messages", true, input -> UserMessage.from((String) input))
                .stateGraph(workflow) // Pass the raw graph
                .compileConfig(CompileConfig.builder()
                        .checkpointSaver(new MemorySaver())  // Enable checkpoint saving
                        .build())
                .build();
    }
    
    @Override
    public LangGraphFlow getFlow() {
        return this.flow;
    }

    @Bean
    public MultiAgentHandoffWorkflow multiAgentHandoffWorkflow(ChatModel chatLanguageModel, OmniToolCatalog omniToolCatalog) throws Exception {
        return new MultiAgentHandoffWorkflow(chatLanguageModel, omniToolCatalog);
    }
}