package com.travelmate.catalog;

import com.travelmate.catalog.CatalogDtos.*;
import com.travelmate.common.ApiException;
import com.travelmate.domain.RouteEntity;
import com.travelmate.domain.RoutePoint;
import com.travelmate.domain.Spot;
import com.travelmate.repository.CityRepository;
import com.travelmate.repository.RoutePointRepository;
import com.travelmate.repository.RouteRepository;
import com.travelmate.repository.SpotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * 目录读缓存；sync行为取决于缓存实现，不能视为跨实例分布式锁。
 * 城市空结果不缓存，避免种子初始化期间污染缓存。
 */
@Service
@RequiredArgsConstructor
public class CatalogService {

    private final CityRepository cityRepository;
    private final SpotRepository spotRepository;
    private final RouteRepository routeRepository;
    private final RoutePointRepository routePointRepository;

    @Cacheable(value = "cities", key = "'all'", unless = "#result.isEmpty()")
    public List<CityView> listCities() {
        return cityRepository.findAll().stream()
                .map(c -> new CityView(c.getCityKey(), c.getName(), c.getSummary()))
                .toList();
    }

    @Cacheable(value = "spots", key = "#cityKey", sync = true)
    public List<SpotView> listSpots(String cityKey) {
        return spotRepository.findByCityKey(cityKey).stream().map(this::toSpotView).toList();
    }

    @Cacheable(value = "routes", key = "#cityKey", sync = true)
    public List<RouteView> listRoutes(String cityKey) {
        return routeRepository.findByCityKey(cityKey).stream()
                .map(r -> toRouteView(r, false)).toList();
    }

    @Cacheable(value = "route", key = "#routeKey", sync = true)
    public RouteView getRoute(String routeKey) {
        RouteEntity route = routeRepository.findByRouteKey(routeKey)
                .orElseThrow(() -> ApiException.notFound("路线不存在:" + routeKey));
        return toRouteView(route, true);
    }

    private RouteView toRouteView(RouteEntity route, boolean withPoints) {
        List<RoutePointView> points = List.of();
        if (withPoints) {
            points = routePointRepository.findByRouteKeyOrderByOrderIndexAsc(route.getRouteKey()).stream()
                    .map(this::toPointView).toList();
        }
        return new RouteView(
                route.getRouteKey(), route.getCityKey(), route.getName(), route.getSummary(),
                route.getEstimatedDuration(), route.getDistanceText(), route.getIntensity(), route.getStyle(),
                splitCsv(route.getTags()), splitCsv(route.getRisks()), points);
    }

    private RoutePointView toPointView(RoutePoint p) {
        return new RoutePointView(p.getSpotId(), p.getOrderIndex(), p.getName(), p.getHeadingText(),
                p.getStayMinutes(), p.getTriggerRadius(), p.getLatitude(), p.getLongitude());
    }

    private SpotView toSpotView(Spot s) {
        return new SpotView(String.valueOf(s.getId()), s.getCityKey(), s.getName(), s.getCategory(),
                s.getIntro(), s.getHighlight(), s.getOpenTime(), s.getRecommendedDuration(),
                splitCsv(s.getTags()), s.getSourceName(), s.getSourceStatus(), s.getLatitude(), s.getLongitude());
    }

    private List<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(",")).map(String::trim).filter(v -> !v.isEmpty()).toList();
    }
}
