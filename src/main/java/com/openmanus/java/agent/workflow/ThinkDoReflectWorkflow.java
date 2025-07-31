package com.openmanus.java.agent.workflow;

import com.openmanus.java.agent.base.AgentHandoff;
import com.openmanus.java.agent.impl.supervisor.SupervisorAgent;
import com.openmanus.java.agent.impl.thinker.ThinkingAgent;
import com.openmanus.java.agent.impl.executor.SearchAgent;
import com.openmanus.java.agent.impl.executor.CodeAgent;
import com.openmanus.java.agent.impl.executor.FileAgent;
import com.openmanus.java.agent.impl.reflection.ReflectionAgent;
import com.openmanus.java.agent.tool.AgentToolCatalog;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.agentexecutor.AgentExecutor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Think-Do-Reflect工作流服务
 * 
 * 这是整个"想、做、回"循环反思型多智能体系统的主要入口点。
 * 它集成了所有智能体，并通过AgentHandoff机制实现智能体间的协作。
 */
@Service
public class ThinkDoReflectWorkflow {
    
    private final CompiledGraph<AgentExecutor.State> handoffExecutor;
    
    public ThinkDoReflectWorkflow(
            ChatModel chatModel, 
            AgentToolCatalog agentToolCatalog,
            ThinkingAgent thinkingAgent,
            SearchAgent searchAgent,
            CodeAgent codeAgent,
            FileAgent fileAgent,
            ReflectionAgent reflectionAgent) throws GraphStateException {
        
        // 创建SupervisorAgent
        SupervisorAgent supervisorAgent = SupervisorAgent.builder()
                .chatModel(chatModel)
                .agentToolCatalog(agentToolCatalog)
                .build();
                
        // 构建handoff工作流
        this.handoffExecutor = AgentHandoff.builder()
                .chatModel(chatModel)
                .agent(supervisorAgent)  // SupervisorAgent作为入口点
                .agent(thinkingAgent)
                .agent(searchAgent)
                .agent(codeAgent)
                .agent(fileAgent)
                .agent(reflectionAgent)
                .build()
                .compile();
    }
    
    /**
     * 执行Think-Do-Reflect工作流
     * @param userInput 用户输入
     * @return 异步执行结果
     */
    public CompletableFuture<String> execute(String userInput) {
        // 初始化最小状态，只包含messages字段
        Map<String, Object> initialState = Map.of("messages", UserMessage.from(userInput));

        return CompletableFuture.supplyAsync(() -> handoffExecutor.invoke(initialState))
                .thenApply(response -> response
                        .map(AgentExecutor.State::finalResponse)
                        .flatMap(opt -> opt)
                        .orElse("未收到智能体响应"));
    }
}
