package com.openmanus.agentteam.domain.model;

/**
 * Successful subtask result prepared for final aggregation.
 */
public record SubTaskResult(
        String taskId,
        String title,
        String summary,
        String detail
) {
}
