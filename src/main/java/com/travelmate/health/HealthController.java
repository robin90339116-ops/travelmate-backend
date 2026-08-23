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

    @Value("${app.ai.api-key:}")
    private String aiKey;

    @Value("${app.map.amap-web-key:}")
    private String amapKey;

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        return Result.ok(Map.of("status", "ok", "time", Instant.now().toString()));
    }

    @GetMapping("/config/status")
    public Result<Map<String, Object>> configStatus() {
        return Result.ok(Map.of(
                "ai", Map.of("provider", "bailian", "configured", !aiKey.isBlank()),
                "map", Map.of("provider", "amap", "configured", !amapKey.isBlank()),
                "realtime", Map.of("websocket", "/ws", "topic", "/topic/teams/{teamId}")));
    }
}
