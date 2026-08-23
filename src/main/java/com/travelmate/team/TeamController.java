package com.travelmate.team;

import com.travelmate.common.CurrentUser;
import com.travelmate.common.Result;
import com.travelmate.team.TeamDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;

    @PostMapping
    public Result<TeamView> create(@RequestBody(required = false) CreateTeamRequest request) {
        return Result.ok(teamService.createTeam(CurrentUser.id(), request));
    }

    @PostMapping("/join")
    public Result<TeamView> join(@RequestBody JoinTeamRequest request) {
        return Result.ok(teamService.joinTeam(CurrentUser.id(), request));
    }

    @GetMapping("/{teamId}")
    public Result<TeamView> get(@PathVariable Long teamId) {
        return Result.ok(teamService.getTeam(teamId));
    }

    @PostMapping("/{teamId}/leave")
    public Result<Void> leave(@PathVariable Long teamId) {
        teamService.leave(CurrentUser.id(), teamId);
        return Result.ok();
    }

    @PostMapping("/{teamId}/playback")
    public Result<TeamView> playback(@PathVariable Long teamId, @RequestBody PlaybackRequest request) {
        return Result.ok(teamService.updatePlayback(CurrentUser.id(), teamId, request));
    }

    @DeleteMapping("/{teamId}/members/{targetUserId}")
    public Result<Void> kick(@PathVariable Long teamId, @PathVariable Long targetUserId) {
        teamService.kick(CurrentUser.id(), teamId, targetUserId);
        return Result.ok();
    }

    @PostMapping("/{teamId}/transfer/{targetUserId}")
    public Result<Void> transfer(@PathVariable Long teamId, @PathVariable Long targetUserId) {
        teamService.transferOwner(CurrentUser.id(), teamId, targetUserId);
        return Result.ok();
    }
}
