package com.openmanus.java.integration;

import com.openmanus.java.agent.ManusAgent;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OpenManus Java 端到端测试
 * 
 * 测试完整的系统功能，包括：
 * - Agent 完整推理流程
 * - 工具集成和调用
 * - StateGraph 工作流执行
 * - 错误处理和恢复
 * - 性能和稳定性
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("OpenManus Java 端到端测试")
public class EndToEndTest {
    
    private static final Logger logger = LoggerFactory.getLogger(EndToEndTest.class);
    
    @Autowired
    private ChatModel chatModel;
    
    @Autowired
    private ManusAgent manusAgent;
    
    @Test
    @DisplayName("测试ChatModel配置")
    void testChatModelConfiguration() {
        logger.info("测试ChatModel配置...");
        
        assertNotNull(chatModel, "ChatModel不应为空");
        logger.info("ChatModel配置测试完成");
    }
    
    @Test
    @DisplayName("测试ManusAgent配置")
    void testManusAgentConfiguration() {
        logger.info("测试ManusAgent配置...");
        
        assertNotNull(manusAgent, "ManusAgent不应为空");
        logger.info("ManusAgent配置测试完成");
    }
    
    @Test
    @DisplayName("测试简单对话")
    void testSimpleConversation() {
        logger.info("开始测试简单对话...");
        
        String userMessage = "你好，请简单介绍一下你自己";
        
        try {
            // 执行CoT推理对话
            Map<String, Object> result = manusAgent.chatWithCot(userMessage);
            
            // 验证结果
            assertNotNull(result, "结果不应为空");
            assertTrue(result.containsKey("answer"), "结果应包含答案");
            
            String answer = (String) result.get("answer");
            assertNotNull(answer, "答案不应为空");
            assertFalse(answer.trim().isEmpty(), "答案不应为空字符串");
            
            logger.info("简单对话测试完成，答案: {}", answer);
            
        } catch (Exception e) {
            logger.error("简单对话测试失败", e);
            fail("简单对话测试失败: " + e.getMessage());
        }
    }
}