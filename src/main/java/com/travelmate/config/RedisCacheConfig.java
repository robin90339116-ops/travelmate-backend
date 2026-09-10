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
 * 空值短TTL，每次写入独立TTL抖动。抛异常的查询不生成空值缓存。
 * 不提供分布式回源锁。
 */
@Configuration
@Profile({"redis", "prod"})
public class RedisCacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory, ObjectMapper objectMapper) {
        var mapper = objectMapper.copy();
        mapper.activateDefaultTyping(com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.travelmate.catalog.").allowIfSubType("java.util.").allowIfSubType("java.lang.")
                .build(), ObjectMapper.DefaultTyping.EVERYTHING, com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY);
        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(mapper);
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl((key, value) -> value == null ? Duration.ofSeconds(30)
                        : Duration.ofSeconds(600 + ThreadLocalRandom.current().nextInt(300)))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer));
        return RedisCacheManager.builder(factory)
                .cacheDefaults(config)
                .build();
    }
}
