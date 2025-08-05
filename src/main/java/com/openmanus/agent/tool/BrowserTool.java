package com.openmanus.agent.tool;

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
            // 使用DuckDuckGo搜索引擎，支持中文搜索且无需API密钥
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String searchUrl = "https://html.duckduckgo.com/html/?q=" + encodedQuery;
            
            logger.info("Searching web for: {}", query);
            
            URL url = new URL(searchUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(DEFAULT_TIMEOUT * 1000);
            connection.setReadTimeout(DEFAULT_TIMEOUT * 1000);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
            
            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                return "搜索失败，HTTP状态码: " + responseCode;
            }
            
            // 读取搜索结果页面
            StringBuilder htmlContent = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    htmlContent.append(line).append("\n");
                }
            }
            
            // 解析搜索结果
            String results = parseSearchResults(htmlContent.toString(), query);
            logger.info("🔍 Search results: {}", results);
            return results;
            
        } catch (IOException e) {
            logger.error("网页搜索失败: {}", query, e);
            return "搜索失败: " + e.getMessage();
        }
    }
    
    /**
     * 解析DuckDuckGo搜索结果页面
     */
    private String parseSearchResults(String htmlContent, String query) {
        StringBuilder results = new StringBuilder();
        results.append("🔍 搜索结果: ").append(query).append("\n\n");
        
        try {
            // DuckDuckGo搜索结果的HTML结构解析
            String[] lines = htmlContent.split("\n");
            int resultCount = 0;
            boolean inResult = false;
            String currentTitle = "";
            String currentUrl = "";
            String currentSnippet = "";
            
            for (String line : lines) {
                line = line.trim();
                
                // 查找搜索结果标题和链接
                if (line.contains("class=\"result__a\"") && line.contains("href=")) {
                    // 提取标题和URL
                    int hrefStart = line.indexOf("href=\"") + 6;
                    int hrefEnd = line.indexOf("\"", hrefStart);
                    if (hrefStart > 5 && hrefEnd > hrefStart) {
                        currentUrl = line.substring(hrefStart, hrefEnd);
                        // 解码URL
                        if (currentUrl.startsWith("/l/?uddg=")) {
                            int urlStart = currentUrl.indexOf("&rut=") + 5;
                            if (urlStart > 4) {
                                int urlEnd = currentUrl.indexOf("&", urlStart);
                                if (urlEnd == -1) urlEnd = currentUrl.length();
                                currentUrl = URLEncoder.encode(currentUrl.substring(urlStart, urlEnd), StandardCharsets.UTF_8);
                                try {
                                    currentUrl = java.net.URLDecoder.decode(currentUrl, StandardCharsets.UTF_8);
                                } catch (Exception ignored) {}
                            }
                        }
                    }
                    
                    // 提取标题
                    int titleStart = line.indexOf(">") + 1;
                    int titleEnd = line.lastIndexOf("<");
                    if (titleStart > 0 && titleEnd > titleStart) {
                        currentTitle = line.substring(titleStart, titleEnd);
                        currentTitle = cleanHtmlText(currentTitle);
                    }
                    inResult = true;
                }
                
                // 查找搜索结果摘要
                if (inResult && line.contains("class=\"result__snippet\"")) {
                    int snippetStart = line.indexOf(">") + 1;
                    int snippetEnd = line.lastIndexOf("<");
                    if (snippetStart > 0 && snippetEnd > snippetStart) {
                        currentSnippet = line.substring(snippetStart, snippetEnd);
                        currentSnippet = cleanHtmlText(currentSnippet);
                    }
                    
                    // 输出完整的搜索结果
                    if (!currentTitle.isEmpty() && !currentUrl.isEmpty()) {
                        resultCount++;
                        results.append(resultCount).append(". **").append(currentTitle).append("**\n");
                        results.append("   🔗 ").append(currentUrl).append("\n");
                        if (!currentSnippet.isEmpty()) {
                            results.append("   📝 ").append(currentSnippet).append("\n");
                        }
                        results.append("\n");
                        
                        // 限制结果数量
                        if (resultCount >= 5) {
                            break;
                        }
                    }
                    
                    // 重置状态
                    inResult = false;
                    currentTitle = "";
                    currentUrl = "";
                    currentSnippet = "";
                }
            }
            
            if (resultCount == 0) {
                results.append("未找到相关搜索结果，请尝试其他关键词。\n");
            } else {
                results.append("共找到 ").append(resultCount).append(" 个相关结果\n");
            }
            
        } catch (Exception e) {
            logger.warn("解析搜索结果时出错: {}", e.getMessage());
            results.append("搜索结果解析失败，但搜索请求已发送。请尝试直接访问搜索引擎。\n");
        }
        
        // 限制返回内容长度
        String result = results.toString();
        if (result.length() > 8000) {
            result = result.substring(0, 8000) + "\n... (结果已截断)";
        }
        
        return result;
    }
    
    /**
     * 清理HTML文本，移除标签和转义字符
     */
    private String cleanHtmlText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        
        // 移除HTML标签
        text = text.replaceAll("<[^>]+>", "");
        
        // 处理HTML转义字符
        text = text.replace("&amp;", "&")
                   .replace("&lt;", "<")
                   .replace("&gt;", ">")
                   .replace("&quot;", "\"")
                   .replace("&#39;", "'")
                   .replace("&nbsp;", " ");
        
        // 移除多余空白字符
        text = text.replaceAll("\\s+", " ").trim();
        
        return text;
    }

}