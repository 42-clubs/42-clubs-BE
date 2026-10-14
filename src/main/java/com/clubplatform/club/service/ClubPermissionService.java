package com.clubplatform.club.service;

import com.clubplatform.club.entity.OperationStatus;
import com.clubplatform.club.repository.ClubManagerRepository;
import com.clubplatform.club.repository.ClubRepository;
import com.clubplatform.global.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 활동·공지 작성 전에 현재 동아리 상태와 회장 관계를 검사한다. */
@Service
@RequiredArgsConstructor
public class ClubPermissionService {
    private final ClubRepository clubs;
    private final ClubManagerRepository managers;

    @Transactional(readOnly = true)
    public void requireManager(long actorId, long clubId) {
        var club =
                clubs.findById(clubId)
                        .orElseThrow(() -> new ResourceNotFoundException("동아리를 찾을 수 없습니다."));
        if (club.getOperationStatus() != OperationStatus.ACTIVE
                || !managers.existsByUserIdAndClubId(actorId, clubId)) {
            throw new AccessDeniedException("운영 중인 해당 동아리의 회장 권한이 필요합니다.");
        }
    }
}
