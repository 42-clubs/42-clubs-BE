package com.clubplatform.activity.service;

import com.clubplatform.activity.dto.*;
import com.clubplatform.activity.entity.*;
import com.clubplatform.activity.repository.*;
import com.clubplatform.activity.storage.PhotoStorage;
import com.clubplatform.club.service.ClubPermissionService;
import com.clubplatform.global.dto.PageResponse;
import com.clubplatform.global.exception.ResourceNotFoundException;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActivityQueryService {
    private final ActivityRepository activities;
    private final ActivityParticipantRepository participants;
    private final ActivityImageRepository images;
    private final ClubPermissionService clubs;
    private final PhotoStorage storage;
    private final Clock clock;

    public PageResponse<ActivityResponse> list(long clubId, int page, int size) {
        clubs.requireExisting(clubId);
        var now = clock.instant();
        var rows = activities.findPublic(clubId, now, PageRequest.of(page, size));
        var ids = rows.stream().map(Activity::getId).toList();
        if (ids.isEmpty())
            return PageResponse.from(rows.map(a -> response(a, 0, List.of(), false, now)));
        var counts =
                participants.confirmedCounts(ids).stream()
                        .collect(
                                Collectors.toMap(
                                        ActivityParticipantRepository.ConfirmedCount::getParentId,
                                        ActivityParticipantRepository.ConfirmedCount::getTotal));
        var photos =
                images.findByActivityIdInOrderByActivityIdAscSortOrderAsc(ids).stream()
                        .collect(Collectors.groupingBy(ActivityImage::getActivityId));
        return PageResponse.from(
                rows.map(
                        a ->
                                response(
                                        a,
                                        counts.getOrDefault(a.getId(), 0L),
                                        photos.getOrDefault(a.getId(), List.of()),
                                        false,
                                        now)));
    }

    public ActivityResponse detail(long id) {
        var a =
                activities
                        .findByIdAndDeletedAtIsNull(id)
                        .orElseThrow(() -> new ResourceNotFoundException("활동을 찾을 수 없습니다."));
        return response(
                a,
                participants.countByActivityIdAndStatus(id, "CONFIRMED"),
                images.findByActivityIdOrderBySortOrder(id),
                true,
                clock.instant());
    }

    private ActivityResponse response(
            Activity a, long count, List<ActivityImage> photos, boolean detail, Instant now) {
        String representative =
                photos.isEmpty() ? null : storage.url(photos.get(0).getStorageKey());
        var imageResponses =
                detail
                        ? photos.stream()
                                .map(
                                        p ->
                                                new ImageResponse(
                                                        storage.url(p.getStorageKey()),
                                                        p.getSortOrder()))
                                .toList()
                        : List.<ImageResponse>of();
        long days =
                ChronoUnit.DAYS.between(
                        now.atZone(ZoneId.of("Asia/Seoul")).toLocalDate(),
                        a.getStartsAt().atZone(ZoneId.of("Asia/Seoul")).toLocalDate());
        return new ActivityResponse(
                a.getId(),
                a.getClubId(),
                a.getOrganizerId(),
                a.getTitle(),
                detail ? a.getContent() : null,
                a.getStartsAt(),
                a.getRecruitmentClosesAt(),
                a.getCapacity(),
                count,
                Math.max(0, a.getCapacity() - count),
                now.isBefore(a.getRecruitmentClosesAt())
                        && now.isBefore(a.getStartsAt())
                        && count < a.getCapacity(),
                days < 0 ? "종료" : days == 0 ? "D-Day" : "D-" + days,
                representative,
                imageResponses);
    }
}
