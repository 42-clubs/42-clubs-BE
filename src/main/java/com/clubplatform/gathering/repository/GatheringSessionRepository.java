package com.clubplatform.gathering.repository;

import com.clubplatform.gathering.entity.GatheringSession;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface GatheringSessionRepository extends JpaRepository<GatheringSession, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from GatheringSession s where s.id=:id")
    Optional<GatheringSession> findForUpdate(@Param("id") long id);

    @Query(
            "select s from GatheringSession s where s.gatheringId=:id and (:filter='ALL' or (:filter='UPCOMING' and s.startsAt>:now) or (:filter='ONGOING' and s.startsAt<=:now and s.endsAt>:now) or (:filter='PAST' and s.endsAt<=:now))")
    Page<GatheringSession> findFiltered(
            @Param("id") long id,
            @Param("filter") String filter,
            @Param("now") Instant now,
            Pageable page);

    @Query(
            value =
                    """
 SELECT * FROM gathering_sessions WHERE gathering_id=:id AND cancelled_at IS NULL
 ORDER BY CASE WHEN starts_at<=:now AND ends_at>:now THEN 0 WHEN starts_at>:now THEN 1 ELSE 2 END,
 CASE WHEN ends_at>:now THEN starts_at END ASC,CASE WHEN ends_at<=:now THEN starts_at END DESC,id LIMIT 1
 """,
            nativeQuery = true)
    Optional<GatheringSession> representative(@Param("id") long id, @Param("now") Instant now);

    @Query(
            value =
                    """
 SELECT ranked.* FROM (SELECT s.*,row_number() OVER (PARTITION BY gathering_id ORDER BY
 CASE WHEN starts_at<=:now AND ends_at>:now THEN 0 WHEN starts_at>:now THEN 1 ELSE 2 END,
 CASE WHEN ends_at>:now THEN starts_at END ASC,CASE WHEN ends_at<=:now THEN starts_at END DESC,id) AS rn
 FROM gathering_sessions s WHERE gathering_id IN (:ids) AND cancelled_at IS NULL) ranked WHERE rn=1
 """,
            nativeQuery = true)
    List<GatheringSession> representatives(@Param("ids") List<Long> ids, @Param("now") Instant now);
}
