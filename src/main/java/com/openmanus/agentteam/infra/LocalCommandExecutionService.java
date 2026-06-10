package com.openmanus.agentteam.infra;

import com.openmanus.agentteam.domain.port.CommandExecutionPort;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Executes local shell commands for integration verification.
 */
public class LocalCommandExecutionService implements CommandExecutionPort {

    private static final long COMMAND_TIMEOUT_SECONDS = 60L;

    @Override
    public CommandExecutionResult execute(Path workingDirectory, String command) {
        if (workingDirectory == null) {
            throw new IllegalArgumentException("workingDirectory must not be null");
        }
        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException("command must not be blank");
        }
        List<String> shellCommand = buildShellCommand(command.trim());
        ProcessBuilder processBuilder = new ProcessBuilder(shellCommand);
        processBuilder.directory(workingDirectory.toFile());
        try {
            Process process = processBuilder.start();
            boolean finished = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new CommandExecutionResult(124, "", "command timed out: " + command);
            }
            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            return new CommandExecutionResult(process.exitValue(), stdout, stderr);
        } catch (IOException exception) {
            throw new GitWorktreeProvisioningException("failed to start verification command: " + command, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new GitWorktreeProvisioningException("verification command interrupted: " + command, exception);
        }
    }

    private List<String> buildShellCommand(String command) {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        if (windows) {
            return List.of("powershell", "-NoProfile", "-Command", command);
        }
        return List.of("sh", "-lc", command);
    }
}
