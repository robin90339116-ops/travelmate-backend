package com.travelmate.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "route", indexes = {
        @Index(name = "idx_route_key", columnList = "routeKey", unique = true),
        @Index(name = "idx_route_city", columnList = "cityKey")
})
public class RouteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String routeKey;

    @Column(nullable = false, length = 32)
    private String cityKey;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 512)
    private String summary;

    @Column(length = 32)
    private String estimatedDuration;

    @Column(length = 32)
    private String distanceText;

    @Column(length = 16)
    private String intensity;

    @Column(length = 16)
    private String style;

    @Column(length = 256)
    private String tags;

    @Column(length = 512)
    private String risks;
}
