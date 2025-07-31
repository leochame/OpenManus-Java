package com.openmanus.java.agent.impl.omni.memory;

import com.openmanus.java.infra.config.VectorDatabaseConfig.EmbeddingStoreService;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.segment.TextSegment;
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

/**
 * Memory Tool
 * 
 * Provides long-term memory storage and retrieval functionality:
 * - Store important information in vector database
 * - Retrieve memories based on semantic similarity
 * - Manage memory metadata and importance levels
 */
@Component
public class MemoryTool {
    private static final Logger logger = LoggerFactory.getLogger(MemoryTool.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private EmbeddingStoreService embeddingStoreService;

    public enum MemoryType {
        OBSERVATION("Observation"),
        REFLECTION("Reflection"),
        PLAN("Plan"),
        ACTION("Action"),
        FEEDBACK("Feedback"),
        GENERAL("General Information");

        private final String description;

        MemoryType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Store a memory in the vector database
     *
     * @param content The text content to store
     * @param type The type of memory
     * @param importance The importance level (1-5)
     * @param tags Optional tags for categorization
     * @return Memory ID if successful, error message if failed
     */
    @Tool("Store important information in long-term memory")
    public String storeMemory(String content, String type, Double importance, String tags) {
        try {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("type", type);
            metadata.put("importance", importance);
            metadata.put("timestamp", LocalDateTime.now().format(DATE_FORMATTER));
            if (tags != null && !tags.isEmpty()) {
                metadata.put("tags", tags);
            }

            String memoryId = embeddingStoreService.store(content, metadata);
            logger.info("Memory stored successfully with ID: {}", memoryId);
            return String.format("? Memory stored successfully!\nID: %s\nType: %s\nImportance: %s\nTags: %s",
                    memoryId, type, importance, tags != null ? tags : "none");

        } catch (Exception e) {
            logger.error("Failed to store memory", e);
            return "? Failed to store memory: " + e.getMessage();
        }
    }

    /**
     * Search for relevant memories
     *
     * @param query The search query
     * @param maxResults Maximum number of results to return (default: 5)
     * @param minRelevance Minimum relevance score (0-1, default: 0.7)
     * @return Formatted string of relevant memories
     */
    @Tool("Search for relevant memories based on semantic similarity")
    public String searchMemories(String query, Integer maxResults, Double minRelevance) {
        try {
            if (maxResults == null) maxResults = 5;
            if (minRelevance == null) minRelevance = 0.7;

            List<EmbeddingMatch<TextSegment>> matches = embeddingStoreService.search(query, maxResults, minRelevance);
            
            if (matches.isEmpty()) {
                return "? No relevant memories found\n" +
                       "? Suggestion: Try different keywords or lower the relevance requirement";
            }
            
            StringBuilder result = new StringBuilder();
            result.append(String.format("? Found %d relevant memories:\n\n", matches.size()));
            
            for (int i = 0; i < matches.size(); i++) {
                EmbeddingMatch<TextSegment> match = matches.get(i);
                Map<String, Object> metadata = match.embedded().metadata().toMap();
                
                result.append(String.format("**%d. Memory Fragment** (Relevance: %.2f)\n", i + 1, match.score()));
                
                if (metadata != null) {
                    String type = String.valueOf(metadata.get("type"));
                    String importance = String.valueOf(metadata.get("importance"));
                    String timestamp = String.valueOf(metadata.get("timestamp"));
                    String tags = String.valueOf(metadata.get("tags"));
                    
                    if (type != null && !type.equals("null")) {
                        result.append(String.format("?? Type: %s\n", 
                                parseMemoryType(type).getDescription()));
                    }
                    if (importance != null && !importance.equals("null")) {
                        result.append(String.format("? Importance: %s\n", importance));
                    }
                    if (timestamp != null && !timestamp.equals("null")) {
                        result.append(String.format("? Timestamp: %s\n", timestamp));
                    }
                    if (tags != null && !tags.equals("null")) {
                        result.append(String.format("? Tags: %s\n", tags));
                    }
                }
                
                result.append(String.format("? Content: %s\n\n", match.embedded().text()));
            }
            
            return result.toString();

        } catch (Exception e) {
            logger.error("Failed to search memories", e);
            return "? Failed to search memories: " + e.getMessage();
        }
    }

    private MemoryType parseMemoryType(String type) {
        try {
            return MemoryType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return MemoryType.GENERAL;
        }
    }
} 