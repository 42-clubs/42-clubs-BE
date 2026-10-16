package com.clubplatform.activity.service;

import com.clubplatform.activity.dto.ActivityRequest;
import com.clubplatform.activity.entity.*;
import com.clubplatform.activity.repository.*;
import com.clubplatform.activity.storage.ImageUploadRepository;
import com.clubplatform.club.service.ClubPermissionService;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ActivityWriter {
    private final ActivityRepository activities;
    private final ActivityParticipantRepository participants;
    private final ActivityImageRepository images;
    private final ImageUploadRepository uploads;
    private final ClubPermissionService clubs;
    private final Clock clock;

    @Transactional
    public long create(long actorId, long clubId, ActivityRequest request, List<String> keys) {
        clubs.requireManager(actorId, clubId);
        var now = clock.instant();
        request.validate(now);
        var a =
                activities.save(
                        new Activity(
                                clubId,
                                actorId,
                                request.title(),
                                request.content(),
                                request.startsAt().toInstant(),
                                request.recruitmentClosesAt().toInstant(),
                                request.capacity(),
                                now));
        participants.save(new ActivityParticipant(a.getId(), actorId, now));
        for (int i = 0; i < keys.size(); i++) {
            uploads.findForUpdate(keys.get(i))
                    .orElseThrow(
                            () ->
                                    new com.clubplatform.activity.storage
                                            .StorageUnavailableException("사진 업로드 기록이 만료되었습니다."));
            images.save(new ActivityImage(a.getId(), keys.get(i), i, now));
            uploads.deleteById(keys.get(i));
        }
        return a.getId();
    }
}
