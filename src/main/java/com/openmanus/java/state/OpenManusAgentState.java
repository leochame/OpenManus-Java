package com.openmanus.java.state;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import dev.langchain4j.data.message.ChatMessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenManus Agent State - 基于 langgraph4j 的状态管理
 * 
 * 这个类定义了 Agent 在执行过程中需要维护的所有状态信息，
 * 包括消息历史、工具调用结果、当前任务状态等。
 */
public class OpenManusAgentState extends AgentState {
    
    // 状态字段的键名常量
    public static final String MESSAGES_KEY = "messages";
    public static final String CURRENT_TASK_KEY = "current_task";
    public static final String TOOL_CALLS_KEY = "tool_calls";
    public static final String EXECUTION_HISTORY_KEY = "execution_history";
    public static final String AGENT_STATUS_KEY = "agent_status";
    public static final String ERROR_INFO_KEY = "error_info";
    public static final String CURRENT_STEP_KEY = "current_step";
    public static final String CONVERSATION_BUFFER_KEY = "conversation_buffer";
    public static final String MEMORY_CONTEXT_KEY = "memory_context";
    
    /**
     * 定义状态的 Schema
     * 这个 Schema 定义了状态中每个字段的类型和更新策略
     */
    public static final Map<String, Channel<?>> SCHEMA = Map.of(
        // 消息列表 - 使用 appender 策略，新消息会被追加到列表中
        MESSAGES_KEY, Channels.appender(ArrayList::new),
        
        // 当前任务 - 使用 base 策略，新值会覆盖旧值
        CURRENT_TASK_KEY, Channels.base(() -> ""),
        
        // 工具调用列表 - 使用 appender 策略
        TOOL_CALLS_KEY, Channels.appender(ArrayList::new),
        
        // 执行历史 - 使用 appender 策略
        EXECUTION_HISTORY_KEY, Channels.appender(ArrayList::new),
        
        // Agent 状态 - 使用 base 策略，默认为 IDLE 状态的字符串表示
        AGENT_STATUS_KEY, Channels.base(() -> Status.IDLE.name()),
        
        // 错误信息 - 使用 base 策略
        ERROR_INFO_KEY, Channels.base(() -> ""),
        
        // 当前步骤 - 使用 base 策略
        CURRENT_STEP_KEY, Channels.base(() -> 0),
        
        // 对话缓冲区 - 使用 base 策略
        CONVERSATION_BUFFER_KEY, Channels.base(() -> ""),
        
        // 记忆上下文 - 使用 base 策略
        MEMORY_CONTEXT_KEY, Channels.base(() -> "")
    );
    
    /**
     * Agent 执行状态枚举
     */
    public enum Status {
        IDLE,           // 空闲状态
        THINKING,       // 思考中
        ACTING,         // 执行动作中
        OBSERVING,      // 观察结果中
        PLANNING,       // 规划中
        REFLECTING,     // 反思中
        FINISHED,       // 已完成
        ERROR           // 错误状态
    }
    
    /**
     * 工具调用信息
     */
    public static class ToolCall {
        private String toolName;
        private Map<String, Object> parameters;
        private String result;
        private boolean success;
        private String errorMessage;
        
        public ToolCall(String toolName, Map<String, Object> parameters) {
            this.toolName = toolName;
            this.parameters = parameters;
        }
        
        // Getters and setters
        public String getToolName() { return toolName; }
        public void setToolName(String toolName) { this.toolName = toolName; }
        
        public Map<String, Object> getParameters() { return parameters; }
        public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }
        
        public String getResult() { return result; }
        public void setResult(String result) { this.result = result; }
        
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    }
    
    /**
     * 执行步骤信息
     */
    public static class ExecutionStep {
        private String stepName;
        private String description;
        private long startTime;
        private long endTime;
        private boolean success;
        private String result;
        
        public ExecutionStep(String stepName, String description) {
            this.stepName = stepName;
            this.description = description;
            this.startTime = System.currentTimeMillis();
        }
        
        public void complete(boolean success, String result) {
            this.success = success;
            this.result = result;
            this.endTime = System.currentTimeMillis();
        }
        
        // Getters and setters
        public String getStepName() { return stepName; }
        public void setStepName(String stepName) { this.stepName = stepName; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public long getStartTime() { return startTime; }
        public void setStartTime(long startTime) { this.startTime = startTime; }
        
        public long getEndTime() { return endTime; }
        public void setEndTime(long endTime) { this.endTime = endTime; }
        
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        
        public String getResult() { return result; }
        public void setResult(String result) { this.result = result; }
        
        public long getDuration() { return endTime - startTime; }
    }
    
    /**
     * 构造函数
     */
    public OpenManusAgentState(Map<String, Object> initData) {
        super(initData);
    }
    
    public OpenManusAgentState() {
        super(new HashMap<>());
    }
    
    // 便捷方法来获取和设置状态值
    
    @SuppressWarnings("unchecked")
    public List<ChatMessage> getMessages() {
        return (List<ChatMessage>) data().getOrDefault(MESSAGES_KEY, new ArrayList<>());
    }
    
    public String getCurrentTask() {
        return (String) data().getOrDefault(CURRENT_TASK_KEY, "");
    }
    
    @SuppressWarnings("unchecked")
    public List<ToolCall> getToolCalls() {
        return (List<ToolCall>) data().getOrDefault(TOOL_CALLS_KEY, new ArrayList<>());
    }
    
    @SuppressWarnings("unchecked")
    public List<ExecutionStep> getExecutionHistory() {
        return (List<ExecutionStep>) data().getOrDefault(EXECUTION_HISTORY_KEY, new ArrayList<>());
    }
    

    
    public Status getAgentStatus() {
        Object statusObj = data().getOrDefault(AGENT_STATUS_KEY, Status.IDLE.name());
        if (statusObj instanceof Status) {
            return (Status) statusObj;
        } else if (statusObj instanceof String) {
            try {
                return Status.valueOf((String) statusObj);
            } catch (IllegalArgumentException e) {
                return Status.IDLE;
            }
        }
        return Status.IDLE;
    }
    
    public String getErrorInfo() {
        return (String) data().getOrDefault(ERROR_INFO_KEY, "");
    }
    

    
    public Integer getCurrentStep() {
        return (Integer) data().getOrDefault(CURRENT_STEP_KEY, 0);
    }
    
    public String getConversationBuffer() {
        return (String) data().getOrDefault(CONVERSATION_BUFFER_KEY, "");
    }
    
    public String getMemoryContext() {
        return (String) data().getOrDefault(MEMORY_CONTEXT_KEY, "");
    }
    
    // 用于更新状态的便捷方法（返回更新后的状态映射）
    public Map<String, Object> updateMessages(List<ChatMessage> messages) {
        return Map.of(MESSAGES_KEY, messages);
    }
    
    public Map<String, Object> updateCurrentTask(String task) {
        return Map.of(CURRENT_TASK_KEY, task);
    }
    
    public Map<String, Object> updateToolCalls(List<ToolCall> toolCalls) {
        return Map.of(TOOL_CALLS_KEY, toolCalls);
    }
    
    public Map<String, Object> updateExecutionHistory(List<ExecutionStep> history) {
        return Map.of(EXECUTION_HISTORY_KEY, history);
    }
    

    
    public Map<String, Object> updateAgentStatus(Status status) {
        return Map.of(AGENT_STATUS_KEY, status.name());
    }
    
    public Map<String, Object> updateErrorInfo(String errorInfo) {
        return Map.of(ERROR_INFO_KEY, errorInfo);
    }
    

    
    public Map<String, Object> updateCurrentStep(Integer step) {
        return Map.of(CURRENT_STEP_KEY, step);
    }
    
    public Map<String, Object> updateConversationBuffer(String buffer) {
        return Map.of(CONVERSATION_BUFFER_KEY, buffer);
    }
    
    public Map<String, Object> updateMemoryContext(String context) {
        return Map.of(MEMORY_CONTEXT_KEY, context);
    }
    
    /**
     * 获取状态的字符串表示，用于调试
     */
    @Override
    public String toString() {
        return String.format("OpenManusAgentState{status=%s, task='%s', messages=%d, toolCalls=%d, steps=%d}", 
            getAgentStatus(), getCurrentTask(), getMessages().size(), 
            getToolCalls().size(), getExecutionHistory().size());
    }
} 