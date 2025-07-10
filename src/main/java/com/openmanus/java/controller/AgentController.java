package com.openmanus.java.controller;

import com.openmanus.java.agent.ManusAgent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Agent REST API Controller
 * Provides web interface for Agent, supporting tool calls and interactions
 */
@RestController
@RequestMapping("/api/agent")
@Tag(name = "Agent API", description = "Web API interface for intelligent Agent")
public class AgentController {
    
    private static final Logger logger = LoggerFactory.getLogger(AgentController.class);
    
    @Autowired
    private ManusAgent manusAgent;
    
    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    @Operation(summary = "Health Check", description = "Check if Agent service is running normally")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("message", "OpenManus Agent is running");
        response.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(response);
    }
    
    /**
     * Chat with Agent (Simple version)
     */
    @PostMapping("/chat")
    @Operation(summary = "Agent Chat", description = "Chat with Agent, supporting tool calls")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, String> request) {
        try {
            String message = request.get("message");
            if (message == null || message.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Message cannot be empty"));
            }
            
            logger.info("Received user message (COT): {}", message);
            
            // Process with COT reasoning
            Map<String, Object> result = manusAgent.chatWithCot(message);
            
            logger.info("Agent response (COT): {}", result.get("answer"));
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("Error occurred while processing user message", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Internal server error: " + e.getMessage()));
        }
    }
    
    /**
     * Chat with Agent (With Chain of Thought reasoning)
     */
    @PostMapping("/chat/cot")
    @Operation(summary = "Agent Chat (COT)", description = "Chat with Agent, returns reasoning process and reflection")
    public ResponseEntity<Map<String, Object>> chatWithCOT(@RequestBody Map<String, String> request) {
        try {
            String message = request.get("message");
            if (message == null || message.trim().isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Message cannot be empty");
                return ResponseEntity.badRequest().body(error);
            }
            
            logger.info("Received user message (COT): {}", message);
            
            // Call Agent to process message (with COT)
            Map<String, Object> result = manusAgent.chatWithCot(message);
            result.put("timestamp", System.currentTimeMillis());
            
            logger.info("Agent response (COT): {}", result.get("answer"));
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("Error occurred while processing user message", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Internal server error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
    
    /**
     * Get Agent information
     */
    @GetMapping("/info")
    @Operation(summary = "Agent Info", description = "Get basic information about the Agent")
    public ResponseEntity<Map<String, Object>> getAgentInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("name", "OpenManus Agent");
        info.put("version", "1.0.0");
        info.put("description", "Intelligent Agent based on langchain4j and langgraph4j");
        info.put("capabilities", new String[]{
            "Python code execution",
            "File operations",
            "Web access",
            "Task reflection",
            "COT reasoning"
        });
        info.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(info);
    }
} 