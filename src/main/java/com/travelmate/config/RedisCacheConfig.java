package com.travelmate.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Redis 缓存管理器(仅 redis/prod profile)。
 * - 缓存空值:防缓存穿透(空结果也写入,短 TTL)。
 * - 每个缓存随机基础 TTL:防缓存雪崩(避免同一时刻集中失效)。
 * - 击穿由 @Cacheable(sync=true) 在应用层控制并发回源。
 */
@Configuration
@Profile({"redis", "prod"})
public class RedisCacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory, ObjectMapper objectMapper) {
        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(objectMapper);
        Duration randomBase = Duration.ofMinutes(10).plusSeconds(ThreadLocalRandom.current().nextInt(0, 300));
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(randomBase)
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer));
        return RedisCacheManager.builder(factory)
                .cacheDefaults(config)
                .build();
    }
}
