#!/bin/bash

# StateGraph 集成测试运行脚本
# 用于验证 LangGraph4j StateGraph 架构的正常运行

echo "开始运行 StateGraph 集成测试..."
echo "======================================"

# 运行集成测试
mvn test -Dtest=StateGraphIntegrationTest

if [ $? -eq 0 ]; then
    echo "======================================"
    echo "✅ StateGraph 集成测试全部通过！"
    echo "LangGraph4j StateGraph 工作流运行正常"
else
    echo "======================================"
    echo "❌ StateGraph 集成测试失败"
    echo "请检查测试报告: target/surefire-reports/"
    exit 1
fi