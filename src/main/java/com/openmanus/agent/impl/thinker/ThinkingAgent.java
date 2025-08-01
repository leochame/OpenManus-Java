package com.openmanus.agent.impl.thinker;

import com.openmanus.agent.base.AbstractAgentExecutor;
import com.openmanus.infra.monitoring.AgentExecutionTracker;
import com.openmanus.domain.model.DetailedExecutionFlow;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.SystemMessage;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.GraphStateException;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

/**
 * 思考智能体 - 负责任务分析和规划
 * 
 * 核心功能：
 * 1. 分析用户请求，理解真正的需求
 * 2. 将复杂任务分解为清晰的步骤
 * 3. 制定详细的执行计划
 * 4. 接收反思反馈并调整规划
 */
@Slf4j
public class ThinkingAgent extends AbstractAgentExecutor<ThinkingAgent.Builder> {

    @Autowired
    private AgentExecutionTracker executionTracker;
    
    public static class Builder extends AbstractAgentExecutor.Builder<Builder> {
        public ThinkingAgent build() throws GraphStateException {
            this.name("thinking_agent")
                .description("当用户提出新任务或需要重新规划时，使用此工具进行任务分析和制定执行计划。适用于：分析复杂任务、制定执行步骤、重新规划策略")
                .singleParameter("用户请求或需要重新规划的任务描述")
                .systemMessage(SystemMessage.from("""
                    你是规划专家，负责：
                    1. 分析用户请求，理解真正的需求
                    2. 将复杂任务分解为清晰的步骤
                    3. 制定详细的执行计划
                    
                    如果这是第一轮规划，你需要从零开始分析任务。
                    如果这是后续轮次，你将收到前一轮的反思反馈。请仔细分析反馈，调整你的规划。
                    
                    输出必须包括：
                    1. 任务分析：对请求的理解
                    2. 执行步骤：清晰的步骤列表
                    3. 具体执行计划：详细的操作指南
                    
                    请确保你的规划具体、可执行，并考虑可能的执行障碍。
                    """));
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
    public String execute(ToolExecutionRequest request, Object context) {
        log.info("🚀🚀🚀 ThinkingAgent.execute 被调用了！🚀🚀🚀");
        log.info("ThinkingAgent Request: {}", request.arguments());
        log.info("ThinkingAgent Context: {}", context != null ? context.toString() : "null");

        // 生成会话ID用于跟踪
        String sessionId = generateSessionId(context);

        // 开始思考阶段跟踪
        if (executionTracker != null) {
            executionTracker.startPhaseTracking(
                sessionId,
                "思考阶段",
                DetailedExecutionFlow.PhaseType.THINKING,
                "thinking_agent",
                "ThinkingAgent",
                request.arguments()
            );
        }

        String thinkingResult = ""; // 初始化为空字符串
        boolean success = true;
        String error = null;

        try {
            if (context != null) {
                log.info("🚀🚀🚀 ThinkingAgent -> LLM调用开始！🚀🚀🚀");
                thinkingResult = super.execute(request, context);
                log.info("大模型调用结果：——————————：{}",thinkingResult);
                // 更新状态
                Map<String, Object> state = (Map<String, Object>) context;
                state.put("execution_plan", thinkingResult);
                state.put("phase", "doing");
            } else {
                // 直接调用，从参数中解析必要信息
                // 这里只是模拟，实际实现需调用语言模型
                thinkingResult = "基于输入「" + request.arguments() + "」的规划结果";
            }
        } catch (Exception e) {
            success = false;
            error = e.getMessage();
            thinkingResult = "思考过程中发生错误: " + e.getMessage();
            log.error("ThinkingAgent execution failed", e);
        } finally {
            // 结束思考阶段跟踪
            if (executionTracker != null) {
                executionTracker.endPhaseTracking(sessionId, thinkingResult, success, error);
            }
        }

        log.info("ThinkingAgent Result:{}",thinkingResult);

        return thinkingResult;
    }

    /**
     * 生成或提取会话ID
     */
    private String generateSessionId(Object context) {
        if (context instanceof Map) {
            Map<String, Object> state = (Map<String, Object>) context;
            Object sessionId = state.get("sessionId");
            if (sessionId != null) {
                return sessionId.toString();
            }
        }
        // 如果没有现有的会话ID，生成一个新的
        return "thinking-session-" + System.currentTimeMillis();
    }
}
