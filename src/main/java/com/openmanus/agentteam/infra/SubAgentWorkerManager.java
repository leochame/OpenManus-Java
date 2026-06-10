package com.openmanus.agentteam.infra;

import com.openmanus.agentteam.application.SubAgentExecutionService;
import com.openmanus.agentteam.domain.port.AgentMessageBusPort;
import com.openmanus.agentteam.domain.port.TaskPoolPort;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Starts and owns a fixed set of polling subagent workers.
 */
public class SubAgentWorkerManager implements AutoCloseable {

    private final int workerCount;
    private final long idlePollIntervalMillis;
    private final TaskPoolPort taskPoolPort;
    private final AgentMessageBusPort messageBusPort;
    private final SubAgentExecutionService executionService;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final List<SubAgentWorker> workers = new ArrayList<>();
    private ExecutorService executorService;

    public SubAgentWorkerManager(
            int workerCount,
            long idlePollIntervalMillis,
            TaskPoolPort taskPoolPort,
            AgentMessageBusPort messageBusPort,
            SubAgentExecutionService executionService
    ) {
        this.workerCount = workerCount;
        this.idlePollIntervalMillis = idlePollIntervalMillis;
        this.taskPoolPort = taskPoolPort;
        this.messageBusPort = messageBusPort;
        this.executionService = executionService;
    }

    public void ensureStarted() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        executorService = Executors.newFixedThreadPool(workerCount);
        for (int i = 1; i <= workerCount; i++) {
            SubAgentWorker worker = new SubAgentWorker(
                    "subagent-" + i,
                    taskPoolPort,
                    messageBusPort,
                    executionService,
                    idlePollIntervalMillis
            );
            workers.add(worker);
            executorService.submit(worker);
        }
    }

    public List<String> getWorkerIds() {
        return workers.stream().map(SubAgentWorker::getAgentId).toList();
    }

    @Override
    public void close() {
        for (SubAgentWorker worker : workers) {
            worker.stop();
        }
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }
}
