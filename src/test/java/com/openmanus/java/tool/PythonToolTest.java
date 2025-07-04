package com.openmanus.java.tool;

import com.openmanus.java.config.OpenManusProperties;
import com.openmanus.java.sandbox.SandboxClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "openmanus.sandbox.use-sandbox=true",
    "openmanus.sandbox.image=python:3.11-slim",
    "openmanus.sandbox.work-dir=/workspace",
    "openmanus.sandbox.timeout=30"
})
class PythonToolTest {

    private PythonTool pythonTool;
    private OpenManusProperties properties;

    @BeforeEach
    void setUp() {
        properties = new OpenManusProperties();
        OpenManusProperties.SandboxSettings sandboxSettings = new OpenManusProperties.SandboxSettings();
        sandboxSettings.setUseSandbox(true);
        sandboxSettings.setImage("python:3.11-slim");
        sandboxSettings.setWorkDir("/workspace");
        sandboxSettings.setTimeout(30);
        properties.setSandbox(sandboxSettings);
        
        pythonTool = new PythonTool(properties);
    }

    @Test
    void testPythonToolCreation() {
        assertNotNull(pythonTool);
        assertEquals("python_execute", pythonTool.getName());
        assertNotNull(pythonTool.getDescription());
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "DOCKER_AVAILABLE", matches = "true")
    void testSimplePythonExecution() {
        String code = "print('Hello, World!')";
        
        try {
            Map<String, Object> result = pythonTool.execute(code, 30);
            
            assertNotNull(result);
            assertTrue((Boolean) result.get("success"));
            assertTrue(((String) result.get("observation")).contains("Hello, World!"));
        } catch (Exception e) {
            fail("Python execution should not throw exception: " + e.getMessage());
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "DOCKER_AVAILABLE", matches = "true")
    void testPythonCalculation() {
        String code = "result = 2 + 3\nprint(f'Result: {result}')";
        
        try {
            Map<String, Object> result = pythonTool.execute(code, 30);
            
            assertNotNull(result);
            assertTrue((Boolean) result.get("success"));
            assertTrue(((String) result.get("observation")).contains("Result: 5"));
        } catch (Exception e) {
            fail("Python calculation should not throw exception: " + e.getMessage());
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "DOCKER_AVAILABLE", matches = "true")
    void testPythonErrorHandling() {
        String code = "print(undefined_variable)";
        
        try {
            Map<String, Object> result = pythonTool.execute(code, 30);
            
            assertNotNull(result);
            assertFalse((Boolean) result.get("success"));
            assertTrue(((String) result.get("observation")).contains("NameError") || 
                      ((String) result.get("observation")).contains("undefined_variable"));
        } catch (Exception e) {
            fail("Python error handling should not throw exception: " + e.getMessage());
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "DOCKER_AVAILABLE", matches = "true")
    void testPythonTimeout() {
        String code = "import time\ntime.sleep(60)\nprint('This should timeout')";
        
        try {
            Map<String, Object> result = pythonTool.execute(code, 5);
            
            assertNotNull(result);
            assertFalse((Boolean) result.get("success"));
            // Timeout should result in failure
            assertTrue(((String) result.get("observation")).contains("timeout"));
        } catch (Exception e) {
            // Timeout exception is expected
            assertTrue(e.getMessage().contains("timeout") || 
                      e.getMessage().contains("Timeout"));
        }
    }

    @Test
    void testPythonToolWithoutDocker() {
        // Test with sandbox disabled
        OpenManusProperties localProperties = new OpenManusProperties();
        OpenManusProperties.SandboxSettings sandboxSettings = new OpenManusProperties.SandboxSettings();
        sandboxSettings.setUseSandbox(false);
        localProperties.setSandbox(sandboxSettings);
        
        PythonTool localPythonTool = new PythonTool(localProperties);
        
        String code = "print('Local execution')";
        
        try {
            Map<String, Object> result = localPythonTool.execute(code, 30);
            
            assertNotNull(result);
            // Local execution should work if Python is available
            // This test might fail if Python is not installed locally
        } catch (Exception e) {
            // Expected if Python is not available locally
            assertTrue(e.getMessage().contains("python") || 
                      e.getMessage().contains("command not found"));
        }
    }

    @Test
    void testPythonToolParameters() {
        assertNotNull(pythonTool.getName());
        assertNotNull(pythonTool.getDescription());
        
        // Test that the tool has proper metadata
        assertEquals("python_execute", pythonTool.getName());
        assertTrue(pythonTool.getDescription().toLowerCase().contains("python"));
    }

    @Test
    void testPythonToolCloseable() {
        try {
            pythonTool.close();
            // Should not throw exception
        } catch (Exception e) {
            fail("Closing PythonTool should not throw exception: " + e.getMessage());
        }
    }
}