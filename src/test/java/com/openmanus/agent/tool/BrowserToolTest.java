package com.openmanus.agent.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * BrowserTool测试类
 * 用于验证搜索功能的修复效果
 */
public class BrowserToolTest {
    
    private static final Logger logger = LoggerFactory.getLogger(BrowserToolTest.class);
    private BrowserTool browserTool;
    
    @BeforeEach
    void setUp() {
        browserTool = new BrowserTool();
    }
    
    @Test
    void testSearchWeb_SimpleQuery() {
        logger.info("测试简单搜索查询...");
        
        String query = "Java programming";
        String result = browserTool.searchWeb(query);
        
        logger.info("搜索查询: {}", query);
        logger.info("搜索结果长度: {}", result.length());
        logger.info("搜索结果预览: {}", result.substring(0, Math.min(200, result.length())));
        
        // 验证结果不为空且不是错误信息
        assert result != null;
        assert !result.isEmpty();
        assert !result.startsWith("搜索失败");
    }
    
    @Test
    void testSearchWeb_ChineseQuery() {
        logger.info("测试中文搜索查询...");
        
        String query = "人工智能";
        String result = browserTool.searchWeb(query);
        
        logger.info("搜索查询: {}", query);
        logger.info("搜索结果长度: {}", result.length());
        logger.info("搜索结果预览: {}", result.substring(0, Math.min(200, result.length())));
        
        // 验证结果
        assert result != null;
        assert !result.isEmpty();
        assert !result.startsWith("搜索失败");
    }
    
    @Test
    void testSearchWeb_TechnicalQuery() {
        logger.info("测试技术相关搜索查询...");
        
        String query = "Spring Boot tutorial";
        String result = browserTool.searchWeb(query);
        
        logger.info("搜索查询: {}", query);
        logger.info("搜索结果长度: {}", result.length());
        logger.info("搜索结果预览: {}", result.substring(0, Math.min(200, result.length())));
        
        // 验证结果
        assert result != null;
        assert !result.isEmpty();
        assert !result.startsWith("搜索失败");
    }
    
    @Test
    void testBrowseWeb_SimpleUrl() {
        logger.info("测试网页访问功能...");
        
        String url = "https://httpbin.org/get";
        String result = browserTool.browseWeb(url);
        
        logger.info("访问URL: {}", url);
        logger.info("访问结果长度: {}", result.length());
        logger.info("访问结果预览: {}", result.substring(0, Math.min(200, result.length())));
        
        // 验证结果
        assert result != null;
        assert !result.isEmpty();
        assert !result.startsWith("Failed to access web page");
    }
    
    /**
     * 手动测试方法 - 可以在IDE中直接运行
     */
    public static void main(String[] args) {
        BrowserTool tool = new BrowserTool();
        
        System.out.println("=== BrowserTool 搜索功能测试 ===\n");
        
        // 测试1: 简单英文搜索
        System.out.println("测试1: 搜索 'Java programming'");
        String result1 = tool.searchWeb("Java programming");
        System.out.println("结果长度: " + result1.length());
        System.out.println("结果预览: " + result1.substring(0, Math.min(300, result1.length())));
        System.out.println("\n" + "=".repeat(50) + "\n");
        
        // 测试2: 中文搜索
        System.out.println("测试2: 搜索 '人工智能'");
        String result2 = tool.searchWeb("人工智能");
        System.out.println("结果长度: " + result2.length());
        System.out.println("结果预览: " + result2.substring(0, Math.min(300, result2.length())));
        System.out.println("\n" + "=".repeat(50) + "\n");
        
        // 测试3: 技术搜索
        System.out.println("测试3: 搜索 'Spring Boot REST API'");
        String result3 = tool.searchWeb("Spring Boot REST API");
        System.out.println("结果长度: " + result3.length());
        System.out.println("结果预览: " + result3.substring(0, Math.min(300, result3.length())));
        System.out.println("\n" + "=".repeat(50) + "\n");
        
        System.out.println("测试完成！");
    }
}
