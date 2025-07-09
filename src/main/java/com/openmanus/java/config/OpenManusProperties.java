package com.openmanus.java.config;

import jdk.jfr.DataAmount;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import lombok.Data;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Spring Boot配置属性类，用于自动绑定application.yml中的配置
 */
@Data
@ConfigurationProperties(prefix = "openmanus")
public class OpenManusProperties {
    
    @NestedConfigurationProperty
    private AppSettings app = new AppSettings();
    
    @NestedConfigurationProperty
    private LLMSettings llm = new LLMSettings();
    
    @NestedConfigurationProperty
    private SandboxSettings sandbox = new SandboxSettings();
    
    @NestedConfigurationProperty
    private BrowserSettings browser = new BrowserSettings();
    
    @NestedConfigurationProperty
    private ProxySettings proxy = new ProxySettings();
    
    @NestedConfigurationProperty
    private SearchSettings search = new SearchSettings();
    
    @NestedConfigurationProperty
    private MCPSettings mcp = new MCPSettings();
    
    @NestedConfigurationProperty
    private RunflowSettings runflow = new RunflowSettings();
    
    @NestedConfigurationProperty
    private VectorDatabaseSettings vectorDatabase = new VectorDatabaseSettings();

    
    @Data
    public static class AppSettings {
        private String name = "OpenManus";
        private String version = "1.0.0";
        private String workspaceRoot = "./workspace";
        private String logLevel = "INFO";
        
        public String getWorkspaceRoot() {
            return workspaceRoot;
        }
        
        public void setWorkspaceRoot(String workspaceRoot) {
            this.workspaceRoot = workspaceRoot;
        }
    }
    @Data
    public static class LLMSettings {
        private DefaultLLM defaultLlm = new DefaultLLM();
        
        public DefaultLLM getDefaultLlm() {
            return defaultLlm;
        }
        
        public void setDefaultLlm(DefaultLLM defaultLlm) {
            this.defaultLlm = defaultLlm;
        }
        @Data
        public static class DefaultLLM {
            private String model = "gpt-4-turbo-preview";
            private String apiType = "openai";
            private String baseUrl = "https://api.openai.com/v1";
            private String apiKey;
            private double temperature = 0.7;
            private int maxTokens = 4096;
            private int timeout = 30;

        }
    }
    
    @Data
    public static class SandboxSettings {
        private String type = "docker";
        private boolean useSandbox = true;
        private String image = "python:3.11-slim";
        private String workDir = "/workspace";
        private String memoryLimit = "512m";
        private double cpuLimit = 1.0;
        private int timeout = 120;
        private boolean networkEnabled = true;
    }
    
    @Data
    public static class BrowserSettings {
        private String type = "chrome";
        private boolean headless = true;
        private boolean disableSecurity = true;
        private int timeout = 30;
        private String userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36";
        private List<String> extraChromiumArgs;
        private String chromeInstancePath;
        private String wssUrl;
        private String cdpUrl;
        private int maxContentLength = 2000;
    }
    
    @Data
    public static class ProxySettings {
        private boolean enabled = false;
        private String httpProxy = "";
        private String httpsProxy = "";
    }
    
    @Data
    public static class SearchSettings {
        private String engine = "google";
        private String apiKey = "";
        private int maxResults = 10;
        private List<String> fallbackEngines = List.of("DuckDuckGo", "Baidu", "Bing");
        private int retryDelay = 60;
        private int maxRetries = 3;
        private String lang = "en";
        private String country = "us";
    }
    
    @Data
    public static class MCPSettings {
        private boolean enabled = false;
        private List<MCPServerConfig> servers = new ArrayList<>();
        private int connectionTimeout = 30000;
        private int readTimeout = 60000;
        private boolean logEvents = false;
        @Data
        public static class MCPServerConfig {
            private String id;
            private String name;
            private String type; // "stdio", "http", "websocket"
            private TransportType transport = TransportType.STDIO;
            private String command; // for stdio transport
            private List<String> args = new ArrayList<>(); // for stdio transport
            private String url; // for http/websocket transport
            private String host; // for http transport
            private int port; // for http transport
            private Map<String, String> env = new HashMap<>(); // environment variables
            private Map<String, String> environment = new HashMap<>(); // environment variables (alias)
            private boolean enabled = true;
            private int timeout = 30000;
            
            public enum TransportType {
                STDIO, HTTP, WEBSOCKET
            }

            public MCPServerConfig() {}

            public MCPServerConfig(String name, String type) {
                this.name = name;
                this.type = type;
            }

            public String getId() {
                return id != null ? id : name;
            }
        }
    }
    
    @Data
    public static class RunflowSettings {
        private boolean enabled = false;
        private int maxSteps = 20;
        private int timeout = 300;
    }
    
    @Data
    public static class VectorDatabaseSettings {
        private boolean enabled = false;
        private String host = "localhost";
        private int port = 19530;
        private String collectionName = "openmanus_memory";
        private String indexType = "IVF_FLAT";
        private String metricType = "L2";
        private String username = "";
        private String password = "";
        private String databaseName = "default";
    }
}