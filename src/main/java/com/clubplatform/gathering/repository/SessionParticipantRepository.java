package com.clubplatform.gathering.repository;

import com.clubplatform.gathering.entity.SessionParticipant;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionParticipantRepository extends JpaRepository<SessionParticipant, Long> {
    Optional<SessionParticipant> findBySessionIdAndUserId(long parentId, long userId);

    long countBySessionIdAndStatus(long parentId, String status);

    Page<SessionParticipant> findBySessionId(long parentId, Pageable pageable);

    @org.springframework.data.jpa.repository.Query(
            """
        select new com.clubplatform.gathering.dto.MySession(s.id,g.id,g.title,s.title,s.startsAt,s.endsAt,g.organizerId,p.status,p.attendanceStatus)
        from SessionParticipant p join GatheringSession s on s.id=p.sessionId
        join Gathering g on g.id=s.gatheringId
        where p.userId=:userId and p.status='CONFIRMED' and g.deletedAt is null and s.cancelledAt is null
        """)
    java.util.List<com.clubplatform.gathering.dto.MySession> findMine(
            @org.springframework.data.repository.query.Param("userId") long userId);

    interface ConfirmedCount {
        Long getParentId();

        long getTotal();
    }

    @org.springframework.data.jpa.repository.Query(
            "select p.sessionId as parentId,count(p) as total from SessionParticipant p where p.sessionId in :ids and p.status='CONFIRMED' group by p.sessionId")
    java.util.List<ConfirmedCount> confirmedCounts(
            @org.springframework.data.repository.query.Param("ids") java.util.List<Long> ids);
}
