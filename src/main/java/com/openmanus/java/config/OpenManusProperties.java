package com.openmanus.java.config;

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
    
    // Getter方法
    public AppSettings getApp() {
        return app;
    }
    
    public LLMSettings getLlm() {
        return llm;
    }
    
    public SandboxSettings getSandbox() {
        return sandbox;
    }
    
    public BrowserSettings getBrowser() {
        return browser;
    }
    
    public ProxySettings getProxy() {
        return proxy;
    }
    
    public SearchSettings getSearch() {
        return search;
    }
    
    public MCPSettings getMcp() {
        return mcp;
    }
    
    public void setMcp(MCPSettings mcp) {
        this.mcp = mcp;
    }
    
    public RunflowSettings getRunflow() {
        return runflow;
    }
    
    public VectorDatabaseSettings getVectorDatabase() {
        return vectorDatabase;
    }
    
    // Setter methods
    public void setSandbox(SandboxSettings sandbox) {
        this.sandbox = sandbox;
    }
    
    public void setLlm(LLMSettings llm) {
        this.llm = llm;
    }
    
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
    
    public static class LLMSettings {
        private DefaultLLM defaultLlm = new DefaultLLM();
        
        public DefaultLLM getDefaultLlm() {
            return defaultLlm;
        }
        
        public void setDefaultLlm(DefaultLLM defaultLlm) {
            this.defaultLlm = defaultLlm;
        }
        
        public static class DefaultLLM {
            private String model = "gpt-4-turbo-preview";
            private String apiType = "openai";
            private String baseUrl = "https://api.openai.com/v1";
            private String apiKey;
            private double temperature = 0.7;
            private int maxTokens = 4096;
            private int timeout = 30;
            
            // Getter和Setter方法
            public String getModel() {
                return model;
            }
            
            public void setModel(String model) {
                this.model = model;
            }
            
            public String getApiType() {
                return apiType;
            }
            
            public void setApiType(String apiType) {
                this.apiType = apiType;
            }
            
            public String getBaseUrl() {
                return baseUrl;
            }
            
            public void setBaseUrl(String baseUrl) {
                this.baseUrl = baseUrl;
            }
            
            public String getApiKey() {
                return apiKey;
            }
            
            public void setApiKey(String apiKey) {
                this.apiKey = apiKey;
            }
            
            public double getTemperature() {
                return temperature;
            }
            
            public void setTemperature(double temperature) {
                this.temperature = temperature;
            }
            
            public int getMaxTokens() {
                return maxTokens;
            }
            
            public void setMaxTokens(int maxTokens) {
                this.maxTokens = maxTokens;
            }
            
            public int getTimeout() {
                return timeout;
            }
            
            public void setTimeout(int timeout) {
                this.timeout = timeout;
            }
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
        
        public boolean isUseSandbox() {
            return useSandbox;
        }
        
        public void setUseSandbox(boolean useSandbox) {
            this.useSandbox = useSandbox;
        }
        
        public int getTimeout() {
            return timeout;
        }
        
        public String getImage() {
            return image;
        }
        
        public String getWorkDir() {
            return workDir;
        }
        
        public String getMemoryLimit() {
            return memoryLimit;
        }
        
        public double getCpuLimit() {
            return cpuLimit;
        }
        
        public boolean isNetworkEnabled() {
            return networkEnabled;
        }
        
        public void setWorkDir(String workDir) {
            this.workDir = workDir;
        }
        
        public void setTimeout(int timeout) {
            this.timeout = timeout;
        }
        
        public void setImage(String image) {
            this.image = image;
        }
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
        
        public boolean isHeadless() {
            return headless;
        }
        
        public boolean isDisableSecurity() {
            return disableSecurity;
        }
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
        
        public String getEngine() {
            return engine;
        }
        
        public String getApiKey() {
            return apiKey;
        }
        
        public int getMaxResults() {
            return maxResults;
        }
        
        public List<String> getFallbackEngines() {
            return fallbackEngines;
        }
        
        public int getRetryDelay() {
            return retryDelay;
        }
        
        public int getMaxRetries() {
            return maxRetries;
        }
        
        public String getLang() {
            return lang;
        }
        
        public String getCountry() {
            return country;
        }
    }
    
    @Data
    public static class MCPSettings {
        private boolean enabled = false;
        private List<MCPServerConfig> servers = new ArrayList<>();
        private int connectionTimeout = 30000;
        private int readTimeout = 60000;
        private boolean logEvents = false;
        
        public boolean isEnabled() {
            return enabled;
        }
        
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
        
        public List<MCPServerConfig> getServers() {
            return servers;
        }
        
        public void setServers(List<MCPServerConfig> servers) {
            this.servers = servers;
        }
        
        public int getConnectionTimeout() {
            return connectionTimeout;
        }
        
        public void setConnectionTimeout(int connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
        }
        
        public int getReadTimeout() {
            return readTimeout;
        }
        
        public void setReadTimeout(int readTimeout) {
            this.readTimeout = readTimeout;
        }
        
        public boolean isLogEvents() {
            return logEvents;
        }
        
        public void setLogEvents(boolean logEvents) {
            this.logEvents = logEvents;
        }
        
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
            
            public void setId(String id) {
                this.id = id;
            }
            
            public String getName() {
                return name;
            }
            
            public void setName(String name) {
                this.name = name;
            }
            
            public String getType() {
                return type;
            }
            
            public void setType(String type) {
                this.type = type;
            }
            
            public TransportType getTransport() {
                return transport;
            }
            
            public void setTransport(TransportType transport) {
                this.transport = transport;
            }
            
            public String getCommand() {
                return command;
            }
            
            public void setCommand(String command) {
                this.command = command;
            }
            
            public List<String> getArgs() {
                return args;
            }
            
            public void setArgs(List<String> args) {
                this.args = args;
            }
            
            public String getUrl() {
                return url;
            }
            
            public void setUrl(String url) {
                this.url = url;
            }
            
            public String getHost() {
                return host;
            }
            
            public void setHost(String host) {
                this.host = host;
            }
            
            public int getPort() {
                return port;
            }
            
            public void setPort(int port) {
                this.port = port;
            }
            
            public Map<String, String> getEnv() {
                return env;
            }
            
            public void setEnv(Map<String, String> env) {
                this.env = env;
            }
            
            public Map<String, String> getEnvironment() {
                return environment.isEmpty() ? env : environment;
            }
            
            public void setEnvironment(Map<String, String> environment) {
                this.environment = environment;
            }
            
            public boolean isEnabled() {
                return enabled;
            }
            
            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }
            
            public int getTimeout() {
                return timeout;
            }
            
            public void setTimeout(int timeout) {
                this.timeout = timeout;
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
        
        public boolean isEnabled() {
            return enabled;
        }
        
        public String getHost() {
            return host;
        }
        
        public int getPort() {
            return port;
        }
        
        public String getCollectionName() {
            return collectionName;
        }
        
        public String getIndexType() {
            return indexType;
        }
        
        public String getMetricType() {
            return metricType;
        }
        
        public String getUsername() {
            return username;
        }
        
        public String getPassword() {
            return password;
        }
        
        public String getDatabaseName() {
            return databaseName;
        }
    }
}