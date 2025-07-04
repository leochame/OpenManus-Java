package com.openmanus.java.agent;

import com.openmanus.java.model.*;
import com.openmanus.java.tool.ToolRegistry;
import com.openmanus.java.llm.LlmClient;
import com.openmanus.java.config.OpenManusProperties;
import com.openmanus.java.tool.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * A versatile general-purpose agent with support for both local and MCP tools.
 * Corresponds to app/agent/manus.py in the Python version.
 */
@Component
public class ManusAgent extends ToolCallAgent {
    private static final Logger logger = LoggerFactory.getLogger(ManusAgent.class);
    
    // Constants matching Python implementation
    private static final String AGENT_NAME = "Manus";
    private static final String AGENT_DESCRIPTION = "A versatile agent that can solve various tasks using multiple tools including MCP-based tools";
    private static final int DEFAULT_MAX_OBSERVE = 10000;
    private static final int DEFAULT_MAX_STEPS = 20;
    
    // MCP and browser support (placeholders for future implementation)
    private final Map<String, String> connectedServers;
    private boolean initialized;
    private BrowserContextHelper browserContextHelper;
    private final OpenManusProperties properties;
    
    /**
     * Constructor for ManusAgent with default configuration.
     */
    @Autowired
    public ManusAgent(LlmClient llm, Memory memory, OpenManusProperties properties) {
        this(llm, memory, properties, createDefaultToolRegistry(properties), ToolChoice.AUTO, createDefaultSpecialTools());
    }
    
    /**
     * Full constructor for ManusAgent.
     */
    public ManusAgent(LlmClient llm, Memory memory, OpenManusProperties properties, ToolRegistry availableTools, 
                     ToolChoice toolChoice, Set<String> specialToolNames) {
        super(AGENT_NAME, AGENT_DESCRIPTION, createSystemPrompt(properties), llm, memory, 
              availableTools, toolChoice, specialToolNames);
        
        this.properties = properties;
        this.connectedServers = new HashMap<>();
        this.initialized = true;  // Set to true after successful construction
        this.maxObserve = DEFAULT_MAX_OBSERVE;
        
        // Initialize browser context helper (placeholder)
        // this.browserContextHelper = new BrowserContextHelper(this);
        
        logger.info("ManusAgent initialized with {} tools", availableTools.getToolCount());
    }
    
    /**
     * Create default tool registry with general-purpose tools.
     * Corresponds to the available_tools field in Python Manus class.
     */
    private static ToolRegistry createDefaultToolRegistry(OpenManusProperties properties) {
        // Create registry with tool instances - ToolRegistry constructor handles registration
        return new ToolRegistry(
            new PythonTool(properties),
            new BrowserTool(properties),
            new FileTool(properties),
            new AskHumanTool(),
            new TerminateTool()
        );
    }
    
    /**
     * Create default special tool names.
     */
    private static Set<String> createDefaultSpecialTools() {
        Set<String> specialTools = new HashSet<>();
        specialTools.add("terminate");
        return specialTools;
    }
    
    /**
     * Create system prompt with workspace directory.
     * Corresponds to system_prompt field in Python Manus class.
     */
    private static String createSystemPrompt(OpenManusProperties properties) {
        String workspaceRoot = properties.getApp().getWorkspaceRoot();
        return String.format(
            "You are Manus, a versatile AI assistant capable of solving various tasks using multiple tools.\n" +
            "Current workspace directory: %s\n" +
            "You can execute Python code, browse the web, edit files, and interact with users.\n" +
            "Always think step by step and use the most appropriate tools for each task.",
            workspaceRoot
        );
    }
    
    /**
     * Factory method to create and properly initialize a Manus instance.
     * Corresponds to the create() classmethod in Python Manus class.
     */
    public static CompletableFuture<ManusAgent> create(LlmClient llm, Memory memory, OpenManusProperties properties) {
        return CompletableFuture.supplyAsync(() -> {
            ManusAgent instance = new ManusAgent(llm, memory, properties, createDefaultToolRegistry(properties), ToolChoice.AUTO, createDefaultSpecialTools());
            // Initialize MCP servers (placeholder for future implementation)
            // instance.initializeMcpServers();
            instance.initialized = true;
            return instance;
        });
    }
    
    /**
     * Initialize connections to configured MCP servers.
     * Corresponds to initialize_mcp_servers() method in Python Manus class.
     * Currently a placeholder for future MCP implementation.
     */
    public CompletableFuture<Void> initializeMcpServers() {
        return CompletableFuture.runAsync(() -> {
            logger.info("Initializing MCP servers (placeholder implementation)");
            // TODO: Implement MCP server connections
            // For now, just mark as initialized
            initialized = true;
        });
    }
    
    /**
     * Connect to an MCP server and add its tools.
     * Corresponds to connect_mcp_server() method in Python Manus class.
     * Currently a placeholder for future MCP implementation.
     */
    public CompletableFuture<Void> connectMcpServer(String serverUrl, String serverId, 
                                                   boolean useStdio, List<String> stdioArgs) {
        return CompletableFuture.runAsync(() -> {
            logger.info("Connecting to MCP server {} at {} (placeholder)", serverId, serverUrl);
            connectedServers.put(serverId, serverUrl);
            // TODO: Implement actual MCP connection logic
        });
    }
    
    /**
     * Disconnect from an MCP server and remove its tools.
     * Corresponds to disconnect_mcp_server() method in Python Manus class.
     */
    public CompletableFuture<Void> disconnectMcpServer(String serverId) {
        return CompletableFuture.runAsync(() -> {
            logger.info("Disconnecting from MCP server {} (placeholder)", serverId);
            if (serverId.isEmpty()) {
                connectedServers.clear();
            } else {
                connectedServers.remove(serverId);
            }
            // TODO: Implement actual MCP disconnection logic
        });
    }
    
    /**
     * Process current state and decide next actions with appropriate context.
     * Overrides the parent think() method to add browser context handling.
     * Corresponds to think() method in Python Manus class.
     */
    @Override
    public CompletableFuture<Boolean> think() {
        return CompletableFuture.supplyAsync(() -> {
            if (!initialized) {
                // Initialize MCP servers if not already done
                initializeMcpServers().join();
                initialized = true;
            }
            
            // Store original prompt
            String originalPrompt = nextStepPrompt;
            
            // Check if browser is in use in recent messages
            List<Message> recentMessages = memory.getMessages();
            int startIndex = Math.max(0, recentMessages.size() - 3);
            List<Message> lastThreeMessages = recentMessages.subList(startIndex, recentMessages.size());
            
            boolean browserInUse = lastThreeMessages.stream()
                .filter(msg -> msg.getToolCalls() != null)
                .flatMap(msg -> msg.getToolCalls().stream())
                .anyMatch(tc -> "browser_use".equals(tc.function().name()));
            
            if (browserInUse && browserContextHelper != null) {
                // Format next step prompt with browser context
                nextStepPrompt = browserContextHelper.formatNextStepPrompt();
            }
            
            try {
                // Call parent think() method
                return super.think().join();
            } finally {
                // Restore original prompt
                nextStepPrompt = originalPrompt;
            }
        });
    }
    
    /**
     * Clean up Manus agent resources.
     * Corresponds to cleanup() method in Python Manus class.
     */
    @Override
    public void cleanup() {
        super.cleanup();
        logger.info("Cleaning up ManusAgent resources...");
        
        // Clean up browser context helper
        if (browserContextHelper != null) {
            try {
                browserContextHelper.cleanup();
            } catch (Exception e) {
                logger.error("Error cleaning up browser context: {}", e.getMessage(), e);
            }
        }
        
        // Disconnect from all MCP servers if initialized
        if (initialized) {
            disconnectMcpServer("").join();
            initialized = false;
        }
        
        logger.info("ManusAgent cleanup complete.");
    }
    
    // Getters for monitoring and debugging
    public Map<String, String> getConnectedServers() {
        return new HashMap<>(connectedServers);
    }
    
    public boolean isInitialized() {
        return initialized;
    }
    
    /**
     * Get the properties configuration.
     */
    public OpenManusProperties getProperties() {
        return properties;
    }
    
    /**
     * Placeholder class for browser context helper.
     * Will be implemented when browser functionality is added.
     */
    private static class BrowserContextHelper {
        private final ManusAgent agent;
        
        public BrowserContextHelper(ManusAgent agent) {
            this.agent = agent;
        }
        
        public String formatNextStepPrompt() {
            // Placeholder implementation
            return "Continue with the browser task...";
        }
        
        public void cleanup() {
            // Placeholder cleanup
        }
    }
}