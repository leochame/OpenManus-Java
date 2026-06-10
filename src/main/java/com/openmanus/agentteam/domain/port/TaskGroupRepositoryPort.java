package com.openmanus.agentteam.domain.port;

import com.openmanus.agentteam.domain.model.SubTask;
import com.openmanus.agentteam.domain.model.TaskGroup;

import java.util.List;
import java.util.Optional;

public interface TaskGroupRepositoryPort {

    void saveGroup(TaskGroup group);

    Optional<TaskGroup> findGroup(String groupId);

    void saveSubTask(SubTask subTask);

    Optional<SubTask> findSubTask(String taskId);

    List<SubTask> findSubTasksByGroupId(String groupId);
}
