package com.openmanus.agentteam.application;

/**
 * Output returned after one subtask is executed by a subagent.
 */
public record SubTaskExecutionOutput(String summary, String detail) {

    public SubTaskExecutionOutput {
        summary = summary == null ? "" : summary.trim();
        detail = detail == null ? "" : detail.trim();
    }
}
