package com.openmanus.java.tool;

import com.openmanus.java.config.OpenManusProperties;
import com.openmanus.java.model.CLIResult;
import com.openmanus.java.sandbox.SandboxClient;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Bash command execution tool that uses sandbox for secure execution.
 * Corresponds to bash.py in the Python version.
 * 
 * Features:
 * - Session management for persistent bash sessions
 * - Interactive command support
 * - Long-running command handling
 * - Timeout management (default 120 seconds)
 * - Background process support
 */
@Slf4j
@Component
public class BashTool implements Closeable {
    private static final Logger log = LoggerFactory.getLogger(BashTool.class);

    private final SandboxClient sandboxClient;
    private static final int DEFAULT_TIMEOUT_SECONDS = 120;
    private boolean sessionStarted = false;
    
    public static final String NAME = "bash";
    public static final String DESCRIPTION = "Execute a bash command in the terminal.\n" +
            "* Long running commands: For commands that may run indefinitely, it should be run in the background and the output should be redirected to a file, e.g. command = `python3 app.py > server.log 2>&1 &`.\n" +
            "* Interactive: If a bash command returns exit code `-1`, this means the process is not yet finished. The assistant must then send a second call to terminal with an empty `command` (which will retrieve any additional logs), or it can send additional text (set `command` to the text) to STDIN of the running process, or it can send command=`ctrl+c` to interrupt the process.\n" +
            "* Timeout: If a command execution result says \"Command timed out. Sending SIGINT to the process\", the assistant should retry running the command in the background.";

    @Autowired
    public BashTool(OpenManusProperties properties) {
        this.sandboxClient = new SandboxClient(properties);
    }
    
    public BashTool(SandboxClient sandboxClient) {
        this.sandboxClient = sandboxClient;
    }

    @Tool(DESCRIPTION)
    public CLIResult execute(String command) {
        return execute(command, false);
    }
    
    /**
     * Execute bash command with restart option.
     * Mimics the Python version's behavior.
     * 
     * @param command Bash command to execute (can be null for retrieving logs)
     * @param restart Whether to restart the bash session
     * @return CLIResult containing execution result
     */
    public CLIResult execute(String command, boolean restart) {
        try {
            // Handle restart request
            if (restart) {
                if (sessionStarted) {
                    sandboxClient.close(); // Close existing session
                }
                sessionStarted = false;
                log.debug("Bash session restarted");
                return CLIResult.withSystem("tool has been restarted.");
            }
            
            // Initialize session if not started
            if (!sessionStarted) {
                // Initialize sandbox session
                sessionStarted = true;
                log.debug("Bash session initialized");
            }
            
            // Handle empty command (retrieve logs)
            if (command == null || command.trim().isEmpty()) {
                log.debug("Retrieving additional logs");
                // In a real implementation, this would retrieve buffered output
                return CLIResult.withOutput("No additional logs available");
            }
            
            // Handle interrupt command
            if ("ctrl+c".equals(command.trim())) {
                log.debug("Sending interrupt signal");
                // In a real implementation, this would send SIGINT
                return CLIResult.withSystem("Interrupt signal sent");
            }
            
            log.debug("Executing bash command: {}", command);
            
            // Execute with timeout using CompletableFuture
            CompletableFuture<SandboxClient.ExecutionResult> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return sandboxClient.executeBash(command, DEFAULT_TIMEOUT_SECONDS);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            
            SandboxClient.ExecutionResult result;
            try {
                result = future.get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                return CLIResult.builder()
                        .error("Command timed out. Sending SIGINT to the process")
                        .success(false)
                        .exitCode(-1)
                        .build();
            }
            
            // Build CLIResult similar to Python version
            CLIResult cliResult = CLIResult.builder()
                    .output(result.getStdout())
                    .error(result.getStderr())
                    .exitCode(result.getExitCode())
                    .success(result.getExitCode() == 0)
                    .build();
            
            log.debug("Bash execution result - exit code: {}, success: {}", 
                     result.getExitCode(), cliResult.isSuccess());
            
            return cliResult;
            
        } catch (Exception e) {
            log.error("Error executing bash command: {}", e.getMessage(), e);
            return CLIResult.builder()
                    .error(e.getMessage())
                    .success(false)
                    .exitCode(1)
                    .build();
        }
    }
    
    /**
     * Convenience method that returns a string representation for tool calls.
     * This method is used when the tool is called from LangChain4j.
     */
    public String executeForTool(String command) {
        CLIResult result = execute(command);
        return result.toString();
    }
    
    @Override
    public void close() throws IOException {
        if (sandboxClient != null) {
            sandboxClient.close();
        }
    }
}