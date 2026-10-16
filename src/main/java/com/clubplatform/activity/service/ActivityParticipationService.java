package com.clubplatform.activity.service;

import com.clubplatform.activity.dto.*;
import com.clubplatform.activity.entity.*;
import com.clubplatform.activity.repository.*;
import com.clubplatform.global.dto.PageResponse;
import com.clubplatform.global.exception.*;
import com.clubplatform.user.service.UserAdministrationService;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActivityParticipationService {
    private final ActivityRepository activities;
    private final ActivityParticipantRepository participants;
    private final UserAdministrationService users;
    private final Clock clock;

    @Transactional
    public void join(long actorId, long clubId, long id) {
        var resource = activities.findForUpdate(clubId, id).orElseThrow(() -> missing());
        long organizerId = resource.getOrganizerId();
        var now = clock.instant();
        resource.requireRecruiting(now);
        var existing = participants.findByActivityIdAndUserId(id, actorId);
        if (existing.isPresent() && "CONFIRMED".equals(existing.get().getStatus()))
            throw new ConflictException("ALREADY_JOINED", "이미 참가 중입니다.");
        if (participants.countByActivityIdAndStatus(id, "CONFIRMED") >= resource.getCapacity())
            throw new ConflictException("CAPACITY_FULL", "정원이 찼습니다.");
        if (existing.isPresent()) existing.get().confirm(now);
        else participants.save(new ActivityParticipant(id, actorId, now));
    }

    @Transactional
    public void cancel(long actorId, long clubId, long id) {
        var resource = activities.findForUpdate(clubId, id).orElseThrow(() -> missing());
        long organizerId = resource.getOrganizerId();
        var now = clock.instant();
        resource.requireRecruiting(now);
        if (actorId == organizerId)
            throw new ConflictException("ORGANIZER_CANNOT_CANCEL", "주최자는 참가를 취소할 수 없습니다.");
        participants
                .findByActivityIdAndUserId(id, actorId)
                .orElseThrow(() -> missing())
                .cancel(now);
    }

    public ParticipationResponse mine(long actorId, long clubId, long id) {
        var resource =
                activities
                        .findByIdAndClubIdAndDeletedAtIsNull(id, clubId)
                        .orElseThrow(() -> missing());
        long organizerId = resource.getOrganizerId();
        return participants
                .findByActivityIdAndUserId(id, actorId)
                .map(p -> new ParticipationResponse(p.getStatus(), p.getAttendanceStatus()))
                .orElse(new ParticipationResponse(null, null));
    }

    public PageResponse<ParticipantResponse> roster(
            long actorId, long clubId, long id, int page, int size) {
        var resource =
                activities
                        .findByIdAndClubIdAndDeletedAtIsNull(id, clubId)
                        .orElseThrow(() -> missing());
        long organizerId = resource.getOrganizerId();
        requireOrganizer(actorId, organizerId);
        var rows = participants.findByActivityId(id, PageRequest.of(page, size, Sort.by("userId")));
        var identities =
                users.findSummaries(rows.stream().map(ActivityParticipant::getUserId).toList());
        return PageResponse.from(
                rows.map(
                        p -> {
                            var u = identities.get(p.getUserId());
                            return new ParticipantResponse(
                                    p.getUserId(),
                                    u.intraLogin(),
                                    u.displayName(),
                                    p.getStatus(),
                                    p.getAttendanceStatus(),
                                    p.getAttendanceCheckedBy(),
                                    p.getAttendanceCheckedAt());
                        }));
    }

    @Transactional
    public void attendance(long actorId, long clubId, long id, long userId, String value) {
        var resource = activities.findForUpdate(clubId, id).orElseThrow(() -> missing());
        long organizerId = resource.getOrganizerId();
        requireOrganizer(actorId, organizerId);
        participants
                .findByActivityIdAndUserId(id, userId)
                .orElseThrow(() -> missing())
                .attendance(value, actorId, clock.instant());
    }

    private static void requireOrganizer(long actorId, long organizerId) {
        if (actorId != organizerId) throw new AccessDeniedException("주최자 권한이 필요합니다.");
    }

    private static ResourceNotFoundException missing() {
        return new ResourceNotFoundException("대상을 찾을 수 없습니다.");
    }
}
