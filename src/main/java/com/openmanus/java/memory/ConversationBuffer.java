package com.openmanus.java.memory;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

/**
 * 对话缓冲区
 * 
 * 负责管理短期记忆和上下文窗口，包括：
 * - 消息历史管理
 * - 上下文窗口限制
 * - 消息压缩和摘要
 * - 重要消息保留
 */
@Component
public class ConversationBuffer {
    
    private static final Logger logger = LoggerFactory.getLogger(ConversationBuffer.class);
    
    // 配置参数
    private final int maxMessages;
    private final int maxTokens;
    private final int compressionThreshold;
    
    // 消息存储
    private final Queue<MessageEntry> messages;
    private final List<ChatMessage> systemMessages;
    private final Set<String> pinnedMessageIds;
    
    // 统计信息
    private int totalMessages = 0;
    private int compressedMessages = 0;
    
    /**
     * 构造函数
     * 
     * @param maxMessages 最大消息数量
     * @param maxTokens 最大token数量
     * @param compressionThreshold 压缩阈值
     */
    public ConversationBuffer(int maxMessages, int maxTokens, int compressionThreshold) {
        this.maxMessages = maxMessages;
        this.maxTokens = maxTokens;
        this.compressionThreshold = compressionThreshold;
        this.messages = new ConcurrentLinkedQueue<>();
        this.systemMessages = new ArrayList<>();
        this.pinnedMessageIds = new HashSet<>();
        
        logger.info("ConversationBuffer 初始化: maxMessages={}, maxTokens={}, compressionThreshold={}", 
                   maxMessages, maxTokens, compressionThreshold);
    }
    
    /**
     * 默认构造函数
     */
    public ConversationBuffer() {
        this(100, 8000, 50);
    }
    
    /**
     * 添加消息到缓冲区
     * 
     * @param message 要添加的消息
     */
    public synchronized void addMessage(ChatMessage message) {
        if (message == null) {
            logger.warn("尝试添加空消息");
            return;
        }
        
        MessageEntry entry = new MessageEntry(message, LocalDateTime.now(), generateMessageId());
        
        // 系统消息单独处理
        if (message instanceof SystemMessage) {
            systemMessages.add(message);
            logger.debug("添加系统消息: {}", truncateText(getMessageText(message), 100));
            return;
        }
        
        messages.offer(entry);
        totalMessages++;
        
        logger.debug("添加消息: 类型={}, 内容={}", 
                    message.getClass().getSimpleName(), 
                    truncateText(getMessageText(message), 100));
        
        // 检查是否需要清理
        if (shouldCleanup()) {
            cleanup();
        }
    }
    
    /**
     * 获取所有消息（用于LLM调用）
     * 
     * @return 消息列表
     */
    public synchronized List<ChatMessage> getMessages() {
        List<ChatMessage> result = new ArrayList<>();
        
        // 添加系统消息
        result.addAll(systemMessages);
        
        // 添加对话消息
        result.addAll(messages.stream()
                .map(MessageEntry::getMessage)
                .collect(Collectors.toList()));
        
        logger.debug("获取消息列表: 系统消息={}, 对话消息={}, 总计={}", 
                    systemMessages.size(), messages.size(), result.size());
        
        return result;
    }
    
    /**
     * 获取最近的N条消息
     * 
     * @param count 消息数量
     * @return 消息列表
     */
    public synchronized List<ChatMessage> getRecentMessages(int count) {
        List<MessageEntry> recentEntries = messages.stream()
                .skip(Math.max(0, messages.size() - count))
                .collect(Collectors.toList());
        
        List<ChatMessage> result = new ArrayList<>();
        result.addAll(systemMessages);
        result.addAll(recentEntries.stream()
                .map(MessageEntry::getMessage)
                .collect(Collectors.toList()));
        
        return result;
    }
    
    /**
     * 固定重要消息（不会被清理）
     * 
     * @param messageId 消息ID
     */
    public synchronized void pinMessage(String messageId) {
        pinnedMessageIds.add(messageId);
        logger.debug("固定消息: {}", messageId);
    }
    
    /**
     * 取消固定消息
     * 
     * @param messageId 消息ID
     */
    public synchronized void unpinMessage(String messageId) {
        pinnedMessageIds.remove(messageId);
        logger.debug("取消固定消息: {}", messageId);
    }
    
    /**
     * 清空缓冲区
     */
    public synchronized void clear() {
        messages.clear();
        systemMessages.clear();
        pinnedMessageIds.clear();
        totalMessages = 0;
        compressedMessages = 0;
        logger.info("缓冲区已清空");
    }
    
    /**
     * 获取缓冲区统计信息
     * 
     * @return 统计信息
     */
    public synchronized BufferStats getStats() {
        int currentTokens = estimateTokenCount();
        return new BufferStats(
                messages.size(),
                systemMessages.size(),
                totalMessages,
                compressedMessages,
                currentTokens,
                pinnedMessageIds.size()
        );
    }
    
    /**
     * 检查是否需要清理
     */
    private boolean shouldCleanup() {
        return messages.size() > maxMessages || estimateTokenCount() > maxTokens;
    }
    
    /**
     * 清理旧消息
     */
    private void cleanup() {
        logger.debug("开始清理缓冲区: 当前消息数={}, 当前token数={}", 
                    messages.size(), estimateTokenCount());
        
        int removedCount = 0;
        
        // 移除最旧的非固定消息
        Iterator<MessageEntry> iterator = messages.iterator();
        while (iterator.hasNext() && shouldCleanup()) {
            MessageEntry entry = iterator.next();
            
            // 跳过固定的消息
            if (pinnedMessageIds.contains(entry.getId())) {
                continue;
            }
            
            iterator.remove();
            removedCount++;
        }
        
        if (removedCount > 0) {
            logger.info("清理完成: 移除了{}条消息, 剩余消息数={}", removedCount, messages.size());
        }
        
        // 如果清理后仍然超限，考虑压缩
        if (shouldCleanup() && messages.size() > compressionThreshold) {
            compressOldMessages();
        }
    }
    
    /**
     * 压缩旧消息
     */
    private void compressOldMessages() {
        // 这里可以实现消息压缩逻辑
        // 例如：将多条消息合并为摘要
        logger.debug("消息压缩功能待实现");
        compressedMessages++;
    }
    
    /**
     * 估算token数量
     */
    private int estimateTokenCount() {
        // 简单估算：1个token约等于4个字符
        return messages.stream()
                .mapToInt(entry -> getMessageText(entry.getMessage()).length() / 4)
                .sum() +
               systemMessages.stream()
                .mapToInt(msg -> getMessageText(msg).length() / 4)
                .sum();
    }
    
    /**
     * 生成消息ID
     */
    private String generateMessageId() {
        return "msg_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
    }
    
    /**
     * 截断文本用于日志
     */
    private String truncateText(String text, int maxLength) {
        if (text == null) return "null";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }
    
    /**
     * 从ChatMessage中获取文本内容
     */
    private String getMessageText(ChatMessage message) {
        if (message instanceof UserMessage) {
            return ((UserMessage) message).singleText();
        } else if (message instanceof AiMessage) {
            return ((AiMessage) message).text();
        } else if (message instanceof SystemMessage) {
            return ((SystemMessage) message).text();
        } else if (message instanceof ToolExecutionResultMessage) {
            return ((ToolExecutionResultMessage) message).text();
        }
        return "";
    }
    
    /**
     * 消息条目
     */
    public static class MessageEntry {
        private final ChatMessage message;
        private final LocalDateTime timestamp;
        private final String id;
        
        public MessageEntry(ChatMessage message, LocalDateTime timestamp, String id) {
            this.message = message;
            this.timestamp = timestamp;
            this.id = id;
        }
        
        public ChatMessage getMessage() {
            return message;
        }
        
        public LocalDateTime getTimestamp() {
            return timestamp;
        }
        
        public String getId() {
            return id;
        }
    }
    
    /**
     * 缓冲区统计信息
     */
    public static class BufferStats {
        private final int messageCount;
        private final int systemMessageCount;
        private final int totalMessages;
        private final int compressedMessages;
        private final int estimatedTokens;
        private final int pinnedMessages;
        
        public BufferStats(int messageCount, int systemMessageCount, int totalMessages, 
                          int compressedMessages, int estimatedTokens, int pinnedMessages) {
            this.messageCount = messageCount;
            this.systemMessageCount = systemMessageCount;
            this.totalMessages = totalMessages;
            this.compressedMessages = compressedMessages;
            this.estimatedTokens = estimatedTokens;
            this.pinnedMessages = pinnedMessages;
        }
        
        // Getters
        public int getMessageCount() { return messageCount; }
        public int getSystemMessageCount() { return systemMessageCount; }
        public int getTotalMessages() { return totalMessages; }
        public int getCompressedMessages() { return compressedMessages; }
        public int getEstimatedTokens() { return estimatedTokens; }
        public int getPinnedMessages() { return pinnedMessages; }
        
        @Override
        public String toString() {
            return String.format("BufferStats{messages=%d, system=%d, total=%d, compressed=%d, tokens=%d, pinned=%d}",
                    messageCount, systemMessageCount, totalMessages, compressedMessages, estimatedTokens, pinnedMessages);
        }
    }
} 