package com.openmanus.java;

import com.openmanus.java.config.OpenManusProperties;
import com.openmanus.java.llm.LlmClient;
import com.openmanus.java.model.Message;
import com.openmanus.java.model.Memory;
import com.openmanus.java.model.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Disabled;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 集成测试类，用于测试OpenManus Java版本的核心功能集成
 */
public class OpenManusIntegrationTest {

    private OpenManusProperties properties;
    private Memory memory;

    @BeforeEach
    void setUp() {
        properties = new OpenManusProperties();
        memory = new Memory();
    }

    @Test
    @DisplayName("测试Memory和Message的基本功能")
    void testMemoryAndMessageIntegration() {
        // 测试Memory初始化
        assertNotNull(memory, "Memory应该成功创建");
        assertTrue(memory.getMessages().isEmpty(), "新创建的Memory应该为空");

        // 测试添加用户消息
        Message userMessage = Message.userMessage("Hello, OpenManus!");
        assertNotNull(userMessage, "用户消息应该成功创建");
        assertEquals("Hello, OpenManus!", userMessage.getContent(), "消息内容应该正确");
        assertEquals(Role.USER, userMessage.getRole(), "消息角色应该是user");

        memory.addMessage(userMessage);
        assertEquals(1, memory.getMessages().size(), "Memory应该包含一条消息");
        assertEquals(userMessage, memory.getMessages().get(0), "Memory中的消息应该是刚添加的消息");

        // 测试添加助手消息
        Message assistantMessage = Message.assistantMessage("Hello! How can I help you?");
        assertNotNull(assistantMessage, "助手消息应该成功创建");
        assertEquals("Hello! How can I help you?", assistantMessage.getContent(), "助手消息内容应该正确");
        assertEquals(Role.ASSISTANT, assistantMessage.getRole(), "消息角色应该是assistant");

        memory.addMessage(assistantMessage);
        assertEquals(2, memory.getMessages().size(), "Memory应该包含两条消息");

        // 测试消息顺序
        assertEquals(Role.USER, memory.getMessages().get(0).getRole(), "第一条消息应该是用户消息");
        assertEquals(Role.ASSISTANT, memory.getMessages().get(1).getRole(), "第二条消息应该是助手消息");
    }

    @Test
    @DisplayName("测试系统消息功能")
    void testSystemMessage() {
        Message systemMessage = Message.systemMessage("You are a helpful assistant.");
        assertNotNull(systemMessage, "系统消息应该成功创建");
        assertEquals("You are a helpful assistant.", systemMessage.getContent(), "系统消息内容应该正确");
        assertEquals(Role.SYSTEM, systemMessage.getRole(), "消息角色应该是SYSTEM");

        memory.addMessage(systemMessage);
        assertEquals(1, memory.getMessages().size(), "Memory应该包含一条系统消息");
    }

    @Test
    @DisplayName("测试配置和Memory的集成")
    void testConfigurationMemoryIntegration() {
        // 验证配置可以正常访问
        assertNotNull(properties.getApp().getWorkspaceRoot(), "工作空间配置应该可访问");
        assertNotNull(properties.getLlm().getDefaultLlm().getModel(), "LLM模型配置应该可访问");

        // 创建包含配置信息的消息
        String configInfo = String.format("当前工作空间: %s, 使用模型: %s", 
            properties.getApp().getWorkspaceRoot(),
            properties.getLlm().getDefaultLlm().getModel());
        
        Message configMessage = Message.systemMessage(configInfo);
        memory.addMessage(configMessage);
        
        assertEquals(1, memory.getMessages().size(), "应该成功添加配置消息");
        assertTrue(memory.getMessages().get(0).getContent().contains("当前工作空间"), "消息应该包含配置信息");
    }

    @Test
    @Disabled("需要有效的API密钥才能运行")
    @DisplayName("测试LlmClient集成 - 需要API密钥")
    void testLlmClientIntegration() {
        // 这个测试需要有效的API密钥，所以默认禁用
        // 在有API密钥的环境中可以启用此测试
        
        try {
            // 测试LlmClient构造
            OpenManusProperties.LLMSettings.DefaultLLM defaultLlm = properties.getLlm().getDefaultLlm();
            LlmClient llmClient = new LlmClient(null, defaultLlm); // 使用null作为ChatLanguageModel进行测试
            
            assertNotNull(llmClient, "LlmClient应该能够成功创建");
            
            // 测试配置一致性
        OpenManusProperties.AppSettings appSettings = properties.getApp();
        assertNotNull(appSettings, "应用配置不应为空");
        assertNotNull(appSettings.getWorkspaceRoot(), "工作空间根目录不应为空");
            
            // 准备测试消息
            memory.addMessage(Message.userMessage("Hello, this is a test message."));
            
            // 注意：实际调用需要有效的API密钥
            // String response = llmClient.chat(memory.getMessages());
            // assertNotNull(response, "LLM应该返回响应");
            
        } catch (Exception e) {
            // 如果没有API密钥或网络问题，测试应该优雅地处理
            System.out.println("LlmClient测试跳过: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("测试多轮对话场景")
    void testMultiTurnConversation() {
        // 模拟多轮对话
        memory.addMessage(Message.systemMessage("You are a helpful coding assistant."));
        memory.addMessage(Message.userMessage("How do I create a Java class?"));
        memory.addMessage(Message.assistantMessage("To create a Java class, you use the 'class' keyword..."));
        memory.addMessage(Message.userMessage("Can you show me an example?"));
        memory.addMessage(Message.assistantMessage("Sure! Here's a simple example: public class MyClass { }"));

        assertEquals(5, memory.getMessages().size(), "应该有5条消息");
        
        // 验证对话结构
        assertEquals(Role.SYSTEM, memory.getMessages().get(0).getRole(), "第一条应该是系统消息");
        assertEquals(Role.USER, memory.getMessages().get(1).getRole(), "第二条应该是用户消息");
        assertEquals(Role.ASSISTANT, memory.getMessages().get(2).getRole(), "第三条应该是助手消息");
        assertEquals(Role.USER, memory.getMessages().get(3).getRole(), "第四条应该是用户消息");
        assertEquals(Role.ASSISTANT, memory.getMessages().get(4).getRole(), "第五条应该是助手消息");
    }

    @Test
    @DisplayName("测试配置的完整性和一致性")
    void testConfigurationConsistency() {
        // 测试所有配置模块都能正常访问
        assertDoesNotThrow(() -> {
            // App配置
            // 验证应用配置可访问性
        OpenManusProperties.AppSettings appSettings = properties.getApp();
        assertNotNull(appSettings, "应用配置应该可访问");
            String workspace = properties.getApp().getWorkspaceRoot();
            
            // LLM配置
            String model = properties.getLlm().getDefaultLlm().getModel();
            String apiType = properties.getLlm().getDefaultLlm().getApiType();
            double temperature = properties.getLlm().getDefaultLlm().getTemperature();
            
            // 沙箱配置
            boolean useSandbox = properties.getSandbox().isUseSandbox();
            String image = properties.getSandbox().getImage();
            int timeout = properties.getSandbox().getTimeout();
            
            // 验证默认值的合理性
            assertNotNull(workspace, "工作空间路径不应为null");
            assertNotNull(workspace, "工作空间不应为null");
            assertNotNull(model, "模型不应为null");
            assertNotNull(apiType, "API类型不应为null");
            assertTrue(temperature >= 0 && temperature <= 2, "温度值应该在合理范围内");
            assertNotNull(image, "镜像名称不应为null");
            assertTrue(timeout > 0, "超时时间应该大于0");
            
        }, "访问所有配置项时不应抛出异常");
    }

    @Test
    @DisplayName("测试错误处理和边界情况")
    void testErrorHandlingAndEdgeCases() {
        // 测试空消息处理
        assertDoesNotThrow(() -> {
            Message emptyMessage = Message.userMessage("");
            memory.addMessage(emptyMessage);
        }, "添加空消息不应抛出异常");

        // 测试null消息处理（Memory可能允许null值）
        assertDoesNotThrow(() -> {
            memory.addMessage(null);
        }, "Memory应该能够处理null消息");

        // 测试大量消息
        assertDoesNotThrow(() -> {
            for (int i = 0; i < 100; i++) {
                memory.addMessage(Message.userMessage("Message " + i));
            }
        }, "添加大量消息不应抛出异常");
        
        assertTrue(memory.getMessages().size() >= 100, "应该能够处理大量消息");
    }
}