package com.clubplatform.activity.repository;

import com.clubplatform.activity.entity.ActivityImage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityImageRepository extends JpaRepository<ActivityImage, Long> {
    @org.springframework.data.jpa.repository.Query(
            "select (count(i) > 0) from ActivityImage i, Activity a where i.activityId=a.id and i.storageKey=:key and a.deletedAt is null")
    boolean isPublished(@org.springframework.data.repository.query.Param("key") String key);

    List<ActivityImage> findByActivityIdOrderBySortOrder(long activityId);

    List<ActivityImage> findByActivityIdInOrderByActivityIdAscSortOrderAsc(List<Long> ids);
}
