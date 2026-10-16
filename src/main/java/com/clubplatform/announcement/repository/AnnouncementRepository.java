package com.clubplatform.announcement.repository;

import com.clubplatform.announcement.entity.Announcement;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {
    Page<Announcement> findByDeletedAtIsNull(Pageable page);

    Optional<Announcement> findByIdAndDeletedAtIsNull(long id);
}
