package com.openmanus.java.tool;

import dev.langchain4j.agent.tool.Tool;

public class Terminate {

    @Tool("""
            Terminate the interaction when the request is met OR if the assistant cannot proceed further with the task.
            When you have finished all the tasks, call this tool to end the work.
            """)
    public String execute(String status) {
        return String.format("The interaction has been completed with status: %s", status);
    }
}