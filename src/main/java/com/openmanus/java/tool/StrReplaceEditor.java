package com.openmanus.java.tool;

import com.openmanus.java.config.OpenManusProperties;
import com.openmanus.java.exception.ToolErrorException;
import com.openmanus.java.model.CLIResult;
import com.openmanus.java.sandbox.SandboxClient;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
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
import java.util.*;
import java.util.stream.Collectors;

@Component
public class StrReplaceEditor implements Closeable {
    private static final Logger log = LoggerFactory.getLogger(StrReplaceEditor.class);
    public static final String NAME = "str_replace_editor";
    public static final String DESCRIPTION = "Custom editing tool for viewing, creating and editing files\n" +
            "* State is persistent across command calls and discussions with the user\n" +
            "* If `path` is a file, `view` displays the result of applying `cat -n`. If `path` is a directory, `view` lists non-hidden files and directories up to 2 levels deep\n" +
            "* The `create` command cannot be used if the specified `path` already exists as a file\n" +
            "* If a `command` generates a long output, it will be truncated and marked with `<response clipped>`\n" +
            "* The `undo_edit` command will revert the last edit made to the file at `path`\n" +
            "\n" +
            "Notes for using the `str_replace` command:\n" +
            "* The `old_str` parameter should match EXACTLY one or more consecutive lines from the original file. Be mindful of whitespaces!\n" +
            "* If the `old_str` parameter is not unique in the file, the replacement will not be performed. Make sure to include enough context in `old_str` to make it unique\n" +
            "* The `new_str` parameter should contain the edited lines that should replace the `old_str`";

    private static final int SNIPPET_LINES = 4;
    private static final int MAX_RESPONSE_LEN = 16000;
    private static final String TRUNCATED_MESSAGE = "<response clipped><NOTE>To save on context only part of this file has been shown to you. " +
            "You should retry this tool after you have searched inside the file with `grep -n` " +
            "in order to find the line numbers of what you are looking for.</NOTE>";

    private final SandboxClient sandboxClient;
    private final boolean useSandbox;
    private final Map<String, List<String>> fileHistory = new HashMap<>();

    @Autowired
    public StrReplaceEditor(OpenManusProperties properties) {
        OpenManusProperties.SandboxSettings sandboxSettings = properties.getSandbox();
        this.useSandbox = sandboxSettings.isUseSandbox();
        this.sandboxClient = useSandbox ? new SandboxClient(properties) : null;
    }

    public StrReplaceEditor(SandboxClient sandboxClient) {
        this.sandboxClient = sandboxClient;
        this.useSandbox = sandboxClient != null;
    }

    @Tool("Execute file operations: view, create, str_replace, insert, undo_edit")
    public String execute(String command, String path, String fileText, List<Integer> viewRange, 
                         String oldStr, String newStr, Integer insertLine) {
        try {
            log.debug("Executing command: {} on path: {} (sandbox: {})", command, path, useSandbox);
            
            // Validate path and command
            validatePath(command, path);
            
            switch (command.toLowerCase()) {
                case "view":
                    return view(path, viewRange).getOutput();
                case "create":
                    if (fileText == null) {
                        throw new ToolErrorException("Parameter `file_text` is required for command: create");
                    }
                    return create(path, fileText).getOutput();
                case "str_replace":
                    if (oldStr == null) {
                        throw new ToolErrorException("Parameter `old_str` is required for command: str_replace");
                    }
                    return strReplace(path, oldStr, newStr).getOutput();
                case "insert":
                    if (insertLine == null) {
                        throw new ToolErrorException("Parameter `insert_line` is required for command: insert");
                    }
                    if (newStr == null) {
                        throw new ToolErrorException("Parameter `new_str` is required for command: insert");
                    }
                    return insert(path, insertLine, newStr).getOutput();
                case "undo_edit":
                    return undoEdit(path).getOutput();
                default:
                    throw new ToolErrorException("Unrecognized command: " + command + 
                            ". Allowed commands are: view, create, str_replace, insert, undo_edit");
            }
        } catch (Exception e) {
            log.error("Error executing command {}: {}", command, e.getMessage(), e);
            return "Error: " + e.getMessage();
        }
    }

    private void validatePath(String command, String path) throws Exception {
        Path pathObj = Paths.get(path);
        
        // Check if path is absolute
        if (!pathObj.isAbsolute()) {
            throw new ToolErrorException("The path " + path + " is not an absolute path");
        }
        
        // Only check if path exists for non-create commands
        if (!"create".equals(command)) {
            if (!exists(path)) {
                throw new ToolErrorException("The path " + path + " does not exist. Please provide a valid path.");
            }
            
            // Check if path is a directory
            if (isDirectory(path) && !"view".equals(command)) {
                throw new ToolErrorException("The path " + path + " is a directory and only the `view` command can be used on directories");
            }
        } else {
            // Check if file exists for create command
            if (exists(path)) {
                throw new ToolErrorException("File already exists at: " + path + ". Cannot overwrite files using command `create`.");
            }
        }
    }

    private CLIResult view(String path, List<Integer> viewRange) throws Exception {
        if (isDirectory(path)) {
            return viewDirectory(path);
        } else {
            return viewFile(path, viewRange);
        }
    }

    private CLIResult viewDirectory(String path) throws Exception {
        String findCmd = "find \"" + path + "\" -maxdepth 2 -not -path '*/\\.*'";
        
        if (useSandbox) {
            SandboxClient.ExecutionResult result = sandboxClient.executeBash(findCmd, 30);
            if (result.getExitCode() != 0) {
                throw new ToolErrorException("Failed to list directory: " + result.getStderr());
            }
            String output = "Here's the files and directories up to 2 levels deep in " + path + ", excluding hidden items:\n" + result.getStdout();
            return new CLIResult(output, "", "", 0, true);
        } else {
            // For local execution, use Java's Files.walk
            try {
                String output = Files.walk(Paths.get(path), 2)
                        .filter(p -> !p.getFileName().toString().startsWith("."))
                        .map(Path::toString)
                        .collect(Collectors.joining("\n"));
                return new CLIResult("Here's the files and directories up to 2 levels deep in " + path + ", excluding hidden items:\n" + output, "", "", 0, true);
            } catch (IOException e) {
                throw new ToolErrorException("Failed to list directory: " + e.getMessage());
            }
        }
    }

    private CLIResult viewFile(String path, List<Integer> viewRange) throws Exception {
        String fileContent = readFile(path);
        int initLine = 1;
        
        // Apply view range if specified
        if (viewRange != null && !viewRange.isEmpty()) {
            if (viewRange.size() != 2) {
                throw new ToolErrorException("Invalid `view_range`. It should be a list of two integers.");
            }
            
            String[] fileLines = fileContent.split("\n");
            int nLinesFile = fileLines.length;
            int startLine = viewRange.get(0);
            int endLine = viewRange.get(1);
            
            // Validate view range
            if (startLine < 1 || startLine > nLinesFile) {
                throw new ToolErrorException(String.format("Invalid `view_range`: %s. Its first element `%d` should be within the range of lines of the file: [1, %d]", 
                        viewRange, startLine, nLinesFile));
            }
            if (endLine > nLinesFile) {
                throw new ToolErrorException(String.format("Invalid `view_range`: %s. Its second element `%d` should be smaller than the number of lines in the file: `%d`", 
                        viewRange, endLine, nLinesFile));
            }
            if (endLine != -1 && endLine < startLine) {
                throw new ToolErrorException(String.format("Invalid `view_range`: %s. Its second element `%d` should be larger or equal than its first `%d`", 
                        viewRange, endLine, startLine));
            }
            
            // Apply range
            if (endLine == -1) {
                fileContent = String.join("\n", Arrays.copyOfRange(fileLines, startLine - 1, fileLines.length));
            } else {
                fileContent = String.join("\n", Arrays.copyOfRange(fileLines, startLine - 1, endLine));
            }
            initLine = startLine;
        }
        
        return new CLIResult(makeOutput(fileContent, path, initLine), "", "", 0, true);
    }

    private CLIResult create(String path, String fileText) throws Exception {
        writeFile(path, fileText);
        fileHistory.computeIfAbsent(path, k -> new ArrayList<>()).add(fileText);
        return new CLIResult("File created successfully at: " + path, "", "", 0, true);
    }

    private CLIResult strReplace(String path, String oldStr, String newStr) throws Exception {
        // Read file content and expand tabs
        String fileContent = readFile(path).replace("\t", "    ");
        oldStr = oldStr.replace("\t", "    ");
        newStr = newStr != null ? newStr.replace("\t", "    ") : "";
        
        // Check if old_str is unique in the file
        int occurrences = countOccurrences(fileContent, oldStr);
        if (occurrences == 0) {
            throw new ToolErrorException("No replacement was performed, old_str `" + oldStr + "` did not appear verbatim in " + path + ".");
        } else if (occurrences > 1) {
            // Find line numbers of occurrences
            String[] fileContentLines = fileContent.split("\n");
            List<Integer> lines = new ArrayList<>();
            for (int i = 0; i < fileContentLines.length; i++) {
                if (fileContentLines[i].contains(oldStr)) {
                    lines.add(i + 1);
                }
            }
            throw new ToolErrorException("No replacement was performed. Multiple occurrences of old_str `" + oldStr + "` in lines " + lines + ". Please ensure it is unique");
        }
        
        // Replace old_str with new_str
        String newFileContent = fileContent.replace(oldStr, newStr);
        
        // Write the new content to the file
        writeFile(path, newFileContent);
        
        // Save the original content to history
        fileHistory.computeIfAbsent(path, k -> new ArrayList<>()).add(fileContent);
        
        // Create a snippet of the edited section
        int replacementLine = fileContent.substring(0, fileContent.indexOf(oldStr)).split("\n").length - 1;
        int startLine = Math.max(0, replacementLine - SNIPPET_LINES);
        int endLine = replacementLine + SNIPPET_LINES + (newStr != null ? newStr.split("\n").length : 0);
        String[] newFileLines = newFileContent.split("\n");
        String snippet = String.join("\n", Arrays.copyOfRange(newFileLines, startLine, Math.min(endLine + 1, newFileLines.length)));
        
        // Prepare the success message
        String successMsg = "The file " + path + " has been edited. " +
                makeOutput(snippet, "a snippet of " + path, startLine + 1) +
                "Review the changes and make sure they are as expected. Edit the file again if necessary.";
        
        return new CLIResult(successMsg, "", "", 0, true);
    }

    private CLIResult insert(String path, int insertLine, String newStr) throws Exception {
        // Read and prepare content
        String fileText = readFile(path).replace("\t", "    ");
        newStr = newStr.replace("\t", "    ");
        String[] fileTextLines = fileText.split("\n");
        int nLinesFile = fileTextLines.length;
        
        // Validate insert_line
        if (insertLine < 0 || insertLine > nLinesFile) {
            throw new ToolErrorException(String.format("Invalid `insert_line` parameter: %d. It should be within the range of lines of the file: [0, %d]", 
                    insertLine, nLinesFile));
        }
        
        // Perform insertion
        String[] newStrLines = newStr.split("\n");
        List<String> newFileTextLines = new ArrayList<>();
        newFileTextLines.addAll(Arrays.asList(Arrays.copyOfRange(fileTextLines, 0, insertLine)));
        newFileTextLines.addAll(Arrays.asList(newStrLines));
        newFileTextLines.addAll(Arrays.asList(Arrays.copyOfRange(fileTextLines, insertLine, fileTextLines.length)));
        
        // Create a snippet for preview
        List<String> snippetLines = new ArrayList<>();
        snippetLines.addAll(Arrays.asList(Arrays.copyOfRange(fileTextLines, Math.max(0, insertLine - SNIPPET_LINES), insertLine)));
        snippetLines.addAll(Arrays.asList(newStrLines));
        snippetLines.addAll(Arrays.asList(Arrays.copyOfRange(fileTextLines, insertLine, Math.min(insertLine + SNIPPET_LINES, fileTextLines.length))));
        
        // Join lines and write to file
        String newFileText = String.join("\n", newFileTextLines);
        String snippet = String.join("\n", snippetLines);
        
        writeFile(path, newFileText);
        fileHistory.computeIfAbsent(path, k -> new ArrayList<>()).add(fileText);
        
        // Prepare success message
        String successMsg = "The file " + path + " has been edited. " +
                makeOutput(snippet, "a snippet of the edited file", Math.max(1, insertLine - SNIPPET_LINES + 1)) +
                "Review the changes and make sure they are as expected (correct indentation, no duplicate lines, etc). Edit the file again if necessary.";
        
        return new CLIResult(successMsg, "", "", 0, true);
    }

    private CLIResult undoEdit(String path) throws Exception {
        List<String> history = fileHistory.get(path);
        if (history == null || history.isEmpty()) {
            throw new ToolErrorException("No edit history found for " + path + ".");
        }
        
        String oldText = history.remove(history.size() - 1);
        writeFile(path, oldText);
        
        return new CLIResult("Last edit to " + path + " undone successfully. " + makeOutput(oldText, path), "", "", 0, true);
    }

    private String makeOutput(String fileContent, String fileDescriptor, int initLine) {
        fileContent = maybeTruncate(fileContent);
        fileContent = fileContent.replace("\t", "    ");
        
        // Add line numbers to each line
        String[] lines = fileContent.split("\n");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            result.append(String.format("%6d\t%s\n", i + initLine, lines[i]));
        }
        
        return "Here's the result of running `cat -n` on " + fileDescriptor + ":\n" + result.toString();
    }

    private String makeOutput(String fileContent, String fileDescriptor) {
        return makeOutput(fileContent, fileDescriptor, 1);
    }

    private String maybeTruncate(String content) {
        if (content.length() <= MAX_RESPONSE_LEN) {
            return content;
        }
        return content.substring(0, MAX_RESPONSE_LEN) + TRUNCATED_MESSAGE;
    }

    private int countOccurrences(String text, String pattern) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(pattern, index)) != -1) {
            count++;
            index += pattern.length();
        }
        return count;
    }

    // File operation methods
    private String readFile(String path) throws Exception {
        if (useSandbox) {
            SandboxClient.ExecutionResult result = sandboxClient.executeBash("cat \"" + path + "\"", 30);
            if (result.getExitCode() != 0) {
                throw new ToolErrorException("Failed to read file in sandbox: " + result.getStderr());
            }
            return result.getStdout();
        } else {
            try {
                return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new ToolErrorException("Failed to read file: " + e.getMessage());
            }
        }
    }

    private void writeFile(String path, String content) throws Exception {
        if (useSandbox) {
            // Create directory if needed
            Path pathObj = Paths.get(path);
            if (pathObj.getParent() != null) {
                String dirPath = pathObj.getParent().toString();
                sandboxClient.executeBash("mkdir -p \"" + dirPath + "\"", 30);
            }
            
            // Write file using cat with here document to handle special characters
            String command = String.format("cat > \"%s\" << 'EOF'\n%s\nEOF", path, content);
            SandboxClient.ExecutionResult result = sandboxClient.executeBash(command, 30);
            if (result.getExitCode() != 0) {
                throw new ToolErrorException("Failed to write file in sandbox: " + result.getStderr());
            }
        } else {
            try {
                Path filePath = Paths.get(path);
                if (filePath.getParent() != null) {
                    Files.createDirectories(filePath.getParent());
                }
                Files.write(filePath, content.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new ToolErrorException("Failed to write file: " + e.getMessage());
            }
        }
    }

    private boolean exists(String path) throws Exception {
        if (useSandbox) {
            SandboxClient.ExecutionResult result = sandboxClient.executeBash("test -e \"" + path + "\" && echo 'true' || echo 'false'", 30);
            return "true".equals(result.getStdout().trim());
        } else {
            return Files.exists(Paths.get(path));
        }
    }

    private boolean isDirectory(String path) throws Exception {
        if (useSandbox) {
            SandboxClient.ExecutionResult result = sandboxClient.executeBash("test -d \"" + path + "\" && echo 'true' || echo 'false'", 30);
            return "true".equals(result.getStdout().trim());
        } else {
            return Files.isDirectory(Paths.get(path));
        }
    }

    @Override
    public void close() throws IOException {
        if (sandboxClient != null) {
            sandboxClient.close();
        }
    }
}