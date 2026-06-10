package com.openmanus.agentteam.domain.model;

import java.util.List;

/**
 * Aggregated task-group output returned by application orchestration.
 */
public record TaskGroupResult(
        String groupId,
        List<SubTaskResult> successResults,
        List<SubTaskFailure> failures,
        boolean allSucceeded
) {

    public TaskGroupResult {
        successResults = successResults == null ? List.of() : List.copyOf(successResults);
        failures = failures == null ? List.of() : List.copyOf(failures);
    }
}
