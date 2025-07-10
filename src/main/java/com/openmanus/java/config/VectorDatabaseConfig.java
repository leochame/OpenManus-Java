package com.openmanus.java.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.Map;

/**
 * Vector Database Configuration
 * 
 * Configures vector store for embeddings and retrieval
 * Supports in-memory store for development and external vector databases for production
 */
@Configuration
@EnableConfigurationProperties(OpenManusProperties.class)
@DependsOn("langChain4jConfig")
public class VectorDatabaseConfig {
 
    private static final Logger logger = LoggerFactory.getLogger(VectorDatabaseConfig.class);
    
    private final OpenManusProperties properties;
    private final EmbeddingModel embeddingModel;
    
    public VectorDatabaseConfig(OpenManusProperties properties, EmbeddingModel embeddingModel) {
        this.properties = properties;
        this.embeddingModel = embeddingModel;
    }
    
    /**
     * Configure vector store bean
     * Currently supports in-memory store for development
     */
    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        try {
            logger.info("Initializing vector database configuration");
            
            // For development, use in-memory store
            // In production, configure Milvus, Pinecone, or other vector databases
            EmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();
            
            logger.info("Vector database initialized successfully");
            return store;
            
        } catch (Exception e) {
            logger.error("Failed to initialize vector database", e);
            throw new RuntimeException("Failed to initialize vector database", e);
        }
    }
    
    /**
     * Configure embedding store service
     */
    @Bean
    public EmbeddingStoreService embeddingStoreService() {
        try {
            logger.info("Initializing embedding store service");
            
            EmbeddingStore<TextSegment> store = embeddingStore();
            EmbeddingStoreService service = new EmbeddingStoreService(store, embeddingModel);
            
            logger.info("Embedding store service initialized successfully");
            return service;
            
        } catch (Exception e) {
            logger.error("Failed to initialize embedding store service", e);
            throw new RuntimeException("Failed to initialize embedding store service", e);
        }
    }
    
    /**
     * Embedding store service class
     * Provides high-level embedding storage operations
     */
    public static class EmbeddingStoreService {
        
        private final EmbeddingStore<TextSegment> embeddingStore;
        private final EmbeddingModel embeddingModel;
        private final Logger logger = LoggerFactory.getLogger(EmbeddingStoreService.class);
        
        public EmbeddingStoreService(EmbeddingStore<TextSegment> embeddingStore, EmbeddingModel embeddingModel) {
            this.embeddingStore = embeddingStore;
            this.embeddingModel = embeddingModel;
        }

        public String store(String content, Map<String, Object> metadata) {
            try {
                // 构造带元数据的 TextSegment
                TextSegment segment = TextSegment.from(content, dev.langchain4j.data.document.Metadata.from(metadata));
                // 生成 embedding
                var embedding = embeddingModel.embed(content).content();
                // 存储并返回ID
                return embeddingStore.add(embedding, segment);
            } catch (Exception e) {
                throw new RuntimeException("Failed to store content", e);
            }
        }
        public List<dev.langchain4j.store.embedding.EmbeddingMatch<TextSegment>> search(String query, int maxResults, double minRelevance) {
            try {
                var embedding = embeddingModel.embed(query).content();
                var searchRequest = dev.langchain4j.store.embedding.EmbeddingSearchRequest.builder()
                        .queryEmbedding(embedding)
                        .maxResults(maxResults)
                        .minScore(minRelevance)
                        .build();
                var searchResult = embeddingStore.search(searchRequest);
                return searchResult.matches();
            } catch (Exception e) {
                throw new RuntimeException("Failed to search for similar segments", e);
            }
        }
        /**
         * Add text segments to vector store
         */
        public void addSegments(List<TextSegment> segments) {
            try {
                logger.debug("Adding {} segments to vector store", segments.size());
                
                // Generate embeddings for segments
                List<dev.langchain4j.data.embedding.Embedding> embeddings = embeddingModel.embedAll(segments).content();
                
                // Add to store
                embeddingStore.addAll(embeddings, segments);
                
                logger.debug("Successfully added {} segments to vector store", segments.size());
                
            } catch (Exception e) {
                logger.error("Failed to add segments to vector store", e);
                throw new RuntimeException("Failed to add segments to vector store", e);
            }
        }
        
        /**
         * Search similar segments
         */
        public List<TextSegment> findSimilar(String query, int maxResults) {
            try {
                logger.debug("Searching for similar segments: query='{}', maxResults={}", query, maxResults);
                
                // Generate embedding for query
                var embedding = embeddingModel.embed(query).content();
                
                // Search for similar segments - using correct API
                var searchRequest = dev.langchain4j.store.embedding.EmbeddingSearchRequest.builder()
                        .queryEmbedding(embedding)
                        .maxResults(maxResults)
                        .minScore(0.0)
                        .build();
                
                var searchResult = embeddingStore.search(searchRequest);
                
                List<TextSegment> results = searchResult.matches().stream()
                        .map(match -> match.embedded())
                        .toList();
                
                logger.debug("Found {} similar segments", results.size());
                return results;
                
            } catch (Exception e) {
                logger.error("Failed to search for similar segments", e);
                throw new RuntimeException("Failed to search for similar segments", e);
            }
        }
    }
} 