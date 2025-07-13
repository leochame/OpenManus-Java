package com.openmanus.java.nodes;

import com.openmanus.java.state.OpenManusAgentState;
import com.openmanus.java.omni.tool.PythonTool;
import com.openmanus.java.omni.tool.FileTool;
import com.openmanus.java.omni.tool.BrowserTool;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;


/**
 * The ActNode is responsible for executing a tool call requested by the LLM.
 * It takes the tool execution requests from the agent's state, executes them using the ToolExecutor,
 * and returns the results as a map to be merged back into the state.
 */
@Deprecated
//@Component
public class ActNode implements AsyncNodeAction<OpenManusAgentState> {
    
    private static final Logger logger = LoggerFactory.getLogger(ActNode.class);
    
    private final ChatModel chatModel;
    private final PythonTool pythonTool;
    private final FileTool fileTool;
    private final BrowserTool browserTool;
    
    // Action decision prompt template
    private static final PromptTemplate ACTION_PROMPT = PromptTemplate.from("""
        Based on the previous thinking analysis, now we need to execute specific actions.
        
        Thinking result:
        {{thinking_result}}
        
        User question:
        {{user_input}}
        
        Available tools and usage methods:
        1. **executePython(code)** - Execute Python code
           Usage format: executePython("print('Hello World')")
           
        2. **readFile(filePath)** - Read file content
           Usage format: readFile("/path/to/file.txt")
           
        3. **writeFile(filePath, content)** - Write to file
           Usage format: writeFile("/path/to/file.txt", "content")
           
        4. **listDirectory(path)** - List directory contents
           Usage format: listDirectory("/path/to/directory")
           
        5. **browseWeb(url)** - Browse web pages
           Usage format: browseWeb("https://example.com")
        
        Please decide the specific action to execute based on the thinking result:
        
        **If tool call is needed, please output in the following format:**
        
        ACTION: [Tool name]
        INPUT: [Specific parameters]
        REASON: [Reason for using this tool]
        
        **If direct answer is possible, please output:**
        
        DIRECT_ANSWER: [Direct answer content]
        
        **If more information is needed, please output:**
        
        NEED_INFO: [What information is needed]
        """);
    
    @Autowired
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
                logger.info("Start action phase - Current state: {}", state.getCurrentState());
                
                // Get thinking result
                String thinkingResult = (String) state.getMetadata().get("last_think_result");
                if (thinkingResult == null || thinkingResult.trim().isEmpty()) {
                    logger.warn("No thinking result found, directly enter observation phase");
                    return Map.of(
                        "current_state", "observe",
                        "reasoning_steps", Map.of("type", "action", "content", "No specific action plan, skipping action phase")
                    );
                }
                
                // Prepare action decision prompt
                Map<String, Object> promptVariables = new HashMap<>();
                promptVariables.put("thinking_result", thinkingResult);
                promptVariables.put("user_input", state.getUserInput());
                
                Prompt prompt = ACTION_PROMPT.apply(promptVariables);
                
                // Call LLM to determine specific action
                logger.debug("Calling LLM to determine specific action...");
                String actionDecision = chatModel.chat(prompt.text());
                
                logger.info("Action decision completed, starting action execution");
                
                // Parse and execute action
                Map<String, Object> actionResult = executeAction(state, actionDecision);
                
                // Merge results
                Map<String, Object> result = new HashMap<>(actionResult);
                result.put("current_state", "acting");
                result.put("reasoning_steps", Map.of("type", "action", "content", actionDecision));
                
                return result;
                
            } catch (Exception e) {
                logger.error("Action node execution failed", e);
                return Map.of(
                    "error", "Error during action execution: " + e.getMessage(),
                    "reasoning_steps", Map.of("type", "error", "content", "Action failed: " + e.getMessage())
                );
            }
        });
    }
    
    /**
     * Parse and execute specific action
     */
    private Map<String, Object> executeAction(OpenManusAgentState state, String actionDecision) {
        try {
            // Parse action type and parameters
            ActionInfo actionInfo = parseActionDecision(actionDecision);
            
            logger.info("Executing action: {} - {}", actionInfo.type, actionInfo.action);
            
            String result;
            switch (actionInfo.type.toLowerCase()) {
                case "action":
                    result = executeToolCall(actionInfo);
                    break;
                case "direct_answer":
                    // Directly return final answer
                    Map<String, Object> directAnswerResult = new HashMap<>();
                    directAnswerResult.put("final_answer", actionInfo.content);
                    directAnswerResult.put("reasoning_steps", Map.of(
                        "type", "direct_answer",
                        "content", "Direct answer: " + actionInfo.content
                    ));
                    directAnswerResult.put("metadata", Map.of("next_action", "direct_answer"));
                    return directAnswerResult;
                case "need_info":
                    result = "Need more information: " + actionInfo.content;
                    break;
                default:
                    result = "Unknown action type: " + actionInfo.type;
                    logger.warn("Unknown action type: {}", actionInfo.type);
            }
            
            Map<String, Object> updates = new HashMap<>();
            
            // Record tool call result
            if (!actionInfo.action.isEmpty()) {
                updates.put("tool_calls", Map.of(
                    "action", actionInfo.action,
                    "input", actionInfo.input,
                    "result", result
                ));
            }
            
            // Add observation result
            updates.put("observations", result);
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("last_action_result", result);
            metadata.put("next_action", "observe");
            updates.put("metadata", metadata);
            
            return updates;
            
        } catch (Exception e) {
            logger.error("Action execution failed", e);
            return Map.of("error", "Action execution failed: " + e.getMessage());
        }
    }
    
    /**
     * Execute specific tool calls
     */
    private String executeToolCall(ActionInfo actionInfo) {
        try {
            switch (actionInfo.action.toLowerCase()) {
                case "executepython":
                    return pythonTool.executePython(actionInfo.input);
                    
                case "readfile":
                    return fileTool.readFile(actionInfo.input);
                    
                case "writefile":
                    // Parse file path and content
                    String[] parts = actionInfo.input.split(",", 2);
                    if (parts.length != 2) {
                        return "writeFile parameter format error, needs: path,content";
                    }
                    return fileTool.writeFile(parts[0].trim(), parts[1].trim());
                    
                case "listdirectory":
                    return fileTool.listDirectory(actionInfo.input);
                    
                case "browseweb":
                    return browserTool.browseWeb(actionInfo.input);
                    
                default:
                    return "Unknown tool: " + actionInfo.action;
            }
        } catch (Exception e) {
            logger.error("Tool call failed: {}", actionInfo.action, e);
            return "Tool call failed: " + e.getMessage();
        }
    }
    
    /**
     * Parse action decision
     */
    private ActionInfo parseActionDecision(String decision) {
        ActionInfo info = new ActionInfo();
        
        // Check if it's a direct answer
        if (decision.contains("DIRECT_ANSWER:")) {
            info.type = "direct_answer";
            String[] parts = decision.split("DIRECT_ANSWER:");
            if (parts.length > 1) {
                info.content = parts[1].trim();
            }
            return info;
        }
        
        // Check if more information is needed
        if (decision.contains("NEED_INFO:")) {
            info.type = "need_info";
            String[] parts = decision.split("NEED_INFO:");
            if (parts.length > 1) {
                info.content = parts[1].trim();
            }
            return info;
        }
        
        // Parse tool call
        if (decision.contains("ACTION:")) {
            info.type = "action";
            String[] lines = decision.split("\n");
            for (String line : lines) {
                line = line.trim();
                if (line.startsWith("ACTION:")) {
                    info.action = line.substring("ACTION:".length()).trim();
                } else if (line.startsWith("INPUT:")) {
                    info.input = line.substring("INPUT:".length()).trim();
                } else if (line.startsWith("REASON:")) {
                    info.reason = line.substring("REASON:".length()).trim();
                }
            }
        }
        
        return info;
    }
    
    /**
     * Action information class
     */
    private static class ActionInfo {
        String type = "";
        String action = "";
        String input = "";
        String reason = "";
        String content = "";
    }
} 