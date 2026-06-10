package com.openmanus.agentteam.domain.model;

import java.util.List;

/**
 * Result of asking the system whether a request can be decomposed safely.
 */
public record DecompositionPlan(
        boolean parallelizable,
        String reason,
        List<SubTaskPlan> subTasks
) {

    public DecompositionPlan {
        reason = reason == null ? "" : reason.trim();
        subTasks = subTasks == null ? List.of() : List.copyOf(subTasks);
    }
}
