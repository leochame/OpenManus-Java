package com.openmanus.agent.impl.reflection;

import com.openmanus.agent.base.AbstractAgentExecutor;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.SystemMessage;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.GraphStateException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;

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

    // 状态常量
    private static final String STATUS_COMPLETE = "STATUS: COMPLETE";
    private static final String STATUS_INCOMPLETE = "STATUS: INCOMPLETE";


    // 系统消息模板提取为常量
    private static final String SYSTEM_MESSAGE_TEMPLATE = """
        你是严格的评估专家，专注于判断执行结果是否完全满足原始需求。

        核心职责：
        1. 深入分析原始用户请求的真实意图和要求
        2. 全面评估当前执行结果的完整性、准确性和质量
        3. 明确判断任务完成状态
        4. 提供具体、可操作的改进建议

        评估标准：
        - 功能完整性：是否实现了所有要求的功能
        - 结果准确性：输出是否正确和可靠
        - 质量标准：是否达到预期的质量水平
        - 用户满意度：是否真正解决了用户的问题

        输出格式：
        ## 评估结果
        [详细的评估分析]

        ## 完成状态
        如果任务完全完成：
        STATUS: COMPLETE

        ## 总结
        [执行结果的总结]

        如果任务未完成：
        STATUS: INCOMPLETE

        ## 问题分析
        [具体指出不满足要求的方面]

        ## 改进建议
        FEEDBACK: [详细、具体的改进建议和下一步行动指导]

        评估原则：
        - 严格但公正，不过度苛刻也不过度宽松
        - 基于客观事实进行判断
        - 提供建设性的反馈意见
        - 考虑任务的复杂性和实际可行性
        """;

    public static class Builder extends AbstractAgentExecutor.Builder<Builder> {

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
    }

    @Override
    public String execute(ToolExecutionRequest toolExecutionRequest, Object memoryId) {
        log.info("🚀🚀 ToolExecutionRequest:{}\n,memoryId:{}",toolExecutionRequest.toString(),memoryId);
        String result = super.execute(toolExecutionRequest, memoryId);
        log.info("ReflectionAgent.execute result: {}", result);
        return result;
    }
}
