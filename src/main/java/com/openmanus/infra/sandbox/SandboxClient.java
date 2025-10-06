package com.openmanus.infra.sandbox;

import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.InspectExecResponse;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.openmanus.infra.config.OpenManusProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Docker 代码执行沙箱客户端
 * 
 * 功能：
 * 1. 提供安全的 Python/Bash 代码执行环境
 * 2. 资源隔离和限制
 * 3. 支持本地执行模式（禁用沙箱时）
 * 
 * 设计：单例容器，应用启动时初始化并持续运行
 */
@Component
public class SandboxClient implements Closeable {
    private static final Logger log = LoggerFactory.getLogger(SandboxClient.class);
    
    private final DockerClientManager dockerManager;
    private final OpenManusProperties.SandboxSettings config;
    private String containerId;
    private boolean isRunning = false;
    
    @Autowired
    public SandboxClient(OpenManusProperties properties) {
        this.config = properties.getSandbox();
        
        if (!config.isUseSandbox()) {
            this.dockerManager = null;
            log.info("沙箱已禁用，将使用本地执行模式");
            return;
        }
        
        // 初始化 Docker 管理器
        this.dockerManager = new DockerClientManager();
        
        try {
            initializeContainer();
        } catch (Exception e) {
            log.error("初始化沙箱容器失败: {}", e.getMessage(), e);
            throw new RuntimeException("沙箱初始化失败", e);
        }
    }
    
    /**
     * 初始化沙箱容器
     */
    private void initializeContainer() {
        log.info("初始化沙箱容器...");
        
        // 拉取镜像
        dockerManager.pullImageIfNeeded(config.getImage());
        
        // 创建容器
        CreateContainerResponse container = dockerManager.getClient()
            .createContainerCmd(config.getImage())
            .withWorkingDir(config.getWorkDir())
            .withHostConfig(HostConfig.newHostConfig()
                .withMemory(DockerClientManager.parseMemoryLimit(config.getMemoryLimit()))
                .withCpuQuota((long) (config.getCpuLimit() * 100000))
                .withCpuPeriod(100000L)
                .withNetworkMode(config.isNetworkEnabled() ? "bridge" : "none")
                .withAutoRemove(true)
            )
            .withCmd("tail", "-f", "/dev/null")  // 保持容器运行
            .exec();
        
        this.containerId = container.getId();
        
        // 启动容器
        dockerManager.getClient().startContainerCmd(containerId).exec();
        this.isRunning = true;
        
        log.info("沙箱容器启动成功: {}", containerId);
    }
    
    /**
     * 执行命令
     * 
     * @param command 要执行的命令
     * @param timeoutSeconds 超时时间（秒），0 表示使用默认超时
     * @return 执行结果
     */
    public ExecutionResult executeCommand(String command, int timeoutSeconds) {
        if (!config.isUseSandbox()) {
            return executeLocally(command, timeoutSeconds);
        }
        
        if (!isRunning) {
            throw new IllegalStateException("沙箱容器未运行");
        }
        
        try {
            // 创建执行实例
            ExecCreateCmdResponse execCmd = dockerManager.getClient()
                .execCreateCmd(containerId)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withCmd("/bin/sh", "-c", command)
                .exec();
            
            // 执行命令并捕获输出
            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();
            
            @SuppressWarnings("deprecation")
            ExecStartResultCallback callback = new ExecStartResultCallback(stdout, stderr);
            dockerManager.getClient().execStartCmd(execCmd.getId()).exec(callback);
            
            // 等待执行完成
            int timeout = timeoutSeconds > 0 ? timeoutSeconds : config.getTimeout();
            boolean completed = callback.awaitCompletion(timeout, TimeUnit.SECONDS);
            
            if (!completed) {
                log.warn("命令执行超时: {} 秒", timeout);
                return new ExecutionResult(
                    stdout.toString(StandardCharsets.UTF_8),
                    stderr.toString(StandardCharsets.UTF_8) + "\n执行超时",
                    124
                );
            }
            
            // 获取退出码
            InspectExecResponse execResponse = dockerManager.getClient()
                .inspectExecCmd(execCmd.getId()).exec();
            Integer exitCode = execResponse.getExitCodeLong() != null ? 
                execResponse.getExitCodeLong().intValue() : 0;
            
            return new ExecutionResult(
                stdout.toString(StandardCharsets.UTF_8),
                stderr.toString(StandardCharsets.UTF_8),
                exitCode
            );
            
        } catch (Exception e) {
            log.error("沙箱执行命令失败: {}", e.getMessage(), e);
            return new ExecutionResult("", "沙箱执行失败: " + e.getMessage(), 1);
        }
    }
    
    /**
     * 本地执行命令（沙箱禁用时）
     */
    private ExecutionResult executeLocally(String command, int timeoutSeconds) {
        try {
            ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", command);
            pb.redirectErrorStream(false);
            Process process = pb.start();
            
            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();
            
            // 读取输出流
            Thread stdoutThread = new Thread(() -> {
                try {
                    process.getInputStream().transferTo(stdout);
                } catch (IOException e) {
                    log.error("读取 stdout 失败: {}", e.getMessage());
                }
            });
            
            Thread stderrThread = new Thread(() -> {
                try {
                    process.getErrorStream().transferTo(stderr);
                } catch (IOException e) {
                    log.error("读取 stderr 失败: {}", e.getMessage());
                }
            });
            
            stdoutThread.start();
            stderrThread.start();
            
            // 等待进程完成
            int timeout = timeoutSeconds > 0 ? timeoutSeconds : config.getTimeout();
            boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);
            
            if (!finished) {
                process.destroyForcibly();
                return new ExecutionResult(
                    stdout.toString(StandardCharsets.UTF_8),
                    stderr.toString(StandardCharsets.UTF_8) + "\n执行超时",
                    124
                );
            }
            
            stdoutThread.join(1000);
            stderrThread.join(1000);
            
            return new ExecutionResult(
                stdout.toString(StandardCharsets.UTF_8),
                stderr.toString(StandardCharsets.UTF_8),
                process.exitValue()
            );
            
        } catch (Exception e) {
            log.error("本地执行命令失败: {}", e.getMessage(), e);
            return new ExecutionResult("", "本地执行失败: " + e.getMessage(), 1);
        }
    }
    
    /**
     * 执行 Python 脚本
     */
    public ExecutionResult executePython(String script, int timeoutSeconds) {
        String command = String.format("python3 -c %s", escapeShellArgument(script));
        return executeCommand(command, timeoutSeconds);
    }
    
    /**
     * 执行 Bash 脚本
     */
    public ExecutionResult executeBash(String script, int timeoutSeconds) {
        return executeCommand(script, timeoutSeconds);
    }
    
    /**
     * Shell 参数转义
     */
    private String escapeShellArgument(String arg) {
        return "'" + arg.replace("'", "'\"'\"'") + "'";
    }
    
    @Override
    public void close() throws IOException {
        if (dockerManager != null && containerId != null && isRunning) {
            try {
                log.info("停止沙箱容器: {}", containerId);
                dockerManager.getClient().stopContainerCmd(containerId).exec();
                isRunning = false;
                log.info("沙箱容器已停止");
            } catch (Exception e) {
                log.error("停止容器失败: {}", e.getMessage(), e);
            }
        }
        
        if (dockerManager != null) {
            try {
                dockerManager.close();
            } catch (Exception e) {
                log.error("关闭 Docker 管理器失败: {}", e.getMessage(), e);
            }
        }
    }
}