package com.openmanus.agentteam.domain.model;

import java.util.List;

/**
 * Structured result emitted by one sub-agent coding execution.
 */
public record SubAgentCodingResult(
        String taskId,
        SubAgentCodingStatus status,
        String summary,
        List<String> changedFiles,
        String branchName,
        String commitSha,
        String worktreePath,
        boolean testPassed,
        String testSummary,
        String rawOutput,
        String errorMessage
) {

    public SubAgentCodingResult {
        changedFiles = changedFiles == null ? List.of() : List.copyOf(changedFiles);
    }
}
