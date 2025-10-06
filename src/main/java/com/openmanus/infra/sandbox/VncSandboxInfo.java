package com.openmanus.infra.sandbox;

/**
 * VNC 沙箱信息
 * 
 * 封装 VNC 容器的元数据和访问信息
 */
public class VncSandboxInfo {
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
