package com.lingchuang.ai.config;

import cn.hutool.core.util.StrUtil;
import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Redis 持久化对话记忆
 */
@Configuration
@ConfigurationProperties(prefix = "spring.data.redis")
@Data
@Slf4j
public class RedisChatMemoryStoreConfig {

    private static final byte[] REDIS_JSON_PROBE_KEY = "__langchain4j:redisjson:probe__".getBytes(StandardCharsets.UTF_8);
    private static final byte[] REDIS_JSON_PROBE_PATH = "$".getBytes(StandardCharsets.UTF_8);

    private String host;

    private int port;

    private String password;

    private long ttl;

    @Resource
    private RedisConnectionFactory redisConnectionFactory;

    @Bean
    public ChatMemoryStore redisChatMemoryStore() {
        RedisChatMemoryStore.Builder builder = RedisChatMemoryStore.builder()
                .host(host)
                .port(port)
                .password(password)
                .ttl(ttl);
        if (StrUtil.isNotBlank(password)) {
            builder.user("default");
        }
        boolean fallbackToMemory = !supportsRedisJson();
        if (fallbackToMemory) {
            log.warn("检测到当前 Redis 不支持 RedisJSON 指令，聊天记忆将直接使用内存实现");
        }
        return new ResilientChatMemoryStore(builder.build(), fallbackToMemory);
    }

    private boolean supportsRedisJson() {
        if (redisConnectionFactory == null) {
            return true;
        }
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            connection.execute("JSON.GET", REDIS_JSON_PROBE_KEY, REDIS_JSON_PROBE_PATH);
            return true;
        } catch (RuntimeException e) {
            if (isRedisJsonUnsupported(e)) {
                return false;
            }
            log.warn("探测 RedisJSON 能力失败，将在运行期按需回退聊天记忆存储", e);
            return true;
        }
    }

    private static boolean isRedisJsonUnsupported(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && (message.contains("JSON.GET")
                    || message.contains("JSON.SET")
                    || message.contains("ERR unknown command"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * 当本地 Redis 不支持 RedisJSON 指令时，自动回退到内存实现，避免开发 / 测试环境直接失败。
     */
    private static final class ResilientChatMemoryStore implements ChatMemoryStore {

        private final ChatMemoryStore redisDelegate;
        private final InMemoryChatMemoryStore fallbackDelegate = new InMemoryChatMemoryStore();
        private final AtomicBoolean fallbackEnabled;

        private ResilientChatMemoryStore(ChatMemoryStore redisDelegate) {
            this(redisDelegate, false);
        }

        private ResilientChatMemoryStore(ChatMemoryStore redisDelegate, boolean fallbackEnabled) {
            this.redisDelegate = redisDelegate;
            this.fallbackEnabled = new AtomicBoolean(fallbackEnabled);
        }

        @Override
        public List<dev.langchain4j.data.message.ChatMessage> getMessages(Object memoryId) {
            return executeWithFallback(memoryId, () -> redisDelegate.getMessages(memoryId),
                    () -> fallbackDelegate.getMessages(memoryId));
        }

        @Override
        public void updateMessages(Object memoryId, List<dev.langchain4j.data.message.ChatMessage> messages) {
            executeWithFallback(memoryId,
                    () -> redisDelegate.updateMessages(memoryId, messages),
                    () -> fallbackDelegate.updateMessages(memoryId, messages));
        }

        @Override
        public void deleteMessages(Object memoryId) {
            executeWithFallback(memoryId,
                    () -> redisDelegate.deleteMessages(memoryId),
                    () -> fallbackDelegate.deleteMessages(memoryId));
        }

        private <T> T executeWithFallback(Object memoryId, Supplier<T> primary, Supplier<T> fallback) {
            if (fallbackEnabled.get()) {
                return fallback.get();
            }
            try {
                return primary.get();
            } catch (RuntimeException e) {
                if (!isRedisJsonUnsupported(e)) {
                    throw e;
                }
                enableFallbackOnce(e);
                return fallback.get();
            }
        }

        private void executeWithFallback(Object memoryId, Runnable primary, Runnable fallback) {
            if (fallbackEnabled.get()) {
                fallback.run();
                return;
            }
            try {
                primary.run();
            } catch (RuntimeException e) {
                if (!isRedisJsonUnsupported(e)) {
                    throw e;
                }
                enableFallbackOnce(e);
                fallback.run();
            }
        }

        private void enableFallbackOnce(RuntimeException e) {
            if (fallbackEnabled.compareAndSet(false, true)) {
                log.warn("检测到当前 Redis 不支持 RedisJSON 指令，聊天记忆自动回退为内存实现，原因: {}", e.getMessage());
            }
        }
    }
}
