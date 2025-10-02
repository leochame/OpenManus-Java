package com.openmanus.infra.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 向量数据库配置类
 * 
 * 配置向量存储用于文本嵌入和检索
 * 开发环境使用内存存储，生产环境可配置Milvus、Pinecone等外部向量数据库
 */
@Configuration
@EnableConfigurationProperties(OpenManusProperties.class)
@Slf4j
public class VectorDatabaseConfig {
    
    /**
     * 配置向量存储Bean
     * 当前使用内存存储用于开发
     * 生产环境可替换为持久化向量数据库
     */
    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        log.info("初始化向量数据库 - 使用内存存储");
        return new InMemoryEmbeddingStore<>();
    }
} 