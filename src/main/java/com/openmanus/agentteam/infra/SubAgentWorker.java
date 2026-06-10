package com.openmanus.agentteam.infra;

import com.openmanus.agentteam.application.AgentTeamErrorSupport;
import com.openmanus.agentteam.application.SubAgentExecutionService;
import com.openmanus.agentteam.application.SubTaskExecutionOutput;
import com.openmanus.agentteam.domain.model.AgentMessage;
import com.openmanus.agentteam.domain.model.SubTask;
import com.openmanus.agentteam.domain.port.AgentMessageBusPort;
import com.openmanus.agentteam.domain.port.TaskPoolPort;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Polling worker for V1 agent-team execution.
 */
@Slf4j
public class SubAgentWorker implements Runnable {

    private final String agentId;
    private final TaskPoolPort taskPoolPort;
    private final AgentMessageBusPort messageBusPort;
    private final SubAgentExecutionService executionService;
    private final long idlePollIntervalMillis;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public SubAgentWorker(
            String agentId,
            TaskPoolPort taskPoolPort,
            AgentMessageBusPort messageBusPort,
            SubAgentExecutionService executionService,
            long idlePollIntervalMillis
    ) {
        this.agentId = agentId;
        this.taskPoolPort = taskPoolPort;
        this.messageBusPort = messageBusPort;
        this.executionService = executionService;
        this.idlePollIntervalMillis = idlePollIntervalMillis;
    }

    public String getAgentId() {
        return agentId;
    }

    public void stop() {
        running.set(false);
    }

    @Override
    public void run() {
        log.info("SubAgentWorker started: agentId={}, idlePollIntervalMillis={}", agentId, idlePollIntervalMillis);
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            drainMailbox();
            SubTask subTask = taskPoolPort.claimNext(agentId).orElse(null);
            if (subTask == null) {
                sleepQuietly(idlePollIntervalMillis);
                continue;
            }
            executeClaimedTask(subTask);
        }
        log.info("SubAgentWorker stopped: agentId={}", agentId);
    }

    private void drainMailbox() {
        List<AgentMessage> unreadMessages = messageBusPort.fetchUnread(agentId);
        if (unreadMessages.isEmpty()) {
            return;
        }
        List<String> messageIds = unreadMessages.stream()
                .map(AgentMessage::messageId)
                .filter(id -> id != null && !id.isBlank())
                .toList();
        messageBusPort.markAsRead(agentId, messageIds);
    }

    private void executeClaimedTask(SubTask subTask) {
        try {
            log.info(
                    "SubAgentWorker executing claimed task: agentId={}, groupId={}, taskId={}, title={}",
                    agentId,
                    subTask.getGroupId(),
                    subTask.getTaskId(),
                    subTask.getTitle()
            );
            taskPoolPort.markRunning(subTask.getTaskId(), agentId);
            SubTaskExecutionOutput output = executionService.execute(subTask, agentId);
            taskPoolPort.markSucceeded(subTask.getTaskId(), agentId, output.summary(), output.detail());
            log.info(
                    "SubAgentWorker completed task: agentId={}, groupId={}, taskId={}, title={}, summary={}",
                    agentId,
                    subTask.getGroupId(),
                    subTask.getTaskId(),
                    subTask.getTitle(),
                    summarize(output.summary())
            );
        } catch (Exception exception) {
            handleExecutionFailure(subTask, exception);
        }
    }

    private void handleExecutionFailure(SubTask subTask, Exception exception) {
        String errorMessage = AgentTeamErrorSupport.safeMessage(exception);
        try {
            taskPoolPort.markFailed(subTask.getTaskId(), agentId, errorMessage);
            log.warn(
                    "SubAgentWorker failed task: agentId={}, groupId={}, taskId={}, title={}, errorType={}, error={}",
                    agentId,
                    subTask.getGroupId(),
                    subTask.getTaskId(),
                    subTask.getTitle(),
                    AgentTeamErrorSupport.errorType(exception),
                    errorMessage
            );
        } catch (RuntimeException writebackException) {
            log.error(
                    "SubAgentWorker failure writeback rejected: agentId={}, groupId={}, taskId={}, title={}, executionErrorType={}, executionError={}, writebackErrorType={}, writebackError={}",
                    agentId,
                    subTask.getGroupId(),
                    subTask.getTaskId(),
                    subTask.getTitle(),
                    AgentTeamErrorSupport.errorType(exception),
                    errorMessage,
                    AgentTeamErrorSupport.errorType(writebackException),
                    AgentTeamErrorSupport.safeMessage(writebackException),
                    writebackException
            );
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            running.set(false);
        }
    }

    private String summarize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() > 120 ? trimmed.substring(0, 120) + "..." : trimmed;
    }
}
