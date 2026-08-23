package com.travelmate.team;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 房间事件广播。
 * - 单实例(默认):直接经 STOMP 推到 /topic/teams/{teamId}。
 * - 多实例(app.realtime.redis=true,redis profile):发布到 Redis 频道,由各实例 relay 再推到本地
 *   STOMP,实现跨实例扇出。
 */
@Slf4j
@Component
public class TeamBroadcaster {

    public static final String CHANNEL = "travelmate:team";

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final ObjectMapper objectMapper;
    private final boolean redisEnabled;

    public TeamBroadcaster(SimpMessagingTemplate messagingTemplate,
                           ObjectProvider<StringRedisTemplate> redisTemplateProvider,
                           ObjectMapper objectMapper,
                           @Value("${app.realtime.redis:false}") boolean redisEnabled) {
        this.messagingTemplate = messagingTemplate;
        this.redisTemplateProvider = redisTemplateProvider;
        this.objectMapper = objectMapper;
        this.redisEnabled = redisEnabled;
    }

    public void broadcast(Long teamId, String type, Object data) {
        Map<String, Object> event = Map.of("type", type, "data", data);
        if (redisEnabled) {
            StringRedisTemplate redis = redisTemplateProvider.getIfAvailable();
            if (redis != null) {
                try {
                    String payload = objectMapper.writeValueAsString(Map.of("teamId", teamId, "event", event));
                    redis.convertAndSend(CHANNEL, payload);
                    return;
                } catch (Exception e) {
                    log.warn("Redis 广播失败,回退本地 STOMP", e);
                }
            }
        }
        deliverLocal(teamId, event);
    }

    public void deliverLocal(Long teamId, Object event) {
        messagingTemplate.convertAndSend("/topic/teams/" + teamId, event);
    }
}
