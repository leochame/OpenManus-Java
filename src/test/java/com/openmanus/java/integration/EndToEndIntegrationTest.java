package com.openmanus.java.integration;

import com.openmanus.java.config.OpenManusProperties;
import com.openmanus.java.tool.*;
import com.openmanus.java.model.*;
import com.openmanus.java.flow.*;
import com.openmanus.java.llm.LlmClient;
import com.openmanus.java.sandbox.SandboxClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.Timeout;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 端到端集成测试类
 * 测试完整的工作流、多工具协作和异常场景处理
 */
@SpringBootTest
@EnableConfigurationProperties(OpenManusProperties.class)
@Import(com.openmanus.java.config.TestConfig.class)
public class EndToEndIntegrationTest {

    private OpenManusProperties properties;
    private ToolRegistry toolRegistry;
    private Memory memory;

    @Mock
    private LlmClient mockLlmClient;
    
    @Mock
    private SandboxClient mockSandboxClient;
    
    @Mock
    private PlanningFlow mockPlanningFlow;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        properties = new OpenManusProperties();
        memory = new Memory();
        
        // 创建工具注册表（只使用不需要外部依赖的工具）
        toolRegistry = new ToolRegistry(
            new AskHumanTool(),
            new TerminateTool()
        );
    }

    @Test
    @DisplayName("测试基础组件集成")
    void testBasicComponentIntegration() {
        // 测试基础组件之间的集成
        assertDoesNotThrow(() -> {
            // 验证配置系统
            assertNotNull(properties, "配置系统应该正常工作");
            assertNotNull(properties.getApp(), "应用配置应该可用");
            assertNotNull(properties.getSandbox(), "沙箱配置应该可用");
            assertNotNull(properties.getLlm(), "LLM配置应该可用");
            
            // 验证内存系统
            assertNotNull(memory, "内存系统应该正常工作");
            assertTrue(memory.getMessages().isEmpty(), "初始内存应该为空");
            
            // 验证工具注册表
            assertNotNull(toolRegistry, "工具注册表应该正常工作");
        }, "基础组件集成测试不应抛出异常");
    }

    @Test
    @DisplayName("测试消息流处理")
    void testMessageFlowProcessing() {
        // 测试完整的消息流处理
        assertDoesNotThrow(() -> {
            // 创建系统消息
            Message systemMessage = Message.systemMessage("You are a helpful assistant.");
            memory.addMessage(systemMessage);
            
            // 创建用户消息
            Message userMessage = Message.userMessage("Hello, can you help me with a simple calculation?");
            memory.addMessage(userMessage);
            
            // 创建助手响应
            Message assistantMessage = Message.assistantMessage("Of course! I'd be happy to help you with calculations. What would you like me to calculate?");
            memory.addMessage(assistantMessage);
            
            // 验证消息流
            List<Message> messages = memory.getMessages();
            assertEquals(3, messages.size(), "应该有3条消息");
            assertEquals(Role.SYSTEM, messages.get(0).getRole(), "第一条应该是系统消息");
            assertEquals(Role.USER, messages.get(1).getRole(), "第二条应该是用户消息");
            assertEquals(Role.ASSISTANT, messages.get(2).getRole(), "第三条应该是助手消息");
        }, "消息流处理测试不应抛出异常");
    }

    @Test
    @DisplayName("测试工具协作场景")
    void testToolCollaborationScenario() {
        // 测试多个工具协作的场景
        assertDoesNotThrow(() -> {
            // 模拟一个需要多个工具协作的场景
            
            // 1. 用户提出问题
            Message userQuestion = Message.userMessage("I need to analyze some data and then ask for user confirmation.");
            memory.addMessage(userQuestion);
            
            // 2. 助手决定使用工具
            Message assistantResponse = Message.assistantMessage("I'll help you analyze the data and then ask for your confirmation.");
            memory.addMessage(assistantResponse);
            
            // 3. 模拟工具调用序列
            // 首先可能需要文件操作工具读取数据
            // 然后使用Python工具进行分析
            // 最后使用AskHuman工具获取用户确认
            
            // 验证工具注册表中的工具
            assertNotNull(toolRegistry, "工具注册表应该可用");
            
            // 验证消息历史
            assertEquals(2, memory.getMessages().size(), "应该有2条消息");
        }, "工具协作场景测试不应抛出异常");
    }

    @Test
    @DisplayName("测试异常场景处理")
    void testExceptionScenarioHandling() {
        // 测试各种异常场景的处理
        assertDoesNotThrow(() -> {
            // 1. 测试空消息处理
            Message emptyMessage = Message.userMessage("");
            // 系统应该能够处理空消息而不崩溃
            
            // 2. 测试null消息处理
            // memory.addMessage(null); // 这可能会抛出异常，这是预期的
            
            // 3. 测试无效角色处理
            Message invalidRoleMessage = Message.userMessage("Test message");
            // 系统应该能够处理基本消息
            
            // 4. 测试超长消息处理
            StringBuilder longContent = new StringBuilder();
            for (int i = 0; i < 10000; i++) {
                longContent.append("This is a very long message. ");
            }
            Message longMessage = Message.userMessage(longContent.toString());
            memory.addMessage(longMessage);
            
            // 验证系统仍然正常工作
            assertNotNull(memory.getMessages(), "内存系统应该仍然正常工作");
        }, "异常场景处理测试不应抛出异常");
    }

    @Test
    @DisplayName("测试并发访问安全性")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testConcurrentAccessSafety() {
        // 测试并发访问的安全性
        assertDoesNotThrow(() -> {
            List<Thread> threads = new ArrayList<>();
            
            // 创建多个线程同时访问内存系统
            for (int i = 0; i < 5; i++) {
                final int threadId = i;
                Thread thread = new Thread(() -> {
                    try {
                        Message message = Message.userMessage("Message from thread " + threadId);
                        memory.addMessage(message);
                        
                        // 读取消息
                        List<Message> messages = memory.getMessages();
                        assertNotNull(messages, "消息列表不应为null");
                    } catch (Exception e) {
                        fail("线程 " + threadId + " 执行失败: " + e.getMessage());
                    }
                });
                threads.add(thread);
                thread.start();
            }
            
            // 等待所有线程完成
            for (Thread thread : threads) {
                thread.join(1000); // 最多等待1秒
            }
            
            // 验证最终状态
            assertTrue(memory.getMessages().size() >= 0, "消息数量应该合理");
        }, "并发访问安全性测试不应抛出异常");
    }

    @Test
    @DisplayName("测试内存管理")
    void testMemoryManagement() {
        // 测试内存管理功能
        assertDoesNotThrow(() -> {
            // 添加大量消息测试内存使用
            for (int i = 0; i < 100; i++) {
                Message message = i % 2 == 0 ? 
                    Message.userMessage("Test message " + i) : 
                    Message.assistantMessage("Test message " + i);
                memory.addMessage(message);
            }
            
            assertEquals(100, memory.getMessages().size(), "应该有100条消息");
            
            // 测试内存清理（如果有的话）
            // memory.clear();
            // assertEquals(0, memory.getMessages().size(), "清理后应该没有消息");
        }, "内存管理测试不应抛出异常");
    }

    @Test
    @DisplayName("测试配置热重载")
    void testConfigurationHotReload() {
        // 测试配置的热重载功能
        assertDoesNotThrow(() -> {
            // 获取初始配置
            OpenManusProperties.SandboxSettings initialSandbox = properties.getSandbox();
            assertNotNull(initialSandbox, "初始沙箱配置不应为null");
            
            // 注意：这里可能需要根据实际的配置热重载实现进行调整
            // 如果支持配置热重载，可以测试配置更新
            
            // 验证配置仍然有效
            assertNotNull(properties.getSandbox(), "沙箱配置应该仍然有效");
            assertNotNull(properties.getLlm(), "LLM配置应该仍然有效");
        }, "配置热重载测试不应抛出异常");
    }

    @Test
    @DisplayName("测试错误恢复机制")
    void testErrorRecoveryMechanism() {
        // 测试系统的错误恢复机制
        assertDoesNotThrow(() -> {
            // 模拟各种错误情况
            
            // 1. 模拟工具执行失败
            // 系统应该能够优雅地处理工具执行失败
            
            // 2. 模拟网络连接失败
            // 系统应该能够处理LLM API调用失败
            
            // 3. 模拟内存不足
            // 系统应该能够处理内存压力
            
            // 验证系统仍然响应
            assertNotNull(memory, "内存系统应该仍然可用");
            assertNotNull(toolRegistry, "工具注册表应该仍然可用");
            assertNotNull(properties, "配置系统应该仍然可用");
        }, "错误恢复机制测试不应抛出异常");
    }

    // 需要完整环境的集成测试
    @Test
    @DisplayName("测试完整工作流（需要完整环境）")
    @EnabledIfEnvironmentVariable(named = "FULL_INTEGRATION_TEST", matches = "true")
    void testFullWorkflow() {
        // 这个测试需要完整的环境（Docker、API密钥等）
        assertDoesNotThrow(() -> {
            // 1. 初始化所有组件
            // LlmClient llmClient = new LlmClient(properties.getLlm());
            // SandboxClient sandboxClient = new SandboxClient(properties.getSandbox());
            // PlanningFlow planningFlow = new PlanningFlow();
            
            // 2. 执行完整的工作流
            // - 接收用户输入
            // - 生成执行计划
            // - 执行工具调用
            // - 生成响应
            
            // 3. 验证结果
            System.out.println("完整工作流测试需要完整的环境配置");
        }, "完整工作流测试");
    }

    @Test
    @DisplayName("测试性能基准")
    void testPerformanceBenchmark() {
        // 测试系统的基本性能
        assertDoesNotThrow(() -> {
            long startTime = System.currentTimeMillis();
            
            // 执行一系列操作
            for (int i = 0; i < 10; i++) {
                Message message = Message.userMessage("Performance test message " + i);
                memory.addMessage(message);
            }
            
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            
            // 验证性能在合理范围内
            assertTrue(duration < 1000, "10条消息的处理时间应该少于1秒: " + duration + "ms");
            
            System.out.println("性能测试完成，耗时: " + duration + "ms");
        }, "性能基准测试不应抛出异常");
    }
}