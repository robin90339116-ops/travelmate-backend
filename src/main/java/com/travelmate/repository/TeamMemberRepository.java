package com.travelmate.repository;

import com.travelmate.domain.TeamMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {
    List<TeamMember> findByUserId(Long userId);
    List<TeamMember> findByTeamIdOrderByJoinedAtAsc(Long teamId);

    Optional<TeamMember> findByTeamIdAndUserId(Long teamId, Long userId);
}
