package com.openmanus.sandbox.infra;

import com.openmanus.aiframework.runtime.AiSandboxCommandResult;
import com.openmanus.aiframework.runtime.AiSessionSandboxGateway;
import com.openmanus.aiframework.runtime.AiSessionSandboxInfo;
import com.openmanus.sandbox.application.SandboxSessionApplicationService;
import com.openmanus.sandbox.domain.model.SessionSandboxInfo;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Primary
@Component
public class SandboxGatewayAdapter implements AiSessionSandboxGateway {

    private final SandboxSessionApplicationService sandboxSessionApplicationService;

    public SandboxGatewayAdapter(SandboxSessionApplicationService sandboxSessionApplicationService) {
        this.sandboxSessionApplicationService = sandboxSessionApplicationService;
    }

    @Override
    public Optional<AiSessionSandboxInfo> getSandboxInfo(String sessionId) {
        return sandboxSessionApplicationService.getSandboxInfo(sessionId).map(this::toRuntimeInfo);
    }

    @Override
    public AiSessionSandboxInfo getOrCreateSandbox(String sessionId) {
        return toRuntimeInfo(sandboxSessionApplicationService.getOrCreateSandbox(sessionId));
    }

    @Override
    public String getWorkspaceRoot(String sessionId) {
        return sandboxSessionApplicationService.getWorkspaceRoot(sessionId);
    }

    @Override
    public String resolveWorkspacePath(String sessionId, String userPath) {
        return sandboxSessionApplicationService.resolveWorkspacePath(sessionId, userPath);
    }

    @Override
    public AiSandboxCommandResult executeCommand(String sessionId, String command, String cwd, int timeoutSeconds) {
        var result = sandboxSessionApplicationService.executeCommand(sessionId, command, cwd, timeoutSeconds);
        return new AiSandboxCommandResult(result.stdout(), result.stderr(), result.exitCode());
    }

    @Override
    public AiSandboxCommandResult openBrowserUrl(String sessionId, String url) {
        var result = sandboxSessionApplicationService.openBrowserUrl(sessionId, url);
        return new AiSandboxCommandResult(result.stdout(), result.stderr(), result.exitCode());
    }

    @Override
    public String readTextFile(String sessionId, String path) {
        return sandboxSessionApplicationService.readTextFile(sessionId, path);
    }

    @Override
    public void writeTextFile(String sessionId, String path, String content) {
        sandboxSessionApplicationService.writeTextFile(sessionId, path, content);
    }

    private AiSessionSandboxInfo toRuntimeInfo(SessionSandboxInfo info) {
        return new AiSessionSandboxInfo(
                info.getSessionId(),
                info.getContainerId(),
                info.getWorkspaceRoot(),
                info.getVncUrl(),
                null,
                info.getStatus() == null ? null : info.getStatus().name()
        );
    }
}
