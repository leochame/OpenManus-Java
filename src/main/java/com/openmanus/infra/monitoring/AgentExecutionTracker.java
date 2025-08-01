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
 * Agent执行跟踪服务
 * 负责跟踪和管理Agent的执行状态
 *
 * 增强功能：
 * 1. 详细的输入输出追踪
 * 2. 工具调用自动监控
 * 3. 执行步骤分解
 * 4. 实时状态更新
 * 5. 深度执行流程分析
 */
@Service
@Slf4j
public class AgentExecutionTracker {
    
    /**
     * 存储所有执行会话的事件
     * Key: sessionId, Value: 事件列表
     */
    private final Map<String, List<AgentExecutionEvent>> sessionEvents = new ConcurrentHashMap<>();
    
    /**
     * 存储当前活跃的执行会话
     * Key: sessionId, Value: 当前执行的Agent信息
     */
    private final Map<String, AgentExecutionEvent> activeAgents = new ConcurrentHashMap<>();
    
    /**
     * 详细执行流程跟踪
     * Key: sessionId, Value: 详细执行流程
     */
    private final Map<String, DetailedExecutionFlow> detailedFlows = new ConcurrentHashMap<>();

    /**
     * 当前执行阶段跟踪
     * Key: sessionId, Value: 当前执行阶段
     */
    private final Map<String, DetailedExecutionFlow.ExecutionPhase> currentPhases = new ConcurrentHashMap<>();

    /**
     * 事件监听器列表
     */
    private final List<AgentExecutionEventListener> listeners = new CopyOnWriteArrayList<>();
    
    /**
     * 开始跟踪Agent执行
     */
    public void startAgentExecution(String sessionId, String agentName, String agentType, Object input) {
        AgentExecutionEvent event = AgentExecutionEvent.createStartEvent(sessionId, agentName, agentType, input);
        
        // 添加到会话事件列表
        sessionEvents.computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>()).add(event);
        
        // 更新活跃Agent
        activeAgents.put(sessionId, event);
        
        // 通知监听器
        notifyListeners(event);
        
        log.info("Agent execution started - Session: {}, Agent: {}, Type: {}", sessionId, agentName, agentType);
    }
    
    /**
     * 结束Agent执行
     */
    public void endAgentExecution(String sessionId, String agentName, String agentType, Object output, AgentExecutionEvent.ExecutionStatus status) {
        AgentExecutionEvent event = AgentExecutionEvent.createEndEvent(sessionId, agentName, agentType, output, status);
        
        // 计算执行时间
        AgentExecutionEvent startEvent = activeAgents.get(sessionId);
        if (startEvent != null) {
            event.setStartTime(startEvent.getStartTime());
            event.calculateDuration();
        }
        
        // 添加到会话事件列表
        sessionEvents.computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>()).add(event);
        
        // 移除活跃Agent
        activeAgents.remove(sessionId);
        
        // 通知监听器
        notifyListeners(event);
        
        log.info("Agent execution ended - Session: {}, Agent: {}, Type: {}, Status: {}, Duration: {}ms", 
                sessionId, agentName, agentType, status, event.getDuration());
    }
    
    /**
     * 记录Agent执行错误
     */
    public void recordAgentError(String sessionId, String agentName, String agentType, String error) {
        AgentExecutionEvent event = AgentExecutionEvent.createErrorEvent(sessionId, agentName, agentType, error);
        
        // 添加到会话事件列表
        sessionEvents.computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>()).add(event);
        
        // 移除活跃Agent
        activeAgents.remove(sessionId);
        
        // 通知监听器
        notifyListeners(event);
        
        log.error("Agent execution error - Session: {}, Agent: {}, Type: {}, Error: {}", 
                sessionId, agentName, agentType, error);
    }
    
    /**
     * 记录工具调用事件
     */
    public void recordToolCall(String sessionId, String agentName, String toolName, Object input, Object output) {
        AgentExecutionEvent event = AgentExecutionEvent.builder()
                .sessionId(sessionId)
                .eventId(UUID.randomUUID().toString())
                .agentName(agentName)
                .agentType("TOOL_CALL")
                .eventType(AgentExecutionEvent.EventType.TOOL_CALL_END)
                .status(AgentExecutionEvent.ExecutionStatus.SUCCESS)
                .input(input)
                .output(output)
                .startTime(LocalDateTime.now())
                .endTime(LocalDateTime.now())
                .metadata(Map.of("toolName", toolName))
                .build();

        event.calculateDuration();

        // 添加到会话事件列表
        sessionEvents.computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>()).add(event);

        // 通知监听器
        notifyListeners(event);

        log.info("Tool call recorded - Session: {}, Agent: {}, Tool: {}", sessionId, agentName, toolName);
    }

    /**
     * 记录自定义事件
     */
    public void recordCustomEvent(AgentExecutionEvent event) {
        // 添加到会话事件列表
        sessionEvents.computeIfAbsent(event.getSessionId(), k -> new CopyOnWriteArrayList<>()).add(event);

        // 通知监听器
        notifyListeners(event);

        log.debug("Custom event recorded - Session: {}, Agent: {}, Type: {}",
                event.getSessionId(), event.getAgentName(), event.getEventType());
    }
    
    /**
     * 获取会话的所有事件
     */
    public List<AgentExecutionEvent> getSessionEvents(String sessionId) {
        return new ArrayList<>(sessionEvents.getOrDefault(sessionId, Collections.emptyList()));
    }
    
    /**
     * 获取当前活跃的Agent
     */
    public AgentExecutionEvent getCurrentActiveAgent(String sessionId) {
        return activeAgents.get(sessionId);
    }
    
    /**
     * 获取所有活跃的会话
     */
    public Map<String, AgentExecutionEvent> getAllActiveSessions() {
        return new HashMap<>(activeAgents);
    }
    
    /**
     * 清理会话数据
     */
    public void clearSession(String sessionId) {
        sessionEvents.remove(sessionId);
        activeAgents.remove(sessionId);
        log.info("Session cleared: {}", sessionId);
    }
    
    /**
     * 添加事件监听器
     */
    public void addListener(AgentExecutionEventListener listener) {
        listeners.add(listener);
    }
    
    /**
     * 移除事件监听器
     */
    public void removeListener(AgentExecutionEventListener listener) {
        listeners.remove(listener);
    }
    
    /**
     * 通知所有监听器
     */
    private void notifyListeners(AgentExecutionEvent event) {
        for (AgentExecutionEventListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (Exception e) {
                log.error("Error notifying listener", e);
            }
        }
    }
    
    /**
     * 事件监听器接口
     */
    public interface AgentExecutionEventListener {
        void onEvent(AgentExecutionEvent event);
    }
    
    /**
     * 记录执行步骤
     */
    public void recordExecutionStep(String sessionId, String agentName, String agentType, String stepName, Object stepData) {
        AgentExecutionEvent event = AgentExecutionEvent.builder()
                .sessionId(sessionId)
                .eventId(UUID.randomUUID().toString())
                .agentName(agentName)
                .agentType(agentType)
                .eventType(AgentExecutionEvent.EventType.STEP_START)
                .status(AgentExecutionEvent.ExecutionStatus.RUNNING)
                .input(stepData)
                .startTime(LocalDateTime.now())
                .metadata(Map.of("stepName", stepName))
                .build();

        recordCustomEvent(event);

        log.debug("Execution step recorded - Session: {}, Agent: {}, Step: {}",
                sessionId, agentName, stepName);
    }

    /**
     * 记录中间结果
     */
    public void recordIntermediateResult(String sessionId, String agentName, String agentType, Object result, String description) {
        AgentExecutionEvent event = AgentExecutionEvent.builder()
                .sessionId(sessionId)
                .eventId(UUID.randomUUID().toString())
                .agentName(agentName)
                .agentType(agentType)
                .eventType(AgentExecutionEvent.EventType.INTERMEDIATE_RESULT)
                .status(AgentExecutionEvent.ExecutionStatus.SUCCESS)
                .output(result)
                .startTime(LocalDateTime.now())
                .endTime(LocalDateTime.now())
                .metadata(Map.of("description", description))
                .build();

        event.calculateDuration();
        recordCustomEvent(event);

        log.info("Intermediate result recorded - Session: {}, Agent: {}, Description: {}",
                sessionId, agentName, description);
    }

    /**
     * 记录决策点
     */
    public void recordDecisionPoint(String sessionId, String agentName, String agentType, Object decision, String reasoning) {
        AgentExecutionEvent event = AgentExecutionEvent.builder()
                .sessionId(sessionId)
                .eventId(UUID.randomUUID().toString())
                .agentName(agentName)
                .agentType(agentType)
                .eventType(AgentExecutionEvent.EventType.DECISION_POINT)
                .status(AgentExecutionEvent.ExecutionStatus.SUCCESS)
                .output(decision)
                .startTime(LocalDateTime.now())
                .endTime(LocalDateTime.now())
                .metadata(Map.of("reasoning", reasoning))
                .build();

        event.calculateDuration();
        recordCustomEvent(event);

        log.info("Decision point recorded - Session: {}, Agent: {}, Decision: {}",
                sessionId, agentName, decision);
    }



    /**
     * 获取会话统计信息
     */
    public Map<String, Object> getSessionStatistics(String sessionId) {
        List<AgentExecutionEvent> events = getSessionEvents(sessionId);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalEvents", events.size());
        stats.put("agentCount", events.stream().map(AgentExecutionEvent::getAgentName).distinct().count());
        stats.put("successCount", events.stream().filter(e -> e.getStatus() == AgentExecutionEvent.ExecutionStatus.SUCCESS).count());
        stats.put("errorCount", events.stream().filter(e -> e.getStatus() == AgentExecutionEvent.ExecutionStatus.FAILED).count());

        // 计算总执行时间
        long totalDuration = events.stream()
                .filter(e -> e.getDuration() != null)
                .mapToLong(AgentExecutionEvent::getDuration)
                .sum();
        stats.put("totalDuration", totalDuration);

        // Agent类型统计
        Map<String, Long> agentTypeStats = events.stream()
                .collect(Collectors.groupingBy(
                        AgentExecutionEvent::getAgentType,
                        Collectors.counting()
                ));
        stats.put("agentTypeStats", agentTypeStats);

        // 事件类型统计
        Map<String, Long> eventTypeStats = events.stream()
                .collect(Collectors.groupingBy(
                        event -> event.getEventType().toString(),
                        Collectors.counting()
                ));
        stats.put("eventTypeStats", eventTypeStats);

        // 成功率统计
        double successRate = events.isEmpty() ? 0 :
                (double) stats.get("successCount") / events.size() * 100;
        stats.put("successRate", successRate);

        return stats;
    }

    // ==================== 详细执行流程跟踪方法 ====================

    /**
     * 开始工作流跟踪
     */
    public void startWorkflowTracking(String sessionId, String userInput) {
        DetailedExecutionFlow flow = DetailedExecutionFlow.builder()
                .sessionId(sessionId)
                .userInput(userInput)
                .startTime(LocalDateTime.now())
                .status(DetailedExecutionFlow.WorkflowStatus.RUNNING)
                .phases(new ArrayList<>())
                .build();

        detailedFlows.put(sessionId, flow);

        log.info("Started workflow tracking for session: {}", sessionId);
    }

    /**
     * 结束工作流跟踪
     */
    public void endWorkflowTracking(String sessionId, String finalResult, boolean success) {
        DetailedExecutionFlow flow = detailedFlows.get(sessionId);
        if (flow != null) {
            flow.setEndTime(LocalDateTime.now());
            flow.setFinalResult(finalResult);
            flow.setStatus(success ? DetailedExecutionFlow.WorkflowStatus.COMPLETED : DetailedExecutionFlow.WorkflowStatus.FAILED);

            if (flow.getStartTime() != null) {
                flow.setTotalDuration(java.time.Duration.between(flow.getStartTime(), flow.getEndTime()).toMillis());
            }

            log.info("Ended workflow tracking for session: {} with status: {}", sessionId, flow.getStatus());
        }
    }

    /**
     * 开始执行阶段跟踪
     */
    public void startPhaseTracking(String sessionId, String phaseName, DetailedExecutionFlow.PhaseType phaseType,
                                  String agentName, String agentType, Object input) {
        DetailedExecutionFlow flow = detailedFlows.get(sessionId);
        if (flow == null) {
            log.warn("No workflow found for session: {}, creating new one", sessionId);
            startWorkflowTracking(sessionId, "Unknown input");
            flow = detailedFlows.get(sessionId);
        }

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

        log.info("Started phase tracking: {} for session: {}", phaseName, sessionId);
    }

    /**
     * 结束执行阶段跟踪
     */
    public void endPhaseTracking(String sessionId, Object output, boolean success, String error) {
        DetailedExecutionFlow.ExecutionPhase phase = currentPhases.get(sessionId);
        if (phase != null) {
            phase.setEndTime(LocalDateTime.now());
            phase.setOutput(output);
            phase.setStatus(success ? DetailedExecutionFlow.PhaseStatus.COMPLETED : DetailedExecutionFlow.PhaseStatus.FAILED);
            phase.setError(error);

            if (phase.getStartTime() != null) {
                phase.setDuration(java.time.Duration.between(phase.getStartTime(), phase.getEndTime()).toMillis());
            }

            currentPhases.remove(sessionId);

            log.info("Ended phase tracking: {} for session: {} with status: {}",
                    phase.getPhaseName(), sessionId, phase.getStatus());
        }
    }

    /**
     * 记录LLM交互
     */
    public void recordLLMInteraction(String sessionId, String request, String response, String model,
                                   DetailedExecutionFlow.TokenUsage tokenUsage, long responseTimeMs) {
        DetailedExecutionFlow.ExecutionPhase phase = currentPhases.get(sessionId);
        if (phase != null) {
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

            log.debug("Recorded LLM interaction for session: {}, model: {}, tokens: {}",
                    sessionId, model, tokenUsage != null ? tokenUsage.getTotalTokens() : "unknown");
        }
    }

    /**
     * 记录工具调用
     */
    public void recordToolCall(String sessionId, String toolName, Object parameters, Object result,
                              boolean success, String error, long duration) {
        DetailedExecutionFlow.ExecutionPhase phase = currentPhases.get(sessionId);
        if (phase != null) {
            DetailedExecutionFlow.ToolCall toolCall = DetailedExecutionFlow.ToolCall.builder()
                    .callId(UUID.randomUUID().toString())
                    .toolName(toolName)
                    .callTime(LocalDateTime.now().minusNanos(duration * 1_000_000))
                    .completionTime(LocalDateTime.now())
                    .parameters(parameters)
                    .result(result)
                    .status(success ? "SUCCESS" : "FAILED")
                    .error(error)
                    .duration(duration)
                    .build();

            phase.getToolCalls().add(toolCall);

            log.debug("Recorded tool call for session: {}, tool: {}, status: {}",
                    sessionId, toolName, toolCall.getStatus());
        }
    }

    /**
     * 获取详细执行流程
     */
    public DetailedExecutionFlow getDetailedExecutionFlow(String sessionId) {
        return detailedFlows.get(sessionId);
    }

    /**
     * 获取所有详细执行流程
     */
    public Map<String, DetailedExecutionFlow> getAllDetailedExecutionFlows() {
        return new HashMap<>(detailedFlows);
    }

    /**
     * 清理已完成的执行流程（可选的清理方法）
     */
    public void cleanupCompletedFlows(int maxAge_hours) {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(maxAge_hours);

        detailedFlows.entrySet().removeIf(entry -> {
            DetailedExecutionFlow flow = entry.getValue();
            return flow.getEndTime() != null && flow.getEndTime().isBefore(cutoff);
        });

        log.info("Cleaned up completed flows older than {} hours", maxAge_hours);
    }
}
