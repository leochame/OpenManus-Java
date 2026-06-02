package com.openmanus.agentteam.application;

import com.openmanus.domain.model.ExecutionErrorCodes;

/**
 * Raised when a subtask attempts an invalid lifecycle transition.
 */
public class InvalidTaskStateTransitionException extends AgentTeamException {

    public InvalidTaskStateTransitionException(String message) {
        super(ExecutionErrorCodes.AGENTTEAM_TASK_STATE_INVALID, message);
    }
}
