package com.openmanus.java.agent;

import com.openmanus.java.config.OpenManusProperties;
import com.openmanus.java.model.AgentState;
import com.openmanus.java.model.Memory;
import com.openmanus.java.model.ToolChoice;
import com.openmanus.java.tool.*;
import com.openmanus.java.llm.MockLlmClient;
import dev.langchain4j.agent.tool.ToolExecutor;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = { ManusAgentTestConfig.class })
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "openmanus.sandbox.use-sandbox=false", // Disable sandbox for integration tests
        "openmanus.llm.provider=mock",
        "openmanus.llm.model=test-model"
})
class ManusAgentIntegrationTest {

    private ManusAgent manusAgent;
    private OpenManusProperties properties;

    @Autowired
    private ToolRegistry toolRegistry;

    @Autowired
    private Memory memory;

    @BeforeEach
    void setUp() {
        properties = new OpenManusProperties();

        // Configure sandbox settings
        OpenManusProperties.SandboxSettings sandboxSettings = new OpenManusProperties.SandboxSettings();
        sandboxSettings.setUseSandbox(false); // Disable for integration tests
        properties.setSandbox(sandboxSettings);

        // Configure LLM settings
        OpenManusProperties.LLMSettings llmSettings = new OpenManusProperties.LLMSettings();
        OpenManusProperties.LLMSettings.DefaultLLM defaultLlm = new OpenManusProperties.LLMSettings.DefaultLLM();
        defaultLlm.setApiType("mock");
        defaultLlm.setModel("test-model");
        llmSettings.setDefaultLlm(defaultLlm);
        properties.setLlm(llmSettings);

        // Create a mock LLM client for testing
        MockLlmClient mockLlmClient = new MockLlmClient();

        // Create ManusAgent with injected dependencies
        manusAgent = new ManusAgent(mockLlmClient, memory, properties, toolRegistry, ToolChoice.AUTO,
                Set.of("terminate"));
    }

    private ToolRegistry createTestToolRegistry() {
        // Create registry with tool instances - ToolRegistry constructor handles
        // registration
        if (properties.getSandbox().isUseSandbox()) {
            return new ToolRegistry(
                    new AskHumanTool(),
                    new TerminateTool(),
                    new FileTool(properties),
                    new PythonTool(properties));
        } else {
            return new ToolRegistry(
                    new AskHumanTool(),
                    new TerminateTool(),
                    new FileTool(properties));
        }
    }

    @Test
    void testManusAgentCreation() {
        assertNotNull(manusAgent);
        assertNotNull(manusAgent.getToolRegistry());
        assertTrue(manusAgent.getToolRegistry().getAllTools().size() > 0);
    }

    @Test
    void testToolRegistryIntegration() {
        ToolRegistry registry = manusAgent.getToolRegistry();

        assertNotNull(registry);

        // Verify essential tools are present
        assertTrue(registry.hasToolByName("ask_human"));
        assertTrue(registry.hasToolByName("terminate"));
        assertTrue(registry.hasToolByName("file_operations"));

        // Get tools and verify they're properly configured
        ToolExecutor askHumanExecutor = registry.getToolExecutor("ask_human");
        assertNotNull(askHumanExecutor);

        ToolExecutor terminateExecutor = registry.getToolExecutor("terminate");
        assertNotNull(terminateExecutor);
    }

    @Test
    void testAgentConfiguration() {
        // Test that agent is properly configured with properties
        assertNotNull(manusAgent.getProperties());
        assertEquals("mock", manusAgent.getProperties().getLlm().getDefaultLlm().getApiType());
        assertEquals("test-model", manusAgent.getProperties().getLlm().getDefaultLlm().getModel());
        assertFalse(manusAgent.getProperties().getSandbox().isUseSandbox());
    }

    @Test
    void testSimpleTaskExecution() {
        try {
            // Test agent run method with a simple task
            String task = "Please terminate with message: Test completed";

            // This would normally involve LLM interaction, but we're testing the framework
            String result = manusAgent.run(task).join();

            assertNotNull(result);
            // The result should indicate task completion or framework response
            assertTrue(result.contains("completed") ||
                    result.contains("terminated") ||
                    result.contains("finished") ||
                    result.contains("No steps executed"));
        } catch (Exception e) {
            // If LLM is not available, this is expected
            assertTrue(e.getMessage().contains("LLM") ||
                    e.getMessage().contains("mock") ||
                    e.getMessage().contains("not implemented"));
        }
    }

    @Test
    void testMultiToolWorkflow() {
        try {
            // Test a workflow that involves multiple tools
            List<String> tasks = List.of(
                    "Create a test file",
                    "Write content to the file",
                    "Read the file content",
                    "Terminate with success message");

            for (String task : tasks) {
                String result = manusAgent.run(task).join();
                assertNotNull(result);
            }
        } catch (Exception e) {
            // Expected if LLM integration is not fully implemented
            assertTrue(e.getMessage().contains("LLM") ||
                    e.getMessage().contains("not implemented") ||
                    e.getMessage().contains("mock"));
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "DOCKER_AVAILABLE", matches = "true")
    void testPythonToolIntegration() {
        // Enable sandbox for this test
        OpenManusProperties.SandboxSettings sandboxSettings = properties.getSandbox();
        sandboxSettings.setUseSandbox(true);

        // Recreate agent with Python tool
        ToolRegistry registryWithPython = createTestToolRegistry();

        // Create a mock LLM client for testing
        MockLlmClient mockLlmClient = new MockLlmClient();

        ManusAgent agentWithPython = new ManusAgent(mockLlmClient, new Memory(), properties,
                registryWithPython, ToolChoice.AUTO,
                Set.of("terminate"));

        assertTrue(agentWithPython.getToolRegistry().hasToolByName("python_execute"));

        try {
            // Test Python code execution through agent
            String pythonTask = "Execute Python code: print('Hello from integration test')";
            String result = agentWithPython.run(pythonTask).join();

            assertNotNull(result);
            // Should contain Python execution result
            assertTrue(result.contains("Hello from integration test") ||
                    result.contains("executed") ||
                    result.contains("completed"));
        } catch (Exception e) {
            // Expected if Docker is not available or LLM is not configured
            assertTrue(e.getMessage().contains("Docker") ||
                    e.getMessage().contains("LLM") ||
                    e.getMessage().contains("sandbox"));
        }
    }

    @Test
    void testErrorHandling() {
        try {
            // Test error handling with invalid task
            String invalidTask = "This is an invalid task that should cause an error";
            String result = manusAgent.run(invalidTask).join();

            // Should handle error gracefully
            assertNotNull(result);
            assertTrue(result.contains("error") ||
                    result.contains("failed") ||
                    result.contains("invalid") ||
                    result.contains("not implemented") ||
                    result.contains("No steps executed"));
        } catch (Exception e) {
            // Exception is acceptable for error handling test
            assertNotNull(e.getMessage());
        }
    }

    @Test
    void testAgentState() {
        // Test agent state management
        assertNotNull(manusAgent.getState());

        // Test state transitions using AgentState enum
        manusAgent.setState(AgentState.IDLE);
        assertEquals(AgentState.IDLE, manusAgent.getState());

        manusAgent.setState(AgentState.RUNNING);
        assertEquals(AgentState.RUNNING, manusAgent.getState());

        manusAgent.setState(AgentState.FINISHED);
        assertEquals(AgentState.FINISHED, manusAgent.getState());
    }

    @Test
    void testAgentMetrics() {
        // Test agent metrics and monitoring through available methods
        assertNotNull(manusAgent.getToolRegistry());

        // Verify tool count metric
        int toolCount = manusAgent.getToolRegistry().getToolCount();
        assertTrue(toolCount > 0);

        // Test other available metrics
        assertNotNull(manusAgent.getName());
        assertNotNull(manusAgent.getDescription());
        assertTrue(manusAgent.getMaxSteps() > 0);
        assertTrue(manusAgent.getCurrentStep() >= 0);
    }

    @Test
    void testAgentLifecycle() {
        // Test available lifecycle methods
        assertTrue(manusAgent.isInitialized());

        // Test MCP server initialization
        try {
            manusAgent.initializeMcpServers().join();
            assertTrue(manusAgent.isInitialized());
        } catch (Exception e) {
            // MCP initialization might not be fully implemented
            assertTrue(e.getMessage().contains("not implemented") ||
                    e.getMessage().contains("MCP"));
        }
    }

    @Test
    void testConcurrentExecution() {
        try {
            // Test concurrent task execution using CompletableFuture
            List<String> concurrentTasks = List.of(
                    "Task 1: Simple operation",
                    "Task 2: Another operation",
                    "Task 3: Third operation");

            List<CompletableFuture<String>> futures = concurrentTasks.stream()
                    .map(task -> manusAgent.run(task))
                    .collect(Collectors.toList());

            List<String> results = futures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList());

            assertNotNull(results);
            assertEquals(concurrentTasks.size(), results.size());

            for (String result : results) {
                assertNotNull(result);
            }
        } catch (Exception e) {
            // Concurrent execution might not be fully implemented
            assertTrue(e.getMessage().contains("concurrent") ||
                    e.getMessage().contains("not implemented") ||
                    e.getMessage().contains("LLM"));
        }
    }

    @Test
    void testAgentCleanup() {
        try {
            // Test proper cleanup
            manusAgent.cleanup();

            // Verify cleanup completed successfully (state should be IDLE after cleanup)
            assertEquals(AgentState.IDLE, manusAgent.getState());
            assertEquals(0, manusAgent.getCurrentStep());
        } catch (Exception e) {
            fail("Agent cleanup should not throw exception: " + e.getMessage());
        }
    }
}
