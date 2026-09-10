package com.travelmate.ai;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
public interface GuideJobRepository extends JpaRepository<GuideJob,String> {
 java.util.List<GuideJob> findByUserId(Long userId);
 java.util.List<GuideJob> findByTeamIdOrderByCreatedAtAsc(Long teamId);
 @Modifying @Transactional
 @Query("update GuideJob j set j.status='running',j.updatedAt=:now where j.id=:id and j.status='queued'")
 int claim(@Param("id") String id,@Param("now") Instant now);
 @Modifying @Transactional
 @Query("update GuideJob j set j.status=:status,j.content=:content,j.error=:error,j.updatedAt=:now where j.id=:id and j.status='running'")
 int finish(@Param("id") String id,@Param("status") String status,@Param("content") String content,@Param("error") String error,@Param("now") Instant now);
 @Modifying @Transactional
 @Query("update GuideJob j set j.status='cancelled',j.updatedAt=:now where j.id=:id and j.userId=:uid and j.status in ('queued','running')")
 int cancel(@Param("id") String id,@Param("uid") Long uid,@Param("now") Instant now);
 @Modifying @Transactional
 @Query("update GuideJob j set j.status='failed',j.error='Worker unavailable or deadline exceeded',j.updatedAt=:now where j.status in ('queued','running') and j.updatedAt<:before")
 int expire(@Param("before") Instant before,@Param("now") Instant now);
}
