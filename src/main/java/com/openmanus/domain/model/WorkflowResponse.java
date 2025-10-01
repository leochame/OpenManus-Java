package com.openmanus.domain.model;

import lombok.Builder;
import lombok.Data;

/**
 * 工作流响应DTO
 * 用于返回给前端的响应数据
 */
@Data
@Builder
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
} 