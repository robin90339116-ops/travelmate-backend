package com.travelmate.team;

import java.util.List;

public final class TeamDtos {

    private TeamDtos() {
    }

    public record CreateTeamRequest(String routeId) {
    }

    public record JoinTeamRequest(String teamCode, String memberName) {
    }

    public record PlaybackRequest(String currentPointId, String playbackStatus) {
    }

    public record MemberView(Long userId, String memberName, String role) {
    }

    public record TeamView(
            Long teamId, String teamCode, Long ownerId, String routeId,
            String currentPointId, String playbackStatus, List<MemberView> members) {
    }
}
