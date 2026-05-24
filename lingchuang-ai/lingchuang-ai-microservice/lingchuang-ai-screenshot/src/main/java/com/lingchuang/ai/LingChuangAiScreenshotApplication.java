package com.lingchuang.ai;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableDubbo
public class LingChuangAiScreenshotApplication {
    public static void main(String[] args) {
        SpringApplication.run(LingChuangAiScreenshotApplication.class, args);
    }
}