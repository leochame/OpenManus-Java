package com.openmanus.java.controller;
import com.openmanus.java.omni.tool.OmniToolCatalog;
import com.openmanus.java.workflow.MultiAgentHandoffWorkflow;
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

//        String ans =  multiAgentHandoffWorkflow.execute(message);
//        log.info(ans);
        return multiAgentHandoffWorkflow.execute(message).thenApply(response -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("answer", response);
                    result.put("conversationId", conversationId);
                    result.put("timestamp", LocalDateTime.now().toString());
                    return ResponseEntity.ok(result);
                });
//        return AgentOmni.invoke(conversationId, message)
//                .thenApply(response -> {
//                    Map<String, Object> result = new HashMap<>();
//                    result.put("answer", response);
//                    result.put("conversationId", conversationId);
//                    result.put("timestamp", LocalDateTime.now().toString());
//                    return ResponseEntity.ok(result);
//                })
//                .exceptionally(ex -> {
//                    Map<String, Object> errorResult = new HashMap<>();
//                    errorResult.put("error", "Agent execution failed: " + ex.getMessage());
//                    errorResult.put("conversationId", conversationId);
//                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResult);
//                });
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