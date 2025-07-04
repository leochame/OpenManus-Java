package com.openmanus.java.flow;

import com.openmanus.java.agent.BaseAgent;
import com.openmanus.java.agent.PlanningAgent;
import com.openmanus.java.tool.TerminateTool;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.state.AgentStateFactory;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.StateGraph.END;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The core workflow orchestrator, built using LangGraph4j.
 * This class defines the graph of nodes and edges that represents the
 * multi-agent collaboration.
 */
public class PlanningFlow {

    private final CompiledGraph<PlanningFlowState> graph;

    public PlanningFlow(PlanningAgent plannerAgent) throws org.bsc.langgraph4j.GraphStateException {
        // Define Nodes
        AsyncNodeAction<PlanningFlowState> plannerNode = (state) -> {
            try {
                System.out.println("Planner Node: Creating a plan...");
                Object userRequestObj = state.value("currentUserRequest");
                String userRequest = userRequestObj != null ? userRequestObj.toString() : "Complete the user's request";
                
                // Use the planning agent to create a real plan
                PlanningFlowState.Plan plan = plannerAgent.createPlan(userRequest).join();
                System.out.println("Plan created with " + plan.getSteps().size() + " steps");
                
                return CompletableFuture.completedFuture(Map.of("plan", plan));
            } catch (Exception e) {
                System.err.println("Error in planner node: " + e.getMessage());
                // Fallback to a simple plan
                PlanningFlowState.Step fallbackStep = new PlanningFlowState.Step("ManusAgent", "Handle the user request");
                PlanningFlowState.Plan fallbackPlan = new PlanningFlowState.Plan(Collections.singletonList(fallbackStep));
                return CompletableFuture.completedFuture(Map.of("plan", fallbackPlan));
            }
        };

        AsyncNodeAction<PlanningFlowState> agentExecutorNode = (state) -> {
            try {
                Object planObj = state.value("plan");
                Object agentsObj = state.value("agents");
                Object currentStepIndexObj = state.value("currentStepIndex");
                Object executionHistoryObj = state.value("executionHistory");
            
            if (planObj instanceof PlanningFlowState.Plan plan && 
                agentsObj instanceof Map agents &&
                currentStepIndexObj instanceof Integer currentStepIndex &&
                executionHistoryObj instanceof java.util.List executionHistory) {
                
                if (currentStepIndex < plan.getSteps().size()) {
                    PlanningFlowState.Step currentStep = plan.getSteps().get(currentStepIndex);
                    System.out.println("Agent Executor Node: Executing step " + currentStepIndex + " with agent " + currentStep.getAgentName());
                    BaseAgent agent = (BaseAgent) agents.get(currentStep.getAgentName());
                    
                    try {
                        // Provide context to the agent about the current step
                        String stepContext = String.format("You are working on step %d of the plan: %s", 
                                                          currentStepIndex + 1, currentStep.getTask());
                        System.out.println("Executing: " + stepContext);
                        
                        String result = agent.run().get(); // Simplified synchronous call
                        executionHistory.add(String.format("Step %d (%s): %s", 
                                                          currentStepIndex + 1, currentStep.getAgentName(), result));
                        return CompletableFuture.completedFuture(Map.of(
                            "executionHistory", executionHistory, 
                            "currentStepIndex", currentStepIndex + 1
                        ));
                    } catch (Exception e) {
                        String errorMsg = String.format("Error in step %d: %s", currentStepIndex + 1, e.getMessage());
                        executionHistory.add(errorMsg);
                        return CompletableFuture.completedFuture(Map.of(
                            "executionHistory", executionHistory,
                            "currentStepIndex", currentStepIndex + 1,
                            "error", errorMsg
                        ));
                    }
                }
            }
            return CompletableFuture.completedFuture(Map.of("error", "Invalid state data"));
            } catch (Exception e) {
                return CompletableFuture.completedFuture(Map.of("error", e.getMessage()));
            }
        };

        AsyncNodeAction<PlanningFlowState> cleanupNode = (state) -> {
            try {
                System.out.println("Cleanup Node: Cleaning up resources...");
                Object agentsObj = state.value("agents");
                if (agentsObj instanceof Map agents) {
                    agents.values().forEach(agent -> {
                        if (agent instanceof BaseAgent baseAgent) {
                            baseAgent.cleanup();
                        }
                    });
                }
                return CompletableFuture.completedFuture(Map.of("cleanup", true));
            } catch (Exception e) {
                return CompletableFuture.completedFuture(Map.of("cleanup", true, "error", e.getMessage()));
            }
        };

        // Define Conditional Router
        AsyncEdgeAction<PlanningFlowState> router = (state) -> {
            try {
                Object executionHistoryObj = state.value("executionHistory");
                Object planObj = state.value("plan");
                Object currentStepIndexObj = state.value("currentStepIndex");
            
            // Check for termination signal
            if (executionHistoryObj instanceof java.util.List executionHistory && !executionHistory.isEmpty()) {
                Object lastResultObj = executionHistory.get(executionHistory.size() - 1);
                if (lastResultObj instanceof String lastResult && 
                    lastResult.startsWith(TerminateTool.FINISH_SIGNAL)) {
                    return CompletableFuture.completedFuture("cleanup");
                }
            }

            // Check if plan is complete
            if (planObj instanceof PlanningFlowState.Plan plan && 
                currentStepIndexObj instanceof Integer currentStepIndex) {
                if (currentStepIndex >= plan.getSteps().size()) {
                    return CompletableFuture.completedFuture("cleanup");
                } else {
                    return CompletableFuture.completedFuture("agent_executor");
                }
            }
            
            return CompletableFuture.completedFuture("cleanup"); // Default to cleanup if state is invalid
            } catch (Exception e) {
                return CompletableFuture.completedFuture("cleanup"); // Default to cleanup on error
            }
        };

        // Build the graph
        AgentStateFactory<PlanningFlowState> stateFactory = new AgentStateFactory<PlanningFlowState>() {
            @Override
            public PlanningFlowState apply(Map<String, Object> data) {
                return new PlanningFlowState(data);
            }
        };
        StateGraph<PlanningFlowState> stateGraph = new StateGraph<PlanningFlowState>(PlanningFlowState.SCHEMA, stateFactory);
        
        stateGraph.addNode("planner", plannerNode);
        stateGraph.addNode("agent_executor", agentExecutorNode);
        stateGraph.addNode("cleanup", cleanupNode);
        stateGraph.addConditionalEdges("planner", router, Map.of(
                "cleanup", "cleanup",
                "agent_executor", "agent_executor"
        ));
        stateGraph.addConditionalEdges("agent_executor", router, Map.of(
                "cleanup", "cleanup",
                "agent_executor", "agent_executor"
        ));
        stateGraph.addEdge("cleanup", END);
        stateGraph.addEdge(START, "planner");
        
        this.graph = stateGraph.compile();

    }

    public PlanningFlowState run(PlanningFlowState initialState) throws org.bsc.langgraph4j.GraphStateException {
        // Convert PlanningFlowState to Map<String, Object> for graph.invoke()
        Map<String, Object> initialData = initialState.data();
        var result = graph.invoke(initialData);
        return result.orElse(initialState);
    }
}
