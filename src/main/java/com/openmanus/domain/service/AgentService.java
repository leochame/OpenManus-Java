package com.openmanus.domain.service;

import com.openmanus.agent.workflow.FastThinkWorkflow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 快思考服务 - 处理需要快速响应的简单任务
 * 
 * 这个服务基于MultiAgentHandoffWorkflow（快思考工作流），专注于：
 * 1. 简单明确的任务
 * 2. 需要快速响应的场景
 * 3. 对话式交互
 * 
 * 与ThinkDoReflectService的区别：
 * - AgentService：直接执行，响应迅速，适合简单任务
 * - ThinkDoReflectService：分析规划、执行、反思，适合复杂任务
 */
@Service
@Slf4j
public class AgentService {

    private final FastThinkWorkflow fastThinkWorkflow;

    @Autowired
    public AgentService(FastThinkWorkflow fastThinkWorkflow) {
        this.fastThinkWorkflow = fastThinkWorkflow;
    }

    /**
     * 快速处理与Agent的对话 - 快思考模式
     * 适合简单明确的任务和对话式交互
     * 
     * @param message 用户消息
     * @param conversationId 会话ID，如果为null则创建新会话
     * @param sync 是否同步执行
     * @return 对话结果
     */
    public CompletableFuture<Map<String, Object>> chat(String message, String conversationId, boolean sync) {
        final String sessionId = conversationId != null ? conversationId : UUID.randomUUID().toString();
        
        if (sync) {
            // 同步执行
            try {
                String response = fastThinkWorkflow.executeSync(message);
                Map<String, Object> result = new HashMap<>();
                result.put("answer", response);
                result.put("conversationId", sessionId);
                result.put("timestamp", LocalDateTime.now().toString());
                result.put("mode", "fast_thinking");
                return CompletableFuture.completedFuture(result);
            } catch (Exception e) {
                log.error("Error in sync chat execution", e);
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("error", e.getMessage());
                errorResult.put("conversationId", sessionId);
                return CompletableFuture.completedFuture(errorResult);
            }
        } else {
            // 异步执行
            return fastThinkWorkflow.execute(message)
                .thenApply(response -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("answer", response);
                    result.put("conversationId", sessionId);
                    result.put("timestamp", LocalDateTime.now().toString());
                    result.put("mode", "fast_thinking");
                    return result;
                })
                .exceptionally(e -> {
                    log.error("Error in async chat execution", e);
                    Map<String, Object> errorResult = new HashMap<>();
                    errorResult.put("error", e.getMessage());
                    errorResult.put("conversationId", sessionId);
                    return errorResult;
                });
        }
    }
} 