# OpenManus-Java 快速启动指南

本文档将指导您如何快速启动并运行 OpenManus-Java 项目。

## 1. 先决条件

在开始之前，请确保您的开发环境中已安装以下软件：

- **Java 21**: 项目需要 Java 21 (JDK 21) 或更高版本。
- **Maven 3.6+**: 用于项目构建和依赖管理。
- **Docker**: (推荐) 用于一键启动应用，并为 Agent 提供沙箱环境。

## 2. 配置

### 2.1. API 密钥配置

项目运行需要一些外部服务的 API 密钥。我们使用 `.env` 文件来管理这些密钥，以确保安全。

1.  **复制 `.env` 文件**:
    在项目根目录下，找到 `dotenv.example` 文件，并复制一份，将其重命名为 `.env`。

    ```bash
    cp dotenv.example .env
    ```

2.  **编辑 `.env` 文件**:
    打开 `.env` 文件，填入您自己的 API 密钥。

    ```dotenv
    # -----------------------------------------------------------------------------
    # API Keys for OpenManus Java
    # -----------------------------------------------------------------------------

    # DashScope API Key (必需)
    # 这是项目默认使用的大语言模型服务。
    # 获取地址: https://help.aliyun.com/zh/dashscope/developer-reference/activate-dashscope-and-create-an-api-key
    OPENMANUS_LLM_DEFAULT_LLM_API_KEY=your-dashscope-api-key-here
    # 兼容旧命名（仍支持）：
    # OPENMANUS_LLM_DEFAULTLLM_APIKEY=your-dashscope-api-key-here

    # Serper API Key (可选)
    # 用于 Agent 的网页搜索功能。
    # 获取地址: https://serper.dev
    SERPER_API_KEY=your-serper-api-key-here

    # OpenAI API Key (可选)
    # 如果您想切换到 OpenAI 模型，请配置此项。
    # 获取地址: https://platform.openai.com/api-keys
    OPENAI_API_KEY=your-openai-api-key-here
    ```

    **注意**:
    - `OPENMANUS_LLM_DEFAULT_LLM_API_KEY` 是项目运行所必需的。
    - 其他 API 密钥是可选的，但会影响部分 Agent 功能（如网页搜索）。

### 2.2. 应用配置 (可选)

配置参考文件为 `src/main/resources/application-example.yml`，包含完整的可配置项示例。

如果您需要覆盖某些配置（例如，更换 LLM 模型或修改端口），建议创建一个 `src/main/resources/application-local.yml` 文件，并在其中写入您需要修改的配置。此文件不会被 Git 跟踪，可以避免将个人配置提交到代码库。

启动时使用 local profile 使其生效，例如：

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

## 3. 启动应用

我们推荐使用 Docker Compose 进行一键启动，这是最简单快捷的方式。

### 3.1. 使用 Docker Compose 启动 (推荐)

在项目根目录下，运行以下命令：

```bash
docker-compose up --build
```

这个命令会：
1.  **构建 Docker 镜像**: 根据 `Dockerfile` 中的定义，编译项目并构建一个可运行的镜像。
2.  **创建并启动容器**: 使用构建好的镜像启动一个 Docker 容器。
3.  **加载环境变量**: 从 `.env` 文件中读取 API 密钥并注入到容器中。
4.  **挂载数据卷**: 将本地的 `workspace` 和 `logs` 目录挂载到容器中，方便查看和持久化数据。

启动成功后，您应该能看到类似以下的日志输出：

```
openmanus-java    | ...
openmanus-java    | ...  Tomcat started on port(s): 8089 (http) with context path ''
openmanus-java    | ...  Started WebApplication in ... seconds
```

### 3.2. 本地直接运行 (不使用 Docker)

如果您不想使用 Docker，也可以直接在本地运行。

1.  **设置环境变量**:
    确保您的 shell 环境中已经设置了 `.env` 文件中的环境变量。您可以手动 `export`，或者使用 `source .env` 等命令。

2.  **使用 Maven 运行**:
    在项目根目录下，运行以下 Maven 命令：

    ```bash
    mvn spring-boot:run
    ```

    Maven 会自动编译项目并启动 Spring Boot 应用。

## 4. 访问应用

应用启动后，您可以通过以下地址访问：

- **Web 界面**: [http://localhost:8089](http://localhost:8089)
- **API 文档 (Swagger UI)**: [http://localhost:8089/swagger-ui.html](http://localhost:8089/swagger-ui.html)

现在，您可以开始与 OpenManus-Java Agent 进行交互了！
