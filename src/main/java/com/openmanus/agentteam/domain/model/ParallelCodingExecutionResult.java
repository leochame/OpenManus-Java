package com.openmanus.agentteam.domain.model;

import java.util.List;

/**
 * Aggregated result for one parallel coding orchestration attempt.
 */
public record ParallelCodingExecutionResult(
        boolean success,
        boolean fallbackToSingleAgent,
        String summary,
        String fallbackResponse,
        CodeTaskGroup taskGroup,
        List<SubAgentCodingResult> subAgentResults,
        IntegrationResult integrationResult
) {

    public ParallelCodingExecutionResult {
        subAgentResults = subAgentResults == null ? List.of() : List.copyOf(subAgentResults);
    }
}
