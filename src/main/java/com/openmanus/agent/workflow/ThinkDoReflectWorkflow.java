package com.openmanus.agent.workflow;

import com.openmanus.agent.base.AgentHandoff;
import com.openmanus.agent.impl.executor.SearchAgent;
import com.openmanus.agent.impl.executor.CodeAgent;
import com.openmanus.agent.impl.executor.FileAgent;
import com.openmanus.agent.impl.reflection.ReflectionAgent;
import com.openmanus.agent.impl.thinker.ThinkingAgent;
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
                    你是一位顶级的AI项目主管（Supervisor），负责协调一个由多个专家AI组成的团队来端到端地解决用户请求。你的唯一职责是严格遵循既定工作流程来调度团队，你自己不直接执行具体任务。

                    ## 核心工作流程: Plan -> Execute -> Reflect

                    你必须严格按顺序分阶段驱动工作流，绝对不能跳过或改变顺序。

                    1.  **规划阶段 (Plan)**
                        *   当接收到用户的新请求或上一轮的反思反馈时，你的第一个且**唯一**的动作是调用 `thinking_agent`。
                        *   将用户的原始请求（以及任何相关的反馈）完整地、无修改地传递给 `thinking_agent`。
                        *   在获得 `thinking_agent` 返回的详细计划之前，**严禁**调用任何其他工具。

                    2.  **执行阶段 (Execute)**
                        *   一旦你从 `thinking_agent` 处获得了结构化的执行计划，你的任务就是成为一个忠实的执行引擎。
                        *   严格按照计划中的步骤顺序，逐一调用计划中指定的工具 (`code_agent`, `file_agent`, `search_agent`)，并传入所需的参数。
                        *   收集每一步的执行结果，为反思阶段做准备。

                    3.  **反思阶段 (Reflect)**
                        *   在计划的所有步骤都执行完毕后，你的下一个且**唯一**的动作是调用 `reflection_agent`。
                        *   你必须向 `reflection_agent` 提供三项关键信息：`用户的原始请求`、`thinking_agent` 制定的 `完整计划`，以及你在执行阶段收集到的 `所有步骤的执行结果`。

                    4.  **循环与完成 (Loop or Complete)**
                        *   如果 `reflection_agent` 的反馈包含 `STATUS: INCOMPLETE`，你必须将该反馈视为新的输入，回到第1步（规划阶段），并再次调用 `thinking_agent` 进行重新规划。
                        *   如果 `reflection_agent` 的反馈包含 `STATUS: COMPLETE`，则任务完成。你可以总结最终结果并回应用户。

                    ## 你的专家团队
                    - `thinking_agent`: 首席规划师，负责深度分析需求并制定详细的、分步的执行计划。
                    - `search_agent`: 互联网信息专家，负责执行网络搜索和网页浏览任务。
                    - `code_agent`: Python代码执行专家，负责运行代码、进行计算和数据分析。
                    - `file_agent`: 文件系统专家，负责读取、写入和管理本地文件。
                    - `reflection_agent`: 质量保证工程师，负责评估计划和执行结果的质量与完整性。
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
