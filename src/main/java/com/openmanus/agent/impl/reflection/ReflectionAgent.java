package com.openmanus.agent.impl.reflection;

import com.openmanus.agent.base.AbstractAgentExecutor;
import com.openmanus.domain.model.AgentExecutionEvent;
import com.openmanus.infra.monitoring.AgentExecutionTracker;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.SystemMessage;
import lombok.extern.slf4j.Slf4j;
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
        **角色**: 你是一名严谨、客观的质量保证 (QA) 工程师。
        **核心目标**: 对 `执行结果` 进行事实驱动的评估，并为下一轮迭代提供清晰、可操作的反馈。

        ## 核心原则
        1.  **绝对客观**: **你的评估必须严格且仅仅基于 `原始需求`、`执行计划` 和 `执行结果` 这三项输入。** 任何超出此范围的信息都不得纳入考量。
        2.  **先分析后判断**: 在给出最终的 `完成状态` 之前，必须先提供你的详细 `评估分析`。
        3.  **建设性反馈**: 你的 `改进建议` 必须是具体的、可执行的，能够直接指导规划师进行下一步工作。

        ---

        ## 工作流程: 对比分析 -> 状态判断 -> 提供反馈

        ### 阶段一: 对比分析
        *   **任务**: 按照以下三个维度，对输入信息进行严格的对比分析。
        *   **产出**: 在 `评估分析` 部分完整地呈现你的分析过程。

        ### 阶段二: 状态判断与根因分析
        *   **任务**: 基于你的分析，判断任务的完成状态。如果未完成，深入分析其根本原因。
        *   **产出**: 明确标注 `完成状态`，如果适用，提供 `根本原因分析`。

        ### 阶段三: 提供反馈
        *   **任务**: 根据根因分析，为规划师提供具体的下一步改进建议。
        *   **产出**: 在 `改进建议` 部分给出清晰的指令。

        ---

        ## 输出格式 (必须严格遵守)

        ### 评估分析
        *   **计划 vs. 需求**: [你的分析]
        *   **结果 vs. 计划**: [你的分析]
        *   **结果 vs. 需求**: [你的分析]

        ### 完成状态
        `STATUS: [COMPLETE 或 INCOMPLETE]`

        ### 根本原因分析 (仅在 INCOMPLETE 时提供)
        *   **诊断**: `[规划错误 或 执行错误]`
        *   **分析**: [深入解释失败的具体原因]

        ### 改进建议
        `FEEDBACK:` [提供一个清晰、简洁、可操作的下一步指令]

        ---

        **关键指令重复**: 记住，你的核心是 **提供可操作的反馈**。一个好的反馈是能够让规划师立即知道下一步应该做什么。
        """;

    public static class Builder extends AbstractAgentExecutor.Builder<Builder> {

        private AgentExecutionTracker agentExecutionTracker;

        public Builder agentExecutionTracker(AgentExecutionTracker agentExecutionTracker) {
            this.agentExecutionTracker = agentExecutionTracker;
            return this;
        }

        public ReflectionAgent build() {
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

    public ReflectionAgent(Builder builder) {
        super(builder);
        this.agentExecutionTracker = builder.agentExecutionTracker;
    }

    @Override
    public String execute(ToolExecutionRequest toolExecutionRequest, Object memoryId) {
        String sessionId = memoryId != null ? memoryId.toString() : "unknown-session";
        String input = toolExecutionRequest.arguments();
        
        agentExecutionTracker.startAgentExecution(sessionId, name(), "REFLECTION_START", input);
        log.debug("ReflectionAgent.execute, ToolExecutionRequest:{}\n memoryId:{}", toolExecutionRequest, memoryId);

        // 反思阶段开始
        log.info(TO_FRONTEND, "┌──────────────────────────────────────────────────────────┐");
        log.info(TO_FRONTEND, "│  🔍 REFLECTION AGENT · 质量评估模块                       │");
        log.info(TO_FRONTEND, "├──────────────────────────────────────────────────────────┤");
        log.info(TO_FRONTEND, "│  📋 正在审查执行结果...                                  │");
        log.info(TO_FRONTEND, "│  🎯 对比原始需求与实际产出                                │");
        log.info(TO_FRONTEND, "│  💡 评估完成度并提供改进建议                              │");
        log.info(TO_FRONTEND, "└──────────────────────────────────────────────────────────┘");

        String result = super.execute(toolExecutionRequest, memoryId);

        // 反思阶段完成
        boolean isComplete = result != null && result.contains("STATUS: COMPLETE");
        if (isComplete) {
            log.info(TO_FRONTEND, "┌──────────────────────────────────────────────────────────┐");
            log.info(TO_FRONTEND, "│  ✅ 评估完成 · 任务已达标                                  │");
            log.info(TO_FRONTEND, "└──────────────────────────────────────────────────────────┘");
        } else {
            log.info(TO_FRONTEND, "┌──────────────────────────────────────────────────────────┐");
            log.info(TO_FRONTEND, "│  🔄 需要进一步优化 · 启动下一轮迭代                        │");
            log.info(TO_FRONTEND, "└──────────────────────────────────────────────────────────┘");
        }
        agentExecutionTracker.endAgentExecution(sessionId, name(), "REFLECTION_END", result, AgentExecutionEvent.ExecutionStatus.SUCCESS);
        
        return result;
    }
}
