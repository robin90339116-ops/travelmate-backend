package com.travelmate.catalog;

import com.travelmate.catalog.CatalogDtos.*;
import com.travelmate.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/catalog")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalogService;

    @GetMapping("/cities")
    public Result<List<CityView>> cities() {
        return Result.ok(catalogService.listCities());
    }

    @GetMapping("/cities/{cityKey}/spots")
    public Result<List<SpotView>> spots(@PathVariable String cityKey) {
        return Result.ok(catalogService.listSpots(cityKey));
    }

    @GetMapping("/cities/{cityKey}/routes")
    public Result<List<RouteView>> routes(@PathVariable String cityKey) {
        return Result.ok(catalogService.listRoutes(cityKey));
    }

    @GetMapping("/routes/{routeKey}")
    public Result<RouteView> route(@PathVariable String routeKey) {
        return Result.ok(catalogService.getRoute(routeKey));
    }
}
