package com.clubplatform.activity.repository;

import com.clubplatform.activity.entity.ActivityParticipant;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityParticipantRepository extends JpaRepository<ActivityParticipant, Long> {
    Optional<ActivityParticipant> findByActivityIdAndUserId(long parentId, long userId);

    long countByActivityIdAndStatus(long parentId, String status);

    Page<ActivityParticipant> findByActivityId(long parentId, Pageable pageable);

    interface ConfirmedCount {
        Long getParentId();

        long getTotal();
    }

    @org.springframework.data.jpa.repository.Query(
            "select p.activityId as parentId,count(p) as total from ActivityParticipant p where p.activityId in :ids and p.status='CONFIRMED' group by p.activityId")
    java.util.List<ConfirmedCount> confirmedCounts(
            @org.springframework.data.repository.query.Param("ids") java.util.List<Long> ids);
}
