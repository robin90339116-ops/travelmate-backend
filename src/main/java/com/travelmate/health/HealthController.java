package com.travelmate.health;

import com.travelmate.common.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {
    private volatile boolean ready;
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void markReady() { ready = true; }

    @Value("${app.ai.api-key:}")
    private String aiKey;

    @Value("${app.map.amap-web-key:}")
    private String amapKey;

    @Value("${app.ai.provider:bailian}")
    private String provider;

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        if (!ready) throw com.travelmate.common.ApiException.serviceUnavailable("服务正在初始化");
        return Result.ok(Map.of("status", "ok", "time", Instant.now().toString()));
    }

    @GetMapping("/config/status")
    public Result<Map<String, Object>> configStatus() {
        return Result.ok(Map.of(
                "ai", Map.of("provider", provider, "configured", !aiKey.isBlank()),
                "map", Map.of("provider", "amap", "configured", !amapKey.isBlank()),
                "realtime", Map.of("websocket", "/ws", "topic", "/topic/teams/{teamId}")));
    }
}
