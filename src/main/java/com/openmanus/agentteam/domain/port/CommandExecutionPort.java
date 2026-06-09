package com.openmanus.agentteam.domain.port;

import java.nio.file.Path;

/**
 * Runs local verification commands for integration validation.
 */
public interface CommandExecutionPort {

    CommandExecutionResult execute(Path workingDirectory, String command);

    record CommandExecutionResult(int exitCode, String stdout, String stderr) {
        public boolean isSuccess() {
            return exitCode == 0;
        }
    }
}
