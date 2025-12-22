package com.openmanus.agent.impl.thinker;

import com.openmanus.agent.base.AbstractAgentExecutor;
import com.openmanus.infra.monitoring.AgentExecutionTracker;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.SystemMessage;
import lombok.extern.slf4j.Slf4j;
import com.openmanus.domain.model.AgentExecutionEvent;
import static com.openmanus.infra.log.LogMarkers.TO_FRONTEND;

/**
 * 思考智能体 - 负责任务分析和规划
 *
 * 重构改进：
 * 1. 移除了错误的状态管理逻辑
 * 2. 简化了实现，专注于核心思考功能
 * 3. 遵循ToolExecutor接口的设计原则
 * 4. 状态管理交由langgraph4j的StateGraph处理
 *
 * 核心功能：
 * 1. 分析用户请求，理解真正的需求
 * 2. 将复杂任务分解为清晰的步骤
 * 3. 制定详细的执行计划
 * 4. 接收反思反馈并调整规划
 */
@Slf4j
public class ThinkingAgent extends AbstractAgentExecutor<ThinkingAgent.Builder> {

    private final AgentExecutionTracker agentExecutionTracker;

    // 系统消息模板提取为常量，便于维护
    private static final String SYSTEM_MESSAGE_TEMPLATE = """
        **角色**: 你是一位顶级的AI软件工程师和首席规划师。
        **核心目标**: 将用户的模糊需求，转化为一份精确、详尽、且完全基于事实的行动计划。

        ## 核心原则
        1.  **事实驱动**: **你的所有决策和规划都必须严格基于通过工具获取到的最新信息。** 你必须假设自己的内部知识已过时，对项目状态一无所知。
        2.  **先分析后规划**: 在给出最终计划前，必须先提供你的“任务分析”过程。这有助于确保你的规划是深思熟虑的。
        3.  **简洁有效**: 规则是用来遵守的，不是用来展示的。你的计划应该直截了当，避免不必要的复杂性。

        ## 工作流程: 探索 -> 分析 -> 规划

        ### 阶段一: 探索 (如果信息不足)
        *   **任务**: 识别当前信息的缺口和需要验证的假设。
        *   **产出**: 如果信息不完整，你的唯一产出应该是一个“探索计划”，该计划只包含用于收集信息的工具调用。

        ### 阶段二: 分析与规划 (信息充足后)
        *   **任务**: 基于探索阶段收集到的事实，进行深度分析，并制定最终的执行计划。
        *   **产出**: 你的最终回复 **必须** 遵循下面的输出格式。

        ---

        ## 输出格式 (必须严格遵守)

        你的输出 **必须** 严格遵循此 Markdown 格式，将分析过程置于计划之前。

        ### 任务分析
        *   **最终目标**: [清晰地重述你理解的最终业务或技术目标]
        *   **关键假设**:
            *   [列出你进行规划所依赖的关键假设1]
            *   [列出关键假设2]

        ### 详细执行计划
        1.  **步骤一: [步骤目标]**
            *   **描述**: [描述具体做什么，以及为什么]
            *   **工具**: `[从 'search_agent', 'file_agent', 'code_agent' 中选择]`
            *   **参数**: 
            ```json
            {
                "parameter_name": "value"
            }
            ```
            *   **预期**: [说明此步骤的预期产出]
        2.  **步骤二: [步骤目标]**
            *   **描述**: [同上]
            *   **工具**: `[同上]`
            *   **参数**: 
            ```json
            {
                "parameter_name": "value"
            }
            ```
            *   **预期**: [同上]
        
        ---
        
        **关键指令重复**: 记住，你的核心是 **事实驱动**。**绝对不要臆测文件路径、项目状态或任何未经验证的信息。** 你的价值在于基于实时数据进行规划。
        """;

    public static class Builder extends AbstractAgentExecutor.Builder<Builder> {

        private AgentExecutionTracker agentExecutionTracker;

        public Builder agentExecutionTracker(AgentExecutionTracker agentExecutionTracker) {
            this.agentExecutionTracker = agentExecutionTracker;
            return this;
        }

        public ThinkingAgent build() {
            this.name("thinking_agent")
                .description("当用户提出新任务或需要重新规划时，使用此工具进行任务分析和制定执行计划。适用于：分析复杂任务、制定执行步骤、重新规划策略")
                .singleParameter("用户请求或需要重新规划的任务描述")
                .systemMessage(SystemMessage.from(SYSTEM_MESSAGE_TEMPLATE));

            return new ThinkingAgent(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public ThinkingAgent(Builder builder) {
        super(builder);
        this.agentExecutionTracker = builder.agentExecutionTracker;
    }

    @Override
    public String execute(ToolExecutionRequest toolExecutionRequest, Object memoryId) {
        String sessionId = memoryId != null ? memoryId.toString() : "unknown-session";
        String input = toolExecutionRequest.arguments();
        
        agentExecutionTracker.startAgentExecution(sessionId, name(), "THINKING_START", input);
        log.info("🚀🚀 ThinkingAgent.execute, ToolExecutionRequest:{}\n memoryId:{}", toolExecutionRequest, memoryId);
        log.info(TO_FRONTEND,"User Request {}",toolExecutionRequest.arguments());
        String result = super.execute(toolExecutionRequest, memoryId);

        log.info(TO_FRONTEND,"ThinkingAgent.execute result: {}", result);
        agentExecutionTracker.endAgentExecution(sessionId, name(), "THINKING_END", result, AgentExecutionEvent.ExecutionStatus.SUCCESS);

        return result;
    }
}
