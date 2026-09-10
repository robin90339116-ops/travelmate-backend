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

    private final RestClient restClient = mapClient();
    private static RestClient mapClient() {
        var factory=new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);factory.setReadTimeout(10000);
        return RestClient.builder().requestFactory(factory).build();
    }

    public record WalkingRequest(@jakarta.validation.constraints.NotBlank String origin,
            @jakarta.validation.constraints.NotBlank String destination) {}

    @PostMapping("/route-validate")
    public Result<?> walking(@jakarta.validation.Valid @RequestBody WalkingRequest request) {
        validateCoordinate(request.origin());validateCoordinate(request.destination());
        if(amapKey.isBlank())throw ApiException.serviceUnavailable("高德服务未配置");
        var uri=java.net.URI.create("https://restapi.amap.com/v5/direction/walking?key="+enc(amapKey)
                +"&origin="+enc(request.origin())+"&destination="+enc(request.destination()));
        try {
            Map<?,?> data=restClient.get().uri(uri).retrieve().body(Map.class);
            if(data==null||!"1".equals(data.get("status")))throw ApiException.serviceUnavailable("高德路线查询失败");
            return Result.ok(data);
        } catch (org.springframework.web.client.RestClientException e) {
            throw ApiException.serviceUnavailable("高德服务暂时不可用");
        }
    }
    private void validateCoordinate(String coordinate) {
        try {
            String[] parts=coordinate.split(",");if(parts.length!=2)throw new IllegalArgumentException();
            double lng=Double.parseDouble(parts[0]),lat=Double.parseDouble(parts[1]);
            if(!Double.isFinite(lng)||!Double.isFinite(lat)||Math.abs(lng)>180||Math.abs(lat)>90)throw new IllegalArgumentException();
        } catch(Exception e){throw ApiException.badRequest("坐标必须为有效的经度,纬度");}
    }

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
        Map<String, Object> data;
        try { data = restClient.get().uri(java.net.URI.create(uri.toString())).retrieve().body(Map.class); }
        catch (org.springframework.web.client.RestClientException e) { throw ApiException.serviceUnavailable("高德服务暂时不可用"); }
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
