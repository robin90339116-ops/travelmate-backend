package com.travelmate.repository;

import com.travelmate.domain.RouteEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RouteRepository extends JpaRepository<RouteEntity, Long> {
    List<RouteEntity> findByCityKey(String cityKey);

    Optional<RouteEntity> findByRouteKey(String routeKey);
}
