package com.openmanus.infra.config;

import com.openmanus.agentteam.application.AgentTeamApplicationService;
import com.openmanus.agentteam.application.AgentTeamCodingApplicationService;
import com.openmanus.agentteam.application.AgentTeamCodingExecutionStreamingApplicationService;
import com.openmanus.agentteam.application.AgentTeamConversationApplicationService;
import com.openmanus.agentteam.application.AgentTeamExecutionStreamingApplicationService;
import com.openmanus.infra.config.AgentTeamProperties;
import com.openmanus.domain.service.ConversationApplicationService;
import com.openmanus.domain.service.AgentExecutionPort;
import com.openmanus.domain.service.ExecutionStreamingApplicationService;
import com.openmanus.domain.service.ExecutionEventPort;
import com.openmanus.domain.service.ExecutionStreamPublisher;
import com.openmanus.domain.service.InMemorySessionExecutionGuard;
import com.openmanus.domain.service.SessionExecutionGuard;
import com.openmanus.domain.service.WebProxyFetchPort;
import com.openmanus.domain.service.WebProxyService;
import com.openmanus.sandbox.application.SandboxSessionApplicationService;
import com.openmanus.sandbox.domain.port.SandboxRuntimePort;
import com.openmanus.sandbox.domain.port.SandboxWorkspacePort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;

import java.nio.file.Path;
import java.util.concurrent.Executor;

@Configuration
public class DomainServiceConfig {

    @Bean
    ConversationApplicationService conversationApplicationService(AgentExecutionPort agentExecutionPort,
                                                                  ExecutionEventPort executionEventPort,
                                                                  SessionExecutionGuard sessionExecutionGuard) {
        return new ConversationApplicationService(agentExecutionPort, executionEventPort, sessionExecutionGuard);
    }

    @Bean
    ExecutionStreamingApplicationService executionStreamingApplicationService(AgentExecutionPort agentExecutionPort,
                                                                             ExecutionEventPort executionEventPort,
                                                                             ExecutionStreamPublisher streamPublisher,
                                                                             @Qualifier(AsyncConfig.ASYNC_EXECUTOR_NAME) Executor asyncExecutor,
                                                                             SessionExecutionGuard sessionExecutionGuard) {
        return new ExecutionStreamingApplicationService(
                agentExecutionPort,
                executionEventPort,
                streamPublisher,
                asyncExecutor,
                sessionExecutionGuard
        );
    }

    @Bean
    AgentTeamConversationApplicationService agentTeamConversationApplicationService(
            AgentTeamApplicationService agentTeamApplicationService,
            ExecutionEventPort executionEventPort,
            SessionExecutionGuard sessionExecutionGuard,
            @Qualifier(AsyncConfig.ASYNC_EXECUTOR_NAME) Executor asyncExecutor) {
        return new AgentTeamConversationApplicationService(
                agentTeamApplicationService,
                executionEventPort,
                sessionExecutionGuard,
                asyncExecutor
        );
    }

    @Bean
    AgentTeamExecutionStreamingApplicationService agentTeamExecutionStreamingApplicationService(
            AgentTeamApplicationService agentTeamApplicationService,
            ExecutionEventPort executionEventPort,
            ExecutionStreamPublisher streamPublisher,
            @Qualifier(AsyncConfig.ASYNC_EXECUTOR_NAME) Executor asyncExecutor,
            SessionExecutionGuard sessionExecutionGuard) {
        return new AgentTeamExecutionStreamingApplicationService(
                agentTeamApplicationService,
                executionEventPort,
                streamPublisher,
                asyncExecutor,
                sessionExecutionGuard
        );
    }

    @Bean
    AgentTeamCodingExecutionStreamingApplicationService agentTeamCodingExecutionStreamingApplicationService(
            AgentTeamCodingApplicationService agentTeamCodingApplicationService,
            ExecutionEventPort executionEventPort,
            ExecutionStreamPublisher streamPublisher,
            @Qualifier(AsyncConfig.ASYNC_EXECUTOR_NAME) Executor asyncExecutor,
            SessionExecutionGuard sessionExecutionGuard,
            AgentTeamProperties agentTeamProperties) {
        return new AgentTeamCodingExecutionStreamingApplicationService(
                agentTeamCodingApplicationService,
                executionEventPort,
                streamPublisher,
                asyncExecutor,
                sessionExecutionGuard,
                Path.of("").toAbsolutePath().normalize(),
                agentTeamProperties.isEnabled()
        );
    }

    @Bean
    SessionExecutionGuard sessionExecutionGuard() {
        return new InMemorySessionExecutionGuard();
    }

    @Bean
    SandboxSessionApplicationService sandboxSessionApplicationService(SandboxRuntimePort sandboxRuntimePort,
                                                                     SandboxWorkspacePort sandboxWorkspacePort) {
        return new SandboxSessionApplicationService(sandboxRuntimePort, sandboxWorkspacePort);
    }

    @Bean
    WebProxyService webProxyService(WebProxyFetchPort webProxyFetchPort) {
        return new WebProxyService(webProxyFetchPort);
    }
}
