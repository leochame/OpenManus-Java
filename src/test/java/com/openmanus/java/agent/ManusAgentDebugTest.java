package com.openmanus.java.agent;

import com.openmanus.java.config.OpenManusProperties;
import com.openmanus.java.llm.MockLlmClient;
import com.openmanus.java.model.Memory;
import com.openmanus.java.tool.ToolRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ManusAgentDebugTest {

    @Test
    void debugManusAgentRun() {
        // Create test configuration
        OpenManusProperties properties = new OpenManusProperties();
        properties.getSandbox().setUseSandbox(false);

        // Create mock LLM client
        MockLlmClient mockLlm = new MockLlmClient();

        // Create memory
        Memory memory = new Memory();

        // Create tool registry
        ToolRegistry toolRegistry = new ToolRegistry();

        // Create ManusAgent
        ManusAgent agent = new ManusAgent(mockLlm, memory, properties);

        // Test run method
        String task = "Please terminate with message: Test completed";
        String result = agent.run(task).join();

        System.out.println("=== DEBUG OUTPUT ===");
        System.out.println("Task: " + task);
        System.out.println("Result: '" + result + "'");
        System.out.println("Result length: " + result.length());
        System.out.println("Contains 'completed': " + result.contains("completed"));
        System.out.println("Contains 'terminated': " + result.contains("terminated"));
        System.out.println("Contains 'finished': " + result.contains("finished"));
        System.out.println("Contains 'No steps executed': " + result.contains("No steps executed"));
        System.out.println("====================");

        // This test is just for debugging, so we'll always pass
        assertNotNull(result);
    }
}
