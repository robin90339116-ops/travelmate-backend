package com.travelmate.map;

import com.travelmate.common.ApiException;
import com.travelmate.common.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 高德地图:POI 搜索与步行路线校验(Web 服务 API)。
 */
@Slf4j
@RestController
@RequestMapping("/api/map")
@RequiredArgsConstructor
public class MapController {

    @Value("${app.map.amap-web-key:}")
    private String amapKey;

    private final RestClient restClient = RestClient.create();

    public record SearchRequest(String keyword, String city) {
    }

    public record PoiView(String id, String name, String address, String location) {
    }

    public record SearchResponse(String provider, String keyword, List<PoiView> results) {
    }

    @PostMapping("/search")
    @SuppressWarnings("unchecked")
    public Result<SearchResponse> search(@RequestBody SearchRequest request) {
        String keyword = request.keyword() == null ? "" : request.keyword().trim();
        if (keyword.isEmpty()) {
            throw ApiException.badRequest("请输入要搜索的地点关键词");
        }
        if (amapKey.isBlank()) {
            throw ApiException.serviceUnavailable("AMAP 未配置,请设置 app.map.amap-web-key(AMAP_WEB_KEY)");
        }
        StringBuilder uri = new StringBuilder("https://restapi.amap.com/v5/place/text")
                .append("?key=").append(amapKey)
                .append("&keywords=").append(enc(keyword))
                .append("&page_size=10");
        if (request.city() != null && !request.city().isBlank()) {
            uri.append("&region=").append(enc(request.city())).append("&city_limit=true");
        }
        Map<String, Object> data = restClient.get().uri(uri.toString()).retrieve().body(Map.class);
        if (data == null || !"1".equals(String.valueOf(data.get("status")))) {
            throw ApiException.serviceUnavailable("高德地点搜索失败:" + (data == null ? "空响应" : data.get("info")));
        }
        List<PoiView> results = new ArrayList<>();
        Object pois = data.get("pois");
        if (pois instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> poi) {
                    results.add(new PoiView(
                            str(poi.get("id")), str(poi.get("name")),
                            addressOf(poi), str(poi.get("location"))));
                }
            }
        }
        return Result.ok(new SearchResponse("amap", keyword, results));
    }

    private String addressOf(Map<?, ?> poi) {
        Object address = poi.get("address");
        if (address instanceof String s && !s.isBlank()) {
            return s;
        }
        return str(poi.get("pname")) + str(poi.get("cityname")) + str(poi.get("adname"));
    }

    private String str(Object o) {
        return o == null ? "" : o.toString();
    }

    private String enc(String v) {
        return java.net.URLEncoder.encode(v, java.nio.charset.StandardCharsets.UTF_8);
    }
}
