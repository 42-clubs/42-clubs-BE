package com.clubplatform.activity.service;

import com.clubplatform.activity.dto.ActivityAttendanceCounts;
import com.clubplatform.activity.dto.RecentActivitySummary;
import com.clubplatform.activity.repository.ActivityParticipantRepository;
import com.clubplatform.activity.repository.ActivityRepository;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 다른 기능에 의존하지 않고 활동과 출석 집계를 조회한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActivitySummaryQueryService {
    private final ActivityRepository activities;
    private final ActivityParticipantRepository participants;
    private final Clock clock;

    public Map<Long, ActivityAttendanceCounts> counts(List<Long> activityIds) {
        if (activityIds.isEmpty()) return Map.of();
        return participants.participationCounts(activityIds).stream()
                .collect(
                        Collectors.toMap(
                                ActivityParticipantRepository.ParticipationCount::getParentId,
                                row ->
                                        new ActivityAttendanceCounts(
                                                row.getConfirmedCount(), row.getPresentCount())));
    }

    public Optional<RecentActivitySummary> recentActivity(long clubId) {
        return activities
                .findFirstByClubIdAndDeletedAtIsNullAndStartsAtLessThanEqualOrderByStartsAtDescIdDesc(
                        clubId, clock.instant())
                .map(
                        activity -> {
                            var counts =
                                    counts(List.of(activity.getId()))
                                            .getOrDefault(
                                                    activity.getId(),
                                                    ActivityAttendanceCounts.EMPTY);
                            return new RecentActivitySummary(
                                    activity.getId(),
                                    activity.getTitle(),
                                    activity.getStartsAt(),
                                    counts.currentParticipantCount(),
                                    counts.presentParticipantCount());
                        });
    }
}
