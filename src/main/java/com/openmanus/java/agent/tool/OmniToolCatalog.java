package com.openmanus.java.agent.tool;

import org.springframework.context.annotation.Bean;
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

} 