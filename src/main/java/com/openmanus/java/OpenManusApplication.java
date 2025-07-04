package com.openmanus.java;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot主应用类
 * 如果需要测试模式，可以运行TestRunner
 * 如果需要交互模式，可以运行InteractiveRunner
 */
@SpringBootApplication
public class OpenManusApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpenManusApplication.class, args);
    }
}
