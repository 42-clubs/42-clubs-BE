package com.clubplatform.club.service;

import com.clubplatform.club.entity.OperationStatus;
import com.clubplatform.club.repository.ClubManagerRepository;
import com.clubplatform.club.repository.ClubMemberRepository;
import com.clubplatform.club.repository.ClubRepository;
import com.clubplatform.global.exception.ResourceNotFoundException;
import com.clubplatform.user.service.UserAdministrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 동아리의 활동 작성과 공지 작성·비공개 조회에 필요한 현재 역할·관계를 검사한다. */
@Service
@RequiredArgsConstructor
public class ClubPermissionService {
    private final UserAdministrationService users;
    private final ClubMemberRepository members;
    private final ClubRepository clubs;
    private final ClubManagerRepository managers;

    @Transactional(readOnly = true)
    public void requireExisting(long clubId) {
        if (!clubs.existsById(clubId)) throw new ResourceNotFoundException("동아리를 찾을 수 없습니다.");
    }

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

    @Transactional(readOnly = true)
    public void requireNoticeWriter(long actorId, long clubId) {
        var club =
                clubs.findById(clubId)
                        .orElseThrow(() -> new ResourceNotFoundException("동아리를 찾을 수 없습니다."));
        if (club.getOperationStatus() != OperationStatus.ACTIVE
                || (!users.isAdmin(actorId) && !managers.existsByUserIdAndClubId(actorId, clubId)))
            throw new AccessDeniedException("운영 중인 동아리의 회장 또는 운영 권한이 필요합니다.");
    }

    @Transactional(readOnly = true)
    public boolean canReadPrivateNotices(Long actorId, long clubId) {
        return actorId != null
                && (users.isAdmin(actorId)
                        || managers.existsByUserIdAndClubId(actorId, clubId)
                        || members.existsByUserIdAndClubIdAndStatus(actorId, clubId, "ACTIVE"));
    }
}
