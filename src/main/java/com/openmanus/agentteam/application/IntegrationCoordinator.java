package com.openmanus.agentteam.application;

import com.openmanus.agentteam.domain.model.IntegrationResult;
import com.openmanus.agentteam.domain.model.SubAgentCodingResult;
import com.openmanus.agentteam.domain.model.SubAgentCodingStatus;
import com.openmanus.agentteam.domain.port.CommandExecutionPort;
import com.openmanus.agentteam.domain.port.GitIntegrationPort;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Creates an integration branch, cherry-picks successful commits, and runs validation.
 */
@Slf4j
public class IntegrationCoordinator {

    private final GitIntegrationPort gitIntegrationPort;
    private final CommandExecutionPort commandExecutionPort;

    public IntegrationCoordinator(
            GitIntegrationPort gitIntegrationPort,
            CommandExecutionPort commandExecutionPort
    ) {
        this.gitIntegrationPort = gitIntegrationPort;
        this.commandExecutionPort = commandExecutionPort;
    }

    public IntegrationResult integrate(Path repositoryPath, List<SubAgentCodingResult> subAgentResults) {
        List<SubAgentCodingResult> successfulResults = subAgentResults == null
                ? List.of()
                : subAgentResults.stream()
                .filter(result -> result.status() == SubAgentCodingStatus.SUCCEEDED)
                .filter(result -> result.commitSha() != null && !result.commitSha().isBlank())
                .toList();
        if (successfulResults.isEmpty()) {
            return new IntegrationResult(false, null, List.of(), List.of(), "", "No successful committed subtasks to integrate");
        }

        String integrationBranch = "agentteam/integration-" + UUID.randomUUID();
        List<String> mergedBranches = new ArrayList<>();
        try {
            gitIntegrationPort.createIntegrationBranch(repositoryPath, integrationBranch, "HEAD");
            for (SubAgentCodingResult result : successfulResults) {
                log.info(
                        "IntegrationCoordinator applying subtask result: branch={}, commitSha={}, taskId={}, files={}",
                        result.branchName(),
                        result.commitSha(),
                        result.taskId(),
                        result.changedFiles()
                );
                gitIntegrationPort.cherryPickCommit(repositoryPath, result.commitSha());
                mergedBranches.add(result.branchName());
            }
        } catch (RuntimeException exception) {
            log.warn(
                    "IntegrationCoordinator failed during integration: integrationBranch={}, mergedBranches={}, error={}",
                    integrationBranch,
                    mergedBranches,
                    exception.getMessage()
            );
            return new IntegrationResult(false, integrationBranch, mergedBranches, List.of(), "", exception.getMessage());
        }

        String verificationCommand = "./scripts/mvnw-local.sh -q -DskipTests compile";
        log.info(
                "IntegrationCoordinator running verification: integrationBranch={}, command={}",
                integrationBranch,
                verificationCommand
        );
        CommandExecutionPort.CommandExecutionResult commandResult =
                commandExecutionPort.execute(repositoryPath, verificationCommand);
        String summary = summarizeVerification(commandResult);
        log.info(
                "IntegrationCoordinator verification completed: integrationBranch={}, success={}, exitCode={}",
                integrationBranch,
                commandResult.isSuccess(),
                commandResult.exitCode()
        );
        return new IntegrationResult(
                commandResult.isSuccess(),
                integrationBranch,
                mergedBranches,
                List.of(),
                summary,
                commandResult.isSuccess() ? null : summary
        );
    }

    private String summarizeVerification(CommandExecutionPort.CommandExecutionResult result) {
        String stderr = result.stderr() == null ? "" : result.stderr().trim();
        String stdout = result.stdout() == null ? "" : result.stdout().trim();
        String detail = !stderr.isBlank() ? stderr : stdout;
        if (detail.length() > 200) {
            detail = detail.substring(0, 200);
        }
        return "commandExitCode=" + result.exitCode() + ", detail=" + detail;
    }
}
