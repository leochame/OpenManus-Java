package com.openmanus.java.agent.impl.thinker;

import com.openmanus.java.agent.base.AbstractAgentExecutor;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.SystemMessage;
import org.bsc.langgraph4j.GraphStateException;

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
public class ThinkingAgent extends AbstractAgentExecutor<ThinkingAgent.Builder> {
    
    public static class Builder extends AbstractAgentExecutor.Builder<Builder> {
        public ThinkingAgent build() throws GraphStateException {
            this.name("thinking_agent")
                .description("负责任务分析和规划的智能体")
                .singleParameter("用户请求和任务上下文")
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
        String thinkingResult;
        
        if (context != null) {
            // 来自AgentHandoff的调用，使用内部AgentExecutor处理
            thinkingResult = super.execute(request, context);
            
            // 更新状态
            Map<String, Object> state = (Map<String, Object>) context;
            state.put("execution_plan", thinkingResult);
            state.put("phase", "doing");
        } else {
            // 直接调用，从参数中解析必要信息
            // 这里只是模拟，实际实现需调用语言模型
            thinkingResult = "基于输入「" + request.arguments() + "」的规划结果";
        }
        
        return thinkingResult;
    }
}
