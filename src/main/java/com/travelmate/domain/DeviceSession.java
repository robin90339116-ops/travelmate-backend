package com.travelmate.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 设备会话,用于 refresh token 轮换与单设备/全端下线。
 */
@Getter
@Setter
@Entity
@Table(name = "device_session", indexes = {
        @Index(name = "idx_session_sid", columnList = "sessionId", unique = true),
        @Index(name = "idx_session_user", columnList = "userId")
})
public class DeviceSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String sessionId;

    @Column(nullable = false)
    private Long userId;

    @Column(length = 64)
    private String deviceName;

    /** 当前有效 refresh token 的哈希,轮换时更新,撤销时置空。 */
    @Column(length = 100)
    private String refreshTokenHash;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant lastActiveAt = Instant.now();
}
