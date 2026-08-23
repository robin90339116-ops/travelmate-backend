package com.travelmate.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "team", indexes = @Index(name = "idx_team_code", columnList = "teamCode", unique = true))
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 16)
    private String teamCode;

    @Column(nullable = false)
    private Long ownerId;

    @Column(length = 64)
    private String routeId;

    @Column(length = 64)
    private String currentPointId;

    @Column(length = 16)
    private String playbackStatus = "paused";

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
