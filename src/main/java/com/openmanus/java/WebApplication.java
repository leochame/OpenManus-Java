package com.openmanus.java;

import com.openmanus.java.agent.ManusAgent;
import com.openmanus.java.config.OpenManusProperties;
import com.openmanus.java.sandbox.SandboxClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;

import java.util.Scanner;
import java.util.Map;

/**
 * OpenManus Main Application Class
 * A minimalist AI Agent framework based on langchain4j and langgraph4j
 */
@SpringBootApplication(scanBasePackages = "com.openmanus.java")
public class WebApplication {

    private static final Logger logger = LoggerFactory.getLogger(WebApplication.class);

    public static void main(String[] args) {
        logger.info("? Starting OpenManus-Java");
        SpringApplication.run(WebApplication.class, args);
        logger.info("? OpenManus-Java started successfully!");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        logger.info("? Starting OpenManus-Java");
        logger.info("? OpenManus-Java started successfully!");
    }

    /**
     * Configure sandbox client
     */
    @Bean
    public SandboxClient sandboxClient(OpenManusProperties properties) {
        return new SandboxClient(properties);
    }

    /**
     * Command line runner - provides a simple interactive interface
     */
    @Bean
    public CommandLineRunner commandLineRunner(ManusAgent agent) {
        return args -> {
            if (args.length > 0 && args[0].equals("--cli")) {
                System.out.println("=== OpenManus CLI ===");
                System.out.println("Enter 'quit' to exit");
                
                Scanner scanner = new Scanner(System.in);
                while (true) {
                    System.out.print("> ");
                    String input = scanner.nextLine().trim();
                    
                    if ("quit".equalsIgnoreCase(input)) {
                        break;
                    }
                    
                    if (!input.isEmpty()) {
                        try {
                            Map<String, Object> result = agent.chatWithCot(input);
                            System.out.println("Answer: " + result.get("answer"));
                            System.out.println("Reasoning Process: " + result.get("cot"));
                        } catch (Exception e) {
                            System.err.println("Error: " + e.getMessage());
                        }
                    }
                }
                
                System.out.println("Goodbye!");
                System.exit(0);
            }
        };
    }
}