#!/bin/bash
# 交互模式运行脚本

echo "🤖 启动 OpenManus Java 交互模式..."
echo "您可以直接与智能体对话，给它分配任务"
echo ""
echo "使用示例:"
echo "• 分析这个CSV文件中的数据"
echo "• 帮我写一个Python脚本"
echo "• 搜索最新的技术信息"
echo "• 处理文件和目录"
echo ""

# 启用交互运行器
export OPENMANUS_MODE=interactive

# 运行项目
mvn spring-boot:run -Dspring-boot.run.profiles=interactive
