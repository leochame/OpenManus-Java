package com.openmanus.agentteam.application;

/**
 * Thrown by {@link ParallelCodingOrchestrator} when a coding request cannot proceed
 * in host mode — for example, when the target repository does not support git
 * worktree operations or the request cannot be safely parallelized.
 *
 * <p>Carries a stable {@link #errorCode()} (matching a constant in
 * {@code ExecutionErrorCodes}) and a user-facing {@link #userMessage()} that
 * explains what went wrong in plain language.
 *
 * <p>The orchestrator MUST NOT silently fall back to the Docker sandbox for
 * coding requests — that would route code-generation work into an empty container
 * with no access to the target repository, causing the agent to loop uselessly.
 */
public class ParallelCodingException extends RuntimeException {

    private final String errorCode;
    private final String userMessage;

    public ParallelCodingException(String errorCode, String userMessage) {
        super(userMessage);
        this.errorCode = errorCode;
        this.userMessage = userMessage;
    }

    /**
     * Stable error code suitable for programmatic handling (e.g. HTTP status mapping).
     */
    public String errorCode() {
        return errorCode;
    }

    /**
     * Human-readable description intended to be shown to the end user.
     */
    public String userMessage() {
        return userMessage;
    }
}
