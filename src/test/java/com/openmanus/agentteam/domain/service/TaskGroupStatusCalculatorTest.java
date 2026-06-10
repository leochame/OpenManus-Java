package com.openmanus.agentteam.domain.service;

import com.openmanus.agentteam.domain.model.SubTask;
import com.openmanus.agentteam.domain.model.TaskGroupSnapshot;
import com.openmanus.agentteam.domain.model.TaskGroupStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TaskGroupStatusCalculator Tests")
class TaskGroupStatusCalculatorTest {

    private final TaskGroupStatusCalculator calculator = new TaskGroupStatusCalculator();

    @Test
    @DisplayName("should mark empty group as created")
    void shouldMarkEmptyGroupAsCreated() {
        TaskGroupSnapshot snapshot = calculator.calculate("group-1", List.of());

        assertThat(snapshot.status()).isEqualTo(TaskGroupStatus.CREATED);
        assertThat(snapshot.totalTasks()).isZero();
    }

    @Test
    @DisplayName("should mark all-success group as completed")
    void shouldMarkAllSuccessGroupAsCompleted() {
        SubTask first = new SubTask("task-1", "group-1", "parent-1", "A", "desc", "", 1L);
        SubTask second = new SubTask("task-2", "group-1", "parent-1", "B", "desc", "", 1L);
        first.claim("agent-1", 2L);
        first.markRunning(3L);
        first.markSucceeded("ok", "detail", 4L);
        second.claim("agent-2", 2L);
        second.markRunning(3L);
        second.markSucceeded("ok", "detail", 4L);

        TaskGroupSnapshot snapshot = calculator.calculate("group-1", List.of(first, second));

        assertThat(snapshot.status()).isEqualTo(TaskGroupStatus.COMPLETED);
        assertThat(snapshot.succeededTasks()).isEqualTo(2);
        assertThat(snapshot.allFinished()).isTrue();
    }

    @Test
    @DisplayName("should mark mixed finished group as partial failed")
    void shouldMarkMixedFinishedGroupAsPartialFailed() {
        SubTask success = new SubTask("task-1", "group-1", "parent-1", "A", "desc", "", 1L);
        SubTask failed = new SubTask("task-2", "group-1", "parent-1", "B", "desc", "", 1L);
        success.claim("agent-1", 2L);
        success.markRunning(3L);
        success.markSucceeded("ok", "detail", 4L);
        failed.claim("agent-2", 2L);
        failed.markRunning(3L);
        failed.markFailed("boom", 4L);

        TaskGroupSnapshot snapshot = calculator.calculate("group-1", List.of(success, failed));

        assertThat(snapshot.status()).isEqualTo(TaskGroupStatus.PARTIAL_FAILED);
        assertThat(snapshot.failedTasks()).isEqualTo(1);
        assertThat(snapshot.allFinished()).isTrue();
    }

    @Test
    @DisplayName("should mark claimed or running tasks as running")
    void shouldMarkClaimedOrRunningTasksAsRunning() {
        SubTask claimed = new SubTask("task-1", "group-1", "parent-1", "A", "desc", "", 1L);
        SubTask running = new SubTask("task-2", "group-1", "parent-1", "B", "desc", "", 1L);
        claimed.claim("agent-1", 2L);
        running.claim("agent-2", 2L);
        running.markRunning(3L);

        TaskGroupSnapshot snapshot = calculator.calculate("group-1", List.of(claimed, running));

        assertThat(snapshot.status()).isEqualTo(TaskGroupStatus.RUNNING);
        assertThat(snapshot.claimedTasks()).isEqualTo(1);
        assertThat(snapshot.runningTasks()).isEqualTo(1);
        assertThat(snapshot.allFinished()).isFalse();
    }
}
