package com.openmanus.java.tool;

import com.openmanus.java.config.OpenManusProperties;
import com.openmanus.java.sandbox.SandboxClient;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Python code execution tool that uses sandbox for secure execution.
 * Corresponds to python_execute.py in the Python version.
 * 
 * Features:
 * - Timeout support (default 5 seconds)
 * - Secure execution in sandbox
 * - Captures stdout and stderr
 * - Returns structured result with success status
 */
@Slf4j
@Component
public class PythonTool implements Closeable {
    private static final Logger log = LoggerFactory.getLogger(PythonTool.class);

    private final SandboxClient sandboxClient;
    private static final int DEFAULT_TIMEOUT_SECONDS = 5;
    
    public static final String NAME = "python_execute";
    public static final String DESCRIPTION = "Executes Python code string. Note: Only print outputs are visible, function return values are not captured. Use print statements to see results.";

    @Autowired
    public PythonTool(OpenManusProperties properties) {
        this.sandboxClient = new SandboxClient(properties);
    }
    
    public PythonTool(SandboxClient sandboxClient) {
        this.sandboxClient = sandboxClient;
    }

    @Tool(DESCRIPTION)
    public Map<String, Object> execute(String code) {
        return execute(code, DEFAULT_TIMEOUT_SECONDS);
    }
    
    /**
     * Execute Python code with custom timeout.
     * Mimics the Python version's behavior with structured return.
     * 
     * @param code Python code to execute
     * @param timeoutSeconds Timeout in seconds
     * @return Map containing 'observation' and 'success' keys
     */
    public Map<String, Object> execute(String code, int timeoutSeconds) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            log.debug("Executing Python code: {}", code.substring(0, Math.min(code.length(), 100)));
            
            // Execute with timeout using CompletableFuture
            CompletableFuture<SandboxClient.ExecutionResult> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return sandboxClient.executePython(code, timeoutSeconds);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            
            SandboxClient.ExecutionResult execResult;
            try {
                execResult = future.get(timeoutSeconds, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                result.put("observation", "Execution timeout after " + timeoutSeconds + " seconds");
                result.put("success", false);
                return result;
            }
            
            // Build observation string similar to Python version
            StringBuilder observation = new StringBuilder();
            
            if (!execResult.getStdout().isEmpty()) {
                observation.append(execResult.getStdout());
            }
            
            if (!execResult.getStderr().isEmpty()) {
                if (observation.length() > 0) {
                    observation.append("\n");
                }
                observation.append(execResult.getStderr());
            }
            
            boolean success = execResult.getExitCode() == 0;
            String finalObservation = observation.toString();
            
            result.put("observation", finalObservation);
            result.put("success", success);
            
            log.debug("Python execution result - success: {}, observation: {}", success, 
                     finalObservation.substring(0, Math.min(finalObservation.length(), 100)));
            
            return result;
            
        } catch (Exception e) {
            log.error("Error executing Python code: {}", e.getMessage(), e);
            result.put("observation", e.getMessage());
            result.put("success", false);
            return result;
        }
    }
    
    /**
     * Convenience method that returns a string representation for tool calls.
     * This method is used when the tool is called from LangChain4j.
     */
    public String executeForTool(String code) {
        Map<String, Object> result = execute(code);
        boolean success = (Boolean) result.get("success");
        String observation = (String) result.get("observation");
        
        if (success) {
            return observation.isEmpty() ? "Code executed successfully (no output)" : observation;
        } else {
            return "Error: " + observation;
        }
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
    
    @Override
    public void close() throws IOException {
        if (sandboxClient != null) {
            sandboxClient.close();
        }
    }
}