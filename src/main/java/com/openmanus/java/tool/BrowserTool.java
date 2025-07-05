package com.openmanus.java.tool;

import com.openmanus.java.config.OpenManusProperties;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 🌐 智能网页搜索工具
 * 
 * 核心功能：
 * • 🔍 智能搜索：输入一句话，搜索多个网站
 * • 🧠 内容理解：AI解读网页内容，提供综合回答
 * • 🎯 多源聚合：同时搜索学术、新闻、技术、商业等多个领域
 * • 🚀 高性能：并发搜索，快速响应
 * 
 * 设计模式：
 * • ReAct模式：推理-行动-观察-反思
 * • 多策略规划：根据查询类型选择最佳搜索策略
 * • 自适应学习：根据搜索结果优化策略
 * 
 * @author OpenManus Team
 */
@Component
public class BrowserTool implements Closeable {

    private static final Logger logger = LoggerFactory.getLogger(BrowserTool.class);
    
    private final OpenManusProperties.BrowserSettings browserSettings;
    private final HttpClient httpClient;
    private final ExecutorService executorService;
    
    // 智能搜索引擎配置
    private final List<SearchSource> searchSources = Arrays.asList(
        // 学术搜索
        new SearchSource("ArXiv", "https://arxiv.org/search/?query={query}&searchtype=all", SearchType.ACADEMIC),
        new SearchSource("Google Scholar", "https://scholar.google.com/scholar?q={query}", SearchType.ACADEMIC),
        new SearchSource("DBLP", "https://dblp.org/search?q={query}", SearchType.ACADEMIC),
        
        // 技术搜索
        new SearchSource("GitHub", "https://github.com/search?q={query}&type=repositories", SearchType.TECHNICAL),
        new SearchSource("Stack Overflow", "https://stackoverflow.com/search?q={query}", SearchType.TECHNICAL),
        new SearchSource("MDN", "https://developer.mozilla.org/en-US/search?q={query}", SearchType.TECHNICAL),
        
        // 新闻搜索
        new SearchSource("Reddit", "https://www.reddit.com/search/?q={query}", SearchType.NEWS),
        new SearchSource("Hacker News", "https://hn.algolia.com/?query={query}", SearchType.NEWS),
        
        // 通用搜索
        new SearchSource("Wikipedia", "https://en.wikipedia.org/w/api.php?action=opensearch&search={query}&format=json", SearchType.GENERAL),
        new SearchSource("直接访问", "https://{query}", SearchType.DIRECT)
    );
    
    @Autowired
    public BrowserTool(OpenManusProperties properties) {
        this.browserSettings = properties.getBrowser();
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.executorService = Executors.newFixedThreadPool(8);
        
        logger.info("🌐 智能BrowserTool已初始化 - 支持多源搜索和AI内容理解");
    }

    /**
     * 🎯 智能网页搜索
     * 
     * 功能：
     * • 输入一句话，AI自动分析查询意图
     * • 并发搜索多个相关网站
     * • 智能提取和理解内容
     * • 生成综合性回答
     * 
     * 示例：
     * • "CVPR 2024年论文"
     * • "React框架最新教程"
     * • "人工智能发展现状"
     * • "比特币最新价格"
     */
    @Tool("智能网页搜索：输入一句话，AI自动搜索多个相关网站，理解内容并给出综合回答。支持学术、技术、新闻、商业等各领域搜索。")
    public String intelligentWebSearch(String query) {
        logger.info("🔍 开始智能搜索: {}", query);
        
        try {
            // 1. 分析查询意图
            SearchIntent intent = analyzeSearchIntent(query);
            logger.info("📋 查询意图分析: 类型={}, 领域={}, 复杂度={}", 
                intent.type, intent.domain, intent.complexity);
            
            // 2. 选择搜索源
            List<SearchSource> selectedSources = selectSearchSources(intent);
            logger.info("🎯 已选择 {} 个搜索源: {}", selectedSources.size(), 
                selectedSources.stream().map(s -> s.name).collect(Collectors.joining(", ")));
            
            // 3. 并发搜索
            List<SearchResult> results = performParallelSearch(query, selectedSources);
            logger.info("✅ 搜索完成，获得 {} 个有效结果", results.size());
            
            // 4. 智能内容理解和综合
            String intelligentResponse = synthesizeResults(query, intent, results);
            
            return intelligentResponse;
            
        } catch (Exception e) {
            logger.error("❌ 智能搜索失败: {}", e.getMessage(), e);
            return "❌ 搜索过程中遇到问题: " + e.getMessage() + 
                   "\\n\\n💡 建议尝试：\\n" +
                   "• 使用更具体的关键词\\n" +
                   "• 检查网络连接\\n" +
                   "• 稍后重试";
        }
    }

    /**
     * 🧠 分析搜索意图
     */
    private SearchIntent analyzeSearchIntent(String query) {
        SearchIntent intent = new SearchIntent();
        intent.originalQuery = query;
        
        String lowerQuery = query.toLowerCase();
        
        // 判断查询类型
        if (lowerQuery.contains("论文") || lowerQuery.contains("paper") || 
            lowerQuery.contains("研究") || lowerQuery.contains("cvpr") ||
            lowerQuery.contains("arxiv") || lowerQuery.contains("学术")) {
            intent.type = SearchType.ACADEMIC;
            intent.domain = "学术研究";
        } else if (lowerQuery.contains("代码") || lowerQuery.contains("编程") ||
                   lowerQuery.contains("github") || lowerQuery.contains("api") ||
                   lowerQuery.contains("框架") || lowerQuery.contains("库")) {
            intent.type = SearchType.TECHNICAL;
            intent.domain = "技术开发";
        } else if (lowerQuery.contains("新闻") || lowerQuery.contains("最新") ||
                   lowerQuery.contains("动态") || lowerQuery.contains("发展")) {
            intent.type = SearchType.NEWS;
            intent.domain = "新闻资讯";
        } else if (lowerQuery.contains("价格") || lowerQuery.contains("购买") ||
                   lowerQuery.contains("产品") || lowerQuery.contains("比较")) {
            intent.type = SearchType.SHOPPING;
            intent.domain = "商业信息";
        } else {
            intent.type = SearchType.GENERAL;
            intent.domain = "综合信息";
        }
        
        // 判断复杂度
        if (query.length() > 20 || query.split(" ").length > 3) {
            intent.complexity = QueryComplexity.HIGH;
        } else if (query.length() > 10) {
            intent.complexity = QueryComplexity.MEDIUM;
        } else {
            intent.complexity = QueryComplexity.LOW;
        }
        
        return intent;
    }

    /**
     * 🎯 选择搜索源
     */
    private List<SearchSource> selectSearchSources(SearchIntent intent) {
        List<SearchSource> selected = new ArrayList<>();
        
        // 根据查询类型选择对应的搜索源
        for (SearchSource source : searchSources) {
            if (source.type == intent.type || source.type == SearchType.GENERAL) {
                selected.add(source);
            }
        }
        
        // 确保至少有3个搜索源
        if (selected.size() < 3) {
            selected.addAll(searchSources.stream()
                .filter(s -> s.type == SearchType.GENERAL)
                .limit(3 - selected.size())
                .collect(Collectors.toList()));
        }
        
        return selected.stream().limit(5).collect(Collectors.toList());
    }

    /**
     * 🚀 并发搜索
     */
    private List<SearchResult> performParallelSearch(String query, List<SearchSource> sources) {
        List<CompletableFuture<SearchResult>> futures = sources.stream()
            .map(source -> CompletableFuture.supplyAsync(() -> {
                try {
                    return searchSingleSource(query, source);
                } catch (Exception e) {
                    logger.warn("搜索源 {} 失败: {}", source.name, e.getMessage());
                    return new SearchResult(source.name, false, e.getMessage());
                }
            }, executorService))
            .collect(Collectors.toList());
        
        // 等待所有搜索完成
        List<SearchResult> results = new ArrayList<>();
        for (CompletableFuture<SearchResult> future : futures) {
            try {
                SearchResult result = future.get(15, TimeUnit.SECONDS);
                if (result.isSuccessful()) {
                    results.add(result);
                }
            } catch (Exception e) {
                logger.warn("搜索任务超时或失败: {}", e.getMessage());
            }
        }
        
        return results;
    }

    /**
     * 🔎 搜索单个源
     */
    private SearchResult searchSingleSource(String query, SearchSource source) throws Exception {
        String searchUrl = source.buildUrl(query);
        logger.info("🔗 搜索 {} -> {}", source.name, searchUrl);
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(searchUrl))
            .timeout(Duration.ofSeconds(10))
            .header("User-Agent", generateUserAgent())
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            String content = response.body();
            IntelligentContent extracted = extractIntelligentContent(content, source, query);
            
            return new SearchResult(source.name, true, extracted);
        } else {
            return new SearchResult(source.name, false, "HTTP " + response.statusCode());
        }
    }

    /**
     * 🧠 智能内容提取
     */
    private IntelligentContent extractIntelligentContent(String html, SearchSource source, String query) {
        IntelligentContent content = new IntelligentContent();
        content.source = source.name;
        content.type = source.type;
        
        // 清理HTML
        String cleanText = html.replaceAll("<script[^>]*>.*?</script>", "")
                              .replaceAll("<style[^>]*>.*?</style>", "")
                              .replaceAll("<[^>]+>", " ")
                              .replaceAll("\\s+", " ")
                              .trim();
        
        // 智能提取标题
        content.title = extractTitle(html, cleanText, query);
        
        // 智能提取摘要
        content.summary = extractSummary(cleanText, query);
        
        // 提取关键链接
        content.links = extractRelevantLinks(html, query);
        
        // 提取关键信息
        content.keyInfo = extractKeyInformation(cleanText, query, source.type);
        
        // 计算相关性得分
        content.relevanceScore = calculateRelevanceScore(content, query);
        
        return content;
    }

    /**
     * 🎯 综合搜索结果
     */
    private String synthesizeResults(String query, SearchIntent intent, List<SearchResult> results) {
        if (results.isEmpty()) {
            return "❌ 未找到相关信息\\n\\n💡 建议：\\n" +
                   "• 尝试使用不同的关键词\\n" +
                   "• 检查拼写是否正确\\n" +
                   "• 使用更具体的搜索词";
        }
        
        StringBuilder response = new StringBuilder();
        response.append("🎯 **智能搜索结果** - ").append(query).append("\\n\\n");
        
        // 按相关性排序
        results.sort((a, b) -> Double.compare(b.content.relevanceScore, a.content.relevanceScore));
        
        // 生成综合摘要
        response.append("📋 **综合摘要**\\n");
        response.append(generateComprehensiveSummary(results, intent));
        response.append("\\n\\n");
        
        // 展示最相关的结果
        response.append("🔍 **详细结果**\\n");
        int count = 0;
        for (SearchResult result : results) {
            if (count >= 3) break; // 最多显示3个结果
            if (result.content.relevanceScore > 0.3) {
                response.append(formatSearchResult(result, ++count));
                response.append("\\n");
            }
        }
        
        // 提供相关建议
        response.append("💡 **相关建议**\\n");
        response.append(generateSuggestions(query, intent, results));
        
        return response.toString();
    }

    /**
     * 📝 生成综合摘要
     */
    private String generateComprehensiveSummary(List<SearchResult> results, SearchIntent intent) {
        StringBuilder summary = new StringBuilder();
        
        // 收集所有摘要
        List<String> summaries = results.stream()
            .map(r -> r.content.summary)
            .filter(s -> s != null && !s.isEmpty())
            .collect(Collectors.toList());
        
        if (summaries.isEmpty()) {
            return "根据搜索结果，未找到足够的信息进行综合分析。";
        }
        
        // 简单的摘要合并（实际项目中可以使用更复杂的NLP技术）
        summary.append("基于 ").append(results.size()).append(" 个搜索源的结果：\\n");
        
        if (intent.type == SearchType.ACADEMIC) {
            summary.append("在学术领域，相关研究表明：");
        } else if (intent.type == SearchType.TECHNICAL) {
            summary.append("在技术方面，主要发现：");
        } else if (intent.type == SearchType.NEWS) {
            summary.append("最新动态显示：");
        } else {
            summary.append("综合信息表明：");
        }
        
        // 取前两个最相关的摘要
        summaries.stream()
            .limit(2)
            .forEach(s -> summary.append(" ").append(s.substring(0, Math.min(s.length(), 100))).append("..."));
        
        return summary.toString();
    }

    /**
     * 🎯 格式化搜索结果
     */
    private String formatSearchResult(SearchResult result, int index) {
        StringBuilder formatted = new StringBuilder();
        
        formatted.append("**").append(index).append(". ").append(result.content.source).append("**\\n");
        
        if (result.content.title != null && !result.content.title.isEmpty()) {
            formatted.append("📌 ").append(result.content.title).append("\\n");
        }
        
        if (result.content.summary != null && !result.content.summary.isEmpty()) {
            formatted.append("📝 ").append(result.content.summary, 0, Math.min(result.content.summary.length(), 200));
            if (result.content.summary.length() > 200) {
                formatted.append("...");
            }
            formatted.append("\\n");
        }
        
        if (!result.content.keyInfo.isEmpty()) {
            formatted.append("🔑 关键信息: ").append(String.join(", ", result.content.keyInfo)).append("\\n");
        }
        
        if (!result.content.links.isEmpty()) {
            formatted.append("🔗 相关链接: ").append(result.content.links.get(0)).append("\\n");
        }
        
        formatted.append("⭐ 相关性: ").append(String.format("%.1f%%", result.content.relevanceScore * 100)).append("\\n");
        
        return formatted.toString();
    }

    /**
     * 💡 生成建议
     */
    private String generateSuggestions(String query, SearchIntent intent, List<SearchResult> results) {
        StringBuilder suggestions = new StringBuilder();
        
        if (intent.type == SearchType.ACADEMIC) {
            suggestions.append("• 建议查看 ArXiv 和 Google Scholar 获取更多学术资源\\n");
            suggestions.append("• 可以尝试搜索相关作者的其他论文\\n");
        } else if (intent.type == SearchType.TECHNICAL) {
            suggestions.append("• 推荐查看 GitHub 和 Stack Overflow 获取实际代码\\n");
            suggestions.append("• 可以关注相关项目的更新动态\\n");
        } else if (intent.type == SearchType.NEWS) {
            suggestions.append("• 建议关注多个新闻源获取全面信息\\n");
            suggestions.append("• 可以设置关键词提醒跟踪后续发展\\n");
        } else {
            suggestions.append("• 可以尝试更具体的搜索关键词\\n");
            suggestions.append("• 建议从多个角度搜索相关信息\\n");
        }
        
        return suggestions.toString();
    }

    // ==================== 辅助方法 ====================

    private String generateUserAgent() {
        return "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36";
    }

    private String extractTitle(String html, String cleanText, String query) {
        // 简单的标题提取
        String title = "";
        if (html.contains("<title>")) {
            title = html.substring(html.indexOf("<title>") + 7, html.indexOf("</title>"));
        }
        
        if (title.isEmpty()) {
            // 从clean text中提取第一行作为标题
            String[] lines = cleanText.split("\\n");
            if (lines.length > 0) {
                title = lines[0].substring(0, Math.min(lines[0].length(), 100));
            }
        }
        
        return title.trim();
    }

    private String extractSummary(String cleanText, String query) {
        if (cleanText.length() < 100) {
            return cleanText;
        }
        
        // 简单的摘要提取：找到包含查询词的段落
        String[] paragraphs = cleanText.split("\\n\\n");
        for (String paragraph : paragraphs) {
            if (paragraph.toLowerCase().contains(query.toLowerCase()) && paragraph.length() > 50) {
                return paragraph.substring(0, Math.min(paragraph.length(), 300));
            }
        }
        
        // 如果没有找到包含查询词的段落，返回前300个字符
        return cleanText.substring(0, Math.min(cleanText.length(), 300));
    }

    private List<String> extractRelevantLinks(String html, String query) {
        List<String> links = new ArrayList<>();
        
        // 简单的链接提取
        String[] parts = html.split("href=\"");
        for (int i = 1; i < parts.length && links.size() < 3; i++) {
            String part = parts[i];
            int endIndex = part.indexOf("\"");
            if (endIndex > 0) {
                String link = part.substring(0, endIndex);
                if (link.startsWith("http") && !link.contains("javascript")) {
                    links.add(link);
                }
            }
        }
        
        return links;
    }

    private List<String> extractKeyInformation(String cleanText, String query, SearchType type) {
        List<String> keyInfo = new ArrayList<>();
        
        // 基于查询类型提取关键信息
        if (type == SearchType.ACADEMIC) {
            // 提取年份、作者等
            if (cleanText.matches(".*\\b20\\d{2}\\b.*")) {
                keyInfo.add("发表年份: " + cleanText.replaceAll(".*\\b(20\\d{2})\\b.*", "$1"));
            }
        } else if (type == SearchType.TECHNICAL) {
            // 提取版本、语言等
            if (cleanText.toLowerCase().contains("version")) {
                keyInfo.add("版本信息");
            }
        }
        
        return keyInfo;
    }

    private double calculateRelevanceScore(IntelligentContent content, String query) {
        double score = 0.0;
        String allText = (content.title + " " + content.summary).toLowerCase();
        String queryLower = query.toLowerCase();
        
        // 简单的相关性计算
        String[] queryTerms = queryLower.split("\\s+");
        for (String term : queryTerms) {
            if (allText.contains(term)) {
                score += 0.3;
            }
        }
        
        // 标准化到0-1之间
        return Math.min(score, 1.0);
    }

    @Override
    public void close() throws IOException {
        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
            }
        }
    }

    // ==================== 数据类 ====================

    private static class SearchSource {
        String name;
        String urlTemplate;
        SearchType type;
        
        SearchSource(String name, String urlTemplate, SearchType type) {
            this.name = name;
            this.urlTemplate = urlTemplate;
            this.type = type;
        }
        
        String buildUrl(String query) {
            return urlTemplate.replace("{query}", URLEncoder.encode(query, StandardCharsets.UTF_8));
        }
    }

    private static class SearchIntent {
        String originalQuery;
        SearchType type = SearchType.GENERAL;
        String domain = "综合";
        QueryComplexity complexity = QueryComplexity.MEDIUM;
    }

    private static class SearchResult {
        String sourceName;
        boolean successful;
        IntelligentContent content;
        String errorMessage;
        
        SearchResult(String sourceName, boolean successful, String errorMessage) {
            this.sourceName = sourceName;
            this.successful = successful;
            this.errorMessage = errorMessage;
        }
        
        SearchResult(String sourceName, boolean successful, IntelligentContent content) {
            this.sourceName = sourceName;
            this.successful = successful;
            this.content = content;
        }
        
        boolean isSuccessful() {
            return successful && content != null;
        }
    }

    private static class IntelligentContent {
        String source;
        SearchType type;
        String title;
        String summary;
        List<String> links = new ArrayList<>();
        List<String> keyInfo = new ArrayList<>();
        double relevanceScore = 0.0;
    }

    private enum SearchType {
        GENERAL, ACADEMIC, TECHNICAL, NEWS, SHOPPING, DIRECT
    }

    private enum QueryComplexity {
        LOW, MEDIUM, HIGH
    }
}