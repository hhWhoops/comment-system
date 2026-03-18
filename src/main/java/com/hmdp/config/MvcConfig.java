package com.hmdp.config;

import com.hmdp.utils.Logininterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MvcConfig implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new Logininterceptor())
                .excludePathPatterns(
                        "/shop/**",
                        "/user/code",
                        "/user/login"
                );
    }
}
