package com.openmanus.agentteam.application;

import com.openmanus.domain.model.ExecutionErrorCodes;

/**
 * Raised when the agent-team execution pipeline fails unexpectedly.
 */
public class AgentTeamExecutionFailedException extends AgentTeamException {

    public AgentTeamExecutionFailedException(String message) {
        super(ExecutionErrorCodes.AGENTTEAM_EXECUTION_FAILED, message);
    }

    public AgentTeamExecutionFailedException(String message, Throwable cause) {
        super(ExecutionErrorCodes.AGENTTEAM_EXECUTION_FAILED, message, cause);
    }
}
