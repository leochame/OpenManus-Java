package com.openmanus.java.nodes;

import com.openmanus.java.state.OpenManusAgentState;
import com.openmanus.java.tool.PythonTool;
import com.openmanus.java.tool.FileTool;
import com.openmanus.java.tool.BrowserTool;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Act Node - React框架的行动节点
 * 
 * 负责执行具体的工具调用，基于思考节点的决策执行相应的行动
 */
public class ActNode implements AsyncNodeAction<OpenManusAgentState> {
    
    private static final Logger logger = LoggerFactory.getLogger(ActNode.class);
    
    private final ChatModel chatModel;
    private final PythonTool pythonTool;
    private final FileTool fileTool;
    private final BrowserTool browserTool;
    
    // 行动决策提示词模板
    private static final PromptTemplate ACTION_PROMPT = PromptTemplate.from("""
        基于之前的思考分析，现在需要执行具体的行动。
        
        思考结果：
        {{thinking_result}}
        
        用户问题：
        {{user_input}}
        
        可用工具和使用方法：
        1. **executePython(code)** - 执行Python代码
           使用格式：executePython("print('Hello World')")
           
        2. **readFile(filePath)** - 读取文件内容
           使用格式：readFile("/path/to/file.txt")
           
        3. **writeFile(filePath, content)** - 写入文件
           使用格式：writeFile("/path/to/file.txt", "内容")
           
        4. **listDirectory(path)** - 列出目录内容
           使用格式：listDirectory("/path/to/directory")
           
        5. **browseWeb(url)** - 浏览网页
           使用格式：browseWeb("https://example.com")
        
        请根据思考结果，决定要执行的具体行动：
        
        **如果需要工具调用，请严格按照以下格式输出：**
        
        ACTION: [工具名称]
        INPUT: [具体参数]
        REASON: [使用理由]
        
        **如果可以直接回答，请输出：**
        
        DIRECT_ANSWER: [直接答案内容]
        
        **如果需要更多信息，请输出：**
        
        NEED_INFO: [需要什么信息]
        """);
    
    public ActNode(ChatModel chatModel, PythonTool pythonTool, 
                   FileTool fileTool, BrowserTool browserTool) {
        this.chatModel = chatModel;
        this.pythonTool = pythonTool;
        this.fileTool = fileTool;
        this.browserTool = browserTool;
    }
    
    @Override
    public CompletableFuture<Map<String, Object>> apply(OpenManusAgentState state) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("开始行动阶段 - 当前状态: {}", state.getCurrentState());
                
                // 获取思考结果
                String thinkingResult = (String) state.getMetadata().get("last_think_result");
                if (thinkingResult == null || thinkingResult.trim().isEmpty()) {
                    logger.warn("没有找到思考结果，直接进入观察阶段");
                    return Map.of(
                        "current_state", "observe",
                        "reasoning_steps", Map.of("type", "action", "content", "没有具体的行动计划，跳过行动阶段")
                    );
                }
                
                // 准备行动决策提示词
                Map<String, Object> promptVariables = new HashMap<>();
                promptVariables.put("thinking_result", thinkingResult);
                promptVariables.put("user_input", state.getUserInput());
                
                Prompt prompt = ACTION_PROMPT.apply(promptVariables);
                
                // 调用LLM确定具体行动
                logger.debug("调用LLM确定具体行动...");
                String actionDecision = chatModel.chat(prompt.text());
                
                logger.info("行动决策完成，开始执行行动");
                
                // 解析并执行行动
                Map<String, Object> actionResult = executeAction(state, actionDecision);
                
                // 合并结果
                Map<String, Object> result = new HashMap<>(actionResult);
                result.put("current_state", "acting");
                result.put("reasoning_steps", Map.of("type", "action", "content", actionDecision));
                
                return result;
                
            } catch (Exception e) {
                logger.error("行动节点执行失败", e);
                return Map.of(
                    "error", "行动执行过程中发生错误: " + e.getMessage(),
                    "reasoning_steps", Map.of("type", "error", "content", "行动失败: " + e.getMessage())
                );
            }
        });
    }
    
    /**
     * 解析并执行具体行动
     */
    private Map<String, Object> executeAction(OpenManusAgentState state, String actionDecision) {
        try {
            // 解析行动类型和参数
            ActionInfo actionInfo = parseActionDecision(actionDecision);
            
            logger.info("执行行动: {} - {}", actionInfo.type, actionInfo.action);
            
            String result;
            switch (actionInfo.type.toLowerCase()) {
                case "action":
                    result = executeToolCall(actionInfo);
                    break;
                case "direct_answer":
                    return Map.of(
                        "final_answer", actionInfo.content,
                        "reasoning_steps", Map.of("type", "direct_answer", "content", actionInfo.content)
                    );
                case "need_info":
                    result = "需要更多信息: " + actionInfo.content;
                    break;
                default:
                    result = "未知行动类型: " + actionInfo.type;
                    logger.warn("未知行动类型: {}", actionInfo.type);
            }
            
            Map<String, Object> updates = new HashMap<>();
            
            // 记录工具调用结果
            if (!actionInfo.action.isEmpty()) {
                updates.put("tool_calls", Map.of(
                    "action", actionInfo.action,
                    "input", actionInfo.input,
                    "result", result
                ));
            }
            
            // 添加观察结果
            updates.put("observations", result);
            updates.put("metadata", Map.of("last_action_result", result));
            
            return updates;
            
        } catch (Exception e) {
            logger.error("执行行动失败", e);
            return Map.of("error", "执行行动失败: " + e.getMessage());
        }
    }
    
    /**
     * 执行具体的工具调用
     */
    private String executeToolCall(ActionInfo actionInfo) {
        try {
            switch (actionInfo.action.toLowerCase()) {
                case "executepython":
                    return pythonTool.executePython(actionInfo.input);
                    
                case "readfile":
                    return fileTool.readFile(actionInfo.input);
                    
                case "writefile":
                    // 解析文件路径和内容
                    String[] parts = actionInfo.input.split(",", 2);
                    if (parts.length != 2) {
                        return "writeFile参数格式错误，需要：路径,内容";
                    }
                    return fileTool.writeFile(parts[0].trim(), parts[1].trim());
                    
                case "listdirectory":
                    return fileTool.listDirectory(actionInfo.input);
                    
                case "browseweb":
                    return browserTool.browseWeb(actionInfo.input);
                    
                default:
                    return "未知工具: " + actionInfo.action;
            }
        } catch (Exception e) {
            logger.error("工具调用失败: {}", actionInfo.action, e);
            return "工具调用失败: " + e.getMessage();
        }
    }
    
    /**
     * 解析行动决策
     */
    private ActionInfo parseActionDecision(String decision) {
        ActionInfo info = new ActionInfo();
        
        // 检查直接答案
        Pattern directAnswerPattern = Pattern.compile("DIRECT_ANSWER:\\s*(.+)", Pattern.DOTALL);
        Matcher directMatcher = directAnswerPattern.matcher(decision);
        if (directMatcher.find()) {
            info.type = "direct_answer";
            info.content = directMatcher.group(1).trim();
            return info;
        }
        
        // 检查需要更多信息
        Pattern needInfoPattern = Pattern.compile("NEED_INFO:\\s*(.+)", Pattern.DOTALL);
        Matcher needMatcher = needInfoPattern.matcher(decision);
        if (needMatcher.find()) {
            info.type = "need_info";
            info.content = needMatcher.group(1).trim();
            return info;
        }
        
        // 解析工具调用
        Pattern actionPattern = Pattern.compile("ACTION:\\s*(.+?)\\s*INPUT:\\s*(.+?)(?:\\s*REASON:\\s*(.+))?", 
                                              Pattern.DOTALL);
        Matcher actionMatcher = actionPattern.matcher(decision);
        if (actionMatcher.find()) {
            info.type = "action";
            info.action = actionMatcher.group(1).trim();
            info.input = actionMatcher.group(2).trim();
            info.reason = actionMatcher.groupCount() > 2 && actionMatcher.group(3) != null ? 
                         actionMatcher.group(3).trim() : "";
            
            // 清理输入参数（移除引号）
            if (info.input.startsWith("\"") && info.input.endsWith("\"")) {
                info.input = info.input.substring(1, info.input.length() - 1);
            }
            
            return info;
        }
        
        // 如果无法解析，默认为观察
        info.type = "observe";
        info.content = decision;
        logger.warn("无法解析行动决策，将进入观察阶段: {}", decision.substring(0, Math.min(100, decision.length())));
        
        return info;
    }
    
    /**
     * 行动信息类
     */
    private static class ActionInfo {
        String type = "";
        String action = "";
        String input = "";
        String reason = "";
        String content = "";
    }
} 