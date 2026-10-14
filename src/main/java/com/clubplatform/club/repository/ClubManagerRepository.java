package com.clubplatform.club.repository;

import com.clubplatform.club.entity.ClubManager;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClubManagerRepository extends JpaRepository<ClubManager, Long> {
    boolean existsByUserIdAndClubId(long userId, long clubId);
}
