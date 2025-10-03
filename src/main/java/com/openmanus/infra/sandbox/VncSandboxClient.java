package com.openmanus.infra.sandbox;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.netty.NettyDockerCmdExecFactory;
import com.github.dockerjava.api.async.ResultCallback;
import com.openmanus.infra.config.OpenManusProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.TimeUnit;

/**
 * VNC 沙箱客户端 - 提供带图形界面的浏览器沙箱
 * 
 * 功能特性：
 * 1. 启动带有桌面环境和浏览器的 Docker 容器
 * 2. 暴露 VNC Web 接口（noVNC）供前端嵌入
 * 3. 生成可访问的 VNC URL
 * 4. 支持容器生命周期管理
 * 
 * 设计模式：建造者模式 + 单例容器管理
 */
@Component
public class VncSandboxClient implements Closeable {
    private static final Logger log = LoggerFactory.getLogger(VncSandboxClient.class);
    
    // VNC 配置常量
    private static final String VNC_IMAGE = "dorowu/ubuntu-desktop-lxde-vnc:latest";
    private static final int VNC_WEB_PORT = 6080;  // noVNC Web 端口
    private static final int VNC_PORT = 5900;      // VNC 原生端口
    private static final String VNC_RESOLUTION = "1280x720";
    private static final String VNC_PASSWORD = "openmanus";
    
    private final DockerClient dockerClient;
    private final OpenManusProperties.SandboxSettings config;
    private final String hostAddress;
    
    /**
     * VNC 沙箱信息 - 封装容器元数据
     */
    public static class VncSandboxInfo {
        private final String containerId;
        private final String vncUrl;
        private final int mappedPort;
        
        public VncSandboxInfo(String containerId, String vncUrl, int mappedPort) {
            this.containerId = containerId;
            this.vncUrl = vncUrl;
            this.mappedPort = mappedPort;
        }
        
        public String getContainerId() {
            return containerId;
        }
        
        public String getVncUrl() {
            return vncUrl;
        }
        
        public int getMappedPort() {
            return mappedPort;
        }
        
        @Override
        public String toString() {
            return String.format("VncSandboxInfo{containerId='%s', vncUrl='%s', mappedPort=%d}", 
                    containerId, vncUrl, mappedPort);
        }
    }
    
    public VncSandboxClient(OpenManusProperties properties) {
        this.config = properties.getSandbox();
        
        // 初始化 Docker 客户端
        DefaultDockerClientConfig dockerConfig = DefaultDockerClientConfig.createDefaultConfigBuilder()
            .build();
        
        this.dockerClient = DockerClientBuilder.getInstance(dockerConfig)
            .withDockerCmdExecFactory(new NettyDockerCmdExecFactory())
            .build();
        
        // 获取宿主机地址
        this.hostAddress = getHostAddress();
        
        try {
            dockerClient.pingCmd().exec();
            log.info("VNC Sandbox Docker 客户端初始化成功");
        } catch (Exception e) {
            log.error("Docker 连接失败: {}", e.getMessage(), e);
            throw new RuntimeException("VNC Sandbox Docker 初始化失败", e);
        }
    }
    
    /**
     * 创建 VNC 沙箱容器
     * 
     * @param sessionId 会话标识符（用于容器命名）
     * @return VNC 沙箱信息，包含容器 ID 和访问 URL
     */
    public VncSandboxInfo createVncSandbox(String sessionId) {
        try {
            log.info("开始创建 VNC 沙箱，会话ID: {}", sessionId);
            
            // 拉取镜像（如果不存在）
            pullVncImageIfNeeded();
            
            // 创建容器配置
            String containerName = "vnc-sandbox-" + sessionId;
            
            CreateContainerResponse container = dockerClient.createContainerCmd(VNC_IMAGE)
                .withName(containerName)
                .withEnv(
                    "RESOLUTION=" + VNC_RESOLUTION,
                    "VNC_PASSWORD=" + VNC_PASSWORD,
                    "HTTP_PASSWORD=" + VNC_PASSWORD
                )
                .withHostConfig(HostConfig.newHostConfig()
                    // 端口映射：将容器的 6080 端口映射到宿主机随机端口
                    .withPortBindings(new PortBinding(
                        Ports.Binding.empty(),
                        new ExposedPort(VNC_WEB_PORT)
                    ))
                    // 资源限制
                    .withMemory(parseMemoryLimit("1g"))  // VNC 需要更多内存
                    .withCpuQuota(200000L)  // 2 CPU cores
                    .withCpuPeriod(100000L)
                    // 网络配置
                    .withNetworkMode("bridge")
                    // 自动清理
                    .withAutoRemove(false)  // VNC 容器需要持久化
                    // SHM 大小（Chrome 浏览器需要）
                    .withShmSize(512L * 1024 * 1024)  // 512MB
                )
                .withExposedPorts(new ExposedPort(VNC_WEB_PORT))
                .exec();
            
            String containerId = container.getId();
            
            // 启动容器
            dockerClient.startContainerCmd(containerId).exec();
            log.info("VNC 容器启动成功: {}", containerId);
            
            // 等待容器就绪
            waitForContainerReady(containerId, 10);
            
            // 获取映射的端口
            int mappedPort = getContainerMappedPort(containerId, VNC_WEB_PORT);
            
            // 生成 VNC 访问 URL
            String vncUrl = generateVncUrl(mappedPort);
            
            VncSandboxInfo sandboxInfo = new VncSandboxInfo(containerId, vncUrl, mappedPort);
            log.info("VNC 沙箱创建完成: {}", sandboxInfo);
            
            return sandboxInfo;
            
        } catch (Exception e) {
            log.error("创建 VNC 沙箱失败: {}", e.getMessage(), e);
            throw new RuntimeException("VNC 沙箱创建失败", e);
        }
    }
    
    /**
     * 拉取 VNC Docker 镜像（如果不存在）
     */
    private void pullVncImageIfNeeded() {
        try {
            dockerClient.inspectImageCmd(VNC_IMAGE).exec();
            log.debug("VNC 镜像已存在: {}", VNC_IMAGE);
        } catch (Exception e) {
            log.info("拉取 VNC 镜像: {}", VNC_IMAGE);
            try {
                dockerClient.pullImageCmd(VNC_IMAGE)
                    .exec(new PullImageResultCallback())
                    .awaitCompletion(10, TimeUnit.MINUTES);
                log.info("VNC 镜像拉取成功: {}", VNC_IMAGE);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("VNC 镜像拉取被中断", ie);
            }
        }
    }
    
    /**
     * 等待容器就绪
     */
    private void waitForContainerReady(String containerId, int timeoutSeconds) {
        log.info("等待 VNC 容器就绪...");
        try {
            for (int i = 0; i < timeoutSeconds; i++) {
                var inspection = dockerClient.inspectContainerCmd(containerId).exec();
                if (Boolean.TRUE.equals(inspection.getState().getRunning())) {
                    Thread.sleep(2000);  // 额外等待服务启动
                    log.info("VNC 容器已就绪");
                    return;
                }
                Thread.sleep(1000);
            }
            log.warn("VNC 容器启动超时，但将继续");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("等待容器就绪时被中断");
        }
    }
    
    /**
     * 获取容器端口映射
     */
    private int getContainerMappedPort(String containerId, int containerPort) {
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
     * 生成 VNC 访问 URL
     */
    private String generateVncUrl(int port) {
        // 格式: http://<host>:<port>/vnc.html
        String url = String.format("http://%s:%d/vnc.html", hostAddress, port);
        log.info("VNC 访问地址: {}", url);
        return url;
    }
    
    /**
     * 获取宿主机地址
     */
    private String getHostAddress() {
        try {
            // 优先使用环境变量配置的地址
            String envHost = System.getenv("OPENMANUS_HOST_ADDRESS");
            if (envHost != null && !envHost.isEmpty()) {
                return envHost;
            }
            
            // 否则尝试获取本机地址
            InetAddress localHost = InetAddress.getLocalHost();
            return localHost.getHostAddress();
        } catch (Exception e) {
            log.warn("无法获取宿主机地址，使用 localhost: {}", e.getMessage());
            return "localhost";
        }
    }
    
    /**
     * 停止并删除 VNC 沙箱
     */
    public void destroyVncSandbox(String containerId) {
        try {
            log.info("停止 VNC 沙箱容器: {}", containerId);
            dockerClient.stopContainerCmd(containerId)
                .withTimeout(10)
                .exec();
            
            // 等待容器停止
            Thread.sleep(2000);
            
            // 删除容器
            dockerClient.removeContainerCmd(containerId)
                .withForce(true)
                .exec();
            
            log.info("VNC 沙箱容器已删除: {}", containerId);
        } catch (Exception e) {
            log.error("删除 VNC 沙箱失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 检查容器是否在运行
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
     * 解析内存限制字符串
     */
    private long parseMemoryLimit(String memoryLimit) {
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
                log.info("VNC Sandbox Docker 客户端已关闭");
            } catch (Exception e) {
                log.error("关闭 Docker 客户端失败: {}", e.getMessage(), e);
            }
        }
    }
    
    /**
     * Docker 镜像拉取回调
     */
    private static class PullImageResultCallback extends ResultCallback.Adapter<PullResponseItem> {
        @Override
        public void onNext(PullResponseItem item) {
            if (item.getStatus() != null) {
                log.debug("镜像拉取进度: {}", item.getStatus());
            }
        }
    }
}

