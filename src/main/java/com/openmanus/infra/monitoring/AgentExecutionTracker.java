package com.openmanus.infra.monitoring;

import com.openmanus.domain.model.AgentExecutionEvent;
import com.openmanus.domain.model.DetailedExecutionFlow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Agent 执行跟踪服务（观察者模式）
 * 
 * 核心职责：
 * 1. 跟踪 Agent 执行事件（开始、结束、错误）
 * 2. 记录工具调用
 * 3. 管理详细执行流程
 * 4. 提供事件监听机制
 * 5. 提供统计查询接口
 * 
 * 设计模式：
 * - 观察者模式：支持多个监听器订阅执行事件
 * - 单例模式：作为 Spring Service Bean 存在
 */
@Service
@Slf4j
public class AgentExecutionTracker {
    
    // ==================== 数据存储 ====================
    
    private final Map<String, List<AgentExecutionEvent>> sessionEvents = new ConcurrentHashMap<>();
    private final Map<String, AgentExecutionEvent> activeAgents = new ConcurrentHashMap<>();
    private final Map<String, DetailedExecutionFlow> detailedFlows = new ConcurrentHashMap<>();
    private final Map<String, DetailedExecutionFlow.ExecutionPhase> currentPhases = new ConcurrentHashMap<>();
    private final List<AgentExecutionEventListener> listeners = new CopyOnWriteArrayList<>();
    
    // ==================== 核心事件追踪方法 ====================
    
    /**
     * 开始跟踪 Agent 执行
     */
    public void startAgentExecution(String sessionId, String agentName, String agentType, Object input) {
        AgentExecutionEvent event = AgentExecutionEvent.createStartEvent(sessionId, agentName, agentType, input);
        activeAgents.put(sessionId, event);
        recordEvent(sessionId, event);
        log.info("Agent started - Session: {}, Agent: {}, Type: {}", sessionId, agentName, agentType);
    }
    
    /**
     * 结束 Agent 执行
     */
    public void endAgentExecution(String sessionId, String agentName, String agentType, 
                                   Object output, AgentExecutionEvent.ExecutionStatus status) {
        AgentExecutionEvent event = AgentExecutionEvent.createEndEvent(sessionId, agentName, agentType, output, status);
        
        // 计算执行时间
        AgentExecutionEvent startEvent = activeAgents.get(sessionId);
        if (startEvent != null) {
            event.setStartTime(startEvent.getStartTime());
            event.calculateDuration();
        }
        
        activeAgents.remove(sessionId);
        recordEvent(sessionId, event);
        log.info("Agent ended - Session: {}, Agent: {}, Status: {}, Duration: {}ms", 
                sessionId, agentName, status, event.getDuration());
    }
    
    /**
     * 记录 Agent 执行错误
     */
    public void recordAgentError(String sessionId, String agentName, String agentType, String error) {
        AgentExecutionEvent event = AgentExecutionEvent.createErrorEvent(sessionId, agentName, agentType, error);
        activeAgents.remove(sessionId);
        recordEvent(sessionId, event);
        log.error("Agent error - Session: {}, Agent: {}, Error: {}", sessionId, agentName, error);
    }
    
    // ==================== 工具调用追踪 ====================
    
    /**
     * 记录工具调用（简化版）
     */
    public void recordToolCall(String sessionId, String agentName, String toolName, Object input, Object output) {
        recordToolCall(sessionId, agentName, toolName, input, output, true, null, 0);
    }

    /**
     * 记录工具调用（完整版）
     */
    public void recordToolCall(String sessionId, String agentName, String toolName, Object input, Object output, 
                              boolean success, String error, long durationMs) {
        LocalDateTime callTime = LocalDateTime.now().minusNanos(durationMs * 1_000_000);
        LocalDateTime completionTime = LocalDateTime.now();
        
        AgentExecutionEvent event = AgentExecutionEvent.builder()
                .sessionId(sessionId)
                .eventId(UUID.randomUUID().toString())
                .agentName(agentName)
                .agentType("TOOL_CALL")
                .eventType(success ? AgentExecutionEvent.EventType.TOOL_CALL_END : AgentExecutionEvent.EventType.ERROR)
                .status(success ? AgentExecutionEvent.ExecutionStatus.SUCCESS : AgentExecutionEvent.ExecutionStatus.FAILED)
                .startTime(callTime)
                .endTime(completionTime)
                .error(error)
                .metadata(Map.of("toolName", toolName))
                .build();

        event.setInput(input);
        event.setOutput(output);
        event.calculateDuration();
        recordEvent(sessionId, event);

        // 同步到详细执行流程
        addToolCallToPhase(sessionId, toolName, callTime, completionTime, input, output, success, error, durationMs);
        
        log.info("Tool call - Session: {}, Tool: {}, Status: {}", sessionId, toolName, success ? "SUCCESS" : "FAILED");
    }

    /**
     * 记录自定义事件
     */
    public void recordCustomEvent(AgentExecutionEvent event) {
        recordEvent(event.getSessionId(), event);
        log.debug("Custom event - Session: {}, Type: {}", event.getSessionId(), event.getEventType());
    }
    
    /**
     * 模板方法：统一的事件记录流程
     */
    private void recordEvent(String sessionId, AgentExecutionEvent event) {
        sessionEvents.computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>()).add(event);
        notifyListeners(event);
    }
    
    /**
     * 辅助方法：添加工具调用到执行阶段
     */
    private void addToolCallToPhase(String sessionId, String toolName, LocalDateTime callTime, 
                                    LocalDateTime completionTime, Object input, Object output, 
                                    boolean success, String error, long durationMs) {
        DetailedExecutionFlow.ExecutionPhase phase = currentPhases.get(sessionId);
        if (phase != null) {
            DetailedExecutionFlow.ToolCall toolCall = DetailedExecutionFlow.ToolCall.builder()
                    .callId(UUID.randomUUID().toString())
                    .toolName(toolName)
                    .callTime(callTime)
                    .completionTime(completionTime)
                    .parameters(input)
                    .result(output)
                    .status(success ? "SUCCESS" : "FAILED")
                    .error(error)
                    .duration(durationMs)
                    .build();
            phase.getToolCalls().add(toolCall);
        }
    }
    
    // ==================== 查询方法 ====================
    
    public List<AgentExecutionEvent> getSessionEvents(String sessionId) {
        return new ArrayList<>(sessionEvents.getOrDefault(sessionId, Collections.emptyList()));
    }
    
    public AgentExecutionEvent getCurrentActiveAgent(String sessionId) {
        return activeAgents.get(sessionId);
    }
    
    public Map<String, AgentExecutionEvent> getAllActiveSessions() {
        return new HashMap<>(activeAgents);
    }
    
    public void clearSession(String sessionId) {
        sessionEvents.remove(sessionId);
        activeAgents.remove(sessionId);
        detailedFlows.remove(sessionId);
        currentPhases.remove(sessionId);
        log.info("Session cleared: {}", sessionId);
    }
    
    // ==================== 观察者模式：事件监听 ====================
    
    public void addListener(AgentExecutionEventListener listener) {
        listeners.add(listener);
        log.debug("Listener added: {}", listener.getClass().getSimpleName());
    }
    
    public void removeListener(AgentExecutionEventListener listener) {
        listeners.remove(listener);
        log.debug("Listener removed: {}", listener.getClass().getSimpleName());
    }
    
    private void notifyListeners(AgentExecutionEvent event) {
        listeners.forEach(listener -> {
            try {
                listener.onEvent(event);
            } catch (Exception e) {
                log.error("Error notifying listener: {}", listener.getClass().getSimpleName(), e);
            }
        });
    }
    
    /**
     * 事件监听器接口
     */
    public interface AgentExecutionEventListener {
        void onEvent(AgentExecutionEvent event);
    }
    // ==================== 统计分析 ====================
    
    /**
     * 获取会话统计信息
     */
    public Map<String, Object> getSessionStatistics(String sessionId) {
        List<AgentExecutionEvent> events = getSessionEvents(sessionId);
        if (events.isEmpty()) {
            return Collections.emptyMap();
        }

        long successCount = events.stream()
                .filter(e -> e.getStatus() == AgentExecutionEvent.ExecutionStatus.SUCCESS)
                .count();
        long errorCount = events.stream()
                .filter(e -> e.getStatus() == AgentExecutionEvent.ExecutionStatus.FAILED)
                .count();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalEvents", events.size());
        stats.put("agentCount", events.stream().map(AgentExecutionEvent::getAgentName).distinct().count());
        stats.put("successCount", successCount);
        stats.put("errorCount", errorCount);
        stats.put("totalDuration", events.stream()
                .filter(e -> e.getDuration() != null)
                .mapToLong(AgentExecutionEvent::getDuration)
                .sum());
        stats.put("agentTypeStats", events.stream()
                .collect(Collectors.groupingBy(AgentExecutionEvent::getAgentType, Collectors.counting())));
        stats.put("eventTypeStats", events.stream()
                .collect(Collectors.groupingBy(e -> e.getEventType().toString(), Collectors.counting())));
        stats.put("successRate", (double) successCount / events.size() * 100);

        return stats;
    }

    // ==================== 详细执行流程追踪 ====================

    public void startWorkflowTracking(String sessionId, String userInput) {
        DetailedExecutionFlow flow = DetailedExecutionFlow.builder()
                .sessionId(sessionId)
                .userInput(userInput)
                .startTime(LocalDateTime.now())
                .status(DetailedExecutionFlow.WorkflowStatus.RUNNING)
                .phases(new ArrayList<>())
                .build();
        detailedFlows.put(sessionId, flow);
        log.info("Workflow tracking started - Session: {}", sessionId);
    }

    public void endWorkflowTracking(String sessionId, String finalResult, boolean success) {
        DetailedExecutionFlow flow = detailedFlows.get(sessionId);
        if (flow == null) {
            return;
        }
        
        flow.setEndTime(LocalDateTime.now());
        flow.setFinalResult(finalResult);
        flow.setStatus(success ? DetailedExecutionFlow.WorkflowStatus.COMPLETED : DetailedExecutionFlow.WorkflowStatus.FAILED);
        
        if (flow.getStartTime() != null) {
            flow.setTotalDuration(java.time.Duration.between(flow.getStartTime(), flow.getEndTime()).toMillis());
        }
        
        log.info("Workflow tracking ended - Session: {}, Status: {}", sessionId, flow.getStatus());
    }

    public void startPhaseTracking(String sessionId, String phaseName, DetailedExecutionFlow.PhaseType phaseType,
                                  String agentName, String agentType, Object input) {
        DetailedExecutionFlow flow = detailedFlows.computeIfAbsent(sessionId, k -> {
            log.warn("No workflow found for session: {}, creating new one", k);
            DetailedExecutionFlow newFlow = DetailedExecutionFlow.builder()
                    .sessionId(k)
                    .userInput("Unknown")
                    .startTime(LocalDateTime.now())
                    .status(DetailedExecutionFlow.WorkflowStatus.RUNNING)
                    .phases(new ArrayList<>())
                    .build();
            return newFlow;
        });

        DetailedExecutionFlow.ExecutionPhase phase = DetailedExecutionFlow.ExecutionPhase.builder()
                .phaseId(UUID.randomUUID().toString())
                .phaseName(phaseName)
                .phaseType(phaseType)
                .agentName(agentName)
                .agentType(agentType)
                .startTime(LocalDateTime.now())
                .status(DetailedExecutionFlow.PhaseStatus.RUNNING)
                .input(input)
                .llmInteractions(new ArrayList<>())
                .toolCalls(new ArrayList<>())
                .metadata(new HashMap<>())
                .build();

        flow.getPhases().add(phase);
        currentPhases.put(sessionId, phase);
        log.info("Phase tracking started - Session: {}, Phase: {}", sessionId, phaseName);
    }

    public void endPhaseTracking(String sessionId, Object output, boolean success, String error) {
        DetailedExecutionFlow.ExecutionPhase phase = currentPhases.remove(sessionId);
        if (phase == null) {
            return;
        }
        
        phase.setEndTime(LocalDateTime.now());
        phase.setOutput(output);
        phase.setStatus(success ? DetailedExecutionFlow.PhaseStatus.COMPLETED : DetailedExecutionFlow.PhaseStatus.FAILED);
        phase.setError(error);
        
        if (phase.getStartTime() != null) {
            phase.setDuration(java.time.Duration.between(phase.getStartTime(), phase.getEndTime()).toMillis());
        }
        
        log.info("Phase tracking ended - Session: {}, Phase: {}, Status: {}", 
                sessionId, phase.getPhaseName(), phase.getStatus());
    }

    public void recordLLMInteraction(String sessionId, String request, String response, String model,
                                   DetailedExecutionFlow.TokenUsage tokenUsage, long responseTimeMs) {
        DetailedExecutionFlow.ExecutionPhase phase = currentPhases.get(sessionId);
        if (phase == null) {
            return;
        }
        
        DetailedExecutionFlow.LLMInteraction interaction = DetailedExecutionFlow.LLMInteraction.builder()
                .interactionId(UUID.randomUUID().toString())
                .requestTime(LocalDateTime.now().minusNanos(responseTimeMs * 1_000_000))
                .responseTime(LocalDateTime.now())
                .request(request)
                .response(response)
                .model(model)
                .tokenUsage(tokenUsage)
                .responseTime_ms(responseTimeMs)
                .build();

        phase.getLlmInteractions().add(interaction);
        log.debug("LLM interaction recorded - Session: {}, Model: {}", sessionId, model);
    }

    public DetailedExecutionFlow getDetailedExecutionFlow(String sessionId) {
        return detailedFlows.get(sessionId);
    }

    public Map<String, DetailedExecutionFlow> getAllDetailedExecutionFlows() {
        return new HashMap<>(detailedFlows);
    }

    public void cleanupCompletedFlows(int maxAgeHours) {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(maxAgeHours);
        int removedCount = detailedFlows.size();
        
        detailedFlows.entrySet().removeIf(entry -> {
            DetailedExecutionFlow flow = entry.getValue();
            return flow.getEndTime() != null && flow.getEndTime().isBefore(cutoff);
        });
        
        removedCount -= detailedFlows.size();
        log.info("Cleaned up {} flows older than {} hours", removedCount, maxAgeHours);
    }
}
