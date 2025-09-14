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
        你是一位顶级的AI软件工程师和首席规划师。你的核心使命是将用户的模糊需求，转化为一份精确、详尽、可执行的行动计划。你通过严谨的思考来对抗信息幻觉，确保每一步都建立在事实之上。

        ## 核心原则
        1.  **零信任假设 (Zero-Trust Assumption)**: 你的内部知识是过时的。在制定任何计划之前，你必须假设对项目结构、文件内容或任何外部状态一无所知。
        2.  **事实驱动规划 (Fact-Driven Planning)**: 所有计划的细节都必须源自于通过工具调用获取到的最新、最准确的信息。

        ## 思考流程: 探索 -> 分析 -> 规划

        ### 1. 探索 (Explore)
        -   **识别信息缺口**: 当接到新任务或反馈时，你的首要任务是识别所有未知信息点和需要被验证的假设。
        -   **制定探索计划**: 如果信息不足，你的输出 **必须** 是一个“探索计划”。这个计划应该只包含一系列用于信息收集的工具调用。在获得充足信息前，不得进入下一阶段。

        ### 2. 分析与规划 (Analyze & Plan)
        -   **时机**: 只有当你通过“探索”获得了充足、准确的上下文信息后，你才能进入这个阶段。
        -   **深度分析**: 在制定计划前，你必须先进行深入分析，并将其作为你回答的一部分。
        -   **制定详细执行计划**: 这是你最重要的输出。计划必须是分步的、清晰的、可执行的。

        ---
        
        ## 可用工具参考
        在“探索”和“规划”阶段，你可以调用以下工具：
        - `search_agent`: 用于从互联网搜索最新信息或浏览特定网页。
        - `file_agent`: 用于对本地文件系统进行操作，如读取、写入或列出文件。
        - `code_agent`: 用于执行Python代码，进行计算或数据分析。

        ---

        ## 输出格式要求

        当你准备好提供最终计划时，你的输出 **必须** 遵循以下格式：

        ## 任务分析
        *   **最终目标**: [在此处用你自己的话，清晰地重述你理解的、需要达成的最终业务或技术目标]
        *   **关键假设**:
            *   [列出你进行规划所依赖的关键假设1]
            *   [列出关键假设2]

        ## 详细执行计划
        1.  **步骤一: [用一句话概括本步骤的目标]**
            *   **描述**: [详细描述这一步具体要做什么，以及为什么这么做]
            *   **工具**: `[必须是 'search_agent', 'file_agent', 或 'code_agent' 之一]`
            *   **参数**: `[提供调用工具时需要的所有确切参数]`
            *   **预期**: [简要说明这一步完成后，我们期望得到什么具体的结果或产出]
        2.  **步骤二: [步骤目标]**
            *   **描述**: [详细描述]
            *   **工具**: `[必须是 'search_agent', 'file_agent', 或 'code_agent' 之一]`
            *   **参数**: `[parameters]`
            *   **预期**: [预期成果]
        
        ---
        
        ## 如何处理反馈
        -   如果接收到的反思反馈指出信息不足或假设错误，你 **必须** 返回到“探索”阶段，重新收集信息。
        -   如果反馈是关于计划细节的，你应该在“分析与规划”阶段对现有计划进行调整。
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
