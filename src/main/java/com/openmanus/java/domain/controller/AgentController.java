package com.openmanus.java.domain.controller;
import com.openmanus.java.agent.tool.BrowserTool;
import com.openmanus.java.agent.tool.OmniToolCatalog;
import com.openmanus.java.agent.workflow.MultiAgentHandoffWorkflow;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * REST controller for interacting with the AgentOmni.
 * Provides endpoints for both stateful (multi-turn) and stateless (single-turn) conversations.
 */
@RestController
@RequestMapping("/api/agent")
@Tag(name = "Agent API", description = "Web API interface for intelligent agent")
@Slf4j
public class AgentController {

    private final MultiAgentHandoffWorkflow multiAgentHandoffWorkflow;
    private final OmniToolCatalog omniToolCatalog;
    
    @Autowired
    public AgentController(MultiAgentHandoffWorkflow multiAgentHandoffWorkflow, OmniToolCatalog omniToolCatalog) {
        this.multiAgentHandoffWorkflow = multiAgentHandoffWorkflow;
        this.omniToolCatalog = omniToolCatalog;
    }

    /**
     * 测试工具识别的端点
     */
    @GetMapping("/test-tools")
    @Operation(summary = "Test Tools", description = "Test if tools are properly recognized.")
    public ResponseEntity<Map<String, Object>> testTools() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 获取工具列表
            var tools = omniToolCatalog.getTools();
            result.put("toolsCount", tools.size());
            result.put("toolsInfo", tools.stream().map(tool -> 
                Map.of(
                    "className", tool.getClass().getSimpleName(),
                    "fullClassName", tool.getClass().getName()
                )
            ).toList());
            
            // 直接测试BrowserTool的searchWeb方法
            var browserTool = tools.stream()
                .filter(tool -> tool.getClass().getSimpleName().equals("BrowserTool"))
                .findFirst();
                
            if (browserTool.isPresent()) {
                result.put("browserToolFound", true);
                try {
                    // 直接调用searchWeb方法进行测试
                    var tool = (BrowserTool) browserTool.get();
                    String searchResult = tool.searchWeb("test search");
                    result.put("directSearchTest", "Success: " + searchResult.substring(0, Math.min(200, searchResult.length())));
                } catch (Exception e) {
                    result.put("directSearchTest", "Error: " + e.getMessage());
                }
            } else {
                result.put("browserToolFound", false);
            }
            
            result.put("status", "success");
        } catch (Exception e) {
            log.error("Tool test failed", e);
            result.put("status", "error");
            result.put("error", e.getMessage());
        }
        
        return ResponseEntity.ok(result);
    }


    /**
     * Handles stateful, multi-turn conversations with the agent.
     * A `conversationId` is required to maintain context across multiple requests.
     *
     * @param payload A map containing "conversationId" and "message".
     * @return A CompletableFuture wrapping the agent's response.
     */
    @PostMapping("/chat/multi_turn")
    @Operation(summary = "Stateful Chat", description = "Handles multi-turn conversations using a conversation ID.")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> multiTurnChat(@RequestBody Map<String, String> payload) {
        String conversationId = payload.get("conversationId");
        String message = payload.get("message");

        return multiAgentHandoffWorkflow.execute(message).thenApply(response -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("answer", response);
                    result.put("conversationId", conversationId);
                    result.put("timestamp", LocalDateTime.now().toString());
                    return ResponseEntity.ok(result);
                });
    }

    /**
     * Handles a stateless, single-turn chat.
     * A new random conversationId is generated for each call.
     *
     * @param payload A map containing "message".
     * @return A CompletableFuture wrapping the agent's response.
     */
    @PostMapping("/chat")
    @Operation(summary = "Stateless Chat", description = "Handles a single-turn conversation.")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> statelessChat(@RequestBody Map<String, String> payload) {
        String message = payload.get("message");
        String conversationId = UUID.randomUUID().toString(); // Create a new conversation for each call
        return multiTurnChat(Map.of("conversationId", conversationId, "message", message));
    }
} 