package com.travelmate.team;

import com.travelmate.team.TeamDtos.PlaybackRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

/**
 * STOMP 入站:客户端发送到 /app/teams/{teamId}/playback,服务端更新并广播。
 * CONNECT会话及房间权限由StompSecurity校验，身份仅来自Principal。
 */
@Controller
@RequiredArgsConstructor
public class TeamStompController {

    private final TeamService teamService;

    public record StompPlayback(Long userId, String currentPointId, String playbackStatus) {
    }

    @MessageMapping("/teams/{teamId}/playback")
    public void playback(@DestinationVariable Long teamId, @Payload StompPlayback payload, java.security.Principal principal) {
        teamService.updatePlayback(Long.valueOf(principal.getName()), teamId,
                new PlaybackRequest(payload.currentPointId(), payload.playbackStatus()));
    }
}
