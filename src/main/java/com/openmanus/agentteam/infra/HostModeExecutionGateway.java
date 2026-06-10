package com.openmanus.agentteam.infra;

import com.openmanus.aiframework.runtime.AiSandboxCommandResult;
import com.openmanus.aiframework.runtime.AiSessionSandboxGateway;
import com.openmanus.aiframework.runtime.AiSessionSandboxInfo;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Host-mode execution gateway for agentteam coding sub-agents.
 *
 * Unlike the Docker sandbox gateway, ALL operations (shell, file I/O, path resolution)
 * execute directly on the Host OS filesystem. This allows coding sub-agents to work
 * inside git worktrees created by {@code ParallelCodingOrchestrator}.
 *
 * Thread-safety: stateless — path resolution is per-call and Host OS thread-safe.
 */
@Slf4j
public class HostModeExecutionGateway implements AiSessionSandboxGateway {

    private static final int DEFAULT_TIMEOUT_SECONDS = 15;

    private final BashSecurityChecker bashSecurityChecker;

    /**
     * Maps sessionId → worktree root path for file operation boundary checks.
     * Populated when {@link #executeCommand} is called with a valid cwd.
     */
    private final Map<String, Path> worktreeRoots = new ConcurrentHashMap<>();

    /**
     * Creates a HostModeExecutionGateway without security checks.
     * Prefer {@link #HostModeExecutionGateway(BashSecurityChecker)} for production use.
     */
    public HostModeExecutionGateway() {
        this.bashSecurityChecker = null;
    }

    /**
     * Creates a HostModeExecutionGateway with bash security checking enabled.
     */
    public HostModeExecutionGateway(BashSecurityChecker bashSecurityChecker) {
        this.bashSecurityChecker = Objects.requireNonNull(bashSecurityChecker, "bashSecurityChecker");
    }

    @Override
    public Optional<AiSessionSandboxInfo> getSandboxInfo(String sessionId) {
        return Optional.empty();
    }

    @Override
    public AiSessionSandboxInfo getOrCreateSandbox(String sessionId) {
        String workspaceRoot = getWorkspaceRoot(sessionId);
        return new AiSessionSandboxInfo(sessionId, null, workspaceRoot, null, null, "HOST_MODE");
    }

    @Override
    public String getWorkspaceRoot(String sessionId) {
        return System.getProperty("user.dir");
    }

    /**
     * Resolves a user path WITHOUT sandbox remapping.
     * <p>
     * When a worktree root has been registered for this session (via a prior
     * {@link #executeCommand} call), relative paths are resolved against that
     * worktree root. Otherwise, they fall back to the JVM current working directory.
     * <p>
     * Absolute paths are returned as-is — they will be validated later by
     * {@link #validateFileOperationPath}.
     */
    @Override
    public String resolveWorkspacePath(String sessionId, String userPath) {
        Path basePath = resolveEffectiveBase(sessionId);
        if (userPath == null || userPath.isBlank()) {
            return basePath.toString();
        }
        Path candidate = Paths.get(userPath);
        if (candidate.isAbsolute()) {
            return candidate.normalize().toString();
        }
        return basePath.resolve(candidate).normalize().toString();
    }

    /**
     * Returns the effective base path for resolving relative paths for a session.
     * Prefers the registered worktree root (set by {@link #executeCommand}) over
     * the JVM current working directory.
     */
    private Path resolveEffectiveBase(String sessionId) {
        Path worktreeRoot = worktreeRoots.get(sessionId);
        if (worktreeRoot != null) {
            return worktreeRoot;
        }
        return Paths.get("").toAbsolutePath().normalize();
    }

    /**
     * Executes a shell command on the Host OS using {@link ProcessBuilder}.
     */
    @Override
    public AiSandboxCommandResult executeCommand(String sessionId, String command, String cwd, int timeoutSeconds) {
        if (command == null || command.isBlank()) {
            return new AiSandboxCommandResult("", "command is blank", -1);
        }

        // Security check: intercept dangerous commands before they reach the Host OS
        if (bashSecurityChecker != null && bashSecurityChecker.isEnabled()) {
            BashSecurityCheckResult checkResult = bashSecurityChecker.check(command, cwd, sessionId);
            if (!checkResult.allowed()) {
                log.warn("HostModeExecutionGateway BLOCKED command: sessionId={} rule={} command={}",
                        sessionId, checkResult.rule(), command);
                return new AiSandboxCommandResult("", checkResult.reason(), -1);
            }
        }

        Path workingDir = resolveCwd(cwd);

        // Register worktree root for file operation boundary checks
        if (sessionId != null && cwd != null && !cwd.isBlank()) {
            worktreeRoots.put(sessionId, workingDir);
        }
        int effectiveTimeout = timeoutSeconds > 0 ? timeoutSeconds : DEFAULT_TIMEOUT_SECONDS;
        try {
            ProcessBuilder processBuilder = new ProcessBuilder();
            if (isWindows()) {
                processBuilder.command("cmd", "/c", command);
            } else {
                processBuilder.command("sh", "-c", command);
            }
            processBuilder.directory(workingDir.toFile());
            processBuilder.redirectErrorStream(false);

            Process process = processBuilder.start();
            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();

            Thread stdoutReader = new Thread(() -> {
                try {
                    process.getInputStream().transferTo(stdout);
                } catch (IOException ignored) {
                }
            });
            Thread stderrReader = new Thread(() -> {
                try {
                    process.getErrorStream().transferTo(stderr);
                } catch (IOException ignored) {
                }
            });
            stdoutReader.start();
            stderrReader.start();

            boolean finished = process.waitFor(effectiveTimeout, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                stdoutReader.interrupt();
                stderrReader.interrupt();
                return new AiSandboxCommandResult(
                        stdout.toString(StandardCharsets.UTF_8),
                        stderr.toString(StandardCharsets.UTF_8) + "\n执行超时",
                        124
                );
            }
            stdoutReader.join(Math.max(1, effectiveTimeout));
            stderrReader.join(Math.max(1, effectiveTimeout));

            int exitCode = process.exitValue();
            return new AiSandboxCommandResult(
                    stdout.toString(StandardCharsets.UTF_8),
                    stderr.toString(StandardCharsets.UTF_8),
                    exitCode
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new AiSandboxCommandResult("", "执行被中断: " + e.getMessage(), 130);
        } catch (Exception e) {
            log.warn("Host command execution failed: cwd={}, command={}, error={}", workingDir, command, e.getMessage());
            return new AiSandboxCommandResult("", "执行失败: " + e.getMessage(), 1);
        }
    }

    /**
     * Not supported in host mode — returns an error result.
     */
    @Override
    public AiSandboxCommandResult openBrowserUrl(String sessionId, String url) {
        return new AiSandboxCommandResult("", "浏览器操作在 Host 模式下不可用", -1);
    }

    /**
     * Reads a text file directly from the Host filesystem.
     * When security is enabled, validates the path is within a registered worktree root.
     */
    @Override
    public String readTextFile(String sessionId, String path) {
        Path filePath = Paths.get(resolveWorkspacePath(sessionId, path));
        validateFileOperationPath(sessionId, filePath);
        try {
            return Files.readString(filePath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("读取文件失败: " + filePath + " — " + e.getMessage(), e);
        }
    }

    /**
     * Writes content to a text file on the Host filesystem.
     * When security is enabled, validates the path is within a registered worktree root.
     */
    @Override
    public void writeTextFile(String sessionId, String path, String content) {
        Path filePath = Paths.get(resolveWorkspacePath(sessionId, path));
        validateFileOperationPath(sessionId, filePath);
        try {
            Path parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(filePath, content == null ? "" : content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("写入文件失败: " + filePath + " — " + e.getMessage(), e);
        }
    }

    /**
     * Validates that a file operation path is within the worktree boundary for the session.
     * Only enforced when BashSecurityChecker is active.
     */
    private void validateFileOperationPath(String sessionId, Path resolvedPath) {
        if (bashSecurityChecker == null || !bashSecurityChecker.isEnabled()) {
            return;
        }
        Path worktreeRoot = worktreeRoots.get(sessionId);
        if (worktreeRoot == null) {
            // No worktree root registered yet — allow (first shell command will register one)
            return;
        }
        Path normalized = resolvedPath.toAbsolutePath().normalize();
        if (!normalized.startsWith(worktreeRoot)) {
            String message = "安全策略拒绝 (PATH_TRAVERSAL): "
                    + "文件操作试图访问工作树之外的路径: " + resolvedPath
                    + " (worktreeRoot=" + worktreeRoot + ")";
            log.warn("HostModeExecutionGateway BLOCKED file operation: sessionId={} path={} worktreeRoot={}",
                    sessionId, resolvedPath, worktreeRoot);
            throw new SecurityException(message);
        }
    }

    private Path resolveCwd(String cwd) {
        if (cwd == null || cwd.isBlank()) {
            return Paths.get("").toAbsolutePath().normalize();
        }
        return Paths.get(cwd).toAbsolutePath().normalize();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
