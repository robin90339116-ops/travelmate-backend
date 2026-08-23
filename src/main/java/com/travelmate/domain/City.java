package com.travelmate.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "city", indexes = @Index(name = "idx_city_key", columnList = "cityKey", unique = true))
public class City {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String cityKey;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(length = 512)
    private String summary;
}
