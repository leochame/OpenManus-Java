package com.openmanus.java.memory;

import com.openmanus.java.config.VectorDatabaseConfig.EmbeddingStoreService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MemoryToolTest {

    @Mock
    private EmbeddingStoreService embeddingStoreService;

    private MemoryTool memoryTool;

    @BeforeEach
    void setUp() throws Exception {
        // 创建 MemoryTool 实例
        memoryTool = new MemoryTool();
        
        // 使用反射注入 embeddingStoreService
        Field field = MemoryTool.class.getDeclaredField("embeddingStoreService");
        field.setAccessible(true);
        field.set(memoryTool, embeddingStoreService);
    }

    @Test
    void testStoreMemory_Success() {
        // Given
        String content = "用户喜欢使用Python进行数据分析";
        String type = "PREFERENCE";
        double importance = 0.8;
        String tags = "programming,python";
        
        when(embeddingStoreService.store(anyString(), any(Metadata.class))).thenReturn("memory-id-123");
        
        // When
        String result = memoryTool.storeMemory(content, type, importance, tags);
        
        // Then
        assertTrue(result.contains("✅ 记忆已存储"));
        assertTrue(result.contains("memory-id-123"));
        verify(embeddingStoreService).store(eq(content), any(Metadata.class));
    }

    @Test
    void testStoreMemory_WithDefaultValues() {
        // Given
        String content = "这是一个重要的事实";
        String type = null;
        double importance = 0.5;
        String tags = null;
        
        when(embeddingStoreService.store(anyString(), any(Metadata.class))).thenReturn("memory-id-456");
        
        // When
        String result = memoryTool.storeMemory(content, type, importance, tags);
        
        // Then
        assertTrue(result.contains("✅ 记忆已存储"));
        verify(embeddingStoreService).store(eq(content), any(Metadata.class));
    }

    @Test
    void testRetrieveMemory_Success() {
        // Given
        String query = "Python编程相关的记忆";
        int maxResults = 5;
        double minRelevance = 0.3;
        
        // 创建模拟的元数据
        Map<String, Object> metadataMap = new HashMap<>();
        metadataMap.put("type", "PREFERENCE");
        metadataMap.put("importance", 0.8);
        metadataMap.put("timestamp", "2023-12-25T10:30:00");
        metadataMap.put("tags", "programming,python");
        Metadata metadata = Metadata.from(metadataMap);
        
        EmbeddingMatch<Metadata> match1 = new EmbeddingMatch<>(0.9, "id1", 
            Embedding.from(new float[]{0.1f, 0.2f}), metadata);
        EmbeddingMatch<Metadata> match2 = new EmbeddingMatch<>(0.8, "id2", 
            Embedding.from(new float[]{0.3f, 0.4f}), metadata);
        
        when(embeddingStoreService.search(eq(query), eq(maxResults), eq(minRelevance)))
            .thenReturn(Arrays.asList(match1, match2));
        
        // When
        String result = memoryTool.retrieveMemory(query, maxResults, minRelevance);
        
        // Then
        assertTrue(result.contains("🧠 找到 2 条相关记忆"));
        assertTrue(result.contains("相关性: 0.9"));
        assertTrue(result.contains("相关性: 0.8"));
        assertTrue(result.contains("偏好"));
    }

    @Test
    void testRetrieveMemory_NoResults() {
        // Given
        String query = "不存在的记忆";
        int maxResults = 5;
        double minRelevance = 0.3;
        
        when(embeddingStoreService.search(eq(query), eq(maxResults), eq(minRelevance)))
            .thenReturn(Arrays.asList());
        
        // When
        String result = memoryTool.retrieveMemory(query, maxResults, minRelevance);
        
        // Then
        assertTrue(result.contains("🔍 未找到相关记忆"));
    }

    @Test
    void testRetrieveMemory_WithDefaultParameters() {
        // Given
        String query = "测试查询";
        int maxResults = 0; // 将被设置为默认值5
        double minRelevance = -0.1; // 将被设置为默认值0.3
        
        when(embeddingStoreService.search(eq(query), eq(5), eq(0.3)))
            .thenReturn(Arrays.asList());
        
        // When
        memoryTool.retrieveMemory(query, maxResults, minRelevance);
        
        // Then
        verify(embeddingStoreService).search(query, 5, 0.3);
    }

    @Test
    void testGetMemoryStats() {
        // Given
        when(embeddingStoreService.size()).thenReturn(10);
        
        // When
        String result = memoryTool.getMemoryStats();
        
        // Then
        assertTrue(result.contains("🧠 记忆系统统计"));
        assertTrue(result.contains("📊 总记忆数: 10"));
        assertTrue(result.contains("事实"));
        assertTrue(result.contains("经验"));
        assertTrue(result.contains("偏好"));
    }

    @Test
    void testClearAllMemories_Success() {
        // Given
        String confirmationCode = "CONFIRM_DELETE_ALL";
        
        // When
        String result = memoryTool.clearAllMemories(confirmationCode);
        
        // Then
        assertTrue(result.contains("✅ 所有记忆已清空"));
        verify(embeddingStoreService).removeAll();
    }

    @Test
    void testClearAllMemories_WrongConfirmationCode() {
        // Given
        String wrongCode = "WRONG_CODE";
        
        // When
        String result = memoryTool.clearAllMemories(wrongCode);
        
        // Then
        assertTrue(result.contains("❌ 确认码错误"));
        verify(embeddingStoreService, never()).removeAll();
    }

    @Test
    void testStoreMemory_EmptyContent() {
        // Given
        String content = "";
        String type = "FACT";
        double importance = 0.5;
        String tags = "test";
        
        // When
        String result = memoryTool.storeMemory(content, type, importance, tags);
        
        // Then
        assertTrue(result.contains("错误: 记忆内容不能为空"));
        verify(embeddingStoreService, never()).store(anyString(), any(Metadata.class));
    }

    @Test
    void testRetrieveMemory_EmptyQuery() {
        // Given
        String query = "";
        int maxResults = 5;
        double minRelevance = 0.3;
        
        // When
        String result = memoryTool.retrieveMemory(query, maxResults, minRelevance);
        
        // Then
        assertTrue(result.contains("错误: 查询内容不能为空"));
        verify(embeddingStoreService, never()).search(anyString(), anyInt(), anyDouble());
    }

    @Test
    void testStoreMemory_ExceptionHandling() {
        // Given
        String content = "测试内容";
        String type = "FACT";
        double importance = 0.5;
        String tags = "test";
        
        when(embeddingStoreService.store(anyString(), any(Metadata.class)))
            .thenThrow(new RuntimeException("存储失败"));
        
        // When
        String result = memoryTool.storeMemory(content, type, importance, tags);
        
        // Then
        assertTrue(result.contains("❌ 存储记忆失败"));
        assertTrue(result.contains("存储失败"));
    }

    @Test
    void testRetrieveMemory_ExceptionHandling() {
        // Given
        String query = "测试查询";
        int maxResults = 5;
        double minRelevance = 0.3;
        
        when(embeddingStoreService.search(anyString(), anyInt(), anyDouble()))
            .thenThrow(new RuntimeException("检索失败"));
        
        // When
        String result = memoryTool.retrieveMemory(query, maxResults, minRelevance);
        
        // Then
        assertTrue(result.contains("❌ 检索记忆失败"));
        assertTrue(result.contains("检索失败"));
    }

    @Test
    void testImportanceValidation() {
        // Given
        String content = "测试内容";
        String type = "FACT";
        double invalidImportance = 1.5; // 超出范围
        String tags = "test";
        
        when(embeddingStoreService.store(anyString(), any(Metadata.class))).thenReturn("memory-id");
        
        // When
        String result = memoryTool.storeMemory(content, type, invalidImportance, tags);
        
        // Then
        assertTrue(result.contains("✅ 记忆已存储"));
        // 验证重要性被限制在有效范围内
        verify(embeddingStoreService).store(eq(content), argThat(metadata -> {
            Double importance = metadata.getDouble("importance");
            return importance != null && importance >= 0.0 && importance <= 1.0;
        }));
    }
} 