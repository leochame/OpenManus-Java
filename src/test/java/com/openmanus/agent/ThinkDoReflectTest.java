package com.openmanus.agent;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Think-Do-Reflect系统的基本测试
 */
@SpringBootTest
@TestPropertySource(properties = {
    "openmanus.langchain4j.chat-model.provider=openai",
    "openmanus.langchain4j.chat-model.openai.api-key=test-key",
    "openmanus.langchain4j.chat-model.openai.model-name=gpt-3.5-turbo"
})
public class ThinkDoReflectTest {

    @Test
    public void contextLoads() {
        // 基本的上下文加载测试
        // 如果Spring能够成功启动并创建所有Bean，说明配置是正确的
    }
}
