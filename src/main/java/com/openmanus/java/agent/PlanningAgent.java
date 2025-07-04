package com.openmanus.java.agent;

import com.openmanus.java.flow.PlanningFlowState;
import com.openmanus.java.model.Memory;
import com.openmanus.java.model.Message;
import com.openmanus.java.llm.LlmClient;
import com.openmanus.java.tool.ToolRegistry;
import com.openmanus.java.model.ToolChoice;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A specialized agent for creating execution plans.
 * This agent analyzes user requests and generates structured plans for execution.
 */
public class PlanningAgent extends ToolCallAgent {

    private static final String PLANNING_SYSTEM_PROMPT = """
            You are a planning agent responsible for breaking down user requests into actionable steps.
            
            Your task is to analyze the user's request and create a structured plan with the following format:
            
            PLAN:
            1. [AgentName]: [Task description]
            2. [AgentName]: [Task description]
            3. [AgentName]: [Task description]
            ...
            
            Available agents:
            - ManusAgent: General-purpose agent for complex tasks, coding, analysis, and problem-solving
            - DataAnalysisAgent: Specialized agent for data analysis, visualization, and statistical tasks
            
            Guidelines:
            - Break down complex requests into logical, sequential steps
            - Choose the most appropriate agent for each task
            - Keep steps focused and actionable
            - Ensure each step builds upon previous ones when necessary
            - Use ManusAgent for general tasks and DataAnalysisAgent for data-specific tasks
            
            Example:
            User: "Analyze the sales data in data.csv and create a visualization"
            
            PLAN:
            1. ManusAgent: Load and examine the sales data from data.csv
            2. DataAnalysisAgent: Perform statistical analysis on the sales data
            3. DataAnalysisAgent: Create visualizations showing sales trends and patterns
            4. ManusAgent: Generate a summary report with findings and recommendations
            """;

    public PlanningAgent(LlmClient llm, Memory memory, ToolRegistry availableTools) {
        super("PlanningAgent", "Agent for creating execution plans", PLANNING_SYSTEM_PROMPT, 
              llm, memory, availableTools, ToolChoice.AUTO, new java.util.HashSet<String>());
        
        // Add system message for planning
        memory.addMessage(Message.systemMessage(PLANNING_SYSTEM_PROMPT));
    }

    /**
     * Creates a plan based on the user request.
     * @param userRequest The user's request to be planned
     * @return A structured plan with steps
     */
    public CompletableFuture<PlanningFlowState.Plan> createPlan(String userRequest) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Add user request to memory
                memory.addMessage(Message.userMessage("Please create a plan for: " + userRequest));
                
                // Generate plan using think and act
                think().join();
                String response = act().join();
                
                // Parse the response to extract the plan
                return parsePlanFromResponse(response);
                
            } catch (Exception e) {
                // Return a default plan if parsing fails
                List<PlanningFlowState.Step> defaultSteps = List.of(
                    new PlanningFlowState.Step("ManusAgent", "Handle the user request: " + userRequest)
                );
                return new PlanningFlowState.Plan(defaultSteps);
            }
        });
    }

    /**
     * Parses the AI response to extract a structured plan.
     * @param response The AI's response containing the plan
     * @return A structured Plan object
     */
    private PlanningFlowState.Plan parsePlanFromResponse(String response) {
        List<PlanningFlowState.Step> steps = new ArrayList<>();
        
        // Pattern to match plan steps: "1. AgentName: Task description"
        Pattern stepPattern = Pattern.compile("\\d+\\.\\s*([^:]+):\\s*(.+)");
        
        String[] lines = response.split("\\n");
        for (String line : lines) {
            Matcher matcher = stepPattern.matcher(line.trim());
            if (matcher.find()) {
                String agentName = matcher.group(1).trim();
                String taskDescription = matcher.group(2).trim();
                
                // Validate agent name
                if (isValidAgentName(agentName)) {
                    steps.add(new PlanningFlowState.Step(agentName, taskDescription));
                }
            }
        }
        
        // If no valid steps were parsed, create a default plan
        if (steps.isEmpty()) {
            steps.add(new PlanningFlowState.Step("ManusAgent", "Complete the user's request"));
        }
        
        return new PlanningFlowState.Plan(steps);
    }

    /**
     * Validates if the agent name is one of the available agents.
     * @param agentName The agent name to validate
     * @return true if the agent name is valid
     */
    private boolean isValidAgentName(String agentName) {
        return agentName.equals("ManusAgent") || 
               agentName.equals("DataAnalysisAgent") ||
               agentName.equals("PlanningAgent");
    }
}