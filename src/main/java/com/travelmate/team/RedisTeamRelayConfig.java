package com.travelmate.team;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 跨实例广播中继(仅 redis profile):订阅 Redis 频道,把事件再推给本实例的 STOMP 订阅者。
 */
@Slf4j
@Configuration
@Profile("redis")
public class RedisTeamRelayConfig {

    @Bean
    public RedisMessageListenerContainer teamRedisContainer(RedisConnectionFactory factory,
                                                            TeamBroadcaster broadcaster,
                                                            ObjectMapper objectMapper) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        container.addMessageListener((message, pattern) -> {
            try {
                String payload = new String(message.getBody(), StandardCharsets.UTF_8);
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = objectMapper.readValue(payload, Map.class);
                Long teamId = Long.valueOf(String.valueOf(parsed.get("teamId")));
                broadcaster.deliverLocal(teamId, parsed.get("event"));
            } catch (Exception e) {
                log.warn("Redis 团队事件中继解析失败", e);
            }
        }, new ChannelTopic(TeamBroadcaster.CHANNEL));
        return container;
    }
}
