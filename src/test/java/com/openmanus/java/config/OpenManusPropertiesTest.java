package com.openmanus.java.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 配置系统详细测试类
 * 测试配置参数验证和默认配置值
 */
@SpringBootTest(classes = {OpenManusProperties.class, TestConfig.class})
@EnableConfigurationProperties(OpenManusProperties.class)
class OpenManusPropertiesTest {

    private OpenManusProperties properties;

    @BeforeEach
    void setUp() {
        properties = new OpenManusProperties();
    }

    @Test
    @DisplayName("测试应用配置默认值")
    void testAppConfigurationDefaults() {
        OpenManusProperties.AppSettings app = properties.getApp();
        
        assertNotNull(app, "应用配置不应为null");
        assertNotNull(app.getWorkspaceRoot(), "工作空间根目录应该有默认值");
        assertTrue(app.getWorkspaceRoot().length() > 0, "工作空间根目录不应为空");
        
        // 验证默认工作空间路径格式
        String workspaceRoot = app.getWorkspaceRoot();
        assertTrue(workspaceRoot.startsWith("/") || workspaceRoot.matches("[A-Za-z]:\\\\.+") || workspaceRoot.startsWith("./") || workspaceRoot.startsWith("../"), 
                   "工作空间路径应该是有效的路径格式（绝对路径或相对路径）");
    }

    @Test
    @DisplayName("测试沙箱配置默认值")
    void testSandboxConfigurationDefaults() {
        OpenManusProperties.SandboxSettings sandbox = properties.getSandbox();
        
        assertNotNull(sandbox, "沙箱配置不应为null");
        assertTrue(sandbox.isUseSandbox(), "默认应该启用沙箱");
        assertEquals("python:3.11-slim", sandbox.getImage(), "默认镜像应该是python:3.11-slim");
        assertEquals("/workspace", sandbox.getWorkDir(), "默认工作目录应该是/workspace");
        assertEquals(120, sandbox.getTimeout(), "默认超时时间应该是120秒");
        
        // 验证超时时间范围
        assertTrue(sandbox.getTimeout() > 0, "超时时间应该大于0");
        assertTrue(sandbox.getTimeout() <= 3600, "超时时间不应超过1小时");
    }

    @Test
    @DisplayName("测试LLM配置默认值")
    void testLlmConfigurationDefaults() {
        OpenManusProperties.LLMSettings llm = properties.getLlm();
        
        assertNotNull(llm, "LLM配置不应为null");
        assertNotNull(llm.getDefaultLlm(), "默认LLM配置不应为null");
        
        OpenManusProperties.LLMSettings.DefaultLLM defaultLlm = llm.getDefaultLlm();
        assertNotNull(defaultLlm.getApiType(), "API类型不应为null");
        assertNotNull(defaultLlm.getModel(), "模型名称不应为null");
        
        // 验证API类型是有效值
        String apiType = defaultLlm.getApiType();
        assertTrue(apiType.equals("anthropic") || apiType.equals("openai") || 
                  apiType.equals("azure") || apiType.equals("google") || apiType.equals("ollama"),
                  "API类型应该是支持的类型之一");
        
        // 验证模型名称不为空
        assertTrue(defaultLlm.getModel().length() > 0, "模型名称不应为空");
    }

    @Test
    @DisplayName("测试配置参数验证")
    void testConfigurationValidation() {
        // 测试所有必要的配置项都可以访问
        assertDoesNotThrow(() -> {
            properties.getApp().getWorkspaceRoot();
            properties.getSandbox().isUseSandbox();
            properties.getSandbox().getImage();
            properties.getSandbox().getWorkDir();
            properties.getSandbox().getTimeout();
            properties.getLlm().getDefaultLlm().getApiType();
            properties.getLlm().getDefaultLlm().getModel();
        }, "访问所有配置项时不应抛出异常");
    }

    @Test
    @DisplayName("测试配置对象的不可变性")
    void testConfigurationImmutability() {
        OpenManusProperties.AppSettings app1 = properties.getApp();
        OpenManusProperties.AppSettings app2 = properties.getApp();
        
        // 验证每次获取的是同一个对象（单例模式）
        assertSame(app1, app2, "配置对象应该是单例的");
        
        OpenManusProperties.SandboxSettings sandbox1 = properties.getSandbox();
        OpenManusProperties.SandboxSettings sandbox2 = properties.getSandbox();
        assertSame(sandbox1, sandbox2, "沙箱配置对象应该是单例的");
        
        OpenManusProperties.LLMSettings llm1 = properties.getLlm();
        OpenManusProperties.LLMSettings llm2 = properties.getLlm();
        assertSame(llm1, llm2, "LLM配置对象应该是单例的");
    }

    @Test
    @DisplayName("测试配置字符串表示")
    void testConfigurationStringRepresentation() {
        // 验证toString方法不会抛出异常
        assertDoesNotThrow(() -> {
            String appStr = properties.getApp().toString();
            String sandboxStr = properties.getSandbox().toString();
            String llmStr = properties.getLlm().toString();
            
            assertNotNull(appStr, "应用配置的字符串表示不应为null");
            assertNotNull(sandboxStr, "沙箱配置的字符串表示不应为null");
            assertNotNull(llmStr, "LLM配置的字符串表示不应为null");
        }, "配置对象的toString方法不应抛出异常");
    }
}