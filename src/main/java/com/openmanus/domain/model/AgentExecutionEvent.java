package com.openmanus.domain.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Agent执行事件模型
 * 用于跟踪和展示Agent的执行状态
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentExecutionEvent {
    
    // 使用静态ObjectMapper避免每次序列化都创建新的实例
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 执行会话ID
     */
    private String sessionId;
    
    /**
     * 事件ID
     */
    private String eventId;
    
    /**
     * Agent名称
     */
    private String agentName;
    
    /**
     * Agent类型
     */
    private String agentType;
    
    /**
     * 事件类型
     */
    private EventType eventType;
    
    /**
     * 执行状态
     */
    private ExecutionStatus status;
    
    /**
     * 输入数据
     * 被序列化为字符串以确保WebSocket传输的稳定性
     */
    private Object input;
    
    /**
     * 输出数据
     * 被序列化为字符串以确保WebSocket传输的稳定性
     */
    private Object output;
    
    /**
     * 错误信息
     */
    private String error;
    
    /**
     * 执行开始时间
     */
    private LocalDateTime startTime;
    
    /**
     * 执行结束时间
     */
    private LocalDateTime endTime;
    
    /**
     * 执行耗时（毫秒）
     */
    private Long duration;
    
    /**
     * 额外的元数据
     */
    private Map<String, Object> metadata;
    
    /**
     * 安全地设置输入，确保它可以序列化
     */
    public void setInput(Object input) {
        if (input == null) {
            this.input = null;
            return;
        }
        
        // 如果是String、Number或Boolean类型，直接设置
        if (input instanceof String || input instanceof Number || input instanceof Boolean) {
            this.input = input;
        } else {
            // 尝试将复杂对象转换为字符串表示
            try {
                // 如果已经是字符串表示，则不需要再转换
                if (input instanceof String) {
                    this.input = input;
                } else {
                    // 将对象转换为JSON字符串
                    this.input = objectMapper.writeValueAsString(input);
                }
            } catch (Exception e) {
                // 如果序列化失败，则保存对象的简单字符串表示
                this.input = input.toString();
            }
        }
    }
    
    /**
     * 安全地设置输出，确保它可以序列化
     */
    public void setOutput(Object output) {
        if (output == null) {
            this.output = null;
            return;
        }
        
        // 如果是String、Number或Boolean类型，直接设置
        if (output instanceof String || output instanceof Number || output instanceof Boolean) {
            this.output = output;
        } else {
            // 尝试将复杂对象转换为字符串表示
            try {
                // 如果已经是字符串表示，则不需要再转换
                if (output instanceof String) {
                    this.output = output;
                } else {
                    // 将对象转换为JSON字符串
                    this.output = objectMapper.writeValueAsString(output);
                }
            } catch (Exception e) {
                // 如果序列化失败，则保存对象的简单字符串表示
                this.output = output.toString();
            }
        }
    }
    
    /**
     * 事件类型枚举
     */
    public enum EventType {
        AGENT_START,        // Agent开始执行
        AGENT_END,          // Agent执行结束
        TOOL_CALL_START,    // 工具调用开始
        TOOL_CALL_END,      // 工具调用结束
        TOOL_CALL,          // 工具调用（通用）
        THINKING_START,     // 思考开始
        THINKING_END,       // 思考结束
        REFLECTION_START,   // 反思开始
        REFLECTION_END,     // 反思结束
        EXECUTION_START,    // 执行开始
        EXECUTION_END,      // 执行结束
        LLM_REQUEST,        // LLM请求
        LLM_RESPONSE,       // LLM响应
        ERROR,              // 错误事件
        HANDOFF,            // Agent交接
        STEP_START,         // 执行步骤开始
        STEP_END,           // 执行步骤结束
        DECISION_POINT,     // 决策点
        WORKFLOW_START,     // 工作流开始
        WORKFLOW_END,       // 工作流结束
        INTERMEDIATE_RESULT // 中间结果
    }
    
    /**
     * 执行状态枚举
     */
    public enum ExecutionStatus {
        PENDING,            // 等待执行
        RUNNING,            // 正在执行
        SUCCESS,            // 执行成功
        FAILED,             // 执行失败
        CANCELLED,          // 执行取消
        ERROR,              // 执行错误
        TIMEOUT             // 执行超时
    }
    
    /**
     * 计算执行耗时
     */
    public void calculateDuration() {
        if (startTime != null && endTime != null) {
            this.duration = java.time.Duration.between(startTime, endTime).toMillis();
        }
    }
    
    /**
     * 创建Agent开始事件
     */
    public static AgentExecutionEvent createStartEvent(String sessionId, String agentName, String agentType, Object input) {
        AgentExecutionEvent event = AgentExecutionEvent.builder()
                .sessionId(sessionId)
                .eventId(java.util.UUID.randomUUID().toString())
                .agentName(agentName)
                .agentType(agentType)
                .eventType(EventType.AGENT_START)
                .status(ExecutionStatus.RUNNING)
                .startTime(LocalDateTime.now())
                .build();
                
        // 安全地设置输入
        event.setInput(input);
        
        return event;
    }
    
    /**
     * 创建Agent结束事件
     */
    public static AgentExecutionEvent createEndEvent(String sessionId, String agentName, String agentType, Object output, ExecutionStatus status) {
        AgentExecutionEvent event = AgentExecutionEvent.builder()
                .sessionId(sessionId)
                .eventId(java.util.UUID.randomUUID().toString())
                .agentName(agentName)
                .agentType(agentType)
                .eventType(EventType.AGENT_END)
                .status(status)
                .endTime(LocalDateTime.now())
                .build();
                
        // 安全地设置输出
        event.setOutput(output);
        
        return event;
    }
    
    /**
     * 创建错误事件
     */
    public static AgentExecutionEvent createErrorEvent(String sessionId, String agentName, String agentType, String error) {
        return AgentExecutionEvent.builder()
                .sessionId(sessionId)
                .eventId(java.util.UUID.randomUUID().toString())
                .agentName(agentName)
                .agentType(agentType)
                .eventType(EventType.ERROR)
                .status(ExecutionStatus.FAILED)
                .error(error)
                .endTime(LocalDateTime.now())
                .build();
    }
}
