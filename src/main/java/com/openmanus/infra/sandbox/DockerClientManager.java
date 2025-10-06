package com.openmanus.infra.sandbox;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.PullResponseItem;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.netty.NettyDockerCmdExecFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Docker 客户端管理器 - 统一管理 Docker 操作
 * 
 * 职责：
 * 1. Docker 客户端生命周期管理
 * 2. 镜像拉取与检查
 * 3. 容器状态检查
 * 4. 端口映射获取
 * 5. 工具方法（内存解析等）
 * 
 * 设计模式：单例 + 工具类
 */
public class DockerClientManager implements Closeable {
    private static final Logger log = LoggerFactory.getLogger(DockerClientManager.class);
    
    private final DockerClient dockerClient;
    
    @SuppressWarnings("deprecation")
    public DockerClientManager() {
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
            .build();
        
        this.dockerClient = DockerClientBuilder.getInstance(config)
            .withDockerCmdExecFactory(new NettyDockerCmdExecFactory())
            .build();
        
        verifyConnection();
    }
    
    /**
     * 验证 Docker 连接
     */
    private void verifyConnection() {
        try {
            dockerClient.pingCmd().exec();
            log.info("Docker 连接验证成功");
        } catch (Exception e) {
            log.error("Docker 连接失败: {}", e.getMessage(), e);
            throw new RuntimeException("Docker 初始化失败", e);
        }
    }
    
    /**
     * 获取 Docker 客户端
     */
    public DockerClient getClient() {
        return dockerClient;
    }
    
    /**
     * 拉取镜像（如果不存在）
     */
    public void pullImageIfNeeded(String image) {
        try {
            dockerClient.inspectImageCmd(image).exec();
            log.debug("镜像已存在: {}", image);
        } catch (Exception e) {
            log.info("开始拉取镜像: {}", image);
            try {
                dockerClient.pullImageCmd(image)
                    .exec(new PullImageCallback())
                    .awaitCompletion(10, TimeUnit.MINUTES);
                log.info("镜像拉取成功: {}", image);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("镜像拉取被中断", ie);
            }
        }
    }
    
    /**
     * 检查容器是否运行
     */
    public boolean isContainerRunning(String containerId) {
        try {
            var inspection = dockerClient.inspectContainerCmd(containerId).exec();
            return Boolean.TRUE.equals(inspection.getState().getRunning());
        } catch (Exception e) {
            log.warn("检查容器状态失败: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 等待容器就绪
     */
    public void waitForContainerReady(String containerId, int timeoutSeconds) {
        log.info("等待容器就绪...");
        try {
            for (int i = 0; i < timeoutSeconds; i++) {
                var inspection = dockerClient.inspectContainerCmd(containerId).exec();
                if (Boolean.TRUE.equals(inspection.getState().getRunning())) {
                    Thread.sleep(2000);  // 额外等待服务启动
                    log.info("容器已就绪");
                    return;
                }
                Thread.sleep(1000);
            }
            log.warn("容器启动超时，但将继续");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("等待容器就绪时被中断");
        }
    }
    
    /**
     * 获取容器端口映射
     */
    public int getContainerMappedPort(String containerId, int containerPort) {
        var inspection = dockerClient.inspectContainerCmd(containerId).exec();
        var bindings = inspection.getNetworkSettings().getPorts().getBindings();
        var exposedPort = new ExposedPort(containerPort);
        
        Ports.Binding[] portBindings = bindings.get(exposedPort);
        if (portBindings != null && portBindings.length > 0) {
            int mappedPort = Integer.parseInt(portBindings[0].getHostPortSpec());
            log.info("容器端口映射: {} -> {}", containerPort, mappedPort);
            return mappedPort;
        }
        
        throw new RuntimeException("无法获取容器端口映射");
    }
    
    /**
     * 停止并删除容器
     */
    public void destroyContainer(String containerId) {
        try {
            log.info("停止容器: {}", containerId);
            dockerClient.stopContainerCmd(containerId)
                .withTimeout(10)
                .exec();
            
            Thread.sleep(2000);
            
            dockerClient.removeContainerCmd(containerId)
                .withForce(true)
                .exec();
            
            log.info("容器已删除: {}", containerId);
        } catch (Exception e) {
            log.error("删除容器失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 解析内存限制字符串（如 "512m", "1g"）
     */
    public static long parseMemoryLimit(String memoryLimit) {
        String limit = memoryLimit.toLowerCase().trim();
        
        if (limit.endsWith("k")) {
            return Long.parseLong(limit.substring(0, limit.length() - 1)) * 1024;
        } else if (limit.endsWith("m")) {
            return Long.parseLong(limit.substring(0, limit.length() - 1)) * 1024 * 1024;
        } else if (limit.endsWith("g")) {
            return Long.parseLong(limit.substring(0, limit.length() - 1)) * 1024 * 1024 * 1024;
        } else {
            return Long.parseLong(limit);
        }
    }
    
    @Override
    public void close() throws IOException {
        if (dockerClient != null) {
            try {
                dockerClient.close();
                log.info("Docker 客户端已关闭");
            } catch (Exception e) {
                log.error("关闭 Docker 客户端失败: {}", e.getMessage(), e);
            }
        }
    }
    
    /**
     * 镜像拉取回调
     */
    private static class PullImageCallback extends ResultCallback.Adapter<PullResponseItem> {
        @Override
        public void onNext(PullResponseItem item) {
            if (item.getStatus() != null) {
                log.debug("镜像拉取进度: {}", item.getStatus());
            }
        }
    }
}

