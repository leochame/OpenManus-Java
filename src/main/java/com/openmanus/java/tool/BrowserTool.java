package com.openmanus.java.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 浏览器工具
 * 使用 langchain4j 的 @Tool 注解
 */
@Component
public class BrowserTool {

    private static final Logger logger = LoggerFactory.getLogger(BrowserTool.class);
    private static final int DEFAULT_TIMEOUT = 30; // 30秒超时
    
    @Tool("访问网页并获取内容")
    public String browseWeb(@P("网页URL") String url) {
        try {
            // 验证URL格式
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://" + url;
            }
            
            URL targetUrl = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) targetUrl.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(DEFAULT_TIMEOUT * 1000);
            connection.setReadTimeout(DEFAULT_TIMEOUT * 1000);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; OpenManus/1.0)");
            
            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                return "访问失败，HTTP状态码: " + responseCode;
    }

            // 读取响应内容
            StringBuilder content = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
        }
            }
            
            String result = content.toString();
            // 限制返回内容长度，避免过长
            if (result.length() > 10000) {
                result = result.substring(0, 10000) + "\n... (内容已截断)";
            }
            
            return "网页内容:\n" + result;
            
        } catch (IOException e) {
            logger.error("访问网页失败: {}", url, e);
            return "访问网页失败: " + e.getMessage();
                }
    }
    
    @Tool("搜索网络内容")
    public String searchWeb(@P("搜索关键词") String query) {
        try {
            // 使用 DuckDuckGo 搜索（无需API密钥）
            String searchUrl = "https://duckduckgo.com/html/?q=" + 
                URLEncoder.encode(query, StandardCharsets.UTF_8);
            
            return browseWeb(searchUrl);
            
        } catch (Exception e) {
            logger.error("搜索失败: {}", query, e);
            return "搜索失败: " + e.getMessage();
        }
    }
    
    @Tool("获取网页标题")
    public String getWebPageTitle(@P("网页URL") String url) {
        try {
            String content = browseWeb(url);
            if (content.startsWith("访问网页失败")) {
                return content;
    }

            // 简单提取标题（实际项目中可能需要HTML解析器）
            int titleStart = content.indexOf("<title>");
            int titleEnd = content.indexOf("</title>");
            
            if (titleStart != -1 && titleEnd != -1) {
                String title = content.substring(titleStart + 7, titleEnd);
                return "网页标题: " + title;
        } else {
                return "无法提取网页标题";
    }

        } catch (Exception e) {
            logger.error("获取网页标题失败: {}", url, e);
            return "获取网页标题失败: " + e.getMessage();
            }
        }
        
    @Tool("检查网站是否可访问")
    public String checkWebsiteAccessibility(@P("网站URL") String url) {
        try {
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://" + url;
    }

            URL targetUrl = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) targetUrl.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            
            int responseCode = connection.getResponseCode();
            String responseMessage = connection.getResponseMessage();
            
            return String.format("网站状态: %d %s", responseCode, responseMessage);
            
        } catch (IOException e) {
            logger.error("检查网站可访问性失败: {}", url, e);
            return "网站不可访问: " + e.getMessage();
        }
    }
    
    @Tool("异步访问网页")
    public String browseWebAsync(@P("网页URL") String url) {
        try {
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> browseWeb(url));
            
            // 等待结果，设置超时
            String result = future.get(DEFAULT_TIMEOUT, TimeUnit.SECONDS);
            return "异步访问结果:\n" + result;
            
        } catch (Exception e) {
            logger.error("异步访问网页失败: {}", url, e);
            return "异步访问失败: " + e.getMessage();
        }
    }
}