package com.openmanus.infra.config;

import com.openmanus.agent.coordination.AgentCoordinator;
import com.openmanus.agent.tool.BrowserTool;
import com.openmanus.agent.tool.PythonExecutionTool;
import com.openmanus.agent.tool.SearchTool;
import com.openmanus.agent.tool.ShellTool;
import com.openmanus.agent.tool.TaskReflectionTool;
import com.openmanus.agent.tool.WebFetchTool;
import com.openmanus.agent.execution.AgentExecutionService;
import com.openmanus.aiframework.runtime.AiChatModel;
import com.openmanus.aiframework.runtime.AiCodeSandbox;
import com.openmanus.aiframework.runtime.AiMemoryProvider;
import com.openmanus.aiframework.runtime.AiProxyConfig;
import com.openmanus.aiframework.runtime.AiSearchConfig;
import com.openmanus.aiframework.runtime.AiSessionSandboxGateway;
import com.openmanus.aiframework.tool.AiRegisteredTool;
import com.openmanus.aiframework.tool.mcp.McpToolRegistryBootstrap;
import com.openmanus.domain.service.ExecutionEventPort;
import com.openmanus.sandbox.support.SandboxPathResolver;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * Agent 架构配置：
 * 将当前默认工具集注册到统一执行协调器。
 */
@Configuration
public class AgentArchitectureConfig {

    @Bean
    public SandboxPathResolver sandboxPathResolver(AiSessionSandboxGateway sessionSandboxGateway) {
        return new SandboxPathResolver(sessionSandboxGateway);
    }

    @Bean
    public BrowserTool browserTool(AiSessionSandboxGateway sessionSandboxGateway,
                                   ExecutionEventPort executionEventPort) {
        return new BrowserTool(sessionSandboxGateway, executionEventPort);
    }

    @Bean
    public PythonExecutionTool pythonExecutionTool(AiCodeSandbox sandbox, SandboxPathResolver sandboxPathResolver) {
        return new PythonExecutionTool(sandbox, sandboxPathResolver);
    }

    @Bean
    public SearchTool searchTool(AiSearchConfig searchConfig,
                                 ExecutionEventPort executionEventPort,
                                 AiSessionSandboxGateway sessionSandboxGateway) {
        return new SearchTool(searchConfig, executionEventPort, sessionSandboxGateway);
    }

    @Bean
    public WebFetchTool webFetchTool(AiSessionSandboxGateway sessionSandboxGateway,
                                     AiProxyConfig proxyConfig,
                                     ExecutionEventPort executionEventPort) {
        return new WebFetchTool(sessionSandboxGateway, proxyConfig, executionEventPort);
    }

    @Bean
    public ShellTool shellTool(AiSessionSandboxGateway sessionSandboxGateway,
                               SandboxPathResolver sandboxPathResolver,
                               OpenManusProperties properties) {
        return new ShellTool(
                sessionSandboxGateway,
                sandboxPathResolver,
                properties.getChatMemory().isShellToolEnabled(),
                properties.getChatMemory().getShellToolTimeoutSeconds()
        );
    }

    @Bean
    public TaskReflectionTool taskReflectionTool() {
        return new TaskReflectionTool();
    }

    @Bean
    public LocalAgentToolRegistry localAgentToolRegistry(BrowserTool browserTool,
                                                         PythonExecutionTool pythonExecutionTool,
                                                         SearchTool searchTool,
                                                         WebFetchTool webFetchTool,
                                                         ShellTool shellTool,
                                                         TaskReflectionTool taskReflectionTool,
                                                         OpenManusProperties properties) {
        return new LocalAgentToolRegistry(
                browserTool,
                pythonExecutionTool,
                searchTool,
                webFetchTool,
                shellTool,
                taskReflectionTool,
                properties.getChatMemory().isShellToolEnabled()
        );
    }

    @Bean
    public AgentExecutionService agentExecutionService(AgentCoordinator agentCoordinator,
                                                       @Qualifier(AsyncConfig.ASYNC_EXECUTOR_NAME) Executor asyncExecutor) {
        return new AgentExecutionService(agentCoordinator, asyncExecutor);
    }

    AgentCoordinator agentCoordinator(
            AiChatModel chatModel,
            AiMemoryProvider chatMemoryProvider,
            OpenManusProperties properties,
            AiSessionSandboxGateway sessionSandboxGateway,
            BrowserTool browserTool,
            PythonExecutionTool pythonExecutionTool,
            SearchTool searchTool,
            WebFetchTool webFetchTool,
            ShellTool shellTool,
            TaskReflectionTool taskReflectionTool,
            LocalAgentToolRegistry localAgentToolRegistry) {
        return agentCoordinator(chatModel, chatMemoryProvider, properties, sessionSandboxGateway, null, null,
                browserTool, pythonExecutionTool, searchTool, webFetchTool, shellTool, taskReflectionTool,
                localAgentToolRegistry);
    }

    @Bean
    public AgentCoordinator agentCoordinator(
            AiChatModel chatModel,
            AiMemoryProvider chatMemoryProvider,
            OpenManusProperties properties,
            @Qualifier("sandboxGatewayAdapter") AiSessionSandboxGateway sessionSandboxGateway,
            BrowserTool browserTool,
            PythonExecutionTool pythonExecutionTool,
            SearchTool searchTool,
            WebFetchTool webFetchTool,
            ShellTool shellTool,
            TaskReflectionTool taskReflectionTool,
            Optional<McpToolRegistryBootstrap> mcpToolRegistryBootstrap,
            LocalAgentToolRegistry localAgentToolRegistry,
            ExecutionEventPort executionEventPort) {
        return agentCoordinator(
                chatModel,
                chatMemoryProvider,
                properties,
                sessionSandboxGateway,
                mcpToolRegistryBootstrap.orElse(null),
                executionEventPort,
                browserTool,
                pythonExecutionTool,
                searchTool,
                webFetchTool,
                shellTool,
                taskReflectionTool,
                localAgentToolRegistry
        );
    }

    public AgentCoordinator agentCoordinator(
            AiChatModel chatModel,
            AiMemoryProvider chatMemoryProvider,
            OpenManusProperties properties,
            AiSessionSandboxGateway sessionSandboxGateway,
            ExecutionEventPort executionEventPort,
            BrowserTool browserTool,
            PythonExecutionTool pythonExecutionTool,
            SearchTool searchTool,
            WebFetchTool webFetchTool,
            ShellTool shellTool,
            TaskReflectionTool taskReflectionTool,
            LocalAgentToolRegistry localAgentToolRegistry) {
        return agentCoordinator(
                chatModel,
                chatMemoryProvider,
                properties,
                sessionSandboxGateway,
                null,
                executionEventPort,
                browserTool,
                pythonExecutionTool,
                searchTool,
                webFetchTool,
                shellTool,
                taskReflectionTool,
                localAgentToolRegistry
        );
    }

    AgentCoordinator agentCoordinator(
            AiChatModel chatModel,
            AiMemoryProvider chatMemoryProvider,
            OpenManusProperties properties,
            AiSessionSandboxGateway sessionSandboxGateway,
            McpToolRegistryBootstrap mcpToolRegistryBootstrap,
            ExecutionEventPort executionEventPort,
            BrowserTool browserTool,
            PythonExecutionTool pythonExecutionTool,
            SearchTool searchTool,
            WebFetchTool webFetchTool,
            ShellTool shellTool,
            TaskReflectionTool taskReflectionTool,
            LocalAgentToolRegistry localAgentToolRegistry) {
        AgentCoordinator.Builder builder = AgentCoordinator.builder()
                .aiChatModel(chatModel)
                .aiMemoryProvider(chatMemoryProvider)
                .sessionSandboxGateway(sessionSandboxGateway)
                .maxIterations(properties.getChatMemory().getReactMaxIterations())
                .maxExecutionSeconds(properties.getChatMemory().getReactMaxExecutionSeconds())
                .repeatedToolCallThreshold(properties.getChatMemory().getReactRepeatedToolCallThreshold())
                .taskStatePlanMaxChars(properties.getChatMemory().getTaskStatePlanMaxChars())
                .taskStateInProgressMaxChars(properties.getChatMemory().getTaskStateInProgressMaxChars())
                .taskStateLastFailureMaxChars(properties.getChatMemory().getTaskStateLastFailureMaxChars())
                .taskStateTodoMaxItems(properties.getChatMemory().getTaskStateTodoMaxItems())
                .taskStateTodoItemMaxChars(properties.getChatMemory().getTaskStateTodoItemMaxChars())
                .enableToolResultBudget(properties.getChatMemory().isToolResultBudgetEnabled())
                .toolResultBudgetMinChars(properties.getChatMemory().getToolResultBudgetMinChars())
                .toolResultBudgetPreviewHeadChars(properties.getChatMemory().getToolResultBudgetPreviewHeadChars())
                .toolResultBudgetPreviewTailChars(properties.getChatMemory().getToolResultBudgetPreviewTailChars())
                .toolResultBudgetDecayChars(properties.getChatMemory().getToolResultBudgetDecayChars())
                .executionEventPort(executionEventPort);

        for (AiRegisteredTool tool : localAgentToolRegistry.allLocalTools()) {
            builder.tool(tool);
        }

        registerMcpTools(
                builder,
                properties,
                mcpToolRegistryBootstrap,
                browserTool,
                pythonExecutionTool,
                searchTool,
                webFetchTool,
                shellTool,
                taskReflectionTool
        );
        return builder.build();
    }

    private static void registerMcpTools(
            AgentCoordinator.Builder builder,
            OpenManusProperties properties,
            McpToolRegistryBootstrap bootstrap,
            BrowserTool browserTool,
            PythonExecutionTool pythonExecutionTool,
            SearchTool searchTool,
            WebFetchTool webFetchTool,
            ShellTool shellTool,
            TaskReflectionTool taskReflectionTool
    ) {
        if (bootstrap == null || properties == null || !properties.getMcp().isEnabled()) {
            return;
        }
        Map<String, AiRegisteredTool> localToolRegistry = new LinkedHashMap<>();
        LocalAgentToolRegistry.appendLocalTools(localToolRegistry, browserTool);
        LocalAgentToolRegistry.appendLocalTools(localToolRegistry, pythonExecutionTool);
        LocalAgentToolRegistry.appendLocalTools(localToolRegistry, searchTool);
        LocalAgentToolRegistry.appendLocalTools(localToolRegistry, webFetchTool);
        if (properties.getChatMemory().isShellToolEnabled()) {
            LocalAgentToolRegistry.appendLocalTools(localToolRegistry, shellTool);
        }
        LocalAgentToolRegistry.appendLocalTools(localToolRegistry, taskReflectionTool);

        List<AiRegisteredTool> mcpTools = bootstrap.discoverAndRegister(localToolRegistry);
        for (AiRegisteredTool mcpTool : mcpTools) {
            builder.tool(mcpTool);
        }
    }

}
