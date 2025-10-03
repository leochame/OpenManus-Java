package com.openmanus.domain.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

/**
 * 工作流响应DTO
 * 用于返回给前端的响应数据
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkflowResponse {
    /**
     * 是否成功
     */
    private boolean success;
    
    /**
     * 会话ID
     */
    private String sessionId;
    
    /**
     * WebSocket订阅主题
     */
    private String topic;
    
    /**
     * 错误信息，如果有的话
     */
    private String error;
    
    /**
     * VNC 沙箱浏览器 URL
     * 前端可通过 iframe 嵌入此 URL 来展示 Agent 的工作台
     */
    private String sandboxVncUrl;
    
    /**
     * 沙箱容器 ID
     */
    private String sandboxContainerId;
} 