package com.clubplatform.gathering.repository;

import com.clubplatform.gathering.entity.Gathering;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface GatheringRepository extends JpaRepository<Gathering, Long> {
    Optional<Gathering> findByIdAndDeletedAtIsNull(long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from Gathering g where g.id=:id and g.deletedAt is null")
    Optional<Gathering> findForUpdate(@Param("id") long id);

    @Query(
            value =
                    """
 SELECT g.* FROM gatherings g
 JOIN LATERAL (SELECT s.* FROM gathering_sessions s WHERE s.gathering_id=g.id AND s.cancelled_at IS NULL
 ORDER BY CASE WHEN s.starts_at<=:now AND s.ends_at>:now THEN 0 WHEN s.starts_at>:now THEN 1 ELSE 2 END,
 CASE WHEN s.ends_at>:now THEN s.starts_at END ASC,CASE WHEN s.ends_at<=:now THEN s.starts_at END DESC,s.id LIMIT 1) r ON true
 WHERE g.deleted_at IS NULL
 ORDER BY CASE WHEN r.recruitment_opens_at<=:now AND r.recruitment_closes_at>:now AND r.starts_at>:now AND (SELECT count(*) FROM gathering_session_participants p WHERE p.session_id=r.id AND p.status='CONFIRMED')<r.capacity THEN 0 ELSE 1 END,r.starts_at,g.id
 """,
            countQuery =
                    "SELECT count(*) FROM gatherings g WHERE g.deleted_at IS NULL AND EXISTS(SELECT 1 FROM gathering_sessions s WHERE s.gathering_id=g.id AND s.cancelled_at IS NULL)",
            nativeQuery = true)
    Page<Gathering> findPublic(@Param("now") Instant now, Pageable pageable);
}
