package com.travelmate.repository;

import com.travelmate.domain.DeviceSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeviceSessionRepository extends JpaRepository<DeviceSession, Long> {
    Optional<DeviceSession> findBySessionId(String sessionId);

    List<DeviceSession> findByUserIdOrderByLastActiveAtDesc(Long userId);

    List<DeviceSession> findByUserIdAndActiveTrue(Long userId);
}
