package com.openmanus.domain.service;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 向量存储服务
 * 
 * 提供文本嵌入的存储、检索和相似度搜索功能
 * 采用依赖注入模式，便于测试和扩展
 */
@Service
@Slf4j
public class EmbeddingStoreService {
    
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    
    public EmbeddingStoreService(EmbeddingStore<TextSegment> embeddingStore, EmbeddingModel embeddingModel) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
    }

    /**
     * 存储文本内容及其元数据
     * 
     * @param content 文本内容
     * @param metadata 元数据
     * @return 存储ID
     */
    public String store(String content, Map<String, Object> metadata) {
        try {
            TextSegment segment = TextSegment.from(content, Metadata.from(metadata));
            Embedding embedding = embeddingModel.embed(content).content();
            return embeddingStore.add(embedding, segment);
        } catch (Exception e) {
            log.error("存储内容失败: {}", e.getMessage(), e);
            throw new RuntimeException("存储内容失败", e);
        }
    }

    /**
     * 搜索相似文本
     * 
     * @param query 查询文本
     * @param maxResults 最大结果数
     * @param minRelevance 最小相关度分数
     * @return 匹配结果列表
     */
    public List<EmbeddingMatch<TextSegment>> search(String query, int maxResults, double minRelevance) {
        try {
            Embedding embedding = embeddingModel.embed(query).content();
            EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(embedding)
                    .maxResults(maxResults)
                    .minScore(minRelevance)
                    .build();
            return embeddingStore.search(searchRequest).matches();
        } catch (Exception e) {
            log.error("搜索相似文本失败: {}", e.getMessage(), e);
            throw new RuntimeException("搜索相似文本失败", e);
        }
    }

    /**
     * 批量添加文本片段
     * 
     * @param segments 文本片段列表
     */
    public void addSegments(List<TextSegment> segments) {
        try {
            log.debug("添加 {} 个文本片段到向量存储", segments.size());
            List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
            embeddingStore.addAll(embeddings, segments);
            log.debug("成功添加 {} 个文本片段", segments.size());
        } catch (Exception e) {
            log.error("添加文本片段失败: {}", e.getMessage(), e);
            throw new RuntimeException("添加文本片段失败", e);
        }
    }
    
    /**
     * 查找相似文本片段
     * 
     * @param query 查询文本
     * @param maxResults 最大结果数
     * @return 相似文本片段列表
     */
    public List<TextSegment> findSimilar(String query, int maxResults) {
        try {
            log.debug("搜索相似文本片段: query='{}', maxResults={}", query, maxResults);
            
            Embedding embedding = embeddingModel.embed(query).content();
            EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(embedding)
                    .maxResults(maxResults)
                    .minScore(0.0)
                    .build();
            
            List<TextSegment> results = embeddingStore.search(searchRequest).matches().stream()
                    .map(EmbeddingMatch::embedded)
                    .toList();
            
            log.debug("找到 {} 个相似文本片段", results.size());
            return results;
        } catch (Exception e) {
            log.error("搜索相似文本片段失败: {}", e.getMessage(), e);
            throw new RuntimeException("搜索相似文本片段失败", e);
        }
    }
}

