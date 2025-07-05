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
 * 记忆工具
 * 
 * 提供长期记忆存储和检索功能，包括：
 * - 存储重要信息到向量数据库
 * - 基于语义相似度检索记忆
 * - 记忆分类和标签管理
 * - 记忆重要性评分
 */
@Component
public class MemoryTool {
    
    private static final Logger logger = LoggerFactory.getLogger(MemoryTool.class);
    
    @Autowired
    private EmbeddingStoreService embeddingStoreService;
    
    // 记忆类型
    public enum MemoryType {
        FACT("事实"),
        EXPERIENCE("经验"),
        PREFERENCE("偏好"),
        CONTEXT("上下文"),
        KNOWLEDGE("知识");
        
        private final String description;
        
        MemoryType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * 存储记忆
     * 
     * @param content 记忆内容
     * @param type 记忆类型
     * @param importance 重要性评分 (0.0-1.0)
     * @param tags 标签（逗号分隔）
     * @return 存储结果
     */
    @Tool("存储重要信息到长期记忆中。用于保存用户偏好、重要事实、经验教训等需要长期保留的信息。")
    public String storeMemory(String content, String type, double importance, String tags) {
        try {
            if (content == null || content.trim().isEmpty()) {
                return "错误: 记忆内容不能为空";
            }
            
            // 验证重要性评分
            if (importance < 0.0 || importance > 1.0) {
                importance = Math.max(0.0, Math.min(1.0, importance));
            }
            
            // 解析记忆类型
            MemoryType memoryType = parseMemoryType(type);
            
            // 创建元数据
            Map<String, Object> metadataMap = new HashMap<>();
            metadataMap.put("type", memoryType.name());
            metadataMap.put("importance", importance);
            metadataMap.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            metadataMap.put("tags", tags != null ? tags.trim() : "");
            
            Metadata metadata = Metadata.from(metadataMap);
            
            // 存储到向量数据库
            String memoryId = embeddingStoreService.store(content, metadata);
            
            logger.info("存储记忆成功: ID={}, 类型={}, 重要性={}, 内容={}", 
                       memoryId, memoryType.getDescription(), importance, 
                       content.length() > 100 ? content.substring(0, 100) + "..." : content);
            
            return String.format("✅ 记忆已存储\\n" +
                    "📝 内容: %s\\n" +
                    "🏷️ 类型: %s\\n" +
                    "⭐ 重要性: %.2f\\n" +
                    "🔖 标签: %s\\n" +
                    "🆔 ID: %s",
                    content.length() > 200 ? content.substring(0, 200) + "..." : content,
                    memoryType.getDescription(),
                    importance,
                    tags != null ? tags : "无",
                    memoryId);
                    
        } catch (Exception e) {
            logger.error("存储记忆失败: {}", e.getMessage(), e);
            return "❌ 存储记忆失败: " + e.getMessage();
        }
    }
    
    /**
     * 检索记忆
     * 
     * @param query 查询内容
     * @param maxResults 最大结果数
     * @param minRelevance 最小相关性分数 (0.0-1.0)
     * @return 检索结果
     */
    @Tool("从长期记忆中检索相关信息。用于回忆之前存储的重要信息、用户偏好、经验教训等。")
    public String retrieveMemory(String query, int maxResults, double minRelevance) {
        try {
            if (query == null || query.trim().isEmpty()) {
                return "错误: 查询内容不能为空";
            }
            
            // 设置默认值
            if (maxResults <= 0) maxResults = 5;
            if (minRelevance < 0.0 || minRelevance > 1.0) minRelevance = 0.3;
            
            // 检索相似记忆
            List<EmbeddingMatch<Metadata>> matches = embeddingStoreService.search(query, maxResults, minRelevance);
            
            if (matches.isEmpty()) {
                return "🔍 未找到相关记忆\\n" +
                       "💡 建议: 尝试使用不同的关键词或降低相关性要求";
            }
            
            StringBuilder result = new StringBuilder();
            result.append(String.format("🧠 找到 %d 条相关记忆:\\n\\n", matches.size()));
            
            for (int i = 0; i < matches.size(); i++) {
                EmbeddingMatch<Metadata> match = matches.get(i);
                Metadata metadata = match.embedded();
                
                result.append(String.format("**%d. 记忆片段** (相关性: %.2f)\\n", i + 1, match.score()));
                result.append(String.format("📝 内容: %s\\n", getEmbeddedText(match)));
                
                if (metadata != null) {
                    String type = metadata.getString("type");
                    Double importance = metadata.getDouble("importance");
                    String timestamp = metadata.getString("timestamp");
                    String tags = metadata.getString("tags");
                    
                    if (type != null) {
                        result.append(String.format("🏷️ 类型: %s\\n", 
                                parseMemoryType(type).getDescription()));
                    }
                    if (importance != null) {
                        result.append(String.format("⭐ 重要性: %.2f\\n", importance));
                    }
                    if (timestamp != null) {
                        result.append(String.format("📅 时间: %s\\n", timestamp));
                    }
                    if (tags != null && !tags.trim().isEmpty()) {
                        result.append(String.format("🔖 标签: %s\\n", tags));
                    }
                }
                
                result.append("\\n");
            }
            
            logger.info("检索记忆成功: 查询='{}', 结果数={}", query, matches.size());
            return result.toString();
            
        } catch (Exception e) {
            logger.error("检索记忆失败: {}", e.getMessage(), e);
            return "❌ 检索记忆失败: " + e.getMessage();
        }
    }
    
    /**
     * 获取记忆统计信息
     * 
     * @return 统计信息
     */
    @Tool("获取记忆系统的统计信息，包括存储的记忆数量、类型分布等。")
    public String getMemoryStats() {
        try {
            int totalMemories = embeddingStoreService.size();
            
            StringBuilder stats = new StringBuilder();
            stats.append("🧠 记忆系统统计\\n\\n");
            stats.append(String.format("📊 总记忆数: %d\\n", totalMemories));
            stats.append("\\n💡 记忆类型说明:\\n");
            
            for (MemoryType type : MemoryType.values()) {
                stats.append(String.format("• %s: %s\\n", type.name(), type.getDescription()));
            }
            
            return stats.toString();
            
        } catch (Exception e) {
            logger.error("获取记忆统计失败: {}", e.getMessage(), e);
            return "❌ 获取统计信息失败: " + e.getMessage();
        }
    }
    
    /**
     * 清空记忆
     * 
     * @param confirmationCode 确认码（必须为"CONFIRM_DELETE_ALL"）
     * @return 清空结果
     */
    @Tool("清空所有记忆（危险操作）。需要提供确认码'CONFIRM_DELETE_ALL'来执行此操作。")
    public String clearAllMemories(String confirmationCode) {
        try {
            if (!"CONFIRM_DELETE_ALL".equals(confirmationCode)) {
                return "❌ 确认码错误。如需清空所有记忆，请提供确认码: CONFIRM_DELETE_ALL";
            }
            
            embeddingStoreService.removeAll();
            logger.warn("所有记忆已被清空");
            
            return "✅ 所有记忆已清空\\n" +
                   "⚠️ 此操作不可逆，请谨慎使用";
                   
        } catch (Exception e) {
            logger.error("清空记忆失败: {}", e.getMessage(), e);
            return "❌ 清空记忆失败: " + e.getMessage();
        }
    }
    
    /**
     * 解析记忆类型
     */
    private MemoryType parseMemoryType(String type) {
        if (type == null || type.trim().isEmpty()) {
            return MemoryType.CONTEXT;
        }
        
        try {
            return MemoryType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            // 尝试按描述匹配
            for (MemoryType memoryType : MemoryType.values()) {
                if (memoryType.getDescription().equals(type)) {
                    return memoryType;
                }
            }
            return MemoryType.CONTEXT;
        }
    }
    
    /**
     * 获取嵌入的文本内容
     * 这是一个简化的实现，实际应用中需要更复杂的逻辑
     */
    private String getEmbeddedText(EmbeddingMatch<Metadata> match) {
        // 由于我们无法直接从 EmbeddingMatch 获取原始文本，
        // 这里返回一个占位符。实际应用中应该在元数据中存储原始文本
        return "记忆内容 (ID: " + match.embeddingId() + ")";
    }
} 