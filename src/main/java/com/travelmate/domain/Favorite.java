package com.travelmate.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "favorite", indexes = @Index(name = "idx_favorite_user", columnList = "userId"))
public class Favorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 16)
    private String targetType;

    @Column(nullable = false, length = 128)
    private String title;

    @Column(length = 256)
    private String subtitle;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
