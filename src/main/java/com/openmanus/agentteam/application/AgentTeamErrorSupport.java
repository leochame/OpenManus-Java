package com.openmanus.agentteam.application;

import com.openmanus.domain.model.ExecutionErrorCodes;

/**
 * Shared error helpers for the agent-team execution path.
 */
public final class AgentTeamErrorSupport {

    private AgentTeamErrorSupport() {
    }

    public static Throwable unwrap(Throwable throwable) {
        if (throwable == null) {
            return new AgentTeamExecutionFailedException("agentteam execution failed");
        }
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    public static String safeMessage(Throwable throwable) {
        if (throwable == null) {
            return "agentteam execution failed";
        }
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return "agentteam execution failed";
        }
        return message.trim();
    }

    public static String errorCode(Throwable throwable) {
        Throwable actual = unwrap(throwable);
        if (actual instanceof AgentTeamException agentTeamException) {
            return agentTeamException.getErrorCode();
        }
        return ExecutionErrorCodes.INTERNAL_ERROR;
    }

    public static String errorType(Throwable throwable) {
        Throwable actual = unwrap(throwable);
        return actual.getClass().getSimpleName();
    }
}
