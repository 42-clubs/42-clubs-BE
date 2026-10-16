package com.clubplatform.gathering.service;

import com.clubplatform.gathering.dto.*;
import com.clubplatform.gathering.entity.*;
import com.clubplatform.gathering.repository.*;
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
public class SessionParticipationService {
    private final GatheringSessionRepository sessions;
    private final GatheringRepository gatherings;
    private final SessionParticipantRepository participants;
    private final UserAdministrationService users;
    private final Clock clock;

    @Transactional
    public void join(long actorId, long gatheringId, long id) {
        var resource = sessions.findForUpdate(id).orElseThrow(() -> missing());
        if (resource.getGatheringId() != gatheringId) throw missing();
        var gathering =
                gatherings.findByIdAndDeletedAtIsNull(gatheringId).orElseThrow(() -> missing());
        long organizerId = gathering.getOrganizerId();
        var now = clock.instant();
        resource.requireRecruiting(now);
        var existing = participants.findBySessionIdAndUserId(id, actorId);
        if (existing.isPresent() && "CONFIRMED".equals(existing.get().getStatus()))
            throw new ConflictException("ALREADY_JOINED", "이미 참가 중입니다.");
        if (participants.countBySessionIdAndStatus(id, "CONFIRMED") >= resource.getCapacity())
            throw new ConflictException("CAPACITY_FULL", "정원이 찼습니다.");
        if (existing.isPresent()) existing.get().confirm(now);
        else participants.save(new SessionParticipant(id, actorId, now));
    }

    @Transactional
    public void cancel(long actorId, long gatheringId, long id) {
        var resource = sessions.findForUpdate(id).orElseThrow(() -> missing());
        if (resource.getGatheringId() != gatheringId) throw missing();
        var gathering =
                gatherings.findByIdAndDeletedAtIsNull(gatheringId).orElseThrow(() -> missing());
        long organizerId = gathering.getOrganizerId();
        var now = clock.instant();
        resource.requireRecruiting(now);
        if (actorId == organizerId)
            throw new ConflictException("ORGANIZER_CANNOT_CANCEL", "주최자는 참가를 취소할 수 없습니다.");
        participants.findBySessionIdAndUserId(id, actorId).orElseThrow(() -> missing()).cancel(now);
    }

    public ParticipationResponse mine(long actorId, long gatheringId, long id) {
        var resource = sessions.findById(id).orElseThrow(() -> missing());
        if (resource.getGatheringId() != gatheringId) throw missing();
        var gathering =
                gatherings.findByIdAndDeletedAtIsNull(gatheringId).orElseThrow(() -> missing());
        long organizerId = gathering.getOrganizerId();
        return participants
                .findBySessionIdAndUserId(id, actorId)
                .map(p -> new ParticipationResponse(p.getStatus(), p.getAttendanceStatus()))
                .orElse(new ParticipationResponse(null, null));
    }

    public PageResponse<ParticipantResponse> roster(
            long actorId, long gatheringId, long id, int page, int size) {
        var resource = sessions.findById(id).orElseThrow(() -> missing());
        if (resource.getGatheringId() != gatheringId) throw missing();
        var gathering =
                gatherings.findByIdAndDeletedAtIsNull(gatheringId).orElseThrow(() -> missing());
        long organizerId = gathering.getOrganizerId();
        requireOrganizer(actorId, organizerId);
        var rows = participants.findBySessionId(id, PageRequest.of(page, size, Sort.by("userId")));
        var identities =
                users.findSummaries(rows.stream().map(SessionParticipant::getUserId).toList());
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
    public void attendance(long actorId, long gatheringId, long id, long userId, String value) {
        var resource = sessions.findForUpdate(id).orElseThrow(() -> missing());
        if (resource.getGatheringId() != gatheringId) throw missing();
        var gathering =
                gatherings.findByIdAndDeletedAtIsNull(gatheringId).orElseThrow(() -> missing());
        long organizerId = gathering.getOrganizerId();
        requireOrganizer(actorId, organizerId);
        if (resource.getCancelledAt() != null)
            throw new ConflictException("RECRUITMENT_CLOSED", "취소된 회차입니다.");
        participants
                .findBySessionIdAndUserId(id, userId)
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
