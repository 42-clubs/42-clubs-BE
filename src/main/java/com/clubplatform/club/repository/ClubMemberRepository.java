package com.clubplatform.club.repository;

import com.clubplatform.club.entity.ClubMember;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClubMemberRepository extends JpaRepository<ClubMember, Long> {
    Optional<ClubMember> findByUserIdAndClubId(long userId, long clubId);

    boolean existsByUserIdAndClubIdAndStatus(long userId, long clubId, String status);

    List<ClubMember> findByUserIdAndStatus(long userId, String status);

    Page<ClubMember> findByClubIdAndStatus(long clubId, String status, Pageable pageable);

    /** ACTIVE 부원과 현재 회장의 합집합을 집계하며 같은 사용자는 한 번만 센다. */
    @Query(
            value =
                    """
                    SELECT COUNT(*) FROM (
                        SELECT user_id FROM club_members WHERE club_id = :clubId AND status = 'ACTIVE'
                        UNION
                        SELECT user_id FROM club_managers WHERE club_id = :clubId
                    ) members_including_managers
                    """,
            nativeQuery = true)
    long countMembersIncludingManagers(@Param("clubId") long clubId);
}
