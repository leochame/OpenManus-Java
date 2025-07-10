package com.openmanus.java.nodes;

import com.openmanus.java.state.OpenManusAgentState;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Think Node - React框架的思考节点
 * 
 * 负责分析用户问题，制定解决计划，决定下一步行动
 */
@Component
public class ThinkNode implements AsyncNodeAction<OpenManusAgentState> {
    
    private static final Logger logger = LoggerFactory.getLogger(ThinkNode.class);
    
    private final ChatModel chatModel;
    
    // 思考提示词模板
    private static final PromptTemplate THINK_PROMPT = PromptTemplate.from("""
        你是一个智能助手，正在使用ReAct（Reasoning and Acting）框架来解决问题。
        
        当前情况：
        - 用户问题: {{user_input}}
        - 推理步骤: {{iteration}}/{{max_iterations}}
        - 之前的推理: {{previous_reasoning}}
        - 之前的观察: {{previous_observations}}
        
        请进行深度思考分析：
        
        1. **问题理解**: 分析用户问题的核心需求和目标
        2. **问题分类**: 判断问题类型（简单计算/复杂计算/信息查询/工具操作等）
        3. **现状评估**: 基于已有信息和观察结果评估当前进展
        4. **解决策略**: 
           - 对于简单问题（如基础数学计算），直接给出答案
           - 对于复杂问题，决定是否需要使用工具
        5. **工具选择**: 如果需要使用工具，选择最合适的工具
        
        可用工具：
        - executePython: 执行Python代码进行计算、数据处理等
        - readFile: 读取文件内容
        - writeFile: 写入文件内容
        - listDirectory: 列出目录内容
        - browseWeb: 浏览网页获取信息
        - searchWeb: 搜索网络信息
        - askHuman: 询问用户以获取更多信息
        
        请以以下格式回答：
        
        **思考分析:**
        [详细的思考过程，包括问题分析、策略制定等]
        
        **问题类型:**
        [简单计算/复杂计算/信息查询/工具操作]
        
        **解决方案:**
        - 方案类型: [直接回答/工具调用/需要更多信息]
        - 具体计划: [具体要做什么]
        - 预期结果: [希望得到什么结果]
        
        **如果是直接回答:**
        DIRECT_ANSWER: [答案内容]
        
        **如果需要工具调用:**
        - 工具名称: [工具名]
        - 参数: [具体参数]
        - 理由: [为什么选择这个工具]
        """);
    
    @Autowired
    public ThinkNode(ChatModel chatModel) {
        this.chatModel = chatModel;
    }
    
    @Override
    public CompletableFuture<Map<String, Object>> apply(OpenManusAgentState state) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("开始思考阶段 - 用户问题: {}", state.getUserInput());
                
                // 检查是否已达到最大迭代次数
                if (state.isMaxIterationsReached()) {
                    logger.warn("达到最大推理次数: {}", state.getMaxIterations());
                    Map<String, Object> errorUpdates = new HashMap<>();
                    errorUpdates.put("error", "推理次数超过限制，无法继续处理");
                    Map<String, Object> errorReasoning = new HashMap<>();
                    errorReasoning.put("type", "error");
                    errorReasoning.put("content", "达到最大推理次数限制");
                    errorUpdates.put("reasoning_steps", errorReasoning);
                    return errorUpdates;
                }
                
                // 准备思考提示词的参数
                Map<String, Object> promptVariables = new HashMap<>();
                promptVariables.put("user_input", state.getUserInput());
                promptVariables.put("iteration", state.getIterationCount());
                promptVariables.put("max_iterations", state.getMaxIterations());
                promptVariables.put("previous_reasoning", formatPreviousReasoning(state));
                promptVariables.put("previous_observations", formatPreviousObservations(state));
                
                // 生成思考提示词
                Prompt prompt = THINK_PROMPT.apply(promptVariables);
                
                // 调用LLM进行思考
                logger.debug("调用LLM进行思考分析...");
                String thinkingResult = chatModel.chat(prompt.text());
                
                // 记录思考结果
                logger.info("思考完成，结果长度: {} 字符", thinkingResult.length());
                
                // 分析思考结果，决定下一步行动
                String nextAction = analyzeThinkingResult(thinkingResult);
                
                logger.info("思考节点完成 - 下一步行动: {}", nextAction);
                
                // 返回状态更新
                Map<String, Object> updates = new HashMap<>();
                updates.put("current_state", "thinking");
                Map<String, Object> reasoningStep = new HashMap<>();
                reasoningStep.put("type", "thinking");
                reasoningStep.put("content", thinkingResult);
                updates.put("reasoning_steps", reasoningStep);
                updates.put("iteration_count", state.getIterationCount() + 1);
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("last_think_result", thinkingResult);
                metadata.put("next_action", nextAction);
                updates.put("metadata", metadata);
                return updates;
                
            } catch (Exception e) {
                logger.error("思考节点执行失败", e);
                Map<String, Object> errorUpdates = new HashMap<>();
                errorUpdates.put("error", "思考过程中发生错误: " + e.getMessage());
                Map<String, Object> errorReasoning = new HashMap<>();
                errorReasoning.put("type", "error");
                errorReasoning.put("content", "思考失败: " + e.getMessage());
                errorUpdates.put("reasoning_steps", errorReasoning);
                return errorUpdates;
            }
        });
    }
    
    /**
     * 格式化之前的推理步骤
     */
    private String formatPreviousReasoning(OpenManusAgentState state) {
        StringBuilder sb = new StringBuilder();
        var steps = state.getReasoningSteps();
        
        if (steps.isEmpty()) {
            return "无之前推理记录";
        }
        
        int displayCount = Math.min(steps.size(), 3); // 只显示最近3步
        for (int i = steps.size() - displayCount; i < steps.size(); i++) {
            Map<String, Object> step = steps.get(i);
            sb.append(String.format("- [%s] %s\n", 
                step.get("type"), 
                truncateText((String) step.get("content"), 200)));
        }
        
        return sb.toString().trim();
    }
    
    /**
     * 格式化之前的观察结果
     */
    private String formatPreviousObservations(OpenManusAgentState state) {
        StringBuilder sb = new StringBuilder();
        var observations = state.getObservations();
        
        if (observations.isEmpty()) {
            return "无观察记录";
        }
        
        int displayCount = Math.min(observations.size(), 2); // 只显示最近2个观察
        for (int i = observations.size() - displayCount; i < observations.size(); i++) {
            String obs = observations.get(i);
            sb.append(String.format("- %s\n", 
                truncateText(obs, 150)));
        }
        
        return sb.toString().trim();
    }
    
    /**
     * 分析思考结果，确定下一步行动
     */
    private String analyzeThinkingResult(String thinkingResult) {
        String lowerResult = thinkingResult.toLowerCase();
        
        // 检查是否有直接答案
        if (lowerResult.contains("direct_answer:")) {
            return "direct_answer";
        }
        
        // 检查是否需要工具调用
        if (lowerResult.contains("executepython") || lowerResult.contains("python")) {
            return "tool_call_python";
        } else if (lowerResult.contains("readfile") || lowerResult.contains("读取文件")) {
            return "tool_call_file";
        } else if (lowerResult.contains("writefile") || lowerResult.contains("写入文件")) {
            return "tool_call_file";
        } else if (lowerResult.contains("browseweb") || lowerResult.contains("浏览网页")) {
            return "tool_call_web";
        } else if (lowerResult.contains("searchweb") || lowerResult.contains("搜索网络")) {
            return "tool_call_web";
        } else if (lowerResult.contains("askhuman") || lowerResult.contains("询问用户")) {
            return "need_info";
        }
        
        // 默认继续思考
        return "continue_thinking";
    }
    
    /**
     * 截断文本到指定长度
     */
    private String truncateText(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }
}