package com.clubplatform.activity.service;

import com.clubplatform.activity.dto.ActivityUpdateRequest;
import com.clubplatform.activity.repository.ActivityRepository;
import com.clubplatform.club.service.ClubPermissionService;
import com.clubplatform.global.exception.ResourceNotFoundException;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ActivityUpdateService {
    private final ActivityRepository activities;
    private final ClubPermissionService clubs;
    private final Clock clock;

    @Transactional
    public void update(long actorId, long clubId, long id, ActivityUpdateRequest request) {
        clubs.requireActivityEditor(actorId, clubId);
        var activity =
                activities
                        .findForUpdate(clubId, id)
                        .orElseThrow(() -> new ResourceNotFoundException("활동을 찾을 수 없습니다."));
        activity.updateText(request.title(), request.content(), clock.instant());
    }
}
