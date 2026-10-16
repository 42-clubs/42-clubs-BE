package com.clubplatform.activity.repository;

import com.clubplatform.activity.entity.ActivityImage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityImageRepository extends JpaRepository<ActivityImage, Long> {
    List<ActivityImage> findByActivityIdOrderBySortOrder(long activityId);

    List<ActivityImage> findByActivityIdInOrderByActivityIdAscSortOrderAsc(List<Long> ids);
}
