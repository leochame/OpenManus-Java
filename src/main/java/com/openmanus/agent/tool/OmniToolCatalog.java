package com.openmanus.agent.tool;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 工具目录 - 自动注册所有可用工具
 * 
 * 采用依赖注入自动收集所有工具，无需硬编码
 * 符合开闭原则：新增工具时无需修改此类
 */
@Component
public class OmniToolCatalog {

    private final List<Object> tools;

    /**
     * Spring 自动注入所有工具实例
     * 只要新工具标注了 @Component，就会自动被收集
     */
    public OmniToolCatalog(List<Object> tools) {
        this.tools = tools;
    }
    
    public List<Object> getTools() {
        return tools;
    }

} 