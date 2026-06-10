package com.openmanus.agentteam.domain.service;

import com.openmanus.agentteam.domain.model.SubTask;
import com.openmanus.agentteam.domain.model.TaskGroup;
import com.openmanus.agentteam.domain.model.TaskGroupResult;

import java.util.List;

public interface ResultAggregationService {

    TaskGroupResult aggregate(TaskGroup taskGroup, List<SubTask> subTasks);
}
