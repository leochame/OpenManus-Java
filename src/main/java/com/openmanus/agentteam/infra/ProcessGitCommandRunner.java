package com.openmanus.agentteam.infra;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Runs local Git CLI commands through {@link ProcessBuilder}.
 */
class ProcessGitCommandRunner implements GitCommandRunner {

    private static final long COMMAND_TIMEOUT_SECONDS = 30L;

    @Override
    public GitCommandResult run(Path workingDirectory, List<String> command) {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        if (workingDirectory != null) {
            processBuilder.directory(workingDirectory.toFile());
        }
        try {
            Process process = processBuilder.start();
            boolean finished = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new GitWorktreeProvisioningException("git command timed out: " + String.join(" ", command));
            }
            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            return new GitCommandResult(process.exitValue(), stdout, stderr);
        } catch (IOException exception) {
            throw new GitWorktreeProvisioningException(
                    "failed to start git command: " + String.join(" ", command),
                    exception
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new GitWorktreeProvisioningException(
                    "git command interrupted: " + String.join(" ", command),
                    exception
            );
        }
    }
}
