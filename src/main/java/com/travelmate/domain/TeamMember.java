package com.travelmate.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "team_member", uniqueConstraints=@UniqueConstraint(columnNames={"teamId","userId"}), indexes = {
        @Index(name = "idx_member_team", columnList = "teamId"),
        @Index(name = "idx_member_user", columnList = "userId")
})
public class TeamMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long teamId;

    @Column(nullable = false)
    private Long userId;

    @Column(length = 64)
    private String memberName;

    @Column(nullable = false, length = 16)
    private String role = "member";

    @Column(nullable = false)
    private Instant joinedAt = Instant.now();
}
