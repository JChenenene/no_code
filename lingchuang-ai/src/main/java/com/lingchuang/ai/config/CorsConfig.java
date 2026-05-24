package com.lingchuang.ai.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 全局跨域问题解决
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 1. 覆盖项目中【所有的接口】，所有请求都遵循这个跨域规则
        registry.addMapping("/**")
                // 2. 允许前端请求中 携带Cookie、Token等身份凭证 ✅ 重中之重
                .allowCredentials(true)
                // 3. 允许【任意域名】的前端页面跨域调用 ✅ 你的写法是最优解！
                .allowedOriginPatterns("*")
                // 4. 允许前端发起的【所有请求方式】
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                // 5. 允许前端请求中 携带【所有请求头】
                .allowedHeaders("*")
                // 6. 允许前端读取【所有响应头】
                .exposedHeaders("*");
    }
}