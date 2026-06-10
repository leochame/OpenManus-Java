package com.openmanus.agentteam.domain.model;

import java.util.List;

/**
 * Planning result for parallel coding execution.
 */
public record ParallelCodingPlan(
        boolean parallelizable,
        String reason,
        List<CodeSubTask> subTasks
) {

    public ParallelCodingPlan {
        subTasks = subTasks == null ? List.of() : List.copyOf(subTasks);
    }
}
