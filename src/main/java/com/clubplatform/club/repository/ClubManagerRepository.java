package com.clubplatform.club.repository;

import com.clubplatform.club.entity.ClubManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClubManagerRepository extends JpaRepository<ClubManager, Long> {
    boolean existsByUserIdAndClubId(long userId, long clubId);

    Page<ClubManager> findByClubId(long clubId, Pageable pageable);

    void deleteByUserIdAndClubId(long userId, long clubId);
}
