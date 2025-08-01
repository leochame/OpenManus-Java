package com.openmanus.java.domain.controller;

import com.openmanus.java.domain.model.AgentExecutionEvent;
import com.openmanus.java.domain.service.AgentExecutionTracker;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Agent监控测试控制器
 * 提供模拟Agent执行的接口，用于测试监控功能
 */
@RestController
@RequestMapping("/api/agent-monitor-test")
@Tag(name = "Agent Monitor Test", description = "Agent监控测试API")
@CrossOrigin(origins = "*")
@Slf4j
public class AgentMonitorTestController {
    
    private final AgentExecutionTracker executionTracker;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);
    
    @Autowired
    public AgentMonitorTestController(AgentExecutionTracker executionTracker) {
        this.executionTracker = executionTracker;
    }
    
    /**
     * 模拟Think-Do-Reflect工作流执行
     */
    @PostMapping("/simulate/think-do-reflect")
    @Operation(summary = "模拟Think-Do-Reflect工作流", description = "模拟完整的Think-Do-Reflect执行流程")
    public ResponseEntity<Map<String, Object>> simulateThinkDoReflect(@RequestBody Map<String, String> request) {
        String sessionId = UUID.randomUUID().toString();
        String userInput = request.getOrDefault("input", "分析AI技术发展趋势");
        
        // 异步执行模拟流程
        CompletableFuture.runAsync(() -> {
            try {
                simulateThinkDoReflectFlow(sessionId, userInput);
            } catch (Exception e) {
                log.error("模拟执行失败", e);
            }
        });
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("sessionId", sessionId);
        response.put("message", "Think-Do-Reflect工作流模拟已启动");
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 模拟多Agent协作执行
     */
    @PostMapping("/simulate/multi-agent")
    @Operation(summary = "模拟多Agent协作", description = "模拟多个Agent协作执行任务")
    public ResponseEntity<Map<String, Object>> simulateMultiAgent(@RequestBody Map<String, String> request) {
        String sessionId = UUID.randomUUID().toString();
        String userInput = request.getOrDefault("input", "搜索并分析机器学习论文");
        
        // 异步执行模拟流程
        CompletableFuture.runAsync(() -> {
            try {
                simulateMultiAgentFlow(sessionId, userInput);
            } catch (Exception e) {
                log.error("模拟执行失败", e);
            }
        });
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("sessionId", sessionId);
        response.put("message", "多Agent协作模拟已启动");
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 模拟错误处理流程
     */
    @PostMapping("/simulate/error-flow")
    @Operation(summary = "模拟错误处理", description = "模拟Agent执行过程中的错误处理")
    public ResponseEntity<Map<String, Object>> simulateErrorFlow(@RequestBody Map<String, String> request) {
        String sessionId = UUID.randomUUID().toString();
        String userInput = request.getOrDefault("input", "执行一个会失败的任务");
        
        // 异步执行模拟流程
        CompletableFuture.runAsync(() -> {
            try {
                simulateErrorHandlingFlow(sessionId, userInput);
            } catch (Exception e) {
                log.error("模拟执行失败", e);
            }
        });
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("sessionId", sessionId);
        response.put("message", "错误处理流程模拟已启动");
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 模拟Think-Do-Reflect执行流程
     */
    private void simulateThinkDoReflectFlow(String sessionId, String userInput) throws InterruptedException {
        // 1. Supervisor Agent 开始
        executionTracker.startAgentExecution(sessionId, "supervisor", "SupervisorAgent", 
            Map.of("input", userInput, "phase", "starting"));
        Thread.sleep(500);
        
        // 2. Thinking Agent 执行
        executionTracker.startAgentExecution(sessionId, "thinking_agent", "ThinkingAgent", 
            Map.of("task", "分析任务", "input", userInput));
        Thread.sleep(2000);
        executionTracker.endAgentExecution(sessionId, "thinking_agent", "ThinkingAgent", 
            "任务分析完成：需要搜索AI技术趋势信息，然后进行数据分析和报告生成", 
            AgentExecutionEvent.ExecutionStatus.SUCCESS);
        
        // 3. Search Agent 执行
        executionTracker.startAgentExecution(sessionId, "search_agent", "SearchAgent", 
            Map.of("query", "AI技术发展趋势 2024", "sources", "学术论文,新闻报道"));
        Thread.sleep(3000);
        executionTracker.recordToolCall(sessionId, "search_agent", "searchWeb", 
            "AI技术发展趋势 2024", "找到15篇相关文章和报告");
        executionTracker.endAgentExecution(sessionId, "search_agent", "SearchAgent", 
            "搜索完成：收集到AI技术趋势相关信息，包括大语言模型、多模态AI、边缘计算等领域的最新进展", 
            AgentExecutionEvent.ExecutionStatus.SUCCESS);
        
        // 4. Code Agent 执行数据分析
        executionTracker.startAgentExecution(sessionId, "code_agent", "CodeAgent", 
            Map.of("task", "数据分析", "data", "搜索结果数据"));
        Thread.sleep(2500);
        executionTracker.recordToolCall(sessionId, "code_agent", "executePython", 
            "import pandas as pd\n# 分析AI技术趋势数据", "生成了趋势分析图表和统计数据");
        executionTracker.endAgentExecution(sessionId, "code_agent", "CodeAgent", 
            "数据分析完成：生成了AI技术发展趋势的可视化图表和关键指标统计", 
            AgentExecutionEvent.ExecutionStatus.SUCCESS);
        
        // 5. Reflection Agent 评估
        executionTracker.startAgentExecution(sessionId, "reflection_agent", "ReflectionAgent", 
            Map.of("results", "分析结果", "original_request", userInput));
        Thread.sleep(1500);
        executionTracker.endAgentExecution(sessionId, "reflection_agent", "ReflectionAgent", 
            "STATUS: COMPLETE - 任务已完成，生成了完整的AI技术发展趋势分析报告", 
            AgentExecutionEvent.ExecutionStatus.SUCCESS);
        
        // 6. Supervisor Agent 结束
        executionTracker.endAgentExecution(sessionId, "supervisor", "SupervisorAgent", 
            "Think-Do-Reflect工作流执行完成，生成了AI技术发展趋势分析报告", 
            AgentExecutionEvent.ExecutionStatus.SUCCESS);
    }
    
    /**
     * 模拟多Agent协作执行流程
     */
    private void simulateMultiAgentFlow(String sessionId, String userInput) throws InterruptedException {
        // 1. Omni Agent 开始
        executionTracker.startAgentExecution(sessionId, "omni_agent", "AgentOmni", 
            Map.of("input", userInput));
        Thread.sleep(800);
        
        // 2. 调用搜索工具
        executionTracker.recordToolCall(sessionId, "omni_agent", "searchWeb", 
            "machine learning papers 2024", "找到20篇最新机器学习论文");
        Thread.sleep(2000);
        
        // 3. 调用文件工具保存结果
        executionTracker.recordToolCall(sessionId, "omni_agent", "writeFile", 
            Map.of("path", "ml_papers_2024.txt", "content", "论文列表和摘要"), 
            "文件保存成功");
        Thread.sleep(1000);
        
        // 4. 调用Python工具进行分析
        executionTracker.recordToolCall(sessionId, "omni_agent", "executePython", 
            "# 分析论文主题和趋势\nimport matplotlib.pyplot as plt", 
            "生成了论文主题分布图和趋势分析");
        Thread.sleep(2500);
        
        // 5. Omni Agent 完成
        executionTracker.endAgentExecution(sessionId, "omni_agent", "AgentOmni", 
            "多Agent协作完成：搜索了最新机器学习论文，进行了主题分析，并生成了可视化报告", 
            AgentExecutionEvent.ExecutionStatus.SUCCESS);
    }
    
    /**
     * 模拟错误处理执行流程
     */
    private void simulateErrorHandlingFlow(String sessionId, String userInput) throws InterruptedException {
        // 1. Supervisor Agent 开始
        executionTracker.startAgentExecution(sessionId, "supervisor", "SupervisorAgent", 
            Map.of("input", userInput));
        Thread.sleep(500);
        
        // 2. Thinking Agent 执行
        executionTracker.startAgentExecution(sessionId, "thinking_agent", "ThinkingAgent", 
            Map.of("task", "分析任务", "input", userInput));
        Thread.sleep(1500);
        executionTracker.endAgentExecution(sessionId, "thinking_agent", "ThinkingAgent", 
            "任务分析完成：需要执行一个复杂的计算任务", 
            AgentExecutionEvent.ExecutionStatus.SUCCESS);
        
        // 3. Code Agent 执行失败
        executionTracker.startAgentExecution(sessionId, "code_agent", "CodeAgent", 
            Map.of("task", "复杂计算", "code", "有问题的代码"));
        Thread.sleep(2000);
        executionTracker.recordAgentError(sessionId, "code_agent", "CodeAgent", 
            "执行失败：代码中存在语法错误 - NameError: name 'undefined_variable' is not defined");
        
        // 4. Reflection Agent 评估错误
        executionTracker.startAgentExecution(sessionId, "reflection_agent", "ReflectionAgent", 
            Map.of("error", "代码执行失败", "original_request", userInput));
        Thread.sleep(1000);
        executionTracker.endAgentExecution(sessionId, "reflection_agent", "ReflectionAgent", 
            "STATUS: INCOMPLETE - 需要修复代码错误并重新执行", 
            AgentExecutionEvent.ExecutionStatus.SUCCESS);
        
        // 5. Code Agent 重新执行（修复后）
        executionTracker.startAgentExecution(sessionId, "code_agent", "CodeAgent", 
            Map.of("task", "修复后的计算", "code", "修复后的代码"));
        Thread.sleep(1800);
        executionTracker.recordToolCall(sessionId, "code_agent", "executePython", 
            "# 修复后的代码\nresult = 42 * 2", "计算结果：84");
        executionTracker.endAgentExecution(sessionId, "code_agent", "CodeAgent", 
            "代码执行成功：计算任务完成，结果为84", 
            AgentExecutionEvent.ExecutionStatus.SUCCESS);
        
        // 6. Supervisor Agent 结束
        executionTracker.endAgentExecution(sessionId, "supervisor", "SupervisorAgent", 
            "错误处理流程完成：成功修复了代码错误并完成了计算任务", 
            AgentExecutionEvent.ExecutionStatus.SUCCESS);
    }
    
    /**
     * 健康检查接口
     */
    @GetMapping("/health")
    @Operation(summary = "健康检查", description = "检查测试服务的健康状态")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> healthStatus = Map.of(
            "status", "healthy",
            "service", "Agent Monitor Test Service",
            "timestamp", System.currentTimeMillis()
        );
        return ResponseEntity.ok(healthStatus);
    }
}
