package com.travelmate.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "app_user", indexes = @Index(name = "idx_user_phone", columnList = "phone", unique = true))
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String phone;

    @Column(length = 64)
    private String displayName;

    @Column(length = 100)
    private String passwordHash;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
