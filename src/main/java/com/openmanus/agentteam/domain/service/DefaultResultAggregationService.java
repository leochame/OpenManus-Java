package com.openmanus.agentteam.domain.service;

import com.openmanus.agentteam.domain.model.SubTask;
import com.openmanus.agentteam.domain.model.SubTaskFailure;
import com.openmanus.agentteam.domain.model.SubTaskResult;
import com.openmanus.agentteam.domain.model.TaskGroup;
import com.openmanus.agentteam.domain.model.TaskGroupResult;
import com.openmanus.agentteam.domain.model.TaskStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * Default aggregation logic for one task group.
 */
public class DefaultResultAggregationService implements ResultAggregationService {

    @Override
    public TaskGroupResult aggregate(TaskGroup taskGroup, List<SubTask> subTasks) {
        List<SubTaskResult> successResults = new ArrayList<>();
        List<SubTaskFailure> failures = new ArrayList<>();

        if (subTasks != null) {
            for (SubTask subTask : subTasks) {
                if (subTask == null || subTask.getStatus() == null) {
                    continue;
                }
                if (subTask.getStatus() == TaskStatus.SUCCEEDED) {
                    successResults.add(new SubTaskResult(
                            subTask.getTaskId(),
                            subTask.getTitle(),
                            subTask.getResultSummary(),
                            subTask.getResultDetail()
                    ));
                } else if (subTask.getStatus() == TaskStatus.FAILED) {
                    failures.add(new SubTaskFailure(
                            subTask.getTaskId(),
                            subTask.getTitle(),
                            subTask.getErrorMessage()
                    ));
                }
            }
        }

        return new TaskGroupResult(
                taskGroup.getGroupId(),
                successResults,
                failures,
                failures.isEmpty()
        );
    }
}
