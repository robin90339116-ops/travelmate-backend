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

    public record FavoriteView(Long id, String targetType, String title, String subtitle, Instant createdAt) {
    }

    public record CreateFavoriteRequest(
            @NotBlank String targetType, @NotBlank String title, String subtitle) {
    }

    @GetMapping
    public Result<List<FavoriteView>> list() {
        List<FavoriteView> views = favoriteRepository.findByUserIdOrderByCreatedAtDesc(CurrentUser.id()).stream()
                .map(f -> new FavoriteView(f.getId(), f.getTargetType(), f.getTitle(), f.getSubtitle(), f.getCreatedAt()))
                .toList();
        return Result.ok(views);
    }

    @PostMapping
    public Result<FavoriteView> create(@RequestBody CreateFavoriteRequest request) {
        Favorite favorite = new Favorite();
        favorite.setUserId(CurrentUser.id());
        favorite.setTargetType(request.targetType());
        favorite.setTitle(request.title());
        favorite.setSubtitle(request.subtitle());
        Favorite saved = favoriteRepository.save(favorite);
        return Result.ok(new FavoriteView(saved.getId(), saved.getTargetType(), saved.getTitle(),
                saved.getSubtitle(), saved.getCreatedAt()));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Favorite favorite = favoriteRepository.findByIdAndUserId(id, CurrentUser.id())
                .orElseThrow(() -> ApiException.notFound("收藏不存在"));
        favoriteRepository.delete(favorite);
        return Result.ok();
    }
}
