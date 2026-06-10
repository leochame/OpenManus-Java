package com.openmanus.agentteam.domain.model;

import java.util.List;

/**
 * Summary of integration-stage results for multiple sub-agent branches.
 */
public record IntegrationResult(
        boolean success,
        String integrationBranch,
        List<String> mergedBranches,
        List<String> conflictFiles,
        String testSummary,
        String errorMessage
) {

    public IntegrationResult {
        mergedBranches = mergedBranches == null ? List.of() : List.copyOf(mergedBranches);
        conflictFiles = conflictFiles == null ? List.of() : List.copyOf(conflictFiles);
    }
}
