package com.openmanus.agentteam.domain.service;

import com.openmanus.agentteam.domain.model.SubTask;
import com.openmanus.agentteam.domain.model.TaskGroup;
import com.openmanus.agentteam.domain.model.TaskGroupSnapshot;
import com.openmanus.agentteam.domain.model.TaskGroupStatus;
import com.openmanus.agentteam.domain.model.TaskStatus;
import com.openmanus.agentteam.infra.InMemoryTaskGroupRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DefaultTaskGroupManager Tests")
class DefaultTaskGroupManagerTest {

    private final InMemoryTaskGroupRepository repository = new InMemoryTaskGroupRepository();
    private final DefaultTaskGroupManager manager =
            new DefaultTaskGroupManager(repository, new TaskGroupStatusCalculator());

    @Test
    @DisplayName("should create group and persist metadata")
    void shouldCreateGroupAndPersistMetadata() {
        TaskGroup group = manager.createGroup("parent-1", "master-1", "split this task");

        assertThat(group.getGroupId()).isNotBlank();
        assertThat(group.getParentTaskId()).isEqualTo("parent-1");
        assertThat(group.getMasterAgentId()).isEqualTo("master-1");
        assertThat(group.getOriginalUserRequest()).isEqualTo("split this task");
        assertThat(group.getStatus()).isEqualTo(TaskGroupStatus.CREATED);
        assertThat(repository.findGroup(group.getGroupId())).containsSame(group);
    }

    @Test
    @DisplayName("should register subtasks and refresh snapshot")
    void shouldRegisterSubTasksAndRefreshSnapshot() {
        TaskGroup group = manager.createGroup("parent-1", "master-1", "split this task");
        SubTask first = new SubTask("task-1", group.getGroupId(), group.getParentTaskId(), "task A", "desc A", "", 1L);
        SubTask second = new SubTask("task-2", group.getGroupId(), group.getParentTaskId(), "task B", "desc B", "", 1L);

        manager.registerSubTasks(group.getGroupId(), List.of(first, second));

        assertThat(repository.findSubTasksByGroupId(group.getGroupId()))
                .extracting(SubTask::getTaskId)
                .containsExactlyInAnyOrder("task-1", "task-2");
        assertThat(repository.findGroup(group.getGroupId()))
                .get()
                .extracting(TaskGroup::getSubTaskIds)
                .asList()
                .hasSize(2);

        first.claim("agent-1", 2L);
        first.markRunning(3L);
        first.markSucceeded("ok", "detail", 4L);
        second.claim("agent-2", 2L);
        second.markRunning(3L);
        second.markFailed("boom", 4L);
        repository.saveSubTask(first);
        repository.saveSubTask(second);

        TaskGroupSnapshot snapshot = manager.getSnapshot(group.getGroupId());

        assertThat(snapshot.status()).isEqualTo(TaskGroupStatus.PARTIAL_FAILED);
        assertThat(snapshot.succeededTasks()).isEqualTo(1);
        assertThat(snapshot.failedTasks()).isEqualTo(1);
        assertThat(repository.findGroup(group.getGroupId()))
                .get()
                .extracting(TaskGroup::getStatus)
                .isEqualTo(TaskGroupStatus.PARTIAL_FAILED);
    }

    @Test
    @DisplayName("should keep group created when registering empty subtasks")
    void shouldKeepGroupCreatedWhenRegisteringEmptySubTasks() {
        TaskGroup group = manager.createGroup("parent-1", "master-1", "split this task");

        manager.registerSubTasks(group.getGroupId(), List.of());

        assertThat(repository.findGroup(group.getGroupId()))
                .get()
                .extracting(TaskGroup::getStatus)
                .isEqualTo(TaskGroupStatus.CREATED);
        assertThat(repository.findSubTasksByGroupId(group.getGroupId())).isEmpty();
    }
}
