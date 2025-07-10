#!/bin/bash

# OpenManus Java 端到端测试运行脚本
# 此脚本用于运行完整的端到端测试套件

set -e  # 遇到错误时退出

echo "=== OpenManus Java 端到端测试 ==="
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

# 设置测试环境变量
export MAVEN_OPTS="-Xmx2g -XX:MaxPermSize=256m"
export JAVA_OPTS="-Xmx2g"

echo "设置的环境变量:"
echo "MAVEN_OPTS: $MAVEN_OPTS"
echo "JAVA_OPTS: $JAVA_OPTS"
echo

# 清理之前的构建
echo "清理之前的构建..."
mvn clean
echo

# 编译项目
echo "编译项目..."
mvn compile test-compile
echo

# 运行端到端测试
echo "运行端到端测试..."
echo "测试类: EndToEndTest"
echo

if mvn test -Dtest=EndToEndTest -Dmaven.test.failure.ignore=false; then
    echo
    echo "✅ 端到端测试通过!"
    echo
    
    # 显示测试报告位置
    echo "测试报告位置:"
    echo "- Surefire报告: target/surefire-reports/"
    echo "- JaCoCo覆盖率报告: target/site/jacoco/"
    echo
    
    # 生成覆盖率报告
    echo "生成覆盖率报告..."
    mvn jacoco:report
    echo
    
    # 显示测试统计
    echo "测试统计:"
    if [ -f "target/surefire-reports/TEST-com.openmanus.java.integration.EndToEndTest.xml" ]; then
        echo "详细测试结果请查看: target/surefire-reports/TEST-com.openmanus.java.integration.EndToEndTest.xml"
    fi
    
    echo
    echo "🎉 所有端到端测试成功完成!"
    echo "结束时间: $(date)"
    
    exit 0
else
    echo
    echo "❌ 端到端测试失败!"
    echo
    
    # 显示失败的测试信息
    echo "失败测试详情:"
    if [ -d "target/surefire-reports" ]; then
        echo "查看详细错误信息: target/surefire-reports/"
        
        # 显示最新的测试报告
        latest_report=$(find target/surefire-reports -name "*.txt" -type f -exec ls -t {} + | head -1)
        if [ -n "$latest_report" ]; then
            echo
            echo "最新测试报告内容:"
            echo "=================="
            cat "$latest_report"
            echo "=================="
        fi
    fi
    
    echo
    echo "结束时间: $(date)"
    exit 1
fi