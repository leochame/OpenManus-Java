package com.openmanus.java.config;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;

import com.openmanus.java.config.OpenManusProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Arrays;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.data.segment.TextSegment;

/**
 * 向量数据库配置类
 * 
 * 这个类负责配置和创建向量数据库相关的组件，
 * 包括嵌入模型和嵌入存储。
 */
@Configuration
public class VectorDatabaseConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(VectorDatabaseConfig.class);
    
    @Autowired
    private OpenManusProperties properties;
    
    /**
     * 创建嵌入模型 Bean
     * 
     * @return 配置好的嵌入模型实例
     */
    @Bean
    public EmbeddingModel embeddingModel() {
        logger.info("初始化嵌入模型: 使用简单实现");
        
        // 使用简单的嵌入模型实现
        return new SimpleEmbeddingModel();
    }
    
    /**
     * 创建嵌入存储 Bean
     * 
     * @param embeddingModel 嵌入模型
     * @return 配置好的嵌入存储实例
     */
    @Bean
    public EmbeddingStore<Metadata> embeddingStore(EmbeddingModel embeddingModel) {
        OpenManusProperties.VectorDatabaseSettings vectorDbConfig = properties.getVectorDatabase();
        
        if (vectorDbConfig == null || !vectorDbConfig.isEnabled()) {
            logger.warn("向量数据库未启用，使用内存存储");
            return createInMemoryStore();
        }
        
        // 暂时使用内存存储，后续可以扩展到 Milvus
        logger.info("使用内存向量存储");
        return createInMemoryStore();
    }
    
    /**
     * 创建内存嵌入存储
     */
    private EmbeddingStore<Metadata> createInMemoryStore() {
        return new InMemoryEmbeddingStore<>();
    }
    
    /**
     * 创建嵌入存储服务 Bean
     * 
     * @param embeddingStore 嵌入存储
     * @param embeddingModel 嵌入模型
     * @return 嵌入存储服务实例
     */
    @Bean
    public EmbeddingStoreService embeddingStoreService(
            EmbeddingStore<Metadata> embeddingStore,
            EmbeddingModel embeddingModel) {
        return new EmbeddingStoreService(embeddingStore, embeddingModel);
    }
    
    /**
     * 嵌入存储服务类
     * 
     * 提供高级的嵌入存储操作
     */
    public static class EmbeddingStoreService {
        
        private final EmbeddingStore<Metadata> embeddingStore;
        private final EmbeddingModel embeddingModel;
        private final Logger logger = LoggerFactory.getLogger(EmbeddingStoreService.class);
        
        public EmbeddingStoreService(
                EmbeddingStore<Metadata> embeddingStore,
                EmbeddingModel embeddingModel) {
            this.embeddingStore = embeddingStore;
            this.embeddingModel = embeddingModel;
        }
        
        /**
         * 存储文本及其元数据
         * 
         * @param text 要存储的文本
         * @param metadata 关联的元数据
         * @return 存储的 ID
         */
        public String store(String text, Metadata metadata) {
            try {
                Embedding embedding = embeddingModel.embed(text).content();
                String id = embeddingStore.add(embedding, metadata);
                logger.debug("存储文本嵌入成功: ID={}, 文本长度={}", id, text.length());
                return id;
            } catch (Exception e) {
                logger.error("存储文本嵌入失败: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to store text embedding", e);
            }
        }
        
        /**
         * 搜索相似文本
         * 
         * @param query 查询文本
         * @param maxResults 最大结果数
         * @param minScore 最小相似度分数
         * @return 搜索结果列表
         */
        public List<EmbeddingMatch<Metadata>> search(
                String query, int maxResults, double minScore) {
            try {
                Embedding queryEmbedding = embeddingModel.embed(query).content();
                EmbeddingSearchResult<Metadata> searchResult = 
                    embeddingStore.search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .maxResults(maxResults)
                        .minScore(minScore)
                        .build());
                List<EmbeddingMatch<Metadata>> matches = searchResult.matches();
                
                logger.debug("搜索完成: 查询='{}', 结果数={}", query, matches.size());
                return matches;
                
            } catch (Exception e) {
                logger.error("搜索文本嵌入失败: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to search text embeddings", e);
            }
        }
        
        /**
         * 删除指定 ID 的嵌入
         * 
         * @param id 要删除的 ID
         */
        public void remove(String id) {
            try {
                embeddingStore.remove(id);
                logger.debug("删除嵌入成功: ID={}", id);
            } catch (Exception e) {
                logger.error("删除嵌入失败: ID={}, 错误: {}", id, e.getMessage(), e);
                throw new RuntimeException("Failed to remove embedding", e);
            }
        }
        
        /**
         * 清空所有嵌入
         */
        public void removeAll() {
            try {
                embeddingStore.removeAll();
                logger.info("清空所有嵌入成功");
            } catch (Exception e) {
                logger.error("清空嵌入失败: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to remove all embeddings", e);
            }
        }
        
        /**
         * 获取存储的嵌入数量
         */
        public int size() {
            try {
                // InMemoryEmbeddingStore 没有直接的 size 方法，我们通过搜索来估算
                return embeddingStore.search(EmbeddingSearchRequest.builder()
                    .queryEmbedding(embeddingModel.embed("").content())
                    .maxResults(Integer.MAX_VALUE)
                    .minScore(0.0)
                    .build()
                ).matches().size();
            } catch (Exception e) {
                logger.warn("无法获取嵌入存储大小: {}", e.getMessage());
                return 0;
            }
        }
    }
    
    /**
     * 简单的嵌入模型实现，用于演示
     */
    public static class SimpleEmbeddingModel implements EmbeddingModel {
        
        @Override
        public Response<Embedding> embed(String text) {
            return embed(TextSegment.from(text));
        }
        
        @Override
        public Response<Embedding> embed(TextSegment textSegment) {
            String text = textSegment.text();
            // 创建一个简单的嵌入向量（实际应用中应该使用真实的模型）
            float[] vector = new float[384]; // 384维向量
            
            // 简单的文本哈希作为嵌入
            int hash = text.hashCode();
            for (int i = 0; i < vector.length; i++) {
                vector[i] = (float) Math.sin(hash * (i + 1) * 0.01);
            }
            
            Embedding embedding = Embedding.from(vector);
            return Response.from(embedding);
        }
        
        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
            List<Embedding> embeddings = textSegments.stream()
                .map(segment -> embed(segment).content())
                .toList();
            return Response.from(embeddings);
        }
    }
} 