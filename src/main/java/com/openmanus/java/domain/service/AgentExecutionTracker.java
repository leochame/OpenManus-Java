package com.openmanus.java.domain.service;

import com.openmanus.java.domain.model.AgentExecutionEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Agent执行跟踪服务
 * 负责跟踪和管理Agent的执行状态
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
                .eventType(AgentExecutionEvent.EventType.TOOL_CALL)
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
        
        return stats;
    }
}
