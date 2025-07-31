package com.openmanus.java.domain.controller;

import com.openmanus.java.agent.workflow.ThinkDoReflectWorkflow;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Think-Do-Reflect系统的REST API控制器
 * 
 * 提供HTTP接口来测试和使用Think-Do-Reflect工作流。
 */
@RestController
@RequestMapping("/api/think-do-reflect")
@CrossOrigin(origins = "*")
public class ThinkDoReflectController {

    private final ThinkDoReflectWorkflow workflow;

    public ThinkDoReflectController(ThinkDoReflectWorkflow workflow) {
        this.workflow = workflow;
    }

    /**
     * 执行Think-Do-Reflect工作流
     *
     * @param request 包含用户输入的请求
     * @return 异步执行结果
     */
    @PostMapping("/execute")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> execute(
            @RequestBody Map<String, String> request) {
        
        String userInput = request.get("input");
        if (userInput == null || userInput.trim().isEmpty()) {
            Map<String, Object> errorBody = Map.of("error", "输入不能为空");
            return CompletableFuture.completedFuture(
                ResponseEntity.badRequest().body(errorBody)
            );
        }

        return workflow.execute(userInput)
                .thenApply(result -> {
                    Map<String, Object> responseBody = Map.of(
                        "success", true,
                        "result", result,
                        "input", userInput
                    );
                    return ResponseEntity.ok(responseBody);
                })
                .exceptionally(throwable -> {
                    Map<String, Object> errorBody = Map.of(
                        "success", false,
                        "error", throwable.getMessage(),
                        "input", userInput
                    );
                    return ResponseEntity.internalServerError().body(errorBody);
                });
    }

    /**
     * 健康检查接口
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> healthStatus = Map.of(
            "status", "healthy",
            "service", "Think-Do-Reflect Workflow",
            "timestamp", System.currentTimeMillis()
        );
        return ResponseEntity.ok(healthStatus);
    }
}
