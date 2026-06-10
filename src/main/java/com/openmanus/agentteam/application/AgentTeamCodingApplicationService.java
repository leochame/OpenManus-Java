package com.openmanus.agentteam.application;

import com.openmanus.agentteam.domain.model.ParallelCodingExecutionResult;

import java.nio.file.Path;

/**
 * Thin facade for worktree-based multi-agent coding use cases.
 */
public class AgentTeamCodingApplicationService {

    private final ParallelCodingOrchestrator parallelCodingOrchestrator;

    public AgentTeamCodingApplicationService(ParallelCodingOrchestrator parallelCodingOrchestrator) {
        this.parallelCodingOrchestrator = parallelCodingOrchestrator;
    }

    public ParallelCodingExecutionResult execute(String userInput, String conversationId, Path repositoryPath) {
        return parallelCodingOrchestrator.execute(userInput, conversationId, repositoryPath);
    }

    public ParallelCodingExecutionResult execute(
            String userInput,
            String conversationId,
            String targetRepositoryPath,
            Path defaultRepositoryPath
    ) {
        Path repositoryPath = resolveRepositoryPath(targetRepositoryPath, defaultRepositoryPath);
        return parallelCodingOrchestrator.execute(userInput, conversationId, repositoryPath);
    }

    private Path resolveRepositoryPath(String targetRepositoryPath, Path defaultRepositoryPath) {
        if (targetRepositoryPath == null || targetRepositoryPath.isBlank()) {
            return defaultRepositoryPath;
        }
        return Path.of(targetRepositoryPath.trim()).toAbsolutePath().normalize();
    }
}
