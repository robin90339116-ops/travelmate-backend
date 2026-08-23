package com.travelmate.repository;

import com.travelmate.domain.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, Long> {
    Optional<Team> findByTeamCode(String teamCode);
}
