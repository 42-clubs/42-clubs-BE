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
        clubs.requireManager(actorId, clubId);
        return notices.save(
                        new Notice(
                                clubId,
                                actorId,
                                request.title(),
                                request.content(),
                                clock.instant()))
                .getId();
    }

    public PageResponse<NoticeSummary> list(long clubId, int page, int size) {
        clubs.requireExisting(clubId);
        return PageResponse.from(
                notices.findByClubIdAndDeletedAtIsNull(
                                clubId,
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
                                                n.getCreatedAt())));
    }

    public NoticeResponse detail(long id) {
        var n =
                notices.findByIdAndDeletedAtIsNull(id)
                        .orElseThrow(() -> new ResourceNotFoundException("공지를 찾을 수 없습니다."));
        return new NoticeResponse(
                n.getId(),
                n.getClubId(),
                n.getAuthorId(),
                n.getTitle(),
                n.getContent(),
                n.getCreatedAt());
    }
}
