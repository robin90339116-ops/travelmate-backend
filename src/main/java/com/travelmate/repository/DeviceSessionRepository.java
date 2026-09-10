package com.travelmate.repository;

import com.travelmate.domain.DeviceSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeviceSessionRepository extends JpaRepository<DeviceSession, Long> {
    Optional<DeviceSession> findBySessionId(String sessionId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from DeviceSession s where s.sessionId = :sid")
    Optional<DeviceSession> lockBySessionId(@org.springframework.data.repository.query.Param("sid") String sid);

    List<DeviceSession> findByUserIdOrderByLastActiveAtDesc(Long userId);

    List<DeviceSession> findByUserIdAndActiveTrue(Long userId);
}
