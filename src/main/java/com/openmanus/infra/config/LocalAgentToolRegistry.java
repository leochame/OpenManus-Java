package com.openmanus.infra.config;

import com.openmanus.agent.tool.BrowserTool;
import com.openmanus.agent.tool.PythonExecutionTool;
import com.openmanus.agent.tool.SearchTool;
import com.openmanus.agent.tool.ShellTool;
import com.openmanus.agent.tool.TaskReflectionTool;
import com.openmanus.agent.tool.WebFetchTool;
import com.openmanus.aiframework.tool.AiRegisteredTool;
import com.openmanus.aiframework.tool.AiToolRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Centralized local tool registry shared by default agent and agentteam.
 */
public class LocalAgentToolRegistry {

    private final BrowserTool browserTool;
    private final PythonExecutionTool pythonExecutionTool;
    private final SearchTool searchTool;
    private final WebFetchTool webFetchTool;
    private final ShellTool shellTool;
    private final TaskReflectionTool taskReflectionTool;
    private final boolean shellToolEnabled;

    public LocalAgentToolRegistry(
            BrowserTool browserTool,
            PythonExecutionTool pythonExecutionTool,
            SearchTool searchTool,
            WebFetchTool webFetchTool,
            ShellTool shellTool,
            TaskReflectionTool taskReflectionTool,
            boolean shellToolEnabled
    ) {
        this.browserTool = browserTool;
        this.pythonExecutionTool = pythonExecutionTool;
        this.searchTool = searchTool;
        this.webFetchTool = webFetchTool;
        this.shellTool = shellTool;
        this.taskReflectionTool = taskReflectionTool;
        this.shellToolEnabled = shellToolEnabled;
    }

    public List<AiRegisteredTool> allLocalTools() {
        Map<String, AiRegisteredTool> registry = new LinkedHashMap<>();
        appendLocalTools(registry, browserTool);
        appendLocalTools(registry, pythonExecutionTool);
        appendLocalTools(registry, searchTool);
        appendLocalTools(registry, webFetchTool);
        if (shellToolEnabled) {
            appendLocalTools(registry, shellTool);
        }
        appendLocalTools(registry, taskReflectionTool);
        return new ArrayList<>(registry.values());
    }

    public static void appendLocalTools(Map<String, AiRegisteredTool> targetRegistry, Object toolObject) {
        if (toolObject == null) {
            return;
        }
        for (AiRegisteredTool localTool : AiToolRegistry.scan(toolObject)) {
            AiRegisteredTool existing = targetRegistry.putIfAbsent(localTool.name(), localTool);
            if (existing != null) {
                throw new IllegalStateException("Duplicate tool name detected: " + localTool.name());
            }
        }
    }
}
