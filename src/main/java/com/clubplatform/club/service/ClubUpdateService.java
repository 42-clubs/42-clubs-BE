package com.clubplatform.club.service;

import com.clubplatform.club.dto.ClubUpdateRequest;
import com.clubplatform.club.entity.OperationStatus;
import com.clubplatform.club.repository.ClubManagerRepository;
import com.clubplatform.club.repository.ClubRepository;
import com.clubplatform.global.exception.ResourceNotFoundException;
import com.clubplatform.user.service.UserAdministrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClubUpdateService {
    private final ClubRepository clubs;
    private final ClubManagerRepository managers;
    private final UserAdministrationService users;

    @Transactional
    public void update(long actorId, long clubId, ClubUpdateRequest request) {
        var club =
                clubs.findForUpdate(clubId)
                        .orElseThrow(() -> new ResourceNotFoundException("동아리를 찾을 수 없습니다."));
        if (club.getOperationStatus() != OperationStatus.ACTIVE)
            throw new AccessDeniedException("아카이브 동아리의 기본정보는 수정할 수 없습니다.");
        if (!users.isAdmin(actorId)) {
            if (request.name() != null)
                throw new AccessDeniedException("동아리 이름은 ADMIN만 수정할 수 있습니다.");
            if (club.getOperationStatus() != OperationStatus.ACTIVE
                    || !managers.existsByUserIdAndClubId(actorId, clubId))
                throw new AccessDeniedException("운영 중인 해당 동아리의 회장 권한이 필요합니다.");
        }
        club.updateBasicInfo(request.name(), request.summary(), request.description());
    }
}
