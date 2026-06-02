package com.openmanus.agentteam.infra;

import com.openmanus.agentteam.application.InvalidTaskStateTransitionException;
import com.openmanus.agentteam.application.TaskOwnershipViolationException;
import com.openmanus.agentteam.domain.model.SubTask;
import com.openmanus.agentteam.domain.model.TaskStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InMemoryTaskPool Tests")
class InMemoryTaskPoolTest {

    @Test
    @DisplayName("should claim pending task once")
    void shouldClaimPendingTaskOnce() {
        InMemoryTaskGroupRepository repository = new InMemoryTaskGroupRepository();
        InMemoryTaskPool taskPool = new InMemoryTaskPool(repository);
        SubTask subTask = new SubTask("task-1", "group-1", "parent-1", "A", "desc", "", 1L);

        taskPool.submit(subTask);

        Optional<SubTask> claimed = taskPool.claimNext("agent-1");

        assertThat(claimed).isPresent();
        assertThat(claimed.get().getAssignedAgentId()).isEqualTo("agent-1");
        assertThat(claimed.get().getStatus()).isEqualTo(TaskStatus.CLAIMED);
        assertThat(taskPool.claimNext("agent-2")).isEmpty();
    }

    @Test
    @DisplayName("should prevent duplicate claim under concurrency")
    void shouldPreventDuplicateClaimUnderConcurrency() throws InterruptedException {
        InMemoryTaskGroupRepository repository = new InMemoryTaskGroupRepository();
        InMemoryTaskPool taskPool = new InMemoryTaskPool(repository);
        taskPool.submit(new SubTask("task-1", "group-1", "parent-1", "A", "desc", "", 1L));

        AtomicInteger successClaims = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executorService = Executors.newFixedThreadPool(2);

        Runnable claimer = () -> {
            ready.countDown();
            try {
                start.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            if (taskPool.claimNext(Thread.currentThread().getName()).isPresent()) {
                successClaims.incrementAndGet();
            }
        };

        executorService.submit(claimer);
        executorService.submit(claimer);
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        executorService.shutdown();
        assertThat(executorService.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        assertThat(successClaims.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("should allow owner to complete claimed task through running state")
    void shouldAllowOwnerToCompleteClaimedTaskThroughRunningState() {
        InMemoryTaskGroupRepository repository = new InMemoryTaskGroupRepository();
        InMemoryTaskPool taskPool = new InMemoryTaskPool(repository);
        taskPool.submit(new SubTask("task-1", "group-1", "parent-1", "A", "desc", "", 1L));

        SubTask claimed = taskPool.claimNext("agent-1").orElseThrow();
        taskPool.markRunning(claimed.getTaskId(), "agent-1");
        taskPool.markSucceeded(claimed.getTaskId(), "agent-1", "done", "detail");

        SubTask stored = taskPool.findById(claimed.getTaskId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(TaskStatus.SUCCEEDED);
        assertThat(stored.getResultSummary()).isEqualTo("done");
        assertThat(stored.getResultDetail()).isEqualTo("detail");
    }

    @Test
    @DisplayName("should reject non owner when marking running")
    void shouldRejectNonOwnerWhenMarkingRunning() {
        InMemoryTaskGroupRepository repository = new InMemoryTaskGroupRepository();
        InMemoryTaskPool taskPool = new InMemoryTaskPool(repository);
        taskPool.submit(new SubTask("task-1", "group-1", "parent-1", "A", "desc", "", 1L));

        SubTask claimed = taskPool.claimNext("agent-1").orElseThrow();

        assertThatThrownBy(() -> taskPool.markRunning(claimed.getTaskId(), "agent-2"))
                .isInstanceOf(TaskOwnershipViolationException.class)
                .hasMessageContaining("Task is not owned by agent");
    }

    @Test
    @DisplayName("should reject non owner when writing success result")
    void shouldRejectNonOwnerWhenWritingSuccessResult() {
        InMemoryTaskGroupRepository repository = new InMemoryTaskGroupRepository();
        InMemoryTaskPool taskPool = new InMemoryTaskPool(repository);
        taskPool.submit(new SubTask("task-1", "group-1", "parent-1", "A", "desc", "", 1L));

        SubTask claimed = taskPool.claimNext("agent-1").orElseThrow();
        taskPool.markRunning(claimed.getTaskId(), "agent-1");

        assertThatThrownBy(() -> taskPool.markSucceeded(claimed.getTaskId(), "agent-2", "done", "detail"))
                .isInstanceOf(TaskOwnershipViolationException.class)
                .hasMessageContaining("Task is not owned by agent");
    }

    @Test
    @DisplayName("should reject unclaimed task writeback")
    void shouldRejectUnclaimedTaskWriteback() {
        InMemoryTaskGroupRepository repository = new InMemoryTaskGroupRepository();
        InMemoryTaskPool taskPool = new InMemoryTaskPool(repository);
        taskPool.submit(new SubTask("task-1", "group-1", "parent-1", "A", "desc", "", 1L));

        assertThatThrownBy(() -> taskPool.markSucceeded("task-1", "agent-1", "done", "detail"))
                .isInstanceOf(InvalidTaskStateTransitionException.class)
                .hasMessageContaining("Task has not been claimed yet");
    }

    @Test
    @DisplayName("should reject success before running")
    void shouldRejectSuccessBeforeRunning() {
        InMemoryTaskGroupRepository repository = new InMemoryTaskGroupRepository();
        InMemoryTaskPool taskPool = new InMemoryTaskPool(repository);
        taskPool.submit(new SubTask("task-1", "group-1", "parent-1", "A", "desc", "", 1L));

        SubTask claimed = taskPool.claimNext("agent-1").orElseThrow();

        assertThatThrownBy(() -> taskPool.markSucceeded(claimed.getTaskId(), "agent-1", "done", "detail"))
                .isInstanceOf(InvalidTaskStateTransitionException.class)
                .hasMessageContaining("Only running task can be marked succeeded");
    }

    @Test
    @DisplayName("should reject rewrite after task finished")
    void shouldRejectRewriteAfterTaskFinished() {
        InMemoryTaskGroupRepository repository = new InMemoryTaskGroupRepository();
        InMemoryTaskPool taskPool = new InMemoryTaskPool(repository);
        taskPool.submit(new SubTask("task-1", "group-1", "parent-1", "A", "desc", "", 1L));

        SubTask claimed = taskPool.claimNext("agent-1").orElseThrow();
        taskPool.markRunning(claimed.getTaskId(), "agent-1");
        taskPool.markSucceeded(claimed.getTaskId(), "agent-1", "done", "detail");

        assertThatThrownBy(() -> taskPool.markFailed(claimed.getTaskId(), "agent-1", "boom"))
                .isInstanceOf(InvalidTaskStateTransitionException.class)
                .hasMessageContaining("Task already finished");
    }
}
