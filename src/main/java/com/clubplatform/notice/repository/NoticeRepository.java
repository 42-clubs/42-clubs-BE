package com.clubplatform.notice.repository;

import com.clubplatform.notice.entity.Notice;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeRepository extends JpaRepository<Notice, Long> {
    Page<Notice> findByClubIdAndDeletedAtIsNull(long clubId, Pageable pageable);

    Optional<Notice> findByIdAndDeletedAtIsNull(long id);
}
