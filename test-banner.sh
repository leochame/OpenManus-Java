#!/bin/bash

echo "测试 OpenManus Java 启动Banner..."
echo "=================================="

# 显示Banner内容
echo "Banner内容预览:"
echo "=================================="
cat src/main/resources/banner-simple.txt
echo "=================================="

echo ""
echo "启动信息预览:"
echo "=================================="
echo "╭─────────────────────────────────────────────────────────╮"
echo "│                    OpenManus Java                      │"
echo "│              智能思考系统启动成功!                        │"
echo "╰─────────────────────────────────────────────────────────╯"
echo ""
echo "🌐 Web界面: http://localhost:8089"
echo "📚 API文档: http://localhost:8089/swagger-ui.html"
echo "🔍 健康检查: http://localhost:8089/actuator/health"
echo ""
echo "💡 思考模式:"
echo "   ⚡ 快思考模式 - 直接响应，适合简单任务"
echo "   🔍 慢思考模式 - 深度思考，适合复杂任务"
echo "   🤖 自动模式 - 智能选择最佳模式"
echo ""
echo "☕ 基于 Java 21 + Spring Boot 3.2.0 + LangChain4j"
echo "╰─────────────────────────────────────────────────────────╯"

echo ""
echo "要启动完整应用，请运行: ./mvnw spring-boot:run" 