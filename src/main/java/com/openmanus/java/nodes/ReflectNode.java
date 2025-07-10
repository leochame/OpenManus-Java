package com.openmanus.java.nodes;

import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.openmanus.java.state.OpenManusAgentState;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.input.Prompt;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.HashMap;

/**
 * 反思节�? - 负责分析Agent的推理过程并提供改进建议
 * 
 * 反思机制包括：
 * - 分析推理步骤的有效�?
 * - 识别可能的错误或改进�?
 * - 生成反思和建议
 * - 决定是否需要调整策�?
 */
@Component
public class ReflectNode implements AsyncNodeAction<OpenManusAgentState> {
    
    private static final Logger logger = LoggerFactory.getLogger(ReflectNode.class);
    
    @Autowired
    private ChatModel chatModel;
    
    @Override
    public CompletableFuture<Map<String, Object>> apply(OpenManusAgentState state) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Starting reflection phase - Iteration: {}", state.getIterationCount());
                
                // Build reflection prompt
                String reflectionPrompt = buildReflectionPrompt(state);
                
                // Call LLM for reflection
                Prompt prompt = Prompt.from(reflectionPrompt);
                String reflection = chatModel.chat(prompt.text());
                
                logger.debug("Reflection result: {}", reflection);
                
                // Return reflection updates
                Map<String, Object> updates = new HashMap<>();
                updates.put("current_state", "reflecting");
                updates.put("reflections", reflection);
                updates.put("reasoning_steps", Map.of(
                    "type", "reflection", 
                    "content", reflection
                ));
                
                return updates;
                
            } catch (Exception e) {
                logger.error("Reflection node execution failed", e);
                return Map.of(
                    "error", "Reflection failed: " + e.getMessage(),
                    "reasoning_steps", Map.of("type", "error", "content", "Reflection failed: " + e.getMessage())
                );
            }
        });
    }
    
    /**
     * 构建反思提�?
     */
    private String buildReflectionPrompt(OpenManusAgentState state) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("请对以下AI Agent的推理过程进行反思和分析：\n\n");
        
        // 添加用户输入
        prompt.append("=== 原始任务 ===\n");
        prompt.append(state.getUserInput()).append("\n\n");
        
        // 添加当前推理状�?
        prompt.append("=== 当前推理状�? ===\n");
        prompt.append("迭代次数: ").append(state.getIterationCount()).append("\n");
        prompt.append("当前步骤: ").append(state.getCurrentStep()).append("\n");
        prompt.append("最新思�?: ").append(state.getCurrentThought()).append("\n\n");
        
        // 添加工具调用历史
        if (!state.getToolCalls().isEmpty()) {
            prompt.append("=== 工具调用历史 ===\n");
            state.getToolCalls().forEach(toolCall -> {
                prompt.append("- ").append(toolCall.toString()).append("\n");
            });
            prompt.append("\n");
        }
        
        // 添加观察结果
        if (!state.getObservations().isEmpty()) {
            prompt.append("=== 观察结果 ===\n");
            state.getObservations().forEach(obs -> {
                prompt.append("- ").append(obs).append("\n");
            });
            prompt.append("\n");
        }
        
        // 添加反思指�?
        prompt.append("=== 反思要�? ===\n");
        prompt.append("请从以下几个方面进行反思：\n");
        prompt.append("1. 推理路径是否合理和高效？\n");
        prompt.append("2. 工具使用是否恰当？\n");
        prompt.append("3. 是否遗漏了重要信息？\n");
        prompt.append("4. 当前方法是否需要调整？\n");
        prompt.append("5. 如何提高解决问题的效率？\n\n");
        
        prompt.append("请提供具体的反思和改进建议，格式如下：\n");
        prompt.append("反�?: [你的分析]\n");
        prompt.append("建议: [改进建议]\n");
        prompt.append("优先�?: [�?/�?/低]\n");
        
        return prompt.toString();
    }
}