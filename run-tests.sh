#!/bin/bash

# OpenManus Java功能测试运行脚本
# 此脚本用于运行OpenManus Java版本的功能测试

set -e  # 遇到错误时退出

echo "=== OpenManus Java 功能测试 ==="
echo "开始时间: $(date)"
echo

# 检查Java版本
echo "检查Java版本..."
java -version
echo

# 检查Maven版本
echo "检查Maven版本..."
mvn -version
echo

# 检查Docker是否可用
echo "检查Docker状态..."
if command -v docker &> /dev/null && docker info &> /dev/null; then
    echo "✅ Docker可用"
    export DOCKER_AVAILABLE=true
    
    # 启动测试环境
    echo "启动Docker测试环境..."
    docker-compose -f docker-compose.test.yml up -d
    
    # 等待服务启动
    echo "等待服务启动..."
    sleep 10
    
    # 检查服务状态
    echo "检查服务状态:"
    docker-compose -f docker-compose.test.yml ps
else
    echo "⚠️  Docker不可用，将跳过需要Docker的测试"
    export DOCKER_AVAILABLE=false
fi
echo

# 清理之前的测试结果
echo "清理之前的测试结果..."
mvn clean
echo

# 编译项目
echo "编译项目..."
mvn compile test-compile
echo

# 运行配置系统测试
echo "=== 1. 配置系统测试 ==="
mvn test -Dtest=OpenManusPropertiesTest -Dspring.profiles.active=test
echo

# 运行工具系统测试
echo "=== 2. 工具系统测试 ==="
echo "运行PythonTool测试..."
mvn test -Dtest=PythonToolTest -Dspring.profiles.active=test
echo

echo "运行FileTool测试..."
mvn test -Dtest=FileToolTest -Dspring.profiles.active=test
echo

echo "运行AskHumanTool测试..."
mvn test -Dtest=AskHumanToolTest -Dspring.profiles.active=test
echo

echo "运行TerminateTool测试..."
mvn test -Dtest=TerminateToolTest -Dspring.profiles.active=test
echo

# 运行集成测试
echo "=== 3. 集成测试 ==="
echo "运行ManusAgent集成测试..."
mvn test -Dtest=ManusAgentIntegrationTest -Dspring.profiles.active=test
echo

# 运行所有测试
echo "=== 4. 完整测试套件 ==="
echo "运行所有测试..."
mvn test -Dspring.profiles.active=test
echo

# 生成测试报告
echo "=== 5. 生成测试报告 ==="
mvn surefire-report:report
echo

# 显示测试结果摘要
echo "=== 测试结果摘要 ==="
if [ -f "target/surefire-reports/TEST-*.xml" ]; then
    echo "测试报告位置: target/surefire-reports/"
    echo "HTML报告位置: target/site/surefire-report.html"
    
    # 统计测试结果
    TOTAL_TESTS=$(find target/surefire-reports -name "TEST-*.xml" -exec grep -h "tests=" {} \; | sed 's/.*tests="\([0-9]*\)".*/\1/' | awk '{sum+=$1} END {print sum}')
    FAILED_TESTS=$(find target/surefire-reports -name "TEST-*.xml" -exec grep -h "failures=" {} \; | sed 's/.*failures="\([0-9]*\)".*/\1/' | awk '{sum+=$1} END {print sum}')
    ERROR_TESTS=$(find target/surefire-reports -name "TEST-*.xml" -exec grep -h "errors=" {} \; | sed 's/.*errors="\([0-9]*\)".*/\1/' | awk '{sum+=$1} END {print sum}')
    
    echo "总测试数: ${TOTAL_TESTS:-0}"
    echo "失败测试: ${FAILED_TESTS:-0}"
    echo "错误测试: ${ERROR_TESTS:-0}"
    
    PASSED_TESTS=$((${TOTAL_TESTS:-0} - ${FAILED_TESTS:-0} - ${ERROR_TESTS:-0}))
    echo "通过测试: ${PASSED_TESTS}"
    
    if [ "${TOTAL_TESTS:-0}" -gt 0 ]; then
        SUCCESS_RATE=$(echo "scale=2; ${PASSED_TESTS} * 100 / ${TOTAL_TESTS}" | bc -l)
        echo "成功率: ${SUCCESS_RATE}%"
    fi
else
    echo "未找到测试报告文件"
fi
echo

# 清理Docker环境
if [ "$DOCKER_AVAILABLE" = "true" ]; then
    echo "清理Docker测试环境..."
    docker-compose -f docker-compose.test.yml down
    echo "Docker环境已清理"
fi
echo

echo "=== 测试完成 ==="
echo "结束时间: $(date)"
echo

# 检查测试是否全部通过
if [ "${FAILED_TESTS:-0}" -eq 0 ] && [ "${ERROR_TESTS:-0}" -eq 0 ]; then
    echo "🎉 所有测试通过！"
    exit 0
else
    echo "❌ 有测试失败，请检查测试报告"
    exit 1
fi