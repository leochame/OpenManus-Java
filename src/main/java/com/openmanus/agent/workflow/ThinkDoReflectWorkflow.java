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
        
        // 构建handoff工作流 - 取消Supervisor，让ChatModel自主选择
        // 但添加系统提示引导Think-Do-Reflect流程
        this.handoffExecutor = AgentHandoff.builder()
                .chatModel(chatModel)
                .systemMessage(dev.langchain4j.data.message.SystemMessage.from("""
                    你是一个智能的任务执行系统，遵循"Think-Do-Reflect"工作流程：

                    🧠 THINK阶段：对于新任务，首先使用thinking_agent进行任务分析和规划
                    🔧 DO阶段：根据规划使用适当的执行工具：
                       - search_agent：获取信息、搜索网络内容
                       - code_agent：编写代码、执行计算、数据分析
                       - file_agent：文件读写、目录操作
                    🤔 REFLECT阶段：执行完成后使用reflection_agent评估结果

                    工作流程：
                    1. 新任务 → 使用thinking_agent分析规划
                    2. 执行规划 → 选择合适的执行工具
                    3. 评估结果 → 使用reflection_agent检查完成度
                    4. 如果未完成 → 返回步骤1重新规划

                    重要原则：
                    - 对于复杂任务，必须先思考再执行
                    - 执行完成后必须进行反思评估
                    - 根据反思结果决定是否需要进一步改进
                    """))
                .agent(thinkingAgent)    // 思考Agent - 任务分析和规划
                .agent(searchAgent)      // 搜索Agent - 信息检索
                .agent(codeAgent)        // 代码Agent - 代码执行
                .agent(fileAgent)        // 文件Agent - 文件操作
                .agent(reflectionAgent)  // 反思Agent - 结果评估
                .build()
                .compile();
    }
    
    /**
     * 执行Think-Do-Reflect工作流（异步版本）
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

    /**
     * 执行Think-Do-Reflect工作流（同步版本）
     * @param userInput 用户输入
     * @return 同步执行结果
     */
    public String executeSync(String userInput) {
        // 初始化最小状态，只包含messages字段
        Map<String, Object> initialState = Map.of("messages", UserMessage.from(userInput));

        return handoffExecutor.invoke(initialState)
                .map(AgentExecutor.State::finalResponse)
                .flatMap(opt -> opt)
                .orElse("未收到智能体响应");
    }
}
