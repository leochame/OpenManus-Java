package com.openmanus.infra.sandbox;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.InspectExecResponse;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.netty.NettyDockerCmdExecFactory;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.github.dockerjava.api.async.ResultCallback;
import com.openmanus.infra.config.OpenManusProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Docker-based sandbox client for secure code execution.
 * Corresponds to the sandbox client in the Python version.
 */
@Component
public class SandboxClient implements Closeable {
    private static final Logger log = LoggerFactory.getLogger(SandboxClient.class);
    
    private final DockerClient dockerClient;
    private final OpenManusProperties.SandboxSettings config;
    private String containerId;
    private boolean isRunning = false;
    
    @Autowired
    public SandboxClient(OpenManusProperties properties) {
        this.config = properties.getSandbox();
        
        if (!config.isUseSandbox()) {
            this.dockerClient = null;
            log.info("Sandbox is disabled in configuration");
            return;
        }
        
        // Initialize Docker client with Netty transport to avoid Jersey conflicts
        DefaultDockerClientConfig dockerConfig = DefaultDockerClientConfig.createDefaultConfigBuilder()
            .build();
        
        this.dockerClient = DockerClientBuilder.getInstance(dockerConfig)
            .withDockerCmdExecFactory(new NettyDockerCmdExecFactory())
            .build();
        
        try {
            // Test Docker connection
            dockerClient.pingCmd().exec();
            log.info("Docker connection established successfully");
            
            // Initialize container
            initializeContainer();
            
        } catch (Exception e) {
            log.error("Failed to initialize Docker client: {}", e.getMessage(), e);
            throw new RuntimeException("Docker initialization failed", e);
        }
    }
    
    private void initializeContainer() {
        try {
            // Pull the image if it doesn't exist
            pullImageIfNeeded();
            
            // Create container
            CreateContainerResponse container = dockerClient.createContainerCmd(config.getImage())
                .withWorkingDir(config.getWorkDir())
                .withHostConfig(HostConfig.newHostConfig()
                    .withMemory(parseMemoryLimit(config.getMemoryLimit()))
                    .withCpuQuota((long) (config.getCpuLimit() * 100000)) // CPU quota in microseconds
                    .withCpuPeriod(100000L) // 100ms period
                    .withNetworkMode(config.isNetworkEnabled() ? "bridge" : "none")
                    .withAutoRemove(true) // Auto-remove container when stopped
                )
                .withCmd("tail", "-f", "/dev/null") // Keep container running
                .exec();
            
            this.containerId = container.getId();
            
            // Start container
            dockerClient.startContainerCmd(containerId).exec();
            this.isRunning = true;
            
            log.info("Sandbox container started with ID: {}", containerId);
            
        } catch (Exception e) {
            log.error("Failed to initialize container: {}", e.getMessage(), e);
            throw new RuntimeException("Container initialization failed", e);
        }
    }
    
    private void pullImageIfNeeded() {
        try {
            // Check if image exists locally
            dockerClient.inspectImageCmd(config.getImage()).exec();
            log.debug("Image {} already exists locally", config.getImage());
        } catch (Exception e) {
            // Image doesn't exist, pull it
            log.info("Pulling Docker image: {}", config.getImage());
            try {
                dockerClient.pullImageCmd(config.getImage())
                    .exec(new PullImageResultCallback())
                    .awaitCompletion(5, TimeUnit.MINUTES);
                log.info("Successfully pulled image: {}", config.getImage());
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Image pull interrupted", ie);
            }
        }
    }
    
    private long parseMemoryLimit(String memoryLimit) {
        // Parse memory limit string like "512m", "1g", etc.
        String limit = memoryLimit.toLowerCase().trim();
        
        if (limit.endsWith("k")) {
            return Long.parseLong(limit.substring(0, limit.length() - 1)) * 1024;
        } else if (limit.endsWith("m")) {
            return Long.parseLong(limit.substring(0, limit.length() - 1)) * 1024 * 1024;
        } else if (limit.endsWith("g")) {
            return Long.parseLong(limit.substring(0, limit.length() - 1)) * 1024 * 1024 * 1024;
        } else {
            return Long.parseLong(limit); // Assume bytes
        }
    }
    
    /**
     * Execute a command in the sandbox.
     * 
     * @param command The command to execute
     * @param timeoutSeconds Timeout in seconds (0 for default)
     * @return ExecutionResult containing output and exit code
     */
    public ExecutionResult executeCommand(String command, int timeoutSeconds) {
        if (!config.isUseSandbox()) {
            return executeLocally(command);
        }
        
        if (!isRunning) {
            throw new IllegalStateException("Sandbox container is not running");
        }
        
        try {
            // Create exec instance
            ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withCmd("/bin/sh", "-c", command)
                .exec();
            
            // Execute command and capture output
            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();
            
            ExecStartResultCallback callback = new ExecStartResultCallback(stdout, stderr);
            
            dockerClient.execStartCmd(execCreateCmdResponse.getId())
                .exec(callback);
            
            // Wait for completion with timeout
            int timeout = timeoutSeconds > 0 ? timeoutSeconds : config.getTimeout();
            boolean completed = callback.awaitCompletion(timeout, TimeUnit.SECONDS);
            
            if (!completed) {
                log.warn("Command execution timed out after {} seconds", timeout);
                return new ExecutionResult(
                    stdout.toString(StandardCharsets.UTF_8),
                    stderr.toString(StandardCharsets.UTF_8) + "\nExecution timed out",
                    124 // Timeout exit code
                );
            }
            
            // Get exit code
            InspectExecResponse execResponse = dockerClient.inspectExecCmd(execCreateCmdResponse.getId()).exec();
            Integer exitCode = execResponse.getExitCodeLong() != null ? 
                execResponse.getExitCodeLong().intValue() : 0;
            
            return new ExecutionResult(
                stdout.toString(StandardCharsets.UTF_8),
                stderr.toString(StandardCharsets.UTF_8),
                exitCode
            );
            
        } catch (Exception e) {
            log.error("Failed to execute command in sandbox: {}", e.getMessage(), e);
            return new ExecutionResult(
                "",
                "Sandbox execution failed: " + e.getMessage(),
                1
            );
        }
    }
    
    /**
     * Execute command locally (when sandbox is disabled).
     */
    private ExecutionResult executeLocally(String command) {
        try {
            ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", command);
            pb.redirectErrorStream(false);
            
            Process process = pb.start();
            
            // Read output
            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();
            
            // Start threads to read stdout and stderr
            Thread stdoutThread = new Thread(() -> {
                try {
                    process.getInputStream().transferTo(stdout);
                } catch (IOException e) {
                    log.error("Error reading stdout: {}", e.getMessage());
                }
            });
            
            Thread stderrThread = new Thread(() -> {
                try {
                    process.getErrorStream().transferTo(stderr);
                } catch (IOException e) {
                    log.error("Error reading stderr: {}", e.getMessage());
                }
            });
            
            stdoutThread.start();
            stderrThread.start();
            
            // Wait for process completion
            boolean finished = process.waitFor(config.getTimeout(), TimeUnit.SECONDS);
            
            if (!finished) {
                process.destroyForcibly();
                return new ExecutionResult(
                    stdout.toString(StandardCharsets.UTF_8),
                    stderr.toString(StandardCharsets.UTF_8) + "\nExecution timed out",
                    124
                );
            }
            
            // Wait for output threads to complete
            stdoutThread.join(1000);
            stderrThread.join(1000);
            
            return new ExecutionResult(
                stdout.toString(StandardCharsets.UTF_8),
                stderr.toString(StandardCharsets.UTF_8),
                process.exitValue()
            );
            
        } catch (Exception e) {
            log.error("Failed to execute command locally: {}", e.getMessage(), e);
            return new ExecutionResult(
                "",
                "Local execution failed: " + e.getMessage(),
                1
            );
        }
    }
    
    /**
     * Execute a Python script in the sandbox.
     */
    public ExecutionResult executePython(String script, int timeoutSeconds) {
        String command = String.format("python3 -c %s", escapeShellArgument(script));
        return executeCommand(command, timeoutSeconds);
    }
    
    /**
     * Execute a bash script in the sandbox.
     */
    public ExecutionResult executeBash(String script, int timeoutSeconds) {
        return executeCommand(script, timeoutSeconds);
    }
    
    private String escapeShellArgument(String arg) {
        // Simple shell argument escaping
        return "'" + arg.replace("'", "'\"'\"'") + "'";
    }
    
    @Override
    public void close() throws IOException {
        if (dockerClient != null && containerId != null && isRunning) {
            try {
                log.info("Stopping sandbox container: {}", containerId);
                dockerClient.stopContainerCmd(containerId).exec();
                isRunning = false;
                log.info("Sandbox container stopped successfully");
            } catch (Exception e) {
                log.error("Failed to stop container: {}", e.getMessage(), e);
            }
        }
        
        if (dockerClient != null) {
            try {
                dockerClient.close();
            } catch (Exception e) {
                log.error("Failed to close Docker client: {}", e.getMessage(), e);
            }
        }
    }
    
    /**
     * Result of command execution.
     */
    public static class ExecutionResult {
        private final String stdout;
        private final String stderr;
        private final int exitCode;
        
        public ExecutionResult(String stdout, String stderr, int exitCode) {
            this.stdout = stdout != null ? stdout : "";
            this.stderr = stderr != null ? stderr : "";
            this.exitCode = exitCode;
        }
        
        public String getStdout() {
            return stdout;
        }
        
        public String getStderr() {
            return stderr;
        }
        
        public int getExitCode() {
            return exitCode;
        }
        
        public boolean isSuccess() {
            return exitCode == 0;
        }
        
        public String getCombinedOutput() {
            StringBuilder sb = new StringBuilder();
            if (!stdout.isEmpty()) {
                sb.append(stdout);
            }
            if (!stderr.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append("STDERR: ").append(stderr);
            }
            return sb.toString();
        }
        
        @Override
        public String toString() {
            return String.format("ExecutionResult{exitCode=%d, stdout='%s', stderr='%s'}", 
                exitCode, stdout, stderr);
        }
    }
    
    // Helper class for Docker image pulling
    private static class PullImageResultCallback extends ResultCallback.Adapter<PullResponseItem> {
        @Override
        public void onNext(PullResponseItem item) {
            // Log progress if needed
            if (item.getStatus() != null) {
                log.debug("Pull progress: {}", item.getStatus());
            }
        }
    }
}