package com.openmanus.java.memory;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ConversationBuffer 单元测试
 */
public class ConversationBufferTest {
    
    private ConversationBuffer buffer;
    
    @BeforeEach
    void setUp() {
        // 创建一个小容量的缓冲区用于测试
        buffer = new ConversationBuffer(5, 100, 3);
    }
    
    @Test
    @DisplayName("测试添加和获取消息")
    void testAddAndGetMessages() {
        // 添加系统消息
        SystemMessage systemMessage = SystemMessage.from("你是一个AI助手");
        buffer.addMessage(systemMessage);
        
        // 添加用户消息
        UserMessage userMessage = UserMessage.from("你好");
        buffer.addMessage(userMessage);
        
        // 添加AI回复
        AiMessage aiMessage = AiMessage.from("你好！我是AI助手，很高兴为您服务。");
        buffer.addMessage(aiMessage);
        
        // 验证消息数量和内容
        List<ChatMessage> messages = buffer.getMessages();
        assertEquals(3, messages.size(), "应该有3条消息");
        
        // 验证消息顺序：系统消息在前，然后是对话消息
        assertTrue(messages.get(0) instanceof SystemMessage, "第一条应该是系统消息");
        assertTrue(messages.get(1) instanceof UserMessage, "第二条应该是用户消息");
        assertTrue(messages.get(2) instanceof AiMessage, "第三条应该是AI消息");
    }
    
    @Test
    @DisplayName("测试消息数量限制和清理")
    void testMessageLimitAndCleanup() {
        // 添加超过限制的消息
        for (int i = 0; i < 10; i++) {
            buffer.addMessage(UserMessage.from("消息 " + i));
        }
        
        // 验证消息数量不超过限制
        List<ChatMessage> messages = buffer.getMessages();
        assertTrue(messages.size() <= 5, "消息数量不应超过限制");
        
        // 验证保留的是最新的消息
        String lastMessageText = messages.get(messages.size() - 1).text();
        assertTrue(lastMessageText.contains("消息 9"), "应该保留最新的消息");
    }
    
    @Test
    @DisplayName("测试获取最近消息")
    void testGetRecentMessages() {
        // 添加多条消息
        for (int i = 0; i < 5; i++) {
            buffer.addMessage(UserMessage.from("消息 " + i));
        }
        
        // 获取最近3条消息
        List<ChatMessage> recentMessages = buffer.getRecentMessages(3);
        assertEquals(3, recentMessages.size(), "应该返回3条最近的消息");
        
        // 验证是最新的消息
        String lastMessageText = recentMessages.get(recentMessages.size() - 1).text();
        assertTrue(lastMessageText.contains("消息 4"), "应该是最新的消息");
    }
    
    @Test
    @DisplayName("测试固定消息功能")
    void testPinMessage() {
        // 添加消息并获取ID（这里简化测试，实际中需要能获取消息ID）
        buffer.addMessage(UserMessage.from("重要消息"));
        buffer.addMessage(UserMessage.from("普通消息1"));
        buffer.addMessage(UserMessage.from("普通消息2"));
        
        // 测试固定和取消固定
        buffer.pinMessage("test_id");
        buffer.unpinMessage("test_id");
        
        // 这个测试主要验证方法不会抛出异常
        assertDoesNotThrow(() -> {
            buffer.pinMessage("another_id");
            buffer.unpinMessage("another_id");
        });
    }
    
    @Test
    @DisplayName("测试清空缓冲区")
    void testClearBuffer() {
        // 添加消息
        buffer.addMessage(UserMessage.from("测试消息1"));
        buffer.addMessage(UserMessage.from("测试消息2"));
        
        // 验证有消息
        assertFalse(buffer.getMessages().isEmpty(), "添加消息后缓冲区不应为空");
        
        // 清空缓冲区
        buffer.clear();
        
        // 验证已清空
        assertTrue(buffer.getMessages().isEmpty(), "清空后缓冲区应为空");
    }
    
    @Test
    @DisplayName("测试缓冲区统计信息")
    void testBufferStats() {
        // 添加不同类型的消息
        buffer.addMessage(SystemMessage.from("系统消息"));
        buffer.addMessage(UserMessage.from("用户消息"));
        buffer.addMessage(AiMessage.from("AI消息"));
        
        // 获取统计信息
        ConversationBuffer.BufferStats stats = buffer.getStats();
        
        // 验证统计信息
        assertNotNull(stats, "统计信息不应为空");
        assertEquals(2, stats.getMessageCount(), "对话消息数应为2");
        assertEquals(1, stats.getSystemMessageCount(), "系统消息数应为1");
        assertTrue(stats.getEstimatedTokens() > 0, "估算的token数应大于0");
        
        // 验证toString方法
        String statsString = stats.toString();
        assertNotNull(statsString, "统计信息字符串不应为空");
        assertTrue(statsString.contains("BufferStats"), "应包含类名");
    }
    
    @Test
    @DisplayName("测试空消息处理")
    void testNullMessageHandling() {
        // 添加空消息
        buffer.addMessage(null);
        
        // 验证空消息不会被添加
        List<ChatMessage> messages = buffer.getMessages();
        assertTrue(messages.isEmpty(), "空消息不应被添加到缓冲区");
    }
    
    @Test
    @DisplayName("测试默认构造函数")
    void testDefaultConstructor() {
        ConversationBuffer defaultBuffer = new ConversationBuffer();
        
        // 验证默认配置
        assertNotNull(defaultBuffer, "默认缓冲区不应为空");
        
        // 添加消息测试默认配置是否工作
        defaultBuffer.addMessage(UserMessage.from("测试消息"));
        assertEquals(1, defaultBuffer.getMessages().size(), "默认缓冲区应能正常工作");
    }
    
    @Test
    @DisplayName("测试MessageEntry内部类")
    void testMessageEntry() {
        ChatMessage message = UserMessage.from("测试消息");
        ConversationBuffer.MessageEntry entry = new ConversationBuffer.MessageEntry(
                message, 
                java.time.LocalDateTime.now(), 
                "test_id"
        );
        
        // 验证MessageEntry的功能
        assertEquals(message, entry.getMessage(), "消息应该匹配");
        assertEquals("test_id", entry.getId(), "ID应该匹配");
        assertNotNull(entry.getTimestamp(), "时间戳不应为空");
    }
} 