package com.openmanus.agent.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static com.openmanus.infra.log.LogMarkers.TO_FRONTEND;

/**
 * 浏览器工具
 * 
 * 提供网页访问和搜索能力：
 * 1. 访问网页并获取内容
 * 2. 搜索网络信息（基于DuckDuckGo）
 * 
 * 设计模式：策略模式 - 不同的搜索引擎可以作为不同策略
 */
@Component
@Slf4j
public class BrowserTool {
    
    // 网络配置常量
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final int DEFAULT_TIMEOUT_MS = DEFAULT_TIMEOUT_SECONDS * 1000;
    private static final int MAX_CONTENT_LENGTH = 10000;
    private static final int MAX_SEARCH_RESULTS = 5;
    private static final int MAX_RESULT_LENGTH = 8000;
    private static final int LOG_PREVIEW_LENGTH = 100;
    
    // 搜索引擎配置
    private static final String SEARCH_ENGINE_URL = "https://html.duckduckgo.com/html/?q=";
    
    // 用户代理配置
    private static final String USER_AGENT_BROWSER = "Mozilla/5.0 (compatible; OpenManus/1.0)";
    private static final String USER_AGENT_SEARCH = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
    
    // HTTP状态码
    private static final int HTTP_OK = 200;
    
    // 消息模板
    private static final String MSG_ACCESS_FAILED = "访问失败，HTTP状态码: ";
    private static final String MSG_SEARCH_FAILED = "搜索失败，HTTP状态码: ";
    private static final String MSG_CONTENT_TRUNCATED = "\n... (内容已截断)";
    private static final String MSG_RESULT_TRUNCATED = "\n... (结果已截断)";
    
    /**
     * 访问网页并获取内容
     */
    @Tool("Visit web page and get content")
    public String browseWeb(@P("Web page URL") String url) {
        try {
            // 标准化URL格式
            url = normalizeUrl(url);
            log.info("访问网页: {}", url);

            // 建立HTTP连接
            HttpURLConnection connection = createConnection(url, USER_AGENT_BROWSER);
            
            // 检查响应状态
            int responseCode = connection.getResponseCode();
            if (responseCode != HTTP_OK) {
                return MSG_ACCESS_FAILED + responseCode;
            }

            // 读取网页内容
            String content = readContent(connection);
            
            // 限制返回内容长度
            if (content.length() > MAX_CONTENT_LENGTH) {
                content = content.substring(0, MAX_CONTENT_LENGTH) + MSG_CONTENT_TRUNCATED;
            }

            return "网页内容:\n" + content;

        } catch (IOException e) {
            log.error("访问网页失败: {}", url, e);
            return "访问网页失败: " + e.getMessage();
        }
    }
    
    /**
     * 搜索网络内容
     */
    @Tool("Search web content")
    public String searchWeb(@P("Search keywords") String query) {
        try {
            log.info(TO_FRONTEND, "搜索关键词: {}", query);
            
            // 构建搜索URL
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String searchUrl = SEARCH_ENGINE_URL + encodedQuery;
            
            // 建立搜索连接
            HttpURLConnection connection = createSearchConnection(searchUrl);
            
            // 检查响应状态
            int responseCode = connection.getResponseCode();
            if (responseCode != HTTP_OK) {
                return MSG_SEARCH_FAILED + responseCode;
            }
            
            // 读取搜索结果页面
            String htmlContent = readContent(connection);
            
            // 解析搜索结果
            String results = parseSearchResults(htmlContent, query);
            log.info(TO_FRONTEND, "🔍 搜索结果: {}", 
                    results.length() > LOG_PREVIEW_LENGTH ? results.substring(0, LOG_PREVIEW_LENGTH) + "..." : results);
            return results;
            
        } catch (IOException e) {
            log.error("网页搜索失败: {}", query, e);
            return "搜索失败: " + e.getMessage();
        }
    }
    
    /**
     * 标准化URL格式
     */
    private String normalizeUrl(String url) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return "https://" + url;
        }
        return url;
    }
    
    /**
     * 创建HTTP连接
     */
    private HttpURLConnection createConnection(String urlString, String userAgent) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(DEFAULT_TIMEOUT_MS);
        connection.setReadTimeout(DEFAULT_TIMEOUT_MS);
        connection.setRequestProperty("User-Agent", userAgent);
        return connection;
    }
    
    /**
     * 创建搜索连接（带额外Header）
     */
    private HttpURLConnection createSearchConnection(String urlString) throws IOException {
        HttpURLConnection connection = createConnection(urlString, USER_AGENT_SEARCH);
        connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        return connection;
    }
    
    /**
     * 读取HTTP响应内容
     */
    private String readContent(HttpURLConnection connection) throws IOException {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        return content.toString();
    }
    
    /**
     * 解析DuckDuckGo搜索结果页面
     * 采用状态机模式解析HTML
     */
    private String parseSearchResults(String htmlContent, String query) {
        StringBuilder results = new StringBuilder();
        results.append("🔍 搜索结果: ").append(query).append("\n\n");
        
        try {
            SearchResultParser parser = new SearchResultParser();
            int resultCount = parser.parse(htmlContent, results);
            
            if (resultCount == 0) {
                results.append("未找到相关搜索结果，请尝试其他关键词。\n");
            } else {
                results.append("共找到 ").append(resultCount).append(" 个相关结果\n");
            }
            
        } catch (Exception e) {
            log.warn("解析搜索结果时出错: {}", e.getMessage());
            results.append("搜索结果解析失败，但搜索请求已发送。请尝试直接访问搜索引擎。\n");
        }
        
        // 限制返回内容长度
        String result = results.toString();
        if (result.length() > MAX_RESULT_LENGTH) {
            result = result.substring(0, MAX_RESULT_LENGTH) + MSG_RESULT_TRUNCATED;
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
    
    /**
     * 搜索结果解析器 - 状态机模式
     * 将复杂的HTML解析逻辑封装为独立类
     */
    private class SearchResultParser {
        private static final String RESULT_LINK_CLASS = "class=\"result__a\"";
        private static final String RESULT_SNIPPET_CLASS = "class=\"result__snippet\"";
        private static final String HREF_ATTR = "href=\"";
        
        private int resultCount = 0;
        private boolean inResult = false;
        private String currentTitle = "";
        private String currentUrl = "";
        private String currentSnippet = "";
        
        int parse(String htmlContent, StringBuilder results) {
            String[] lines = htmlContent.split("\n");
            
            for (String line : lines) {
                line = line.trim();
                
                if (line.contains(RESULT_LINK_CLASS) && line.contains(HREF_ATTR)) {
                    parseResultLink(line);
                    inResult = true;
                } else if (inResult && line.contains(RESULT_SNIPPET_CLASS)) {
                    parseSnippet(line);
                    appendResult(results);
                    resetState();
                    
                    if (resultCount >= MAX_SEARCH_RESULTS) {
                        break;
                    }
                }
            }
            
            return resultCount;
        }
        
        private void parseResultLink(String line) {
            // 提取URL
            int hrefStart = line.indexOf(HREF_ATTR) + HREF_ATTR.length();
            int hrefEnd = line.indexOf("\"", hrefStart);
            if (hrefStart > HREF_ATTR.length() - 1 && hrefEnd > hrefStart) {
                currentUrl = decodeUrl(line.substring(hrefStart, hrefEnd));
            }
            
            // 提取标题
            int titleStart = line.indexOf(">") + 1;
            int titleEnd = line.lastIndexOf("<");
            if (titleStart > 0 && titleEnd > titleStart) {
                currentTitle = cleanHtmlText(line.substring(titleStart, titleEnd));
            }
        }
        
        private void parseSnippet(String line) {
            int snippetStart = line.indexOf(">") + 1;
            int snippetEnd = line.lastIndexOf("<");
            if (snippetStart > 0 && snippetEnd > snippetStart) {
                currentSnippet = cleanHtmlText(line.substring(snippetStart, snippetEnd));
            }
        }
        
        private String decodeUrl(String url) {
            // 解码DuckDuckGo的重定向URL
            if (url.startsWith("/l/?uddg=")) {
                int urlStart = url.indexOf("&rut=") + 5;
                if (urlStart > 4) {
                    int urlEnd = url.indexOf("&", urlStart);
                    if (urlEnd == -1) urlEnd = url.length();
                    try {
                        return java.net.URLDecoder.decode(url.substring(urlStart, urlEnd), StandardCharsets.UTF_8);
                    } catch (Exception e) {
                        log.warn("URL解码失败: {}", url);
                    }
                }
            }
            return url;
        }
        
        private void appendResult(StringBuilder results) {
            if (!currentTitle.isEmpty() && !currentUrl.isEmpty()) {
                resultCount++;
                results.append(resultCount).append(". **").append(currentTitle).append("**\n");
                results.append("   🔗 ").append(currentUrl).append("\n");
                if (!currentSnippet.isEmpty()) {
                    results.append("   📝 ").append(currentSnippet).append("\n");
                }
                results.append("\n");
            }
        }
        
        private void resetState() {
            inResult = false;
            currentTitle = "";
            currentUrl = "";
            currentSnippet = "";
        }
    }
}