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
     * 慢思考模式 - Think-Do-Reflect工作流
     * 
     * 适用场景：
     * 1. 复杂任务需要规划和分析
     * 2. 需要深度思考的问题
     * 3. 需要验证和反思结果的场景
     *
     * @param request 包含用户输入的请求
     * @param sync 是否同步执行
     * @return 执行结果
     */
    @PostMapping("/think-do-reflect")
    @Operation(
        summary = "Deep Thinking Mode", 
        description = "Structured Think-Do-Reflect workflow for complex tasks. " +
                      "Includes task analysis, planning, execution and reflection phases."
    )
    public CompletableFuture<ResponseEntity<Map<String, Object>>> thinkDoReflect(
            @RequestBody Map<String, String> request,
            @RequestParam(defaultValue = "false") boolean sync) {

        String userInput = request.get("input");
        return thinkDoReflectService.executeWorkflow(userInput, sync)
            .thenApply(result -> {
                if (result.containsKey("error")) {
                    return ResponseEntity.badRequest().body(result);
                }
                return ResponseEntity.ok(result);
            });
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

    /**
     * 智能路由 - 自动选择合适的思考模式
     * 
     * 系统会根据任务复杂度自动选择使用快思考或慢思考模式
     *
     * @param request 包含用户输入的请求
     * @param sync 是否同步执行
     * @return 执行结果
     */
    @PostMapping("/auto")
    @Operation(
        summary = "Auto Mode Selection", 
        description = "Automatically selects between fast and deep thinking modes based on task complexity."
    )
    public CompletableFuture<ResponseEntity<Map<String, Object>>> autoMode(
            @RequestBody Map<String, String> request,
            @RequestParam(defaultValue = "false") boolean sync) {
        
        String userInput = request.get("input");
        
        // 简单启发式：如果输入包含某些关键词，使用慢思考模式，否则使用快思考模式
        // 这里可以实现更复杂的逻辑来决定使用哪种模式
        boolean needsDeepThinking = userInput.toLowerCase().matches(".*(分析|计划|复杂|思考|规划|设计|优化|研究|比较|评估).*");
        
        if (needsDeepThinking) {
            log.info("Using deep thinking mode for: {}", userInput);
            return thinkDoReflect(request, sync);
        } else {
            log.info("Using fast thinking mode for: {}", userInput);
            Map<String, String> chatPayload = Map.of("message", userInput);
            return chat(chatPayload, false, sync);
        }
    }
} 