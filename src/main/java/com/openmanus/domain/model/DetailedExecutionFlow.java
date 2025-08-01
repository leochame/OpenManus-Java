package com.openmanus.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 详细执行流程模型
 * 用于可视化展示Agent执行的完整流程，包括思考、执行、反思等各个阶段
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DetailedExecutionFlow {
    
    /**
     * 会话ID
     */
    private String sessionId;
    
    /**
     * 用户原始输入
     */
    private String userInput;
    
    /**
     * 工作流开始时间
     */
    private LocalDateTime startTime;
    
    /**
     * 工作流结束时间
     */
    private LocalDateTime endTime;
    
    /**
     * 总执行时长（毫秒）
     */
    private Long totalDuration;
    
    /**
     * 工作流状态
     */
    private WorkflowStatus status;
    
    /**
     * 最终结果
     */
    private String finalResult;
    
    /**
     * 执行阶段列表
     */
    private List<ExecutionPhase> phases;
    
    /**
     * 错误信息（如果有）
     */
    private String error;
    
    /**
     * 工作流状态枚举
     */
    public enum WorkflowStatus {
        RUNNING,        // 运行中
        COMPLETED,      // 已完成
        FAILED,         // 失败
        CANCELLED       // 已取消
    }
    
    /**
     * 执行阶段模型
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExecutionPhase {
        
        /**
         * 阶段ID
         */
        private String phaseId;
        
        /**
         * 阶段名称
         */
        private String phaseName;
        
        /**
         * 阶段类型
         */
        private PhaseType phaseType;
        
        /**
         * 执行的Agent名称
         */
        private String agentName;
        
        /**
         * Agent类型
         */
        private String agentType;
        
        /**
         * 阶段开始时间
         */
        private LocalDateTime startTime;
        
        /**
         * 阶段结束时间
         */
        private LocalDateTime endTime;
        
        /**
         * 阶段持续时间（毫秒）
         */
        private Long duration;
        
        /**
         * 阶段状态
         */
        private PhaseStatus status;
        
        /**
         * 输入数据
         */
        private Object input;
        
        /**
         * 输出数据
         */
        private Object output;
        
        /**
         * LLM交互记录
         */
        private List<LLMInteraction> llmInteractions;
        
        /**
         * 工具调用记录
         */
        private List<ToolCall> toolCalls;
        
        /**
         * 错误信息
         */
        private String error;
        
        /**
         * 阶段元数据
         */
        private Map<String, Object> metadata;
    }
    
    /**
     * 阶段类型枚举
     */
    public enum PhaseType {
        THINKING,       // 思考阶段
        EXECUTION,      // 执行阶段
        REFLECTION,     // 反思阶段
        TOOL_USAGE,     // 工具使用
        DECISION        // 决策阶段
    }
    
    /**
     * 阶段状态枚举
     */
    public enum PhaseStatus {
        PENDING,        // 等待中
        RUNNING,        // 运行中
        COMPLETED,      // 已完成
        FAILED,         // 失败
        SKIPPED         // 跳过
    }
    
    /**
     * LLM交互记录
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LLMInteraction {
        
        /**
         * 交互ID
         */
        private String interactionId;
        
        /**
         * 请求时间
         */
        private LocalDateTime requestTime;
        
        /**
         * 响应时间
         */
        private LocalDateTime responseTime;
        
        /**
         * 请求内容
         */
        private String request;
        
        /**
         * 响应内容
         */
        private String response;
        
        /**
         * 使用的模型
         */
        private String model;
        
        /**
         * Token使用情况
         */
        private TokenUsage tokenUsage;
        
        /**
         * 响应时长（毫秒）
         */
        private Long responseTime_ms;
    }
    
    /**
     * 工具调用记录
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolCall {
        
        /**
         * 工具调用ID
         */
        private String callId;
        
        /**
         * 工具名称
         */
        private String toolName;
        
        /**
         * 调用时间
         */
        private LocalDateTime callTime;
        
        /**
         * 完成时间
         */
        private LocalDateTime completionTime;
        
        /**
         * 调用参数
         */
        private Object parameters;
        
        /**
         * 调用结果
         */
        private Object result;
        
        /**
         * 调用状态
         */
        private String status;
        
        /**
         * 错误信息（如果有）
         */
        private String error;
        
        /**
         * 执行时长（毫秒）
         */
        private Long duration;
    }
    
    /**
     * Token使用情况
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TokenUsage {
        
        /**
         * 输入Token数
         */
        private Integer inputTokens;
        
        /**
         * 输出Token数
         */
        private Integer outputTokens;
        
        /**
         * 总Token数
         */
        private Integer totalTokens;
    }
}
