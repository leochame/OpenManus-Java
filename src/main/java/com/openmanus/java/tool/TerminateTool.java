package com.openmanus.java.tool;

import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Terminate tool for ending the interaction.
 * Corresponds to terminate.py in the Python version.
 */
public class TerminateTool {
    
    private static final Logger log = LoggerFactory.getLogger(TerminateTool.class);

    public static final String NAME = "terminate";
    public static final String DESCRIPTION = "Terminate the interaction when the request is met OR if the assistant cannot proceed further with the task.\n" +
            "When you have finished all the tasks, call this tool to end the work.";
    
    public static final String FINISH_SIGNAL = "TERMINATE";

    @Tool(name = "terminate", value = DESCRIPTION)
    public String execute(String status) {
        log.info("Terminating interaction with status: {}", status);
        return "The interaction has been completed with status: " + status;
    }
    
    /**
     * Alternative method name for consistency with Python version
     */
    public String terminate(String status) {
        return execute(status);
    }
    
    /**
     * Get the tool name
     */
    public String getName() {
        return NAME;
    }
    
    /**
     * Get the tool description
     */
    public String getDescription() {
        return DESCRIPTION;
    }
}