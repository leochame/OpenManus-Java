package com.openmanus.java.agent.impl.reflection;

import com.openmanus.java.agent.base.AbstractAgentExecutor;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.SystemMessage;
import org.bsc.langgraph4j.GraphStateException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 反思智能体 - 负责评估执行结果并决定是否需要继续循环
 * 
 * 核心功能：
 * 1. 评估执行结果与原始需求的匹配程度
 * 2. 判断任务是否完成
 * 3. 提供具体的改进建议
 * 4. 控制循环执行流程
 */
public class ReflectionAgent extends AbstractAgentExecutor<ReflectionAgent.Builder> {
    
    // 状态常量
    private static final String STATUS_COMPLETE = "STATUS: COMPLETE";
    private static final String STATUS_INCOMPLETE = "STATUS: INCOMPLETE";
    
    public static class Builder extends AbstractAgentExecutor.Builder<Builder> {
        public ReflectionAgent build() throws GraphStateException {
            this.name("reflection_agent")
                .description("负责评估执行结果并决定是否需要继续循环的智能体")
                .singleParameter("执行结果和任务上下文")
                .systemMessage(SystemMessage.from("""
                    你是一位严格的评估专家，你的核心任务是判断执行结果是否完全满足原始需求。
                    
                    评估流程：
                    1. 仔细对比原始用户请求与当前执行结果
                    2. 全面评估结果的完整性、准确性和质量
                    3. 明确判断任务是否完成
                    
                    如果任务完全完成（所有需求都已满足）：
                    - 总结执行结果
                    - 输出必须包含标记："STATUS: COMPLETE"
                    
                    如果任务未完成（任何方面不满足需求）：
                    - 具体指出哪些方面不满足要求
                    - 提供详细、具体的改进建议
                    - 输出必须包含标记："STATUS: INCOMPLETE"
                    - 在"FEEDBACK:"部分提供给下一轮规划的具体指导
                    
                    请注意：你的判断将决定系统是否进行下一轮迭代，务必谨慎评估！
                    """));
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
    public String execute(ToolExecutionRequest request, Object context) {
        // 如果是来自AgentHandoff的调用，context是AgentExecutor.State
        // 如果是来自AgentToolbox的调用，context为null，需要从请求参数中提取必要信息
        
        String originalRequest;
        String executionResult;
        int cycleCount;
        Map<String, Object> state = null;
        
        if (context != null) {
            // 来自AgentHandoff的调用
            state = (Map<String, Object>) context;
            originalRequest = (String) state.get("original_request");
            executionResult = request.arguments(); // 从请求中获取
            cycleCount = (int) state.getOrDefault("cycle_count", 0);
        } else {
            // 来自AgentToolbox的调用，从参数中解析
            String args = request.arguments();
            // 假设参数格式为 JSON 字符串
            try {
                // 使用简单的字符串解析，实际应使用JSON解析
                originalRequest = extractField(args, "original_request");
                executionResult = extractField(args, "execution_result");
                cycleCount = Integer.parseInt(extractField(args, "cycle_count", "0"));
            } catch (Exception e) {
                return "参数格式错误，无法解析请求。";
            }
        }
        
        // 构建评估提示
        String evaluationPrompt = String.format(
            "原始请求: %s\n\n当前执行结果: %s\n\n这是第%d轮执行。请评估结果是否完全满足原始需求。",
            originalRequest, executionResult, cycleCount + 1
        );
        
        // 调用基础模型进行评估
        String evaluationResult;
        if (context != null) {
            // 使用内部的AgentExecutor处理
            ToolExecutionRequest evaluationRequest = ToolExecutionRequest.builder()
                    .name("evaluate")
                    .arguments(evaluationPrompt)
                    .build();
            evaluationResult = super.execute(evaluationRequest, context);
        } else {
            // 直接构造结果 (实际实现需要调用语言模型)
            evaluationResult = "模拟评估结果";
        }
        
        // 处理评估结果
        boolean isComplete = evaluationResult.contains(STATUS_COMPLETE);
        
        // 更新状态（如果有）
        if (state != null) {
            if (isComplete) {
                state.put("phase", "completed");
                state.put("final_result", extractFinalResult(evaluationResult));
            } else {
                String feedback = extractFeedback(evaluationResult);
                state.put("phase", "thinking");
                state.put("cycle_count", cycleCount + 1);
                state.put("feedback", feedback);
                
                // 记录本轮执行历史
                List<Map<String, Object>> history = (List<Map<String, Object>>) 
                    state.getOrDefault("execution_history", new ArrayList<>());
                
                Map<String, Object> currentExecution = new HashMap<>();
                currentExecution.put("cycle", cycleCount);
                currentExecution.put("result", executionResult);
                currentExecution.put("evaluation", evaluationResult);
                history.add(currentExecution);
                
                state.put("execution_history", history);
            }
        }
        
        // 返回评估结果
        if (isComplete) {
            return "任务已完成: " + extractFinalResult(evaluationResult);
        } else {
            String feedback = extractFeedback(evaluationResult);
            return "任务未完成，需要进一步改进: " + feedback;
        }
    }
    
    // 辅助方法
    private String extractField(String json, String fieldName) {
        return extractField(json, fieldName, "");
    }
    
    private String extractField(String json, String fieldName, String defaultValue) {
        // 简单实现，实际应使用JSON解析
        int startIndex = json.indexOf("\"" + fieldName + "\":");
        if (startIndex == -1) return defaultValue;
        
        startIndex += fieldName.length() + 3;
        int endIndex = json.indexOf("\",", startIndex);
        if (endIndex == -1) endIndex = json.indexOf("\"}", startIndex);
        if (endIndex == -1) return defaultValue;
        
        return json.substring(startIndex, endIndex);
    }
    
    private String extractFinalResult(String evaluationResult) {
        // 提取最终结果的逻辑
        int summaryIndex = evaluationResult.indexOf("SUMMARY:");
        if (summaryIndex != -1) {
            return evaluationResult.substring(summaryIndex + 8).trim();
        }
        return evaluationResult;
    }
    
    private String extractFeedback(String evaluationResult) {
        // 提取反馈的逻辑
        int feedbackIndex = evaluationResult.indexOf("FEEDBACK:");
        if (feedbackIndex != -1) {
            return evaluationResult.substring(feedbackIndex + 9).trim();
        }
        
        // 如果没有明确标记，尝试提取STATUS: INCOMPLETE后的内容
        int incompleteIndex = evaluationResult.indexOf(STATUS_INCOMPLETE);
        if (incompleteIndex != -1) {
            return evaluationResult.substring(incompleteIndex + STATUS_INCOMPLETE.length()).trim();
        }
        
        return evaluationResult;
    }
}
