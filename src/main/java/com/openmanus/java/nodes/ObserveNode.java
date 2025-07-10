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
 * Observe Node - React框架的观察节点
 * 
 * 负责分析行动结果，评估进展情况，决定是否需要继续推理或可以给出最终答案
 */
@Component
public class ObserveNode implements AsyncNodeAction<OpenManusAgentState> {
    
    private static final Logger logger = LoggerFactory.getLogger(ObserveNode.class);
    
    private final ChatModel chatModel;
    
    // 观察分析提示词模板
    private static final PromptTemplate OBSERVE_PROMPT = PromptTemplate.from("""
        作为一个智能助手，请分析刚才执行的行动结果，并决定下一步的推理方向。
        
        **用户原始问题：**
        {{user_input}}
        
        **当前推理进度：**
        - 推理步骤: {{iteration}}/{{max_iterations}}
        - 当前状态: {{current_state}}
        
        **最近的推理历史：**
        {{recent_reasoning}}
        
        **最新的行动结果：**
        {{latest_action_result}}
        
        **所有观察结果：**
        {{all_observations}}
        
        请基于以上信息进行深度分析：
        
        1. **结果评估**: 分析最新行动的执行结果是否有效
        2. **进展评估**: 评估当前是否已经获得足够信息来回答用户问题
        3. **问题解决度**: 判断用户问题的解决程度（0-100%）
        4. **下一步决策**: 决定是否需要继续推理、调用更多工具，还是可以给出答案
        
        请严格按照以下格式回答：
        
        **观察分析：**
        [详细分析行动结果和当前进展]
        
        **解决程度：**
        [数字]% - [简要说明]
        
        **下一步决策：**
        - 决策: [CONTINUE_THINKING/DIRECT_ANSWER/NEED_REFLECTION/ERROR]
        - 理由: [决策原因]
        - 建议: [如果继续推理，下一步应该做什么]
        
        **最终答案（如果决策是DIRECT_ANSWER）：**
        [基于所有信息给出的完整答案]
        """);
    
    @Autowired
    public ObserveNode(ChatModel chatModel) {
        this.chatModel = chatModel;
    }
    
    @Override
    public CompletableFuture<Map<String, Object>> apply(OpenManusAgentState state) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("开始观察阶段 - 分析行动结果");
                
                // 准备观察分析的参数
                Map<String, Object> promptVariables = new HashMap<>();
                promptVariables.put("user_input", state.getUserInput());
                promptVariables.put("iteration", state.getIterationCount());
                promptVariables.put("max_iterations", state.getMaxIterations());
                promptVariables.put("current_state", state.getCurrentState());
                promptVariables.put("recent_reasoning", formatRecentReasoning(state));
                promptVariables.put("latest_action_result", getLatestActionResult(state));
                promptVariables.put("all_observations", formatAllObservations(state));
                
                // 生成观察分析提示词
                Prompt prompt = OBSERVE_PROMPT.apply(promptVariables);
                
                // 调用LLM进行观察分析
                logger.debug("调用LLM进行观察分析...");
                String observationResult = chatModel.chat(prompt.text());
                
                logger.info("观察分析完成，结果长度: {} 字符", observationResult.length());
                
                // 解析观察结果
                ObservationDecision decision = parseObservationResult(observationResult);
                
                Map<String, Object> updates = new HashMap<>();
                updates.put("current_state", "observing");
                updates.put("reasoning_steps", Map.of("type", "observation", "content", observationResult));
                updates.put("metadata", Map.of(
                    "observation_decision", decision.decision,
                    "solution_progress", decision.solutionProgress
                ));
                
                // 根据决策更新状态
                switch (decision.decision.toLowerCase()) {
                    case "direct_answer":
                        if (decision.finalAnswer != null && !decision.finalAnswer.trim().isEmpty()) {
                            updates.put("final_answer", decision.finalAnswer);
                            logger.info("观察节点决定给出最终答案");
                        } else {
                            // 如果没有明确的答案，基于现有信息生成答案
                            String generatedAnswer = generateAnswerFromObservations(state);
                            updates.put("final_answer", generatedAnswer);
                            logger.info("观察节点生成最终答案");
                        }
                        break;
                        
                    case "continue_thinking":
                        updates.put("current_state", "continue_thinking");
                        Map<String, Object> metadata = new HashMap<>((Map<String, Object>) updates.get("metadata"));
                        metadata.put("next_suggestion", decision.suggestion);
                        updates.put("metadata", metadata);
                        logger.info("观察节点决定继续推理");
                        break;
                        
                    case "need_reflection":
                        updates.put("current_state", "need_reflection");
                        logger.info("观察节点决定需要反思");
                        break;
                        
                    case "error":
                        updates.put("error", "观察阶段发现错误: " + decision.reason);
                        logger.warn("观察节点发现错误: {}", decision.reason);
                        break;
                        
                    default:
                        logger.warn("未知的观察决策: {}", decision.decision);
                        updates.put("current_state", "continue_thinking");
                }
                
                return updates;
                
            } catch (Exception e) {
                logger.error("观察节点执行失败", e);
                Map<String, Object> errorUpdates = new HashMap<>();
                errorUpdates.put("error", "观察过程中发生错误: " + e.getMessage());
                Map<String, Object> errorReasoning = new HashMap<>();
                errorReasoning.put("type", "error");
                errorReasoning.put("content", "观察失败: " + e.getMessage());
                errorUpdates.put("reasoning_steps", errorReasoning);
                return errorUpdates;
            }
        });
    }
    
    /**
     * 格式化最近的推理步骤
     */
    private String formatRecentReasoning(OpenManusAgentState state) {
        StringBuilder sb = new StringBuilder();
        var steps = state.getReasoningSteps();
        
        if (steps.isEmpty()) {
            return "无推理历史";
        }
        
        // 显示最近5步推理
        int startIndex = Math.max(0, steps.size() - 5);
        for (int i = startIndex; i < steps.size(); i++) {
            Map<String, Object> step = steps.get(i);
            sb.append(String.format("%d. [%s] %s\n", 
                i + 1,
                step.get("type"), 
                truncateText((String) step.get("content"), 150)));
        }
        
        return sb.toString().trim();
    }
    
    /**
     * 获取最新的行动结果
     */
    private String getLatestActionResult(OpenManusAgentState state) {
        String result = (String) state.getMetadata().get("last_action_result");
        return result != null ? result : "无行动结果";
    }
    
    /**
     * 格式化所有观察结果
     */
    private String formatAllObservations(OpenManusAgentState state) {
        StringBuilder sb = new StringBuilder();
        var observations = state.getObservations();
        
        if (observations.isEmpty()) {
            return "无观察记录";
        }
        
        for (int i = 0; i < observations.size(); i++) {
            String obs = observations.get(i);
            sb.append(String.format("%d. %s\n", 
                i + 1, 
                truncateText(obs, 200)));
        }
        
        return sb.toString().trim();
    }
    
    /**
     * 解析观察结果
     */
    private ObservationDecision parseObservationResult(String result) {
        ObservationDecision decision = new ObservationDecision();
        
        // 解析解决程度
        var progressMatch = result.matches(".*解决程度：.*?(\\d+)%.*");
        if (progressMatch) {
            try {
                decision.solutionProgress = Integer.parseInt(
                    result.replaceAll(".*解决程度：.*?(\\d+)%.*", "$1"));
            } catch (NumberFormatException e) {
                decision.solutionProgress = 50; // 默认值
            }
        }
        
        // 解析决策
        if (result.toLowerCase().contains("决策: direct_answer") || 
            result.toLowerCase().contains("direct_answer")) {
            decision.decision = "direct_answer";
            
            // 提取最终答案
            String[] lines = result.split("\n");
            boolean inAnswerSection = false;
            StringBuilder answerBuilder = new StringBuilder();
            
            for (String line : lines) {
                if (line.contains("最终答案") && line.contains("：")) {
                    inAnswerSection = true;
                    continue;
                }
                if (inAnswerSection) {
                    if (line.trim().startsWith("**") || line.trim().isEmpty()) {
                        break;
                    }
                    answerBuilder.append(line).append("\n");
                }
            }
            
            decision.finalAnswer = answerBuilder.toString().trim();
            
        } else if (result.toLowerCase().contains("continue_thinking")) {
            decision.decision = "continue_thinking";
        } else if (result.toLowerCase().contains("need_reflection")) {
            decision.decision = "need_reflection";
        } else if (result.toLowerCase().contains("error")) {
            decision.decision = "error";
        } else {
            // 根据解决程度自动决策
            if (decision.solutionProgress >= 80) {
                decision.decision = "direct_answer";
            } else if (decision.solutionProgress < 20) {
                decision.decision = "need_reflection";
            } else {
                decision.decision = "continue_thinking";
            }
        }
        
        // 提取理由和建议
        String[] lines = result.split("\n");
        for (String line : lines) {
            if (line.contains("理由:") || line.contains("理由：")) {
                decision.reason = line.substring(line.indexOf("理由") + 3).trim();
            }
            if (line.contains("建议:") || line.contains("建议：")) {
                decision.suggestion = line.substring(line.indexOf("建议") + 3).trim();
            }
        }
        
        return decision;
    }
    
    /**
     * 基于观察结果生成答案
     */
    private String generateAnswerFromObservations(OpenManusAgentState state) {
        StringBuilder sb = new StringBuilder();
        sb.append("基于以下分析过程，我给出如下回答：\n\n");
        
        // 添加推理过程摘要
        var observations = state.getObservations();
        if (!observations.isEmpty()) {
            sb.append("**分析过程：**\n");
            for (int i = 0; i < Math.min(observations.size(), 3); i++) {
                String obs = observations.get(i);
                sb.append(String.format("- %s\n", 
                    truncateText(obs, 100)));
            }
            sb.append("\n");
        }
        
        // 添加工具调用结果
        var toolCalls = state.getToolCalls();
        if (!toolCalls.isEmpty()) {
            sb.append("**执行结果：**\n");
            for (Map<String, Object> toolCall : toolCalls) {
                sb.append(String.format("- 使用 %s: %s\n", 
                    toolCall.get("tool_name"),
                    truncateText((String) toolCall.get("result"), 100)));
            }
            sb.append("\n");
        }
        
        sb.append("**结论：**\n");
        sb.append("基于上述分析，已完成了对您问题的处理。如需更详细的信息或有其他问题，请随时告知。");
        
        return sb.toString();
    }
    
    /**
     * 截断文本
     */
    private String truncateText(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }
    
    /**
     * 观察决策结果类
     */
    private static class ObservationDecision {
        String decision = "continue_thinking";
        String reason = "";
        String suggestion = "";
        String finalAnswer = "";
        int solutionProgress = 50;
    }
}