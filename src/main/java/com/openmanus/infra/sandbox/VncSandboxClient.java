package com.openmanus.infra.sandbox;

import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.openmanus.infra.config.OpenManusProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;

/**
 * VNC 图形界面沙箱客户端
 * 
 * 功能：
 * 1. 提供带桌面环境和浏览器的 Docker 容器
 * 2. 通过 noVNC 提供 Web 访问接口
 * 3. 支持按需创建和销毁（每个 session 独立容器）
 * 
 * 设计：工厂模式，支持多实例
 */
@Component
public class VncSandboxClient implements Closeable {
    private static final Logger log = LoggerFactory.getLogger(VncSandboxClient.class);
    
    // VNC 配置常量
    private static final String VNC_IMAGE = "dorowu/ubuntu-desktop-lxde-vnc:latest";
    private static final int VNC_WEB_PORT = 6080;
    private static final String VNC_RESOLUTION = "1280x720";
    private static final String VNC_PASSWORD = "openmanus";
    
    private final DockerClientManager dockerManager;
    private final String hostAddress;
    
    public VncSandboxClient(OpenManusProperties properties) {
        this.dockerManager = new DockerClientManager();
        this.hostAddress = resolveHostAddress();
        
        log.info("VNC 沙箱客户端初始化成功，主机地址: {}", hostAddress);
    }
    
    /**
     * 创建 VNC 沙箱
     * 
     * @param sessionId 会话标识符（用于容器命名）
     * @return VNC 沙箱信息（包含容器 ID 和访问 URL）
     */
    public VncSandboxInfo createVncSandbox(String sessionId) {
        try {
            log.info("创建 VNC 沙箱，会话ID: {}", sessionId);
            
            // 拉取镜像
            dockerManager.pullImageIfNeeded(VNC_IMAGE);
            
            // 创建容器
            String containerName = "vnc-sandbox-" + sessionId;
            CreateContainerResponse container = dockerManager.getClient()
                .createContainerCmd(VNC_IMAGE)
                .withName(containerName)
                .withEnv(
                    "RESOLUTION=" + VNC_RESOLUTION,
                    "VNC_PASSWORD=" + VNC_PASSWORD,
                    "HTTP_PASSWORD=" + VNC_PASSWORD
                )
                .withHostConfig(HostConfig.newHostConfig()
                    // 端口映射：容器 6080 -> 宿主机随机端口
                    .withPortBindings(new PortBinding(
                        Ports.Binding.empty(),
                        new ExposedPort(VNC_WEB_PORT)
                    ))
                    // 资源限制（VNC 需要更多资源）
                    .withMemory(DockerClientManager.parseMemoryLimit("1g"))
                    .withCpuQuota(200000L)  // 2 CPU cores
                    .withCpuPeriod(100000L)
                    .withNetworkMode("bridge")
                    .withAutoRemove(false)
                    // 共享内存（Chrome 需要）
                    .withShmSize(512L * 1024 * 1024)  // 512MB
                )
                .withExposedPorts(new ExposedPort(VNC_WEB_PORT))
                .exec();
            
            String containerId = container.getId();
            
            // 启动容器
            dockerManager.getClient().startContainerCmd(containerId).exec();
            log.info("VNC 容器启动成功: {}", containerId);
            
            // 等待容器就绪
            dockerManager.waitForContainerReady(containerId, 10);
            
            // 获取映射端口
            int mappedPort = dockerManager.getContainerMappedPort(containerId, VNC_WEB_PORT);
            
            // 生成访问 URL
            String vncUrl = String.format("http://%s:%d/vnc.html", hostAddress, mappedPort);
            
            VncSandboxInfo sandboxInfo = new VncSandboxInfo(containerId, vncUrl, mappedPort);
            log.info("VNC 沙箱创建完成: {}", sandboxInfo);
            
            return sandboxInfo;
            
        } catch (Exception e) {
            log.error("创建 VNC 沙箱失败: {}", e.getMessage(), e);
            throw new RuntimeException("VNC 沙箱创建失败", e);
        }
    }
    
    /**
     * 销毁 VNC 沙箱
     */
    public void destroyVncSandbox(String containerId) {
        dockerManager.destroyContainer(containerId);
    }
    
    /**
     * 检查容器是否运行
     */
    public boolean isContainerRunning(String containerId) {
        return dockerManager.isContainerRunning(containerId);
    }
    
    /**
     * 解析宿主机地址
     */
    private String resolveHostAddress() {
        try {
            // 优先使用环境变量
            String envHost = System.getenv("OPENMANUS_HOST_ADDRESS");
            if (envHost != null && !envHost.isEmpty()) {
                return envHost;
            }
            
            // 否则获取本机地址
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            log.warn("无法获取宿主机地址，使用 localhost: {}", e.getMessage());
            return "localhost";
        }
    }
    
    @Override
    public void close() throws IOException {
        if (dockerManager != null) {
            dockerManager.close();
            log.info("VNC 沙箱客户端已关闭");
        }
    }
}