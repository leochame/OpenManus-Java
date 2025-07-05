package com.openmanus.java.config;

import com.openmanus.java.config.VectorDatabaseConfig.EmbeddingStoreService;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmbeddingStoreServiceTest {

    @Mock
    private EmbeddingModel embeddingModel;

    @Mock
    private EmbeddingStore<Metadata> embeddingStore;

    private EmbeddingStoreService embeddingStoreService;

    @BeforeEach
    void setUp() {
        embeddingStoreService = new EmbeddingStoreService(embeddingStore, embeddingModel);
    }

    @Test
    void testStore_Success() {
        // Given
        String text = "这是一个测试文本";
        Map<String, Object> metadataMap = new HashMap<>();
        metadataMap.put("type", "FACT");
        metadataMap.put("importance", 0.8);
        Metadata metadata = Metadata.from(metadataMap);
        
        Embedding mockEmbedding = Embedding.from(new float[]{0.1f, 0.2f, 0.3f});
        Response<Embedding> mockResponse = Response.from(mockEmbedding);
        when(embeddingModel.embed(text)).thenReturn(mockResponse);
        when(embeddingStore.add(eq(mockEmbedding), eq(metadata))).thenReturn("test-id-123");
        
        // When
        String result = embeddingStoreService.store(text, metadata);
        
        // Then
        assertEquals("test-id-123", result);
        verify(embeddingModel).embed(text);
        verify(embeddingStore).add(mockEmbedding, metadata);
    }

    @Test
    void testStore_WithNullMetadata() {
        // Given
        String text = "测试文本";
        Embedding mockEmbedding = Embedding.from(new float[]{0.1f, 0.2f});
        Response<Embedding> mockResponse = Response.from(mockEmbedding);
        when(embeddingModel.embed(text)).thenReturn(mockResponse);
        when(embeddingStore.add(eq(mockEmbedding), isNull())).thenReturn("test-id-456");
        
        // When
        String result = embeddingStoreService.store(text, null);
        
        // Then
        assertEquals("test-id-456", result);
        verify(embeddingModel).embed(text);
        verify(embeddingStore).add(mockEmbedding, null);
    }

    @Test
    void testSearch_Success() {
        // Given
        String query = "搜索查询";
        int maxResults = 5;
        double minScore = 0.7;
        
        Embedding queryEmbedding = Embedding.from(new float[]{0.4f, 0.5f, 0.6f});
        Response<Embedding> mockResponse = Response.from(queryEmbedding);
        when(embeddingModel.embed(query)).thenReturn(mockResponse);
        
        // 创建模拟的搜索结果
        Map<String, Object> metadataMap1 = new HashMap<>();
        metadataMap1.put("type", "FACT");
        metadataMap1.put("importance", 0.9);
        Metadata metadata1 = Metadata.from(metadataMap1);
        
        Map<String, Object> metadataMap2 = new HashMap<>();
        metadataMap2.put("type", "PREFERENCE");
        metadataMap2.put("importance", 0.8);
        Metadata metadata2 = Metadata.from(metadataMap2);
        
        EmbeddingMatch<Metadata> match1 = new EmbeddingMatch<>(0.95, "id1", 
            Embedding.from(new float[]{0.1f, 0.2f}), metadata1);
        EmbeddingMatch<Metadata> match2 = new EmbeddingMatch<>(0.85, "id2", 
            Embedding.from(new float[]{0.3f, 0.4f}), metadata2);
        
        when(embeddingStore.findRelevant(queryEmbedding, maxResults, minScore))
            .thenReturn(Arrays.asList(match1, match2));
        
        // When
        List<EmbeddingMatch<Metadata>> results = embeddingStoreService.search(query, maxResults, minScore);
        
        // Then
        assertEquals(2, results.size());
        assertEquals(0.95, results.get(0).score(), 0.001);
        assertEquals(0.85, results.get(1).score(), 0.001);
        assertEquals("id1", results.get(0).embeddingId());
        assertEquals("id2", results.get(1).embeddingId());
        
        verify(embeddingModel).embed(query);
        verify(embeddingStore).findRelevant(queryEmbedding, maxResults, minScore);
    }

    @Test
    void testSearch_NoResults() {
        // Given
        String query = "没有结果的查询";
        int maxResults = 5;
        double minScore = 0.9;
        
        Embedding queryEmbedding = Embedding.from(new float[]{0.7f, 0.8f});
        Response<Embedding> mockResponse = Response.from(queryEmbedding);
        when(embeddingModel.embed(query)).thenReturn(mockResponse);
        when(embeddingStore.findRelevant(queryEmbedding, maxResults, minScore))
            .thenReturn(Arrays.asList());
        
        // When
        List<EmbeddingMatch<Metadata>> results = embeddingStoreService.search(query, maxResults, minScore);
        
        // Then
        assertTrue(results.isEmpty());
        verify(embeddingModel).embed(query);
        verify(embeddingStore).findRelevant(queryEmbedding, maxResults, minScore);
    }

    @Test
    void testSize() {
        // Given
        Embedding emptyEmbedding = Embedding.from(new float[]{0.0f, 0.0f});
        Response<Embedding> mockResponse = Response.from(emptyEmbedding);
        when(embeddingModel.embed("")).thenReturn(mockResponse);
        
        EmbeddingMatch<Metadata> match1 = new EmbeddingMatch<>(0.1, "id1", 
            Embedding.from(new float[]{0.1f, 0.2f}), null);
        EmbeddingMatch<Metadata> match2 = new EmbeddingMatch<>(0.05, "id2", 
            Embedding.from(new float[]{0.3f, 0.4f}), null);
        
        when(embeddingStore.findRelevant(emptyEmbedding, Integer.MAX_VALUE, 0.0))
            .thenReturn(Arrays.asList(match1, match2));
        
        // When
        int result = embeddingStoreService.size();
        
        // Then
        assertEquals(2, result);
        verify(embeddingModel).embed("");
        verify(embeddingStore).findRelevant(emptyEmbedding, Integer.MAX_VALUE, 0.0);
    }

    @Test
    void testRemoveAll() {
        // When
        embeddingStoreService.removeAll();
        
        // Then
        verify(embeddingStore).removeAll();
    }

    @Test
    void testRemove() {
        // Given
        String id = "test-id-123";
        
        // When
        embeddingStoreService.remove(id);
        
        // Then
        verify(embeddingStore).remove(id);
    }

    @Test
    void testStore_EmbeddingException() {
        // Given
        String text = "测试文本";
        Metadata metadata = Metadata.from(Map.of("type", "FACT"));
        
        when(embeddingModel.embed(text)).thenThrow(new RuntimeException("嵌入失败"));
        
        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            embeddingStoreService.store(text, metadata);
        });
        
        assertEquals("Failed to store text embedding", exception.getMessage());
        verify(embeddingModel).embed(text);
        verify(embeddingStore, never()).add(any(Embedding.class), any(Metadata.class));
    }

    @Test
    void testStore_StoreException() {
        // Given
        String text = "测试文本";
        Metadata metadata = Metadata.from(Map.of("type", "FACT"));
        
        Embedding mockEmbedding = Embedding.from(new float[]{0.1f, 0.2f});
        Response<Embedding> mockResponse = Response.from(mockEmbedding);
        when(embeddingModel.embed(text)).thenReturn(mockResponse);
        when(embeddingStore.add(eq(mockEmbedding), eq(metadata)))
            .thenThrow(new RuntimeException("存储失败"));
        
        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            embeddingStoreService.store(text, metadata);
        });
        
        assertEquals("Failed to store text embedding", exception.getMessage());
        verify(embeddingModel).embed(text);
        verify(embeddingStore).add(mockEmbedding, metadata);
    }

    @Test
    void testSearch_EmbeddingException() {
        // Given
        String query = "查询文本";
        when(embeddingModel.embed(query)).thenThrow(new RuntimeException("嵌入失败"));
        
        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            embeddingStoreService.search(query, 5, 0.7);
        });
        
        assertEquals("Failed to search text embeddings", exception.getMessage());
        verify(embeddingModel).embed(query);
        verify(embeddingStore, never()).findRelevant(any(), anyInt(), anyDouble());
    }

    @Test
    void testSearch_StoreException() {
        // Given
        String query = "查询文本";
        Embedding queryEmbedding = Embedding.from(new float[]{0.1f, 0.2f});
        Response<Embedding> mockResponse = Response.from(queryEmbedding);
        when(embeddingModel.embed(query)).thenReturn(mockResponse);
        when(embeddingStore.findRelevant(any(Embedding.class), anyInt(), anyDouble()))
            .thenThrow(new RuntimeException("搜索失败"));
        
        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            embeddingStoreService.search(query, 5, 0.7);
        });
        
        assertEquals("Failed to search text embeddings", exception.getMessage());
        verify(embeddingModel).embed(query);
        verify(embeddingStore).findRelevant(queryEmbedding, 5, 0.7);
    }

    @Test
    void testWithRealInMemoryStore() {
        // 使用真实的 InMemoryEmbeddingStore 和 SimpleEmbeddingModel 进行集成测试
        EmbeddingStore<Metadata> realStore = new InMemoryEmbeddingStore<>();
        VectorDatabaseConfig.SimpleEmbeddingModel realEmbeddingModel = 
            new VectorDatabaseConfig.SimpleEmbeddingModel();
        EmbeddingStoreService realService = new EmbeddingStoreService(realStore, realEmbeddingModel);
        
        // Given
        String text1 = "Java是一种编程语言";
        String text2 = "Python也是编程语言";
        String text3 = "苹果是一种水果";
        
        Metadata metadata1 = Metadata.from(Map.of("type", "KNOWLEDGE", "topic", "programming"));
        Metadata metadata2 = Metadata.from(Map.of("type", "KNOWLEDGE", "topic", "programming"));
        Metadata metadata3 = Metadata.from(Map.of("type", "FACT", "topic", "fruit"));
        
        // When
        String id1 = realService.store(text1, metadata1);
        String id2 = realService.store(text2, metadata2);
        String id3 = realService.store(text3, metadata3);
        
        // Then
        assertNotNull(id1);
        assertNotNull(id2);
        assertNotNull(id3);
        
        // 使用非空字符串获取size，因为SimpleEmbeddingModel不接受空字符串
        // 我们通过搜索来验证存储的数据
        List<EmbeddingMatch<Metadata>> allResults = realService.search("编程", 10, 0.0);
        
        // 应该至少找到一些结果
        assertFalse(allResults.isEmpty());
        assertTrue(allResults.size() >= 1);
        
        // 验证结果包含元数据
        for (EmbeddingMatch<Metadata> match : allResults) {
            assertNotNull(match.embedded());
            assertNotNull(match.embedded().getString("type"));
        }
        
        // 测试搜索相关内容
        List<EmbeddingMatch<Metadata>> programmingResults = realService.search("编程语言", 3, 0.0);
        
        // 应该找到一些结果（具体数量取决于相似度计算）
        assertFalse(programmingResults.isEmpty());
        assertTrue(programmingResults.size() <= 3);
        
        // 验证结果包含元数据
        for (EmbeddingMatch<Metadata> match : programmingResults) {
            assertNotNull(match.embedded());
            assertNotNull(match.embedded().getString("type"));
        }
    }
} 