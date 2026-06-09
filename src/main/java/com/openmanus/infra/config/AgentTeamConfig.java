package com.openmanus.infra.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.agentteam.application.AgentTeamApplicationService;
import com.openmanus.agentteam.application.AgentTeamCodingApplicationService;
import com.openmanus.agentteam.application.AgentTeamPromptProvider;
import com.openmanus.agentteam.application.AgentTeamRoleExecutionPort;
import com.openmanus.agentteam.application.AgentTeamRoleExecutionService;
import com.openmanus.agentteam.application.IntegrationCoordinator;
import com.openmanus.agentteam.application.MasterAgentOrchestrator;
import com.openmanus.agentteam.application.ParallelCodingOrchestrator;
import com.openmanus.agentteam.application.ParallelCodingPlanner;
import com.openmanus.agentteam.application.SubAgentCodingExecutionService;
import com.openmanus.agentteam.application.SubAgentToolPolicy;
import com.openmanus.agentteam.application.SubAgentExecutionService;
import com.openmanus.agentteam.application.TaskDecompositionService;
import com.openmanus.agentteam.application.TeamMasterToolPolicy;
import com.openmanus.agentteam.domain.port.AgentMessageBusPort;
import com.openmanus.agentteam.domain.port.CommandExecutionPort;
import com.openmanus.agentteam.domain.port.GitIntegrationPort;
import com.openmanus.agentteam.domain.port.GitWorkspacePort;
import com.openmanus.agentteam.domain.port.GitWorktreeProvisioningPort;
import com.openmanus.agentteam.domain.port.TaskGroupRepositoryPort;
import com.openmanus.agentteam.domain.port.TaskPoolPort;
import com.openmanus.agentteam.domain.service.DefaultResultAggregationService;
import com.openmanus.agentteam.domain.service.DefaultTaskGroupManager;
import com.openmanus.agentteam.domain.service.ResultAggregationService;
import com.openmanus.agentteam.domain.service.TaskGroupManager;
import com.openmanus.agentteam.domain.service.TaskGroupStatusCalculator;
import com.openmanus.agentteam.infra.AgentTeamCoordinatorFactory;
import com.openmanus.agentteam.infra.ClasspathAgentTeamPromptProvider;
import com.openmanus.agentteam.infra.GitWorktreeProvisioningService;
import com.openmanus.agentteam.infra.InMemoryAgentMessageBus;
import com.openmanus.agentteam.infra.InMemoryTaskGroupRepository;
import com.openmanus.agentteam.infra.InMemoryTaskPool;
import com.openmanus.agentteam.infra.LocalCommandExecutionService;
import com.openmanus.agentteam.infra.LocalGitIntegrationService;
import com.openmanus.agentteam.infra.LocalGitWorkspaceService;
import com.openmanus.agentteam.infra.SubAgentWorkerManager;
import com.openmanus.aiframework.runtime.AiChatModel;
import com.openmanus.aiframework.runtime.AiMemoryProvider;
import com.openmanus.aiframework.runtime.AiSessionSandboxGateway;
import com.openmanus.domain.service.AgentExecutionPort;
import com.openmanus.domain.service.ExecutionEventPort;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Bean wiring for the agentteam module.
 */
@Configuration
@EnableConfigurationProperties(AgentTeamProperties.class)
public class AgentTeamConfig {

    @Bean
    TaskGroupStatusCalculator taskGroupStatusCalculator() {
        return new TaskGroupStatusCalculator();
    }

    @Bean
    TaskGroupRepositoryPort taskGroupRepositoryPort() {
        return new InMemoryTaskGroupRepository();
    }

    @Bean
    TaskPoolPort taskPoolPort(TaskGroupRepositoryPort repositoryPort) {
        return new InMemoryTaskPool(repositoryPort);
    }

    @Bean
    AgentMessageBusPort agentMessageBusPort() {
        return new InMemoryAgentMessageBus();
    }

    @Bean
    TaskGroupManager taskGroupManager(
            TaskGroupRepositoryPort repositoryPort,
            TaskGroupStatusCalculator statusCalculator
    ) {
        return new DefaultTaskGroupManager(repositoryPort, statusCalculator);
    }

    @Bean
    ResultAggregationService resultAggregationService() {
        return new DefaultResultAggregationService();
    }

    @Bean
    AgentTeamPromptProvider agentTeamPromptProvider() {
        return new ClasspathAgentTeamPromptProvider();
    }

    @Bean
    TeamMasterToolPolicy teamMasterToolPolicy() {
        return new TeamMasterToolPolicy();
    }

    @Bean
    SubAgentToolPolicy subAgentToolPolicy() {
        return new SubAgentToolPolicy();
    }

    @Bean
    TaskDecompositionService taskDecompositionService(
            AiChatModel aiChatModel,
            ObjectMapper objectMapper,
            AgentTeamPromptProvider promptProvider
    ) {
        return new TaskDecompositionService(aiChatModel, objectMapper, promptProvider);
    }

    @Bean
    AgentTeamCoordinatorFactory agentTeamCoordinatorFactory(
            AiChatModel aiChatModel,
            AiMemoryProvider aiMemoryProvider,
            AiSessionSandboxGateway sessionSandboxGateway,
            OpenManusProperties properties,
            ExecutionEventPort executionEventPort,
            LocalAgentToolRegistry localAgentToolRegistry,
            AgentTeamPromptProvider promptProvider,
            TeamMasterToolPolicy teamMasterToolPolicy,
            SubAgentToolPolicy subAgentToolPolicy
    ) {
        return new AgentTeamCoordinatorFactory(
                aiChatModel,
                aiMemoryProvider,
                sessionSandboxGateway,
                properties,
                executionEventPort,
                localAgentToolRegistry,
                promptProvider,
                teamMasterToolPolicy,
                subAgentToolPolicy
        );
    }

    @Bean
    AgentTeamRoleExecutionPort agentTeamRoleExecutionPort(AgentTeamCoordinatorFactory coordinatorFactory) {
        return new AgentTeamRoleExecutionService(coordinatorFactory);
    }

    @Bean
    GitWorktreeProvisioningPort gitWorktreeProvisioningPort() {
        return new GitWorktreeProvisioningService();
    }

    @Bean
    GitWorkspacePort gitWorkspacePort() {
        return new LocalGitWorkspaceService();
    }

    @Bean
    ParallelCodingPlanner parallelCodingPlanner() {
        return new ParallelCodingPlanner();
    }

    @Bean
    GitIntegrationPort gitIntegrationPort() {
        return new LocalGitIntegrationService();
    }

    @Bean
    CommandExecutionPort commandExecutionPort() {
        return new LocalCommandExecutionService();
    }

    @Bean
    IntegrationCoordinator integrationCoordinator(
            GitIntegrationPort gitIntegrationPort,
            CommandExecutionPort commandExecutionPort
    ) {
        return new IntegrationCoordinator(gitIntegrationPort, commandExecutionPort);
    }

    @Bean
    SubAgentExecutionService subAgentExecutionService(
            AgentTeamRoleExecutionPort roleExecutionPort,
            AgentTeamPromptProvider promptProvider
    ) {
        return new SubAgentExecutionService(roleExecutionPort, promptProvider);
    }

    @Bean
    SubAgentCodingExecutionService subAgentCodingExecutionService(
            AgentTeamRoleExecutionPort roleExecutionPort,
            GitWorkspacePort gitWorkspacePort
    ) {
        return new SubAgentCodingExecutionService(roleExecutionPort, gitWorkspacePort);
    }

    @Bean(destroyMethod = "close")
    SubAgentWorkerManager subAgentWorkerManager(
            AgentTeamProperties properties,
            TaskPoolPort taskPoolPort,
            AgentMessageBusPort messageBusPort,
            SubAgentExecutionService executionService
    ) {
        return new SubAgentWorkerManager(
                properties.getWorkerCount(),
                properties.getIdlePollIntervalMillis(),
                taskPoolPort,
                messageBusPort,
                executionService
        );
    }

    @Bean
    MasterAgentOrchestrator masterAgentOrchestrator(
            AgentExecutionPort agentExecutionPort,
            TaskDecompositionService taskDecompositionService,
            TaskGroupManager taskGroupManager,
            TaskPoolPort taskPoolPort,
            ResultAggregationService resultAggregationService,
            SubAgentWorkerManager subAgentWorkerManager,
            AgentTeamProperties properties
    ) {
        return new MasterAgentOrchestrator(
                agentExecutionPort,
                taskDecompositionService,
                taskGroupManager,
                taskPoolPort,
                resultAggregationService,
                subAgentWorkerManager,
                properties.getMasterPollIntervalMillis(),
                properties.getMaxSubTasksPerGroup()
        );
    }

    @Bean
    AgentTeamApplicationService agentTeamApplicationService(MasterAgentOrchestrator masterAgentOrchestrator) {
        return new AgentTeamApplicationService(masterAgentOrchestrator);
    }

    @Bean
    ParallelCodingOrchestrator parallelCodingOrchestrator(
            AgentExecutionPort agentExecutionPort,
            ParallelCodingPlanner parallelCodingPlanner,
            GitWorktreeProvisioningPort gitWorktreeProvisioningPort,
            SubAgentCodingExecutionService subAgentCodingExecutionService,
            IntegrationCoordinator integrationCoordinator,
            AgentTeamProperties properties
    ) {
        return new ParallelCodingOrchestrator(
                agentExecutionPort,
                parallelCodingPlanner,
                gitWorktreeProvisioningPort,
                subAgentCodingExecutionService,
                integrationCoordinator,
                properties.getMaxSubTasksPerGroup()
        );
    }

    @Bean
    AgentTeamCodingApplicationService agentTeamCodingApplicationService(
            ParallelCodingOrchestrator parallelCodingOrchestrator
    ) {
        return new AgentTeamCodingApplicationService(parallelCodingOrchestrator);
    }
}
