package com.travelmate.team;

import com.travelmate.team.TeamDtos.PlaybackRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

/**
 * STOMP 入站:客户端发送到 /app/teams/{teamId}/playback,服务端更新并广播。
 * 说明:握手鉴权可结合 STOMP CONNECT 头的 JWT 校验(此处演示 userId 由消息携带)。
 */
@Controller
@RequiredArgsConstructor
public class TeamStompController {

    private final TeamService teamService;

    public record StompPlayback(Long userId, String currentPointId, String playbackStatus) {
    }

    @MessageMapping("/teams/{teamId}/playback")
    public void playback(@DestinationVariable Long teamId, @Payload StompPlayback payload) {
        teamService.updatePlayback(payload.userId(), teamId,
                new PlaybackRequest(payload.currentPointId(), payload.playbackStatus()));
    }
}
