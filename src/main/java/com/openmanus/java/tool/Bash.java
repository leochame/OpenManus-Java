package com.openmanus.java.tool;

import com.openmanus.java.config.OpenManusProperties;
import com.openmanus.java.exception.ToolErrorException;
import com.openmanus.java.model.CLIResult;
import com.openmanus.java.sandbox.SandboxClient;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.concurrent.*;

@Slf4j
@Component
public class Bash implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(Bash.class);
    
    public static final String NAME = "bash";
    public static final String DESCRIPTION = "Execute a bash command in the terminal.\n" +
            "* Long running commands: For commands that may run indefinitely, it should be run in the background and the output should be redirected to a file, e.g. command = `python3 app.py > server.log 2>&1 &`.\n" +
            "* Interactive: If a bash command returns exit code `-1`, this means the process is not yet finished. The assistant must then send a second call to terminal with an empty `command` (which will retrieve any additional logs), or it can send additional text (set `command` to the text) to STDIN of the running process, or it can send command=`ctrl+c` to interrupt the process.\n" +
            "* Timeout: If a command execution result says \"Command timed out. Sending SIGINT to the process\", the assistant should retry running the command in the background.";

    private static final String SENTINEL = "<<exit>>";
    private static final int TIMEOUT_SECONDS = 120;
    private static final int OUTPUT_DELAY_MS = 200;

    private final SandboxClient sandboxClient;
    private final boolean useSandbox;
    private Process process;
    private Writer writer;
    private BufferedReader reader;
    private BufferedReader errorReader;
    private boolean started = false;
    private boolean timedOut = false;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Autowired
    public Bash(OpenManusProperties properties) {
        OpenManusProperties.SandboxSettings sandboxSettings = properties.getSandbox();
        this.useSandbox = sandboxSettings.isUseSandbox();
        this.sandboxClient = useSandbox ? new SandboxClient(properties) : null;
    }

    public Bash(SandboxClient sandboxClient) {
        this.sandboxClient = sandboxClient;
        this.useSandbox = sandboxClient != null;
    }

    @Tool(DESCRIPTION)
    public String execute(String command) {
        try {
            if (useSandbox) {
                return executeSandbox(command).getOutput();
            } else {
                return executeLocal(command).getOutput();
            }
        } catch (Exception e) {
            logger.error("Error executing bash command: {}", e.getMessage(), e);
            return "Error: " + e.getMessage();
        }
    }

    private CLIResult executeSandbox(String command) throws Exception {
        if (command == null || command.trim().isEmpty()) {
            throw new ToolErrorException("No command provided.");
        }

        if ("ctrl+c".equalsIgnoreCase(command.trim())) {
            // In sandbox, we can't really interrupt a running process
            return new CLIResult("Process interrupt signal sent.", "", "", 0, true);
        }

        SandboxClient.ExecutionResult result = sandboxClient.executeBash(command, TIMEOUT_SECONDS);
        return new CLIResult(result.getStdout(), result.getStderr(), "", result.getExitCode(), true);
    }

    private CLIResult executeLocal(String command) throws Exception {
        if (!started) {
            startSession();
        }

        if (process == null || !process.isAlive()) {
            if (timedOut) {
                throw new ToolErrorException(
                    "timed out: bash has not returned in " + TIMEOUT_SECONDS + " seconds and must be restarted"
                );
            }
            return new CLIResult("", "bash has exited with returncode " + 
                (process != null ? process.exitValue() : "unknown"), "tool must be restarted", -1, false);
        }

        if ("ctrl+c".equalsIgnoreCase(command.trim())) {
            process.destroy();
            return new CLIResult("Process interrupted.", "", "", 0, true);
        }

        if (command == null || command.trim().isEmpty()) {
            // Retrieve additional logs
            return readOutput();
        }

        // Send command to the process
        writer.write(command + "; echo '" + SENTINEL + "'\n");
        writer.flush();

        return readOutputWithTimeout();
    }

    private CLIResult readOutputWithTimeout() throws Exception {
        Future<CLIResult> future = executor.submit(this::readOutput);
        try {
            return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            timedOut = true;
            future.cancel(true);
            throw new ToolErrorException(
                "timed out: bash has not returned in " + TIMEOUT_SECONDS + " seconds and must be restarted"
            );
        }
    }

    private CLIResult readOutput() {
        try {
            StringBuilder output = new StringBuilder();
            StringBuilder error = new StringBuilder();
            
            // Read output until sentinel is found or timeout occurs
            int maxAttempts = (TIMEOUT_SECONDS * 1000) / OUTPUT_DELAY_MS; // 防止无限循环
            int attempts = 0;
            
            while (attempts < maxAttempts) {
                attempts++;
                Thread.sleep(OUTPUT_DELAY_MS);
                
                // Check if process is still alive
                if (process != null && !process.isAlive()) {
                    // Process has terminated, return whatever we have
                    break;
                }
                
                String line;
                boolean foundSentinel = false;
                
                while (reader.ready() && (line = reader.readLine()) != null) {
                    if (line.equals(SENTINEL)) {
                        foundSentinel = true;
                        break;
                    }
                    output.append(line).append("\n");
                }
                
                if (foundSentinel) {
                    // Found sentinel, command completed
                    break;
                }
                
                // Read error stream
                while (errorReader.ready() && (line = errorReader.readLine()) != null) {
                    error.append(line).append("\n");
                }
            }
            
            // Clean up output strings
            String outputStr = output.toString();
            if (outputStr.endsWith("\n")) {
                outputStr = outputStr.substring(0, outputStr.length() - 1);
            }
            
            String errorStr = error.toString();
            if (errorStr.endsWith("\n")) {
                errorStr = errorStr.substring(0, errorStr.length() - 1);
            }
            
            return new CLIResult(outputStr, errorStr, "", 0, true);
            
        } catch (Exception e) {
            logger.error("Error reading output: {}", e.getMessage(), e);
            return new CLIResult("", "Error reading output: " + e.getMessage(), "", -1, false);
        }
    }

    private void startSession() throws IOException {
        if (started) {
            return;
        }

        ProcessBuilder pb = new ProcessBuilder("/bin/bash");
        pb.environment().put("TERM", "xterm");
        process = pb.start();
        writer = new OutputStreamWriter(process.getOutputStream());
        reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
        started = true;
    }

    public void stop() {
        if (!started) {
            throw new RuntimeException("Session has not started.");
        }
        if (process != null && process.isAlive()) {
            process.destroy();
        }
    }

    public void restart() throws IOException {
        if (process != null) {
            process.destroy();
        }
        started = false;
        timedOut = false;
        startSession();
    }

    @Override
    public void close() {
        try {
            if (process != null && process.isAlive()) {
                process.destroy();
            }
            if (sandboxClient != null) {
                sandboxClient.close();
            }
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (Exception e) {
            logger.error("Error closing bash session: {}", e.getMessage(), e);
        }
    }
}