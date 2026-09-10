package com.travelmate.favorite;

import com.travelmate.common.ApiException;
import com.travelmate.common.CurrentUser;
import com.travelmate.common.Result;
import com.travelmate.domain.Favorite;
import com.travelmate.repository.FavoriteRepository;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

/**
 * 个人收藏,按当前登录用户隔离。
 */
@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteRepository favoriteRepository;
    private final com.travelmate.repository.UserRepository users;
    private final com.travelmate.repository.SpotRepository spots;
    private final com.travelmate.repository.RouteRepository routes;

    public record FavoriteView(Long id, String targetType, String title, String subtitle, Instant createdAt, String targetId) {
    }

    public record CreateFavoriteRequest(
            @NotBlank @jakarta.validation.constraints.Pattern(regexp="spot|point|route") String targetType,
            @NotBlank @jakarta.validation.constraints.Size(max=128) String title,
            @jakarta.validation.constraints.Size(max=256) String subtitle,
            @jakarta.validation.constraints.Size(max=64) String targetId) {
    }

    @GetMapping
    public Result<List<FavoriteView>> list() {
        List<FavoriteView> views = favoriteRepository.findByUserIdOrderByCreatedAtDesc(CurrentUser.id()).stream()
                .map(f -> view(f))
                .toList();
        return Result.ok(views);
    }

    @PostMapping
    @org.springframework.transaction.annotation.Transactional
    public Result<FavoriteView> create(@jakarta.validation.Valid @RequestBody CreateFavoriteRequest request) {
        // Serialize per user across instances; the database constraint remains the final guard.
        users.lockById(CurrentUser.id()).orElseThrow(()->ApiException.notFound("账号不存在"));
        String targetId=request.targetId();
        String targetType=request.targetType().equals("point")?"spot":request.targetType();
        if(targetId!=null){
            if(targetId.isBlank())throw ApiException.badRequest("收藏目标不能为空");
            boolean exists;
            if(targetType.equals("spot")){
                long id;try{id=Long.parseLong(targetId);}catch(NumberFormatException e){throw ApiException.badRequest("收藏目标无效");}
                exists=spots.existsById(id);targetId=Long.toString(id);
            }else{exists=routes.findByRouteKey(targetId).isPresent();}
            if(!exists)throw ApiException.notFound("收藏目标不存在");
            var existing=favoriteRepository.findByUserIdAndTargetTypeAndTargetId(CurrentUser.id(),targetType,targetId);
            if(existing.isPresent())return Result.ok(view(existing.get()));
        }
        Favorite favorite = new Favorite();
        favorite.setUserId(CurrentUser.id());
        favorite.setTargetType(targetType);
        favorite.setTargetId(targetId);
        favorite.setTitle(request.title());
        favorite.setSubtitle(request.subtitle());
        Favorite saved = favoriteRepository.save(favorite);
        return Result.ok(view(saved));
    }

    private FavoriteView view(Favorite f){return new FavoriteView(f.getId(),f.getTargetType(),f.getTitle(),f.getSubtitle(),f.getCreatedAt(),f.getTargetId());}

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Favorite favorite = favoriteRepository.findByIdAndUserId(id, CurrentUser.id())
                .orElseThrow(() -> ApiException.notFound("收藏不存在"));
        favoriteRepository.delete(favorite);
        return Result.ok();
    }
}
