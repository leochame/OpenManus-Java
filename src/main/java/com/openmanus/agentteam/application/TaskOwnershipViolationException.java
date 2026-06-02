package com.openmanus.agentteam.application;

import com.openmanus.domain.model.ExecutionErrorCodes;

/**
 * Raised when a task writeback is attempted by a non-owner agent.
 */
public class TaskOwnershipViolationException extends AgentTeamException {

    public TaskOwnershipViolationException(String message) {
        super(ExecutionErrorCodes.AGENTTEAM_TASK_OWNERSHIP_VIOLATION, message);
    }
}
