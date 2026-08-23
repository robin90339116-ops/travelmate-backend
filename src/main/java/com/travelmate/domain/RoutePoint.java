package com.travelmate.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "route_point", indexes = @Index(name = "idx_point_route", columnList = "routeKey"))
public class RoutePoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String routeKey;

    @Column(length = 64)
    private String spotId;

    @Column(nullable = false)
    private Integer orderIndex;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(length = 128)
    private String headingText;

    private Integer stayMinutes;

    private Integer triggerRadius;

    private Double latitude;

    private Double longitude;
}
