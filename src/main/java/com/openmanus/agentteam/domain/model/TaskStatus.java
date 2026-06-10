package com.openmanus.agentteam.domain.model;

/**
 * Lifecycle for a single subtask inside an agent-team execution.
 */
public enum TaskStatus {
    PENDING,
    CLAIMED,
    RUNNING,
    SUCCEEDED,
    FAILED;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED;
    }
}
