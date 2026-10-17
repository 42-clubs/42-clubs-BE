package com.clubplatform.club.service;

import com.clubplatform.club.entity.ClubManager;
import com.clubplatform.club.entity.OperationStatus;
import com.clubplatform.club.repository.ClubManagerRepository;
import com.clubplatform.club.repository.ClubRepository;
import com.clubplatform.global.exception.ResourceNotFoundException;
import com.clubplatform.user.dto.UserDirectoryPage;
import com.clubplatform.user.service.UserAdministrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClubManagerAdministrationService {
    private final ClubRepository clubs;
    private final ClubManagerRepository managers;
    private final UserAdministrationService users;

    public UserDirectoryPage list(long actorId, long clubId, int page, int size) {
        users.requireAdmin(actorId);
        if (!clubs.existsById(clubId)) {
            throw new ResourceNotFoundException("동아리를 찾을 수 없습니다.");
        }
        var relations =
                managers.findByClubId(clubId, PageRequest.of(page, size, Sort.by("userId")));
        var summaries =
                users.findSummaries(relations.stream().map(ClubManager::getUserId).toList());
        return UserDirectoryPage.from(relations.map(manager -> summaries.get(manager.getUserId())));
    }

    @Transactional
    public void change(long actorId, long clubId, long userId, boolean granted) {
        users.requireAdmin(actorId);
        // 같은 동아리의 부여·회수를 직렬화해 확인과 저장 사이의 중복 생성을 막는다.
        var club =
                clubs.findForUpdate(clubId)
                        .orElseThrow(() -> new ResourceNotFoundException("동아리를 찾을 수 없습니다."));
        if (club.getOperationStatus() != OperationStatus.ACTIVE)
            throw new AccessDeniedException("아카이브 동아리의 회장 관계는 변경할 수 없습니다.");
        users.requireExisting(userId);
        if (granted) {
            if (!managers.existsByUserIdAndClubId(userId, clubId)) {
                managers.save(new ClubManager(userId, clubId));
            }
        } else {
            managers.deleteByUserIdAndClubId(userId, clubId);
        }
    }
}
