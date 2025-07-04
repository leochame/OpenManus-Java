package com.openmanus.java.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.openmanus.java.config.OpenManusProperties;
import com.openmanus.java.exception.ToolErrorException;
import dev.langchain4j.agent.tool.Tool;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Web search tool that supports multiple search engines with fallback.
 * Corresponds to web_search.py in the Python version.
 */
@Slf4j
@Component
public class WebSearchTool {
    private static final Logger logger = LoggerFactory.getLogger(WebSearchTool.class);

    private final OpenManusProperties.SearchSettings searchSettings;
    private final List<String> searchEngines;
    
    public static final String NAME = "web_search";
    public static final String DESCRIPTION = "Search the web for real-time information about any topic.\n" +
            "This tool returns comprehensive search results with relevant information, URLs, titles, and descriptions.\n" +
            "If the primary search engine fails, it automatically falls back to alternative engines.";
    
    /**
     * Represents a single search result.
     */
    @Data
    public static class SearchResult {
        private int position;
        private String url;
        private String title;
        private String description;
        private String source;
        private String rawContent;
        
        public SearchResult(int position, String url, String title, String description, String source) {
            this.position = position;
            this.url = url;
            this.title = title != null ? title : "";
            this.description = description != null ? description : "";
            this.source = source;
        }
        
        public int getPosition() {
            return position;
        }
        
        public String getUrl() {
            return url;
        }
        
        public String getTitle() {
            return title;
        }
        
        public String getDescription() {
            return description;
        }
        
        public String getSnippet() {
            return description;
        }
        
        public String getSource() {
            return source;
        }
        
        public String getRawContent() {
            return rawContent;
        }
        
        public void setRawContent(String rawContent) {
            this.rawContent = rawContent;
        }
    }
    
    /**
     * Metadata about the search operation.
     */
    @Data
    public static class SearchMetadata {
        private int totalResults;
        private String language;
        private String country;
        private List<String> enginesUsed;
        
        public SearchMetadata(int totalResults, String language, String country) {
            this.totalResults = totalResults;
            this.language = language;
            this.country = country;
            this.enginesUsed = new ArrayList<>();
        }
        
        public int getTotalResults() {
            return totalResults;
        }
        
        public String getLanguage() {
            return language;
        }
        
        public String getCountry() {
            return country;
        }
        
        public List<String> getEnginesUsed() {
            return enginesUsed;
        }
        
        public String getQuery() {
            return "";
        }
        
        public void setEnginesUsed(List<String> enginesUsed) {
            this.enginesUsed = enginesUsed;
        }
    }
    
    /**
     * Structured response from the web search tool.
     */
    @Data
    public static class SearchResponse {
        private String status;
        private String query;
        private List<SearchResult> results;
        private SearchMetadata metadata;
        private String error;
        
        public SearchResponse(String query) {
            this.query = query;
            this.results = new ArrayList<>();
            this.status = "success";
        }
        
        public List<SearchResult> getResults() {
            return results;
        }
        
        public SearchMetadata getMetadata() {
            return metadata;
        }
        
        public String getStatus() {
            return status;
        }
        
        public String getQuery() {
            return query;
        }
        
        public String getError() {
            return error;
        }
        
        public void setResults(List<SearchResult> results) {
            this.results = results;
        }
        
        public void setMetadata(SearchMetadata metadata) {
            this.metadata = metadata;
        }
        
        public void setStatus(String status) {
            this.status = status;
        }
        
        public void setError(String error) {
            this.error = error;
        }
        
        public String formatOutput() {
            if (error != null) {
                return "Error: " + error;
            }
            
            StringBuilder output = new StringBuilder();
            output.append("Search results for '").append(query).append("':");
            
            for (SearchResult result : results) {
                output.append("\n\n").append(result.position).append(". ").append(result.title);
                output.append("\n   URL: ").append(result.url);
                if (!result.description.isEmpty()) {
                    output.append("\n   Description: ").append(result.description);
                }
                if (result.rawContent != null && !result.rawContent.isEmpty()) {
                    String contentPreview = result.rawContent.length() > 1000 ? 
                        result.rawContent.substring(0, 1000) + "..." : result.rawContent;
                    output.append("\n   Content: ").append(contentPreview);
                }
            }
            
            if (metadata != null) {
                output.append("\n\nMetadata:");
                output.append("\n- Total results: ").append(metadata.totalResults);
                output.append("\n- Language: ").append(metadata.language);
                output.append("\n- Country: ").append(metadata.country);
            }
            
            return output.toString();
        }
    }
    
    @Autowired
    public WebSearchTool(OpenManusProperties properties) {
        this.searchSettings = properties.getSearch();
        this.searchEngines = Arrays.asList("serpapi", "duckduckgo", "bing"); // Available engines
    }
    
    @Tool(DESCRIPTION)
    public String search(String query) throws ToolErrorException {
        return search(query, 5, "en", "us", false);
    }
    
    @Tool("Search the web for real-time information with custom parameters.")
    public String search(String query, int numResults, String lang, String country, boolean fetchContent) throws ToolErrorException {
        logger.debug("Searching for: {} (results: {}, lang: {}, country: {}, fetch_content: {})", 
                    query, numResults, lang, country, fetchContent);
        
        if (query == null || query.trim().isEmpty()) {
            throw new ToolErrorException("Query is required for web search");
        }
        
        // Use config defaults if not specified
        String searchLang = (lang != null && !lang.isEmpty()) ? lang : 
                           (searchSettings.getLang() != null ? searchSettings.getLang() : "en");
        String searchCountry = (country != null && !country.isEmpty()) ? country : 
                              (searchSettings.getCountry() != null ? searchSettings.getCountry() : "us");
        
        SearchResponse response = executeSearch(query, numResults, searchLang, searchCountry, fetchContent);
        return response.formatOutput();
    }
    
    /**
     * Execute search with retry logic and fallback engines.
     */
    private SearchResponse executeSearch(String query, int numResults, String lang, String country, boolean fetchContent) {
        int maxRetries = searchSettings.getMaxRetries() > 0 ? searchSettings.getMaxRetries() : 3;
        int retryDelay = searchSettings.getRetryDelay() > 0 ? searchSettings.getRetryDelay() : 60;
        
        for (int retry = 0; retry <= maxRetries; retry++) {
            List<SearchResult> results = tryAllEngines(query, numResults, lang, country, fetchContent);
            
            if (!results.isEmpty()) {
                SearchResponse response = new SearchResponse(query);
                response.setResults(results);
                response.setMetadata(new SearchMetadata(results.size(), lang, country));
                return response;
            }
            
            if (retry < maxRetries) {
                logger.warn("All search engines failed. Waiting {} seconds before retry {}/{}", 
                           retryDelay, retry + 1, maxRetries);
                try {
                    Thread.sleep(retryDelay * 1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } else {
                logger.error("All search engines failed after {} retries. Giving up.", maxRetries);
            }
        }
        
        SearchResponse errorResponse = new SearchResponse(query);
        errorResponse.setError("All search engines failed to return results after multiple retries.");
        errorResponse.setStatus("error");
        return errorResponse;
    }
    
    /**
     * Try all search engines in the configured order.
     */
    private List<SearchResult> tryAllEngines(String query, int numResults, String lang, String country, boolean fetchContent) {
        List<String> engineOrder = getEngineOrder();
        List<String> failedEngines = new ArrayList<>();
        
        for (String engine : engineOrder) {
            try {
                logger.info("🔎 Attempting search with {}...", engine);
                List<SearchResult> results = performSearchWithEngine(engine, query, numResults, lang, country, fetchContent);
                
                if (!results.isEmpty()) {
                    if (!failedEngines.isEmpty()) {
                        logger.info("Search successful with {} after trying: {}", engine, String.join(", ", failedEngines));
                    }
                    return results;
                }
                failedEngines.add(engine);
            } catch (Exception e) {
                logger.warn("Search engine {} failed: {}", engine, e.getMessage());
                failedEngines.add(engine);
            }
        }
        
        if (!failedEngines.isEmpty()) {
            logger.error("All search engines failed: {}", String.join(", ", failedEngines));
        }
        return new ArrayList<>();
    }
    
    private List<String> getEngineOrder() {
        List<String> order = new ArrayList<>();
        
        // Add preferred engine first
        String preferred = searchSettings.getEngine();
        if (searchEngines.contains(preferred)) {
            order.add(preferred);
        }
        
        // Add fallback engines
        for (String fallback : searchSettings.getFallbackEngines()) {
            if (searchEngines.contains(fallback) && !order.contains(fallback)) {
                order.add(fallback);
            }
        }
        
        // Add remaining engines
        for (String engine : searchEngines) {
            if (!order.contains(engine)) {
                order.add(engine);
            }
        }
        
        return order;
    }
    
    /**
     * Perform search with a specific engine and return structured results.
     */
    private List<SearchResult> performSearchWithEngine(String engine, String query, int numResults, 
                                                       String lang, String country, boolean fetchContent) {
        switch (engine.toLowerCase()) {
            case "serpapi":
                return performSerpApiSearch(query, numResults, lang, country, fetchContent);
            case "duckduckgo":
                return performDuckDuckGoSearch(query, numResults, lang, country, fetchContent);
            case "bing":
                return performBingSearch(query, numResults, lang, country, fetchContent);
            default:
                logger.warn("Unsupported search engine: {}", engine);
                return new ArrayList<>();
        }
    }
    
    private List<SearchResult> performSerpApiSearch(String query, int numResults, String lang, String country, boolean fetchContent) {
        String apiKey = System.getenv("SERPAPI_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            logger.warn("SERPAPI_API_KEY environment variable is not set");
            return new ArrayList<>();
        }

        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.toString());
            String urlString = String.format(
                "https://serpapi.com/search.json?q=%s&num=%d&hl=%s&gl=%s&api_key=%s",
                encodedQuery, numResults, lang, country, apiKey
            );

            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(30000);

            InputStream responseStream = connection.getInputStream();
            JsonObject jsonResponse = JsonParser.parseReader(new InputStreamReader(responseStream)).getAsJsonObject();

            JsonArray organicResults = jsonResponse.getAsJsonArray("organic_results");
            if (organicResults == null) {
                logger.warn("No organic results found from SerpAPI");
                return new ArrayList<>();
            }

            return parseSearchResults(organicResults, fetchContent, "SerpAPI");

        } catch (IOException e) {
            logger.warn("Error during SerpAPI search: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
    
    private List<SearchResult> performDuckDuckGoSearch(String query, int numResults, String lang, String country, boolean fetchContent) {
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.toString());
            String urlString = String.format(
                "https://html.duckduckgo.com/html/?q=%s&kl=%s-%s",
                encodedQuery, lang, country
            );

            Document doc = Jsoup.connect(urlString)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(30000)
                    .get();

            List<SearchResult> results = new ArrayList<>();
            Elements resultElements = doc.select(".result");
            
            int count = 0;
            for (Element element : resultElements) {
                if (count >= numResults) break;
                
                var titleElement = element.selectFirst(".result__title a");
                var snippetElement = element.selectFirst(".result__snippet");
                
                if (titleElement != null) {
                    String title = titleElement.text();
                    String link = titleElement.attr("href");
                    String snippet = snippetElement != null ? snippetElement.text() : "";
                    
                    SearchResult result = new SearchResult(count + 1, link, title, snippet, "DuckDuckGo");
                    
                    if (fetchContent && !link.isEmpty()) {
                        String content = fetchWebContent(link);
                        if (content != null) {
                            result.setRawContent(content);
                        }
                    }
                    
                    results.add(result);
                    count++;
                }
            }
            
            if (results.isEmpty()) {
                logger.warn("No results found from DuckDuckGo");
            }
            
            return results;

        } catch (IOException e) {
            logger.warn("Error during DuckDuckGo search: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * Placeholder for Bing search implementation.
     */
    private List<SearchResult> performBingSearch(String query, int numResults, String lang, String country, boolean fetchContent) {
        // TODO: Implement Bing search API integration
        logger.warn("Bing search not yet implemented");
        return new ArrayList<>();
    }
    
    /**
     * Parse JSON search results into SearchResult objects.
     */
    private List<SearchResult> parseSearchResults(JsonArray organicResults, boolean fetchContent, String source) {
        List<SearchResult> results = new ArrayList<>();
        
        for (int i = 0; i < organicResults.size(); i++) {
            JsonObject resultJson = organicResults.get(i).getAsJsonObject();
            String title = resultJson.has("title") ? resultJson.get("title").getAsString() : "No title";
            String link = resultJson.has("link") ? resultJson.get("link").getAsString() : "";
            String snippet = resultJson.has("snippet") ? resultJson.get("snippet").getAsString() : "";

            SearchResult result = new SearchResult(i + 1, link, title, snippet, source);

            if (fetchContent && !link.isEmpty()) {
                String content = fetchWebContent(link);
                if (content != null) {
                    result.setRawContent(content);
                }
            }
            results.add(result);
        }

        return results;
    }
    
    /**
     * Format SearchResponse for output.
     */
    private String formatOutput(SearchResponse response) {
        if (response.getResults().isEmpty()) {
            return "No search results found.";
        }
        
        StringBuilder output = new StringBuilder();
        output.append(String.format("Search results for '%s':\n\n", response.getMetadata().getQuery()));
        
        for (SearchResult result : response.getResults()) {
            output.append(String.format("%d. %s\n", result.getPosition(), result.getTitle()));
            output.append(String.format("   URL: %s\n", result.getUrl()));
            output.append(String.format("   Description: %s\n", result.getSnippet()));
            
            if (result.getRawContent() != null && !result.getRawContent().isEmpty()) {
                output.append(String.format("   Content: %s\n", result.getRawContent()));
            }
            
            output.append("\n");
        }
        
        output.append(String.format("\nSearch completed using: %s\n", 
            response.getMetadata().getEnginesUsed().stream().collect(Collectors.joining(", "))));
        output.append(String.format("Total results: %d\n", response.getResults().size()));
        
        return output.toString();
    }
    
    private String fetchWebContent(String url) {
        try {
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                try {
                    Document doc = Jsoup.connect(url)
                            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                            .timeout(10000)
                            .get();
                    
                    // Remove script and style elements
                    doc.select("script, style, header, footer, nav").remove();
                    
                    String text = doc.text();
                    // Limit content size and clean up
                    String preview = text.substring(0, Math.min(text.length(), 1000));
                    return preview + (text.length() > 1000 ? "..." : "");
                } catch (IOException e) {
                    return null;
                }
            });
            
            return future.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.debug("Failed to fetch content from {}: {}", url, e.getMessage());
            return "Failed to fetch content.";
        }
    }
}