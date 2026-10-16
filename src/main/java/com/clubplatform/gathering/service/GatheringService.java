package com.clubplatform.gathering.service;

import com.clubplatform.gathering.dto.*;
import com.clubplatform.gathering.entity.*;
import com.clubplatform.gathering.repository.*;
import com.clubplatform.global.dto.PageResponse;
import com.clubplatform.global.exception.ResourceNotFoundException;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GatheringService {
    private final GatheringRepository gatherings;
    private final GatheringSessionRepository sessions;
    private final SessionParticipantRepository participants;
    private final Clock clock;

    @Transactional
    public GatheringCreated create(long actorId, GatheringRequest request) {
        var now = clock.instant();
        request.session().validate(now);
        var g = gatherings.save(new Gathering(actorId, request.title(), request.content(), now));
        return add(g, request.session(), now);
    }

    @Transactional
    public GatheringCreated addSession(long actorId, long gatheringId, SessionRequest request) {
        var g = gatherings.findForUpdate(gatheringId).orElseThrow(() -> missing());
        if (g.getOrganizerId() != actorId) throw new AccessDeniedException("주최자만 회차를 추가할 수 있습니다.");
        var now = clock.instant();
        request.validate(now);
        return add(g, request, now);
    }

    private GatheringCreated add(Gathering g, SessionRequest r, Instant now) {
        var s =
                sessions.save(
                        new GatheringSession(
                                g.getId(),
                                r.startsAt().toInstant(),
                                r.endsAt().toInstant(),
                                r.recruitmentClosesAt().toInstant(),
                                r.location(),
                                r.capacity(),
                                now));
        participants.save(new SessionParticipant(s.getId(), g.getOrganizerId(), now));
        return new GatheringCreated(g.getId(), s.getId());
    }

    public PageResponse<GatheringResponse> list(int page, int size) {
        var now = clock.instant();
        var rows = gatherings.findPublic(now, PageRequest.of(page, size));
        var ids = rows.stream().map(Gathering::getId).toList();
        if (ids.isEmpty())
            return PageResponse.from(
                    rows.map(
                            g ->
                                    new GatheringResponse(
                                            g.getId(),
                                            g.getOrganizerId(),
                                            g.getTitle(),
                                            null,
                                            null,
                                            null)));
        var reps = sessions.representatives(ids, now);
        var byGathering =
                reps.stream().collect(Collectors.toMap(GatheringSession::getGatheringId, s -> s));
        var counts =
                participants
                        .confirmedCounts(reps.stream().map(GatheringSession::getId).toList())
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        SessionParticipantRepository.ConfirmedCount::getParentId,
                                        SessionParticipantRepository.ConfirmedCount::getTotal));
        return PageResponse.from(
                rows.map(
                        g -> {
                            var s = byGathering.get(g.getId());
                            return new GatheringResponse(
                                    g.getId(),
                                    g.getOrganizerId(),
                                    g.getTitle(),
                                    null,
                                    null,
                                    response(s, counts.getOrDefault(s.getId(), 0L), now));
                        }));
    }

    public GatheringResponse detail(long id) {
        var g = requireGathering(id);
        return new GatheringResponse(
                g.getId(), g.getOrganizerId(), g.getTitle(), g.getContent(), null, null);
    }

    public SessionResponse session(long id) {
        var s = sessions.findById(id).orElseThrow(() -> missing());
        requireGathering(s.getGatheringId());
        return response(
                s, participants.countBySessionIdAndStatus(id, "CONFIRMED"), clock.instant());
    }

    public PageResponse<SessionResponse> sessions(long id, String filter, int page, int size) {
        requireGathering(id);
        var now = clock.instant();
        var rows =
                sessions.findFiltered(
                        id,
                        filter,
                        now,
                        PageRequest.of(
                                page,
                                size,
                                Sort.by(
                                        "PAST".equals(filter)
                                                ? Sort.Direction.DESC
                                                : Sort.Direction.ASC,
                                        "startsAt",
                                        "id")));
        if (rows.isEmpty()) return PageResponse.from(rows.map(s -> response(s, 0, now)));
        var counts =
                participants
                        .confirmedCounts(rows.stream().map(GatheringSession::getId).toList())
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        SessionParticipantRepository.ConfirmedCount::getParentId,
                                        SessionParticipantRepository.ConfirmedCount::getTotal));
        return PageResponse.from(
                rows.map(s -> response(s, counts.getOrDefault(s.getId(), 0L), now)));
    }

    private Gathering requireGathering(long id) {
        return gatherings.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> missing());
    }

    private SessionResponse response(GatheringSession s, long count, Instant now) {
        long days =
                ChronoUnit.DAYS.between(
                        now.atZone(ZoneId.of("Asia/Seoul")).toLocalDate(),
                        s.getStartsAt().atZone(ZoneId.of("Asia/Seoul")).toLocalDate());
        return new SessionResponse(
                s.getId(),
                s.getGatheringId(),
                s.getStartsAt(),
                s.getEndsAt(),
                s.getRecruitmentOpensAt(),
                s.getRecruitmentClosesAt(),
                s.getLocation(),
                s.getCapacity(),
                count,
                Math.max(0, s.getCapacity() - count),
                s.getCancelledAt() == null
                        && !now.isBefore(s.getRecruitmentOpensAt())
                        && now.isBefore(s.getRecruitmentClosesAt())
                        && now.isBefore(s.getStartsAt())
                        && count < s.getCapacity(),
                days < 0 ? "종료" : days == 0 ? "D-Day" : "D-" + days,
                s.getCancelledAt() != null);
    }

    private static ResourceNotFoundException missing() {
        return new ResourceNotFoundException("모임 또는 회차를 찾을 수 없습니다.");
    }
}
