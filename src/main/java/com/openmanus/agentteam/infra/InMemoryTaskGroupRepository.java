package com.openmanus.agentteam.infra;

import com.openmanus.agentteam.domain.model.SubTask;
import com.openmanus.agentteam.domain.model.TaskGroup;
import com.openmanus.agentteam.domain.port.TaskGroupRepositoryPort;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory repository for task-group runtime state.
 */
public class InMemoryTaskGroupRepository implements TaskGroupRepositoryPort {

    private final ConcurrentHashMap<String, TaskGroup> groups = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SubTask> subTasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> groupToTaskIds = new ConcurrentHashMap<>();

    @Override
    public void saveGroup(TaskGroup group) {
        if (group == null) {
            return;
        }
        groups.put(group.getGroupId(), group);
    }

    @Override
    public Optional<TaskGroup> findGroup(String groupId) {
        return Optional.ofNullable(groups.get(groupId));
    }

    @Override
    public void saveSubTask(SubTask subTask) {
        if (subTask == null) {
            return;
        }
        subTasks.put(subTask.getTaskId(), subTask);
        groupToTaskIds.computeIfAbsent(subTask.getGroupId(), ignored -> ConcurrentHashMap.newKeySet())
                .add(subTask.getTaskId());
    }

    @Override
    public Optional<SubTask> findSubTask(String taskId) {
        return Optional.ofNullable(subTasks.get(taskId));
    }

    @Override
    public List<SubTask> findSubTasksByGroupId(String groupId) {
        Set<String> taskIds = groupToTaskIds.getOrDefault(groupId, Set.of());
        List<SubTask> results = new ArrayList<>(taskIds.size());
        for (String taskId : taskIds) {
            SubTask subTask = subTasks.get(taskId);
            if (subTask != null) {
                results.add(subTask);
            }
        }
        return results;
    }
}
