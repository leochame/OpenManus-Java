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
 * Conversation Buffer
 * 
 * Responsible for managing short-term memory and context window, including:
 * - Message history management
 * - Context window limits
 * - Message compression and summarization
 * - Important message retention
 */
@Component
public class ConversationBuffer {
    
    private static final Logger logger = LoggerFactory.getLogger(ConversationBuffer.class);
    
    // Configuration parameters
    private final int maxMessages;
    private final int maxTokens;
    private final int compressionThreshold;
    
    // Message storage
    private final Queue<MessageEntry> messages;
    private final List<ChatMessage> systemMessages;
    private final Set<String> pinnedMessageIds;
    
    // Statistics
    private int totalMessages = 0;
    private int compressedMessages = 0;
    
    /**
     * Constructor
     * 
     * @param maxMessages Maximum number of messages
     * @param maxTokens Maximum number of tokens
     * @param compressionThreshold Compression threshold
     */
    public ConversationBuffer(int maxMessages, int maxTokens, int compressionThreshold) {
        this.maxMessages = maxMessages;
        this.maxTokens = maxTokens;
        this.compressionThreshold = compressionThreshold;
        this.messages = new ConcurrentLinkedQueue<>();
        this.systemMessages = new ArrayList<>();
        this.pinnedMessageIds = new HashSet<>();
        
        logger.info("ConversationBuffer initialized: maxMessages={}, maxTokens={}, compressionThreshold={}", 
                   maxMessages, maxTokens, compressionThreshold);
    }
    
    /**
     * Default constructor
     */
    public ConversationBuffer() {
        this(100, 8000, 50);
    }
    
    /**
     * Add message to buffer
     * 
     * @param message Message to add
     */
    public synchronized void addMessage(ChatMessage message) {
        if (message == null) {
            logger.warn("Attempted to add null message");
            return;
        }
        
        MessageEntry entry = new MessageEntry(message, LocalDateTime.now(), generateMessageId());
        
        // Handle system messages separately
        if (message instanceof SystemMessage) {
            systemMessages.add(message);
            logger.debug("Added system message: {}", truncateText(getMessageText(message), 100));
            return;
        }
        
        messages.offer(entry);
        totalMessages++;
        
        logger.debug("Added message: type={}, content={}", 
                    message.getClass().getSimpleName(), 
                    truncateText(getMessageText(message), 100));
        
        // Check if cleanup is needed
        if (shouldCleanup()) {
            cleanup();
        }
    }
    
    /**
     * Get all messages (for LLM calls)
     * 
     * @return List of messages
     */
    public synchronized List<ChatMessage> getMessages() {
        List<ChatMessage> result = new ArrayList<>();
        
        // Add system messages
        result.addAll(systemMessages);
        
        // Add conversation messages
        result.addAll(messages.stream()
                .map(MessageEntry::getMessage)
                .collect(Collectors.toList()));
        
        logger.debug("Retrieved message list: system={}, conversation={}, total={}", 
                    systemMessages.size(), messages.size(), result.size());
        
        return result;
    }
    
    /**
     * Get recent N messages
     * 
     * @param count Number of messages
     * @return List of messages
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
     * Pin important message (won't be cleaned up)
     * 
     * @param messageId Message ID
     */
    public synchronized void pinMessage(String messageId) {
        pinnedMessageIds.add(messageId);
        logger.debug("Pinned message: {}", messageId);
    }
    
    /**
     * Unpin message
     * 
     * @param messageId Message ID
     */
    public synchronized void unpinMessage(String messageId) {
        pinnedMessageIds.remove(messageId);
        logger.debug("Unpinned message: {}", messageId);
    }
    
    /**
     * Clear buffer
     */
    public synchronized void clear() {
        messages.clear();
        systemMessages.clear();
        pinnedMessageIds.clear();
        totalMessages = 0;
        compressedMessages = 0;
        logger.info("Buffer cleared");
    }
    
    /**
     * Get buffer statistics
     * 
     * @return Statistics
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
     * Check if cleanup is needed
     */
    private boolean shouldCleanup() {
        return messages.size() > maxMessages || estimateTokenCount() > maxTokens;
    }
    
    /**
     * Clean up old messages
     */
    private void cleanup() {
        logger.debug("Starting buffer cleanup: current messages={}, current tokens={}", 
                    messages.size(), estimateTokenCount());
        
        int removedCount = 0;
        
        // Remove oldest non-pinned messages
        Iterator<MessageEntry> iterator = messages.iterator();
        while (iterator.hasNext() && shouldCleanup()) {
            MessageEntry entry = iterator.next();
            
            // Skip pinned messages
            if (pinnedMessageIds.contains(entry.getId())) {
                continue;
            }
            
            iterator.remove();
            removedCount++;
        }
        
        if (removedCount > 0) {
            logger.info("Cleanup completed: removed {} messages, remaining messages={}", removedCount, messages.size());
        }
        
        // Consider compression if still over limit
        if (shouldCleanup() && messages.size() > compressionThreshold) {
            compressOldMessages();
        }
    }
    
    /**
     * Compress old messages
     */
    private void compressOldMessages() {
        // Message compression logic can be implemented here
        // For example: merge multiple messages into summaries
        logger.debug("Message compression feature to be implemented");
        compressedMessages++;
    }
    
    /**
     * Estimate token count
     */
    private int estimateTokenCount() {
        // Simple estimation: 1 token ≈ 4 characters
        return messages.stream()
                .mapToInt(entry -> getMessageText(entry.getMessage()).length() / 4)
                .sum() +
               systemMessages.stream()
                .mapToInt(msg -> getMessageText(msg).length() / 4)
                .sum();
    }
    
    /**
     * Generate message ID
     */
    private String generateMessageId() {
        return "msg_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
    }
    
    /**
     * Truncate text for logging
     */
    private String truncateText(String text, int maxLength) {
        if (text == null) return "null";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }
    
    /**
     * Get text content from ChatMessage
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
     * Message entry
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
     * Buffer statistics
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