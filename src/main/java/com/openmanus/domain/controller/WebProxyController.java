package com.openmanus.domain.controller;

import com.openmanus.infra.config.OpenManusProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/**
 * Web 代理控制器 - 用于在 iframe 中显示被 X-Frame-Options 限制的网页
 * 
 * 原理：
 * 1. 后端代理请求目标网页
 * 2. 移除限制性的响应头（X-Frame-Options, Content-Security-Policy 的 frame-ancestors）
 * 3. 重写页面中的相对链接为代理链接
 * 4. 返回处理后的内容给前端
 * 
 * 注意：此功能仅用于开发和演示目的
 */
@RestController
@RequestMapping("/api/proxy")
@Tag(name = "Web Proxy", description = "Proxy for viewing websites in iframe that have frame restrictions")
@CrossOrigin(origins = "*")
@Slf4j
public class WebProxyController {

    private static final int TIMEOUT_MS = 15000;
    private static final int MAX_CONTENT_SIZE = 5 * 1024 * 1024; // 5MB
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    
    // 需要移除的限制性响应头
    private static final List<String> HEADERS_TO_REMOVE = List.of(
        "x-frame-options",
        "content-security-policy",
        "content-security-policy-report-only"
    );
    
    @Autowired
    private OpenManusProperties properties;

    /**
     * 代理访问网页
     * 
     * @param url Base64 编码的目标 URL
     * @param response HTTP 响应
     */
    @GetMapping("/web")
    @Operation(
        summary = "Proxy Web Page",
        description = "Proxies a web page and removes frame restriction headers (X-Frame-Options, CSP frame-ancestors) to allow iframe embedding"
    )
    public void proxyWeb(
            @RequestParam("url") String url,
            HttpServletResponse response) {
        
        String targetUrl;
        try {
            // URL 是 Base64 编码的，解码它
            targetUrl = new String(Base64.getUrlDecoder().decode(url), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            // 如果不是 Base64，直接使用原始 URL
            targetUrl = url;
        }
        
        log.debug("Proxying web page: {}", targetUrl);
        
        try {
            // 标准化 URL
            if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) {
                targetUrl = "https://" + targetUrl;
            }
            
            URL urlObj = URI.create(targetUrl).toURL();
            String baseUrl = urlObj.getProtocol() + "://" + urlObj.getHost() + (urlObj.getPort() > 0 ? ":" + urlObj.getPort() : "");
            
            // 创建连接
            HttpURLConnection connection = createConnection(targetUrl);
            connection.setInstanceFollowRedirects(true);
            
            int responseCode = connection.getResponseCode();
            
            // 处理重定向
            if (responseCode >= 300 && responseCode < 400) {
                String redirectUrl = connection.getHeaderField("Location");
                if (redirectUrl != null) {
                    if (!redirectUrl.startsWith("http")) {
                        redirectUrl = baseUrl + (redirectUrl.startsWith("/") ? "" : "/") + redirectUrl;
                    }
                    // 返回重定向到代理 URL
                    String proxyRedirectUrl = "/api/proxy/web?url=" + Base64.getUrlEncoder().encodeToString(redirectUrl.getBytes(StandardCharsets.UTF_8));
                    response.sendRedirect(proxyRedirectUrl);
                    return;
                }
            }
            
            // 获取 Content-Type
            String contentType = connection.getContentType();
            if (contentType == null) {
                contentType = "text/html; charset=utf-8";
            }
            
            // 设置响应头
            response.setContentType(contentType);
            response.setCharacterEncoding("UTF-8");
            
            // 复制安全的响应头，跳过限制性头
            connection.getHeaderFields().forEach((name, values) -> {
                if (name != null && !HEADERS_TO_REMOVE.contains(name.toLowerCase()) && 
                    !name.equalsIgnoreCase("transfer-encoding") &&
                    !name.equalsIgnoreCase("content-length") &&
                    !name.equalsIgnoreCase("content-encoding")) {
                    values.forEach(value -> response.addHeader(name, value));
                }
            });
            
            // 读取内容
            InputStream inputStream;
            try {
                inputStream = connection.getInputStream();
            } catch (IOException e) {
                inputStream = connection.getErrorStream();
                if (inputStream == null) {
                    response.sendError(responseCode, "Failed to fetch: " + e.getMessage());
                    return;
                }
            }
            
            // 判断是否需要处理内容（只处理 HTML）
            boolean isHtml = contentType != null && contentType.toLowerCase().contains("text/html");
            
            if (isHtml) {
                // 读取 HTML 内容并处理
                String content = readContent(inputStream, MAX_CONTENT_SIZE);
                String processedContent = processHtmlContent(content, baseUrl, targetUrl);
                
                byte[] contentBytes = processedContent.getBytes(StandardCharsets.UTF_8);
                response.setContentLength(contentBytes.length);
                response.getOutputStream().write(contentBytes);
            } else {
                // 直接流式传输非 HTML 内容
                try (OutputStream out = response.getOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    int totalBytes = 0;
                    while ((bytesRead = inputStream.read(buffer)) != -1 && totalBytes < MAX_CONTENT_SIZE) {
                        out.write(buffer, 0, bytesRead);
                        totalBytes += bytesRead;
                    }
                }
            }
            
            inputStream.close();
            connection.disconnect();
            
        } catch (Exception e) {
            log.error("Proxy error for URL: {}", targetUrl, e);
            try {
                response.sendError(HttpServletResponse.SC_BAD_GATEWAY, "Proxy error: " + e.getMessage());
            } catch (IOException ignored) {}
        }
    }
    
    /**
     * 获取代理 URL
     * 前端可以调用此接口获取某个 URL 的代理地址
     */
    @GetMapping("/url")
    @Operation(summary = "Get Proxy URL", description = "Returns the proxy URL for a given target URL")
    public String getProxyUrl(@RequestParam("target") String targetUrl) {
        String encoded = Base64.getUrlEncoder().encodeToString(targetUrl.getBytes(StandardCharsets.UTF_8));
        return "/api/proxy/web?url=" + encoded;
    }
    
    /**
     * 处理 HTML 内容
     * - 注入 base 标签
     * - 重写相对链接
     */
    private String processHtmlContent(String content, String baseUrl, String fullUrl) {
        // 如果已有 <base> 标签，不处理
        if (content.toLowerCase().contains("<base ")) {
            return content;
        }
        
        // 在 <head> 后注入 <base> 标签，让浏览器正确解析相对路径
        String baseTag = "<base href=\"" + baseUrl + "/\" target=\"_blank\">";
        
        // 查找 <head> 标签位置
        int headIndex = content.toLowerCase().indexOf("<head>");
        if (headIndex != -1) {
            int insertPos = headIndex + 6; // "<head>".length()
            content = content.substring(0, insertPos) + "\n" + baseTag + "\n" + content.substring(insertPos);
        } else {
            // 没有 <head> 标签，在 <html> 后插入
            int htmlIndex = content.toLowerCase().indexOf("<html");
            if (htmlIndex != -1) {
                int closeIndex = content.indexOf(">", htmlIndex);
                if (closeIndex != -1) {
                    content = content.substring(0, closeIndex + 1) + "\n<head>" + baseTag + "</head>\n" + content.substring(closeIndex + 1);
                }
            }
        }
        
        return content;
    }
    
    /**
     * 创建 HTTP 连接
     */
    private HttpURLConnection createConnection(String urlString) throws IOException {
        Proxy proxy = getProxy();
        HttpURLConnection connection;
        
        URL url = URI.create(urlString).toURL();
        if (proxy != null) {
            connection = (HttpURLConnection) url.openConnection(proxy);
        } else {
            connection = (HttpURLConnection) url.openConnection();
        }
        
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
        connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        
        return connection;
    }
    
    /**
     * 获取代理配置
     */
    private Proxy getProxy() {
        OpenManusProperties.ProxyConfig proxyConfig = properties.getProxy();
        if (proxyConfig != null && proxyConfig.isEnabled()) {
            String proxyUrl = proxyConfig.getHttpsProxy();
            if (proxyUrl == null || proxyUrl.isEmpty()) {
                proxyUrl = proxyConfig.getHttpProxy();
            }
            
            if (proxyUrl != null && !proxyUrl.isEmpty()) {
                try {
                    if (proxyUrl.contains("://")) {
                        proxyUrl = proxyUrl.substring(proxyUrl.indexOf("://") + 3);
                    }
                    
                    String[] parts = proxyUrl.split(":");
                    if (parts.length == 2) {
                        String host = parts[0];
                        int port = Integer.parseInt(parts[1]);
                        return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port));
                    }
                } catch (Exception e) {
                    log.warn("Proxy config parse error: {}", proxyUrl, e);
                }
            }
        }
        return null;
    }
    
    /**
     * 读取输入流内容
     */
    private String readContent(InputStream inputStream, int maxSize) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int length;
        int totalRead = 0;
        
        while ((length = inputStream.read(buffer)) != -1 && totalRead < maxSize) {
            result.write(buffer, 0, length);
            totalRead += length;
        }
        
        return result.toString(StandardCharsets.UTF_8);
    }
}

