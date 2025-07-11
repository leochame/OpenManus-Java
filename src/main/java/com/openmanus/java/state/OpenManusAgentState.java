package com.openmanus.java.state;

import dev.langchain4j.data.message.ChatMessage;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenManus Agent State - 扩展AgentState以支持React框架状态管理
 * 
 * 基于langgraph4j的StateGraph架构，管理Agent的完整状态：
 * - React步骤跟踪 (思考、行动、观察、反思)
 * - 工具调用历史
 * - 反思机制数据
 * - 迭代控制参数
 */
public class OpenManusAgentState extends AgentState {
    
    // 状态属性键名
    public static final String USER_INPUT = "user_input";
    public static final String MESSAGES = "messages";
    public static final String TOOL_CALLS = "tool_calls";
    public static final String OBSERVATIONS = "observations";
    public static final String THOUGHTS = "thoughts";
    public static final String REFLECTIONS = "reflections";
    public static final String INTERMEDIATE_STEPS = "intermediate_steps";
    public static final String CURRENT_STEP = "current_step";
    public static final String MEMORY_CONTEXT = "memory_context";
    public static final String ITERATION_COUNT = "iteration_count";
    public static final String MAX_ITERATIONS = "max_iterations";
    public static final String LAST_ACTION = "last_action";
    public static final String NEXT_ACTION = "next_action";
    public static final String START_TIME = "start_time";
    public static final String END_TIME = "end_time";
    public static final String FINAL_ANSWER = "final_answer";
    public static final String ERROR = "error";
    public static final String TASK_ID = "task_id";
    public static final String SESSION_ID = "session_id";
    public static final String METADATA = "metadata";
    public static final String REASONING_STEPS = "reasoning_steps";
    
    // 状态Schema定义 - 定义每个属性的Channel行为
    public static final Map<String, Channel<?>> SCHEMA = createSchema();
    
    private static Map<String, Channel<?>> createSchema() {
        Map<String, Channel<?>> schema = new HashMap<>();
        schema.put(USER_INPUT, Channels.base(() -> ""));
        schema.put(MESSAGES, Channels.appender(ArrayList::new));
        schema.put(TOOL_CALLS, Channels.appender(ArrayList::new));
        schema.put(OBSERVATIONS, Channels.appender(ArrayList::new));
        schema.put(THOUGHTS, Channels.base(() -> ""));
        schema.put(REFLECTIONS, Channels.appender(ArrayList::new));
        schema.put(INTERMEDIATE_STEPS, Channels.appender(ArrayList::new));
        schema.put(CURRENT_STEP, Channels.base(() -> ""));
        schema.put(MEMORY_CONTEXT, Channels.base(() -> new HashMap<String, Object>()));
        schema.put(ITERATION_COUNT, Channels.base(() -> Integer.valueOf(0)));
        schema.put(MAX_ITERATIONS, Channels.base(() -> Integer.valueOf(10)));
        schema.put(LAST_ACTION, Channels.base(() -> ""));
        schema.put(NEXT_ACTION, Channels.base(() -> ""));
        schema.put(START_TIME, Channels.base(() -> LocalDateTime.now()));
        schema.put(END_TIME, Channels.base(() -> LocalDateTime.now()));
        schema.put(FINAL_ANSWER, Channels.base(() -> ""));
        schema.put(ERROR, Channels.base(() -> ""));
        schema.put(TASK_ID, Channels.base(() -> ""));
        schema.put(SESSION_ID, Channels.base(() -> ""));
        schema.put(METADATA, Channels.base(() -> new HashMap<String, Object>()));
        schema.put(REASONING_STEPS, Channels.appender(ArrayList::new));
        return schema;
    }
    
    /**
     * 构造函数
     * @param initData 初始状态数据
     */
    public OpenManusAgentState(Map<String, Object> initData) {
        super(initData);
    }
    
    // Getter方法 - 使用Optional处理
    
    public String getUserInput() {
        return this.<String>value(USER_INPUT).orElse("");
    }
    
    public String getCurrentThought() {
        return this.<String>value(THOUGHTS).orElse("");
    }
    
    @SuppressWarnings("unchecked")
    public List<ChatMessage> getMessages() {
        return this.<List<ChatMessage>>value(MESSAGES).orElse(new ArrayList<>());
    }
    
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getToolCalls() {
        return this.<List<Map<String, Object>>>value(TOOL_CALLS).orElse(new ArrayList<>());
    }
    
    @SuppressWarnings("unchecked")
    public List<String> getObservations() {
        return this.<List<String>>value(OBSERVATIONS).orElse(new ArrayList<>());
    }
    
    @SuppressWarnings("unchecked")
    public List<String> getReflections() {
        return this.<List<String>>value(REFLECTIONS).orElse(new ArrayList<>());
    }
    
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getIntermediateSteps() {
        return this.<List<Map<String, Object>>>value(INTERMEDIATE_STEPS).orElse(new ArrayList<>());
    }
    
    public String getCurrentStep() {
        return this.<String>value(CURRENT_STEP).orElse("");
    }
    
    public int getIterationCount() {
        return this.<Integer>value(ITERATION_COUNT).orElse(0);
    }
    
    public int getMaxIterations() {
        return this.<Integer>value(MAX_ITERATIONS).orElse(10);
    }
    
    public String getLastAction() {
        return this.<String>value(LAST_ACTION).orElse("");
    }
    
    public String getNextAction() {
        return this.<String>value(NEXT_ACTION).orElse("");
    }
    
    @SuppressWarnings("unchecked")
    public Map<String, Object> getMemoryContext() {
        return this.<Map<String, Object>>value(MEMORY_CONTEXT).orElse(new HashMap<>());
    }
    
    public LocalDateTime getStartTime() {
        return this.<LocalDateTime>value(START_TIME).orElse(LocalDateTime.now());
    }
    
    public LocalDateTime getEndTime() {
        return this.<LocalDateTime>value(END_TIME).orElse(LocalDateTime.now());
    }
    
    // 新增的getter方法 - 用于兼容性
    public String getFinalAnswer() {
        return this.<String>value(FINAL_ANSWER).orElse("");
    }
    
    public String getError() {
        return this.<String>value(ERROR).orElse("");
    }
    
    public boolean hasError() {
        String error = getError();
        return error != null && !error.trim().isEmpty();
    }
    
    public boolean isMaxIterationsReached() {
        return hasReachedMaxIterations();
    }
    
    public String getCurrentState() {
        return getCurrentStep();
    }
    
    public String getTaskId() {
        return this.<String>value(TASK_ID).orElse("");
    }
    
    public String getSessionId() {
        return this.<String>value(SESSION_ID).orElse("");
    }
    
    @SuppressWarnings("unchecked")
    public Map<String, Object> getMetadata() {
        return this.<Map<String, Object>>value(METADATA).orElse(new HashMap<>());
    }
    
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getReasoningSteps() {
        return this.<List<Map<String, Object>>>value(REASONING_STEPS).orElse(new ArrayList<>());
    }
    
    // 便利方法
    
    /**
     * 检查是否达到最大迭代次数
     */
    public boolean hasReachedMaxIterations() {
        return getIterationCount() >= getMaxIterations();
    }
    
    /**
     * 获取当前React循环的状态摘要
     */
    public String getReactSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("=== React状态摘要 ===\n");
        summary.append("当前步骤: ").append(getCurrentStep()).append("\n");
        summary.append("迭代次数: ").append(getIterationCount()).append("/").append(getMaxIterations()).append("\n");
        summary.append("最后行动: ").append(getLastAction()).append("\n");
        summary.append("下一行动: ").append(getNextAction()).append("\n");
        summary.append("思考记录: ").append(getCurrentThought()).append("\n");
        summary.append("工具调用数: ").append(getToolCalls().size()).append("\n");
        summary.append("观察记录数: ").append(getObservations().size()).append("\n");
        summary.append("反思记录数: ").append(getReflections().size()).append("\n");
        return summary.toString();
    }
    
    /**
     * 创建状态更新Map - 用于在节点中返回状态更新
     */
    public static Map<String, Object> updateUserInput(String userInput) {
        return Map.of(USER_INPUT, userInput);
    }
    
    public static Map<String, Object> updateThought(String thought) {
        return Map.of(THOUGHTS, thought);
    }
    
    public static Map<String, Object> addMessage(ChatMessage message) {
        return Map.of(MESSAGES, message);
    }
    
    public static Map<String, Object> addToolCall(Map<String, Object> toolCall) {
        return Map.of(TOOL_CALLS, toolCall);
    }
    
    public static Map<String, Object> addObservation(String observation) {
        return Map.of(OBSERVATIONS, observation);
    }
    
    public static Map<String, Object> addReflection(String reflection) {
        return Map.of(REFLECTIONS, reflection);
    }
    
    public static Map<String, Object> addIntermediateStep(Map<String, Object> step) {
        return Map.of(INTERMEDIATE_STEPS, step);
    }
    
    public static Map<String, Object> updateCurrentStep(String step) {
        return Map.of(CURRENT_STEP, step);
    }
    
    public static Map<String, Object> incrementIteration(int currentCount) {
        return Map.of(ITERATION_COUNT, currentCount + 1);
    }
    
    public static Map<String, Object> updateMaxIterations(int maxIterations) {
        return Map.of(MAX_ITERATIONS, maxIterations);
    }
    
    public static Map<String, Object> updateLastAction(String action) {
        return Map.of(LAST_ACTION, action);
    }
    
    public static Map<String, Object> updateNextAction(String action) {
        return Map.of(NEXT_ACTION, action);
    }
    
    public static Map<String, Object> updateMemoryContext(Map<String, Object> context) {
        return Map.of(MEMORY_CONTEXT, context);
    }
    
    public static Map<String, Object> updateStartTime(LocalDateTime startTime) {
        return Map.of(START_TIME, startTime);
    }
    
    public static Map<String, Object> updateEndTime(LocalDateTime endTime) {
        return Map.of(END_TIME, endTime);
    }
    
    // 新增的静态更新方法
    public static Map<String, Object> updateFinalAnswer(String finalAnswer) {
        return Map.of(FINAL_ANSWER, finalAnswer);
    }
    
    public static Map<String, Object> updateError(String error) {
        return Map.of(ERROR, error);
    }
    
    public static Map<String, Object> updateTaskId(String taskId) {
        return Map.of(TASK_ID, taskId);
    }
    
    public static Map<String, Object> updateSessionId(String sessionId) {
        return Map.of(SESSION_ID, sessionId);
    }
    
    public static Map<String, Object> updateMetadata(Map<String, Object> metadata) {
        return Map.of(METADATA, metadata);
    }
    
    public static Map<String, Object> addReasoningStep(Map<String, Object> step) {
        return Map.of(REASONING_STEPS, step);
    }
    
    /**
     * 创建组合状态更新
     */
    public static Map<String, Object> createUpdate(Map<String, Object>... updates) {
        Map<String, Object> result = new HashMap<>();
        for (Map<String, Object> update : updates) {
            result.putAll(update);
        }
        return result;
    }
} 