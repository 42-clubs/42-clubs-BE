package com.clubplatform.notice.service;

import com.clubplatform.club.service.ClubPermissionService;
import com.clubplatform.global.dto.PageResponse;
import com.clubplatform.global.exception.ResourceNotFoundException;
import com.clubplatform.notice.dto.*;
import com.clubplatform.notice.entity.Notice;
import com.clubplatform.notice.repository.NoticeRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NoticeService {
    private final NoticeRepository notices;
    private final ClubPermissionService clubs;
    private final Clock clock;

    @Transactional
    public long create(long actorId, long clubId, NoticeRequest request) {
        clubs.requireNoticeWriter(actorId, clubId);
        return notices.save(
                        new Notice(
                                clubId,
                                actorId,
                                request.title(),
                                request.content(),
                                request.visibility(),
                                clock.instant()))
                .getId();
    }

    public PageResponse<NoticeSummary> list(Long actorId, long clubId, int page, int size) {
        clubs.requireExisting(clubId);
        boolean includePrivate = clubs.canReadPrivateNotices(actorId, clubId);
        return PageResponse.from(
                notices.findVisible(
                                clubId,
                                includePrivate,
                                PageRequest.of(
                                        page,
                                        size,
                                        Sort.by(Sort.Direction.DESC, "createdAt", "id")))
                        .map(
                                n ->
                                        new NoticeSummary(
                                                n.getId(),
                                                n.getClubId(),
                                                n.getTitle(),
                                                n.getVisibility(),
                                                n.getCreatedAt())));
    }

    public NoticeResponse detail(Long actorId, long clubId, long id) {
        boolean includePrivate = clubs.canReadPrivateNotices(actorId, clubId);
        var n =
                notices.findVisibleById(clubId, id, includePrivate)
                        .orElseThrow(() -> new ResourceNotFoundException("공지를 찾을 수 없습니다."));
        return new NoticeResponse(
                n.getId(),
                n.getClubId(),
                n.getAuthorId(),
                n.getTitle(),
                n.getContent(),
                n.getVisibility(),
                n.getCreatedAt());
    }
}
