package com.openmanus.domain.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 执行请求 DTO
 * 用于接收前端传递的请求数据
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExecutionRequest {
    /**
     * 用户输入内容
     */
    private String input;

    /**
     * 会话ID；为空时由服务端生成新会话。
     */
    private String sessionId;

    @JsonProperty("targetRepositoryPath")
    @JsonAlias({"target_repository_path", "targetRepoPath"})
    private String targetRepositoryPath;
} 
