package com.lingchuang.ai.user;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@EnableDubbo
@MapperScan("com.lingchuang.ai.user.mapper")
@ComponentScan("com.lingchuang.ai")
public class LingChuangAiUserApplication {
    public static void main(String[] args) {
        SpringApplication.run(LingChuangAiUserApplication.class, args);
    }
}