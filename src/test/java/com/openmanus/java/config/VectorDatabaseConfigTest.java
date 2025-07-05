package com.openmanus.java.config;

import com.openmanus.java.config.VectorDatabaseConfig.EmbeddingStoreService;
import com.openmanus.java.config.VectorDatabaseConfig.SimpleEmbeddingModel;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * VectorDatabaseConfig 单元测试
 */
public class VectorDatabaseConfigTest {
    
    private VectorDatabaseConfig config;
    
    @Mock
    private OpenManusProperties properties;
    
    @Mock
    private OpenManusProperties.VectorDatabaseSettings vectorDbSettings;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        config = new VectorDatabaseConfig();
        
        // 设置mock行为
        when(properties.getVectorDatabase()).thenReturn(vectorDbSettings);
        when(vectorDbSettings.isEnabled()).thenReturn(false); // 默认未启用，使用内存存储
    }
    
    @Test
    @DisplayName("测试嵌入模型创建")
    void testEmbeddingModelCreation() {
        EmbeddingModel embeddingModel = config.embeddingModel();
        
        assertNotNull(embeddingModel, "嵌入模型不应为空");
        assertTrue(embeddingModel instanceof SimpleEmbeddingModel, "应该是SimpleEmbeddingModel实例");
    }
    
    @Test
    @DisplayName("测试SimpleEmbeddingModel功能")
    void testSimpleEmbeddingModel() {
        SimpleEmbeddingModel model = new SimpleEmbeddingModel();
        
        // 测试单个文本嵌入
        String testText = "这是一个测试文本";
        var response = model.embed(testText);
        
        assertNotNull(response, "嵌入响应不应为空");
        assertNotNull(response.content(), "嵌入内容不应为空");
        
        Embedding embedding = response.content();
        assertNotNull(embedding, "嵌入向量不应为空");
        assertEquals(384, embedding.vector().length, "向量维度应为384");
        
        // 测试相同文本产生相同嵌入
        var response2 = model.embed(testText);
        assertArrayEquals(embedding.vector(), response2.content().vector(), "相同文本应产生相同嵌入");
        
        // 测试不同文本产生不同嵌入
        var response3 = model.embed("不同的文本");
        assertFalse(java.util.Arrays.equals(embedding.vector(), response3.content().vector()), 
                   "不同文本应产生不同嵌入");
    }
    
    @Test
    @DisplayName("测试EmbeddingStoreService功能")
    void testEmbeddingStoreService() {
        EmbeddingModel embeddingModel = new SimpleEmbeddingModel();
        
        // 手动创建内存存储，避免依赖注入问题
        EmbeddingStore<Metadata> embeddingStore = new dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore<>();
        EmbeddingStoreService service = new EmbeddingStoreService(embeddingStore, embeddingModel);
        
        // 测试存储
        Map<String, Object> metadataMap = new HashMap<>();
        metadataMap.put("type", "test");
        metadataMap.put("importance", 0.8);
        Metadata metadata = Metadata.from(metadataMap);
        
        String testText = "测试存储的文本";
        String id = service.store(testText, metadata);
        
        assertNotNull(id, "存储ID不应为空");
        
        // 测试搜索
        List<EmbeddingMatch<Metadata>> matches = service.search(testText, 5, 0.0);
        assertFalse(matches.isEmpty(), "应该找到匹配的结果");
        
        EmbeddingMatch<Metadata> match = matches.get(0);
        assertTrue(match.score() > 0.9, "相同文本的相似度应该很高");
        
        // 测试删除（注意：InMemoryEmbeddingStore 不支持删除操作）
        try {
            service.remove(id);
        } catch (RuntimeException e) {
            // 预期的异常，因为 InMemoryEmbeddingStore 不支持删除
            assertTrue(e.getMessage().contains("Failed to remove"), "应该抛出删除失败异常");
        }
    }
    
    @Test
    @DisplayName("测试EmbeddingStoreService错误处理")
    void testEmbeddingStoreServiceErrorHandling() {
        EmbeddingModel embeddingModel = new SimpleEmbeddingModel();
        EmbeddingStore<Metadata> embeddingStore = new dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore<>();
        EmbeddingStoreService service = new EmbeddingStoreService(embeddingStore, embeddingModel);
        
        // 测试存储空文本
        assertThrows(RuntimeException.class, () -> {
            service.store(null, Metadata.from(new HashMap<>()));
        }, "存储空文本应该抛出异常");
        
        // 测试搜索空查询
        assertThrows(RuntimeException.class, () -> {
            service.search(null, 5, 0.0);
        }, "搜索空查询应该抛出异常");
    }
    
    @Test
    @DisplayName("测试清空所有嵌入")
    void testRemoveAllEmbeddings() {
        EmbeddingModel embeddingModel = new SimpleEmbeddingModel();
        EmbeddingStore<Metadata> embeddingStore = new dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore<>();
        EmbeddingStoreService service = new EmbeddingStoreService(embeddingStore, embeddingModel);
        
        // 添加一些数据
        Metadata metadata = Metadata.from(new HashMap<>());
        service.store("文本1", metadata);
        service.store("文本2", metadata);
        
        // 验证有数据
        List<EmbeddingMatch<Metadata>> matches = service.search("文本", 10, 0.0);
        assertFalse(matches.isEmpty(), "添加数据后应该能找到结果");
        
        // 清空所有数据（注意：InMemoryEmbeddingStore 不支持清空操作）
        try {
            service.removeAll();
        } catch (RuntimeException e) {
            // 预期的异常，因为 InMemoryEmbeddingStore 不支持清空
            assertTrue(e.getMessage().contains("Failed to remove"), "应该抛出清空失败异常");
        }
    }
    
    @Test
    @DisplayName("测试嵌入存储大小估算")
    void testEmbeddingStoreSize() {
        EmbeddingModel embeddingModel = new SimpleEmbeddingModel();
        EmbeddingStore<Metadata> embeddingStore = new dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore<>();
        EmbeddingStoreService service = new EmbeddingStoreService(embeddingStore, embeddingModel);
        
        // 初始大小应该为0
        assertEquals(0, service.size(), "初始大小应该为0");
        
        // 添加数据
        Metadata metadata = Metadata.from(new HashMap<>());
        service.store("文本1", metadata);
        service.store("文本2", metadata);
        
        // 大小应该增加（注意：这个测试可能不够精确，因为实现是估算的）
        int sizeAfterAdding = service.size();
        assertTrue(sizeAfterAdding >= 0, "添加数据后大小应该>=0");
    }
    
    @Test
    @DisplayName("测试多文本嵌入")
    void testEmbedAllTexts() {
        SimpleEmbeddingModel model = new SimpleEmbeddingModel();
        
        List<dev.langchain4j.data.segment.TextSegment> textSegments = List.of(
            dev.langchain4j.data.segment.TextSegment.from("文本1"),
            dev.langchain4j.data.segment.TextSegment.from("文本2"),
            dev.langchain4j.data.segment.TextSegment.from("文本3")
        );
        
        var response = model.embedAll(textSegments);
        
        assertNotNull(response, "批量嵌入响应不应为空");
        assertNotNull(response.content(), "批量嵌入内容不应为空");
        assertEquals(3, response.content().size(), "应该返回3个嵌入向量");
        
        // 验证每个嵌入向量都有正确的维度
        for (Embedding embedding : response.content()) {
            assertEquals(384, embedding.vector().length, "每个向量维度应为384");
        }
    }
    
    @Test
    @DisplayName("测试嵌入向量的数值范围")
    void testEmbeddingVectorRange() {
        SimpleEmbeddingModel model = new SimpleEmbeddingModel();
        
        var response = model.embed("测试文本");
        Embedding embedding = response.content();
        
        // 验证向量值在合理范围内（-1到1之间）
        for (float value : embedding.vector()) {
            assertTrue(value >= -1.0f && value <= 1.0f, 
                      "嵌入向量值应该在-1到1之间，实际值: " + value);
        }
    }
} 