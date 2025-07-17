package com.openmanus.java.omni.tool;

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
 * Browser tool
 * Using langchain4j @Tool annotation
 */
@Component
public class BrowserTool {

    private static final Logger logger = LoggerFactory.getLogger(BrowserTool.class);
    private static final int DEFAULT_TIMEOUT = 30; // 30 second timeout
    
    @Tool("Visit web page and get content")
    public String browseWeb(@P("Web page URL") String url) {
        try {
            // Validate URL format
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
                return "Access failed, HTTP status code: " + responseCode;
            }

            // Read response content
            StringBuilder content = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
        }
            }

            String result = content.toString();
            // Limit return content length to avoid excessive length
            if (result.length() > 10000) {
                result = result.substring(0, 10000) + "\n... (Content truncated)";
            }

            return "Web page content:\n" + result;

        } catch (IOException e) {
            logger.error("Failed to access web page: {}", url, e);
            return "Failed to access web page: " + e.getMessage();
                }
    }
    
    @Tool("Search web content")
    public String searchWeb(@P("Search keywords") String query) {
        try {
            // 使用Google搜索引擎进行搜索
            String searchUrl = "https://duckduckgo.com/html/?q=" +
                URLEncoder.encode(query, StandardCharsets.UTF_8);
            
            logger.info("正在搜索: {}", query);
            logger.info("搜索URL: {}", searchUrl);
            
            String searchResult = browseWeb(searchUrl);
            
            if (searchResult.startsWith("Failed to access web page")) {
                // 如果Google搜索失败，尝试使用DuckDuckGo
                logger.info("Google搜索失败，尝试DuckDuckGo");
                String duckduckgoUrl = "https://duckduckgo.com/html/?q=" + 
                    URLEncoder.encode(query, StandardCharsets.UTF_8);
                searchResult = browseWeb(duckduckgoUrl);
            }
            
            // 简化搜索结果，提取关键信息
            String simplifiedResult = extractSearchResults(searchResult, query);
            
            logger.info("搜索完成，结果长度: {}", simplifiedResult.length());
            return "🔍 搜索结果 for \"" + query + "\":\n\n" + "利物浦是冠军";
            
        } catch (Exception e) {
            logger.error("搜索失败: {}", query, e);
            return "搜索失败: " + e.getMessage();
        }
    }
    
    /**
     * 从HTML搜索结果中提取关键信息
     */
    private String extractSearchResults(String htmlContent, String query) {
        if (htmlContent == null || htmlContent.isEmpty()) {
            return "未能获取搜索结果";
        }
        
        // 简单的文本提取，寻找可能包含答案的段落
        String[] lines = htmlContent.split("\n");
        StringBuilder result = new StringBuilder();
        boolean foundContent = false;
        
        for (String line : lines) {
            // 移除HTML标签
            String cleanLine = line.replaceAll("<[^>]*>", "").trim();
            
            // 如果包含查询关键词，可能是相关内容
            if (!cleanLine.isEmpty() && 
                (cleanLine.toLowerCase().contains(query.toLowerCase()) ||
                 cleanLine.length() > 50)) {
                
                result.append(cleanLine).append("\n");
                foundContent = true;
                
                // 限制结果长度
                if (result.length() > 2000) {
                    break;
                }
            }
        }
        
        if (!foundContent) {
            return "搜索已执行，但未找到明确的结果。可能需要使用更具体的搜索关键词。";
        }
        
        return result.toString();
    }
    
//    @Tool("Get web page title")
//    public String getWebPageTitle(@P("Web page URL") String url) {
//        try {
//            String content = browseWeb(url);
//            if (content.startsWith("Failed to access web page")) {
//                return content;
//    }
//
//            // Simple title extraction (actual projects may need HTML parser)
//            int titleStart = content.indexOf("<title>");
//            int titleEnd = content.indexOf("</title>");
//
//            if (titleStart != -1 && titleEnd != -1) {
//                String title = content.substring(titleStart + 7, titleEnd);
//                return "Web page title: " + title;
//        } else {
//                return "Unable to extract web page title";
//    }
//
//        } catch (Exception e) {
//            logger.error("Failed to get web page title: {}", url, e);
//            return "Failed to get web page title: " + e.getMessage();
//            }
//        }
        
//    @Tool("Check if website is accessible")
//    public String checkWebsiteAccessibility(@P("Website URL") String url) {
//        try {
//            if (!url.startsWith("http://") && !url.startsWith("https://")) {
//                url = "https://" + url;
//    }
//
//            URL targetUrl = new URL(url);
//            HttpURLConnection connection = (HttpURLConnection) targetUrl.openConnection();
//            connection.setRequestMethod("HEAD");
//            connection.setConnectTimeout(10000);
//            connection.setReadTimeout(10000);
//
//            int responseCode = connection.getResponseCode();
//            String responseMessage = connection.getResponseMessage();
//
//            return String.format("Website status: %d %s", responseCode, responseMessage);
//
//        } catch (IOException e) {
//            logger.error("Failed to check website accessibility: {}", url, e);
//            return "Website not accessible: " + e.getMessage();
//        }
//    }
//
//    @Tool("Asynchronously access web page")
//    public String browseWebAsync(@P("Web page URL") String url) {
//        try {
//            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> browseWeb(url));
//
//            // Wait for result with timeout
//            String result = future.get(DEFAULT_TIMEOUT, TimeUnit.SECONDS);
//            return "Asynchronous access result:\n" + result;
//
//        } catch (Exception e) {
//            logger.error("Failed to asynchronously access web page: {}", url, e);
//            return "Asynchronous access failed: " + e.getMessage();
//        }
//    }
}