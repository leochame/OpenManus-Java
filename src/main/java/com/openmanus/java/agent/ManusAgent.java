package com.openmanus.java.agent;

import com.openmanus.java.tool.BrowserTool;
import com.openmanus.java.tool.FileTool;
import com.openmanus.java.tool.PythonTool;
import com.openmanus.java.tool.ReflectionTool;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于 LangChain4j AI Services 的智能 Agent 实现
 * 支持真正的 ReAct 推理链路和 COT 推理过程
 */
@Component
public class ManusAgent {
    
    private static final Logger logger = LoggerFactory.getLogger(ManusAgent.class);
    
    private final ChatModel chatModel;
    private final PythonTool pythonTool;
    private final FileTool fileTool;
    private final BrowserTool browserTool;
    private final ReflectionTool reflectionTool;
    private final ReactAgent reactAgent;
    
    public ManusAgent(@Qualifier("chatModel") ChatModel chatModel,
                     PythonTool pythonTool,
                     FileTool fileTool,
                     BrowserTool browserTool,
                     ReflectionTool reflectionTool) {
        this.chatModel = chatModel;
        this.pythonTool = pythonTool;
        this.fileTool = fileTool;
        this.browserTool = browserTool;
        this.reflectionTool = reflectionTool;
        
        // 创建AI Services实例
        this.reactAgent = AiServices.builder(ReactAgent.class)
                .chatModel(chatModel)
                .tools(new ToolProvider(pythonTool, fileTool, browserTool))
                .build();
        
        logger.info("ManusAgent initialized with ChatModel: {}", chatModel.getClass().getSimpleName());
    }
    
    /**
     * ReAct Agent接口定义
     */
    public interface ReactAgent {
        String reason(String userMessage);
    }
    
    /**
     * 工具提供者类
     */
    public static class ToolProvider {
        private final PythonTool pythonTool;
        private final FileTool fileTool;
        private final BrowserTool browserTool;
        
        public ToolProvider(PythonTool pythonTool, FileTool fileTool, BrowserTool browserTool) {
            this.pythonTool = pythonTool;
            this.fileTool = fileTool;
            this.browserTool = browserTool;
        }
        
        @Tool("Execute Python code for calculations, data processing, or programming tasks")
        public String executePython(String code) {
            return pythonTool.executePython(code);
        }
        
        @Tool("List files and directories in the specified path")
        public String listDirectory(String path) {
            return fileTool.listDirectory(path);
        }
        
        @Tool("Read content from a file")
        public String readFile(String path) {
            return fileTool.readFile(path);
        }
        
        @Tool("Write content to a file")
        public String writeFile(String path, String content) {
            return fileTool.writeFile(path, content);
        }
        
        @Tool("Browse a web page and return its content")
        public String browseWeb(String url) {
            return browserTool.browseWeb(url);
        }
    }
    
    /**
     * 与Agent对话（带真正的COT推理过程）
     */
    public Map<String, Object> chatWithCot(String userMessage) {
        logger.info("开始处理用户消息: {}", userMessage);
        
        List<String> cotSteps = new ArrayList<>();
        String finalAnswer = "";
        String fullContent = "";
        
        try {
            // 步骤1：记录用户输入
            cotSteps.add("用户输入: " + userMessage);
            
            // 步骤2：构建ReAct提示词
            String reactPrompt = buildReactPrompt(userMessage);
            cotSteps.add("构建ReAct提示词完成");
            
            // 步骤3：调用AI Services进行推理
            logger.info("调用ReactAgent进行推理...");
            String agentResponse = reactAgent.reason(reactPrompt);
            logger.info("Agent响应: {}", agentResponse);
            
            // 步骤4：解析响应并提取COT步骤
            List<String> reasoningSteps = parseReasoningSteps(agentResponse);
            cotSteps.addAll(reasoningSteps);
            
            // 步骤5：提取最终答案
            finalAnswer = extractFinalAnswer(agentResponse);
            if (finalAnswer.isEmpty()) {
                finalAnswer = agentResponse; // 如果没有明确的答案标记，使用整个响应
            }
            
            // 保存完整内容
            fullContent = finalAnswer;
            
            // 步骤6：反思
            String taskId = "task_" + System.currentTimeMillis();
            reflectionTool.recordTask(taskId, userMessage, 
                                    String.join(" -> ", cotSteps), 
                                    "ReAct推理", finalAnswer);
            String reflection = reflectionTool.reflectOnTask(taskId);
            cotSteps.add("反思: " + reflection);
            
            logger.info("推理完成，最终答案: {}", finalAnswer);
            
        } catch (Exception e) {
            logger.error("处理用户消息时出现错误", e);
            cotSteps.add("错误: " + e.getMessage());
            finalAnswer = "抱歉，处理您的问题时出现了错误：" + e.getMessage();
            fullContent = finalAnswer;
        }
        
        // 提取完整的回答内容
        String completeAnswer = extractCompleteAnswer(finalAnswer);
        
        Map<String, Object> result = new HashMap<>();
        result.put("answer", completeAnswer);
        result.put("content", fullContent);  // 添加完整内容字段
        result.put("cot", cotSteps);
        
        // 记录完整的返回内容
        logger.debug("完整回答内容: {}", completeAnswer);
        logger.debug("COT步骤数量: {}", cotSteps.size());
        
        return result;
    }
    
    /**
     * 提取完整的回答内容，确保不被截断
     */
    private String extractCompleteAnswer(String answer) {
        // 检查是否有列表格式的回答
        Pattern listPattern = Pattern.compile("(\\d+\\..+?)(?=\\d+\\.|$)", Pattern.DOTALL);
        Matcher matcher = listPattern.matcher(answer);
        
        StringBuilder fullAnswer = new StringBuilder();
        boolean foundList = false;
        
        while (matcher.find()) {
            foundList = true;
            fullAnswer.append(matcher.group(1)).append("\n");
        }
        
        if (foundList) {
            return fullAnswer.toString().trim();
        }
        
        return answer;
    }
    
    /**
     * 构建ReAct提示词
     */
    private String buildReactPrompt(String userMessage) {
        return String.format("""
            你是一个智能助手，请使用ReAct (Reasoning and Acting) 框架来回答用户问题。
            
            请按照以下格式进行推理：
            
            思考: [分析问题，思考需要做什么]
            行动: [如果需要使用工具，说明要使用什么工具和参数]
            观察: [工具执行的结果]
            思考: [基于观察结果继续推理]
            行动: [如果需要更多信息，继续使用工具]
            观察: [更多的工具执行结果]
            答案: [基于所有推理和观察给出最终答案]
            
            可用工具：
            - executePython: 执行Python代码，适用于数学计算、数据处理等
            - listDirectory: 列出目录中的文件
            - readFile: 读取文件内容
            - writeFile: 写入文件
            - browseWeb: 浏览网页
            
            用户问题: %s
            
            请开始你的推理：
            """, userMessage);
    }
    
    /**
     * 解析推理步骤
     */
    private List<String> parseReasoningSteps(String response) {
        List<String> steps = new ArrayList<>();
        
        String[] lines = response.split("\\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("思考:") || line.startsWith("Thought:")) {
                steps.add("思考: " + line.substring(line.indexOf(":") + 1).trim());
            } else if (line.startsWith("行动:") || line.startsWith("Action:")) {
                steps.add("行动: " + line.substring(line.indexOf(":") + 1).trim());
            } else if (line.startsWith("观察:") || line.startsWith("Observation:")) {
                steps.add("观察: " + line.substring(line.indexOf(":") + 1).trim());
            }
        }
        
        // 如果没有解析到明确的步骤，创建基本的推理步骤
        if (steps.isEmpty()) {
            steps.add("思考: 正在分析用户问题...");
            steps.add("行动: 调用相应工具处理问题");
            steps.add("观察: 获取工具执行结果");
        }
        
        return steps;
    }
    
    /**
     * 提取最终答案
     */
    private String extractFinalAnswer(String response) {
        String[] lines = response.split("\\n");
        StringBuilder answer = new StringBuilder();
        boolean answerStarted = false;
        
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("答案:") || line.startsWith("Answer:") || 
                line.startsWith("最终答案:") || line.startsWith("Final Answer:")) {
                answerStarted = true;
                answer.append(line.substring(line.indexOf(":") + 1).trim());
            } else if (answerStarted) {
                answer.append("\n").append(line);
            }
        }
        
        if (answer.length() > 0) {
            return answer.toString().trim();
        }
        
        // 如果没有找到明确的答案标记，尝试获取最后几行作为答案
        if (lines.length > 0) {
            return lines[lines.length - 1].trim();
        }
        
        return response;
    }
}