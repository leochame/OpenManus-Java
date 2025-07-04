package com.openmanus.java.tool;

import com.openmanus.java.config.OpenManusProperties;
import com.openmanus.java.exception.ToolErrorException;
import com.openmanus.java.sandbox.SandboxClient;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;

@Component
public class FileTool implements Closeable {
    private static final Logger log = LoggerFactory.getLogger(FileTool.class);

    private final SandboxClient sandboxClient;
    private final boolean useSandbox;

    public FileTool() {
        this.useSandbox = false;
        this.sandboxClient = null;
    }

    @Autowired
    public FileTool(OpenManusProperties properties) {
        OpenManusProperties.SandboxSettings sandboxSettings = properties.getSandbox();
        this.useSandbox = sandboxSettings.isUseSandbox();
        this.sandboxClient = useSandbox ? new SandboxClient(properties) : null;
    }

    public FileTool(SandboxClient sandboxClient) {
        this.sandboxClient = sandboxClient;
        this.useSandbox = sandboxClient != null;
    }

    @Tool(name = "file_operations", value = "Read content from a file.")
    public String readFile(String path) throws Exception {
        log.debug("Reading file: {} (sandbox: {})", path, useSandbox);

        if (useSandbox) {
            return readFileFromSandbox(path);
        } else {
            return readFileLocally(path);
        }
    }

    @Tool("Write content to a file.")
    public String writeFile(String path, String content) {
        try {
            log.debug("Writing file: {} (sandbox: {})", path, useSandbox);

            if (useSandbox) {
                writeFileToSandbox(path, content);
            } else {
                writeFileLocally(path, content);
            }

            return "File written successfully to " + path;
        } catch (Exception e) {
            log.error("Error writing file {}: {}", path, e.getMessage(), e);
            return "Error writing file: " + e.getMessage();
        }
    }

    @Tool("Append content to a file.")
    public String appendToFile(String path, String content) {
        try {
            log.debug("Appending to file: {} (sandbox: {})", path, useSandbox);

            if (useSandbox) {
                appendToFileInSandbox(path, content);
            } else {
                appendToFileLocally(path, content);
            }

            return "Content appended successfully to " + path;
        } catch (Exception e) {
            log.error("Error appending to file {}: {}", path, e.getMessage(), e);
            return "Error appending to file: " + e.getMessage();
        }
    }

    @Tool("List files and directories in a given path.")
    public String listDirectory(String path) throws Exception {
        log.debug("Listing directory: {} (sandbox: {})", path, useSandbox);

        if (useSandbox) {
            return listDirectoryInSandbox(path);
        } else {
            return listDirectoryLocally(path);
        }
    }

    @Tool("Check if a file or directory exists.")
    public boolean fileExists(String path) {
        try {
            log.debug("Checking file existence: {} (sandbox: {})", path, useSandbox);

            if (useSandbox) {
                return fileExistsInSandbox(path);
            } else {
                return Files.exists(Paths.get(path));
            }
        } catch (Exception e) {
            log.error("Error checking file existence {}: {}", path, e.getMessage(), e);
            return false;
        }
    }

    @Tool("Check if a path points to a directory.")
    public boolean isDirectory(String path) {
        try {
            log.debug("Checking if directory: {} (sandbox: {})", path, useSandbox);

            if (useSandbox) {
                return isDirectoryInSandbox(path);
            } else {
                return Files.isDirectory(Paths.get(path));
            }
        } catch (Exception e) {
            log.error("Error checking if directory {}: {}", path, e.getMessage(), e);
            return false;
        }
    }

    // Local file operations
    private String readFileLocally(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    private void writeFileLocally(String path, String content) throws IOException {
        Path filePath = Paths.get(path);
        if (filePath.getParent() != null) {
            Files.createDirectories(filePath.getParent());
        }
        Files.write(filePath, content.getBytes(StandardCharsets.UTF_8));
    }

    private void appendToFileLocally(String path, String content) throws IOException {
        Path filePath = Paths.get(path);
        if (filePath.getParent() != null) {
            Files.createDirectories(filePath.getParent());
        }
        Files.write(filePath, content.getBytes(StandardCharsets.UTF_8),
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND);
    }

    private String listDirectoryLocally(String path) throws IOException {
        return Files.list(Paths.get(path))
                .map(Path::getFileName)
                .map(Path::toString)
                .collect(Collectors.joining("\n"));
    }

    // Sandbox file operations
    private String readFileFromSandbox(String path) throws Exception {
        SandboxClient.ExecutionResult result = sandboxClient.executeBash("cat \"" + path + "\"", 30);
        if (result.getExitCode() != 0) {
            throw new ToolErrorException("Failed to read file in sandbox: " + result.getStderr());
        }
        return result.getStdout();
    }

    private void writeFileToSandbox(String path, String content) throws Exception {
        // Create directory if needed
        String dirPath = Paths.get(path).getParent() != null ? Paths.get(path).getParent().toString() : ".";
        sandboxClient.executeBash("mkdir -p \"" + dirPath + "\"", 30);

        // Write file using cat with here document to handle special characters
        String command = String.format("cat > \"%s\" << 'EOF'\n%s\nEOF", path, content);
        SandboxClient.ExecutionResult result = sandboxClient.executeBash(command, 30);
        if (result.getExitCode() != 0) {
            throw new ToolErrorException("Failed to write file in sandbox: " + result.getStderr());
        }
    }

    private void appendToFileInSandbox(String path, String content) throws Exception {
        // Create directory if needed
        String dirPath = Paths.get(path).getParent() != null ? Paths.get(path).getParent().toString() : ".";
        sandboxClient.executeBash("mkdir -p \"" + dirPath + "\"", 30);

        // Append to file using cat with here document
        String command = String.format("cat >> \"%s\" << 'EOF'\n%s\nEOF", path, content);
        SandboxClient.ExecutionResult result = sandboxClient.executeBash(command, 30);
        if (result.getExitCode() != 0) {
            throw new ToolErrorException("Failed to append to file in sandbox: " + result.getStderr());
        }
    }

    private String listDirectoryInSandbox(String path) throws Exception {
        SandboxClient.ExecutionResult result = sandboxClient.executeBash("ls -1 \"" + path + "\"", 30);
        if (result.getExitCode() != 0) {
            throw new ToolErrorException("Failed to list directory in sandbox: " + result.getStderr());
        }
        return result.getStdout().trim();
    }

    private boolean fileExistsInSandbox(String path) throws Exception {
        SandboxClient.ExecutionResult result = sandboxClient
                .executeBash("test -e \"" + path + "\" && echo 'true' || echo 'false'", 30);
        return "true".equals(result.getStdout().trim());
    }

    private boolean isDirectoryInSandbox(String path) throws Exception {
        SandboxClient.ExecutionResult result = sandboxClient
                .executeBash("test -d \"" + path + "\" && echo 'true' || echo 'false'", 30);
        return "true".equals(result.getStdout().trim());
    }

    /**
     * Get file permissions
     */
    public String getFilePermissions(String path) {
        try {
            log.debug("Getting file permissions: {} (sandbox: {})", path, useSandbox);

            if (useSandbox) {
                return getFilePermissionsInSandbox(path);
            } else {
                return getFilePermissionsLocally(path);
            }
        } catch (Exception e) {
            log.error("Error getting file permissions {}: {}", path, e.getMessage(), e);
            return "Error getting file permissions: " + e.getMessage();
        }
    }

    /**
     * Set file permissions
     */
    public String setFilePermissions(String path, String permissions) {
        try {
            log.debug("Setting file permissions: {} to {} (sandbox: {})", path, permissions, useSandbox);

            if (useSandbox) {
                setFilePermissionsInSandbox(path, permissions);
            } else {
                setFilePermissionsLocally(path, permissions);
            }

            return "File permissions set successfully for " + path;
        } catch (Exception e) {
            log.error("Error setting file permissions {}: {}", path, e.getMessage(), e);
            return "Error setting file permissions: " + e.getMessage();
        }
    }

    private String getFilePermissionsLocally(String path) throws Exception {
        // For local files, return basic permission info
        Path filePath = Paths.get(path);
        if (!Files.exists(filePath)) {
            throw new Exception("File does not exist: " + path);
        }

        boolean readable = Files.isReadable(filePath);
        boolean writable = Files.isWritable(filePath);
        boolean executable = Files.isExecutable(filePath);

        return String.format("r%sw%sx%s", readable ? "" : "-", writable ? "" : "-", executable ? "" : "-");
    }

    private void setFilePermissionsLocally(String path, String permissions) throws Exception {
        // For local files, we can only set basic permissions
        Path filePath = Paths.get(path);
        if (!Files.exists(filePath)) {
            throw new Exception("File does not exist: " + path);
        }

        // This is a simplified implementation
        // In a real implementation, you might want to use PosixFilePermissions
        log.info("Setting permissions {} for file {} (simplified local implementation)", permissions, path);
    }

    private String getFilePermissionsInSandbox(String path) throws Exception {
        SandboxClient.ExecutionResult result = sandboxClient.executeBash("stat -c '%a' \"" + path + "\"", 30);
        if (result.getExitCode() != 0) {
            throw new Exception("Failed to get file permissions in sandbox: " + result.getStderr());
        }
        return result.getStdout().trim();
    }

    private void setFilePermissionsInSandbox(String path, String permissions) throws Exception {
        SandboxClient.ExecutionResult result = sandboxClient.executeBash("chmod " + permissions + " \"" + path + "\"",
                30);
        if (result.getExitCode() != 0) {
            throw new Exception("Failed to set file permissions in sandbox: " + result.getStderr());
        }
    }

    /**
     * Get the tool name
     */
    public String getName() {
        return "file_operations";
    }

    /**
     * Get the tool description
     */
    public String getDescription() {
        return "File operations tool for reading, writing, listing files and managing permissions";
    }

    /**
     * Delete a file
     */
    public String deleteFile(String path) throws Exception {
        log.debug("Deleting file: {} (sandbox: {})", path, useSandbox);

        if (useSandbox) {
            deleteFileInSandbox(path);
        } else {
            deleteFileLocally(path);
        }

        return "File deleted successfully: " + path;
    }

    /**
     * Get file information
     */
    public String getFileInfo(String path) throws Exception {
        log.debug("Getting file info: {} (sandbox: {})", path, useSandbox);

        if (useSandbox) {
            return getFileInfoInSandbox(path);
        } else {
            return getFileInfoLocally(path);
        }
    }

    private void deleteFileLocally(String path) throws Exception {
        Path filePath = Paths.get(path);
        if (!Files.exists(filePath)) {
            throw new Exception("File does not exist: " + path);
        }
        Files.delete(filePath);
    }

    private void deleteFileInSandbox(String path) throws Exception {
        SandboxClient.ExecutionResult result = sandboxClient.executeBash("rm \"" + path + "\"", 30);
        if (result.getExitCode() != 0) {
            throw new Exception("Failed to delete file in sandbox: " + result.getStderr());
        }
    }

    private String getFileInfoLocally(String path) throws Exception {
        Path filePath = Paths.get(path);
        if (!Files.exists(filePath)) {
            throw new Exception("File does not exist: " + path);
        }

        StringBuilder info = new StringBuilder();
        info.append("Path: ").append(path).append("\n");
        info.append("Size: ").append(Files.size(filePath)).append(" bytes\n");
        info.append("Directory: ").append(Files.isDirectory(filePath)).append("\n");
        info.append("Readable: ").append(Files.isReadable(filePath)).append("\n");
        info.append("Writable: ").append(Files.isWritable(filePath)).append("\n");
        info.append("Executable: ").append(Files.isExecutable(filePath)).append("\n");
        info.append("Modified: ").append(Files.getLastModifiedTime(filePath));

        return info.toString();
    }

    private String getFileInfoInSandbox(String path) throws Exception {
        SandboxClient.ExecutionResult result = sandboxClient.executeBash("ls -la \"" + path + "\"", 30);
        if (result.getExitCode() != 0) {
            throw new Exception("Failed to get file info in sandbox: " + result.getStderr());
        }
        return result.getStdout().trim();
    }

    /**
     * Create a directory
     */
    public String createDirectory(String path) {
        try {
            log.debug("Creating directory: {} (sandbox: {})", path, useSandbox);

            if (useSandbox) {
                createDirectoryInSandbox(path);
            } else {
                createDirectoryLocally(path);
            }

            return "Directory created successfully: " + path;
        } catch (Exception e) {
            log.error("Error creating directory {}: {}", path, e.getMessage(), e);
            return "Error creating directory: " + e.getMessage();
        }
    }

    private void createDirectoryLocally(String path) throws Exception {
        Path dirPath = Paths.get(path);
        Files.createDirectories(dirPath);
    }

    private void createDirectoryInSandbox(String path) throws Exception {
        SandboxClient.ExecutionResult result = sandboxClient.executeBash("mkdir -p \"" + path + "\"", 30);
        if (result.getExitCode() != 0) {
            throw new Exception("Failed to create directory in sandbox: " + result.getStderr());
        }
    }

    @Override
    public void close() throws IOException {
        if (sandboxClient != null) {
            sandboxClient.close();
        }
    }
}
