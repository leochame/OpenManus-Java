package com.openmanus.domain.model;

public final class ExecutionErrorCodes {

    private ExecutionErrorCodes() {
    }

    public static final String INPUT_INVALID = "INPUT_INVALID";
    public static final String SESSION_BUSY = "SESSION_BUSY";
    public static final String ASYNC_SUBMIT_REJECTED = "ASYNC_SUBMIT_REJECTED";
    public static final String ASYNC_SUBMIT_EXCEPTION = "ASYNC_SUBMIT_EXCEPTION";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    public static final String AGENTTEAM_TASK_OWNERSHIP_VIOLATION = "AGENTTEAM_TASK_OWNERSHIP_VIOLATION";
    public static final String AGENTTEAM_TASK_STATE_INVALID = "AGENTTEAM_TASK_STATE_INVALID";
    public static final String AGENTTEAM_EXECUTION_FAILED = "AGENTTEAM_EXECUTION_FAILED";
    // Fallback-only code for controller status mapping when upstream returns an unknown business error code.
    // It is not a stable output of ExecutionStreamingApplicationService in normal execution paths.
    public static final String UNKNOWN_ERROR = "UNKNOWN_ERROR";
}
