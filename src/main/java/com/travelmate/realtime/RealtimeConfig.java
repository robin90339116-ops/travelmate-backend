package com.travelmate.realtime;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.bind.annotation.*;
import com.travelmate.common.Result;
import java.util.Map;

@Configuration @EnableWebSocket
class RealtimeConfig implements WebSocketConfigurer {
 private final RealtimeGateway gateway;
 private final String[] origins;
 RealtimeConfig(RealtimeGateway gateway,@Value("${app.realtime.origins:http://127.0.0.1:5173,http://localhost:5173}") String[] origins){this.gateway=gateway;this.origins=origins;}
 @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry){((ServletWebSocketHandlerRegistry)registry).setOrder(-1);registry.addHandler(gateway,"/ws/realtime").setAllowedOrigins(origins);}
}
@RestController @RequestMapping("/api/realtime")
class RealtimeStatusController {
 private final RealtimeGateway gateway;
 RealtimeStatusController(RealtimeGateway gateway){this.gateway=gateway;}
 @GetMapping("/status") public Result<Map<String,Object>> status(){return Result.ok(gateway.status());}
}
