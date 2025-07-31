package com.openmanus.java.infra.config;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import lombok.Data;

/**
 * OpenManus Configuration Properties
 * 
 * Centralized configuration management for OpenManus project
 * Supports configuration from application.yml and environment variables
 */
@Data
@ConfigurationProperties(prefix = "openmanus")
public class OpenManusProperties {
    
    /**
     * Application basic configuration
     */
    @NestedConfigurationProperty
    private AppConfig app = new AppConfig();
    
    /**
     * LLM service configuration
     */
    @NestedConfigurationProperty
    private LlmConfig llm = new LlmConfig();
    
    /**
     * Sandbox environment configuration
     */
    @NestedConfigurationProperty
    private SandboxSettings sandbox = new SandboxSettings();
    
    /**
     * Browser automation configuration
     */
    @NestedConfigurationProperty
    private BrowserConfig browser = new BrowserConfig();
    
    /**
     * Proxy configuration
     */
    @NestedConfigurationProperty
    private ProxyConfig proxy = new ProxyConfig();
    
    /**
     * Search engine configuration
     */
    @NestedConfigurationProperty
    private SearchConfig search = new SearchConfig();
    
    /**
     * Runflow configuration
     */
    @NestedConfigurationProperty
    private RunflowConfig runflow = new RunflowConfig();
    
    /**
     * Application basic configuration
     */
    @Data
    public static class AppConfig {
        private String name = "OpenManus";
        private String version = "1.0.0";
        private String workspaceRoot = "./workspace";
        private String logLevel = "INFO";
    }
    
    /**
     * LLM service configuration
     */
    @Data
    public static class LlmConfig {
        @NestedConfigurationProperty
        private DefaultLLM defaultLlm = new DefaultLLM();
        
        @Data
        public static class DefaultLLM {
            private String model = "qwen-max";
            private String embeddingModel = "text-embedding-ada-002";
            private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1/";
            private String apiType = "openai";
            private Double temperature = 0.7;
            private Integer maxTokens = 8192;
            private Integer timeout = 120;
            private String apiKey = "";
        }
    }
    
    /**
     * Sandbox configuration
     */
    @Data
    public static class SandboxSettings {
        private boolean useSandbox = true;
        private String image = "python:3.9-slim";
        private String workDir = "/workspace";
        private String memoryLimit = "512m";
        private double cpuLimit = 1.0;
        private int timeout = 30;
        private boolean networkEnabled = false;
    }
    
    /**
     * Browser automation configuration
     */
    @Data
    public static class BrowserConfig {
        /**
         * Browser type (chrome, firefox, safari)
         */
        private String type = "chrome";
        
        /**
         * Whether to run in headless mode
         */
        private boolean headless = true;
        
        /**
         * Browser operation timeout (seconds)
         */
        private int timeout = 30;
        
        /**
         * User agent string
         */
        private String userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36";
    }
    
    /**
     * Proxy configuration
     */
    @Data
    public static class ProxyConfig {
        private boolean enabled = false;
        private String httpProxy = "";
        private String httpsProxy = "";
    }
    
    /**
     * Search engine configuration
     */
    @Data
    public static class SearchConfig {
        private String engine = "google";
        private String apiKey = "";
        private int maxResults = 10;
    }
    
    /**
     * Runflow configuration
     */
    @Data
    public static class RunflowConfig {
        private boolean enabled = false;
        private int maxSteps = 20;
        private int timeout = 300;
    }
}