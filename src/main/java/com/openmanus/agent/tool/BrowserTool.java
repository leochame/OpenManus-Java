package com.openmanus.agent.tool;

import com.openmanus.domain.model.SessionSandboxInfo;
import com.openmanus.domain.service.SessionSandboxManager;
import com.openmanus.infra.config.OpenManusProperties;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static com.openmanus.infra.log.LogMarkers.TO_FRONTEND;

/**
 * 浏览器工具 - 提供网页访问和搜索能力
 * 
 * 功能：
 * 1. 访问网页并获取内容
 * 2. 搜索网络信息（基于 Bing，国内可访问）
 * 3. 自动管理 VNC 沙箱浏览器
 * 4. 支持代理配置（通过 application.yml）
 * 
 * 设计模式：
 * - 策略模式：不同搜索引擎可扩展
 * - 块解析模式：HTML 结果解析器
 */
@Component
@Slf4j
public class BrowserTool {
    
    private final SessionSandboxManager sessionSandboxManager;
    private final OpenManusProperties properties;
    
    @Autowired
    public BrowserTool(SessionSandboxManager sessionSandboxManager, OpenManusProperties properties) {
        this.sessionSandboxManager = sessionSandboxManager;
        this.properties = properties;
    }
    
    // 网络配置常量
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final int DEFAULT_TIMEOUT_MS = DEFAULT_TIMEOUT_SECONDS * 1000;
    private static final int MAX_CONTENT_LENGTH = 10000;
    private static final int MAX_SEARCH_RESULTS = 5;
    private static final int MAX_RESULT_LENGTH = 8000;
    private static final int MAX_RETRIES = 3;
    
    // 用户代理配置
    private static final String USER_AGENT_BROWSER = "Mozilla/5.0 (compatible; OpenManus/1.0)";
    
    // HTTP状态码
    private static final int HTTP_OK = 200;
    
    // 消息模板
    private static final String MSG_ACCESS_FAILED = "访问失败，HTTP状态码: ";
    private static final String MSG_CONTENT_TRUNCATED = "\n... (内容已截断)";
    private static final String MSG_RESULT_TRUNCATED = "\n... (结果已截断)";
    
    /**
     * 访问网页并获取内容
     * 首次调用时会自动创建 VNC 沙箱浏览器
     */
    @Tool("访问网页并获取内容")
    public String browseWeb(@P("网页 URL") String url) {
        try {
            // 确保沙箱已创建（首次调用时触发）
            ensureSandboxCreated();
            
            // 标准化URL格式
            url = normalizeUrl(url);
            log.info("访问网页: {}", url);
            
            // 通知前端当前访问的 URL（用于网页预览）
            log.info(TO_FRONTEND, "│  🌐 BROWSER · 网页访问模块");
            log.info(TO_FRONTEND, "│  📄 正在访问: {}", url);

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
     * 使用 Serper API 获取结构化搜索结果，同时通知前端展示搜索页面
     */
    @Tool("搜索网络内容")
    public String searchWeb(@P("搜索关键词") String query) {
        int retryCount = 0;
        Exception lastException = null;

        while (retryCount < MAX_RETRIES) {
            try {
                if (retryCount > 0) {
                    log.info("搜索重试第 {} 次: {}", retryCount, query);
                    Thread.sleep(1000 * retryCount);
                }
                
                log.info(TO_FRONTEND, "┌──────────────────────────────────────────────────────────┐");
                log.info(TO_FRONTEND, "│  🔍 SEARCH ENGINE · 智能搜索引擎                         │");
                log.info(TO_FRONTEND, "├──────────────────────────────────────────────────────────┤");
                log.info(TO_FRONTEND, "│  📝 关键词: {}                                              ", query);
                log.info(TO_FRONTEND, "│  ⚡ 正在检索全球网络资源...                                │");
                log.info(TO_FRONTEND, "└──────────────────────────────────────────────────────────┘");
                
                // 确保沙箱已创建（用于可视化展示）
                ensureSandboxCreated();
                
                // 通知前端展示 Google 搜索页面（可视化）
                String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
                String displayUrl = "https://www.google.com/search?q=" + encodedQuery;
                log.info(TO_FRONTEND, "│  🌐 可视化预览: {}", displayUrl);
                
                // 检查是否配置了 Serper API
                OpenManusProperties.SearchConfig searchConfig = properties.getSearch();
                String apiKey = searchConfig != null ? searchConfig.getApiKey() : null;
                
                if (apiKey == null || apiKey.isEmpty() || apiKey.startsWith("your-")) {
                    log.warn("Serper API 未配置，使用降级方案");
                    return fallbackSearch(query, encodedQuery);
                }
                
                // 使用 Serper API 获取搜索结果
                String results = searchWithSerperApi(query, searchConfig);
                log.info(TO_FRONTEND, "┌──────────────────────────────────────────────────────────┐");
                log.info(TO_FRONTEND, "│  ✅ 搜索完成 · 已获取相关结果                              │");
                log.info(TO_FRONTEND, "└──────────────────────────────────────────────────────────┘");
                return results;
                
            } catch (Exception e) {
                lastException = e;
                log.warn("搜索尝试 {} 失败: {}", retryCount + 1, e.getMessage());
                retryCount++;
            }
        }
        
        log.error("搜索最终失败: {}", query, lastException);
        return "搜索失败 (已重试 " + MAX_RETRIES + " 次): " + (lastException != null ? lastException.getMessage() : "Unknown error");
    }
    
    /**
     * 使用 Serper API 进行搜索
     */
    private String searchWithSerperApi(String query, OpenManusProperties.SearchConfig config) throws IOException {
        String endpoint = config.getSerperEndpoint();
        if (endpoint == null || endpoint.isEmpty()) {
            endpoint = "https://google.serper.dev/search";
        }
        
        // 构建请求
        HttpURLConnection connection = (HttpURLConnection) URI.create(endpoint).toURL().openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(DEFAULT_TIMEOUT_MS);
        connection.setReadTimeout(DEFAULT_TIMEOUT_MS);
        connection.setRequestProperty("X-API-KEY", config.getApiKey());
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);
        
        // 构建 JSON 请求体
        String requestBody = String.format("{\"q\":\"%s\",\"num\":%d}", 
                query.replace("\"", "\\\""), 
                Math.min(config.getMaxResults(), MAX_SEARCH_RESULTS));
        
        try (OutputStream os = connection.getOutputStream()) {
            os.write(requestBody.getBytes(StandardCharsets.UTF_8));
        }
        
        int responseCode = connection.getResponseCode();
        if (responseCode != HTTP_OK) {
            throw new IOException("Serper API 错误: HTTP " + responseCode);
        }
        
        // 读取响应
        String jsonResponse = readContent(connection);
        
        // 解析 JSON 结果
        return parseSerperResults(jsonResponse, query);
    }
    
    /**
     * 解析 Serper API 返回的 JSON 结果
     */
    private String parseSerperResults(String jsonResponse, String query) {
        StringBuilder results = new StringBuilder();
        results.append("🔍 搜索结果: ").append(query).append("\n\n");
        
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(jsonResponse);
            
            int count = 0;
            
            // 解析 organic 搜索结果
            JsonNode organic = root.get("organic");
            if (organic != null && organic.isArray()) {
                for (JsonNode item : organic) {
                    if (count >= MAX_SEARCH_RESULTS) break;
                    
                    String title = item.has("title") ? item.get("title").asText() : "";
                    String link = item.has("link") ? item.get("link").asText() : "";
                    String snippet = item.has("snippet") ? item.get("snippet").asText() : "";
                    
                    if (!title.isEmpty() && !link.isEmpty()) {
                        count++;
                        results.append(count).append(". **").append(title).append("**\n");
                        results.append("   🔗 ").append(link).append("\n");
                        if (!snippet.isEmpty()) {
                            results.append("   📝 ").append(snippet).append("\n");
                        }
                        results.append("\n");
                    }
                }
            }
            
            // 如果有知识图谱结果，也添加进来
            JsonNode knowledgeGraph = root.get("knowledgeGraph");
            if (knowledgeGraph != null) {
                String kgTitle = knowledgeGraph.has("title") ? knowledgeGraph.get("title").asText() : "";
                String kgDescription = knowledgeGraph.has("description") ? knowledgeGraph.get("description").asText() : "";
                
                if (!kgTitle.isEmpty()) {
                    results.append("📚 **知识卡片: ").append(kgTitle).append("**\n");
                    if (!kgDescription.isEmpty()) {
                        results.append("   ").append(kgDescription).append("\n");
                    }
                    results.append("\n");
                }
            }
            
            if (count == 0) {
                results.append("未找到相关搜索结果，请尝试其他关键词。\n");
            } else {
                results.append("共找到 ").append(count).append(" 个相关结果\n");
            }
            
        } catch (Exception e) {
            log.warn("解析 Serper 结果时出错: {}", e.getMessage());
            results.append("搜索结果解析失败: ").append(e.getMessage()).append("\n");
        }
        
        String result = results.toString();
        if (result.length() > MAX_RESULT_LENGTH) {
            result = result.substring(0, MAX_RESULT_LENGTH) + MSG_RESULT_TRUNCATED;
        }
        
        return result;
    }
    
    /**
     * 降级搜索方案：当 API 未配置时使用
     */
    private String fallbackSearch(String query, String encodedQuery) {
        StringBuilder results = new StringBuilder();
        results.append("🔍 搜索结果: ").append(query).append("\n\n");
        results.append("⚠️ 搜索 API 未配置，请在 application.yml 中配置 Serper API Key：\n\n");
        results.append("```yaml\n");
        results.append("openmanus:\n");
        results.append("  search:\n");
        results.append("    engine: serper\n");
        results.append("    api-key: your-serper-api-key\n");
        results.append("```\n\n");
        results.append("获取 Serper API Key: https://serper.dev\n\n");
        results.append("📄 您可以手动访问搜索页面查看结果:\n");
        results.append("   https://www.google.com/search?q=").append(encodedQuery).append("\n");
        return results.toString();
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
        java.net.Proxy proxy = getProxy();
        HttpURLConnection connection;
        
        if (proxy != null) {
            connection = (HttpURLConnection) URI.create(urlString).toURL().openConnection(proxy);
        } else {
            connection = (HttpURLConnection) URI.create(urlString).toURL().openConnection();
        }
        
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(DEFAULT_TIMEOUT_MS);
        connection.setReadTimeout(DEFAULT_TIMEOUT_MS);
        connection.setRequestProperty("User-Agent", userAgent);
        return connection;
    }

    /**
     * 获取代理配置
     */
    private java.net.Proxy getProxy() {
        OpenManusProperties.ProxyConfig proxyConfig = properties.getProxy();
        if (proxyConfig != null && proxyConfig.isEnabled()) {
            String proxyUrl = proxyConfig.getHttpsProxy(); // 优先使用 HTTPS 代理
            if (proxyUrl == null || proxyUrl.isEmpty()) {
                proxyUrl = proxyConfig.getHttpProxy();
            }
            
            if (proxyUrl != null && !proxyUrl.isEmpty()) {
                try {
                    // 处理 http://hostname:port 格式
                    if (proxyUrl.contains("://")) {
                        proxyUrl = proxyUrl.substring(proxyUrl.indexOf("://") + 3);
                    }
                    
                    String[] parts = proxyUrl.split(":");
                    if (parts.length == 2) {
                        String host = parts[0];
                        int port = Integer.parseInt(parts[1]);
                        return new java.net.Proxy(java.net.Proxy.Type.HTTP, new java.net.InetSocketAddress(host, port));
                    }
                } catch (Exception e) {
                    log.warn("代理配置解析失败: {}", proxyUrl, e);
                }
            }
        }
        return null;
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
     * 确保当前会话的沙箱已创建
     * 从 MDC 中获取 sessionId，如果沙箱不存在则创建
     */
    private void ensureSandboxCreated() {
        String sessionId = MDC.get("sessionId");
        
        if (sessionId == null || sessionId.isEmpty()) {
            log.warn("MDC 中未找到 sessionId，跳过沙箱创建");
            return;
        }
        
        try {
            // 检查是否已存在沙箱
            SessionSandboxInfo sandboxInfo = sessionSandboxManager.getSandboxInfo(sessionId)
                .orElse(null);
            
            if (sandboxInfo == null || !sandboxInfo.isAvailable()) {
                // 不存在或不可用，创建新沙箱
                log.info(TO_FRONTEND, "┌──────────────────────────────────────────────────────────┐");
                log.info(TO_FRONTEND, "│  🖥️ SANDBOX · 可视化沙箱环境                            │");
                log.info(TO_FRONTEND, "├──────────────────────────────────────────────────────────┤");
                log.info(TO_FRONTEND, "│  ⚡ 正在初始化安全沙箱环境...                              │");
                log.info(TO_FRONTEND, "└──────────────────────────────────────────────────────────┘");
                sandboxInfo = sessionSandboxManager.getOrCreateSandbox(sessionId);
                log.info(TO_FRONTEND, "│  ✅ 沙箱已就绪 · VNC 可视化界面已开放                        │");
                log.debug("沙箱已创建: sessionId={}, vncUrl={}", sessionId, sandboxInfo.getVncUrl());
            } else {
                log.debug("复用现有沙箱: sessionId={}", sessionId);
            }
        } catch (Exception e) {
            log.error("创建沙箱时出错: {}", e.getMessage(), e);
            // 不抛出异常，允许工具继续执行（降级为无沙箱模式）
        }
    }
    
}