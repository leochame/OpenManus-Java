package com.openmanus.java.memory;

import com.openmanus.java.config.VectorDatabaseConfig;
import com.openmanus.java.config.VectorDatabaseConfig.EmbeddingStoreService;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Memory Tool
 * 
 * Provides long-term memory storage and retrieval functionality, including:
 * - Store important information in vector database
 * - Retrieve memories based on semantic similarity
 * - Memory classification and tag management
 * - Memory importance scoring
 */
@Component
public class MemoryTool {
    
    private static final Logger logger = LoggerFactory.getLogger(MemoryTool.class);
    
    @Autowired
    private EmbeddingStoreService embeddingStoreService;
    
    // Memory types
    public enum MemoryType {
        FACT("Fact"),
        EXPERIENCE("Experience"),
        PREFERENCE("Preference"),
        CONTEXT("Context"),
        KNOWLEDGE("Knowledge");
        
        private final String description;
        
        MemoryType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * Store memory
     * 
     * @param content Memory content
     * @param type Memory type
     * @param importance Importance score (0.0-1.0)
     * @param tags Tags (comma separated)
     * @return Storage result
     */
    @Tool("Store important information in long-term memory. Used to save user preferences, important facts, lessons learned, and other information that needs to be retained long-term.")
    public String storeMemory(String content, String type, double importance, String tags) {
        try {
            if (content == null || content.trim().isEmpty()) {
                return "Error: Memory content cannot be empty";
            }
            
            // 验证重要性评�?
            if (importance < 0.0 || importance > 1.0) {
                importance = Math.max(0.0, Math.min(1.0, importance));
            }
            
            // 解析记忆类型
            MemoryType memoryType = parseMemoryType(type);
            
            // 创建元数�?
            Map<String, Object> metadataMap = new HashMap<>();
            metadataMap.put("type", memoryType.name());
            metadataMap.put("importance", importance);
            metadataMap.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            metadataMap.put("tags", tags != null ? tags.trim() : "");
            
            Metadata metadata = Metadata.from(metadataMap);
            
            // 存储到向量数据库
            String memoryId = embeddingStoreService.store(content, metadata);
            
            logger.info("Memory stored successfully: ID={}, Type={}, Importance={}, Content={}", 
                       memoryId, memoryType.getDescription(), importance, 
                       content.length() > 100 ? content.substring(0, 100) + "..." : content);
            
            return String.format("�? Memory stored\\n" +
                    "📝 Content: %s\\n" +
                    "🏷�? Type: %s\\n" +
                    "�? Importance: %.2f\\n" +
                    "🔖 Tags: %s\\n" +
                    "🆔 ID: %s",
                    content.length() > 200 ? content.substring(0, 200) + "..." : content,
                    memoryType.getDescription(),
                    importance,
                    tags != null ? tags : "None",
                    memoryId);
                    
        } catch (Exception e) {
            logger.error("Failed to store memory: {}", e.getMessage(), e);
            return "�? Failed to store memory: " + e.getMessage();
        }
    }
    
    /**
     * Retrieve memory
     * 
     * @param query Query content
     * @param maxResults Maximum number of results
     * @param minRelevance Minimum relevance score (0.0-1.0)
     * @return Retrieval result
     */
    @Tool("Retrieve relevant information from long-term memory. Used to recall previously stored important information, user preferences, lessons learned, etc.")
    public String retrieveMemory(String query, int maxResults, double minRelevance) {
        try {
            if (query == null || query.trim().isEmpty()) {
                return "Error: Query content cannot be empty";
            }
            
            // 设置默认�?
            if (maxResults <= 0) maxResults = 5;
            if (minRelevance < 0.0 || minRelevance > 1.0) minRelevance = 0.3;
            
            // 检索相似记�?
            List<EmbeddingMatch<Metadata>> matches = embeddingStoreService.search(query, maxResults, minRelevance);
            
            if (matches.isEmpty()) {
                return "🔍 No relevant memories found\\n" +
                       "💡 Suggestion: Try different keywords or lower the relevance requirement";
            }
            
            StringBuilder result = new StringBuilder();
            result.append(String.format("🧠 Found %d relevant memories:\\n\\n", matches.size()));
            
            for (int i = 0; i < matches.size(); i++) {
                EmbeddingMatch<Metadata> match = matches.get(i);
                Metadata metadata = match.embedded();
                
                result.append(String.format("**%d. Memory Fragment** (Relevance: %.2f)\\n", i + 1, match.score()));
                result.append(String.format("📝 Content: %s\\n", getEmbeddedText(match)));
                
                if (metadata != null) {
                    String type = metadata.getString("type");
                    Double importance = metadata.getDouble("importance");
                    String timestamp = metadata.getString("timestamp");
                    String tags = metadata.getString("tags");
                    
                    if (type != null) {
                        result.append(String.format("🏷�? Type: %s\\n", 
                                parseMemoryType(type).getDescription()));
                    }
                    if (importance != null) {
                        result.append(String.format("�? Importance: %.2f\\n", importance));
                    }
                    if (timestamp != null) {
                        result.append(String.format("📅 Time: %s\\n", timestamp));
                    }
                    if (tags != null && !tags.trim().isEmpty()) {
                        result.append(String.format("🔖 Tags: %s\\n", tags));
                    }
                }
                
                result.append("\\n");
            }
            
            logger.info("Memory retrieval successful: Query='{}', Results={}", query, matches.size());
            return result.toString();
            
        } catch (Exception e) {
            logger.error("Failed to retrieve memory: {}", e.getMessage(), e);
            return "�? Failed to retrieve memory: " + e.getMessage();
        }
    }
    
    /**
     * Get memory statistics
     * 
     * @return Statistics
     */
    @Tool("Get memory system statistics, including number of stored memories, type distribution, etc.")
    public String getMemoryStats() {
        try {
            int totalMemories = embeddingStoreService.size();
            
            StringBuilder stats = new StringBuilder();
            stats.append("🧠 Memory System Statistics\\n\\n");
            stats.append(String.format("📊 Total memories: %d\\n", totalMemories));
            stats.append("\\n💡 Memory type descriptions:\\n");
            
            for (MemoryType type : MemoryType.values()) {
                stats.append(String.format("�? %s: %s\\n", type.name(), type.getDescription()));
            }
            
            return stats.toString();
            
        } catch (Exception e) {
            logger.error("Failed to get memory stats: {}", e.getMessage(), e);
            return "�? Failed to get memory stats: " + e.getMessage();
        }
    }
    
    /**
     * Clear memories
     * 
     * @param confirmationCode Confirmation code (must be "CONFIRM_DELETE_ALL")
     * @return Clearing result
     */
    @Tool("Clear all memories (dangerous operation). Requires confirmation code 'CONFIRM_DELETE_ALL' to execute this operation.")
    public String clearAllMemories(String confirmationCode) {
        try {
            if (!"CONFIRM_DELETE_ALL".equals(confirmationCode)) {
                return "�? Confirmation code error. If you want to clear all memories, please provide the confirmation code: CONFIRM_DELETE_ALL";
            }
            
            embeddingStoreService.removeAll();
            logger.warn("All memories have been cleared");
            
            return "�? All memories cleared\\n" +
                   "⚠️ This operation is irreversible, please use with caution";
                   
        } catch (Exception e) {
            logger.error("Failed to clear memories: {}", e.getMessage(), e);
            return "�? Failed to clear memories: " + e.getMessage();
        }
    }
    
    /**
     * Parse memory type
     */
    private MemoryType parseMemoryType(String type) {
        if (type == null || type.trim().isEmpty()) {
            return MemoryType.CONTEXT;
        }
        
        try {
            return MemoryType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            // 尝试按描述匹�?
            for (MemoryType memoryType : MemoryType.values()) {
                if (memoryType.getDescription().equals(type)) {
                    return memoryType;
                }
            }
            return MemoryType.CONTEXT;
        }
    }
    
    /**
     * Get embedded text content
     * This is a simplified implementation, actual application requires more complex logic
     */
    private String getEmbeddedText(EmbeddingMatch<Metadata> match) {
        // Since we cannot directly get the original text from EmbeddingMatch,
        // here we return a placeholder. In a real application, the original text should be stored in metadata
        return "Memory content (ID: " + match.embeddingId() + ")";
    }
} 