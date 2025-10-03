package com.openmanus.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 会话沙箱信息模型
 * 
 * 用于存储每个会话关联的沙箱容器信息，包括：
 * - VNC 访问 URL
 * - 容器 ID
 * - 创建时间
 * - 状态信息
 * 
 * 设计模式：值对象模式（Value Object）- 封装沙箱元数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionSandboxInfo {
    
    /**
     * 会话 ID
     */
    private String sessionId;
    
    /**
     * VNC 沙箱容器 ID
     */
    private String containerId;
    
    /**
     * VNC Web 访问 URL
     * 前端通过 iframe 嵌入此 URL 来展示浏览器工作台
     */
    private String vncUrl;
    
    /**
     * 容器映射的主机端口
     */
    private Integer mappedPort;
    
    /**
     * 沙箱创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 沙箱状态
     */
    private SandboxStatus status;
    
    /**
     * 沙箱状态枚举
     */
    public enum SandboxStatus {
        /** 正在创建 */
        CREATING,
        /** 运行中 */
        RUNNING,
        /** 已停止 */
        STOPPED,
        /** 错误 */
        ERROR
    }
    
    /**
     * 检查沙箱是否可用
     */
    public boolean isAvailable() {
        return status == SandboxStatus.RUNNING && vncUrl != null && !vncUrl.isEmpty();
    }
}

