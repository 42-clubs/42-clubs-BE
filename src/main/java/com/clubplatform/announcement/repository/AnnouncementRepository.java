package com.clubplatform.announcement.repository;

import com.clubplatform.announcement.entity.Announcement;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Announcement a where a.id=:id")
    Optional<Announcement> findForUpdate(@Param("id") long id);

    Page<Announcement> findByDeletedAtIsNull(Pageable page);

    Optional<Announcement> findByIdAndDeletedAtIsNull(long id);
}
