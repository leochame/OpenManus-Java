# OpenManus Java

[![Build Status](https://github.com/OpenManus/OpenManus-Java/actions/workflows/ci.yml/badge.svg)](https://github.com/OpenManus/OpenManus-Java/actions)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java Version](https://img.shields.io/badge/Java-21-blue.svg)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Docker](https://img.shields.io/badge/Docker-Ready-blue.svg)](https://www.docker.com/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-red.svg)](https://maven.apache.org/)

🤖 **OpenManus Java** 是一个基于Spring Boot的智能代理系统，集成了阿里云百炼API，具备强大的任务执行能力。它可以自动执行Python代码、处理文件、搜索网络信息，并与用户进行智能交互。

## ✨ 功能特性

- 🐍 **Python代码执行** - 在Docker沙箱中安全执行Python代码
- 📁 **文件操作** - 读取、写入、创建和管理文件目录
- 🌐 **网络搜索** - 实时网络信息搜索和内容分析
- 💬 **智能对话** - 支持自然语言交互和任务分配
- 🛡️ **安全沙箱** - 隔离的Docker环境执行代码
- 🔧 **多工具集成** - 10+种工具支持复杂任务执行
- 📊 **数据分析** - 内置数据处理和可视化能力
- 🎯 **任务规划** - 智能任务分解和执行规划

## 🚀 快速开始

### 前置条件

- Java 21+
- Maven 3.9+
- Docker (推荐)
- 阿里云百炼API Key

### 安装步骤

1. **克隆项目**
```bash
git clone https://github.com/OpenManus/OpenManus-Java.git
cd OpenManus-Java
```

2. **配置API密钥**
```bash
# 编辑配置文件
vim src/main/resources/application.yml

# 修改API密钥
openmanus:
  llm:
    default-llm:
      api-key: "your-api-key-here"
```

3. **启动Docker服务**
```bash
# macOS/Linux
sudo systemctl start docker

# 或者确保Docker Desktop正在运行
```

4. **运行项目**
```bash
# 编译项目
mvn clean compile

# 启动交互模式
./run_interactive.sh

# 或者运行测试
./run-tests.sh
```

## 📖 使用指南

### 交互模式

启动交互模式后，您可以直接与AI助手对话：

```bash
$ ./run_interactive.sh

🎉 欢迎使用 OpenManus Java 版本!
💡 您可以输入任务，让AI助手帮您完成

👤 请输入您的任务: 帮我分析sales.csv文件中的销售数据
🤖 我来帮您分析sales.csv文件...
```

### 任务示例

#### 1. 数据分析任务
```
用户输入: "分析data.csv文件中的销售数据，生成可视化图表"

OpenManus会：
1. 读取CSV文件
2. 使用Python进行数据分析
3. 生成统计报告
4. 创建可视化图表
5. 保存结果文件
```

#### 2. 代码开发任务
```
用户输入: "帮我写一个Python脚本，实现快速排序算法"

OpenManus会：
1. 编写快速排序代码
2. 在沙箱中测试运行
3. 提供代码说明
4. 保存到文件
```

#### 3. 信息搜索任务
```
用户输入: "搜索最新的人工智能技术发展趋势"

OpenManus会：
1. 执行网络搜索
2. 收集相关信息
3. 整理和分析内容
4. 生成总结报告
```

## 🔧 配置说明

### 应用配置 (`application.yml`)

```yaml
openmanus:
  llm:
    default-llm:
      model: "qwen-plus"
      base-url: "https://dashscope.aliyuncs.com/compatible-mode/v1/"
      api-key: "your-api-key"
      max-tokens: 8192
      temperature: 0.7
      
  sandbox:
    type: "docker"
    image: "python:3.11-slim"
    memory-limit: "512m"
    cpu-limit: 1.0
    timeout: 120
```

### Docker配置

项目使用Docker沙箱环境确保代码执行安全：

- **基础镜像**: `python:3.11-slim`
- **内存限制**: 512MB
- **CPU限制**: 1.0核
- **执行超时**: 120秒

## 🏗️ 项目结构

```
OpenManus-Java/
├── src/
│   ├── main/
│   │   ├── java/com/openmanus/java/
│   │   │   ├── agent/          # AI代理实现
│   │   │   ├── config/         # 配置类
│   │   │   ├── llm/            # LLM客户端
│   │   │   ├── sandbox/        # Docker沙箱
│   │   │   ├── tool/           # 工具集合
│   │   │   └── model/          # 数据模型
│   │   └── resources/
│   │       └── application.yml # 应用配置
│   └── test/                   # 测试代码
├── docker-compose.test.yml     # Docker测试配置
├── run_interactive.sh          # 交互模式启动脚本
├── run-tests.sh               # 测试脚本
├── pom.xml                    # Maven配置
└── README.md                  # 项目文档
```

## 🧪 测试

### 运行所有测试

```bash
# 运行完整测试套件
./run-tests.sh

# 或者使用Maven
mvn test -Dspring.profiles.active=test
```

### 运行特定测试

```bash
# 运行集成测试
mvn test -Dtest=ManusAgentIntegrationTest

# 运行工具测试
mvn test -Dtest=ToolSystemTest
```

## 📊 性能指标

- **启动时间**: ~10-15秒
- **内存使用**: ~200-500MB
- **响应时间**: 1-5秒 (取决于任务复杂度)
- **并发支持**: 单任务处理
- **API调用**: 平均20-50 tokens/请求

## 🛠️ 开发

### 开发环境设置

1. **安装开发工具**
```bash
# 安装Java 21
brew install openjdk@21

# 安装Maven
brew install maven

# 安装Docker
brew install docker
```

2. **IDE配置**
- 推荐使用IntelliJ IDEA或Eclipse
- 安装Spring Boot插件
- 配置代码格式化规则

### 构建和打包

```bash
# 编译项目
mvn clean compile

# 运行测试
mvn test

# 打包应用
mvn package

# 构建Docker镜像
docker build -t openmanus-java .
```

## 🤝 贡献指南

我们欢迎所有形式的贡献！请查看 [CONTRIBUTING.md](CONTRIBUTING.md) 了解详细信息。

### 贡献流程

1. Fork 本项目
2. 创建功能分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 创建Pull Request

### 代码规范

- 使用标准Java代码风格
- 添加适当的注释和文档
- 编写单元测试
- 确保所有测试通过

## 🐛 问题报告

如果您发现bug或有功能建议，请在 [Issues](https://github.com/OpenManus/OpenManus-Java/issues) 页面创建issue。

## 📝 许可证

本项目采用MIT许可证 - 查看 [LICENSE](LICENSE) 文件了解详情。

## 🌟 致谢

- [Spring Boot](https://spring.io/projects/spring-boot) - 应用框架
- [阿里云百炼](https://dashscope.aliyuncs.com/) - AI模型服务
- [Docker](https://www.docker.com/) - 容器化解决方案
- [Maven](https://maven.apache.org/) - 项目管理工具

## 📞 联系我们

- 项目主页: [https://github.com/OpenManus/OpenManus-Java](https://github.com/OpenManus/OpenManus-Java)
- 问题报告: [GitHub Issues](https://github.com/OpenManus/OpenManus-Java/issues)
- 讨论区: [GitHub Discussions](https://github.com/OpenManus/OpenManus-Java/discussions)

---

⭐ 如果这个项目对您有帮助，请给我们一个star！ 