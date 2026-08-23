package com.travelmate.catalog;

import java.io.Serializable;
import java.util.List;

/**
 * 目录相关 DTO。实现 Serializable 以便进入 Redis 缓存。
 */
public final class CatalogDtos {

    private CatalogDtos() {
    }

    public record CityView(String cityKey, String name, String summary) implements Serializable {
    }

    public record SpotView(
            String id, String cityKey, String name, String category, String intro,
            String highlight, String openTime, String recommendedDuration,
            List<String> tags, String sourceName, String sourceStatus,
            Double latitude, Double longitude) implements Serializable {
    }

    public record RoutePointView(
            String spotId, int orderIndex, String name, String headingText,
            Integer stayMinutes, Integer triggerRadius, Double latitude, Double longitude)
            implements Serializable {
    }

    public record RouteView(
            String routeId, String cityKey, String name, String summary,
            String estimatedDuration, String distanceText, String intensity, String style,
            List<String> tags, List<String> risks, List<RoutePointView> points) implements Serializable {
    }
}
