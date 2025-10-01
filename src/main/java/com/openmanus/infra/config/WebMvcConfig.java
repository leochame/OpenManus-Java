package com.openmanus.infra.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册MDC拦截器，使其对所有API请求生效
        registry.addInterceptor(new MdcInterceptor())
                .addPathPatterns("/api/**");
    }
}
