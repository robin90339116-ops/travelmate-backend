package com.travelmate.repository;

import com.travelmate.domain.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {
    List<Favorite> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Favorite> findByIdAndUserId(Long id, Long userId);
}
