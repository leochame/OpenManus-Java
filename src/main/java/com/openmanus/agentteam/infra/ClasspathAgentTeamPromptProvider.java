package com.openmanus.agentteam.infra;

import com.openmanus.agentteam.application.AgentTeamPromptProvider;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads agentteam prompt templates from classpath resources.
 */
public class ClasspathAgentTeamPromptProvider implements AgentTeamPromptProvider {

    private static final String TASK_DECOMPOSITION_TEMPLATE = "prompts/agentteam/task-decomposition.prompt.md";
    private static final String TEAM_MASTER_SYSTEM_TEMPLATE = "prompts/agentteam/team-master-system.prompt.md";
    private static final String SUB_AGENT_SYSTEM_TEMPLATE = "prompts/agentteam/subagent-system.prompt.md";
    private static final String SUB_AGENT_EXECUTION_TEMPLATE = "prompts/agentteam/subagent-execution.prompt.md";

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    @Override
    public String taskDecompositionPromptTemplate() {
        return load(TASK_DECOMPOSITION_TEMPLATE);
    }

    @Override
    public String teamMasterSystemPromptTemplate() {
        return load(TEAM_MASTER_SYSTEM_TEMPLATE);
    }

    @Override
    public String subAgentSystemPromptTemplate() {
        return load(SUB_AGENT_SYSTEM_TEMPLATE);
    }

    @Override
    public String subAgentExecutionPromptTemplate() {
        return load(SUB_AGENT_EXECUTION_TEMPLATE);
    }

    private String load(String path) {
        return cache.computeIfAbsent(path, this::readClasspathResource);
    }

    private String readClasspathResource(String path) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try (InputStream stream = classLoader.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Prompt template not found: " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load prompt template: " + path, exception);
        }
    }
}
