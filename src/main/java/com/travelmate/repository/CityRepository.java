package com.travelmate.repository;

import com.travelmate.domain.City;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CityRepository extends JpaRepository<City, Long> {
    Optional<City> findByCityKey(String cityKey);
}
