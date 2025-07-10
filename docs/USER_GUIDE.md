# OpenManus Java User Guide

Welcome to OpenManus Java! This is an intelligent agent system based on LangGraph4j StateGraph framework, supporting both Web UI and command-line interaction modes.

## ? Quick Start

### Starting the Application
```bash
# Web mode (recommended)
mvn spring-boot:run

# Command line mode
mvn spring-boot:run -Dspring-boot.run.arguments=--cli
```

### Configuration Requirements
- DashScope API key (required)
- Docker environment (for tool execution)
- Network connection (for search functionality)

## ? Web Interface Usage

### Access Method
Access after startup: `http://localhost:8080`

### Interface Layout
```
©°©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©´
©¦           Top Navigation Bar                ©¦
©À©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©È
©¦  Sidebar     ©¦        Main Content Area     ©¦
©¦              ©¦                              ©¦
©¦ - Connection ©¦  ? Chat | ? Viz | ? Monitor©¦
©¦ - Task Info  ©¦                              ©¦
©¦ - History    ©¦                              ©¦
©¸©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©Ø©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¼
```

### Chat Features

#### Basic Usage
1. Enter your question or task in the input box
2. Click "Send Message" button (or Ctrl+Enter)
3. View AI assistant's response and execution process

#### Supported Task Types
- **Information Search**: "Search Python best practices"
- **Code Programming**: "Write a sorting algorithm"
- **File Operations**: "Create project documentation"
- **Data Analysis**: "Analyze this CSV file"
- **Q&A Dialogue**: "Explain what microservices are"

### Visualization Features

#### StateGraph Status Graph
- **Green**: Node execution completed
- **Blue**: Currently executing node
- **Yellow**: Tool call node
- **Red**: Error state node
- **Arrows**: State transition paths

#### Chain of Thought
- Display StateGraph node execution steps
- Show state transition decision process
- Track tool calls and state updates
- Visualize condition path selection

### Monitoring Features
- Real-time state events
- Tool execution monitoring
- System performance metrics
- Error log tracking

## ? Command Line Usage

### Starting and Basic Operations
```bash
# Start command line mode
mvn spring-boot:run -Dspring-boot.run.arguments=--cli

# Interaction example
? Enter your task: Search Java 21 new features
? Searching...
? Results compiled

# Exit program
? Enter your task: exit
```

### Available Commands
- **help** - Help information
- **tools** - View tool list
- **clear** - Clear conversation
- **exit** - Exit program

## ? StateGraph Architecture Features

### State Management
- **AgentState**: Unified management of conversation state, message history, and tool results
- **State Persistence**: Support for long-term conversation state maintenance
- **State Rollback**: Can roll back to previous stable state when errors occur

### Workflow Programming
- **Node Definition**: Thinking, action, observation, and other functional nodes
- **Conditional Routing**: Dynamically choose next step based on execution results
- **Parallel Execution**: Support for multiple tool calls in parallel
- **Error Recovery**: Built-in retry and error handling mechanisms

### Execution Flow
```
User Input ¡ú Think Node ¡ú Tool Call Node ¡ú Observe Node ¡ú Condition ¡ú Response
     ¡ü                                                    ¡ý
     ©¸©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤ Continue Loop ¡û©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¼
```

## ?? Core Features

### Intelligent Search
```
Input: "Search React Hooks best practices"
Output: Organized search results and best practice recommendations
```

### Code Programming
```
Input: "Write a calculator in Python"
Output: Complete calculator code + usage instructions
```

### File Operations
```
Input: "Create a project README file"
Output: Generated README.md based on project content
```

### Data Analysis
```
Input: "Analyze sales trends in sales.csv"
Output: Data statistics + trend analysis + chart explanation
```

## ? Usage Tips

### Question Techniques
- **Be Specific**: "Write a sorting algorithm in Python" ?
- **Avoid Vague**: "Help me write some code" ?

### Task Breakdown
- **Complex Tasks**: Break down into multiple simple steps
- **File Operations**: Clearly specify file paths and formats
- **Search Tasks**: Provide keywords and search scope

### Context Utilization
- AI remembers conversation history
- Can continue previous tasks
- Supports multi-round deep dialogue

## ?? Tool Documentation

### Available Tools
- **Python Executor**: Run Python code
- **File Tool**: Read/write file operations
- **Search Tool**: Network information search
- **Browser Tool**: Web content extraction
- **Terminal Tool**: End current task

### Intelligent Tool Dispatch
StateGraph intelligently selects tools based on current state and task requirements:
- **State Analysis**: Analyze current AgentState to determine needed tools
- **Parallel Calls**: Support multiple tools executing simultaneously
- **Result Merging**: Merge tool execution results into state
- **Dynamic Routing**: Choose next node based on tool execution results

#### Tool Selection Strategy
- Programming Tasks ¡ú Python Executor Node
- Information Query ¡ú Search Tool Node
- File Processing ¡ú File Tool Node
- Complex Tasks ¡ú Multi-tool Parallel Node

## ?? Important Notes

### Security Reminders
- Code executes in sandbox environment
- Sensitive operations need confirmation
- Back up important files in advance

### Performance Suggestions
- Complex tasks may take longer
- Network searches affected by network conditions
- Limited number of concurrent tasks

### Error Handling
- Clear prompts for task failures
- Can retry or adjust requests
- Check monitoring panel for details

## ? FAQ

**Q: How to change API key?**
A: Modify configuration in `application.yml` or set environment variable

**Q: Why no search results?**
A: Check network connection and API key configuration

**Q: How to clear conversation history?**
A: Click "Clear Chat" button in Web UI or enter `clear` in command line

**Q: What to do if task execution takes too long?**
A: Can wait for completion or restart application

**Q: How to view detailed error information?**
A: Check monitoring tab in Web UI or application logs

## ? More Resources

- **API Documentation**: http://localhost:8080/swagger-ui.html
- **System Monitor**: http://localhost:8080/actuator/health
- **Project Homepage**: https://github.com/OpenManus/OpenManus-Java

---

? **Tip**: Make full use of the Web UI's visualization features to better understand AI's working process!
