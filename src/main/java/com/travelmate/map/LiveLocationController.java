package com.travelmate.map;
import com.travelmate.common.*;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import java.util.Map;
@RestController @RequestMapping("/api/location") @RequiredArgsConstructor
public class LiveLocationController {
 private final LiveLocationService service;
 public record Selection(String contextToken,String poiId){}
 public record Token(String contextToken){}
 @GetMapping("/status") public Result<?> status(){return Result.ok(service.status());}
 @PostMapping("/nearby") public Result<?> nearby(@RequestBody LiveLocationService.Position p){return Result.ok(service.nearby(CurrentUser.id(),p));}
 @PostMapping("/select") public Result<?> select(@RequestBody Selection s){return Result.ok(service.select(s.contextToken(),CurrentUser.id(),s.poiId()));}
 @PostMapping("/research") public Result<?> research(@RequestBody Token t){return Result.ok(service.research(t.contextToken(),CurrentUser.id()));}
 @DeleteMapping public Result<?> clear(){service.clear(CurrentUser.id());return Result.ok();}
}
