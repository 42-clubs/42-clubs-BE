package com.clubplatform.gathering.repository;

import com.clubplatform.gathering.entity.SessionParticipant;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionParticipantRepository extends JpaRepository<SessionParticipant, Long> {
    Optional<SessionParticipant> findBySessionIdAndUserId(long parentId, long userId);

    long countBySessionIdAndStatus(long parentId, String status);

    Page<SessionParticipant> findBySessionId(long parentId, Pageable pageable);

    interface ConfirmedCount {
        Long getParentId();

        long getTotal();
    }

    @org.springframework.data.jpa.repository.Query(
            "select p.sessionId as parentId,count(p) as total from SessionParticipant p where p.sessionId in :ids and p.status='CONFIRMED' group by p.sessionId")
    java.util.List<ConfirmedCount> confirmedCounts(
            @org.springframework.data.repository.query.Param("ids") java.util.List<Long> ids);
}
