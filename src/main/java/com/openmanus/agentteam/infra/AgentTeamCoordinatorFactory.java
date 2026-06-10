package com.openmanus.agentteam.infra;

import com.openmanus.agent.coordination.AgentCoordinator;
import com.openmanus.agent.tool.BrowserTool;
import com.openmanus.agent.tool.PythonExecutionTool;
import com.openmanus.agent.tool.ShellTool;
import com.openmanus.agent.tool.TaskReflectionTool;
import com.openmanus.agentteam.application.AgentTeamRole;
import com.openmanus.agentteam.application.AgentTeamPromptProvider;
import com.openmanus.agentteam.application.AgentTeamToolPolicy;
import com.openmanus.agentteam.application.PermissionLevel;
import com.openmanus.agentteam.application.SubAgentToolPolicy;
import com.openmanus.agentteam.application.TeamMasterToolPolicy;
import com.openmanus.aiframework.runtime.AiChatModel;
import com.openmanus.aiframework.runtime.AiMemoryProvider;
import com.openmanus.aiframework.runtime.AiSessionSandboxGateway;
import com.openmanus.aiframework.tool.AiRegisteredTool;
import com.openmanus.aiframework.tool.AiToolRegistry;
import com.openmanus.domain.service.ExecutionEventPort;
import com.openmanus.infra.config.LocalAgentToolRegistry;
import com.openmanus.infra.config.OpenManusProperties;
import com.openmanus.sandbox.support.SandboxPathResolver;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Objects;

/**
 * Builds role-scoped coordinators for the agentteam module.
 *
 * For {@link AgentTeamRole#CODING_SUB_AGENT}, tools execute on the Host OS
 * (no Docker sandbox) so the AI can access git worktrees created by the orchestrator.
 */
@Slf4j
public class AgentTeamCoordinatorFactory {

    private final AiChatModel aiChatModel;
    private final AiMemoryProvider aiMemoryProvider;
    private final AiSessionSandboxGateway sessionSandboxGateway;
    private final AiSessionSandboxGateway hostModeExecutionGateway;
    private final OpenManusProperties properties;
    private final ExecutionEventPort executionEventPort;
    private final LocalAgentToolRegistry localAgentToolRegistry;
    private final AgentTeamPromptProvider promptProvider;
    private final TeamMasterToolPolicy teamMasterToolPolicy;
    private final SubAgentToolPolicy subAgentToolPolicy;

    public AgentTeamCoordinatorFactory(
            AiChatModel aiChatModel,
            AiMemoryProvider aiMemoryProvider,
            AiSessionSandboxGateway sessionSandboxGateway,
            AiSessionSandboxGateway hostModeExecutionGateway,
            OpenManusProperties properties,
            ExecutionEventPort executionEventPort,
            LocalAgentToolRegistry localAgentToolRegistry,
            AgentTeamPromptProvider promptProvider,
            TeamMasterToolPolicy teamMasterToolPolicy,
            SubAgentToolPolicy subAgentToolPolicy
    ) {
        this.aiChatModel = Objects.requireNonNull(aiChatModel, "aiChatModel");
        this.aiMemoryProvider = Objects.requireNonNull(aiMemoryProvider, "aiMemoryProvider");
        this.sessionSandboxGateway = Objects.requireNonNull(sessionSandboxGateway, "sessionSandboxGateway");
        this.hostModeExecutionGateway = Objects.requireNonNull(hostModeExecutionGateway, "hostModeExecutionGateway");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.executionEventPort = executionEventPort;
        this.localAgentToolRegistry = Objects.requireNonNull(localAgentToolRegistry, "localAgentToolRegistry");
        this.promptProvider = Objects.requireNonNull(promptProvider, "promptProvider");
        this.teamMasterToolPolicy = Objects.requireNonNull(teamMasterToolPolicy, "teamMasterToolPolicy");
        this.subAgentToolPolicy = Objects.requireNonNull(subAgentToolPolicy, "subAgentToolPolicy");
    }

    public AgentCoordinator create(AgentTeamRole role) {
        boolean isCodingSubAgent = role == AgentTeamRole.CODING_SUB_AGENT;
        PermissionLevel permissionLevel = role.permissionLevel();
        AiSessionSandboxGateway effectiveGateway = isCodingSubAgent
                ? hostModeExecutionGateway
                : sessionSandboxGateway;

        // Security: HostModeExecutionGateway already has BashSecurityChecker wired in.
        // For RESTRICTED roles, shell commands pass through BashSecurityChecker before reaching Host OS.
        // For FULL / SANDBOXED roles, Docker sandbox provides physical isolation — no extra checks needed.
        if (permissionLevel == PermissionLevel.RESTRICTED && !isCodingSubAgent) {
            log.warn("AgentTeamCoordinatorFactory: RESTRICTED permission level but not CODING_SUB_AGENT — "
                    + "this may indicate a misconfiguration: role={}", role);
        }

        AgentCoordinator.Builder builder = AgentCoordinator.builder()
                .aiChatModel(aiChatModel)
                .aiMemoryProvider(aiMemoryProvider)
                .sessionSandboxGateway(effectiveGateway)
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
                .executionEventPort(executionEventPort)
                .name("agentteam_" + role.name().toLowerCase())
                .description("Role-scoped executor for agentteam role " + role.name().toLowerCase())
                .singleParameter("Role-scoped request")
                .systemMessage(systemPromptFor(role));

        if (isCodingSubAgent) {
            attachHostModeTools(builder);
        } else {
            for (AiRegisteredTool tool : toolsFor(role)) {
                builder.tool(tool);
            }
        }
        AgentCoordinator coordinator = builder.build();
        log.info(
                "AgentTeam coordinator created: role={}, permissionLevel={}, name={}, mode={}",
                role,
                permissionLevel,
                "agentteam_" + role.name().toLowerCase(),
                isCodingSubAgent ? "HOST" : "SANDBOX"
        );
        return coordinator;
    }

    private void attachHostModeTools(AgentCoordinator.Builder builder) {
        SandboxPathResolver hostPathResolver = new SandboxPathResolver(hostModeExecutionGateway);
        HostCodeSandbox hostCodeSandbox = new HostCodeSandbox();

        if (properties.getChatMemory().isShellToolEnabled()) {
            ShellTool hostShellTool = new ShellTool(
                    hostModeExecutionGateway,
                    hostPathResolver,
                    true,
                    properties.getChatMemory().getShellToolTimeoutSeconds()
            );
            for (AiRegisteredTool tool : AiToolRegistry.scan(hostShellTool)) {
                builder.tool(tool);
            }
        }

        PythonExecutionTool hostPythonTool = new PythonExecutionTool(hostCodeSandbox, hostPathResolver);
        for (AiRegisteredTool tool : AiToolRegistry.scan(hostPythonTool)) {
            builder.tool(tool);
        }

        List<AiRegisteredTool> allTools = localAgentToolRegistry.allLocalTools();
        List<AiRegisteredTool> nonExecTools = subAgentToolPolicy.selectTools(allTools).stream()
                .filter(tool -> !isExecTool(tool.name()))
                .toList();
        for (AiRegisteredTool tool : nonExecTools) {
            builder.tool(tool);
        }
    }

    private boolean isExecTool(String name) {
        return "runShellCommand".equals(name)
                || "executePython".equals(name)
                || "executePythonFile".equals(name);
    }

    private String systemPromptFor(AgentTeamRole role) {
        return switch (role) {
            case TEAM_MASTER -> promptProvider.teamMasterSystemPromptTemplate();
            case SUB_AGENT -> promptProvider.subAgentSystemPromptTemplate();
            case CODING_SUB_AGENT -> promptProvider.subAgentSystemPromptTemplate();
        };
    }

    private List<AiRegisteredTool> toolsFor(AgentTeamRole role) {
        List<AiRegisteredTool> defaultTools = localAgentToolRegistry.allLocalTools();
        AgentTeamToolPolicy policy = switch (role) {
            case TEAM_MASTER -> teamMasterToolPolicy;
            case SUB_AGENT -> subAgentToolPolicy;
            case CODING_SUB_AGENT -> subAgentToolPolicy;
        };
        return policy.selectTools(defaultTools);
    }
}
