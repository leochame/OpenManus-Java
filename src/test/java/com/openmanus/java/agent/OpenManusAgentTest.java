package com.openmanus.java.agent;

import com.openmanus.java.config.OpenManusProperties;
import com.openmanus.java.state.OpenManusAgentState;
import com.openmanus.java.tool.ToolRegistry;
import com.openmanus.java.tool.AskHumanTool;
import com.openmanus.java.tool.MockAskHumanTool;
import com.openmanus.java.tool.TerminateTool;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.agent.tool.ToolSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * OpenManusAgent 测试类
 */
public class OpenManusAgentTest {
    
    @Mock
    private ChatLanguageModel mockChatModel;
    
    @Mock
    private OpenManusProperties mockProperties;
    
    private ToolRegistry toolRegistry;
    private OpenManusAgent agent;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // 创建简单的工具注册表
        toolRegistry = new ToolRegistry(
            new MockAskHumanTool(),
            new TerminateTool()
        );
        
        // 设置模拟的 ChatLanguageModel
        AiMessage mockResponse = AiMessage.from("这是一个测试响应");
        Response<AiMessage> mockResponseWrapper = Response.from(mockResponse);
        
        when(mockChatModel.generate(anyList())).thenReturn(mockResponseWrapper);
        when(mockChatModel.generate(anyList(), anyList())).thenReturn(mockResponseWrapper);
        
        // 创建 Agent
        agent = new OpenManusAgent(mockChatModel, toolRegistry, mockProperties);
    }
    
    @Test
    @DisplayName("测试 Agent 初始化")
    void testAgentInitialization() {
        assertNotNull(agent, "Agent 应该成功初始化");
        assertNotNull(agent.getCompiledGraph(), "编译后的图应该不为空");
    }
    
    @Test
    @DisplayName("测试 Agent 状态")
    void testAgentState() {
        // 测试 OpenManusAgentState 的基本功能
        OpenManusAgentState state = new OpenManusAgentState();
        
        assertNotNull(state, "状态对象应该不为空");
        assertEquals(OpenManusAgentState.Status.IDLE, state.getAgentStatus(), "初始状态应该是 IDLE");
        
        // 测试状态更新方法返回映射
        Map<String, Object> statusUpdate = state.updateAgentStatus(OpenManusAgentState.Status.THINKING);
        assertNotNull(statusUpdate, "状态更新映射不应为空");
        assertTrue(statusUpdate.containsKey(OpenManusAgentState.AGENT_STATUS_KEY), "状态更新映射应包含状态键");
        assertEquals(OpenManusAgentState.Status.THINKING.name(), statusUpdate.get(OpenManusAgentState.AGENT_STATUS_KEY), "状态更新映射应包含新状态的字符串表示");
        
        // 测试消息获取
        assertTrue(state.getMessages().isEmpty(), "初始消息列表应该为空");
        
        // 测试任务更新方法
        Map<String, Object> taskUpdate = state.updateCurrentTask("测试任务");
        assertNotNull(taskUpdate, "任务更新映射不应为空");
        assertTrue(taskUpdate.containsKey(OpenManusAgentState.CURRENT_TASK_KEY), "任务更新映射应包含任务键");
        assertEquals("测试任务", taskUpdate.get(OpenManusAgentState.CURRENT_TASK_KEY), "任务更新映射应包含新任务");
    }
    
    @Test
    @DisplayName("测试 Agent 执行 - 简单对话")
    void testAgentExecution() throws ExecutionException, InterruptedException {
        // 测试简单的对话执行
        String userMessage = "你好，请介绍一下自己";
        
        CompletableFuture<String> resultFuture = agent.execute(userMessage);
        assertNotNull(resultFuture, "执行结果 Future 不应为空");
        
        // 等待执行完成
        String result = resultFuture.get();
        assertNotNull(result, "执行结果不应为空");
        assertFalse(result.isEmpty(), "执行结果不应为空字符串");
        
        // 验证 ChatLanguageModel 被调用（可能带有工具规格）
        verify(mockChatModel, atLeastOnce()).generate(anyList(), anyList());
    }
    
    @Test
    @DisplayName("测试工具注册表集成")
    void testToolRegistryIntegration() {
        // 验证工具注册表正确集成
        assertNotNull(toolRegistry, "工具注册表不应为空");
        assertTrue(toolRegistry.getToolCount() > 0, "应该有可用的工具");
        
        // 验证特定工具存在
        assertTrue(toolRegistry.hasToolByName("ask_human"), "应该有 ask_human 工具");
        assertTrue(toolRegistry.hasToolByName("terminate"), "应该有 terminate 工具");
        
        // 验证工具规格
        List<ToolSpecification> specs = toolRegistry.getToolSpecifications();
        assertNotNull(specs, "工具规格列表不应为空");
        assertFalse(specs.isEmpty(), "应该有工具规格");
    }
    
    @Test
    @DisplayName("测试错误处理")
    void testErrorHandling() {
        // 测试空消息处理
        CompletableFuture<String> emptyResult = agent.execute("");
        assertNotNull(emptyResult, "空消息的执行结果不应为空");
        
        // 测试 null 消息处理
        CompletableFuture<String> nullResult = agent.execute(null);
        assertNotNull(nullResult, "null 消息的执行结果不应为空");
    }
    
    @Test
    @DisplayName("测试状态图结构")
    void testStateGraphStructure() {
        // 验证状态图的基本结构
        assertNotNull(agent.getCompiledGraph(), "编译后的图不应为空");
        
        // 这里可以添加更多关于图结构的测试
        // 例如验证节点数量、边的连接等
    }
} 