package com.clubplatform.activity.repository;

import com.clubplatform.activity.entity.ActivityParticipant;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityParticipantRepository extends JpaRepository<ActivityParticipant, Long> {
    Optional<ActivityParticipant> findByActivityIdAndUserId(long parentId, long userId);

    long countByActivityIdAndStatus(long parentId, String status);

    Page<ActivityParticipant> findByActivityId(long parentId, Pageable pageable);

    @org.springframework.data.jpa.repository.Query(
            """
        select new com.clubplatform.activity.dto.MyActivity(a.id,a.clubId,a.title,a.startsAt,a.organizerId,p.status,p.attendanceStatus)
        from ActivityParticipant p join Activity a on a.id=p.activityId
        where p.userId=:userId and p.status='CONFIRMED' and a.deletedAt is null
        """)
    java.util.List<com.clubplatform.activity.dto.MyActivity> findMine(
            @org.springframework.data.repository.query.Param("userId") long userId);

    interface ParticipationCount {
        Long getParentId();

        long getConfirmedCount();

        long getPresentCount();
    }

    @org.springframework.data.jpa.repository.Query(
            """
            select p.activityId as parentId, count(p) as confirmedCount,
                sum(case when p.attendanceStatus='PRESENT' then 1 else 0 end) as presentCount
            from ActivityParticipant p where p.activityId in :ids and p.status='CONFIRMED'
            group by p.activityId
            """)
    java.util.List<ParticipationCount> participationCounts(
            @org.springframework.data.repository.query.Param("ids") java.util.List<Long> ids);
}
