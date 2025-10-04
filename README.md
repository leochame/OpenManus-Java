# OpenManusJava

<div align="center">

<img src="assets/img/logo.png" alt="OpenManusJava Logo" width="256" height="256">

**An Intelligent Thinking System Based on Java - A Multi-modal Agent Framework with Fast/Slow Thinking**

[![Java](https://img.shields.io/badge/Java-21+-orange)](https://openjdk.java.net/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.0-green)](https://spring.io/projects/spring-boot)
[![LangChain4j](https://img.shields.io/badge/LangChain4j-1.1.0-yellow)](https://github.com/langchain4j/langchain4j)
[![License](https://img.shields.io/badge/License-MIT-blue)](LICENSE)

[🚀 Quick Start](#-quick-start) •
[🎯 Features](#-features) •
[🏗️ Architecture](#️-architecture) •

</div>

## 📋 Project Overview

OpenManusJava is an intelligent thinking system developed based on Spring Boot and LangChain4j. It adopts a "fast thinking/slow thinking" dual-mode architecture, combining the efficiency of direct output with the depth of a think-execute-reflect loop. The system can automatically or manually select the most appropriate thinking mode according to task complexity, significantly improving the processing quality of complex tasks.

### 🎯 Features

#### 🧠 Multi-modal Intelligent Thinking
- **Fast Thinking Mode**: Direct execution for high efficiency, suitable for simple tasks.
- **Slow Thinking Mode**: Think-execute-reflect loop, suitable for complex tasks.
- **Automatic Mode**: Intelligently selects the thinking mode based on task complexity.

#### 💭 Intelligent Agent System
- **FastThinkWorkflow**: A workflow for quick responses.
- **ThinkDoReflectWorkflow**: A workflow for cyclic reflection.
- **Multiple Specialized Agents**: Specialized agents for thinking, execution, reflection, etc.

#### 🔧 Tool Ecosystem
- **Code Execution**: Executes code and analyzes the results.
- **File Operations**: Manages files and content.
- **Web Access**: Intelligently retrieves information.

#### 🎨 User Interface
- **Modern 3-Column Workspace**:
  - **Left**: An intelligent chat panel for core human-computer interaction.
  - **Middle**: A versatile tool panel displaying structured search results, tool outputs, and files.
  - **Right**: A browser workspace with multi-tab support, address bar navigation, and dual-mode (Web/VNC) capabilities.
- **Real-time Thinking Process**: Visualizes the AI's thinking steps and logs.
- **Responsive Design**: Adapts to desktop, tablet, and mobile devices.

## 🏗️ Architecture

### Core Architecture Diagram

```mermaid
graph TD
    User --> UI[Web Interface]
    UI --> Controller[AgentController]
    
    Controller --> Service[AgentService]
    
    subgraph "Workflow"
        Service -->|Complex Task| TDR[ThinkDoReflectWorkflow<br/>Deep Thinking]
        Service -->|Simple Task| FT[FastThinkWorkflow<br/>Quick Response]
    end
    
    TDR --> TA[ThinkingAgent<br/>Analyze & Plan]
    TA --> EA[ExecutionAgent<br/>Execute Task]
    EA --> RA[ReflectionAgent<br/>Evaluate Result]
    RA -->|Task Complete| FinalResult[Final Result]
    RA -->|Needs More Work| TA
    
    subgraph "Tool Layer"
        CodeTool[Code Execution Tool]
        FileTool[File Operation Tool]
        SearchTool[Information Retrieval Tool]
    end
    
    EA --> CodeTool
    EA --> FileTool
    EA --> SearchTool
    
    FT --> FinalResult
    FinalResult --> WebSocket --> UI
```

### Technology Stack

| **Component** | **Technology** | **Purpose** |
|----------|-------------|---------|
| **Backend Framework** | Spring Boot 3.2.0 | Core application framework |
| **AI Integration** | LangChain4j 1.1.0 | LLM integration and multi-agent collaboration |
| **Frontend** | Vue.js 3 + Element Plus | Modern, responsive user interface |
| **Real-time Comms** | WebSocket + STOMP | Real-time messaging and log streaming |
| **API** | RESTful API | Service interface |
| **Documentation** | Markdown | Project documentation |

## 🚀 Quick Start

### Prerequisites

- **Java 21+**
- **Maven 3.9+**
- **Alibaba Cloud Bailian API Key** (or other supported LLM service)

### Installation

1. **Clone the project**
   ```bash
   git clone https://github.com/OpenManus/OpenManus-Java.git
   cd OpenManus-Java
   ```

2. **Configure the environment**
   Copy `src/main/resources/application-example.yml` to `src/main/resources/application.yaml` and fill in your LLM API Key:
   ```yaml
   openmanus:
     llm:
       default-llm:
         # Fill in your API Key
         api-key: "sk-..."
   ```

3. **Start the application**
   ```bash
   ./mvnw spring-boot:run
   ```

4. **Access the service**
   Open your browser and go to: http://localhost:8080

## 📊 Usage

### Unified API Endpoint

All interactions are handled through a unified streaming API, `think-do-reflect-stream`, which automatically processes and returns real-time progress.

```bash
# Example Request
curl -X POST http://localhost:8080/api/agent/think-do-reflect-stream \
  -H "Content-Type: application/json" \
  -d '{"input": "Analyze the development trend of the tourism industry during the Spring Festival."}'
```

## 📬 Contact Me

- WeChat: leochame007
- Email: liulch.cn@gmail.com


## 🙏 Acknowledgements

Thanks to the following open-source projects for their support:
- [LangChain4j](https://github.com/langchain4j/langchain4j)
- [Spring Boot](https://spring.io/projects/spring-boot)

## 📄 License

This project is licensed under the [MIT License](LICENSE).

---

<div align="center">

**🌟 If this project is helpful to you, please give it a Star!**

</div> 