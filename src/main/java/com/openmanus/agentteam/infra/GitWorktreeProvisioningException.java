package com.openmanus.agentteam.infra;

/**
 * Signals a local Git worktree provisioning failure.
 */
public class GitWorktreeProvisioningException extends RuntimeException {

    public GitWorktreeProvisioningException(String message) {
        super(message);
    }

    public GitWorktreeProvisioningException(String message, Throwable cause) {
        super(message, cause);
    }
}
