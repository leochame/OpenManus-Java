package com.openmanus.agent.impl.thinker;

import com.openmanus.agent.base.AbstractAgentExecutor;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.SystemMessage;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.GraphStateException;

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

    // 系统消息模板提取为常量，便于维护
    private static final String SYSTEM_MESSAGE_TEMPLATE = """
        你是规划专家，专注于任务分析和执行规划：

        核心职责：
        1. 分析用户请求，理解真正的需求和目标
        2. 将复杂任务分解为清晰、可执行的步骤
        3. 制定详细的执行计划和操作指南
        4. 基于反思反馈调整和优化规划

        输入处理：
        - 新任务：从零开始分析用户请求
        - 重新规划：基于反思反馈调整原有计划
        - 上下文信息：充分利用提供的背景信息

        输出格式：
        ## 任务分析
        [对请求的深入理解和目标识别]

        ## 执行步骤
        1. [具体步骤1 - 详细描述]
        2. [具体步骤2 - 详细描述]
        3. [具体步骤3 - 详细描述]
        ...

        ## 执行计划
        [详细的操作指南，包括工具选择、参数设置、预期结果等]

        ## 风险评估
        [可能的执行障碍和应对策略]

        注意事项：
        - 确保规划具体、可执行
        - 考虑任务的复杂性和依赖关系
        - 提供清晰的成功标准
        - 预见可能的问题和解决方案
        """;

    public static class Builder extends AbstractAgentExecutor.Builder<Builder> {

        public ThinkingAgent build() throws GraphStateException {
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

    public ThinkingAgent(Builder builder) throws GraphStateException {
        super(builder);
    }

    @Override
    public String execute(ToolExecutionRequest toolExecutionRequest, Object memoryId) {
        log.info("🚀🚀 ThinkingAgent.execute, ToolExecutionRequest:{}\n memoryId:{}",toolExecutionRequest,memoryId);

        String result = super.execute(toolExecutionRequest, memoryId);

        log.info("ThinkingAgent.execute result: {}", result);

        return result;
    }
}
