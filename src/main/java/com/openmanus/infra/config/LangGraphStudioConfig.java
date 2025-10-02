package com.openmanus.infra.config;

import dev.langchain4j.data.message.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.agentexecutor.AgentExecutor;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.studio.springboot.AbstractLangGraphStudioConfig;
import org.bsc.langgraph4j.studio.springboot.LangGraphFlow;
import org.springframework.context.annotation.Configuration;

/**
 * LangGraph4j Studio配置类
 *
 * 提供可视化调试界面，支持OpenManus Agent执行流程的实时监控和调试
 * 访问地址：http://localhost:8089/
 */
@Configuration
@Slf4j
public class LangGraphStudioConfig extends AbstractLangGraphStudioConfig {

    private final LangGraphFlow flow;

    public LangGraphStudioConfig(CompiledGraph<AgentExecutor.State> compiledGraph) throws GraphStateException {
        log.info("初始化LangGraph Studio配置...");
        this.flow = createStudioFlow(compiledGraph.stateGraph);
        log.info("LangGraph Studio初始化完成 - 访问地址: http://localhost:8089/");
    }

    /**
     * 创建Studio流程配置
     */
    private LangGraphFlow createStudioFlow(StateGraph<AgentExecutor.State> workflow) throws GraphStateException {
        return LangGraphFlow.builder()
                .title("OpenManus AgentExecutor - 可视化调试")
                .addInputStringArg("messages", true, input -> UserMessage.from((String) input))
                .stateGraph(workflow)
                .compileConfig(CompileConfig.builder()
                        .checkpointSaver(new MemorySaver())
                        .build())
                .build();
    }
    
    @Override
    public LangGraphFlow getFlow() {
        return this.flow;
    }
}