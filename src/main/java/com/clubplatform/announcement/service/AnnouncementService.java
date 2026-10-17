package com.clubplatform.announcement.service;

import com.clubplatform.announcement.dto.*;
import com.clubplatform.announcement.entity.Announcement;
import com.clubplatform.announcement.repository.AnnouncementRepository;
import com.clubplatform.global.dto.PageResponse;
import com.clubplatform.global.exception.ResourceNotFoundException;
import com.clubplatform.user.service.UserAdministrationService;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnnouncementService {
    private final AnnouncementRepository announcements;
    private final UserAdministrationService users;
    private final Clock clock;

    @Transactional
    public long create(long actorId, AnnouncementRequest r) {
        users.requireAdmin(actorId);
        return announcements
                .save(new Announcement(actorId, r.title(), r.content(), clock.instant()))
                .getId();
    }

    @Transactional
    public void delete(long actorId, long id) {
        users.requireAdmin(actorId);
        var announcement =
                announcements
                        .findForUpdate(id)
                        .orElseThrow(() -> new ResourceNotFoundException("운영 공지를 찾을 수 없습니다."));
        announcement.delete(clock.instant());
    }

    public PageResponse<AnnouncementSummary> list(int page, int size) {
        return PageResponse.from(
                announcements
                        .findByDeletedAtIsNull(
                                PageRequest.of(
                                        page,
                                        size,
                                        Sort.by(Sort.Direction.DESC, "createdAt", "id")))
                        .map(
                                a ->
                                        new AnnouncementSummary(
                                                a.getId(), a.getTitle(), a.getCreatedAt())));
    }

    public AnnouncementResponse detail(long id) {
        var a =
                announcements
                        .findByIdAndDeletedAtIsNull(id)
                        .orElseThrow(() -> new ResourceNotFoundException("운영 공지를 찾을 수 없습니다."));
        return new AnnouncementResponse(
                a.getId(), a.getAuthorId(), a.getTitle(), a.getContent(), a.getCreatedAt());
    }
}
