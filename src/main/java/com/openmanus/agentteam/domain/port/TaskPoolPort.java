package com.openmanus.agentteam.domain.port;

import com.openmanus.agentteam.domain.model.SubTask;

import java.util.List;
import java.util.Optional;

public interface TaskPoolPort {

    void submit(SubTask subTask);

    Optional<SubTask> claimNext(String agentId);

    void markRunning(String taskId, String agentId);

    void markSucceeded(String taskId, String agentId, String summary, String detail);

    void markFailed(String taskId, String agentId, String errorMessage);

    Optional<SubTask> findById(String taskId);

    List<SubTask> findByGroupId(String groupId);
}
