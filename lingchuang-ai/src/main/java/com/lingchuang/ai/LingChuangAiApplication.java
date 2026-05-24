package com.lingchuang.ai;

import dev.langchain4j.community.store.embedding.redis.spring.RedisEmbeddingStoreAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@EnableCaching
@SpringBootApplication(exclude = {RedisEmbeddingStoreAutoConfiguration.class})
@MapperScan("com.lingchuang.ai.mapper")
public class LingChuangAiApplication {

    private static final String HTTP_CLIENT_FACTORY_PROPERTY = "langchain4j.http.clientBuilderFactory";
    private static final String SPRING_REST_CLIENT_FACTORY =
            "dev.langchain4j.http.client.spring.restclient.SpringRestClientBuilderFactory";

    static {
        if (System.getProperty(HTTP_CLIENT_FACTORY_PROPERTY) == null) {
            System.setProperty(HTTP_CLIENT_FACTORY_PROPERTY, SPRING_REST_CLIENT_FACTORY);
        }
    }

    public static void main(String[] args) {
        SpringApplication.run(LingChuangAiApplication.class, args);
    }

}
