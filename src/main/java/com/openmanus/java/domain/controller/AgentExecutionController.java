package com.openmanus.java.domain.controller;

import com.openmanus.java.domain.model.AgentExecutionEvent;
import com.openmanus.java.domain.service.AgentExecutionTracker;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Agent执行监控控制器
 * 提供Agent执行状态的REST API接口
 */
@RestController
@RequestMapping("/api/agent-execution")
@Tag(name = "Agent Execution Monitor", description = "Agent执行状态监控API")
@CrossOrigin(origins = "*")
@Slf4j
public class AgentExecutionController {
    
    private final AgentExecutionTracker executionTracker;
    
    @Autowired
    public AgentExecutionController(AgentExecutionTracker executionTracker) {
        this.executionTracker = executionTracker;
    }
    
    /**
     * 获取指定会话的所有执行事件
     */
    @GetMapping("/sessions/{sessionId}/events")
    @Operation(summary = "获取会话执行事件", description = "获取指定会话的所有Agent执行事件")
    public ResponseEntity<List<AgentExecutionEvent>> getSessionEvents(@PathVariable String sessionId) {
        try {
            List<AgentExecutionEvent> events = executionTracker.getSessionEvents(sessionId);
            return ResponseEntity.ok(events);
        } catch (Exception e) {
            log.error("Error getting session events for session: " + sessionId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 获取指定会话当前活跃的Agent
     */
    @GetMapping("/sessions/{sessionId}/current")
    @Operation(summary = "获取当前活跃Agent", description = "获取指定会话当前正在执行的Agent")
    public ResponseEntity<AgentExecutionEvent> getCurrentActiveAgent(@PathVariable String sessionId) {
        try {
            AgentExecutionEvent activeAgent = executionTracker.getCurrentActiveAgent(sessionId);
            if (activeAgent != null) {
                return ResponseEntity.ok(activeAgent);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("Error getting current active agent for session: " + sessionId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 获取所有活跃的会话
     */
    @GetMapping("/sessions/active")
    @Operation(summary = "获取所有活跃会话", description = "获取当前所有正在执行的会话")
    public ResponseEntity<Map<String, AgentExecutionEvent>> getAllActiveSessions() {
        try {
            Map<String, AgentExecutionEvent> activeSessions = executionTracker.getAllActiveSessions();
            return ResponseEntity.ok(activeSessions);
        } catch (Exception e) {
            log.error("Error getting all active sessions", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 获取会话统计信息
     */
    @GetMapping("/sessions/{sessionId}/statistics")
    @Operation(summary = "获取会话统计", description = "获取指定会话的执行统计信息")
    public ResponseEntity<Map<String, Object>> getSessionStatistics(@PathVariable String sessionId) {
        try {
            Map<String, Object> statistics = executionTracker.getSessionStatistics(sessionId);
            return ResponseEntity.ok(statistics);
        } catch (Exception e) {
            log.error("Error getting session statistics for session: " + sessionId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 清理会话数据
     */
    @DeleteMapping("/sessions/{sessionId}")
    @Operation(summary = "清理会话数据", description = "清理指定会话的所有执行数据")
    public ResponseEntity<Map<String, Object>> clearSession(@PathVariable String sessionId) {
        try {
            executionTracker.clearSession(sessionId);
            Map<String, Object> response = Map.of(
                "success", true,
                "message", "Session cleared successfully",
                "sessionId", sessionId
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error clearing session: " + sessionId, e);
            Map<String, Object> response = Map.of(
                "success", false,
                "message", "Failed to clear session: " + e.getMessage(),
                "sessionId", sessionId
            );
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    /**
     * 健康检查接口
     */
    @GetMapping("/health")
    @Operation(summary = "健康检查", description = "检查Agent执行监控服务的健康状态")
    public ResponseEntity<Map<String, Object>> health() {
        try {
            Map<String, AgentExecutionEvent> activeSessions = executionTracker.getAllActiveSessions();
            Map<String, Object> healthStatus = Map.of(
                "status", "healthy",
                "service", "Agent Execution Monitor",
                "activeSessions", activeSessions.size(),
                "timestamp", System.currentTimeMillis()
            );
            return ResponseEntity.ok(healthStatus);
        } catch (Exception e) {
            log.error("Health check failed", e);
            Map<String, Object> healthStatus = Map.of(
                "status", "unhealthy",
                "service", "Agent Execution Monitor",
                "error", e.getMessage(),
                "timestamp", System.currentTimeMillis()
            );
            return ResponseEntity.internalServerError().body(healthStatus);
        }
    }
    
    /**
     * 获取Agent类型列表
     */
    @GetMapping("/agent-types")
    @Operation(summary = "获取Agent类型", description = "获取系统中所有可用的Agent类型")
    public ResponseEntity<Map<String, Object>> getAgentTypes() {
        try {
            // 定义系统中的Agent类型
            List<Map<String, Object>> agentTypes = List.of(
                Map.of("name", "supervisor", "displayName", "监督者Agent", "description", "总协调者，管理整个工作流程"),
                Map.of("name", "thinking_agent", "displayName", "思考Agent", "description", "负责任务分析和规划"),
                Map.of("name", "search_agent", "displayName", "搜索Agent", "description", "负责信息检索和搜索"),
                Map.of("name", "code_agent", "displayName", "代码Agent", "description", "负责代码执行和处理"),
                Map.of("name", "file_agent", "displayName", "文件Agent", "description", "负责文件操作和管理"),
                Map.of("name", "reflection_agent", "displayName", "反思Agent", "description", "负责结果评估和反思"),
                Map.of("name", "marketplace_agent", "displayName", "市场Agent", "description", "负责市场相关操作"),
                Map.of("name", "payment_agent", "displayName", "支付Agent", "description", "负责支付相关操作"),
                Map.of("name", "omni_agent", "displayName", "全能Agent", "description", "具备多种工具能力的综合Agent")
            );
            
            Map<String, Object> response = Map.of(
                "agentTypes", agentTypes,
                "count", agentTypes.size()
            );
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting agent types", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
