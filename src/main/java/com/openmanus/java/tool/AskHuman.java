package com.openmanus.java.tool;

import dev.langchain4j.agent.tool.Tool;

import java.util.Scanner;

public class AskHuman {

    @Tool("Use this tool to ask human for help.")
    public String execute(String inquire) {
        System.out.printf("Bot: %s%n%n", inquire);
        System.out.print("You: ");
        try (Scanner scanner = new Scanner(System.in)) {
            return scanner.nextLine().strip();
        }
    }
}