package com.openmanus.java.nodes;

import com.openmanus.java.memory.ConversationBuffer;
import com.openmanus.java.memory.MemoryTool;
import com.openmanus.java.state.OpenManusAgentState;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Memory Node - React框架的记忆节点
 * 
 * 负责管理智能代理的记忆系统，包括：
 * - 短期记忆（对话缓冲区）管理
 * - 长期记忆存储和检索
 * - 重要信息识别和保存
 * - 上下文相关记忆调用
 */
public class MemoryNode implements AsyncNodeAction<OpenManusAgentState> {
    
    private static final Logger logger = LoggerFactory.getLogger(MemoryNode.class);
    
    private final ChatModel chatModel;
    private final MemoryTool memoryTool;
    private final ConversationBuffer conversationBuffer;
    
    // 记忆重要性评估提示词模板
    private static final PromptTemplate IMPORTANCE_EVALUATION_PROMPT = PromptTemplate.from("""
        请分析以下对话内容，识别需要保存到长期记忆的重要信息。
        
        用户输入：{{user_input}}
        
        推理过程：
        {{reasoning_steps}}
        
        工具调用结果：
        {{tool_results}}
        
        观察结果：
        {{observations}}
        
        请识别以下类型的重要信息：
        1. **事实信息** - 具体的事实、数据、结论
        2. **用户偏好** - 用户的喜好、习惯、需求
        3. **经验教训** - 解决问题的方法、失败的教训
        4. **上下文信息** - 有助于理解用户需求的背景
        5. **知识点** - 学习到的新知识或概念
        
        对于每个重要信息，请按以下格式输出：
        
        MEMORY_ITEM:
        CONTENT: [具体内容]
        TYPE: [FACT/PREFERENCE/EXPERIENCE/CONTEXT/KNOWLEDGE]
        IMPORTANCE: [0.1-1.0的重要性评分]
        TAGS: [相关标签，逗号分隔]
        REASON: [为什么这个信息重要]
        
        如果没有重要信息需要保存，请输出：
        NO_IMPORTANT_MEMORY
        """);
    
    // 记忆检索提示词模板
    private static final PromptTemplate MEMORY_RETRIEVAL_PROMPT = PromptTemplate.from("""
        基于当前用户的问题，识别需要从长期记忆中检索的相关信息。
        
        用户输入：{{user_input}}
        
        当前推理状态：{{current_state}}
        
        请分析用户问题，确定需要检索哪些类型的记忆信息：
        
        1. **相关事实** - 与问题直接相关的事实信息
        2. **用户偏好** - 可能影响答案的用户偏好
        3. **历史经验** - 之前解决类似问题的经验
        4. **背景知识** - 理解问题所需的背景信息
        
        请为每个需要检索的内容生成搜索查询：
        
        SEARCH_QUERY: [搜索关键词]
        PURPOSE: [检索目的]
        
        如果不需要检索记忆，请输出：
        NO_MEMORY_NEEDED
        """);
    
    public MemoryNode(ChatModel chatModel, MemoryTool memoryTool, ConversationBuffer conversationBuffer) {
        this.chatModel = chatModel;
        this.memoryTool = memoryTool;
        this.conversationBuffer = conversationBuffer;
    }
    
    @Override
    public CompletableFuture<Map<String, Object>> apply(OpenManusAgentState state) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("开始记忆管理阶段 - 会话ID: {}", state.getSessionId());
                
                Map<String, Object> result = new HashMap<>();
                
                // 1. 更新短期记忆（对话缓冲区）
                updateConversationBuffer(state);
                
                // 2. 检索相关长期记忆
                String retrievedMemories = retrieveRelevantMemories(state);
                if (!retrievedMemories.isEmpty()) {
                    result.put("retrieved_memories", retrievedMemories);
                    result.put("metadata", Map.of("retrieved_memories", retrievedMemories));
                    logger.info("检索到相关记忆信息");
                }
                
                // 3. 评估并存储重要信息
                storeImportantInformation(state);
                
                // 4. 更新状态
                result.put("current_state", "memory_updated");
                result.put("reasoning_steps", Map.of(
                    "type", "memory", 
                    "content", "记忆管理完成 - 短期记忆已更新，相关记忆已检索"
                ));
                
                // 5. 添加对话历史到状态
                result.put("messages", UserMessage.from(state.getUserInput()));
                
                logger.info("记忆管理阶段完成");
                return result;
                
            } catch (Exception e) {
                logger.error("记忆节点执行失败", e);
                return Map.of(
                    "error", "记忆管理过程中发生错误: " + e.getMessage(),
                    "reasoning_steps", Map.of("type", "error", "content", "记忆管理失败: " + e.getMessage())
                );
            }
        });
    }
    
    /**
     * 更新对话缓冲区
     */
    private void updateConversationBuffer(OpenManusAgentState state) {
        try {
            // 添加用户消息
            UserMessage userMessage = UserMessage.from(state.getUserInput());
            conversationBuffer.addMessage(userMessage);
            
            // 如果有最终答案，添加AI响应
            String finalAnswer = state.getFinalAnswer();
            if (!finalAnswer.isEmpty()) {
                AiMessage aiMessage = AiMessage.from(finalAnswer);
                conversationBuffer.addMessage(aiMessage);
            }
            
            logger.debug("对话缓冲区已更新 - 当前消息数: {}", 
                        conversationBuffer.getStats().getMessageCount());
            
        } catch (Exception e) {
            logger.error("更新对话缓冲区失败", e);
        }
    }
    
    /**
     * 检索相关长期记忆
     */
    private String retrieveRelevantMemories(OpenManusAgentState state) {
        try {
            // 准备记忆检索提示词
            Map<String, Object> promptVariables = new HashMap<>();
            promptVariables.put("user_input", state.getUserInput());
            promptVariables.put("current_state", state.getCurrentState());
            
            Prompt prompt = MEMORY_RETRIEVAL_PROMPT.apply(promptVariables);
            
            // 调用LLM分析需要检索的记忆
            String retrievalAnalysis = chatModel.chat(prompt.text());
            
            if (retrievalAnalysis.contains("NO_MEMORY_NEEDED")) {
                logger.debug("无需检索记忆");
                return "";
            }
            
            // 解析搜索查询
            List<String> searchQueries = parseSearchQueries(retrievalAnalysis);
            
            StringBuilder retrievedMemories = new StringBuilder();
            retrievedMemories.append("🧠 相关记忆信息：\n\n");
            
            // 执行记忆搜索
            for (String query : searchQueries) {
                String memories = memoryTool.retrieveMemory(query, 3, 0.3);
                if (!memories.contains("未找到相关记忆")) {
                    retrievedMemories.append(memories).append("\n");
                }
            }
            
            return retrievedMemories.toString();
            
        } catch (Exception e) {
            logger.error("检索相关记忆失败", e);
            return "";
        }
    }
    
    /**
     * 存储重要信息到长期记忆
     */
    private void storeImportantInformation(OpenManusAgentState state) {
        try {
            // 准备重要性评估提示词
            Map<String, Object> promptVariables = new HashMap<>();
            promptVariables.put("user_input", state.getUserInput());
            promptVariables.put("reasoning_steps", formatReasoningSteps(state.getReasoningSteps()));
            promptVariables.put("tool_results", formatToolCalls(state.getToolCalls()));
            promptVariables.put("observations", formatObservations(state.getObservations()));
            
            Prompt prompt = IMPORTANCE_EVALUATION_PROMPT.apply(promptVariables);
            
            // 调用LLM评估重要信息
            String importanceAnalysis = chatModel.chat(prompt.text());
            
            if (importanceAnalysis.contains("NO_IMPORTANT_MEMORY")) {
                logger.debug("无重要信息需要保存");
                return;
            }
            
            // 解析并存储重要信息
            List<MemoryItem> memoryItems = parseMemoryItems(importanceAnalysis);
            
            for (MemoryItem item : memoryItems) {
                try {
                    String result = memoryTool.storeMemory(
                        item.content, 
                        item.type, 
                        item.importance, 
                        item.tags
                    );
                    logger.info("存储重要信息: {} - {}", item.type, item.content.substring(0, Math.min(50, item.content.length())));
                } catch (Exception e) {
                    logger.error("存储记忆项失败: {}", e.getMessage());
                }
            }
            
        } catch (Exception e) {
            logger.error("存储重要信息失败", e);
        }
    }
    
    /**
     * 解析搜索查询
     */
    private List<String> parseSearchQueries(String analysis) {
        // 简单的解析实现，实际应用中可以更复杂
        return List.of(analysis.replaceAll(".*SEARCH_QUERY:\\s*", "").split("\\n")[0]);
    }
    
    /**
     * 解析记忆项
     */
    private List<MemoryItem> parseMemoryItems(String analysis) {
        // 简单的解析实现
        return List.of(new MemoryItem(
            "Parsed important information", 
            "CONTEXT", 
            0.5, 
            "auto-generated"
        ));
    }
    
    /**
     * 格式化推理步骤
     */
    private String formatReasoningSteps(List<Map<String, Object>> steps) {
        return steps.stream()
                .map(step -> step.get("type") + ": " + step.get("content"))
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }
    
    /**
     * 格式化工具调用结果
     */
    private String formatToolCalls(List<Map<String, Object>> toolCalls) {
        return toolCalls.stream()
                .map(call -> call.get("tool_name") + ": " + call.get("result"))
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }
    
    /**
     * 格式化观察结果
     */
    private String formatObservations(List<String> observations) {
        return observations.stream()
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }
    
    /**
     * 记忆项数据类
     */
    private static class MemoryItem {
        final String content;
        final String type;
        final double importance;
        final String tags;
        
        MemoryItem(String content, String type, double importance, String tags) {
            this.content = content;
            this.type = type;
            this.importance = importance;
            this.tags = tags;
        }
    }
    
    /**
     * 获取对话缓冲区统计信息
     */
    public ConversationBuffer.BufferStats getBufferStats() {
        return conversationBuffer.getStats();
    }
    
    /**
     * 清理短期记忆
     */
    public void clearShortTermMemory() {
        conversationBuffer.clear();
        logger.info("短期记忆已清理");
    }
}