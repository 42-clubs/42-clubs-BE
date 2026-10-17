package com.clubplatform.activity.repository;

import com.clubplatform.activity.entity.Activity;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ActivityRepository extends JpaRepository<Activity, Long> {
    Optional<Activity> findByIdAndClubIdAndDeletedAtIsNull(long id, long clubId);

    Optional<Activity>
            findFirstByClubIdAndDeletedAtIsNullAndStartsAtLessThanEqualOrderByStartsAtDescIdDesc(
                    long clubId, Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Activity a where a.id=:id and a.clubId=:clubId and a.deletedAt is null")
    Optional<Activity> findForUpdate(@Param("clubId") long clubId, @Param("id") long id);

    @Query(
            value =
                    """
 SELECT a.* FROM activities a WHERE a.club_id=:clubId AND a.deleted_at IS NULL
 ORDER BY CASE WHEN a.recruitment_closes_at>:now AND a.starts_at>:now AND (SELECT count(*) FROM activity_participants p WHERE p.activity_id=a.id AND p.status='CONFIRMED')<a.capacity THEN 0 ELSE 1 END,a.starts_at,a.id
 """,
            countQuery =
                    "SELECT count(*) FROM activities WHERE club_id=:clubId AND deleted_at IS NULL",
            nativeQuery = true)
    Page<Activity> findPublic(
            @Param("clubId") long clubId, @Param("now") Instant now, Pageable pageable);
}
