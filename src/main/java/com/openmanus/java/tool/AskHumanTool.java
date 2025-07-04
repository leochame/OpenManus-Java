package com.openmanus.java.tool;

import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.InputStream;

/**
 * Tool for asking human for help.
 * Corresponds to ask_human.py in the Python version.
 */
@Component
public class AskHumanTool {

    private static final Logger log = LoggerFactory.getLogger(AskHumanTool.class);

    public static final String NAME = "ask_human";
    public static final String DESCRIPTION = "Use this tool to ask human for help.";

    private Scanner scanner;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public AskHumanTool() {
        this(System.in);
    }

    public AskHumanTool(InputStream inputStream) {
        this.scanner = new Scanner(inputStream);
    }

    /**
     * Close resources used by this tool.
     */
    public void close() {
        try {
            if (scanner != null) {
                scanner.close();
            }
        } catch (Exception e) {
            log.warn("Error closing scanner: {}", e.getMessage());
        }

        try {
            if (executor != null && !executor.isShutdown()) {
                executor.shutdown();
                if (!executor.awaitTermination(1, java.util.concurrent.TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            }
        } catch (Exception e) {
            log.warn("Error shutting down executor: {}", e.getMessage());
        }
    }

    @Tool(name = "ask_human", value = DESCRIPTION)
    public String execute(String inquire) {
        log.debug("Asking human: {}", inquire);
        System.out.println("Bot: " + inquire);
        System.out.print("\nYou: ");
        String response = "";
        try {
            response = scanner.nextLine().trim();
        } catch (Exception e) {
            log.warn("No input received or error: {}", e.getMessage());
            response = "Timeout: No response received";
        }
        log.debug("Human response: {}", response);
        return response;
    }

    /**
     * Asynchronous version for non-blocking human input
     */
    public CompletableFuture<String> executeAsync(String inquire) {
        return CompletableFuture.supplyAsync(() -> execute(inquire), executor);
    }

    /**
     * Alternative method name for consistency with Python version
     */
    public String ask(String inquire) {
        return execute(inquire);
    }

    /**
     * Alternative method name for test compatibility
     */
    public String askQuestion(String inquire) {
        return execute(inquire);
    }

    /**
     * Ask question with timeout for test compatibility
     */
    public String askQuestionWithTimeout(String inquire, int timeoutSeconds) {
        try {
            CompletableFuture<String> future = executeAsync(inquire);
            return future.get(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Timeout or error asking question: {}", e.getMessage());
            return "Timeout: No response received within " + timeoutSeconds + " seconds";
        }
    }

    /**
     * Get the tool name
     */
    public String getName() {
        return NAME;
    }

    /**
     * Get the tool description
     */
    public String getDescription() {
        return DESCRIPTION;
    }

    /**
     * Clean up resources
     */
}
