package com.openmanus.agentteam.application;

import com.openmanus.infra.exception.OpenManusException;

/**
 * Base runtime exception for agent-team execution path.
 */
public class AgentTeamException extends OpenManusException {

    private final String errorCode;

    public AgentTeamException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public AgentTeamException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
