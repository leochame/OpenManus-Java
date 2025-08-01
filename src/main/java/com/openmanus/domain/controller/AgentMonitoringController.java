package com.openmanus.domain.controller;

import com.openmanus.infra.monitoring.AgentExecutionTracker;
import com.openmanus.domain.model.AgentExecutionEvent;
import com.openmanus.domain.model.DetailedExecutionFlow;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Agent 执行监控控制器
 * 专注于 Agent 执行流程的可视化监控
 */
@RestController
@RequestMapping("/api/agent-monitoring")
@Tag(name = "Agent执行监控", description = "Agent执行流程可视化监控API")
@CrossOrigin(origins = "*")
@Slf4j
public class AgentMonitoringController {

    private final AgentExecutionTracker agentExecutionTracker;

    @Autowired
    public AgentMonitoringController(AgentExecutionTracker agentExecutionTracker) {
        this.agentExecutionTracker = agentExecutionTracker;
    }

    /**
     * 获取监控仪表板数据
     */
    @GetMapping("/dashboard")
    @Operation(summary = "获取监控仪表板", description = "获取Agent执行监控的仪表板数据")
    public ResponseEntity<Map<String, Object>> getDashboard() {
        try {
            Map<String, Object> dashboard = new HashMap<>();
            
            // 活跃会话统计
            Map<String, AgentExecutionEvent> activeSessions = agentExecutionTracker.getAllActiveSessions();
            dashboard.put("totalActiveSessions", activeSessions.size());
            
            // 详细执行流程统计
            Map<String, DetailedExecutionFlow> detailedFlows = agentExecutionTracker.getAllDetailedExecutionFlows();
            dashboard.put("totalDetailedFlows", detailedFlows.size());
            
            // 运行中的工作流
            long runningFlows = detailedFlows.values().stream()
                    .filter(flow -> flow.getStatus() == DetailedExecutionFlow.WorkflowStatus.RUNNING)
                    .count();
            dashboard.put("runningFlows", runningFlows);
            
            // 已完成的工作流
            long completedFlows = detailedFlows.values().stream()
                    .filter(flow -> flow.getStatus() == DetailedExecutionFlow.WorkflowStatus.COMPLETED)
                    .count();
            dashboard.put("completedFlows", completedFlows);
            
            // 失败的工作流
            long failedFlows = detailedFlows.values().stream()
                    .filter(flow -> flow.getStatus() == DetailedExecutionFlow.WorkflowStatus.FAILED)
                    .count();
            dashboard.put("failedFlows", failedFlows);
            
            return ResponseEntity.ok(dashboard);
        } catch (Exception e) {
            log.error("Error getting dashboard data", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取所有活跃的Agent会话
     */
    @GetMapping("/sessions/active")
    @Operation(summary = "获取活跃会话", description = "获取当前所有活跃的Agent执行会话")
    public ResponseEntity<Map<String, AgentExecutionEvent>> getActiveSessions() {
        try {
            Map<String, AgentExecutionEvent> activeSessions = agentExecutionTracker.getAllActiveSessions();
            return ResponseEntity.ok(activeSessions);
        } catch (Exception e) {
            log.error("Error getting active sessions", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取会话的执行事件
     */
    @GetMapping("/sessions/{sessionId}/events")
    @Operation(summary = "获取会话事件", description = "获取指定会话的所有执行事件")
    public ResponseEntity<List<AgentExecutionEvent>> getSessionEvents(@PathVariable String sessionId) {
        try {
            List<AgentExecutionEvent> events = agentExecutionTracker.getSessionEvents(sessionId);
            return ResponseEntity.ok(events);
        } catch (Exception e) {
            log.error("Error getting session events for sessionId: {}", sessionId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取详细执行流程
     */
    @GetMapping("/sessions/{sessionId}/detailed-flow")
    @Operation(summary = "获取详细执行流程", description = "获取指定会话的详细执行流程，包括思考、执行、反思等各个阶段")
    public ResponseEntity<DetailedExecutionFlow> getDetailedExecutionFlow(@PathVariable String sessionId) {
        try {
            DetailedExecutionFlow flow = agentExecutionTracker.getDetailedExecutionFlow(sessionId);
            if (flow == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(flow);
        } catch (Exception e) {
            log.error("Error getting detailed execution flow for sessionId: {}", sessionId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取所有详细执行流程
     */
    @GetMapping("/flows/all")
    @Operation(summary = "获取所有执行流程", description = "获取所有的详细执行流程")
    public ResponseEntity<Map<String, DetailedExecutionFlow>> getAllDetailedFlows() {
        try {
            Map<String, DetailedExecutionFlow> flows = agentExecutionTracker.getAllDetailedExecutionFlows();
            return ResponseEntity.ok(flows);
        } catch (Exception e) {
            log.error("Error getting all detailed flows", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取最近的执行流程
     */
    @GetMapping("/flows/recent")
    @Operation(summary = "获取最近执行流程", description = "获取最近的执行流程，按开始时间倒序")
    public ResponseEntity<List<DetailedExecutionFlow>> getRecentFlows(@RequestParam(defaultValue = "10") int limit) {
        try {
            List<DetailedExecutionFlow> recentFlows = agentExecutionTracker.getAllDetailedExecutionFlows()
                    .values()
                    .stream()
                    .sorted((a, b) -> b.getStartTime().compareTo(a.getStartTime()))
                    .limit(limit)
                    .collect(Collectors.toList());
            
            return ResponseEntity.ok(recentFlows);
        } catch (Exception e) {
            log.error("Error getting recent flows", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取会话统计信息
     */
    @GetMapping("/sessions/{sessionId}/stats")
    @Operation(summary = "获取会话统计", description = "获取指定会话的统计信息")
    public ResponseEntity<Map<String, Object>> getSessionStats(@PathVariable String sessionId) {
        try {
            Map<String, Object> stats = agentExecutionTracker.getSessionStatistics(sessionId);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error getting session stats for sessionId: {}", sessionId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 清理已完成的执行流程
     */
    @DeleteMapping("/flows/cleanup")
    @Operation(summary = "清理执行流程", description = "清理指定时间之前的已完成执行流程")
    public ResponseEntity<Map<String, Object>> cleanupFlows(@RequestParam(defaultValue = "24") int maxAgeHours) {
        try {
            agentExecutionTracker.cleanupCompletedFlows(maxAgeHours);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "清理完成，删除了 " + maxAgeHours + " 小时前的已完成流程");
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error cleaning up flows", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
