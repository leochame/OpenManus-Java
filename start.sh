#!/bin/bash

# OpenManus Java 启动脚本
# 用于启动交互式对话模式

set -e

echo "🚀 OpenManus Java 启动脚本"
echo "================================"

# 检查Java环境
if ! command -v java &> /dev/null; then
    echo "❌ 错误: 未找到Java环境"
    echo "请确保已安装Java 21或更高版本"
    exit 1
fi

# 检查Maven环境
if ! command -v mvn &> /dev/null; then
    echo "❌ 错误: 未找到Maven环境"
    echo "请确保已安装Maven 3.9或更高版本"
    exit 1
fi

# 检查Docker环境（可选）
if ! command -v docker &> /dev/null; then
    echo "⚠️ 警告: 未找到Docker环境，Python代码执行功能将受限"
    echo "建议安装Docker以获得完整功能"
fi

# 进入项目目录
cd "$(dirname "$0")"

echo "📦 正在构建项目..."
mvn clean compile -q

echo "🎯 启动交互式对话模式..."
echo "================================"
echo ""

# 使用InteractiveRunner启动应用
mvn spring-boot:run -Dspring-boot.run.main-class=com.openmanus.java.InteractiveRunner -q

echo ""
echo "👋 OpenManus已退出，感谢使用！" 