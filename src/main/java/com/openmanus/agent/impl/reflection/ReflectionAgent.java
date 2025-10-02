package com.openmanus.agent.impl.reflection;

import com.openmanus.agent.base.AbstractAgentExecutor;
import com.openmanus.domain.model.AgentExecutionEvent;
import com.openmanus.infra.monitoring.AgentExecutionTracker;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.SystemMessage;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.GraphStateException;
import static com.openmanus.infra.log.LogMarkers.TO_FRONTEND;

/**
 * 反思智能体 - 负责评估执行结果并决定是否需要继续循环
 *
 * 重构改进：
 * 1. 移除了复杂的状态管理逻辑
 * 2. 使用标准JSON解析替代手动字符串解析
 * 3. 简化了实现，专注于核心评估功能
 * 4. 遵循ToolExecutor接口的设计原则
 * 5. 状态管理交由langgraph4j的StateGraph处理
 *
 * 核心功能：
 * 1. 评估执行结果与原始需求的匹配程度
 * 2. 判断任务是否完成
 * 3. 提供具体的改进建议
 * 4. 输出标准化的评估结果
 */
@Slf4j
public class ReflectionAgent extends AbstractAgentExecutor<ReflectionAgent.Builder> {

    private final AgentExecutionTracker agentExecutionTracker;

    // 状态常量
    private static final String STATUS_COMPLETE = "STATUS: COMPLETE";
    private static final String STATUS_INCOMPLETE = "STATUS: INCOMPLETE";


    // 系统消息模板提取为常量
    private static final String SYSTEM_MESSAGE_TEMPLATE = """
        你是一名严谨、客观的质量保证 (QA) 工程师，你的唯一职责是进行事实驱动的评估。

        ## 核心原则
        1.  **事实至上 (Truth-Oriented)**: 你的评估必须严格基于以下三个输入：`原始需求`、`执行计划`和`执行结果`。
        2.  **内部知识全部过时**: 仅仅格局任何输入中明确提供的信息判断。自己的内部知识已经失效。如果信息不足，就明确指出。

        ---

        ### 评估流程 (必须严格遵守)

        你必须按照以下三步进行结构化分析：

        **1. 计划 vs. 需求**
           - `执行计划`是否完全、准确地响应了`原始需求`中的所有要点？
           - `执行计划`是否建立在事实之上（例如，基于探索阶段的结果），还是基于错误的假设？

        **2. 结果 vs. 计划**
           - `执行结果`是否成功地完成了`执行计划`中的每一步？
           - 是否有任何步骤失败或产生了与预期不符的结果？

        **3. 结果 vs. 需求**
           - 最终的`执行结果`是否从根本上解决了用户的`原始需求`？

        ---

        ### 输出格式 (必须严格遵守)

        ## 评估分析
        *   **计划 vs. 需求**: [你的分析]
        *   **结果 vs. 计划**: [你的分析]
        *   **结果 vs. 需求**: [你的分析]

        ## 完成状态
        [根据你的分析，明确标注 `STATUS: COMPLETE` 或 `STATUS: INCOMPLETE`]

        ## 根本原因 (如果未完成)
        *   **诊断**: [明确指出是 **规划错误** 还是 **执行错误**]
        *   **分析**: [详细解释为什么会发生这个错误]

        ## 改进建议
        `FEEDBACK:` [提供具体、可操作的下一步建议。例如：如果是规划错误，应建议`ThinkingAgent`重新进行探索；如果是执行错误，应指出具体的修复方向。]
        """;

    public static class Builder extends AbstractAgentExecutor.Builder<Builder> {

        private AgentExecutionTracker agentExecutionTracker;

        public Builder agentExecutionTracker(AgentExecutionTracker agentExecutionTracker) {
            this.agentExecutionTracker = agentExecutionTracker;
            return this;
        }

        public ReflectionAgent build() throws GraphStateException {
            this.name("reflection_agent")
                .description("当任务执行完成后，使用此工具评估结果质量和完整性，决定是否需要进一步改进。适用于：评估执行结果、检查任务完成度、提供改进建议")
                .singleParameter("执行结果或包含上下文的评估请求")
                .systemMessage(SystemMessage.from(SYSTEM_MESSAGE_TEMPLATE));

            return new ReflectionAgent(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public ReflectionAgent(Builder builder) throws GraphStateException {
        super(builder);
        this.agentExecutionTracker = builder.agentExecutionTracker;
    }

    @Override
    public String execute(ToolExecutionRequest toolExecutionRequest, Object memoryId) {
        String sessionId = memoryId != null ? memoryId.toString() : "unknown-session";
        String input = toolExecutionRequest.arguments();
        
        agentExecutionTracker.startAgentExecution(sessionId, name(), "REFLECTION_START", input);
        log.info(TO_FRONTEND,"🚀🚀 ReflectionAgent.execute, ToolExecutionRequest:{}\n memoryId:{}", toolExecutionRequest, memoryId);

        String result = super.execute(toolExecutionRequest, memoryId);

        log.info(TO_FRONTEND,"ReflectionAgent.execute result: {}", result);
        agentExecutionTracker.recordIntermediateResult(sessionId, name(), "REFLECTION_RESULT", result, "Generated reflection and feedback");
        agentExecutionTracker.endAgentExecution(sessionId, name(), "REFLECTION_END", result, AgentExecutionEvent.ExecutionStatus.SUCCESS);
        
        return result;
    }
}
