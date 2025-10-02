package com.openmanus.infra.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC配置类
 * 
 * 负责注册Web拦截器
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final MdcInterceptor mdcInterceptor;

    public WebMvcConfig(MdcInterceptor mdcInterceptor) {
        this.mdcInterceptor = mdcInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(mdcInterceptor)
                .addPathPatterns("/api/**");
    }
}
