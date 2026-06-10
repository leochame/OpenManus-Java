package com.openmanus.agentteam.domain.model;

import java.util.List;

/**
 * One code-oriented subtask that may run inside an isolated worktree.
 */
public record CodeSubTask(
        String taskId,
        String title,
        String goal,
        List<String> ownedPaths,
        List<String> forbiddenPaths,
        List<String> verificationCommands,
        List<String> dependsOn,
        String conflictRisk
) {

    public CodeSubTask {
        ownedPaths = ownedPaths == null ? List.of() : List.copyOf(ownedPaths);
        forbiddenPaths = forbiddenPaths == null ? List.of() : List.copyOf(forbiddenPaths);
        verificationCommands = verificationCommands == null ? List.of() : List.copyOf(verificationCommands);
        dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
    }
}
