package com.travelmate.config;

import com.travelmate.auth.SessionAuthenticator;
import com.travelmate.repository.TeamMemberRepository;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class StompSecurity implements WebSocketMessageBrokerConfigurer {
    private final SessionAuthenticator auth;
    private final TeamMemberRepository members;

    @Override public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override public Message<?> preSend(Message<?> message, MessageChannel channel) {
                var h = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (h == null) return message;
                if (h.getCommand() == StompCommand.CONNECT) {
                    String bearer = h.getFirstNativeHeader("Authorization");
                    if (bearer == null || !bearer.startsWith("Bearer ")) throw new IllegalArgumentException("Authentication required");
                    h.setUser(auth.authenticate(bearer.substring(7)));
                    h.getSessionAttributes().put("accessToken", bearer.substring(7));
                }
                if (h.getCommand() == StompCommand.SEND || h.getCommand() == StompCommand.SUBSCRIBE) {
                    String token = (String) h.getSessionAttributes().get("accessToken");
                    var user = auth.authenticate(token);
                    String destination = h.getDestination();
                    String pattern = h.getCommand() == StompCommand.SUBSCRIBE ? "/topic/teams/[0-9]+" : "/app/teams/[0-9]+/playback";
                    if (destination == null || !destination.matches(pattern)) throw new IllegalArgumentException("Destination denied");
                    long team = Long.parseLong(destination.split("/")[3]);
                    if (members.findByTeamIdAndUserId(team, (Long) user.getPrincipal()).isEmpty())
                        throw new IllegalArgumentException("Not a team member");
                    h.setUser(user);
                }
                return message;
            }
        });
    }

    @Override public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override public Message<?> preSend(Message<?> message, MessageChannel channel) {
                var h = StompHeaderAccessor.wrap(message);
                if (h.getCommand() == StompCommand.MESSAGE) {
                    try {
                        var user = auth.authenticate((String) h.getSessionAttributes().get("accessToken"));
                        long team = Long.parseLong(h.getDestination().split("/")[3]);
                        if (members.findByTeamIdAndUserId(team, (Long) user.getPrincipal()).isEmpty()) return null;
                    } catch (Exception e) { return null; }
                }
                return message;
            }
        });
    }
}
