package com.travelmate.repository;

import com.travelmate.domain.RoutePoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoutePointRepository extends JpaRepository<RoutePoint, Long> {
    List<RoutePoint> findByRouteKeyOrderByOrderIndexAsc(String routeKey);
}
