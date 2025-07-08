package com.openmanus.java.tool;

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
 * 文件操作工具
 * 使用 langchain4j 的 @Tool 注解
 */
@Component
public class FileTool {
    
    private static final Logger logger = LoggerFactory.getLogger(FileTool.class);

    @Tool("读取文件内容")
    public String readFile(@P("文件路径") String filePath) {
        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                return "文件不存在: " + filePath;
            }
            
            if (!Files.isReadable(path)) {
                return "文件不可读: " + filePath;
        }
            
            String content = Files.readString(path);
            return "文件内容:\n" + content;
            
        } catch (IOException e) {
            logger.error("读取文件失败: {}", filePath, e);
            return "读取文件失败: " + e.getMessage();
        }
    }

    @Tool("写入文件内容")
    public String writeFile(@P("文件路径") String filePath, @P("文件内容") String content) {
        try {
            Path path = Paths.get(filePath);
            
            // 确保父目录存在
            Path parent = path.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
        }
            
            Files.writeString(path, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return "文件写入成功: " + filePath;
            
        } catch (IOException e) {
            logger.error("写入文件失败: {}", filePath, e);
            return "写入文件失败: " + e.getMessage();
        }
    }

    @Tool("追加文件内容")
    public String appendFile(@P("文件路径") String filePath, @P("追加内容") String content) {
        try {
            Path path = Paths.get(filePath);
            
            // 确保父目录存在
            Path parent = path.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
        }
            
            Files.writeString(path, content, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            return "内容追加成功: " + filePath;
            
        } catch (IOException e) {
            logger.error("追加文件失败: {}", filePath, e);
            return "追加文件失败: " + e.getMessage();
    }
    }
    
    @Tool("列出目录内容")
    public String listDirectory(@P("目录路径") String dirPath) {
        try {
            Path path = Paths.get(dirPath);
            if (!Files.exists(path)) {
                return "目录不存在: " + dirPath;
        }
            
            if (!Files.isDirectory(path)) {
                return "不是目录: " + dirPath;
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
            
            return "目录内容:\n" + String.join("\n", items);
            
        } catch (IOException e) {
            logger.error("列出目录失败: {}", dirPath, e);
            return "列出目录失败: " + e.getMessage();
        }
    }

    @Tool("创建目录")
    public String createDirectory(@P("目录路径") String dirPath) {
        try {
            Path path = Paths.get(dirPath);
            Files.createDirectories(path);
            return "目录创建成功: " + dirPath;
            
        } catch (IOException e) {
            logger.error("创建目录失败: {}", dirPath, e);
            return "创建目录失败: " + e.getMessage();
        }
    }

    @Tool("删除文件或目录")
    public String deleteFile(@P("文件或目录路径") String path) {
        try {
        Path filePath = Paths.get(path);
        if (!Files.exists(filePath)) {
                return "文件或目录不存在: " + path;
        }

            if (Files.isDirectory(filePath)) {
                // 递归删除目录
                deleteDirectoryRecursively(filePath);
                return "目录删除成功: " + path;
        } else {
        Files.delete(filePath);
                return "文件删除成功: " + path;
            }
            
        } catch (IOException e) {
            logger.error("删除失败: {}", path, e);
            return "删除失败: " + e.getMessage();
        }
    }
    
    private void deleteDirectoryRecursively(Path dir) throws IOException {
        Files.walk(dir)
            .sorted((a, b) -> b.compareTo(a)) // 先删除子文件，再删除父目录
            .forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    logger.error("删除文件失败: {}", path, e);
                }
            });
    }
    
    @Tool("检查文件是否存在")
    public String fileExists(@P("文件路径") String filePath) {
        Path path = Paths.get(filePath);
        boolean exists = Files.exists(path);
        return exists ? "文件存在: " + filePath : "文件不存在: " + filePath;
        }
    
    @Tool("获取文件信息")
    public String getFileInfo(@P("文件路径") String filePath) {
        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                return "文件不存在: " + filePath;
        }

        StringBuilder info = new StringBuilder();
            info.append("文件信息:\n");
            info.append("路径: ").append(path.toAbsolutePath()).append("\n");
            info.append("大小: ").append(Files.size(path)).append(" 字节\n");
            info.append("类型: ").append(Files.isDirectory(path) ? "目录" : "文件").append("\n");
            info.append("可读: ").append(Files.isReadable(path)).append("\n");
            info.append("可写: ").append(Files.isWritable(path)).append("\n");

        return info.toString();
            
        } catch (IOException e) {
            logger.error("获取文件信息失败: {}", filePath, e);
            return "获取文件信息失败: " + e.getMessage();
        }
    }
}
