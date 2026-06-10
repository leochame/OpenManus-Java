package com.openmanus.agentteam.application;

import com.openmanus.agentteam.domain.model.SubTask;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Executes one subtask through the role-scoped sub-agent runtime.
 *
 * <p>This service remains a thin application-layer bridge and deliberately does not own
 * task scheduling or result aggregation decisions.</p>
 */
@Slf4j
public class SubAgentExecutionService {

    private final AgentTeamRoleExecutionPort roleExecutionPort;
    private final AgentTeamPromptProvider promptProvider;

    public SubAgentExecutionService(
            AgentTeamRoleExecutionPort roleExecutionPort,
            AgentTeamPromptProvider promptProvider
    ) {
        this.roleExecutionPort = roleExecutionPort;
        this.promptProvider = promptProvider;
    }

    public SubTaskExecutionOutput execute(SubTask subTask, String agentId) {
        String prompt = buildSubTaskPrompt(subTask, agentId);
        AgentTeamExecutionContext context = AgentTeamExecutionContext.subAgent(
                subTask.getParentSessionId(),
                subTask.getGroupId(),
                subTask.getTaskId(),
                agentId
        );
        log.info(
                "SubAgentExecution dispatching to role-scoped runtime: agentId={}, groupId={}, taskId={}, title={}, memoryId={}",
                agentId,
                subTask.getGroupId(),
                subTask.getTaskId(),
                subTask.getTitle(),
                context.memoryId()
        );
        String result = roleExecutionPort.executeSync(context, prompt);
        String summary = summarize(result);
        log.info(
                "SubAgentExecution finished runtime call: agentId={}, groupId={}, taskId={}, title={}, memoryId={}, summary={}",
                agentId,
                subTask.getGroupId(),
                subTask.getTaskId(),
                subTask.getTitle(),
                context.memoryId(),
                summary
        );
        return new SubTaskExecutionOutput(summary, result);
    }

    private String buildSubTaskPrompt(SubTask subTask, String agentId) {
        return PromptTemplateRenderer.render(
                promptProvider.subAgentExecutionPromptTemplate(),
                Map.of(
                        "agentId", safe(agentId),
                        "taskId", safe(subTask.getTaskId()),
                        "groupId", safe(subTask.getGroupId()),
                        "taskTitle", safe(subTask.getTitle()),
                        "taskDescription", safe(subTask.getDescription()),
                        "contextSummary", safe(subTask.getContextSummary())
                )
        );
    }

    private String summarize(String result) {
        if (result == null || result.isBlank()) {
            return "Subtask finished but returned no usable content";
        }
        String compact = result.trim();
        return compact.length() > 80 ? compact.substring(0, 80) : compact;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
