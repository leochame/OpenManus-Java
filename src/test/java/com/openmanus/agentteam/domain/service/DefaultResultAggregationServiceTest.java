package com.openmanus.agentteam.domain.service;

import com.openmanus.agentteam.domain.model.SubTask;
import com.openmanus.agentteam.domain.model.TaskGroup;
import com.openmanus.agentteam.domain.model.TaskGroupResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DefaultResultAggregationService Tests")
class DefaultResultAggregationServiceTest {

    private final DefaultResultAggregationService aggregationService = new DefaultResultAggregationService();

    @Test
    @DisplayName("should aggregate success and failure results")
    void shouldAggregateSuccessAndFailureResults() {
        TaskGroup group = new TaskGroup("group-1", "parent-1", "master-1", "request", 1L);
        SubTask success = new SubTask("task-1", "group-1", "parent-1", "task A", "desc", "", 1L);
        SubTask failed = new SubTask("task-2", "group-1", "parent-1", "task B", "desc", "", 1L);
        SubTask pending = new SubTask("task-3", "group-1", "parent-1", "task C", "desc", "", 1L);

        success.claim("agent-1", 2L);
        success.markRunning(3L);
        success.markSucceeded("summary A", "detail A", 4L);
        failed.claim("agent-2", 2L);
        failed.markRunning(3L);
        failed.markFailed("boom", 4L);

        TaskGroupResult result = aggregationService.aggregate(group, java.util.Arrays.asList(success, failed, pending, null));

        assertThat(result.groupId()).isEqualTo("group-1");
        assertThat(result.allSucceeded()).isFalse();
        assertThat(result.successResults())
                .extracting(item -> item.title() + ":" + item.summary())
                .containsExactly("task A:summary A");
        assertThat(result.failures())
                .extracting(item -> item.title() + ":" + item.errorMessage())
                .containsExactly("task B:boom");
    }

    @Test
    @DisplayName("should mark all succeeded when there are no failures")
    void shouldMarkAllSucceededWhenThereAreNoFailures() {
        TaskGroup group = new TaskGroup("group-1", "parent-1", "master-1", "request", 1L);
        SubTask success = new SubTask("task-1", "group-1", "parent-1", "task A", "desc", "", 1L);
        success.claim("agent-1", 2L);
        success.markRunning(3L);
        success.markSucceeded("summary A", "detail A", 4L);

        TaskGroupResult result = aggregationService.aggregate(group, List.of(success));

        assertThat(result.allSucceeded()).isTrue();
        assertThat(result.failures()).isEmpty();
        assertThat(result.successResults()).hasSize(1);
    }
}
