package com.openmanus.domain.model;

import lombok.Data;

/**
 * 工作流请求DTO
 * 用于接收前端传递的请求数据
 */
@Data
public class WorkflowRequest {
    /**
     * 用户输入内容
     */
    private String input;
} 