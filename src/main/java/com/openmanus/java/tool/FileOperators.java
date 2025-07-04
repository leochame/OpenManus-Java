package com.openmanus.java.tool;

import dev.langchain4j.agent.tool.Tool;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

public class FileOperators {

    @Tool("Read content from a file.")
    public String readFile(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)));
    }

    @Tool("Write content to a file.")
    public void writeFile(String path, String content) throws IOException {
        Files.write(Paths.get(path), content.getBytes());
    }

    @Tool("Check if path points to a directory.")
    public boolean isDirectory(String path) {
        return Files.isDirectory(Paths.get(path));
    }

    @Tool("Check if path exists.")
    public boolean exists(String path) {
        return Files.exists(Paths.get(path));
    }

    @Tool("Run a shell command and return its output.")
    public String runCommand(String command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command.split(" "))
                .redirectErrorStream(true)
                .start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        process.waitFor(120, TimeUnit.SECONDS);
        return output.toString();
    }
}