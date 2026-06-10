package com.openmanus.agentteam.domain.service;

import com.openmanus.agentteam.domain.model.SubTask;
import com.openmanus.agentteam.domain.model.TaskGroup;
import com.openmanus.agentteam.domain.model.TaskGroupSnapshot;

import java.util.List;

public interface TaskGroupManager {

    TaskGroup createGroup(String parentTaskId, String masterAgentId, String originalUserRequest);

    void registerSubTasks(String groupId, List<SubTask> subTasks);

    TaskGroupSnapshot getSnapshot(String groupId);
}
