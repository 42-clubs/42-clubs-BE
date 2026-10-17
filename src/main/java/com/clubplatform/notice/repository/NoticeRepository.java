package com.clubplatform.notice.repository;

import com.clubplatform.notice.entity.Notice;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface NoticeRepository extends JpaRepository<Notice, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from Notice n where n.id=:id and n.clubId=:clubId")
    Optional<Notice> findForUpdate(@Param("clubId") long clubId, @Param("id") long id);

    @Query(
            "select n from Notice n where n.clubId=:clubId and n.deletedAt is null and (:includePrivate=true or n.visibility=com.clubplatform.notice.entity.NoticeVisibility.PUBLIC)")
    Page<Notice> findVisible(
            @Param("clubId") long clubId,
            @Param("includePrivate") boolean includePrivate,
            Pageable page);

    @Query(
            "select n from Notice n where n.id=:id and n.clubId=:clubId and n.deletedAt is null and (:includePrivate=true or n.visibility=com.clubplatform.notice.entity.NoticeVisibility.PUBLIC)")
    Optional<Notice> findVisibleById(
            @Param("clubId") long clubId,
            @Param("id") long id,
            @Param("includePrivate") boolean includePrivate);
}
