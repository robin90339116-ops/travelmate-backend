package com.travelmate.config;

import com.travelmate.domain.City;
import com.travelmate.domain.RouteEntity;
import com.travelmate.domain.RoutePoint;
import com.travelmate.domain.Spot;
import com.travelmate.repository.CityRepository;
import com.travelmate.repository.RoutePointRepository;
import com.travelmate.repository.RouteRepository;
import com.travelmate.repository.SpotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时若库为空,写入三城 City Walk 演示数据。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final CityRepository cityRepository;
    private final SpotRepository spotRepository;
    private final RouteRepository routeRepository;
    private final RoutePointRepository routePointRepository;

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void run(String... args) {
        if (cityRepository.count() > 0) {
            return;
        }
        log.info("初始化三城 City Walk 演示数据");

        city("beijing", "北京", "中轴线与胡同交织的古都 City Walk。");
        city("xian", "西安", "城墙内外的十三朝古都漫步。");
        city("chengdu", "成都", "茶馆与街巷慢生活城市漫游。");

        long yongdingmen = spot("beijing", "永定门", "地标", "北京中轴线南起点。",
                "观察中轴线石板走向", "全天", "20 分钟", "历史,建筑", "官方资料", "verified", 39.8709, 116.3974);
        long tiantan = spot("beijing", "天坛", "古建筑", "明清皇帝祭天场所。",
                "祈年殿三重檐结构", "8:00-17:30", "90 分钟", "历史,建筑,拍照", "官方资料", "verified", 39.8822, 116.4066);
        long qianmen = spot("beijing", "前门大街", "商业街", "老北京商业与老字号聚集地。",
                "老字号招牌与街巷肌理", "全天", "60 分钟", "美食,人文", "现场采集", "pending", 39.8951, 116.3975);

        RouteEntity route = route("route-beijing-axis", "beijing", "北京中轴线轻量 City Walk",
                "从永定门沿中轴线向北,串联天坛与前门。", "2.5 小时", "约 4.2 公里", "normal", "story",
                "历史,建筑,拍照", "夏季注意防晒;前门人流较多");
        point(route.getRouteKey(), String.valueOf(yongdingmen), 1, "永定门", "向北", 20, 80, 39.8709, 116.3974);
        point(route.getRouteKey(), String.valueOf(tiantan), 2, "天坛", "东侧步行", 90, 120, 39.8822, 116.4066);
        point(route.getRouteKey(), String.valueOf(qianmen), 3, "前门大街", "沿街北行", 60, 100, 39.8951, 116.3975);

        long citywall = spot("xian", "西安城墙", "古建筑", "现存规模最大的古代城垣之一。",
                "城墙马道与角楼", "8:00-19:00", "120 分钟", "历史,建筑", "官方资料", "verified", 34.2611, 108.9422);
        route("route-xian-wall", "xian", "西安城墙环线", "沿城墙感受古都轮廓。",
                "3 小时", "约 13.7 公里", "deep", "knowledge", "历史,建筑", "骑行需租车;注意日晒");
        point("route-xian-wall", String.valueOf(citywall), 1, "西安城墙", "环墙", 120, 150, 34.2611, 108.9422);

        long kuanzhai = spot("chengdu", "宽窄巷子", "历史街区", "由宽巷子、窄巷子、井巷子组成。",
                "川西院落与茶文化", "全天", "90 分钟", "美食,人文,拍照", "现场采集", "pending", 30.6739, 104.0546);
        route("route-chengdu-kuanzhai", "chengdu", "成都宽窄巷子漫步", "茶馆街巷慢生活体验。",
                "2 小时", "约 3.1 公里", "easy", "family", "美食,人文", "周末人多");
        point("route-chengdu-kuanzhai", String.valueOf(kuanzhai), 1, "宽窄巷子", "巷内漫步", 90, 90, 30.6739, 104.0546);
    }

    private void city(String key, String name, String summary) {
        City c = new City();
        c.setCityKey(key);
        c.setName(name);
        c.setSummary(summary);
        cityRepository.save(c);
    }

    private long spot(String cityKey, String name, String category, String intro, String highlight,
                      String openTime, String duration, String tags, String sourceName, String status,
                      double lat, double lng) {
        Spot s = new Spot();
        s.setCityKey(cityKey);
        s.setName(name);
        s.setCategory(category);
        s.setIntro(intro);
        s.setHighlight(highlight);
        s.setOpenTime(openTime);
        s.setRecommendedDuration(duration);
        s.setTags(tags);
        s.setSourceName("内置演示资料（未经外部核验）");
        s.setSourceStatus("pending");
        s.setLatitude(lat);
        s.setLongitude(lng);
        return spotRepository.save(s).getId();
    }

    private RouteEntity route(String key, String cityKey, String name, String summary, String duration,
                              String distance, String intensity, String style, String tags, String risks) {
        RouteEntity r = new RouteEntity();
        r.setRouteKey(key);
        r.setCityKey(cityKey);
        r.setName(name);
        r.setSummary(summary);
        r.setEstimatedDuration(duration);
        r.setDistanceText(distance);
        r.setIntensity(intensity);
        r.setStyle(style);
        r.setTags(tags);
        r.setRisks(risks);
        return routeRepository.save(r);
    }

    private void point(String routeKey, String spotId, int order, String name, String heading,
                       int stay, int radius, double lat, double lng) {
        RoutePoint p = new RoutePoint();
        p.setRouteKey(routeKey);
        p.setSpotId(spotId);
        p.setOrderIndex(order);
        p.setName(name);
        p.setHeadingText(heading);
        p.setStayMinutes(stay);
        p.setTriggerRadius(radius);
        p.setLatitude(lat);
        p.setLongitude(lng);
        routePointRepository.save(p);
    }
}
