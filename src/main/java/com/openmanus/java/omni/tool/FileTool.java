package com.openmanus.java.omni.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.stream.Collectors;

/**
 * File operation tool
 * Using langchain4j @Tool annotation
 */
@Component
public class FileTool {
    
    private static final Logger logger = LoggerFactory.getLogger(FileTool.class);

    @Tool("Read file content")
    public String readFile(@P("File path") String filePath) {
        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                return "File does not exist: " + filePath;
            }
            
            if (!Files.isReadable(path)) {
                return "File is not readable: " + filePath;
        }
            
            String content = Files.readString(path);
            return "File content:\n" + content;
            
        } catch (IOException e) {
            logger.error("Failed to read file: {}", filePath, e);
            return "Failed to read file: " + e.getMessage();
        }
    }

    @Tool("Write file content")
    public String writeFile(@P("File path") String filePath, @P("File content") String content) {
        try {
            Path path = Paths.get(filePath);
            
            // Ensure parent directory exists
            Path parent = path.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
        }
            
            Files.writeString(path, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return "File written successfully: " + filePath;
            
        } catch (IOException e) {
            logger.error("Failed to write file: {}", filePath, e);
            return "Failed to write file: " + e.getMessage();
        }
    }

    @Tool("Append file content")
    public String appendFile(@P("File path") String filePath, @P("Content to append") String content) {
        try {
            Path path = Paths.get(filePath);
            
            // Ensure parent directory exists
            Path parent = path.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
        }
            
            Files.writeString(path, content, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            return "Content appended successfully: " + filePath;
            
        } catch (IOException e) {
            logger.error("Failed to append file: {}", filePath, e);
            return "Failed to append file: " + e.getMessage();
    }
    }
    
    @Tool("List directory contents")
    public String listDirectory(@P("Directory path") String dirPath) {
        try {
            Path path = Paths.get(dirPath);
            if (!Files.exists(path)) {
                return "Directory does not exist: " + dirPath;
        }
            
            if (!Files.isDirectory(path)) {
                return "Not a directory: " + dirPath;
            }
            
            List<String> items = Files.list(path)
                .map(item -> {
                    String name = item.getFileName().toString();
                    if (Files.isDirectory(item)) {
                        return "[DIR] " + name;
                    } else {
                        return "[FILE] " + name;
    }
                })
                .collect(Collectors.toList());
            
            return "Directory contents:\n" + String.join("\n", items);
            
        } catch (IOException e) {
            logger.error("Failed to list directory: {}", dirPath, e);
            return "Failed to list directory: " + e.getMessage();
        }
    }

    @Tool("Create directory")
    public String createDirectory(@P("Directory path") String dirPath) {
        try {
            Path path = Paths.get(dirPath);
            Files.createDirectories(path);
            return "Directory created successfully: " + dirPath;
            
        } catch (IOException e) {
            logger.error("Failed to create directory: {}", dirPath, e);
            return "Failed to create directory: " + e.getMessage();
        }
    }

    @Tool("Delete file or directory")
    public String deleteFile(@P("File or directory path") String path) {
        try {
        Path filePath = Paths.get(path);
        if (!Files.exists(filePath)) {
                return "File or directory does not exist: " + path;
        }

            if (Files.isDirectory(filePath)) {
                // Recursively delete directory
                deleteDirectoryRecursively(filePath);
                return "Directory deleted successfully: " + path;
        } else {
        Files.delete(filePath);
                return "File deleted successfully: " + path;
            }
            
        } catch (IOException e) {
            logger.error("Failed to delete: {}", path, e);
            return "Failed to delete: " + e.getMessage();
        }
    }
    
    private void deleteDirectoryRecursively(Path dir) throws IOException {
        Files.walk(dir)
            .sorted((a, b) -> b.compareTo(a)) // Delete child files first, then parent directory
            .forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    logger.error("Failed to delete file: {}", path, e);
                }
            });
    }
    
    @Tool("Check if file exists")
    public String fileExists(@P("File path") String filePath) {
        Path path = Paths.get(filePath);
        boolean exists = Files.exists(path);
        return exists ? "File exists: " + filePath : "File does not exist: " + filePath;
        }
    
    @Tool("Get file information")
    public String getFileInfo(@P("File path") String filePath) {
        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                return "File does not exist: " + filePath;
        }

        StringBuilder info = new StringBuilder();
            info.append("File information:\n");
            info.append("Path: ").append(path.toAbsolutePath()).append("\n");
            info.append("Size: ").append(Files.size(path)).append(" bytes\n");
            info.append("Type: ").append(Files.isDirectory(path) ? "Directory" : "File").append("\n");
            info.append("Readable: ").append(Files.isReadable(path)).append("\n");
            info.append("Writable: ").append(Files.isWritable(path)).append("\n");

        return info.toString();
            
        } catch (IOException e) {
            logger.error("Failed to get file information: {}", filePath, e);
            return "Failed to get file information: " + e.getMessage();
        }
    }
}
