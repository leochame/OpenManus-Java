package com.openmanus.agentteam.domain.service;

import com.openmanus.agentteam.domain.model.SubTask;
import com.openmanus.agentteam.domain.model.TaskGroup;
import com.openmanus.agentteam.domain.model.TaskGroupSnapshot;
import com.openmanus.agentteam.domain.port.TaskGroupRepositoryPort;

import java.util.List;
import java.util.UUID;

/**
 * Default domain implementation for task-group lifecycle management.
 */
public class DefaultTaskGroupManager implements TaskGroupManager {

    private final TaskGroupRepositoryPort repository;
    private final TaskGroupStatusCalculator statusCalculator;

    public DefaultTaskGroupManager(
            TaskGroupRepositoryPort repository,
            TaskGroupStatusCalculator statusCalculator
    ) {
        this.repository = repository;
        this.statusCalculator = statusCalculator;
    }

    @Override
    public TaskGroup createGroup(String parentTaskId, String masterAgentId, String originalUserRequest) {
        long now = System.currentTimeMillis();
        TaskGroup group = new TaskGroup(
                UUID.randomUUID().toString(),
                parentTaskId,
                masterAgentId,
                originalUserRequest,
                now
        );
        repository.saveGroup(group);
        return group;
    }

    @Override
    public void registerSubTasks(String groupId, List<SubTask> subTasks) {
        TaskGroup group = repository.findGroup(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Task group not found: " + groupId));
        if (subTasks == null || subTasks.isEmpty()) {
            repository.saveGroup(group);
            return;
        }
        for (SubTask subTask : subTasks) {
            if (subTask == null) {
                continue;
            }
            group.addSubTask(subTask.getTaskId());
            repository.saveSubTask(subTask);
        }
        group.updateStatus(statusCalculator.calculate(groupId, subTasks).status(), System.currentTimeMillis());
        repository.saveGroup(group);
    }

    @Override
    public TaskGroupSnapshot getSnapshot(String groupId) {
        List<SubTask> subTasks = repository.findSubTasksByGroupId(groupId);
        TaskGroupSnapshot snapshot = statusCalculator.calculate(groupId, subTasks);
        repository.findGroup(groupId).ifPresent(group -> {
            group.updateStatus(snapshot.status(), System.currentTimeMillis());
            repository.saveGroup(group);
        });
        return snapshot;
    }
}
