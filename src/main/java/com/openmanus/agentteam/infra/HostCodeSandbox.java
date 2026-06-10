package com.openmanus.agentteam.infra;

import com.openmanus.aiframework.runtime.AiCodeExecutionResult;
import com.openmanus.aiframework.runtime.AiCodeSandbox;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Host-mode Python code sandbox for agentteam coding sub-agents.
 *
 * Executes Python scripts directly on the Host OS using {@code python} or {@code python3}.
 * Falls back gracefully if Python is not installed on the Host.
 */
@Slf4j
public class HostCodeSandbox implements AiCodeSandbox {

    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    @Override
    public AiCodeExecutionResult executePython(String script, int timeoutSeconds) {
        if (script == null || script.isBlank()) {
            return new AiCodeExecutionResult("", "Python script is blank", -1);
        }
        int effectiveTimeout = timeoutSeconds > 0 ? timeoutSeconds : DEFAULT_TIMEOUT_SECONDS;

        String pythonCommand = resolvePythonCommand();
        if (pythonCommand == null) {
            return new AiCodeExecutionResult(
                    "",
                    "Python is not installed on the Host. Shell commands are available via runShellCommand.",
                    -1
            );
        }

        try {
            Path tempScript = Files.createTempFile("openmanus_host_py_", ".py");
            try {
                Files.writeString(tempScript, script, StandardCharsets.UTF_8);

                ProcessBuilder processBuilder = new ProcessBuilder(pythonCommand, tempScript.toString());
                processBuilder.redirectErrorStream(false);
                Process process = processBuilder.start();

                ByteArrayOutputStream stdout = new ByteArrayOutputStream();
                ByteArrayOutputStream stderr = new ByteArrayOutputStream();

                Thread stdoutReader = pipeAsync(process.getInputStream(), stdout);
                Thread stderrReader = pipeAsync(process.getErrorStream(), stderr);
                stdoutReader.start();
                stderrReader.start();

                boolean finished = process.waitFor(effectiveTimeout, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    stdoutReader.interrupt();
                    stderrReader.interrupt();
                    return new AiCodeExecutionResult("", "Python execution timed out after " + effectiveTimeout + "s", 124);
                }
                stdoutReader.join(Math.max(1, effectiveTimeout));
                stderrReader.join(Math.max(1, effectiveTimeout));

                return new AiCodeExecutionResult(
                        stdout.toString(StandardCharsets.UTF_8),
                        stderr.toString(StandardCharsets.UTF_8),
                        process.exitValue()
                );
            } finally {
                try {
                    Files.deleteIfExists(tempScript);
                } catch (IOException ignored) {
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new AiCodeExecutionResult("", "Python execution interrupted", 130);
        } catch (Exception e) {
            log.warn("Host Python execution failed: {}", e.getMessage());
            return new AiCodeExecutionResult("", "Python execution failed: " + e.getMessage(), 1);
        }
    }

    private String resolvePythonCommand() {
        for (String candidate : new String[]{"python3", "python"}) {
            try {
                ProcessBuilder pb = new ProcessBuilder(
                        isWindows() ? "cmd" : "sh",
                        isWindows() ? "/c" : "-c",
                        candidate + " --version"
                );
                Process process = pb.start();
                boolean finished = process.waitFor(5, TimeUnit.SECONDS);
                if (finished && process.exitValue() == 0) {
                    return candidate;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private Thread pipeAsync(java.io.InputStream source, OutputStream target) {
        return new Thread(() -> {
            try {
                source.transferTo(target);
            } catch (IOException ignored) {
            }
        });
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
