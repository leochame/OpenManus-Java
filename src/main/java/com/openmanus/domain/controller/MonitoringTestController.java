package com.openmanus.domain.controller;

import com.openmanus.infra.monitoring.AgentExecutionTracker;
import com.openmanus.domain.model.DetailedExecutionFlow;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 监控测试控制器
 * 用于演示和测试Agent执行监控功能
 */
@RestController
@RequestMapping("/api/agent-monitor-test")
@Tag(name = "监控测试", description = "Agent执行监控功能测试API")
@CrossOrigin(origins = "*")
@Slf4j
public class MonitoringTestController {

    private final AgentExecutionTracker agentExecutionTracker;

    @Autowired
    public MonitoringTestController(AgentExecutionTracker agentExecutionTracker) {
        this.agentExecutionTracker = agentExecutionTracker;
    }

    /**
     * 模拟Think-Do-Reflect工作流执行
     */
    @PostMapping("/simulate-think-do-reflect")
    @Operation(summary = "模拟Think-Do-Reflect工作流", description = "模拟一个完整的Think-Do-Reflect工作流执行过程，用于测试监控功能")
    public ResponseEntity<Map<String, Object>> simulateThinkDoReflect(@RequestBody Map<String, String> request) {
        String userInput = request.getOrDefault("userInput", "测试输入");
        String sessionId = "test-session-" + UUID.randomUUID().toString();
        
        log.info("开始模拟Think-Do-Reflect工作流，会话ID: {}", sessionId);
        
        // 在后台线程中执行模拟，避免阻塞响应
        new Thread(() -> {
            try {
                simulateWorkflowExecution(sessionId, userInput);
            } catch (Exception e) {
                log.error("模拟工作流执行失败", e);
            }
        }).start();
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("sessionId", sessionId);
        response.put("message", "Think-Do-Reflect工作流模拟已启动");
        
        return ResponseEntity.ok(response);
    }

    /**
     * 模拟工作流执行过程
     */
    private void simulateWorkflowExecution(String sessionId, String userInput) throws InterruptedException {
        // 1. 开始工作流跟踪
        agentExecutionTracker.startWorkflowTracking(sessionId, userInput);
        
        // 2. 模拟思考阶段
        simulateThinkingPhase(sessionId, userInput);
        
        // 3. 模拟执行阶段
        simulateExecutionPhase(sessionId);
        
        // 4. 模拟反思阶段
        simulateReflectionPhase(sessionId);
        
        // 5. 结束工作流跟踪
        agentExecutionTracker.endWorkflowTracking(sessionId, "模拟工作流执行完成", true);
        
        log.info("Think-Do-Reflect工作流模拟完成，会话ID: {}", sessionId);
    }

    /**
     * 模拟思考阶段
     */
    private void simulateThinkingPhase(String sessionId, String userInput) throws InterruptedException {
        agentExecutionTracker.startPhaseTracking(
            sessionId, 
            "任务分析与规划", 
            DetailedExecutionFlow.PhaseType.THINKING,
            "thinking_agent",
            "ThinkingAgent",
            userInput
        );
        
        // 模拟LLM交互
        Thread.sleep(1000); // 模拟思考时间
        
        DetailedExecutionFlow.TokenUsage tokenUsage = DetailedExecutionFlow.TokenUsage.builder()
            .inputTokens(150)
            .outputTokens(200)
            .totalTokens(350)
            .build();
            
        agentExecutionTracker.recordLLMInteraction(
            sessionId,
            "分析任务: " + userInput,
            "经过分析，我需要执行以下步骤：1. 理解需求 2. 制定计划 3. 执行任务",
            "qwen-max",
            tokenUsage,
            1200
        );
        
        String thinkingResult = "任务分析完成，制定了详细的执行计划";
        agentExecutionTracker.endPhaseTracking(sessionId, thinkingResult, true, null);
    }

    /**
     * 模拟执行阶段
     */
    private void simulateExecutionPhase(String sessionId) throws InterruptedException {
        agentExecutionTracker.startPhaseTracking(
            sessionId, 
            "任务执行", 
            DetailedExecutionFlow.PhaseType.EXECUTION,
            "code_agent",
            "CodeAgent",
            "执行计划中的具体任务"
        );
        
        // 模拟工具调用
        Thread.sleep(800);
        
        agentExecutionTracker.recordToolCall(
            sessionId,
            "executePython",
            "print('Hello, World!')",
            "Hello, World!",
            true,
            null,
            500
        );
        
        // 模拟另一个LLM交互
        Thread.sleep(1500);
        
        DetailedExecutionFlow.TokenUsage tokenUsage = DetailedExecutionFlow.TokenUsage.builder()
            .inputTokens(100)
            .outputTokens(150)
            .totalTokens(250)
            .build();
            
        agentExecutionTracker.recordLLMInteraction(
            sessionId,
            "执行代码并分析结果",
            "代码执行成功，输出了预期的结果",
            "qwen-max",
            tokenUsage,
            800
        );
        
        String executionResult = "任务执行完成，获得了预期的结果";
        agentExecutionTracker.endPhaseTracking(sessionId, executionResult, true, null);
    }

    /**
     * 模拟反思阶段
     */
    private void simulateReflectionPhase(String sessionId) throws InterruptedException {
        agentExecutionTracker.startPhaseTracking(
            sessionId, 
            "结果评估与反思", 
            DetailedExecutionFlow.PhaseType.REFLECTION,
            "reflection_agent",
            "ReflectionAgent",
            "评估执行结果的质量和完整性"
        );
        
        // 模拟反思过程
        Thread.sleep(1200);
        
        DetailedExecutionFlow.TokenUsage tokenUsage = DetailedExecutionFlow.TokenUsage.builder()
            .inputTokens(200)
            .outputTokens(100)
            .totalTokens(300)
            .build();
            
        agentExecutionTracker.recordLLMInteraction(
            sessionId,
            "评估任务执行结果是否满足原始需求",
            "经过评估，任务已经完全满足了用户的需求，执行质量良好",
            "qwen-max",
            tokenUsage,
            900
        );
        
        String reflectionResult = "反思完成：任务执行成功，满足了所有要求";
        agentExecutionTracker.endPhaseTracking(sessionId, reflectionResult, true, null);
    }

    /**
     * 获取模拟会话的状态
     */
    @GetMapping("/session/{sessionId}/status")
    @Operation(summary = "获取模拟会话状态", description = "获取指定模拟会话的执行状态")
    public ResponseEntity<Map<String, Object>> getSessionStatus(@PathVariable String sessionId) {
        try {
            DetailedExecutionFlow flow = agentExecutionTracker.getDetailedExecutionFlow(sessionId);
            
            Map<String, Object> status = new HashMap<>();
            if (flow != null) {
                status.put("sessionId", flow.getSessionId());
                status.put("status", flow.getStatus());
                status.put("userInput", flow.getUserInput());
                status.put("finalResult", flow.getFinalResult());
                status.put("totalDuration", flow.getTotalDuration());
                status.put("phaseCount", flow.getPhases() != null ? flow.getPhases().size() : 0);
            } else {
                status.put("error", "会话不存在");
            }
            
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("获取会话状态失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 清理测试数据
     */
    @DeleteMapping("/cleanup")
    @Operation(summary = "清理测试数据", description = "清理所有测试产生的监控数据")
    public ResponseEntity<Map<String, Object>> cleanup() {
        try {
            agentExecutionTracker.cleanupCompletedFlows(0); // 清理所有已完成的流程
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "测试数据清理完成");
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("清理测试数据失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
