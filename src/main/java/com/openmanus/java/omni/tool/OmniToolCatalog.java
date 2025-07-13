package com.openmanus.java.omni.tool;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class OmniToolCatalog {

    private final PythonTool pythonTool;
    private final FileTool fileTool;
    private final BrowserTool browserTool;

    public OmniToolCatalog(PythonTool pythonTool, FileTool fileTool, BrowserTool browserTool) {
        this.pythonTool = pythonTool;
        this.fileTool = fileTool;
        this.browserTool = browserTool;
    }

    public List<Object> getTools() {
        return Arrays.asList(pythonTool, fileTool, browserTool);
    }

    public List<ToolSpecification> getToolSpecifications() {
        return ToolSpecifications.toolSpecificationsFrom(getTools());
    }
} 