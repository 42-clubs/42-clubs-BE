package com.clubplatform.clubdetail.service;

import com.clubplatform.activity.service.ActivitySummaryQueryService;
import com.clubplatform.club.dto.ClubDetailResponse;
import com.clubplatform.club.dto.ClubDetailResponse.RecentActivityResponse;
import com.clubplatform.club.service.ClubQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** club·activity의 공개 조회 결과를 조합하며 기능 간 순환 의존성을 만들지 않는다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClubDetailQueryService {
    private final ClubQueryService clubs;
    private final ActivitySummaryQueryService activities;

    public ClubDetailResponse findClub(long clubId) {
        var club = clubs.findClub(clubId);
        var recent =
                activities
                        .recentActivity(clubId)
                        .map(
                                activity ->
                                        new RecentActivityResponse(
                                                activity.id(),
                                                activity.title(),
                                                activity.startsAt(),
                                                activity.currentParticipantCount(),
                                                activity.presentParticipantCount()))
                        .orElse(null);
        return club.withRecentActivity(recent);
    }
}
