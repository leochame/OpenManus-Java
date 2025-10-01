package com.openmanus.domain.controller;
import com.openmanus.domain.model.WorkflowRequest;
import com.openmanus.domain.model.WorkflowResponse;
import com.openmanus.domain.service.AgentService;
import com.openmanus.domain.service.ThinkDoReflectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * REST controller for interacting with Agent capabilities.
 * Provides endpoints for both fast thinking and deep thinking workflows.
 */
@RestController
@RequestMapping("/api/agent")
@Tag(name = "Agent API", description = "Web API interface for intelligent agent")
@CrossOrigin(origins = "*")
@Slf4j
public class AgentController {

    private final AgentService agentService;
    private final ThinkDoReflectService thinkDoReflectService;
    
    @Autowired
    public AgentController(AgentService agentService, ThinkDoReflectService thinkDoReflectService) {
        this.agentService = agentService;
        this.thinkDoReflectService = thinkDoReflectService;
    }
    /**
     * 快思考模式 - 处理简单明确的任务，快速响应
     * 
     * 适用场景：
     * 1. 简单明确的任务
     * 2. 需要快速响应的场景
     * 3. 对话式交互
     *
     * @param payload 包含"message"和可选的"conversationId"
     * @param stateful 是否保持会话状态
     * @param sync 是否同步执行
     * @return Agent的响应
     */
    @PostMapping("/chat")
    @Operation(
        summary = "Fast Thinking Mode", 
        description = "Quick response mode for simple tasks and conversational interactions. " +
                      "Uses direct execution without complex planning or reflection."
    )
    public CompletableFuture<ResponseEntity<Map<String, Object>>> chat(
            @RequestBody Map<String, String> payload,
            @RequestParam(defaultValue = "false") boolean stateful,
            @RequestParam(defaultValue = "false") boolean sync) {
        
        String message = payload.get("message");
        String conversationId = stateful ? payload.get("conversationId") : null;
        
        return agentService.chat(message, conversationId, sync)
            .thenApply(ResponseEntity::ok);
    }

    /**
     * 慢思考模式（流式） - Think-Do-Reflect工作流
     *
     * @param request 包含用户输入的请求
     * @return 立即返回一个包含sessionId的响应，用于WebSocket订阅
     */
    @PostMapping("/think-do-reflect-stream")
    @Operation(
            summary = "Deep Thinking Mode (Streaming)",
            description = "Initiates the Think-Do-Reflect workflow and returns a session ID for streaming results over WebSocket."
    )
    public ResponseEntity<WorkflowResponse> thinkDoReflectStream(
            @RequestBody WorkflowRequest request) {
        String userInput = request.getInput();
        WorkflowResponse serviceResult = thinkDoReflectService.executeWorkflowAndStreamEvents(userInput);

        if (!serviceResult.isSuccess()) {
            return ResponseEntity.badRequest().body(serviceResult);
        }

        // 从服务层获取sessionId
        String sessionId = serviceResult.getSessionId();
        if (sessionId == null) {
            // 如果没有sessionId，返回一个错误
            return ResponseEntity.status(500).body(
                WorkflowResponse.builder()
                    .success(false)
                    .error("无法启动工作流：未生成会话ID")
                    .build()
            );
        }

        return ResponseEntity.ok(serviceResult);
    }
} 