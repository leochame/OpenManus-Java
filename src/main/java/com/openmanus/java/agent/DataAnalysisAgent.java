package com.openmanus.java.agent;

import com.openmanus.java.model.Memory;
import com.openmanus.java.model.Message;
import com.openmanus.java.llm.LlmClient;
import com.openmanus.java.tool.ToolRegistry;
import com.openmanus.java.model.ToolChoice;

import java.util.HashSet;

/**
 * A specialized agent for data analysis tasks.
 * This agent is optimized for statistical analysis, data visualization, and data processing tasks.
 */
public class DataAnalysisAgent extends ToolCallAgent {

    private static final String DATA_ANALYSIS_SYSTEM_PROMPT = """
            You are a specialized data analysis agent with expertise in:
            - Statistical analysis and data processing
            - Data visualization and chart creation
            - Data cleaning and transformation
            - Pattern recognition and trend analysis
            - Report generation from data insights
            
            Your capabilities include:
            - Loading and examining various data formats (CSV, JSON, Excel, etc.)
            - Performing statistical calculations (mean, median, correlation, regression, etc.)
            - Creating visualizations (charts, graphs, plots)
            - Identifying patterns, trends, and anomalies in data
            - Generating comprehensive data reports
            
            When working with data:
            1. Always start by examining the data structure and quality
            2. Clean and preprocess data as needed
            3. Apply appropriate statistical methods
            4. Create meaningful visualizations
            5. Provide clear insights and recommendations
            
            Use the available tools to:
            - Execute Python scripts for data analysis
            - Read and write files
            - Search for additional information when needed
            - Create and save visualizations
            
            Always explain your analysis process and findings clearly.
            """;

    public DataAnalysisAgent(LlmClient llm, Memory memory, ToolRegistry availableTools) {
        super("DataAnalysisAgent", "Specialized agent for data analysis tasks", DATA_ANALYSIS_SYSTEM_PROMPT,
              llm, memory, availableTools, ToolChoice.AUTO, new HashSet<String>());
        
        // Add system message for data analysis
        memory.addMessage(Message.systemMessage(DATA_ANALYSIS_SYSTEM_PROMPT));
    }

    @Override
    public void cleanup() {
        super.cleanup();
        // Additional cleanup for data analysis specific resources
        // This could include clearing temporary data files, closing database connections, etc.
    }
}