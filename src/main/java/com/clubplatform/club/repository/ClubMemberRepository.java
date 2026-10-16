package com.clubplatform.club.repository;

import com.clubplatform.club.entity.ClubMember;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClubMemberRepository extends JpaRepository<ClubMember, Long> {
    Optional<ClubMember> findByUserIdAndClubId(long userId, long clubId);

    boolean existsByUserIdAndClubIdAndStatus(long userId, long clubId, String status);

    List<ClubMember> findByUserIdAndStatus(long userId, String status);

    Page<ClubMember> findByClubIdAndStatus(long clubId, String status, Pageable pageable);
}
