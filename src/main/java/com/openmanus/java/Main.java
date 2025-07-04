package com.openmanus.java;

import com.openmanus.java.agent.BaseAgent;
import com.openmanus.java.agent.ManusAgent;
import com.openmanus.java.agent.PlanningAgent;
import com.openmanus.java.agent.DataAnalysisAgent;
import com.openmanus.java.flow.PlanningFlow;
import com.openmanus.java.flow.PlanningFlowState;
import com.openmanus.java.tool.ToolRegistry;
import com.openmanus.java.model.Memory;
import com.openmanus.java.llm.LlmClient;
import com.openmanus.java.model.ToolChoice;

import com.openmanus.java.tool.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class Main {

    public static void main(String[] args) {
        // 1. Initialize Model (Note: This is a legacy main class, consider using OpenManusApplication instead)
        System.out.println("Warning: This Main class is deprecated. Please use OpenManusApplication for Spring Boot integration.");
        System.out.println("For now, this will exit. Please run: mvn spring-boot:run");
        System.exit(1);
        
        // Legacy code - not reachable due to System.exit(1) above
        // All the following code is commented out because:
        // 1. This Main class is deprecated in favor of OpenManusApplication
        // 2. Tool constructors now require OpenManusProperties parameter
        // 3. Agent constructors have been updated for dependency injection
        
        /*
        LlmClient model = null;
        OpenManusProperties properties = null; // Would need proper initialization

        // 2. Initialize Tools (now require OpenManusProperties)
        AskHumanTool askHumanTool = new AskHumanTool();
        BashTool bashTool = new BashTool(properties);
        PythonTool pythonTool = new PythonTool(properties);
        FileTool fileTool = new FileTool(properties);
        TerminateTool terminateTool = new TerminateTool();
        WebSearchTool webSearchTool = new WebSearchTool(properties);

        // 3. Initialize Tool Registry
        ToolRegistry toolRegistry = new ToolRegistry(
                askHumanTool, bashTool, pythonTool, fileTool, terminateTool, webSearchTool
        );
        
        // Create specialized agents
        Memory memory = new Memory();
        
        ManusAgent manusAgent = new ManusAgent(model, memory, properties);
        
        DataAnalysisAgent dataAnalysisAgent = new DataAnalysisAgent(
                 model,
                 memory,
                 toolRegistry
         );
         
         // Create PlanningAgent with access to other agents
         PlanningAgent planningAgent = new PlanningAgent(
                 model,
                 memory,
                 toolRegistry
         );

        Map<String, BaseAgent> agents = new HashMap<>();
         agents.put("ManusAgent", manusAgent);
         agents.put("DataAnalysisAgent", dataAnalysisAgent);

        // 5. Get user input from command line
        String userInput;
        if (args.length > 0) {
            userInput = String.join(" ", args);
        } else {
            System.out.println("Enter your prompt: ");
            userInput = new java.util.Scanner(System.in).nextLine();
        }

        // 6. Prepare Initial State
        Map<String, Object> initialData = new HashMap<>();
        initialData.put("currentUserRequest", userInput);
        initialData.put("agents", agents);
        initialData.put("executionHistory", new ArrayList<>());
        initialData.put("currentStepIndex", 0);
        PlanningFlowState initialState = new PlanningFlowState(initialData);

        // 7. Initialize Flow and Run
        System.out.println("Processing your request...");
        try {
            // 4. Initialize Flow with the planning agent
            PlanningFlow planningFlow = new PlanningFlow(planningAgent);
            PlanningFlowState finalState = planningFlow.run(initialState);
            System.out.println("Request processing completed.");
            System.out.println("Execution History: " + finalState.getExecutionHistory());
        } catch (org.bsc.langgraph4j.GraphStateException e) {
            System.err.println("Error processing request: " + e.getMessage());
            e.printStackTrace();
        }
        */
    }
}