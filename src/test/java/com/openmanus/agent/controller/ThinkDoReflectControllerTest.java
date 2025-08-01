package com.openmanus.java.agent.controller;

import com.openmanus.java.agent.workflow.ThinkDoReflectWorkflow;
import com.openmanus.java.domain.controller.ThinkDoReflectController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * ThinkDoReflectController的单元测试
 */
@ExtendWith(MockitoExtension.class)
public class ThinkDoReflectControllerTest {

    @Mock
    private ThinkDoReflectWorkflow workflow;

    @InjectMocks
    private ThinkDoReflectController controller;

    @Test
    public void testHealthEndpoint() {
        // 测试健康检查接口
        ResponseEntity<Map<String, Object>> response = controller.health();
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("healthy", response.getBody().get("status"));
        assertEquals("Think-Do-Reflect Workflow", response.getBody().get("service"));
        assertTrue(response.getBody().containsKey("timestamp"));
    }

    @Test
    public void testExecuteWithValidInput() throws Exception {
        // 模拟工作流返回结果
        String mockResult = "任务执行完成";
        when(workflow.execute(anyString())).thenReturn(CompletableFuture.completedFuture(mockResult));

        // 准备请求
        Map<String, String> request = Map.of("input", "测试任务");

        // 执行测试
        CompletableFuture<ResponseEntity<Map<String, Object>>> future = controller.execute(request);
        ResponseEntity<Map<String, Object>> response = future.get();

        // 验证结果
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(true, response.getBody().get("success"));
        assertEquals(mockResult, response.getBody().get("result"));
        assertEquals("测试任务", response.getBody().get("input"));
    }

    @Test
    public void testExecuteWithEmptyInput() throws Exception {
        // 准备空输入请求
        Map<String, String> request = Map.of("input", "");

        // 执行测试
        CompletableFuture<ResponseEntity<Map<String, Object>>> future = controller.execute(request);
        ResponseEntity<Map<String, Object>> response = future.get();

        // 验证结果
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("输入不能为空", response.getBody().get("error"));
    }

    @Test
    public void testExecuteWithNullInput() throws Exception {
        // 准备null输入请求
        Map<String, String> request = Map.of();

        // 执行测试
        CompletableFuture<ResponseEntity<Map<String, Object>>> future = controller.execute(request);
        ResponseEntity<Map<String, Object>> response = future.get();

        // 验证结果
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("输入不能为空", response.getBody().get("error"));
    }
}
