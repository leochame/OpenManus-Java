#!/bin/bash
# 测试模式运行脚本

echo "🧪 启动 OpenManus Java 测试模式..."
echo "这将运行各种功能测试"
echo ""

# 启用测试运行器
export OPENMANUS_MODE=test

# 运行项目
mvn spring-boot:run -Dspring-boot.run.profiles=test

echo ""
echo "测试完成!"
