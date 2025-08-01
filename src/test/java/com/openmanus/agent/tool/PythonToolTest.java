package com.openmanus.agent.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PythonTool 测试类
 * 用于验证 Python 代码执行功能
 */
public class PythonToolTest {
    
    private static final Logger logger = LoggerFactory.getLogger(PythonToolTest.class);
    
    private PythonTool pythonTool;
    
    @BeforeEach
    void setUp() {
        pythonTool = new PythonTool();
    }
    
    @Test
    void testSimplePrintStatement() {
        logger.info("测试简单的 print 语句");
        
        String code = "print('Hello, World!')";
        String result = pythonTool.executePython(code);
        
        logger.info("执行结果: {}", result);
        
        assertNotNull(result, "结果不应为空");
        assertTrue(result.contains("Hello, World!"), "结果应包含 'Hello, World!'");
        assertTrue(result.contains("Execution successful"), "应该执行成功");
    }
    
    @Test
    void testMathCalculation() {
        logger.info("测试数学计算");
        
        String code = "result = 6 * 7\nprint(f'The result is: {result}')";
        String result = pythonTool.executePython(code);
        
        logger.info("执行结果: {}", result);
        
        assertNotNull(result, "结果不应为空");
        assertTrue(result.contains("42"), "结果应包含计算结果 42");
        assertTrue(result.contains("Execution successful"), "应该执行成功");
    }
    
    @Test
    void testMultipleOutputs() {
        logger.info("测试多行输出");
        
        String code = """
            for i in range(3):
                print(f'Line {i + 1}')
            print('Done!')
            """;
        String result = pythonTool.executePython(code);
        
        logger.info("执行结果: {}", result);
        
        assertNotNull(result, "结果不应为空");
        assertTrue(result.contains("Line 1"), "应包含第一行输出");
        assertTrue(result.contains("Line 2"), "应包含第二行输出");
        assertTrue(result.contains("Line 3"), "应包含第三行输出");
        assertTrue(result.contains("Done!"), "应包含最后一行输出");
        assertTrue(result.contains("Execution successful"), "应该执行成功");
    }
    
    @Test
    void testErrorHandling() {
        logger.info("测试错误处理");
        
        String code = "print(undefined_variable)";
        String result = pythonTool.executePython(code);
        
        logger.info("执行结果: {}", result);
        
        assertNotNull(result, "结果不应为空");
        assertTrue(result.contains("NameError") || result.contains("Execution failed"), 
                  "应该包含错误信息");
    }
    
    @Test
    void testEmptyOutput() {
        logger.info("测试无输出的代码");
        
        String code = "x = 1 + 1";  // 没有 print 语句
        String result = pythonTool.executePython(code);
        
        logger.info("执行结果: {}", result);
        
        assertNotNull(result, "结果不应为空");
        assertTrue(result.contains("Execution successful"), "应该执行成功");
        // 这里可能是问题所在 - 如果没有输出，output.toString() 可能是空的
    }
}
