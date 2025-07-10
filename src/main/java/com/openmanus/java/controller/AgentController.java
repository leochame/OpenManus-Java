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
 * 提供Agent的Web接口，支持工具调用和交互
 */
@RestController
@RequestMapping("/api/agent")
@Tag(name = "Agent API", description = "智能Agent的Web API接口")
public class AgentController {
    
    private static final Logger logger = LoggerFactory.getLogger(AgentController.class);
    
    @Autowired
    private ManusAgent manusAgent;
    
    /**
     * 健康检�?
     */
    @GetMapping("/health")
    @Operation(summary = "健康检�?", description = "检查Agent服务是否正常运行")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("message", "OpenManus Agent is running");
        response.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(response);
    }
    
    /**
     * 与Agent对话（简单版本）
     */
    @PostMapping("/chat")
    @Operation(summary = "Agent对话", description = "与Agent进行对话，支持工具调�?")
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
     * 与Agent对话（带COT推理过程�?
     */
    @PostMapping("/chat/cot")
    @Operation(summary = "Agent对话(COT)", description = "与Agent进行对话，返回推理过程和反�?")
    public ResponseEntity<Map<String, Object>> chatWithCOT(@RequestBody Map<String, String> request) {
        try {
            String message = request.get("message");
            if (message == null || message.trim().isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Message cannot be empty");
                return ResponseEntity.badRequest().body(error);
            }
            
            logger.info("Received user message (COT): {}", message);
            
            // 调用Agent处理消息（带COT�
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
     * 获取Agent信息
     */
    @GetMapping("/info")
    @Operation(summary = "Agent信息", description = "获取Agent的基本信�?")
    public ResponseEntity<Map<String, Object>> getAgentInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("name", "OpenManus Agent");
        info.put("version", "1.0.0");
        info.put("description", "基于langchain4j和langgraph4j的智能Agent");
        info.put("capabilities", new String[]{
            "Python代码执行",
            "文件操作",
            "网页访问",
            "任务反�?",
            "COT推理"
        });
        info.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(info);
    }
} 